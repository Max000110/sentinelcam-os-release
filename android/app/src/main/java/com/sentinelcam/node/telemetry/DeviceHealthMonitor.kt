package com.sentinelcam.node.telemetry

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.Environment
import android.os.StatFs
import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

data class DeviceHealthStats(
    val batteryLevel: Int,
    val isCharging: String,
    val temperatureC: Float,
    val storageFreeMb: Long,
    val storageTotalMb: Long,
    val networkType: String,
    val wifiRssiDbm: Int,
    val uptimeSeconds: Long
)

class DeviceHealthMonitor(
    private val context: Context,
    private val serverBaseUrl: String,
    private val deviceId: String
) {
    private val TAG = "SentinelCam.Health"
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()
    private val gson = Gson()
    private var job: Job? = null
    private val startTimeMs = System.currentTimeMillis()

    fun start(scope: CoroutineScope, intervalSeconds: Long = 60L) {
        job?.cancel()
        job = scope.launch(Dispatchers.IO) {
            while (isActive) {
                try {
                    val stats = collectCurrentStats()
                    sendHeartbeat(stats)
                } catch (e: Exception) {
                    Log.e(TAG, "Error sending telemetry: ${e.message}")
                }
                delay(intervalSeconds * 1000)
            }
        }
    }

    fun collectCurrentStats(): DeviceHealthStats {
        val batteryIntent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: 100
        val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: 100
        val batteryPct = if (level >= 0 && scale > 0) ((level.toFloat() / scale.toFloat()) * 100).toInt() else 100

        val plugged = batteryIntent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1) ?: 0
        val chargingType = when (plugged) {
            BatteryManager.BATTERY_PLUGGED_AC -> "AC"
            BatteryManager.BATTERY_PLUGGED_USB -> "USB"
            BatteryManager.BATTERY_PLUGGED_WIRELESS -> "WIRELESS"
            else -> "BATTERY"
        }

        val rawTemp = batteryIntent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0
        val tempCelsius = rawTemp / 10.0f

        // Storage
        val statFs = StatFs(Environment.getDataDirectory().path)
        val freeMb = (statFs.availableBlocksLong * statFs.blockSizeLong) / (1024 * 1024)
        val totalMb = (statFs.blockCountLong * statFs.blockSizeLong) / (1024 * 1024)

        // Network
        val connManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val activeNetwork = connManager.activeNetwork
        val caps = connManager.getNetworkCapabilities(activeNetwork)
        val netType = when {
            caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true -> "WIFI"
            caps?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true -> "CELLULAR"
            caps?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) == true -> "ETHERNET"
            else -> "UNKNOWN"
        }

        var rssiDbm = -60
        if (netType == "WIFI") {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val wifiInfo = wifiManager.connectionInfo
            rssiDbm = wifiInfo.rssi
        }

        val uptimeSec = (System.currentTimeMillis() - startTimeMs) / 1000

        return DeviceHealthStats(
            batteryLevel = batteryPct,
            isCharging = chargingType,
            temperatureC = tempCelsius,
            storageFreeMb = freeMb,
            storageTotalMb = totalMb,
            networkType = netType,
            wifiRssiDbm = rssiDbm,
            uptimeSeconds = uptimeSec
        )
    }

    private fun sendHeartbeat(stats: DeviceHealthStats) {
        val url = "$serverBaseUrl/api/v1/telemetry/heartbeat"
        val json = JsonObject().apply {
            addProperty("device_id", deviceId)
            addProperty("battery_level", stats.batteryLevel)
            addProperty("is_charging", stats.isCharging)
            addProperty("temperature_c", stats.temperatureC)
            addProperty("storage_free_mb", stats.storageFreeMb)
            addProperty("storage_total_mb", stats.storageTotalMb)
            addProperty("network_type", stats.networkType)
            addProperty("wifi_rssi_dbm", stats.wifiRssiDbm)
            addProperty("current_fps", 30.0)
            addProperty("current_bitrate_kbps", 1800)
            addProperty("uptime_seconds", stats.uptimeSeconds)
        }

        val body = gson.toJson(json).toRequestBody("application/json".toMediaType())
        val req = Request.Builder().url(url).post(body).build()
        client.newCall(req).execute().use { response ->
            if (response.isSuccessful) {
                Log.d(TAG, "Heartbeat sent successfully (Battery: ${stats.batteryLevel}%, Temp: ${stats.temperatureC}°C)")
            } else {
                Log.w(TAG, "Heartbeat server response code: ${response.code}")
            }
        }
    }

    fun stop() {
        job?.cancel()
    }
}
