package com.gritnative.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gritnative.app.data.DayTagFocus
import com.gritnative.app.data.HeatCell
import com.gritnative.app.data.dateKey
import com.gritnative.app.ui.theme.Grit
import com.gritnative.app.ui.theme.lerpColor
import com.gritnative.app.ui.theme.tagColors
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

data class DonutSlice(
    val name: String,
    val value: Long,
    val color: Color
)

/**
 * A stacked bar chart of focus time per day. Each segment keeps its tag's colour,
 * so one chart answers both "when" and "on what" — the same contract as Grit's
 * desktop chart.
 */
@Composable
fun TagBarChart(
    series: List<DayTagFocus>,
    modifier: Modifier = Modifier,
    chartHeight: Dp = 150.dp,
    showLabels: Boolean = true,
    labelEvery: Int = 1,
    fallbackColor: Color = MaterialTheme.colorScheme.primary,
    goalSeconds: Long = 0L
) {
    val segmentColors = series.map { day ->
        day.segments.map { segment ->
            if (segment.tag != null) tagColors(segment.tag).accent else fallbackColor
        }
    }
    val trackColor = Grit.colors.field
    val labelColor = Grit.colors.subtext
    val enter = remember { Animatable(0f) }
    LaunchedEffect(series.map { it.total }, goalSeconds) {
        enter.snapTo(0f)
        enter.animateTo(1f, tween(720, easing = FastOutSlowInEasing))
    }
    Column(modifier = modifier) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(chartHeight)
        ) {
            if (series.isEmpty()) return@Canvas
            val count = series.size
            val slot = size.width / count
            // Wider slots get a little more air between the capsule bars.
            val gap = (slot * 0.22f).coerceIn(2.5.dp.toPx(), 6.dp.toPx())
            val barWidth = (slot - gap).coerceAtLeast(1f)
            // Scale to the busiest real day, not the goal, so the chart always
            // uses its full height instead of sitting half empty under a tall
            // two-hour goal line.
            val maxValue = (series.maxOfOrNull { it.total.toFloat() } ?: 0f)
                .coerceAtLeast(1f)
            val usableHeight = size.height
            for (index in series.indices) {
                val day = series[index]
                val x = index * slot + gap / 2f
                // The idle track is a full capsule too, so empty days read as
                // part of the design instead of as a flat strip.
                drawRoundRect(
                    color = trackColor.copy(alpha = 0.30f),
                    topLeft = Offset(x, 0f),
                    size = Size(barWidth, usableHeight),
                    cornerRadius = CornerRadius(barWidth / 2f)
                )
                if (day.total <= 0L) continue
                val stagger = (enter.value * 1.55f - index.toFloat() / count * 0.55f)
                    .coerceIn(0f, 1f)
                if (stagger <= 0f) continue
                val barHeight = usableHeight * (day.total.toFloat() / maxValue) * stagger
                // Each tag's slice is its own capsule, with a sliver of air
                // between the slices so the stack reads as a tidy column of
                // coloured pills.
                val visibleCount = day.segments.count { it.seconds > 0L }
                val segmentGap = 2.dp.toPx()
                val gapsTotal = (visibleCount - 1).coerceAtLeast(0) * segmentGap
                val colorHeight = (barHeight - gapsTotal).coerceAtLeast(0f)
                var bottom = usableHeight
                var drawn = 0
                day.segments.forEachIndexed { segmentIndex, segment ->
                    if (segment.seconds <= 0L) return@forEachIndexed
                    if (drawn > 0) bottom -= segmentGap
                    val height = (colorHeight * segment.seconds.toFloat() /
                        day.total.coerceAtLeast(1L)).coerceAtLeast(0.5f)
                    val color = segmentColors[index].getOrElse(segmentIndex) { fallbackColor }
                    drawRoundRect(
                        color = color,
                        topLeft = Offset(x, bottom - height),
                        size = Size(barWidth, height),
                        cornerRadius = CornerRadius(min(barWidth / 2f, height / 2f))
                    )
                    bottom -= height
                    drawn++
                }
            }
            if (goalSeconds > 0L && goalSeconds.toFloat() <= maxValue) {
                val y = usableHeight * (1f - goalSeconds.toFloat() / maxValue)
                drawLine(
                    color = labelColor.copy(alpha = 0.6f * enter.value),
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = 1.2.dp.toPx(),
                    cap = StrokeCap.Round,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(7f, 6f))
                )
            }
        }
        if (showLabels) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp)
            ) {
                series.forEachIndexed { index, day ->
                    Box(
                        modifier = Modifier.weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        if (index % labelEvery.coerceAtLeast(1) == 0) {
                            Text(
                                text = day.label,
                                color = labelColor,
                                fontSize = 9.sp,
                                lineHeight = 11.sp,
                                maxLines = 1,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * The rhythm chart with gestures: one finger pans across the days, a pinch
 * spreads or squeezes them — zooming in fits fewer, larger days, zooming out
 * fits more. Everything is drawn in a single canvas so zooming never
 * re-lays-out a row of boxes and cannot flicker; off-screen days are skipped
 * entirely so even years of history pan smoothly.
 */
@Composable
fun ZoomableRhythmChart(
    series: List<DayTagFocus>,
    modifier: Modifier = Modifier,
    chartHeight: Dp = 158.dp,
    fallbackColor: Color = MaterialTheme.colorScheme.primary,
    goalSeconds: Long = 0L,
    visibleDays: Int = 0
) {
    if (series.isEmpty()) return
    val density = LocalDensity.current
    val trackColor = Grit.colors.field
    val labelColor = Grit.colors.subtext
    val segmentColors = series.map { day ->
        day.segments.map { segment ->
            if (segment.tag != null) tagColors(segment.tag).accent else fallbackColor
        }
    }
    val minDayPx = with(density) { 7.dp.toPx() }
    val maxDayPx = with(density) { 64.dp.toPx() }

    // Pan and zoom live in plain state, and the canvas is the only thing that
    // redraws — no layout pass runs on a gesture, so nothing can jump.
    var dayWidthPx by remember(series.size) { mutableFloatStateOf(0f) }
    var scrollPx by remember(series.size) { mutableFloatStateOf(0f) }
    var measuredViewport by remember(series.size) { mutableFloatStateOf(0f) }

    // The default view fits a sensible window; scroll starts at the newest
    // day, so "today" is what you see without touching anything.
    LaunchedEffect(series.size, measuredViewport > 0f) {
        if (measuredViewport <= 0f) return@LaunchedEffect
        if (dayWidthPx <= 0f) {
            // Never reserve room for days that do not exist: a seven-day
            // series in a fourteen-day window used to fill only half the box.
            val window = if (visibleDays > 0) minOf(visibleDays, series.size)
            else minOf(series.size, 30)
            // Fit the real days to the full width: no maximum cap here, or a
            // short range would sit in the corner with empty space beside it.
            dayWidthPx = (measuredViewport / window.coerceAtLeast(1))
                .coerceAtLeast(minDayPx)
        }
        val content = series.size * dayWidthPx
        scrollPx = (content - measuredViewport).coerceAtLeast(0f)
    }

    val labelPaint = remember(labelColor) {
        android.graphics.Paint().apply {
            isAntiAlias = true
            color = labelColor.toArgb()
            textSize = with(density) { 9.dp.toPx() }
            textAlign = android.graphics.Paint.Align.CENTER
        }
    }

    Column(modifier = modifier) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(chartHeight + 22.dp)
                .onSizeChanged { measuredViewport = it.width.toFloat() }
                .pointerInput(series.size, minDayPx, maxDayPx) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        if (dayWidthPx <= 0f) return@detectTransformGestures
                        dayWidthPx = (dayWidthPx * zoom).coerceIn(minDayPx, maxDayPx)
                        val content = series.size * dayWidthPx
                        val maxScroll = (content - measuredViewport).coerceAtLeast(0f)
                        scrollPx = (scrollPx - pan.x).coerceIn(0f, maxScroll)
                    }
                }
        ) {
            if (series.isEmpty() || dayWidthPx <= 0f) return@Canvas
            val chartBottom = size.height - 22.dp.toPx()
            val slot = dayWidthPx
            val gap = (slot * 0.22f).coerceIn(1.5.dp.toPx(), 6.dp.toPx())
            val barWidth = (slot - gap).coerceAtLeast(1f)
            // Scale to the busiest real day, not the goal, so the chart always
            // uses its full height instead of sitting half empty under a tall
            // two-hour goal line.
            val maxValue = (series.maxOfOrNull { it.total.toFloat() } ?: 0f)
                .coerceAtLeast(1f)
            // Only days whose slot intersects the viewport are drawn.
            val firstVisible = floor(scrollPx / slot).toInt().coerceAtLeast(0)
            val lastVisible = ceil((scrollPx + size.width) / slot).toInt()
                .coerceAtMost(series.lastIndex)
            for (index in firstVisible..lastVisible) {
                val day = series[index]
                val x = index * slot - scrollPx + gap / 2f
                drawRoundRect(
                    color = trackColor.copy(alpha = 0.30f),
                    topLeft = Offset(x, 0f),
                    size = Size(barWidth, chartBottom),
                    cornerRadius = CornerRadius(barWidth / 2f)
                )
                if (day.total <= 0L) continue
                val barHeight = chartBottom * (day.total.toFloat() / maxValue)
                val visibleCount = day.segments.count { it.seconds > 0L }
                val segmentGap = 2.dp.toPx()
                val gapsTotal = (visibleCount - 1).coerceAtLeast(0) * segmentGap
                val colorHeight = (barHeight - gapsTotal).coerceAtLeast(0f)
                var bottom = chartBottom
                var drawn = 0
                day.segments.forEachIndexed { segmentIndex, segment ->
                    if (segment.seconds <= 0L) return@forEachIndexed
                    if (drawn > 0) bottom -= segmentGap
                    val height = (colorHeight * segment.seconds.toFloat() /
                        day.total.coerceAtLeast(1L)).coerceAtLeast(0.5f)
                    val color = segmentColors[index].getOrElse(segmentIndex) { fallbackColor }
                    drawRoundRect(
                        color = color,
                        topLeft = Offset(x, bottom - height),
                        size = Size(barWidth, height),
                        cornerRadius = CornerRadius(min(barWidth / 2f, height / 2f))
                    )
                    bottom -= height
                    drawn++
                }
            }
            if (goalSeconds > 0L && goalSeconds.toFloat() <= maxValue) {
                val y = chartBottom * (1f - goalSeconds.toFloat() / maxValue)
                drawLine(
                    color = labelColor.copy(alpha = 0.6f),
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = 1.2.dp.toPx(),
                    cap = StrokeCap.Round,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(7f, 6f))
                )
            }
            // Day labels ride the same pan and zoom as the bars, drawn straight
            // onto the canvas so they can never lag a frame behind.
            val labelEvery = max(1, ceil(34f / slot).toInt())
            for (index in firstVisible..lastVisible) {
                if (index % labelEvery != 0) continue
                val x = index * slot - scrollPx + slot / 2f
                if (x < 0f || x > size.width) continue
                drawContext.canvas.nativeCanvas.drawText(
                    series[index].label,
                    x,
                    size.height - 4.dp.toPx(),
                    labelPaint
                )
            }
        }
    }
}

