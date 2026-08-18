package com.sentinelcam.node.webrtc

import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonObject
import okhttp3.*
import org.webrtc.IceCandidate
import org.webrtc.SessionDescription
import java.util.concurrent.TimeUnit

interface SignalingListener {
    fun onConnected()
    fun onDisconnected()
    fun onViewerJoined()
    fun onViewerLeft()
    fun onAnswerReceived(sdp: SessionDescription)
    fun onIceCandidateReceived(candidate: IceCandidate)
    fun onCommandReceived(command: String, payload: JsonObject?)
}

class SignalingClient(
    private val serverBaseUrl: String, // e.g. "http://192.168.1.100:8000" or "https://sentinelcam.domain.com"
    private val deviceId: String,
    private val listener: SignalingListener
) {
    private val TAG = "SentinelCam.Signaling"
    private val gson = Gson()
    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .pingInterval(20, TimeUnit.SECONDS)
        .build()

    private var webSocket: WebSocket? = null
    private var isConnected = false

    fun connect() {
        val wsUrl = serverBaseUrl
            .replace("http://", "ws://")
            .replace("https://", "wss://") + "/ws/signaling/node/$deviceId"

        Log.i(TAG, "Connecting to signaling server: $wsUrl")
        val request = Request.Builder().url(wsUrl).build()
        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(ws: WebSocket, response: Response) {
                isConnected = true
                Log.i(TAG, "Signaling WebSocket connected successfully")
                listener.onConnected()
            }

            override fun onMessage(ws: WebSocket, text: String) {
                handleIncomingMessage(text)
            }

            override fun onClosing(ws: WebSocket, code: Int, reason: String) {
                isConnected = false
                Log.w(TAG, "Signaling closing: $code / $reason")
                listener.onDisconnected()
            }

            override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                isConnected = false
                Log.e(TAG, "Signaling failure: ${t.message}")
                listener.onDisconnected()
            }
        })
    }

    private fun handleIncomingMessage(text: String) {
        try {
            val json = gson.fromJson(text, JsonObject::class.java)
            val type = json.get("type")?.asString ?: return

            when (type) {
                "viewer_joined" -> listener.onViewerJoined()
                "viewer_left" -> listener.onViewerLeft()
                "answer" -> {
                    val sdpStr = json.get("sdp")?.asString ?: return
                    val sdp = SessionDescription(SessionDescription.Type.ANSWER, sdpStr)
                    listener.onAnswerReceived(sdp)
                }
                "ice_candidate" -> {
                    val candObj = json.getAsJsonObject("candidate") ?: return
                    val sdpMid = candObj.get("sdpMid")?.asString ?: ""
                    val sdpMLineIndex = candObj.get("sdpMLineIndex")?.asInt ?: 0
                    val sdp = candObj.get("candidate")?.asString ?: return
                    val candidate = IceCandidate(sdpMid, sdpMLineIndex, sdp)
                    listener.onIceCandidateReceived(candidate)
                }
                "command" -> {
                    val cmd = json.get("command")?.asString ?: ""
                    val payload = json.getAsJsonObject("payload")
                    listener.onCommandReceived(cmd, payload)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing signaling message: ${e.message}")
        }
    }

    fun sendOffer(sdp: SessionDescription) {
        val json = JsonObject().apply {
            addProperty("type", "offer")
            addProperty("device_id", deviceId)
            addProperty("sender_role", "node")
            addProperty("sdp", sdp.description)
        }
        webSocket?.send(gson.toJson(json))
    }

    fun sendIceCandidate(candidate: IceCandidate) {
        val candObj = JsonObject().apply {
            addProperty("sdpMid", candidate.sdpMid)
            addProperty("sdpMLineIndex", candidate.sdpMLineIndex)
            addProperty("candidate", candidate.sdp)
        }
        val json = JsonObject().apply {
            addProperty("type", "ice_candidate")
            addProperty("device_id", deviceId)
            addProperty("sender_role", "node")
            add("candidate", candObj)
        }
        webSocket?.send(gson.toJson(json))
    }

    fun disconnect() {
        webSocket?.close(1000, "Node disconnecting")
        webSocket = null
        isConnected = false
    }
}
