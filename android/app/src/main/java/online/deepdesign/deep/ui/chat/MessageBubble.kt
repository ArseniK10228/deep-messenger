package online.deepdesign.deep.ui.chat

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import online.deepdesign.deep.DeepApp
import online.deepdesign.deep.data.MessageDto
import online.deepdesign.deep.data.resolveMediaUrl
import online.deepdesign.deep.ui.components.VoiceWaveform
import online.deepdesign.deep.ui.components.messageBubbleEnter
import online.deepdesign.deep.ui.theme.DeepAccent
import online.deepdesign.deep.ui.theme.DeepBubbleIn
import online.deepdesign.deep.ui.theme.DeepBubbleOut
import online.deepdesign.deep.ui.theme.DeepMuted
import online.deepdesign.deep.ui.theme.DeepText
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MessageBubble(
    msg: MessageDto,
    mine: Boolean,
    onLongClick: (() -> Unit)? = null
) {
    val bg = if (mine) DeepBubbleOut else DeepBubbleIn
    val align = if (mine) Alignment.CenterEnd else Alignment.CenterStart
    val shape = RoundedCornerShape(
        topStart = 18.dp,
        topEnd = 18.dp,
        bottomStart = if (mine) 18.dp else 4.dp,
        bottomEnd = if (mine) 4.dp else 18.dp
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .messageBubbleEnter(mine),
        contentAlignment = align
    ) {
        val isVoice = msg.kind == "voice"
        Column(
            modifier = Modifier
                .widthIn(min = if (isVoice) 240.dp else 0.dp, max = if (isVoice) 300.dp else 300.dp)
                .clip(shape)
                .background(bg)
                .then(
                    if (onLongClick != null) {
                        Modifier.combinedClickable(
                            onClick = {},
                            onLongClick = onLongClick
                        )
                    } else Modifier
                )
                .padding(horizontal = 10.dp, vertical = 8.dp)
        ) {
            when (msg.kind) {
                "image" -> ImageMessage(msg)
                "voice" -> VoiceMessage(msg)
                "file" -> FileMessage(msg)
                else -> Text(
                    text = msg.body.orEmpty(),
                    color = DeepText,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                )
            }
            Spacer(Modifier.size(4.dp))
            Row(
                modifier = Modifier.align(Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End
            ) {
                Text(
                    text = formatMessageTime(msg.createdAt),
                    color = DeepMuted,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
                if (mine) {
                    Spacer(Modifier.width(4.dp))
                    MessageStatusIcon(msg)
                }
            }
        }
    }
}

@Composable
private fun ImageMessage(msg: MessageDto) {
    val url = resolveMediaUrl(msg.mediaUrl)
    if (url == null) {
        Text("📷 Фото", color = DeepText)
        return
    }
    AsyncImage(
        model = url,
        contentDescription = "Фото",
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 260.dp)
            .clip(RoundedCornerShape(12.dp)),
        contentScale = ContentScale.Crop
    )
}

@Composable
private fun FileMessage(msg: MessageDto) {
    val context = LocalContext.current
    val url = resolveMediaUrl(msg.mediaUrl)
    val name = msg.body?.takeIf { it.isNotBlank() } ?: "Файл"
    val sizeLabel = msg.mediaSize?.let { formatFileSize(it) }

    Row(
        modifier = Modifier
            .clickable(enabled = url != null) {
                url?.let {
                    try {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse(it))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    } catch (_: ActivityNotFoundException) { }
                }
            }
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Default.AttachFile,
            contentDescription = null,
            tint = DeepAccent,
            modifier = Modifier.size(28.dp)
        )
        Spacer(Modifier.width(10.dp))
        Column {
            Text(name, color = DeepText, style = MaterialTheme.typography.bodyLarge)
            sizeLabel?.let {
                Text(it, color = DeepMuted, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun VoiceMessage(msg: MessageDto) {
    val url = resolveMediaUrl(msg.mediaUrl) ?: return
    val durationSec = ((msg.mediaDurationMs ?: 0L) / 1000f).roundToInt().coerceAtLeast(1)
    val player = DeepApp.instance.voicePlayer
    val playState by player.state.collectAsState()
    val isThis = playState.messageId == msg.id
    val playing = isThis && playState.playing
    val progress = if (isThis) playState.progress else 0f
    val elapsedSec = if (isThis && playing) {
        (durationSec * progress).roundToInt().coerceIn(0, durationSec)
    } else {
        0
    }

    Row(
        modifier = Modifier
            .padding(horizontal = 2.dp, vertical = 4.dp)
            .heightIn(min = 44.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(DeepAccent.copy(alpha = 0.22f))
                .clickable { player.toggle(msg.id, url) },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = if (playing) "Пауза" else "Слушать",
                tint = DeepAccent,
                modifier = Modifier.size(26.dp)
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            VoiceWaveform(
                seed = msg.id,
                progress = progress,
                playing = playing,
                activeColor = DeepAccent,
                inactiveColor = DeepMuted.copy(alpha = 0.35f),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = if (playing) formatVoiceTime(elapsedSec) else formatVoiceTime(durationSec),
                color = DeepMuted,
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}

private fun formatVoiceTime(seconds: Int): String {
    val m = seconds / 60
    val s = seconds % 60
    return if (m > 0) "%d:%02d".format(m, s) else "0:%02d".format(s)
}

private fun formatFileSize(bytes: Long): String {
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${(bytes / 1024.0).roundToInt()} KB"
        else -> "${(bytes / (1024.0 * 1024.0) * 10).roundToInt() / 10.0} MB"
    }
}

private fun formatMessageTime(iso: String): String {
    return try {
        val dt = Instant.parse(iso).atZone(ZoneId.systemDefault())
        DateTimeFormatter.ofPattern("HH:mm").format(dt)
    } catch (_: Exception) {
        ""
    }
}

@Composable
private fun MessageStatusIcon(msg: MessageDto) {
    val read = msg.peerRead == true
    val delivered = msg.peerDelivered == true
    val icon = if (delivered || read) Icons.Default.DoneAll else Icons.Default.Done
    val tint = if (read) DeepAccent else DeepMuted
    Icon(
        imageVector = icon,
        contentDescription = when {
            read -> "Прочитано"
            delivered -> "Доставлено"
            else -> "Отправлено"
        },
        tint = tint,
        modifier = Modifier.size(14.dp)
    )
}
