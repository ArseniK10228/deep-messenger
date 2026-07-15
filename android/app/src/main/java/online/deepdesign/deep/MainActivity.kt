package online.deepdesign.deep

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.zIndex
import androidx.core.content.ContextCompat
import online.deepdesign.deep.call.CallPermissions
import online.deepdesign.deep.call.CallUiState
import online.deepdesign.deep.navigation.DeepNavHost
import online.deepdesign.deep.ui.call.CallMinimizedBar
import online.deepdesign.deep.ui.call.CallOverlay
import online.deepdesign.deep.ui.theme.DeepTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestNotificationPermissionIfNeeded()
        enableEdgeToEdge()
        setContent {
            DeepTheme {
                val callManager = DeepApp.instance.callManager
                val callState by callManager.state.collectAsState()
                val callError by callManager.error.collectAsState()
                val muted by callManager.muted.collectAsState()
                val speakerOn by callManager.speakerOn.collectAsState()
                val overlayExpanded by callManager.overlayExpanded.collectAsState()
                val videoOn by callManager.videoOn.collectAsState()
                val localVideo by callManager.localVideoTrack.collectAsState()
                val localVideoMirror by callManager.localVideoMirror.collectAsState()
                val remoteVideo by callManager.remoteVideoTrack.collectAsState()
                val snackbar = remember { SnackbarHostState() }

                val micPermission = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestPermission()
                ) { granted ->
                    if (granted) callManager.acceptIncoming()
                }

                val videoAcceptPermissions = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestMultiplePermissions()
                ) { results ->
                    val mic = results[Manifest.permission.RECORD_AUDIO] == true
                    val cam = results[Manifest.permission.CAMERA] == true
                    if (mic && cam) callManager.acceptIncoming()
                }

                fun acceptCall() {
                    val incoming = callState as? CallUiState.Incoming
                    if (incoming?.video == true) {
                        val missing = CallPermissions.missingForVideo(this@MainActivity)
                        when {
                            missing.isEmpty() -> callManager.acceptIncoming()
                            else -> videoAcceptPermissions.launch(missing)
                        }
                    } else if (CallPermissions.hasMic(this@MainActivity)) {
                        callManager.acceptIncoming()
                    } else {
                        micPermission.launch(Manifest.permission.RECORD_AUDIO)
                    }
                }

                LaunchedEffect(callError) {
                    callError?.let {
                        snackbar.showSnackbar(it)
                        callManager.clearError()
                    }
                }

                Scaffold(
                    snackbarHost = { SnackbarHost(snackbar) },
                    contentWindowInsets = WindowInsets(0, 0, 0, 0),
                ) { _ ->
                    Box(Modifier.fillMaxSize()) {
                        DeepNavHost()

                        if (callState !is CallUiState.Idle) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .zIndex(1f)
                            ) {
                            val showFullOverlay = callState is CallUiState.Incoming || overlayExpanded
                            if (showFullOverlay) {
                                CallOverlay(
                                    modifier = Modifier.fillMaxSize(),
                                    state = callState,
                                    muted = muted,
                                    speakerOn = speakerOn,
                                    videoOn = videoOn,
                                    localVideo = localVideo,
                                    localVideoMirror = localVideoMirror,
                                    remoteVideo = remoteVideo,
                                    onAccept = { acceptCall() },
                                    onReject = { callManager.rejectIncoming() },
                                    onHangup = { callManager.hangup() },
                                    onToggleMute = { callManager.toggleMute() },
                                    onToggleSpeaker = { callManager.toggleSpeaker() },
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
            }
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 100)
        }
    }

    companion object {
        fun callIntent(context: Context): Intent =
            Intent(context, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
    }
}
