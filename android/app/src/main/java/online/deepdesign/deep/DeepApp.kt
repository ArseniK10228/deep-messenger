package online.deepdesign.deep

import android.app.Application
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import online.deepdesign.deep.call.CallManager
import online.deepdesign.deep.call.SignalingHub
import online.deepdesign.deep.data.ApiClient
import online.deepdesign.deep.data.DeepApi
import online.deepdesign.deep.data.ChatDraftStore
import online.deepdesign.deep.data.SessionStore
import online.deepdesign.deep.data.DeepAppToken
import online.deepdesign.deep.data.VoicePlayer
import online.deepdesign.deep.push.FcmRegistrar

class DeepApp : Application() {
    lateinit var sessionStore: SessionStore
        private set

    lateinit var chatDraftStore: ChatDraftStore
        private set

    lateinit var api: DeepApi
        private set

    lateinit var callManager: CallManager
        private set

    lateinit var signalingHub: SignalingHub
        private set

    lateinit var voicePlayer: VoicePlayer
        private set

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    @Volatile
    private var cachedToken: String? = null

    @Volatile
    private var cachedUserId: String? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        sessionStore = SessionStore(this)
        chatDraftStore = ChatDraftStore(this)
        api = ApiClient.create { cachedToken }
        DeepAppToken.current = { cachedToken }
        val signaling = SignalingHub { cachedToken }
        signalingHub = signaling
        callManager = CallManager(this, signaling)
        voicePlayer = VoicePlayer()

        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                onForeground()
            }

            override fun onStop(owner: LifecycleOwner) {
                onBackground()
            }
        })
    }

    fun cacheSession(token: String, userId: String?) {
        cachedToken = token
        cachedUserId = userId
    }

    private fun onForeground() {
        AppForegroundState.setForeground(true)
        callManager.onAppForegrounded()
        if (!cachedToken.isNullOrBlank()) {
            callManager.start()
        }
    }

    private fun onBackground() {
        AppForegroundState.setForeground(false)
        callManager.onAppBackgrounded()
        if (!callManager.isInCall()) {
            callManager.stop()
        }
    }

    fun setAuthSession(token: String?, userId: String?) {
        cachedToken = token
        cachedUserId = userId
        if (token.isNullOrBlank()) {
            callManager.stop()
        } else {
            callManager.start()
            appScope.launch {
                runCatching { FcmRegistrar.register(api) }
            }
        }
    }

    val currentUserId: String?
        get() = cachedUserId

    fun currentToken(): String? = cachedToken

    fun saveChatDraft(conversationId: String, text: String) {
        appScope.launch {
            runCatching { chatDraftStore.saveDraft(conversationId, text) }
        }
    }

    companion object {
        lateinit var instance: DeepApp
            private set
    }
}
