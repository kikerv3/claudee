package com.kikerv.dirspace.clean

import com.kikerv.dirspace.model.FsNode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit
import kotlin.coroutines.coroutineContext

class CleanItem(
    val node: FsNode,
    /** Por qué se propone, ya formateado: paquete desinstalado, fecha, copia… */
    val detail: String,
)

class CleanGroup(
    val category: CleanCategory,
    val items: List<CleanItem>,
) {
    val bytes: Long = items.sumOf { it.node.size }
}

class CleanReport(
    val groups: List<CleanGroup>,
    /** True si el sistema oculta Android/data, que es lo normal desde Android 11. */
    val appDataHidden: Boolean,
) {
    val totalBytes: Long = groups.sumOf { it.bytes }

    companion object {
        val EMPTY = CleanReport(emptyList(), appDataHidden = false)
    }
}

data class CleanOptions(
    /** A partir de qué tamaño un archivo entra en "grandes y antiguos". */
    val oldLargeMinBytes: Long = 20L * 1024 * 1024,
    val oldLargeMinAgeDays: Int = 180,
)

/**
 * Aplica las reglas de limpieza sobre el árbol ya escaneado.
 *
 * Trabaja sobre el árbol en memoria en vez de volver a tocar el disco, así que
 * analizar es instantáneo comparado con el escaneo. Cada elemento cae en una
 * sola categoría —la primera que casa, por orden de prioridad— para que los
 * totales de las tarjetas puedan sumarse sin contar dos veces lo mismo.
 */
object CleanScanner {

    /** Carpetas de usuario que nunca se proponen para borrar aunque estén vacías. */
    private val PROTECTED_TOP_LEVEL = setOf(
        "dcim", "pictures", "movies", "music", "documents", "download", "downloads",
        "podcasts", "ringtones", "alarms", "notifications", "audiobooks", "recordings",
        "android", "backups",
    )

    // Sin .bak ni .old a propósito: una copia de seguridad no la regenera
    // nadie, y esta categoría se marca sola para la limpieza rápida. Si pesan,
    // aparecen igualmente en "grandes y antiguos", que sí se revisa a mano.
    private val TEMP_EXTENSIONS = setOf(
        "tmp", "temp", "part", "partial", "crdownload", "dmp", "chk",
    )

    private val TEMP_NAMES = setOf(
        "thumbs.db", ".ds_store", "desktop.ini", ".directory",
    )

    // Sólo nombres que no puede haber elegido una persona para sus cosas:
    // borrar esto va marcado de serie.
    private val TRASH_DIRS = setOf(
        ".trash", ".trash-1000", "lost.dir", ".recycle", "\$recycle.bin",
        ".trashed", ".mtrash",
    )

    private val THUMBNAIL_DIRS = setOf(".thumbnails", ".thumbs")

    private val APK_EXTENSIONS = setOf("apk", "apks", "xapk", "aab")

    /** Carpetas bajo las que un subdirectorio con nombre de paquete es datos de una app. */
    private val PACKAGE_PARENTS = setOf("data", "media", "obb")

