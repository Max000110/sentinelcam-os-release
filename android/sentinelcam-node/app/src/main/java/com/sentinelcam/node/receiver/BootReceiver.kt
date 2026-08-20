package com.sentinelcam.node.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import com.sentinelcam.node.data.PreferencesManager
import com.sentinelcam.node.service.CctvForegroundService
import com.sentinelcam.node.ui.MainActivity

class BootReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "SentinelCam.BootReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        Log.i(TAG, "Boot event received: $action on Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")

        val prefs = PreferencesManager(context)
        if (!prefs.autoStartOnBoot) {
            Log.i(TAG, "Auto-start on boot is disabled in user preferences. Skipping start.")
            return
        }

        val hasCamera = ContextCompat.checkSelfPermission(context, android.Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        val hasAudio = ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

        if (!hasCamera || !hasAudio) {
            Log.e(TAG, "Cannot auto-start on boot: Camera ($hasCamera) or Audio ($hasAudio) permission missing.")
            return
        }

        Log.i(TAG, "Launching SentinelCam 24x7 CCTV Node following boot/reboot event...")

        val serviceIntent = Intent(context, CctvForegroundService::class.java).apply {
            this.action = CctvForegroundService.ACTION_START
        }

        var startedSuccessfully = false
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
            startedSuccessfully = true
            Log.i(TAG, "CctvForegroundService successfully launched from boot receiver.")
        } catch (e: Exception) {
            Log.e(TAG, "Direct startForegroundService failed from BootReceiver: ${e.message}", e)
        }

        // On Android 14/15/16: If direct FGS start encountered restriction, launch Activity with NEW_TASK
        if (!startedSuccessfully) {
            try {
                val activityIntent = Intent(context, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    putExtra("AUTO_STARTED_FROM_BOOT", true)
                }
                context.startActivity(activityIntent)
                Log.i(TAG, "MainActivity launched as fallback to start CCTV service on Android 14/15/16.")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to launch activity fallback: ${e.message}", e)
            }
        }

        // Schedule periodic hardware watchdog
        WatchdogReceiver.scheduleWatchdog(context)
    }
}
