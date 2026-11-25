package com.gritnative.app.ui

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import com.gritnative.app.ui.components.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gritnative.app.data.Store
import com.gritnative.app.data.TimerState
import com.gritnative.app.data.computeSecondsLeft
import com.gritnative.app.data.formatClock
import com.gritnative.app.data.formatMinutesShort
import com.gritnative.app.data.tagById
import com.gritnative.app.timer.AodOverlayWindow
import com.gritnative.app.timer.CompletionAlarm
import com.gritnative.app.ui.components.GritButton
import com.gritnative.app.ui.components.GritButtonStyle
import com.gritnative.app.ui.components.ShapeBadge
import com.gritnative.app.ui.components.tapHaptic
import com.gritnative.app.ui.components.translateText
import com.gritnative.app.ui.theme.mostReadable
import com.gritnative.app.ui.theme.tagColors
import kotlinx.coroutines.delay

/**
 * The always-on display's shared state. MainActivity stamps every touch into
 * [lastInteraction]; the focus stage's button (or the inactivity ticker in
 * AppNav) raises [active], and any touch brings the full app back.
 */
object Aod {
    var active by mutableStateOf(false)
    var lastInteraction by mutableLongStateOf(System.currentTimeMillis())

    fun noteInteraction() {
        lastInteraction = System.currentTimeMillis()
    }

    fun exit() {
        noteInteraction()
        active = false
        // If the system overlay window is up (app in the background or the
        // phone locked), it leaves with the mode.
        AodOverlayWindow.dismiss()
    }

    /**
     * Enters the always-on display and asks for what it needs to survive a
     * locked phone: "display over other apps" so the dim countdown can draw
     * over the lock screen and anything else in the way, and (on Android 14+)
     * full-screen notifications so the screen lights back up when it was
     * turned off mid-session. Missing permissions open their settings page
     * with an explanation — granting them is one tap away, never forced.
     */
    fun request(context: Context) {
        active = true
        noteInteraction()
        val app = context.applicationContext
        // Draw over other apps / the lock screen.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(app)) {
            Toast.makeText(
                app,
                translateText(
                    "Allow \u201CDisplay over other apps\u201D so the countdown can stay on the lock screen"
                ),
                Toast.LENGTH_LONG
            ).show()
            runCatching {
                app.startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:${app.packageName}")
                    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
            return
        }
        // Android 14 gates full-screen intents behind an explicit grant.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val manager = app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (!manager.canUseFullScreenIntent()) {
                Toast.makeText(
                    app,
                    translateText(
                        "Allow \u201CFull screen notifications\u201D so the display wakes when the phone is locked"
                    ),
                    Toast.LENGTH_LONG
                ).show()
                runCatching {
                    app.startActivity(
                        Intent(
                            Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT,
                            Uri.parse("package:${app.packageName}")
                        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }
            }
        }
    }
}

/**
 * The dim countdown that stays on while a session runs. There is no wavy line
 * here on purpose: a static, mostly-black surface is what lets a screen sip
 * power instead of drinking.
 */
@Composable
fun AodOverlay(store: Store, timer: TimerState?) {
    val tag = timer?.let { store.tagById(it.tagId) }
    val colors = tagColors(tag)
    val accent = colors.accent
    val onAccent = mostReadable(accent, listOf(Color.White, Color(0xFF17130F)))
    val phase = timer?.phase ?: "focus"
    val phaseLabel = when (phase) {
        "shortBreak" -> "Short break"
        "longBreak" -> "Long break"
        else -> "Focus"
    }
    val clock = rememberTimerNow(timer)
    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        entered = true
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                detectTapGestures { Aod.exit() }
            }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 26.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(Modifier.scale(if (entered) 1f else 0.94f)) {
                ShapeBadge(
                    symbol = if (phase == "focus") "psychiatry" else "coffee",
                    shapeName = if (phase == "focus") (tag?.shape ?: "Cookie7Sided") else "SoftBurst",
                    container = accent.copy(alpha = 0.85f),
                    content = onAccent,
                    size = 42.dp,
                    iconSize = 21.dp
                )
            }
            Spacer(Modifier.height(16.dp))
            Text(
                text = tag?.name ?: phaseLabel,
                color = Color.White.copy(alpha = 0.62f),
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = formatClock(computeSecondsLeft(timer, clock)),
                color = accent,
                fontSize = 72.sp,
                lineHeight = 78.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
            Text(
                text = "$phaseLabel · still running",
                color = Color.White.copy(alpha = 0.45f),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
        }
        Text(
            text = "Tap anywhere to wake · the screen stays on while the session runs",
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(bottom = 30.dp),
            color = Color.White.copy(alpha = 0.34f),
            fontSize = 10.5.sp,
            lineHeight = 14.sp,
            textAlign = TextAlign.Center
        )
    }
}

