package online.deepdesign.deep.call

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import online.deepdesign.deep.DeepApp
import online.deepdesign.deep.MainActivity
import online.deepdesign.deep.SessionBootstrap
import online.deepdesign.deep.push.IncomingCallNotifier
import online.deepdesign.deep.ui.call.CallOverlay
import online.deepdesign.deep.ui.theme.DeepTheme

class IncomingCallActivity : ComponentActivity() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val micPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) acceptCall()
        else finish()
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
        val conversationId = intent.getStringExtra(EXTRA_CONVERSATION_ID).orEmpty()
        val callerName = intent.getStringExtra(EXTRA_CALLER_NAME) ?: "Deep"
        val video = intent.getBooleanExtra(EXTRA_VIDEO, false)

        scope.launch {
            val app = DeepApp.instance
            SessionBootstrap.restore(app.sessionStore, app)
            app.callManager.prepareIncomingFromNotification(callId, conversationId, callerName, video)
        }

        setContent {
            DeepTheme {
                val callManager = DeepApp.instance.callManager
                val callState by callManager.state.collectAsState()
                val muted by callManager.muted.collectAsState()
                val speakerOn by callManager.speakerOn.collectAsState()
                val videoOn by callManager.videoOn.collectAsState()
                val localVideo by callManager.localVideoTrack.collectAsState()
                val remoteVideo by callManager.remoteVideoTrack.collectAsState()

                CallOverlay(
                    state = callState,
                    muted = muted,
                    speakerOn = speakerOn,
                    videoOn = videoOn,
                    localVideo = localVideo,
                    remoteVideo = remoteVideo,
                    onAccept = { requestAccept() },
                    onReject = {
                        callManager.rejectIncoming()
                        IncomingCallNotifier.dismiss(this)
                        finish()
                    },
                    onHangup = {
                        callManager.hangup()
                        IncomingCallNotifier.dismiss(this)
                        finish()
                    },
                    onToggleMute = { callManager.toggleMute() },
                    onToggleSpeaker = { callManager.toggleSpeaker() },
                    onToggleVideo = { callManager.toggleVideo() },
                    onSwitchCamera = { callManager.switchCamera() },
                    onMinimize = {
                        startActivity(MainActivity.callIntent(this))
                        finish()
                    }
                )
            }
        }
    }

    private fun requestAccept() {
        val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        if (granted) acceptCall() else micPermission.launch(Manifest.permission.RECORD_AUDIO)
    }

    private fun acceptCall() {
        DeepApp.instance.callManager.acceptIncoming()
        IncomingCallNotifier.dismiss(this)
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
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
    }
}
