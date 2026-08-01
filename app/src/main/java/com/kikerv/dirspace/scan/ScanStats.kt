package com.kikerv.dirspace.scan

import com.kikerv.dirspace.model.FileCategory
import com.kikerv.dirspace.model.FsNode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.util.PriorityQueue
import kotlin.coroutines.coroutineContext

data class ExtensionStat(
    val extension: String,
    val category: FileCategory,
    val bytes: Long,
    val count: Int,
)

data class CategoryStat(
    val category: FileCategory,
    val bytes: Long,
    val count: Int,
)

data class ScanStats(
    val extensions: List<ExtensionStat>,
    val categories: List<CategoryStat>,
    val largestFiles: List<FsNode>,
) {
    companion object {
        const val LARGEST_FILES_LIMIT = 250

        val EMPTY = ScanStats(emptyList(), emptyList(), emptyList())

        /**
         * Recorre el árbol ya construido y agrega el reparto por extensión más el
         * top de archivos grandes. Se rehace tras cada borrado para que las
         * pestañas no queden desfasadas.
         */
        suspend fun build(root: FsNode): ScanStats = withContext(Dispatchers.Default) {
            val byExtension = HashMap<String, LongArray>(256)
            val byCategory = HashMap<FileCategory, LongArray>(16)
            // Cola de mínimos: la cima es el más pequeño del top actual, así que
            // basta compararlo para decidir si entra un archivo nuevo.
            val largest = PriorityQueue<FsNode>(LARGEST_FILES_LIMIT + 1) { a, b ->
                a.size.compareTo(b.size)
            }

            val stack = ArrayList<FsNode>(256)
            stack.add(root)
            var checked = 0
            while (stack.isNotEmpty()) {
                if ((++checked and 0x3FF) == 0) coroutineContext.ensureActive()
                val node = stack.removeAt(stack.size - 1)
                val children = node.children
                if (children != null) {
                    for (c in children) stack.add(c)
                    continue
                }
                if (node.isDirectory) continue

                val ext = node.extension
                val category = FileCategory.of(ext)
                val extKey = ext.ifEmpty { "" }
                val extAcc = byExtension.getOrPut(extKey) { LongArray(2) }
                extAcc[0] += node.size
                extAcc[1]++
                val catAcc = byCategory.getOrPut(category) { LongArray(2) }
                catAcc[0] += node.size
                catAcc[1]++

                if (largest.size < LARGEST_FILES_LIMIT) {
                    largest.add(node)
                } else if (node.size > largest.peek()!!.size) {
                    largest.poll()
                    largest.add(node)
                }
            }

            val extensions = byExtension.entries
                .map { (ext, acc) -> ExtensionStat(ext, FileCategory.of(ext), acc[0], acc[1].toInt()) }
                .sortedByDescending { it.bytes }

            val categories = byCategory.entries
                .map { (cat, acc) -> CategoryStat(cat, acc[0], acc[1].toInt()) }
                .sortedByDescending { it.bytes }

            val largestSorted = largest.sortedByDescending { it.size }

            ScanStats(extensions, categories, largestSorted)
        }
    }
}
