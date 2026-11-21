package com.gritnative.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import com.gritnative.app.ui.components.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gritnative.app.data.Store
import com.gritnative.app.data.Task
import com.gritnative.app.data.doneTaskCount
import com.gritnative.app.data.dueTodayTaskCount
import com.gritnative.app.data.openTaskCount
import com.gritnative.app.data.overdueTaskCount
import com.gritnative.app.data.taskFiltered
import com.gritnative.app.ui.components.EmptyState
import com.gritnative.app.ui.components.HeroCard
import com.gritnative.app.ui.components.MiniStatTile
import com.gritnative.app.ui.components.SectionTitle
import com.gritnative.app.ui.components.SegmentOption
import com.gritnative.app.ui.components.SegmentedSelector
import com.gritnative.app.ui.components.StaggeredEntry
import com.gritnative.app.ui.components.TaskRow
import com.gritnative.app.ui.theme.Grit

/**
 * Tasks live beside habits: the same day, the same calm pacing. The four views
 * mirror the quickshell tabs so a sync round-trip changes nothing about how a
 * task feels.
 */
@Composable
fun TasksScreen(
    store: Store,
    onNewTask: () -> Unit,
    onEditTask: (String) -> Unit
) {
    var filter by rememberSaveable { mutableStateOf("open") }
    val tasks = store.taskFiltered(filter)
    val open = store.openTaskCount()
    val dueToday = store.dueTodayTaskCount()
    val done = store.doneTaskCount()
    val overdue = store.overdueTaskCount()
    val showingDone = filter == "done"

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 112.dp),
        verticalArrangement = Arrangement.spacedBy(13.dp)
    ) {
        item(key = "hero") {
            StaggeredEntry(0) {
            HeroCard(
                symbol = if (tasks.isEmpty()) "task_alt" else "checklist",
                title = when {
                    tasks.isEmpty() && showingDone -> "No history yet."
                    tasks.isEmpty() -> "Nothing left in this list."
                    else -> "One thing at a time."
                },
                text = "$open open · $dueToday due today · $done done",
                container = if (showingDone) Grit.colors.successContainer
                else MaterialTheme.colorScheme.primaryContainer,
                onContainer = if (showingDone) Grit.colors.onSuccessContainer
                else MaterialTheme.colorScheme.onPrimaryContainer,
                accent = if (showingDone) Grit.colors.success else MaterialTheme.colorScheme.primary,
                shapeName = "Cookie7Sided",
                trailing = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = tasks.size.toString(),
                            color = if (showingDone) Grit.colors.onSuccessContainer
                            else MaterialTheme.colorScheme.onPrimaryContainer,
                            fontSize = 36.sp,
                            lineHeight = 40.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = if (showingDone) "done" else "showing",
                            color = (if (showingDone) Grit.colors.onSuccessContainer
                            else MaterialTheme.colorScheme.onPrimaryContainer).copy(alpha = 0.8f),
                            fontSize = 10.5.sp,
                            lineHeight = 13.sp,
                            maxLines = 1
                        )
                    }
                }
            )
            }
        }

        item(key = "stats") {
            StaggeredEntry(1) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MiniStatTile(
                    symbol = "radio_button_unchecked",
                    value = open.toString(),
                    label = "Open tasks",
                    shapeName = "Cookie7Sided",
                    modifier = Modifier.weight(1f)
                )
                MiniStatTile(
                    symbol = "today",
                    value = dueToday.toString(),
                    label = "Due today",
                    hint = if (overdue > 0) "$overdue overdue" else "",
                    shapeName = "Cookie9Sided",
                    container = MaterialTheme.colorScheme.tertiaryContainer,
                    onContainer = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier.weight(1f)
                )
                MiniStatTile(
                    symbol = "check_circle",
                    value = done.toString(),
                    label = "Finished",
                    shapeName = "Flower",
                    container = Grit.colors.successContainer,
                    onContainer = Grit.colors.onSuccessContainer,
                    modifier = Modifier.weight(1f)
                )
            }
            }
        }

        item(key = "heading") {
            StaggeredEntry(2) {
                SectionTitle(
                    title = "Tasks",
                    modifier = Modifier.padding(top = 3.dp)
                )
            }
        }

        item(key = "filters") {
            StaggeredEntry(3) {
            SegmentedSelector(
                options = listOf(
                    SegmentOption("open", "Open", "radio_button_unchecked"),
                    SegmentOption("today", "Today", "today"),
                    SegmentOption("upcoming", "Upcoming", "date_range"),
                    SegmentOption("done", "Done", "check_circle")
                ),
                selected = filter,
                onSelect = { filter = it }
            )
            }
        }

        if (tasks.isEmpty()) {
            item(key = "empty") {
                StaggeredEntry(4) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    EmptyState(
                        symbol = if (showingDone) "history" else "task_alt",
                        title = if (showingDone) "No history yet" else "The list is clear",
                        subtitle = if (showingDone) {
                            "Finished tasks will collect here."
                        } else {
                            "Add a task, or enjoy the empty space."
                        },
                        shapeName = "Cookie9Sided"
                    )
                    if (!showingDone) {
                        com.gritnative.app.ui.components.GritButton(
                            label = "New task",
                            onClick = onNewTask,
                            symbol = "add",
                            style = com.gritnative.app.ui.components.GritButtonStyle.TONAL
                        )
                    }
                }
                }
            }
        } else {
            itemsIndexed(tasks, key = { _, task -> task.id }) { index, task: Task ->
                StaggeredEntry(
                    index = index.coerceAtMost(8),
                    modifier = Modifier.animateItem()
                ) {
                    TaskRow(
                        store = store,
                        task = task,
                        onEdit = { onEditTask(task.id) }
                    )
                }
            }
        }

        item(key = "new-task") {
            StaggeredEntry(5) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "Use + to add a task",
                    color = Grit.colors.subtext,
                    fontSize = 11.sp,
                    lineHeight = 14.sp,
                    textAlign = TextAlign.Center
                )
            }
            }
        }
    }
}
