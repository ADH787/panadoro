package com.gritnative.app.widgets

import androidx.compose.runtime.Composable
import androidx.glance.GlanceModifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import com.gritnative.app.R
import com.gritnative.app.data.AppSettings
import com.gritnative.app.data.Store
import com.gritnative.app.data.activeTag
import com.gritnative.app.data.computeSecondsLeft
import com.gritnative.app.data.formatClock
import com.gritnative.app.data.progress
import com.gritnative.app.data.tagById

class FocusWidget : BaseGritWidget() {
    @Composable
    override fun WidgetContent(store: Store, settings: AppSettings, colors: WidgetColors) {
        FocusWidgetContent(store, colors)
    }
}

class FocusWidgetReceiver : GlanceAppWidgetReceiver() {
    // The shared instance matters: updates resolve widget ids through the
    // instance the receiver hosted, so a fresh one here would leave pinned
    // widgets stale.
    override val glanceAppWidget: GlanceAppWidget = GritWidgetHost.focus
}

@Composable
internal fun FocusWidgetContent(store: Store, colors: WidgetColors) {
    val context = LocalContext.current
    val size = LocalSize.current
    val timer = store.timer
    val tag = timer?.let { store.tagById(it.tagId) } ?: store.activeTag
    val running = timer?.running == true
    val secondsLeft = timer?.let { computeSecondsLeft(it) }
        ?: ((tag?.focusMinutes ?: 25) * 60)
    val progress = timer?.progress() ?: 0f
    val phase = when (timer?.phase) {
        "shortBreak" -> "Short break"
        "longBreak" -> "Long break"
        else -> "Focus"
    }
    val dense = size.height < 150.dp
    val compact = size.height < 170.dp || size.width < 190.dp
    val controlsLabel = if (running) "Pause" else if (timer != null && secondsLeft > 0) "Resume" else "Start"
    val controlsIcon = if (running) R.drawable.ic_widget_pause else R.drawable.ic_widget_play
    val cycle = timer?.let {
        "Cycle ${it.cycle.coerceAtLeast(1)}/${tag?.cycles ?: 4}"
    } ?: (tag?.let { "${it.focusMinutes}m focus" } ?: "Ready")

    GritWidgetRoot(colors, dense = dense) {
        WidgetHeader(
            icon = R.drawable.ic_widget_timer,
            title = if (timer == null) "Focus" else phase,
            subtitle = tag?.name ?: "Pick a focus tag",
            colors = colors,
            trailing = if (dense) null else cycle,
            dense = dense
        )
        Spacer(GlanceModifier.height(if (dense) 3.dp else if (compact) 6.dp else 10.dp))
        WidgetText(
            text = formatClock(secondsLeft),
            color = if (running) colors.primary else colors.text,
            size = if (dense) 24.sp else if (compact) 27.sp else 38.sp,
            weight = androidx.glance.text.FontWeight.Bold
        )
        if (!compact) {
            Spacer(GlanceModifier.height(2.dp))
            WidgetText(
                text = when {
                    timer == null -> "Tap start to begin a session"
                    running -> "Running · tap pause when you need a breath"
                    secondsLeft <= 0 -> "Session complete"
                    else -> "Paused · your place is saved"
                },
                color = colors.subtext,
                size = 10.sp,
                maxLines = 2
            )
            Spacer(GlanceModifier.height(8.dp))
            WidgetProgress(progress, colors)
        }
        Spacer(GlanceModifier.height(if (dense) 4.dp else if (compact) 7.dp else 10.dp))
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            WidgetPrimaryAction(
                label = controlsLabel,
                icon = controlsIcon,
                colors = colors,
                action = actionRunCallback<ToggleTimerAction>(),
                height = if (dense) 30.dp else 34.dp
            )
            if (!compact && timer != null) {
                Spacer(GlanceModifier.width(7.dp))
                WidgetTextAction(
                    label = "Skip",
                    icon = R.drawable.ic_widget_skip,
                    colors = colors,
                    action = actionRunCallback<SkipTimerAction>()
                )
            }
            Spacer(GlanceModifier.defaultWeight())
            if (!compact) {
                WidgetTextAction(
                    label = "Open",
                    icon = R.drawable.ic_widget_arrow,
                    colors = colors,
                    action = openGritAction(context, "focus")
                )
            } else {
                Row(
                    modifier = GlanceModifier
                        .height(if (dense) 30.dp else 34.dp)
                        .cornerRadius(if (dense) 15.dp else 17.dp)
                        .background(colors.surfaceHigh)
                        .clickable(openGritAction(context, "focus"))
                        .padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    WidgetIcon(R.drawable.ic_widget_arrow, colors.primary, 15.dp)
                }
            }
        }
        if (!compact) {
            // Padding instead of another Spacer keeps the root Column inside
            // Glance's ten-child limit.
            WidgetText(
                text = tag?.let {
                    "Planned ${it.focusMinutes}m · ${it.breakMinutes}m break · ${it.cycles} cycles"
                } ?: "Focus sessions stay in your stats.",
                color = colors.subtext,
                size = 8.5.sp,
                modifier = GlanceModifier.padding(top = 4.dp),
                maxLines = 1
            )
        }
    }
}
