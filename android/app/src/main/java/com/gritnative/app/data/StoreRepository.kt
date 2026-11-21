package com.gritnative.app.data

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.time.format.DateTimeFormatter
import java.util.UUID
import kotlin.math.min
import kotlin.math.roundToInt

data class PhaseCompletion(
    val session: FocusSession?,
    val previousPhase: String,
    val next: TimerState,
    val tag: Tag?,
    val focusCompleted: Boolean
)

/**
 * The single source of truth for the app and the foreground timer service.
 * All record mutations are stamped with this device's id and persisted with a
 * short debounce; timer actions persist immediately so a process death cannot
 * lose a session start.
 */
object StoreRepository {
    private const val TAG = "GritRepository"
    private const val STORE_FILE = "grit-store.json"
    private const val PREF_DEVICE = "deviceId"
    private const val PREF_THEME = "themeMode"
    private const val PREF_LANGUAGE = "appLanguage"
    private const val PREF_COLOR_PROFILE = "colorProfile"
    private const val PREF_DYNAMIC = "dynamicColor"
    private const val PREF_AMOLED = "amoled"
    private const val PREF_WEEK_MONDAY = "weekStartMonday"
    private const val PREF_SHOW_COMPLETED = "showCompleted"
    private const val PREF_AUTO_SYNC = "autoSync"
    private const val PREF_ALARM_SOUND = "alarmSound"
    private const val PREF_LAST_SYNC = "lastSyncAt"
    private const val PREF_DISPLAY_NAME = "leaderboardDisplayName"
    private const val PREF_DISPLAY_NAME_AT = "leaderboardDisplayNameUpdatedAt"
    private const val PREF_PUBLIC_TAGS = "leaderboardPublicTags"
    private const val PREF_PUBLIC_TAGS_AT = "leaderboardPublicTagsUpdatedAt"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Any()
    private val saveMutex = Mutex()
    private lateinit var appContext: Context
    private lateinit var prefs: SecurePrefs
    private val loaded = CompletableDeferred<Unit>()

    @Volatile
    private var initialized = false
    private var saveJob: Job? = null
    private var autoSyncJob: Job? = null
    @Volatile
    private var celebratedDate: String = ""

    private val _store = MutableStateFlow(emptyStore(""))
    val store: StateFlow<Store> = _store.asStateFlow()

    private val _ready = MutableStateFlow(false)
    /** False until the on-disk store has been read (or a fresh one created). */
    val ready: StateFlow<Boolean> = _ready.asStateFlow()

    private val _settings = MutableStateFlow(AppSettings())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    private val _syncState = MutableStateFlow(SyncUiState())
    val syncState: StateFlow<SyncUiState> = _syncState.asStateFlow()

    private val _events = MutableSharedFlow<GritEvent>(
        extraBufferCapacity = 16,
        onBufferOverflow = kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
    )
    val events = _events.asSharedFlow()

    /** Installed by the Application; starts the service / arms the fallback alarm. */
    @Volatile
    var timerObserver: ((Store) -> Unit)? = null

    val deviceId: String get() = _settings.value.deviceId
    val currentStore: Store get() = _store.value
    val currentSettings: AppSettings get() = _settings.value
    val isReady: Boolean get() = initialized && loaded.isCompleted

