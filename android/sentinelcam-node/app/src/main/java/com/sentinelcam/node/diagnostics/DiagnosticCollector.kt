package com.sentinelcam.node.diagnostics

import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.StatFs
import com.google.gson.Gson
import com.google.gson.JsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

class DiagnosticCollector(
    private val context: Context,
    private val serverUrl: String,
    private val deviceId: String
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .build()
    private val gson = Gson()

    fun uploadDiagnosticReport(
        webrtcRttMs: Int,
        packetLossPct: Float,
        batteryPct: Int,
        tempCelsius: Float,
        recentErrors: List<String>
    ) {
        val statFs = StatFs(Environment.getDataDirectory().path)
        val freeMb = (statFs.availableBlocksLong * statFs.blockSizeLong) / (1024 * 1024)

        val metrics = JsonObject().apply {
            addProperty("app_version", "1.0.0")
            addProperty("android_version", Build.VERSION.RELEASE)
            addProperty("device_model", Build.MODEL)
            addProperty("battery_level", batteryPct)
            addProperty("temperature_c", tempCelsius)
            addProperty("storage_free_mb", freeMb)
            addProperty("webrtc_rtt_ms", webrtcRttMs)
            addProperty("packet_loss_pct", packetLossPct)
            add("recent_errors", gson.toJsonTree(recentErrors))
        }

        val payload = JsonObject().apply {
            addProperty("device_id", deviceId)
            add("metrics", metrics)
        }

        val body = gson.toJson(payload).toRequestBody("application/json".toMediaType())
        val request = Request.Builder().url("$serverUrl/api/v1/diagnostics").post(body).build()
        client.newCall(request).execute().close()
    }
}
