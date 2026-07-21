package online.deepdesign.deep.ui.chats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import online.deepdesign.deep.DeepApp
import online.deepdesign.deep.data.AuthEvents
import online.deepdesign.deep.data.ChatEvent
import online.deepdesign.deep.data.ChatNotifier
import online.deepdesign.deep.data.ConversationDto
import online.deepdesign.deep.data.DirectChatRequest
import online.deepdesign.deep.data.PresenceInfo
import online.deepdesign.deep.data.PresenceStore
import online.deepdesign.deep.data.UpdateProfileRequest
import online.deepdesign.deep.data.UserDto
import online.deepdesign.deep.data.readApiError
import retrofit2.HttpException

data class ChatsUiState(
    val loading: Boolean = true,
    val conversations: List<ConversationDto> = emptyList(),
    val error: String? = null,
    val searchQuery: String = "",
    val searchResults: List<UserDto> = emptyList(),
    val searching: Boolean = false,
    val searchError: String? = null,
    val showNewChat: Boolean = false,
    val showProfile: Boolean = false,
    val profileLoading: Boolean = false,
    val profileSaving: Boolean = false,
    val profileError: String? = null,
    val profileDisplayName: String = "",
    val profileUsername: String = "",
    val currentUser: UserDto? = null,
    val typingConversations: Set<String> = emptySet()
)

class ChatsViewModel : ViewModel() {
    private val api = DeepApp.instance.api
    private val typingJobs = mutableMapOf<String, Job>()

    private val _state = MutableStateFlow(ChatsUiState())
    val state: StateFlow<ChatsUiState> = _state.asStateFlow()

    init {
        refresh()
        loadProfile()
        viewModelScope.launch {
            ChatNotifier.events.collect { event ->
                when (event) {
                    is ChatEvent.NewMessage, ChatEvent.RefreshChats -> refresh()
                    is ChatEvent.PeerTyping -> onPeerTyping(event.conversationId)
                }
            }
        }
        viewModelScope.launch {
            PresenceStore.users.collectLatest { users -> refreshPresenceLabels(users) }
        }
    }

    private fun onPeerTyping(conversationId: String) {
        typingJobs[conversationId]?.cancel()
        _state.update { it.copy(typingConversations = it.typingConversations + conversationId) }
        typingJobs[conversationId] = viewModelScope.launch {
            delay(3_000)
            _state.update { it.copy(typingConversations = it.typingConversations - conversationId) }
            typingJobs.remove(conversationId)
        }
    }

    private fun refreshPresenceLabels(users: Map<String, PresenceInfo>) {
        _state.update { current ->
            current.copy(conversations = current.conversations.map { conv ->
                val peer = conv.peers?.firstOrNull() ?: return@map conv
                val live = users[peer.id] ?: return@map conv
                conv.copy(
                    peers = listOf(
                        peer.copy(online = live.online, lastSeenAt = live.lastSeenAt)
                    )
                )
            })
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            try {
                val list = api.conversations().conversations
                list.forEach { conv ->
                    conv.peers?.firstOrNull()?.let { peer ->
                        PresenceStore.seed(peer.id, peer.online, peer.lastSeenAt)
                    }
                }
                _state.update { it.copy(loading = false, conversations = list) }
            } catch (e: Exception) {
                val msg = when {
                    e is HttpException && e.code() == 401 -> "Сессия истекла — войдите снова"
                    e is HttpException -> e.readApiError()
                    else -> e.message ?: "Ошибка загрузки"
                }
                if (e is HttpException && e.code() == 401) {
                    AuthEvents.notifySessionExpired()
                }
                _state.update { it.copy(loading = false, error = msg) }
            }
        }
    }

    fun loadProfile() {
        viewModelScope.launch {
            _state.update { it.copy(profileLoading = true, profileError = null) }
            try {
                val user = api.me().user
                _state.update {
                    it.copy(
                        profileLoading = false,
                        currentUser = user,
                        profileDisplayName = user.displayName,
                        profileUsername = user.username.orEmpty(),
                        showProfile = it.showProfile || user.username.isNullOrBlank()
                    )
                }
            } catch (e: Exception) {
                _state.update { it.copy(profileLoading = false, profileError = e.message) }
            }
        }
    }

