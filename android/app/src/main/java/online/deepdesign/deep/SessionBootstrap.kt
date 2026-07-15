package online.deepdesign.deep

import kotlinx.coroutines.flow.first
import online.deepdesign.deep.data.SessionStore

object SessionBootstrap {
    suspend fun restore(sessionStore: SessionStore, app: DeepApp) {
        val token = sessionStore.tokenFlow.first()
        val userId = sessionStore.userIdFlow.first()
        if (!token.isNullOrBlank()) {
            app.setAuthSession(token, userId)
        }
    }
}
