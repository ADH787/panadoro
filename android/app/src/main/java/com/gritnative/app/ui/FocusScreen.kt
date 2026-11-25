package com.gritnative.app.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import com.gritnative.app.ui.components.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gritnative.app.data.Store
import com.gritnative.app.data.StoreRepository
import com.gritnative.app.data.TaskDraft
import com.gritnative.app.data.Tag
import com.gritnative.app.data.TimerState
import com.gritnative.app.data.activeCategories
import com.gritnative.app.data.activeTag
import com.gritnative.app.data.activeTags
import com.gritnative.app.data.computeSecondsLeft
import com.gritnative.app.data.dateKey
import com.gritnative.app.data.elapsedSecondsExact
import com.gritnative.app.data.formatClock
import com.gritnative.app.data.formatDurationShort
import com.gritnative.app.data.formatMinutesShort
import com.gritnative.app.data.focusMinutesInRange
import com.gritnative.app.data.recentSessions
import com.gritnative.app.data.tagGoalProgress
import com.gritnative.app.data.tagStats
import com.gritnative.app.data.tagTodayMinutes
import com.gritnative.app.data.todayFocusMinutes
import com.gritnative.app.data.tasksForTag
import com.gritnative.app.ui.components.CardHeader
import com.gritnative.app.ui.components.CompactTaskRow
import com.gritnative.app.ui.components.GoalBar
import com.gritnative.app.ui.components.GritButton
import com.gritnative.app.ui.components.GritButtonStyle
import com.gritnative.app.ui.components.GritField
import com.gritnative.app.ui.components.GritPanel
import com.gritnative.app.ui.components.LiveWavyRing
import com.gritnative.app.ui.components.ProgressLine
import com.gritnative.app.ui.components.RoundIconButton
import com.gritnative.app.ui.components.SessionRow
import com.gritnative.app.ui.components.ShapeBadge
import com.gritnative.app.ui.components.StaggeredEntry
import com.gritnative.app.ui.components.TagChip
import com.gritnative.app.ui.components.tapHaptic
import com.gritnative.app.ui.theme.Grit
import com.gritnative.app.ui.theme.Symbol
import com.gritnative.app.ui.theme.gritShape
import com.gritnative.app.ui.theme.lerpColor
import com.gritnative.app.ui.theme.mostReadable
import com.gritnative.app.ui.theme.tagColors

