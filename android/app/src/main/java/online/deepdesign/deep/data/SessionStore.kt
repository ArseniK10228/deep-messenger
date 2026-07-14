package online.deepdesign.deep.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.sessionDataStore by preferencesDataStore("deep_session")

class SessionStore(private val context: Context) {
    private val tokenKey = stringPreferencesKey("jwt")
    private val userIdKey = stringPreferencesKey("user_id")
    private val userNameKey = stringPreferencesKey("user_name")

    val tokenFlow: Flow<String?> = context.sessionDataStore.data.map { it[tokenKey] }
    val userIdFlow: Flow<String?> = context.sessionDataStore.data.map { it[userIdKey] }

    suspend fun saveSession(token: String, user: UserDto) {
        context.sessionDataStore.edit {
            it[tokenKey] = token
            it[userIdKey] = user.id
            it[userNameKey] = user.displayName
        }
    }

    suspend fun clear() {
        context.sessionDataStore.edit { it.clear() }
    }
}
