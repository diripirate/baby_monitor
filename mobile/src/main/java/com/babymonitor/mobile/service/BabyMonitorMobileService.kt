package com.babymonitor.mobile.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.babymonitor.core.detection.AudioLevelDetector
import com.babymonitor.core.detection.MotionDetector
import com.babymonitor.core.model.DeviceRole
import com.babymonitor.core.model.MonitoringState
import com.babymonitor.core.model.MonitoringType
import com.babymonitor.core.protocol.ProtocolConstants
import com.babymonitor.mobile.MainActivity
import com.babymonitor.mobile.audio.AudioRecorderManager
import com.babymonitor.mobile.audio.AudioTrackPlayer
import com.babymonitor.mobile.camera.CameraMotionStreamer
import com.babymonitor.mobile.communication.MobileWearableManager
import com.google.android.gms.wearable.ChannelClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class BabyMonitorMobileService : LifecycleService() {

    private var wakeLock: PowerManager.WakeLock? = null
    private lateinit var wearableManager: MobileWearableManager

    // Audio & Camera components
    private lateinit var audioDetector: AudioLevelDetector
    private lateinit var audioRecorder: AudioRecorderManager
    private lateinit var audioPlayer: AudioTrackPlayer

    private lateinit var motionDetector: MotionDetector
    private var cameraStreamer: CameraMotionStreamer? = null

    private var activeAudioChannel: ChannelClient.Channel? = null
    private var activeVideoChannel: ChannelClient.Channel? = null

    companion object {
        const val ACTION_START_BABY_MODE = "action_start_baby_mode"
        const val ACTION_START_PARENT_MODE = "action_start_parent_mode"
        const val ACTION_STOP_SERVICE = "action_stop_service"
        const val ACTION_TOGGLE_LISTEN = "action_toggle_listen"

        private val _monitoringState = MutableStateFlow(MonitoringState())
        val monitoringState = _monitoringState.asStateFlow()

        var activeRole = DeviceRole.UNSELECTED
            private set
    }

    override fun onCreate() {
        super.onCreate()
        wearableManager = MobileWearableManager(this)
        audioDetector = AudioLevelDetector(thresholdDb = _monitoringState.value.soundThresholdDb)
        motionDetector = MotionDetector(sensitivityPercent = _monitoringState.value.motionThresholdPercent)

        audioPlayer = AudioTrackPlayer()

        audioRecorder = AudioRecorderManager(
            audioLevelDetector = audioDetector,
            onLevelUpdate = { db ->
                _monitoringState.update { it.copy(currentDb = db) }
            },
            onAlarmTriggered = {
                handleBabySoundAlarm()
            }
        )

        acquireWakeLock()
        createNotificationChannels()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        when (intent?.action) {
            ACTION_START_BABY_MODE -> startBabyMode()
            ACTION_START_PARENT_MODE -> startParentMode()
            ACTION_STOP_SERVICE -> stopMonitoring()
            ACTION_TOGGLE_LISTEN -> toggleParentListening()
        }
        return START_STICKY
    }

    private fun startBabyMode() {
        activeRole = DeviceRole.BABY
        _monitoringState.update { it.copy(isRunning = true) }

        startForegroundNotification("Modo Habitación del Bebé activo", "Detectando sonido y movimiento...")

        // Start Audio Recording
        if (_monitoringState.value.monitoringType != MonitoringType.MOTION_ONLY) {
            audioRecorder.start(lifecycleScope)
        }

        // Start Camera Motion & Streaming
        if (_monitoringState.value.monitoringType != MonitoringType.SOUND_ONLY) {
            cameraStreamer = CameraMotionStreamer(
                context = this,
                motionDetector = motionDetector,
                onMotionResult = { /* Can publish telemetry */ },
                onAlarmTriggered = { handleBabyMotionAlarm() }
            ).apply {
                startCamera(this@BabyMonitorMobileService)
            }
        }
    }

    private fun startParentMode() {
        activeRole = DeviceRole.PARENT
        _monitoringState.update { it.copy(isRunning = true) }
        startForegroundNotification("Modo Padre activo", "Esperando alertas de la habitación...")
    }

    private fun handleBabySoundAlarm() {
        lifecycleScope.launch {
            wearableManager.sendAlert(isSound = true, value = _monitoringState.value.currentDb)
            // Automatically open audio stream on alarm so parent can hear immediately
            startAudioStreamingToWatch()
        }
    }

    private fun handleBabyMotionAlarm() {
        lifecycleScope.launch {
            wearableManager.sendAlert(isSound = false, value = 1)
        }
    }

    fun startAudioStreamingToWatch() {
        lifecycleScope.launch {
            val channel = wearableManager.openStreamChannel(ProtocolConstants.CHANNEL_AUDIO)
            if (channel != null) {
                activeAudioChannel = channel
                val out = wearableManager.getChannelOutputStream(channel)
                audioRecorder.setStreamingTarget(out)
                audioRecorder.isStreamingAudio = true
                _monitoringState.update { it.copy(isAudioStreaming = true) }
            }
        }
    }

    fun stopAudioStreamingToWatch() {
        audioRecorder.isStreamingAudio = false
        audioRecorder.setStreamingTarget(null)
        activeAudioChannel?.let { wearableManager.closeChannel(it) }
        activeAudioChannel = null
        _monitoringState.update { it.copy(isAudioStreaming = false) }
    }

    fun startVideoStreamingToWatch() {
        lifecycleScope.launch {
            val channel = wearableManager.openStreamChannel(ProtocolConstants.CHANNEL_VIDEO)
            if (channel != null) {
                activeVideoChannel = channel
                val out = wearableManager.getChannelOutputStream(channel)
                cameraStreamer?.setStreamingTarget(out)
                cameraStreamer?.isVideoStreaming = true
                _monitoringState.update { it.copy(isVideoStreaming = true) }
            }
        }
    }

    fun stopVideoStreamingToWatch() {
        cameraStreamer?.isVideoStreaming = false
        cameraStreamer?.setStreamingTarget(null)
        activeVideoChannel?.let { wearableManager.closeChannel(it) }
        activeVideoChannel = null
        _monitoringState.update { it.copy(isVideoStreaming = false) }
    }

    fun toggleParentListening() {
        if (activeRole != DeviceRole.PARENT) return
        val currentlyStreaming = _monitoringState.value.isAudioStreaming
        lifecycleScope.launch {
            if (currentlyStreaming) {
                wearableManager.sendMessageToWatch(ProtocolConstants.PATH_CMD_STOP_AUDIO)
                audioPlayer.stopPlayback()
                _monitoringState.update { it.copy(isAudioStreaming = false) }
            } else {
                wearableManager.sendMessageToWatch(ProtocolConstants.PATH_CMD_START_AUDIO)
                _monitoringState.update { it.copy(isAudioStreaming = true) }
            }
        }
    }

    fun toggleTorch(): Boolean {
        val newState = cameraStreamer?.toggleTorch() ?: false
        _monitoringState.update { it.copy(isTorchOn = newState) }
        return newState
    }

    fun switchCamera() {
        cameraStreamer?.switchCamera(this)
        _monitoringState.update { it.copy(isBackCamera = cameraStreamer?.isBackCamera ?: true) }
    }

    fun updateThresholds(soundDb: Int, motionPercent: Int, type: MonitoringType) {
        audioDetector.thresholdDb = soundDb
        motionDetector.sensitivityPercent = motionPercent
        _monitoringState.update {
            it.copy(
                soundThresholdDb = soundDb,
                motionThresholdPercent = motionPercent,
                monitoringType = type
            )
        }
    }

    fun stopMonitoring() {
        // Send exit command to connected watch so both devices close cleanly
        lifecycleScope.launch {
            wearableManager.sendMessageToWatch(ProtocolConstants.PATH_CMD_EXIT)
        }

        audioRecorder.stop()
        audioPlayer.stopPlayback()
        cameraStreamer?.stop()
        cameraStreamer = null

        activeAudioChannel?.let { wearableManager.closeChannel(it) }
        activeVideoChannel?.let { wearableManager.closeChannel(it) }

        releaseWakeLock()
        activeRole = DeviceRole.UNSELECTED
        _monitoringState.update { it.copy(isRunning = false, isAudioStreaming = false, isVideoStreaming = false) }

        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun startForegroundNotification(title: String, content: String) {
        val stopIntent = Intent(this, BabyMonitorMobileService::class.java).apply {
            action = ACTION_STOP_SERVICE
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 1, stopIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val activityIntent = Intent(this, MainActivity::class.java)
        val activityPendingIntent = PendingIntent.getActivity(
            this, 0, activityIntent, PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, ProtocolConstants.CHANNEL_ID_SERVICE)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(android.R.drawable.ic_lock_idle_charging)
            .setContentIntent(activityPendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Salir y Apagar", stopPendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val serviceType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE or
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA or
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE or
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            } else {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE or
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
            }
            startForeground(ProtocolConstants.NOTIFICATION_ID_FOREGROUND_SERVICE, notification, serviceType)
        } else {
            startForeground(ProtocolConstants.NOTIFICATION_ID_FOREGROUND_SERVICE, notification)
        }
    }

    private fun acquireWakeLock() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "BabyMonitor:ServiceWakeLock")
        wakeLock?.acquire(24 * 60 * 60 * 1000L) // 24 hours max
    }

    private fun releaseWakeLock() {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        } catch (e: Exception) {
            Log.e("BabyMonitorMobileService", "Error releasing wake lock", e)
        }
        wakeLock = null
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)

            val serviceChannel = NotificationChannel(
                ProtocolConstants.CHANNEL_ID_SERVICE,
                "Monitor de Bebé - Estado",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Notificación persistente mientras el monitor de bebé está en ejecución"
            }

            val alarmChannel = NotificationChannel(
                ProtocolConstants.CHANNEL_ID_ALARM,
                "Monitor de Bebé - Alarmas",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notificaciones de alerta por sonido o movimiento del bebé"
                enableVibration(true)
            }

            manager.createNotificationChannel(serviceChannel)
            manager.createNotificationChannel(alarmChannel)
        }
    }

    override fun onDestroy() {
        stopMonitoring()
        super.onDestroy()
    }
}
