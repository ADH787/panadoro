package com.gritnative.app.timer

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.gritnative.app.MainActivity
import com.gritnative.app.R
import com.gritnative.app.data.StoreRepository
import com.gritnative.app.data.Tag
import com.gritnative.app.data.computeSecondsLeft
import com.gritnative.app.data.formatClock
import com.gritnative.app.data.tagById
import com.gritnative.app.ui.Aod

/**
 * The always-on display as a system overlay window. While the app itself is
 * not on top — the phone locked, another app in front — and the "display over
 * other apps" permission is granted, this window keeps the dim countdown
 * visible: a full-screen black surface, the tag's colour, and one clock that
 * the timer service refreshes every tick. A tap on it is the same wake-up as
 * tapping the in-app display.
 */
object AodOverlayWindow {
    private val main = Handler(Looper.getMainLooper())
    private var root: FrameLayout? = null
    private var clockView: TextView? = null
    private var tagView: TextView? = null
    private var phaseView: TextView? = null

    val isShowing: Boolean get() = root != null

    private fun dp(context: Context, value: Int): Int =
        TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), context.resources.displayMetrics
        ).toInt()

    private fun sp(context: Context, value: Int): Int =
        TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_SP, value.toFloat(), context.resources.displayMetrics
        ).toInt()

    fun show(context: Context) {
        if (!Settings.canDrawOverlays(context)) return
        if (root != null) return
        val app = context.applicationContext
        main.post {
            if (root != null) return@post
            val windowManager = app.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                screenBrightness = AOD_BRIGHTNESS
            }

            val frame = FrameLayout(app).apply { setBackgroundColor(Color.BLACK) }

            val center = LinearLayout(app).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(dp(app, 26), 0, dp(app, 26), 0)
            }
            val tag = TextView(app).apply {
                gravity = Gravity.CENTER
                setTextColor(0x9EFFFFFF.toInt())
                textSize = spAsFloat(app, 14)
                typeface = Typeface.DEFAULT_BOLD
            }
            val clock = TextView(app).apply {
                gravity = Gravity.CENTER
                setTextColor(Color.WHITE)
                textSize = spAsFloat(app, 72)
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            val phase = TextView(app).apply {
                gravity = Gravity.CENTER
                setTextColor(0x73FFFFFF.toInt())
                textSize = spAsFloat(app, 12)
                typeface = Typeface.DEFAULT_BOLD
            }
            center.addView(tag)
            center.addView(clock, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ))
            center.addView(phase)
            frame.addView(
                center,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
            )

            val hint = TextView(app).apply {
                gravity = Gravity.CENTER
                setTextColor(0x57FFFFFF.toInt())
                textSize = spAsFloat(app, 10.5f.toInt())
                text = "Tap to wake · press power twice to sleep the display"
            }
            frame.addView(
                hint,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    Gravity.BOTTOM
                ).apply { bottomMargin = dp(app, 34) }
            )

            frame.setOnClickListener {
                Aod.exit()
            }

            tagView = tag
            clockView = clock
            phaseView = phase
            root = frame
            runCatching {
                windowManager.addView(frame, params)
            }.onFailure {
                root = null
                tagView = null
                clockView = null
                phaseView = null
            }
            update(app)
        }
    }

    fun dismiss() {
        main.post {
            val frame = root ?: return@post
            root = null
            tagView = null
            clockView = null
            phaseView = null
            runCatching {
                (frame.context.getSystemService(Context.WINDOW_SERVICE) as WindowManager)
                    .removeView(frame)
            }
        }
    }

    /** Refreshes the clock; called by the timer service's loop. */
    fun updateIfShowing(context: Context) {
        if (root == null) return
        main.post { update(context.applicationContext) }
    }

    private fun update(app: Context) {
        if (root == null) return
        val store = StoreRepository.currentStore
        val timer = store.timer ?: return
        val tag = store.tagById(timer.tagId)
        val accent = accentArgb(tag)
        val phase = timer.phase
        val remaining = computeSecondsLeft(timer)
        tagView?.text = tag?.name ?: "Focus"
        clockView?.text = formatClock(remaining)
        clockView?.setTextColor(accent)
        phaseView?.text = when (phase) {
            "shortBreak" -> "Short break · still running"
            "longBreak" -> "Long break · still running"
            else -> "Focus · still running"
        }
    }

    private fun spAsFloat(context: Context, value: Int): Float =
        TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_SP, value.toFloat(), context.resources.displayMetrics
        )

    /** A quiet static tone per accent name; a tag's own hex colour always wins. */
    private fun accentArgb(tag: Tag?): Int {
        val hex = tag?.color?.trim()?.takeIf { it.startsWith("#") }
        if (hex != null) {
            runCatching { return Color.parseColor(hex) }
        }
        return when (tag?.accent ?: "primary") {
            "secondary" -> 0xFFCBB8CE.toInt()
            "tertiary" -> 0xFFEFB8C8.toInt()
            "success" -> 0xFF8BD5AC.toInt()
            "error" -> 0xFFF2B8B5.toInt()
            "outline" -> 0xFFCAC4D0.toInt()
            else -> 0xFFA8C3FF.toInt()
        }
    }

    /** Dim enough to sip power, bright enough to read across the room. */
    private const val AOD_BRIGHTNESS = 0.12f
}

