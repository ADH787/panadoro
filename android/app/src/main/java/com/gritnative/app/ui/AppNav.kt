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
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkHorizontally
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import com.gritnative.app.ui.components.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
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
import com.gritnative.app.data.dueTodayTaskCount
import com.gritnative.app.data.formatClock
import com.gritnative.app.data.openTaskCount
import com.gritnative.app.data.todayHabits
import com.gritnative.app.data.todayHabitsDone
import com.gritnative.app.ui.components.AmbientBackdrop
import com.gritnative.app.ui.components.CategoryEditorSheet
import com.gritnative.app.ui.components.DeleteTagSheet
import com.gritnative.app.ui.components.EntranceWindow
import com.gritnative.app.ui.components.HabitEditorSheet
import com.gritnative.app.ui.components.LocalEntranceWindow
import com.gritnative.app.ui.components.RoundIconButton
import com.gritnative.app.ui.components.ShapeBadge
import com.gritnative.app.ui.components.StaggeredEntry
import com.gritnative.app.ui.components.TagEditorSheet
import com.gritnative.app.ui.components.TaskEditorSheet
import com.gritnative.app.ui.theme.Grit
import com.gritnative.app.ui.theme.Symbol
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private data class GritTab(val id: String, val label: String, val symbol: String)

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

/**
 * The sign-in screen is shown on every cold start while signed out, but a user
 * who chooses to continue without an account should not be asked again until the
 * process restarts (or until they tap "Sign in" somewhere).
 */
private val authSkippedThisProcess = mutableStateOf(false)

