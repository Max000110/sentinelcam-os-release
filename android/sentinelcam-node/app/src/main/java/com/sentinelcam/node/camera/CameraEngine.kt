package com.sentinelcam.node.camera

import android.content.Context
import android.util.Log
import android.util.Size
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class CameraEngine(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
    private val onFrameAvailable: (ByteArray, Int, Int, Int) -> Unit
) {
    companion object {
        private const val TAG = "SentinelCam.CameraEngine"
    }

    private var camera: Camera? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private var analysisExecutor: ExecutorService? = null
    private val isStopping = AtomicBoolean(false)

    var currentLensFacing = CameraSelector.LENS_FACING_BACK
    var isTorchEnabled = false
    var activeFps = 0
    private var frameCount = 0
    private var lastFpsTimestamp = System.currentTimeMillis()

    fun startCamera(onSuccess: (() -> Unit)? = null) {
        isStopping.set(false)
        if (analysisExecutor == null || analysisExecutor!!.isShutdown) {
            analysisExecutor = Executors.newSingleThreadExecutor()
        }

        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            try {
                cameraProvider = cameraProviderFuture.get()
                bindCameraUseCases()
                onSuccess?.invoke()
                Log.i(TAG, "CameraX started successfully on lens: $currentLensFacing")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to bind CameraX use cases: ${e.message}", e)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    private fun bindCameraUseCases() {
        val provider = cameraProvider ?: return
        try {
            provider.unbindAll()

            val cameraSelector = CameraSelector.Builder()
                .requireLensFacing(currentLensFacing)
                .build()

            val imageAnalysis = ImageAnalysis.Builder()
                .setTargetResolution(Size(1280, 720))
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()

            val executor = analysisExecutor ?: return

            imageAnalysis.setAnalyzer(executor) { imageProxy ->
                if (isStopping.get()) {
                    imageProxy.close()
                    return@setAnalyzer
                }

                try {
                    val width = imageProxy.width
                    val height = imageProxy.height
                    val rotation = imageProxy.imageInfo.rotationDegrees
                    val nv21 = imageProxyToNv21(imageProxy)
                    
                    // Measure live FPS
                    frameCount++
                    val now = System.currentTimeMillis()
                    if (now - lastFpsTimestamp >= 1000) {
                        activeFps = frameCount
                        frameCount = 0
                        lastFpsTimestamp = now
                    }

                    onFrameAvailable(nv21, width, height, rotation)
                } catch (e: Exception) {
                    Log.e(TAG, "Analyzer error: ${e.message}")
                } finally {
                    imageProxy.close()
                }
            }

            camera = provider.bindToLifecycle(
                lifecycleOwner,
                cameraSelector,
                imageAnalysis
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error binding camera lifecycle: ${e.message}", e)
        }
    }

    fun switchCamera() {
        currentLensFacing = if (currentLensFacing == CameraSelector.LENS_FACING_BACK) {
            CameraSelector.LENS_FACING_FRONT
        } else {
            CameraSelector.LENS_FACING_BACK
        }
        isTorchEnabled = false
        bindCameraUseCases()
        Log.i(TAG, "Switched camera lens to: $currentLensFacing")
    }

    fun toggleTorch(): Boolean {
        camera?.let { cam ->
            if (cam.cameraInfo.hasFlashUnit() && currentLensFacing == CameraSelector.LENS_FACING_BACK) {
                isTorchEnabled = !isTorchEnabled
                cam.cameraControl.enableTorch(isTorchEnabled)
                return isTorchEnabled
            }
        }
        return false
    }

    fun stop() {
        isStopping.set(true)
        try {
            cameraProvider?.unbindAll()
            camera = null
            analysisExecutor?.shutdown()
            analysisExecutor = null
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping CameraEngine: ${e.message}")
        }
    }

    private fun imageProxyToNv21(image: ImageProxy): ByteArray {
        val width = image.width
        val height = image.height
        val planes = image.planes

        val yBuffer = planes[0].buffer
        val uBuffer = planes[1].buffer
        val vBuffer = planes[2].buffer

        val yRowStride = planes[0].rowStride
        val yPixelStride = planes[0].pixelStride

        val uvRowStride = planes[1].rowStride
        val uvPixelStride = planes[1].pixelStride

        val nv21 = ByteArray(width * height * 3 / 2)
        var pos = 0

        // Y-plane
        if (yRowStride == width && yPixelStride == 1) {
            yBuffer.position(0)
            yBuffer.get(nv21, 0, width * height)
            pos = width * height
        } else {
            for (row in 0 until height) {
                yBuffer.position(row * yRowStride)
                yBuffer.get(nv21, pos, width)
                pos += width
            }
        }

        // UV interleaved (NV21: V, U, V, U...)
        val uvHeight = height / 2
        val uvWidth = width / 2
        for (row in 0 until uvHeight) {
            val vRow = row * uvRowStride
            val uRow = row * uvRowStride
            for (col in 0 until uvWidth) {
                val vIdx = vRow + col * uvPixelStride
                val uIdx = uRow + col * uvPixelStride
                vBuffer.position(vIdx)
                uBuffer.position(uIdx)
                nv21[pos++] = vBuffer.get()
                nv21[pos++] = uBuffer.get()
            }
        }

        return nv21
    }
}
