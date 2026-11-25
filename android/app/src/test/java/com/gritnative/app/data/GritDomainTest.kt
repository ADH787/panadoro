package com.gritnative.app.data

import kotlinx.serialization.encodeToString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JVM tests for the parts of Grit that are shared with the quickshell app.
 *
 * These deliberately avoid Android framework classes: the Gist merge rules,
 * timer transitions and wire format are pure Kotlin and are the places where a
 * regression would otherwise silently corrupt synced data.
 */
class GritDomainTest {

    private val device = "test-device-1"
    private val now = 1_750_000_000_000L

    // ── Gist merge rules ────────────────────────────────────────────────

    @Test
    fun `merge takes newer remote records and keeps local-only records`() {
        val local = emptyStore(device, now - 1000).copy(
            habits = listOf(
                habit("h1", "Local old", updatedAt = 100),
                habit("h2", "Local only", updatedAt = 100)
            )
        )
        val remote = emptyStore("remote-device", now).copy(
            habits = listOf(
                habit("h1", "Remote new", updatedAt = 200),
                habit("h3", "Remote only", updatedAt = 200)
            )
        )

        val merged = mergeStores(local, remote, device, now)

        assertEquals("Remote new", merged.habitById("h1")?.name)
        assertEquals("Local only", merged.habitById("h2")?.name)
        assertEquals("Remote only", merged.habitById("h3")?.name)
        assertEquals(3, merged.activeHabits.size)
        assertEquals(device, merged.device)
        assertEquals(now, merged.updatedAt)
    }

    @Test
    fun `merge keeps a newer local edit over an older remote value`() {
        val local = emptyStore(device, now).copy(
            habits = listOf(habit("h1", "Local new", updatedAt = 500))
        )
        val remote = emptyStore("remote-device", now).copy(
            habits = listOf(habit("h1", "Remote old", updatedAt = 400))
        )

        val merged = mergeStores(local, remote, device, now)

        assertEquals("Local new", merged.habitById("h1")?.name)
        assertFalse(merged.habitById("h1")!!.deleted)
    }

    @Test
    fun `remote tombstone wins an equal-timestamp conflict`() {
        val local = emptyStore(device, now).copy(
            habits = listOf(habit("h1", "Deleted elsewhere", updatedAt = 700))
        )
        val remote = emptyStore("remote-device", now).copy(
            habits = listOf(habit("h1", "Deleted elsewhere", updatedAt = 700, deleted = true))
        )

        val merged = mergeStores(local, remote, device, now)

        assertTrue(merged.habitById("h1")!!.deleted)
        assertTrue(merged.activeHabits.isEmpty())
        // Tombstones stay in the document so the next device also sees the delete.
        assertEquals(1, merged.habits.size)
    }

    @Test
    fun `local tombstone survives an older remote value and wins an equal-timestamp conflict`() {
        val olderRemote = emptyStore("remote-device", now).copy(
            habits = listOf(habit("h1", "Stale remote edit", updatedAt = 600))
        )
        val localDelete = emptyStore(device, now).copy(
            habits = listOf(habit("h1", "", updatedAt = 800, deleted = true))
        )

        val afterOlder = mergeStores(localDelete, olderRemote, device, now)
        assertTrue(afterOlder.habitById("h1")!!.deleted)

        val equalRemote = emptyStore("remote-device", now).copy(
            habits = listOf(habit("h1", "Same stamp", updatedAt = 800))
        )
        val afterEqual = mergeStores(localDelete, equalRemote, device, now)
        assertTrue(afterEqual.habitById("h1")!!.deleted)
    }

