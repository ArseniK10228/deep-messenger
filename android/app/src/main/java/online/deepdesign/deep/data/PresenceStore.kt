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
    private val _users = MutableStateFlow<Map<String, PresenceInfo>>(emptyMap())
    val users: StateFlow<Map<String, PresenceInfo>> = _users.asStateFlow()

    fun applySnapshot(entries: List<PresenceSnapshotEntry>) {
        if (entries.isEmpty()) return
        _users.update { current ->
            val next = current.toMutableMap()
            entries.forEach { entry ->
                next[entry.userId] = PresenceInfo(entry.online, entry.lastSeenAt)
            }
            next
        }
    }

    fun update(userId: String, online: Boolean, lastSeenAt: String?) {
        _users.update { current ->
            current + (userId to PresenceInfo(online, lastSeenAt))
        }
    }

    fun seed(userId: String, online: Boolean?, lastSeenAt: String?) {
        _users.update { current ->
            if (current.containsKey(userId)) current
            else current + (userId to PresenceInfo(online == true, lastSeenAt))
        }
    }
}

data class PresenceSnapshotEntry(
    val userId: String,
    val online: Boolean,
    val lastSeenAt: String? = null
)
