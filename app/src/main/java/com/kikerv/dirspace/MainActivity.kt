package com.kikerv.dirspace

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.ui.Modifier
import com.kikerv.dirspace.ui.MainViewModel
import com.kikerv.dirspace.ui.screens.DirSpaceRoot
import com.kikerv.dirspace.ui.theme.DirSpaceTheme

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    private val legacyPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { viewModel.refreshPermission() }

    private val settingsLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { viewModel.refreshPermission() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            DirSpaceTheme {
                DirSpaceRoot(
                    viewModel = viewModel,
                    onRequestStoragePermission = ::requestStoragePermission,
                    onRequestUsageAccess = ::openUsageAccessSettings,
                    onOpenAppInfo = ::openAppInfo,
                    modifier = Modifier,
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshPermission()
    }

    /**
     * En Android 11+ el acceso completo se concede desde una pantalla de Ajustes,
     * no con un diálogo de permisos; en versiones anteriores basta el permiso normal.
     */
    private fun requestStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val scoped = Intent(
                Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                Uri.parse("package:$packageName"),
            )
            if (!launchSettings(scoped)) {
                launchSettings(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
            }
        } else {
            legacyPermissionLauncher.launch(android.Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }

    private fun openUsageAccessSettings() {
        launchSettings(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
    }

    private fun openAppInfo(packageName: String) {
        launchSettings(
            Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:$packageName"),
            )
        )
    }

    private fun launchSettings(intent: Intent): Boolean = try {
        settingsLauncher.launch(intent)
        true
    } catch (e: Exception) {
        false
    }
}