    @Test
    fun `merge applies newest timer state and sorts sessions by end time`() {
        val local = emptyStore(device, now).copy(
            timer = TimerState(
                tagId = "tag-deep-work",
                accumulatedSec = 10.0,
                updatedAt = 100
            ),
            focusSessions = listOf(
                session("s-old", endedAt = 1_000, updatedAt = 100),
                session("s-mid", endedAt = 2_000, updatedAt = 100)
            )
        )
        val remote = emptyStore("remote-device", now).copy(
            timer = TimerState(
                tagId = "tag-study",
                accumulatedSec = 42.5,
                updatedAt = 200
            ),
            focusSessions = listOf(
                session("s-new", endedAt = 3_000, updatedAt = 100),
                session("s-old", endedAt = 1_000, updatedAt = 200, deleted = true)
            )
        )

        val merged = mergeStores(local, remote, device, now)

        assertEquals("tag-study", merged.timer?.tagId)
        assertTrue(nearlyEqual(42.5, merged.timer!!.accumulatedSec))
        assertEquals(listOf("s-new", "s-mid"), merged.activeSessions.map { it.id })
        assertTrue(merged.focusSessions.single { it.id == "s-old" }.deleted)
    }

    @Test
    fun `merge keeps the local timer when it is at least as new as the remote timer`() {
        val local = emptyStore(device, now).copy(
            timer = TimerState(
                tagId = "tag-deep-work",
                accumulatedSec = 10.0,
                updatedAt = 200
            )
        )
        val remote = emptyStore("remote-device", now).copy(
            timer = TimerState(
                tagId = "tag-study",
                accumulatedSec = 20.0,
                updatedAt = 200
            )
        )

        val merged = mergeStores(local, remote, device, now)

        assertEquals("tag-deep-work", merged.timer?.tagId)
        assertTrue(nearlyEqual(10.0, merged.timer!!.accumulatedSec))
    }

    // ── Timer transitions ──────────────────────────────────────────────

    @Test
    fun `completed focus advances through short break to long break`() {
        val tag = defaultTag("tag-deep-work")
        val store = emptyStore(device, now).copy(tags = listOf(tag))

        val focus1 = store.makeTimer(tag.id, "focus", 0, false, device, now)
        val shortBreak = store.advanceAfter(focus1, device, now + 1000)
        assertEquals("shortBreak", shortBreak.phase)
        assertEquals(1, shortBreak.cycle)
        assertEquals(tag.breakMinutes * 60, shortBreak.durationSec)
        assertEquals(tag.id, shortBreak.tagId)
        assertTrue(shortBreak.running)
        assertTrue(shortBreak.sessionId.isNotBlank())

        val focus4 = store.makeTimer(tag.id, "focus", 3, false, device, now)
        val longBreak = store.advanceAfter(focus4, device, now + 1000)
        assertEquals("longBreak", longBreak.phase)
        assertEquals(4, longBreak.cycle)
        assertEquals(tag.longBreakMinutes * 60, longBreak.durationSec)
        assertTrue(longBreak.running)
    }

    @Test
    fun `break completion returns to focus and long break resets the cycle`() {
        val tag = defaultTag("tag-deep-work")
        val store = emptyStore(device, now).copy(tags = listOf(tag))

        val shortBreak = store.makeTimer(tag.id, "shortBreak", 2, false, device, now)
        val afterShort = store.advanceAfter(shortBreak, device, now + 1000)
        assertEquals("focus", afterShort.phase)
        assertEquals(2, afterShort.cycle)
        assertEquals(tag.focusMinutes * 60, afterShort.durationSec)
        assertFalse(afterShort.running)

        val longBreak = store.makeTimer(tag.id, "longBreak", 4, false, device, now)
        val afterLong = store.advanceAfter(longBreak, device, now + 1000)
        assertEquals("focus", afterLong.phase)
        assertEquals(0, afterLong.cycle)
        assertFalse(afterLong.running)
    }

    @Test
    fun `timer auto-start flags are honoured in both directions`() {
        val tag = defaultTag("tag-deep-work").copy(
            autoStartBreak = false,
            autoStartFocus = true
        )
        val store = emptyStore(device, now).copy(tags = listOf(tag))

        val focus = store.makeTimer(tag.id, "focus", 0, false, device, now)
        val pauseAtBreak = store.advanceAfter(focus, device, now + 1000)
        assertEquals("shortBreak", pauseAtBreak.phase)
        assertFalse(pauseAtBreak.running)

        val resumeAtFocus = store.advanceAfter(pauseAtBreak, device, now + 2000)
        assertEquals("focus", resumeAtFocus.phase)
        assertTrue(resumeAtFocus.running)
    }

