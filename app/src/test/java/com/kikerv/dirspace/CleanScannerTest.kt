package com.kikerv.dirspace

import com.kikerv.dirspace.clean.CleanCategory
import com.kikerv.dirspace.clean.CleanReport
import com.kikerv.dirspace.clean.CleanRisk
import com.kikerv.dirspace.clean.CleanScanner
import com.kikerv.dirspace.model.FsNode
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

class CleanScannerTest {

    private fun dir(name: String, parent: FsNode?): FsNode {
        val node = if (parent == null) {
            FsNode(name, null, true, "/storage/emulated/0")
        } else {
            FsNode(name, parent, true)
        }
        node.children = ArrayList()
        parent?.children?.add(node)
        return node
    }

    private fun file(name: String, parent: FsNode, size: Long, ageDays: Int = 0): FsNode {
        val node = FsNode(name, parent, false)
        node.size = size
        node.fileCount = 1
        node.lastModified = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(ageDays.toLong())
        parent.children?.add(node)
        return node
    }

    /** Suma tamaños de abajo arriba, como hace el escáner de verdad. */
    private fun aggregate(node: FsNode) {
        val children = node.children ?: return
        var size = 0L
        var files = 0
        var dirs = 0
        for (child in children) {
            aggregate(child)
            size += child.size
            files += child.fileCount
            if (child.isDirectory) dirs += child.dirCount + 1
        }
        node.size = size
        node.fileCount = files
        node.dirCount = dirs
    }

    private fun analyze(root: FsNode, installed: Set<String> = emptySet()): CleanReport {
        aggregate(root)
        return runBlocking { CleanScanner.analyze(root, installed) }
    }

    private fun CleanReport.namesIn(category: CleanCategory): List<String> =
        groups.firstOrNull { it.category == category }?.items?.map { it.node.name } ?: emptyList()

    @Test
    fun `reconoce temporales, registros e instaladores`() {
        val root = dir("Interno", null)
        val download = dir("Download", root)
        file("pelicula.mp4.part", download, 500)
        file("descarga.tmp", download, 100)
        file("app.apk", download, 900)
        file("errores.log", download, 50)
        file("foto.jpg", download, 700)

        val report = analyze(root)

        assertEquals(
            listOf("pelicula.mp4.part", "descarga.tmp"),
            report.namesIn(CleanCategory.TEMP_FILES),
        )
        assertEquals(listOf("app.apk"), report.namesIn(CleanCategory.APK_INSTALLERS))
        assertEquals(listOf("errores.log"), report.namesIn(CleanCategory.LOGS))
        // Una foto normal no es basura: no debe aparecer en ninguna categoría.
        assertTrue(report.groups.none { group -> group.items.any { it.node.name == "foto.jpg" } })
    }

    @Test
    fun `una papelera se propone entera y no archivo a archivo`() {
        val root = dir("Interno", null)
        val trash = dir(".Trash-1000", root)
        file("borrado1.mp4", trash, 1000)
        file("borrado2.mp4", trash, 2000)

        val report = analyze(root)

        val group = report.groups.first { it.category == CleanCategory.TRASH }
        assertEquals(1, group.items.size)
        assertEquals(".Trash-1000", group.items.first().node.name)
        // El tamaño propuesto es el de la carpeta entera, no el de un archivo.
        assertEquals(3000L, group.bytes)
    }

    @Test
    fun `solo propone datos de paquetes que ya no estan instalados`() {
        val root = dir("Interno", null)
        val android = dir("Android", root)
        val data = dir("data", android)
        val vivo = dir("com.app.viva", data)
        file("cache.bin", vivo, 400)
        val muerto = dir("com.app.desinstalada", data)
        file("cache.bin", muerto, 800)

        val report = analyze(root, installed = setOf("com.app.viva"))

        assertEquals(listOf("com.app.desinstalada"), report.namesIn(CleanCategory.LEFTOVERS))
    }

    @Test
    fun `sin lista de paquetes no propone restos de apps`() {
        val root = dir("Interno", null)
        val android = dir("Android", root)
        val data = dir("data", android)
        val app = dir("com.app.loquesea", data)
        file("cache.bin", app, 800)

        val report = analyze(root, installed = emptySet())

        assertTrue(report.namesIn(CleanCategory.LEFTOVERS).isEmpty())
    }

    @Test
    fun `las carpetas de usuario vacias no se proponen para borrar`() {
        val root = dir("Interno", null)
        dir("DCIM", root)
        dir("BasuraVieja", root)

        val report = analyze(root)

        val empty = report.namesIn(CleanCategory.EMPTY_FOLDERS)
        assertTrue("BasuraVieja" in empty)
        assertFalse("DCIM debería estar protegida", "DCIM" in empty)
    }

    @Test
    fun `grandes y antiguos exige tamano y antiguedad a la vez`() {
        val root = dir("Interno", null)
        val movies = dir("Movies", root)
        val big = 30L * 1024 * 1024
        file("viejo-grande.mkv", movies, big, ageDays = 400)
        file("nuevo-grande.mkv", movies, big, ageDays = 5)
        file("viejo-pequeno.txt", movies, 1000, ageDays = 400)

        val report = analyze(root)

        assertEquals(listOf("viejo-grande.mkv"), report.namesIn(CleanCategory.OLD_LARGE))
    }

    @Test
    fun `las categorias sensibles no se marcan solas`() {
        // Un borrado en bloque sólo debe tocar lo que el sistema regenera.
        assertEquals(CleanRisk.REVIEW, CleanCategory.OLD_LARGE.risk)
        assertEquals(CleanRisk.REVIEW, CleanCategory.DUPLICATES.risk)
        assertEquals(CleanRisk.REVIEW, CleanCategory.APK_INSTALLERS.risk)
        assertEquals(CleanRisk.REVIEW, CleanCategory.LEFTOVERS.risk)
        assertEquals(CleanRisk.SAFE, CleanCategory.TEMP_FILES.risk)
        assertEquals(CleanRisk.SAFE, CleanCategory.THUMBNAILS.risk)
    }

    @Test
    fun `un volumen limpio no propone nada`() {
        val root = dir("Interno", null)
        val dcim = dir("DCIM", root)
        file("IMG_0001.jpg", dcim, 3_000_000)

        val report = analyze(root)

        assertEquals(0L, report.totalBytes)
        assertTrue(report.groups.isEmpty())
        assertNull(report.groups.firstOrNull())
    }
}
