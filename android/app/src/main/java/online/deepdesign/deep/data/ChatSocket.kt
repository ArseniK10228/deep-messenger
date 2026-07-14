package online.deepdesign.deep.data

import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener

class ChatSocket(
    private val tokenProvider: () -> String?
) {
    private val adapter = ApiClient.moshi.adapter(WsEnvelope::class.java)

    fun events(conversationId: String): Flow<WsEnvelope> = callbackFlow {
        val token = tokenProvider() ?: run {
            close()
            return@callbackFlow
        }
        val url = "${ApiConfig.WS_URL}?token=${java.net.URLEncoder.encode(token, "UTF-8")}"
        val client = ApiClient.okHttp(tokenProvider)
        val ws = client.newWebSocket(
            Request.Builder().url(url).build(),
            object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    webSocket.send("""{"type":"subscribe","conversationId":"$conversationId"}""")
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    try {
                        adapter.fromJson(text)?.let { trySend(it) }
                    } catch (_: Exception) { }
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    close(t)
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    close()
                }
            }
        )

        awaitClose { ws.close(1000, "bye") }
    }

    fun sendTyping(conversationId: String) {
        // optional: separate lightweight socket or reuse — skip for MVP
    }
}