    @Test
    fun `elapsed time is clamped to the planned duration`() {
        val timer = TimerState(
            phase = "focus",
            tagId = "tag-deep-work",
            running = true,
            runStartedAt = now - 200_000,
            accumulatedSec = 0.0,
            durationSec = 60,
            updatedAt = now
        )

        assertTrue(nearlyEqual(60.0, elapsedSecondsExact(timer, now)))
        assertEquals(60, elapsedSeconds(timer, now))
        assertEquals(0, computeSecondsLeft(timer, now))
        assertEquals(1f, timer.progress(now))
    }

    @Test
    fun `paused fractional seconds survive the wire format and the timer clock`() {
        val timer = TimerState(
            phase = "focus",
            tagId = "tag-deep-work",
            running = false,
            runStartedAt = 0,
            accumulatedSec = 61.75,
            durationSec = 1500,
            sessionId = "session-fractional",
            updatedAt = now
        )

        val decoded = GritNetJson.decodeFromString(
            TimerState.serializer(),
            GritNetJson.encodeToString(TimerState.serializer(), timer)
        )

        assertTrue(nearlyEqual(61.75, decoded.accumulatedSec))
        assertTrue(nearlyEqual(61.75, elapsedSecondsExact(decoded, now)))
        assertEquals(62, elapsedSeconds(decoded, now))
        assertEquals(1439, computeSecondsLeft(decoded, now))
    }

    // ── Session finalisation ────────────────────────────────────────────

    @Test
    fun `sessions shorter than fifteen seconds are discarded`() {
        val timer = timer(accumulatedSec = 14.0)

        val (result, session) = emptyStore(device, now).finalizeSession(
            value = timer,
            completed = true,
            deviceId = device,
            now = now
        )

        assertNull(session)
        assertTrue(result.focusSessions.isEmpty())
    }

    @Test
    fun `a session at exactly fifteen seconds is kept`() {
        val (store, session) = emptyStore(device, now).finalizeSession(
            value = timer(accumulatedSec = 15.0),
            completed = true,
            deviceId = device,
            now = now
        )

        assertNotNull(session)
        assertEquals(15, session!!.durationSec)
        assertEquals(1, store.focusSessions.size)
    }

    @Test
    fun `a session is finalised once and cannot be inserted twice`() {
        val store = emptyStore(device, now)
        val timer = timer(accumulatedSec = 90.0)

        val (firstStore, firstSession) = store.finalizeSession(
            value = timer,
            completed = true,
            deviceId = device,
            now = now
        )

        assertNotNull(firstSession)
        assertEquals("session-fixed", firstSession!!.id)
        assertEquals("focus", firstSession.phase)
        assertEquals("tag-deep-work", firstSession.tagId)
        assertEquals("task-1", firstSession.taskId)
        assertEquals("A note", firstSession.note)
        assertEquals(90, firstSession.durationSec)
        assertEquals(1500, firstSession.plannedSec)
        assertTrue(firstSession.completed)
        assertEquals(device, firstSession.device)
        assertEquals(1, firstStore.focusSessions.size)

        val (secondStore, secondSession) = firstStore.finalizeSession(
            value = timer,
            completed = true,
            deviceId = device,
            now = now + 1000
        )

        assertNull(secondSession)
        assertEquals(1, secondStore.focusSessions.size)
        assertEquals(90, secondStore.focusSessions.single().durationSec)
    }

    @Test
    fun `reset finalisation records an incomplete session`() {
        val timer = timer(accumulatedSec = 30.0)

        val (store, session) = emptyStore(device, now).finalizeSession(
            value = timer,
            completed = false,
            deviceId = device,
            now = now
        )

        assertNotNull(session)
        assertFalse(session!!.completed)
        assertFalse(store.focusSessions.single().completed)
    }

    // ── Gist document format ────────────────────────────────────────────

