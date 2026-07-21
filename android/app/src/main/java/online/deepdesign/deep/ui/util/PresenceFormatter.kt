package online.deepdesign.deep.ui.util

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object PresenceFormatter {
    private val zone = ZoneId.systemDefault()
    private val timeFmt = DateTimeFormatter.ofPattern("HH:mm", Locale("ru"))
    private val dateFmt = DateTimeFormatter.ofPattern("d MMM", Locale("ru"))

    /** Subtitle on chats list: only when offline — never "в сети", never "давно". */
    fun listLastSeen(online: Boolean, lastSeenAt: String?): String? {
        if (online) return null
        val instant = parseInstant(lastSeenAt) ?: return "был(а) недавно"
        return formatLastSeen(instant)
    }

    /** Status line inside an open chat. */
    fun chatStatus(online: Boolean, lastSeenAt: String?, typing: Boolean = false): String {
        if (typing) return "печатает…"
        if (online) return "в сети"
        val instant = parseInstant(lastSeenAt) ?: return "был(а) недавно"
        val minutes = Duration.between(instant, Instant.now()).toMinutes()
        return when {
            minutes < 1 -> "был(а) только что"
            minutes < 60 -> "был(а) ${minutes} мин. назад"
            else -> formatLastSeen(instant)
        }
    }

    fun isOnlineAccent(online: Boolean, typing: Boolean): Boolean = online || typing

    private fun formatLastSeen(instant: Instant): String {
        val date = instant.atZone(zone).toLocalDate()
        val today = LocalDate.now(zone)
        val time = timeFmt.format(instant.atZone(zone))
        return when {
            date == today -> "был(а) в $time"
            date == today.minusDays(1) -> "был(а) вчера в $time"
            date.year == today.year -> "был(а) ${dateFmt.format(date)} в $time"
            else -> "был(а) ${dateFmt.format(date)} ${date.year} в $time"
        }
    }

    private fun parseInstant(raw: String?): Instant? {
        if (raw.isNullOrBlank()) return null
        return runCatching { Instant.parse(raw) }.getOrNull()
    }
}
