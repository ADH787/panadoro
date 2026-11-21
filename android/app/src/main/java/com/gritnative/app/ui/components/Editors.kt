package com.gritnative.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gritnative.app.data.CategoryDraft
import com.gritnative.app.data.HabitDraft
import com.gritnative.app.data.Store
import com.gritnative.app.data.StoreRepository
import com.gritnative.app.data.Subtask
import com.gritnative.app.data.Tag
import com.gritnative.app.data.TagDraft
import com.gritnative.app.data.TaskDraft
import com.gritnative.app.data.activeCategories
import com.gritnative.app.data.activeTags
import com.gritnative.app.data.addDays
import com.gritnative.app.data.dateKey
import com.gritnative.app.data.formatMinutesShort
import com.gritnative.app.data.newId
import com.gritnative.app.ui.theme.Grit
import com.gritnative.app.ui.theme.GritIconPickerIcons
import com.gritnative.app.ui.theme.Symbol
import com.gritnative.app.ui.theme.accentColors
import com.gritnative.app.ui.theme.isHexColor
import com.gritnative.app.ui.theme.mostReadable
import com.gritnative.app.ui.theme.tagColors
import kotlinx.coroutines.launch

// ── Sheet scaffolding ───────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GritSheet(
    title: String,
    subtitle: String,
    onDismiss: () -> Unit,
    actions: @Composable ColumnScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit
) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val dismiss: () -> Unit = {
        scope.launch {
            runCatching { state.hide() }
            onDismiss()
        }
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = state,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        scrimColor = Color.Black.copy(alpha = 0.46f),
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
        dragHandle = { BottomSheetDefaults.DragHandle(color = Grit.colors.outlineStrong.copy(alpha = 0.6f)) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 26.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            Text(
                text = title,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 24.sp,
                lineHeight = 29.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = subtitle,
                color = Grit.colors.subtext,
                fontSize = 12.5.sp,
                lineHeight = 17.sp
            )
            Spacer(Modifier.height(4.dp))
            content()
            Spacer(Modifier.height(6.dp))
            actions()
            Spacer(Modifier.height(2.dp))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun IconGrid(
    selected: String,
    accent: Color,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        GritIconPickerIcons.forEach { icon ->
            val active = icon == selected
            Surface(
                onClick = { onSelect(icon) },
                modifier = Modifier.size(44.dp),
                shape = if (active) RoundedCornerShape(14.dp) else CircleShape,
                color = if (active) accent else Grit.colors.field,
                contentColor = if (active) mostReadable(accent, listOf(Color.White, Color(0xFF17130F)))
                else MaterialTheme.colorScheme.onSurfaceVariant
            ) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Symbol(
                        icon,
                        size = 21.dp,
                        filled = active,
                        color = if (active) mostReadable(accent, listOf(Color.White, Color(0xFF17130F)))
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DayChips(
    selected: Set<Int>,
    onToggle: (Int) -> Unit,
    modifier: Modifier = Modifier,
    accent: Color = MaterialTheme.colorScheme.primary,
    accentContainer: Color = MaterialTheme.colorScheme.primaryContainer,
    onAccentContainer: Color = MaterialTheme.colorScheme.onPrimaryContainer
) {
    val order = listOf(1, 2, 3, 4, 5, 6, 0)
    // Narrow weekday letters follow the in-app language, not the system one.
    val labels = if (LocalArabic.current) {
        mapOf(1 to "ن", 2 to "ث", 3 to "ر", 4 to "خ", 5 to "ج", 6 to "س", 0 to "ح")
    } else {
        mapOf(1 to "M", 2 to "T", 3 to "W", 4 to "T", 5 to "F", 6 to "S", 0 to "S")
    }
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        order.forEach { day ->
            val active = day in selected
            Surface(
                onClick = { onToggle(day) },
                modifier = Modifier.weight(1f).height(42.dp),
                shape = RoundedCornerShape(13.dp),
                color = if (active) accentContainer else Grit.colors.field,
                contentColor = if (active) onAccentContainer else MaterialTheme.colorScheme.onSurfaceVariant
            ) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text(
                        text = labels[day] ?: "",
                        color = if (active) onAccentContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

// ── Habit editor ────────────────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HabitEditorSheet(
    store: Store,
    habitId: String,
    onDismiss: () -> Unit
) {
    val existing = store.habits.firstOrNull { it.id == habitId && !it.deleted }
    val categories = store.activeCategories
    var draft by remember(habitId) {
        mutableStateOf(
            if (existing != null) {
                HabitDraft(
                    name = existing.name,
                    icon = existing.icon,
                    categoryId = existing.categoryId,
                    days = existing.days,
                    time = existing.time,
                    target = existing.target.coerceAtLeast(1),
                    unit = existing.unit,
                    note = existing.note
                )
            } else {
                HabitDraft(categoryId = categories.firstOrNull()?.id.orEmpty())
            }
        )
    }
    val accent = accentColors(categories.firstOrNull { it.id == draft.categoryId }?.accent).accent
    val accentContainer = accentColors(categories.firstOrNull { it.id == draft.categoryId }?.accent).container
    val onAccent = accentColors(categories.firstOrNull { it.id == draft.categoryId }?.accent).onContainer

    GritSheet(
        title = if (existing != null) "Edit habit" else "New habit",
        subtitle = "Small enough to keep, visible enough to remember",
        onDismiss = onDismiss,
        actions = {
            GritButton(
                label = if (existing != null) "Save habit" else "Create habit",
                onClick = {
                    if (existing != null) StoreRepository.updateHabit(existing.id, draft)
                    else StoreRepository.createHabit(draft)
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth(),
                symbol = "check",
                style = GritButtonStyle.FILLED
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                GritButton(
                    label = "Cancel",
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                    symbol = "close"
                )
                if (existing != null) {
                    GritButton(
                        label = "Delete",
                        onClick = {
                            StoreRepository.deleteHabit(existing.id)
                            onDismiss()
                        },
                        modifier = Modifier.weight(1f),
                        symbol = "delete",
                        danger = true
                    )
                }
            }
        }
    ) {
        GritField(
            value = draft.name,
            onValueChange = { value -> draft = draft.copy(name = value) },
            caption = "Habit",
            placeholder = "Read ten pages",
            symbol = "edit_note",
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences)
        )
        Label("Icon")
        IconGrid(
            selected = draft.icon,
            accent = accent,
            onSelect = { value -> draft = draft.copy(icon = value) }
        )
        Label("Category")
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            categories.forEach { category ->
                val colors = accentColors(category.accent)
                FormChip(
                    label = category.name,
                    selected = draft.categoryId == category.id,
                    onClick = { draft = draft.copy(categoryId = category.id) },
                    symbol = category.icon,
                    accent = colors.accent
                )
            }
        }
        Label("Days")
        DayChips(
            selected = draft.days.toSet(),
            onToggle = { day ->
                val next = draft.days.toMutableSet()
                if (!next.remove(day)) next.add(day)
                if (next.isEmpty()) next.add(day)
                draft = draft.copy(days = next.toList().sorted())
            },
            accent = accent,
            accentContainer = accentContainer,
            onAccentContainer = onAccent
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Grit.colors.field, RoundedCornerShape(16.dp))
                .padding(horizontal = 11.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            ShapeBadge(
                symbol = "flag",
                shapeName = "Cookie4Sided",
                container = accentContainer,
                content = onAccent,
                size = 36.dp,
                iconSize = 18.dp
            )
            Column(Modifier.weight(1f)) {
                Text("Daily target", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    "How many times before it counts as done",
                    fontSize = 10.5.sp,
                    lineHeight = 13.sp,
                    color = Grit.colors.subtext
                )
            }
            Stepper(
                value = draft.target,
                onChange = { value -> draft = draft.copy(target = value) },
                from = 1,
                to = 100,
                accent = accent
            )
        }
        GritField(
            value = draft.unit,
            onValueChange = { value -> draft = draft.copy(unit = value) },
            caption = "Unit",
            placeholder = "times",
            symbol = "straighten"
        )
        GritField(
            value = draft.time,
            onValueChange = { value -> draft = draft.copy(time = value) },
            caption = "Preferred time",
            placeholder = "07:00",
            symbol = "schedule"
        )
        GritField(
            value = draft.note,
            onValueChange = { value -> draft = draft.copy(note = value) },
            caption = "Why this habit",
            placeholder = "A sentence to your future self",
            symbol = "notes",
            singleLine = false,
            minLines = 2
        )
    }
}

// ── Task editor ─────────────────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TaskEditorSheet(
    store: Store,
    taskId: String,
    onDismiss: () -> Unit
) {
    val existing = store.tasks.firstOrNull { it.id == taskId && !it.deleted }
    var draft by remember(taskId) {
        mutableStateOf(
            if (existing != null) TaskDraft(
                title = existing.title,
                notes = existing.notes,
                categoryId = existing.categoryId,
                due = existing.due,
                priority = existing.priority,
                subtasks = existing.subtasks,
                taskTag = existing.taskTag
            ) else TaskDraft(categoryId = store.activeCategories.firstOrNull()?.id.orEmpty())
        )
    }
    var newSubtask by remember(taskId) { mutableStateOf("") }
    val today = dateKey()

    fun addSubtask() {
        val text = newSubtask.trim()
        if (text.isEmpty()) return
        val subtask = Subtask(
            id = newId("sub", StoreRepository.deviceId),
            title = text,
            done = false,
            updatedAt = System.currentTimeMillis()
        )
        draft = draft.copy(subtasks = draft.subtasks + subtask)
        newSubtask = ""
    }

    GritSheet(
        title = if (existing != null) "Edit task" else "New task",
        subtitle = "Capture it now, finish it when you can",
        onDismiss = onDismiss,
        actions = {
            GritButton(
                label = if (existing != null) "Save task" else "Create task",
                onClick = {
                    if (existing != null) StoreRepository.updateTask(existing.id, draft)
                    else StoreRepository.createTask(draft)
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth(),
                symbol = "check",
                style = GritButtonStyle.FILLED
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                GritButton(
                    label = "Cancel",
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                    symbol = "close"
                )
                if (existing != null) {
                    GritButton(
                        label = "Delete",
                        onClick = {
                            StoreRepository.deleteTask(existing.id)
                            onDismiss()
                        },
                        modifier = Modifier.weight(1f),
                        symbol = "delete",
                        danger = true
                    )
                }
            }
        }
    ) {
        GritField(
            value = draft.title,
            onValueChange = { value -> draft = draft.copy(title = value) },
            caption = "Task",
            placeholder = "Draft the letter",
            symbol = "task_alt",
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences)
        )
        GritField(
            value = draft.notes,
            onValueChange = { value -> draft = draft.copy(notes = value) },
            caption = "Notes",
            placeholder = "Anything worth remembering",
            symbol = "notes",
            singleLine = false,
            minLines = 2
        )
        Label("Category")
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            store.activeCategories.forEach { category ->
                FormChip(
                    label = category.name,
                    selected = draft.categoryId == category.id,
                    onClick = { draft = draft.copy(categoryId = category.id) },
                    symbol = category.icon,
                    accent = accentColors(category.accent).accent
                )
            }
        }
        Label("Focus tag")
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            FormChip(
                label = "None",
                selected = draft.taskTag.isBlank(),
                onClick = { draft = draft.copy(taskTag = "") }
            )
            store.activeTags.forEach { tag ->
                TagChip(
                    tag = tag,
                    selected = draft.taskTag == tag.id,
                    onClick = { draft = draft.copy(taskTag = tag.id) }
                )
            }
        }
        Label("Due")
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            FormChip("None", draft.due.isBlank(), { draft = draft.copy(due = "") })
            FormChip("Today", draft.due == today, { draft = draft.copy(due = today) })
            FormChip(
                "Tomorrow",
                draft.due == addDays(today, 1),
                { draft = draft.copy(due = addDays(today, 1)) }
            )
            FormChip(
                "Next week",
                symbol = "date_range",
                selected = draft.due == addDays(today, 7),
                onClick = { draft = draft.copy(due = addDays(today, 7)) }
            )
        }
        GritField(
            value = draft.due,
            onValueChange = { value -> draft = draft.copy(due = value) },
            caption = "Date",
            placeholder = "yyyy-MM-dd",
            symbol = "event"
        )
        Label("Priority")
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            FormChip("Normal", draft.priority == 0, { draft = draft.copy(priority = 0) })
            FormChip("Important", draft.priority == 1, { draft = draft.copy(priority = 1) }, symbol = "priority_high")
            FormChip("Urgent", draft.priority == 2, { draft = draft.copy(priority = 2) }, symbol = "error")
        }
        Label("Subtasks")
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(Modifier.weight(1f)) {
                GritField(
                    value = newSubtask,
                    onValueChange = { newSubtask = it },
                    caption = "Break it down",
                    placeholder = "One small step",
                    symbol = "add_task",
                    keyboardActions = KeyboardActions(onDone = { addSubtask() })
                )
            }
            RoundIconButton(
                symbol = "add",
                onClick = { addSubtask() },
                container = MaterialTheme.colorScheme.secondaryContainer,
                content = MaterialTheme.colorScheme.onSecondaryContainer,
                size = 44.dp,
                iconSize = 20.dp,
                enabled = newSubtask.isNotBlank()
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            draft.subtasks.forEach { subtask ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    RoundIconButton(
                        symbol = if (subtask.done) "check_circle" else "radio_button_unchecked",
                        onClick = {
                            draft = draft.copy(subtasks = draft.subtasks.map {
                                if (it.id == subtask.id) it.copy(done = !it.done) else it
                            })
                        },
                        size = 28.dp,
                        iconSize = 18.dp,
                        container = Color.Transparent,
                        content = if (subtask.done) MaterialTheme.colorScheme.primary else Grit.colors.subtext
                    )
                    Text(
                        text = subtask.title,
                        modifier = Modifier.weight(1f),
                        fontSize = 12.5.sp,
                        color = if (subtask.done) Grit.colors.subtext else MaterialTheme.colorScheme.onSurface,
                        textDecoration = if (subtask.done) TextDecoration.LineThrough else null,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    RoundIconButton(
                        symbol = "close",
                        onClick = {
                            draft = draft.copy(subtasks = draft.subtasks.filter { it.id != subtask.id })
                        },
                        size = 28.dp,
                        iconSize = 15.dp,
                        container = Color.Transparent,
                        content = Grit.colors.subtext
                    )
                }
            }
            if (draft.subtasks.isEmpty()) {
                Text(
                    "No steps yet. A task can be one small thing.",
                    color = Grit.colors.subtext,
                    fontSize = 11.sp
                )
            }
        }
    }
}

// ── Tag editor ──────────────────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TagEditorSheet(
    store: Store,
    tagId: String,
    onDismiss: () -> Unit
) {
    val existing = store.tags.firstOrNull { it.id == tagId && !it.deleted }
    var draft by remember(tagId) {
        mutableStateOf(
            if (existing != null) TagDraft(
                name = existing.name,
                accent = existing.accent,
                color = existing.color,
                shape = existing.shape,
                icon = existing.icon,
                focusMinutes = existing.focusMinutes,
                breakMinutes = existing.breakMinutes,
                longBreakMinutes = existing.longBreakMinutes,
                cycles = existing.cycles,
                dailyGoalMinutes = existing.dailyGoalMinutes.coerceAtLeast(0),
                autoStartBreak = existing.autoStartBreak,
                autoStartFocus = existing.autoStartFocus
            ) else TagDraft()
        )
    }
    var editorTab by remember(tagId) { mutableStateOf("look") }
    var error by remember(tagId) { mutableStateOf("") }
    val preview = Tag(
        id = "preview",
        name = draft.name,
        accent = draft.accent,
        color = draft.color,
        shape = draft.shape,
        icon = draft.icon
    )
    val colors = tagColors(preview)

    fun save() {
        if (draft.color.isNotBlank() && !isHexColor(draft.color)) {
            error = "The colour should look like #7C4DFF"
            editorTab = "look"
            return
        }
        if (existing != null) StoreRepository.updateTag(existing.id, draft)
        else StoreRepository.createTag(draft)
        onDismiss()
    }

    GritSheet(
        title = if (existing != null) "Edit tag" else "New tag",
        subtitle = "A label, a length and a colour for the work",
        onDismiss = onDismiss,
        actions = {
            GritButton(
                label = if (existing != null) "Save tag" else "Create tag",
                onClick = { save() },
                modifier = Modifier.fillMaxWidth(),
                symbol = "check",
                style = GritButtonStyle.FILLED
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                GritButton(
                    label = "Cancel",
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                    symbol = "close"
                )
                if (existing != null) {
                    GritButton(
                        label = "Delete",
                        onClick = {
                            StoreRepository.deleteTag(existing.id)
                            onDismiss()
                        },
                        modifier = Modifier.weight(1f),
                        symbol = "delete",
                        danger = true
                    )
                }
            }
        }
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            FormChip(
                label = "Look & name",
                symbol = "palette",
                selected = editorTab == "look",
                onClick = { editorTab = "look" },
                accent = colors.accent
            )
            FormChip(
                label = "Timing & goal",
                symbol = "timer",
                selected = editorTab == "timing",
                onClick = { editorTab = "timing" },
                accent = colors.accent
            )
        }
        if (editorTab == "look") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.container, RoundedCornerShape(22.dp))
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ShapeBadge(
                    symbol = draft.icon,
                    shapeName = draft.shape,
                    container = colors.accent,
                    content = colors.onSolid,
                    size = 56.dp,
                    iconSize = 27.dp
                )
                Column(Modifier.weight(1f)) {
                    Text(
                        text = draft.name.ifBlank { "New tag" },
                        color = colors.onContainer,
                        fontSize = 19.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${formatMinutesShort(draft.focusMinutes)} focus · " +
                            "${formatMinutesShort(draft.breakMinutes)} break · " +
                            "${draft.cycles} cycles · " +
                            (if (draft.dailyGoalMinutes > 0) "${formatMinutesShort(draft.dailyGoalMinutes)} goal" else "goal off"),
                        color = colors.onContainer.copy(alpha = 0.82f),
                        fontSize = 11.sp,
                        lineHeight = 14.sp
                    )
                }
            }
            GritField(
                value = draft.name,
                onValueChange = { draft = draft.copy(name = it) },
                caption = "Name",
                placeholder = "Deep work",
                symbol = "label"
            )
            Label("Icon")
            IconGrid(
                selected = draft.icon,
                accent = colors.accent,
                onSelect = { draft = draft.copy(icon = it) }
            )
            Label("Shape")
            ShapePicker(
                selected = draft.shape,
                onSelect = { draft = draft.copy(shape = it) },
                accent = colors.accent
            )
            Label("Accent")
            AccentPicker(
                selected = draft.accent,
                onSelect = { draft = draft.copy(accent = it, color = "") },
                customColor = draft.color
            )
            GritField(
                value = draft.color,
                onValueChange = {
                    draft = draft.copy(color = it)
                    error = ""
                },
                caption = "Custom colour",
                placeholder = "#7C4DFF (leave empty to use the accent)",
                symbol = "palette"
            )
            if (error.isNotBlank()) {
                Text(error, color = MaterialTheme.colorScheme.error, fontSize = 11.5.sp)
            }
        } else {
            StepperRow(
                symbol = "psychiatry",
                label = "Focus",
                description = "Length of one session",
                value = draft.focusMinutes,
                onValueChange = { draft = draft.copy(focusMinutes = it) },
                from = 1,
                to = 180,
                accent = colors.accent,
                accentContainer = colors.container,
                onAccent = colors.onContainer,
                format = { formatMinutesShort(it) }
            )
            StepperRow(
                symbol = "coffee",
                label = "Short break",
                description = "Between focus sessions",
                value = draft.breakMinutes,
                onValueChange = { draft = draft.copy(breakMinutes = it) },
                from = 1,
                to = 60,
                accent = colors.accent,
                accentContainer = colors.container,
                onAccent = colors.onContainer,
                format = { formatMinutesShort(it) }
            )
            StepperRow(
                symbol = "self_improvement",
                label = "Long break",
                description = "After the last cycle",
                value = draft.longBreakMinutes,
                onValueChange = { draft = draft.copy(longBreakMinutes = it) },
                from = 1,
                to = 90,
                accent = colors.accent,
                accentContainer = colors.container,
                onAccent = colors.onContainer,
                format = { formatMinutesShort(it) }
            )
            StepperRow(
                symbol = "repeat",
                label = "Cycles",
                description = "Sessions per long break",
                value = draft.cycles,
                onValueChange = { draft = draft.copy(cycles = it) },
                from = 1,
                to = 12,
                accent = colors.accent,
                accentContainer = colors.container,
                onAccent = colors.onContainer,
                format = { "${it}×" }
            )
            StepperRow(
                symbol = "flag",
                label = "Daily goal",
                description = "Target focus time each day",
                value = draft.dailyGoalMinutes,
                onValueChange = { draft = draft.copy(dailyGoalMinutes = it) },
                from = 0,
                to = 480,
                step = 15,
                accent = colors.accent,
                accentContainer = colors.container,
                onAccent = colors.onContainer,
                format = { if (it <= 0) "Off" else formatMinutesShort(it) }
            )
            ToggleRow(
                symbol = "coffee",
                label = "Start breaks automatically",
                description = "The break begins when focus ends",
                checked = draft.autoStartBreak,
                onCheckedChange = { draft = draft.copy(autoStartBreak = it) },
                accent = colors.accent,
                accentContainer = colors.container,
                accentOn = colors.onContainer
            )
            ToggleRow(
                symbol = "psychiatry",
                label = "Start focus automatically",
                description = "Roll from a break straight into the next session",
                checked = draft.autoStartFocus,
                onCheckedChange = { draft = draft.copy(autoStartFocus = it) },
                accent = colors.accent,
                accentContainer = colors.container,
                accentOn = colors.onContainer
            )
        }
    }
}