    @Test
    fun `gist document round-trips revision payload and fractional timer`() {
        val wire = """
            {
              "revision": 41,
              "payload": {
                "version": 1,
                "habits": [],
                "entries": [],
                "tasks": [],
                "categories": [],
                "tags": [],
                "focusSessions": [],
                "timer": {
                  "phase": "focus",
                  "tagId": "tag-deep-work",
                  "taskId": "",
                  "note": "from quickshell",
                  "running": false,
                  "startedAt": 111,
                  "runStartedAt": 0,
                  "accumulatedSec": 61.75,
                  "durationSec": 1500,
                  "sessionId": "session-qs",
                  "cycle": 0,
                  "updatedAt": 222,
                  "device": "qs-device"
                },
                "updatedAt": 222,
                "device": "qs-device"
              },
              "updatedAt": "2026-10-26T01:02:03.456Z",
              "fieldFromTheFuture": true
            }
        """.trimIndent()

        val document = GritNetJson.decodeFromString(SyncDocument.serializer(), wire)

        assertEquals(41, document.revision)
        assertEquals("2026-10-26T01:02:03.456Z", document.updatedAt)
        assertEquals("from quickshell", document.payload?.timer?.note)
        assertTrue(nearlyEqual(61.75, document.payload!!.timer!!.accumulatedSec))

        val encoded = GritNetJson.encodeToString(SyncDocument.serializer(), document)
        val decoded = GritNetJson.decodeFromString(SyncDocument.serializer(), encoded)

        assertEquals(41, decoded.revision)
        assertEquals("2026-10-26T01:02:03.456Z", decoded.updatedAt)
        val payload = decoded.payload!!
        assertTrue(nearlyEqual(61.75, payload.timer!!.accumulatedSec))
        assertEquals("qs-device", payload.timer.device)
        assertFalse(encoded.contains("fieldFromTheFuture"))
    }

    @Test
    fun `empty gist document decodes with defaults`() {
        val document = GritNetJson.decodeFromString(
            SyncDocument.serializer(),
            """{"revision":0,"payload":null,"updatedAt":""}"""
        )

        assertEquals(0, document.revision)
        assertNull(document.payload)
        assertEquals("", document.updatedAt)
    }

    @Test
    fun `new gist document has the quickshell envelope`() {
        val store = emptyStore(device, now)
        val document = SyncDocument(
            revision = 1,
            payload = store,
            updatedAt = "2026-10-26T01:02:03Z"
        )

        val encoded = GritNetJson.encodeToString(SyncDocument.serializer(), document)

        assertTrue(encoded.contains("\"revision\":1"))
        assertTrue(encoded.contains("\"payload\""))
        assertTrue(encoded.contains("\"updatedAt\":\"2026-10-26T01:02:03Z\""))
        assertTrue(encoded.contains("\"focusSessions\":[]"))
    }

    // ── Week / date helpers ─────────────────────────────────────────────

    @Test
    fun `week rows and heatmaps follow the monday setting`() {
        // 2026-10-26 is a Monday.
        val monday = "2026-10-26"
        assertEquals(0, weekStartOffset(monday, monday = true))
        assertEquals(1, weekStartOffset(monday, monday = false))
        assertEquals(
            listOf(
                "2026-10-26", "2026-10-27", "2026-10-28", "2026-10-29",
                "2026-10-30", "2026-10-31", "2026-11-01"
            ),
            thisWeekDays(monday, monday = true)
        )
        assertEquals(
            listOf(
                "2026-10-25", "2026-10-26", "2026-10-27", "2026-10-28",
                "2026-10-29", "2026-10-30", "2026-10-31"
            ),
            thisWeekDays(monday, monday = false)
        )
    }

    @Test
    fun `heatmap ends on the current week and marks future days`() {
        val today = "2026-10-26" // Monday
        val cells = emptyStore(device, now).focusHeatmapWeeks(
            weeks = 2,
            todayKey = today,
            monday = true
        )

        assertEquals(14, cells.size)
        assertEquals("2026-10-19", cells.first().date)
        assertEquals(today, cells.last { !it.future }.date)
        assertTrue(cells.last().future)
    }

