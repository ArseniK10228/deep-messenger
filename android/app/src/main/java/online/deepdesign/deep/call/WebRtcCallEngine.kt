package online.deepdesign.deep.call

import android.content.Context
import online.deepdesign.deep.data.IceServerDto
import org.webrtc.AudioSource
import org.webrtc.AudioTrack
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.SessionDescription
import org.webrtc.audio.JavaAudioDeviceModule

class WebRtcCallEngine(
    context: Context,
    iceServers: List<IceServerDto>,
    private val listener: Listener
) {
    interface Listener {
        fun onIceCandidate(candidate: IceCandidate)
        fun onConnectionChange(state: PeerConnection.PeerConnectionState)
    }

    private val factory: PeerConnectionFactory
    private val audioSource: AudioSource
    private val localAudioTrack: AudioTrack
    private var peerConnection: PeerConnection? = null

    init {
        val initOpts = PeerConnectionFactory.InitializationOptions.builder(context)
            .setEnableInternalTracer(false)
            .createInitializationOptions()
        PeerConnectionFactory.initialize(initOpts)

        val audioModule = JavaAudioDeviceModule.builder(context).createAudioDeviceModule()
        factory = PeerConnectionFactory.builder()
            .setAudioDeviceModule(audioModule)
            .createPeerConnectionFactory()

        audioSource = factory.createAudioSource(MediaConstraints())
        localAudioTrack = factory.createAudioTrack("deep_audio", audioSource)

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
        }
        peerConnection = factory.createPeerConnection(rtcConfig, object : PeerConnection.Observer {
            override fun onIceCandidate(candidate: IceCandidate) {
                listener.onIceCandidate(candidate)
            }

            override fun onConnectionChange(newState: PeerConnection.PeerConnectionState) {
                listener.onConnectionChange(newState)
            }

            override fun onSignalingChange(state: PeerConnection.SignalingState?) {}
            override fun onIceConnectionChange(state: PeerConnection.IceConnectionState?) {}
            override fun onIceConnectionReceivingChange(receiving: Boolean) {}
            override fun onIceGatheringChange(state: PeerConnection.IceGatheringState?) {}
            override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) {}
            override fun onAddStream(stream: org.webrtc.MediaStream?) {}
            override fun onRemoveStream(stream: org.webrtc.MediaStream?) {}
            override fun onDataChannel(channel: org.webrtc.DataChannel?) {}
            override fun onRenegotiationNeeded() {}
            override fun onAddTrack(receiver: org.webrtc.RtpReceiver?, streams: Array<out org.webrtc.MediaStream>?) {}
        })
        peerConnection?.addTrack(localAudioTrack, listOf("deep_stream"))
    }

    fun createOffer(onReady: (SessionDescription) -> Unit) {
        val pc = peerConnection ?: return
        pc.createOffer(object : SimpleSdpObserver() {
            override fun onCreateSuccess(sdp: SessionDescription?) {
                sdp ?: return
                pc.setLocalDescription(SimpleSdpObserver(), sdp)
                onReady(sdp)
            }
        }, MediaConstraints())
    }

    fun createAnswer(onReady: (SessionDescription) -> Unit) {
        val pc = peerConnection ?: return
        pc.createAnswer(object : SimpleSdpObserver() {
            override fun onCreateSuccess(sdp: SessionDescription?) {
                sdp ?: return
                pc.setLocalDescription(SimpleSdpObserver(), sdp)
                onReady(sdp)
            }
        }, MediaConstraints())
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

    fun close() {
        try {
            localAudioTrack.dispose()
            audioSource.dispose()
            peerConnection?.close()
            peerConnection?.dispose()
            factory.dispose()
        } catch (_: Exception) { }
        peerConnection = null
    }
}
