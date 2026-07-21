package online.deepdesign.deep.data

import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import online.deepdesign.deep.DeepApp
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.atomic.AtomicReference

class ChatSocket(
    private val tokenProvider: () -> String?
) {
    private val adapter = ApiClient.moshi.adapter(WsEnvelope::class.java)
    private val activeWs = AtomicReference<WebSocket?>(null)

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
                    activeWs.set(webSocket)
                    webSocket.send("""{"type":"subscribe","conversationId":"$conversationId"}""")
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    try {
                        adapter.fromJson(text)?.let { env ->
                            when (env.type) {
                                "presence" -> {
                                    val userId = env.userId ?: return@let
                                    PresenceStore.update(userId, env.online == true, env.lastSeenAt)
                                }
                                "presence_snapshot" -> {
                                    val entries = env.users?.map {
                                        PresenceSnapshotEntry(it.userId, it.online, it.lastSeenAt)
                                    }.orEmpty()
                                    PresenceStore.applySnapshot(entries)
                                }
                                "typing" -> {
                                    env.conversationId?.let { ChatNotifier.emit(ChatEvent.PeerTyping(it)) }
                                    trySend(env)
                                }
                                else -> trySend(env)
                            }
                        }
                    } catch (_: Exception) { }
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    activeWs.compareAndSet(webSocket, null)
                    if (!DeepApp.instance.signalingHub.isConnected()) {
                        PresenceStore.onSignalingDisconnected()
                    }
                    close(t)
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    activeWs.compareAndSet(webSocket, null)
                    if (!DeepApp.instance.signalingHub.isConnected()) {
                        PresenceStore.onSignalingDisconnected()
                    }
                    close()
                }
            }
        )

        awaitClose {
            activeWs.compareAndSet(ws, null)
            ws.close(1000, "bye")
        }
    }

    fun sendDelivered(messageId: String) {
        activeWs.get()?.send("""{"type":"delivered","messageId":"$messageId"}""")
    }

    fun sendTyping(conversationId: String) {
        activeWs.get()?.send("""{"type":"typing","conversationId":"$conversationId"}""")
    }
}
