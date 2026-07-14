package online.deepdesign.deep.ui.chat

import android.content.Intent
import android.media.MediaPlayer
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import online.deepdesign.deep.data.MessageDto
import online.deepdesign.deep.data.resolveMediaUrl
import online.deepdesign.deep.ui.components.deepAppear
import online.deepdesign.deep.ui.theme.DeepAccent
import online.deepdesign.deep.ui.theme.DeepBubbleIn
import online.deepdesign.deep.ui.theme.DeepBubbleOut
import online.deepdesign.deep.ui.theme.DeepMuted
import online.deepdesign.deep.ui.theme.DeepText
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

@Composable
fun MessageBubble(msg: MessageDto, mine: Boolean) {
    val bg = if (mine) DeepBubbleOut else DeepBubbleIn
    val align = if (mine) Alignment.CenterEnd else Alignment.CenterStart
    val shape = RoundedCornerShape(
        topStart = 18.dp,
        topEnd = 18.dp,
        bottomStart = if (mine) 18.dp else 4.dp,
        bottomEnd = if (mine) 4.dp else 18.dp
    )

    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = align) {
        Column(
            modifier = Modifier
                .widthIn(max = 300.dp)
                .clip(shape)
                .background(bg)
                .padding(horizontal = 10.dp, vertical = 8.dp)
                .deepAppear(durationMillis = 260)
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
            Text(
                text = formatMessageTime(msg.createdAt),
                color = DeepMuted,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier
                    .align(Alignment.End)
                    .padding(horizontal = 4.dp)
            )
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
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse(it))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
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
    var playing by remember(msg.id) { mutableStateOf(false) }
    val player = remember(msg.id) { MediaPlayer() }

    DisposableEffect(msg.id) {
        onDispose {
            if (player.isPlaying) player.stop()
            player.release()
        }
    }

    Row(
        modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = {
                if (playing) {
                    player.pause()
                    playing = false
                } else {
                    try {
                        if (!player.isPlaying) {
                            player.reset()
                            player.setDataSource(url)
                            player.prepare()
                            player.start()
                            player.setOnCompletionListener {
                                playing = false
                            }
                        }
                        playing = true
                    } catch (_: Exception) {
                        playing = false
                    }
                }
            },
            modifier = Modifier.size(36.dp)
        ) {
            Icon(
                imageVector = if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = if (playing) "Пауза" else "Слушать",
                tint = DeepAccent
            )
        }
        Text(
            text = "${durationSec}с",
            color = DeepText,
            style = MaterialTheme.typography.bodyMedium
        )
    }
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
