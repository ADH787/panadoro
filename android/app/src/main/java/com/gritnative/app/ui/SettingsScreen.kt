package com.gritnative.app.ui

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.gritnative.app.BuildConfig
import com.gritnative.app.data.AppSettings
import com.gritnative.app.data.AuthService
import com.gritnative.app.data.FirestoreSync
import com.gritnative.app.data.LeaderboardService
import com.gritnative.app.data.Store
import com.gritnative.app.data.StoreRepository
import com.gritnative.app.data.SyncStatus
import com.gritnative.app.data.SyncUiState
import com.gritnative.app.data.ThemeMode
import com.gritnative.app.data.activeCategories
import com.gritnative.app.data.activeHabits
import com.gritnative.app.data.categoryById
import com.gritnative.app.ui.components.FormChip
import com.gritnative.app.ui.components.GritButton
import com.gritnative.app.ui.components.GritButtonStyle
import com.gritnative.app.ui.components.GritField
import com.gritnative.app.ui.components.GritPanel
import com.gritnative.app.ui.components.RoundIconButton
import com.gritnative.app.ui.components.SegmentOption
import com.gritnative.app.ui.components.SegmentedSelector
import com.gritnative.app.ui.components.SettingsRow
import com.gritnative.app.ui.components.SettingsSection
import com.gritnative.app.ui.components.ShapeBadge
import com.gritnative.app.ui.components.Stepper
import com.gritnative.app.ui.components.ToggleRow
import com.gritnative.app.ui.theme.Grit
import com.gritnative.app.ui.theme.Symbol
import com.gritnative.app.widgets.WidgetCatalog
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Settings keeps the local-first promises visible: appearance, the shape of a
 * week, the Firebase sync key, and a reset that is deliberately two taps.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    store: Store,
    settings: AppSettings,
    syncState: SyncUiState,
    onNewCategory: () -> Unit,
    onEditCategory: (String) -> Unit,
    onDeleteCategory: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val auth by AuthService.state.collectAsState()
    var emailInput by remember(auth.email) { mutableStateOf(auth.email) }
    var passwordInput by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var resetArmed by remember { mutableStateOf(false) }
    var notificationsEnabled by remember {
        mutableStateOf(NotificationManagerCompat.from(context).areNotificationsEnabled())
    }
    var exactAlarmsEnabled by remember { mutableStateOf(canScheduleExactAlarms(context)) }

    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                notificationsEnabled =
                    NotificationManagerCompat.from(context).areNotificationsEnabled()
                exactAlarmsEnabled = canScheduleExactAlarms(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        notificationsEnabled = granted
        if (!granted) openNotificationSettings(context)
    }
    val exactAlarmSettings = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        exactAlarmsEnabled = canScheduleExactAlarms(context)
    }

    LaunchedEffect(resetArmed) {
        if (resetArmed) {
            delay(5_000)
            resetArmed = false
        }
    }

    val configured = auth.signedIn
    val statusColor = when (syncState.status) {
        SyncStatus.ERROR -> MaterialTheme.colorScheme.error
        SyncStatus.SYNCED -> Grit.colors.success
        SyncStatus.SYNCING -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val statusContainer = when (syncState.status) {
        SyncStatus.ERROR -> MaterialTheme.colorScheme.errorContainer
        SyncStatus.SYNCED -> Grit.colors.successContainer
        else -> Grit.colors.field
    }
    val statusContent = when (syncState.status) {
        SyncStatus.ERROR -> MaterialTheme.colorScheme.onErrorContainer
        SyncStatus.SYNCED -> Grit.colors.onSuccessContainer
        else -> MaterialTheme.colorScheme.onSurface
    }
    val statusIcon = when (syncState.status) {
        SyncStatus.ERROR -> "cloud_off"
        SyncStatus.SYNCED -> "cloud_done"
        SyncStatus.SYNCING -> "cloud_sync"
        else -> "cloud"
    }
    val statusText = syncState.message.ifBlank {
        when (syncState.status) {
            SyncStatus.SYNCED -> "Synced"
            SyncStatus.SYNCING -> "Syncing…"
            SyncStatus.ERROR -> "Sync needs attention"
            SyncStatus.IDLE -> "Ready to sync"
            SyncStatus.OFF -> "Sign in to sync"
        }
    }
    val lastSync = syncState.lastSyncAt.ifBlank { settings.lastSyncAt }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 112.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item(key = "appearance") {
            SettingsSection(title = "Appearance", symbol = "palette") {
                SettingsRow(
                    symbol = "contrast",
                    title = "Theme",
                    description = "Follow the system, or pick a side"
                )
                SegmentedSelector(
                    options = listOf(
                        SegmentOption(ThemeMode.SYSTEM, "System", "contrast"),
                        SegmentOption(ThemeMode.LIGHT, "Light", "light_mode"),
                        SegmentOption(ThemeMode.DARK, "Dark", "dark_mode")
                    ),
                    selected = settings.themeMode,
                    onSelect = { StoreRepository.setThemeMode(it) },
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
                Spacer(Modifier.height(2.dp))
                ToggleRow(
                    symbol = "wallpaper",
                    label = "Dynamic color",
                    description = "Take the wallpaper palette on Android 12+",
                    checked = settings.dynamicColor,
                    onCheckedChange = { StoreRepository.setDynamicColor(it) }
                )
                ToggleRow(
                    symbol = "nightlight",
                    label = "AMOLED black",
                    description = "Pure black surfaces in the dark theme",
                    checked = settings.amoled,
                    onCheckedChange = { StoreRepository.setAmoled(it) },
                    accent = MaterialTheme.colorScheme.onSurface,
                    accentContainer = MaterialTheme.colorScheme.surfaceContainerHighest,
                    accentOn = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        item(key = "week") {
            SettingsSection(title = "Week", symbol = "date_range") {
                SettingsRow(
                    symbol = "date_range",
                    title = "Week starts on",
                    description = "Used by streaks and the weekly view",
                    trailing = {
                        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                            FormChip(
                                label = "Monday",
                                selected = settings.weekStartMonday,
                                onClick = { StoreRepository.setWeekStartMonday(true) }
                            )
                            FormChip(
                                label = "Sunday",
                                selected = !settings.weekStartMonday,
                                onClick = { StoreRepository.setWeekStartMonday(false) }
                            )
                        }
                    }
                )
            }
        }

        item(key = "today") {
            SettingsSection(title = "Today", symbol = "checklist") {
                ToggleRow(
                    symbol = "visibility",
                    label = "Show completed habits",
                    description = "Completed rows stay in the list instead of disappearing",
                    checked = settings.showCompleted,
                    onCheckedChange = { StoreRepository.setShowCompleted(it) }
                )
            }
        }

        item(key = "alerts") {
            SettingsSection(title = "Alerts", symbol = "notifications") {
                ToggleRow(
                    symbol = "alarm",
                    label = "Alarm sound",
                    description = "Ring and vibrate when a focus phase ends",
                    checked = settings.alarmSound,
                    onCheckedChange = { StoreRepository.setAlarmSound(it) },
                    accent = MaterialTheme.colorScheme.tertiary,
                    accentContainer = MaterialTheme.colorScheme.tertiaryContainer,
                    accentOn = MaterialTheme.colorScheme.onTertiaryContainer
                )
                BoxDivider()
                SettingsRow(
                    symbol = "notifications",
                    title = "Notifications",
                    description = if (notificationsEnabled) {
                        "Allowed — the live timer and completion alarm can appear"
                    } else {
                        "Turn on session progress and the alarm notification"
                    },
                    trailing = {
                        GritButton(
                            label = if (notificationsEnabled) "Settings" else "Allow",
                            onClick = {
                                if (notificationsEnabled || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                                    openNotificationSettings(context)
                                } else {
                                    notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                                }
                            },
                            symbol = if (notificationsEnabled) "settings" else "notifications",
                            style = GritButtonStyle.TONAL
                        )
                    }
                )
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    BoxDivider()
                    SettingsRow(
                        symbol = "alarm",
                        title = "Exact alarms",
                        description = if (exactAlarmsEnabled) {
                            "Allowed — sessions finish on time even while the phone sleeps"
                        } else {
                            "Allow alarms & reminders so the timer can finish in the background"
                        },
                        trailing = {
                            GritButton(
                                label = if (exactAlarmsEnabled) "Settings" else "Allow",
                                onClick = { openExactAlarmSettings(context) },
                                symbol = if (exactAlarmsEnabled) "settings" else "alarm",
                                style = GritButtonStyle.TONAL
                            )
                        }
                    )
                }
                Text(
                    text = "If a session ends silently, check the phone's alarm volume and whether " +
                        "Do Not Disturb allows alarms.",
                    color = Grit.colors.subtext,
                    fontSize = 10.5.sp,
                    lineHeight = 14.sp
                )
            }
        }

        item(key = "widgets") {
            SettingsSection(title = "Productivity widgets", symbol = "apps") {
                Text(
                    text = "Pin live Panadoro surfaces to your home screen. Resize any tile " +
                        "and it will adapt from a compact glance to a larger dashboard.",
                    color = Grit.colors.subtext,
                    fontSize = 10.5.sp,
                    lineHeight = 14.sp
                )
                WidgetCatalog.specs.forEachIndexed { index, spec ->
                    if (index > 0) BoxDivider()
                    SettingsRow(
                        symbol = spec.symbol,
                        title = spec.title,
                        description = "${spec.description} · ${spec.sizeLabel}",
                        trailing = {
                            GritButton(
                                label = "Add",
                                onClick = {
                                    scope.launch {
                                        val requested = runCatching {
                                            WidgetCatalog.requestPin(context, spec)
                                        }.getOrDefault(false)
                                        if (!requested) {
                                            Toast.makeText(
                                                context,
                                                "This launcher did not open the pin dialog. " +
                                                    "Long-press the home screen and choose Widgets → Grit.",
                                                Toast.LENGTH_LONG
                                            ).show()
                                        }
                                    }
                                },
                                symbol = "add",
                                style = GritButtonStyle.TONAL
                            )
                        }
                    )
                }
                Text(
                    text = "Android groups every tile under Panadoro in the system widget picker. " +
                        "This section is the curated productivity collection.",
                    color = Grit.colors.subtext,
                    fontSize = 9.5.sp,
                    lineHeight = 13.sp
                )
            }
        }

        item(key = "categories") {
            SettingsSection(title = "Categories", symbol = "label") {
                store.activeCategories.forEachIndexed { index, category ->
                    val count = store.activeHabits.count { it.categoryId == category.id }
                    SettingsRow(
                        symbol = category.icon,
                        title = category.name,
                        description = "$count ${if (count == 1) "habit" else "habits"}",
                        onClick = { onEditCategory(category.id) },
                        trailing = {
                            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                RoundIconButton(
                                    symbol = "edit",
                                    onClick = { onEditCategory(category.id) },
                                    size = 30.dp,
                                    iconSize = 15.dp,
                                    container = Color.Transparent,
                                    content = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                RoundIconButton(
                                    symbol = "delete",
                                    onClick = { onDeleteCategory(category.id) },
                                    size = 30.dp,
                                    iconSize = 15.dp,
                                    container = Color.Transparent,
                                    content = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    )
                    if (index != store.activeCategories.lastIndex) {
                        BoxDivider()
                    }
                }
                Spacer(Modifier.height(4.dp))
                GritButton(
                    label = "New category",
                    onClick = onNewCategory,
                    symbol = "add",
                    style = GritButtonStyle.TONAL,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        item(key = "sync") {
            SettingsSection(title = "Firebase sync", symbol = "cloud_sync") {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = statusContainer,
                    border = BorderStroke(1.dp, statusColor.copy(alpha = 0.18f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(9.dp)
                    ) {
                        Symbol(statusIcon, size = 22.dp, color = statusColor)
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = statusText,
                                color = statusContent,
                                fontSize = 12.sp,
                                lineHeight = 15.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            if (lastSync.isNotBlank()) {
                                Text(
                                    text = "Last sync $lastSync",
                                    color = statusContent.copy(alpha = 0.68f),
                                    fontSize = 10.sp,
                                    lineHeight = 13.sp
                                )
                            }
                        }
                        if (configured) {
                            GritButton(
                                label = "Sync now",
                                onClick = { scope.launch { FirestoreSync.syncNow(silent = false) } },
                                symbol = "sync",
                                style = GritButtonStyle.TONAL,
                                enabled = syncState.status != SyncStatus.SYNCING
                            )
                        }
                    }
                }

                Spacer(Modifier.height(2.dp))
                if (auth.signedIn) {
                    SettingsRow(
                        symbol = "person",
                        title = auth.email.ifBlank { "Signed in" },
                        description = "Your data is private to this account",
                        trailing = {
                            GritButton(
                                label = "Sign out",
                                onClick = { AuthService.signOut() },
                                symbol = "power",
                                style = GritButtonStyle.TONAL
                            )
                        }
                    )
                } else {
                    GritField(
                        value = emailInput,
                        onValueChange = { emailInput = it },
                        caption = "Email",
                        placeholder = "you@example.com",
                        symbol = "inbox"
                    )
                    GritField(
                        value = passwordInput,
                        onValueChange = { passwordInput = it },
                        caption = "Password",
                        placeholder = "At least 6 characters",
                        symbol = "key",
                        visualTransformation = if (showPassword) VisualTransformation.None
                        else PasswordVisualTransformation(),
                        trailing = {
                            RoundIconButton(
                                symbol = "visibility",
                                onClick = { showPassword = !showPassword },
                                size = 30.dp,
                                iconSize = 16.dp,
                                container = Color.Transparent,
                                content = if (showPassword) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                                filled = showPassword
                            )
                        }
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        GritButton(
                            label = if (auth.busy) "Please wait…" else "Sign in",
                            onClick = {
                                scope.launch {
                                    if (AuthService.signIn(emailInput, passwordInput)) {
                                        passwordInput = ""
                                        FirestoreSync.syncNow(silent = false)
                                        LeaderboardService.publish()
                                    }
                                }
                            },
                            symbol = "arrow_forward",
                            style = GritButtonStyle.FILLED,
                            enabled = !auth.busy,
                            modifier = Modifier.weight(1f)
                        )
                        GritButton(
                            label = "Create account",
                            onClick = {
                                scope.launch {
                                    if (AuthService.createAccount(emailInput, passwordInput)) {
                                        passwordInput = ""
                                        FirestoreSync.syncNow(silent = false)
                                        LeaderboardService.publish()
                                    }
                                }
                            },
                            symbol = "person",
                            style = GritButtonStyle.TONAL,
                            enabled = !auth.busy,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    if (auth.error.isNotBlank()) {
                        Text(
                            text = auth.error,
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 11.sp,
                            lineHeight = 14.sp
                        )
                    }

                    Text(
                        text = "Create an account here, then sign in with the same email on your " +
                            "other devices. Your habits, tasks and focus history stay private to " +
                            "your account.",
                        color = Grit.colors.subtext,
                        fontSize = 10.5.sp,
                        lineHeight = 14.sp
                    )
                }

                Spacer(Modifier.height(4.dp))
                ToggleRow(
                    symbol = "sync",
                    label = "Live sync",
                    description = "Pushes after each edit and checks for changes every 5 seconds",
                    checked = settings.autoSync,
                    onCheckedChange = { StoreRepository.setAutoSync(it) }
                )
            }
        }

        item(key = "about") {
            GritPanel {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    ShapeBadge(
                        symbol = "local_fire_department",
                        shapeName = "Cookie12Sided",
                        container = MaterialTheme.colorScheme.primary,
                        content = MaterialTheme.colorScheme.onPrimary,
                        size = 52.dp,
                        iconSize = 26.dp,
                        filled = true
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Panadoro ${BuildConfig.VERSION_NAME} × Tomato",
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 19.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Habits, tasks and tagged focus in one quiet place",
                            color = Grit.colors.subtext,
                            fontSize = 10.5.sp,
                            lineHeight = 14.sp
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Data is stored locally in grit-store.json and merged record by record when sync is on. " +
                        "The timer and its sessions live in a foreground service, so closing the app never stops a session.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 10.5.sp,
                    lineHeight = 14.sp
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Device ${settings.deviceId}",
                    color = Grit.colors.subtext,
                    fontSize = 9.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        item(key = "data") {
            SettingsSection(title = "Data", symbol = "database") {
                SettingsRow(
                    symbol = "delete_forever",
                    title = "Reset Panadoro data",
                    description = if (resetArmed) {
                        "Press again to erase habits, tasks and focus history. This syncs the deletion."
                    } else {
                        "Erase every habit, task and session on every synced device"
                    },
                    danger = resetArmed
                )
                GritButton(
                    label = if (resetArmed) "Erase everything" else "Reset…",
                    onClick = {
                        if (resetArmed) {
                            StoreRepository.resetAllData()
                            resetArmed = false
                        } else {
                            resetArmed = true
                        }
                    },
                    symbol = if (resetArmed) "warning" else "restart_alt",
                    style = if (resetArmed) GritButtonStyle.FILLED else GritButtonStyle.TONAL,
                    danger = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun BoxDivider() {
    Spacer(
        Modifier
            .fillMaxWidth()
            .height(1.dp)
            .padding(horizontal = 8.dp)
            .background(Grit.colors.outline.copy(alpha = 0.18f))
    )
}

private fun canScheduleExactAlarms(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
    val manager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    return runCatching { manager.canScheduleExactAlarms() }.getOrDefault(false)
}

private fun openNotificationSettings(context: Context) {
    runCatching {
        context.startActivity(
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        )
    }
}

private fun openExactAlarmSettings(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
    runCatching {
        context.startActivity(
            Intent(
                Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                Uri.parse("package:${context.packageName}")
            )
        )
    }
}
