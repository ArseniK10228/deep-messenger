package online.deepdesign.deep.ui.call

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        color = DeepSurfaceHigh.copy(alpha = 0.96f),
        shape = RoundedCornerShape(16.dp),
        shadowElevation = 12.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onExpand)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            ChatAvatar(name = peerName, size = 40.dp, online = state is CallUiState.Active && state.connected)
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    Icons.Default.Call,
                    contentDescription = null,
                    tint = DeepAccent,
                    modifier = Modifier.size(18.dp)
                )
                androidx.compose.foundation.layout.Column {
                    Text(
                        peerName,
                        color = DeepText,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(status, color = DeepMuted, style = MaterialTheme.typography.labelSmall)
                }
            }
            IconButton(onClick = onToggleMute, modifier = Modifier.size(40.dp)) {
                Icon(
                    if (muted) Icons.Default.MicOff else Icons.Default.Mic,
                    contentDescription = if (muted) "Включить микрофон" else "Выключить микрофон",
                    tint = if (muted) DeepAccent else DeepText
                )
            }
            IconButton(
                onClick = onHangup,
                modifier = Modifier
                    .size(40.dp)
                    .background(DeepError.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
            ) {
                Icon(Icons.Default.CallEnd, contentDescription = "Завершить", tint = DeepError)
            }
        }
    }
}
