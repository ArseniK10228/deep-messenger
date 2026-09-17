package online.deepdesign.deep.ui.call

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.zIndex
import online.deepdesign.deep.call.CallManager
import online.deepdesign.deep.call.CallUiState

@Composable
fun BoxScope.CallUiLayer(
    callManager: CallManager,
    onAccept: () -> Unit,
    onOpenAudioSettings: () -> Unit
) {
    val callState by callManager.state.collectAsState()
    val overlayExpanded by callManager.overlayExpanded.collectAsState()

    if (callState is CallUiState.Idle) return

    val muted by callManager.muted.collectAsState()
    val peerMuted by callManager.peerMuted.collectAsState()
    val callAudio by callManager.callAudio.collectAsState()
    val callNetwork by callManager.callNetwork.collectAsState()
    val micLevel by callManager.micLevel.collectAsState()
    val videoOn by callManager.videoOn.collectAsState()
    val localVideo by callManager.localVideoTrack.collectAsState()
    val localVideoMirror by callManager.localVideoMirror.collectAsState()
    val remoteVideo by callManager.remoteVideoTrack.collectAsState()

    val showFullOverlay = callState is CallUiState.Incoming || overlayExpanded

    if (showFullOverlay) {
        AnimatedVisibility(
            visible = true,
            enter = fadeIn(tween(180)) +
                slideInVertically(tween(260, easing = FastOutSlowInEasing)) { -it / 8 },
            exit = fadeOut(tween(140)) +
                slideOutVertically(tween(180, easing = FastOutSlowInEasing)) { -it / 6 },
            modifier = Modifier
                .fillMaxSize()
                .zIndex(1f)
        ) {
            CallOverlay(
                modifier = Modifier.fillMaxSize(),
                state = callState,
                muted = muted,
                peerMuted = peerMuted,
                callAudio = callAudio,
                callNetwork = callNetwork,
                micLevel = micLevel,
                videoOn = videoOn,
                localVideo = localVideo,
                localVideoMirror = localVideoMirror,
                remoteVideo = remoteVideo,
                onAccept = onAccept,
                onReject = { callManager.rejectIncoming() },
                onHangup = { callManager.hangup() },
                onToggleMute = { callManager.toggleMute() },
                onOpenAudioSettings = onOpenAudioSettings,
                onToggleVideo = { callManager.toggleVideo() },
                onSwitchCamera = { callManager.switchCamera() },
                onMinimize = { callManager.minimizeOverlay() }
            )
        }
    } else {
        CallMinimizedBar(
            state = callState,
            muted = muted,
            peerMuted = peerMuted,
            onExpand = { callManager.expandOverlay() },
            onToggleMute = { callManager.toggleMute() },
            onHangup = { callManager.hangup() },
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .statusBarsPadding()
                .zIndex(1f)
        )
    }
}
