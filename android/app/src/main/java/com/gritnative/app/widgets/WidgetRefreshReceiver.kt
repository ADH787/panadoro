package com.gritnative.app.widgets

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.gritnative.app.data.StoreRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Date changes matter to almost every Grit widget: "today" becomes a new set
 * of habits and tasks, and the weekly chart rolls forward. Refresh at the
 * system broadcast instead of relying on a launcher update period.
 */
class WidgetRefreshReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        when (intent?.action) {
            Intent.ACTION_DATE_CHANGED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_LOCALE_CHANGED -> {
                val appContext = context.applicationContext
                val pending = goAsync()
                CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                    try {
                        StoreRepository.init(appContext)
                        StoreRepository.awaitLoaded()
                        WidgetUpdater.updateNow(appContext)
                    } finally {
                        pending.finish()
                    }
                }
            }
        }
    }
}

/**
 * The quarter-hour nudge from [WidgetKeepAlive]: the store is loaded (the
 * process may have been dead), then every pinned widget is redrawn with fresh
 * data. Doze may delay a beat or two — never by more than a few minutes.
 */
class WidgetKeepAliveReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val appContext = context.applicationContext
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                StoreRepository.init(appContext)
                StoreRepository.awaitLoaded()
                WidgetUpdater.updateNow(appContext)
                com.gritnative.app.timer.TimerCoordinator.sync(appContext)
            } finally {
                pending.finish()
            }
        }
    }
}
