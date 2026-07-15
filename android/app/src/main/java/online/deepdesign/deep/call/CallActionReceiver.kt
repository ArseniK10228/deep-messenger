package online.deepdesign.deep.call

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import online.deepdesign.deep.DeepApp
import online.deepdesign.deep.MainActivity
import online.deepdesign.deep.SessionBootstrap
import online.deepdesign.deep.push.IncomingCallNotifier

class CallActionReceiver : BroadcastReceiver() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        scope.launch {
            try {
                val app = DeepApp.instance
                SessionBootstrap.restore(app.sessionStore, app)
                when (intent.action) {
                    ACTION_ACCEPT -> {
                        val callId = intent.getStringExtra(EXTRA_CALL_ID) ?: return@launch
                        val conversationId = intent.getStringExtra(EXTRA_CONVERSATION_ID).orEmpty()
                        val callerName = intent.getStringExtra(EXTRA_CALLER_NAME) ?: "Deep"
                        app.callManager.prepareIncomingFromNotification(callId, conversationId, callerName)
                        IncomingCallNotifier.dismiss(context)
                        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
                            == PackageManager.PERMISSION_GRANTED
                        ) {
                            app.callManager.acceptIncoming()
                            context.startActivity(MainActivity.callIntent(context))
                        } else {
                            context.startActivity(
                                IncomingCallActivity.intent(context, callId, conversationId, callerName)
                            )
                        }
                    }
                    ACTION_REJECT -> {
                        app.callManager.rejectIncoming()
                        IncomingCallNotifier.dismiss(context)
                    }
                }
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_ACCEPT = "online.deepdesign.deep.CALL_ACCEPT"
        const val ACTION_REJECT = "online.deepdesign.deep.CALL_REJECT"
        const val EXTRA_CALL_ID = "callId"
        const val EXTRA_CONVERSATION_ID = "conversationId"
        const val EXTRA_CALLER_NAME = "callerName"

        fun acceptIntent(
            context: Context,
            callId: String,
            conversationId: String,
            callerName: String
        ): Intent = Intent(context, CallActionReceiver::class.java).apply {
            action = ACTION_ACCEPT
            putExtra(EXTRA_CALL_ID, callId)
            putExtra(EXTRA_CONVERSATION_ID, conversationId)
            putExtra(EXTRA_CALLER_NAME, callerName)
        }

        fun rejectIntent(context: Context, callId: String): Intent =
            Intent(context, CallActionReceiver::class.java).apply {
                action = ACTION_REJECT
                putExtra(EXTRA_CALL_ID, callId)
            }
    }
}
