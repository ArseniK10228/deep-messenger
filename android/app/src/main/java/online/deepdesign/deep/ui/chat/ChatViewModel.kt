package online.deepdesign.deep.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import online.deepdesign.deep.DeepApp
import online.deepdesign.deep.data.ChatSocket
import online.deepdesign.deep.data.MessageDto
import online.deepdesign.deep.data.SendMessageRequest

data class ChatUiState(
    val loading: Boolean = true,
    val messages: List<MessageDto> = emptyList(),
    val input: String = "",
    val sending: Boolean = false,
    val error: String? = null,
    val peerTyping: Boolean = false
)

class ChatViewModel(
    private val conversationId: String
) : ViewModel() {
    private val api = DeepApp.instance.api
    private val socket = ChatSocket { DeepApp.instance.currentToken() }
    private var wsJob: Job? = null

    private val _state = MutableStateFlow(ChatUiState())
    val state: StateFlow<ChatUiState> = _state.asStateFlow()

    init {
        loadMessages()
        connectWs()
    }

    private fun loadMessages() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            try {
                val msgs = api.messages(conversationId).messages
                _state.update { it.copy(loading = false, messages = msgs) }
                msgs.lastOrNull()?.id?.let { api.markRead(it) }
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, error = e.message) }
            }
        }
    }

    private fun connectWs() {
        wsJob?.cancel()
        wsJob = viewModelScope.launch {
            socket.events(conversationId).collect { event ->
                when (event.type) {
                    "message" -> event.message?.let { appendMessage(it) }
                    "message_deleted" -> event.messageId?.let { removeMessage(it) }
                    "typing" -> _state.update { it.copy(peerTyping = true) }
                }
            }
        }
    }

    fun onInputChange(v: String) {
        _state.update { it.copy(input = v) }
    }

    fun send() {
        val text = _state.value.input.trim()
        if (text.isEmpty() || _state.value.sending) return
        viewModelScope.launch {
            _state.update { it.copy(sending = true, input = "") }
            try {
                val msg = api.sendMessage(
                    conversationId,
                    SendMessageRequest(kind = "text", body = text)
                ).message
                appendMessage(msg)
            } catch (e: Exception) {
                _state.update { it.copy(sending = false, input = text, error = e.message) }
            } finally {
                _state.update { it.copy(sending = false) }
            }
        }
    }

    private fun appendMessage(msg: MessageDto) {
        _state.update { s ->
            if (s.messages.any { it.id == msg.id }) s
            else s.copy(messages = s.messages + msg)
        }
        viewModelScope.launch { api.markRead(msg.id) }
    }

    private fun removeMessage(id: String) {
        _state.update { s -> s.copy(messages = s.messages.filterNot { it.id == id }) }
    }

    fun isMine(msg: MessageDto): Boolean = msg.senderId == DeepApp.instance.currentUserId

    override fun onCleared() {
        wsJob?.cancel()
        super.onCleared()
    }

    companion object {
        fun factory(conversationId: String): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return ChatViewModel(conversationId) as T
                }
            }
    }
}
