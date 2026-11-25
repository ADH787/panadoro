package com.gritnative.app.timer

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
import com.gritnative.app.MainActivity
import com.gritnative.app.AppForeground
import com.gritnative.app.R
import com.gritnative.app.data.StoreRepository
import com.gritnative.app.data.PhaseCompletion
import com.gritnative.app.data.formatMinutesShort

/** Sound, vibration and a high-priority notification when a phase ends. */
object CompletionAlert {
    const val CHANNEL_ALARM = "focus_alarm"
    const val CHANNEL_SILENT = "focus_alarm_silent"
    const val NOTIFICATION_ID = 2002

    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val alarmChannel = NotificationChannel(
            CHANNEL_ALARM,
            "Focus alarms",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Plays when a focus session or break ends"
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 260, 160, 260)
            setSound(
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                    ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
        }
        val silentChannel = NotificationChannel(
            CHANNEL_SILENT,
            "Focus alarms (silent)",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Announces phase changes without sound"
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 260, 160, 260)
            setSound(null, null)
            lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
        }
        manager.createNotificationChannel(alarmChannel)
        manager.createNotificationChannel(silentChannel)
    }

    fun notifyCompletion(context: Context, completion: PhaseCompletion) {
        createChannels(context)
        // Only the device that last drove the timer alarms. Every other
        // signed-in device records the finished phase quietly, so one
        // session can never ring on the whole account at once.
        if (completion.ownerDevice.isNotBlank() && completion.ownerDevice != StoreRepository.deviceId) {
            return
        }
        val settings = StoreRepository.currentSettings
        val inApp = AppForeground.isForeground
        if (inApp) {
            // The full-screen overlay owns the moment while the app is on
            // screen: no completion notification is posted at all, so no
            // channel can beep or buzz behind it. One ring, one stop button,
            // and a double alarm is impossible. The background path below
            // keeps the notification alarm for when the app is not visible.
            CompletionAlarm.start(context, completion)
            return
        }
        val channel = if (settings.alarmSound) CHANNEL_ALARM else CHANNEL_SILENT
        val finishedFocus = completion.focusCompleted
        val tagName = completion.tag?.name ?: "Focus"
        val title = if (finishedFocus) "Focus complete · $tagName" else "Break complete"
        val minutes = completion.session?.let { (it.durationSec / 60.0).toInt() }?.coerceAtLeast(1)
        val text = when {
            finishedFocus && completion.session != null ->
                "${formatMinutesShort(minutes ?: 0)} banked. ${completion.next.phase.let {
                    if (it == "longBreak") "Long break is ready." else "Short break is ready."
                }}"
            finishedFocus -> "Time for a break."
            else -> "The next focus session is ready."
        }
        val openIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val builder = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_stat_focus)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setContentIntent(openIntent)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
        if (!settings.alarmSound) builder.setSilent(true)
        runCatching {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, builder.build())
        }
        // If notifications are denied, the alarm channel was muted, or our own
        // focus DND is holding the phone quiet, the notification cannot be the
        // alarm. Play it ourselves and keep a strong reference until it
        // finishes.
        if (settings.alarmSound &&
            (!notificationAlarmCanSound(context) || FocusDnd.isBlocking)
        ) {
            SessionAlarmPlayer.play(context)
        }
    }

    private fun notificationAlarmCanSound(context: Context): Boolean {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return false
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return true
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = manager.getNotificationChannel(CHANNEL_ALARM) ?: return true
        return channel.importance != NotificationManager.IMPORTANCE_NONE && channel.sound != null
    }
}