/**
 * Focus is the room the timer lives in. The stage takes the selected tag's
 * colour, the ring drains as the phase runs, and every finished session is
 * already a row in the store — there is no second timer to drift.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FocusScreen(
    store: Store,
    onNewTag: () -> Unit,
    onEditTag: (String) -> Unit,
    onDeleteTag: (String) -> Unit,
    onEditTask: (String) -> Unit
) {
    val tags = store.activeTags
    val tag = store.activeTag
    val colors = tagColors(tag)
    val timer = store.timer
    val running = timer?.running == true
    val onBreak = timer != null && timer.phase != "focus"
    val duration = timer?.durationSec?.coerceAtLeast(1) ?: (tag?.focusMinutes ?: 25) * 60
    val phaseLabel = when (timer?.phase) {
        "shortBreak" -> "Short break"
        "longBreak" -> "Long break"
        else -> "Focus"
    }
    val phaseIcon = if (onBreak) "coffee" else "psychiatry"
    val goalMinutes = (tag?.dailyGoalMinutes ?: 0).coerceAtLeast(0)
    val tagTasks = remember(store.tasks, tag?.id) {
        if (tag == null) emptyList() else store.tasksForTag(tag.id)
    }
    val weekStats = remember(store.focusSessions, store.tags) { store.tagStats(7) }
    val weekMax = (weekStats.maxOfOrNull { it.seconds } ?: 0L).coerceAtLeast(1L)
    val weekMinutes = store.focusMinutesInRange(7)
    val recent = store.recentSessions(5)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 112.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (tag != null && goalMinutes > 0) {
            item(key = "goal") {
                StaggeredEntry(0) {
                    GoalBar(
                        todayMinutes = store.tagTodayMinutes(tag.id),
                        goalMinutes = goalMinutes,
                        accent = colors.accent
                    )
                }
            }
        }

        item(key = "stage") {
            StaggeredEntry(1) {
            FocusStage(
                tag = tag,
                tags = tags,
                timer = timer,
                running = running,
                onBreak = onBreak,
                phaseLabel = phaseLabel,
                phaseIcon = phaseIcon,
                duration = duration,
                onNewTag = onNewTag,
                onEditTag = onEditTag,
                onDeleteTag = onDeleteTag,
                progressAt = { clock ->
                    val value = timer
                    if (value == null) {
                        0f
                    } else {
                        val total = value.durationSec.coerceAtLeast(1)
                        (1.0 - elapsedSecondsExact(value, clock) / total).toFloat().coerceIn(0f, 1f)
                    }
                },
                paused = timer?.accumulatedSec?.let { it > 0.5 } == true && !running,
                cycles = (tag?.cycles ?: 4).coerceAtLeast(1),
                cycle = timer?.cycle ?: 0
            )
            }
        }

        item(key = "note") {
            StaggeredEntry(2) {
                SessionNote(timer?.note.orEmpty())
            }
        }

        if (tag != null) {
            item(key = "todos") {
                StaggeredEntry(2) {
                    TagTodoCard(
                        tag = tag,
                        tasks = tagTasks,
                        onEditTask = onEditTask
                    )
                }
            }
        }

        item(key = "week") {
            StaggeredEntry(3) {
            GritPanel {
                CardHeader(
                    title = "This week, by tag",
                    trailing = {
                        Text(
                            text = formatMinutesShort(weekMinutes),
                            color = Grit.colors.subtext,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                )
                Spacer(Modifier.height(8.dp))
                if (weekStats.isEmpty()) {
                    Text(
                        text = "Finish a focus session and its tag will appear here.",
                        color = Grit.colors.subtext,
                        fontSize = 11.5.sp,
                        lineHeight = 16.sp
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                        weekStats.forEach { stat ->
                            val tagObject = stat.tag
                            val statColors = tagColors(tagObject)
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(7.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(18.dp)
                                            .rotate(6f)
                                            .background(
                                                statColors.accent,
                                                gritShape(tagObject?.shape ?: "Cookie7Sided")
                                            )
                                    )
                                    Text(
                                        text = stat.name,
                                        modifier = Modifier.weight(1f),
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = formatMinutesShort((stat.seconds / 60.0).toInt()),
                                        color = statColors.accent,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                ProgressLine(
                                    progress = stat.seconds.toFloat() / weekMax.toFloat(),
                                    color = statColors.accent
                                )
                            }
                        }
                    }
                }
            }
            }
        }

        item(key = "recent") {
            StaggeredEntry(4) {
            GritPanel {
                CardHeader(
                    title = "Recent sessions",
                    trailing = {
                        Text(
                            text = formatMinutesShort(store.todayFocusMinutes()),
                            color = Grit.colors.subtext,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                )
                Spacer(Modifier.height(8.dp))
                if (recent.isEmpty()) {
                    Text(
                        text = "No sessions yet. The first one is the hardest.",
                        color = Grit.colors.subtext,
                        fontSize = 11.5.sp,
                        lineHeight = 16.sp
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        recent.forEach { session ->
                            SessionRow(store = store, session = session)
                        }
                    }
                }
            }
            }
        }
    }
}

@Composable
private fun FocusStage(
    tag: Tag?,
    tags: List<Tag>,
    timer: TimerState?,
    running: Boolean,
    onBreak: Boolean,
    phaseLabel: String,
    phaseIcon: String,
    duration: Int,
    onNewTag: () -> Unit,
    onEditTag: (String) -> Unit,
    onDeleteTag: (String) -> Unit,
    progressAt: (Long) -> Float,
    paused: Boolean,
    cycles: Int,
    cycle: Int
) {
    val colors = tagColors(tag)
    val onContainer = colors.onContainer
    val accent = colors.accent
    val shape = RoundedCornerShape(30.dp)
    val view = LocalView.current
    var tagsOpen by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.container)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ShapeBadge(
                    symbol = phaseIcon,
                    shapeName = if (onBreak) "SoftBurst" else (tag?.shape ?: "Cookie7Sided"),
                    container = accent,
                    content = mostReadable(accent, listOf(Color.White, Color(0xFF17130F))),
                    size = 34.dp,
                    iconSize = 17.dp
                )
                Text(
                    text = phaseLabel,
                    color = onContainer,
                    fontSize = 19.sp,
                    lineHeight = 23.sp,
                    fontWeight = FontWeight.SemiBold
                )
                if (!onBreak && tag != null) {
                    Text(
                        text = "· ${tag.name}",
                        color = onContainer.copy(alpha = 0.7f),
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (!onBreak) {
                    // The tag shelf stays folded away until this little arrow
                    // is tapped, so the stage opens with just the active tag.
                    RoundIconButton(
                        symbol = if (tagsOpen) "expand_less" else "expand_more",
                        onClick = { tagsOpen = !tagsOpen },
                        size = 28.dp,
                        iconSize = 16.dp,
                        container = onContainer.copy(alpha = 0.12f),
                        content = onContainer
                    )
                }
                Spacer(Modifier.weight(1f))
                // The always-on toggle lives beside the phase, so the controls
                // row below stays symmetric and play stays dead center.
                RoundIconButton(
                    symbol = "bedtime",
                    onClick = {
                        tapHaptic(view)
                        Aod.request(context = view.context)
                    },
                    enabled = running,
                    size = 34.dp,
                    iconSize = 17.dp,
                    container = onContainer.copy(alpha = 0.12f),
                    content = onContainer
                )
            }

            AnimatedVisibility(
                visible = tagsOpen,
                enter = fadeIn(tween(180)) + expandVertically(tween(220)),
                exit = fadeOut(tween(120)) + shrinkVertically(tween(160))
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        tags.forEach { item ->
                            TagChip(
                                tag = item,
                                selected = item.id == tag?.id,
                                onClick = {
                                    StoreRepository.selectTag(item.id)
                                    tagsOpen = false
                                },
                                enabled = !running
                            )
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        RoundIconButton(
                            symbol = "add",
                            onClick = onNewTag,
                            size = 36.dp,
                            iconSize = 17.dp,
                            container = onContainer.copy(alpha = 0.12f),
                            content = onContainer
                        )
                        if (tag != null) {
                            RoundIconButton(
                                symbol = "tune",
                                onClick = { onEditTag(tag.id) },
                                size = 36.dp,
                                iconSize = 17.dp,
                                container = onContainer.copy(alpha = 0.12f),
                                content = onContainer
                            )
                        }
                        if (tags.size > 1 && tag != null) {
                            RoundIconButton(
                                symbol = "delete",
                                onClick = { onDeleteTag(tag.id) },
                                size = 36.dp,
                                iconSize = 17.dp,
                                container = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.65f),
                                content = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }

            // Keep the decorative shape inside the ring's inner circle so its
            // points never poke over the countdown's edge.
            Box(
                modifier = Modifier
                    .widthIn(max = 324.dp)
                    .fillMaxWidth()
                    .aspectRatio(1f),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(206.dp)
                        .rotate(18f)
                        .background(
                            accent.copy(alpha = 0.10f),
                            gritShape(tag?.shape ?: "Cookie7Sided")
                        )
                )
                LiveWavyRing(
                    progressAt = progressAt,
                    running = running,
                    modifier = Modifier.size(260.dp),
                    color = accent,
                    track = onContainer.copy(alpha = 0.16f),
                    strokeWidth = 11.dp,
                    amplitude = 3.5.dp,
                    wavy = running
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        // Only this clock recomposes on every tick; the rest
                        // of the stage (and the screen) stays put.
                        StageClock(timer = timer, color = onContainer)
                        Text(
                            text = when {
                                running -> "of ${formatDurationShort((duration / 60.0).toInt())}"
                                paused -> "Paused"
                                else -> "Ready"
                            },
                            color = onContainer.copy(alpha = 0.75f),
                            fontSize = 12.5.sp,
                            lineHeight = 16.sp
                        )
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                repeat(cycles) { index ->
                    val done = index < cycle || (index == cycle && onBreak)
                    val current = index == cycle
                    Box(
                        modifier = Modifier
                            .width(if (current) 34.dp else 10.dp)
                            .height(10.dp)
                            .background(
                                when {
                                    done -> accent
                                    current -> accent.copy(alpha = 0.45f)
                                    else -> onContainer.copy(alpha = 0.16f)
                                },
                                CircleShape
                            )
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                RoundIconButton(
                    symbol = "restart_alt",
                    onClick = {
                        tapHaptic(view)
                        StoreRepository.resetTimer()
                    },
                    size = 50.dp,
                    iconSize = 22.dp,
                    container = onContainer.copy(alpha = 0.12f),
                    content = onContainer
                )
                // The play/pause button bounces once on every toggle and the
                // glyph itself spins across the change.
                val buttonPulse = remember { Animatable(1f) }
                LaunchedEffect(running) {
                    buttonPulse.snapTo(0.86f)
                    buttonPulse.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = 380f))
                }
                Surface(
                    onClick = {
                        // The press always lands with a tick; starting the
                        // phase gets the firmer confirmation.
                        tapHaptic(view, strong = !running)
                        StoreRepository.ensureTimer()
                        StoreRepository.toggleTimer()
                    },
                    modifier = Modifier
                        .size(78.dp)
                        .scale(buttonPulse.value),
                    shape = CircleShape,
                    color = accent,
                    contentColor = mostReadable(accent, listOf(Color.White, Color(0xFF17130F)))
                ) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        // The glyph spins back into place as it swaps between
                        // the pause bars and the play triangle.
                        val glyphSpin = remember { Animatable(0f) }
                        LaunchedEffect(running) {
                            glyphSpin.snapTo(if (running) 70f else -70f)
                            glyphSpin.animateTo(0f, spring(dampingRatio = 0.6f, stiffness = 380f))
                        }
                        Box(Modifier.rotate(glyphSpin.value)) {
                            AnimatedContent(
                                targetState = running,
                                transitionSpec = {
                                    (
                                        scaleIn(
                                            initialScale = 0.4f,
                                            animationSpec = spring(dampingRatio = 0.55f, stiffness = 380f)
                                        ) + fadeIn(tween(160))
                                        ).togetherWith(
                                        scaleOut(targetScale = 0.4f, animationSpec = tween(150)) +
                                            fadeOut(tween(90))
                                    )
                                },
                                label = "play-pause"
                            ) { isRunning ->
                                Symbol(
                                    if (isRunning) "pause" else "play_arrow",
                                    size = 34.dp,
                                    filled = true,
                                    color = mostReadable(accent, listOf(Color.White, Color(0xFF17130F)))
                                )
                            }
                        }
                    }
                }
                RoundIconButton(
                    symbol = "skip_next",
                    onClick = {
                        tapHaptic(view)
                        StoreRepository.skipTimer()
                    },
                    size = 50.dp,
                    iconSize = 22.dp,
                    container = onContainer.copy(alpha = 0.12f),
                    content = onContainer
                )
            }

            if (tag != null) {
                Text(
                    text = "${formatMinutesShort(tag.focusMinutes)} focus · " +
                        "${formatMinutesShort(tag.breakMinutes)} break · ${tag.cycles} cycles",
                    color = onContainer.copy(alpha = 0.7f),
                    fontSize = 10.5.sp,
                    lineHeight = 13.sp,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun StageClock(timer: TimerState?, color: Color) {
    val now = rememberTimerNow(timer)
    Text(
        text = formatClock(computeSecondsLeft(timer, now)),
        color = color,
        fontSize = 54.sp,
        lineHeight = 58.sp,
        fontWeight = FontWeight.Bold,
        maxLines = 1
    )
}

@Composable
private fun SessionNote(initial: String) {
    var note by remember(initial) { mutableStateOf(initial) }
    val changed = note != initial

    fun commit() {
        StoreRepository.setTimerNote(note)
    }

    GritField(
        value = note,
        onValueChange = { note = it },
        caption = "Session note",
        placeholder = "What are you working on?",
        symbol = "notes",
        keyboardActions = KeyboardActions(onDone = { commit() }),
        trailing = {
            if (changed) {
                RoundIconButton(
                    symbol = "check",
                    onClick = { commit() },
                    size = 30.dp,
                    iconSize = 15.dp,
                    container = MaterialTheme.colorScheme.primaryContainer,
                    content = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    )
}

@Composable
private fun TagTodoCard(
    tag: Tag,
    tasks: List<com.gritnative.app.data.Task>,
    onEditTask: (String) -> Unit
) {
    val colors = tagColors(tag)
    var draft by remember(tag.id) { mutableStateOf("") }

    fun addTask() {
        val text = draft.trim()
        if (text.isEmpty()) return
        StoreRepository.createTask(
            TaskDraft(
                title = text,
                categoryId = StoreRepository.currentStore.activeCategories.firstOrNull()?.id.orEmpty(),
                due = dateKey(),
                taskTag = tag.id
            )
        )
        draft = ""
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                lerpColor(MaterialTheme.colorScheme.surfaceContainerLow, colors.accent, 0.055f),
                RoundedCornerShape(24.dp)
            )
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(18.dp)
                    .background(colors.accent, gritShape(tag.shape))
            )
            Text(
                text = "To-do · ${tag.name}",
                modifier = Modifier.weight(1f),
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${tasks.count { !it.done }} open",
                color = colors.accent,
                fontSize = 10.5.sp,
                fontWeight = FontWeight.SemiBold
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            GritField(
                value = draft,
                onValueChange = { draft = it },
                caption = "Add a to-do for this tag",
                placeholder = "One small next step",
                symbol = "add_task",
                modifier = Modifier.weight(1f),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { addTask() })
            )
            RoundIconButton(
                symbol = "add",
                onClick = { addTask() },
                enabled = draft.trim().isNotEmpty(),
                size = 44.dp,
                iconSize = 20.dp,
                container = colors.accent,
                content = mostReadable(colors.accent, listOf(Color.White, Color(0xFF17130F)))
            )
        }

        if (tasks.isEmpty()) {
            Text(
                text = "No to-dos for this tag yet.",
                color = Grit.colors.subtext,
                fontSize = 11.sp,
                lineHeight = 14.sp
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                tasks.forEach { task ->
                    CompactTaskRow(
                        task = task,
                        accent = colors.accent,
                        onToggle = { StoreRepository.toggleTask(task.id) },
                        onEdit = { onEditTask(task.id) }
                    )
                }
            }
        }
    }
}