    // ── Midnight session split ──────────────────────────────────────────

    @Test
    fun `a session crossing midnight is split between its days`() {
        // 23:50 on the first day to 00:20 on the next: ten minutes before
        // midnight, twenty minutes after it.
        val firstDay = "2026-11-23"
        val secondDay = "2026-11-24"
        val parts = splitSessionByDay(
            spannedSession("s-midnight", localMs(firstDay, 23, 50), localMs(secondDay, 0, 20), 1800)
        )

        assertEquals(2, parts.size)
        assertEquals(firstDay, parts[0].dayKey)
        assertEquals(600L, parts[0].seconds)
        assertEquals(secondDay, parts[1].dayKey)
        assertEquals(1200L, parts[1].seconds)
        assertEquals(1800L, parts.sumOf { it.seconds })
    }

    @Test
    fun `today totals credit each day with its own portion`() {
        val firstDay = "2026-11-23"
        val secondDay = "2026-11-24"
        val store = emptyStore(device, now).copy(
            focusSessions = listOf(
                spannedSession("s-midnight", localMs(firstDay, 23, 50), localMs(secondDay, 0, 20), 1800)
            )
        )

        assertEquals(600, store.todayFocusSeconds(firstDay))
        assertEquals(1200, store.todayFocusSeconds(secondDay))
        assertEquals(10, store.todayFocusMinutes(firstDay))
        assertEquals(20, store.todayFocusMinutes(secondDay))
        // The session itself counts once on each day it touched.
        assertEquals(1, store.todayFocusSessions(firstDay))
        assertEquals(1, store.todayFocusSessions(secondDay))
        // A one-day range only ever counts the seconds that fall inside it.
        assertEquals(600L, store.secondsInRange(1, "focus", firstDay))
        assertEquals(1200L, store.secondsInRange(1, "focus", secondDay))
        assertEquals(1800L, store.secondsInRange(2, "focus", secondDay))
    }

    @Test
    fun `a paused midnight session spreads its recorded seconds proportionally`() {
        val firstDay = "2026-11-23"
        val secondDay = "2026-11-24"
        // The wall clock spans thirty minutes, but only fifteen were focused.
        val parts = splitSessionByDay(
            spannedSession("s-paused", localMs(firstDay, 23, 50), localMs(secondDay, 0, 20), 900)
        )

        assertEquals(2, parts.size)
        assertEquals(300L, parts[0].seconds)
        assertEquals(600L, parts[1].seconds)
    }

    @Test
    fun `a session without a usable span keeps its end-day attribution`() {
        val parts = splitSessionByDay(
            FocusSession(
                id = "s-legacy",
                phase = "focus",
                startedAt = 0L,
                endedAt = localMs("2026-11-24", 0, 20),
                durationSec = 600,
                updatedAt = now,
                device = "seed"
            )
        )

        assertEquals(1, parts.size)
        assertEquals("2026-11-24", parts[0].dayKey)
        assertEquals(600L, parts[0].seconds)
    }

    @Test
    fun `a multi-day session splits once per midnight`() {
        val parts = splitSessionByDay(
            spannedSession("s-long", localMs("2026-11-21", 22, 0), localMs("2026-11-23", 2, 0), 28 * 3600)
        )

        assertEquals(3, parts.size)
        assertEquals(listOf("2026-11-21", "2026-11-22", "2026-11-23"), parts.map { it.dayKey })
        assertEquals((28 * 3600).toLong(), parts.sumOf { it.seconds })
    }

    @Test
    fun `the focus streak counts both sides of midnight`() {
        val firstDay = "2026-11-23"
        val secondDay = "2026-11-24"
        val store = emptyStore(device, now).copy(
            focusSessions = listOf(
                spannedSession("s-midnight", localMs(firstDay, 23, 50), localMs(secondDay, 0, 20), 1800)
            )
        )

        assertEquals(2, store.focusStreak(secondDay))
    }

