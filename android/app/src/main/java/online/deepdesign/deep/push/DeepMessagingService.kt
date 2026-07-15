package online.deepdesign.deep.push

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import online.deepdesign.deep.DeepApp
import online.deepdesign.deep.SessionBootstrap
import online.deepdesign.deep.data.ChatEvent
import online.deepdesign.deep.data.ChatNotifier
import online.deepdesign.deep.data.FcmRegisterRequest

class DeepMessagingService : FirebaseMessagingService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNewToken(token: String) {
        scope.launch {
            val app = DeepApp.instance
            SessionBootstrap.restore(app.sessionStore, app)
            runCatching {
                app.api.registerFcm(FcmRegisterRequest(token))
            }
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val data = message.data
        val notifTitle = message.notification?.title
        val notifBody = message.notification?.body
        scope.launch {
            val app = DeepApp.instance
            SessionBootstrap.restore(app.sessionStore, app)
            when (data["type"]) {
                "incoming_call" -> {
                    app.callManager.handleIncomingPush(data)
                    val callId = data["callId"]
                    if (callId != null && app.callManager.shouldPostIncomingNotification(callId)) {
                        IncomingCallNotifier.show(this@DeepMessagingService, data)
                    }
                }
                "call_ended" -> {
                    app.callManager.handleIncomingPush(data)
                }
                "message" -> {
                    val convId = data["conversationId"]
                    val sender = notifTitle ?: data["senderName"] ?: data["senderUsername"] ?: "Deep"
                    val preview = notifBody ?: data["preview"] ?: data["body"] ?: "Новое сообщение"
                    if (convId != null) {
                        MessageNotifier.show(
                            this@DeepMessagingService,
                            convId,
                            sender,
                            preview
                        )
                        ChatNotifier.emit(ChatEvent.NewMessage(convId))
                    } else {
                        ChatNotifier.emit(ChatEvent.RefreshChats)
                    }
                }
            }
        }
    }
}
