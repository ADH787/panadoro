package com.gritnative.app.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import com.gritnative.app.ui.components.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gritnative.app.data.Habit
import com.gritnative.app.data.Store
import com.gritnative.app.data.StoreRepository
import com.gritnative.app.data.Tag
import com.gritnative.app.data.activeCategories
import com.gritnative.app.data.categoryById
import com.gritnative.app.data.dateKey
import com.gritnative.app.data.formatMinutesShort
import com.gritnative.app.data.focusStreak
import com.gritnative.app.data.greetingFor
import com.gritnative.app.data.habitDone
import com.gritnative.app.data.scheduledOn
import com.gritnative.app.data.todayFocusMinutes
import com.gritnative.app.data.todayHabits
import com.gritnative.app.data.todayHabitsDone
import com.gritnative.app.ui.components.CategoryChip
import com.gritnative.app.ui.components.EmptyState
import com.gritnative.app.ui.components.GritButton
import com.gritnative.app.ui.components.GritButtonStyle
import com.gritnative.app.ui.components.HabitCard
import com.gritnative.app.ui.components.MiniStatTile
import com.gritnative.app.ui.components.RoundIconButton
import com.gritnative.app.ui.components.StaggeredEntry
import com.gritnative.app.ui.components.TagChip
import com.gritnative.app.ui.components.WavyRing
import com.gritnative.app.ui.theme.Grit
import com.gritnative.app.ui.theme.Symbol
import com.gritnative.app.ui.theme.categoryColors
import com.gritnative.app.ui.theme.tagColors
import java.time.LocalTime
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TodayScreen(
    store: Store,
    showCompleted: Boolean,
    weekStartMonday: Boolean,
    onNewHabit: () -> Unit,
    onEditHabit: (String) -> Unit,
    onNewCategory: () -> Unit,
    onEditCategory: (String) -> Unit,
    onDeleteCategory: (String) -> Unit
) {
    val today = dateKey()
    val categories = store.activeCategories
    var selectedCategory by rememberSaveable { mutableStateOf("all") }

    LaunchedEffect(categories) {
        if (selectedCategory != "all" && categories.none { it.id == selectedCategory }) {
            selectedCategory = "all"
        }
    }

    val scheduled = store.todayHabits
    val forCategory = if (selectedCategory == "all") scheduled
    else scheduled.filter { it.categoryId == selectedCategory }
    val visible = if (showCompleted) forCategory else forCategory.filterNot { store.habitDone(it) }

    val done = visible.count { store.habitDone(it) }
    val total = visible.size
    val progress = if (total > 0) done.toFloat() / total else 0f
    val allDone = total > 0 && done == total
    val totalHabits = scheduled.size
    val doneHabits = scheduled.count { store.habitDone(it) }
    val globalPct = if (totalHabits > 0) (doneHabits * 100f / totalHabits).roundToInt() else 0
    val greeting = greetingFor(LocalTime.now().hour)
    val subtitle = when {
        total == 0 -> "Nothing scheduled. Add one small thing."
        else -> "$done of $total habits · ${formatMinutesShort(store.todayFocusMinutes())} focused today"
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 112.dp),
        verticalArrangement = Arrangement.spacedBy(13.dp)
    ) {
        item(key = "hero") {
            StaggeredEntry(0) {
                TodayHero(
                    greeting = greeting,
                    allDone = allDone,
                    done = done,
                    total = total,
                    progress = progress,
                    subtitle = subtitle
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
                    symbol = "check_circle",
                    value = "$doneHabits / $totalHabits",
                    label = "Habits today",
                    hint = "$globalPct% complete",
                    shapeName = "Cookie7Sided",
                    modifier = Modifier.weight(1f)
                )
                MiniStatTile(
                    symbol = "psychiatry",
                    value = formatMinutesShort(store.todayFocusMinutes()),
                    label = "Focused today",
                    hint = "Goals per tag",
                    shapeName = "Flower",
                    container = MaterialTheme.colorScheme.tertiaryContainer,
                    onContainer = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier.weight(1f)
                )
                MiniStatTile(
                    symbol = "local_fire_department",
                    value = store.focusStreak().toString(),
                    label = "Focus streak",
                    hint = "Keep the thread",
                    shapeName = "SoftBurst",
                    container = MaterialTheme.colorScheme.secondaryContainer,
                    onContainer = MaterialTheme.colorScheme.onSecondaryContainer,
                    flame = true,
                    modifier = Modifier.weight(1f)
                )
            }
            }
        }

        item(key = "today-heading") {
            StaggeredEntry(2) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "Habits",
                    modifier = Modifier.weight(1f),
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 22.sp,
                    lineHeight = 27.sp,
                    fontWeight = FontWeight.Bold
                )
                if (selectedCategory != "all") {
                    RoundIconButton(
                        symbol = "edit",
                        onClick = { onEditCategory(selectedCategory) },
                        size = 36.dp,
                        iconSize = 17.dp
                    )
                    RoundIconButton(
                        symbol = "delete",
                        onClick = { onDeleteCategory(selectedCategory) },
                        size = 36.dp,
                        iconSize = 17.dp,
                        content = MaterialTheme.colorScheme.error
                    )
                }
                GritButton(
                    label = "Category",
                    onClick = onNewCategory,
                    symbol = "add",
                    style = GritButtonStyle.TONAL
                )
            }
            }
        }

        item(key = "categories") {
            StaggeredEntry(3) {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TagChip(
                    tag = Tag(
                        id = "all",
                        name = "All",
                        icon = "apps",
                        accent = "primary",
                        shape = "Cookie4Sided"
                    ),
                    selected = selectedCategory == "all",
                    onClick = { selectedCategory = "all" },
                    trailingText = totalHabits.toString()
                )
                categories.forEach { category ->
                    val count = scheduled.count { it.categoryId == category.id }
                    CategoryChip(
                        category = category,
                        selected = selectedCategory == category.id,
                        onClick = { selectedCategory = category.id },
                        count = if (count > 0) count.toString() else ""
                    )
                }
            }
            }
        }

        if (visible.isEmpty()) {
            item(key = "empty") {
                StaggeredEntry(4) {
                EmptyState(
                    symbol = if (selectedCategory == "all") "check_circle" else "label",
                    title = when {
                        selectedCategory == "all" && forCategory.isEmpty() -> "A clear day"
                        selectedCategory == "all" -> "Everything is done"
                        else -> "Nothing here"
                    },
                    subtitle = when {
                        selectedCategory == "all" && forCategory.isEmpty() ->
                            "Add a habit you can keep, and it will wait for you here."
                        selectedCategory == "all" ->
                            "Completed habits stay tucked away while you enjoy the quiet."
                        else -> "No habits in this category are scheduled today."
                    },
                    shapeName = "Cookie9Sided"
                )
                }
            }
        } else {
            itemsIndexed(visible, key = { _, habit -> habit.id }) { index, habit ->
                StaggeredEntry(
                    index = index.coerceAtMost(8),
                    modifier = Modifier.animateItem()
                ) {
                    HabitCard(
                        store = store,
                        habit = habit,
                        weekStartMonday = weekStartMonday,
                        onEdit = { onEditHabit(habit.id) },
                        onToggle = { toggleHabit(store, habit, today) },
                        onDayClick = { key ->
                            val value = if (store.habitDone(habit, key)) 0 else habit.target.coerceAtLeast(1)
                            StoreRepository.setHabitEntry(habit.id, key, value)
                        }
                    )
                }
            }
        }
    }
}

