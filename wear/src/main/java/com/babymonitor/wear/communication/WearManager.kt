package com.babymonitor.wear.communication

import android.content.Context
import android.util.Log
import com.babymonitor.core.protocol.ProtocolConstants
import com.google.android.gms.wearable.ChannelClient
import com.google.android.gms.wearable.Node
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.tasks.await
import java.io.InputStream
import java.io.OutputStream

class WearManager(private val context: Context) {
    private val messageClient = Wearable.getMessageClient(context)
    private val channelClient = Wearable.getChannelClient(context)
    private val nodeClient = Wearable.getNodeClient(context)

    suspend fun getConnectedPhoneNode(): Node? {
        return try {
            val nodes = nodeClient.connectedNodes.await()
            nodes.firstOrNull()
        } catch (e: Exception) {
            Log.e("WearManager", "Error getting paired phone node", e)
            null
        }
    }

    suspend fun sendMessageToPhone(path: String, data: ByteArray = ByteArray(0)): Boolean {
        val phone = getConnectedPhoneNode() ?: return false
        return try {
            messageClient.sendMessage(phone.id, path, data).await()
            true
        } catch (e: Exception) {
            Log.e("WearManager", "Error sending message $path", e)
            false
        }
    }

    suspend fun openStreamChannel(channelPath: String): ChannelClient.Channel? {
        val phone = getConnectedPhoneNode() ?: return null
        return try {
            channelClient.openChannel(phone.id, channelPath).await()
        } catch (e: Exception) {
            Log.e("WearManager", "Error opening channel $channelPath", e)
            null
        }
    }

    suspend fun getChannelOutputStream(channel: ChannelClient.Channel): OutputStream? {
        return try {
            channelClient.getOutputStream(channel).await()
        } catch (e: Exception) {
            Log.e("WearManager", "Failed to open OutputStream", e)
            null
        }
    }

    suspend fun getChannelInputStream(channel: ChannelClient.Channel): InputStream? {
        return try {
            channelClient.getInputStream(channel).await()
        } catch (e: Exception) {
            Log.e("WearManager", "Failed to open InputStream", e)
            null
        }
    }

    fun closeChannel(channel: ChannelClient.Channel) {
        try {
            channelClient.close(channel)
        } catch (e: Exception) {
            Log.e("WearManager", "Error closing channel", e)
        }
    }
}
