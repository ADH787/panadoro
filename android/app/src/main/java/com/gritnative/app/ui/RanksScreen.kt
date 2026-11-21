package com.gritnative.app.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gritnative.app.data.AppSettings
import com.gritnative.app.data.AuthService
import com.gritnative.app.data.LeaderboardEntry
import com.gritnative.app.data.LeaderboardPeriod
import com.gritnative.app.data.LeaderboardService
import com.gritnative.app.data.LeaderboardTag
import com.gritnative.app.data.Store
import com.gritnative.app.data.StoreRepository
import com.gritnative.app.data.Tag
import com.gritnative.app.data.activeTags
import com.gritnative.app.data.formatMinutesShort
import com.gritnative.app.ui.components.CardHeader
import com.gritnative.app.ui.components.FormChip
import com.gritnative.app.ui.components.GritField
import com.gritnative.app.ui.components.GritPanel
import com.gritnative.app.ui.components.ProgressLine
import com.gritnative.app.ui.components.RoundIconButton
import com.gritnative.app.ui.components.SegmentOption
import com.gritnative.app.ui.components.SegmentedSelector
import com.gritnative.app.ui.components.ShapeBadge
import com.gritnative.app.ui.components.StaggeredEntry
import com.gritnative.app.ui.theme.Grit
import com.gritnative.app.ui.theme.gritShape
import com.gritnative.app.ui.theme.lerpColor
import com.gritnative.app.ui.theme.tagColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private val medalColors = listOf(
    Color(0xFFE7B84B),
    Color(0xFFB9C4CE),
    Color(0xFFC98B5B)
)

private fun leaderboardTag(tag: LeaderboardTag): Tag = Tag(
    id = tag.id,
    name = tag.name,
    accent = tag.accent,
    color = tag.color,
    shape = tag.shape,
    icon = tag.icon
)

private fun secondsLabel(seconds: Long): String =
    formatMinutesShort((seconds / 60.0).roundToInt())

private fun initials(name: String): String {
    val words = name.trim().split(' ').filter { it.isNotBlank() }
    if (words.isEmpty()) return "?"
    if (words.size == 1) return words.first().take(2).uppercase()
    return (words.first().take(1) + words.last().take(1)).uppercase()
}

