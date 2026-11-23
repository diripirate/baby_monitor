package com.babymonitor.mobile.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import com.babymonitor.core.detection.AudioLevelDetector
import com.babymonitor.core.protocol.ProtocolConstants
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

class AudioRecorderManager(
    private val audioLevelDetector: AudioLevelDetector,
    private val onLevelUpdate: (Int) -> Unit,
    private val onAlarmTriggered: () -> Unit
) {
    private var recordJob: Job? = null
    private var isRecording = false
    private var audioRecord: AudioRecord? = null
    private var streamingOutputStream: OutputStream? = null

    var isStreamingAudio: Boolean = false

    fun setStreamingTarget(outputStream: OutputStream?) {
        this.streamingOutputStream = outputStream
    }

    @SuppressLint("MissingPermission")
    fun start(scope: CoroutineScope) {
        if (isRecording) return

        val minBufferSize = AudioRecord.getMinBufferSize(
            ProtocolConstants.AUDIO_SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        val bufferSize = maxOf(minBufferSize, ProtocolConstants.AUDIO_BUFFER_SIZE_SAMPLES * 2)

        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                ProtocolConstants.AUDIO_SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize
            )

            audioRecord?.startRecording()
            isRecording = true

            recordJob = scope.launch(Dispatchers.IO) {
                val shortBuffer = ShortArray(ProtocolConstants.AUDIO_BUFFER_SIZE_SAMPLES)
                val byteBuffer = ByteBuffer.allocate(ProtocolConstants.AUDIO_BUFFER_SIZE_SAMPLES * 2)
                    .order(ByteOrder.LITTLE_ENDIAN)

                while (isActive && isRecording) {
                    val readSamples = audioRecord?.read(shortBuffer, 0, shortBuffer.size) ?: 0
                    if (readSamples > 0) {
                        val db = audioLevelDetector.calculateDb(shortBuffer, readSamples)
                        onLevelUpdate(db)

                        if (audioLevelDetector.checkAlarm(db)) {
                            onAlarmTriggered()
                        }

                        // Stream audio if active
                        if (isStreamingAudio && streamingOutputStream != null) {
                            try {
                                byteBuffer.clear()
                                for (i in 0 until readSamples) {
                                    byteBuffer.putShort(shortBuffer[i])
                                }
                                streamingOutputStream?.write(byteBuffer.array(), 0, readSamples * 2)
                                streamingOutputStream?.flush()
                            } catch (e: Exception) {
                                Log.e("AudioRecorderManager", "Error streaming audio", e)
                                isStreamingAudio = false
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("AudioRecorderManager", "Failed to start AudioRecord", e)
            stop()
        }
    }

    fun stop() {
        isRecording = false
        isStreamingAudio = false
        recordJob?.cancel()
        recordJob = null
        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (e: Exception) {
            Log.e("AudioRecorderManager", "Error stopping AudioRecord", e)
        }
        audioRecord = null
        streamingOutputStream = null
        audioLevelDetector.reset()
    }
}
