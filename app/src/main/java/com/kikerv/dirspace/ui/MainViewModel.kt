package com.kikerv.dirspace.ui

import android.app.Application
import android.os.Build
import android.os.Environment
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kikerv.dirspace.apps.AppUsage
import com.kikerv.dirspace.apps.AppUsageRepository
import com.kikerv.dirspace.model.FsNode
import com.kikerv.dirspace.scan.ScanProgress
import com.kikerv.dirspace.scan.ScanStats
import com.kikerv.dirspace.scan.StorageScanner
import com.kikerv.dirspace.scan.StorageVolumeInfo
import com.kikerv.dirspace.scan.VolumeRepository
import com.kikerv.dirspace.util.DeleteResult
import com.kikerv.dirspace.util.FileOps
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class Tab { TREE, MAP, TYPES, LARGEST, APPS }

enum class SortMode { SIZE, NAME, DATE, COUNT }

sealed interface ScanUiState {
    data object Idle : ScanUiState
    data class Running(val progress: ScanProgress) : ScanUiState
    data class Done(val root: FsNode, val volume: StorageVolumeInfo) : ScanUiState
    data class Failed(val message: String) : ScanUiState
}

sealed interface AppsUiState {
    data object NeedsPermission : AppsUiState
    data object Loading : AppsUiState
    data class Ready(val apps: List<AppUsage>) : AppsUiState
}

/** Mensajes de un solo uso para la snackbar. */
sealed interface Toast {
    data class Deleted(val bytesFreed: Long, val complete: Boolean) : Toast
    data object DeleteFailed : Toast
    data object NoAppToOpen : Toast
}

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val volumeRepository = VolumeRepository(application)
    private val appUsageRepository = AppUsageRepository(application)

    private val _hasStoragePermission = MutableStateFlow(false)
    val hasStoragePermission = _hasStoragePermission.asStateFlow()

    private val _volumes = MutableStateFlow<List<StorageVolumeInfo>>(emptyList())
    val volumes = _volumes.asStateFlow()

    private val _scanState = MutableStateFlow<ScanUiState>(ScanUiState.Idle)
    val scanState = _scanState.asStateFlow()

    private val _stats = MutableStateFlow(ScanStats.EMPTY)
    val stats = _stats.asStateFlow()

    /** Carpeta que se está mostrando en las pestañas Carpetas y Mapa. */
    private val _currentDir = MutableStateFlow<FsNode?>(null)
    val currentDir = _currentDir.asStateFlow()

    private val _selected = MutableStateFlow<FsNode?>(null)
    val selected = _selected.asStateFlow()

    private val _tab = MutableStateFlow(Tab.TREE)
    val tab = _tab.asStateFlow()

    private val _sortMode = MutableStateFlow(SortMode.SIZE)
    val sortMode = _sortMode.asStateFlow()

    private val _appsState = MutableStateFlow<AppsUiState>(AppsUiState.NeedsPermission)
    val appsState = _appsState.asStateFlow()

    private val _toasts = MutableSharedFlow<Toast>(extraBufferCapacity = 4)
    val toasts = _toasts.asSharedFlow()

    /** Fuerza a las vistas a redibujarse cuando el árbol cambia en sitio (borrados). */
    private val _treeRevision = MutableStateFlow(0)
    val treeRevision = _treeRevision.asStateFlow()

    private var scanJob: Job? = null

    fun refreshPermission() {
        val granted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            val context = getApplication<Application>()
            context.checkSelfPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
        }
        _hasStoragePermission.value = granted
        if (granted && _volumes.value.isEmpty()) refreshVolumes()
    }

    fun refreshVolumes() {
        viewModelScope.launch {
            val found = withContext(Dispatchers.IO) { volumeRepository.volumes() }
            _volumes.value = found
        }
    }

    fun scan(volume: StorageVolumeInfo) {
        scanJob?.cancel()
        _stats.value = ScanStats.EMPTY
        _selected.value = null
        _currentDir.value = null
        _scanState.value = ScanUiState.Running(ScanProgress())

        scanJob = viewModelScope.launch {
            try {
                val root = StorageScanner.scan(volume.root, volume.label) { progress ->
                    _scanState.value = ScanUiState.Running(progress)
                }
                _scanState.value = ScanUiState.Done(root, volume)
                _currentDir.value = root
                _stats.value = ScanStats.build(root)
            } catch (e: CancellationException) {
                _scanState.value = ScanUiState.Idle
                throw e
            } catch (e: Exception) {
                _scanState.value = ScanUiState.Failed(e.message ?: e.javaClass.simpleName)
            }
        }
    }

    fun cancelScan() {
        scanJob?.cancel()
        scanJob = null
        _scanState.value = ScanUiState.Idle
    }

    fun rescan() {
        val done = _scanState.value as? ScanUiState.Done ?: return
        scan(done.volume)
    }

    fun backToVolumes() {
        scanJob?.cancel()
        scanJob = null
        _scanState.value = ScanUiState.Idle
        _currentDir.value = null
        _selected.value = null
        _stats.value = ScanStats.EMPTY
        refreshVolumes()
    }

    fun selectTab(tab: Tab) {
        _tab.value = tab
        if (tab == Tab.APPS) loadApps()
    }

    fun setSortMode(mode: SortMode) {
        _sortMode.value = mode
    }

    /** Entra en una carpeta; sobre un archivo sólo lo selecciona. */
    fun open(node: FsNode) {
        if (node.isDirectory && !node.children.isNullOrEmpty()) {
            _currentDir.value = node
            _selected.value = null
        } else {
            _selected.value = node
        }
    }

    fun select(node: FsNode?) {
        _selected.value = node
    }

    /** Muestra la carpeta que contiene [node] y lo deja resaltado. */
    fun reveal(node: FsNode) {
        val parent = node.parent ?: node
        _currentDir.value = parent
        _selected.value = node
        _tab.value = Tab.TREE
    }

    fun navigateTo(node: FsNode) {
        if (!node.isDirectory) return
        _currentDir.value = node
        _selected.value = null
    }

    /** Sube un nivel. Devuelve false si ya estamos en la raíz del volumen. */
    fun navigateUp(): Boolean {
        val current = _currentDir.value ?: return false
        val parent = current.parent ?: return false
        _currentDir.value = parent
        _selected.value = current
        return true
    }

    fun delete(node: FsNode) {
        viewModelScope.launch {
            val parent = node.parent
            val result: DeleteResult = FileOps.delete(node)
            if (result.bytesFreed == 0L && !result.complete) {
                _toasts.emit(Toast.DeleteFailed)
            } else {
                _toasts.emit(Toast.Deleted(result.bytesFreed, result.complete))
            }

            if (_selected.value === node && result.complete) _selected.value = null
            if (_currentDir.value === node && result.complete && parent != null) {
                _currentDir.value = parent
            }

            val root = (_scanState.value as? ScanUiState.Done)?.root
            if (root != null) _stats.value = ScanStats.build(root)
            _treeRevision.value++
        }
    }

    fun loadApps() {
        if (!appUsageRepository.hasUsageAccess()) {
            _appsState.value = AppsUiState.NeedsPermission
            return
        }
        if (_appsState.value is AppsUiState.Ready) return
        _appsState.value = AppsUiState.Loading
        viewModelScope.launch {
            val apps = appUsageRepository.load()
            _appsState.value = AppsUiState.Ready(apps)
        }
    }

    fun reloadApps() {
        _appsState.value = AppsUiState.NeedsPermission
        loadApps()
    }

    fun emitNoAppToOpen() {
        _toasts.tryEmit(Toast.NoAppToOpen)
    }
}
