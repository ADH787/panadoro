package com.gritnative.app.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gritnative.app.data.DayTagFocus
import com.gritnative.app.data.HabitStat
import com.gritnative.app.data.Store
import com.gritnative.app.data.TagSegment
import com.gritnative.app.data.TagStat
import com.gritnative.app.data.activeTags
import com.gritnative.app.data.bestFocusDay
import com.gritnative.app.data.breakRatio
import com.gritnative.app.data.dailyFocusSeries
import com.gritnative.app.data.dailyTagFocusSeries
import com.gritnative.app.data.focusHeatmapWeeks
import com.gritnative.app.data.focusMinutesInRange
import com.gritnative.app.data.focusStreak
import com.gritnative.app.data.formatMinutesShort
import com.gritnative.app.data.habitStats
import com.gritnative.app.data.relativeDay
import com.gritnative.app.data.sessionsInRange
import com.gritnative.app.data.tagFocusSeries
import com.gritnative.app.data.tagGoalProgress
import com.gritnative.app.data.tagPercent
import com.gritnative.app.data.tagStats
import com.gritnative.app.data.tagStreak
import com.gritnative.app.data.tagTodayMinutes
import com.gritnative.app.data.timeOfDayStats
import com.gritnative.app.ui.components.CardHeader
import com.gritnative.app.ui.components.DonutSlice
import com.gritnative.app.ui.components.Flame
import com.gritnative.app.ui.components.FocusDonut
import com.gritnative.app.ui.components.FocusHeatmap
import com.gritnative.app.ui.components.FormChip
import com.gritnative.app.ui.components.GritPanel
import com.gritnative.app.ui.components.HeroCard
import com.gritnative.app.ui.components.MiniStatTile
import com.gritnative.app.ui.components.ProgressLine
import com.gritnative.app.ui.components.SegmentOption
import com.gritnative.app.ui.components.SegmentedSelector
import com.gritnative.app.ui.components.ShapeBadge
import com.gritnative.app.ui.components.StaggeredEntry
import com.gritnative.app.ui.components.TagBarChart
import com.gritnative.app.ui.theme.Grit
import com.gritnative.app.ui.theme.Symbol
import com.gritnative.app.ui.theme.lerpColor
import com.gritnative.app.ui.theme.tagColors
import kotlin.math.roundToInt

