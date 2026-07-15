package online.deepdesign.deep.call

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import online.deepdesign.deep.data.ApiClient
import online.deepdesign.deep.data.ApiConfig
import online.deepdesign.deep.data.ChatEvent
import online.deepdesign.deep.data.ChatNotifier
import online.deepdesign.deep.data.WsEnvelope

class SignalingHub(
    private val tokenProvider: () -> String?
) {
    private val adapter = ApiClient.moshi.adapter(WsEnvelope::class.java)
    private val _events = MutableSharedFlow<WsEnvelope>(extraBufferCapacity = 64)
    val events: SharedFlow<WsEnvelope> = _events.asSharedFlow()

    @Volatile
    private var ws: WebSocket? = null

    private val callTypes = setOf(
        "call_invite", "call_accept", "call_end",
        "call_sdp", "call_ice"
    )

    private val chatTypes = setOf(
        "message", "message_delivered", "message_read", "message_deleted"
    )

    fun connect() {
        if (ws != null) return
        val token = tokenProvider() ?: return
        val url = "${ApiConfig.WS_URL}?token=${java.net.URLEncoder.encode(token, "UTF-8")}"
        val client = ApiClient.okHttp(tokenProvider)
        ws = client.newWebSocket(
            Request.Builder().url(url).build(),
            object : WebSocketListener() {
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
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    ws = null
                }
            }
        )
    }

    fun disconnect() {
        ws?.close(1000, "bye")
        ws = null
    }

    fun sendDelivered(messageId: String) {
        sendSignal("""{"type":"delivered","messageId":"$messageId"}""")
    }

    fun sendSignal(payload: String) {
        ws?.send(payload)
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
}
