package com.gritnative.app.data

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.abs
import kotlin.math.roundToInt

val GritJson: Json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    coerceInputValues = true
    prettyPrint = true
}

val GritNetJson: Json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    coerceInputValues = true
}

private val serial = AtomicLong(0L)

fun newId(prefix: String, deviceId: String, now: Long = System.currentTimeMillis()): String {
    val n = serial.updateAndGet { (it + 1) % 1_000_000L }
    val dev = deviceId.take(8).ifBlank { "local" }
    return "$prefix-$dev-${now.toString(36)}-${n.toString(36)}"
}

// ── Dates ────────────────────────────────────────────────────────────────

/**
 * Mirrors the in-app language so date labels follow it even when the system
 * locale is different. MainActivity keeps the flag in step with the setting.
 */
object AppLocale {
    @Volatile var isArabic: Boolean = false

    fun current(): Locale = if (isArabic) Locale.forLanguageTag("ar") else Locale.getDefault()
}

fun dateKey(date: LocalDate = LocalDate.now()): String = date.toString()

fun dateKey(epochMs: Long): String =
    Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault()).toLocalDate().toString()

fun dateFromKey(key: String): LocalDate = runCatching { LocalDate.parse(key) }
    .getOrElse { LocalDate.now() }

fun addDays(key: String, amount: Int): String =
    dateFromKey(key).plusDays(amount.toLong()).toString()

fun weekdayOf(key: String): Int = dateFromKey(key).dayOfWeek.value % 7 // Sunday = 0

fun weekStartOffset(todayKey: String, monday: Boolean): Int {
    val day = weekdayOf(todayKey)
    return if (monday) (day + 6) % 7 else day
}

fun thisWeekDays(todayKey: String, monday: Boolean): List<String> {
    val start = addDays(todayKey, -weekStartOffset(todayKey, monday))
    return (0..6).map { addDays(start, it) }
}

fun lastNDays(count: Int, todayKey: String): List<String> {
    val n = count.coerceAtLeast(1)
    return (n - 1 downTo 0).map { addDays(todayKey, -it) }
}

fun relativeDay(key: String, todayKey: String = dateKey()): String = when (key) {
    todayKey -> "Today"
    addDays(todayKey, 1) -> "Tomorrow"
    addDays(todayKey, -1) -> "Yesterday"
    else -> runCatching {
        dateFromKey(key).format(DateTimeFormatter.ofPattern("EEE, d MMM", AppLocale.current()))
    }.getOrDefault(key)
}

fun weekdayShort(key: String): String = runCatching {
    dateFromKey(key).format(DateTimeFormatter.ofPattern("EEEEE", AppLocale.current()))
}.getOrDefault("")

fun weekdayNarrow(key: String): String = weekdayShort(key)

fun dateNumberOf(key: String): Int = dateFromKey(key).dayOfMonth

/** "07:05" style clock used by the timer and its notification. */
fun formatClock(seconds: Int): String {
    val total = seconds.coerceAtLeast(0)
    val hours = total / 3600
    val minutes = (total % 3600) / 60
    val rest = total % 60
    return if (hours > 0) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes, rest)
    } else {
        String.format(Locale.US, "%02d:%02d", minutes, rest)
    }
}

/** "45m", "1h", "1h 30m" — the duration format used throughout Grit. */
fun formatMinutesShort(minutes: Int): String {
    val value = minutes.coerceAtLeast(0)
    if (value < 60) return "${value}m"
    val hours = value / 60
    val rest = value % 60
    return if (rest == 0) "${hours}h" else "${hours}h ${rest}m"
}

fun formatDurationShort(minutes: Int): String = formatMinutesShort(minutes)

fun formatTimeOfDay(epochMs: Long): String =
    Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault()))

fun greetingFor(hour: Int): String = when {
    hour < 5 -> "Still up?"
    hour < 12 -> "Good morning"
    hour < 18 -> "Good afternoon"
    else -> "Good evening"
}

/** Local midnight at the start of [key], in epoch millis. */
fun startOfDayMillis(key: String): Long =
    dateFromKey(key).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

// ── Normalisation / merge ────────────────────────────────────────────────

