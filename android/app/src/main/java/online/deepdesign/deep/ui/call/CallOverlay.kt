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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideocamOff
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.CircleShape
import online.deepdesign.deep.call.CallUiState
import org.webrtc.VideoTrack
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
    videoOn: Boolean,
    localVideo: VideoTrack?,
    remoteVideo: VideoTrack?,
    onAccept: () -> Unit,
    onReject: () -> Unit,
    onHangup: () -> Unit,
    onToggleMute: () -> Unit,
    onToggleSpeaker: () -> Unit,
    onToggleVideo: () -> Unit,
    onSwitchCamera: () -> Unit,
    onMinimize: () -> Unit
) {
    when (state) {
        is CallUiState.Incoming -> IncomingCallUi(state.callerName, state.video, onAccept, onReject)
        is CallUiState.Outgoing -> if (state.video) {
            VideoCallUi(
                peerName = state.peerName,
                status = "Вызов…",
                connected = false,
                muted = muted,
                videoOn = videoOn,
                localVideo = localVideo,
                remoteVideo = remoteVideo,
                onToggleMute = onToggleMute,
                onToggleVideo = onToggleVideo,
                onSwitchCamera = onSwitchCamera,
                onHangup = onHangup,
                onMinimize = onMinimize
            )
        } else {
            OngoingCallUi(
                peerName = state.peerName,
                status = "Вызов…",
                connected = false,
                muted = muted,
                speakerOn = speakerOn,
                onToggleMute = onToggleMute,
                onToggleSpeaker = onToggleSpeaker,
                onHangup = onHangup,
                onMinimize = onMinimize
            )
        }
        is CallUiState.Active -> if (state.video) {
            VideoCallUi(
                peerName = state.peerName,
                status = if (state.connected) "На линии" else "Соединяем…",
                connected = state.connected,
                muted = muted,
                videoOn = videoOn,
                localVideo = localVideo,
                remoteVideo = remoteVideo,
                onToggleMute = onToggleMute,
                onToggleVideo = onToggleVideo,
                onSwitchCamera = onSwitchCamera,
                onHangup = onHangup,
                onMinimize = onMinimize
            )
        } else {
            OngoingCallUi(
                peerName = state.peerName,
                status = if (state.connected) "На линии" else "Соединяем…",
                connected = state.connected,
                muted = muted,
                speakerOn = speakerOn,
                onToggleMute = onToggleMute,
                onToggleSpeaker = onToggleSpeaker,
                onHangup = onHangup,
                onMinimize = onMinimize
            )
        }
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
private fun IncomingCallUi(
    callerName: String,
    video: Boolean,
    onAccept: () -> Unit,
    onReject: () -> Unit
) {
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
            Text(
                if (video) "Входящий видеозвонок" else "Входящий звонок",
                color = DeepMuted,
                style = MaterialTheme.typography.titleMedium
            )
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
    onHangup: () -> Unit,
    onMinimize: () -> Unit
) {
    val pulse by rememberInfiniteTransition(label = "callPulse").animateFloat(
        initialValue = 1f,
        targetValue = if (connected) 1f else 1.05f,
        animationSpec = infiniteRepeatable(tween(1200, easing = LinearEasing), RepeatMode.Reverse),
        label = "avatarPulse"
    )

    CallBackground {
        Box(Modifier.fillMaxSize()) {
            CallMinimizeButton(
                onClick = onMinimize,
                modifier = Modifier.align(Alignment.TopStart)
            )
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
}

@Composable
private fun VideoCallUi(
    peerName: String,
    status: String,
    connected: Boolean,
    muted: Boolean,
    videoOn: Boolean,
    localVideo: VideoTrack?,
    remoteVideo: VideoTrack?,
    onToggleMute: () -> Unit,
    onToggleVideo: () -> Unit,
    onSwitchCamera: () -> Unit,
    onHangup: () -> Unit,
    onMinimize: () -> Unit
) {
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (remoteVideo != null && connected) {
            WebRtcVideoView(track = remoteVideo, mirror = false, modifier = Modifier.fillMaxSize())
        } else {
            CallBackground {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    ChatAvatar(name = peerName, size = 120.dp, online = connected)
                }
            }
        }

        if (videoOn && localVideo != null) {
            WebRtcVideoView(
                track = localVideo,
                mirror = true,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 64.dp, end = 16.dp)
                    .size(110.dp, 156.dp)
                    .clip(RoundedCornerShape(16.dp))
            )
        }

        CallMinimizeButton(
            onClick = onMinimize,
            lightOnDark = true,
            modifier = Modifier.align(Alignment.TopStart)
        )

        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 56.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(peerName, color = DeepText, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(status, color = if (connected) DeepAccent else DeepMuted)
        }

        Surface(
            color = Color.Black.copy(alpha = 0.55f),
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 32.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CallControlChip(
                    icon = if (muted) Icons.Default.MicOff else Icons.Default.Mic,
                    label = "Микрофон",
                    active = muted,
                    onClick = onToggleMute
                )
                CallControlChip(
                    icon = if (videoOn) Icons.Default.Videocam else Icons.Default.VideocamOff,
                    label = "Камера",
                    active = !videoOn,
                    onClick = onToggleVideo
                )
                CallControlChip(
                    icon = Icons.Default.Cameraswitch,
                    label = "Сменить",
                    active = false,
                    onClick = onSwitchCamera
                )
                FloatingActionButton(
                    onClick = onHangup,
                    containerColor = DeepError,
                    modifier = Modifier.size(56.dp)
                ) {
                    Icon(Icons.Default.CallEnd, contentDescription = "Завершить")
                }
            }
        }
    }
}

@Composable
private fun CallMinimizeButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    lightOnDark: Boolean = false
) {
    val bg = if (lightOnDark) Color.White.copy(alpha = 0.14f) else DeepSurfaceHigh.copy(alpha = 0.72f)
    val borderColor = if (lightOnDark) Color.White.copy(alpha = 0.22f) else DeepAccent.copy(alpha = 0.25f)
    val contentColor = if (lightOnDark) Color.White else DeepText
    val subColor = if (lightOnDark) Color.White.copy(alpha = 0.72f) else DeepMuted

    Surface(
        onClick = onClick,
        modifier = modifier
            .statusBarsPadding()
            .padding(start = 16.dp, top = 12.dp)
            .border(1.dp, borderColor, RoundedCornerShape(20.dp)),
        color = bg,
        shape = RoundedCornerShape(20.dp),
        shadowElevation = if (lightOnDark) 0.dp else 6.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                Icons.Default.KeyboardArrowDown,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(22.dp)
            )
            Column {
                Text(
                    "В чат",
                    color = contentColor,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    "свернуть",
                    color = subColor,
                    style = MaterialTheme.typography.labelSmall
                )
            }
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
