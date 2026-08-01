package com.kikerv.dirspace.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kikerv.dirspace.R
import com.kikerv.dirspace.model.FsNode
import com.kikerv.dirspace.ui.MainViewModel
import com.kikerv.dirspace.ui.ScanUiState
import com.kikerv.dirspace.ui.Toast
import com.kikerv.dirspace.util.FileOps
import com.kikerv.dirspace.util.formatBytes

/**
 * Decide qué pantalla toca: permisos, elección de volumen, escaneo o el
 * explorador con sus pestañas.
 */
@Composable
fun DirSpaceRoot(
    viewModel: MainViewModel,
    onRequestStoragePermission: () -> Unit,
    onRequestUsageAccess: () -> Unit,
    onOpenAppInfo: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val hasPermission by viewModel.hasStoragePermission.collectAsStateWithLifecycle()
    val volumes by viewModel.volumes.collectAsStateWithLifecycle()
    val scanState by viewModel.scanState.collectAsStateWithLifecycle()
    val currentDir by viewModel.currentDir.collectAsStateWithLifecycle()
    val selected by viewModel.selected.collectAsStateWithLifecycle()
    val stats by viewModel.stats.collectAsStateWithLifecycle()
    val tab by viewModel.tab.collectAsStateWithLifecycle()
    val sortMode by viewModel.sortMode.collectAsStateWithLifecycle()
    val appsState by viewModel.appsState.collectAsStateWithLifecycle()
    val treeRevision by viewModel.treeRevision.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    var detailsNode by remember { mutableStateOf<FsNode?>(null) }

    LaunchedEffect(Unit) {
        viewModel.toasts.collect { toast ->
            val message = when (toast) {
                is Toast.Deleted -> context.getString(
                    if (toast.complete) R.string.deleted_ok else R.string.deleted_partial,
                    formatBytes(toast.bytesFreed),
                )
                Toast.DeleteFailed -> context.getString(R.string.deleted_fail)
                Toast.NoAppToOpen -> context.getString(R.string.no_app_to_open)
            }
            snackbarHostState.showSnackbar(message)
        }
    }

    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        when {
            !hasPermission -> PermissionScreen(onGrant = onRequestStoragePermission)

            scanState is ScanUiState.Running -> {
                val running = scanState as ScanUiState.Running
                ScanningScreen(
                    progress = running.progress,
                    onCancel = viewModel::cancelScan,
                )
            }

            scanState is ScanUiState.Failed -> {
                val failed = scanState as ScanUiState.Failed
                ErrorScreen(
                    message = failed.message,
                    onRetry = viewModel::backToVolumes,
                )
            }

            scanState is ScanUiState.Done && currentDir != null -> {
                val done = scanState as ScanUiState.Done
                val dir = currentDir!!

                // El botón "atrás" del sistema sube un nivel; en la raíz vuelve a
                // la lista de volúmenes en vez de cerrar la app de golpe.
                BackHandler(enabled = true) {
                    when {
                        detailsNode != null -> detailsNode = null
                        !viewModel.navigateUp() -> viewModel.backToVolumes()
                    }
                }

                ExplorerScreen(
                    root = done.root,
                    volume = done.volume,
                    currentDir = dir,
                    selected = selected,
                    stats = stats,
                    tab = tab,
                    sortMode = sortMode,
                    appsState = appsState,
                    treeRevision = treeRevision,
                    snackbarHostState = snackbarHostState,
                    onTabSelected = viewModel::selectTab,
                    onSortModeChange = viewModel::setSortMode,
                    onNavigate = viewModel::navigateTo,
                    onOpen = viewModel::open,
                    onDetails = { node ->
                        viewModel.select(node)
                        detailsNode = node
                    },
                    onUp = {
                        if (!viewModel.navigateUp()) viewModel.backToVolumes()
                    },
                    onRescan = viewModel::rescan,
                    onRequestUsageAccess = onRequestUsageAccess,
                    onOpenAppInfo = onOpenAppInfo,
                    onOpenFile = { node ->
                        if (!FileOps.open(context, node.file)) viewModel.emitNoAppToOpen()
                    },
                )
            }

            else -> {
                Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { padding ->
                    VolumeScreen(
                        volumes = volumes,
                        onSelect = viewModel::scan,
                        modifier = Modifier.padding(padding),
                    )
                }
            }
        }
    }

    val details = detailsNode
    if (details != null) {
        NodeDetailsSheet(
            node = details,
            onDismiss = { detailsNode = null },
            onOpen = { node ->
                detailsNode = null
                if (!FileOps.open(context, node.file)) viewModel.emitNoAppToOpen()
            },
            onShare = { node ->
                detailsNode = null
                FileOps.share(context, node.file)
            },
            onDelete = { node ->
                detailsNode = null
                viewModel.delete(node)
            },
            onReveal = { node ->
                detailsNode = null
                viewModel.reveal(node)
            },
        )
    }
}

@Composable
private fun ErrorScreen(message: String, onRetry: () -> Unit) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = stringResource(R.string.error_generic, message),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(20.dp))
            Button(onClick = onRetry) {
                Text(stringResource(R.string.perm_retry))
            }
        }
    }
}
