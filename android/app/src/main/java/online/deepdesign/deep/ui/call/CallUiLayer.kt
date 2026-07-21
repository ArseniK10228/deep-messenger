package online.deepdesign.deep.ui.call

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.zIndex
import online.deepdesign.deep.call.CallManager
import online.deepdesign.deep.call.CallUiState

@Composable
fun CallUiLayer(
    callManager: CallManager,
    onAccept: () -> Unit,
    onOpenAudioSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val callState by callManager.state.collectAsState()
    val overlayExpanded by callManager.overlayExpanded.collectAsState()

    AnimatedVisibility(
        visible = callState !is CallUiState.Idle,
        enter = fadeIn(tween(180)) +
            slideInVertically(tween(260, easing = FastOutSlowInEasing)) { it / 6 },
        exit = fadeOut(tween(140)) +
            slideOutVertically(tween(180, easing = FastOutSlowInEasing)) { it / 5 },
        modifier = modifier
            .fillMaxSize()
            .zIndex(1f)
    ) {
        val muted by callManager.muted.collectAsState()
        val callAudio by callManager.callAudio.collectAsState()
        val callNetwork by callManager.callNetwork.collectAsState()
        val micLevel by callManager.micLevel.collectAsState()
        val videoOn by callManager.videoOn.collectAsState()
        val localVideo by callManager.localVideoTrack.collectAsState()
        val localVideoMirror by callManager.localVideoMirror.collectAsState()
        val remoteVideo by callManager.remoteVideoTrack.collectAsState()

        val showFullOverlay = callState is CallUiState.Incoming || overlayExpanded

        Box(Modifier.fillMaxSize()) {
            AnimatedContent(
                targetState = showFullOverlay,
                transitionSpec = {
                    (fadeIn(tween(220, easing = FastOutSlowInEasing)) +
                        scaleIn(initialScale = 0.96f, animationSpec = tween(220))) togetherWith
                        (fadeOut(tween(160)) + scaleOut(targetScale = 0.98f, animationSpec = tween(160)))
                },
                label = "callOverlayMode",
                modifier = Modifier.fillMaxSize()
            ) { full ->
                if (full) {
                    CallOverlay(
                        modifier = Modifier.fillMaxSize(),
                        state = callState,
                        muted = muted,
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
                } else {
                    CallMinimizedBar(
                        state = callState,
                        muted = muted,
                        onExpand = { callManager.expandOverlay() },
                        onToggleMute = { callManager.toggleMute() },
                        onHangup = { callManager.hangup() },
                        modifier = Modifier.align(Alignment.BottomCenter)
                    )
                }
            }
        }
    }
}
