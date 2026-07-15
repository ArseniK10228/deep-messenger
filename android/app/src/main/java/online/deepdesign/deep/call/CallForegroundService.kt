package online.deepdesign.deep.call

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.media.AudioManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import online.deepdesign.deep.MainActivity
import online.deepdesign.deep.R

class CallForegroundService : Service() {
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                releaseWakeLock()
                restoreAudioMode()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_HANGUP -> {
                DeepAppCallBridge.hangup()
                return START_STICKY
            }
            else -> {
                val peer = intent?.getStringExtra(EXTRA_PEER) ?: "Deep"
                val video = intent?.getBooleanExtra(EXTRA_VIDEO, false) == true
                if (!hasMicPermission()) {
                    Log.w(TAG, "RECORD_AUDIO not granted — cannot start call FGS")
                    stopSelf()
                    return START_NOT_STICKY
                }
                lastPeer = peer
                lastVideo = video
                ensureChannel()
                beginCallAudio()
                acquireWakeLock()
                val notification = buildNotification(peer)
                try {
                    startCallForeground(notification, video)
                } catch (e: SecurityException) {
                    Log.e(TAG, "startForeground failed", e)
                    releaseWakeLock()
                    restoreAudioMode()
                    stopSelf()
                    return START_NOT_STICKY
                }
            }
        }
        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        if (DeepAppCallBridge.isInCall()) {
            val peer = lastPeer ?: "Deep"
            ensureChannel()
            beginCallAudio()
            acquireWakeLock()
            try {
                startCallForeground(buildNotification(peer), lastVideo)
            } catch (e: SecurityException) {
                Log.e(TAG, "onTaskRemoved restart failed", e)
            }
        }
    }

    override fun onDestroy() {
        releaseWakeLock()
        restoreAudioMode()
        super.onDestroy()
    }

    private fun startCallForeground(notification: Notification, video: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            var type = ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            if (video) {
                type = type or ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
            }
            startForeground(NOTIFICATION_ID, notification, type)
        } else {
            @Suppress("DEPRECATION")
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun beginCallAudio() {
        val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        am.mode = AudioManager.MODE_IN_COMMUNICATION
    }

    private fun restoreAudioMode() {
        runCatching {
            val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            am.mode = AudioManager.MODE_NORMAL
        }
    }

    private fun acquireWakeLock() {
        runCatching {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            if (wakeLock?.isHeld == true) return
            wakeLock = pm.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "deep:call:fgs"
            ).apply {
                setReferenceCounted(false)
                acquire(4 * 60 * 60 * 1000L)
            }
        }
    }

    private fun releaseWakeLock() {
        runCatching { wakeLock?.release() }
        wakeLock = null
    }

    private fun hasMicPermission(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
    }

    private fun buildNotification(peer: String): Notification {
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val hangup = PendingIntent.getService(
            this,
            1,
            Intent(this, CallForegroundService::class.java).setAction(ACTION_HANGUP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Deep — звонок")
            .setContentText(peer)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(open)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .addAction(0, "Завершить", hangup)
            .build()
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val mgr = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Звонки Deep",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Активный звонок"
            setSound(null, null)
        }
        mgr.createNotificationChannel(channel)
    }

    companion object {
        private const val TAG = "CallForegroundService"
        private const val CHANNEL_ID = "deep_calls"
        private const val NOTIFICATION_ID = 42
        private const val EXTRA_PEER = "peer"
        private const val EXTRA_VIDEO = "video"
        private const val ACTION_STOP = "stop"
        private const val ACTION_HANGUP = "hangup"

        @Volatile
        private var lastPeer: String? = null

        @Volatile
        private var lastVideo: Boolean = false

        fun start(context: Context, peerName: String, outgoing: Boolean, video: Boolean = false) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED
            ) {
                return
            }
            val peer = if (outgoing) "Вызов: $peerName" else peerName
            lastPeer = peer
            lastVideo = video
            val intent = Intent(context, CallForegroundService::class.java)
                .putExtra(EXTRA_PEER, peer)
                .putExtra(EXTRA_VIDEO, video)
            try {
                context.startForegroundService(intent)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start call service", e)
            }
        }

        fun refresh(context: Context, peerName: String, outgoing: Boolean, video: Boolean) {
            start(context, peerName, outgoing, video)
        }

        fun stop(context: Context) {
            lastPeer = null
            lastVideo = false
            val intent = Intent(context, CallForegroundService::class.java).setAction(ACTION_STOP)
            context.startService(intent)
        }
    }
}

/** Thin bridge so the service can talk to CallManager without a hard circular dependency at init. */
object DeepAppCallBridge {
    fun isInCall(): Boolean = DeepAppCallBridgeHolder.manager?.isInCall() == true
    fun hangup() {
        DeepAppCallBridgeHolder.manager?.hangup()
    }
}

object DeepAppCallBridgeHolder {
    var manager: CallManager? = null
}
