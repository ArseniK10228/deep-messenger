package online.deepdesign.deep.push

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import online.deepdesign.deep.R
import online.deepdesign.deep.call.CallActionReceiver
import online.deepdesign.deep.call.IncomingCallActivity

object IncomingCallNotifier {
    const val CHANNEL_ID = "deep_incoming_calls"
    private const val NOTIFICATION_ID = 9001

    fun show(context: Context, data: Map<String, String>) {
        val callId = data["callId"] ?: return
        val conversationId = data["conversationId"] ?: ""
        val callerName = data["callerName"] ?: "Deep"
        val video = data["video"] == "true"

        ensureChannel(context)

        val fullScreen = PendingIntent.getActivity(
            context,
            callId.hashCode(),
            IncomingCallActivity.intent(context, callId, conversationId, callerName, video),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val accept = PendingIntent.getBroadcast(
            context,
            callId.hashCode() + 1,
            CallActionReceiver.acceptIntent(context, callId, conversationId, callerName, video),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val reject = PendingIntent.getBroadcast(
            context,
            callId.hashCode() + 2,
            CallActionReceiver.rejectIntent(context, callId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(if (video) "Входящий видеозвонок" else "Входящий звонок")
            .setContentText(callerName)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setAutoCancel(false)
            .setFullScreenIntent(fullScreen, true)
            .setContentIntent(fullScreen)
            .addAction(R.drawable.ic_launcher_foreground, "Принять", accept)
            .addAction(R.drawable.ic_launcher_foreground, "Отклонить", reject)
            .setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE))
            .setVibrate(longArrayOf(0, 800, 400, 800))
            .build()

        notification.flags = notification.flags or Notification.FLAG_INSISTENT

        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
    }

    fun dismiss(context: Context) {
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
    }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val mgr = context.getSystemService(NotificationManager::class.java)
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
        mgr.createNotificationChannel(channel)
    }
}
