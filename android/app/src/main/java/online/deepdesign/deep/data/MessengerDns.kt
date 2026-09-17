package online.deepdesign.deep.data

import okhttp3.Dns
import java.net.InetAddress

/**
 * Some VPNs / ISPs still cache the old VPS IP for deepdesignpc.online.
 */
object MessengerDns : Dns {
    private val ourHosts = setOf(
        "api.deepdesignpc.online",
        "deepdesignpc.online",
        "turn.deepdesignpc.online"
    )
    private const val STALE_VPS = "138.124.102.53"
    private const val CURRENT_VPS = "2.56.120.54"

    override fun lookup(hostname: String): List<InetAddress> {
        val system = Dns.SYSTEM.lookup(hostname)
        if (hostname !in ourHosts) return system
        val filtered = system.filterNot { it.hostAddress == STALE_VPS }
        if (filtered.isNotEmpty()) return filtered
        if (system.isNotEmpty()) {
            return listOf(InetAddress.getByName(CURRENT_VPS))
        }
        return system
    }
}
