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

class WearListenerService : WearableListenerService() {

    private val scope = CoroutineScope(Dispatchers.IO)

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
                // Phone is asking watch to stream its mic
                val intent = Intent(this, BabyMonitorWearService::class.java).apply {
                    action = "START_AUDIO_STREAM"
                }
                startService(intent)
            }
            ProtocolConstants.PATH_CMD_STOP_AUDIO -> {
                val intent = Intent(this, BabyMonitorWearService::class.java).apply {
                    action = "STOP_AUDIO_STREAM"
                }
                startService(intent)
            }
            ProtocolConstants.PATH_CMD_EXIT -> {
                val intent = Intent(this, BabyMonitorWearService::class.java).apply {
                    action = BabyMonitorWearService.ACTION_STOP_SERVICE
                }
                startService(intent)
            }
        }
    }

    override fun onChannelOpened(channel: ChannelClient.Channel) {
        Log.d("WearListenerService", "Channel opened on watch: ${channel.path}")
        scope.launch {
            try {
                val inputStream = Wearable.getChannelClient(this@WearListenerService)
                    .getInputStream(channel).await()

                if (channel.path == ProtocolConstants.CHANNEL_AUDIO) {
                    val intent = Intent(this@WearListenerService, BabyMonitorWearService::class.java).apply {
                        action = "AUDIO_STREAM_RECEIVED"
                    }
                    startService(intent)
                } else if (channel.path == ProtocolConstants.CHANNEL_VIDEO) {
                    val intent = Intent(this@WearListenerService, BabyMonitorWearService::class.java).apply {
                        action = "VIDEO_STREAM_RECEIVED"
                    }
                    startService(intent)
                }
            } catch (e: Exception) {
                Log.e("WearListenerService", "Error handling channel stream", e)
            }
        }
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

        val notification = NotificationCompat.Builder(this, ProtocolConstants.CHANNEL_ID_ALARM)
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
