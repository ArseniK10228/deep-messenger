package online.deepdesign.deep.push

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import online.deepdesign.deep.DeepApp
import online.deepdesign.deep.SessionBootstrap
import online.deepdesign.deep.call.IncomingCallActivity
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
        scope.launch {
            val app = DeepApp.instance
            SessionBootstrap.restore(app.sessionStore, app)
            when (data["type"]) {
                "incoming_call" -> {
                    app.callManager.handleIncomingPush(data)
                    IncomingCallNotifier.show(this@DeepMessagingService, data)
                    val callId = data["callId"] ?: return@launch
                    val conversationId = data["conversationId"].orEmpty()
                    val callerName = data["callerName"] ?: "Deep"
                    try {
                        startActivity(
                            IncomingCallActivity.intent(
                                this@DeepMessagingService,
                                callId,
                                conversationId,
                                callerName
                            )
                        )
                    } catch (_: Exception) {
                        // Full-screen intent from notification is the fallback.
                    }
                }
                "message" -> {
                    val convId = data["conversationId"]
                    if (convId != null) {
                        ChatNotifier.emit(ChatEvent.NewMessage(convId))
                    } else {
                        ChatNotifier.emit(ChatEvent.RefreshChats)
                    }
                }
            }
        }
    }
}
