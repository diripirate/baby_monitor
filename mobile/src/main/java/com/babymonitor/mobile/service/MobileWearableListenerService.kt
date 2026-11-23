package com.babymonitor.mobile.service

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import com.babymonitor.core.model.DeviceRole
import com.babymonitor.core.protocol.ProtocolConstants
import com.babymonitor.mobile.MainActivity
import com.babymonitor.mobile.audio.AudioTrackPlayer
import com.google.android.gms.wearable.ChannelClient
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class MobileWearableListenerService : WearableListenerService() {

    private val serviceScope = CoroutineScope(Dispatchers.IO)
    private val audioPlayer = AudioTrackPlayer()

    override fun onMessageReceived(messageEvent: MessageEvent) {
        val path = messageEvent.path
        val data = String(messageEvent.data)
        Log.d("MobileWearableListener", "Message received: $path, data: $data")

        when (path) {
            ProtocolConstants.PATH_ALERT_SOUND -> {
                showAlarmNotification("¡Ruido detectado!", "El bebé está haciendo ruido ($data dB)")
            }
            ProtocolConstants.PATH_ALERT_MOTION -> {
                showAlarmNotification("¡Movimiento detectado!", "Se ha detectado movimiento en la cuna")
            }
            ProtocolConstants.PATH_CMD_START_AUDIO -> {
                sendServiceAction(BabyMonitorMobileService.ACTION_TOGGLE_LISTEN)
            }
            ProtocolConstants.PATH_CMD_STOP_AUDIO -> {
                sendServiceAction(BabyMonitorMobileService.ACTION_TOGGLE_LISTEN)
            }
            ProtocolConstants.PATH_CMD_TOGGLE_TORCH -> {
                // If service is running, it can toggle torch
                val intent = Intent(this, BabyMonitorMobileService::class.java).apply {
                    action = "ACTION_TOGGLE_TORCH"
                }
                startService(intent)
            }
            ProtocolConstants.PATH_CMD_SWITCH_CAMERA -> {
                val intent = Intent(this, BabyMonitorMobileService::class.java).apply {
                    action = "ACTION_SWITCH_CAMERA"
                }
                startService(intent)
            }
            ProtocolConstants.PATH_CMD_EXIT -> {
                sendServiceAction(BabyMonitorMobileService.ACTION_STOP_SERVICE)
            }
        }
    }

    override fun onChannelOpened(channel: ChannelClient.Channel) {
        Log.d("MobileWearableListener", "Channel opened: ${channel.path}")
        if (channel.path == ProtocolConstants.CHANNEL_AUDIO) {
            serviceScope.launch {
                try {
                    val inputStream = Wearable.getChannelClient(this@MobileWearableListenerService)
                        .getInputStream(channel).await()
                    audioPlayer.startPlayback(inputStream, serviceScope)
                } catch (e: Exception) {
                    Log.e("MobileWearableListener", "Error reading audio channel stream", e)
                }
            }
        }
    }

    private fun sendServiceAction(actionName: String) {
        val intent = Intent(this, BabyMonitorMobileService::class.java).apply {
            action = actionName
        }
        startService(intent)
    }

    private fun showAlarmNotification(title: String, message: String) {
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
            .setVibrate(longArrayOf(0, 500, 200, 500, 200, 500))
            .setContentIntent(pendingIntent)
            .build()

        notificationManager.notify(ProtocolConstants.NOTIFICATION_ID_ALARM, notification)
    }

    override fun onDestroy() {
        audioPlayer.stopPlayback()
        super.onDestroy()
    }
}
