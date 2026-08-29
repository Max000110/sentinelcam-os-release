package com.sentinelcam.node.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
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

        Log.i(TAG, "Launching SentinelCam 24x7 CCTV Node following boot/reboot event ($action)...")

        val serviceIntent = Intent(context, CctvForegroundService::class.java).apply {
            this.action = CctvForegroundService.ACTION_START
        }

        var startedViaFgs = false
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
            startedViaFgs = true
            Log.i(TAG, "CctvForegroundService started via startForegroundService from BootReceiver.")
        } catch (e: Exception) {
            Log.w(TAG, "Direct startForegroundService from BootReceiver met restriction: ${e.message}")
        }

        // On Android 14/15/16 (e.g. Vivo V40 5G), launch MainActivity to guarantee camera pipeline start
        try {
            val activityIntent = Intent(context, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                putExtra("AUTO_STARTED_FROM_BOOT", true)
            }
            context.startActivity(activityIntent)
            Log.i(TAG, "MainActivity launched from BootReceiver for reliable camera activation.")
        } catch (e: Exception) {
            Log.e(TAG, "Failed launching MainActivity from BootReceiver: ${e.message}")
        }

        // Schedule periodic hardware watchdog
        WatchdogReceiver.scheduleWatchdog(context)
    }
}
