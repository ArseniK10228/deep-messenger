package online.deepdesign.deep.call

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import online.deepdesign.deep.DeepApp
import online.deepdesign.deep.MainActivity
import online.deepdesign.deep.SessionBootstrap
import online.deepdesign.deep.push.IncomingCallNotifier

/**
 * Trampoline for notification actions — required on Android 12+ instead of BroadcastReceiver.
 */
class CallNotificationActionActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val action = intent.action
        if (action.isNullOrBlank()) {
            finish()
            return
        }

        lifecycleScope.launch {
            try {
                val app = DeepApp.instance
                SessionBootstrap.restore(app.sessionStore, app)
                when (action) {
                    ACTION_ACCEPT -> handleAccept(app)
                    ACTION_REJECT -> handleReject(app)
                    ACTION_HANGUP -> app.callManager.hangup()
                }
            } finally {
                finish()
            }
        }
    }

    private fun handleAccept(app: DeepApp) {
        val callId = intent.getStringExtra(EXTRA_CALL_ID) ?: return
        val conversationId = intent.getStringExtra(EXTRA_CONVERSATION_ID).orEmpty()
        val callerName = intent.getStringExtra(EXTRA_CALLER_NAME) ?: "Deep"
        val video = intent.getBooleanExtra(EXTRA_VIDEO, false)

        IncomingCallNotifier.dismiss(this, callId)
        app.callManager.prepareIncomingFromNotification(callId, conversationId, callerName, video)

        val needsVideoPerms = video && !CallPermissions.hasVideoCallPermissions(this)
        val needsMic = !CallPermissions.hasMic(this)
        when {
            needsVideoPerms || needsMic -> {
                startActivity(
                    IncomingCallActivity.intent(this, callId, conversationId, callerName, video)
                )
            }
            else -> {
                app.callManager.acceptIncoming()
                startActivity(MainActivity.callIntent(this))
            }
        }
    }

    private fun handleReject(app: DeepApp) {
        val callId = intent.getStringExtra(EXTRA_CALL_ID) ?: return
        IncomingCallNotifier.dismiss(this, callId)
        app.callManager.rejectFromNotification(callId)
    }

    companion object {
        const val ACTION_ACCEPT = "online.deepdesign.deep.NOTIF_ACCEPT"
        const val ACTION_REJECT = "online.deepdesign.deep.NOTIF_REJECT"
        const val ACTION_HANGUP = "online.deepdesign.deep.NOTIF_HANGUP"
        const val EXTRA_CALL_ID = "callId"
        const val EXTRA_CONVERSATION_ID = "conversationId"
        const val EXTRA_CALLER_NAME = "callerName"
        const val EXTRA_VIDEO = "video"

        fun acceptIntent(
            context: Context,
            callId: String,
            conversationId: String,
            callerName: String,
            video: Boolean
        ): Intent = Intent(context, CallNotificationActionActivity::class.java).apply {
            action = ACTION_ACCEPT
            putExtra(EXTRA_CALL_ID, callId)
            putExtra(EXTRA_CONVERSATION_ID, conversationId)
            putExtra(EXTRA_CALLER_NAME, callerName)
            putExtra(EXTRA_VIDEO, video)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }

        fun rejectIntent(context: Context, callId: String): Intent =
            Intent(context, CallNotificationActionActivity::class.java).apply {
                action = ACTION_REJECT
                putExtra(EXTRA_CALL_ID, callId)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }

        fun hangupIntent(context: Context): Intent =
            Intent(context, CallNotificationActionActivity::class.java).apply {
                action = ACTION_HANGUP
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
    }
}
