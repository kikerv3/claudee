package com.kikerv.dirspace.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kikerv.dirspace.R
import com.kikerv.dirspace.clean.CleanCategory
import com.kikerv.dirspace.clean.CleanGroup
import com.kikerv.dirspace.clean.CleanItem
import com.kikerv.dirspace.clean.CleanRisk
import com.kikerv.dirspace.model.FsNode
import com.kikerv.dirspace.ui.CleanUiState
import com.kikerv.dirspace.ui.DuplicateUiState
import com.kikerv.dirspace.util.formatBytes

/** Cuántos elementos de un grupo se listan antes de resumir el resto. */
private const val ITEMS_SHOWN_PER_GROUP = 40

@Composable
fun CleanTab(
    state: CleanUiState,
    duplicateState: DuplicateUiState,
    selection: Set<FsNode>,
    onAnalyze: () -> Unit,
    onToggleItem: (FsNode) -> Unit,
    onToggleGroup: (CleanGroup, Boolean) -> Unit,
    onSelectSafeOnly: () -> Unit,
    onDelete: () -> Unit,
    onFindDuplicates: () -> Unit,
    onCancelDuplicates: () -> Unit,
    onOpenSystemStorage: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (state) {
        CleanUiState.Idle, CleanUiState.Analyzing -> {
            Column(
                modifier = modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                CircularProgressIndicator()
                Spacer(Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.clean_analyzing),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        is CleanUiState.Ready -> CleanReportView(
            state = state,
            duplicateState = duplicateState,
            selection = selection,
            onAnalyze = onAnalyze,
            onToggleItem = onToggleItem,
            onToggleGroup = onToggleGroup,
            onSelectSafeOnly = onSelectSafeOnly,
            onDelete = onDelete,
            onFindDuplicates = onFindDuplicates,
            onCancelDuplicates = onCancelDuplicates,
            onOpenSystemStorage = onOpenSystemStorage,
            modifier = modifier,
        )
    }
}

@Composable
private fun CleanReportView(
    state: CleanUiState.Ready,
    duplicateState: DuplicateUiState,
    selection: Set<FsNode>,
    onAnalyze: () -> Unit,
    onToggleItem: (FsNode) -> Unit,
    onToggleGroup: (CleanGroup, Boolean) -> Unit,
    onSelectSafeOnly: () -> Unit,
    onDelete: () -> Unit,
    onFindDuplicates: () -> Unit,
    onCancelDuplicates: () -> Unit,
    onOpenSystemStorage: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val report = state.report
    var confirming by remember { mutableStateOf(false) }
    var expandedGroups by remember { mutableStateOf(setOf<String>()) }

    val selectedBytes = remember(selection, report) {
        selection.sumOf { it.size }
    }
    // Los duplicados se sacan de la lista general porque tienen su propia
    // tarjeta con el botón de búsqueda; si no, saldrían dos veces.
    val regularGroups = report.groups.filterNot { it.category == CleanCategory.DUPLICATES }
    val duplicateGroup = report.groups.firstOrNull { it.category == CleanCategory.DUPLICATES }

    Column(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item(key = "@header") {
                CleanHeader(
                    totalBytes = report.totalBytes,
                    onQuickClean = {
                        onSelectSafeOnly()
                        confirming = true
                    },
                    onAnalyze = onAnalyze,
                )
            }

            if (report.appDataHidden) {
                item(key = "@appdata") {
                    AppDataNotice(onOpenSystemStorage = onOpenSystemStorage)
                }
            }

            if (report.groups.isEmpty()) {
                item(key = "@empty") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = stringResource(R.string.clean_nothing),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = stringResource(R.string.clean_nothing_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }

            items(regularGroups, key = { it.category.name }) { group ->
                GroupCard(
                    group = group,
                    selection = selection,
                    expanded = group.category.name in expandedGroups,
                    onExpandToggle = {
                        val key = group.category.name
                        expandedGroups = if (key in expandedGroups) {
                            expandedGroups - key
                        } else {
                            expandedGroups + key
                        }
                    },
                    onToggleItem = onToggleItem,
                    onToggleGroup = onToggleGroup,
                )
            }

            item(key = "@duplicates") {
                DuplicatesCard(
                    state = duplicateState,
                    alreadyListed = duplicateGroup != null,
                    onFind = onFindDuplicates,
                    onCancel = onCancelDuplicates,
                )
            }

            if (duplicateGroup != null) {
                item(key = "@duplicate-group") {
                    GroupCard(
                        group = duplicateGroup,
                        selection = selection,
                        expanded = CleanCategory.DUPLICATES.name in expandedGroups,
                        onExpandToggle = {
                            val key = CleanCategory.DUPLICATES.name
                            expandedGroups = if (key in expandedGroups) {
                                expandedGroups - key
                            } else {
                                expandedGroups + key
                            }
                        },
                        onToggleItem = onToggleItem,
                        onToggleGroup = onToggleGroup,
                    )
                }
            }
        }

        if (selection.isNotEmpty()) {
            DeleteBar(
                count = selection.size,
                bytes = selectedBytes,
                onDelete = { confirming = true },
            )
        }
    }

    if (confirming) {
        AlertDialog(
            onDismissRequest = { confirming = false },
            title = { Text(stringResource(R.string.clean_confirm_title, selection.size)) },
            text = {
                Text(stringResource(R.string.clean_confirm_msg, formatBytes(selectedBytes)))
            },
            confirmButton = {
                TextButton(onClick = {
                    confirming = false
                    onDelete()
                }) {
                    Text(
                        text = stringResource(R.string.confirm),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { confirming = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

@Composable
private fun CleanHeader(
    totalBytes: Long,
    onQuickClean: () -> Unit,
    onAnalyze: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.clean_total_found, formatBytes(totalBytes)),
                style = MaterialTheme.typography.titleLarge,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.clean_quick_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onQuickClean) {
                    Icon(Icons.Filled.CleaningServices, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.clean_quick))
                }
                OutlinedButton(onClick = onAnalyze) {
                    Text(stringResource(R.string.clean_reanalyze))
                }
            }
        }
    }
}

@Composable
private fun AppDataNotice(onOpenSystemStorage: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(14.dp)) {
            Icon(
                imageVector = Icons.Filled.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    text = stringResource(R.string.clean_appdata_hidden),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(6.dp))
                TextButton(
                    onClick = onOpenSystemStorage,
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                ) {
                    Text(stringResource(R.string.clean_open_system_storage))
                }
            }
        }
    }
}

