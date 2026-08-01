package com.kikerv.dirspace.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kikerv.dirspace.R
import com.kikerv.dirspace.model.FsNode
import com.kikerv.dirspace.ui.components.CategoryDot
import com.kikerv.dirspace.util.formatBytes
import com.kikerv.dirspace.util.formatCount
import com.kikerv.dirspace.util.formatDate
import com.kikerv.dirspace.util.formatPercent

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun NodeDetailsSheet(
    node: FsNode,
    onDismiss: () -> Unit,
    onOpen: (FsNode) -> Unit,
    onShare: (FsNode) -> Unit,
    onDelete: (FsNode) -> Unit,
    onReveal: (FsNode) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var confirmingDelete by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CategoryDot(node.category, size = 14)
                Spacer(Modifier.padding(horizontal = 5.dp))
                Text(
                    text = node.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            Spacer(Modifier.height(6.dp))

            Text(
                text = node.path,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(16.dp))

            DetailLine(label = stringResource(R.string.sort_size), value = formatBytes(node.size))
            DetailLine(
                label = stringResource(R.string.detail_share),
                value = formatPercent(node.fractionOfParent()),
            )
            if (node.isDirectory) {
                DetailLine(
                    label = stringResource(R.string.detail_files),
                    value = formatCount(node.fileCount),
                )
            }
            DetailLine(
                label = stringResource(R.string.detail_modified),
                value = formatDate(node.lastModified),
            )

            Spacer(Modifier.height(20.dp))

            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!node.isDirectory) {
                    ActionButton(Icons.Filled.OpenInNew, stringResource(R.string.action_open)) {
                        onOpen(node)
                    }
                    ActionButton(Icons.Filled.Share, stringResource(R.string.action_share)) {
                        onShare(node)
                    }
                }
                ActionButton(Icons.Filled.FolderOpen, stringResource(R.string.action_locate)) {
                    onReveal(node)
                }
                ActionButton(
                    icon = Icons.Filled.Delete,
                    label = stringResource(R.string.action_delete),
                    tint = MaterialTheme.colorScheme.error,
                ) {
                    confirmingDelete = true
                }
            }
        }
    }

    if (confirmingDelete) {
        AlertDialog(
            onDismissRequest = { confirmingDelete = false },
            title = { Text(stringResource(R.string.delete_title)) },
            text = {
                Text(
                    if (node.isDirectory) {
                        stringResource(
                            R.string.delete_dir_msg,
                            node.name,
                            node.fileCount,
                            formatBytes(node.size),
                        )
                    } else {
                        stringResource(R.string.delete_file_msg, node.name, formatBytes(node.size))
                    }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmingDelete = false
                    onDelete(node)
                }) {
                    Text(
                        text = stringResource(R.string.confirm),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmingDelete = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

@Composable
private fun DetailLine(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun ActionButton(
    icon: ImageVector,
    label: String,
    tint: Color = MaterialTheme.colorScheme.primary,
    onClick: () -> Unit,
) {
    TextButton(onClick = onClick) {
        Icon(imageVector = icon, contentDescription = null, tint = tint)
        Spacer(Modifier.padding(horizontal = 3.dp))
        Text(text = label, color = tint)
    }
}