private val tabs = listOf(
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
            authSkippedThisProcess.value = false
        }
        wasSignedIn = auth.signedIn
    }

    LaunchedEffect(requestedTab) {
        val request = requestedTab ?: return@LaunchedEffect
        when (request) {
            "settings" -> settingsOpen = true
            "today", "tasks", "focus", "stats", "ranks" -> {
                settingsOpen = false
                tab = request
            }
        }
        onRequestHandled()
    }

    LaunchedEffect(Unit) {
        StoreRepository.events.collect { event ->
            celebration = when (event) {
                is GritEvent.FocusCompleted -> if (event.wasFocus) {
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
        enabled = celebration != null || pendingCategoryDelete != null || sheet != null || settingsOpen
    ) {
        when {
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

    if (!auth.signedIn && !authSkippedThisProcess.value) {
        AuthScreen(
            onSignedIn = {
                authSkippedThisProcess.value = true
                celebration = null
            },
            onSkip = { authSkippedThisProcess.value = true }
        )
        return
    }

    val page = if (settingsOpen) "settings" else tab
    val pageOrder = listOf("today", "tasks", "focus", "stats", "ranks")
    val title = if (settingsOpen) "Settings" else tabs.firstOrNull { it.id == tab }?.label ?: "Habits"
    val subtitle = when (page) {
        "settings" -> "Tune Panadoro to your rhythm"
        "today" -> LocalDate.now().format(
            DateTimeFormatter.ofPattern("EEEE, d MMMM", com.gritnative.app.data.AppLocale.current())
        )
        "tasks" -> "${store.openTaskCount()} open · ${store.dueTodayTaskCount()} due today"
        "focus" -> {
            val tag = store.activeTag
            if (tag == null) "Pick a tag to begin" else "${tag.name} · ${tag.focusMinutes}m focus · ${tag.cycles} cycles"
        }
        "stats" -> "One rhythm for every tag"
        "ranks" -> if (auth.signedIn) {
            "Only the tags people chose to publish"
        } else {
            "Sign in to join the shared board"
        }
        else -> ""
    }

    val timer = store.timer
    val now = rememberTimerNow(timer)
    val secondsLeft = computeSecondsLeft(timer, now)
    val running = timer?.running == true

    // One pager holds the five tabs: swiping sideways moves between them, and
    // a navigation-bar tap glides the same pages.
    val pagerState = rememberPagerState(
        initialPage = pageOrder.indexOf(tab).coerceAtLeast(0),
        pageCount = { pageOrder.size }
    )

    // A swipe that settles on a page makes it the current tab.
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.collect { page ->
            val id = pageOrder.getOrNull(page) ?: return@collect
            if (id != tab) {
                settingsOpen = false
                tab = id
            }
        }
    }

    // A tab tap (or a deep link) glides the pager to that page.
    LaunchedEffect(tab, settingsOpen) {
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
                subtitle = subtitle,
                settingsOpen = settingsOpen,
                timer = timer,
                secondsLeft = secondsLeft,
                onBack = { settingsOpen = false },
                onTimerClick = {
                    settingsOpen = false
                    tab = "focus"
                },
                onSettings = { settingsOpen = true }
            )

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
                            CompositionLocalProvider(
                                LocalEntranceWindow provides remember { EntranceWindow() }
                            ) {
                                when (pageOrder[page]) {
                                    "tasks" -> TasksScreen(
                                        store = store,
                                        onNewTask = { sheet = AppSheet.Task("") },
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
                                        onSignIn = { authSkippedThisProcess.value = false }
                                    )
                                    else -> TodayScreen(
                                        store = store,
                                        showCompleted = settings.showCompleted,
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

        GritBottomNav(
            current = tab,
            store = store,
            running = running,
            onSelect = {
                settingsOpen = false
                tab = it
            },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 10.dp)
        )

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
    subtitle: String,
    settingsOpen: Boolean,
    timer: TimerState?,
    secondsLeft: Int,
    onBack: () -> Unit,
    onTimerClick: () -> Unit,
    onSettings: () -> Unit
) {
    // The bar keeps a stable height no matter whether the timer chip is
    // showing: the chip is capped well below the bar's own height, so pages
    // never get nudged up and down when a session starts or ends.
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .defaultMinSize(minHeight = 56.dp)
            .padding(start = if (settingsOpen) 8.dp else 18.dp, end = 10.dp, top = 7.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        if (settingsOpen) {
            RoundIconButton(
                symbol = "arrow_back",
                onClick = onBack,
                size = 38.dp,
                iconSize = 19.dp,
                container = Grit.colors.field
            )
            Spacer(Modifier.size(1.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
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
                    fontSize = 23.sp,
                    lineHeight = 27.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            AnimatedContent(
                targetState = subtitle,
                transitionSpec = {
                    (fadeIn(tween(280)) + slideInVertically(tween(300, easing = FastOutSlowInEasing)) { it / 4 })
                        .togetherWith(fadeOut(tween(140)))
                },
                label = "top-subtitle"
            ) { value ->
                if (value.isNotBlank()) {
                    Text(
                        text = value,
                        color = Grit.colors.subtext,
                        fontSize = 10.5.sp,
                        lineHeight = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

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
                    modifier = Modifier.padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Symbol(
                        if (focus) "psychiatry" else "coffee",
                        size = 13.dp,
                        filled = true
                    )
                    AnimatedContent(
                        targetState = formatClock(secondsLeft),
                        transitionSpec = {
                            (
                                fadeIn(tween(150)) +
                                    scaleIn(initialScale = 0.8f, animationSpec = tween(200))
                                ).togetherWith(
                                fadeOut(tween(80)) +
                                    scaleOut(targetScale = 0.8f, animationSpec = tween(120))
                            )
                        },
                        label = "top-clock"
                    ) { clock ->
                        Text(
                            text = clock,
                            fontSize = 11.sp,
                            lineHeight = 14.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                    }
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

@Composable
private fun GritBottomNav(
    current: String,
    store: Store,
    running: Boolean,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    // The Tomato-style floating bar: one tinted pill, every tab an icon, and
    // the tab you are on becomes a solid button that pops its name open. The
    // reflow of the widening button is what makes the highlight feel like it
    // travels between tabs.
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        shadowElevation = 12.dp
    ) {
        Row(
            modifier = Modifier.padding(7.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            tabs.forEach { item ->
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
                val iconScale by animateFloatAsState(
                    targetValue = if (selected) 1.1f else 0.94f,
                    animationSpec = spring(dampingRatio = 0.55f, stiffness = 460f),
                    label = "nav-icon-scale"
                )
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
                    modifier = Modifier.height(48.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(7.dp)
                    ) {
                        Box(
                            Modifier
                                .size(22.dp)
                                .scale(iconScale),
                            contentAlignment = Alignment.Center
                        ) {
                            Symbol(item.symbol, size = 20.dp, filled = selected)
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
                        AnimatedVisibility(
                            visible = selected,
                            enter = expandHorizontally(
                                spring(dampingRatio = 0.78f, stiffness = 380f)
                            ) + fadeIn(tween(160)),
                            exit = shrinkHorizontally(
                                spring(dampingRatio = 0.82f, stiffness = 460f)
                            ) + fadeOut(tween(110))
                        ) {
                            Text(
                                text = item.label,
                                fontSize = 12.5.sp,
                                lineHeight = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Clip
                            )
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