@Composable
private fun GroupCard(
    group: CleanGroup,
    selection: Set<FsNode>,
    expanded: Boolean,
    onExpandToggle: () -> Unit,
    onToggleItem: (FsNode) -> Unit,
    onToggleGroup: (CleanGroup, Boolean) -> Unit,
) {
    val selectedCount = remember(selection, group) {
        group.items.count { it.node in selection }
    }
    val allSelected = selectedCount == group.items.size && group.items.isNotEmpty()
    val accent = Color(group.category.argb)

    Card(modifier = Modifier.fillMaxWidth()) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onExpandToggle)
                    .padding(start = 8.dp, end = 12.dp, top = 10.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(
                    checked = allSelected,
                    onCheckedChange = { onToggleGroup(group, it) },
                )

                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .size(10.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(accent),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = stringResource(group.category.labelRes),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = stringResource(
                            R.string.clean_group_count,
                            group.items.size,
                            formatBytes(group.bytes),
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Icon(
                    imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            AnimatedVisibility(visible = expanded) {
                Column(Modifier.padding(start = 16.dp, end = 12.dp, bottom = 12.dp)) {
                    Text(
                        text = stringResource(group.category.descriptionRes),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    RiskChip(group.category.risk)
                    Spacer(Modifier.height(8.dp))

                    group.items.take(ITEMS_SHOWN_PER_GROUP).forEach { item ->
                        ItemRow(
                            item = item,
                            category = group.category,
                            checked = item.node in selection,
                            onToggle = { onToggleItem(item.node) },
                        )
                    }

                    val hidden = group.items.size - ITEMS_SHOWN_PER_GROUP
                    if (hidden > 0) {
                        Text(
                            text = stringResource(R.string.clean_more_items, hidden),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 6.dp, start = 4.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RiskChip(risk: CleanRisk) {
    val label = when (risk) {
        CleanRisk.SAFE -> stringResource(R.string.clean_risk_safe)
        CleanRisk.REVIEW -> stringResource(R.string.clean_risk_review)
    }
    val color = when (risk) {
        CleanRisk.SAFE -> MaterialTheme.colorScheme.primary
        CleanRisk.REVIEW -> MaterialTheme.colorScheme.error
    }
    Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        color = color,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.14f))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

@Composable
private fun ItemRow(
    item: CleanItem,
    category: CleanCategory,
    checked: Boolean,
    onToggle: () -> Unit,
) {
    // El detalle sale del análisis en crudo (ruta, paquete o copia conservada);
    // darle sentido es cosa de la UI, que es la que tiene los textos.
    val detail = when (category) {
        CleanCategory.LEFTOVERS -> stringResource(R.string.clean_leftover_of, item.detail)
        CleanCategory.DUPLICATES -> stringResource(R.string.clean_keeps, item.detail)
        else -> item.detail
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = { onToggle() })
        Column(Modifier.weight(1f)) {
            Text(
                text = item.node.name,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = detail,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(
            text = formatBytes(item.node.size),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun DuplicatesCard(
    state: DuplicateUiState,
    alreadyListed: Boolean,
    onFind: () -> Unit,
    onCancel: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.clean_cat_dup),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(4.dp))

            when (state) {
                DuplicateUiState.NotRun -> {
                    Text(
                        text = stringResource(R.string.clean_duplicates_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedButton(onClick = onFind) {
                        Text(stringResource(R.string.clean_find_duplicates))
                    }
                }

                is DuplicateUiState.Running -> {
                    val progress = state.progress
                    Text(
                        text = stringResource(
                            R.string.clean_comparing,
                            progress.done,
                            progress.total,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    if (progress.total > 0) {
                        LinearProgressIndicator(
                            progress = {
                                (progress.done.toFloat() / progress.total).coerceIn(0f, 1f)
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = onCancel) {
                        Text(stringResource(R.string.scan_cancel))
                    }
                }

                DuplicateUiState.Done -> {
                    if (!alreadyListed) {
                        Text(
                            text = stringResource(R.string.clean_no_duplicates),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    OutlinedButton(onClick = onFind) {
                        Text(stringResource(R.string.clean_reanalyze))
                    }
                }
            }
        }
    }
}

@Composable
private fun DeleteBar(count: Int, bytes: Long, onDelete: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.clean_selected_count, count),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Button(onClick = onDelete) {
            Text(stringResource(R.string.clean_free, formatBytes(bytes)))
        }
    }
}