private fun toggleHabit(store: Store, habit: Habit, today: String) {
    val target = habit.target.coerceAtLeast(1)
    when {
        store.habitDone(habit, today) -> StoreRepository.setHabitEntry(habit.id, today, 0)
        target > 1 -> StoreRepository.incrementHabit(habit.id, 1)
        else -> StoreRepository.setHabitEntry(habit.id, today, target)
    }
}

@Composable
private fun TodayHero(
    greeting: String,
    allDone: Boolean,
    done: Int,
    total: Int,
    progress: Float,
    subtitle: String
) {
    // Expressive Material shapes are designed around a square icon box. Clipping
    // a wide hero card with one stretches the polygon into jagged spikes, so the
    // large surfaces use the smooth rounded corner language instead.
    val shape = RoundedCornerShape(30.dp)
    val container by animateColorAsState(
        targetValue = if (allDone) Grit.colors.successContainer
        else MaterialTheme.colorScheme.tertiaryContainer,
        animationSpec = tween(460),
        label = "hero-container"
    )
    val onContainer by animateColorAsState(
        targetValue = if (allDone) Grit.colors.onSuccessContainer
        else MaterialTheme.colorScheme.onTertiaryContainer,
        animationSpec = tween(460),
        label = "hero-on-container"
    )
    val accent by animateColorAsState(
        targetValue = if (allDone) Grit.colors.success else MaterialTheme.colorScheme.tertiary,
        animationSpec = tween(460),
        label = "hero-accent"
    )
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = spring(dampingRatio = 0.72f, stiffness = 240f),
        label = "hero-progress"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(container)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 19.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                AnimatedContent(
                    targetState = greeting + if (allDone) ", all done." else ".",
                    transitionSpec = {
                        (fadeIn(tween(240)) + slideInVertically(tween(280)) { it / 3 })
                            .togetherWith(fadeOut(tween(130)))
                    },
                    label = "hero-greeting"
                ) { value ->
                    Text(
                        text = value,
                        color = onContainer,
                        fontSize = 22.sp,
                        lineHeight = 27.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                AnimatedContent(
                    targetState = subtitle,
                    transitionSpec = {
                        (fadeIn(tween(260)) + slideInVertically(tween(300)) { it / 4 })
                            .togetherWith(fadeOut(tween(140)))
                    },
                    label = "hero-subtitle"
                ) { value ->
                    Text(
                        text = value,
                        color = onContainer.copy(alpha = 0.84f),
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            WavyRing(
                progress = animatedProgress,
                modifier = Modifier.size(78.dp),
                color = accent,
                track = onContainer.copy(alpha = 0.16f),
                strokeWidth = 6.dp,
                amplitude = 1.6.dp,
                wavy = total > 0 && !allDone
            ) {
                Text(
                    text = "$done/$total",
                    color = onContainer,
                    fontSize = 16.sp,
                    lineHeight = 19.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    maxLines = 1
                )
            }
        }
    }
}
