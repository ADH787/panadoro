package com.gritnative.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import com.gritnative.app.ui.components.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gritnative.app.data.AppSettings
import com.gritnative.app.data.AuthService
import com.gritnative.app.data.GritEvent
import com.gritnative.app.data.Store
import com.gritnative.app.data.StoreRepository
import com.gritnative.app.data.activeCategories
import com.gritnative.app.data.TimerState
import com.gritnative.app.data.activeTag
import com.gritnative.app.data.computeSecondsLeft
import com.gritnative.app.data.dateKey
import com.gritnative.app.data.formatClock
import com.gritnative.app.data.openTaskCount
import com.gritnative.app.data.todayHabits
import com.gritnative.app.data.todayHabitsDone
import com.gritnative.app.AppForeground
import com.gritnative.app.timer.CompletionAlarm
import com.gritnative.app.ui.components.AmbientBackdrop
import com.gritnative.app.ui.components.CategoryEditorSheet
import com.gritnative.app.ui.components.DeleteTagSheet
import com.gritnative.app.ui.components.EntranceWindow
import com.gritnative.app.ui.components.HabitEditorSheet
import com.gritnative.app.ui.components.LocalArabic
import com.gritnative.app.ui.components.LocalEntranceWindow
import com.gritnative.app.ui.components.RoundIconButton
import com.gritnative.app.ui.components.ShapeBadge
import com.gritnative.app.ui.components.StaggeredEntry
import com.gritnative.app.ui.components.TagEditorSheet
import com.gritnative.app.ui.components.TaskEditorSheet
import com.gritnative.app.ui.components.translateText
import com.gritnative.app.ui.theme.Grit
import com.gritnative.app.ui.theme.Symbol
import kotlin.math.abs
import kotlinx.coroutines.delay

private data class GritTab(val id: String, val label: String, val symbol: String)

/** How long the screen may stay untouched before the AOD fades in by itself. */
private const val AOD_AFTER_MS = 45_000L

private sealed interface AppSheet {
    data class Habit(val id: String) : AppSheet
    data class Task(val id: String) : AppSheet
    data class Tag(val id: String) : AppSheet
    data class DeleteTag(val id: String) : AppSheet
    data class Category(val id: String) : AppSheet
}

private data class Celebration(
    val id: Long,
    val kind: String,
    val title: String,
    val subtitle: String,
    val symbol: String
)

private val allTabs = listOf(
    GritTab("today", "Habits", "wb_sunny"),
    GritTab("tasks", "Tasks", "checklist"),
    GritTab("focus", "Focus", "psychiatry"),
    GritTab("stats", "Stats", "monitoring"),
    GritTab("ranks", "Ranks", "emoji_events")
)

/**
 * The native shell: one top bar, one floating navigation bar, one sheet host and
 * a single box where celebrations land. The timer keeps living in the service,
 * so this composable can disappear without stopping a session.
 */
