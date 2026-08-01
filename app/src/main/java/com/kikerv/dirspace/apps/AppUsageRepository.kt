package com.kikerv.dirspace.apps

import android.app.AppOpsManager
import android.app.usage.StorageStatsManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Process
import android.os.UserHandle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

data class AppUsage(
    val packageName: String,
    val label: String,
    val icon: Bitmap?,
    val appBytes: Long,
    val dataBytes: Long,
    val cacheBytes: Long,
    val isSystem: Boolean,
) {
    /** `dataBytes` ya incluye la caché, así que sumarla otra vez inflaría el total. */
    val totalBytes: Long get() = appBytes + dataBytes
}

/**
 * Tamaño real de cada aplicación instalada.
 *
 * El escaneo de archivos no puede ver `/data/data`, así que ese espacio —a
 * menudo la mitad del almacenamiento ocupado— sólo se obtiene por aquí, y sólo
 * si el usuario concede "Acceso a datos de uso" en Ajustes.
 */
class AppUsageRepository(private val context: Context) {

    fun hasUsageAccess(): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager
            ?: return false
        val uid = Process.myUid()
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                uid,
                context.packageName,
            )
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                uid,
                context.packageName,
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    /**
     * Paquetes instalados. La limpieza los usa para distinguir los datos de una
     * app viva de los restos de otra que ya se desinstaló. No necesita el
     * permiso de datos de uso.
     */
    suspend fun installedPackageNames(): Set<String> = withContext(Dispatchers.IO) {
        try {
            context.packageManager
                .getInstalledApplications(0)
                .mapTo(HashSet()) { it.packageName }
        } catch (e: Exception) {
            emptySet()
        }
    }

    suspend fun load(iconSizePx: Int = 128): List<AppUsage> = withContext(Dispatchers.IO) {
        val statsManager = context.getSystemService(Context.STORAGE_STATS_SERVICE)
            as? StorageStatsManager ?: return@withContext emptyList()
        val packageManager = context.packageManager
        val user: UserHandle = Process.myUserHandle()

        val installed = try {
            packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
        } catch (e: Exception) {
            emptyList<ApplicationInfo>()
        }

        val result = ArrayList<AppUsage>(installed.size)
        for (info in installed) {
            coroutineContext.ensureActive()
            val stats = try {
                statsManager.queryStatsForPackage(info.storageUuid, info.packageName, user)
            } catch (e: Exception) {
                // Apps de otro perfil o desinstaladas a media consulta.
                continue
            }
            val label = try {
                packageManager.getApplicationLabel(info).toString()
            } catch (e: Exception) {
                info.packageName
            }
            val icon = try {
                packageManager.getApplicationIcon(info).toBitmap(iconSizePx)
            } catch (e: Exception) {
                null
            }
            result.add(
                AppUsage(
                    packageName = info.packageName,
                    label = label,
                    icon = icon,
                    appBytes = stats.appBytes,
                    dataBytes = stats.dataBytes,
                    cacheBytes = stats.cacheBytes,
                    isSystem = (info.flags and ApplicationInfo.FLAG_SYSTEM) != 0,
                )
            )
        }
        result.sortByDescending { it.totalBytes }
        result
    }

    private fun Drawable.toBitmap(sizePx: Int): Bitmap {
        if (this is BitmapDrawable) {
            val source = bitmap
            if (source != null) return Bitmap.createScaledBitmap(source, sizePx, sizePx, true)
        }
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        setBounds(0, 0, sizePx, sizePx)
        draw(canvas)
        return bitmap
    }
}