    @Test
    fun `time of day buckets follow a session across midnight`() {
        val firstDay = "2026-11-23"
        val secondDay = "2026-11-24"
        val store = emptyStore(device, now).copy(
            focusSessions = listOf(
                spannedSession("s-midnight", localMs(firstDay, 23, 50), localMs(secondDay, 0, 20), 1800)
            )
        )

        val buckets = store.timeOfDayStats(2, secondDay)

        assertEquals(1200L, buckets.first { it.id == "night" }.seconds)
        assertEquals(0L, buckets.first { it.id == "morning" }.seconds)
        assertEquals(0L, buckets.first { it.id == "afternoon" }.seconds)
        assertEquals(600L, buckets.first { it.id == "evening" }.seconds)
    }

    // ── Weekly habits ──────────────────────────────────────────────────

    /** Nov 8 2026 is a Sunday; the week below uses the Sunday default. */
    private val weekSunday = "2026-11-08"

    private fun weeklyHabit(times: Int, createdAtMs: Long = 0L) = Habit(
        id = "h-week",
        name = "Weekly",
        weeklyTimes = times,
        createdAt = createdAtMs,
        updatedAt = 1,
        device = "seed"
    )

    private fun doneEntry(habitId: String, date: String) = Entry(
        id = "e-$date",
        habitId = habitId,
        date = date,
        value = 1,
        updatedAt = 1,
        device = "seed"
    )

    @Test
    fun `a weekly habit is due every day and done for today once the week quota is met`() {
        val habit = weeklyHabit(2)
        val store = emptyStore(device, now).copy(
            habits = listOf(habit),
            // Sunday and Monday done; "today" is Wednesday of that week.
            entries = listOf(doneEntry("h-week", "2026-11-08"), doneEntry("h-week", "2026-11-09"))
        )

        assertTrue(store.scheduledOn(habit, "2026-11-11"))
        assertTrue(store.habitDoneEffective(habit, "2026-11-11", monday = false))
        // Tuesday alone would not be enough for a 2-day quota.
        val thinStore = emptyStore(device, now).copy(
            habits = listOf(weeklyHabit(2)),
            entries = listOf(doneEntry("h-week", "2026-11-10"))
        )
        assertFalse(thinStore.habitDoneEffective(weeklyHabit(2), "2026-11-11", monday = false))
    }

    @Test
    fun `the weekly streak counts days and only resets at a missed week`() {
        val habit = weeklyHabit(3)
        // Week 1 (Nov 1..7, the week before the Sunday week above): quota met
        // with three days. Week 2 (Nov 8..14): two days done so far, today is
        // the 10th — the streak holds both weeks' days.
        val store = emptyStore(device, now).copy(
            habits = listOf(habit.copy(createdAt = localMs("2026-11-01", 0, 0))),
            entries = listOf(
                doneEntry("h-week", "2026-11-01"),
                doneEntry("h-week", "2026-11-03"),
                doneEntry("h-week", "2026-11-05"),
                doneEntry("h-week", "2026-11-08"),
                doneEntry("h-week", "2026-11-09")
            )
        )

        assertEquals(5, store.weeklyHabitStreak(habit, "2026-11-10", monday = false))
    }

    @Test
    fun `a finished week that missed its quota cuts the weekly streak to zero`() {
        val habit = weeklyHabit(3)
        // Week 1 met its quota, but week 2 ended with only one day: the thread
        // resets at the week boundary, and week 3 starts a new count.
        val store = emptyStore(device, now).copy(
            habits = listOf(habit.copy(createdAt = localMs("2026-11-01", 0, 0))),
            entries = listOf(
                doneEntry("h-week", "2026-11-01"),
                doneEntry("h-week", "2026-11-02"),
                doneEntry("h-week", "2026-11-04"),
                doneEntry("h-week", "2026-11-09"),
                doneEntry("h-week", "2026-11-18"),
                doneEntry("h-week", "2026-11-19")
            )
        )

        assertEquals(2, store.weeklyHabitStreak(habit, "2026-11-19", monday = false))
    }

