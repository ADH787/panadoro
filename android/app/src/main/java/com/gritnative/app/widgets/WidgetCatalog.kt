package com.gritnative.app.widgets

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.updateAll
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

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
 * One instance per widget, shared by the receivers and the updater. Glance
 * binds a widget instance to its home-screen ids when the receiver first
 * hosts it; updating through a different instance of the same class finds no
 * ids and silently does nothing — the exact bug that left pinned widgets
 * stale while the app itself kept ticking.
 */
object GritWidgetHost {
    val today = TodayWidget()
    val tasks = TasksWidget()
    val focus = FocusWidget()
}

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
            widget = GritWidgetHost.today
        ),
        WidgetSpec(
            id = "tasks",
            title = "Tasks",
            description = "Today's task queue, overdue work and quick completion.",
            sizeLabel = "2×2 → 4×4",
            symbol = "task_alt",
            receiver = TasksWidgetReceiver::class.java,
            widget = GritWidgetHost.tasks
        ),
        WidgetSpec(
            id = "pomodoro",
            title = "Pomodoro",
            description = "A live focus countdown with pause, resume and skip.",
            sizeLabel = "2×2 → 4×4",
            symbol = "psychiatry",
            receiver = FocusWidgetReceiver::class.java,
            widget = GritWidgetHost.focus
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
    private val updateMutex = Mutex()
    private var worker: Job? = null
    private var pendingAll = false
    private var pendingFocus = false

    fun refresh(context: Context) {
        request(context, all = true)
    }

    fun refreshFocus(context: Context) {
        request(context, all = false)
    }

    /**
     * Coalesce updates instead of cancelling the previous one. A widget action
     * or a timer change can arrive while Glance is still composing the last
     * frame; cancelling that job left pinned widgets showing the state before
     * the tap. Requests now set a flag, the running worker folds them into its
     * next pass, and a new worker starts only when the previous one has really
     * finished.
     */
    private fun request(context: Context, all: Boolean) {
        val appContext = context.applicationContext
        synchronized(lock) {
            if (all) pendingAll = true else pendingFocus = true
            if (worker?.isActive == true) return
            worker = scope.launch {
                delay(90L)
                drain(appContext)
            }
        }
    }

    private suspend fun drain(context: Context) {
        while (true) {
            val (all, focus) = synchronized(lock) {
                if (!pendingAll && !pendingFocus) {
                    worker = null
                    return
                }
                val nextAll = pendingAll
                val nextFocus = pendingFocus
                pendingAll = false
                pendingFocus = false
                nextAll to nextFocus
            }
            if (all) {
                updateWidgets(context, WidgetCatalog.specs)
            } else if (focus) {
                updateWidgets(
                    context,
                    WidgetCatalog.specs.filter { it.id == "pomodoro" }
                )
            }
        }
    }

    /**
     * Completes an update inside the caller's lifetime. Widget action
     * receivers and day-rollover receivers can be torn down as soon as
     * onReceive returns, so they must not hand the last frame to a delayed
     * process-wide job that may be killed first.
     */
    suspend fun updateNow(context: Context, widgetId: String? = null) {
        val specs = if (widgetId == null) {
            WidgetCatalog.specs
        } else {
            WidgetCatalog.specs.filter { it.id == widgetId }
        }
        updateWidgets(context.applicationContext, specs)
    }

    private suspend fun updateWidgets(context: Context, specs: List<WidgetSpec>) {
        updateMutex.withLock {
            val manager = AppWidgetManager.getInstance(context) ?: return
            for (spec in specs) {
                val ids = manager.getAppWidgetIds(ComponentName(context, spec.receiver))
                if (ids.isEmpty()) continue
                try {
                    spec.widget.updateAll(context)
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    android.util.Log.w("GritWidgets", "Update failed for ${spec.id}", error)
                }
            }
        }
    }
}

/**
 * Widgets go stale when the app's process is killed and no timer is running:
 * nothing is left to push fresh data to the home screen. This light repeating
 * alarm (inexact, so the system batches it) wakes the widgets roughly every
 * quarter hour; with none pinned it costs a single no-op.
 */
object WidgetKeepAlive {
    private const val REQUEST_CODE = 4032
    private const val INTERVAL_MS = 15 * 60_000L

    fun schedule(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pending = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            Intent(context, WidgetKeepAliveReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        runCatching {
            alarmManager.setInexactRepeating(
                AlarmManager.ELAPSED_REALTIME,
                android.os.SystemClock.elapsedRealtime() + INTERVAL_MS,
                INTERVAL_MS,
                pending
            )
        }
    }
}
