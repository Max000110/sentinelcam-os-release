package com.sentinelcam.node.webrtc

import android.content.Context
import android.util.Log
import org.webrtc.*
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class WebRtcClient(
    private val context: Context,
    private val onIceCandidateGenerated: (IceCandidate) -> Unit,
    private val onOfferGenerated: (SessionDescription) -> Unit
) {
    companion object {
        private const val TAG = "SentinelCam.WebRTC"
        private var isFactoryInitialized = false

        val rootEglBase: EglBase by lazy {
            EglBase.create()
        }

        @Synchronized
        fun initializeWebRtc(context: Context) {
            if (!isFactoryInitialized) {
                val options = PeerConnectionFactory.InitializationOptions.builder(context)
                    .setEnableInternalTracer(false)
                    .createInitializationOptions()
                PeerConnectionFactory.initialize(options)
                isFactoryInitialized = true
            }
        }
    }

    private var peerConnectionFactory: PeerConnectionFactory? = null
    private var peerConnection: PeerConnection? = null
    private var videoSource: VideoSource? = null
    private var localVideoTrack: VideoTrack? = null
    private var audioSource: AudioSource? = null
    private var localAudioTrack: AudioTrack? = null

    private val isStreamingActive = AtomicBoolean(false)
    var rtcConnectionState: String = "NEW"
    var iceState: String = "NEW"

    init {
        initializeFactory()
    }

    private fun initializeFactory() {
        initializeWebRtc(context)

        val encoderFactory = DefaultVideoEncoderFactory(
            rootEglBase.eglBaseContext,
            true, // enableIntelVp8Encoder
            true  // enableH264HighProfile
        )
        val decoderFactory = DefaultVideoDecoderFactory(rootEglBase.eglBaseContext)

        peerConnectionFactory = PeerConnectionFactory.builder()
            .setVideoEncoderFactory(encoderFactory)
            .setVideoDecoderFactory(decoderFactory)
            .createPeerConnectionFactory()

        createLocalMediaTracks()
    }

    private fun createLocalMediaTracks() {
        val factory = peerConnectionFactory ?: return

        // 1. Audio Track with Echo Cancellation & Noise Suppression
        val audioConstraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("googEchoCancellation", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("googAutoGainControl", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("googHighpassFilter", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("googNoiseSuppression", "true"))
        }
        audioSource = factory.createAudioSource(audioConstraints)
        localAudioTrack = factory.createAudioTrack("ARDAMSa0", audioSource).apply {
            setEnabled(true)
        }

        // 2. Video Track
        videoSource = factory.createVideoSource(false)
        localVideoTrack = factory.createVideoTrack("ARDAMSv0", videoSource).apply {
            setEnabled(true)
        }
        Log.i(TAG, "Local Audio & Video tracks initialized successfully")
    }

    fun onFrameCaptured(nv21: ByteArray, width: Int, height: Int, rotation: Int) {
        val source = videoSource ?: return
        if (!isStreamingActive.get()) return

        try {
            val buffer = NV21Buffer(nv21, width, height, null)
            val timestampNs = System.nanoTime()
            val videoFrame = VideoFrame(buffer, rotation, timestampNs)
            source.capturerObserver.onFrameCaptured(videoFrame)
            videoFrame.release()
        } catch (e: Exception) {
            Log.e(TAG, "Error passing frame to WebRTC: ${e.message}")
        }
    }

    fun startSession(iceServers: List<PeerConnection.IceServer>) {
        closeCurrentPeerConnection()

        val rtcConfig = PeerConnection.RTCConfiguration(iceServers).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
            iceTransportsType = PeerConnection.IceTransportsType.ALL
            bundlePolicy = PeerConnection.BundlePolicy.MAXBUNDLE
            tcpCandidatePolicy = PeerConnection.TcpCandidatePolicy.ENABLED
        }

        peerConnection = peerConnectionFactory?.createPeerConnection(rtcConfig, object : PeerConnection.Observer {
            override fun onIceCandidate(candidate: IceCandidate?) {
                candidate?.let { onIceCandidateGenerated(it) }
            }

            override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) {}
            override fun onSignalingChange(state: PeerConnection.SignalingState?) {}
            override fun onIceConnectionChange(state: PeerConnection.IceConnectionState?) {
                iceState = state?.name ?: "UNKNOWN"
                Log.i(TAG, "ICE Connection State: $state")
            }
            override fun onIceConnectionReceivingChange(receiving: Boolean) {}
            override fun onIceGatheringChange(state: PeerConnection.IceGatheringState?) {}
            override fun onConnectionChange(newState: PeerConnection.PeerConnectionState?) {
                rtcConnectionState = newState?.name ?: "UNKNOWN"
                Log.i(TAG, "WebRTC PeerConnection State: $newState")
            }
            override fun onAddStream(stream: MediaStream?) {}
            override fun onRemoveStream(stream: MediaStream?) {}
            override fun onDataChannel(channel: DataChannel?) {}
            override fun onRenegotiationNeeded() {}
            override fun onAddTrack(receiver: RtpReceiver?, streams: Array<out MediaStream>?) {
                Log.i(TAG, "Remote track added (Two-way audio intercom received)")
            }
        })

        // Add Local Video and Audio Tracks to the PeerConnection
        localVideoTrack?.let { vTrack ->
            val sender = peerConnection?.addTrack(vTrack, listOf("ARDAMS"))
            // Configure RTP sender for low-latency CCTV streaming
            sender?.let { rtpSender ->
                val params = rtpSender.parameters
                if (params.encodings.isNotEmpty()) {
                    params.encodings[0].maxBitrateBps = 1_500_000  // 1.5 Mbps cap
                    params.encodings[0].minBitrateBps = 300_000    // 300 Kbps floor
                    // Prioritize framerate over resolution when bandwidth-constrained
                    params.degradationPreference = RtpParameters.DegradationPreference.MAINTAIN_FRAMERATE
                }
                rtpSender.parameters = params
                Log.i(TAG, "RTP sender configured: maxBitrate=1500kbps, degradation=MAINTAIN_FRAMERATE")
            }
        }
        localAudioTrack?.let { aTrack ->
            peerConnection?.addTrack(aTrack, listOf("ARDAMS"))
        }

        isStreamingActive.set(true)
        createLocalOffer()
    }

    private fun createLocalOffer() {
        val mediaConstraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "false"))
        }

        peerConnection?.createOffer(object : SdpObserver {
            override fun onCreateSuccess(desc: SessionDescription?) {
                desc?.let { offer ->
                    peerConnection?.setLocalDescription(object : SdpObserver {
                        override fun onCreateSuccess(p0: SessionDescription?) {}
                        override fun onSetSuccess() {
                            Log.i(TAG, "Local SDP Offer set and dispatched successfully")
                            onOfferGenerated(offer)
                        }
                        override fun onCreateFailure(err: String?) {
                            Log.e(TAG, "SetLocalDescription failed: $err")
                        }
                        override fun onSetFailure(err: String?) {
                            Log.e(TAG, "SetLocalDescription error: $err")
                        }
                    }, offer)
                }
            }
            override fun onSetSuccess() {}
            override fun onCreateFailure(error: String?) {
                Log.e(TAG, "CreateOffer failed: $error")
            }
            override fun onSetFailure(error: String?) {}
        }, mediaConstraints)
    }

    fun handleRemoteAnswer(sdpString: String) {
        val sdp = SessionDescription(SessionDescription.Type.ANSWER, sdpString)
        peerConnection?.setRemoteDescription(object : SdpObserver {
            override fun onCreateSuccess(p0: SessionDescription?) {}
            override fun onSetSuccess() {
                Log.i(TAG, "Remote SDP Answer set successfully - Streaming active")
            }
            override fun onCreateFailure(p0: String?) {}
            override fun onSetFailure(p0: String?) {}
        }, sdp)
    }

    fun addRemoteIceCandidate(candidate: IceCandidate) {
        peerConnection?.addIceCandidate(candidate)
    }

    private fun closeCurrentPeerConnection() {
        try {
            isStreamingActive.set(false)
            peerConnection?.close()
            peerConnection = null
        } catch (e: Exception) {
            Log.e(TAG, "Error closing peer connection: ${e.message}")
        }
    }

    fun close() {
        isStreamingActive.set(false)
        closeCurrentPeerConnection()
        try {
            localVideoTrack?.dispose()
            localVideoTrack = null
            videoSource?.dispose()
            videoSource = null

            localAudioTrack?.dispose()
            localAudioTrack = null
            audioSource?.dispose()
            audioSource = null

            peerConnectionFactory?.dispose()
            peerConnectionFactory = null
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing WebRTC resources: ${e.message}")
        }
    }
}
