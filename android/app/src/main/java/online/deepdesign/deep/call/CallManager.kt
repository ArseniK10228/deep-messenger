package online.deepdesign.deep.call

import android.content.Context
import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.PowerManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import online.deepdesign.deep.DeepApp
import online.deepdesign.deep.data.IceServerDto
import online.deepdesign.deep.data.StartCallRequest
import online.deepdesign.deep.push.IncomingCallNotifier
import online.deepdesign.deep.data.WsEnvelope
import org.webrtc.IceCandidate
import org.webrtc.PeerConnection
import org.webrtc.SessionDescription
import org.webrtc.VideoTrack

sealed class CallUiState {
    data object Idle : CallUiState()
    data class Outgoing(
        val callId: String,
        val conversationId: String,
        val peerName: String,
        val video: Boolean = false
    ) : CallUiState()
    data class Incoming(
        val callId: String,
        val conversationId: String,
        val callerName: String,
        val video: Boolean = false
    ) : CallUiState()
    data class Active(
        val callId: String,
        val peerName: String,
        val video: Boolean = false,
        val connected: Boolean = false
    ) : CallUiState()
}

class CallManager(
    private val context: Context,
    private val signaling: SignalingHub
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val api get() = DeepApp.instance.api

    private val _state = MutableStateFlow<CallUiState>(CallUiState.Idle)
    val state: StateFlow<CallUiState> = _state.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _muted = MutableStateFlow(false)
    val muted: StateFlow<Boolean> = _muted.asStateFlow()

    private val _speakerOn = MutableStateFlow(false)
    val speakerOn: StateFlow<Boolean> = _speakerOn.asStateFlow()

    private val _overlayExpanded = MutableStateFlow(true)
    val overlayExpanded: StateFlow<Boolean> = _overlayExpanded.asStateFlow()

    private val _videoOn = MutableStateFlow(true)
    val videoOn: StateFlow<Boolean> = _videoOn.asStateFlow()

    private val _localVideoTrack = MutableStateFlow<VideoTrack?>(null)
    val localVideoTrack: StateFlow<VideoTrack?> = _localVideoTrack.asStateFlow()

    private val _remoteVideoTrack = MutableStateFlow<VideoTrack?>(null)
    val remoteVideoTrack: StateFlow<VideoTrack?> = _remoteVideoTrack.asStateFlow()

    private var engine: WebRtcCallEngine? = null
    private var iceServers: List<IceServerDto> = emptyList()
    private var listenJob: Job? = null
    private var activeCallId: String? = null
    private var activePeerName: String = "Deep"
    private var pendingOffer: WsEnvelope? = null
    private val pendingIce = mutableListOf<IceCandidate>()
    private var disconnectJob: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null

    fun start() {
        signaling.connect()
        listenJob?.cancel()
        listenJob = scope.launch {
            signaling.events.collect { handleSignal(it) }
        }
    }

    fun stop() {
        listenJob?.cancel()
        signaling.disconnect()
        teardownRtc()
        _state.value = CallUiState.Idle
    }

    fun clearError() {
        _error.value = null
    }

    fun toggleMute() {
        val next = !_muted.value
        _muted.value = next
        engine?.setMicrophoneMuted(next)
    }

    fun toggleSpeaker() {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val next = !_speakerOn.value
        _speakerOn.value = next
        am.isSpeakerphoneOn = next
    }

    fun minimizeOverlay() {
        if (_state.value is CallUiState.Outgoing || _state.value is CallUiState.Active) {
            _overlayExpanded.value = false
        }
    }

    fun expandOverlay() {
        _overlayExpanded.value = true
    }

    fun startOutgoing(conversationId: String, peerName: String, video: Boolean = false) {
        if (!hasMicPermission()) {
            _error.value = "Разреши доступ к микрофону для звонка"
            return
        }
        if (video && !hasCameraPermission()) {
            _error.value = "Разреши доступ к камере для видеозвонка"
            return
        }
        scope.launch {
            try {
                val resp = api.startCall(StartCallRequest(conversationId, video))
                activeCallId = resp.callId
                activePeerName = peerName
                iceServers = resp.iceServers
                _videoOn.value = video
                _overlayExpanded.value = true
                _state.value = CallUiState.Outgoing(resp.callId, conversationId, peerName, video)
                beginAudioSession()
                CallForegroundService.start(context, peerName, outgoing = true)
            } catch (e: Exception) {
                _state.value = CallUiState.Idle
                _error.value = e.message?.takeIf { it.isNotBlank() } ?: "Не удалось начать звонок"
                CallForegroundService.stop(context)
            }
        }
    }

    fun acceptIncoming() {
        val incoming = _state.value as? CallUiState.Incoming ?: return
        if (!hasMicPermission()) {
            _error.value = "Разреши доступ к микрофону для звонка"
            return
        }
        scope.launch {
            try {
                val resp = api.acceptCall(incoming.callId)
                iceServers = resp.iceServers
                activeCallId = incoming.callId
                activePeerName = incoming.callerName
                _overlayExpanded.value = true
                _state.value = CallUiState.Active(
                    incoming.callId,
                    incoming.callerName,
                    incoming.video,
                    connected = false
                )
                CallForegroundService.start(context, incoming.callerName, outgoing = false)
                initEngine()
                pendingOffer?.let {
                    pendingOffer = null
                    handleRemoteSdp(it)
                }
            } catch (_: Exception) {
                _error.value = "Не удалось принять звонок"
                rejectIncoming()
            }
        }
    }

    fun rejectIncoming() {
        val incoming = _state.value as? CallUiState.Incoming ?: return
        scope.launch {
            runCatching { api.rejectCall(incoming.callId) }
            endLocal("reject")
        }
    }

    fun hangup() {
        val callId = activeCallId ?: return
        scope.launch {
            runCatching { api.endCall(callId) }
            endLocal("hangup")
        }
    }

    fun toggleVideo() {
        val next = !_videoOn.value
        _videoOn.value = next
        engine?.setVideoEnabled(next)
    }

    fun switchCamera() {
        engine?.switchCamera()
    }

    fun handleIncomingPush(data: Map<String, String>) {
        if (data["type"] != "incoming_call") return
        val callId = data["callId"] ?: return
        val conversationId = data["conversationId"] ?: return
        val callerName = data["callerName"] ?: "Deep"
        val video = data["video"] == "true"
        prepareIncomingFromNotification(callId, conversationId, callerName, video)
    }

    fun prepareIncomingFromNotification(
        callId: String,
        conversationId: String,
        callerName: String,
        video: Boolean = false
    ) {
        if (_state.value is CallUiState.Outgoing || _state.value is CallUiState.Active) return
        _overlayExpanded.value = true
        _videoOn.value = video
        _state.value = CallUiState.Incoming(callId, conversationId, callerName, video)
        signaling.connect()
    }

    private fun handleSignal(env: WsEnvelope) {
        when (env.type) {
            "call_invite" -> {
                val callId = env.callId ?: return
                if (_state.value !is CallUiState.Idle) return
                val video = env.video == "true"
                _videoOn.value = video
                _state.value = CallUiState.Incoming(
                    callId,
                    env.conversationId.orEmpty(),
                    env.callerName ?: "Deep",
                    video
                )
            }
            "call_accept" -> {
                val callId = env.callId ?: return
                if (_state.value is CallUiState.Outgoing) {
                    val outgoing = _state.value as CallUiState.Outgoing
                    activeCallId = callId
                    _overlayExpanded.value = true
                    _state.value = CallUiState.Active(
                        callId,
                        activePeerName,
                        outgoing.video,
                        connected = false
                    )
                    initEngine()
                    engine?.createOffer { sdp ->
                        signaling.sendSdp(callId, sdp.description, sdp.type.canonicalForm())
                    }
                }
            }
            "call_sdp" -> handleRemoteSdp(env)
            "call_ice" -> handleRemoteIce(env)
            "call_end" -> endLocal(env.reason ?: "end")
        }
    }

    private fun handleRemoteSdp(env: WsEnvelope) {
        val callId = env.callId ?: return
        val sdp = env.sdp ?: return
        val type = env.sdpType ?: return
        if (_state.value is CallUiState.Incoming) {
            pendingOffer = env
            return
        }
        if (activeCallId == null) activeCallId = callId

        scope.launch {
            try {
                if (engine == null) {
                    if (iceServers.isEmpty()) {
                        iceServers = runCatching { api.callIce().iceServers }.getOrDefault(emptyList())
                    }
                    initEngine()
                }
                val session = SessionDescription(
                    SessionDescription.Type.fromCanonicalForm(type),
                    sdp
                )
                engine?.setRemoteDescription(session) {
                    if (type == "offer") {
                        engine?.createAnswer { answer ->
                            signaling.sendSdp(callId, answer.description, answer.type.canonicalForm())
                        }
                    }
                }
            } catch (_: Exception) {
                _error.value = "Ошибка соединения"
                hangup()
            }
        }
    }

    private fun handleRemoteIce(env: WsEnvelope) {
        val candidate = env.candidate ?: return
        val ice = IceCandidate(
            env.sdpMid,
            env.sdpMLineIndex ?: 0,
            candidate
        )
        val eng = engine
        if (eng == null) {
            pendingIce.add(ice)
        } else {
            eng.addIceCandidate(ice)
        }
    }

    private fun initEngine() {
        val video = when (val s = _state.value) {
            is CallUiState.Outgoing -> s.video
            is CallUiState.Incoming -> s.video
            is CallUiState.Active -> s.video
            else -> false
        }
        teardownRtc()
        beginAudioSession()
        engine = WebRtcCallEngine(context, iceServers, video, object : WebRtcCallEngine.Listener {
            override fun onIceCandidate(candidate: IceCandidate) {
                val callId = activeCallId ?: return
                signaling.sendIce(callId, candidate.sdp, candidate.sdpMid, candidate.sdpMLineIndex)
            }

            override fun onRemoteVideoTrack(track: VideoTrack) {
                _remoteVideoTrack.value = track
            }

            override fun onConnectionChange(state: PeerConnection.PeerConnectionState) {
                scope.launch {
                    when (state) {
                        PeerConnection.PeerConnectionState.CONNECTED -> {
                            disconnectJob?.cancel()
                            _state.update { current ->
                                if (current is CallUiState.Active) {
                                    current.copy(connected = true)
                                } else current
                            }
                            engine?.localVideoTrackFlow?.value?.let { _localVideoTrack.value = it }
                        }
                        PeerConnection.PeerConnectionState.DISCONNECTED -> scheduleDisconnectHangup()
                        PeerConnection.PeerConnectionState.FAILED -> {
                            if (_state.value is CallUiState.Active) hangup()
                        }
                        else -> Unit
                    }
                }
            }

            override fun onIceConnectionChange(state: PeerConnection.IceConnectionState) {
                scope.launch {
                    when (state) {
                        PeerConnection.IceConnectionState.CONNECTED,
                        PeerConnection.IceConnectionState.COMPLETED -> {
                            disconnectJob?.cancel()
                            _state.update { current ->
                                if (current is CallUiState.Active) {
                                    current.copy(connected = true)
                                } else current
                            }
                            engine?.localVideoTrackFlow?.value?.let { _localVideoTrack.value = it }
                        }
                        PeerConnection.IceConnectionState.DISCONNECTED -> scheduleDisconnectHangup()
                        PeerConnection.IceConnectionState.FAILED -> scheduleDisconnectHangup(graceMs = 20_000)
                        else -> Unit
                    }
                }
            }
        })
        engine?.localVideoTrackFlow?.value?.let { _localVideoTrack.value = it }
        pendingIce.forEach { engine?.addIceCandidate(it) }
        pendingIce.clear()
    }

    private fun scheduleDisconnectHangup(graceMs: Long = 15_000) {
        if (_state.value !is CallUiState.Active) return
        disconnectJob?.cancel()
        disconnectJob = scope.launch {
            delay(graceMs)
            if (_state.value is CallUiState.Active) hangup()
        }
    }

    private fun beginAudioSession() {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        am.mode = AudioManager.MODE_IN_COMMUNICATION
        am.isSpeakerphoneOn = _speakerOn.value
        runCatching {
            val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock?.release()
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "deep:call").apply {
                acquire(60 * 60 * 1000L)
            }
        }
    }

    private fun endAudioSession() {
        disconnectJob?.cancel()
        runCatching { wakeLock?.release() }
        wakeLock = null
        runCatching {
            val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            am.isSpeakerphoneOn = false
            am.mode = AudioManager.MODE_NORMAL
        }
    }

    private fun endLocal(@Suppress("UNUSED_PARAMETER") reason: String) {
        endAudioSession()
        teardownRtc()
        activeCallId = null
        pendingOffer = null
        pendingIce.clear()
        _muted.value = false
        _speakerOn.value = false
        _videoOn.value = true
        _localVideoTrack.value = null
        _remoteVideoTrack.value = null
        _overlayExpanded.value = true
        _state.value = CallUiState.Idle
        IncomingCallNotifier.dismiss(context)
        CallForegroundService.stop(context)
    }

    private fun teardownRtc() {
        engine?.close()
        engine = null
    }

    private fun hasMicPermission(): Boolean {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
    }

    private fun hasCameraPermission(): Boolean {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
    }
}