    fun init(context: Context) {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            appContext = context.applicationContext
            prefs = SecurePrefs(appContext)
            val id = prefs.getString(PREF_DEVICE).ifBlank {
                "dev-" + UUID.randomUUID().toString().replace("-", "").take(12)
            }
            prefs.putString(PREF_DEVICE, id)
            val theme = runCatching {
                ThemeMode.valueOf(prefs.getString(PREF_THEME, ThemeMode.SYSTEM.name))
            }.getOrDefault(ThemeMode.SYSTEM)
            val settings = AppSettings(
                deviceId = id,
                themeMode = theme,
                language = prefs.getString(PREF_LANGUAGE, "system"),
                colorProfile = prefs.getString(PREF_COLOR_PROFILE, "dynamic"),
                dynamicColor = prefs.getBool(PREF_DYNAMIC, true),
                amoled = prefs.getBool(PREF_AMOLED, false),
                weekStartMonday = prefs.getBool(PREF_WEEK_MONDAY, true),
                showCompleted = prefs.getBool(PREF_SHOW_COMPLETED, true),
                autoSync = prefs.getBool(PREF_AUTO_SYNC, true),
                alarmSound = prefs.getBool(PREF_ALARM_SOUND, true),
                lastSyncAt = prefs.getString(PREF_LAST_SYNC),
                displayName = prefs.getString(PREF_DISPLAY_NAME),
                displayNameUpdatedAt = prefs.getString(PREF_DISPLAY_NAME_AT).toLongOrNull() ?: 0L,
                publicTagIds = prefs.getStringSet(PREF_PUBLIC_TAGS),
                publicTagsUpdatedAt = prefs.getString(PREF_PUBLIC_TAGS_AT).toLongOrNull() ?: 0L
            )
            _settings.value = settings
            _store.value = emptyStore(id)
            _syncState.value = SyncUiState(
                status = if (AuthService.isSignedIn()) SyncStatus.IDLE else SyncStatus.OFF,
                message = if (AuthService.isSignedIn()) "" else "Sign in to sync",
                lastSyncAt = settings.lastSyncAt
            )
            initialized = true
        }
        scope.launch { loadStore() }
    }

    private fun storeFile(): File = File(appContext.filesDir, STORE_FILE)

    private suspend fun loadStore() {
        val file = storeFile()
        var parsed: Store? = null
        if (file.exists()) {
            try {
                val raw = file.readText()
                if (raw.isNotBlank()) parsed = decodeStore(raw)
            } catch (error: Exception) {
                Log.w(TAG, "Unreadable grit store; keeping a backup", error)
                runCatching {
                    file.copyTo(File(appContext.filesDir, "$STORE_FILE.corrupt-${System.currentTimeMillis()}"))
                }
            }
        }
        val id = deviceId
        val base = parsed?.let { normalizeStore(it, id) } ?: emptyStore(id)
        val withTimer = base.ensureTimer(id)
        synchronized(lock) { _store.value = withTimer }
        applyLeaderboardPrefs(withTimer.leaderboard)
        if (parsed == null) saveNow()
        if (!loaded.isCompleted) loaded.complete(Unit)
        _ready.value = true
    }

    suspend fun awaitLoaded() {
        loaded.await()
    }

    fun appContext(): Context = appContext

    // ── Persistence ─────────────────────────────────────────────────────

    private fun writeStore() {
        try {
            val snapshot = synchronized(lock) {
                _store.value.ensureTimer(deviceId).copy(
                    leaderboard = leaderboardFromSettings(),
                    updatedAt = System.currentTimeMillis(),
                    device = deviceId
                )
            }
            val bytes = encodeStore(snapshot).toByteArray(Charsets.UTF_8)
            val target = storeFile()
            val temp = File(target.parentFile, "$STORE_FILE.tmp")
            FileOutputStream(temp).use { out ->
                out.write(bytes)
                out.flush()
                out.fd.sync()
            }
            try {
                Files.move(
                    temp.toPath(), target.toPath(),
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE
                )
            } catch (_: Exception) {
                Files.move(temp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        } catch (error: Exception) {
            Log.w(TAG, "Could not persist grit store", error)
        }
    }

    fun saveSoon() {
        if (!initialized) return
        saveJob?.cancel()
        saveJob = scope.launch {
            delay(350)
            saveMutex.withLock { writeStore() }
        }
    }

    fun saveNow() {
        if (!initialized) return
        saveJob?.cancel()
        saveJob = scope.launch {
            saveMutex.withLock { writeStore() }
        }
    }

    private fun mutate(
        saveImmediately: Boolean = false,
        scheduleSync: Boolean = true,
        block: (Store) -> Store
    ): Store {
        val next: Store
        synchronized(lock) {
            next = block(_store.value)
            _store.value = next
        }
        if (saveImmediately) saveNow() else saveSoon()
        if (scheduleSync) scheduleSyncIfConfigured()
        return next
    }

    private fun dispatchTimer() {
        val callback = timerObserver ?: return
        runCatching { callback(_store.value) }
            .onFailure { Log.w(TAG, "Timer observer failed", it) }
    }

    fun emitEvent(event: GritEvent) {
        scope.launch { _events.emit(event) }
    }

    private fun scheduleSyncIfConfigured() {
        val settings = _settings.value
        if (!settings.autoSync || !AuthService.isSignedIn()) return
        autoSyncJob?.cancel()
        autoSyncJob = scope.launch {
            delay(500)
            val current = _settings.value
            if (current.autoSync && AuthService.isSignedIn()) {
                FirestoreSync.syncNow(silent = true)
            }
        }
    }

    // ── Settings ────────────────────────────────────────────────────────

    private fun updateSettings(transform: (AppSettings) -> AppSettings) {
        _settings.value = transform(_settings.value)
    }

    fun setThemeMode(mode: ThemeMode) {
        prefs.putString(PREF_THEME, mode.name)
        updateSettings { it.copy(themeMode = mode) }
    }

    fun setDynamicColor(enabled: Boolean) {
        prefs.putBool(PREF_DYNAMIC, enabled)
        updateSettings { it.copy(dynamicColor = enabled) }
    }

    fun setLanguage(language: String) {
        val clean = if (language == "ar" || language == "en") language else "system"
        prefs.putString(PREF_LANGUAGE, clean)
        updateSettings { it.copy(language = clean) }
    }

    fun setColorProfile(profile: String) {
        val clean = profile.takeIf {
            it in setOf("dynamic", "forest", "ocean", "amber", "rose", "violet", "slate")
        } ?: "dynamic"
        prefs.putString(PREF_COLOR_PROFILE, clean)
        prefs.putBool(PREF_DYNAMIC, clean == "dynamic")
        updateSettings { it.copy(colorProfile = clean, dynamicColor = clean == "dynamic") }
    }

    fun setAmoled(enabled: Boolean) {
        prefs.putBool(PREF_AMOLED, enabled)
        updateSettings { it.copy(amoled = enabled) }
    }

    fun setWeekStartMonday(enabled: Boolean) {
        prefs.putBool(PREF_WEEK_MONDAY, enabled)
        updateSettings { it.copy(weekStartMonday = enabled) }
    }

    fun setShowCompleted(enabled: Boolean) {
        prefs.putBool(PREF_SHOW_COMPLETED, enabled)
        updateSettings { it.copy(showCompleted = enabled) }
    }

    fun setAlarmSound(enabled: Boolean) {
        prefs.putBool(PREF_ALARM_SOUND, enabled)
        updateSettings { it.copy(alarmSound = enabled) }
    }

    fun setAutoSync(enabled: Boolean) {
        prefs.putBool(PREF_AUTO_SYNC, enabled)
        updateSettings { it.copy(autoSync = enabled) }
        if (enabled) scheduleSyncIfConfigured()
    }

    fun setLastSyncAt(value: String) {
        prefs.putString(PREF_LAST_SYNC, value)
        updateSettings { it.copy(lastSyncAt = value) }
        _syncState.value = _syncState.value.copy(lastSyncAt = value)
    }

    private fun leaderboardFromSettings(settings: AppSettings = _settings.value): LeaderboardPrefs =
        LeaderboardPrefs(
            displayName = settings.displayName,
            nameUpdatedAt = settings.displayNameUpdatedAt,
            publicTagIds = settings.publicTagIds.toList(),
            tagsUpdatedAt = settings.publicTagsUpdatedAt,
            updatedAt = maxOf(settings.displayNameUpdatedAt, settings.publicTagsUpdatedAt)
        )

    private fun storeWithLeaderboard(store: Store): Store =
        store.copy(leaderboard = leaderboardFromSettings())

    private fun syncLeaderboardToStore() {
        if (!initialized) return
        val now = System.currentTimeMillis()
        mutate {
            it.copy(leaderboard = leaderboardFromSettings(), updatedAt = now, device = deviceId)
        }
    }

    /**
     * Adopt synced preferences from the private document (or a leaderboard row)
     * without letting an older device overwrite a newer local change.
     */
    private fun applyLeaderboardPrefs(prefs: LeaderboardPrefs) {
        val current = _settings.value
        var name = current.displayName
        var nameAt = current.displayNameUpdatedAt
        var ids = current.publicTagIds
        var tagsAt = current.publicTagsUpdatedAt
        if (prefs.nameUpdatedAt > nameAt) {
            name = prefs.displayName.trim().take(40)
            nameAt = prefs.nameUpdatedAt
        }
        if (prefs.tagsUpdatedAt > tagsAt) {
            ids = prefs.publicTagIds.toSet()
            tagsAt = prefs.tagsUpdatedAt
        }
        if (name == current.displayName && nameAt == current.displayNameUpdatedAt &&
            ids == current.publicTagIds && tagsAt == current.publicTagsUpdatedAt
        ) return
        prefsStore(ids, name, nameAt, tagsAt)
        updateSettings {
            it.copy(
                displayName = name,
                displayNameUpdatedAt = nameAt,
                publicTagIds = ids,
                publicTagsUpdatedAt = tagsAt
            )
        }
    }

    private fun prefsStore(ids: Set<String>, name: String, nameAt: Long, tagsAt: Long) {
        prefs.putString(PREF_DISPLAY_NAME, name)
        prefs.putString(PREF_DISPLAY_NAME_AT, nameAt.toString())
        prefs.putStringSet(PREF_PUBLIC_TAGS, ids)
        prefs.putString(PREF_PUBLIC_TAGS_AT, tagsAt.toString())
    }

    fun setDisplayName(value: String) {
        val clean = value.trim().take(40)
        if (clean == _settings.value.displayName) return
        val now = System.currentTimeMillis()
        prefs.putString(PREF_DISPLAY_NAME, clean)
        prefs.putString(PREF_DISPLAY_NAME_AT, now.toString())
        updateSettings { it.copy(displayName = clean, displayNameUpdatedAt = now) }
        syncLeaderboardToStore()
    }

    fun setTagPublic(tagId: String, isPublic: Boolean) {
        if (tagId.isBlank()) return
        val current = _settings.value.publicTagIds
        if ((tagId in current) == isPublic) return
        val next = current.toMutableSet().apply {
            if (isPublic) add(tagId) else remove(tagId)
        }
        val now = System.currentTimeMillis()
        prefs.putStringSet(PREF_PUBLIC_TAGS, next)
        prefs.putString(PREF_PUBLIC_TAGS_AT, now.toString())
        updateSettings { it.copy(publicTagIds = next, publicTagsUpdatedAt = now) }
        syncLeaderboardToStore()
    }

    /**
     * Another device published a newer leaderboard preference. Adopt only the
     * fields whose timestamp is newer so two devices cannot erase each other's
     * name or public-tag changes with a stale write.
     */
    fun adoptLeaderboardSettings(
        displayName: String,
        nameUpdatedAt: Long,
        publicTagIds: Set<String>,
        tagsUpdatedAt: Long
    ) {
        val current = _settings.value
        val nextName = if (nameUpdatedAt > current.displayNameUpdatedAt) {
            displayName.trim().take(40)
        } else {
            current.displayName
        }
        val nextNameAt = maxOf(current.displayNameUpdatedAt, nameUpdatedAt)
        val nextTags = if (tagsUpdatedAt > current.publicTagsUpdatedAt) {
            publicTagIds.toSet()
        } else {
            current.publicTagIds
        }
        val nextTagsAt = maxOf(current.publicTagsUpdatedAt, tagsUpdatedAt)
        if (nextName == current.displayName &&
            nextNameAt == current.displayNameUpdatedAt &&
            nextTags == current.publicTagIds &&
            nextTagsAt == current.publicTagsUpdatedAt
        ) {
            return
        }
        prefsStore(nextTags, nextName, nextNameAt, nextTagsAt)
        updateSettings {
            it.copy(
                displayName = nextName,
                displayNameUpdatedAt = nextNameAt,
                publicTagIds = nextTags,
                publicTagsUpdatedAt = nextTagsAt
            )
        }
        syncLeaderboardToStore()
    }

    fun updateSyncState(state: SyncUiState) {
        _syncState.value = state
        if (state.lastSyncAt.isNotBlank()) setLastSyncAt(state.lastSyncAt)
    }

    fun markSyncMessage(status: SyncStatus, message: String) {
        _syncState.value = _syncState.value.copy(status = status, message = message)
    }

    fun syncTimestamp(): String = java.time.LocalDateTime.now()
        .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))

    // ── Habits ──────────────────────────────────────────────────────────

    fun setHabitEntry(habitId: String, date: String, value: Int) {
        val habit = _store.value.habitById(habitId) ?: return
        val now = System.currentTimeMillis()
        val dev = deviceId
        val clean = value.coerceIn(0, habit.target.coerceAtLeast(1))
        val updated = mutate { store ->
            val existing = store.entryFor(habitId, date)
            val entries = if (existing != null) {
                store.entries.map {
                    if (it.id == existing.id) {
                        it.copy(value = clean, updatedAt = now, device = dev)
                    } else it
                }
            } else {
                store.entries + Entry(
                    id = newId("entry", dev, now),
                    habitId = habitId,
                    date = date,
                    value = clean,
                    note = "",
                    updatedAt = now,
                    device = dev,
                    deleted = false
                )
            }
            store.copy(entries = entries, updatedAt = now, device = dev)
        }
        maybeCelebrateAllHabits(updated)
    }

    fun incrementHabit(habitId: String, delta: Int = 1) {
        val habit = _store.value.habitById(habitId) ?: return
        val today = dateKey()
        setHabitEntry(habitId, today, _store.value.habitValue(habitId, today) + delta)
    }

    fun toggleHabitDone(habitId: String) {
        val habit = _store.value.habitById(habitId) ?: return
        val today = dateKey()
        val next = if (_store.value.habitDone(habit, today)) 0 else habit.target.coerceAtLeast(1)
        setHabitEntry(habitId, today, next)
    }

    private fun maybeCelebrateAllHabits(store: Store) {
        val today = dateKey()
        val due = store.todayHabits
        if (due.isEmpty()) return
        if (due.all { store.habitDone(it, today) } && celebratedDate != today) {
            celebratedDate = today
            emitEvent(GritEvent.AllHabitsDone(done = due.size, total = due.size))
        }
    }

    fun createHabit(draft: HabitDraft): Habit {
        val now = System.currentTimeMillis()
        val dev = deviceId
        val habit = Habit(
            id = newId("habit", dev, now),
            name = draft.name.trim().ifBlank { "New habit" },
            icon = draft.icon.ifBlank { "check_circle" },
            categoryId = draft.categoryId.ifBlank { _store.value.activeCategories.firstOrNull()?.id ?: "" },
            days = draft.days.filter { it in 0..6 }.distinct().ifEmpty { (0..6).toList() },
            time = draft.time.trim(),
            target = draft.target.coerceAtLeast(1),
            unit = draft.unit.trim().ifBlank { "times" },
            note = draft.note.trim(),
            createdAt = now,
            updatedAt = now,
            device = dev,
            deleted = false
        )
        mutate { it.copy(habits = it.habits + habit) }
        return habit
    }

    fun updateHabit(id: String, draft: HabitDraft): Boolean {
        val now = System.currentTimeMillis()
        val dev = deviceId
        var found = false
        mutate { store ->
            store.copy(habits = store.habits.map { habit ->
                if (habit.id != id) return@map habit
                found = true
                habit.copy(
                    name = draft.name.trim().ifBlank { "New habit" },
                    icon = draft.icon.ifBlank { "check_circle" },
                    categoryId = draft.categoryId,
                    days = draft.days.filter { it in 0..6 }.distinct().ifEmpty { (0..6).toList() },
                    time = draft.time.trim(),
                    target = draft.target.coerceAtLeast(1),
                    unit = draft.unit.trim().ifBlank { "times" },
                    note = draft.note.trim(),
                    updatedAt = now,
                    device = dev
                )
            })
        }
        return found
    }

    fun deleteHabit(id: String) {
        val now = System.currentTimeMillis()
        val dev = deviceId
        mutate { store ->
            store.copy(habits = store.habits.map {
                if (it.id == id) it.copy(deleted = true, updatedAt = now, device = dev) else it
            })
        }
    }

    // ── Categories ──────────────────────────────────────────────────────

    fun createCategory(draft: CategoryDraft): Category {
        val now = System.currentTimeMillis()
        val dev = deviceId
        val category = Category(
            id = newId("cat", dev, now),
            name = draft.name.trim().ifBlank { "New category" },
            accent = draft.accent.ifBlank { "primary" },
            icon = draft.icon.ifBlank { "label" },
            updatedAt = now,
            device = dev,
            deleted = false
        )
        mutate { it.copy(categories = it.categories + category) }
        return category
    }

    fun updateCategory(id: String, draft: CategoryDraft) {
        val now = System.currentTimeMillis()
        val dev = deviceId
        mutate { store ->
            store.copy(categories = store.categories.map {
                if (it.id == id) it.copy(
                    name = draft.name.trim().ifBlank { "New category" },
                    accent = draft.accent.ifBlank { "primary" },
                    icon = draft.icon.ifBlank { "label" },
                    updatedAt = now,
                    device = dev
                ) else it
            })
        }
    }

    fun deleteCategory(id: String) {
        val now = System.currentTimeMillis()
        val dev = deviceId
        mutate { store ->
            val fallback = store.activeCategories.firstOrNull { it.id != id }?.id ?: ""
            store.copy(
                categories = store.categories.map {
                    if (it.id == id) it.copy(deleted = true, updatedAt = now, device = dev) else it
                },
                habits = store.habits.map {
                    if (it.categoryId == id) it.copy(categoryId = fallback, updatedAt = now, device = dev) else it
                },
                tasks = store.tasks.map {
                    if (it.categoryId == id) it.copy(categoryId = fallback, updatedAt = now, device = dev) else it
                }
            )
        }
    }

    // ── Tags ────────────────────────────────────────────────────────────

    fun createTag(draft: TagDraft): Tag {
        val now = System.currentTimeMillis()
        val dev = deviceId
        val tag = Tag(
            id = newId("tag", dev, now),
            name = draft.name.trim().ifBlank { "New tag" },
            accent = draft.accent.ifBlank { "primary" },
            color = draft.color.trim(),
            shape = draft.shape.ifBlank { "Cookie7Sided" },
            icon = draft.icon.ifBlank { "local_fire_department" },
            focusMinutes = draft.focusMinutes.coerceAtLeast(1),
            breakMinutes = draft.breakMinutes.coerceAtLeast(1),
            longBreakMinutes = draft.longBreakMinutes.coerceAtLeast(1),
            cycles = draft.cycles.coerceAtLeast(1),
            dailyGoalMinutes = draft.dailyGoalMinutes.coerceIn(0, 720),
            autoStartBreak = draft.autoStartBreak,
            autoStartFocus = draft.autoStartFocus,
            updatedAt = now,
            device = dev,
            deleted = false
        )
        mutate { it.copy(tags = it.tags + tag) }
        return tag
    }

    fun updateTag(id: String, draft: TagDraft): Boolean {
        val now = System.currentTimeMillis()
        val dev = deviceId
        var found = false
        mutate { store ->
            val tags = store.tags.map { tag ->
                if (tag.id != id) return@map tag
                found = true
                normalizeTag(tag.copy(
                    name = draft.name.trim().ifBlank { "New tag" },
                    accent = draft.accent.ifBlank { "primary" },
                    color = draft.color.trim(),
                    shape = draft.shape.ifBlank { "Cookie7Sided" },
                    icon = draft.icon.ifBlank { "local_fire_department" },
                    focusMinutes = draft.focusMinutes.coerceAtLeast(1),
                    breakMinutes = draft.breakMinutes.coerceAtLeast(1),
                    longBreakMinutes = draft.longBreakMinutes.coerceAtLeast(1),
                    cycles = draft.cycles.coerceAtLeast(1),
                    dailyGoalMinutes = draft.dailyGoalMinutes.coerceIn(0, 720),
                    autoStartBreak = draft.autoStartBreak,
                    autoStartFocus = draft.autoStartFocus,
                    updatedAt = now,
                    device = dev
                ))
            }
            val updatedTag = tags.firstOrNull { it.id == id }
            var timer = store.timer
            if (timer != null && timer.tagId == id && !timer.running && updatedTag != null) {
                val minutes = minutesForPhase(updatedTag, timer.phase)
                val duration = (minutes * 60.0).roundToInt()
                timer = timer.copy(
                    durationSec = duration,
                    accumulatedSec = min(timer.accumulatedSec, duration.toDouble()),
                    updatedAt = now
                )
            }
            store.copy(tags = tags, timer = timer)
        }
        return found
    }

    fun deleteTag(id: String): Boolean {
        if (_store.value.activeTags.size <= 1) return false
        val now = System.currentTimeMillis()
        val dev = deviceId
        mutate { store ->
            val tags = store.tags.map {
                if (it.id == id) it.copy(deleted = true, updatedAt = now, device = dev) else it
            }
            val fallback = tags.firstOrNull { !it.deleted && it.id != id }?.id ?: ""
            val timer = if (store.timer?.tagId == id) {
                store.timer.copy(tagId = fallback, updatedAt = now)
            } else store.timer
            store.copy(tags = tags, timer = timer)
        }
        dispatchTimer()
        return true
    }

    // ── Tasks ───────────────────────────────────────────────────────────

    fun createTask(draft: TaskDraft): Task {
        val now = System.currentTimeMillis()
        val dev = deviceId
        val task = Task(
            id = newId("task", dev, now),
            title = draft.title.trim().ifBlank { "New task" },
            notes = draft.notes.trim(),
            categoryId = draft.categoryId.ifBlank { _store.value.activeCategories.firstOrNull()?.id ?: "" },
            due = draft.due.trim(),
            done = false,
            priority = draft.priority.coerceIn(0, 2),
            subtasks = normalizeSubtasks(draft.subtasks),
            taskTag = draft.taskTag,
            createdAt = now,
            updatedAt = now,
            device = dev,
            deleted = false
        )
        mutate { it.copy(tasks = it.tasks + task) }
        return task
    }

    private fun normalizeSubtasks(list: List<Subtask>): List<Subtask> = list
        .filter { it.title.isNotBlank() }
        .distinctBy { it.id }

    fun updateTask(id: String, draft: TaskDraft): Boolean {
        val now = System.currentTimeMillis()
        val dev = deviceId
        var found = false
        mutate { store ->
            store.copy(tasks = store.tasks.map { task ->
                if (task.id != id) return@map task
                found = true
                task.copy(
                    title = draft.title.trim().ifBlank { "New task" },
                    notes = draft.notes.trim(),
                    categoryId = draft.categoryId,
                    due = draft.due.trim(),
                    priority = draft.priority.coerceIn(0, 2),
                    subtasks = normalizeSubtasks(draft.subtasks),
                    taskTag = draft.taskTag,
                    updatedAt = now,
                    device = dev
                )
            })
        }
        return found
    }

    fun deleteTask(id: String) {
        val now = System.currentTimeMillis()
        val dev = deviceId
        mutate { store ->
            store.copy(tasks = store.tasks.map {
                if (it.id == id) it.copy(deleted = true, updatedAt = now, device = dev) else it
            })
        }
    }

    fun toggleTask(id: String) {
        val now = System.currentTimeMillis()
        val dev = deviceId
        mutate { store ->
            store.copy(tasks = store.tasks.map {
                if (it.id == id) it.copy(done = !it.done, updatedAt = now, device = dev) else it
            })
        }
    }

    fun addSubtask(taskId: String, title: String) {
        val text = title.trim()
        if (text.isEmpty()) return
        val task = _store.value.taskById(taskId) ?: return
        val now = System.currentTimeMillis()
        updateSubtaskList(taskId, task.subtasks + Subtask(
            id = newId("sub", deviceId, now),
            title = text,
            done = false,
            updatedAt = now
        ))
    }

    fun toggleSubtask(taskId: String, subtaskId: String) {
        val task = _store.value.taskById(taskId) ?: return
        val now = System.currentTimeMillis()
        updateSubtaskList(taskId, task.subtasks.map {
            if (it.id == subtaskId) it.copy(done = !it.done, updatedAt = now) else it
        })
    }

    fun removeSubtask(taskId: String, subtaskId: String) {
        val task = _store.value.taskById(taskId) ?: return
        updateSubtaskList(taskId, task.subtasks.filter { it.id != subtaskId })
    }

    private fun updateSubtaskList(taskId: String, subtasks: List<Subtask>) {
        val now = System.currentTimeMillis()
        val dev = deviceId
        mutate { store ->
            store.copy(tasks = store.tasks.map {
                if (it.id == taskId) it.copy(
                    subtasks = normalizeSubtasks(subtasks),
                    updatedAt = now,
                    device = dev
                ) else it
            })
        }
    }

    // ── Focus sessions ──────────────────────────────────────────────────

    fun deleteSession(id: String) {
        val now = System.currentTimeMillis()
        val dev = deviceId
        mutate { store ->
            store.copy(focusSessions = store.focusSessions.map {
                if (it.id == id) it.copy(deleted = true, updatedAt = now, device = dev) else it
            })
        }
    }

    // ── Timer ───────────────────────────────────────────────────────────

    fun ensureTimer() {
        if (_store.value.timer != null) return
        val now = System.currentTimeMillis()
        mutate(saveImmediately = true) { it.ensureTimer(deviceId, now) }
        dispatchTimer()
    }

    fun selectTag(tagId: String): Boolean {
        val store = _store.value
        if (store.timer?.running == true) return false
        val tag = store.activeTagById(tagId) ?: return false
        val phase = store.timer?.phase ?: "focus"
        val cycle = store.timer?.cycle ?: 0
        val now = System.currentTimeMillis()
        mutate(saveImmediately = true) {
            it.copy(timer = it.makeTimer(tag.id, phase, cycle, false, deviceId, now))
        }
        dispatchTimer()
        return true
    }

    fun startPhase(tagId: String, phase: String, cycle: Int, running: Boolean) {
        val now = System.currentTimeMillis()
        mutate(saveImmediately = running) {
            it.copy(timer = it.makeTimer(tagId, phase, cycle, running, deviceId, now))
        }
        dispatchTimer()
    }

    fun startFocusNow(tagId: String = _store.value.activeTagId) {
        val store = _store.value
        val id = store.activeTagById(tagId)?.id ?: store.activeTags.firstOrNull()?.id ?: ""
        val cycle = if (store.timer?.phase == "focus") store.timer.cycle else 0
        startPhase(id, "focus", cycle, true)
    }

    fun toggleTimer() {
        val store = _store.value
        val timer = store.timer
        if (timer == null) {
            startFocusNow()
            return
        }
        when {
            timer.running -> pauseTimer()
            computeSecondsLeft(timer) <= 0 -> startFocusNow(store.activeTagId)
            else -> resumeTimer()
        }
    }

    fun pauseTimer() {
        val timer = _store.value.timer ?: return
        if (!timer.running) return
        val now = System.currentTimeMillis()
        mutate(saveImmediately = true) { store ->
            val value = store.timer ?: return@mutate store
            if (!value.running) return@mutate store
            val elapsed = elapsedSecondsExact(value, now)
            store.copy(timer = value.copy(
                running = false,
                runStartedAt = 0L,
                accumulatedSec = elapsed,
                updatedAt = now
            ))
        }
        dispatchTimer()
    }

    fun resumeTimer() {
        val store = _store.value
        val timer = store.timer ?: return
        if (timer.running) return
        if (computeSecondsLeft(timer) <= 0) {
            startFocusNow(store.activeTagId)
            return
        }
        val now = System.currentTimeMillis()
        mutate(saveImmediately = true) {
            it.copy(timer = timer.copy(running = true, runStartedAt = now, updatedAt = now))
        }
        dispatchTimer()
    }

    fun resetTimer() {
        val now = System.currentTimeMillis()
        mutate(saveImmediately = true) { store ->
            val current = store.timer ?: return@mutate store
            val (withSession, _) = store.finalizeSession(current, false, deviceId, now)
            withSession.copy(timer = withSession.makeTimer(
                current.tagId, current.phase, current.cycle, false, deviceId, now
            ))
        }
        dispatchTimer()
    }

    fun skipTimer() {
        val now = System.currentTimeMillis()
        mutate(saveImmediately = true) { store ->
            val current = store.timer ?: return@mutate store
            val (withSession, _) = store.finalizeSession(current, false, deviceId, now)
            withSession.copy(timer = withSession.advanceAfter(current, deviceId, now))
        }
        dispatchTimer()
    }

    fun setTimerNote(text: String) {
        val now = System.currentTimeMillis()
        mutate { store ->
            val timer = store.timer ?: store.makeTimer(store.activeTagId, "focus", 0, false, deviceId, now)
            store.copy(timer = timer.copy(note = text, updatedAt = now))
        }
    }

    /**
     * Called by the service and by the exact-alarm fallback. The lock plus the
     * state re-check makes completion idempotent when both fire together.
     */
    fun completeCurrentPhase(): PhaseCompletion? {
        val now = System.currentTimeMillis()
        val dev = deviceId
        val completion = synchronized(lock) {
            val store = _store.value
            val timer = store.timer ?: return@synchronized null
            if (!timer.running) return@synchronized null
            if (computeSecondsLeft(timer, now) > 0) return@synchronized null

            val stopped = timer.copy(
                running = false,
                runStartedAt = 0L,
                accumulatedSec = maxOf(timer.durationSec.toDouble(), elapsedSecondsExact(timer, now)),
                updatedAt = now
            )
            val (withSession, session) = store.finalizeSession(stopped, true, dev, now)
            val next = withSession.advanceAfter(stopped, dev, now)
            val result = withSession.copy(timer = next, updatedAt = now, device = dev)
            _store.value = result
            val tag = result.tagById(stopped.tagId)
            PhaseCompletion(
                session = session,
                previousPhase = stopped.phase,
                next = next,
                tag = tag,
                focusCompleted = stopped.phase == "focus"
            )
        }
        if (completion != null) {
            saveSoon()
            scheduleSyncIfConfigured()
            dispatchTimer()
            if (completion.focusCompleted && completion.session != null) {
                emitEvent(GritEvent.FocusCompleted(
                    tagName = completion.tag?.name ?: "Focus",
                    minutes = (completion.session.durationSec / 60.0).roundToInt(),
                    wasFocus = true
                ))
            }
        }
        return completion
    }

    /** On app launch, finish a phase whose wall-clock time elapsed while dead. */
    fun reconcileTimerOnResume(): PhaseCompletion? {
        val timer = _store.value.timer ?: return null
        if (!timer.running) return null
        if (computeSecondsLeft(timer) > 0) return null
        return completeCurrentPhase()
    }

    // ── Sync bridge ─────────────────────────────────────────────────────

    fun snapshotForSync(): Store = synchronized(lock) {
        _store.value.ensureTimer(deviceId).snapshot(deviceId)
            .copy(leaderboard = leaderboardFromSettings())
    }

    fun applyRemoteStore(remote: Store): Store {
        val merged = synchronized(lock) {
            val local = storeWithLeaderboard(_store.value)
            val next = mergeStores(local, remote, deviceId).ensureTimer(deviceId)
            _store.value = next
            next
        }
        applyLeaderboardPrefs(merged.leaderboard)
        saveSoon()
        dispatchTimer()
        return merged
    }

    // ── Backup / restore ────────────────────────────────────────────────

    fun exportBackup(uri: Uri) {
        scope.launch {
            runCatching {
                val text = encodeStore(snapshotForSync())
                appContext.contentResolver.openOutputStream(uri, "wt")?.use { out ->
                    out.write(text.toByteArray(Charsets.UTF_8))
                } ?: error("Could not open the selected file")
            }.onSuccess {
                toast("Backup saved")
            }.onFailure {
                Log.w(TAG, "Backup export failed", it)
                toast("Could not save the backup")
            }
        }
    }

    fun importBackup(uri: Uri) {
        scope.launch {
            runCatching {
                val text = appContext.contentResolver.openInputStream(uri)?.use { input ->
                    input.readBytes().toString(Charsets.UTF_8)
                } ?: error("Could not read the selected file")
                val parsed = normalizeStore(decodeStore(text), deviceId)
                val now = System.currentTimeMillis()
                val dev = deviceId
                val restored = parsed.copy(
                    habits = restoreRecords(parsed.habits, _store.value.habits, { it.id }, now, dev,
                        { h, t, d -> h.copy(updatedAt = t, device = d) },
                        { h, t, d -> h.copy(deleted = true, updatedAt = t, device = d) }),
                    entries = restoreRecords(parsed.entries, _store.value.entries, { it.id }, now, dev,
                        { e, t, d -> e.copy(updatedAt = t, device = d) },
                        { e, t, d -> e.copy(deleted = true, updatedAt = t, device = d) }),
                    tasks = restoreRecords(parsed.tasks, _store.value.tasks, { it.id }, now, dev,
                        { task, t, d -> task.copy(updatedAt = t, device = d) },
                        { task, t, d -> task.copy(deleted = true, updatedAt = t, device = d) }),
                    categories = restoreRecords(parsed.categories, _store.value.categories, { it.id }, now, dev,
                        { c, t, d -> c.copy(updatedAt = t, device = d) },
                        { c, t, d -> c.copy(deleted = true, updatedAt = t, device = d) }),
                    tags = restoreRecords(parsed.tags, _store.value.tags, { it.id }, now, dev,
                        { tag, t, d -> tag.copy(updatedAt = t, device = d) },
                        { tag, t, d -> tag.copy(deleted = true, updatedAt = t, device = d) }),
                    focusSessions = restoreRecords(parsed.focusSessions, _store.value.focusSessions, { it.id }, now, dev,
                        { session, t, d -> session.copy(updatedAt = t, device = d) },
                        { session, t, d -> session.copy(deleted = true, updatedAt = t, device = d) }),
                    leaderboard = if (parsed.leaderboard.displayName.isNotBlank() ||
                        parsed.leaderboard.publicTagIds.isNotEmpty()) {
                        parsed.leaderboard.copy(
                            nameUpdatedAt = now,
                            tagsUpdatedAt = now,
                            updatedAt = now
                        )
                    } else {
                        leaderboardFromSettings()
                    },
                    updatedAt = now,
                    device = dev
                ).ensureTimer(dev)
                synchronized(lock) { _store.value = restored }
                applyLeaderboardPrefs(restored.leaderboard)
                saveNow()
                dispatchTimer()
                FirestoreSync.syncNow(silent = false)
            }.onSuccess {
                toast("Backup restored and uploaded to your devices")
            }.onFailure {
                Log.w(TAG, "Backup import failed", it)
                toast("Could not restore that backup")
            }
        }
    }

    private fun <T> restoreRecords(
        imported: List<T>,
        current: List<T>,
        id: (T) -> String,
        now: Long,
        device: String,
        stamp: (T, Long, String) -> T,
        tombstone: (T, Long, String) -> T
    ): List<T> {
        val importedIds = imported.map(id).toHashSet()
        return imported.map { stamp(it, now, device) } +
            current.filter { id(it) !in importedIds }.map { tombstone(it, now, device) }
    }

    private fun toast(message: String) {
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(appContext, message, Toast.LENGTH_SHORT).show()
        }
    }

    // ── Reset ───────────────────────────────────────────────────────────

    fun resetAllData() {
        val now = System.currentTimeMillis()
        val dev = deviceId
        mutate(saveImmediately = true) { store ->
            fun <T> tombstone(item: T, stamp: (T, Long, String) -> T): T = stamp(item, now, dev)
            val habits = store.habits.map { tombstone(it) { h, t, d -> h.copy(deleted = true, updatedAt = t, device = d) } }
            val entries = store.entries.map { it.copy(deleted = true, updatedAt = now, device = dev) }
            val tasks = store.tasks.map { it.copy(deleted = true, updatedAt = now, device = dev) }
            val sessions = store.focusSessions.map { it.copy(deleted = true, updatedAt = now, device = dev) }
            val tags = store.tags.map { it.copy(deleted = true, updatedAt = now, device = dev) } +
                defaultTags().map { it.copy(updatedAt = now, device = dev) }
            val categories = store.categories.map { it.copy(deleted = true, updatedAt = now, device = dev) } +
                defaultCategories().map { it.copy(updatedAt = now, device = dev) }
            val base = store.copy(
                habits = habits,
                entries = entries,
                tasks = tasks,
                focusSessions = sessions,
                tags = tags,
                categories = categories,
                updatedAt = now,
                device = dev
            )
            base.copy(timer = base.makeTimer(tags.firstOrNull { !it.deleted }?.id ?: "", "focus", 0, false, dev, now))
        }
        dispatchTimer()
        scope.launch { FirestoreSync.syncNow(silent = false) }
    }
}
