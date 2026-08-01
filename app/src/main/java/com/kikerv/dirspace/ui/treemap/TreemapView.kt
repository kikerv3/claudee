package com.kikerv.dirspace.ui.treemap

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.kikerv.dirspace.model.FsNode
import com.kikerv.dirspace.util.formatBytes

private class RenderedMap(
    val bitmap: ImageBitmap,
    val layout: TreemapLayout,
)

/**
 * Mapa de bloques del contenido de [root]: cada archivo es un rectángulo con
 * área proporcional a su tamaño y color según su tipo, igual que el treemap de
 * WinDirStat.
 */
@Composable
fun TreemapView(
    root: FsNode,
    selected: FsNode?,
    onTap: (FsNode) -> Unit,
    onLongPress: (FsNode) -> Unit,
    modifier: Modifier = Modifier,
) {
    val background = MaterialTheme.colorScheme.surfaceVariant

    BoxWithConstraints(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(background),
    ) {
        val density = LocalDensity.current
        val widthPx = with(density) { maxWidth.toPx().toInt() }
        val heightPx = with(density) { maxHeight.toPx().toInt() }

        var rendered by remember { mutableStateOf<RenderedMap?>(null) }

        LaunchedEffect(root, root.size, widthPx, heightPx, background) {
            rendered = null
            if (widthPx <= 0 || heightPx <= 0) return@LaunchedEffect
            val layout = Squarify.layout(
                root = root,
                bounds = Rect(0f, 0f, widthPx.toFloat(), heightPx.toFloat()),
            )
            val bitmap = TreemapRenderer.render(layout, widthPx, heightPx, background)
            if (bitmap != null) rendered = RenderedMap(bitmap, layout)
        }

        val map = rendered
        if (map == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@BoxWithConstraints
        }

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(map) {
                    detectTapGestures(
                        onTap = { offset -> map.layout.hit(offset)?.let(onTap) },
                        onLongPress = { offset -> map.layout.hit(offset)?.let(onLongPress) },
                    )
                },
        ) {
            drawImage(map.bitmap)

            val selectedRect = selected?.let { map.layout.rectOf(it) }
            if (selectedRect != null) {
                drawRect(
                    color = Color.Black.copy(alpha = 0.9f),
                    topLeft = Offset(selectedRect.left, selectedRect.top),
                    size = Size(selectedRect.width, selectedRect.height),
                    style = Stroke(width = 5f),
                )
                drawRect(
                    color = Color.White,
                    topLeft = Offset(selectedRect.left, selectedRect.top),
                    size = Size(selectedRect.width, selectedRect.height),
                    style = Stroke(width = 2f),
                )
            }
        }

        // Etiquetas sólo en los bloques de primer nivel con hueco suficiente:
        // más texto que eso convierte el mapa en ruido ilegible.
        for (tile in map.layout.topLevel) {
            if (tile.rect.width < 110f || tile.rect.height < 46f) continue
            Column(
                modifier = Modifier
                    .offset { IntOffset(tile.rect.left.toInt(), tile.rect.top.toInt()) }
                    .width(with(density) { tile.rect.width.toDp() })
                    .padding(horizontal = 6.dp, vertical = 4.dp),
            ) {
                Text(
                    text = tile.node.name,
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = formatBytes(tile.node.size),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.9f),
                    maxLines = 1,
                )
            }
        }
    }
}

/** Devuelve la hoja que contiene el punto tocado. */
private fun TreemapLayout.hit(offset: Offset): FsNode? {
    var best: TreemapTile? = null
    for (tile in tiles) {
        if (!tile.leaf) continue
        if (!tile.rect.contains(offset)) continue
        val current = best
        // Las hojas no se solapan, pero si el redondeo hiciera coincidir dos
        // bordes gana la más pequeña, que es la que el usuario apuntaba.
        if (current == null || tile.rect.area < current.rect.area) best = tile
    }
    return best?.node
}

private fun TreemapLayout.rectOf(node: FsNode): Rect? {
    for (tile in tiles) if (tile.node === node) return tile.rect
    return null
}

private val Rect.area: Float get() = width * height
