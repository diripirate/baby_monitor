package com.babymonitor.mobile.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import android.util.Log
import android.util.Size
import androidx.camera.core.Camera
import androidx.camera.core.CameraControl
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.babymonitor.core.detection.MotionDetector
import com.babymonitor.core.detection.MotionResult
import com.babymonitor.core.protocol.ProtocolConstants
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class CameraMotionStreamer(
    private val context: Context,
    private val motionDetector: MotionDetector,
    private val onMotionResult: (MotionResult) -> Unit,
    private val onAlarmTriggered: () -> Unit
) {
    private var cameraProvider: ProcessCameraProvider? = null
    private var camera: Camera? = null
    private var cameraControl: CameraControl? = null
    private var imageAnalysis: ImageAnalysis? = null
    private val cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()

    var isBackCamera = true
    var isTorchEnabled = false
        private set
    var isMotionDetectionActive = true
    var isVideoStreaming = false
    private var streamingOutputStream: OutputStream? = null

    private var lastFrameTime = 0L
    private val frameIntervalMs = 120L // ~8 fps for Wear OS streaming

    fun setStreamingTarget(outputStream: OutputStream?) {
        this.streamingOutputStream = outputStream
    }

    fun startCamera(lifecycleOwner: LifecycleOwner) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            cameraProvider = cameraProviderFuture.get()
            bindCameraUseCases(lifecycleOwner)
        }, ContextCompat.getMainExecutor(context))
    }

    private fun bindCameraUseCases(lifecycleOwner: LifecycleOwner) {
        val provider = cameraProvider ?: return
        val cameraSelector = if (isBackCamera) {
            CameraSelector.DEFAULT_BACK_CAMERA
        } else {
            CameraSelector.DEFAULT_FRONT_CAMERA
        }

        imageAnalysis = ImageAnalysis.Builder()
            .setTargetResolution(Size(ProtocolConstants.VIDEO_WIDTH, ProtocolConstants.VIDEO_HEIGHT))
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
            .also {
                it.setAnalyzer(cameraExecutor) { imageProxy ->
                    processImage(imageProxy)
                }
            }

        try {
            provider.unbindAll()
            camera = provider.bindToLifecycle(lifecycleOwner, cameraSelector, imageAnalysis)
            cameraControl = camera?.cameraControl
            setTorch(isTorchEnabled)
        } catch (e: Exception) {
            Log.e("CameraMotionStreamer", "Error binding camera use cases", e)
        }
    }

    private fun processImage(imageProxy: ImageProxy) {
        try {
            // 1. Motion Detection
            if (isMotionDetectionActive) {
                val yPlane = imageProxy.planes[0]
                val yBuffer = yPlane.buffer
                val result = motionDetector.processLuminanceFrame(
                    yBuffer = yBuffer,
                    frameWidth = imageProxy.width,
                    frameHeight = imageProxy.height,
                    rowStride = yPlane.rowStride
                )
                onMotionResult(result)
                if (result.isMotionDetected) {
                    onAlarmTriggered()
                }
            }

            // 2. Video Streaming to Watch (MJPEG frame)
            val now = System.currentTimeMillis()
            if (isVideoStreaming && streamingOutputStream != null && (now - lastFrameTime >= frameIntervalMs)) {
                lastFrameTime = now
                val jpegBytes = imageProxyToJpeg(imageProxy)
                if (jpegBytes != null) {
                    val out = streamingOutputStream
                    if (out != null) {
                        synchronized(out) {
                            // Frame protocol: 4 bytes length header + JPEG payload
                            val lengthBuffer = ByteBuffer.allocate(4).putInt(jpegBytes.size).array()
                            out.write(lengthBuffer)
                            out.write(jpegBytes)
                            out.flush()
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("CameraMotionStreamer", "Error processing frame or streaming", e)
        } finally {
            imageProxy.close()
        }
    }

    private fun imageProxyToJpeg(image: ImageProxy): ByteArray? {
        val yBuffer = image.planes[0].buffer
        val uBuffer = image.planes[1].buffer
        val vBuffer = image.planes[2].buffer

        val ySize = yBuffer.remaining()
        val uSize = uBuffer.remaining()
        val vSize = vBuffer.remaining()

        val nv21 = ByteArray(ySize + uSize + vSize)
        yBuffer.get(nv21, 0, ySize)
        vBuffer.get(nv21, ySize, vSize)
        uBuffer.get(nv21, ySize + vSize, uSize)

        val yuvImage = YuvImage(nv21, ImageFormat.NV21, image.width, image.height, null)
        val out = ByteArrayOutputStream()
        yuvImage.compressToJpeg(Rect(0, 0, image.width, image.height), ProtocolConstants.VIDEO_JPEG_QUALITY, out)
        return out.toByteArray()
    }

    fun switchCamera(lifecycleOwner: LifecycleOwner) {
        isBackCamera = !isBackCamera
        bindCameraUseCases(lifecycleOwner)
    }

    fun toggleTorch(): Boolean {
        if (!isBackCamera) return false // Front camera rarely has flash
        isTorchEnabled = !isTorchEnabled
        setTorch(isTorchEnabled)
        return isTorchEnabled
    }

    private fun setTorch(enabled: Boolean) {
        if (camera?.cameraInfo?.hasFlashUnit() == true) {
            cameraControl?.enableTorch(enabled)
        }
    }

    fun stop() {
        try {
            setTorch(false)
            cameraProvider?.unbindAll()
            cameraExecutor.shutdown()
        } catch (e: Exception) {
            Log.e("CameraMotionStreamer", "Error stopping camera", e)
        }
    }
}