/**
 * A quiet donut of focus time. The middle carries the total for the range and the
 * legend is laid out by the caller.
 */
@Composable
fun FocusDonut(
    slices: List<DonutSlice>,
    centerValue: String,
    centerLabel: String,
    modifier: Modifier = Modifier,
    thickness: Dp = 22.dp
) {
    val enter = remember { Animatable(0f) }
    LaunchedEffect(slices.map { it.value }) {
        enter.snapTo(0f)
        enter.animateTo(1f, tween(760, easing = FastOutSlowInEasing))
    }
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        val track = Grit.colors.field
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidth = thickness.toPx()
            val diameter = (min(size.width, size.height) - strokeWidth).coerceAtLeast(1f)
            val topLeft = Offset(
                x = (size.width - diameter) / 2f,
                y = (size.height - diameter) / 2f
            )
            drawArc(
                color = track,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = Size(diameter, diameter),
                style = Stroke(width = strokeWidth)
            )
            val total = slices.sumOf { it.value.coerceAtLeast(0L) }
            if (total <= 0L) return@Canvas
            var start = -90f
            slices.forEach { slice ->
                val value = slice.value.coerceAtLeast(0L)
                if (value <= 0L) return@forEach
                val sweep = value.toFloat() / total.toFloat() * 360f * enter.value
                drawArc(
                    color = slice.color,
                    startAngle = start,
                    sweepAngle = sweep,
                    useCenter = false,
                    topLeft = topLeft,
                    size = Size(diameter, diameter),
                    // Round caps give the ring soft ends — including the
                    // leading edge while the entrance animation sweeps it in.
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )
                start += sweep
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = centerValue,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 26.sp,
                lineHeight = 30.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
            if (centerLabel.isNotBlank()) {
                Text(
                    text = centerLabel,
                    color = Grit.colors.subtext,
                    fontSize = 11.sp,
                    lineHeight = 14.sp,
                    maxLines = 1
                )
            }
        }
    }
}

