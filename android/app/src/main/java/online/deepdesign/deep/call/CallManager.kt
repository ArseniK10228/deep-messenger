package online.deepdesign.deep.call

import android.content.Context
import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import online.deepdesign.deep.DeepApp
import online.deepdesign.deep.data.IceServerDto
import online.deepdesign.deep.data.StartCallRequest
import online.deepdesign.deep.data.WsEnvelope
import org.webrtc.IceCandidate
import org.webrtc.PeerConnection
import org.webrtc.SessionDescription

sealed class CallUiState {
    data object Idle : CallUiState()
    data class Outgoing(
        val callId: String,
        val conversationId: String,
        val peerName: String
    ) : CallUiState()
    data class Incoming(
        val callId: String,
        val conversationId: String,
        val callerName: String
    ) : CallUiState()
    data class Active(
        val callId: String,
        val peerName: String,
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

    private var engine: WebRtcCallEngine? = null
    private var iceServers: List<IceServerDto> = emptyList()
    private var listenJob: Job? = null
    private var activeCallId: String? = null
    private var activePeerName: String = "Deep"
    private var pendingOffer: WsEnvelope? = null
    private val pendingIce = mutableListOf<IceCandidate>()

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

    fun startOutgoing(conversationId: String, peerName: String) {
        if (!hasMicPermission()) {
            _error.value = "Разреши доступ к микрофону для звонка"
            return
        }
        scope.launch {
            try {
                val resp = api.startCall(StartCallRequest(conversationId))
                activeCallId = resp.callId
                activePeerName = peerName
                iceServers = resp.iceServers
                _state.value = CallUiState.Outgoing(resp.callId, conversationId, peerName)
                CallForegroundService.start(context, peerName, outgoing = true)
            } catch (e: Exception) {
                _state.value = CallUiState.Idle
                _error.value = "Не удалось начать звонок"
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
                _state.value = CallUiState.Active(incoming.callId, incoming.callerName, connected = false)
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

    fun handleIncomingPush(data: Map<String, String>) {
        if (data["type"] != "incoming_call") return
        val callId = data["callId"] ?: return
        val conversationId = data["conversationId"] ?: return
        val callerName = data["callerName"] ?: "Deep"
        if (_state.value !is CallUiState.Idle) return
        _state.value = CallUiState.Incoming(callId, conversationId, callerName)
        signaling.connect()
    }

    private fun handleSignal(env: WsEnvelope) {
        when (env.type) {
            "call_invite" -> {
                val callId = env.callId ?: return
                if (_state.value !is CallUiState.Idle) return
                _state.value = CallUiState.Incoming(
                    callId,
                    env.conversationId.orEmpty(),
                    env.callerName ?: "Deep"
                )
            }
            "call_accept" -> {
                val callId = env.callId ?: return
                if (_state.value is CallUiState.Outgoing) {
                    activeCallId = callId
                    _state.value = CallUiState.Active(callId, activePeerName, connected = false)
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
        teardownRtc()
        engine = WebRtcCallEngine(context, iceServers, object : WebRtcCallEngine.Listener {
            override fun onIceCandidate(candidate: IceCandidate) {
                val callId = activeCallId ?: return
                signaling.sendIce(callId, candidate.sdp, candidate.sdpMid, candidate.sdpMLineIndex)
            }

            override fun onConnectionChange(state: PeerConnection.PeerConnectionState) {
                scope.launch {
                    if (state == PeerConnection.PeerConnectionState.CONNECTED) {
                        _state.update { current ->
                            if (current is CallUiState.Active) current.copy(connected = true) else current
                        }
                    }
                    if (state == PeerConnection.PeerConnectionState.FAILED ||
                        state == PeerConnection.PeerConnectionState.DISCONNECTED
                    ) {
                        if (_state.value is CallUiState.Active) {
                            hangup()
                        }
                    }
                }
            }
        })
        pendingIce.forEach { engine?.addIceCandidate(it) }
        pendingIce.clear()
    }

    private fun endLocal(@Suppress("UNUSED_PARAMETER") reason: String) {
        teardownRtc()
        activeCallId = null
        pendingOffer = null
        pendingIce.clear()
        _muted.value = false
        _speakerOn.value = false
        runCatching {
            val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            am.isSpeakerphoneOn = false
        }
        _state.value = CallUiState.Idle
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
}