@Composable
fun AppNav(
    requestedTab: String? = null,
    onRequestHandled: () -> Unit = {}
) {
    val store by StoreRepository.store.collectAsState()
    val settings by StoreRepository.settings.collectAsState()
    val ready by StoreRepository.ready.collectAsState()
    val syncState by StoreRepository.syncState.collectAsState()
    val auth by AuthService.state.collectAsState()
    val alarmActive by CompletionAlarm.active.collectAsState()
    // Every tab can be hidden, but the settings layer refuses to turn off the
    // last one, so this list is never empty.
    val tabs = allTabs.filter { it.id !in settings.hiddenTabs }.ifEmpty { allTabs.take(1) }

    var tab by rememberSaveable { mutableStateOf("today") }
    var settingsOpen by rememberSaveable { mutableStateOf(false) }
    var sheet by remember { mutableStateOf<AppSheet?>(null) }
    var celebration by remember { mutableStateOf<Celebration?>(null) }
    var pendingCategoryDelete by remember { mutableStateOf<String?>(null) }
    var wasSignedIn by remember { mutableStateOf(auth.signedIn) }

    LaunchedEffect(auth.signedIn) {
        if (wasSignedIn && !auth.signedIn) {
            // A deliberate sign-out returns to the login screen; a cold start
            // with a saved refresh token never passes through here.
            StoreRepository.setAuthSkipped(false)
        }
        wasSignedIn = auth.signedIn
    }

    LaunchedEffect(requestedTab, tabs) {
        val request = requestedTab ?: return@LaunchedEffect
        when (request) {
            "settings" -> settingsOpen = true
            else -> if (allTabs.any { it.id == request }) {
                // A widget or shortcut may point at a tab the user hid; honour
                // the tap and bring that tab back.
                if (tabs.none { it.id == request }) {
                    StoreRepository.setTabHidden(request, false)
                }
                settingsOpen = false
                tab = request
            }
        }
        onRequestHandled()
    }

    // Hiding the tab the user is standing on quietly returns them home.
    LaunchedEffect(tabs.map { it.id }) {
        if (tabs.none { it.id == tab }) tab = tabs.first().id
    }

    LaunchedEffect(Unit) {
        StoreRepository.events.collect { event ->
            celebration = when (event) {
                // In the foreground the full-screen alarm owns the moment; the
                // confetti would only rattle behind it.
                is GritEvent.FocusCompleted -> if (event.wasFocus && !AppForeground.isForeground) {
                    Celebration(
                        id = System.nanoTime(),
                        kind = "focus",
                        title = "Focus complete · ${event.tagName}",
                        subtitle = "${event.minutes} ${if (event.minutes == 1) "minute" else "minutes"} banked. " +
                            "Let the rest of the day be lighter.",
                        symbol = "celebration"
                    )
                } else {
                    null
                }
                is GritEvent.AllHabitsDone -> Celebration(
                    id = System.nanoTime(),
                    kind = "habits",
                    title = "All ${event.total} habits done",
                    subtitle = "${event.done} of ${event.total} cleared for today. " +
                        "Enjoy the quiet — you earned it.",
                    symbol = "done_all"
                )
                GritEvent.SyncFinished -> null
            }
        }
    }

    BackHandler(
        enabled = alarmActive != null || celebration != null ||
            pendingCategoryDelete != null || sheet != null || settingsOpen
    ) {
        when {
            alarmActive != null -> CompletionAlarm.stop()
            celebration != null -> celebration = null
            pendingCategoryDelete != null -> pendingCategoryDelete = null
            sheet != null -> sheet = null
            settingsOpen -> settingsOpen = false
        }
    }

    if (!ready) {
        GritSplash()
        return
    }

    if (!auth.signedIn && !settings.authSkipped) {
        AuthScreen(
            onSignedIn = {
                StoreRepository.setAuthSkipped(false)
                celebration = null
            },
            onSkip = { StoreRepository.setAuthSkipped(true) }
        )
        return
    }

    val pageOrder = tabs.map { it.id }
    val title = if (settingsOpen) "Settings"
    else tabs.firstOrNull { it.id == tab }?.label ?: tabs.firstOrNull()?.label ?: "Habits"

    val timer = store.timer
    val running = timer?.running == true

    // The always-on display: with the option on, a running phase and an
    // untouched screen fade into the dim countdown after a little while. The
    // stage's moon button does the same immediately; any touch brings the
    // full app back, and a stopped phase always leaves.
    LaunchedEffect(settings.alwaysOnDisplay, running, alarmActive != null) {
        if (alarmActive != null) {
            Aod.active = false
            return@LaunchedEffect
        }
        if (!running || !settings.alwaysOnDisplay) {
            Aod.active = false
            return@LaunchedEffect
        }
        while (true) {
            if (!Aod.active && System.currentTimeMillis() - Aod.lastInteraction > AOD_AFTER_MS) {
                Aod.active = true
            }
            delay(5_000)
        }
    }

    // One pager holds the visible tabs: swiping sideways moves between them,
    // and a navigation-bar tap glides the same pages. The state is keyed to
    // the visible-tab list so hiding a tab earlier in the list cannot make the
    // pager's old index point at a different tab.
    val pagerKey = pageOrder.joinToString("|")
    val pagerState = key(pagerKey) {
        rememberPagerState(
            initialPage = pageOrder.indexOf(tab).coerceAtLeast(0),
            pageCount = { pageOrder.size }
        )
    }

    // The tab follows the swipe in real time: targetPage flips the moment a
    // drag makes the neighbouring page dominant, so the header and the nav
    // bar update while the swipe is still in motion instead of a beat after
    // it settles.
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.targetPage }.collect { page ->
            val id = pageOrder.getOrNull(page) ?: return@collect
            if (id != tab) {
                settingsOpen = false
                tab = id
            }
        }
    }

    // A tab tap (or a deep link) glides the pager to that page.
    LaunchedEffect(tab, settingsOpen, pagerState) {
        if (settingsOpen) return@LaunchedEffect
        val target = pageOrder.indexOf(tab).coerceAtLeast(0)
        if (!pagerState.isScrollInProgress && pagerState.settledPage != target) {
            pagerState.animateScrollToPage(target)
        }
    }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(Modifier.fillMaxSize()) {
            GritTopBar(
                title = title,
                settingsOpen = settingsOpen,
                timer = timer,
                onBack = { settingsOpen = false },
                onTimerClick = {
                    // A running timer is always reachable: if Focus was hidden,
                    // tapping its chip brings the tab back for this visit.
                    if (tabs.none { it.id == "focus" }) {
                        StoreRepository.setTabHidden("focus", false)
                    }
                    settingsOpen = false
                    tab = "focus"
                },
                onSettings = { settingsOpen = true }
            )
            Spacer(Modifier.height(12.dp))

            Box(Modifier.fillMaxWidth().weight(1f)) {
                AnimatedContent(
                    targetState = settingsOpen,
                    transitionSpec = {
                        (fadeIn(tween(260)) + slideInVertically(tween(300, easing = FastOutSlowInEasing)) { it / 8 })
                            .togetherWith(
                                fadeOut(tween(170)) +
                                    slideOutVertically(tween(200)) { -it / 10 }
                            )
                    },
                    label = "grit-page"
                ) { openSettings ->
                    if (openSettings) {
                        CompositionLocalProvider(
                            LocalEntranceWindow provides remember { EntranceWindow() }
                        ) {
                            SettingsScreen(
                                store = store,
                                settings = settings,
                                syncState = syncState,
                                onNewCategory = { sheet = AppSheet.Category("") },
                                onEditCategory = { sheet = AppSheet.Category(it) },
                                onDeleteCategory = { pendingCategoryDelete = it }
                            )
                        }
                    } else {
                        HorizontalPager(
                            state = pagerState,
                            modifier = Modifier.fillMaxSize(),
                            key = { page -> pageOrder[page] }
                        ) { page ->
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .graphicsLayer {
                                        // Pages cross-fade as they take over —
                                        // no scale, nothing grows or shrinks.
                                        val distance = abs(
                                            (pagerState.currentPage - page).toFloat() +
                                                pagerState.currentPageOffsetFraction
                                        ).coerceIn(0f, 1f)
                                        alpha = 1f - 0.35f * distance
                                    }
                            ) {
                                CompositionLocalProvider(
                                    LocalEntranceWindow provides remember { EntranceWindow() }
                                ) {
                                    when (pageOrder[page]) {
                                    "tasks" -> TasksScreen(
                                        store = store,
                                        onEditTask = { sheet = AppSheet.Task(it) }
                                    )
                                    "focus" -> FocusScreen(
                                        store = store,
                                        onNewTag = { sheet = AppSheet.Tag("") },
                                        onEditTag = { sheet = AppSheet.Tag(it) },
                                        onDeleteTag = { sheet = AppSheet.DeleteTag(it) },
                                        onEditTask = { sheet = AppSheet.Task(it) }
                                    )
                                    "stats" -> StatsScreen(
                                        store = store,
                                        weekStartMonday = settings.weekStartMonday
                                    )
                                    "ranks" -> RanksScreen(
                                        store = store,
                                        settings = settings,
                                        signedIn = auth.signedIn,
                                        onSignIn = { StoreRepository.setAuthSkipped(false) }
                                    )
                                    else -> TodayScreen(
                                        store = store,
                                        weekStartMonday = settings.weekStartMonday,
                                        onNewHabit = { sheet = AppSheet.Habit("") },
                                        onEditHabit = { sheet = AppSheet.Habit(it) },
                                        onNewCategory = { sheet = AppSheet.Category("") },
                                        onEditCategory = { sheet = AppSheet.Category(it) },
                                        onDeleteCategory = { pendingCategoryDelete = it }
                                    )
                                }
                                }
                            }
                        }
                    }
                }

            }
        }

        val fabLabel = when (tab) {
            "today" -> "New habit"
            "tasks" -> "New task"
            else -> null
        }
        AnimatedVisibility(
            visible = !settingsOpen && fabLabel != null,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(end = 18.dp, bottom = 92.dp),
            enter = fadeIn(tween(200)) +
                scaleIn(
                    initialScale = 0.7f,
                    animationSpec = spring(dampingRatio = 0.62f, stiffness = 420f)
                ) +
                slideInVertically(spring(dampingRatio = 0.75f, stiffness = 380f)) { it / 2 },
            exit = fadeOut(tween(160)) +
                scaleOut(targetScale = 0.8f, animationSpec = tween(160))
        ) {
            GritFab(
                label = fabLabel.orEmpty(),
                symbol = if (tab == "today") "add_task" else "add",
                onClick = {
                    sheet = if (tab == "today") AppSheet.Habit("") else AppSheet.Task("")
                }
            )
        }

        AnimatedVisibility(
            visible = !settingsOpen,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically(
                animationSpec = spring(dampingRatio = 0.78f, stiffness = 360f),
                initialOffsetY = { it }
            ) + fadeIn(tween(180)),
            exit = slideOutVertically(
                animationSpec = tween(240, easing = FastOutSlowInEasing),
                targetOffsetY = { it }
            ) + fadeOut(tween(150))
        ) {
            GritBottomNav(
                tabs = tabs,
                current = tab,
                store = store,
                running = running,
                onSelect = {
                    settingsOpen = false
                    tab = it
                },
                modifier = Modifier
                    .navigationBarsPadding()
                    .padding(start = 10.dp, end = 10.dp, top = 6.dp, bottom = 10.dp)
            )
        }

        celebration?.let { item ->
            CelebrationOverlay(
                seed = item.id,
                title = item.title,
                subtitle = item.subtitle,
                symbol = item.symbol,
                success = item.kind == "habits",
                onDismiss = { celebration = null }
            )
        }

        // The dim, always-on countdown sits under everything else that can
        // claim the screen, and the completion alarm sits above it.
        AodLayer(store = store, timer = timer, suppressed = alarmActive != null)

        alarmActive?.let { payload ->
            CompletionAlarmOverlay(
                payload = payload,
                onDismiss = { CompletionAlarm.stop() }
            )
        }
    }

    val pending = pendingCategoryDelete
    if (pending != null) {
        val category = store.activeCategories.firstOrNull { it.id == pending }
        AlertDialog(
            onDismissRequest = { pendingCategoryDelete = null },
            title = { Text("Delete category?") },
            text = {
                Text(
                    "Habits and tasks in ${category?.name ?: "this category"} move to another category. " +
                        "The deletion syncs to your other devices."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        StoreRepository.deleteCategory(pending)
                        pendingCategoryDelete = null
                    }
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingCategoryDelete = null }) { Text("Cancel") }
            },
            shape = RoundedCornerShape(28.dp)
        )
    }

    sheet?.let { current ->
        when (current) {
            is AppSheet.Habit -> HabitEditorSheet(
                store = store,
                habitId = current.id,
                onDismiss = { sheet = null }
            )
            is AppSheet.Task -> TaskEditorSheet(
                store = store,
                taskId = current.id,
                onDismiss = { sheet = null }
            )
            is AppSheet.Tag -> TagEditorSheet(
                store = store,
                tagId = current.id,
                onDismiss = { sheet = null }
            )
            is AppSheet.DeleteTag -> DeleteTagSheet(
                store = store,
                tagId = current.id,
                onDismiss = { sheet = null }
            )
            is AppSheet.Category -> CategoryEditorSheet(
                store = store,
                categoryId = current.id,
                onDismiss = { sheet = null }
            )
        }
    }
}

