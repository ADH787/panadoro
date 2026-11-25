package com.gritnative.app.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.gritnative.app.data.Habit
import com.gritnative.app.data.Store
import com.gritnative.app.data.StoreRepository
import com.gritnative.app.data.Tag
import com.gritnative.app.data.activeCategories
import com.gritnative.app.data.dateKey
import com.gritnative.app.data.greetingFor
import com.gritnative.app.data.habitDone
import com.gritnative.app.data.habitDoneEffective
import com.gritnative.app.data.todayHabits
import com.gritnative.app.ui.components.CategoryChip
import com.gritnative.app.ui.components.EmptyState
import com.gritnative.app.ui.components.HabitCard
import com.gritnative.app.ui.components.HeroBanner
import com.gritnative.app.ui.components.RoundIconButton
import com.gritnative.app.ui.components.StaggeredEntry
import com.gritnative.app.ui.components.TagChip
import com.gritnative.app.ui.theme.Grit
import java.time.LocalTime

@Composable
fun TodayScreen(
    store: Store,
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
    // Done habits stay in the list and glide to the end instead of vanishing.
    val visible = forCategory.sortedBy {
        store.habitDoneEffective(it, today, weekStartMonday)
    }
    val totalHabits = scheduled.size
    val doneHabits = scheduled.count { store.habitDoneEffective(it, today, weekStartMonday) }
    val allDone = totalHabits > 0 && doneHabits == totalHabits
    val greeting = greetingFor(LocalTime.now().hour) + "."

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 112.dp),
        verticalArrangement = Arrangement.spacedBy(13.dp)
    ) {
        item(key = "hero") {
            StaggeredEntry(0) {
                HeroBanner(
                    title = greeting,
                    done = doneHabits,
                    total = totalHabits,
                    container = if (allDone) Grit.colors.successContainer
                    else MaterialTheme.colorScheme.tertiaryContainer,
                    onContainer = if (allDone) Grit.colors.onSuccessContainer
                    else MaterialTheme.colorScheme.onTertiaryContainer,
                    accent = if (allDone) Grit.colors.success
                    else MaterialTheme.colorScheme.tertiary,
                    shapeName = "Cookie12Sided",
                    wavy = totalHabits > 0 && !allDone
                )
            }
        }

        item(key = "category-chips") {
            StaggeredEntry(1) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
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
                    if (selectedCategory != "all") {
                        RoundIconButton(
                            symbol = "edit",
                            onClick = { onEditCategory(selectedCategory) },
                            size = 38.dp,
                            iconSize = 18.dp
                        )
                        RoundIconButton(
                            symbol = "delete",
                            onClick = { onDeleteCategory(selectedCategory) },
                            size = 38.dp,
                            iconSize = 18.dp,
                            content = MaterialTheme.colorScheme.error
                        )
                    }
                    }
                    RoundIconButton(
                        symbol = "add",
                        onClick = onNewCategory,
                        size = 42.dp,
                        iconSize = 20.dp,
                        container = Grit.colors.field,
                        content = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        if (visible.isEmpty()) {
            item(key = "empty") {
                StaggeredEntry(2) {
                    EmptyState(
                        symbol = if (selectedCategory == "all") "check_circle" else "label",
                        title = when {
                            selectedCategory == "all" && forCategory.isEmpty() -> "A clear day"
                            selectedCategory == "all" -> "Everything is done"
                            else -> "Nothing here"
                        },
                        subtitle = "",
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
