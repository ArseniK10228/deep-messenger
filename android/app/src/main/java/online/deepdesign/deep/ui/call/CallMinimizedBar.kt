package online.deepdesign.deep.ui.call

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import online.deepdesign.deep.call.CallUiState
import online.deepdesign.deep.ui.components.ChatAvatar
import online.deepdesign.deep.ui.theme.DeepAccent
import online.deepdesign.deep.ui.theme.DeepError
import online.deepdesign.deep.ui.theme.DeepMuted
import online.deepdesign.deep.ui.theme.DeepSurfaceHigh
import online.deepdesign.deep.ui.theme.DeepText

@Composable
fun CallMinimizedBar(
    state: CallUiState,
    muted: Boolean,
    peerMuted: Boolean = false,
    onExpand: () -> Unit,
    onToggleMute: () -> Unit,
    onHangup: () -> Unit,
    modifier: Modifier = Modifier
) {
    val (peerName, status) = when (state) {
        is CallUiState.Outgoing -> state.peerName to "Вызов…"
        is CallUiState.Active -> state.peerName to if (state.connected) "На линии" else "Соединяем…"
        else -> return
    }

    val online = (state as? CallUiState.Active)?.connected == true
    val showPeerMuted = peerMuted && online

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        color = DeepAccent.copy(alpha = 0.92f),
        shape = RoundedCornerShape(bottomStart = 14.dp, bottomEnd = 14.dp),
        shadowElevation = 8.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onExpand
                    ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ChatAvatar(name = peerName, size = 40.dp, online = online)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Default.Call,
                        contentDescription = null,
                        tint = androidx.compose.ui.graphics.Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                peerName,
                                color = androidx.compose.ui.graphics.Color.White,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            if (showPeerMuted) {
                                Icon(
                                    Icons.Default.MicOff,
                                    contentDescription = "Собеседник без микрофона",
                                    tint = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.9f),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                        Text(status, color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            IconButton(onClick = onToggleMute, modifier = Modifier.size(44.dp)) {
                Icon(
                    if (muted) Icons.Default.MicOff else Icons.Default.Mic,
                    contentDescription = if (muted) "Включить микрофон" else "Выключить микрофон",
                    tint = androidx.compose.ui.graphics.Color.White
                )
            }
            IconButton(
                onClick = onHangup,
                modifier = Modifier
                    .size(44.dp)
                    .background(DeepError.copy(alpha = 0.85f), RoundedCornerShape(12.dp))
            ) {
                Icon(Icons.Default.CallEnd, contentDescription = "Завершить", tint = androidx.compose.ui.graphics.Color.White)
            }
        }
    }
}
