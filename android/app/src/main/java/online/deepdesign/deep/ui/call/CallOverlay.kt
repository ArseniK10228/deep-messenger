package online.deepdesign.deep.ui.call

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Cameraswitch
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
import online.deepdesign.deep.call.CallUiState
import online.deepdesign.deep.ui.components.ChatAvatar
import online.deepdesign.deep.ui.components.deepAppear
import online.deepdesign.deep.ui.theme.DeepAccent
import online.deepdesign.deep.ui.theme.DeepBg
import online.deepdesign.deep.ui.theme.DeepError
import online.deepdesign.deep.ui.theme.DeepMuted
import online.deepdesign.deep.ui.theme.DeepSurfaceHigh
import online.deepdesign.deep.ui.theme.DeepText
import org.webrtc.VideoTrack

@Composable
fun CallOverlay(
    state: CallUiState,
    muted: Boolean,
    speakerOn: Boolean,
    videoOn: Boolean,
    localVideo: VideoTrack?,
    localVideoMirror: Boolean,
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
                localVideoMirror = localVideoMirror,
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
                localVideoMirror = localVideoMirror,
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
                        Color(0xFF221438),
                        DeepBg,
                        Color(0xFF080810)
                    )
                )
            )
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(DeepAccent.copy(alpha = 0.12f), Color.Transparent),
                        radius = 900f
                    )
                )
        )
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
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 28.dp, vertical = 32.dp)
                .deepAppear(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.weight(0.3f))
            CallStatusChip(
                text = if (video) "Входящий видеозвонок" else "Входящий звонок",
                accent = true
            )
            Spacer(Modifier.height(28.dp))
            Box(Modifier.scale(pulse)) {
                ChatAvatar(name = callerName, size = 124.dp)
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
                    icon = if (video) Icons.Default.Videocam else Icons.Default.Call,
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
            CallMinimizeButton(onClick = onMinimize, modifier = Modifier.align(Alignment.TopStart))

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(horizontal = 24.dp, vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(Modifier.weight(0.22f))
                CallStatusChip(text = status, accent = connected)
                Spacer(Modifier.height(28.dp))
                Box(Modifier.scale(pulse)) {
                    ChatAvatar(name = peerName, size = 132.dp, online = connected)
                }
                Spacer(Modifier.height(20.dp))
                Text(
                    peerName,
                    color = DeepText,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.weight(1f))

                CallControlsDock {
                    CallControlChip(
                        icon = if (muted) Icons.Default.MicOff else Icons.Default.Mic,
                        label = if (muted) "Вкл." else "Микрофон",
                        active = muted,
                        onClick = onToggleMute
                    )
                    CallControlChip(
                        icon = if (speakerOn) Icons.AutoMirrored.Filled.VolumeUp else Icons.Default.VolumeOff,
                        label = "Динамик",
                        active = speakerOn,
                        onClick = onToggleSpeaker
                    )
                    HangupChip(onClick = onHangup)
                }
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
    localVideoMirror: Boolean,
    remoteVideo: VideoTrack?,
    onToggleMute: () -> Unit,
    onToggleVideo: () -> Unit,
    onSwitchCamera: () -> Unit,
    onHangup: () -> Unit,
    onMinimize: () -> Unit
) {
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        when {
            remoteVideo != null && connected -> {
                WebRtcVideoView(track = remoteVideo, mirror = false, modifier = Modifier.fillMaxSize())
            }
            videoOn && localVideo != null -> {
                WebRtcVideoView(track = localVideo, mirror = localVideoMirror, modifier = Modifier.fillMaxSize())
            }
            else -> {
                CallBackground {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        ChatAvatar(name = peerName, size = 120.dp, online = connected)
                    }
                }
            }
        }

        Box(
            Modifier
                .fillMaxWidth()
                .height(160.dp)
                .align(Alignment.TopCenter)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Black.copy(alpha = 0.72f), Color.Transparent)
                    )
                )
        )
        Box(
            Modifier
                .fillMaxWidth()
                .height(200.dp)
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.82f))
                    )
                )
        )

        if (videoOn && localVideo != null && remoteVideo != null && connected) {
            Surface(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(top = 56.dp, end = 16.dp)
                    .size(112.dp, 158.dp)
                    .border(2.dp, Color.White.copy(alpha = 0.35f), RoundedCornerShape(18.dp)),
                shape = RoundedCornerShape(18.dp),
                shadowElevation = 8.dp
            ) {
                WebRtcVideoView(
                    track = localVideo,
                    mirror = localVideoMirror,
                    modifier = Modifier.fillMaxSize()
                )
            }
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
            Text(
                peerName,
                color = Color.White,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            CallStatusChip(text = status, accent = connected, light = true)
        }

        CallControlsDock(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 20.dp),
            dark = true
        ) {
            CallControlChip(
                icon = if (muted) Icons.Default.MicOff else Icons.Default.Mic,
                label = "Микрофон",
                active = muted,
                onClick = onToggleMute,
                light = true
            )
            CallControlChip(
                icon = if (videoOn) Icons.Default.Videocam else Icons.Default.VideocamOff,
                label = "Камера",
                active = !videoOn,
                onClick = onToggleVideo,
                light = true
            )
            CallControlChip(
                icon = Icons.Default.Cameraswitch,
                label = "Сменить",
                active = false,
                onClick = onSwitchCamera,
                light = true
            )
            HangupChip(onClick = onHangup)
        }
    }
}

