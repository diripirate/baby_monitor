package com.babymonitor.core.detection

import kotlin.math.log10
import kotlin.math.sqrt

class AudioLevelDetector(
    var thresholdDb: Int = 65,
    private val debounceWindowMs: Long = 1000L
) {
    private var lastAlarmTimestamp: Long = 0
    private var consecutiveOverThresholdFrames = 0
    private val framesRequiredForAlarm = 3 // ~120ms of sustained sound

    /**
     * Calculates decibels (dB SPL relative) from 16-bit PCM buffer.
     * Raw 16-bit amplitude ranges from -32768 to 32767.
     */
    fun calculateDb(buffer: ShortArray, readSize: Int): Int {
        if (readSize <= 0) return 0
        var sumSquares = 0.0
        for (i in 0 until readSize) {
            val sample = buffer[i].toDouble()
            sumSquares += sample * sample
        }
        val rms = sqrt(sumSquares / readSize)
        if (rms <= 0.0) return 0

        // Normalizing dB relative to full scale (0 dB to ~90 dB representation)
        // 20 * log10(rms) gives values from 0 up to ~90.3 dB
        val db = (20.0 * log10(rms)).toInt().coerceIn(0, 100)
        return db
    }

    /**
     * Checks if current dB value triggers an alarm condition.
     * Returns true if sound exceeds threshold for the required duration and debounce window passed.
     */
    fun checkAlarm(currentDb: Int): Boolean {
        val now = System.currentTimeMillis()
        if (currentDb >= thresholdDb) {
            consecutiveOverThresholdFrames++
            if (consecutiveOverThresholdFrames >= framesRequiredForAlarm) {
                if (now - lastAlarmTimestamp > debounceWindowMs) {
                    lastAlarmTimestamp = now
                    consecutiveOverThresholdFrames = 0
                    return true
                }
            }
        } else {
            consecutiveOverThresholdFrames = (consecutiveOverThresholdFrames - 1).coerceAtLeast(0)
        }
        return false
    }

    fun reset() {
        consecutiveOverThresholdFrames = 0
        lastAlarmTimestamp = 0
    }
}
