package online.deepdesign.deep

import kotlinx.coroutines.flow.first
import online.deepdesign.deep.data.SessionStore

object SessionBootstrap {
    /** Restore JWT into memory. No network — safe for FCM/call wakeups. */
    suspend fun restore(sessionStore: SessionStore, app: DeepApp) {
        val token = sessionStore.tokenFlow.first()
        val userId = sessionStore.userIdFlow.first()
        if (token.isNullOrBlank()) return
        app.setAuthSession(token, userId)
    }
}
