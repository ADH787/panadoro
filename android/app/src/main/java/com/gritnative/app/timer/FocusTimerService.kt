package com.gritnative.app.timer

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.gritnative.app.MainActivity
import com.gritnative.app.R
import com.gritnative.app.data.StoreRepository
import com.gritnative.app.data.computeSecondsLeft
import com.gritnative.app.data.tagById
import com.gritnative.app.data.formatClock
import com.gritnative.app.widgets.WidgetUpdater
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Keeps a running phase visible and alive. The service is a thin shell around
 * StoreRepository: it never owns the countdown, so process death cannot lose the
 * session and the in-app timer and notification always show the same second.
 */
class FocusTimerService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var loopJob: Job? = null
    private var foregroundStarted = false
    private var screenReceiver: AodScreenReceiver? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createTimerChannel()
        CompletionAlert.createChannels(this)
        // While a session runs, a screen that goes dark with the always-on
        // display up lights straight back up.
        if (screenReceiver == null) {
            screenReceiver = AodScreenReceiver().also { registerAodScreenReceiver(this, it) }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_TOGGLE -> StoreRepository.toggleTimer()
            ACTION_SKIP -> StoreRepository.skipTimer()
            ACTION_STOP -> {
                stopLoop()
                return START_NOT_STICKY
            }
        }
        val running = StoreRepository.currentStore.timer?.running == true
        if (!running) {
            stopLoop()
            return START_NOT_STICKY
        }
        ensureForeground()
        startLoop()
        return START_STICKY
    }

    private fun ensureForeground() {
        val notification = buildNotification()
        if (!foregroundStarted) {
            startForeground(NOTIFICATION_ID, notification)
            foregroundStarted = true
        } else {
            notify(notification)
        }
    }

    private fun startLoop() {
        if (loopJob?.isActive == true) return
        loopJob = scope.launch {
            StoreRepository.awaitLoaded()
            var lastWidgetMinute = -1
            while (currentCoroutineContext().isActive) {
                val timer = StoreRepository.currentStore.timer
                if (timer?.running != true) {
                    stopLoop()
                    break
                }
                val now = System.currentTimeMillis()
                val remaining = computeSecondsLeft(timer, now)
                val widgetMinute = remaining / 60
                if (widgetMinute != lastWidgetMinute) {
                    lastWidgetMinute = widgetMinute
                    WidgetUpdater.refreshFocus(this@FocusTimerService)
                }
                // The system overlay clock (lock screen / other apps) rides
                // the same tick.
                AodOverlayWindow.updateIfShowing(this@FocusTimerService)
                if (remaining <= 0) {
                    val completion = StoreRepository.completeCurrentPhase()
                    if (completion != null) {
                        CompletionAlert.notifyCompletion(this@FocusTimerService, completion)
                    }
                    val next = StoreRepository.currentStore.timer
                    if (next?.running == true) {
                        lastWidgetMinute = -1
                        WidgetUpdater.refreshFocus(this@FocusTimerService)
                        AlarmScheduler.schedule(this@FocusTimerService, next)
                    } else {
                        AlarmScheduler.cancel(this@FocusTimerService)
                        stopLoop()
                        break
                    }
                }
                notify(buildNotification())
                delay(500L)
            }
        }
    }

    private fun notify(notification: Notification) {
        if (!foregroundStarted) return
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, notification)
    }

    private fun stopLoop() {
        foregroundStarted = false
        loopJob?.cancel()
        loopJob = null
        runCatching { stopForeground(STOP_FOREGROUND_REMOVE) }
        runCatching { stopSelf() }
    }

    private fun buildNotification(): Notification {
        val store = StoreRepository.currentStore
        val timer = store.timer
        val tag = timer?.let { store.tagById(it.tagId) }
        val remaining = computeSecondsLeft(timer)
        val total = (timer?.durationSec ?: 1).coerceAtLeast(1)
        val elapsed = (total - remaining).coerceIn(0, total)
        val phase = when (timer?.phase) {
            "shortBreak" -> "Short break"
            "longBreak" -> "Long break"
            else -> "Focus"
        }
        val title = if (timer?.phase == "focus") {
            "${tag?.name ?: "Focus"} · $phase"
        } else {
            phase
        }
        val cycle = (timer?.cycle ?: 0).coerceAtLeast(0)
        val cycles = (tag?.cycles ?: 4).coerceAtLeast(1)
        val percent = ((elapsed * 100.0) / total).roundToInt().coerceIn(0, 100)
        val subtitle = if (timer?.phase == "focus") {
            "${formatClock(remaining)} left · $percent% · cycle ${cycle.coerceAtLeast(1)} of $cycles"
        } else {
            "${formatClock(remaining)} left · $percent% · next up: focus"
        }
        val openIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val toggleIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, FocusTimerService::class.java).setAction(ACTION_TOGGLE),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val skipIntent = PendingIntent.getService(
            this,
            2,
            Intent(this, FocusTimerService::class.java).setAction(ACTION_SKIP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        // The standard progress bar keeps its place in the notification; the
        // wavy line stays a feature of the in-app ring only.
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(subtitle)
            .setSubText("$percent% · cycle ${cycle.coerceAtLeast(1)}/$cycles · ${tag?.name ?: "Focus"}")
            .setSmallIcon(R.drawable.ic_stat_focus)
            .setContentIntent(openIntent)
            .setProgress(total, elapsed, false)
            .setWhen(0L)
            .setShowWhen(false)
            .setOngoing(timer?.running == true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_STOPWATCH)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .addAction(R.drawable.ic_stat_pause, "Pause", toggleIntent)
            .addAction(R.drawable.ic_stat_skip, "Skip", skipIntent)
            .build()
    }

    private fun createTimerChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Focus timer",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "The live countdown while a focus session or break is running"
            setShowBadge(false)
            setSound(null, null)
            enableVibration(false)
        }
        manager.createNotificationChannel(channel)
    }

    override fun onDestroy() {
        screenReceiver?.let { runCatching { unregisterReceiver(it) } }
        screenReceiver = null
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        const val CHANNEL_ID = "focus_timer"
        const val NOTIFICATION_ID = 1001
        const val ACTION_START = "com.gritnative.app.timer.START"
        const val ACTION_STOP = "com.gritnative.app.timer.STOP"
        const val ACTION_TOGGLE = "com.gritnative.app.timer.TOGGLE"
        const val ACTION_SKIP = "com.gritnative.app.timer.SKIP"

        fun start(context: Context) {
            val intent = Intent(context, FocusTimerService::class.java).setAction(ACTION_START)
            runCatching {
                ContextCompat.startForegroundService(context, intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, FocusTimerService::class.java).setAction(ACTION_STOP)
            runCatching { context.startService(intent) }
        }
    }
}
