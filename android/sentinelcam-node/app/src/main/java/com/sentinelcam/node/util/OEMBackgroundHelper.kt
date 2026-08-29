package com.sentinelcam.node.util

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log

object OEMBackgroundHelper {
    private const val TAG = "SentinelCam.OEMHelper"

    fun isVivoOrIqoo(): Boolean {
        val manufacturer = Build.MANUFACTURER.lowercase()
        val brand = Build.BRAND.lowercase()
        return manufacturer.contains("vivo") || manufacturer.contains("iqoo") ||
                brand.contains("vivo") || brand.contains("iqoo")
    }

    fun isXiaomiOrRedmi(): Boolean {
        val manufacturer = Build.MANUFACTURER.lowercase()
        val brand = Build.BRAND.lowercase()
        return manufacturer.contains("xiaomi") || manufacturer.contains("redmi") ||
                brand.contains("xiaomi") || brand.contains("redmi")
    }

    fun isSamsung(): Boolean {
        val manufacturer = Build.MANUFACTURER.lowercase()
        return manufacturer.contains("samsung")
    }

    /**
     * Attempts to open Vivo / Funtouch OS / OriginOS native Auto-Start manager.
     */
    fun openVivoAutoStart(context: Context): Boolean {
        val intents = listOf(
            Intent().setComponent(ComponentName("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity")),
            Intent().setComponent(ComponentName("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.BgStartUpManager")),
            Intent().setComponent(ComponentName("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity")),
            Intent().setComponent(ComponentName("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.PurviewTabActivity")),
            Intent().setComponent(ComponentName("com.vivo.abe", "com.vivo.applicationbehaviorengine.ui.ExcessivePowerManagerActivity"))
        )

        for (intent in intents) {
            try {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                Log.i(TAG, "Successfully opened Vivo Auto-Start screen via ${intent.component}")
                return true
            } catch (e: Exception) {
                Log.d(TAG, "Failed intent ${intent.component}: ${e.message}")
            }
        }

        return openAppDetails(context)
    }

    /**
     * Attempts to open Vivo High Background Power Consumption Whitelist.
     */
    fun openVivoHighPowerConsumption(context: Context): Boolean {
        val intents = listOf(
            Intent().setComponent(ComponentName("com.vivo.abe", "com.vivo.applicationbehaviorengine.ui.ExcessivePowerManagerActivity")),
            Intent().setComponent(ComponentName("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity")),
            Intent().setComponent(ComponentName("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.PurviewTabActivity")),
            Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
        )

        for (intent in intents) {
            try {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                Log.i(TAG, "Successfully opened Vivo Power screen via ${intent.component}")
                return true
            } catch (e: Exception) {
                Log.d(TAG, "Failed intent ${intent.component}: ${e.message}")
            }
        }

        return openAppDetails(context)
    }

    /**
     * Attempts to open generic App Details settings.
     */
    fun openAppDetails(context: Context): Boolean {
        return try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open application details settings: ${e.message}")
            false
        }
    }
}
