package online.deepdesign.deep.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import online.deepdesign.deep.call.SignalingHub

/** Reconnect signaling when VPN routing settles (debounced). */
class AppNetworkMonitor(
    context: Context,
    private val signaling: SignalingHub,
    private val tokenProvider: () -> String?
) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var callback: ConnectivityManager.NetworkCallback? = null
    private var recoverJob: Job? = null

    fun start() {
        if (callback != null) return
        val cm = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val cb = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                scheduleRecover()
            }

            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                if (caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                    caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                ) {
                    scheduleRecover()
                }
            }
        }
        callback = cb
        runCatching {
            cm.registerNetworkCallback(
                NetworkRequest.Builder()
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    .build(),
                cb
            )
        }
    }

    fun stop() {
        recoverJob?.cancel()
        val cm = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        callback?.let { runCatching { cm.unregisterNetworkCallback(it) } }
        callback = null
    }

    private fun scheduleRecover() {
        if (tokenProvider().isNullOrBlank()) return
        recoverJob?.cancel()
        recoverJob = scope.launch {
            delay(1_500L)
            if (!signaling.isConnected()) {
                signaling.setUrgentReconnect(true)
                signaling.forceReconnect()
            }
        }
    }
}