fun <T> dedupeById(
    items: List<T>,
    idOf: (T) -> String,
    updatedAtOf: (T) -> Long,
    deletedOf: (T) -> Boolean
): List<T> {
    val byId = LinkedHashMap<String, T>()
    for (item in items) {
        val id = idOf(item)
        if (id.isBlank()) continue
        val previous = byId[id]
        if (previous == null ||
            (deletedOf(previous) && !deletedOf(item)) ||
            (deletedOf(previous) == deletedOf(item) && updatedAtOf(item) > updatedAtOf(previous))
        ) {
            byId[id] = item
        }
    }
    return byId.values.toList()
}

fun normalizeHabits(items: List<Habit>): List<Habit> = dedupeById(
    items, { it.id }, { it.updatedAt }, { it.deleted }
).map {
    it.copy(
        name = it.name.ifBlank { "New habit" },
        icon = it.icon.ifBlank { "check_circle" },
        days = it.days.filter { day -> day in 0..6 }.distinct().ifEmpty { (0..6).toList() },
        target = it.target.coerceAtLeast(1),
        unit = it.unit.ifBlank { "times" }
    )
}

fun normalizeEntries(items: List<Entry>): List<Entry> = dedupeById(
    items, { it.id }, { it.updatedAt }, { it.deleted }
).filter { it.habitId.isNotBlank() && it.date.isNotBlank() }

fun normalizeTasks(items: List<Task>): List<Task> = dedupeById(
    items, { it.id }, { it.updatedAt }, { it.deleted }
).map {
    it.copy(
        title = it.title.ifBlank { "New task" },
        priority = it.priority.coerceIn(0, 2),
        subtasks = it.subtasks
            .filter { sub -> sub.title.isNotBlank() }
            .distinctBy { sub -> sub.id }
    )
}

fun normalizeCategories(items: List<Category>): List<Category> = dedupeById(
    items, { it.id }, { it.updatedAt }, { it.deleted }
).map { it.copy(name = it.name.ifBlank { "New category" }, icon = it.icon.ifBlank { "label" }) }

fun normalizeTags(items: List<Tag>): List<Tag> = dedupeById(
    items, { it.id }, { it.updatedAt }, { it.deleted }
).map(::normalizeTag)

fun sortSessions(items: List<FocusSession>): List<FocusSession> =
    items.sortedByDescending { it.endedAt }

fun normalizeSessions(items: List<FocusSession>): List<FocusSession> = sortSessions(dedupeById(
    items, { it.id }, { it.updatedAt }, { it.deleted }
))

fun normalizeStore(raw: Store, deviceId: String): Store {
    val categories = if (raw.categories.isEmpty()) raw.categories else normalizeCategories(raw.categories)
    return raw.copy(
        version = 1,
        habits = normalizeHabits(raw.habits),
        entries = normalizeEntries(raw.entries),
        tasks = normalizeTasks(raw.tasks),
        categories = categories,
        tags = normalizeTags(raw.tags),
        focusSessions = normalizeSessions(raw.focusSessions),
        timer = raw.timer,
        leaderboard = raw.leaderboard,
        device = raw.device.ifBlank { deviceId }
    )
}

/** Last-write-wins per preference field, so a stale device cannot undo a newer name or tag list. */
fun mergeLeaderboardPrefs(
    local: LeaderboardPrefs,
    remote: LeaderboardPrefs
): LeaderboardPrefs {
    val nameWinsRemote = remote.nameUpdatedAt > local.nameUpdatedAt
    val tagsWinRemote = remote.tagsUpdatedAt > local.tagsUpdatedAt
    val nameAt = maxOf(local.nameUpdatedAt, remote.nameUpdatedAt)
    val tagsAt = maxOf(local.tagsUpdatedAt, remote.tagsUpdatedAt)
    return LeaderboardPrefs(
        displayName = if (nameWinsRemote) remote.displayName else local.displayName,
        nameUpdatedAt = nameAt,
        publicTagIds = if (tagsWinRemote) remote.publicTagIds else local.publicTagIds,
        tagsUpdatedAt = tagsAt,
        updatedAt = maxOf(local.updatedAt, remote.updatedAt, nameAt, tagsAt)
    )
}

