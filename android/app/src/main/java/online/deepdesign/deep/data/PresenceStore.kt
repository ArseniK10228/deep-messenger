package online.deepdesign.deep.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class PresenceInfo(
    val online: Boolean = false,
    val lastSeenAt: String? = null
)

object PresenceStore {
    private val _signalingLive = MutableStateFlow(false)
    val signalingLive: StateFlow<Boolean> = _signalingLive.asStateFlow()

    private val _users = MutableStateFlow<Map<String, PresenceInfo>>(emptyMap())
    val users: StateFlow<Map<String, PresenceInfo>> = _users.asStateFlow()

    fun applySnapshot(entries: List<PresenceSnapshotEntry>) {
        if (entries.isEmpty()) return
        _signalingLive.value = true
        _users.update { current ->
            val next = current.toMutableMap()
            entries.forEach { entry ->
                next[entry.userId] = PresenceInfo(entry.online, entry.lastSeenAt)
            }
            next
        }
    }

    fun update(userId: String, online: Boolean, lastSeenAt: String?) {
        if (!_signalingLive.value) return
        _users.update { current ->
            current + (userId to PresenceInfo(online, lastSeenAt))
        }
    }

    /** Authoritative when WS is down; also refreshes cache from REST. */
    fun setFromApi(userId: String, online: Boolean?, lastSeenAt: String?) {
        _users.update { current ->
            current + (userId to PresenceInfo(online == true, lastSeenAt))
        }
    }

    fun onSignalingDisconnected() {
        _signalingLive.value = false
        _users.update { map -> map.mapValues { (_, info) -> info.copy(online = false) } }
    }

    fun clear() {
        _signalingLive.value = false
        _users.value = emptyMap()
    }

    fun peerOnline(
        userId: String,
        apiOnline: Boolean?,
        apiLastSeen: String?
    ): Pair<Boolean, String?> {
        if (!_signalingLive.value) {
            return (apiOnline == true) to apiLastSeen
        }
        val live = _users.value[userId]
        return (live?.online == true) to (live?.lastSeenAt ?: apiLastSeen)
    }
}

data class PresenceSnapshotEntry(
    val userId: String,
    val online: Boolean,
    val lastSeenAt: String? = null
)
