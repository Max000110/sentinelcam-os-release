package com.sentinelcam.node.data

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.util.Log
import androidx.core.os.UserManagerCompat

class PreferencesManager(context: Context) {
    companion object {
        private const val TAG = "SentinelCam.Prefs"
        private const val PREFS_NAME = "sentinelcam_node_prefs"
        private const val DEFAULT_SERVER_URL = "http://161.118.183.23:8000"
        private const val DEFAULT_DEVICE_ID = "cam_livingroom_01"
    }

    private val sharedPreferences: SharedPreferences

    init {
        // Device-protected storage is ALWAYS available: before user unlock, across reboots, and in DirectBoot
        val safeContext = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && !UserManagerCompat.isUserUnlocked(context)) {
            context.createDeviceProtectedStorageContext()
        } else {
            context
        }

        sharedPreferences = safeContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        Log.d(TAG, "PreferencesManager initialized (isUserUnlocked: ${UserManagerCompat.isUserUnlocked(context)})")
    }

    var deviceId: String
        get() = sharedPreferences.getString("device_id", DEFAULT_DEVICE_ID) ?: DEFAULT_DEVICE_ID
        set(value) = sharedPreferences.edit().putString("device_id", value).apply()

    var serverUrl: String
        get() = sharedPreferences.getString("server_url", DEFAULT_SERVER_URL) ?: DEFAULT_SERVER_URL
        set(value) = sharedPreferences.edit().putString("server_url", value).apply()

    var apiKey: String
        get() = sharedPreferences.getString("api_key", "") ?: ""
        set(value) = sharedPreferences.edit().putString("api_key", value).apply()

    var isPrivacyModeEnabled: Boolean
        get() = sharedPreferences.getBoolean("privacy_mode", false)
        set(value) = sharedPreferences.edit().putBoolean("privacy_mode", value).apply()

    var isAiEnabled: Boolean
        get() = sharedPreferences.getBoolean("ai_enabled", true)
        set(value) = sharedPreferences.edit().putBoolean("ai_enabled", value).apply()

    var recordingMode: String
        get() = sharedPreferences.getString("recording_mode", "MOTION") ?: "MOTION"
        set(value) = sharedPreferences.edit().putString("recording_mode", value).apply()

    var autoStartOnBoot: Boolean
        get() = sharedPreferences.getBoolean("auto_start_on_boot", true)
        set(value) = sharedPreferences.edit().putBoolean("auto_start_on_boot", value).apply()

    var isBlackScreenModeEnabled: Boolean
        get() = sharedPreferences.getBoolean("black_screen_mode", false)
        set(value) = sharedPreferences.edit().putBoolean("black_screen_mode", value).apply()
}
