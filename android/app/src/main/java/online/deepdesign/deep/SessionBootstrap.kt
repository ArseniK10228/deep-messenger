package online.deepdesign.deep

import kotlinx.coroutines.flow.first
import online.deepdesign.deep.data.SessionStore

object SessionBootstrap {
    suspend fun restore(sessionStore: SessionStore, app: DeepApp) {
        val token = sessionStore.tokenFlow.first()
        val userId = sessionStore.userIdFlow.first()
        if (token.isNullOrBlank()) return
        app.setAuthSession(token, userId)
        val refreshed = runCatching { app.api.me() }
            .recoverCatching {
                val resp = app.api.refreshToken()
                sessionStore.saveSession(resp.token, resp.user)
                app.setAuthSession(resp.token, resp.user.id)
                resp
            }
        if (refreshed.isFailure) {
            // Keep cached session on transient network errors.
        }
    }
}
