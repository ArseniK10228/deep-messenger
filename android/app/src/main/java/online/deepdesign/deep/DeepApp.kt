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

    override fun onCreate() {
        super.onCreate()
        instance = this
        sessionStore = SessionStore(this)
        api = ApiClient.create { cachedToken }
    }

    fun setAuthToken(token: String?) {
        cachedToken = token
    }

    companion object {
        lateinit var instance: DeepApp
            private set
    }
}
