package com.babymonitor.wear.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.*
import android.util.Log
import androidx.core.app.NotificationCompat
import com.babymonitor.core.detection.AudioLevelDetector
import com.babymonitor.core.model.DeviceRole
import com.babymonitor.core.model.MonitoringState
import com.babymonitor.core.protocol.ProtocolConstants
import com.babymonitor.wear.MainActivity
import com.babymonitor.wear.audio.WearAudioPlayer
import com.babymonitor.wear.audio.WearAudioRecorder
import com.babymonitor.wear.communication.WearManager
import com.google.android.gms.wearable.ChannelClient
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.io.InputStream
import java.nio.ByteBuffer

class BabyMonitorWearService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())
    private var wakeLock: PowerManager.WakeLock? = null
    private lateinit var wearManager: WearManager

    private lateinit var audioDetector: AudioLevelDetector
    private lateinit var audioRecorder: WearAudioRecorder
    private lateinit var audioPlayer: WearAudioPlayer

    private var activeAudioChannel: ChannelClient.Channel? = null
    private var videoJob: Job? = null

    companion object {
        const val ACTION_START_BABY_MODE = "action_start_baby_mode"
        const val ACTION_START_PARENT_MODE = "action_start_parent_mode"
        const val ACTION_STOP_SERVICE = "action_stop_service"
        const val ACTION_TOGGLE_AUDIO = "action_toggle_audio"
        const val ACTION_TOGGLE_VIDEO = "action_toggle_video"
        const val ACTION_TOGGLE_TORCH = "action_toggle_torch"
        const val ACTION_SWITCH_CAMERA = "action_switch_camera"

        private val _monitoringState = MutableStateFlow(MonitoringState())
        val monitoringState = _monitoringState.asStateFlow()

        private val _latestVideoFrame = MutableStateFlow<Bitmap?>(null)
        val latestVideoFrame = _latestVideoFrame.asStateFlow()

        var activeRole = DeviceRole.UNSELECTED
            private set
    }

    override fun onCreate() {
        super.onCreate()
        wearManager = WearManager(this)
        audioDetector = AudioLevelDetector(thresholdDb = _monitoringState.value.soundThresholdDb)
        audioPlayer = WearAudioPlayer()

        audioRecorder = WearAudioRecorder(
            audioDetector = audioDetector,
            onLevelUpdate = { db ->
                _monitoringState.update { it.copy(currentDb = db) }
            },
            onAlarmTriggered = {
                handleWatchBabyAlarm()
            }
        )

        acquireWakeLock()
        createNotificationChannels()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_BABY_MODE -> startBabyMode()
            ACTION_START_PARENT_MODE -> startParentMode()
            ACTION_STOP_SERVICE -> stopMonitoring()
            ACTION_TOGGLE_AUDIO -> toggleAudioListening()
            ACTION_TOGGLE_VIDEO -> toggleVideoWatching()
            ACTION_TOGGLE_TORCH -> toggleRemoteTorch()
            ACTION_SWITCH_CAMERA -> switchRemoteCamera()
        }
        return START_STICKY
    }

    private fun startBabyMode() {
        activeRole = DeviceRole.BABY
        _monitoringState.update { it.copy(isRunning = true) }
        startForegroundNotification("Reloj en Habitación", "Detectando sonido...")
        audioRecorder.start(serviceScope)
    }

    private fun startParentMode() {
        activeRole = DeviceRole.PARENT
        _monitoringState.update { it.copy(isRunning = true) }
        startForegroundNotification("Modo Receptor Reloj", "Vigilando al bebé...")
    }

    private fun handleWatchBabyAlarm() {
        serviceScope.launch {
            wearManager.sendMessageToPhone(
                ProtocolConstants.PATH_ALERT_SOUND,
                _monitoringState.value.currentDb.toString().toByteArray()
            )
            // Stream audio immediately upon alarm
            startAudioStreamingToPhone()
        }
    }

    fun startAudioStreamingToPhone() {
        serviceScope.launch {
            val channel = wearManager.openStreamChannel(ProtocolConstants.CHANNEL_AUDIO)
            if (channel != null) {
                activeAudioChannel = channel
                val out = wearManager.getChannelOutputStream(channel)
                audioRecorder.setStreamingTarget(out)
                audioRecorder.isStreamingAudio = true
                _monitoringState.update { it.copy(isAudioStreaming = true) }
            }
        }
    }

    fun stopAudioStreamingToPhone() {
        audioRecorder.isStreamingAudio = false
        audioRecorder.setStreamingTarget(null)
        activeAudioChannel?.let { wearManager.closeChannel(it) }
        activeAudioChannel = null
        _monitoringState.update { it.copy(isAudioStreaming = false) }
    }

    private fun toggleAudioListening() {
        if (activeRole != DeviceRole.PARENT) return
        val currentlyListening = _monitoringState.value.isAudioStreaming
        serviceScope.launch {
            if (currentlyListening) {
                wearManager.sendMessageToPhone(ProtocolConstants.PATH_CMD_STOP_AUDIO)
                audioPlayer.stopPlayback()
                _monitoringState.update { it.copy(isAudioStreaming = false) }
            } else {
                wearManager.sendMessageToPhone(ProtocolConstants.PATH_CMD_START_AUDIO)
                _monitoringState.update { it.copy(isAudioStreaming = true) }
            }
        }
    }

    private fun toggleVideoWatching() {
        if (activeRole != DeviceRole.PARENT) return
        val currentlyWatching = _monitoringState.value.isVideoStreaming
        serviceScope.launch {
            if (currentlyWatching) {
                wearManager.sendMessageToPhone(ProtocolConstants.PATH_CMD_STOP_VIDEO)
                videoJob?.cancel()
                videoJob = null
                _latestVideoFrame.value = null
                _monitoringState.update { it.copy(isVideoStreaming = false) }
            } else {
                wearManager.sendMessageToPhone(ProtocolConstants.PATH_CMD_START_VIDEO)
                _monitoringState.update { it.copy(isVideoStreaming = true) }
            }
        }
    }

    fun onVideoStreamReceived(inputStream: InputStream) {
        videoJob?.cancel()
        videoJob = serviceScope.launch(Dispatchers.IO) {
            val lengthBuffer = ByteArray(4)
            try {
                while (isActive && _monitoringState.value.isVideoStreaming) {
                    var read = 0
                    while (read < 4) {
                        val r = inputStream.read(lengthBuffer, read, 4 - read)
                        if (r == -1) return@launch
                        read += r
                    }
                    val frameLength = ByteBuffer.wrap(lengthBuffer).int
                    if (frameLength in 1..500_000) {
                        val frameBuffer = ByteArray(frameLength)
                        var frameRead = 0
                        while (frameRead < frameLength) {
                            val r = inputStream.read(frameBuffer, frameRead, frameLength - frameRead)
                            if (r == -1) return@launch
                            frameRead += r
                        }
                        val bitmap = BitmapFactory.decodeByteArray(frameBuffer, 0, frameLength)
                        if (bitmap != null) {
                            _latestVideoFrame.value = bitmap
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("BabyMonitorWearService", "Error decoding video stream", e)
            }
        }
    }

    fun onAudioStreamReceived(inputStream: InputStream) {
        audioPlayer.startPlayback(inputStream, serviceScope)
    }

    private fun toggleRemoteTorch() {
        serviceScope.launch {
            wearManager.sendMessageToPhone(ProtocolConstants.PATH_CMD_TOGGLE_TORCH)
            _monitoringState.update { it.copy(isTorchOn = !it.isTorchOn) }
        }
    }

    private fun switchRemoteCamera() {
        serviceScope.launch {
            wearManager.sendMessageToPhone(ProtocolConstants.PATH_CMD_SWITCH_CAMERA)
            _monitoringState.update { it.copy(isBackCamera = !it.isBackCamera) }
        }
    }

    fun stopMonitoring() {
        serviceScope.launch {
            wearManager.sendMessageToPhone(ProtocolConstants.PATH_CMD_EXIT)
        }

        audioRecorder.stop()
        audioPlayer.stopPlayback()
        videoJob?.cancel()
        videoJob = null
        _latestVideoFrame.value = null

        activeAudioChannel?.let { wearManager.closeChannel(it) }

        releaseWakeLock()
        activeRole = DeviceRole.UNSELECTED
        _monitoringState.update { it.copy(isRunning = false, isAudioStreaming = false, isVideoStreaming = false) }

        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun startForegroundNotification(title: String, content: String) {
        val stopIntent = Intent(this, BabyMonitorWearService::class.java).apply {
            action = ACTION_STOP_SERVICE
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 2, stopIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
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
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Salir", stopPendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val serviceType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE or
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
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "BabyMonitor:WearWakeLock")
        wakeLock?.acquire(24 * 60 * 60 * 1000L)
    }

    private fun releaseWakeLock() {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        } catch (e: Exception) {
            Log.e("BabyMonitorWearService", "Error releasing wake lock", e)
        }
        wakeLock = null
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            val serviceChannel = NotificationChannel(
                ProtocolConstants.CHANNEL_ID_SERVICE,
                "Monitor Bebé Reloj",
                NotificationManager.IMPORTANCE_LOW
            )
            val alarmChannel = NotificationChannel(
                ProtocolConstants.CHANNEL_ID_ALARM,
                "Alarmas Bebé",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                enableVibration(true)
            }
            manager.createNotificationChannel(serviceChannel)
            manager.createNotificationChannel(alarmChannel)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        stopMonitoring()
        serviceScope.cancel()
        super.onDestroy()
    }
}