@Composable
private fun CallStatusChip(
    text: String,
    accent: Boolean,
    light: Boolean = false
) {
    val bg = when {
        light && accent -> DeepAccent.copy(alpha = 0.35f)
        light -> Color.White.copy(alpha = 0.14f)
        accent -> DeepAccent.copy(alpha = 0.22f)
        else -> DeepSurfaceHigh.copy(alpha = 0.55f)
    }
    val color = when {
        light && accent -> Color.White
        light -> Color.White.copy(alpha = 0.85f)
        accent -> DeepAccent
        else -> DeepMuted
    }
    Surface(color = bg, shape = RoundedCornerShape(20.dp)) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
            color = color,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun CallControlsDock(
    modifier: Modifier = Modifier,
    dark: Boolean = false,
    content: @Composable RowScope.() -> Unit
) {
    Surface(
        modifier = modifier,
        color = if (dark) Color.Black.copy(alpha = 0.55f) else DeepSurfaceHigh.copy(alpha = 0.78f),
        shape = RoundedCornerShape(28.dp),
        shadowElevation = if (dark) 0.dp else 8.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = content
        )
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
            Icon(Icons.Default.KeyboardArrowDown, contentDescription = null, tint = contentColor, modifier = Modifier.size(22.dp))
            Column {
                Text("В чат", color = contentColor, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                Text("свернуть", color = subColor, style = MaterialTheme.typography.labelSmall)
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
            modifier = Modifier.size(72.dp)
        ) {
            Icon(icon, contentDescription = label, modifier = Modifier.size(30.dp))
        }
        Spacer(Modifier.height(10.dp))
        Text(label, color = DeepMuted, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun HangupChip(onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        FloatingActionButton(
            onClick = onClick,
            containerColor = DeepError,
            contentColor = Color.White,
            shape = CircleShape,
            modifier = Modifier.size(58.dp)
        ) {
            Icon(Icons.Default.CallEnd, contentDescription = "Завершить", modifier = Modifier.size(28.dp))
        }
        Spacer(Modifier.height(6.dp))
        Text("Сброс", color = DeepMuted, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun CallControlChip(
    icon: ImageVector,
    label: String,
    active: Boolean,
    onClick: () -> Unit,
    light: Boolean = false
) {
    val activeBg = if (light) Color.White.copy(alpha = 0.22f) else DeepAccent.copy(alpha = 0.35f)
    val iconTint = when {
        active -> DeepAccent
        light -> Color.White
        else -> DeepText
    }
    val labelColor = if (light) Color.White.copy(alpha = 0.8f) else DeepMuted

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(
            onClick = onClick,
            modifier = Modifier
                .size(52.dp)
                .background(if (active) activeBg else Color.Transparent, CircleShape)
        ) {
            Icon(icon, contentDescription = label, tint = iconTint, modifier = Modifier.size(24.dp))
        }
        Text(label, color = labelColor, style = MaterialTheme.typography.labelSmall)
    }
}
