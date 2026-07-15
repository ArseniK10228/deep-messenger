package online.deepdesign.deep.push

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import online.deepdesign.deep.R
import online.deepdesign.deep.call.CallNotificationActionActivity
import online.deepdesign.deep.call.IncomingCallActivity

object IncomingCallNotifier {
    const val CHANNEL_ID = "deep_incoming_calls_v2"
    private const val LEGACY_CHANNEL_ID = "deep_incoming_calls"

    fun show(context: Context, data: Map<String, String>) {
        val callId = data["callId"] ?: return
        val conversationId = data["conversationId"] ?: ""
        val callerName = data["callerName"] ?: "Deep"
        val video = data["video"] == "true"

        ensureChannel(context)
        cancelLegacy(context)

        val notificationId = notificationId(callId)
        val fullScreen = PendingIntent.getActivity(
            context,
            notificationId,
            IncomingCallActivity.intent(context, callId, conversationId, callerName, video),
            pendingFlags()
        )

        val accept = PendingIntent.getActivity(
            context,
            notificationId + 1,
            CallNotificationActionActivity.acceptIntent(context, callId, conversationId, callerName, video),
            pendingFlags()
        )

        val reject = PendingIntent.getActivity(
            context,
            notificationId + 2,
            CallNotificationActionActivity.rejectIntent(context, callId),
            pendingFlags()
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_call)
            .setContentTitle(if (video) "Входящий видеозвонок" else "Входящий звонок")
            .setContentText(callerName)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setAutoCancel(false)
            .setFullScreenIntent(fullScreen, true)
            .setContentIntent(fullScreen)
            .setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE))
            .setVibrate(longArrayOf(0, 800, 400, 800))

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val caller = Person.Builder().setName(callerName).setImportant(true).build()
            builder.setStyle(
                NotificationCompat.CallStyle.forIncomingCall(caller, reject, accept)
            )
        } else {
            builder
                .addAction(R.drawable.ic_stat_call, "Принять", accept)
                .addAction(R.drawable.ic_stat_call, "Отклонить", reject)
        }

        val notification = builder.build().apply {
            flags = flags or Notification.FLAG_INSISTENT
        }

        NotificationManagerCompat.from(context).notify(notificationId, notification)
    }

    fun dismiss(context: Context, callId: String? = null) {
        if (callId != null) {
            NotificationManagerCompat.from(context).cancel(notificationId(callId))
        } else {
            NotificationManagerCompat.from(context).cancelAll()
        }
        cancelLegacy(context)
    }

    fun notificationId(callId: String): Int =
        callId.hashCode().and(0x7FFFFFFF).coerceAtLeast(1)

    private fun pendingFlags(): Int =
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE

    private fun cancelLegacy(context: Context) {
        NotificationManagerCompat.from(context).cancel(9001)
    }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val mgr = context.getSystemService(NotificationManager::class.java)
        mgr.deleteNotificationChannel(LEGACY_CHANNEL_ID)
        val ringtone = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Входящие звонки",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Звонки Deep Messenger"
            setSound(
                ringtone,
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                    .build()
            )
            enableVibration(true)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            setBypassDnd(true)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            channel.setAllowBubbles(false)
        }
        mgr.createNotificationChannel(channel)
    }
}