fun <T> mergeRecordLists(
    local: List<T>,
    remote: List<T>,
    idOf: (T) -> String,
    updatedAtOf: (T) -> Long,
    deletedOf: (T) -> Boolean
): List<T> {
    val map = LinkedHashMap<String, T>()
    for (item in local) {
        val id = idOf(item)
        if (id.isNotBlank()) map[id] = item
    }
    for (item in remote) {
        val id = idOf(item)
        if (id.isBlank()) continue
        val current = map[id]
        if (current == null) {
            map[id] = item
            continue
        }
        val currentTime = updatedAtOf(current)
        val remoteTime = updatedAtOf(item)
        if (remoteTime > currentTime ||
            (remoteTime == currentTime && deletedOf(item) && !deletedOf(current))
        ) {
            map[id] = item
        }
    }
    return map.values.toList()
}

/** Exact mirror of GritService.mergeStores / applyRemoteStore. */
fun mergeStores(
    local: Store,
    remote: Store,
    deviceId: String,
    now: Long = System.currentTimeMillis()
): Store {
    val a = normalizeStore(local, deviceId)
    val b = normalizeStore(remote, deviceId)
    val merged = a.copy(
        version = 1,
        habits = normalizeHabits(mergeRecordLists(a.habits, b.habits, { it.id }, { it.updatedAt }, { it.deleted })),
        entries = normalizeEntries(mergeRecordLists(a.entries, b.entries, { it.id }, { it.updatedAt }, { it.deleted })),
        tasks = normalizeTasks(mergeRecordLists(a.tasks, b.tasks, { it.id }, { it.updatedAt }, { it.deleted })),
        categories = normalizeCategories(mergeRecordLists(a.categories, b.categories, { it.id }, { it.updatedAt }, { it.deleted })),
        tags = normalizeTags(mergeRecordLists(a.tags, b.tags, { it.id }, { it.updatedAt }, { it.deleted })),
        focusSessions = normalizeSessions(mergeRecordLists(a.focusSessions, b.focusSessions, { it.id }, { it.updatedAt }, { it.deleted })),
        timer = a.timer,
        leaderboard = mergeLeaderboardPrefs(a.leaderboard, b.leaderboard),
        updatedAt = now,
        device = deviceId
    )
    val remoteTimer = b.timer
    val timer = when {
        remoteTimer != null && (a.timer == null || remoteTimer.updatedAt > a.timer.updatedAt) -> remoteTimer
        else -> a.timer
    }
    return merged.copy(timer = timer)
}

fun Store.snapshot(deviceId: String, now: Long = System.currentTimeMillis()): Store =
    copy(version = 1, updatedAt = now, device = deviceId)

// ── Accessors ────────────────────────────────────────────────────────────

val Store.activeHabits: List<Habit> get() = habits.filter { !it.deleted }
val Store.activeEntries: List<Entry> get() = entries.filter { !it.deleted }
val Store.activeTasks: List<Task> get() = tasks.filter { !it.deleted }
val Store.activeCategories: List<Category> get() = categories.filter { !it.deleted }
val Store.activeTags: List<Tag> get() = tags.filter { !it.deleted }
val Store.activeSessions: List<FocusSession> get() = focusSessions.filter { !it.deleted }

fun Store.habitById(id: String?): Habit? = habits.firstOrNull { it.id == id }
fun Store.tagById(id: String?): Tag? = tags.firstOrNull { it.id == id }
fun Store.activeTagById(id: String?): Tag? = activeTags.firstOrNull { it.id == id }
fun Store.taskById(id: String?): Task? = tasks.firstOrNull { it.id == id }

fun Store.categoryById(id: String?): Category =
    categories.firstOrNull { it.id == id }
        ?: Category(id = "", name = "Uncategorised", accent = "outline", icon = "circle", deleted = true)

fun Store.entryFor(habitId: String, date: String): Entry? = activeEntries.firstOrNull {
    it.habitId == habitId && it.date == date
}

fun Store.habitValue(habitId: String, date: String): Int = entryFor(habitId, date)?.value ?: 0

fun Store.habitDone(habit: Habit?, date: String = dateKey()): Boolean {
    if (habit == null) return false
    val target = habit.target.coerceAtLeast(1)
    return habitValue(habit.id, date) >= target
}

fun Store.habitProgress(habit: Habit?, date: String = dateKey()): Float {
    if (habit == null) return 0f
    val target = habit.target.coerceAtLeast(1)
    return (habitValue(habit.id, date).toFloat() / target).coerceIn(0f, 1f)
}

