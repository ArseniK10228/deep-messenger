package online.deepdesign.deep.ui.chats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import online.deepdesign.deep.DeepApp
import online.deepdesign.deep.data.ConversationDto
import online.deepdesign.deep.data.DirectChatRequest
import online.deepdesign.deep.data.UserDto

data class ChatsUiState(
    val loading: Boolean = true,
    val conversations: List<ConversationDto> = emptyList(),
    val error: String? = null,
    val searchQuery: String = "",
    val searchResults: List<UserDto> = emptyList(),
    val searching: Boolean = false,
    val showNewChat: Boolean = false
)

class ChatsViewModel : ViewModel() {
    private val api = DeepApp.instance.api

    private val _state = MutableStateFlow(ChatsUiState())
    val state: StateFlow<ChatsUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            try {
                val list = api.conversations().conversations
                _state.update { it.copy(loading = false, conversations = list) }
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, error = e.message ?: "Ошибка загрузки") }
            }
        }
    }

    fun toggleNewChat(show: Boolean) {
        _state.update { it.copy(showNewChat = show, searchQuery = "", searchResults = emptyList()) }
    }

    fun onSearchQueryChange(q: String) {
        _state.update { it.copy(searchQuery = q) }
        if (q.filter { it.isDigit() }.length >= 3) {
            searchUsers(q)
        } else {
            _state.update { it.copy(searchResults = emptyList()) }
        }
    }

    private fun searchUsers(q: String) {
        viewModelScope.launch {
            _state.update { it.copy(searching = true) }
            try {
                val users = api.searchUsers(q).users
                _state.update { it.copy(searching = false, searchResults = users) }
            } catch (e: Exception) {
                _state.update { it.copy(searching = false, searchResults = emptyList()) }
            }
        }
    }

    fun startChatWithUser(user: UserDto, onReady: (String, String) -> Unit) {
        viewModelScope.launch {
            try {
                val resp = api.createDirect(DirectChatRequest(user.id))
                val title = user.displayName.ifBlank { user.phone }
                toggleNewChat(false)
                refresh()
                onReady(resp.conversationId, title)
            } catch (e: Exception) {
                _state.update { it.copy(error = e.message ?: "Не удалось создать чат") }
            }
        }
    }

    fun peerTitle(conv: ConversationDto): String {
        val peer = conv.peers?.firstOrNull() ?: return "Чат"
        return peer.displayName.ifBlank { peer.phone }
    }

    fun previewText(conv: ConversationDto): String {
        val m = conv.lastMessage ?: return "Нет сообщений"
        return when (m.kind) {
            "text" -> m.body.orEmpty()
            "image" -> "Фото"
            "voice" -> "Голосовое"
            else -> "Файл"
        }
    }
}
