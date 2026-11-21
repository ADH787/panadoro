package com.gritnative.app

import android.app.Application
import com.gritnative.app.data.AuthService
import com.gritnative.app.data.FirestoreSync
import com.gritnative.app.data.LeaderboardService
import com.gritnative.app.data.StoreRepository
import com.gritnative.app.timer.CompletionAlert
import com.gritnative.app.timer.TimerCoordinator
import com.gritnative.app.widgets.WidgetUpdater
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch

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
        StoreRepository.timerObserver = { store -> TimerCoordinator.sync(this, store) }

        scope.launch {
            StoreRepository.awaitLoaded()
            // A phase that elapsed while the process was dead is finalised here,
            // exactly once; the alarm receiver may already have done it.
            StoreRepository.reconcileTimerOnResume()?.let {
                CompletionAlert.notifyCompletion(this@GritApp, it)
            }
            TimerCoordinator.sync(this@GritApp)
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
