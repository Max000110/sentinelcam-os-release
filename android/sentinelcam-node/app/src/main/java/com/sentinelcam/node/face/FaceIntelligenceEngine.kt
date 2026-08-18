package com.sentinelcam.node.face

import android.content.Context
import android.graphics.RectF
import android.util.Log
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.sqrt

data class RecognizedFace(
    val personId: String?,
    val displayName: String,
    val isKnown: Boolean,
    val similarity: Float,
    val boundingBox: RectF
)

class FaceIntelligenceEngine(private val context: Context) {
    private val TAG = "SentinelCam.FaceEngine"

    var isFaceRecognitionEnabled: Boolean = false
    var isPrivacyModeEnabled: Boolean = false

    // Local in-memory enrolled biometric templates (Key: person_id -> FloatArray embedding)
    private val enrolledProfiles = ConcurrentHashMap<String, Pair<String, FloatArray>>()
    private val MATCH_THRESHOLD = 0.75f // Cosine similarity threshold

    fun enrollKnownPerson(personId: String, displayName: String, embedding: FloatArray) {
        enrolledProfiles[personId] = Pair(displayName, embedding)
        Log.i(TAG, "Enrolled known face profile: $displayName ($personId)")
    }

    fun removeProfile(personId: String) {
        enrolledProfiles.remove(personId)
    }

    fun setPrivacyMode(enabled: Boolean) {
        isPrivacyModeEnabled = enabled
        if (enabled) {
            isFaceRecognitionEnabled = false
            Log.i(TAG, "Privacy Mode ACTIVATED. Face intelligence halted.")
        }
    }

    fun processFaceDetection(faceBoundingBox: RectF, extractedEmbedding: FloatArray?): RecognizedFace {
        if (isPrivacyModeEnabled || !isFaceRecognitionEnabled || extractedEmbedding == null) {
            return RecognizedFace(
                personId = null,
                displayName = if (isPrivacyModeEnabled) "Privacy Mode" else "Face Detected",
                isKnown = false,
                similarity = 0.0f,
                boundingBox = faceBoundingBox
            )
        }

        var bestMatchId: String? = null
        var bestMatchName = "Unknown Person"
        var highestSimilarity = 0.0f

        for ((id, pair) in enrolledProfiles) {
            val (name, storedVector) = pair
            val similarity = calculateCosineSimilarity(extractedEmbedding, storedVector)
            if (similarity > highestSimilarity && similarity >= MATCH_THRESHOLD) {
                highestSimilarity = similarity
                bestMatchId = id
                bestMatchName = name
            }
        }

        val isKnown = bestMatchId != null
        return RecognizedFace(
            personId = bestMatchId,
            displayName = if (isKnown) bestMatchName else "Unknown Face",
            isKnown = isKnown,
            similarity = highestSimilarity,
            boundingBox = faceBoundingBox
        )
    }

    private fun calculateCosineSimilarity(v1: FloatArray, v2: FloatArray): Float {
        if (v1.size != v2.size) return 0f
        var dotProduct = 0.0f
        var normA = 0.0f
        var normB = 0.0f
        for (i in v1.indices) {
            dotProduct += v1[i] * v2[i]
            normA += v1[i] * v1[i]
            normB += v2[i] * v2[i]
        }
        return if (normA > 0 && normB > 0) dotProduct / (sqrt(normA) * sqrt(normB)) else 0f
    }
}
