package com.gritnative.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.gritnative.app.data.Store
import com.gritnative.app.data.Task
import com.gritnative.app.data.activeTasks
import com.gritnative.app.ui.components.EmptyState
import com.gritnative.app.ui.components.HeroBanner
import com.gritnative.app.ui.components.StaggeredEntry
import com.gritnative.app.ui.components.TaskRow
import com.gritnative.app.ui.theme.Grit

/**
 * One straight task list. Finished tasks stay where they are in the store but
 * sink to the bottom with a list animation, so nothing ever disappears.
 */
@Composable
fun TasksScreen(
    store: Store,
    onEditTask: (String) -> Unit
) {
    // Open tasks first, then the finished ones, with the most recently finished
    // task at the very end so a check-off visibly glides down the list.
    val tasks = store.activeTasks.sortedWith(
        compareBy<Task> { it.done }
            .thenBy { if (it.done) it.updatedAt else 0L }
            .thenBy { it.due }
            .thenByDescending { it.priority }
            .thenByDescending { it.createdAt }
    )
    val totalTasks = tasks.size
    val doneTasks = tasks.count { it.done }
    val allDone = totalTasks > 0 && doneTasks == totalTasks
    val title = when {
        tasks.isEmpty() || allDone -> "Nothing left in this list."
        else -> "One thing at a time."
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 112.dp),
        verticalArrangement = Arrangement.spacedBy(13.dp)
    ) {
        item(key = "hero") {
            StaggeredEntry(0) {
                HeroBanner(
                    title = title,
                    done = doneTasks,
                    total = totalTasks,
                    container = if (allDone) Grit.colors.successContainer
                    else MaterialTheme.colorScheme.primaryContainer,
                    onContainer = if (allDone) Grit.colors.onSuccessContainer
                    else MaterialTheme.colorScheme.onPrimaryContainer,
                    accent = if (allDone) Grit.colors.success
                    else MaterialTheme.colorScheme.primary,
                    shapeName = "Cookie7Sided",
                    wavy = totalTasks > 0 && !allDone
                )
            }
        }

        if (tasks.isEmpty()) {
            item(key = "empty") {
                StaggeredEntry(1) {
                    EmptyState(
                        symbol = "task_alt",
                        title = "The list is clear",
                        subtitle = "",
                        shapeName = "Cookie9Sided"
                    )
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

    }
}