    suspend fun analyze(
        root: FsNode,
        installedPackages: Set<String>,
        options: CleanOptions = CleanOptions(),
    ): CleanReport = withContext(Dispatchers.Default) {
        val buckets = LinkedHashMap<CleanCategory, MutableList<CleanItem>>()
        fun add(category: CleanCategory, node: FsNode, detail: String) {
            buckets.getOrPut(category) { ArrayList() }.add(CleanItem(node, detail))
        }

        val oldThreshold = System.currentTimeMillis() -
            TimeUnit.DAYS.toMillis(options.oldLargeMinAgeDays.toLong())

        var appDataHidden = false

        val stack = ArrayList<FsNode>(256)
        stack.add(root)
        var checked = 0

        while (stack.isNotEmpty()) {
            if ((++checked and 0x3FF) == 0) coroutineContext.ensureActive()
            val node = stack.removeAt(stack.size - 1)

            if (!node.isDirectory) {
                classifyFile(node, options, oldThreshold)?.let { (category, detail) ->
                    add(category, node, detail)
                }
                continue
            }

            // Android/data existe pero el sistema lo devuelve vacío desde
            // Android 11: hay que decirlo, no fingir que no hay nada que limpiar.
            if (node.depth == 2 && node.name == "data" && node.parent?.name == "Android") {
                if (node.children.isNullOrEmpty()) appDataHidden = true
            }

            val lower = node.name.lowercase()
            val isTopLevel = node.depth == 1

            // Una carpeta que casa se propone entera y no se sigue bajando: así
            // no aparecen a la vez la papelera y cada archivo de dentro. Las
            // papeleras y LOST.DIR cuelgan de la raíz del volumen, así que aquí
            // no vale excluir el primer nivel: ninguna carpeta protegida se
            // llama como una papelera.
            if (lower in TRASH_DIRS && node.size > 0) {
                add(CleanCategory.TRASH, node, node.path)
                continue
            }
            if (lower in THUMBNAIL_DIRS && node.size > 0) {
                add(CleanCategory.THUMBNAILS, node, node.path)
                continue
            }

            val leftoverOf = leftoverPackage(node, installedPackages)
            if (leftoverOf != null) {
                add(CleanCategory.LEFTOVERS, node, leftoverOf)
                continue
            }

            if (node.fileCount == 0 && !node.isRoot && !(isTopLevel && lower in PROTECTED_TOP_LEVEL)) {
                add(CleanCategory.EMPTY_FOLDERS, node, node.path)
                continue
            }

            node.children?.let { stack.addAll(it) }
        }

        val groups = CleanCategory.entries
            .mapNotNull { category ->
                val items = buckets[category] ?: return@mapNotNull null
                if (items.isEmpty()) return@mapNotNull null
                CleanGroup(category, items.sortedByDescending { it.node.size })
            }

        CleanReport(groups, appDataHidden)
    }

    private fun classifyFile(
        node: FsNode,
        options: CleanOptions,
        oldThreshold: Long,
    ): Pair<CleanCategory, String>? {
        val lower = node.name.lowercase()
        val extension = node.extension

        if (lower in TEMP_NAMES || extension in TEMP_EXTENSIONS || lower.startsWith("~\$")) {
            return CleanCategory.TEMP_FILES to node.path
        }
        // Android 11 marca lo borrado renombrándolo, no moviéndolo de carpeta.
        if (lower.startsWith(".trashed-") || lower.startsWith(".pending-")) {
            return CleanCategory.TRASH to node.path
        }
        if (lower.startsWith("thumbdata") || lower.startsWith(".thumbdata")) {
            return CleanCategory.THUMBNAILS to node.path
        }
        if (extension == "log" || lower.endsWith(".log.1") || lower.startsWith("logcat")) {
            return CleanCategory.LOGS to node.path
        }
        if (extension in APK_EXTENSIONS) {
            return CleanCategory.APK_INSTALLERS to node.path
        }
        if (node.size >= options.oldLargeMinBytes &&
            node.lastModified in 1 until oldThreshold
        ) {
            return CleanCategory.OLD_LARGE to node.path
        }
        return null
    }

    /**
     * Devuelve el nombre del paquete si esta carpeta son datos de una app que ya
     * no está instalada. Sin la lista de paquetes instalados no se arriesga.
     */
    private fun leftoverPackage(node: FsNode, installedPackages: Set<String>): String? {
        if (installedPackages.isEmpty()) return null
        val parent = node.parent ?: return null
        if (parent.name.lowercase() !in PACKAGE_PARENTS) return null
        if (parent.parent?.name != "Android") return null
        val name = node.name
        // Los nombres de paquete llevan punto y no empiezan por él.
        if (!name.contains('.') || name.startsWith('.')) return null
        if (name in installedPackages) return null
        return name
    }
}
