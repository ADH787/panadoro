package com.gritnative.app.data

data class HabitDraft(
    val name: String = "",
    val icon: String = "check_circle",
    val categoryId: String = "",
    val days: List<Int> = (0..6).toList(),
    val time: String = "",
    val target: Int = 1,
    val unit: String = "times",
    val note: String = ""
)

data class TaskDraft(
    val title: String = "",
    val notes: String = "",
    val categoryId: String = "",
    val due: String = "",
    val priority: Int = 0,
    val subtasks: List<Subtask> = emptyList(),
    val taskTag: String = ""
)

data class TagDraft(
    val name: String = "",
    val accent: String = "primary",
    val color: String = "",
    val shape: String = "Cookie7Sided",
    val icon: String = "local_fire_department",
    val focusMinutes: Int = 25,
    val breakMinutes: Int = 5,
    val longBreakMinutes: Int = 15,
    val cycles: Int = 4,
    val dailyGoalMinutes: Int = 0,
    val autoStartBreak: Boolean = true,
    val autoStartFocus: Boolean = false
)

data class CategoryDraft(
    val name: String = "",
    val accent: String = "primary",
    val icon: String = "label"
)

sealed interface GritEvent {
    data class FocusCompleted(val tagName: String, val minutes: Int, val wasFocus: Boolean) : GritEvent
    data class AllHabitsDone(val done: Int, val total: Int) : GritEvent
    data object SyncFinished : GritEvent
}
