package com.kikerv.dirspace.clean

import com.kikerv.dirspace.model.FsNode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.RandomAccessFile
import java.security.MessageDigest
import kotlin.coroutines.coroutineContext

data class DuplicateProgress(val done: Int, val total: Int)

/**
 * Busca archivos idénticos byte a byte.
 *
 * En tres fases para no leer gigabytes de más: primero agrupa por tamaño, que
 * es gratis porque ya está en el árbol; después compara una firma de los
 * extremos del archivo, que descarta casi todos los falsos positivos leyendo
 * 128 KB; y sólo lo que sigue coincidiendo se lee entero. Comparar el contenido
 * completo importa: aquí se borran archivos, y dos vídeos del mismo tamaño con
 * la misma cabecera no tienen por qué ser el mismo vídeo.
 */
object DuplicateFinder {

    /** Los duplicados pequeños no liberan nada y multiplican el trabajo. */
    const val DEFAULT_MIN_BYTES = 1024L * 1024

    private const val EDGE_SAMPLE_BYTES = 64 * 1024
    private const val STREAM_BUFFER = 64 * 1024

    suspend fun find(
        root: FsNode,
        minBytes: Long = DEFAULT_MIN_BYTES,
        onProgress: (DuplicateProgress) -> Unit = {},
    ): List<CleanItem> = withContext(Dispatchers.IO) {
        // Fase 1: agrupar por tamaño exacto.
        val bySize = HashMap<Long, MutableList<FsNode>>()
        val stack = ArrayList<FsNode>(256)
        stack.add(root)
        while (stack.isNotEmpty()) {
            coroutineContext.ensureActive()
            val node = stack.removeAt(stack.size - 1)
            val children = node.children
            if (children != null) {
                stack.addAll(children)
                continue
            }
            if (node.size < minBytes) continue
            bySize.getOrPut(node.size) { ArrayList(2) }.add(node)
        }

        val candidates = bySize.values.filter { it.size > 1 }
        val totalToHash = candidates.sumOf { it.size }
        if (totalToHash == 0) return@withContext emptyList()

        var hashed = 0
        onProgress(DuplicateProgress(0, totalToHash))

        val result = ArrayList<CleanItem>()

        for (sameSize in candidates) {
            coroutineContext.ensureActive()

            // Fase 2: firma de los extremos.
            val byEdges = HashMap<String, MutableList<FsNode>>()
            for (node in sameSize) {
                val signature = edgeSignature(node.file, node.size)
                hashed++
                if (hashed % 8 == 0) onProgress(DuplicateProgress(hashed, totalToHash))
                if (signature == null) continue
                byEdges.getOrPut(signature) { ArrayList(2) }.add(node)
            }
            onProgress(DuplicateProgress(hashed, totalToHash))

            // Fase 3: contenido completo, sólo sobre lo que sigue coincidiendo.
            for (sameEdges in byEdges.values) {
                if (sameEdges.size < 2) continue
                val byContent = HashMap<String, MutableList<FsNode>>()
                for (node in sameEdges) {
                    coroutineContext.ensureActive()
                    val digest = fullHash(node.file) ?: continue
                    byContent.getOrPut(digest) { ArrayList(2) }.add(node)
                }

                for (identical in byContent.values) {
                    if (identical.size < 2) continue
                    // La copia más antigua se queda; el resto se ofrece para
                    // borrar. Nunca se propone el grupo entero, así que marcar
                    // todo no puede dejarte sin el archivo.
                    val sorted = identical.sortedBy { it.lastModified }
                    val keep = sorted.first()
                    for (copy in sorted.drop(1)) {
                        result.add(CleanItem(copy, keep.path))
                    }
                }
            }
        }

        result.sortedByDescending { it.node.size }
    }

    /** Hash de los primeros y últimos 64 KB más el tamaño. */
    private fun edgeSignature(file: File, size: Long): String? = try {
        RandomAccessFile(file, "r").use { raf ->
            val digest = MessageDigest.getInstance("SHA-256")
            val sample = ByteArray(EDGE_SAMPLE_BYTES)

            val head = raf.read(sample, 0, minOf(EDGE_SAMPLE_BYTES.toLong(), size).toInt())
            if (head > 0) digest.update(sample, 0, head)

            if (size > EDGE_SAMPLE_BYTES) {
                raf.seek(size - EDGE_SAMPLE_BYTES)
                val tail = raf.read(sample, 0, EDGE_SAMPLE_BYTES)
                if (tail > 0) digest.update(sample, 0, tail)
            }

            digest.update(size.toString().toByteArray())
            digest.digest().toHex()
        }
    } catch (e: Exception) {
        null
    }

    private fun fullHash(file: File): String? = try {
        file.inputStream().use { stream ->
            val digest = MessageDigest.getInstance("SHA-256")
            val buffer = ByteArray(STREAM_BUFFER)
            while (true) {
                val read = stream.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
            digest.digest().toHex()
        }
    } catch (e: Exception) {
        null
    }

    private fun ByteArray.toHex(): String {
        val chars = CharArray(size * 2)
        val hex = "0123456789abcdef"
        for (i in indices) {
            val value = this[i].toInt() and 0xFF
            chars[i * 2] = hex[value ushr 4]
            chars[i * 2 + 1] = hex[value and 0x0F]
        }
        return String(chars)
    }
}
