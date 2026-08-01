package com.kikerv.dirspace.scan

import com.kikerv.dirspace.model.FsNode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.file.Files
import kotlin.coroutines.coroutineContext

data class ScanProgress(
    val filesSeen: Int = 0,
    val dirsSeen: Int = 0,
    val bytesSeen: Long = 0L,
    val currentPath: String = "",
)

/**
 * Recorre un volumen completo y construye el árbol de [FsNode].
 *
 * El recorrido es iterativo (pila explícita) en vez de recursivo: hay jerarquías
 * de miles de niveles en cachés de apps que reventarían la pila de la JVM.
 */
object StorageScanner {

    /** Cada cuántas entradas se publica progreso, para no saturar el hilo de UI. */
    private const val PROGRESS_EVERY = 750

    suspend fun scan(
        root: File,
        displayName: String,
        onProgress: (ScanProgress) -> Unit,
    ): FsNode = withContext(Dispatchers.IO) {
        val rootNode = FsNode(
            // El nombre real de /storage/emulated/0 es "0": inservible como
            // titulo, asi que la raiz lleva la etiqueta del volumen.
            name = displayName.ifEmpty { root.absolutePath },
            parent = null,
            isDirectory = true,
            rootPath = root.absolutePath,
        )

        // Orden previo (padres antes que hijos): al recorrerlo al revés cada
        // carpeta ya tiene sus hijos calculados.
        val visitOrder = ArrayList<FsNode>(4096)
        val stack = ArrayList<Pair<FsNode, File>>(256)
        stack.add(rootNode to root)

        var filesSeen = 0
        var dirsSeen = 0
        var bytesSeen = 0L
        var sinceProgress = 0

        while (stack.isNotEmpty()) {
            coroutineContext.ensureActive()

            val (node, dir) = stack.removeAt(stack.size - 1)
            visitOrder.add(node)
            dirsSeen++

            val entries = dir.listFiles()
            if (entries == null || entries.isEmpty()) {
                node.children = ArrayList(0)
                continue
            }

            val kids = ArrayList<FsNode>(entries.size)
            for (entry in entries) {
                val isDir = entry.isDirectory
                if (isDir) {
                    // Los enlaces simbólicos se saltan: /sdcard, /storage/self y
                    // varios directorios de sistema apuntan al mismo sitio y
                    // provocarían bucles infinitos y tamaños duplicados.
                    if (isSymlink(entry)) continue
                    val child = FsNode(entry.name, node, true)
                    kids.add(child)
                    stack.add(child to entry)
                } else {
                    val child = FsNode(entry.name, node, false)
                    child.size = entry.length()
                    child.fileCount = 1
                    child.lastModified = entry.lastModified()
                    kids.add(child)
                    filesSeen++
                    bytesSeen += child.size
                }
            }
            node.children = kids

            sinceProgress += entries.size
            if (sinceProgress >= PROGRESS_EVERY) {
                sinceProgress = 0
                onProgress(ScanProgress(filesSeen, dirsSeen, bytesSeen, dir.absolutePath))
            }
        }

        // Agregación ascendente: hijos antes que padres.
        for (i in visitOrder.indices.reversed()) {
            coroutineContext.ensureActive()
            val node = visitOrder[i]
            val kids = node.children ?: continue
            var size = 0L
            var files = 0
            var dirs = 0
            var newest = 0L
            for (k in kids) {
                size += k.size
                files += k.fileCount
                if (k.isDirectory) dirs += k.dirCount + 1
                if (k.lastModified > newest) newest = k.lastModified
            }
            node.size = size
            node.fileCount = files
            node.dirCount = dirs
            node.lastModified = newest
            kids.sortWith(SIZE_DESC)
        }

        onProgress(ScanProgress(filesSeen, dirsSeen, bytesSeen, root.absolutePath))
        rootNode
    }

    /** Reordena los hijos de un nodo tras un borrado o un cambio de criterio. */
    val SIZE_DESC: Comparator<FsNode> = Comparator { a, b ->
        val c = b.size.compareTo(a.size)
        if (c != 0) c else a.name.compareTo(b.name, ignoreCase = true)
    }

    private fun isSymlink(file: File): Boolean = try {
        Files.isSymbolicLink(file.toPath())
    } catch (e: Exception) {
        // Si no se puede resolver, mejor saltarlo que arriesgar un bucle.
        true
    }
}
