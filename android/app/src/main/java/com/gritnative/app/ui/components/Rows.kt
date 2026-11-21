package com.gritnative.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gritnative.app.data.FocusSession
import com.gritnative.app.data.Store
import com.gritnative.app.data.Task
import com.gritnative.app.data.categoryById
import com.gritnative.app.data.dateKey
import com.gritnative.app.data.formatMinutesShort
import com.gritnative.app.data.formatTimeOfDay
import com.gritnative.app.data.relativeDay
import com.gritnative.app.data.tagById
import com.gritnative.app.data.taskSubtaskProgress
import com.gritnative.app.ui.theme.Grit
import com.gritnative.app.ui.theme.Symbol
import com.gritnative.app.ui.theme.categoryColors
import com.gritnative.app.ui.theme.lerpColor
import com.gritnative.app.ui.theme.mostReadable
import com.gritnative.app.ui.theme.tagColors
import com.gritnative.app.data.StoreRepository

/** One task, with its subtasks folded underneath and its due date kept honest. */
@Composable
fun TaskRow(
    store: Store,
    task: Task,
    onEdit: () -> Unit,
    modifier: Modifier = Modifier,
    onToggle: () -> Unit = { StoreRepository.toggleTask(task.id) }
) {
    var expanded by remember(task.id) { mutableStateOf(false) }
    val category = store.categoryById(task.categoryId)
    val taskTag = store.tagById(task.taskTag)
    val accent = if (taskTag != null) tagColors(taskTag).accent else categoryColors(category).accent
    val container = if (taskTag != null) tagColors(taskTag).container else categoryColors(category).container
    val today = dateKey()
    val overdue = !task.done && task.due.isNotBlank() && task.due < today
    val dueToday = task.due == today
    val priority = task.priority.coerceIn(0, 2)
    val doneColor = mostReadable(accent, listOf(Color.White, Color(0xFF17130F)))
    val cardColor by animateColorAsState(
        targetValue = if (task.done) {
            lerpColor(MaterialTheme.colorScheme.surfaceContainerLow, Grit.colors.field, 0.45f)
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        },
        animationSpec = tween(360),
        label = "task-card-color"
    )
    val toggleColor by animateColorAsState(
        targetValue = if (task.done) accent else Color.Transparent,
        animationSpec = tween(280),
        label = "task-toggle-color"
    )
    val checkScale by animateFloatAsState(
        targetValue = if (task.done) 1f else 0.72f,
        animationSpec = spring(dampingRatio = 0.45f, stiffness = 460f),
        label = "task-check-scale"
    )

    Surface(
        modifier = modifier.fillMaxWidth().animateContentSize(),
        shape = RoundedCornerShape(22.dp),
        color = cardColor,
        border = if (task.done) null else BorderStroke(1.dp, Grit.colors.outline.copy(alpha = 0.28f))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(11.dp)
            ) {
                Surface(
                    onClick = onToggle,
                    modifier = Modifier
                        .size(30.dp)
                        .scale(checkScale),
                    shape = if (task.done) RoundedCornerShape(8.dp) else CircleShape,
                    color = toggleColor,
                    border = if (task.done) null else BorderStroke(2.dp, Grit.colors.outlineStrong)
                ) {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        if (task.done) {
                            Symbol("check", size = 18.dp, color = doneColor, filled = true)
                        }
                    }
                }
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = task.title,
                        color = if (task.done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                        fontSize = 14.sp,
                        lineHeight = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        textDecoration = if (task.done) TextDecoration.LineThrough else null
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (taskTag != null) {
                            Text(
                                text = taskTag.name,
                                color = accent,
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1
                            )
                        }
                        if (category.name.isNotBlank()) {
                            Text(
                                text = category.name,
                                color = if (taskTag != null) Grit.colors.subtext else accent,
                                fontSize = 10.5.sp,
                                maxLines = 1
                            )
                        }
                        if (task.due.isNotBlank()) {
                            Text(
                                text = if (overdue) "Overdue · ${relativeDay(task.due)}"
                                else if (dueToday) "Today"
                                else relativeDay(task.due),
                                color = if (overdue) MaterialTheme.colorScheme.error else Grit.colors.subtext,
                                fontSize = 10.5.sp,
                                fontWeight = if (overdue || dueToday) FontWeight.SemiBold else FontWeight.Normal,
                                maxLines = 1
                            )
                        }
                        if (priority > 0) {
                            Text(
                                text = if (priority == 2) "!!" else "!",
                                color = if (priority == 2) MaterialTheme.colorScheme.error
                                else MaterialTheme.colorScheme.tertiary,
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
                if (task.subtasks.isNotEmpty()) {
                    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = "${task.subtasks.count { it.done }}/${task.subtasks.size}",
                            color = Grit.colors.subtext,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Surface(
                            onClick = { expanded = !expanded },
                            modifier = Modifier.size(26.dp),
                            shape = CircleShape,
                            color = Grit.colors.field
                        ) {
                            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                Symbol(
                                    if (expanded) "expand_less" else "expand_more",
                                    size = 16.dp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
                RoundIconButton(
                    symbol = "edit",
                    onClick = onEdit,
                    size = 32.dp,
                    iconSize = 16.dp
                )
            }
            if (expanded && task.subtasks.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 40.dp, top = 9.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    task.subtasks.forEach { subtask ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Surface(
                                onClick = { StoreRepository.toggleSubtask(task.id, subtask.id) },
                                modifier = Modifier.size(22.dp),
                                shape = if (subtask.done) RoundedCornerShape(6.dp) else CircleShape,
                                color = if (subtask.done) accent else Color.Transparent,
                                border = if (subtask.done) null else BorderStroke(1.dp, Grit.colors.outlineStrong)
                            ) {
                                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                    if (subtask.done) {
                                        Symbol("check", size = 13.dp, color = doneColor, filled = true)
                                    }
                                }
                            }
                            Text(
                                text = subtask.title,
                                modifier = Modifier.weight(1f),
                                color = if (subtask.done) Grit.colors.subtext else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 12.sp,
                                textDecoration = if (subtask.done) TextDecoration.LineThrough else null,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            RoundIconButton(
                                symbol = "close",
                                onClick = { StoreRepository.removeSubtask(task.id, subtask.id) },
                                size = 22.dp,
                                iconSize = 12.dp,
                                container = Color.Transparent,
                                content = Grit.colors.subtext
                            )
                        }
                    }
                }
            }
        }
    }
}

/** A finished focus or break session, with the tag that earned it. */
@Composable
fun SessionRow(
    store: Store,
    session: FocusSession,
    modifier: Modifier = Modifier,
    showDate: Boolean = true,
    onDelete: (() -> Unit)? = { StoreRepository.deleteSession(session.id) }
) {
    val tag = store.tagById(session.tagId)
    val isFocus = session.phase == "focus"
    val colors = tagColors(tag)
    val accent = if (isFocus) colors.accent else MaterialTheme.colorScheme.onSurfaceVariant
    val container = if (isFocus) colors.container else MaterialTheme.colorScheme.surfaceContainerHighest
    val onContainer = if (isFocus) colors.onContainer else MaterialTheme.colorScheme.onSurfaceVariant
    val minutes = (session.durationSec / 60.0).toInt().coerceAtLeast(1)
    val title = when {
        isFocus -> tag?.name ?: "Focus"
        session.phase == "longBreak" -> "Long break"
        else -> "Short break"
    }
    val meta = buildList {
        if (showDate) add(relativeDay(com.gritnative.app.data.dateKey(session.endedAt)))
        add(formatTimeOfDay(session.endedAt))
        if (session.note.isNotBlank()) add(session.note)
    }.joinToString(" · ")

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, Grit.colors.outline.copy(alpha = 0.22f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            ShapeBadge(
                symbol = if (isFocus) (tag?.icon ?: "psychiatry") else "coffee",
                shapeName = if (isFocus) (tag?.shape ?: "Cookie7Sided") else "SoftBurst",
                container = container,
                content = onContainer,
                size = 38.dp,
                iconSize = 18.dp
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(
                    text = title,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = meta,
                    color = Grit.colors.subtext,
                    fontSize = 10.5.sp,
                    lineHeight = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                text = formatMinutesShort(minutes),
                color = accent,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
            if (onDelete != null) {
                RoundIconButton(
                    symbol = "delete",
                    onClick = onDelete,
                    size = 28.dp,
                    iconSize = 14.dp,
                    container = Color.Transparent,
                    content = Grit.colors.subtext
                )
            }
        }
    }
}

/** A slim progress row used by goal cards. */
@Composable
fun GoalBar(
    todayMinutes: Int,
    goalMinutes: Int,
    accent: Color,
    modifier: Modifier = Modifier,
    label: String = "Today's goal"
) {
    if (goalMinutes <= 0) return
    val progress = (todayMinutes.toFloat() / goalMinutes.coerceAtLeast(1)).coerceIn(0f, 1f)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(lerpColor(MaterialTheme.colorScheme.surfaceContainerLow, accent, 0.07f), RoundedCornerShape(16.dp))
            .border(1.dp, accent.copy(alpha = 0.14f), RoundedCornerShape(16.dp))
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Symbol("flag", size = 15.dp, color = accent, filled = true)
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold)
        Box(
            modifier = Modifier
                .weight(1f)
                .background(Grit.colors.field, CircleShape)
                .padding(0.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(progress.coerceIn(0.03f, 1f))
                    .background(accent, CircleShape)
                    .padding(vertical = 3.dp)
            )
        }
        Text(
            text = "${formatMinutesShort(todayMinutes)} / ${formatMinutesShort(goalMinutes)}",
            color = accent,
            fontSize = 10.5.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
    }
}

/** Small tappable row for tag task lists. */
@Composable
fun CompactTaskRow(
    task: Task,
    accent: Color,
    onToggle: () -> Unit,
    onEdit: () -> Unit,
    modifier: Modifier = Modifier
) {
    val doneColor = mostReadable(accent, listOf(Color.White, Color(0xFF17130F)))
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onEdit)
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Surface(
            onClick = onToggle,
            modifier = Modifier.size(26.dp),
            shape = if (task.done) RoundedCornerShape(7.dp) else CircleShape,
            color = if (task.done) accent else Color.Transparent,
            border = if (task.done) null else BorderStroke(1.dp, accent.copy(alpha = 0.6f))
        ) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                if (task.done) Symbol("check", size = 14.dp, color = doneColor, filled = true)
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = task.title,
                color = if (task.done) Grit.colors.subtext else MaterialTheme.colorScheme.onSurface,
                fontSize = 12.5.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textDecoration = if (task.done) TextDecoration.LineThrough else null
            )
            if (task.due.isNotBlank()) {
                val overdue = !task.done && task.due < dateKey()
                Text(
                    text = if (overdue) "Overdue · ${relativeDay(task.due)}" else relativeDay(task.due),
                    color = if (overdue) MaterialTheme.colorScheme.error else Grit.colors.subtext,
                    fontSize = 10.sp,
                    maxLines = 1
                )
            }
        }
        RoundIconButton(
            symbol = "edit",
            onClick = onEdit,
            size = 26.dp,
            iconSize = 14.dp,
            container = Color.Transparent,
            content = Grit.colors.subtext
        )
    }
}

/** A section heading with an optional trailing action. */
@Composable
fun CardHeader(title: String, subtitle: String = "", modifier: Modifier = Modifier, trailing: (@Composable () -> Unit)? = null) {
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 14.sp,
                lineHeight = 18.sp,
                fontWeight = FontWeight.SemiBold
            )
            if (subtitle.isNotBlank()) {
                Text(
                    text = subtitle,
                    color = Grit.colors.subtext,
                    fontSize = 10.5.sp,
                    lineHeight = 13.sp
                )
            }
        }
        trailing?.invoke()
    }
}
