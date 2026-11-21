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
