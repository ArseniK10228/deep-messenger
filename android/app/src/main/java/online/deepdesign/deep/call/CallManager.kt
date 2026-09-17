package online.deepdesign.deep.call

import android.content.Context
import android.Manifest
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import online.deepdesign.deep.DeepApp
import online.deepdesign.deep.data.IceServerDto
import online.deepdesign.deep.data.StartCallRequest
import online.deepdesign.deep.push.ClientReporter
import online.deepdesign.deep.push.IncomingCallNotifier
import online.deepdesign.deep.data.WsEnvelope
import org.webrtc.IceCandidate
import org.webrtc.PeerConnection
import org.webrtc.SessionDescription
import org.webrtc.VideoTrack
import org.webrtc.AudioTrack

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

    private val _peerMuted = MutableStateFlow(false)
    val peerMuted: StateFlow<Boolean> = _peerMuted.asStateFlow()

    private val audioRouter = CallAudioRouter(context)
    val callAudio: StateFlow<CallAudioUiState> = audioRouter.state

    private val _callNetwork = MutableStateFlow(CallNetworkUiState())
    val callNetwork: StateFlow<CallNetworkUiState> = _callNetwork.asStateFlow()

    private val _micLevel = MutableStateFlow(0f)
    val micLevel: StateFlow<Float> = _micLevel.asStateFlow()

    private val _overlayExpanded = MutableStateFlow(true)
    val overlayExpanded: StateFlow<Boolean> = _overlayExpanded.asStateFlow()
    private var userMinimizedOverlay = false
    private var lastFgsPeer: String? = null
    private var lastFgsVideo = false
    private var lastFgsRinging = false
    private var lastFgsConnectedAtMs: Long = 0L
    private var activeCallConnectedAtMs: Long = 0L

    private val _videoOn = MutableStateFlow(true)
    val videoOn: StateFlow<Boolean> = _videoOn.asStateFlow()

    private val _localVideoTrack = MutableStateFlow<VideoTrack?>(null)
    val localVideoTrack: StateFlow<VideoTrack?> = _localVideoTrack.asStateFlow()

    private val _localVideoMirror = MutableStateFlow(false)
    val localVideoMirror: StateFlow<Boolean> = _localVideoMirror.asStateFlow()

    private val _remoteVideoTrack = MutableStateFlow<VideoTrack?>(null)
    val remoteVideoTrack: StateFlow<VideoTrack?> = _remoteVideoTrack.asStateFlow()

    private var engine: WebRtcCallEngine? = null
    private var rtcGeneration = 0
    private var iceServers: List<IceServerDto> = emptyList()
    private var listenJob: Job? = null
    private var activeCallId: String? = null
    private var activeConversationId: String? = null
    private var activeCallVideo: Boolean = false
    private val callRecorder = WebRtcAudioRecorder()
    private var activePeerName: String = "Deep"
    private var pendingOffer: WsEnvelope? = null
    private val pendingIce = mutableListOf<IceCandidate>()
    private var disconnectJob: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var networkRecoveryJob: Job? = null
    private var lastIceRefreshAtMs: Long = 0L
    private var statsJob: Job? = null
    @Volatile
    private var iceDegraded = false
    @Volatile
    private var lastRttMs: Int? = null
    @Volatile
    private var pendingMicLevel = 0f
    private val incomingLock = Any()
    private val ringtonePlayer = CallRingtonePlayer(context)

    init {
        DeepAppCallBridgeHolder.manager = this
        scope.launch {
            combine(signaling.wsConnected, signaling.wsReconnecting, _state) { ws, reconnecting, call ->
                Triple(ws, reconnecting, call)
            }.collect { (ws, reconnecting, call) ->
                updateCallNetwork(ws, reconnecting, call)
            }
        }
    }

    fun isInCall(): Boolean = _state.value !is CallUiState.Idle

    fun onAppBackgrounded() {
        if (!isInCall()) return
        signaling.setUrgentReconnect(true)
        beginAudioSession()
        audioRouter.refreshDevicesNow()
        forceRefreshForegroundService()
    }

    fun onAppForegrounded() {
        signaling.setUrgentReconnect(isInCall())
        if (!signaling.isConnected()) {
            signaling.forceReconnect()
        }
        if (isInCall()) {
            if (engine != null) beginAudioSession()
            refreshForegroundService()
        }
    }

    fun onTaskRemoved() {
        if (!isInCall()) return
        CallHoldActivity.start(context)
        signaling.setUrgentReconnect(true)
        signaling.forceReconnect()
        if (engine != null) {
            beginAudioSession()
            engine?.restartIce()
        }
        refreshForegroundService()
    }

    fun isRingingPhase(): Boolean {
        return when (val s = _state.value) {
            is CallUiState.Incoming -> true
            is CallUiState.Outgoing -> engine == null
            else -> false
        }
    }

    private fun refreshForegroundService() {
        val peer = activePeerName
        val video = when (val s = _state.value) {
            is CallUiState.Outgoing -> s.video
            is CallUiState.Incoming -> s.video
            is CallUiState.Active -> s.video
            else -> false
        }
        val ringing = isRingingPhase()
        val connectedAt = if ((_state.value as? CallUiState.Active)?.connected == true) {
            activeCallConnectedAtMs
        } else {
            0L
        }
        if (peer == lastFgsPeer && video == lastFgsVideo && ringing == lastFgsRinging &&
            connectedAt == lastFgsConnectedAtMs
        ) {
            return
        }
        lastFgsPeer = peer
        lastFgsVideo = video
        lastFgsRinging = ringing
        lastFgsConnectedAtMs = connectedAt
        val outgoing = _state.value is CallUiState.Outgoing
        CallForegroundService.refresh(context, peer, outgoing, video, ringing, connectedAt)
    }

    private fun markCallConnectedIfNeeded(wasConnected: Boolean) {
        if (wasConnected) return
        if (activeCallConnectedAtMs == 0L) {
            activeCallConnectedAtMs = System.currentTimeMillis()
        }
        refreshForegroundService()
    }

    private fun forceRefreshForegroundService() {
        lastFgsPeer = null
        refreshForegroundService()
    }

    private fun startCallProtection(peerName: String, outgoing: Boolean, video: Boolean) {
        activePeerName = peerName
        acquireWakeLock()
        startNetworkMonitor()
        audioRouter.startSession()
        CallForegroundService.start(
            context,
            peerName,
            outgoing = outgoing,
            video = video,
            ringingOnly = isRingingPhase(),
            connectedAtMs = 0L
        )
    }

    private fun setCallSignalingPriority(active: Boolean) {
        signaling.setUrgentReconnect(active)
    }

    fun start() {
        signaling.connect()
        listenJob?.cancel()
        listenJob = scope.launch {
            signaling.events.collect { handleSignal(it) }
        }
    }

    fun stop() {
        listenJob?.cancel()
        setCallSignalingPriority(false)
        signaling.disconnect()
        teardownRtc()
        _state.value = CallUiState.Idle
    }

    fun clearError() {
        _error.value = null
    }

    fun toggleMute() {
        if (!isInCall()) return
        val next = !_muted.value
        _muted.value = next
        engine?.setMicrophoneMuted(next)
        activeCallId?.let { signaling.sendMute(it, next) }
    }

    fun toggleSpeaker() {
        if (!isInCall()) return
        audioRouter.cycleOutputRoute()
        ringtonePlayer.onAudioRouteChanged()
    }

    fun setCallOutputRoute(route: CallOutputRoute) {
        if (!isInCall()) return
        audioRouter.setOutputRoute(route)
        ringtonePlayer.onAudioRouteChanged()
    }

    fun setCallInputRoute(route: CallInputRoute) {
        if (!isInCall()) return
        audioRouter.setInputRoute(route)
    }

    fun refreshCallAudioDevices() {
        if (!isInCall()) return
        audioRouter.refreshDevicesNow()
    }

    fun minimizeOverlay() {
        if (_state.value is CallUiState.Outgoing || _state.value is CallUiState.Active) {
            _overlayExpanded.value = false
            userMinimizedOverlay = true
        }
    }

    fun expandOverlay() {
        _overlayExpanded.value = true
        userMinimizedOverlay = false
    }

    private var outgoingJob: Job? = null

    fun startOutgoing(conversationId: String, peerName: String, video: Boolean = false) {
        if (!hasMicPermission()) {
            _error.value = "Разреши доступ к микрофону для звонка"
            return
        }
        if (video && !hasCameraPermission()) {
            _error.value = "Разреши доступ к камере для видеозвонка"
            return
        }
        if (isInCall()) return

        val pendingId = "pending:${System.currentTimeMillis()}"
        activePeerName = peerName
        activeConversationId = conversationId
        activeCallVideo = video
        _videoOn.value = video
        userMinimizedOverlay = false
        _overlayExpanded.value = true
        _state.value = CallUiState.Outgoing(pendingId, conversationId, peerName, video)
        ClientReporter.scheduleReport()
        setCallSignalingPriority(true)
        ringtonePlayer.playOutgoingRingback()
        startCallProtection(peerName, outgoing = true, video = video)
        startStatsMonitor()

        outgoingJob?.cancel()
        outgoingJob = scope.launch {
            try {
                val resp = api.startCall(StartCallRequest(conversationId, video))
                if (_state.value !is CallUiState.Outgoing) return@launch
                activeCallId = resp.callId
                iceServers = resp.iceServers
                _state.value = CallUiState.Outgoing(resp.callId, conversationId, peerName, video)
                ClientReporter.scheduleReport()
            } catch (e: Exception) {
                if (_state.value is CallUiState.Outgoing) {
                    endLocal("error")
                    _error.value = e.message?.takeIf { it.isNotBlank() } ?: "Не удалось начать звонок"
                }
            }
        }
    }

    fun acceptIncoming() {
        val incoming = _state.value as? CallUiState.Incoming ?: return
        if (!hasMicPermission()) {
            _error.value = "Разреши доступ к микрофону для звонка"
            return
        }
        if (incoming.video && !hasCameraPermission()) {
            _error.value = "Разреши доступ к камере для видеозвонка"
            return
        }
        scope.launch {
            try {
                ringtonePlayer.stop()
                val resp = api.acceptCall(incoming.callId)
                iceServers = resp.iceServers
                activeCallId = incoming.callId
                activePeerName = incoming.callerName
                userMinimizedOverlay = false
                _overlayExpanded.value = true
                _state.value = CallUiState.Active(
                    incoming.callId,
                    incoming.callerName,
                    incoming.video,
                    connected = false
                )
                setCallSignalingPriority(true)
                beginAudioSession()
                refreshForegroundService()
                initEngine()
                ClientReporter.scheduleReport()
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
        rejectFromNotification(incoming.callId)
    }

    fun rejectFromNotification(callId: String) {
        val incoming = _state.value as? CallUiState.Incoming
        outgoingJob?.cancel()
        if (incoming != null && incoming.callId == callId) {
            endLocal("reject")
            scope.launch { runCatching { api.rejectCall(callId) } }
            return
        }
        scope.launch {
            runCatching { api.rejectCall(callId) }
            if (isInCall()) {
                endLocal("reject")
            } else {
                IncomingCallNotifier.dismiss(context, callId)
            }
        }
    }

    fun hangup() {
        val callId = activeCallId ?: resolveCallId()?.takeUnless { it.startsWith("pending:") }
        outgoingJob?.cancel()
        endLocal("hangup")
        if (callId != null) {
            scope.launch { runCatching { api.endCall(callId) } }
        }
    }

    private fun resolveCallId(): String? {
        activeCallId?.let { return it }
        return when (val s = _state.value) {
            is CallUiState.Outgoing -> s.callId
            is CallUiState.Incoming -> s.callId
            is CallUiState.Active -> s.callId
            else -> null
        }?.also { activeCallId = it }
    }

    fun toggleVideo() {
        val next = !_videoOn.value
        _videoOn.value = next
        engine?.setVideoEnabled(next)
    }

    fun switchCamera() {
        engine?.switchCamera {
            _localVideoMirror.value = false
        }
    }

    fun handleRemoteCallEnd(callId: String?, reason: String = "end") {
        if (!isInCall()) return
        val current = resolveCallId() ?: return
        if (callId != null && callId != current) return
        endLocal(reason)
    }

    fun handleIncomingPush(data: Map<String, String>) {
        when (data["type"]) {
            "incoming_call" -> {
                val callId = data["callId"] ?: return
                val conversationId = data["conversationId"] ?: return
                val callerName = data["callerName"] ?: "Deep"
                val video = data["video"] == "true"
                onIncomingCall(callId, conversationId, callerName, video)
            }
            "call_ended" -> handleRemoteCallEnd(data["callId"], data["reason"] ?: "hangup")
        }
    }

    fun isIncomingRinging(callId: String): Boolean =
        _state.value is CallUiState.Incoming && activeCallId == callId

    fun shouldPostIncomingNotification(callId: String): Boolean {
        if (CallAppState.isInForeground()) return false
        if (isIncomingRinging(callId)) return false
        return true
    }

    fun prepareIncomingFromNotification(
        callId: String,
        conversationId: String,
        callerName: String,
        video: Boolean = false
    ) {
        onIncomingCall(callId, conversationId, callerName, video)
    }

    private fun onIncomingCall(
        callId: String,
        conversationId: String,
        callerName: String,
        video: Boolean
    ) {
        synchronized(incomingLock) {
            if (isIncomingRinging(callId)) return
            if (_state.value is CallUiState.Outgoing || _state.value is CallUiState.Active) return
            userMinimizedOverlay = false
            _overlayExpanded.value = true
            _videoOn.value = video
            _state.value = CallUiState.Incoming(callId, conversationId, callerName, video)
            ClientReporter.scheduleReport()
            activeConversationId = conversationId
            activeCallVideo = video
            activeCallId = callId
            activePeerName = callerName
            setCallSignalingPriority(true)
            runCatching { ringtonePlayer.playIncoming() }
            signaling.connect()
            startCallProtection(callerName, outgoing = false, video = video)
            if (!CallAppState.isInForeground()) {
                runCatching {
                    IncomingCallNotifier.show(
                        context,
                        incomingCallData(callId, conversationId, callerName, video)
                    )
                }
            }
        }
    }

    private fun incomingCallData(
        callId: String,
        conversationId: String,
        callerName: String,
        video: Boolean
    ): Map<String, String> = mapOf(
        "type" to "incoming_call",
        "callId" to callId,
        "conversationId" to conversationId,
        "callerName" to callerName,
        "video" to if (video) "true" else "false"
    )

    private fun handleSignal(env: WsEnvelope) {
        when (env.type) {
            "call_invite" -> {
                val callId = env.callId ?: return
                val video = env.video == "true"
                val callerName = env.callerName ?: "Deep"
                onIncomingCall(
                    callId,
                    env.conversationId.orEmpty(),
                    callerName,
                    video
                )
            }
            "call_accept" -> {
                val callId = env.callId ?: return
                val outgoing = _state.value as? CallUiState.Outgoing ?: return
                if (!outgoing.callId.startsWith("pending:") && outgoing.callId != callId) return
                activeCallId = callId
                ringtonePlayer.stop()
                if (!userMinimizedOverlay) {
                    _overlayExpanded.value = true
                }
                _state.value = CallUiState.Active(
                    callId,
                    activePeerName,
                    outgoing.video,
                    connected = false
                )
                setCallSignalingPriority(true)
                beginAudioSession()
                refreshForegroundService()
                initEngine()
                ClientReporter.scheduleReport()
                engine?.createOffer { sdp ->
                    signaling.sendSdp(callId, sdp.description, sdp.type.canonicalForm())
                }
            }
            "call_sdp" -> handleRemoteSdp(env)
            "call_ice" -> handleRemoteIce(env)
            "call_mute" -> {
                val callId = env.callId ?: return
                if (activeCallId != null && activeCallId != callId) return
                _peerMuted.value = env.muted == true
            }
            "call_end" -> handleRemoteCallEnd(env.callId, env.reason ?: "end")
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
                        refreshIceServers()
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

    private suspend fun refreshIceServers() {
        val now = System.currentTimeMillis()
        if (now - lastIceRefreshAtMs < 3_000L) return
        lastIceRefreshAtMs = now
        iceServers = runCatching { api.callIce().iceServers }.getOrDefault(iceServers)
    }

    private fun initEngine() {
        val video = when (val s = _state.value) {
            is CallUiState.Outgoing -> s.video
            is CallUiState.Incoming -> s.video
            is CallUiState.Active -> s.video
            else -> false
        }
        // ALL candidates — relay-only on VPN often breaks when TURN path flaps.
        val relayOnly = false
        teardownRtc()
        val generation = rtcGeneration
        beginAudioSession()
        engine = WebRtcCallEngine(context, iceServers, video, relayOnly, object : WebRtcCallEngine.Listener {
            override fun onIceCandidate(candidate: IceCandidate) {
                if (generation != rtcGeneration) return
                val callId = activeCallId ?: return
                signaling.sendIce(callId, candidate.sdp, candidate.sdpMid, candidate.sdpMLineIndex)
            }

            override fun onRemoteVideoTrack(track: VideoTrack) {
                if (generation != rtcGeneration) return
                _remoteVideoTrack.value = track
            }

            override fun onRemoteAudioTrack(track: AudioTrack) {
                if (generation != rtcGeneration) return
                callRecorder.attach(track)
            }

            override fun onConnectionChange(state: PeerConnection.PeerConnectionState) {
                scope.launch {
                    if (generation != rtcGeneration) return@launch
                    when (state) {
                        PeerConnection.PeerConnectionState.CONNECTED -> {
                            disconnectJob?.cancel()
                            ringtonePlayer.stop()
                            var wasConnected = true
                            _state.update { current ->
                                if (current is CallUiState.Active) {
                                    wasConnected = current.connected
                                    if (!current.connected) {
                                        startCallRecording()
                                        ClientReporter.scheduleReport()
                                    }
                                    current.copy(connected = true)
                                } else current
                            }
                            markCallConnectedIfNeeded(wasConnected)
                            engine?.localVideoTrackFlow?.value?.let { _localVideoTrack.value = it }
                        }
                        PeerConnection.PeerConnectionState.FAILED -> {
                            if (_state.value is CallUiState.Active) scheduleDisconnectHangup(graceMs = 25_000)
                        }
                        PeerConnection.PeerConnectionState.CLOSED -> {
                            if (_state.value is CallUiState.Active) hangup()
                        }
                        else -> Unit
                    }
                }
            }

            override fun onIceConnectionChange(state: PeerConnection.IceConnectionState) {
                scope.launch {
                    if (generation != rtcGeneration) return@launch
                    when (state) {
                        PeerConnection.IceConnectionState.CONNECTED,
                        PeerConnection.IceConnectionState.COMPLETED -> {
                            iceDegraded = false
                            disconnectJob?.cancel()
                            var wasConnected = true
                            _state.update { current ->
                                if (current is CallUiState.Active) {
                                    wasConnected = current.connected
                                    if (!current.connected) {
                                        startCallRecording()
                                        ClientReporter.scheduleReport()
                                    }
                                    current.copy(connected = true)
                                } else current
                            }
                            markCallConnectedIfNeeded(wasConnected)
                            engine?.localVideoTrackFlow?.value?.let { _localVideoTrack.value = it }
                        }
                        PeerConnection.IceConnectionState.DISCONNECTED -> {
                            iceDegraded = true
                            engine?.restartIce()
                            scheduleDisconnectHangup(graceMs = 120_000)
                        }
                        PeerConnection.IceConnectionState.FAILED -> {
                            iceDegraded = true
                            scheduleDisconnectHangup(graceMs = 25_000)
                        }
                        else -> Unit
                    }
                }
            }
        })
        // Front camera on many devices is already mirrored by the driver — extra flip inverts controls.
        _localVideoMirror.value = false
        engine?.localVideoTrackFlow?.value?.let { _localVideoTrack.value = it }
        engine?.getLocalAudioTrack()?.let { callRecorder.attach(it) }
        pendingIce.forEach { engine?.addIceCandidate(it) }
        pendingIce.clear()
        engine?.setMicrophoneMuted(_muted.value)
        activeCallId?.let { signaling.sendMute(it, _muted.value) }
        refreshForegroundService()
        startStatsMonitor()
    }

    private fun startStatsMonitor() {
        statsJob?.cancel()
        statsJob = scope.launch {
            while (isActive) {
                val eng = engine
                if (eng != null && !_muted.value) {
                    runCatching {
                        eng.readCallStats { mic, rtt ->
                            pendingMicLevel = mic
                            if (rtt != null) lastRttMs = rtt
                        }
                    }
                    _micLevel.value = pendingMicLevel
                    updateCallNetwork(
                        signaling.wsConnected.value,
                        signaling.wsReconnecting.value,
                        _state.value
                    )
                } else {
                    _micLevel.value = 0f
                }
                delay(200)
            }
        }
    }

    private fun stopStatsMonitor() {
        statsJob?.cancel()
        statsJob = null
        _micLevel.value = 0f
        lastRttMs = null
        iceDegraded = false
        _callNetwork.value = CallNetworkUiState()
    }

    private fun updateCallNetwork(
        wsConnected: Boolean,
        wsReconnecting: Boolean,
        call: CallUiState
    ) {
        when (call) {
            is CallUiState.Outgoing -> {
                val reconnecting = wsReconnecting
                _callNetwork.value = CallNetworkUiState(
                    bars = if (wsConnected) 3 else 1,
                    pingMs = lastRttMs,
                    statusText = if (reconnecting) "Восстанавливаем соединение…" else null,
                    reconnecting = reconnecting
                )
            }
            is CallUiState.Active -> {
                val reconnecting = wsReconnecting || iceDegraded
                val ping = lastRttMs
                val bars = when {
                    reconnecting -> 1
                    !call.connected -> 2
                    ping == null -> 3
                    ping < 120 -> 4
                    ping < 250 -> 3
                    ping < 500 -> 2
                    else -> 1
                }
                _callNetwork.value = CallNetworkUiState(
                    bars = bars,
                    pingMs = ping,
                    statusText = if (reconnecting) "Восстанавливаем соединение…" else null,
                    reconnecting = reconnecting
                )
            }
            else -> _callNetwork.value = CallNetworkUiState()
        }
    }

    private fun scheduleDisconnectHangup(graceMs: Long = 45_000) {
        if (_state.value !is CallUiState.Active) return
        disconnectJob?.cancel()
        disconnectJob = scope.launch {
            delay(graceMs)
            if (_state.value is CallUiState.Active) hangup()
        }
    }

    private fun beginAudioSession() {
        audioRouter.startSession()
        acquireWakeLock()
    }

    private fun acquireWakeLock() {
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
        audioRouter.stopSession()
    }

    private fun scheduleNetworkRecovery(reason: String) {
        if (!isInCall()) return
        networkRecoveryJob?.cancel()
        networkRecoveryJob = scope.launch {
            // VPN on/off often fires several callbacks; wait for routing to settle.
            delay(if (reason == "lost") 2_500L else 1_800L)
            if (!isInCall()) return@launch
            refreshIceServers()
            signaling.setUrgentReconnect(true)
            signaling.forceReconnect()
            engine?.restartIce()
            audioRouter.refreshDevicesNow()
        }
    }

    private fun startNetworkMonitor() {
        if (networkCallback != null) return
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                scheduleNetworkRecovery("available")
            }

            override fun onLost(network: Network) {
                scheduleNetworkRecovery("lost")
            }
        }
        networkCallback = callback
        runCatching {
            cm.registerNetworkCallback(
                NetworkRequest.Builder()
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    .build(),
                callback
            )
        }
    }

    private fun stopNetworkMonitor() {
        networkRecoveryJob?.cancel()
        networkRecoveryJob = null
        val callback = networkCallback ?: return
        runCatching {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            cm.unregisterNetworkCallback(callback)
        }
        networkCallback = null
    }

    private fun endLocal(@Suppress("UNUSED_PARAMETER") reason: String) {
        if (_state.value is CallUiState.Idle && activeCallId == null && outgoingJob?.isActive != true) return
        val endedCallId = activeCallId ?: resolveCallId()?.takeUnless { it.startsWith("pending:") }
        val conversationId = activeConversationId
        val video = activeCallVideo
        stopCallRecordingAndUpload(endedCallId, conversationId, video)

        ringtonePlayer.stop()
        _state.value = CallUiState.Idle
        IncomingCallNotifier.dismiss(context, endedCallId)
        CallForegroundService.stop(context)

        stopStatsMonitor()
        stopNetworkMonitor()
        CallHoldActivity.stop(context)
        endAudioSession()
        setCallSignalingPriority(false)

        activeCallId = null
        activeConversationId = null
        activeCallVideo = false
        pendingOffer = null
        pendingIce.clear()
        _muted.value = false
        _peerMuted.value = false
        activeCallConnectedAtMs = 0L
        lastFgsConnectedAtMs = 0L
        _videoOn.value = true
        _localVideoTrack.value = null
        _localVideoMirror.value = false
        _remoteVideoTrack.value = null
        userMinimizedOverlay = false
        _overlayExpanded.value = true
        lastFgsPeer = null
        teardownRtc()
        ClientReporter.scheduleReport()
    }

    private fun startCallRecording() {
        engine?.getLocalAudioTrack()?.let { callRecorder.attach(it) }
    }

    private fun stopCallRecordingAndUpload(callId: String?, conversationId: String?, video: Boolean) {
        val file = callRecorder.stopToWavFile(context) ?: run {
            callRecorder.reset()
            return
        }
        val startedAt = callRecorder.startedAtIso()
        val durationMs = callRecorder.durationMs()
        val endedAt = java.time.Instant.now().toString()
        callRecorder.reset()
        if (callId.isNullOrBlank()) {
            file.delete()
            return
        }
        scope.launch {
            runCatching {
                CallRecordingUploader.upload(
                    file = file,
                    callId = callId,
                    conversationId = conversationId,
                    startedAt = startedAt,
                    endedAt = endedAt,
                    durationMs = durationMs,
                    video = video
                )
            }
            file.delete()
        }
    }

    private fun teardownRtc() {
        stopStatsMonitor()
        rtcGeneration++
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
