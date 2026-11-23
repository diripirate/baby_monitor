package com.babymonitor.wear.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.util.Log
import com.babymonitor.core.protocol.ProtocolConstants
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.InputStream

class WearAudioPlayer {
    private var audioTrack: AudioTrack? = null
    private var playbackJob: Job? = null
    private var isPlaying = false

    fun startPlayback(inputStream: InputStream, scope: CoroutineScope) {
        if (isPlaying) stopPlayback()

        val minBufferSize = AudioTrack.getMinBufferSize(
            ProtocolConstants.AUDIO_SAMPLE_RATE,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )

        audioTrack = AudioTrack(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build(),
            AudioFormat.Builder()
                .setSampleRate(ProtocolConstants.AUDIO_SAMPLE_RATE)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .build(),
            minBufferSize * 2,
            AudioTrack.MODE_STREAM,
            AudioManager.AUDIO_SESSION_ID_GENERATE
        )

        audioTrack?.play()
        isPlaying = true

        playbackJob = scope.launch(Dispatchers.IO) {
            val buffer = ByteArray(ProtocolConstants.AUDIO_BUFFER_SIZE_SAMPLES * 2)
            try {
                while (isActive && isPlaying) {
                    val bytesRead = inputStream.read(buffer)
                    if (bytesRead > 0) {
                        audioTrack?.write(buffer, 0, bytesRead)
                    } else if (bytesRead == -1) {
                        break
                    }
                }
            } catch (e: Exception) {
                Log.e("WearAudioPlayer", "Error during watch audio playback", e)
            } finally {
                stopPlayback()
            }
        }
    }

    fun stopPlayback() {
        isPlaying = false
        playbackJob?.cancel()
        playbackJob = null
        try {
            audioTrack?.stop()
            audioTrack?.release()
        } catch (e: Exception) {
            Log.e("WearAudioPlayer", "Error releasing AudioTrack", e)
        }
        audioTrack = null
    }
}
