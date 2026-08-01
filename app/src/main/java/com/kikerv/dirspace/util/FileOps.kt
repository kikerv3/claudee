package com.kikerv.dirspace.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import com.kikerv.dirspace.R
import com.kikerv.dirspace.model.FsNode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

data class DeleteResult(
    val bytesFreed: Long,
    val filesDeleted: Int,
    val dirsDeleted: Int,
    val complete: Boolean,
)

object FileOps {

    fun mimeTypeOf(file: File): String {
        val ext = file.extension.lowercase()
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "*/*"
    }

    private fun uriFor(context: Context, file: File) =
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

    /** Devuelve false si no hay ninguna app capaz de abrir el archivo. */
    fun open(context: Context, file: File): Boolean = try {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uriFor(context, file), mimeTypeOf(file))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        true
    } catch (e: ActivityNotFoundException) {
        false
    } catch (e: Exception) {
        false
    }

    fun share(context: Context, file: File): Boolean = try {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeTypeOf(file)
            putExtra(Intent.EXTRA_STREAM, uriFor(context, file))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(intent, context.getString(R.string.action_share))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
        true
    } catch (e: Exception) {
        false
    }

    /**
     * Borra el nodo del disco. Devuelve lo que realmente se liberó: un borrado
     * puede quedarse a medias si el sistema protege alguna ruta, y el árbol en
     * memoria debe reflejar exactamente lo que desapareció.
     */
    suspend fun delete(node: FsNode): DeleteResult = withContext(Dispatchers.IO) {
        var bytes = 0L
        var files = 0
        var dirs = 0
        var complete = true

        fun deleteNode(target: FsNode): Boolean {
            val children = target.children
            var childrenGone = true
            if (children != null) {
                val iterator = children.iterator()
                while (iterator.hasNext()) {
                    val child = iterator.next()
                    if (deleteNode(child)) iterator.remove() else childrenGone = false
                }
            }
            if (!childrenGone) {
                complete = false
                return false
            }
            val deleted = try {
                val f = target.file
                !f.exists() || f.delete()
            } catch (e: Exception) {
                false
            }
            if (!deleted) {
                complete = false
                return false
            }
            if (target.isDirectory) {
                dirs++
            } else {
                bytes += target.size
                files++
            }
            return true
        }

        val removed = deleteNode(node)
        if (removed) {
            node.parent?.children?.remove(node)
        } else {
            // Borrado parcial: se recalcula lo que queda para no mentir en la UI.
            node.size -= bytes
            node.fileCount -= files
            node.dirCount -= dirs
        }
        node.propagateRemoval(bytes, files, dirs)

        DeleteResult(bytes, files, dirs, complete)
    }
}