fun Store.scheduledOn(habit: Habit?, date: String = dateKey()): Boolean {
    if (habit == null) return false
    val days = habit.days.ifEmpty { (0..6).toList() }
    return weekdayOf(date) in days
}

fun Store.previousScheduled(habit: Habit, fromKey: String): String {
    var cursor = addDays(fromKey, -1)
    repeat(14) {
        if (scheduledOn(habit, cursor)) return cursor
        cursor = addDays(cursor, -1)
    }
    return cursor
}

fun Store.currentStreak(habit: Habit?, todayKey: String = dateKey()): Int {
    if (habit == null) return 0
    var cursor = todayKey
    if (!scheduledOn(habit, cursor)) cursor = previousScheduled(habit, cursor)
    var streak = 0
    var guard = 0
    while (guard++ < 400) {
        if (!scheduledOn(habit, cursor)) {
            cursor = addDays(cursor, -1)
            continue
        }
        if (habitDone(habit, cursor)) {
            streak++
            cursor = previousScheduled(habit, cursor)
        } else {
            if (cursor != todayKey) break
            cursor = previousScheduled(habit, cursor)
        }
    }
    return streak
}

fun Store.bestStreak(habit: Habit?, todayKey: String = dateKey()): Int {
    if (habit == null) return 0
    val now = System.currentTimeMillis()
    val floorMs = maxOf(
        habit.createdAt.takeIf { it > 0 } ?: now - 365L * 86_400_000L,
        now - 365L * 86_400_000L
    )
    var cursor = dateKey(floorMs)
    var best = 0
    var run = 0
    var guard = 0
    while (cursor <= todayKey && guard++ < 400) {
        if (scheduledOn(habit, cursor)) {
            if (habitDone(habit, cursor)) {
                run++
                best = maxOf(best, run)
            } else if (cursor != todayKey) {
                run = 0
            }
        }
        cursor = addDays(cursor, 1)
    }
    return maxOf(best, currentStreak(habit, todayKey))
}

fun Store.habitConsistency(habit: Habit?, days: Int, todayKey: String = dateKey()): Float {
    if (habit == null) return 0f
    var due = 0
    var done = 0
    for (key in lastNDays(days, todayKey)) {
        if (!scheduledOn(habit, key)) continue
        due++
        if (habitDone(habit, key)) done++
    }
    return if (due > 0) done.toFloat() / due else 0f
}

val Store.todayHabits: List<Habit>
    get() = activeHabits
        .filter { scheduledOn(it, dateKey()) }
        .sortedWith(compareBy { it.time.ifBlank { "99:99" } })

val Store.todayHabitsDone: Int get() = todayHabits.count { habitDone(it) }

fun Store.taskFiltered(filter: String, todayKey: String = dateKey()): List<Task> {
    var list = activeTasks
    when (filter) {
        "today" -> list = list.filter { !it.done && (it.due == todayKey || (it.due.isNotEmpty() && it.due < todayKey)) }
        "upcoming" -> list = list.filter { !it.done && it.due > todayKey }
        "done" -> list = list.filter { it.done }
        "open" -> list = list.filter { !it.done }
    }
    return list.sortedWith(compareBy<Task> { it.done }
        .thenBy { it.due }
        .thenByDescending { it.priority }
        .thenByDescending { it.createdAt })
}

fun Store.tasksForTag(tagId: String): List<Task> = activeTasks
    .filter { it.taskTag == tagId }
    .sortedWith(compareBy<Task> { it.done }
        .thenBy { it.due }
        .thenByDescending { it.createdAt })

fun Store.taskSubtaskProgress(task: Task): Float {
    if (task.subtasks.isEmpty()) return -1f
    return task.subtasks.count { it.done }.toFloat() / task.subtasks.size
}

// ── Timer helpers ────────────────────────────────────────────────────────

fun minutesForPhase(tag: Tag?, phase: String): Int = when (phase) {
    "shortBreak" -> (tag?.breakMinutes ?: 5).coerceAtLeast(1)
    "longBreak" -> (tag?.longBreakMinutes ?: 15).coerceAtLeast(1)
    else -> (tag?.focusMinutes ?: 25).coerceAtLeast(1)
}

