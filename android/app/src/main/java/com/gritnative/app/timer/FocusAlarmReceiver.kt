package com.gritnative.app.timer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.gritnative.app.data.StoreRepository
import com.gritnative.app.data.computeSecondsLeft
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Exact-alarm fallback. It needs `goAsync` because the store is loaded from disk
 * asynchronously when this process was not already alive.
 */
class FocusAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_ALARM) return
        val appContext = context.applicationContext
        val pending = goAsync()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scope.launch {
            try {
                StoreRepository.init(appContext)
                StoreRepository.awaitLoaded()
                val timer = StoreRepository.currentStore.timer
                if (timer?.running != true) {
                    AlarmScheduler.cancel(appContext)
                    return@launch
                }
                if (computeSecondsLeft(timer) > 0) {
                    // Woke a fraction early; re-arm rather than completing a phase
                    // that still has time on the clock.
                    AlarmScheduler.schedule(appContext, timer)
                    TimerCoordinator.sync(appContext)
                    return@launch
                }
                val completion = StoreRepository.completeCurrentPhase()
                if (completion != null) {
                    CompletionAlert.notifyCompletion(appContext, completion)
                }
                val next = StoreRepository.currentStore.timer
                if (next?.running == true) {
                    TimerCoordinator.sync(appContext)
                } else {
                    AlarmScheduler.cancel(appContext)
                    FocusTimerService.stop(appContext)
                }
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_ALARM = "com.gritnative.app.timer.ALARM"
    }
}

/** Restores the exact alarm after a reboot or an app update. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != Intent.ACTION_MY_PACKAGE_REPLACED &&
            intent.action != "android.intent.action.QUICKBOOT_POWERON"
        ) return
        val appContext = context.applicationContext
        val pending = goAsync()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scope.launch {
            try {
                StoreRepository.init(appContext)
                StoreRepository.awaitLoaded()
                val timer = StoreRepository.currentStore.timer
                if (timer?.running == true) {
                    if (computeSecondsLeft(timer) <= 0) {
                        val completion = StoreRepository.completeCurrentPhase()
                        if (completion != null) {
                            CompletionAlert.notifyCompletion(appContext, completion)
                        }
                    }
                    val next = StoreRepository.currentStore.timer
                    if (next?.running == true) TimerCoordinator.sync(appContext)
                } else {
                    AlarmScheduler.cancel(appContext)
                }
            } finally {
                pending.finish()
            }
        }
    }
}
