package online.deepdesign.deep.session

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import online.deepdesign.deep.DeepApp

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val app = context.applicationContext as DeepApp
                val token = app.sessionStore.tokenFlow.first()
                if (!token.isNullOrBlank()) {
                    SessionForegroundService.start(context)
                }
            } finally {
                pending.finish()
            }
        }
    }
}
