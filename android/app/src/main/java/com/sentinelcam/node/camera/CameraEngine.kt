package com.sentinelcam.node.camera

import android.content.Context
import android.util.Log
import android.util.Size
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.sentinelcam.node.motion.MotionAnalyzer
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class CameraEngine(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
    private val onMotionDetected: (Float) -> Unit
) {
    private val TAG = "SentinelCam.CameraEngine"
    private var cameraProvider: ProcessCameraProvider? = null
    private var camera: Camera? = null
    private var cameraControl: CameraControl? = null
    private val cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()

    var isBackLens: Boolean = true
        private set
    var isTorchOn: Boolean = false
        private set

    fun startCamera(
        previewSurfaceProvider: Preview.SurfaceProvider? = null,
        targetResolution: Size = Size(1920, 1080),
        onInitialized: () -> Unit = {}
    ) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            cameraProvider = cameraProviderFuture.get()
            bindCameraUseCases(previewSurfaceProvider, targetResolution)
            onInitialized()
        }, ContextCompat.getMainExecutor(context))
    }

    private fun bindCameraUseCases(
        previewSurfaceProvider: Preview.SurfaceProvider?,
        targetResolution: Size
    ) {
        val provider = cameraProvider ?: return
        val lensFacing = if (isBackLens) CameraSelector.LENS_FACING_BACK else CameraSelector.LENS_FACING_FRONT
        val cameraSelector = CameraSelector.Builder()
            .requireLensFacing(lensFacing)
            .build()

        val imageAnalysis = ImageAnalysis.Builder()
            .setTargetResolution(Size(640, 480)) // Downscaled for low-power continuous motion analysis
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
            .also {
                it.setAnalyzer(cameraExecutor, MotionAnalyzer { confidence ->
                    onMotionDetected(confidence)
                })
            }

        provider.unbindAll()

        try {
            if (previewSurfaceProvider != null) {
                val preview = Preview.Builder()
                    .setTargetResolution(targetResolution)
                    .build()
                    .also {
                        it.setSurfaceProvider(previewSurfaceProvider)
                    }
                camera = provider.bindToLifecycle(lifecycleOwner, cameraSelector, preview, imageAnalysis)
            } else {
                // Headless / 24x7 background execution without UI preview
                camera = provider.bindToLifecycle(lifecycleOwner, cameraSelector, imageAnalysis)
            }
            cameraControl = camera?.cameraControl
            Log.i(TAG, "Camera bound successfully (Lens: ${if (isBackLens) "BACK" else "FRONT"})")
        } catch (exc: Exception) {
            Log.e(TAG, "Failed to bind camera use cases: ${exc.message}", exc)
        }
    }

    fun toggleTorch(enable: Boolean) {
        if (!isBackLens) return // Front camera has no flash torch
        cameraControl?.enableTorch(enable)
        isTorchOn = enable
        Log.i(TAG, "Torch state changed: $enable")
    }

    fun switchCamera(previewSurfaceProvider: Preview.SurfaceProvider? = null) {
        isBackLens = !isBackLens
        bindCameraUseCases(previewSurfaceProvider, Size(1920, 1080))
    }

    fun shutdown() {
        cameraProvider?.unbindAll()
        cameraExecutor.shutdown()
    }
}
