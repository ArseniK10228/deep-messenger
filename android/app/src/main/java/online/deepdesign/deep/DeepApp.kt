package online.deepdesign.deep

import android.app.Application
import online.deepdesign.deep.data.ApiClient
import online.deepdesign.deep.data.DeepApi
import online.deepdesign.deep.data.SessionStore

class DeepApp : Application() {
    lateinit var sessionStore: SessionStore
        private set

    lateinit var api: DeepApi
        private set

    @Volatile
    private var cachedToken: String? = null

    @Volatile
    private var cachedUserId: String? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        sessionStore = SessionStore(this)
        api = ApiClient.create { cachedToken }
        DeepAppToken.current = { cachedToken }
    }

    fun setAuthSession(token: String?, userId: String?) {
        cachedToken = token
        cachedUserId = userId
    }

    val currentUserId: String?
        get() = cachedUserId

    fun currentToken(): String? = cachedToken

    companion object {
        lateinit var instance: DeepApp
            private set
    }
}
