package online.deepdesign.deep.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import online.deepdesign.deep.call.SignalingHub

/** Reconnect signaling + chat when VPN routing settles (debounced). */
class AppNetworkMonitor(
    context: Context,
    private val signaling: SignalingHub,
    private val tokenProvider: () -> String?
) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var callback: ConnectivityManager.NetworkCallback? = null

    fun start() {
        if (callback != null) return
        val cm = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val cb = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                NetworkRecovery.schedule(scope, signaling, tokenProvider)
            }

            override fun onLost(network: Network) {
                NetworkRecovery.schedule(scope, signaling, tokenProvider, debounceMs = 2_200L)
            }

            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                val hasInternet = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                val validated = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                if (hasInternet && validated) {
                    NetworkRecovery.schedule(scope, signaling, tokenProvider)
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
        NetworkRecovery.cancel()
        val cm = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        callback?.let { runCatching { cm.unregisterNetworkCallback(it) } }
        callback = null
    }
}