@Composable
private fun StepperRow(
    symbol: String,
    label: String,
    description: String,
    value: Int,
    onValueChange: (Int) -> Unit,
    from: Int,
    to: Int,
    accent: Color,
    accentContainer: Color,
    onAccent: Color,
    modifier: Modifier = Modifier,
    step: Int = 1,
    format: (Int) -> String
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(Grit.colors.field, RoundedCornerShape(16.dp))
            .padding(horizontal = 9.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        ShapeBadge(
            symbol = symbol,
            shapeName = "Cookie7Sided",
            container = accentContainer,
            content = onAccent,
            size = 36.dp,
            iconSize = 18.dp
        )
        Column(Modifier.weight(1f)) {
            Text(label, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
            Text(description, fontSize = 10.5.sp, lineHeight = 13.sp, color = Grit.colors.subtext)
        }
        Stepper(
            value = value,
            onChange = onValueChange,
            from = from,
            to = to,
            step = step,
            format = format,
            accent = accent
        )
    }
}

// ── Category editor ─────────────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CategoryEditorSheet(
    store: Store,
    categoryId: String,
    onDismiss: () -> Unit
) {
    val existing = store.activeCategories.firstOrNull { it.id == categoryId }
    var draft by remember(categoryId) {
        mutableStateOf(
            if (existing != null) CategoryDraft(existing.name, existing.accent, existing.icon)
            else CategoryDraft()
        )
    }
    val colors = accentColors(draft.accent)

    GritSheet(
        title = if (existing != null) "Edit category" else "New category",
        subtitle = "A colour for habits and tasks that belong together",
        onDismiss = onDismiss,
        actions = {
            GritButton(
                label = if (existing != null) "Save category" else "Create category",
                onClick = {
                    if (existing != null) StoreRepository.updateCategory(existing.id, draft)
                    else StoreRepository.createCategory(draft)
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth(),
                symbol = "check",
                style = GritButtonStyle.FILLED
            )
            GritButton(
                label = "Cancel",
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
                symbol = "close"
            )
            if (existing != null) {
                GritButton(
                    label = "Delete category",
                    onClick = {
                        StoreRepository.deleteCategory(existing.id)
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    symbol = "delete",
                    danger = true
                )
            }
        }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.container, RoundedCornerShape(22.dp))
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ShapeBadge(
                symbol = draft.icon,
                shapeName = "Cookie9Sided",
                container = colors.accent,
                content = colors.onSolid,
                size = 52.dp,
                iconSize = 25.dp
            )
            Text(
                text = draft.name.ifBlank { "New category" },
                modifier = Modifier.weight(1f),
                color = colors.onContainer,
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        GritField(
            value = draft.name,
            onValueChange = { draft = draft.copy(name = it) },
            caption = "Name",
            placeholder = "Health",
            symbol = "label"
        )
        Label("Icon")
        IconGrid(
            selected = draft.icon,
            accent = colors.accent,
            onSelect = { draft = draft.copy(icon = it) }
        )
        Label("Accent")
        AccentPicker(
            selected = draft.accent,
            onSelect = { draft = draft.copy(accent = it) },
            customColor = ""
        )
    }
}

// ── Delete tag confirmation ─────────────────────────────────────────────

@Composable
fun DeleteTagSheet(
    store: Store,
    tagId: String,
    onDismiss: () -> Unit
) {
    val tag = store.tags.firstOrNull { it.id == tagId && !it.deleted }
    val colors = tagColors(tag)
    GritSheet(
        title = "Delete tag?",
        subtitle = "Past sessions keep their tag name",
        onDismiss = onDismiss,
        actions = {
            GritButton(
                label = "Delete tag",
                onClick = {
                    if (tag != null) StoreRepository.deleteTag(tag.id)
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth(),
                symbol = "delete",
                style = GritButtonStyle.FILLED,
                danger = true,
                enabled = store.activeTags.size > 1
            )
            GritButton(
                label = "Cancel",
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
                symbol = "close"
            )
        }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.container, RoundedCornerShape(22.dp))
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ShapeBadge(
                symbol = tag?.icon ?: "local_fire_department",
                shapeName = tag?.shape ?: "Cookie7Sided",
                container = colors.accent,
                content = colors.onSolid,
                size = 54.dp,
                iconSize = 26.dp
            )
            Column(Modifier.weight(1f)) {
                Text(
                    tag?.name ?: "Tag",
                    color = colors.onContainer,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    "This removes it from the timer and stats.",
                    color = colors.onContainer.copy(alpha = 0.82f),
                    fontSize = 11.sp
                )
            }
        }
        Text(
            "Your finished sessions are kept, so the time you already spent still counts. You can create a tag with the same name later.",
            color = Grit.colors.subtext,
            fontSize = 12.5.sp,
            lineHeight = 17.sp
        )
        if (store.activeTags.size <= 1) {
            Text(
                "Keep at least one tag so the timer has somewhere to live.",
                color = MaterialTheme.colorScheme.error,
                fontSize = 11.5.sp
            )
        }
    }
}
