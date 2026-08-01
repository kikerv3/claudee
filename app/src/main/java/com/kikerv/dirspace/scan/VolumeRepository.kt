package com.kikerv.dirspace.scan

import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.os.storage.StorageManager
import android.os.storage.StorageVolume
import com.kikerv.dirspace.R
import java.io.File

data class StorageVolumeInfo(
    val id: String,
    val label: String,
    val root: File,
    val isRemovable: Boolean,
    val totalBytes: Long,
    val freeBytes: Long,
) {
    val usedBytes: Long get() = (totalBytes - freeBytes).coerceAtLeast(0L)
}

/** Descubre los volúmenes montados (interno + tarjeta SD / USB). */
class VolumeRepository(private val context: Context) {

    fun volumes(): List<StorageVolumeInfo> {
        val found = LinkedHashMap<String, StorageVolumeInfo>()

        val storageManager = context.getSystemService(Context.STORAGE_SERVICE) as? StorageManager
        storageManager?.storageVolumes?.forEach { volume ->
            val root = volumeRoot(volume) ?: return@forEach
            if (!root.isDirectory || !root.canRead()) return@forEach
            val info = describe(root, volume.isRemovable, volume.getDescription(context))
            found[info.root.absolutePath] = info
        }

        // Respaldo por si StorageManager no devuelve nada utilizable
        // (algunas ROMs en API 26-29 ocultan getPath).
        if (found.isEmpty()) {
            val primary = Environment.getExternalStorageDirectory()
            if (primary != null && primary.isDirectory) {
                val info = describe(primary, isRemovable = false, label = null)
                found[info.root.absolutePath] = info
            }
            // Las rutas de app externas revelan las tarjetas SD montadas:
            // /storage/XXXX-XXXX/Android/data/<pkg>/files -> /storage/XXXX-XXXX
            context.getExternalFilesDirs(null)?.forEach { dir ->
                val volumeRoot = dir?.let { stripAndroidDataSuffix(it) } ?: return@forEach
                if (!volumeRoot.isDirectory) return@forEach
                if (found.containsKey(volumeRoot.absolutePath)) return@forEach
                val removable = !volumeRoot.absolutePath.startsWith("/storage/emulated")
                val info = describe(volumeRoot, isRemovable = removable, label = null)
                found[info.root.absolutePath] = info
            }
        }

        return found.values.sortedBy { it.isRemovable }
    }

    private fun describe(root: File, isRemovable: Boolean, label: String?): StorageVolumeInfo {
        // La raíz puede ser un enlace (/sdcard -> /storage/emulated/0). Se resuelve
        // aquí para que el escáner no la descarte como symlink.
        val canonical = try {
            root.canonicalFile
        } catch (e: Exception) {
            root.absoluteFile
        }
        var total = 0L
        var free = 0L
        try {
            val stat = StatFs(canonical.absolutePath)
            total = stat.blockCountLong * stat.blockSizeLong
            free = stat.availableBlocksLong * stat.blockSizeLong
        } catch (e: Exception) {
            // Volumen desmontado a media consulta: se deja en cero.
        }
        val name = label?.takeIf { it.isNotBlank() }
            ?: context.getString(
                if (isRemovable) R.string.volume_sdcard else R.string.volume_internal
            )
        return StorageVolumeInfo(
            id = canonical.absolutePath,
            label = name,
            root = canonical,
            isRemovable = isRemovable,
            totalBytes = total,
            freeBytes = free,
        )
    }

    private fun volumeRoot(volume: StorageVolume): File? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            volume.directory?.let { return it }
        }
        return try {
            val method = volume.javaClass.getMethod("getPath")
            (method.invoke(volume) as? String)?.let { File(it) }
        } catch (e: Exception) {
            null
        }
    }

    private fun stripAndroidDataSuffix(dir: File): File? {
        val marker = File.separator + "Android" + File.separator + "data" + File.separator
        val path = dir.absolutePath
        val index = path.indexOf(marker)
        if (index <= 0) return null
        return File(path.substring(0, index))
    }
}
