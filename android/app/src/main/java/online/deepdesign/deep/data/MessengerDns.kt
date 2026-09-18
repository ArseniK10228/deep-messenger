package online.deepdesign.deep.data

import okhttp3.Dns
import java.net.InetAddress

/**
 * Drop stale VPS IP from DNS answers. Otherwise trust system DNS (works with VPN split/full tunnel).
 */
object MessengerDns : Dns {
    private val ourHosts = setOf(
        "api.deepdesignpc.online",
        "deepdesignpc.online",
        "turn.deepdesignpc.online"
    )
    private const val STALE_VPS = "138.124.102.53"

    override fun lookup(hostname: String): List<InetAddress> {
        val system = resolveSystem(hostname)
        if (hostname !in ourHosts) return system
        val filtered = system.filterNot { it.hostAddress == STALE_VPS }
        if (filtered.isNotEmpty()) return filtered
        return resolveSystem(hostname).filterNot { it.hostAddress == STALE_VPS }
    }

    private fun resolveSystem(hostname: String): List<InetAddress> {
        return runCatching { Dns.SYSTEM.lookup(hostname) }.getOrDefault(emptyList())
    }
}
