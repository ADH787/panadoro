package com.gritnative.app.timer

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import com.gritnative.app.data.StoreRepository
import com.gritnative.app.data.TimerState
import com.gritnative.app.ui.components.translateText

/**
 * "Do Not Disturb while focusing": while a focus phase runs, the phone goes
 * quiet — calls and notifications are held back, and the session's own alarm
 * still rings because it plays with the alarm usage. The filter the user had
 * before the session is remembered (across reboots too, in the prefs) and put
 * back the moment the focus phase ends, pauses or is skipped.
 */
object FocusDnd {
    /** True while the quiet filter we applied is in place. */
    @Volatile
    var isBlocking: Boolean = false

    fun hasPolicyAccess(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return false
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        return manager.isNotificationPolicyAccessGranted
    }

    /** Opens the grant page for DND access, with a line of explanation. */
    fun requestPolicyAccess(context: Context) {
        Toast.makeText(
            context.applicationContext,
            translateText(
                "Allow \u201CDo Not Disturb access\u201D so Panadoro can quiet the phone during focus"
            ),
            Toast.LENGTH_LONG
        ).show()
        runCatching {
            context.startActivity(
                Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    /**
     * Called whenever the timer state changes: applies or lifts the quiet
     * filter. Without the policy grant nothing is ever changed on the user's
     * behalf.
     */
    fun sync(context: Context, timer: TimerState?) {
        val app = context.applicationContext
        if (!hasPolicyAccess(app)) return
        val manager = app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val focusRunning = timer?.running == true && timer.phase == "focus"
        if (focusRunning && StoreRepository.currentSettings.focusDnd) {
            if (isBlocking) return
            val current = manager.currentInterruptionFilter
            // "All" is the natural state to go back to; anything more
            // restrictive is the user's own choice and is left untouched.
            if (current == NotificationManager.INTERRUPTION_FILTER_ALL ||
                current == NotificationManager.INTERRUPTION_FILTER_UNKNOWN
            ) {
                StoreRepository.appContext().getSharedPreferences("grit_settings", Context.MODE_PRIVATE)
                    .edit().putInt(SAVED_FILTER, current).apply()
            }
            manager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY)
            isBlocking = true
        } else if (isBlocking) {
            restore(app, manager)
        }
    }

    private fun restore(app: Context, manager: NotificationManager) {
        val saved = app.getSharedPreferences("grit_settings", Context.MODE_PRIVATE)
            .getInt(SAVED_FILTER, NotificationManager.INTERRUPTION_FILTER_ALL)
        runCatching { manager.setInterruptionFilter(saved) }
        app.getSharedPreferences("grit_settings", Context.MODE_PRIVATE)
            .edit().remove(SAVED_FILTER).apply()
        isBlocking = false
    }

    private const val SAVED_FILTER = "focusDndSavedFilter"
}