fun Store.makeTimer(
    tagId: String?,
    phase: String = "focus",
    cycle: Int = 0,
    running: Boolean = false,
    deviceId: String,
    now: Long = System.currentTimeMillis()
): TimerState {
    val tags = activeTags
    val tag = tags.firstOrNull { it.id == tagId }
        ?: tags.firstOrNull { it.id == timer?.tagId }
        ?: tags.firstOrNull()
    val minutes = minutesForPhase(tag, phase)
    return TimerState(
        phase = phase,
        tagId = tag?.id ?: "",
        taskId = "",
        note = "",
        running = running,
        startedAt = now,
        runStartedAt = if (running) now else 0L,
        accumulatedSec = 0.0,
        durationSec = (minutes * 60.0).roundToInt().coerceAtLeast(1),
        sessionId = newId("session", deviceId, now),
        cycle = cycle.coerceAtLeast(0),
        updatedAt = now,
        device = deviceId
    )
}

fun Store.ensureTimer(deviceId: String, now: Long = System.currentTimeMillis()): Store {
    val current = timer
    if (current == null || current.phase.isBlank()) {
        return copy(timer = makeTimer(activeTagId, "focus", 0, false, deviceId, now))
    }
    if (tagById(current.tagId) == null) {
        return copy(timer = current.copy(tagId = activeTags.firstOrNull()?.id ?: "", updatedAt = now))
    }
    return this
}

val Store.activeTagId: String
    get() = timer?.tagId?.takeIf { id -> activeTags.any { it.id == id } }
        ?: activeTags.firstOrNull()?.id
        ?: ""

val Store.activeTag: Tag?
    get() = tagById(activeTagId) ?: activeTags.firstOrNull()

fun computeSecondsLeft(timer: TimerState?, now: Long = System.currentTimeMillis()): Int {
    if (timer == null) return 0
    val duration = timer.durationSec.coerceAtLeast(1)
    val accumulated = timer.accumulatedSec.coerceAtLeast(0.0)
    val runningPart = if (timer.running) (now - timer.runStartedAt) / 1000.0 else 0.0
    return kotlin.math.ceil(duration - accumulated - runningPart).toInt().coerceAtLeast(0)
}

fun elapsedSecondsExact(timer: TimerState?, now: Long = System.currentTimeMillis()): Double {
    if (timer == null) return 0.0
    val accumulated = timer.accumulatedSec.coerceAtLeast(0.0)
    val runningPart = if (timer.running) (now - timer.runStartedAt) / 1000.0 else 0.0
    return (accumulated + runningPart).coerceIn(0.0, timer.durationSec.toDouble().coerceAtLeast(0.0))
}

fun elapsedSeconds(timer: TimerState?, now: Long = System.currentTimeMillis()): Int =
    elapsedSecondsExact(timer, now).roundToInt()

fun TimerState.progress(now: Long = System.currentTimeMillis()): Float {
    val duration = durationSec.coerceAtLeast(1).toFloat()
    return (1f - computeSecondsLeft(this, now).toFloat() / duration).coerceIn(0f, 1f)
}

fun Store.finalizeSession(
    value: TimerState,
    completed: Boolean,
    deviceId: String,
    now: Long = System.currentTimeMillis()
): Pair<Store, FocusSession?> {
    val elapsed = elapsedSeconds(value, now)
    if (elapsed < 15) return this to null
    val session = FocusSession(
        id = value.sessionId.ifBlank { newId("session", deviceId, now) },
        tagId = value.tagId,
        taskId = value.taskId,
        phase = value.phase,
        startedAt = value.startedAt.takeIf { it > 0 } ?: (now - elapsed * 1000L),
        endedAt = now,
        durationSec = elapsed,
        plannedSec = value.durationSec.coerceAtLeast(1),
        completed = completed,
        note = value.note,
        updatedAt = now,
        device = deviceId,
        deleted = false
    )
    val existed = focusSessions.any { it.id == session.id }
    val sessions = if (existed) focusSessions else sortSessions(focusSessions + session)
    return copy(focusSessions = sessions) to if (existed) null else session
}