    fun toggleProfile(show: Boolean) {
        val user = _state.value.currentUser
        _state.update {
            it.copy(
                showProfile = show,
                profileError = null,
                profileDisplayName = user?.displayName ?: it.profileDisplayName,
                profileUsername = user?.username.orEmpty()
            )
        }
    }

    fun onProfileDisplayNameChange(v: String) {
        _state.update { it.copy(profileDisplayName = v, profileError = null) }
    }

    fun onProfileUsernameChange(v: String) {
        _state.update {
            it.copy(
                profileUsername = v.lowercase().filter { ch -> ch.isLetterOrDigit() || ch == '_' },
                profileError = null
            )
        }
    }

    fun saveProfile() {
        val s = _state.value
        val username = s.profileUsername.trim().lowercase().removePrefix("@")
        if (username.isNotEmpty() && (username.length < 3 || username.length > 32)) {
            _state.update { it.copy(profileError = "Username: 3–32 символа") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(profileSaving = true, profileError = null) }
            try {
                val user = api.updateProfile(
                    UpdateProfileRequest(
                        displayName = s.profileDisplayName.trim().ifBlank { null },
                        username = username.ifBlank { null }
                    )
                ).user
                _state.update {
                    it.copy(
                        profileSaving = false,
                        currentUser = user,
                        profileDisplayName = user.displayName,
                        profileUsername = user.username.orEmpty(),
                        showProfile = user.username.isNullOrBlank()
                    )
                }
            } catch (e: Exception) {
                val msg = when (e) {
                    is HttpException -> e.readApiError()
                    else -> e.message ?: "Не удалось сохранить"
                }
                _state.update { it.copy(profileSaving = false, profileError = msg) }
            }
        }
    }

    fun toggleNewChat(show: Boolean) {
        _state.update {
            it.copy(
                showNewChat = show,
                searchQuery = "",
                searchResults = emptyList(),
                searchError = null
            )
        }
    }

    fun onSearchQueryChange(q: String) {
        _state.update { it.copy(searchQuery = q, searchError = null) }
        if (q.trim().length >= 2) {
            searchUsers(q.trim())
        } else {
            _state.update { it.copy(searchResults = emptyList(), searching = false) }
        }
    }

    private fun searchUsers(q: String) {
        viewModelScope.launch {
            _state.update { it.copy(searching = true, searchError = null) }
            try {
                val users = api.searchUsers(q).users
                _state.update { it.copy(searching = false, searchResults = users) }
            } catch (e: Exception) {
                val msg = when (e) {
                    is HttpException -> e.readApiError()
                    else -> e.message
                }
                _state.update {
                    it.copy(searching = false, searchResults = emptyList(), searchError = msg)
                }
            }
        }
    }

    fun startChatWithUser(user: UserDto, onReady: (String, String) -> Unit) {
        viewModelScope.launch {
            try {
                val resp = api.createDirect(DirectChatRequest(user.id))
                val title = userTitle(user)
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
        return userTitle(peer)
    }

    fun userTitle(user: UserDto): String {
        return user.displayName.ifBlank { user.username?.let { "@$it" }.orEmpty() }
            .ifBlank { user.email.orEmpty().ifBlank { user.phone } }
    }

    fun userSubtitle(user: UserDto): String {
        user.username?.let { return "@$it" }
        return user.email.orEmpty().ifBlank { user.phone }
    }

    fun peerPresence(conv: ConversationDto): Pair<Boolean, String?> {
        val peer = conv.peers?.firstOrNull() ?: return false to null
        val live = PresenceStore.users.value[peer.id]
        val online = live?.online ?: peer.online == true
        val lastSeen = live?.lastSeenAt ?: peer.lastSeenAt
        return online to lastSeen
    }

    fun isPeerTyping(conversationId: String): Boolean =
        conversationId in _state.value.typingConversations

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
