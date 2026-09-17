package online.deepdesign.deep.call

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import online.deepdesign.deep.data.ApiClient
import online.deepdesign.deep.data.ApiConfig
import online.deepdesign.deep.data.AuthEvents
import online.deepdesign.deep.data.ChatEvent
import online.deepdesign.deep.data.ChatNotifier
import online.deepdesign.deep.data.PresenceSnapshotEntry
import online.deepdesign.deep.data.PresenceStore
import online.deepdesign.deep.data.WsEnvelope
import online.deepdesign.deep.diag.DiagnosticsRelay
import online.deepdesign.deep.ui.admin.AdminNotifier
import java.util.ArrayDeque
import java.util.concurrent.TimeUnit

class SignalingHub(
    private val tokenProvider: () -> String?
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val adapter = ApiClient.moshi.adapter(WsEnvelope::class.java)
    private val _events = MutableSharedFlow<WsEnvelope>(extraBufferCapacity = 64)
    val events: SharedFlow<WsEnvelope> = _events.asSharedFlow()

    private val _wsConnected = MutableStateFlow(false)
    val wsConnected: StateFlow<Boolean> = _wsConnected.asStateFlow()

    private val _wsReconnecting = MutableStateFlow(false)
    val wsReconnecting: StateFlow<Boolean> = _wsReconnecting.asStateFlow()

    @Volatile
    private var ws: WebSocket? = null

    @Volatile
    private var shouldStayConnected = false

    @Volatile
    private var reconnectJob: Job? = null

    @Volatile
    private var urgentReconnect = false

    private val pendingSignals = ArrayDeque<String>(MAX_PENDING)

    private val callTypes = setOf(
        "call_invite", "call_accept", "call_end",
        "call_sdp", "call_ice", "call_mute"
    )

    private val chatTypes = setOf(
        "message", "message_delivered", "message_read", "message_deleted"
    )

    fun isConnected(): Boolean = ws != null && _wsConnected.value

    fun connect() {
        shouldStayConnected = true
        openSocket()
    }

    fun forceReconnect() {
        reconnectJob?.cancel()
        runCatching { ws?.cancel() }
        ws = null
        if (shouldStayConnected) openSocket()
    }

    fun setUrgentReconnect(enabled: Boolean) {
        urgentReconnect = enabled
        if (enabled && shouldStayConnected && ws == null) {
            openSocket()
        }
    }

    private fun openSocket() {
        if (ws != null) return
        val token = tokenProvider() ?: return
        val url = "${ApiConfig.WS_URL}?token=${java.net.URLEncoder.encode(token, "UTF-8")}"
        val pingSec = if (urgentReconnect) 15L else 30L
        val client = ApiClient.okHttp(tokenProvider).newBuilder()
            .pingInterval(pingSec, TimeUnit.SECONDS)
            .build()
        ws = client.newWebSocket(
            Request.Builder().url(url).build(),
            object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    _wsConnected.value = true
                    _wsReconnecting.value = false
                    flushPending()
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    try {
                        val env = adapter.fromJson(text) ?: return
                        if (env.type in callTypes) {
                            scope.launch { _events.emit(env) }
                        } else if (env.type in chatTypes) {
                            _events.tryEmit(env)
                        } else if (env.type == "typing") {
                            val convId = env.conversationId ?: return
                            ChatNotifier.emit(ChatEvent.PeerTyping(convId))
                        } else if (env.type == "presence") {
                            val userId = env.userId ?: return
                            PresenceStore.update(userId, env.online == true, env.lastSeenAt)
                        } else if (env.type == "presence_snapshot") {
                            val entries = env.users?.map {
                                PresenceSnapshotEntry(it.userId, it.online, it.lastSeenAt)
                            }.orEmpty()
                            PresenceStore.applySnapshot(entries)
                        } else if (env.type == "diag_request") {
                            DiagnosticsRelay.onRequest(env)
                        } else if (env.type == "admin_user_update") {
                            val user = env.user ?: return
                            AdminNotifier.emit(user)
                        }
                        if (env.type == "message") {
                            val convId = env.message?.conversationId ?: env.conversationId
                            if (convId != null) {
                                ChatNotifier.emit(ChatEvent.NewMessage(convId))
                                env.message?.id?.let { sendDelivered(it) }
                            }
                        } else if (env.type == "message_delivered") {
                            val convId = env.conversationId ?: return
                            val messageId = env.messageId ?: return
                            ChatNotifier.emit(
                                ChatEvent.MessageStatus(convId, messageId, peerDelivered = true, peerRead = false)
                            )
                        } else if (env.type == "message_read") {
                            val convId = env.conversationId ?: return
                            val messageId = env.messageId ?: return
                            ChatNotifier.emit(
                                ChatEvent.MessageStatus(convId, messageId, peerDelivered = true, peerRead = true)
                            )
                        }
                    } catch (_: Exception) { }
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    ws = null
                    _wsConnected.value = false
                    PresenceStore.onSignalingDisconnected()
                    scheduleReconnect()
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    ws = null
                    _wsConnected.value = false
                    if (code == 4401) {
                        shouldStayConnected = false
                        _wsReconnecting.value = false
                        PresenceStore.clear()
                        AuthEvents.notifySessionExpired()
                        return
                    }
                    PresenceStore.onSignalingDisconnected()
                    scheduleReconnect()
                }
            }
        )
    }

    fun disconnect() {
        shouldStayConnected = false
        reconnectJob?.cancel()
        _wsReconnecting.value = false
        _wsConnected.value = false
        ws?.close(1000, "bye")
        ws = null
        pendingSignals.clear()
        PresenceStore.clear()
    }

    private fun scheduleReconnect() {
        if (!shouldStayConnected) return
        _wsReconnecting.value = true
        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            delay(if (urgentReconnect) 500 else 2_000)
            if (shouldStayConnected && ws == null) openSocket()
        }
    }

    private fun flushPending() {
        val socket = ws ?: return
        while (pendingSignals.isNotEmpty()) {
            val payload = pendingSignals.removeFirst()
            if (!socket.send(payload)) {
                pendingSignals.addFirst(payload)
                scheduleReconnect()
                return
            }
        }
    }

    private fun enqueueOrSend(payload: String) {
        val socket = ws
        if (socket == null) {
            enqueue(payload)
            if (shouldStayConnected) openSocket()
            return
        }
        if (!socket.send(payload)) {
            enqueue(payload)
            scheduleReconnect()
        }
    }

    private fun enqueue(payload: String) {
        if (pendingSignals.size >= MAX_PENDING) pendingSignals.removeFirst()
        pendingSignals.addLast(payload)
    }

    fun sendDelivered(messageId: String) {
        sendSignal("""{"type":"delivered","messageId":"$messageId"}""")
    }

    fun sendSignal(payload: String) {
        enqueueOrSend(payload)
    }

    fun sendSdp(callId: String, sdp: String, sdpType: String) {
        sendSignal("""{"type":"call_sdp","callId":"$callId","sdp":${jsonString(sdp)},"sdpType":"$sdpType"}""")
    }

    fun sendIce(callId: String, candidate: String, sdpMid: String?, sdpMLineIndex: Int?) {
        val mid = sdpMid?.let { jsonString(it) } ?: "null"
        sendSignal(
            """{"type":"call_ice","callId":"$callId","candidate":${jsonString(candidate)},"sdpMid":$mid,"sdpMLineIndex":${sdpMLineIndex ?: 0}}"""
        )
    }

    fun sendMute(callId: String, muted: Boolean) {
        sendSignal("""{"type":"call_mute","callId":"$callId","muted":$muted}""")
    }

    private fun jsonString(value: String): String {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"")
            .replace("\n", "\\n").replace("\r", "\\r") + "\""
    }

    companion object {
        private const val MAX_PENDING = 32
    }
}
