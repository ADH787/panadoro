package com.gritnative.app.widgets

import android.content.Context
import android.content.Intent
import androidx.glance.GlanceId
import androidx.glance.action.Action
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionStartActivity
import com.gritnative.app.MainActivity
import com.gritnative.app.data.StoreRepository

internal val HabitIdKey = ActionParameters.Key<String>("habitId")
internal val TaskIdKey = ActionParameters.Key<String>("taskId")

internal fun openGritAction(context: Context, tab: String): Action {
    val intent = Intent(context, MainActivity::class.java)
        .putExtra(MainActivity.EXTRA_TAB, tab)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
    return actionStartActivity(intent)
}

private suspend fun prepare(context: Context) {
    StoreRepository.init(context)
    StoreRepository.awaitLoaded()
}

class ToggleHabitAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        val habitId = parameters[HabitIdKey] ?: return
        prepare(context)
        StoreRepository.toggleHabitDone(habitId)
        WidgetUpdater.updateNow(context)
    }
}

class ToggleTaskAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        val taskId = parameters[TaskIdKey] ?: return
        prepare(context)
        StoreRepository.toggleTask(taskId)
        WidgetUpdater.updateNow(context)
    }
}

class ToggleTimerAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        prepare(context)
        StoreRepository.toggleTimer()
        WidgetUpdater.updateNow(context)
    }
}

class SkipTimerAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        prepare(context)
        StoreRepository.skipTimer()
        WidgetUpdater.updateNow(context)
    }
}

class StartFocusAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        prepare(context)
        StoreRepository.startFocusNow()
        WidgetUpdater.updateNow(context)
    }
}