    @Test
    fun `the weekly best streak remembers the peak before a reset`() {
        val habit = weeklyHabit(3)
        // Week 1 met the quota with three days; week 2 had one day and missed,
        // so the peak (4) survives even though the live streak restarted.
        val store = emptyStore(device, now).copy(
            habits = listOf(habit.copy(createdAt = localMs("2026-11-01", 0, 0))),
            entries = listOf(
                doneEntry("h-week", "2026-11-01"),
                doneEntry("h-week", "2026-11-03"),
                doneEntry("h-week", "2026-11-05"),
                doneEntry("h-week", "2026-11-09")
            )
        )

        assertEquals(4, store.weeklyHabitBestStreak(habit, "2026-11-10", monday = false))
        assertEquals(4, store.weeklyHabitStreak(habit, "2026-11-10", monday = false))
    }

    @Test
    fun `skipped fragments do not count as sessions in stats`() {
        // A full session, a half-way abandoned one, and a tiny skipped try:
        // only the first two are real sessions.
        val day = "2026-11-23"
        val store = emptyStore(device, now).copy(
            focusSessions = listOf(
                FocusSession(
                    id = "s-full", phase = "focus", startedAt = localMs(day, 9, 0),
                    endedAt = localMs(day, 9, 25), durationSec = 1500, plannedSec = 1500,
                    completed = true, updatedAt = 1, device = "seed"
                ),
                FocusSession(
                    id = "s-half", phase = "focus", startedAt = localMs(day, 10, 0),
                    endedAt = localMs(day, 10, 13), durationSec = 800, plannedSec = 1500,
                    completed = false, updatedAt = 1, device = "seed"
                ),
                FocusSession(
                    id = "s-tiny", phase = "focus", startedAt = localMs(day, 11, 0),
                    endedAt = localMs(day, 11, 2), durationSec = 124, plannedSec = 1500,
                    completed = false, updatedAt = 1, device = "seed"
                )
            )
        )

        val sessions = store.sessionsInRange(1, "focus", day)
        assertEquals(listOf("s-full", "s-half"), sessions.map { it.id })
    }

    // ── Helpers ─────────────────────────────────────────────────────────

    private fun habit(
        id: String,
        name: String,
        updatedAt: Long,
        deleted: Boolean = false
    ) = Habit(
        id = id,
        name = name,
        updatedAt = updatedAt,
        device = "seed",
        deleted = deleted
    )

    private fun session(
        id: String,
        endedAt: Long,
        updatedAt: Long,
        deleted: Boolean = false
    ) = FocusSession(
        id = id,
        phase = "focus",
        startedAt = endedAt - 60_000,
        endedAt = endedAt,
        durationSec = 60,
        plannedSec = 60,
        completed = true,
        updatedAt = updatedAt,
        device = "seed",
        deleted = deleted
    )

    /** A finished session with a real wall-clock span. */
    private fun spannedSession(id: String, startMs: Long, endMs: Long, durationSec: Int) = FocusSession(
        id = id,
        phase = "focus",
        startedAt = startMs,
        endedAt = endMs,
        durationSec = durationSec,
        plannedSec = durationSec,
        completed = true,
        updatedAt = endMs,
        device = "seed"
    )

    /** Local wall-clock milliseconds on [dayKey], independent of the test machine's zone. */
    private fun localMs(dayKey: String, hour: Int, minute: Int = 0): Long =
        dateFromKey(dayKey).atTime(hour, minute)
            .atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()

    private fun defaultTag(id: String): Tag =
        defaultTags().first { it.id == id }

    private fun timer(accumulatedSec: Double) = TimerState(
        phase = "focus",
        tagId = "tag-deep-work",
        taskId = "task-1",
        note = "A note",
        running = false,
        startedAt = now - 90_000,
        runStartedAt = 0,
        accumulatedSec = accumulatedSec,
        durationSec = 1500,
        sessionId = "session-fixed",
        cycle = 0,
        updatedAt = now,
        device = device
    )
}
