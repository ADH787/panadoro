package com.gritnative.app.timer

import android.content.Context
import com.gritnative.app.data.PhaseCompletion
import com.gritnative.app.data.StoreRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The in-app face of a finished phase. While the app is on screen no
 * completion notification is posted at all and this full-screen alarm takes
 * over instead: the ring, the glow and the stop button all live in one place,
 * so a completion that happens in the foreground can never double-alarm.
 */
object CompletionAlarm {
    data class Payload(
        val id: Long,
        val tag: com.gritnative.app.data.Tag?,
        val tagName: String,
        val focusCompleted: Boolean,
        val minutes: Int,
        val nextPhase: String,
        /** How long the ring lasts before the overlay calms itself down. */
        val ringMs: Long
    )

    private val _active = MutableStateFlow<Payload?>(null)

    /** Non-null while the in-app alarm is ringing. */
    val active: StateFlow<Payload?> = _active.asStateFlow()

    /**
     * Rings the in-app alarm and raises the overlay. A finished focus phase
     * gets the full long ring; a finished break only needs a short notice, so
     * an auto-started minute-long break never reads as a second alarm. A ring
     * still going from an earlier phase is replaced, never stacked on top.
     */
    fun start(context: Context, completion: PhaseCompletion) {
        SessionAlarmPlayer.stop()
        val ringMs = if (completion.focusCompleted) FOCUS_RING_MS else BREAK_RING_MS
        if (StoreRepository.currentSettings.alarmSound) {
            SessionAlarmPlayer.play(context, durationMs = ringMs)
        }
        _active.value = Payload(
            id = System.currentTimeMillis(),
            tag = completion.tag,
            tagName = completion.tag?.name ?: "Focus",
            focusCompleted = completion.focusCompleted,
            minutes = completion.session?.let { (it.durationSec / 60.0).toInt() }?.coerceAtLeast(1) ?: 0,
            nextPhase = completion.next.phase,
            ringMs = ringMs
        )
    }

    /** The stop button, a tap anywhere, or the end of the ring. */
    fun stop() {
        SessionAlarmPlayer.stop()
        _active.value = null
    }

    /** A completed focus phase rings long enough to be found. */
    private const val FOCUS_RING_MS = 20_000L

    /** A finished break is a gentle notice, not a second alarm. */
    private const val BREAK_RING_MS = 4_000L
}
