package com.sentinelcam.node.data

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import androidx.core.os.UserManagerCompat
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class PreferencesManager(context: Context) {
    private val sharedPreferences: SharedPreferences

    init {
        val safeContext = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && !UserManagerCompat.isUserUnlocked(context)) {
            context.createDeviceProtectedStorageContext()
        } else {
            context
        }

        sharedPreferences = try {
            val masterKey = MasterKey.Builder(safeContext)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()

            EncryptedSharedPreferences.create(
                safeContext,
                "sentinelcam_secure_prefs",
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            safeContext.getSharedPreferences("sentinelcam_fallback_prefs", Context.MODE_PRIVATE)
        }
    }

    var deviceId: String
        get() = sharedPreferences.getString("device_id", "cam_livingroom_01") ?: "cam_livingroom_01"
        set(value) = sharedPreferences.edit().putString("device_id", value).apply()

    var serverUrl: String
        get() = sharedPreferences.getString("server_url", "http://100.65.29.37:8000") ?: "http://100.65.29.37:8000"
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
