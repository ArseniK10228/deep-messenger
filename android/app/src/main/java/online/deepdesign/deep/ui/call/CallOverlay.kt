package online.deepdesign.deep.ui.call

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import online.deepdesign.deep.call.CallUiState
import online.deepdesign.deep.ui.components.ChatAvatar
import online.deepdesign.deep.ui.components.deepAppear
import online.deepdesign.deep.ui.theme.DeepAccent
import online.deepdesign.deep.ui.theme.DeepBg
import online.deepdesign.deep.ui.theme.DeepError
import online.deepdesign.deep.ui.theme.DeepMuted
import online.deepdesign.deep.ui.theme.DeepSurfaceHigh
import online.deepdesign.deep.ui.theme.DeepText

@Composable
fun CallOverlay(
    state: CallUiState,
    muted: Boolean,
    speakerOn: Boolean,
    onAccept: () -> Unit,
    onReject: () -> Unit,
    onHangup: () -> Unit,
    onToggleMute: () -> Unit,
    onToggleSpeaker: () -> Unit
) {
    when (state) {
        is CallUiState.Incoming -> IncomingCallUi(state.callerName, onAccept, onReject)
        is CallUiState.Outgoing -> OngoingCallUi(
            peerName = state.peerName,
            status = "Вызов…",
            connected = false,
            muted = muted,
            speakerOn = speakerOn,
            onToggleMute = onToggleMute,
            onToggleSpeaker = onToggleSpeaker,
            onHangup = onHangup
        )
        is CallUiState.Active -> OngoingCallUi(
            peerName = state.peerName,
            status = if (state.connected) "На линии" else "Соединяем…",
            connected = state.connected,
            muted = muted,
            speakerOn = speakerOn,
            onToggleMute = onToggleMute,
            onToggleSpeaker = onToggleSpeaker,
            onHangup = onHangup
        )
        CallUiState.Idle -> Unit
    }
}

@Composable
private fun CallBackground(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF1A1030),
                        DeepBg,
                        Color(0xFF0A0A12)
                    )
                )
            )
    ) {
        content()
    }
}

@Composable
private fun IncomingCallUi(callerName: String, onAccept: () -> Unit, onReject: () -> Unit) {
    val pulse by rememberInfiniteTransition(label = "ring").animateFloat(
        initialValue = 1f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Reverse),
        label = "ringPulse"
    )

    CallBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 28.dp, vertical = 48.dp)
                .deepAppear(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.weight(0.35f))
            Text("Входящий звонок", color = DeepMuted, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(28.dp))
            Box(Modifier.scale(pulse)) {
                ChatAvatar(name = callerName, size = 120.dp)
            }
            Spacer(Modifier.height(20.dp))
            Text(
                callerName,
                color = DeepText,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text("Deep Messenger", color = DeepMuted, style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.weight(1f))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                CallActionButton(
                    icon = Icons.Default.CallEnd,
                    label = "Отклонить",
                    containerColor = DeepError,
                    onClick = onReject
                )
                CallActionButton(
                    icon = Icons.Default.Call,
                    label = "Принять",
                    containerColor = DeepAccent,
                    onClick = onAccept
                )
            }
        }
    }
}

@Composable
private fun OngoingCallUi(
    peerName: String,
    status: String,
    connected: Boolean,
    muted: Boolean,
    speakerOn: Boolean,
    onToggleMute: () -> Unit,
    onToggleSpeaker: () -> Unit,
    onHangup: () -> Unit
) {
    val pulse by rememberInfiniteTransition(label = "callPulse").animateFloat(
        initialValue = 1f,
        targetValue = if (connected) 1f else 1.05f,
        animationSpec = infiniteRepeatable(tween(1200, easing = LinearEasing), RepeatMode.Reverse),
        label = "avatarPulse"
    )

    CallBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.weight(0.3f))
            Box(Modifier.scale(pulse)) {
                ChatAvatar(name = peerName, size = 128.dp, online = connected)
            }
            Spacer(Modifier.height(24.dp))
            Text(
                peerName,
                color = DeepText,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(status, color = if (connected) DeepAccent else DeepMuted, style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.weight(1f))

            Surface(
                color = DeepSurfaceHigh.copy(alpha = 0.65f),
                shape = CircleShape,
                modifier = Modifier.padding(bottom = 28.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CallControlChip(
                        icon = if (muted) Icons.Default.MicOff else Icons.Default.Mic,
                        label = if (muted) "Вкл. звук" else "Микрофон",
                        active = muted,
                        onClick = onToggleMute
                    )
                    CallControlChip(
                        icon = if (speakerOn) Icons.AutoMirrored.Filled.VolumeUp else Icons.Default.VolumeOff,
                        label = "Динамик",
                        active = speakerOn,
                        onClick = onToggleSpeaker
                    )
                }
            }

            FloatingActionButton(
                onClick = onHangup,
                containerColor = DeepError,
                contentColor = DeepText,
                shape = CircleShape,
                modifier = Modifier.size(76.dp)
            ) {
                Icon(Icons.Default.CallEnd, contentDescription = "Завершить", modifier = Modifier.size(34.dp))
            }
            Spacer(Modifier.height(16.dp))
            Text("Завершить", color = DeepMuted, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun CallActionButton(
    icon: ImageVector,
    label: String,
    containerColor: Color,
    onClick: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        FloatingActionButton(
            onClick = onClick,
            containerColor = containerColor,
            contentColor = DeepText,
            shape = CircleShape,
            modifier = Modifier.size(68.dp)
        ) {
            Icon(icon, contentDescription = label, modifier = Modifier.size(30.dp))
        }
        Spacer(Modifier.height(10.dp))
        Text(label, color = DeepMuted, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun CallControlChip(
    icon: ImageVector,
    label: String,
    active: Boolean,
    onClick: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(
            onClick = onClick,
            modifier = Modifier
                .size(56.dp)
                .background(
                    if (active) DeepAccent.copy(alpha = 0.35f) else Color.Transparent,
                    CircleShape
                )
        ) {
            Icon(
                icon,
                contentDescription = label,
                tint = if (active) DeepAccent else DeepText,
                modifier = Modifier.size(26.dp)
            )
        }
        Text(label, color = DeepMuted, style = MaterialTheme.typography.labelSmall)
    }
}
