package com.kikerv.dirspace.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kikerv.dirspace.R
import com.kikerv.dirspace.model.FsNode
import com.kikerv.dirspace.scan.ScanStats
import com.kikerv.dirspace.scan.StorageVolumeInfo
import com.kikerv.dirspace.ui.AppsUiState
import com.kikerv.dirspace.ui.SortMode
import com.kikerv.dirspace.ui.Tab
import com.kikerv.dirspace.ui.components.Breadcrumb
import com.kikerv.dirspace.ui.components.CategoryLegend
import com.kikerv.dirspace.ui.components.DirSummary
import com.kikerv.dirspace.ui.treemap.TreemapView

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExplorerScreen(
    root: FsNode,
    volume: StorageVolumeInfo,
    currentDir: FsNode,
    selected: FsNode?,
    stats: ScanStats,
    tab: Tab,
    sortMode: SortMode,
    appsState: AppsUiState,
    treeRevision: Int,
    snackbarHostState: SnackbarHostState,
    onTabSelected: (Tab) -> Unit,
    onSortModeChange: (SortMode) -> Unit,
    onNavigate: (FsNode) -> Unit,
    onOpen: (FsNode) -> Unit,
    onDetails: (FsNode) -> Unit,
    onUp: () -> Unit,
    onRescan: () -> Unit,
    onRequestUsageAccess: () -> Unit,
    onOpenAppInfo: (String) -> Unit,
    onOpenFile: (FsNode) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = currentDir.name.ifEmpty { volume.label },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onUp) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onRescan) {
                        Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = stringResource(R.string.rescan),
                        )
                    }
                },
            )
        },
        bottomBar = {
            NavigationBar {
                TabSpec.ALL.forEach { spec ->
                    NavigationBarItem(
                        selected = tab == spec.tab,
                        onClick = { onTabSelected(spec.tab) },
                        icon = { Icon(spec.icon, contentDescription = null) },
                        label = { Text(stringResource(spec.labelRes)) },
                    )
                }
            }
        },
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            val showsCurrentDir = tab == Tab.TREE || tab == Tab.MAP
            if (showsCurrentDir) {
                Breadcrumb(node = currentDir, onNavigate = onNavigate)
                DirSummary(node = currentDir, totalBytes = root.size)
            }

            when (tab) {
                Tab.TREE -> {
                    // `treeRevision` cambia tras cada borrado: el árbol se muta en
                    // sitio y sin esta clave la lista se quedaría con datos viejos.
                    val children = remember(currentDir, sortMode, treeRevision) {
                        sortedChildrenOf(currentDir, sortMode)
                    }
                    TreeTab(
                        children = children,
                        selected = selected,
                        sortMode = sortMode,
                        onSortModeChange = onSortModeChange,
                        onOpen = onOpen,
                        onDetails = onDetails,
                        modifier = Modifier
                            .weight(1f)
                            .padding(top = 8.dp),
                    )
                }

                Tab.MAP -> {
                    Column(Modifier.weight(1f)) {
                        TreemapView(
                            root = currentDir,
                            selected = selected,
                            onTap = onDetails,
                            onLongPress = onOpen,
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                        )
                        CategoryLegend(
                            categories = stats.categories.map { it.category },
                            labelOf = { stringResource(it.labelRes) },
                        )
                    }
                }

                Tab.TYPES -> TypesTab(
                    categories = stats.categories,
                    extensions = stats.extensions,
                    totalBytes = root.size,
                    modifier = Modifier.weight(1f),
                )

                Tab.LARGEST -> LargestTab(
                    files = stats.largestFiles,
                    onOpen = onOpenFile,
                    onDetails = onDetails,
                    modifier = Modifier.weight(1f),
                )

                Tab.APPS -> AppsTab(
                    state = appsState,
                    onRequestUsageAccess = onRequestUsageAccess,
                    onOpenAppInfo = onOpenAppInfo,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

private fun sortedChildrenOf(dir: FsNode, mode: SortMode): List<FsNode> {
    val children = dir.children ?: return emptyList()
    val comparator: Comparator<FsNode> = when (mode) {
        SortMode.SIZE -> compareByDescending { it.size }
        SortMode.NAME -> compareBy(String.CASE_INSENSITIVE_ORDER) { it.name }
        SortMode.DATE -> compareByDescending { it.lastModified }
        SortMode.COUNT -> compareByDescending { it.fileCount }
    }
    return children.sortedWith(comparator)
}

private class TabSpec(
    val tab: Tab,
    val icon: ImageVector,
    val labelRes: Int,
) {
    companion object {
        val ALL = listOf(
            TabSpec(Tab.TREE, Icons.AutoMirrored.Filled.List, R.string.tab_tree),
            TabSpec(Tab.MAP, Icons.Filled.GridView, R.string.tab_map),
            TabSpec(Tab.TYPES, Icons.Filled.Category, R.string.tab_types),
            TabSpec(Tab.LARGEST, Icons.Filled.Straighten, R.string.tab_big),
            TabSpec(Tab.APPS, Icons.Filled.Android, R.string.tab_apps),
        )
    }
}
