package com.kikerv.dirspace.ui.treemap

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Dibuja el mapa completo sobre un [ImageBitmap] una sola vez.
 *
 * Pintar decenas de miles de rectángulos en cada frame haría inusable el
 * scroll y el zoom, así que el mapa se rasteriza fuera del hilo principal y
 * después sólo se dibuja esa imagen más el recuadro de selección.
 */
object TreemapRenderer {

    /** Sombreado tipo "cushion": los bloques parecen abombados y se separan solos. */
    private const val HIGHLIGHT_ALPHA = 0.26f
    private const val SHADOW_ALPHA = 0.22f

    suspend fun render(
        layout: TreemapLayout,
        widthPx: Int,
        heightPx: Int,
        background: Color,
    ): ImageBitmap? = withContext(Dispatchers.Default) {
        if (widthPx <= 0 || heightPx <= 0) return@withContext null

        val bitmap = ImageBitmap(widthPx, heightPx)
        val canvas = Canvas(bitmap)
        val size = Size(widthPx.toFloat(), heightPx.toFloat())

        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, canvas, size) {
            drawRect(color = background, size = size)

            for (tile in layout.tiles) {
                if (!tile.leaf) continue
                val rect = tile.rect
                val w = rect.width
                val h = rect.height
                if (w <= 0f || h <= 0f) continue

                val base = Color(tile.node.category.argb)
                val topLeft = Offset(rect.left, rect.top)
                val tileSize = Size(w, h)

                drawRect(color = base, topLeft = topLeft, size = tileSize)

                if (h >= 3f) {
                    drawRect(
                        brush = Brush.verticalGradient(
                            0f to Color.White.copy(alpha = HIGHLIGHT_ALPHA),
                            0.45f to Color.Transparent,
                            1f to Color.Black.copy(alpha = SHADOW_ALPHA),
                            startY = rect.top,
                            endY = rect.bottom,
                        ),
                        topLeft = topLeft,
                        size = tileSize,
                    )
                }
                if (w >= 8f && h >= 8f) {
                    drawRect(
                        brush = Brush.horizontalGradient(
                            0f to Color.White.copy(alpha = HIGHLIGHT_ALPHA * 0.7f),
                            0.5f to Color.Transparent,
                            1f to Color.Black.copy(alpha = SHADOW_ALPHA * 0.7f),
                            startX = rect.left,
                            endX = rect.right,
                        ),
                        topLeft = topLeft,
                        size = tileSize,
                    )
                }
            }

            // Bordes de las carpetas de primer nivel: dan la lectura de "grupos"
            // que hace entender el mapa de un vistazo.
            for (tile in layout.topLevel) {
                val rect = tile.rect
                if (rect.width < 4f || rect.height < 4f) continue
                drawRect(
                    color = Color.Black.copy(alpha = 0.55f),
                    topLeft = Offset(rect.left, rect.top),
                    size = Size(rect.width, rect.height),
                    style = Stroke(width = 1.5f),
                )
            }
        }

        bitmap
    }
}
