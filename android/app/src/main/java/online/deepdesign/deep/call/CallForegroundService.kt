package online.deepdesign.deep.call

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import online.deepdesign.deep.MainActivity
import online.deepdesign.deep.R

class CallForegroundService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            else -> {
                val peer = intent?.getStringExtra(EXTRA_PEER) ?: "Deep"
                ensureChannel()
                val notification = buildNotification(peer)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    startForeground(
                        NOTIFICATION_ID,
                        notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                    )
                } else {
                    @Suppress("DEPRECATION")
                    startForeground(NOTIFICATION_ID, notification)
                }
            }
        }
        return START_STICKY
    }

    private fun buildNotification(peer: String): Notification {
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Deep — звонок")
            .setContentText(peer)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(open)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .build()
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val mgr = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Звонки Deep",
            NotificationManager.IMPORTANCE_LOW
        )
        mgr.createNotificationChannel(channel)
    }

    companion object {
        private const val CHANNEL_ID = "deep_calls"
        private const val NOTIFICATION_ID = 42
        private const val EXTRA_PEER = "peer"
        private const val ACTION_STOP = "stop"

        fun start(context: Context, peerName: String, outgoing: Boolean) {
            val intent = Intent(context, CallForegroundService::class.java)
                .putExtra(EXTRA_PEER, if (outgoing) "Вызов: $peerName" else peerName)
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, CallForegroundService::class.java).setAction(ACTION_STOP)
            context.startService(intent)
        }
    }
}
