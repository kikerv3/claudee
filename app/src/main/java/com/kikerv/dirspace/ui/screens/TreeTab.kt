package com.kikerv.dirspace.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.kikerv.dirspace.R
import com.kikerv.dirspace.model.FsNode
import com.kikerv.dirspace.ui.SortMode
import com.kikerv.dirspace.ui.components.NodeRow

@Composable
fun TreeTab(
    children: List<FsNode>,
    selected: FsNode?,
    sortMode: SortMode,
    onSortModeChange: (SortMode) -> Unit,
    onOpen: (FsNode) -> Unit,
    onDetails: (FsNode) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (children.isEmpty()) {
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
        item(key = "@sort") {
            SortRow(sortMode = sortMode, onSortModeChange = onSortModeChange)
        }
        // El nombre es único dentro de una carpeta, así que basta como clave.
        items(children, key = { "n:" + it.name }) { child ->
            NodeRow(
                node = child,
                fractionOfParent = child.fractionOfParent(),
                selected = child === selected,
                onClick = { onOpen(child) },
                onLongClick = { onDetails(child) },
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 1.dp),
            )
        }
    }
}

@Composable
private fun SortRow(
    sortMode: SortMode,
    onSortModeChange: (SortMode) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SortMode.entries.forEach { mode ->
                FilterChip(
                    selected = mode == sortMode,
                    onClick = { onSortModeChange(mode) },
                    label = { Text(stringResource(mode.labelRes())) },
                )
            }
        }
    }
}

private fun SortMode.labelRes(): Int = when (this) {
    SortMode.SIZE -> R.string.sort_size
    SortMode.NAME -> R.string.sort_name
    SortMode.DATE -> R.string.sort_date
    SortMode.COUNT -> R.string.sort_count
}
