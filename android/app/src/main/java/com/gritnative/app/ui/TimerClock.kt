package com.gritnative.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import com.gritnative.app.data.TimerState
import kotlinx.coroutines.delay

/**
 * A shared wall clock for the countdown. It ticks quickly while a phase runs so
 * the ring and the top-bar chip stay in step, and sleeps when nothing is moving.
 */
@Composable
fun rememberTimerNow(timer: TimerState?): Long {
    val running = timer?.running == true
    val state = produceState(initialValue = System.currentTimeMillis(), key1 = running) {
        while (true) {
            value = System.currentTimeMillis()
            delay(if (running) 250L else 1_000L)
        }
    }
    return state.value
}
