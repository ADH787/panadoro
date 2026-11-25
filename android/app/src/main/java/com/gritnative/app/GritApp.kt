package com.gritnative.app

import android.app.Application
import android.content.Context
import com.gritnative.app.data.AuthService
import com.gritnative.app.data.FirestoreSync
import com.gritnative.app.data.LeaderboardService
import com.gritnative.app.data.StoreRepository
import com.gritnative.app.timer.CompletionAlert
import com.gritnative.app.timer.FocusDnd
import com.gritnative.app.timer.TimerCoordinator
import com.gritnative.app.widgets.WidgetUpdater
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch

/**
 * Whether any of the app's activities is on screen right now. The completion
 * path reads it to decide between the in-app full-screen alarm and the
 * notification alarm, so the two can never fire together.
 */
object AppForeground {
    @Volatile
    var isForeground: Boolean = false
}

@OptIn(FlowPreview::class)
class GritApp : Application() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        WidgetUpdater.refresh(this)
    }

    override fun onCreate() {
        super.onCreate()
        AuthService.init(this)
        StoreRepository.init(this)
        CompletionAlert.createChannels(this)
        StoreRepository.timerObserver = { store ->
            TimerCoordinator.sync(this, store)
            FocusDnd.sync(this, store.timer)
        }
        registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            // Resumed, not merely started: a covered or screen-off activity
            // must not count as "on screen", or a completion would ring the
            // in-app alarm into a dark display instead of the notification.
            override fun onActivityResumed(activity: android.app.Activity) {
                AppForeground.isForeground = true
            }

            override fun onActivityPaused(activity: android.app.Activity) {
                AppForeground.isForeground = false
            }

            override fun onActivityCreated(activity: android.app.Activity, savedInstanceState: android.os.Bundle?) {}
            override fun onActivityStarted(activity: android.app.Activity) {}
            override fun onActivityStopped(activity: android.app.Activity) {}
            override fun onActivitySaveInstanceState(activity: android.app.Activity, outState: android.os.Bundle) {}
            override fun onActivityDestroyed(activity: android.app.Activity) {}
        })

        scope.launch {
            StoreRepository.awaitLoaded()
            // A phase that elapsed while the process was dead is finalised here,
            // exactly once; the alarm receiver may already have done it.
            StoreRepository.reconcileTimerOnResume()?.let {
                CompletionAlert.notifyCompletion(this@GritApp, it)
            }
            TimerCoordinator.sync(this@GritApp)
            // The quiet mode follows the timer state everywhere it changes —
            // including here, after a cold start.
            FocusDnd.sync(this@GritApp, StoreRepository.currentStore.timer)
            // Pinned widgets keep their freshness even when the process dies
            // between sessions.
            com.gritnative.app.widgets.WidgetKeepAlive.schedule(this@GritApp)
            FirestoreSync.startAutoSync()
            LeaderboardService.startAutoPublish()
            // Firebase refresh tokens do not expire on their own. Refreshing the
            // short-lived ID token at launch keeps a signed-in session signed in
            // even after the app has been closed for weeks.
            scope.launch { AuthService.ensureFreshToken() }
            scope.launch {
                combine(StoreRepository.store, StoreRepository.settings) { _, _ -> }
                    .debounce(12_000)
                    .collect { LeaderboardService.publish() }
            }
            combine(StoreRepository.store, StoreRepository.settings) { _, _ -> }
                .collect { WidgetUpdater.refresh(this@GritApp) }
        }
    }
}
