package com.babymonitor.wear.service

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.babymonitor.core.protocol.ProtocolConstants
import com.babymonitor.wear.MainActivity
import com.google.android.gms.wearable.ChannelClient
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.io.InputStream

class WearListenerService : WearableListenerService() {

    private val scope = CoroutineScope(Dispatchers.IO)

    companion object {
        // Streams delivered by the Wearable channel callback must be passed
        // directly to whoever will consume them. We use volatile holders so that
        // BabyMonitorWearService can pick them up immediately after the intent fires.
        @Volatile var pendingAudioInputStream: InputStream? = null
        @Volatile var pendingVideoInputStream: InputStream? = null
    }

    override fun onMessageReceived(messageEvent: MessageEvent) {
        val path = messageEvent.path
        val data = String(messageEvent.data)
        Log.d("WearListenerService", "Watch received: $path, data: $data")

        when (path) {
            ProtocolConstants.PATH_ALERT_SOUND -> {
                vibrateWatch()
                showWatchAlarmNotification("¡Ruido en la habitación!", "$data dB detectados")
            }
            ProtocolConstants.PATH_ALERT_MOTION -> {
                vibrateWatch()
                showWatchAlarmNotification("¡Movimiento!", "Movimiento en la cuna")
            }
            ProtocolConstants.PATH_CMD_START_AUDIO -> {
                startServiceWithAction(BabyMonitorWearService.ACTION_START_AUDIO_STREAM)
            }
            ProtocolConstants.PATH_CMD_STOP_AUDIO -> {
                startServiceWithAction(BabyMonitorWearService.ACTION_STOP_AUDIO_STREAM)
            }
            ProtocolConstants.PATH_CMD_EXIT -> {
                startServiceWithAction(BabyMonitorWearService.ACTION_STOP_SERVICE)
            }
        }
    }

    override fun onChannelOpened(channel: ChannelClient.Channel) {
        Log.d("WearListenerService", "Channel opened on watch: ${channel.path}")
        scope.launch {
            try {
                val inputStream = Wearable.getChannelClient(this@WearListenerService)
                    .getInputStream(channel).await()

                when (channel.path) {
                    ProtocolConstants.CHANNEL_AUDIO -> {
                        // Store stream so BabyMonitorWearService can retrieve it
                        pendingAudioInputStream = inputStream
                        startServiceWithAction(BabyMonitorWearService.ACTION_AUDIO_STREAM_READY)
                    }
                    ProtocolConstants.CHANNEL_VIDEO -> {
                        // Store stream so BabyMonitorWearService can retrieve it
                        pendingVideoInputStream = inputStream
                        startServiceWithAction(BabyMonitorWearService.ACTION_VIDEO_STREAM_READY)
                    }
                }
            } catch (e: Exception) {
                Log.e("WearListenerService", "Error opening channel stream", e)
            }
        }
    }

    private fun startServiceWithAction(action: String) {
        val intent = Intent(this, BabyMonitorWearService::class.java).apply {
            this.action = action
        }
        startService(intent)
    }

    private fun vibrateWatch() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vibratorManager.defaultVibrator.vibrate(
                    VibrationEffect.createWaveform(longArrayOf(0, 300, 150, 300, 150, 500), -1)
                )
            } else {
                @Suppress("DEPRECATION")
                val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                vibrator.vibrate(longArrayOf(0, 300, 150, 300, 150, 500), -1)
            }
        } catch (e: Exception) {
            Log.e("WearListenerService", "Error vibrating watch", e)
        }
    }

    private fun showWatchAlarmNotification(title: String, message: String) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val openAppIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, openAppIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = androidx.core.app.NotificationCompat.Builder(this, ProtocolConstants.CHANNEL_ID_ALARM)
            .setContentTitle(title)
            .setContentText(message)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        notificationManager.notify(ProtocolConstants.NOTIFICATION_ID_ALARM, notification)
    }
}
