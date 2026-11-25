package com.gritnative.app.widgets

import androidx.compose.runtime.Composable
import androidx.glance.GlanceModifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import com.gritnative.app.R
import com.gritnative.app.data.AppSettings
import com.gritnative.app.data.Store
import com.gritnative.app.data.currentStreak
import com.gritnative.app.data.dateKey
import com.gritnative.app.data.habitDone
import com.gritnative.app.data.habitValue
import com.gritnative.app.data.todayHabits
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

class TodayWidget : BaseGritWidget() {
    @Composable
    override fun WidgetContent(store: Store, settings: AppSettings, colors: WidgetColors) {
        TodayWidgetContent(store, colors)
    }
}

class TodayWidgetReceiver : GlanceAppWidgetReceiver() {
    // Shared with the updater so refreshes reach the pinned ids.
    override val glanceAppWidget: GlanceAppWidget = GritWidgetHost.today
}

@Composable
internal fun TodayWidgetContent(store: Store, colors: WidgetColors) {
    val context = LocalContext.current
    val size = LocalSize.current
    val today = dateKey()
    val habits = store.todayHabits
    val done = habits.count { store.habitDone(it, today) }
    val progress = if (habits.isEmpty()) 0f else done.toFloat() / habits.size
    val dense = size.height < 150.dp
    val compact = size.width < 210.dp || dense
    // Glance caps a Column at ten children. Header, progress and the footer
    // already claim slots, and "+N more" needs one too, so keep the list short
    // enough that a large tile cannot crash its own composition.
    val maxRows = when {
        size.height < 215.dp -> 2
        size.height < 280.dp -> 3
        size.height < 350.dp -> 4
        else -> 5
    }.coerceAtMost(if (size.height >= 170.dp) 3 else 5)
    val date = LocalDate.now().format(
        DateTimeFormatter.ofPattern("EEE, d MMM", Locale.getDefault())
    )

    GritWidgetRoot(colors, dense = dense) {
        WidgetHeader(
            icon = R.drawable.ic_widget_sun,
            title = "Today",
            subtitle = if (dense) "" else date,
            colors = colors,
            trailing = if (habits.isEmpty()) null else "$done/${habits.size}",
            dense = dense
        )
        Spacer(GlanceModifier.height(if (dense) 4.dp else 8.dp))
        WidgetProgress(progress, colors)
        Spacer(GlanceModifier.height(if (dense) 4.dp else 7.dp))
        if (habits.isEmpty()) {
            Column(
                modifier = GlanceModifier.defaultWeight(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                WidgetText(
                    text = "A clear day.",
                    color = colors.text,
                    size = 15.sp,
                    weight = androidx.glance.text.FontWeight.Bold
                )
                Spacer(GlanceModifier.height(2.dp))
                WidgetText(
                    text = "No habits scheduled today.",
                    color = colors.subtext,
                    size = 10.5.sp,
                    maxLines = 2
                )
            }
        } else {
            habits.take(maxRows).forEach { habit ->
                val isDone = store.habitDone(habit, today)
                val streak = store.currentStreak(habit, today)
                Row(
                    modifier = GlanceModifier
                        .fillMaxWidth()
                        .cornerRadius(10.dp)
                        .clickable(
                            actionRunCallback<ToggleHabitAction>(
                                actionParametersOf(HabitIdKey to habit.id)
                            )
                        )
                        .padding(vertical = if (dense) 2.dp else 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    WidgetCheck(isDone, colors, if (dense) 20.dp else 23.dp)
                    Spacer(GlanceModifier.width(8.dp))
                    Column(modifier = GlanceModifier.defaultWeight()) {
                        WidgetText(
                            text = habit.name,
                            color = if (isDone) colors.subtext else colors.text,
                            size = if (dense) 10.5.sp else 11.5.sp,
                            weight = if (isDone) androidx.glance.text.FontWeight.Normal
                            else androidx.glance.text.FontWeight.Medium,
                            maxLines = if (dense) 1 else 2
                        )
                        if (!compact && streak > 0) {
                            WidgetText(
                                text = "$streak day streak",
                                color = colors.subtext,
                                size = 9.sp
                            )
                        }
                    }
                    if (habit.target > 1) {
                        WidgetText(
                            text = "${store.habitValue(habit.id, today)}/${habit.target}",
                            color = colors.subtext,
                            size = 9.5.sp,
                            weight = androidx.glance.text.FontWeight.Medium
                        )
                    } else if (isDone) {
                        WidgetText(
                            text = "Done",
                            color = colors.success,
                            size = 9.5.sp,
                            weight = androidx.glance.text.FontWeight.Medium
                        )
                    }
                }
            }
            if (habits.size > maxRows) {
                WidgetText(
                    text = "+${habits.size - maxRows} more",
                    color = colors.subtext,
                    size = 9.sp
                )
            }
        }
        if (size.height >= 170.dp) {
            Spacer(GlanceModifier.defaultWeight())
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                WidgetText(
                    text = if (habits.isNotEmpty() && done == habits.size) {
                        "All clear. Nicely done."
                    } else {
                        "Tap a habit to check in"
                    },
                    color = colors.subtext,
                    size = 9.sp,
                    modifier = GlanceModifier.defaultWeight(),
                    maxLines = 1
                )
                WidgetTextAction(
                    label = "Open",
                    icon = R.drawable.ic_widget_arrow,
                    colors = colors,
                    action = openGritAction(context, "today")
                )
            }
        }
    }
}
