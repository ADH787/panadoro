package com.gritnative.app.widgets

import androidx.compose.runtime.Composable
import androidx.glance.GlanceModifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.background
import com.gritnative.app.R
import com.gritnative.app.data.AppSettings
import com.gritnative.app.data.Store
import com.gritnative.app.data.addDays
import com.gritnative.app.data.dateKey
import com.gritnative.app.data.openTaskCount
import com.gritnative.app.data.overdueTaskCount
import com.gritnative.app.data.relativeDay
import com.gritnative.app.data.taskFiltered
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

class TasksWidget : BaseGritWidget() {
    @Composable
    override fun WidgetContent(store: Store, settings: AppSettings, colors: WidgetColors) {
        TasksWidgetContent(store, colors)
    }
}

class TasksWidgetReceiver : GlanceAppWidgetReceiver() {
    // Shared with the updater so refreshes reach the pinned ids.
    override val glanceAppWidget: GlanceAppWidget = GritWidgetHost.tasks
}

@Composable
internal fun TasksWidgetContent(store: Store, colors: WidgetColors) {
    val context = LocalContext.current
    val size = LocalSize.current
    val today = dateKey()
    val due = store.taskFiltered("today", today)
    val shown = if (due.isNotEmpty()) due else store.taskFiltered("open", today)
    val dense = size.height < 150.dp
    // Header, the optional "+N more" line and the footer row share Glance's
    // ten-child Column limit with the task rows.
    val maxRows = when {
        size.height < 215.dp -> 2
        size.height < 280.dp -> 3
        size.height < 350.dp -> 4
        else -> 5
    }.coerceAtMost(if (size.height >= 170.dp) 5 else 7)
    val date = LocalDate.now().format(
        DateTimeFormatter.ofPattern("EEE, d MMM", Locale.getDefault())
    )

    GritWidgetRoot(colors, dense = dense) {
        WidgetHeader(
            icon = R.drawable.ic_widget_tasks,
            title = "Tasks",
            subtitle = if (dense) "" else if (due.isEmpty()) "$date · clear runway" else "$date · ${due.size} due",
            colors = colors,
            trailing = if (store.openTaskCount() > 0 && !(dense && size.width < 180.dp)) "${store.openTaskCount()} open" else null,
            dense = dense
        )
        Spacer(GlanceModifier.height(if (dense) 4.dp else 8.dp))
        if (shown.isEmpty()) {
            Column(
                modifier = GlanceModifier.defaultWeight(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                WidgetText(
                    text = "Nothing waiting.",
                    color = colors.text,
                    size = 15.sp,
                    weight = androidx.glance.text.FontWeight.Bold
                )
                Spacer(GlanceModifier.height(2.dp))
                WidgetText(
                    text = "That is the whole list.",
                    color = colors.subtext,
                    size = 10.5.sp,
                    maxLines = 2
                )
            }
        } else {
            shown.take(maxRows).forEach { task ->
                val priorityColor = when (task.priority) {
                    2 -> colors.error
                    1 -> colors.tertiary
                    else -> colors.outline
                }
                Row(
                    modifier = GlanceModifier
                        .fillMaxWidth()
                        .cornerRadius(10.dp)
                        .clickable(
                            actionRunCallback<ToggleTaskAction>(
                                actionParametersOf(TaskIdKey to task.id)
                            )
                        )
                        .padding(vertical = if (dense) 2.dp else 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = GlanceModifier
                            .width(3.dp)
                            .height(21.dp)
                            .cornerRadius(2.dp)
                            .background(priorityColor)
                    ) { }
                    Spacer(GlanceModifier.width(8.dp))
                    WidgetCheck(false, colors, if (dense) 20.dp else 23.dp)
                    Spacer(GlanceModifier.width(8.dp))
                    Column(modifier = GlanceModifier.defaultWeight()) {
                        WidgetText(
                            text = task.title,
                            color = colors.text,
                            size = if (dense) 10.5.sp else 11.5.sp,
                            weight = androidx.glance.text.FontWeight.Medium,
                            maxLines = if (dense) 1 else 2
                        )
                        val detail = dueDetail(task.due, today)
                        if (!dense && detail.isNotBlank()) {
                            WidgetText(
                                text = detail,
                                color = if (task.due.isNotEmpty() && task.due < today) {
                                    colors.error
                                } else colors.subtext,
                                size = 9.sp
                            )
                        }
                    }
                    if (task.subtasks.isNotEmpty()) {
                        WidgetText(
                            text = "${task.subtasks.count { it.done }}/${task.subtasks.size}",
                            color = colors.subtext,
                            size = 9.sp
                        )
                    }
                }
            }
            if (shown.size > maxRows) {
                WidgetText(
                    text = "+${shown.size - maxRows} more",
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
                    text = "${store.overdueTaskCount(today)} overdue",
                    color = if (store.overdueTaskCount(today) > 0) colors.error else colors.subtext,
                    size = 9.sp,
                    modifier = GlanceModifier.defaultWeight()
                )
                WidgetTextAction(
                    label = "Open",
                    icon = R.drawable.ic_widget_arrow,
                    colors = colors,
                    action = openGritAction(context, "tasks")
                )
            }
        }
    }
}

private fun dueDetail(due: String, today: String): String = when {
    due.isEmpty() -> ""
    due < today -> "Overdue · ${relativeDay(due, today)}"
    due == today -> "Due today"
    due == addDays(today, 1) -> "Tomorrow"
    else -> relativeDay(due, today)
}
