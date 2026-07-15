package online.deepdesign.deep.call

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity

/**
 * Invisible activity in a separate task so swiping the main app from recents
 * does not kill an active call process.
 */
class CallHoldActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Never steal touches/focus — this task only keeps the process alive in background.
        window.addFlags(
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        )
        instance = this
        if (intent.getBooleanExtra(EXTRA_FINISH, false)) {
            finish()
            return
        }
        if (!DeepAppCallBridge.isInCall()) {
            finish()
        }
    }

    override fun onResume() {
        super.onResume()
        if (!DeepAppCallBridge.isInCall()) finish()
    }

    override fun onDestroy() {
        if (instance === this) instance = null
        super.onDestroy()
    }

    companion object {
        private const val EXTRA_FINISH = "finish"

        @Volatile
        private var instance: CallHoldActivity? = null

        fun start(context: Context) {
            if (instance != null) return
            if (CallAppState.isInForeground()) return
            val intent = Intent(context, CallHoldActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }

        fun stop(context: Context) {
            val active = instance
            if (active != null) {
                active.finish()
                return
            }
            val intent = Intent(context, CallHoldActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra(EXTRA_FINISH, true)
            context.startActivity(intent)
        }
    }
}