/**
 * The opt-in focus leaderboard. It ranks each account's combined focus seconds
 * across every tag that account chose to make public. Nothing about habits,
 * tasks or notes is ever sent here.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RanksScreen(
    store: Store,
    settings: AppSettings,
    signedIn: Boolean,
    onSignIn: () -> Unit
) {
    val state by LeaderboardService.state.collectAsState()
    val scope = rememberCoroutineScope()
    var periodName by rememberSaveable { mutableStateOf(LeaderboardPeriod.WEEK.name) }
    var displayName by remember(settings.displayName) {
        mutableStateOf(
            settings.displayName.ifBlank {
                LeaderboardService.displayName(settings, AuthService.email())
            }
        )
    }
    val period = LeaderboardPeriod.entries.firstOrNull { it.name == periodName }
        ?: LeaderboardPeriod.WEEK

    LaunchedEffect(signedIn) {
        if (!signedIn) return@LaunchedEffect
        LeaderboardService.publish()
        LeaderboardService.refresh()
        while (true) {
            delay(60_000)
            LeaderboardService.refresh()
        }
    }

    val ownTags = store.activeTags
    val publicTags = ownTags.filter { it.id in settings.publicTagIds }
    val ranked = state.entries
        .map { entry -> entry to entry.seconds(period) }
        .sortedWith(
            compareByDescending<Pair<LeaderboardEntry, Long>> { it.second }
                .thenBy { it.first.displayName.lowercase() }
        )
    val maxSeconds = ranked.maxOfOrNull { it.second }?.coerceAtLeast(1L) ?: 1L

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 112.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(key = "hero") {
            StaggeredEntry(0) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(28.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 15.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Box(
                                modifier = Modifier
                                    .size(52.dp)
                                    .background(
                                        MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f),
                                        gritShape("SoftBurst")
                                    )
                            )
                            ShapeBadge(
                                symbol = "emoji_events",
                                shapeName = "Cookie12Sided",
                                container = MaterialTheme.colorScheme.secondary,
                                content = MaterialTheme.colorScheme.onSecondary,
                                size = 40.dp,
                                iconSize = 21.dp,
                                filled = true
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Focus ranks",
                                fontSize = 20.sp,
                                lineHeight = 24.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (signedIn) {
                                    "Combined focus across every public tag. Updated whenever focus ends."
                                } else {
                                    "Sign in to add your focus to the community board."
                                },
                                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.72f),
                                fontSize = 10.5.sp,
                                lineHeight = 14.sp
                            )
                        }
                        if (signedIn) {
                            RoundIconButton(
                                symbol = "refresh",
                                onClick = {
                                    scope.launch {
                                        LeaderboardService.publish()
                                        LeaderboardService.refresh()
                                    }
                                },
                                size = 36.dp,
                                iconSize = 18.dp,
                                container = MaterialTheme.colorScheme.secondary.copy(alpha = 0.16f),
                                content = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                }
            }
        }

        if (!signedIn) {
            item(key = "signed-out") {
                StaggeredEntry(1) {
                    GritPanel {
                        CardHeader(
                            title = "The ranks are a shared room",
                            subtitle = "Sign in, choose a display name and flip the tags you want to share. " +
                                "Private tags stay on this device."
                        )
                        Spacer(Modifier.height(12.dp))
                        androidx.compose.material3.Button(
                            onClick = onSignIn,
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            shape = CircleShape
                        ) {
                            Text("Sign in or create an account", fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        item(key = "period") {
            StaggeredEntry(1) {
                SegmentedSelector(
                    options = listOf(
                        SegmentOption(LeaderboardPeriod.DAY, "Today", "today"),
                        SegmentOption(LeaderboardPeriod.WEEK, "Week", "date_range"),
                        SegmentOption(LeaderboardPeriod.MONTH, "Month", "event")
                    ),
                    selected = period,
                    onSelect = { periodName = it.name }
                )
            }
        }

        item(key = "tags") {
            StaggeredEntry(2) {
                Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    AnimatedVisibility(visible = state.loading) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(7.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(13.dp),
                                strokeWidth = 1.8.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "Listening to the other devices…",
                                color = Grit.colors.subtext,
                                fontSize = 10.sp
                            )
                        }
                    }
                    AnimatedVisibility(visible = state.error.isNotBlank()) {
                        Text(
                            text = state.error,
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 10.sp,
                            lineHeight = 13.sp
                        )
                    }
                }
            }
        }

        if (ranked.isNotEmpty()) {
            item(key = "podium") {
                StaggeredEntry(3) {
                    AnimatedContent(
                        targetState = Triple(period, "", ranked.take(3).map { it.first.uid }),
                        transitionSpec = {
                            (fadeIn(tween(280)) + slideInVertically(spring(dampingRatio = 0.8f, stiffness = 330f)) { it / 5 })
                                .togetherWith(fadeOut(tween(130)))
                        },
                        label = "podium"
                    ) { snapshot ->
                        val top = ranked.take(3)
                        if (top.isEmpty()) return@AnimatedContent
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.Bottom,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val order = when (top.size) {
                                1 -> listOf(0)
                                2 -> listOf(1, 0)
                                else -> listOf(1, 0, 2)
                            }
                            order.forEach { index ->
                                val (entry, seconds) = top[index]
                                PodiumCard(
                                    entry = entry,
                                    rank = index + 1,
                                    seconds = seconds,
                                    period = period,
                                    modifier = Modifier.weight(1f),
                                    isYou = entry.uid == AuthService.uid()
                                )
                            }
                        }
                    }
                }
            }

            item(key = "list-heading") {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Everyone",
                        modifier = Modifier.weight(1f),
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${ranked.size} ${if (ranked.size == 1) "account" else "accounts"}",
                        color = Grit.colors.subtext,
                        fontSize = 10.5.sp
                    )
                }
            }

            itemsIndexed(
                items = ranked,
                key = { _, pair -> pair.first.uid }
            ) { index, pair ->
                val (entry, seconds) = pair
                StaggeredEntry(
                    index = index.coerceAtMost(8),
                    modifier = Modifier.animateItem()
                ) {
                    RankRow(
                        rank = index + 1,
                        entry = entry,
                        seconds = seconds,
                        maxSeconds = maxSeconds,
                        period = period,
                        isYou = entry.uid == AuthService.uid()
                    )
                }
            }
        } else {
            item(key = "empty") {
                StaggeredEntry(3) {
                    GritPanel {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            ShapeBadge(
                                symbol = "emoji_events",
                                shapeName = "Cookie9Sided",
                                container = Grit.colors.field,
                                content = Grit.colors.subtext,
                                size = 54.dp,
                                iconSize = 26.dp
                            )
                            Text(
                                text = if (signedIn) "The podium is waiting." else "No shared focus yet.",
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = if (signedIn) {
                                    "Publish a tag below and the first focus session puts you on the board."
                                } else {
                                    "Sign in and make one tag public to start the board."
                                },
                                color = Grit.colors.subtext,
                                fontSize = 10.5.sp,
                                lineHeight = 14.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        }

        if (signedIn) {
            item(key = "your-name") {
                StaggeredEntry(4) {
                    GritPanel {
                        CardHeader(
                            title = "How you appear",
                            subtitle = "A display name for the ranks. Your email is never published."
                        )
                        Spacer(Modifier.height(9.dp))
                        Row(
                            verticalAlignment = Alignment.Bottom,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            GritField(
                                value = displayName,
                                onValueChange = { displayName = it.take(32) },
                                caption = "Display name",
                                placeholder = "Your name",
                                symbol = "person",
                                modifier = Modifier.weight(1f)
                            )
                            androidx.compose.material3.Button(
                                onClick = {
                                    StoreRepository.setDisplayName(displayName)
                                    scope.launch {
                                        LeaderboardService.publish()
                                        delay(600)
                                        LeaderboardService.refresh()
                                    }
                                },
                                modifier = Modifier.height(58.dp),
                                shape = CircleShape,
                                contentPadding = PaddingValues(horizontal = 16.dp)
                            ) {
                                Text("Save", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            item(key = "public-tags") {
                StaggeredEntry(5) {
                    GritPanel {
                        CardHeader(
                            title = "Public tags",
                            subtitle = "Flip a tag on to share its focus time for today, this week and this month."
                        )
                        Spacer(Modifier.height(6.dp))
                        if (ownTags.isEmpty()) {
                            Text(
                                text = "Create a focus tag first.",
                                color = Grit.colors.subtext,
                                fontSize = 11.sp
                            )
                        } else {
                            ownTags.forEachIndexed { index, tag ->
                                if (index > 0) {
                                    HorizontalDivider(
                                        modifier = Modifier.padding(vertical = 4.dp),
                                        color = Grit.colors.outline.copy(alpha = 0.18f)
                                    )
                                }
                                val colors = tagColors(tag)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(9.dp)
                                ) {
                                    ShapeBadge(
                                        symbol = tag.icon,
                                        shapeName = tag.shape,
                                        container = colors.container,
                                        content = colors.onContainer,
                                        size = 34.dp,
                                        iconSize = 16.dp
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = tag.name,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Medium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = if (tag.id in settings.publicTagIds) {
                                                "Visible on the ranks"
                                            } else {
                                                "Private · stays here"
                                            },
                                            color = if (tag.id in settings.publicTagIds) colors.accent
                                            else Grit.colors.subtext,
                                            fontSize = 9.5.sp
                                        )
                                    }
                                    Switch(
                                        checked = tag.id in settings.publicTagIds,
                                        onCheckedChange = { checked ->
                                            StoreRepository.setTagPublic(tag.id, checked)
                                            scope.launch {
                                                LeaderboardService.publish()
                                                delay(600)
                                                LeaderboardService.refresh()
                                            }
                                        },
                                        colors = SwitchDefaults.colors(
                                            checkedTrackColor = colors.accent,
                                            checkedThumbColor = colors.onSolid
                                        )
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "${publicTags.size} of ${ownTags.size} tags public · " +
                                "habits, tasks and notes are never uploaded to the board.",
                            color = Grit.colors.subtext.copy(alpha = 0.85f),
                            fontSize = 9.5.sp,
                            lineHeight = 13.sp
                        )
                    }
                }
            }
        }

        item(key = "footer") {
            Text(
                text = when {
                    !signedIn -> "Panadoro ranks are opt-in."
                    state.loadedAt > 0 -> "Updated ${relativeRefresh(state.loadedAt)} · pull the refresh button to update"
                    else -> "Waiting for the first sync."
                },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                color = Grit.colors.subtext.copy(alpha = 0.75f),
                fontSize = 9.5.sp,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(4.dp))
        }
    }
}

@Composable
private fun PodiumCard(
    entry: LeaderboardEntry,
    rank: Int,
    seconds: Long,
    period: LeaderboardPeriod,
    modifier: Modifier = Modifier,
    isYou: Boolean
) {
    val medal = medalColors.getOrElse(rank - 1) { MaterialTheme.colorScheme.primary }
    val height = when (rank) {
        1 -> 192.dp
        2 -> 172.dp
        else -> 164.dp
    }
    val topTag = entry.tags.maxByOrNull { it.seconds(period) }
    val accent = topTag?.let { tagColors(leaderboardTag(it)).accent }
        ?: MaterialTheme.colorScheme.primary
    Surface(
        modifier = modifier.height(height),
        shape = RoundedCornerShape(22.dp),
        color = lerpColor(MaterialTheme.colorScheme.surfaceContainerLow, medal, 0.08f),
        border = BorderStroke(
            if (isYou) 1.5.dp else 1.dp,
            if (isYou) MaterialTheme.colorScheme.primary.copy(alpha = 0.7f) else medal.copy(alpha = 0.34f)
        ),
        shadowElevation = if (rank == 1) 8.dp else 0.dp
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Box(
                    modifier = Modifier
                        .size(if (rank == 1) 42.dp else 34.dp)
                        .background(medal.copy(alpha = 0.2f), CircleShape)
                )
                Text(
                    text = "$rank",
                    color = medal,
                    fontSize = if (rank == 1) 21.sp else 17.sp,
                    fontWeight = FontWeight.Black
                )
            }
            Box(
                modifier = Modifier
                    .size(if (rank == 1) 42.dp else 36.dp)
                    .background(accent.copy(alpha = 0.18f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = initials(entry.displayName),
                    color = accent,
                    fontSize = if (rank == 1) 15.sp else 12.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
            }
            Text(
                text = entry.displayName,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = if (rank == 1) 11.5.sp else 10.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                textAlign = TextAlign.Center,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = secondsLabel(seconds),
                color = accent,
                fontSize = if (rank == 1) 14.sp else 12.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
            Text(
                text = period.label.lowercase(),
                color = Grit.colors.subtext,
                fontSize = 8.5.sp,
                maxLines = 1
            )
            if (isYou) {
                Text(
                    text = "You",
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 8.5.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun RankRow(
    rank: Int,
    entry: LeaderboardEntry,
    seconds: Long,
    maxSeconds: Long,
    period: LeaderboardPeriod,
    isYou: Boolean
) {
    val topTag = entry.tags.maxByOrNull { it.seconds(period) }
    val accent = topTag?.let { tagColors(leaderboardTag(it)).accent }
        ?: MaterialTheme.colorScheme.primary
    val share = (seconds.toFloat() / maxSeconds.toFloat()).coerceIn(0f, 1f)
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = if (isYou) lerpColor(MaterialTheme.colorScheme.surfaceContainerLow, accent, 0.09f)
        else MaterialTheme.colorScheme.surfaceContainerLow,
        border = if (isYou) BorderStroke(1.dp, accent.copy(alpha = 0.45f)) else null
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            Text(
                text = "$rank",
                modifier = Modifier.width(20.dp),
                color = if (rank <= 3) medalColors[rank - 1] else Grit.colors.subtext,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .background(accent.copy(alpha = 0.16f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = initials(entry.displayName),
                    color = accent,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(
                        text = entry.displayName,
                        modifier = Modifier.weight(1f, fill = false),
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (isYou) {
                        Text(
                            text = "YOU",
                            modifier = Modifier
                                .background(accent.copy(alpha = 0.16f), CircleShape)
                                .padding(horizontal = 5.dp, vertical = 1.dp),
                            color = accent,
                            fontSize = 7.5.sp,
                            fontWeight = FontWeight.Black
                        )
                    }
                }
                ProgressLine(
                    progress = share,
                    color = accent,
                    height = 4.dp,
                    minFraction = 0.02f
                )
                Text(
                    text = "${entry.tags.size} public ${if (entry.tags.size == 1) "tag" else "tags"} · " +
                        "${period.label.lowercase()}",
                    color = Grit.colors.subtext,
                    fontSize = 9.sp,
                    maxLines = 1
                )
            }
            AnimatedContent(
                targetState = seconds,
                transitionSpec = {
                    (fadeIn(tween(240)) + slideInVertically(tween(240)) { it / 2 })
                        .togetherWith(fadeOut(tween(110)))
                },
                label = "rank-seconds"
            ) { value ->
                Text(
                    text = secondsLabel(value),
                    color = accent,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
            }
        }
    }
}

private fun relativeRefresh(epochMs: Long): String {
    val minutes = ((System.currentTimeMillis() - epochMs) / 60_000L).coerceAtLeast(0L)
    return when {
        minutes < 1 -> "just now"
        minutes < 60 -> "$minutes min ago"
        else -> "${minutes / 60}h ago"
    }
}
