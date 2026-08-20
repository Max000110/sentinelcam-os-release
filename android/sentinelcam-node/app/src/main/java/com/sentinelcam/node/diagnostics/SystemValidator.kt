package com.sentinelcam.node.diagnostics

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.gson.Gson
import com.google.gson.JsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.TimeUnit

data class ValidationItem(
    val title: String,
    val isPassed: Boolean,
    val details: String,
    val latencyMs: Long = 0
)

class SystemValidator(
    private val context: Context,
    private val serverUrl: String,
    private val deviceId: String
) {
    companion object {
        private const val TAG = "SentinelCam.Validator"
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(3, TimeUnit.SECONDS)
        .build()
    private val gson = Gson()

    private fun getCandidateUrls(): List<String> {
        val list = mutableListOf(serverUrl)
        if (!list.contains("http://127.0.0.1:8000")) list.add("http://127.0.0.1:8000")
        if (!list.contains("http://100.65.29.37:8000")) list.add("http://100.65.29.37:8000")
        return list
    }

    fun runFullDiagnosticSuite(): List<ValidationItem> {
        val results = mutableListOf<ValidationItem>()

        // 1. API Reachability Test
        var apiPassed = false
        var apiLatency = 0L
        var apiDetails = "Unreachable"
        var activeCandidate = serverUrl

        for (candidate in getCandidateUrls()) {
            try {
                val start = System.currentTimeMillis()
                val request = Request.Builder().url("$candidate/api/v1/devices").build()
                client.newCall(request).execute().use { response ->
                    apiLatency = System.currentTimeMillis() - start
                    if (response.isSuccessful) {
                        apiPassed = true
                        activeCandidate = candidate
                        apiDetails = "HTTP ${response.code} OK (${apiLatency}ms via $candidate)"
                        return@use
                    }
                }
                if (apiPassed) break
            } catch (e: Exception) {
                apiDetails = "Connection failed: ${e.message}"
            }
        }
        results.add(ValidationItem("API Reachability", apiPassed, apiDetails, apiLatency))

        // 2. Device Registration Test
        var regPassed = false
        var regDetails = "Failed"
        try {
            val devModel = "${Build.MANUFACTURER} ${Build.MODEL}"
            val payload = JsonObject().apply {
                addProperty("device_id", deviceId)
                addProperty("name", "$devModel ($deviceId)")
                addProperty("resolution", "1080p")
            }
            val body = gson.toJson(payload).toRequestBody("application/json".toMediaType())
            val request = Request.Builder().url("$serverUrl/api/v1/devices/register").post(body).build()
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    regPassed = true
                    regDetails = "Device '$devModel' registered (HTTP ${response.code})"
                } else {
                    regDetails = "HTTP ${response.code}"
                }
            }
        } catch (e: Exception) {
            regDetails = "Error: ${e.message}"
        }
        results.add(ValidationItem("Device Registration", regPassed, regDetails))

        // 3. Heartbeat & Telemetry Test
        var hbPassed = false
        var hbDetails = "Failed"
        try {
            val payload = JsonObject().apply {
                addProperty("device_id", deviceId)
                addProperty("battery_level", 85)
                addProperty("temperature_c", 30.5f)
                addProperty("network_type", "WIFI")
            }
            val body = gson.toJson(payload).toRequestBody("application/json".toMediaType())
            val request = Request.Builder().url("$serverUrl/api/v1/telemetry/heartbeat").post(body).build()
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    hbPassed = true
                    hbDetails = "Telemetry synchronized (HTTP ${response.code})"
                } else {
                    hbDetails = "HTTP ${response.code}"
                }
            }
        } catch (e: Exception) {
            hbDetails = "Error: ${e.message}"
        }
        results.add(ValidationItem("Heartbeat & Telemetry", hbPassed, hbDetails))

        // 4. STUN/TURN ICE Servers Reachability
        var turnPassed = false
        var turnDetails = "No ICE servers"
        try {
            val request = Request.Builder().url("$serverUrl/api/v1/stream/ice-servers").build()
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val bodyStr = response.body?.string() ?: ""
                    val json = gson.fromJson(bodyStr, JsonObject::class.java)
                    val servers = json.getAsJsonArray("iceServers")
                    turnPassed = servers != null && servers.size() > 0
                    turnDetails = "Found ${servers?.size() ?: 0} ICE Server configs"
                } else {
                    turnDetails = "HTTP ${response.code}"
                }
            }
        } catch (e: Exception) {
            turnDetails = "Error: ${e.message}"
        }
        results.add(ValidationItem("STUN / TURN Server Config", turnPassed, turnDetails))

        // 5. Camera Permission
        val camPerm = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        results.add(ValidationItem("Camera Permission", camPerm, if (camPerm) "GRANTED" else "DENIED"))

        // 6. Microphone Permission
        val micPerm = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        results.add(ValidationItem("Microphone Permission", micPerm, if (micPerm) "GRANTED" else "DENIED"))

        // 7. Battery Optimization (Doze Bypass)
        var dozeBypassed = true
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            dozeBypassed = pm.isIgnoringBatteryOptimizations(context.packageName)
        }
        results.add(ValidationItem("Doze Battery Optimization", dozeBypassed, if (dozeBypassed) "BYPASS ACTIVE" else "ACTION REQUIRED (Disable Doze)"))

        // 8. Device Hardware Identity
        val deviceIdentity = "${Build.MANUFACTURER} ${Build.MODEL} (Android ${Build.VERSION.RELEASE}, API ${Build.VERSION.SDK_INT})"
        results.add(ValidationItem("Device Identity", true, deviceIdentity))

        return results
    }
}
