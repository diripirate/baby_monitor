package com.babymonitor.mobile

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
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

    private val requiredPermissions = buildList {
        add(Manifest.permission.RECORD_AUDIO)
        add(Manifest.permission.CAMERA)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        requestIgnoreBatteryOptimizations()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        checkAndRequestPermissions()

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
                                    currentRole = role
                                    startMonitoringService(role)
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

    private fun checkAndRequestPermissions() {
        val missing = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            permissionLauncher.launch(missing.toTypedArray())
        } else {
            requestIgnoreBatteryOptimizations()
        }
    }

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
