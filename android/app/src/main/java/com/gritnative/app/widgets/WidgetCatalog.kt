package com.gritnative.app.widgets

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.updateAll
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

data class WidgetSpec(
    val id: String,
    val title: String,
    val description: String,
    val sizeLabel: String,
    val symbol: String,
    val receiver: Class<out GlanceAppWidgetReceiver>,
    val widget: GlanceAppWidget
)

/**
 * Android has no widget-category API, so this catalog is the practical
 * "productivity" collection. The Settings hub pins entries from here and the
 * launcher picker shows the same eight live surfaces under Grit.
 */
object WidgetCatalog {
    val specs: List<WidgetSpec> = listOf(
        WidgetSpec(
            id = "habits",
            title = "Habits",
            description = "Today's habits, progress and one-tap check-ins.",
            sizeLabel = "2×2 → 4×4",
            symbol = "checklist",
            receiver = TodayWidgetReceiver::class.java,
            widget = TodayWidget()
        ),
        WidgetSpec(
            id = "tasks",
            title = "Tasks",
            description = "Today's task queue, overdue work and quick completion.",
            sizeLabel = "2×2 → 4×4",
            symbol = "task_alt",
            receiver = TasksWidgetReceiver::class.java,
            widget = TasksWidget()
        ),
        WidgetSpec(
            id = "pomodoro",
            title = "Pomodoro",
            description = "A live focus countdown with pause, resume and skip.",
            sizeLabel = "2×2 → 4×4",
            symbol = "psychiatry",
            receiver = FocusWidgetReceiver::class.java,
            widget = FocusWidget()
        )
    )

    suspend fun requestPin(context: Context, spec: WidgetSpec): Boolean {
        val manager = GlanceAppWidgetManager(context)
        @Suppress("UNCHECKED_CAST")
        val receiver = spec.receiver as Class<GlanceAppWidgetReceiver>
        return manager.requestPinGlanceAppWidget(
            receiver = receiver,
            preview = null,
            previewState = null
        )
    }
}

/**
 * Updates only the widgets that are actually on a home screen. This keeps a
 * timer tick or a habit check-in from paying for eight provider updates when
 * the user has pinned just one tile.
 */
object WidgetUpdater {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val lock = Any()
    private var refreshJob: Job? = null
    private var focusJob: Job? = null

    fun refresh(context: Context) {
        val appContext = context.applicationContext
        synchronized(lock) {
            refreshJob?.cancel()
            refreshJob = scope.launch {
                delay(90L)
                updateWidgets(appContext, WidgetCatalog.specs)
            }
        }
    }

    fun refreshFocus(context: Context) {
        val appContext = context.applicationContext
        synchronized(lock) {
            focusJob?.cancel()
            focusJob = scope.launch {
                updateWidgets(
                    appContext,
                    WidgetCatalog.specs.filter { it.id == "pomodoro" }
                )
            }
        }
    }

    private suspend fun updateWidgets(context: Context, specs: List<WidgetSpec>) {
        val manager = AppWidgetManager.getInstance(context) ?: return
        for (spec in specs) {
            val ids = manager.getAppWidgetIds(ComponentName(context, spec.receiver))
            if (ids.isEmpty()) continue
            runCatching { spec.widget.updateAll(context) }
                .onFailure {
                    android.util.Log.w("GritWidgets", "Update failed for ${spec.id}", it)
                }
        }
    }
}
