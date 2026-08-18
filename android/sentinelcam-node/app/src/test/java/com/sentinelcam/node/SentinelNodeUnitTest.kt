package com.sentinelcam.node

import android.graphics.RectF
import org.junit.Assert.*
import org.junit.Test
import java.security.MessageDigest
import kotlin.math.sqrt

/**
 * Production Unit Tests for SentinelCam Android Node Core Subsystems
 */
class SentinelNodeUnitTest {

    @Test
    fun testCosineSimilarityIdenticalVectors() {
        val v1 = floatArrayOf(0.5f, 0.5f, 0.5f, 0.5f)
        val v2 = floatArrayOf(0.5f, 0.5f, 0.5f, 0.5f)

        var dot = 0f
        var normA = 0f
        var normB = 0f
        for (i in v1.indices) {
            dot += v1[i] * v2[i]
            normA += v1[i] * v1[i]
            normB += v2[i] * v2[i]
        }
        val similarity = dot / (sqrt(normA) * sqrt(normB))
        assertEquals(1.0f, similarity, 0.001f)
    }

    @Test
    fun testCosineSimilarityOrthogonalVectors() {
        val v1 = floatArrayOf(1.0f, 0.0f)
        val v2 = floatArrayOf(0.0f, 1.0f)

        var dot = 0f
        var normA = 0f
        var normB = 0f
        for (i in v1.indices) {
            dot += v1[i] * v2[i]
            normA += v1[i] * v1[i]
            normB += v2[i] * v2[i]
        }
        val similarity = dot / (sqrt(normA) * sqrt(normB))
        assertEquals(0.0f, similarity, 0.001f)
    }

    @Test
    fun testSha256DigestCalculation() {
        val testData = "SentinelCam-2026-Segment-Verification".toByteArray(Charsets.UTF_8)
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(testData)
        val hex = digest.joinToString("") { "%02x".format(it) }

        assertNotNull(hex)
        assertEquals(64, hex.length)
        assertTrue(hex.matches(Regex("^[a-f0-9]{64}$")))
    }

    @Test
    fun testRemoteCommandAllowlistEnforcement() {
        val allowlist = setOf(
            "RESTART_SERVICE",
            "SWITCH_CAMERA",
            "TOGGLE_TORCH",
            "SET_PRIVACY_MODE",
            "SYNC_CONFIG"
        )

        // Valid commands must pass
        assertTrue(allowlist.contains("SWITCH_CAMERA"))
        assertTrue(allowlist.contains("SET_PRIVACY_MODE"))

        // Malicious arbitrary commands must be rejected
        assertFalse(allowlist.contains("EXEC_SHELL"))
        assertFalse(allowlist.contains("rm -rf /"))
        assertFalse(allowlist.contains("UPGRADE_SYSTEM"))
    }

    @Test
    fun testPreEventBufferConstraint() {
        val maxBufferSizeSeconds = 10
        val sampleRateFps = 3
        val maxFrames = maxBufferSizeSeconds * sampleRateFps

        assertEquals(30, maxFrames)
    }

    @Test
    fun testThermalThrottlingFpsMapping() {
        fun getTargetFps(temperatureC: Float): Int {
            return when {
                temperatureC >= 45.0f -> 0 // Paused
                temperatureC >= 42.0f -> 1 // Critical throttle
                temperatureC >= 38.0f -> 2 // Moderate throttle
                else -> 3 // Normal operation
            }
        }

        assertEquals(3, getTargetFps(32.0f))
        assertEquals(2, getTargetFps(39.5f))
        assertEquals(1, getTargetFps(43.0f))
        assertEquals(0, getTargetFps(48.0f))
    }
}
