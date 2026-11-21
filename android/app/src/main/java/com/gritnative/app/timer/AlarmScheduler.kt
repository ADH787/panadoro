package com.gritnative.app.timer

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.gritnative.app.MainActivity
import com.gritnative.app.data.TimerState
import com.gritnative.app.data.computeSecondsLeft

/**
 * The service updates the timer every second, but Android can kill it or put the
 * process in a long doze. This exact alarm is the safety net that completes the
 * phase even when nothing else is awake.
 */
object AlarmScheduler {
    private const val REQUEST_CODE = 4021

    fun schedule(context: Context, timer: TimerState?) {
        if (timer?.running != true) {
            cancel(context)
            return
        }
        val left = computeSecondsLeft(timer)
        if (left <= 0) {
            cancel(context)
            return
        }
        val triggerAt = System.currentTimeMillis() + left * 1000L + 750L
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val showIntent = PendingIntent.getActivity(
            context,
            REQUEST_CODE + 1,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val alarmIntent = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            Intent(context, FocusAlarmReceiver::class.java).apply {
                action = FocusAlarmReceiver.ACTION_ALARM
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        try {
            val canExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
                alarmManager.canScheduleExactAlarms()
            if (canExact) {
                alarmManager.setAlarmClock(
                    AlarmManager.AlarmClockInfo(triggerAt, showIntent),
                    alarmIntent
                )
            } else {
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAt,
                    alarmIntent
                )
            }
        } catch (_: SecurityException) {
            runCatching {
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAt,
                    alarmIntent
                )
            }
        } catch (_: Exception) {
            // The foreground service remains the primary mechanism.
        }
    }

    fun cancel(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val alarmIntent = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            Intent(context, FocusAlarmReceiver::class.java).apply {
                action = FocusAlarmReceiver.ACTION_ALARM
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        runCatching { alarmManager.cancel(alarmIntent) }
    }
}
