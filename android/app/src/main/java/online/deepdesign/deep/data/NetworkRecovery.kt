package online.deepdesign.deep.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import online.deepdesign.deep.DeepApp
import online.deepdesign.deep.call.SignalingHub

/** Debounced WS + HTTP reset when default route changes (VPN on/off, Wi‑Fi ↔ mobile). */
object NetworkRecovery {
    private var recoverJob: Job? = null

    fun schedule(
        scope: CoroutineScope,
        signaling: SignalingHub,
        tokenProvider: () -> String?,
        debounceMs: Long = 1_800L
    ) {
        if (tokenProvider().isNullOrBlank()) return
        recoverJob?.cancel()
        recoverJob = scope.launch {
            delay(debounceMs)
            if (tokenProvider().isNullOrBlank()) return@launch
            ApiClient.evictConnections()
            signaling.setUrgentReconnect(true)
            signaling.forceReconnect()
            ChatNotifier.emit(ChatEvent.NetworkRouteChanged)
            runCatching { DeepApp.instance.callManager.onNetworkRouteChanged() }
        }
    }

    fun cancel() {
        recoverJob?.cancel()
        recoverJob = null
    }
}
