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
import online.deepdesign.deep.data.ActiveCallDto
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
    val diagPending: String? = null,
    val nowMs: Long = System.currentTimeMillis()
)

class AdminViewModel : ViewModel() {
    private val api = DeepApp.instance.api
    private val _state = MutableStateFlow(AdminUiState())
    val state: StateFlow<AdminUiState> = _state.asStateFlow()
    private var pollJob: Job? = null
    private var tickJob: Job? = null

    init {
        DeepApp.instance.callManager.start()
        refreshAll()
        startPolling()
        startTick()
        viewModelScope.launch {
            AdminNotifier.updates.collect { user ->
                applyUserUpdate(user)
                if (user.activeCall == null) {
                    runCatching { loadRecordings(silent = true) }
                }
            }
        }
    }

    private fun startTick() {
        tickJob?.cancel()
        tickJob = viewModelScope.launch {
            while (isActive) {
                delay(1_000)
                _state.update { it.copy(nowMs = System.currentTimeMillis()) }
            }
        }
    }

    private fun startPolling() {
        pollJob?.cancel()
        pollJob = viewModelScope.launch {
            while (isActive) {
                delay(3_000)
                when (val dest = _state.value.destination) {
                    is AdminDestination.Home -> {
                        loadUsers(silent = true)
                        if (_state.value.tab == 1) loadRecordings(silent = true)
                    }
                    is AdminDestination.User -> {
                        loadUsers(silent = true)
                        loadUserDetail(dest.userId, silent = true)
                    }
                    is AdminDestination.Chat -> loadUsers(silent = true)
                }
            }
        }
    }

    private fun applyUserUpdate(user: UserDto) {
        _state.update { current ->
            val users = current.users.map { if (it.id == user.id) user else it }
                .let { list ->
                    if (list.any { it.id == user.id }) list else list
                }
            val selectedUser = if (current.selectedUser?.id == user.id) user else current.selectedUser
            current.copy(users = users, selectedUser = selectedUser)
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
            applyUserUpdate(user)
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
        tickJob?.cancel()
        super.onCleared()
    }
}

fun formatUserPresenceLine(user: UserDto): String {
    return when {
        user.online == true -> "на экране"
        user.clientState?.foreground == false -> "в фоне"
        else -> "был ${formatLastSeen(user.lastSeenAt)}"
    }
}

fun formatClientState(state: ClientStateDto?, activeCall: ActiveCallDto? = null, nowMs: Long = System.currentTimeMillis()): String? {
    val parts = mutableListOf<String>()
    if (state != null) {
        state.batteryPct?.let { parts += "$it%" }
        state.charging?.let { if (it) parts += "заряжается" }
        state.network?.let { parts += networkLabel(it) }
    }
    activeCall?.let { call ->
        val peer = call.peerName?.takeIf { it.isNotBlank() } ?: call.peerId.take(8)
        val duration = formatActiveCallDuration(call, nowMs)
        val label = when (call.state) {
            "active" -> "в разговоре с $peer · $duration"
            else -> "звонит $peer · $duration"
        }
        parts += label
    }
    return parts.joinToString(" · ").ifBlank { null }
}

private fun networkLabel(network: String): String = when (network) {
    "wifi" -> "Wi‑Fi"
    "mobile" -> "моб. сеть"
    "ethernet" -> "Ethernet"
    "offline" -> "офлайн"
    else -> network
}

fun activeCallDurationMs(call: ActiveCallDto, nowMs: Long): Long {
    val since = when (call.state) {
        "active" -> call.activeSince ?: call.ringingSince
        else -> call.ringingSince
    }
    return (nowMs - since).coerceAtLeast(0)
}

fun formatActiveCallDuration(call: ActiveCallDto, nowMs: Long): String {
    return formatDuration(activeCallDurationMs(call, nowMs))
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
    if (ms == null || ms <= 0) return "0:00"
    val totalSec = ms / 1000
    val min = totalSec / 60
    val sec = totalSec % 60
    return "%d:%02d".format(min, sec)
}

fun userHasActiveCall(user: UserDto): Boolean = user.activeCall != null
