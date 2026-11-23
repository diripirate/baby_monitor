package com.babymonitor.wear

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.core.content.ContextCompat
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.TimeText
import com.babymonitor.core.model.DeviceRole
import com.babymonitor.wear.service.BabyMonitorWearService
import com.babymonitor.wear.ui.WearBabyScreen
import com.babymonitor.wear.ui.WearParentScreen
import com.babymonitor.wear.ui.WearRoleSelectionScreen

class MainActivity : ComponentActivity() {

    private val requiredPermissions = buildList {
        add(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ -> }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        checkAndRequestPermissions()

        setContent {
            MaterialTheme {
                Scaffold(timeText = { TimeText() }) {
                    val state by BabyMonitorWearService.monitoringState.collectAsState()
                    val latestFrame by BabyMonitorWearService.latestVideoFrame.collectAsState()
                    var currentRole by remember { mutableStateOf(BabyMonitorWearService.activeRole) }

                    LaunchedEffect(state.isRunning) {
                        if (!state.isRunning) {
                            currentRole = DeviceRole.UNSELECTED
                        }
                    }

                    when (currentRole) {
                        DeviceRole.UNSELECTED -> {
                            WearRoleSelectionScreen(
                                onSelectRole = { role ->
                                    currentRole = role
                                    startWearService(role)
                                }
                            )
                        }
                        DeviceRole.BABY -> {
                            WearBabyScreen(
                                state = state,
                                onExit = {
                                    stopWearService()
                                    currentRole = DeviceRole.UNSELECTED
                                }
                            )
                        }
                        DeviceRole.PARENT -> {
                            WearParentScreen(
                                state = state,
                                latestFrame = latestFrame,
                                onToggleAudio = {
                                    sendServiceAction(BabyMonitorWearService.ACTION_TOGGLE_AUDIO)
                                },
                                onToggleVideo = {
                                    sendServiceAction(BabyMonitorWearService.ACTION_TOGGLE_VIDEO)
                                },
                                onToggleTorch = {
                                    sendServiceAction(BabyMonitorWearService.ACTION_TOGGLE_TORCH)
                                },
                                onSwitchCamera = {
                                    sendServiceAction(BabyMonitorWearService.ACTION_SWITCH_CAMERA)
                                },
                                onExit = {
                                    stopWearService()
                                    currentRole = DeviceRole.UNSELECTED
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    private fun startWearService(role: DeviceRole) {
        val action = if (role == DeviceRole.BABY) {
            BabyMonitorWearService.ACTION_START_BABY_MODE
        } else {
            BabyMonitorWearService.ACTION_START_PARENT_MODE
        }
        val intent = Intent(this, BabyMonitorWearService::class.java).apply {
            this.action = action
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    private fun stopWearService() {
        val intent = Intent(this, BabyMonitorWearService::class.java).apply {
            action = BabyMonitorWearService.ACTION_STOP_SERVICE
        }
        startService(intent)
    }

    private fun sendServiceAction(actionName: String) {
        val intent = Intent(this, BabyMonitorWearService::class.java).apply {
            action = actionName
        }
        startService(intent)
    }

    private fun checkAndRequestPermissions() {
        val missing = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            permissionLauncher.launch(missing.toTypedArray())
        }
    }
}
