package online.deepdesign.deep.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.sessionDataStore by preferencesDataStore("deep_session")

class SessionStore(private val context: Context) {
    private val tokenKey = stringPreferencesKey("jwt")
    private val userIdKey = stringPreferencesKey("user_id")
    private val userNameKey = stringPreferencesKey("user_name")
    private val canViewPresenceKey = booleanPreferencesKey("can_view_presence")

    val tokenFlow: Flow<String?> = context.sessionDataStore.data.map { it[tokenKey] }
    val userIdFlow: Flow<String?> = context.sessionDataStore.data.map { it[userIdKey] }
    val canViewPresenceFlow: Flow<Boolean> = context.sessionDataStore.data.map {
        it[canViewPresenceKey] == true
    }

    suspend fun saveSession(token: String, user: UserDto) {
        OperatorAccess.update(user.canViewPresence)
        context.sessionDataStore.edit {
            it[tokenKey] = token
            it[userIdKey] = user.id
            it[userNameKey] = user.displayName
            it[canViewPresenceKey] = user.canViewPresence == true
        }
    }

    suspend fun restoreOperatorAccess() {
        val canView = canViewPresenceFlow.first()
        OperatorAccess.update(canView)
    }

    suspend fun clear() {
        OperatorAccess.update(false)
        context.sessionDataStore.edit { it.clear() }
    }
}
