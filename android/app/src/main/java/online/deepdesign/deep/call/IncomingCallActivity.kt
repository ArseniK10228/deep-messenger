package online.deepdesign.deep.call

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import online.deepdesign.deep.DeepApp
import online.deepdesign.deep.MainActivity
import online.deepdesign.deep.SessionBootstrap
import online.deepdesign.deep.push.IncomingCallNotifier
import online.deepdesign.deep.ui.call.CallAudioSettingsSheet
import online.deepdesign.deep.ui.call.CallOverlay
import online.deepdesign.deep.ui.theme.DeepTheme

class IncomingCallActivity : ComponentActivity() {
    private var isVideoCall = false
    private var callId: String = ""

    private val micPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) acceptCall()
        else finish()
    }

    private val videoPermissions = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val mic = results[Manifest.permission.RECORD_AUDIO] == true
        val cam = results[Manifest.permission.CAMERA] == true
        if (mic && cam) acceptCall() else finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }
        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
        )

        val callId = intent.getStringExtra(EXTRA_CALL_ID) ?: run { finish(); return }
        this.callId = callId
        val conversationId = intent.getStringExtra(EXTRA_CONVERSATION_ID).orEmpty()
        val callerName = intent.getStringExtra(EXTRA_CALLER_NAME) ?: "Deep"
        isVideoCall = intent.getBooleanExtra(EXTRA_VIDEO, false)

        if (CallAppState.isInForeground()) {
            lifecycleScope.launch {
                bootstrapAndPrepare(callId, conversationId, callerName, isVideoCall)
                startActivity(MainActivity.callIntent(this@IncomingCallActivity))
                finish()
            }
            return
        }

        lifecycleScope.launch {
            bootstrapAndPrepare(callId, conversationId, callerName, isVideoCall)
        }

        setContent {
            DeepTheme {
                val callManager = DeepApp.instance.callManager
                val callState by callManager.state.collectAsState()
                val muted by callManager.muted.collectAsState()
                val callAudio by callManager.callAudio.collectAsState()
                val videoOn by callManager.videoOn.collectAsState()
                val localVideo by callManager.localVideoTrack.collectAsState()
                val localVideoMirror by callManager.localVideoMirror.collectAsState()
                val remoteVideo by callManager.remoteVideoTrack.collectAsState()
                var showAudioSettings by remember { mutableStateOf(false) }

                val bluetoothPermission = androidx.activity.compose.rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestPermission()
                ) { granted ->
                    if (granted) {
                        callManager.refreshCallAudioDevices()
                        showAudioSettings = true
                    }
                }

                fun openCallAudioSettings() {
                    if (CallPermissions.needsBluetoothConnect() &&
                        !CallPermissions.hasBluetoothConnect(this@IncomingCallActivity)
                    ) {
                        bluetoothPermission.launch(Manifest.permission.BLUETOOTH_CONNECT)
                    } else {
                        showAudioSettings = true
                    }
                }

                CallOverlay(
                    state = callState,
                    muted = muted,
                    callAudio = callAudio,
                    videoOn = videoOn,
                    localVideo = localVideo,
                    localVideoMirror = localVideoMirror,
                    remoteVideo = remoteVideo,
                    onAccept = { requestAccept() },
                    onReject = {
                        callManager.rejectIncoming()
                        IncomingCallNotifier.dismiss(this, callId)
                        finish()
                    },
                    onHangup = {
                        callManager.hangup()
                        IncomingCallNotifier.dismiss(this, callId)
                        finish()
                    },
                    onToggleMute = { callManager.toggleMute() },
                    onOpenAudioSettings = { openCallAudioSettings() },
                    onToggleVideo = { callManager.toggleVideo() },
                    onSwitchCamera = { callManager.switchCamera() },
                    onMinimize = {
                        startActivity(MainActivity.callIntent(this))
                        finish()
                    }
                )

                if (showAudioSettings) {
                    CallAudioSettingsSheet(
                        audio = callAudio,
                        onDismiss = { showAudioSettings = false },
                        onOutputSelected = { callManager.setCallOutputRoute(it) },
                        onInputSelected = { callManager.setCallInputRoute(it) }
                    )
                }
            }
        }
    }

    private suspend fun bootstrapAndPrepare(
        callId: String,
        conversationId: String,
        callerName: String,
        video: Boolean
    ) {
        val app = DeepApp.instance
        SessionBootstrap.restore(app.sessionStore, app)
        app.callManager.prepareIncomingFromNotification(callId, conversationId, callerName, video)
    }

    private fun requestAccept() {
        if (isVideoCall) {
            val missing = CallPermissions.missingForVideo(this)
            if (missing.isEmpty()) acceptCall()
            else videoPermissions.launch(missing)
        } else if (CallPermissions.hasMic(this)) {
            acceptCall()
        } else {
            micPermission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun acceptCall() {
        DeepApp.instance.callManager.acceptIncoming()
        IncomingCallNotifier.dismiss(this, callId)
        startActivity(MainActivity.callIntent(this))
        finish()
    }

    companion object {
        private const val EXTRA_CALL_ID = "callId"
        private const val EXTRA_CONVERSATION_ID = "conversationId"
        private const val EXTRA_CALLER_NAME = "callerName"
        private const val EXTRA_VIDEO = "video"

        fun intent(
            context: Context,
            callId: String,
            conversationId: String,
            callerName: String,
            video: Boolean = false
        ): Intent = Intent(context, IncomingCallActivity::class.java).apply {
            putExtra(EXTRA_CALL_ID, callId)
            putExtra(EXTRA_CONVERSATION_ID, conversationId)
            putExtra(EXTRA_CALLER_NAME, callerName)
            putExtra(EXTRA_VIDEO, video)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
    }
}