/**
 * Stats mirrors the desktop report: a rhythm chart, the split between tags, the
 * hours you actually sit down, twelve weeks of heat, then a page per tag. The
 * range selector is shared by every card so the numbers always agree.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StatsScreen(
    store: Store,
    weekStartMonday: Boolean
) {
    var rangeDays by rememberSaveable { mutableStateOf(7) }
    var chartMode by rememberSaveable { mutableStateOf("tags") }
    var rhythmTagId by rememberSaveable { mutableStateOf("") }

    val activeTags = store.activeTags
    val focusMinutes = store.focusMinutesInRange(rangeDays)
    val sessionCount = store.sessionsInRange(rangeDays, "focus").size
    val averageMinutes = (focusMinutes.toFloat() / rangeDays).roundToInt()
    val bestDay = store.bestFocusDay(rangeDays)
    val (_, breakSeconds) = store.breakRatio(rangeDays)
    val breakMinutes = (breakSeconds / 60.0).roundToInt()
    val dayStreak = store.focusStreak()
    val tags: List<TagStat> = store.tagStats(rangeDays)
    val habits: List<HabitStat> = store.habitStats(rangeDays)
    val dayParts = store.timeOfDayStats(rangeDays)
    val focusSeries = store.dailyFocusSeries(rangeDays)
    val tagSeries = store.dailyTagFocusSeries(rangeDays)
    val breakSeries = focusSeries.map { day ->
        DayTagFocus(
            date = day.date,
            label = day.label,
            total = day.breaks,
            segments = if (day.breaks > 0) {
                listOf(TagSegment("__breaks", null, day.breaks))
            } else {
                emptyList()
            }
        )
    }
    val tagChartDays = minOf(rangeDays, 30)
    val tagChartLabelEvery = when {
        tagChartDays <= 7 -> 1
        tagChartDays <= 14 -> 2
        else -> 5
    }
    val focusSlices = tags
        .filter { it.seconds > 0 }
        .map { stat ->
            DonutSlice(
                name = stat.name,
                value = stat.seconds,
                color = tagColors(stat.tag).accent
            )
        }
    val dayPartMax = (dayParts.maxOfOrNull { it.seconds } ?: 0L).coerceAtLeast(1L)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 112.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(key = "hero") {
            StaggeredEntry(0) {
                HeroCard(
                    symbol = "monitoring",
                    title = if (focusMinutes > 0) "You gave the time a shape."
                    else "The first session is a blank page.",
                    text = "$focusMinutes focused across $rangeDays ${if (rangeDays == 1) "day" else "days"}",
                    container = MaterialTheme.colorScheme.secondaryContainer,
                    onContainer = MaterialTheme.colorScheme.onSecondaryContainer,
                    accent = MaterialTheme.colorScheme.primary,
                    shapeName = "Cookie12Sided"
                )
            }
        }

        item(key = "range") {
            StaggeredEntry(1) {
                SegmentedSelector(
                    options = listOf(
                        SegmentOption(7, "7 days", "date_range"),
                        SegmentOption(30, "30 days", "event"),
                        SegmentOption(90, "90 days", "history")
                    ),
                    selected = rangeDays,
                    onSelect = { rangeDays = it }
                )
            }
        }

        item(key = "stat-row-1") {
            StaggeredEntry(2) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MiniStatTile(
                    symbol = "psychiatry",
                    value = formatMinutesShort(focusMinutes),
                    label = "Focus time",
                    hint = "${formatMinutesShort(averageMinutes)} per day",
                    shapeName = "Flower",
                    modifier = Modifier.weight(1f)
                )
                MiniStatTile(
                    symbol = "timelapse",
                    value = sessionCount.toString(),
                    label = "Sessions",
                    hint = "${formatMinutesShort(breakMinutes)} break time",
                    shapeName = "Cookie9Sided",
                    container = MaterialTheme.colorScheme.tertiaryContainer,
                    onContainer = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier.weight(1f)
                )
            }
            }
        }

        item(key = "stat-row-2") {
            StaggeredEntry(3) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MiniStatTile(
                    symbol = "emoji_events",
                    value = if (bestDay.focusSeconds > 0) {
                        formatMinutesShort((bestDay.focusSeconds / 60.0).roundToInt())
                    } else {
                        "—"
                    },
                    label = "Best day",
                    hint = if (bestDay.date.isNotBlank()) relativeDay(bestDay.date) else "No sessions yet",
                    shapeName = "Cookie12Sided",
                    container = Grit.colors.successContainer,
                    onContainer = Grit.colors.onSuccessContainer,
                    modifier = Modifier.weight(1f)
                )
                MiniStatTile(
                    symbol = "local_fire_department",
                    value = dayStreak.toString(),
                    label = "Day streak",
                    hint = "Days in a row with focus",
                    shapeName = "SoftBurst",
                    container = MaterialTheme.colorScheme.secondaryContainer,
                    onContainer = MaterialTheme.colorScheme.onSecondaryContainer,
                    flame = true,
                    modifier = Modifier.weight(1f)
                )
            }
            }
        }

        item(key = "rhythm") {
            val rhythmTag = activeTags.firstOrNull { it.id == rhythmTagId }
                ?: tags.firstOrNull { it.tag != null }?.tag
                ?: activeTags.firstOrNull()
            val rhythmColors = tagColors(rhythmTag)
            val selectedSeries = if (chartMode == "breaks") {
                breakSeries
            } else if (rhythmTag != null) {
                store.tagFocusSeries(rhythmTag.id, tagChartDays)
            } else {
                tagSeries
            }
            val rhythmSessions = if (chartMode == "breaks") {
                store.sessionsInRange(tagChartDays, "break").size
            } else {
                store.sessionsInRange(tagChartDays, "focus")
                    .count { rhythmTag == null || it.tagId == rhythmTag.id }
            }
            val rhythmStreak = if (rhythmTag != null) store.tagStreak(rhythmTag.id) else 0
            val peak = selectedSeries.maxByOrNull { it.total }

            GritPanel {
                CardHeader(
                    title = "Focus rhythm",
                    subtitle = when {
                        chartMode == "breaks" -> "Breaks between focus sessions"
                        rhythmTag != null -> "${rhythmTag.name} · one rhythm, ${tagChartDays} days"
                        else -> "Create a focus tag to give each rhythm its own chart"
                    }
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FormChip(
                        label = "Focus",
                        selected = chartMode == "tags",
                        onClick = { chartMode = "tags" },
                        symbol = "psychiatry",
                        accent = rhythmColors.accent
                    )
                    FormChip(
                        label = "Breaks",
                        selected = chartMode == "breaks",
                        onClick = { chartMode = "breaks" },
                        symbol = "coffee",
                        accent = MaterialTheme.colorScheme.tertiary
                    )
                }
                if (chartMode == "tags" && activeTags.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        activeTags.forEach { tag ->
                            val colors = tagColors(tag)
                            FormChip(
                                label = tag.name,
                                selected = rhythmTag?.id == tag.id,
                                onClick = { rhythmTagId = tag.id },
                                symbol = tag.icon,
                                accent = colors.accent
                            )
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                AnimatedContent(
                    targetState = rhythmTag?.id to chartMode,
                    transitionSpec = {
                        (fadeIn(tween(260)) + slideInHorizontally(tween(300)) { it / 6 })
                            .togetherWith(fadeOut(tween(140)) + slideOutHorizontally(tween(180)) { -it / 8 })
                    },
                    label = "rhythm-card"
                ) { (shownTagId, shownMode) ->
                    val shownTag = activeTags.firstOrNull { it.id == shownTagId }
                    val breaks = shownMode == "breaks"
                    val shownSeries = when {
                        breaks -> breakSeries
                        shownTag != null -> store.tagFocusSeries(shownTag.id, tagChartDays)
                        else -> tagSeries
                    }
                    val shownColors = tagColors(shownTag)
                    val shownSeconds = shownSeries.sumOf { it.total }
                    val shownPeak = shownSeries.maxByOrNull { it.total }
                    val shownGoal = (shownTag?.dailyGoalMinutes ?: 0).coerceAtLeast(0)
                    val shownToday = if (shownTag != null) store.tagTodayMinutes(shownTag.id) else 0
                    val shownProgress = if (shownTag != null) {
                        store.tagGoalProgress(shownTag).coerceAtLeast(0f)
                    } else {
                        0f
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = formatMinutesShort((shownSeconds / 60.0).roundToInt()),
                                    color = shownColors.accent,
                                    fontSize = 24.sp,
                                    lineHeight = 28.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = if (breaks) {
                                        "break time in $tagChartDays days"
                                    } else {
                                        "focused in $tagChartDays days · " +
                                            if (shownPeak != null && shownPeak.total > 0) {
                                                "best ${shownPeak.label}"
                                            } else {
                                                "no sessions yet"
                                            }
                                    },
                                    color = Grit.colors.subtext,
                                    fontSize = 10.sp,
                                    lineHeight = 13.sp
                                )
                            }
                            if (shownTag != null && !breaks) {
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = "${(shownProgress * 100).roundToInt()}%",
                                        color = shownColors.accent,
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = if (shownGoal > 0) {
                                            "${formatMinutesShort(shownToday)} / ${formatMinutesShort(shownGoal)} today"
                                        } else {
                                            "no daily goal"
                                        },
                                        color = Grit.colors.subtext,
                                        fontSize = 9.sp
                                    )
                                }
                            }
                        }
                        if (shownTag != null && shownGoal > 0 && !breaks) {
                            ProgressLine(
                                progress = shownProgress,
                                color = shownColors.accent,
                                height = 6.dp,
                                minFraction = 0.02f
                            )
                        }
                        TagBarChart(
                            series = shownSeries,
                            chartHeight = 158.dp,
                            labelEvery = tagChartLabelEvery,
                            fallbackColor = if (breaks) MaterialTheme.colorScheme.tertiary
                            else shownColors.accent,
                            goalSeconds = if (breaks || shownGoal <= 0) 0L else shownGoal * 60L
                        )
                    }
                }
                Spacer(Modifier.height(9.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    MiniPill(
                        symbol = "timelapse",
                        text = "$rhythmSessions sessions",
                        color = rhythmColors.accent
                    )
                    MiniPill(
                        symbol = "local_fire_department",
                        text = "$rhythmStreak day streak",
                        color = rhythmColors.accent
                    )
                    if (peak != null && peak.total > 0) {
                        MiniPill(
                            symbol = "emoji_events",
                            text = "peak ${peak.label}",
                            color = rhythmColors.accent
                        )
                    }
                }
            }
        }

        item(key = "split") {
            GritPanel {
                CardHeader(
                    title = "Focus split",
                    subtitle = "Focused time divided between tags"
                )
                Spacer(Modifier.height(12.dp))
                if (focusSlices.isEmpty()) {
                    Text(
                        text = "No focus time in this range yet.",
                        color = Grit.colors.subtext,
                        fontSize = 11.5.sp,
                        lineHeight = 16.sp
                    )
                } else {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        FocusDonut(
                            slices = focusSlices,
                            centerValue = formatMinutesShort(focusMinutes),
                            centerLabel = "focused",
                            modifier = Modifier.size(148.dp),
                            thickness = 20.dp
                        )
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(7.dp)
                        ) {
                            tags.filter { it.seconds > 0 }.forEach { stat ->
                                val color = tagColors(stat.tag).accent
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Box(
                                        Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(color)
                                    )
                                    Text(
                                        text = stat.name,
                                        modifier = Modifier.weight(1f),
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontSize = 11.5.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${(stat.percent * 100).roundToInt()}%",
                                        color = color,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = formatMinutesShort((stat.seconds / 60.0).roundToInt()),
                                        color = Grit.colors.subtext,
                                        fontSize = 10.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        item(key = "time-of-day") {
            GritPanel {
                CardHeader(title = "When you focus")
                Spacer(Modifier.height(10.dp))
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    dayParts.forEach { part ->
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${part.label}  ${part.range}",
                                    modifier = Modifier.weight(1f),
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = formatMinutesShort((part.seconds / 60.0).roundToInt()),
                                    color = Grit.colors.subtext,
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            ProgressLine(
                                progress = part.seconds.toFloat() / dayPartMax.toFloat(),
                                color = MaterialTheme.colorScheme.tertiary,
                                height = 7.dp,
                                minFraction = 0.025f
                            )
                        }
                    }
                }
            }
        }

        item(key = "heat") {
            GritPanel {
                CardHeader(
                    title = "Twelve weeks of consistency",
                    subtitle = "Each column is a week · darker means more focus"
                )
                Spacer(Modifier.height(12.dp))
                FocusHeatmap(
                    values = store.focusHeatmapWeeks(12, monday = weekStartMonday),
                    modifier = Modifier.fillMaxWidth(),
                    accent = MaterialTheme.colorScheme.primary,
                    cellSize = null,
                    cellGap = 3.dp,
                    showLabels = true,
                    mondayStart = weekStartMonday
                )
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Quieter", color = Grit.colors.subtext, fontSize = 9.sp)
                    Spacer(Modifier.width(5.dp))
                    repeat(5) { index ->
                        Box(
                            Modifier
                                .padding(horizontal = 1.5.dp)
                                .size(9.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(
                                    if (index == 0) Grit.colors.field
                                    else lerpColor(
                                        Grit.colors.field,
                                        MaterialTheme.colorScheme.primary,
                                        0.2f + index * 0.16f
                                    )
                                )
                        )
                    }
                    Spacer(Modifier.width(5.dp))
                    Text("More focus", color = Grit.colors.subtext, fontSize = 9.sp)
                }
            }
        }

        item(key = "habits") {
            GritPanel {
                CardHeader(title = "Habit consistency")
                Spacer(Modifier.height(10.dp))
                if (habits.isEmpty()) {
                    Text(
                        text = "Add a habit to see its consistency here.",
                        color = Grit.colors.subtext,
                        fontSize = 11.5.sp,
                        lineHeight = 16.sp
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        habits.forEach { stat ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                ShapeBadge(
                                    symbol = stat.habit.icon,
                                    shapeName = "Cookie4Sided",
                                    container = MaterialTheme.colorScheme.primaryContainer,
                                    content = MaterialTheme.colorScheme.onPrimaryContainer,
                                    size = 30.dp,
                                    iconSize = 15.dp
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = stat.name,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = if (stat.due > 0) "${stat.done}/${stat.due} due" else "Not scheduled",
                                        color = Grit.colors.subtext,
                                        fontSize = 9.5.sp,
                                        maxLines = 1
                                    )
                                }
                                Box(Modifier.width(58.dp)) {
                                    ProgressLine(
                                        progress = stat.rate,
                                        color = if (stat.rate >= 1f) Grit.colors.success
                                        else MaterialTheme.colorScheme.primary,
                                        height = 7.dp,
                                        minFraction = 0.06f
                                    )
                                }
                                Flame(
                                    count = stat.streak,
                                    color = MaterialTheme.colorScheme.tertiary,
                                    iconSize = 12.dp
                                )
                            }
                        }
                    }
                }
            }
        }

        item(key = "by-tag-heading") {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Consistency by tag",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "A focus graph and twelve weeks of consistency for each tag",
                        color = Grit.colors.subtext,
                        fontSize = 10.5.sp,
                        lineHeight = 14.sp
                    )
                }
            }
        }

        items(activeTags.size, key = { activeTags[it].id }) { index ->
            val tag = activeTags[index]
            val stat = tags.firstOrNull { it.tagId == tag.id }
            val seconds = stat?.seconds ?: 0L
            val percent = store.tagPercent(tag.id, rangeDays)
            val streak = store.tagStreak(tag.id)
            val goalMinutes = tag.dailyGoalMinutes.coerceAtLeast(0)
            val todayMinutes = store.tagTodayMinutes(tag.id)
            val goalProgress = store.tagGoalProgress(tag).coerceAtLeast(0f)
            val colors = tagColors(tag)
            StaggeredEntry(
                index = index.coerceAtMost(8),
                modifier = Modifier.animateItem()
            ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        lerpColor(MaterialTheme.colorScheme.surfaceContainerLow, colors.accent, 0.055f),
                        RoundedCornerShape(24.dp)
                    )
                    .border(1.dp, colors.accent.copy(alpha = 0.16f), RoundedCornerShape(24.dp))
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ShapeBadge(
                        symbol = tag.icon,
                        shapeName = tag.shape,
                        container = colors.accent,
                        content = colors.onSolid,
                        size = 38.dp,
                        iconSize = 18.dp
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = tag.name,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${(percent * 100).roundToInt()}% of focus · ${formatMinutesShort((seconds / 60.0).roundToInt())}",
                            color = Grit.colors.subtext,
                            fontSize = 10.sp,
                            maxLines = 1
                        )
                    }
                    Flame(count = streak, color = colors.accent, iconSize = 12.dp, showZero = true)
                    Text(
                        text = if (streak == 1) "day" else "days",
                        color = Grit.colors.subtext,
                        fontSize = 10.sp
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        text = "Rhythm · $tagChartDays days",
                        color = Grit.colors.subtext,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    TagBarChart(
                        series = store.tagFocusSeries(tag.id, tagChartDays),
                        chartHeight = 94.dp,
                        labelEvery = tagChartLabelEvery,
                        fallbackColor = colors.accent,
                        goalSeconds = if (goalMinutes > 0) goalMinutes * 60L else 0L
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "Last 12 weeks",
                        color = Grit.colors.subtext,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    FocusHeatmap(
                        values = store.focusHeatmapWeeks(12, tagId = tag.id, monday = weekStartMonday),
                        modifier = Modifier.fillMaxWidth(),
                        accent = colors.accent,
                        cellSize = null,
                        cellGap = 2.dp,
                        showLabels = true,
                        mondayStart = weekStartMonday
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = if (goalMinutes > 0) {
                            "${formatMinutesShort(todayMinutes)} / ${formatMinutesShort(goalMinutes)} today"
                        } else {
                            "No daily goal"
                        },
                        color = Grit.colors.subtext,
                        fontSize = 10.sp,
                        maxLines = 1
                    )
                    if (goalMinutes > 0) {
                        Box(Modifier.weight(1f)) {
                            ProgressLine(
                                progress = goalProgress,
                                color = colors.accent,
                                height = 6.dp,
                                minFraction = 0.04f
                            )
                        }
                        Text(
                            text = "${(goalProgress * 100).roundToInt()}%",
                            color = colors.accent,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
            }
        }

        item(key = "bottom-space") {
            Spacer(Modifier.height(4.dp))
        }
    }
}

@Composable
private fun MiniPill(symbol: String, text: String, color: Color) {
    Row(
        modifier = Modifier
            .background(color.copy(alpha = 0.12f), CircleShape)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Symbol(symbol, size = 11.dp, color = color, filled = true)
        Text(
            text = text,
            color = color,
            fontSize = 9.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1
        )
    }
}
