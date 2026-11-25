package com.gritnative.app.widgets

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.glance.GlanceId
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.provideContent
import com.gritnative.app.data.AppSettings
import com.gritnative.app.data.Store
import com.gritnative.app.data.StoreRepository

/**
 * Every Grit widget shares the same loading and theming path. `SizeMode.Exact`
 * keeps the composition aware of the actual launcher cell size, so one provider
 * can grow from a 2x2 glance into a 4x4 dashboard without duplicating receiver
 * entries in the widget picker.
 */
abstract class BaseGritWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Exact

    @Composable
    abstract fun WidgetContent(
        store: Store,
        settings: AppSettings,
        colors: WidgetColors
    )

    final override suspend fun provideGlance(context: Context, id: GlanceId) {
        StoreRepository.init(context)
        StoreRepository.awaitLoaded()
        // Collect inside the session, not before it. Glance keeps a running
        // session across data changes; a snapshot captured in provideGlance
        // would make every update recompose the state from when the session
        // first opened, leaving pinned widgets stuck on old timer/check-in
        // values.
        provideContent {
            val store by StoreRepository.store.collectAsState()
            val settings by StoreRepository.settings.collectAsState()
            val colors = widgetColors(context, settings)
            WidgetContent(store, settings, colors)
        }
    }
}
