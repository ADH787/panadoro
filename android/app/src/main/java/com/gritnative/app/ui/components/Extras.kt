package com.gritnative.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gritnative.app.ui.theme.Grit
import com.gritnative.app.ui.theme.Symbol
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * A Grit progress ring. It can undulate while a timer runs, matching the wavy
 * ring in the quickshell app, and otherwise stays a calm round track.
 */
@Composable
fun WavyRing(
    progress: Float,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    track: Color = Grit.colors.field,
    strokeWidth: Dp = 7.dp,
    amplitude: Dp = 1.8.dp,
    wavy: Boolean = false,
    content: @Composable BoxScope.() -> Unit = {}
) {
    WavyRingCanvas(
        progress = { progress },
        modifier = modifier,
        color = color,
        track = track,
        strokeWidth = strokeWidth,
        amplitude = amplitude,
        wavy = wavy,
        content = content
    )
}

/**
 * A ring driven by wall-clock time rather than recomposition. While [running]
 * is true the clock updates once per frame, and because the state is read only
 * inside the Canvas draw pass, the rest of the screen does not recompose.
 */
@Composable
fun LiveWavyRing(
    progressAt: (Long) -> Float,
    running: Boolean,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    track: Color = Grit.colors.field,
    strokeWidth: Dp = 7.dp,
    amplitude: Dp = 1.8.dp,
    wavy: Boolean = false,
    content: @Composable BoxScope.() -> Unit = {}
) {
    val clock = remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(running) {
        if (running) {
            while (true) {
                withFrameMillis { clock.longValue = System.currentTimeMillis() }
            }
        } else {
            clock.longValue = System.currentTimeMillis()
        }
    }
    WavyRingCanvas(
        progress = { progressAt(clock.longValue) },
        modifier = modifier,
        color = color,
        track = track,
        strokeWidth = strokeWidth,
        amplitude = amplitude,
        wavy = wavy,
        content = content
    )
}

@Composable
private fun WavyRingCanvas(
    progress: () -> Float,
    modifier: Modifier,
    color: Color,
    track: Color,
    strokeWidth: Dp,
    amplitude: Dp,
    wavy: Boolean,
    content: @Composable BoxScope.() -> Unit
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = strokeWidth.toPx()
            val radius = ((min(size.width, size.height) - stroke) / 2f).coerceAtLeast(1f)
            val center = Offset(size.width / 2f, size.height / 2f)
            drawCircle(
                color = track,
                radius = radius,
                center = center,
                style = Stroke(width = stroke)
            )
            val fraction = progress().coerceIn(0f, 1f)
            if (fraction <= 0.0005f) return@Canvas
            val sweep = 360f * fraction
            val steps = (sweep / 2f).roundToInt().coerceAtLeast(2)
            val wave = amplitude.toPx()
            val path = Path()
            for (index in 0..steps) {
                val degrees = -90f + sweep * index / steps
                val radians = Math.toRadians(degrees.toDouble())
                val ripple = if (wavy) sin(radians * 18.0).toFloat() * wave else 0f
                val r = radius + ripple
                val x = center.x + (r * cos(radians)).toFloat()
                val y = center.y + (r * sin(radians)).toFloat()
                if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(
                path = path,
                color = color,
                style = Stroke(width = stroke, cap = StrokeCap.Round)
            )
        }
        content()
    }
}

data class SegmentOption<T>(val value: T, val label: String, val symbol: String? = null)

/** A compact full-width selector, used for ranges, filters and themes. */
@Composable
fun <T> SegmentedSelector(
    options: List<SegmentOption<T>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    accent: Color = MaterialTheme.colorScheme.primary,
    accentContainer: Color = MaterialTheme.colorScheme.primaryContainer,
    onAccentContainer: Color = MaterialTheme.colorScheme.onPrimaryContainer
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = CircleShape,
        color = Grit.colors.field,
        border = BorderStroke(1.dp, Grit.colors.outline.copy(alpha = 0.18f))
    ) {
        Row(
            modifier = Modifier.padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            options.forEach { option ->
                val active = option.value == selected
                val segmentColor by animateColorAsState(
                    targetValue = if (active) accentContainer else Color.Transparent,
                    animationSpec = tween(260, easing = androidx.compose.animation.core.FastOutSlowInEasing),
                    label = "segment-color"
                )
                val segmentContent by animateColorAsState(
                    targetValue = if (active) onAccentContainer
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    animationSpec = tween(220),
                    label = "segment-content"
                )
                val symbolScale by animateFloatAsState(
                    targetValue = if (active) 1.08f else 0.96f,
                    animationSpec = spring(dampingRatio = 0.55f, stiffness = 460f),
                    label = "segment-symbol-scale"
                )
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .height(38.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(segmentColor)
                        .clickable { onSelect(option.value) }
                        .padding(horizontal = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    if (option.symbol != null) {
                        Symbol(
                            option.symbol,
                            modifier = Modifier.scale(symbolScale),
                            size = 15.dp,
                            color = segmentContent,
                            filled = active
                        )
                        Box(Modifier.size(5.dp))
                    }
                    Text(
                        text = option.label,
                        color = segmentContent,
                        fontSize = 11.5.sp,
                        lineHeight = 14.sp,
                        fontWeight = if (active) FontWeight.Bold else FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

/** A rounded card with the border weight used by the desktop and web Grit. */
@Composable
fun GritPanel(
    modifier: Modifier = Modifier,
    container: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    borderColor: Color? = Grit.colors.outline.copy(alpha = 0.28f),
    content: @Composable ColumnScope.() -> Unit
) {
    StaggeredEntry {
        Surface(
            modifier = modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            color = container,
            border = borderColor?.let { BorderStroke(1.dp, it) }
        ) {
            Column(modifier = Modifier.padding(contentPadding), content = content)
        }
    }
}

/** Tiny coloured legend item with a shape dot. */
@Composable
fun LegendShape(
    color: Color,
    label: String,
    modifier: Modifier = Modifier,
    shapeName: String = "Cookie4Sided"
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(com.gritnative.app.ui.theme.gritShape(shapeName))
                .background(color)
        )
        Text(
            text = label,
            color = Grit.colors.subtext,
            fontSize = 10.5.sp,
            lineHeight = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
