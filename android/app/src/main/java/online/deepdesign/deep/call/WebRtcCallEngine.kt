package online.deepdesign.deep.call

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.webrtc.Camera2Enumerator
import org.webrtc.EglBase
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.PeerConnection
import org.webrtc.RTCStatsCollectorCallback
import org.webrtc.RTCStatsReport
import org.webrtc.SessionDescription
import org.webrtc.SurfaceTextureHelper
import org.webrtc.VideoCapturer
import org.webrtc.VideoSource
import org.webrtc.VideoTrack
import online.deepdesign.deep.data.IceServerDto

class WebRtcCallEngine(
    context: Context,
    iceServers: List<IceServerDto>,
    private val videoEnabled: Boolean,
    private val listener: Listener
) {
    interface Listener {
        fun onIceCandidate(candidate: IceCandidate)
        fun onConnectionChange(state: PeerConnection.PeerConnectionState)
        fun onIceConnectionChange(state: PeerConnection.IceConnectionState)
        fun onRemoteVideoTrack(track: VideoTrack)
    }

    private val appContext = context.applicationContext
    private val factory = WebRtcFactoryHolder.getOrCreate(appContext)
    private val eglBase = WebRtcFactoryHolder.eglBase
    private val audioSource = factory.createAudioSource(MediaConstraints())
    private val localAudioTrack = factory.createAudioTrack("deep_audio", audioSource)

    private var peerConnection: PeerConnection? = null
    private var videoCapturer: VideoCapturer? = null
    private var surfaceTextureHelper: SurfaceTextureHelper? = null
    private var videoSource: VideoSource? = null
    private var localVideoTrack: VideoTrack? = null
    private var usingFrontCamera = true

    private val _localVideoTrack = MutableStateFlow<VideoTrack?>(null)
    val localVideoTrackFlow: StateFlow<VideoTrack?> = _localVideoTrack.asStateFlow()

    private val _remoteVideoTrack = MutableStateFlow<VideoTrack?>(null)
    val remoteVideoTrackFlow: StateFlow<VideoTrack?> = _remoteVideoTrack.asStateFlow()

    init {
        val servers = iceServers.flatMap { dto ->
            dto.urls.map { url ->
                val builder = PeerConnection.IceServer.builder(url)
                dto.username?.let { builder.setUsername(it) }
                dto.credential?.let { builder.setPassword(it) }
                builder.createIceServer()
            }
        }
        val rtcConfig = PeerConnection.RTCConfiguration(servers).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            iceTransportsType = PeerConnection.IceTransportsType.ALL
            continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
            iceCandidatePoolSize = 2
            bundlePolicy = PeerConnection.BundlePolicy.MAXBUNDLE
            rtcpMuxPolicy = PeerConnection.RtcpMuxPolicy.REQUIRE
        }
        peerConnection = factory.createPeerConnection(rtcConfig, object : PeerConnection.Observer {
            override fun onIceCandidate(candidate: IceCandidate) {
                listener.onIceCandidate(candidate)
            }

            override fun onConnectionChange(newState: PeerConnection.PeerConnectionState) {
                listener.onConnectionChange(newState)
            }

            override fun onSignalingChange(state: PeerConnection.SignalingState?) {}
            override fun onIceConnectionChange(state: PeerConnection.IceConnectionState?) {
                state?.let { listener.onIceConnectionChange(it) }
            }
            override fun onIceConnectionReceivingChange(receiving: Boolean) {}
            override fun onIceGatheringChange(state: PeerConnection.IceGatheringState?) {}
            override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) {}
            override fun onAddStream(stream: org.webrtc.MediaStream?) {}
            override fun onRemoveStream(stream: org.webrtc.MediaStream?) {}
            override fun onDataChannel(channel: org.webrtc.DataChannel?) {}
            override fun onRenegotiationNeeded() {}
            override fun onAddTrack(
                receiver: org.webrtc.RtpReceiver?,
                streams: Array<out org.webrtc.MediaStream>?
            ) {
                val track = receiver?.track()
                if (track is VideoTrack) {
                    _remoteVideoTrack.value = track
                    listener.onRemoteVideoTrack(track)
                }
            }
        })

        peerConnection?.addTrack(localAudioTrack, listOf("deep_stream"))
        if (videoEnabled) {
            startLocalVideo()
        }
    }

    private fun startLocalVideo() {
        val enumerator = Camera2Enumerator(appContext)
        val device = enumerator.deviceNames.firstOrNull { enumerator.isFrontFacing(it) }
            ?: enumerator.deviceNames.firstOrNull()
            ?: return
        usingFrontCamera = enumerator.isFrontFacing(device)

        val capturer = enumerator.createCapturer(device, null)
        videoCapturer = capturer
        surfaceTextureHelper = SurfaceTextureHelper.create("DeepCapture", eglBase.eglBaseContext)
        videoSource = factory.createVideoSource(capturer.isScreencast)
        capturer.initialize(surfaceTextureHelper, appContext, videoSource!!.capturerObserver)
        capturer.startCapture(1280, 720, 30)

        localVideoTrack = factory.createVideoTrack("deep_video", videoSource!!)
        localVideoTrack?.setEnabled(true)
        _localVideoTrack.value = localVideoTrack
        peerConnection?.addTrack(localVideoTrack, listOf("deep_stream"))
    }

    fun switchCamera(onDone: ((Boolean) -> Unit)? = null) {
        val capturer = videoCapturer as? org.webrtc.CameraVideoCapturer ?: return
        capturer.switchCamera(object : org.webrtc.CameraVideoCapturer.CameraSwitchHandler {
            override fun onCameraSwitchDone(isFront: Boolean) {
                usingFrontCamera = isFront
                onDone?.invoke(isFront)
            }
            override fun onCameraSwitchError(error: String?) {}
        })
    }

    fun isFrontCamera(): Boolean = usingFrontCamera

    fun setVideoEnabled(enabled: Boolean) {
        localVideoTrack?.setEnabled(enabled)
    }

    fun setMicrophoneMuted(muted: Boolean) {
        localAudioTrack.setEnabled(!muted)
    }

    private fun mediaConstraints(): MediaConstraints = MediaConstraints().apply {
        mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
        mandatory.add(
            MediaConstraints.KeyValuePair(
                "OfferToReceiveVideo",
                if (videoEnabled) "true" else "false"
            )
        )
    }

    fun createOffer(onReady: (SessionDescription) -> Unit) {
        val pc = peerConnection ?: return
        pc.createOffer(object : SimpleSdpObserver() {
            override fun onCreateSuccess(sdp: SessionDescription?) {
                sdp ?: return
                pc.setLocalDescription(SimpleSdpObserver(), sdp)
                onReady(sdp)
            }
        }, mediaConstraints())
    }

    fun createAnswer(onReady: (SessionDescription) -> Unit) {
        val pc = peerConnection ?: return
        pc.createAnswer(object : SimpleSdpObserver() {
            override fun onCreateSuccess(sdp: SessionDescription?) {
                sdp ?: return
                pc.setLocalDescription(SimpleSdpObserver(), sdp)
                onReady(sdp)
            }
        }, mediaConstraints())
    }

    fun setRemoteDescription(sdp: SessionDescription, onReady: () -> Unit) {
        peerConnection?.setRemoteDescription(object : SimpleSdpObserver() {
            override fun onSetSuccess() {
                onReady()
            }
        }, sdp)
    }

    fun addIceCandidate(candidate: IceCandidate) {
        peerConnection?.addIceCandidate(candidate)
    }

    fun restartIce() {
        peerConnection?.restartIce()
    }

    fun readCallStats(onResult: (micLevel: Float, rttMs: Int?) -> Unit) {
        val pc = peerConnection ?: return
        runCatching {
            pc.getStats(object : RTCStatsCollectorCallback {
                override fun onStatsDelivered(report: RTCStatsReport?) {
                    if (report == null) return
                    try {
                        var mic = 0f
                        var rttMs: Int? = null
                        for (stats in report.statsMap.values) {
                            when (stats.type) {
                                "media-source" -> {
                                    if (stats.members["kind"] == "audio") {
                                        (stats.members["audioLevel"] as? Number)?.toFloat()?.let { mic = it }
                                    }
                                }
                                "candidate-pair" -> {
                                    if (stats.members["state"] == "succeeded") {
                                        (stats.members["currentRoundTripTime"] as? Number)?.toDouble()?.let {
                                            rttMs = (it * 1000).toInt().coerceAtLeast(1)
                                        }
                                    }
                                }
                            }
                        }
                        onResult(mic, rttMs)
                    } catch (_: Exception) {
                    }
                }
            })
        }
    }

    fun close() {
        runCatching { videoCapturer?.stopCapture() }
        runCatching { videoCapturer?.dispose() }
        runCatching { surfaceTextureHelper?.dispose() }
        runCatching { localVideoTrack?.dispose() }
        runCatching { videoSource?.dispose() }
        runCatching { localAudioTrack.dispose() }
        runCatching { audioSource.dispose() }
        runCatching {
            peerConnection?.close()
            peerConnection?.dispose()
        }
        videoCapturer = null
        surfaceTextureHelper = null
        localVideoTrack = null
        videoSource = null
        peerConnection = null
        _localVideoTrack.value = null
        _remoteVideoTrack.value = null
    }
}
