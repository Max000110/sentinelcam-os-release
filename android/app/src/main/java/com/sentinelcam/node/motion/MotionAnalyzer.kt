package com.sentinelcam.node.motion

import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import java.nio.ByteBuffer
import kotlin.math.abs

class MotionAnalyzer(
    private val sensitivityThreshold: Int = 35, // Delta threshold
    private val minPixelsChangedRatio: Float = 0.04f, // 4% of screen changed
    private val onMotionDetected: (Float) -> Unit
) : ImageAnalysis.Analyzer {

    private var previousLuminanceGrid: IntArray? = null
    private val GRID_WIDTH = 32
    private val GRID_HEIGHT = 24
    private var lastTriggerTimeMs = 0L
    private val COOLDOWN_MS = 2500L // Prevent alert spamming

    override fun analyze(image: ImageProxy) {
        val currentTime = System.currentTimeMillis()
        val yPlane = image.planes[0]
        val buffer = yPlane.buffer
        
        val width = image.width
        val height = image.height
        val rowStride = yPlane.rowStride
        val pixelStride = yPlane.pixelStride

        val currentGrid = extractLuminanceGrid(buffer, width, height, rowStride, pixelStride)

        previousLuminanceGrid?.let { prevGrid ->
            var changedBlocks = 0
            val totalBlocks = GRID_WIDTH * GRID_HEIGHT

            for (i in 0 until totalBlocks) {
                val delta = abs(currentGrid[i] - prevGrid[i])
                if (delta > sensitivityThreshold) {
                    changedBlocks++
                }
            }

            val ratio = changedBlocks.toFloat() / totalBlocks.toFloat()
            if (ratio >= minPixelsChangedRatio && (currentTime - lastTriggerTimeMs > COOLDOWN_MS)) {
                lastTriggerTimeMs = currentTime
                val confidence = (ratio / 0.25f).coerceIn(0.1f, 1.0f)
                onMotionDetected(confidence)
            }
        }

        previousLuminanceGrid = currentGrid
        image.close()
    }

    private fun extractLuminanceGrid(
        buffer: ByteBuffer,
        width: Int,
        height: Int,
        rowStride: Int,
        pixelStride: Int
    ): IntArray {
        val grid = IntArray(GRID_WIDTH * GRID_HEIGHT)
        val stepX = width / GRID_WIDTH
        val stepY = height / GRID_HEIGHT

        for (gy in 0 until GRID_HEIGHT) {
            for (gx in 0 until GRID_WIDTH) {
                val px = gx * stepX
                val py = gy * stepY
                val offset = py * rowStride + px * pixelStride
                if (offset < buffer.limit()) {
                    val luminance = buffer.get(offset).toInt() and 0xFF
                    grid[gy * GRID_WIDTH + gx] = luminance
                }
            }
        }
        return grid
    }
}
