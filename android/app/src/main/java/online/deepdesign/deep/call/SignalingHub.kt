package online.deepdesign.deep.call

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import online.deepdesign.deep.data.ApiClient
import online.deepdesign.deep.data.ApiConfig
import online.deepdesign.deep.data.ChatEvent
import online.deepdesign.deep.data.ChatNotifier
import online.deepdesign.deep.data.WsEnvelope
import java.util.ArrayDeque
import java.util.concurrent.TimeUnit

class SignalingHub(
    private val tokenProvider: () -> String?
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val adapter = ApiClient.moshi.adapter(WsEnvelope::class.java)
    private val _events = MutableSharedFlow<WsEnvelope>(extraBufferCapacity = 64)
    val events: SharedFlow<WsEnvelope> = _events.asSharedFlow()

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
        "call_sdp", "call_ice"
    )

    private val chatTypes = setOf(
        "message", "message_delivered", "message_read", "message_deleted"
    )

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
        if (enabled && shouldStayConnected) {
            if (ws == null) openSocket() else forceReconnect()
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
                    flushPending()
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    try {
                        val env = adapter.fromJson(text) ?: return
                        if (env.type in callTypes || env.type in chatTypes) {
                            _events.tryEmit(env)
                        }
                        if (env.type == "message") {
                            val convId = env.message?.conversationId ?: env.conversationId
                            if (convId != null) {
                                ChatNotifier.emit(ChatEvent.NewMessage(convId))
                                env.message?.id?.let { sendDelivered(it) }
                            }
                        }
                    } catch (_: Exception) { }
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    ws = null
                    scheduleReconnect()
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    ws = null
                    scheduleReconnect()
                }
            }
        )
    }

    fun disconnect() {
        shouldStayConnected = false
        reconnectJob?.cancel()
        ws?.close(1000, "bye")
        ws = null
        pendingSignals.clear()
    }

    private fun scheduleReconnect() {
        if (!shouldStayConnected) return
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

    private fun jsonString(value: String): String {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"")
            .replace("\n", "\\n").replace("\r", "\\r") + "\""
    }

    companion object {
        private const val MAX_PENDING = 32
    }
}
