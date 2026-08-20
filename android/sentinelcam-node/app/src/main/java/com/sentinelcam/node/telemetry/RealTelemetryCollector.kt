package com.sentinelcam.node.telemetry

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.os.SystemClock
import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

class RealTelemetryCollector(
    private val context: Context,
    private val serverUrl: String,
    private val deviceId: String,
    private val getFpsProvider: () -> Int = { 30 }
) {
    companion object {
        private const val TAG = "SentinelCam.Telemetry"
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(3, TimeUnit.SECONDS)
        .build()
    private val gson = Gson()
    private var scheduler: ScheduledExecutorService? = null
    private val startTimeMs = SystemClock.elapsedRealtime()
    private var activeServerUrl: String = serverUrl

    private fun getCandidateUrls(): List<String> {
        val list = mutableListOf(serverUrl)
        if (!list.contains("http://127.0.0.1:8000")) list.add("http://127.0.0.1:8000")
        if (!list.contains("http://100.65.29.37:8000")) list.add("http://100.65.29.37:8000")
        return list
    }

    fun start(intervalSeconds: Long = 10) {
        stop()
        scheduler = Executors.newSingleThreadScheduledExecutor()
        scheduler?.scheduleWithFixedDelay({
            try {
                sendHeartbeat()
            } catch (e: Exception) {
                Log.w(TAG, "Heartbeat failed: ${e.message}")
            }
        }, 1, intervalSeconds, TimeUnit.SECONDS)
        Log.i(TAG, "RealTelemetryCollector started (interval: ${intervalSeconds}s)")
    }

    fun registerDeviceSync(): Boolean {
        val deviceModelName = "${Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} ${Build.MODEL}"
        val payload = JsonObject().apply {
            addProperty("device_id", deviceId)
            addProperty("name", "$deviceModelName ($deviceId)")
            addProperty("resolution", "1080p")
            addProperty("target_fps", 30)
            addProperty("target_bitrate_kbps", 2000)
            addProperty("lens_facing", "BACK")
            addProperty("torch_enabled", false)
            addProperty("motion_detection_enabled", true)
            addProperty("motion_sensitivity", 50)
        }

        val body = gson.toJson(payload).toRequestBody("application/json".toMediaType())

        for (candidate in getCandidateUrls()) {
            try {
                val request = Request.Builder()
                    .url("$candidate/api/v1/devices/register")
                    .post(body)
                    .build()

                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        activeServerUrl = candidate
                        Log.i(TAG, "Device registration successful via $candidate: $deviceModelName (HTTP ${response.code})")
                        return true
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Registration attempt failed on $candidate: ${e.message}")
            }
        }
        return false
    }

    fun collectMetrics(): JsonObject {
        // 1. Real Battery & Charging & Temperature
        var batteryPct = 85
        var isCharging = "DISCHARGING"
        var tempCelsius = 30.0f

        try {
            val iFilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            val batteryStatus = context.registerReceiver(null, iFilter)
            batteryStatus?.let { intent ->
                val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                if (level >= 0 && scale > 0) {
                    batteryPct = (level * 100 / scale.toFloat()).toInt()
                }

                val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                val chargePlug = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1)
                isCharging = when {
                    status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL -> {
                        when (chargePlug) {
                            BatteryManager.BATTERY_PLUGGED_AC -> "AC"
                            BatteryManager.BATTERY_PLUGGED_USB -> "USB"
                            BatteryManager.BATTERY_PLUGGED_WIRELESS -> "WIRELESS"
                            else -> "CHARGING"
                        }
                    }
                    else -> "DISCHARGING"
                }

                val rawTemp = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0)
                if (rawTemp > 0) {
                    tempCelsius = rawTemp / 10.0f
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Battery info error: ${e.message}")
        }

        // 2. Real Storage
        var storageFreeMb = 10000
        var storageTotalMb = 64000
        try {
            val statFs = StatFs(Environment.getDataDirectory().path)
            val blockSize = statFs.blockSizeLong
            val totalBlocks = statFs.blockCountLong
            val availableBlocks = statFs.availableBlocksLong
            storageFreeMb = ((availableBlocks * blockSize) / (1024 * 1024)).toInt()
            storageTotalMb = ((totalBlocks * blockSize) / (1024 * 1024)).toInt()
        } catch (e: Exception) {
            Log.w(TAG, "Storage info error: ${e.message}")
        }

        // 3. Real Wi-Fi RSSI & Network Type
        var wifiRssiDbm = -55
        var networkType = "UNKNOWN"
        try {
            val connMgr = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val network = connMgr.activeNetwork
            val caps = connMgr.getNetworkCapabilities(network)

            if (caps != null) {
                networkType = when {
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "WIFI"
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "CELLULAR"
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "ETHERNET"
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> "VPN"
                    else -> "OTHER"
                }
            }

            val wifiMgr = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val wifiInfo = wifiMgr.connectionInfo
            if (wifiInfo != null && wifiInfo.rssi != 0) {
                wifiRssiDbm = wifiInfo.rssi
            }
        } catch (e: Exception) {
            Log.w(TAG, "Network info error: ${e.message}")
        }

        val uptimeSeconds = ((SystemClock.elapsedRealtime() - startTimeMs) / 1000).toInt()
        val currentFps = getFpsProvider().toFloat()

        return JsonObject().apply {
            addProperty("device_id", deviceId)
            addProperty("battery_level", batteryPct)
            addProperty("is_charging", isCharging)
            addProperty("temperature_c", tempCelsius)
            addProperty("storage_free_mb", storageFreeMb)
            addProperty("storage_total_mb", storageTotalMb)
            addProperty("network_type", networkType)
            addProperty("wifi_rssi_dbm", wifiRssiDbm)
            addProperty("current_fps", currentFps)
            addProperty("current_bitrate_kbps", 2000)
            addProperty("uptime_seconds", uptimeSeconds)
            addProperty("device_model", "${Build.MANUFACTURER} ${Build.MODEL}")
            addProperty("android_version", Build.VERSION.RELEASE)
        }
    }

    private fun sendHeartbeat() {
        val metrics = collectMetrics()
        val body = gson.toJson(metrics).toRequestBody("application/json".toMediaType())

        for (candidate in getCandidateUrls()) {
            try {
                val request = Request.Builder()
                    .url("$candidate/api/v1/telemetry/heartbeat")
                    .post(body)
                    .build()

                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        activeServerUrl = candidate
                        return
                    }
                }
            } catch (e: Exception) {
                // Continue to next candidate
            }
        }
    }

    fun stop() {
        try {
            scheduler?.shutdownNow()
            scheduler = null
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping scheduler: ${e.message}")
        }
    }
}
