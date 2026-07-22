package online.deepdesign.deep.data

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/** App-wide chat events from WS / FCM push. */
object ChatNotifier {
    private val _events = MutableSharedFlow<ChatEvent>(extraBufferCapacity = 64)
    val events: SharedFlow<ChatEvent> = _events.asSharedFlow()

    fun emit(event: ChatEvent) {
        _events.tryEmit(event)
    }
}

sealed class ChatEvent {
    data class NewMessage(val conversationId: String) : ChatEvent()
    data class PeerTyping(val conversationId: String) : ChatEvent()
    data class MessageStatus(
        val conversationId: String,
        val messageId: String,
        val peerDelivered: Boolean,
        val peerRead: Boolean
    ) : ChatEvent()
    data object RefreshChats : ChatEvent()
}
