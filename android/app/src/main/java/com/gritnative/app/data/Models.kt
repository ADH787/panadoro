package com.gritnative.app.data

import kotlinx.serialization.Serializable

/**
 * The on-disk / sync schema is shared byte-for-byte with the quickshell app.
 * Every record carries `updatedAt`, `device` and a `deleted` tombstone so two
 * devices can merge record by record without losing a newer edit.
 */
@Serializable
data class Habit(
    val id: String,
    val name: String = "New habit",
    val icon: String = "check_circle",
    val categoryId: String = "",
    val days: List<Int> = (0..6).toList(),
    val time: String = "",
    val target: Int = 1,
    val unit: String = "times",
    val note: String = "",
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
    val device: String = "",
    val deleted: Boolean = false
)

@Serializable
data class Entry(
    val id: String,
    val habitId: String = "",
    val date: String = "",
    val value: Int = 0,
    val note: String = "",
    val updatedAt: Long = 0L,
    val device: String = "",
    val deleted: Boolean = false
)

@Serializable
data class Subtask(
    val id: String,
    val title: String = "",
    val done: Boolean = false,
    val updatedAt: Long = 0L
)

@Serializable
data class Task(
    val id: String,
    val title: String = "New task",
    val notes: String = "",
    val categoryId: String = "",
    val due: String = "",
    val done: Boolean = false,
    val priority: Int = 0,
    val subtasks: List<Subtask> = emptyList(),
    val taskTag: String = "",
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
    val device: String = "",
    val deleted: Boolean = false
)

@Serializable
data class Category(
    val id: String,
    val name: String = "New category",
    val accent: String = "primary",
    val icon: String = "label",
    val updatedAt: Long = 0L,
    val device: String = "",
    val deleted: Boolean = false
)

@Serializable
data class Tag(
    val id: String,
    val name: String = "New tag",
    val accent: String = "primary",
    val color: String = "",
    val shape: String = "Cookie7Sided",
    val icon: String = "local_fire_department",
    val focusMinutes: Int = 25,
    val breakMinutes: Int = 5,
    val longBreakMinutes: Int = 15,
    val cycles: Int = 4,
    /**
     * -1 means "the field was absent in an older document". It is resolved to
     * the historical default for the seeded tags but never written back as -1.
     */
    val dailyGoalMinutes: Int = -1,
    val autoStartBreak: Boolean = true,
    val autoStartFocus: Boolean = false,
    val updatedAt: Long = 0L,
    val device: String = "",
    val deleted: Boolean = false
)

@Serializable
data class FocusSession(
    val id: String,
    val tagId: String = "",
    val taskId: String = "",
    val phase: String = "focus",
    val startedAt: Long = 0L,
    val endedAt: Long = 0L,
    val durationSec: Int = 0,
    val plannedSec: Int = 0,
    val completed: Boolean = false,
    val note: String = "",
    val updatedAt: Long = 0L,
    val device: String = "",
    val deleted: Boolean = false
)

@Serializable
data class TimerState(
    val phase: String = "focus",
    val tagId: String = "",
    val taskId: String = "",
    val note: String = "",
    val running: Boolean = false,
    val startedAt: Long = 0L,
    val runStartedAt: Long = 0L,
    /**
     * Quickshell stores the real-valued elapsed seconds here when a timer is
     * paused, so this must accept fractional JSON numbers.
     */
    val accumulatedSec: Double = 0.0,
    val durationSec: Int = 1500,
    val sessionId: String = "",
    val cycle: Int = 0,
    val updatedAt: Long = 0L,
    val device: String = ""
)

@Serializable
data class LeaderboardPrefs(
    val displayName: String = "",
    val nameUpdatedAt: Long = 0L,
    val publicTagIds: List<String> = emptyList(),
    val tagsUpdatedAt: Long = 0L,
    val updatedAt: Long = 0L
)

@Serializable
data class Store(
    val version: Int = 1,
    val habits: List<Habit> = emptyList(),
    val entries: List<Entry> = emptyList(),
    val tasks: List<Task> = emptyList(),
    val categories: List<Category> = defaultCategories(),
    val tags: List<Tag> = defaultTags(),
    val focusSessions: List<FocusSession> = emptyList(),
    val timer: TimerState? = null,
    val leaderboard: LeaderboardPrefs = LeaderboardPrefs(),
    val updatedAt: Long = 0L,
    val device: String = ""
)

@Serializable
data class SyncDocument(
    val revision: Int = 0,
    val payload: Store? = null,
    val updatedAt: String = ""
)

fun defaultCategories(): List<Category> = listOf(
    Category("cat-personal", "Personal", "primary", "person", 0L, "seed", false),
    Category("cat-work", "Work", "tertiary", "work", 0L, "seed", false),
    Category("cat-study", "Study", "secondary", "school", 0L, "seed", false),
    Category("cat-health", "Health", "success", "favorite", 0L, "seed", false)
)

fun defaultTags(): List<Tag> = listOf(
    Tag(
        "tag-deep-work", "Deep work", "primary", "", "Cookie7Sided", "neurology",
        25, 5, 15, 4, 120, true, false, 0L, "seed", false
    ),
    Tag(
        "tag-study", "Study", "tertiary", "", "Cookie9Sided", "school",
        45, 10, 20, 3, 90, true, false, 0L, "seed", false
    ),
    Tag(
        "tag-creative", "Creative", "secondary", "", "Flower", "palette",
        30, 8, 15, 4, 60, true, false, 0L, "seed", false
    ),
    Tag(
        "tag-admin", "Admin", "success", "", "Cookie4Sided", "inbox",
        20, 5, 10, 3, 45, true, false, 0L, "seed", false
    )
)

fun defaultGoalForTagId(id: String): Int = when (id) {
    "tag-deep-work" -> 120
    "tag-study" -> 90
    "tag-creative" -> 60
    "tag-admin" -> 45
    else -> 0
}

fun normalizeTag(tag: Tag): Tag {
    val goal = when {
        tag.dailyGoalMinutes < 0 -> defaultGoalForTagId(tag.id)
        else -> tag.dailyGoalMinutes
    }
    return tag.copy(
        accent = tag.accent.ifBlank { "primary" },
        shape = tag.shape.ifBlank { "Cookie7Sided" },
        icon = tag.icon.ifBlank { "local_fire_department" },
        focusMinutes = tag.focusMinutes.coerceIn(1, 24 * 60),
        breakMinutes = tag.breakMinutes.coerceIn(1, 24 * 60),
        longBreakMinutes = tag.longBreakMinutes.coerceIn(1, 24 * 60),
        cycles = tag.cycles.coerceIn(1, 99),
        dailyGoalMinutes = goal.coerceIn(0, 720)
    )
}

fun emptyStore(deviceId: String, now: Long = System.currentTimeMillis()): Store = Store(
    version = 1,
    habits = emptyList(),
    entries = emptyList(),
    tasks = emptyList(),
    categories = defaultCategories(),
    tags = defaultTags(),
    focusSessions = emptyList(),
    timer = null,
    leaderboard = LeaderboardPrefs(),
    updatedAt = now,
    device = deviceId
)