fun Store.advanceAfter(
    current: TimerState,
    deviceId: String,
    now: Long = System.currentTimeMillis()
): TimerState {
    val tag = tagById(current.tagId) ?: activeTag
    val phase = current.phase
    val cycles = (tag?.cycles ?: 4).coerceAtLeast(1)
    return if (phase == "focus") {
        val count = current.cycle.coerceAtLeast(0) + 1
        val long = count >= cycles
        makeTimer(
            tag?.id ?: "",
            if (long) "longBreak" else "shortBreak",
            count,
            tag?.autoStartBreak == true,
            deviceId,
            now
        )
    } else {
        val cycle = if (phase == "longBreak") 0 else current.cycle.coerceAtLeast(0)
        makeTimer(tag?.id ?: "", "focus", cycle, tag?.autoStartFocus == true, deviceId, now)
    }
}

// ── Stats ────────────────────────────────────────────────────────────────

fun Store.sessionsInRange(days: Int, phase: String? = null, todayKey: String = dateKey()): List<FocusSession> {
    val startMs = startOfDayMillis(addDays(todayKey, -(days.coerceAtLeast(1) - 1)))
    return activeSessions.filter { session ->
        (phase == null || session.phase == phase) && session.endedAt >= startMs
    }
}

fun Store.secondsInRange(days: Int, phase: String? = null, todayKey: String = dateKey()): Long =
    sessionsInRange(days, phase, todayKey).sumOf { it.durationSec.coerceAtLeast(0).toLong() }

fun Store.focusMinutesInRange(days: Int, todayKey: String = dateKey()): Int =
    (secondsInRange(days, "focus", todayKey) / 60.0).roundToInt()

fun Store.todayFocusSeconds(todayKey: String = dateKey()): Int = activeSessions
    .filter { it.phase == "focus" && dateKey(it.endedAt) == todayKey }
    .sumOf { it.durationSec.coerceAtLeast(0) }

fun Store.todayFocusMinutes(todayKey: String = dateKey()): Int =
    (todayFocusSeconds(todayKey) / 60.0).roundToInt()

fun Store.todayFocusSessions(todayKey: String = dateKey()): Int = activeSessions.count {
    it.phase == "focus" && dateKey(it.endedAt) == todayKey
}

fun Store.weekFocusMinutes(todayKey: String = dateKey(), monday: Boolean = true): Int {
    val days = thisWeekDays(todayKey, monday)
    return (activeSessions
        .filter { it.phase == "focus" && dateKey(it.endedAt) in days }
        .sumOf { it.durationSec.toLong() } / 60.0).roundToInt()
}

fun Store.totalFocusMinutes(): Int = (activeSessions
    .filter { it.phase == "focus" }
    .sumOf { it.durationSec.toLong() } / 60.0).roundToInt()

fun Store.focusStreak(todayKey: String = dateKey()): Int {
    val days = activeSessions
        .filter { it.phase == "focus" && it.durationSec >= 30 }
        .map { dateKey(it.endedAt) }
        .toHashSet()
    var cursor = todayKey
    if (cursor !in days) cursor = addDays(cursor, -1)
    var streak = 0
    while (cursor in days && streak < 3650) {
        streak++
        cursor = addDays(cursor, -1)
    }
    return streak
}

fun Store.tagSecondsInRange(tagId: String, days: Int, todayKey: String = dateKey()): Long =
    sessionsInRange(days, "focus", todayKey)
        .filter { it.tagId == tagId }
        .sumOf { it.durationSec.coerceAtLeast(0).toLong() }

fun Store.tagTodayMinutes(tagId: String, todayKey: String = dateKey()): Int =
    (tagSecondsInRange(tagId, 1, todayKey) / 60.0).roundToInt()

fun Store.tagPercent(tagId: String, days: Int, todayKey: String = dateKey()): Float {
    val total = secondsInRange(days, "focus", todayKey)
    return if (total > 0) tagSecondsInRange(tagId, days, todayKey).toFloat() / total else 0f
}

fun Store.tagStreak(tagId: String, todayKey: String = dateKey()): Int {
    val dates = activeSessions
        .filter { it.phase == "focus" && it.tagId == tagId }
        .map { dateKey(it.endedAt) }
        .toHashSet()
    var key = todayKey
    if (key !in dates) key = addDays(key, -1)
    var streak = 0
    while (key in dates) {
        streak++
        key = addDays(key, -1)
    }
    return streak
}

fun Store.tagGoalProgress(tag: Tag?, todayKey: String = dateKey()): Float {
    val goal = (tag?.dailyGoalMinutes ?: 0).coerceAtLeast(0)
    if (goal <= 0) return -1f
    return (tagTodayMinutes(tag?.id ?: "", todayKey).toFloat() / goal).coerceIn(0f, 1f)
}

