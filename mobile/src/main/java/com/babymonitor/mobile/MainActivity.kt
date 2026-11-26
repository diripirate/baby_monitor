package com.babymonitor.mobile

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.babymonitor.core.model.DeviceRole
import com.babymonitor.core.model.MonitoringType
import com.babymonitor.mobile.service.BabyMonitorMobileService
import com.babymonitor.mobile.ui.BabyScreen
import com.babymonitor.mobile.ui.ParentScreen
import com.babymonitor.mobile.ui.RoleSelectionScreen

class MainActivity : ComponentActivity() {

    private var pendingRole: DeviceRole? = null
    private var onRoleAuthorized: ((DeviceRole) -> Unit)? = null

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        val role = pendingRole
        pendingRole = null
        val callback = onRoleAuthorized
        onRoleAuthorized = null
        if (role != null && callback != null) {
            val hasRequiredPermissions = role != DeviceRole.BABY || (
                hasPermission(Manifest.permission.RECORD_AUDIO) &&
                    hasPermission(Manifest.permission.CAMERA)
                )
            if (hasRequiredPermissions) {
                callback(role)
                startMonitoringService(role)
                if (role == DeviceRole.BABY) requestIgnoreBatteryOptimizations()
            } else {
                Toast.makeText(
                    this,
                    "El modo bebé necesita permisos de micrófono y cámara.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val state by BabyMonitorMobileService.monitoringState.collectAsState()
                    var currentRole by remember { mutableStateOf(BabyMonitorMobileService.activeRole) }

                    LaunchedEffect(state.isRunning) {
                        if (!state.isRunning) {
                            currentRole = DeviceRole.UNSELECTED
                        }
                    }

                    when (currentRole) {
                        DeviceRole.UNSELECTED -> {
                            RoleSelectionScreen(
                                onSelectRole = { role ->
                                    requestRolePermissions(role) { authorizedRole ->
                                        currentRole = authorizedRole
                                    }
                                }
                            )
                        }
                        DeviceRole.BABY -> {
                            BabyScreen(
                                state = state,
                                onThresholdChange = { soundDb, motionPercent, type ->
                                    val intent = Intent(this, BabyMonitorMobileService::class.java).apply {
                                        action = "ACTION_UPDATE_CONFIG"
                                    }
                                    startService(intent)
                                },
                                onToggleTorch = {
                                    val intent = Intent(this, BabyMonitorMobileService::class.java).apply {
                                        action = "ACTION_TOGGLE_TORCH"
                                    }
                                    startService(intent)
                                },
                                onSwitchCamera = {
                                    val intent = Intent(this, BabyMonitorMobileService::class.java).apply {
                                        action = "ACTION_SWITCH_CAMERA"
                                    }
                                    startService(intent)
                                },
                                onExit = {
                                    stopMonitoringService()
                                    currentRole = DeviceRole.UNSELECTED
                                }
                            )
                        }
                        DeviceRole.PARENT -> {
                            ParentScreen(
                                state = state,
                                onToggleListen = {
                                    val intent = Intent(this, BabyMonitorMobileService::class.java).apply {
                                        action = BabyMonitorMobileService.ACTION_TOGGLE_LISTEN
                                    }
                                    startService(intent)
                                },
                                onExit = {
                                    stopMonitoringService()
                                    currentRole = DeviceRole.UNSELECTED
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    private fun startMonitoringService(role: DeviceRole) {
        val action = if (role == DeviceRole.BABY) {
            BabyMonitorMobileService.ACTION_START_BABY_MODE
        } else {
            BabyMonitorMobileService.ACTION_START_PARENT_MODE
        }
        val intent = Intent(this, BabyMonitorMobileService::class.java).apply {
            this.action = action
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    private fun stopMonitoringService() {
        val intent = Intent(this, BabyMonitorMobileService::class.java).apply {
            action = BabyMonitorMobileService.ACTION_STOP_SERVICE
        }
        startService(intent)
    }

    private fun requestRolePermissions(role: DeviceRole, onAuthorized: (DeviceRole) -> Unit) {
        val permissions = buildList {
            if (role == DeviceRole.BABY) {
                add(Manifest.permission.RECORD_AUDIO)
                add(Manifest.permission.CAMERA)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        val missing = permissions.filterNot(::hasPermission)
        if (missing.isNotEmpty()) {
            pendingRole = role
            onRoleAuthorized = onAuthorized
            permissionLauncher.launch(missing.toTypedArray())
        } else {
            onAuthorized(role)
            startMonitoringService(role)
            if (role == DeviceRole.BABY) requestIgnoreBatteryOptimizations()
        }
    }

    private fun hasPermission(permission: String): Boolean =
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

    private fun requestIgnoreBatteryOptimizations() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val pm = getSystemService(PowerManager::class.java)
            if (!pm.isIgnoringBatteryOptimizations(packageName)) {
                try {
                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                        data = Uri.parse("package:$packageName")
                    }
                    startActivity(intent)
                } catch (_: Exception) {
                    // Fallback to battery saver settings if direct request fails
                }
            }
        }
    }
}
