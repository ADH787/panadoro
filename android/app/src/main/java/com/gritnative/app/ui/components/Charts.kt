package com.gritnative.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
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
            val gap = 3.dp.toPx()
            val slot = size.width / count
            val barWidth = (slot - gap).coerceAtLeast(1f)
            val corner = CornerRadius(min(barWidth / 2f, 3.dp.toPx()))
            val maxValue = maxOf(
                series.maxOfOrNull { it.total.toFloat() } ?: 0f,
                goalSeconds.toFloat()
            ).coerceAtLeast(1f)
            val usableHeight = size.height
            for (index in series.indices) {
                val day = series[index]
                val x = index * slot + gap / 2f
                drawRoundRect(
                    color = trackColor.copy(alpha = 0.30f),
                    topLeft = Offset(x, 0f),
                    size = Size(barWidth, usableHeight),
                    cornerRadius = corner
                )
                if (day.total <= 0L) continue
                val stagger = (enter.value * 1.55f - index.toFloat() / count * 0.55f)
                    .coerceIn(0f, 1f)
                if (stagger <= 0f) continue
                val barHeight = usableHeight * (day.total.toFloat() / maxValue) * stagger
                var bottom = usableHeight
                day.segments.forEachIndexed { segmentIndex, segment ->
                    if (segment.seconds <= 0L) return@forEachIndexed
                    val height = barHeight * (segment.seconds.toFloat() / day.total.coerceAtLeast(1L))
                    val color = segmentColors[index].getOrElse(segmentIndex) { fallbackColor }
                    drawRoundRect(
                        color = color,
                        topLeft = Offset(x, bottom - height),
                        size = Size(barWidth, height.coerceAtLeast(0.5f)),
                        cornerRadius = corner
                    )
                    bottom -= height
                }
            }
            if (goalSeconds > 0L) {
                val y = usableHeight * (1f - goalSeconds.toFloat() / maxValue)
                drawLine(
                    color = labelColor.copy(alpha = 0.55f * enter.value),
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = 1.2.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 5f))
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
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Butt)
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