@Composable
private fun GritTopBar(
    title: String,
    settingsOpen: Boolean,
    timer: TimerState?,
    onBack: () -> Unit,
    onTimerClick: () -> Unit,
    onSettings: () -> Unit
) {
    // The wall clock lives here, so a running session only recomposes this
    // bar (and the focus screen's own clock) — never the pager or the pages.
    val now = rememberTimerNow(timer)
    val secondsLeft = computeSecondsLeft(timer, now)
    // One centred header, with the controls floating at the two ends. No
    // subtitle: the title alone names the page.
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .defaultMinSize(minHeight = 100.dp)
            .padding(horizontal = 10.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        AnimatedContent(
            targetState = title,
            transitionSpec = {
                (fadeIn(tween(240)) + slideInVertically(tween(280, easing = FastOutSlowInEasing)) { it / 3 })
                    .togetherWith(fadeOut(tween(130)) + slideOutVertically(tween(160)) { -it / 4 })
            },
            label = "top-title"
        ) { value ->
            Text(
                text = value,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 44.sp,
                lineHeight = 48.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
        }

        Row(
            modifier = Modifier.align(Alignment.CenterStart),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (settingsOpen) {
                RoundIconButton(
                    symbol = "arrow_back",
                    onClick = onBack,
                    size = 38.dp,
                    iconSize = 19.dp,
                    container = Grit.colors.field
                )
            }
        }

        Row(
            modifier = Modifier.align(Alignment.CenterEnd),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            AnimatedVisibility(
                visible = timer?.running == true,
                enter = fadeIn(tween(180)) + scaleIn(initialScale = 0.7f),
                exit = fadeOut(tween(130)) + scaleOut(targetScale = 0.7f)
            ) {
                val focus = timer?.phase == "focus"
                Surface(
                    onClick = onTimerClick,
                    modifier = Modifier.height(30.dp),
                    shape = CircleShape,
                    color = if (focus) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = if (focus) MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.onTertiaryContainer
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxHeight()
                            .padding(horizontal = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Symbol(
                            if (focus) "psychiatry" else "coffee",
                            size = 13.dp,
                            filled = true
                        )
                        // The clock digits swap instantly — a per-second cross-fade
                        // reads as a flicker. The desktop app does the same
                        // (animateChange: false on its clock text).
                        Text(
                            text = formatClock(secondsLeft),
                            fontSize = 11.sp,
                            lineHeight = 14.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                    }
                }
            }

            if (!settingsOpen) {
                RoundIconButton(
                    symbol = "settings",
                    onClick = onSettings,
                    size = 38.dp,
                    iconSize = 19.dp,
                    container = Grit.colors.field,
                    content = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun GritBottomNav(
    tabs: List<GritTab>,
    current: String,
    store: Store,
    running: Boolean,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    // The Tomato-style bar: the selected tab's button widens to pop its name
    // open. The width change is driven by a single animated index — every
    // slot's width is a fraction of that one value, so the five widths sum
    // to exactly one slot at every instant and the bar's total width is
    // constant by construction. The pill itself also gets an explicit fixed
    // width, so even sub-pixel rounding can never make it breathe.
    val desiredUnit =
        ((LocalConfiguration.current.screenWidthDp - 360).coerceIn(0, 160)) / 160f
    val buttonSize = (52 + 8 * desiredUnit).dp
    val unit = ((buttonSize.value - 52f) / 8f).coerceIn(0f, 1f)
    val iconBoxSize = (22 + 3 * unit).dp
    val symbolSize = (20 + 3 * unit).dp
    val labelSize = (13 + 1.5 * unit).sp
    val buttonPad = (buttonSize - iconBoxSize) / 2

    val barGap = 4.dp
    val barPadding = 8.dp
    val labelGap = 7.dp

    val arabic = LocalArabic.current
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()
    val baseStyle = LocalTextStyle.current
    val labelKey = tabs.joinToString("|") { it.label }
    val labelSlotWidth = remember(labelSize, arabic, labelKey) {
        with(density) {
            tabs.maxOf { item ->
                textMeasurer.measure(
                    translateText(item.label),
                    baseStyle.merge(
                        TextStyle(fontSize = labelSize, fontWeight = FontWeight.SemiBold)
                    )
                ).size.width.toDp()
            } + 8.dp
        }
    }

    val selectedIndex = tabs.indexOfFirst { it.id == current }.coerceAtLeast(0)
    val animatedIndex by animateFloatAsState(
        targetValue = selectedIndex.toFloat(),
        animationSpec = tween(260, easing = FastOutSlowInEasing),
        label = "nav-index"
    )

    val barWidth = barPadding * 2 + buttonSize * tabs.size +
        barGap * (tabs.size - 1).coerceAtLeast(0) + labelGap + labelSlotWidth

    Box(
        modifier = modifier
            .width(barWidth)
            .shadow(12.dp, CircleShape, clip = false)
            .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier.padding(barPadding),
            horizontalArrangement = Arrangement.spacedBy(barGap),
            verticalAlignment = Alignment.CenterVertically
        ) {
            tabs.forEachIndexed { index, item ->
                val selected = item.id == current
                val container by animateColorAsState(
                    targetValue = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                    animationSpec = tween(260, easing = FastOutSlowInEasing),
                    label = "nav-container"
                )
                val content by animateColorAsState(
                    targetValue = if (selected) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onPrimaryContainer,
                    animationSpec = tween(220),
                    label = "nav-content"
                )
                // Single source of truth: this slot's share of the one
                // animated index. Between two tabs the pair sums to 1.
                val slotFraction = (1f - abs(animatedIndex - index)).coerceIn(0f, 1f)
                val gapWidth = labelGap * slotFraction
                val slotWidth = labelSlotWidth * slotFraction
                val badge = when (item.id) {
                    "tasks" -> store.openTaskCount().takeIf { it > 0 }?.toString().orEmpty()
                    "focus" -> if (running) "•" else ""
                    else -> ""
                }
                Surface(
                    onClick = { onSelect(item.id) },
                    shape = CircleShape,
                    color = container,
                    contentColor = content,
                    modifier = Modifier.height(buttonSize)
                ) {
                    Row(
                        // fillMaxHeight keeps the content vertically centred
                        // in the fixed-height surface at every width.
                        modifier = Modifier
                            .fillMaxHeight()
                            .padding(horizontal = buttonPad),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            Modifier.size(iconBoxSize),
                            contentAlignment = Alignment.Center
                        ) {
                            Symbol(
                                item.symbol,
                                size = symbolSize,
                                color = content,
                                filled = selected
                            )
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .offset(x = 5.dp, y = (-4).dp),
                                contentAlignment = Alignment.Center
                            ) {
                                AnimatedContent(
                                    targetState = badge,
                                    transitionSpec = {
                                        (fadeIn(tween(180)) + scaleIn(initialScale = 0.5f))
                                            .togetherWith(fadeOut(tween(100)) + scaleOut(targetScale = 0.5f))
                                    },
                                    label = "nav-badge"
                                ) { value ->
                                    if (value.isNotBlank()) {
                                        val dot = value == "•"
                                        Box(
                                            modifier = Modifier
                                                .widthIn(min = if (dot) 6.dp else 13.dp)
                                                .height(if (dot) 6.dp else 13.dp)
                                                .background(
                                                    if (dot) content
                                                    else MaterialTheme.colorScheme.primary,
                                                    CircleShape
                                                ),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (!dot) {
                                                Text(
                                                    text = value,
                                                    color = MaterialTheme.colorScheme.onPrimary,
                                                    fontSize = 8.sp,
                                                    lineHeight = 9.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    maxLines = 1,
                                                    textAlign = TextAlign.Center
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        if (slotFraction > 0.004f) {
                            Spacer(Modifier.width(gapWidth))
                            Box(
                                modifier = Modifier
                                    .width(slotWidth)
                                    .clipToBounds()
                            ) {
                                Text(
                                    text = item.label,
                                    color = content,
                                    fontSize = labelSize,
                                    lineHeight = (labelSize.value + 2.5f).sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GritFab(
    label: String,
    symbol: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(21.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        shadowElevation = 8.dp
    ) {
        AnimatedContent(
            targetState = label to symbol,
            transitionSpec = {
                (fadeIn(tween(190)) + slideInVertically(tween(220)) { it / 3 })
                    .togetherWith(fadeOut(tween(110)) + slideOutVertically(tween(130)) { -it / 3 })
            },
            label = "fab-content"
        ) { (shownLabel, shownSymbol) ->
            Row(
                modifier = Modifier.padding(horizontal = 17.dp, vertical = 13.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Symbol(shownSymbol, size = 21.dp, filled = true)
                Text(
                    text = shownLabel,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun GritSplash() {
    val transition = rememberInfiniteTransition(label = "splash")
    val pulse by transition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(1_100, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "splashPulse"
    )
    val drift by transition.animateFloat(
        initialValue = -6f,
        targetValue = 6f,
        animationSpec = infiniteRepeatable(
            animation = tween(2_400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "splashDrift"
    )
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        AmbientBackdrop(
            modifier = Modifier.fillMaxSize(),
            first = MaterialTheme.colorScheme.primary,
            second = MaterialTheme.colorScheme.tertiary,
            third = MaterialTheme.colorScheme.secondary
        )
        CompositionLocalProvider(LocalEntranceWindow provides remember { EntranceWindow() }) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
            StaggeredEntry(0) {
                Box(Modifier.scale(pulse).rotate(drift)) {
                    ShapeBadge(
                        symbol = "local_fire_department",
                        shapeName = "Cookie12Sided",
                        container = MaterialTheme.colorScheme.primary,
                        content = MaterialTheme.colorScheme.onPrimary,
                        size = 88.dp,
                        iconSize = 44.dp,
                        filled = true
                    )
                }
            }
            StaggeredEntry(1) {
                Text(
                    text = "Panadoro",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            StaggeredEntry(2) {
                Text(
                    text = "Habits, tasks and focus",
                    color = Grit.colors.subtext,
                    fontSize = 12.sp
                )
            }
            StaggeredEntry(3) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Spacer(Modifier.height(4.dp))
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        strokeWidth = 2.5.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            }
        }
    }
}
