package com.sentinelcam.node.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.google.gson.JsonObject
import com.sentinelcam.node.camera.CameraEngine
import com.sentinelcam.node.telemetry.DeviceHealthMonitor
import com.sentinelcam.node.ui.MainActivity
import com.sentinelcam.node.webrtc.SignalingClient
import com.sentinelcam.node.webrtc.SignalingListener
import com.sentinelcam.node.webrtc.WebRtcClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.webrtc.*

class CctvForegroundService : LifecycleService() {
    private val TAG = "SentinelCam.Service"
    private val NOTIFICATION_ID = 1001
    private val CHANNEL_ID = "sentinelcam_cctv_channel"

    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null

    private var cameraEngine: CameraEngine? = null
    private var webRtcClient: WebRtcClient? = null
    private var signalingClient: SignalingClient? = null
    private var healthMonitor: DeviceHealthMonitor? = null
    private var rootEglBase: EglBase? = null

    private var serverUrl: String = "http://127.0.0.1:8000"
    private var deviceId: String = "cam_livingroom_01"

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "Creating 24x7 CCTV Foreground Service...")
        acquireWakeLocks()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildForegroundNotification("Initializing SentinelCam node..."))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        intent?.let {
            serverUrl = it.getStringExtra("EXTRA_SERVER_URL") ?: serverUrl
            deviceId = it.getStringExtra("EXTRA_DEVICE_ID") ?: deviceId
        }

        initializeNode()
        return START_STICKY
    }

    private fun acquireWakeLocks() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "SentinelCam::WakeLock").apply {
            setReferenceCounted(false)
            acquire()
        }

        val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        wifiLock = wifiManager.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "SentinelCam::WifiLock").apply {
            setReferenceCounted(false)
            acquire()
        }
        Log.i(TAG, "Acquired Partial WakeLock and High Perf WiFi Lock")
    }

    private fun initializeNode() {
        rootEglBase = EglBase.create()
        
        // 1. Camera Engine
        cameraEngine = CameraEngine(this, this) { confidence ->
            Log.i(TAG, "Motion detected! Confidence: $confidence")
            // Upload motion event if required
        }
        cameraEngine?.startCamera()

        // 2. WebRTC Client
        webRtcClient = WebRtcClient(this, rootEglBase!!.eglBaseContext) { iceCandidate ->
            signalingClient?.sendIceCandidate(iceCandidate)
        }

        // 3. Signaling Client
        signalingClient = SignalingClient(serverUrl, deviceId, object : SignalingListener {
            override fun onConnected() {
                updateNotification("Streaming Node Active (Online)")
            }

            override fun onDisconnected() {
                updateNotification("Reconnecting to VPS...")
            }

            override fun onViewerJoined() {
                Log.i(TAG, "Viewer joined room. Initiating WebRTC Peer Connection & Offer...")
                val iceServers = listOf(
                    PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer()
                )
                webRtcClient?.createPeerConnection(iceServers, object : PeerConnection.Observer {
                    override fun onSignalingChange(state: PeerConnection.SignalingState?) {}
                    override fun onIceConnectionChange(state: PeerConnection.IceConnectionState?) {
                        Log.i(TAG, "ICE Connection State: $state")
                    }
                    override fun onIceConnectionReceivingChange(receiving: Boolean) {}
                    override fun onIceGatheringChange(state: PeerConnection.IceGatheringState?) {}
                    override fun onIceCandidate(candidate: IceCandidate?) {
                        candidate?.let { signalingClient?.sendIceCandidate(it) }
                    }
                    override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) {}
                    override fun onAddStream(stream: MediaStream?) {}
                    override fun onRemoveStream(stream: MediaStream?) {}
                    override fun onDataChannel(dataChannel: DataChannel?) {}
                    override fun onRenegotiationNeeded() {}
                    override fun onAddTrack(receiver: RtpReceiver?, streams: Array<out MediaStream>?) {}
                })

                webRtcClient?.createOffer(object : SdpObserver {
                    override fun onCreateSuccess(desc: SessionDescription?) {
                        desc?.let { signalingClient?.sendOffer(it) }
                    }
                    override fun onSetSuccess() {}
                    override fun onCreateFailure(error: String?) {
                        Log.e(TAG, "Create Offer Failure: $error")
                    }
                    override fun onSetFailure(error: String?) {}
                })
            }

            override fun onViewerLeft() {
                Log.i(TAG, "Viewer left. Cleaning up WebRTC PeerConnection...")
                webRtcClient?.close()
            }

            override fun onAnswerReceived(sdp: SessionDescription) {
                Log.i(TAG, "Setting WebRTC Remote Answer SDP...")
                webRtcClient?.setRemoteDescription(sdp, object : SdpObserver {
                    override fun onCreateSuccess(desc: SessionDescription?) {}
                    override fun onSetSuccess() {
                        Log.i(TAG, "WebRTC Remote Answer set successfully. Video streaming active!")
                    }
                    override fun onCreateFailure(error: String?) {}
                    override fun onSetFailure(error: String?) {
                        Log.e(TAG, "Set Answer Failure: $error")
                    }
                })
            }

            override fun onIceCandidateReceived(candidate: IceCandidate) {
                webRtcClient?.addIceCandidate(candidate)
            }

            override fun onCommandReceived(command: String, payload: JsonObject?) {
                when (command) {
                    "toggle_torch" -> {
                        val enable = payload?.get("enable")?.asBoolean ?: !cameraEngine!!.isTorchOn
                        cameraEngine?.toggleTorch(enable)
                    }
                    "switch_camera" -> cameraEngine?.switchCamera()
                }
            }
        })
        signalingClient?.connect()

        // 4. Device Telemetry Heartbeat Monitor
        healthMonitor = DeviceHealthMonitor(this, serverUrl, deviceId)
        healthMonitor?.start(lifecycleScope)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "SentinelCam 24x7 Node Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps SentinelCam CCTV video capture active in the background"
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildForegroundNotification(statusText: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("SentinelCam 24x7 CCTV Node")
            .setContentText(statusText)
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(statusText: String) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, buildForegroundNotification(statusText))
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.i(TAG, "Destroying SentinelCam CCTV Service...")
        healthMonitor?.stop()
        signalingClient?.disconnect()
        webRtcClient?.close()
        cameraEngine?.shutdown()
        rootEglBase?.release()

        wakeLock?.let { if (it.isHeld) it.release() }
        wifiLock?.let { if (it.isHeld) it.release() }
    }
}