/**
 * Turns the display back on when it goes dark while the always-on countdown
 * is up: a silent full-screen-intent notification, which Android answers by
 * launching the activity over the keyguard with the screen lit — the same
 * mechanism alarm clocks use. Two power presses inside the cooldown are read
 * as "sleep the display" and are left alone.
 */
object AodReWake {
    const val CHANNEL_ID = "aod_wake"
    const val NOTIFICATION_ID = 3003

    private var lastWakeAt = 0L

    fun mayWake(): Boolean =
        System.currentTimeMillis() - lastWakeAt > WAKE_COOLDOWN_MS

    fun wake(context: Context) {
        val app = context.applicationContext
        lastWakeAt = System.currentTimeMillis()
        val manager = app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.getNotificationChannel(CHANNEL_ID) ?: manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "Always-on display",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Lights the dim countdown back up when the phone is locked"
                    setShowBadge(false)
                    setSound(null, null)
                    enableVibration(false)
                }
            )
        }
        val showIntent = PendingIntent.getActivity(
            app,
            0,
            Intent(app, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val builder = NotificationCompat.Builder(app, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_focus)
            .setContentTitle("Panadoro")
            .setContentText("Focus is still running")
            .setSilent(true)
            .setAutoCancel(true)
            .setFullScreenIntent(showIntent, true)
        runCatching { manager.notify(NOTIFICATION_ID, builder.build()) }
    }

    /** Two presses inside this window mean "let the display sleep". */
    private const val WAKE_COOLDOWN_MS = 8_000L
}

/**
 * Registered by the timer service while a session runs: the screen going
 * dark while the always-on display is up brings it straight back.
 */
class AodScreenReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_SCREEN_OFF) return
        val app = context.applicationContext
        if (!Aod.active) return
        if (StoreRepository.currentStore.timer?.running != true) return
        // Android 14 gates the full-screen intent behind an explicit grant;
        // without it the screen cannot be lit back up, so a wake would only
        // post a pointless notification.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val manager = app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (!manager.canUseFullScreenIntent()) return
        }
        if (!AodReWake.mayWake()) return
        AodReWake.wake(app)
    }
}

/** Registers the screen-off listener with the right flags per API level. */
fun registerAodScreenReceiver(context: Context, receiver: AodScreenReceiver) {
    val filter = android.content.IntentFilter(Intent.ACTION_SCREEN_OFF)
    ContextCompat.registerReceiver(
        context,
        receiver,
        filter,
        ContextCompat.RECEIVER_NOT_EXPORTED
    )
}
