package com.babymonitor.core.detection

import java.nio.ByteBuffer
import kotlin.math.abs

class MotionDetector(
    var sensitivityPercent: Int = 15,
    private val pixelDiffThreshold: Int = 28,
    private val debounceWindowMs: Long = 1500L
) {
    private var previousGrid: ByteArray? = null
    private var lastAlarmTimestamp: Long = 0

    companion object {
        const val GRID_WIDTH = 64
        const val GRID_HEIGHT = 64
        const val TOTAL_GRID_PIXELS = GRID_WIDTH * GRID_HEIGHT
    }

    /**
     * Evaluates motion from Y plane of YUV_420_888 image buffer.
     * Downsamples to 64x64 grid for high performance and low battery consumption.
     */
    fun processLuminanceFrame(
        yBuffer: ByteBuffer,
        frameWidth: Int,
        frameHeight: Int,
        rowStride: Int
    ): MotionResult {
        val currentGrid = ByteArray(TOTAL_GRID_PIXELS)
        val stepX = frameWidth / GRID_WIDTH
        val stepY = frameHeight / GRID_HEIGHT

        for (gy in 0 until GRID_HEIGHT) {
            val srcY = gy * stepY
            val rowOffset = srcY * rowStride
            for (gx in 0 until GRID_WIDTH) {
                val srcX = gx * stepX
                val pixel = yBuffer.get(rowOffset + srcX)
                currentGrid[gy * GRID_WIDTH + gx] = pixel
            }
        }

        val prev = previousGrid
        previousGrid = currentGrid

        if (prev == null) {
            return MotionResult(changedPercent = 0, isMotionDetected = false)
        }

        var changedPixels = 0
        for (i in 0 until TOTAL_GRID_PIXELS) {
            val p1 = prev[i].toInt() and 0xFF
            val p2 = currentGrid[i].toInt() and 0xFF
            if (abs(p1 - p2) >= pixelDiffThreshold) {
                changedPixels++
            }
        }

        val changedPercent = (changedPixels * 100) / TOTAL_GRID_PIXELS
        val now = System.currentTimeMillis()
        var triggered = false

        if (changedPercent >= sensitivityPercent) {
            if (now - lastAlarmTimestamp > debounceWindowMs) {
                lastAlarmTimestamp = now
                triggered = true
            }
        }

        return MotionResult(
            changedPercent = changedPercent,
            isMotionDetected = triggered
        )
    }

    fun reset() {
        previousGrid = null
        lastAlarmTimestamp = 0
    }
}

data class MotionResult(
    val changedPercent: Int,
    val isMotionDetected: Boolean
)
