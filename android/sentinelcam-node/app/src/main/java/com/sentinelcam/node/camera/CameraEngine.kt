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
import java.nio.ByteBuffer
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
        private const val TARGET_WIDTH = 640
        private const val TARGET_HEIGHT = 480
    }

    private var camera: Camera? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private var analysisExecutor: ExecutorService? = null
    private val isStopping = AtomicBoolean(false)

    // Pre-allocated reusable buffer to eliminate per-frame GC pressure
    // 640x480 * 1.5 = 460800 bytes (vs 1382400 for 1280x720)
    @Volatile
    private var reusableNv21: ByteArray = ByteArray(TARGET_WIDTH * TARGET_HEIGHT * 3 / 2)

    // Reusable row buffer for UV conversion
    @Volatile
    private var reusableUvRowBuf: ByteArray = ByteArray(TARGET_WIDTH) // uvWidth * 2

    var currentLensFacing = CameraSelector.LENS_FACING_BACK
    var isTorchEnabled = false
    var activeFps = 0
    private var frameCount = 0
    private var lastFpsTimestamp = System.currentTimeMillis()

    // Latency instrumentation
    private var totalConversionTimeNs = 0L
    private var conversionSamples = 0

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
                Log.i(TAG, "CameraX started successfully on lens: $currentLensFacing (${TARGET_WIDTH}x${TARGET_HEIGHT})")
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
                .setTargetResolution(Size(TARGET_WIDTH, TARGET_HEIGHT))
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

                    // Ensure buffer is correctly sized (handles resolution changes)
                    val requiredSize = width * height * 3 / 2
                    if (reusableNv21.size != requiredSize) {
                        reusableNv21 = ByteArray(requiredSize)
                        reusableUvRowBuf = ByteArray(width) // uvWidth * 2
                    }

                    val startNs = System.nanoTime()
                    imageProxyToNv21Fast(imageProxy, reusableNv21)
                    val convNs = System.nanoTime() - startNs

                    // Log conversion time every 150 frames (~5 seconds at 30fps)
                    totalConversionTimeNs += convNs
                    conversionSamples++
                    if (conversionSamples >= 150) {
                        val avgMs = (totalConversionTimeNs / conversionSamples) / 1_000_000.0
                        Log.i(TAG, "NV21 conversion avg: %.2fms (${width}x${height})".format(avgMs))
                        totalConversionTimeNs = 0
                        conversionSamples = 0
                    }

                    // Measure live FPS
                    frameCount++
                    val now = System.currentTimeMillis()
                    if (now - lastFpsTimestamp >= 1000) {
                        activeFps = frameCount
                        frameCount = 0
                        lastFpsTimestamp = now
                    }

                    onFrameAvailable(reusableNv21, width, height, rotation)
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

    /**
     * High-performance NV21 conversion using bulk ByteBuffer operations.
     * 
     * On most Android devices (Samsung, Pixel), CameraX provides UV planes with
     * pixelStride=2 and the V buffer starts 1 byte before U buffer, meaning the
     * data is already interleaved as VUVU... (NV21 format). In this case we can
     * do a single bulk copy of the entire UV plane.
     *
     * Fallback: row-by-row bulk copy when pixelStride=2 but rowStride != width.
     * Last resort: per-pixel copy (only for unusual pixelStride=1 planar format).
     */
    private fun imageProxyToNv21Fast(image: ImageProxy, output: ByteArray) {
        val width = image.width
        val height = image.height
        val planes = image.planes

        val yBuffer = planes[0].buffer.duplicate()
        val uBuffer = planes[1].buffer.duplicate()
        val vBuffer = planes[2].buffer.duplicate()

        val yRowStride = planes[0].rowStride
        val uvRowStride = planes[1].rowStride
        val uvPixelStride = planes[1].pixelStride

        val ySize = width * height
        var pos = 0

        // === Y Plane: bulk copy ===
        yBuffer.position(0)
        if (yRowStride == width) {
            // Contiguous Y plane — single bulk read
            yBuffer.get(output, 0, ySize)
        } else {
            // Padded rows — row-by-row bulk read
            for (row in 0 until height) {
                yBuffer.position(row * yRowStride)
                yBuffer.get(output, pos, width)
                pos += width
            }
        }

        // === UV Plane: optimized for interleaved format ===
        pos = ySize
        val uvHeight = height / 2
        val uvWidth = width / 2

        if (uvPixelStride == 2) {
            // Most common on Android: V and U planes are interleaved VUVU...
            // V buffer contains the full VU interleaved data
            vBuffer.position(0)

            if (uvRowStride == width) {
                // Perfect case: contiguous interleaved UV — single bulk copy
                val uvDataSize = uvHeight * width
                val available = vBuffer.remaining()
                val toCopy = minOf(uvDataSize, available)
                vBuffer.get(output, pos, toCopy)
            } else {
                // Padded rows: row-by-row bulk read of interleaved VU data
                val rowBytes = uvWidth * 2 // each row has uvWidth VU pairs
                for (row in 0 until uvHeight) {
                    vBuffer.position(row * uvRowStride)
                    val available = vBuffer.remaining()
                    val toCopy = minOf(rowBytes, available)
                    vBuffer.get(output, pos, toCopy)
                    pos += rowBytes
                }
            }
        } else {
            // Rare fallback: planar U and V (pixelStride == 1)
            // Must interleave manually, but still use row-level bulk reads
            val uRow = ByteArray(uvWidth)
            val vRow = ByteArray(uvWidth)
            for (row in 0 until uvHeight) {
                vBuffer.position(row * uvRowStride)
                vBuffer.get(vRow, 0, uvWidth)
                uBuffer.position(row * uvRowStride)
                uBuffer.get(uRow, 0, uvWidth)
                for (col in 0 until uvWidth) {
                    output[pos++] = vRow[col]
                    output[pos++] = uRow[col]
                }
            }
        }
    }
}
