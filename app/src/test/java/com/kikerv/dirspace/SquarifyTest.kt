package com.kikerv.dirspace

import androidx.compose.ui.geometry.Rect
import com.kikerv.dirspace.model.FsNode
import com.kikerv.dirspace.ui.treemap.Squarify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class SquarifyTest {

    private fun tree(vararg sizes: Long): FsNode {
        val root = FsNode("root", null, true, "/root")
        val children = ArrayList<FsNode>()
        sizes.sortedDescending().forEachIndexed { index, size ->
            val child = FsNode("f$index", root, false)
            child.size = size
            child.fileCount = 1
            children.add(child)
        }
        root.children = children
        root.size = sizes.sum()
        root.fileCount = sizes.size
        return root
    }

    @Test
    fun `el area de cada bloque es proporcional a su tamano`() {
        val bounds = Rect(0f, 0f, 400f, 300f)
        val root = tree(500, 300, 150, 50)
        val layout = Squarify.layout(root, bounds)

        assertEquals(4, layout.topLevel.size)

        val totalArea = bounds.width * bounds.height
        for (tile in layout.topLevel) {
            val expected = totalArea * (tile.node.size.toFloat() / root.size)
            val actual = tile.rect.width * tile.rect.height
            assertTrue(
                "esperado ~$expected pero fue $actual",
                abs(expected - actual) / expected < 0.02f,
            )
        }
    }

    @Test
    fun `los bloques no se solapan ni se salen del area`() {
        val bounds = Rect(0f, 0f, 320f, 480f)
        val root = tree(900, 700, 400, 400, 200, 120, 80, 60, 40, 10)
        val layout = Squarify.layout(root, bounds)

        for (tile in layout.topLevel) {
            assertTrue(tile.rect.left >= bounds.left - 0.5f)
            assertTrue(tile.rect.top >= bounds.top - 0.5f)
            assertTrue(tile.rect.right <= bounds.right + 0.5f)
            assertTrue(tile.rect.bottom <= bounds.bottom + 0.5f)
        }

        for (i in layout.topLevel.indices) {
            for (j in i + 1 until layout.topLevel.size) {
                val a = layout.topLevel[i].rect
                val b = layout.topLevel[j].rect
                val overlapWidth = minOf(a.right, b.right) - maxOf(a.left, b.left)
                val overlapHeight = minOf(a.bottom, b.bottom) - maxOf(a.top, b.top)
                assertTrue(
                    "se solapan ${a} y ${b}",
                    overlapWidth <= 0.5f || overlapHeight <= 0.5f,
                )
            }
        }
    }

    @Test
    fun `una carpeta vacia no produce bloques`() {
        val root = FsNode("root", null, true, "/root")
        root.children = ArrayList()
        val layout = Squarify.layout(root, Rect(0f, 0f, 100f, 100f))
        assertTrue(layout.tiles.isEmpty())
    }
}
