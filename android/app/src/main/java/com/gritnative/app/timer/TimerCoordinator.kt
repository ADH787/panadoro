package com.gritnative.app.timer

import android.content.Context
import android.util.Log
import com.gritnative.app.data.Store
import com.gritnative.app.data.StoreRepository

/** Keeps the foreground service and the fallback alarm in step with the store. */
object TimerCoordinator {
    private const val TAG = "GritTimer"

    fun sync(context: Context, store: Store = StoreRepository.currentStore) {
        val timer = store.timer
        if (timer?.running == true) {
            runCatching { FocusTimerService.start(context) }
                .onFailure { Log.w(TAG, "Could not start the timer service", it) }
            AlarmScheduler.schedule(context, timer)
        } else {
            runCatching { FocusTimerService.stop(context) }
            AlarmScheduler.cancel(context)
        }
    }
}
