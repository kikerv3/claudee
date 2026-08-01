package com.kikerv.dirspace.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kikerv.dirspace.R
import com.kikerv.dirspace.scan.CategoryStat
import com.kikerv.dirspace.scan.ExtensionStat
import com.kikerv.dirspace.ui.components.CategoryDot
import com.kikerv.dirspace.ui.components.UsageBar
import com.kikerv.dirspace.util.formatBytes
import com.kikerv.dirspace.util.formatCount
import com.kikerv.dirspace.util.formatPercent

@Composable
fun TypesTab(
    categories: List<CategoryStat>,
    extensions: List<ExtensionStat>,
    totalBytes: Long,
    modifier: Modifier = Modifier,
) {
    if (extensions.isEmpty()) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = stringResource(R.string.empty_folder),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp),
    ) {
        item(key = "@categories") {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text(
                    text = stringResource(R.string.types_title),
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(Modifier.height(10.dp))
                StackedCategoryBar(categories = categories, totalBytes = totalBytes)
                Spacer(Modifier.height(12.dp))
                categories.forEach { stat ->
                    CategoryLine(stat = stat, totalBytes = totalBytes)
                }
            }
            HorizontalDivider()
        }

        items(extensions, key = { "e:" + it.extension }) { stat ->
            ExtensionRow(stat = stat, totalBytes = totalBytes)
        }
    }
}

/** Una sola barra con el reparto de todas las categorías. */
@Composable
private fun StackedCategoryBar(categories: List<CategoryStat>, totalBytes: Long) {
    if (totalBytes <= 0L) return
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(12.dp)
            .clip(RoundedCornerShape(6.dp)),
    ) {
        categories.forEach { stat ->
            val weight = (stat.bytes.toDouble() / totalBytes).toFloat()
            // Por debajo de medio por mil la franja sería invisible y además
            // Compose no admite pesos de cero.
            if (weight <= 0.0005f) return@forEach
            Box(
                modifier = Modifier
                    .weight(weight)
                    .fillMaxHeight()
                    .background(Color(stat.category.argb)),
            )
        }
    }
}

@Composable
private fun CategoryLine(stat: CategoryStat, totalBytes: Long) {
    val fraction = if (totalBytes > 0) (stat.bytes.toDouble() / totalBytes).toFloat() else 0f
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CategoryDot(stat.category)
        Spacer(Modifier.width(8.dp))
        Text(
            text = stringResource(stat.category.labelRes),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = formatBytes(stat.bytes),
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = formatPercent(fraction),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(48.dp),
        )
    }
}

@Composable
private fun ExtensionRow(stat: ExtensionStat, totalBytes: Long) {
    val fraction = if (totalBytes > 0) (stat.bytes.toDouble() / totalBytes).toFloat() else 0f
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CategoryDot(stat.category, size = 12)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = if (stat.extension.isEmpty()) {
                    stringResource(R.string.cat_other)
                } else {
                    "." + stat.extension
                },
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.height(4.dp))
            UsageBar(
                fraction = fraction,
                color = Color(stat.category.argb),
                modifier = Modifier.fillMaxWidth(),
                height = 5,
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = formatBytes(stat.bytes),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = formatCount(stat.count),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