/**
 * Twelve weeks of weeks-aligned focus cells. Columns are weeks, rows are days of
 * the week, and the intensity is deliberately restrained.
 */
@Composable
fun FocusHeatmap(
    values: List<HeatCell>,
    modifier: Modifier = Modifier,
    accent: Color = MaterialTheme.colorScheme.primary,
    cellSize: Dp? = null,
    cellGap: Dp = 3.dp,
    showLabels: Boolean = true,
    mondayStart: Boolean = true
) {
    if (values.isEmpty()) return
    val weeks = values.chunked(7)
    val track = Grit.colors.field
    val maxValue = values.maxOfOrNull { it.value }?.coerceAtLeast(1L) ?: 1L
    val today = dateKey()
    val firstDay = if (mondayStart) 1 else 0
    val labels = (0 until 7).map { index ->
        val day = java.time.DayOfWeek.of(((firstDay + index) % 7).let { if (it == 0) 7 else it })
        day.getDisplayName(java.time.format.TextStyle.NARROW, Locale.getDefault())
    }
    val enter = remember { Animatable(0f) }
    LaunchedEffect(values.map { it.date to it.value }) {
        enter.snapTo(0f)
        enter.animateTo(1f, tween(620, easing = FastOutSlowInEasing))
    }

    BoxWithConstraints(modifier = modifier) {
        val labelWidth = if (showLabels) 15.dp else 0.dp
        val computedCell = cellSize ?: run {
            val horizontalGaps = cellGap.value * (weeks.size - 1).coerceAtLeast(0) +
                if (showLabels) cellGap.value else 0f
            val available = (maxWidth - labelWidth).value - horizontalGaps
            (available / weeks.size.coerceAtLeast(1)).coerceIn(9f, 24f).dp
        }
        Row(horizontalArrangement = Arrangement.spacedBy(cellGap)) {
            if (showLabels) {
                Column(verticalArrangement = Arrangement.spacedBy(cellGap)) {
                    labels.forEach { label ->
                        Box(
                            modifier = Modifier.size(computedCell),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                color = Grit.colors.subtext,
                                fontSize = (computedCell.value * 0.48f).sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
            weeks.forEachIndexed { weekIndex, week ->
                Column(verticalArrangement = Arrangement.spacedBy(cellGap)) {
                    week.forEachIndexed { dayIndex, cell ->
                        val reveal = (
                            enter.value * 1.55f -
                                (weekIndex * 7 + dayIndex).toFloat() / values.size * 0.55f
                            ).coerceIn(0f, 1f)
                        val intensity = if (cell.value <= 0L) 0f
                        else (0.28f + 0.72f * sqrt(cell.value.toFloat() / maxValue.toFloat()))
                            .coerceIn(0f, 1f)
                        val color = if (cell.value <= 0L) {
                            track.copy(alpha = if (cell.future) 0.25f else 0.62f)
                        } else {
                            lerpColor(track, accent, intensity)
                        }
                        val shape = RoundedCornerShape((computedCell.value * 0.22f).dp)
                        Box(
                            modifier = Modifier
                                .size(computedCell)
                                .graphicsLayer {
                                    alpha = (if (cell.future) 0.35f else 1f) * (0.25f + 0.75f * reveal)
                                    scaleX = 0.72f + 0.28f * reveal
                                    scaleY = 0.72f + 0.28f * reveal
                                }
                                .clip(shape)
                                .background(color)
                                .then(
                                    if (cell.date == today) Modifier.border(1.dp, accent.copy(alpha = 0.85f), shape)
                                    else Modifier
                                )
                        )
                    }
                }
            }
        }
    }
}

/** Legend dots used by the stats cards. */
@Composable
fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(color)
        )
        Text(
            text = label,
            color = Grit.colors.subtext,
            fontSize = 10.5.sp,
            lineHeight = 13.sp,
            maxLines = 1
        )
    }
}
