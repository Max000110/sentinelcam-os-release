package com.sentinelcam.node.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys

class PreferencesManager(context: Context) {
    private val sharedPreferences: SharedPreferences

    init {
        val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
        sharedPreferences = try {
            EncryptedSharedPreferences.create(
                "sentinelcam_secure_prefs",
                masterKeyAlias,
                context,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            context.getSharedPreferences("sentinelcam_fallback_prefs", Context.MODE_PRIVATE)
        }
    }

    var deviceId: String
        get() = sharedPreferences.getString("device_id", "cam_livingroom_01") ?: "cam_livingroom_01"
        set(value) = sharedPreferences.edit().putString("device_id", value).apply()

    var serverUrl: String
        get() = sharedPreferences.getString("server_url", "http://161.118.183.23:8000") ?: "http://161.118.183.23:8000"
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
}
