package com.kikerv.dirspace.ui.treemap

import androidx.compose.ui.geometry.Rect
import com.kikerv.dirspace.model.FsNode

/**
 * Un rectángulo ya colocado en el mapa.
 *
 * @param leaf true si no se sigue subdividiendo (archivo, o carpeta demasiado
 *   pequeña o profunda como para seguir bajando).
 */
class TreemapTile(
    val node: FsNode,
    val rect: Rect,
    val depth: Int,
    val leaf: Boolean,
)

class TreemapLayout(
    val tiles: List<TreemapTile>,
    /** Rectángulos de los hijos directos del nodo mostrado, para etiquetas y bordes. */
    val topLevel: List<TreemapTile>,
)

/**
 * Treemap "squarified" (Bruls, Huizing & van Wijk, 2000): en vez de partir el
 * espacio en tiras, agrupa elementos en filas que mantengan los rectángulos lo
 * más cuadrados posible, que es lo que hace legible el mapa de WinDirStat.
 */
object Squarify {

    /** Por debajo de este lado en píxeles no merece la pena seguir bajando. */
    private const val MIN_SUBDIVIDE_PX = 22f

    /** Rectángulos más finos que esto no se dibujan: no se distinguirían. */
    private const val MIN_TILE_PX = 1.1f

    /** Tope de rectángulos para que el render no se dispare en volúmenes enormes. */
    private const val MAX_TILES = 20_000

    fun layout(
        root: FsNode,
        bounds: Rect,
        maxDepth: Int = 8,
    ): TreemapLayout {
        val tiles = ArrayList<TreemapTile>(2048)
        val topLevel = ArrayList<TreemapTile>(32)
        if (bounds.width <= 0f || bounds.height <= 0f || root.size <= 0L) {
            return TreemapLayout(tiles, topLevel)
        }

        // Recorrido iterativo por niveles: cada entrada es "subdivide este nodo
        // dentro de este rectángulo".
        val pending = ArrayList<Triple<FsNode, Rect, Int>>(256)
        pending.add(Triple(root, bounds, 0))

        while (pending.isNotEmpty()) {
            if (tiles.size >= MAX_TILES) break
            val (node, rect, depth) = pending.removeAt(pending.size - 1)
            val children = node.children
            if (children.isNullOrEmpty()) continue

            val placed = squarify(children, rect)
            for ((child, childRect) in placed) {
                if (childRect.width < MIN_TILE_PX || childRect.height < MIN_TILE_PX) continue

                val canSubdivide = child.isDirectory &&
                    depth + 1 < maxDepth &&
                    !child.children.isNullOrEmpty() &&
                    childRect.width >= MIN_SUBDIVIDE_PX &&
                    childRect.height >= MIN_SUBDIVIDE_PX

                val tile = TreemapTile(child, childRect, depth + 1, leaf = !canSubdivide)
                tiles.add(tile)
                if (depth == 0) topLevel.add(tile)

                if (canSubdivide) {
                    // Se deja un píxel de margen para que se vea la separación
                    // entre carpetas, como el borde de WinDirStat.
                    val inner = shrink(childRect, 1f)
                    if (inner.width > MIN_TILE_PX && inner.height > MIN_TILE_PX) {
                        pending.add(Triple(child, inner, depth + 1))
                    }
                }
            }
        }

        return TreemapLayout(tiles, topLevel)
    }

    /** Coloca los hijos dentro de [bounds] proporcionalmente a su tamaño. */
    private fun squarify(children: List<FsNode>, bounds: Rect): List<Pair<FsNode, Rect>> {
        val out = ArrayList<Pair<FsNode, Rect>>(children.size)

        var total = 0.0
        for (c in children) if (c.size > 0L) total += c.size.toDouble()
        if (total <= 0.0) return out

        val scale = (bounds.width.toDouble() * bounds.height.toDouble()) / total

        // Ya vienen ordenados de mayor a menor por el escáner; el algoritmo lo
        // exige, así que se comprueba en vez de reordenar en caliente.
        val items = ArrayList<Pair<FsNode, Double>>(children.size)
        for (c in children) {
            if (c.size <= 0L) continue
            items.add(c to c.size.toDouble() * scale)
        }
        if (items.isEmpty()) return out
        if (!isDescending(items)) items.sortByDescending { it.second }

        var x = bounds.left.toDouble()
        var y = bounds.top.toDouble()
        var width = bounds.width.toDouble()
        var height = bounds.height.toDouble()

        var index = 0
        val row = ArrayList<Pair<FsNode, Double>>(16)

        while (index < items.size && width > 0.0 && height > 0.0) {
            val side = minOf(width, height)
            row.clear()
            var rowSum = 0.0
            var rowMin = Double.MAX_VALUE
            var rowMax = 0.0

            while (index < items.size) {
                val (node, area) = items[index]
                if (area <= 0.0) {
                    index++
                    continue
                }
                val newSum = rowSum + area
                val newMin = minOf(rowMin, area)
                val newMax = maxOf(rowMax, area)
                val betterWithIt = row.isEmpty() ||
                    worst(side, newSum, newMin, newMax) <= worst(side, rowSum, rowMin, rowMax)
                if (!betterWithIt) break
                row.add(node to area)
                rowSum = newSum
                rowMin = newMin
                rowMax = newMax
                index++
            }

            if (row.isEmpty()) break

            if (width >= height) {
                val rowWidth = rowSum / height
                var cursorY = y
                for ((node, area) in row) {
                    val tileHeight = area / rowWidth
                    out.add(node to rectOf(x, cursorY, x + rowWidth, cursorY + tileHeight))
                    cursorY += tileHeight
                }
                x += rowWidth
                width -= rowWidth
            } else {
                val rowHeight = rowSum / width
                var cursorX = x
                for ((node, area) in row) {
                    val tileWidth = area / rowHeight
                    out.add(node to rectOf(cursorX, y, cursorX + tileWidth, y + rowHeight))
                    cursorX += tileWidth
                }
                y += rowHeight
                height -= rowHeight
            }
        }

        return out
    }

    /**
     * Peor relación de aspecto de una fila. El algoritmo añade elementos a la
     * fila mientras este valor no empeore.
     */
    private fun worst(side: Double, sum: Double, min: Double, max: Double): Double {
        if (sum <= 0.0 || min <= 0.0 || side <= 0.0) return Double.MAX_VALUE
        val sideSq = side * side
        val sumSq = sum * sum
        return maxOf(sideSq * max / sumSq, sumSq / (sideSq * min))
    }

    private fun isDescending(items: List<Pair<FsNode, Double>>): Boolean {
        for (i in 1 until items.size) {
            if (items[i - 1].second < items[i].second) return false
        }
        return true
    }

    private fun rectOf(left: Double, top: Double, right: Double, bottom: Double): Rect =
        Rect(left.toFloat(), top.toFloat(), right.toFloat(), bottom.toFloat())

    private fun shrink(rect: Rect, by: Float): Rect = Rect(
        rect.left + by,
        rect.top + by,
        (rect.right - by).coerceAtLeast(rect.left + by),
        (rect.bottom - by).coerceAtLeast(rect.top + by),
    )
}
