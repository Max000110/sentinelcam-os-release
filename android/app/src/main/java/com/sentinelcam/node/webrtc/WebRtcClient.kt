package com.sentinelcam.node.webrtc

import android.content.Context
import android.util.Log
import org.webrtc.*
import org.webrtc.audio.AudioDeviceModule
import org.webrtc.audio.JavaAudioDeviceModule
import java.util.concurrent.Executors

class WebRtcClient(
    private val context: Context,
    private val eglBaseContext: EglBase.Context,
    private val onIceCandidateGenerated: (IceCandidate) -> Unit
) {
    private val TAG = "SentinelCam.WebRtc"
    private val executor = Executors.newSingleThreadExecutor()

    private var peerConnectionFactory: PeerConnectionFactory? = null
    private var peerConnection: PeerConnection? = null
    private var localVideoTrack: VideoTrack? = null
    private var localAudioTrack: AudioTrack? = null
    private var videoSource: VideoSource? = null
    private var audioSource: AudioSource? = null

    init {
        initializePeerConnectionFactory()
    }

    private fun initializePeerConnectionFactory() {
        val initOptions = PeerConnectionFactory.InitializationOptions.builder(context)
            .setEnableInternalTracer(false)
            .createInitializationOptions()
        PeerConnectionFactory.initialize(initOptions)

        // Hardware-accelerated H.264 / VP8 Video Encoder Factory
        val videoEncoderFactory = DefaultVideoEncoderFactory(
            eglBaseContext,
            true,  // enable Intel/Qualcomm VP8/VP9 hardware acceleration
            true   // enable H.264 high profile
        )
        val videoDecoderFactory = DefaultVideoDecoderFactory(eglBaseContext)

        // Audio engine with hardware echo cancellation (AEC) and noise suppression (NS)
        val adm: AudioDeviceModule = JavaAudioDeviceModule.builder(context)
            .setUseHardwareAcousticEchoCanceler(true)
            .setUseHardwareNoiseSuppressor(true)
            .createAudioDeviceModule()

        peerConnectionFactory = PeerConnectionFactory.builder()
            .setVideoEncoderFactory(videoEncoderFactory)
            .setVideoDecoderFactory(videoDecoderFactory)
            .setAudioDeviceModule(adm)
            .setOptions(PeerConnectionFactory.Options().apply {
                disableEncryption = false
                networkIgnoreMask = 0
            })
            .createPeerConnectionFactory()

        createLocalMediaTracks()
    }

    private fun createLocalMediaTracks() {
        val factory = peerConnectionFactory ?: return

        // Audio Source & Track
        val audioConstraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("googEchoCancellation", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("googAutoGainControl", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("googHighpassFilter", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("googNoiseSuppression", "true"))
        }
        audioSource = factory.createAudioSource(audioConstraints)
        localAudioTrack = factory.createAudioTrack("ARDAMSa0", audioSource)
        localAudioTrack?.setEnabled(true)

        // Video Source & Track
        videoSource = factory.createVideoSource(false)
        localVideoTrack = factory.createVideoTrack("ARDAMSv0", videoSource)
        localVideoTrack?.setEnabled(true)
    }

    fun getVideoSource(): VideoSource? = videoSource

    fun createPeerConnection(iceServers: List<PeerConnection.IceServer>, observer: PeerConnection.Observer) {
        val rtcConfig = PeerConnection.RTCConfiguration(iceServers).apply {
            tcpCandidatePolicy = PeerConnection.TcpCandidatePolicy.ENABLED
            bundlePolicy = PeerConnection.BundlePolicy.MAXBUNDLE
            rtcpMuxPolicy = PeerConnection.RtcpMuxPolicy.REQUIRE
            continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
            keyType = PeerConnection.KeyType.ECDSA
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
        }

        peerConnection?.dispose()
        peerConnection = peerConnectionFactory?.createPeerConnection(rtcConfig, observer)

        // Add Audio and Video Tracks to PeerConnection
        localVideoTrack?.let { videoTrack ->
            peerConnection?.addTrack(videoTrack, listOf("ARDAMS"))
        }
        localAudioTrack?.let { audioTrack ->
            peerConnection?.addTrack(audioTrack, listOf("ARDAMS"))
        }
        
        Log.i(TAG, "WebRTC PeerConnection created with ${iceServers.size} ICE servers")
    }

    fun createOffer(sdpObserver: SdpObserver) {
        val constraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "false"))
        }
        peerConnection?.createOffer(object : SdpObserver by sdpObserver {
            override fun onCreateSuccess(desc: SessionDescription?) {
                desc?.let {
                    peerConnection?.setLocalDescription(sdpObserver, it)
                }
                sdpObserver.onCreateSuccess(desc)
            }
        }, constraints)
    }

    fun setRemoteDescription(sdp: SessionDescription, sdpObserver: SdpObserver) {
        peerConnection?.setRemoteDescription(sdpObserver, sdp)
    }

    fun addIceCandidate(candidate: IceCandidate) {
        peerConnection?.addIceCandidate(candidate)
    }

    fun close() {
        try {
            peerConnection?.close()
            peerConnection?.dispose()
            peerConnection = null
        } catch (e: Exception) {
            Log.e(TAG, "Error closing peer connection: ${e.message}")
        }
    }
}