/**
 * The full-screen alarm that owns the moment a phase ends while the app is on
 * screen. The notification stays silent behind it; this ring is the alarm,
 * and the button (or any touch) stops it.
 */
@Composable
fun CompletionAlarmOverlay(
    payload: CompletionAlarm.Payload,
    onDismiss: () -> Unit
) {
    // The overlay calms itself when the ring has run its course.
    var entered by remember(payload.id) { mutableStateOf(false) }
    LaunchedEffect(payload.id) {
        entered = true
        delay(payload.ringMs)
        onDismiss()
    }
    val fadeInAlpha by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (entered) 1f else 0f,
        animationSpec = tween(260),
        label = "alarm-overlay-alpha"
    )
    val colors = tagColors(payload.tag)
    val accent = colors.accent
    val onAccent = mostReadable(accent, listOf(Color.White, Color(0xFF17130F)))
    val title = if (payload.focusCompleted) {
        "Focus complete · ${payload.tagName}"
    } else {
        "Break complete"
    }
    val nextLabel = when (payload.nextPhase) {
        "longBreak" -> "Long break"
        "shortBreak" -> "Short break"
        else -> "Next focus"
    }
    val subtitle = if (payload.focusCompleted && payload.minutes > 0) {
        "${formatMinutesShort(payload.minutes)} banked · the $nextLabel has started"
    } else {
        "The $nextLabel is ready when you are"
    }
    val pulse = rememberInfiniteTransition(label = "alarm-pulse")
    val pulseScale by pulse.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(650),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alarm-pulse-value"
    )
    val view = androidx.compose.ui.platform.LocalView.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .alpha(fadeInAlpha)
            .background(Color.Black.copy(alpha = 0.9f))
            .clickable(
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                indication = null
            ) { onDismiss() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 26.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(Modifier.scale(pulseScale)) {
                ShapeBadge(
                    symbol = if (payload.focusCompleted) "celebration" else "alarm",
                    shapeName = "SoftBurst",
                    container = accent,
                    content = onAccent,
                    size = 92.dp,
                    iconSize = 46.dp,
                    filled = true
                )
            }
            Spacer(Modifier.height(22.dp))
            Text(
                text = title,
                color = Color.White,
                fontSize = 24.sp,
                lineHeight = 29.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 2
            )
            Spacer(Modifier.height(7.dp))
            Text(
                text = subtitle,
                color = Color.White.copy(alpha = 0.72f),
                fontSize = 13.sp,
                lineHeight = 17.sp,
                textAlign = TextAlign.Center,
                maxLines = 2
            )
            Spacer(Modifier.height(30.dp))
            GritButton(
                label = "Stop alarm",
                onClick = {
                    tapHaptic(view, strong = true)
                    onDismiss()
                },
                symbol = "close",
                style = GritButtonStyle.FILLED,
                modifier = Modifier.fillMaxWidth(0.72f)
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = "Tap anywhere to stop the ring",
                color = Color.White.copy(alpha = 0.4f),
                fontSize = 10.5.sp
            )
        }
    }
}

/**
 * Wired into AppNav: fades the AOD in and out smoothly, and never shows it
 * while the completion alarm is on screen or the phase is not running.
 */
@Composable
fun AodLayer(store: Store, timer: TimerState?, suppressed: Boolean) {
    val visible = Aod.active && !suppressed && timer?.running == true
    androidx.compose.animation.AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(700)),
        exit = fadeOut(tween(420))
    ) {
        AodOverlay(store = store, timer = timer)
    }
}
