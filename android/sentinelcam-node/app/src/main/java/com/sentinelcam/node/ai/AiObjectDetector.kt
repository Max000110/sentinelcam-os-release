package com.sentinelcam.node.ai

import android.graphics.RectF
import android.util.Log
import java.util.concurrent.Executors

data class DetectedObject(
    val trackId: Int,
    val objectClass: String,
    val confidence: Float,
    val boundingBox: RectF, // Normalized 0.0 - 1.0
    val timestampMs: Long
)

class AiObjectDetector(
    private val onObjectsDetected: (List<DetectedObject>) -> Unit
) {
    private val TAG = "SentinelCam.AiDetector"
    private val executor = Executors.newSingleThreadExecutor()
    private val tracker = ObjectTracker()

    var isAiEnabled: Boolean = true
    var inferenceFps: Int = 3 // 3 FPS default
    private var lastInferenceTimeMs = 0L

    // Thermal throttling adaptation
    fun adaptInferenceToTemperature(tempCelsius: Float) {
        inferenceFps = when {
            tempCelsius > 45.0f -> 0 // AI temporarily disabled
            tempCelsius > 42.0f -> 1 // 1 FPS in high heat
            tempCelsius > 38.0f -> 2 // 2 FPS in warm state
            else -> 3                // 3 FPS normal
        }
    }

    fun processFrame(yuvBytes: ByteArray, width: Int, height: Int) {
        if (!isAiEnabled || inferenceFps <= 0) return

        val now = System.currentTimeMillis()
        val minIntervalMs = 1000L / inferenceFps
        if (now - lastInferenceTimeMs < minIntervalMs) {
            return
        }
        lastInferenceTimeMs = now

        if (executor.isShutdown) return

        executor.execute {
            try {
                // Simulated on-device lightweight TFLite inference
                // In production, this executes YOLOv8n-TFLite / MobileNet SSD interpreter
                val rawDetections = runTfliteInference(yuvBytes, width, height)
                val trackedObjects = tracker.updateTracks(rawDetections)
                if (trackedObjects.isNotEmpty()) {
                    onObjectsDetected(trackedObjects)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in AI inference pipeline: ${e.message}")
            }
        }
    }

    fun shutdown() {
        executor.shutdown()
    }

    private fun runTfliteInference(yuvBytes: ByteArray, width: Int, height: Int): List<Pair<String, RectF>> {
        // High-efficiency detection simulator for verification
        return listOf(
            Pair("person", RectF(0.25f, 0.20f, 0.45f, 0.70f))
        )
    }
}
