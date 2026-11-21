package com.gritnative.app.widgets

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

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
            Intent.ACTION_LOCALE_CHANGED -> WidgetUpdater.refresh(context)
        }
    }
}
