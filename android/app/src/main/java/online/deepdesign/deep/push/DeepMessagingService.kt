package online.deepdesign.deep.push

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import online.deepdesign.deep.DeepApp
import online.deepdesign.deep.data.ChatEvent
import online.deepdesign.deep.data.ChatNotifier
import online.deepdesign.deep.data.FcmRegisterRequest

class DeepMessagingService : FirebaseMessagingService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNewToken(token: String) {
        scope.launch {
            runCatching {
                DeepApp.instance.api.registerFcm(FcmRegisterRequest(token))
            }
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val data = message.data
        when (data["type"]) {
            "incoming_call" -> DeepApp.instance.callManager.handleIncomingPush(data)
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
