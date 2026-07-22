package online.deepdesign.deep.ui.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import online.deepdesign.deep.DeepApp
import online.deepdesign.deep.data.CallRecordingDto
import online.deepdesign.deep.data.ClientStateDto
import online.deepdesign.deep.data.ConversationDto
import online.deepdesign.deep.data.DiagRequest
import online.deepdesign.deep.data.MessageDto
import online.deepdesign.deep.data.UserDto
import java.time.Duration
import java.time.Instant

sealed class AdminDestination {
    data object Home : AdminDestination()
    data class User(val userId: String) : AdminDestination()
    data class Chat(val conversationId: String, val title: String) : AdminDestination()
}

data class AdminUiState(
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val error: String? = null,
    val tab: Int = 0,
    val users: List<UserDto> = emptyList(),
    val selectedUser: UserDto? = null,
    val userConversations: List<ConversationDto> = emptyList(),
    val chatMessages: List<MessageDto> = emptyList(),
    val chatLoading: Boolean = false,
    val recordings: List<CallRecordingDto> = emptyList(),
    val destination: AdminDestination = AdminDestination.Home,
    val diagPending: String? = null
)

class AdminViewModel : ViewModel() {
    private val api = DeepApp.instance.api
    private val _state = MutableStateFlow(AdminUiState())
    val state: StateFlow<AdminUiState> = _state.asStateFlow()
    private var pollJob: Job? = null

    init {
        refreshAll()
        startPolling()
    }

    private fun startPolling() {
        pollJob?.cancel()
        pollJob = viewModelScope.launch {
            while (isActive) {
                delay(12_000)
                when (val dest = _state.value.destination) {
                    is AdminDestination.Home -> loadUsers(silent = true)
                    is AdminDestination.User -> {
                        loadUsers(silent = true)
                        loadUserDetail(dest.userId, silent = true)
                    }
                    is AdminDestination.Chat -> loadUsers(silent = true)
                }
            }
        }
    }

    fun refreshAll() {
        viewModelScope.launch {
            _state.update { it.copy(refreshing = true, error = null) }
            runCatching { loadUsers(silent = true) }
            runCatching { loadRecordings(silent = true) }
            when (val dest = _state.value.destination) {
                is AdminDestination.User -> loadUserDetail(dest.userId, silent = true)
                is AdminDestination.Chat -> loadChat(dest.conversationId, silent = true)
                else -> Unit
            }
            _state.update { it.copy(refreshing = false) }
        }
    }

    fun setTab(tab: Int) {
        _state.update { it.copy(tab = tab) }
        if (tab == 1 && _state.value.recordings.isEmpty()) {
            viewModelScope.launch { loadRecordings() }
        }
    }

    fun openUser(userId: String) {
        _state.update { it.copy(destination = AdminDestination.User(userId), selectedUser = null, userConversations = emptyList()) }
        viewModelScope.launch { loadUserDetail(userId) }
    }

    fun openChat(conversationId: String, title: String) {
        _state.update {
            it.copy(
                destination = AdminDestination.Chat(conversationId, title),
                chatMessages = emptyList(),
                chatLoading = true
            )
        }
        viewModelScope.launch { loadChat(conversationId) }
    }

    fun back() {
        when (val dest = _state.value.destination) {
            is AdminDestination.Chat -> {
                val userId = _state.value.selectedUser?.id
                if (userId != null) {
                    _state.update { it.copy(destination = AdminDestination.User(userId)) }
                } else {
                    _state.update { it.copy(destination = AdminDestination.Home) }
                }
            }
            is AdminDestination.User -> _state.update { it.copy(destination = AdminDestination.Home) }
            else -> Unit
        }
    }

    fun requestDiag(userId: String) {
        viewModelScope.launch {
            _state.update { it.copy(diagPending = userId) }
            runCatching { api.adminDiag(userId, DiagRequest("snapshot")) }
            delay(1500)
            loadUserDetail(userId, silent = true)
            _state.update { it.copy(diagPending = null) }
        }
    }

    private suspend fun loadUsers(silent: Boolean = false) {
        if (!silent) _state.update { it.copy(loading = true, error = null) }
        try {
            val users = api.adminUsers().users
            _state.update { it.copy(loading = false, users = users) }
        } catch (e: Exception) {
            _state.update { it.copy(loading = false, error = e.message) }
        }
    }

    private suspend fun loadUserDetail(userId: String, silent: Boolean = false) {
        if (!silent) _state.update { it.copy(loading = true, error = null) }
        try {
            val user = api.adminUser(userId).user
            val conversations = api.adminUserConversations(userId).conversations
            _state.update {
                it.copy(loading = false, selectedUser = user, userConversations = conversations)
            }
        } catch (e: Exception) {
            _state.update { it.copy(loading = false, error = e.message) }
        }
    }

    private suspend fun loadChat(conversationId: String, silent: Boolean = false) {
        if (!silent) _state.update { it.copy(chatLoading = true) }
        try {
            val messages = api.adminConversationMessages(conversationId, 300).messages
            _state.update { it.copy(chatLoading = false, chatMessages = messages) }
        } catch (e: Exception) {
            _state.update { it.copy(chatLoading = false, error = e.message) }
        }
    }

    private suspend fun loadRecordings(silent: Boolean = false) {
        if (!silent) _state.update { it.copy(loading = true) }
        try {
            val recordings = api.adminCallRecordings().recordings
            _state.update { it.copy(loading = false, recordings = recordings) }
        } catch (e: Exception) {
            if (!silent) _state.update { it.copy(loading = false, error = e.message) }
        }
    }

    override fun onCleared() {
        pollJob?.cancel()
        super.onCleared()
    }
}

fun formatClientState(state: ClientStateDto?): String? {
    state ?: return null
    val parts = mutableListOf<String>()
    parts += if (state.foreground == true) "на экране" else "в фоне"
    state.batteryPct?.let { parts += "$it%" }
    state.network?.let { parts += it }
    if (state.inCall == true) parts += "в звонке"
    return parts.joinToString(" · ")
}

fun formatLastSeen(lastSeenAt: String?): String {
    if (lastSeenAt.isNullOrBlank()) return "давно"
    return runCatching {
        val instant = Instant.parse(lastSeenAt)
        val mins = Duration.between(instant, Instant.now()).toMinutes()
        when {
            mins < 1 -> "только что"
            mins < 60 -> "$mins мин назад"
            mins < 1440 -> "${mins / 60} ч назад"
            else -> "${mins / 1440} дн назад"
        }
    }.getOrDefault(lastSeenAt)
}

fun userLabel(user: UserDto): String {
    return user.displayName.ifBlank {
        user.username?.let { "@$it" }.orEmpty()
    }.ifBlank { user.id.take(8) }
}

fun formatDuration(ms: Long?): String {
    if (ms == null || ms <= 0) return "—"
    val totalSec = ms / 1000
    val min = totalSec / 60
    val sec = totalSec % 60
    return "%d:%02d".format(min, sec)
}
