package com.gritnative.app.widgets

import android.content.Context
import androidx.compose.runtime.Composable
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
        val store = StoreRepository.currentStore
        val settings = StoreRepository.currentSettings
        val colors = widgetColors(context, settings)
        provideContent {
            WidgetContent(store, settings, colors)
        }
    }
}