data class TagStat(
    val tagId: String,
    val tag: Tag?,
    val name: String,
    val seconds: Long,
    val sessions: Int,
    val percent: Float,
    val streak: Int,
    val goalMinutes: Int,
    val todayMinutes: Int
)

fun Store.tagStats(days: Int, todayKey: String = dateKey()): List<TagStat> {
    val totals = LinkedHashMap<String, Pair<Long, Int>>()
    for (session in sessionsInRange(days, "focus", todayKey)) {
        val id = session.tagId
        val current = totals[id] ?: (0L to 0)
        totals[id] = (current.first + session.durationSec.coerceAtLeast(0)) to (current.second + 1)
    }
    val allSeconds = secondsInRange(days, "focus", todayKey).coerceAtLeast(1)
    return totals.map { (id, value) ->
        val tag = tagById(id)
        TagStat(
            tagId = id,
            tag = tag,
            name = tag?.name ?: "Untagged",
            seconds = value.first,
            sessions = value.second,
            percent = value.first.toFloat() / allSeconds,
            streak = tagStreak(id, todayKey),
            goalMinutes = (tag?.dailyGoalMinutes ?: 0).coerceAtLeast(0),
            todayMinutes = tagTodayMinutes(id, todayKey)
        )
    }.sortedByDescending { it.seconds }
}

data class DayFocus(
    val date: String,
    val label: String,
    val focus: Long,
    val breaks: Long
)

fun Store.dailyFocusSeries(days: Int, todayKey: String = dateKey()): List<DayFocus> {
    val keys = lastNDays(days, todayKey)
    val index = keys.associateWith { DayFocus(it, weekdayShort(it), 0L, 0L) }.toMutableMap()
    for (session in activeSessions) {
        val key = dateKey(session.endedAt)
        val day = index[key] ?: continue
        index[key] = if (session.phase == "focus") {
            day.copy(focus = day.focus + session.durationSec.coerceAtLeast(0))
        } else {
            day.copy(breaks = day.breaks + session.durationSec.coerceAtLeast(0))
        }
    }
    return keys.mapNotNull { index[it] }
}

data class TagSegment(val tagId: String, val tag: Tag?, val seconds: Long)
data class DayTagFocus(
    val date: String,
    val label: String,
    val total: Long,
    val segments: List<TagSegment>
)

fun Store.dailyTagFocusSeries(days: Int, todayKey: String = dateKey()): List<DayTagFocus> {
    val keys = lastNDays(days, todayKey)
    val index = keys.associateWith {
        DayTagFocus(it, weekdayShort(it), 0L, emptyList())
    }.toMutableMap()
    for (session in activeSessions) {
        if (session.phase != "focus") continue
        val key = dateKey(session.endedAt)
        val day = index[key] ?: continue
        val tagId = session.tagId
        val existing = day.segments.firstOrNull { it.tagId == tagId }
        val seconds = session.durationSec.coerceAtLeast(0).toLong()
        val segments = if (existing == null) {
            day.segments + TagSegment(tagId, tagById(tagId), seconds)
        } else {
            day.segments.map {
                if (it.tagId == tagId) it.copy(seconds = it.seconds + seconds) else it
            }
        }
        index[key] = day.copy(total = day.total + seconds, segments = segments)
    }
    return keys.mapNotNull { index[it]?.let { day -> day.copy(segments = day.segments.sortedByDescending { it.seconds }) } }
}

fun Store.tagFocusSeries(tagId: String, days: Int, todayKey: String = dateKey()): List<DayTagFocus> =
    dailyTagFocusSeries(days, todayKey).map { day ->
        val seconds = day.segments.firstOrNull { it.tagId == tagId }?.seconds ?: 0L
        day.copy(
            total = seconds,
            segments = if (seconds > 0) listOf(TagSegment(tagId, tagById(tagId), seconds)) else emptyList()
        )
    }

data class HeatCell(val date: String, val value: Long, val future: Boolean)

