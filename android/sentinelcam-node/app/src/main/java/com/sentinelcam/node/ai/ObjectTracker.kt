package com.sentinelcam.node.ai

import android.graphics.RectF
import kotlin.math.max
import kotlin.math.min

class ObjectTracker {
    private var nextTrackId = 1
    private val activeTracks = mutableListOf<DetectedObject>()

    fun updateTracks(detections: List<Pair<String, RectF>>): List<DetectedObject> {
        val now = System.currentTimeMillis()
        val updatedList = mutableListOf<DetectedObject>()

        for ((objClass, rect) in detections) {
            var matchedTrackId: Int? = null
            var bestIou = 0.3f // IoU match threshold

            for (existing in activeTracks) {
                if (existing.objectClass == objClass) {
                    val iou = calculateIou(existing.boundingBox, rect)
                    if (iou > bestIou) {
                        bestIou = iou
                        matchedTrackId = existing.trackId
                    }
                }
            }

            val trackId = matchedTrackId ?: (nextTrackId++)
            val detectedObj = DetectedObject(
                trackId = trackId,
                objectClass = objClass,
                confidence = 0.92f,
                boundingBox = rect,
                timestampMs = now
            )
            updatedList.add(detectedObj)
        }

        activeTracks.clear()
        activeTracks.addAll(updatedList)
        return updatedList
    }

    private fun calculateIou(r1: RectF, r2: RectF): Float {
        val xA = max(r1.left, r2.left)
        val yA = max(r1.top, r2.top)
        val xB = min(r1.right, r2.right)
        val yB = min(r1.bottom, r2.bottom)

        val interArea = max(0.0f, xB - xA) * max(0.0f, yB - yA)
        val r1Area = (r1.right - r1.left) * (r1.bottom - r1.top)
        val r2Area = (r2.right - r2.left) * (r2.bottom - r2.top)

        val unionArea = r1Area + r2Area - interArea
        return if (unionArea > 0f) interArea / unionArea else 0f
    }
}
