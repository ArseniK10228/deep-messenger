package online.deepdesign.deep.call

import android.content.Context
import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
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

    private val _localVideoMirror = MutableStateFlow(false)
    val localVideoMirror: StateFlow<Boolean> = _localVideoMirror.asStateFlow()

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
    private var audioFocusRequest: AudioFocusRequest? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private val ringtonePlayer = CallRingtonePlayer(context)

    init {
        DeepAppCallBridgeHolder.manager = this
    }

    fun isInCall(): Boolean = _state.value !is CallUiState.Idle

    fun onAppBackgrounded() {
        if (!isInCall()) return
        signaling.setUrgentReconnect(true)
        signaling.forceReconnect()
        if (engine != null) {
            beginAudioSession()
            engine?.restartIce()
        }
        refreshForegroundService()
    }

    fun onAppForegrounded() {
        signaling.setUrgentReconnect(isInCall())
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
        val outgoing = _state.value is CallUiState.Outgoing
        CallForegroundService.refresh(context, peer, outgoing, video, isRingingPhase())
    }

    private fun startCallProtection(peerName: String, outgoing: Boolean, video: Boolean) {
        activePeerName = peerName
        acquireWakeLock()
        startNetworkMonitor()
        CallHoldActivity.start(context)
        CallForegroundService.start(
            context,
            peerName,
            outgoing = outgoing,
            video = video,
            ringingOnly = isRingingPhase()
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
                setCallSignalingPriority(true)
                ringtonePlayer.playOutgoingRingback()
                startCallProtection(peerName, outgoing = true, video = video)
                if (video) initEngine()
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
        engine?.switchCamera {
            _localVideoMirror.value = false
        }
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
        activeCallId = callId
        activePeerName = callerName
        setCallSignalingPriority(true)
        ringtonePlayer.playIncoming()
        signaling.connect()
        startCallProtection(callerName, outgoing = false, video = video)
    }

    private fun handleSignal(env: WsEnvelope) {
        when (env.type) {
            "call_invite" -> {
                val callId = env.callId ?: return
                if (_state.value !is CallUiState.Idle) return
                val video = env.video == "true"
                val callerName = env.callerName ?: "Deep"
                activeCallId = callId
                activePeerName = callerName
                _videoOn.value = video
                _state.value = CallUiState.Incoming(
                    callId,
                    env.conversationId.orEmpty(),
                    callerName,
                    video
                )
                setCallSignalingPriority(true)
                ringtonePlayer.playIncoming()
                startCallProtection(callerName, outgoing = false, video = video)
            }
            "call_accept" -> {
                val callId = env.callId ?: return
                if (_state.value is CallUiState.Outgoing) {
                    val outgoing = _state.value as CallUiState.Outgoing
                    activeCallId = callId
                    ringtonePlayer.stop()
                    _overlayExpanded.value = true
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
                            ringtonePlayer.stop()
                            _state.update { current ->
                                if (current is CallUiState.Active) {
                                    current.copy(connected = true)
                                } else current
                            }
                            engine?.localVideoTrackFlow?.value?.let { _localVideoTrack.value = it }
                        }
                        PeerConnection.PeerConnectionState.FAILED -> {
                            if (_state.value is CallUiState.Active) scheduleDisconnectHangup(graceMs = 25_000)
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
                        PeerConnection.IceConnectionState.DISCONNECTED -> {
                            engine?.restartIce()
                            scheduleDisconnectHangup(graceMs = 90_000)
                        }
                        PeerConnection.IceConnectionState.FAILED -> scheduleDisconnectHangup(graceMs = 25_000)
                        else -> Unit
                    }
                }
            }
        })
        // Front camera on many devices is already mirrored by the driver — extra flip inverts controls.
        _localVideoMirror.value = false
        engine?.localVideoTrackFlow?.value?.let { _localVideoTrack.value = it }
        pendingIce.forEach { engine?.addIceCandidate(it) }
        pendingIce.clear()
        refreshForegroundService()
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
        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        am.mode = AudioManager.MODE_IN_COMMUNICATION
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val attrs = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
            val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(attrs)
                .setOnAudioFocusChangeListener { focus ->
                    if (focus == AudioManager.AUDIOFOCUS_GAIN) {
                        am.mode = AudioManager.MODE_IN_COMMUNICATION
                    }
                }
                .build()
            audioFocusRequest = request
            am.requestAudioFocus(request)
        } else {
            @Suppress("DEPRECATION")
            am.requestAudioFocus(
                { focus ->
                    if (focus == AudioManager.AUDIOFOCUS_GAIN) {
                        am.mode = AudioManager.MODE_IN_COMMUNICATION
                    }
                },
                AudioManager.STREAM_VOICE_CALL,
                AudioManager.AUDIOFOCUS_GAIN
            )
        }
        am.isSpeakerphoneOn = _speakerOn.value
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
        runCatching {
            val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                audioFocusRequest?.let { am.abandonAudioFocusRequest(it) }
            } else {
                @Suppress("DEPRECATION")
                am.abandonAudioFocus(null)
            }
            audioFocusRequest = null
            am.isSpeakerphoneOn = false
            am.mode = AudioManager.MODE_NORMAL
        }
    }

    private fun startNetworkMonitor() {
        if (networkCallback != null) return
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                if (!isInCall()) return
                scope.launch {
                    signaling.forceReconnect()
                    engine?.restartIce()
                }
            }

            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                if (!isInCall()) return
                if (caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
                    scope.launch {
                        engine?.restartIce()
                    }
                }
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
        val callback = networkCallback ?: return
        runCatching {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            cm.unregisterNetworkCallback(callback)
        }
        networkCallback = null
    }

    private fun endLocal(@Suppress("UNUSED_PARAMETER") reason: String) {
        ringtonePlayer.stop()
        stopNetworkMonitor()
        CallHoldActivity.stop(context)
        endAudioSession()
        teardownRtc()
        activeCallId = null
        pendingOffer = null
        pendingIce.clear()
        _muted.value = false
        _speakerOn.value = false
        _videoOn.value = true
        _localVideoTrack.value = null
        _localVideoMirror.value = false
        _remoteVideoTrack.value = null
        _overlayExpanded.value = true
        setCallSignalingPriority(false)
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