fun Store.focusHeatmapWeeks(
    weeks: Int,
    tagId: String = "",
    todayKey: String = dateKey(),
    monday: Boolean = true
): List<HeatCell> {
    val count = weeks.coerceAtLeast(1)
    val start = addDays(todayKey, -weekStartOffset(todayKey, monday) - (count - 1) * 7)
    val out = ArrayList<HeatCell>(count * 7)
    for (i in 0 until count * 7) {
        val key = addDays(start, i)
        var value = 0L
        for (session in activeSessions) {
            if (session.phase != "focus") continue
            if (tagId.isNotEmpty() && session.tagId != tagId) continue
            if (dateKey(session.endedAt) == key) value += session.durationSec.coerceAtLeast(0)
        }
        out.add(HeatCell(key, value, key > todayKey))
    }
    return out
}

data class TimeOfDayStat(val id: String, val label: String, val range: String, val seconds: Long)

fun Store.timeOfDayStats(days: Int, todayKey: String = dateKey()): List<TimeOfDayStat> {
    val buckets = listOf(
        TimeOfDayStat("night", "Night", "00–06", 0L),
        TimeOfDayStat("morning", "Morning", "06–12", 0L),
        TimeOfDayStat("afternoon", "Afternoon", "12–18", 0L),
        TimeOfDayStat("evening", "Evening", "18–24", 0L)
    ).toMutableList()
    for (session in sessionsInRange(days, "focus", todayKey)) {
        val local = Instant.ofEpochMilli(session.startedAt.takeIf { it > 0 } ?: session.endedAt)
            .atZone(ZoneId.systemDefault())
        val idx = (local.hour / 6).coerceIn(0, 3)
        buckets[idx] = buckets[idx].copy(seconds = buckets[idx].seconds + session.durationSec.coerceAtLeast(0))
    }
    return buckets
}

data class BestDay(val date: String, val focusSeconds: Long)

fun Store.bestFocusDay(days: Int, todayKey: String = dateKey()): BestDay {
    var best = BestDay("", 0L)
    for (day in dailyFocusSeries(days, todayKey)) {
        if (day.focus > best.focusSeconds) best = BestDay(day.date, day.focus)
    }
    return best
}

fun Store.breakRatio(days: Int, todayKey: String = dateKey()): Pair<Long, Long> {
    val focus = secondsInRange(days, "focus", todayKey)
    val breaks = secondsInRange(days, "shortBreak", todayKey) +
        secondsInRange(days, "longBreak", todayKey)
    return focus to breaks
}

fun Store.overdueTaskCount(todayKey: String = dateKey()): Int = activeTasks.count {
    !it.done && it.due.isNotEmpty() && it.due < todayKey
}

fun Store.dueTodayTaskCount(todayKey: String = dateKey()): Int = activeTasks.count {
    !it.done && it.due.isNotEmpty() && it.due <= todayKey
}

fun Store.openTaskCount(): Int = activeTasks.count { !it.done }
fun Store.doneTaskCount(): Int = activeTasks.count { it.done }

fun Store.recentSessions(limit: Int = 8): List<FocusSession> =
    activeSessions.take(limit.coerceAtLeast(1))

data class HabitStat(
    val habit: Habit,
    val name: String,
    val due: Int,
    val done: Int,
    val rate: Float,
    val streak: Int,
    val best: Int
)

/** Mirrors GritService.habitStats: scheduled days in range, completion and streaks. */
fun Store.habitStats(days: Int, todayKey: String = dateKey()): List<HabitStat> {
    val keys = lastNDays(days.coerceAtLeast(1), todayKey)
    return activeHabits.map { habit ->
        var due = 0
        var done = 0
        for (key in keys) {
            if (!scheduledOn(habit, key)) continue
            due++
            if (habitDone(habit, key)) done++
        }
        HabitStat(
            habit = habit,
            name = habit.name,
            due = due,
            done = done,
            rate = if (due > 0) done.toFloat() / due else 0f,
            streak = currentStreak(habit, todayKey),
            best = bestStreak(habit, todayKey)
        )
    }.sortedByDescending { it.rate }
}

// ── Migration of foreign records (defensive helpers) ─────────────────────

fun decodeStore(raw: String): Store = GritJson.decodeFromString(Store.serializer(), raw)

fun encodeStore(store: Store): String = GritJson.encodeToString(Store.serializer(), store)

fun nearlyEqual(a: Double, b: Double, epsilon: Double = 0.0001): Boolean = abs(a - b) < epsilon
