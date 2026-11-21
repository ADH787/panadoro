package com.gritnative.app.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * A light staggered entrance for cards and rows. Animations are intentionally
 * short and spring-based: the app should feel alive, not slow.
 */
@Composable
fun StaggeredEntry(
    index: Int = 0,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay((index.coerceAtLeast(0) * 45L).coerceAtMost(450L))
        visible = true
    }
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = fadeIn(tween(320, easing = FastOutSlowInEasing)) +
            slideInVertically(spring(dampingRatio = 0.82f, stiffness = 380f)) { it / 7 } +
            scaleIn(initialScale = 0.965f, animationSpec = spring(dampingRatio = 0.8f, stiffness = 420f)),
        exit = fadeOut(tween(120))
    ) {
        content()
    }
}

/** Cross-fades a changing value without a layout jump. */
@Composable
fun <T> MotionText(
    value: T,
    style: TextStyle,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontWeight: FontWeight? = null
) {
    AnimatedContent(
        targetState = value,
        transitionSpec = {
            (fadeIn(tween(260)) + slideInVertically(tween(260)) { it / 3 })
                .togetherWith(fadeOut(tween(140)))
        },
        label = "motion-text"
    ) { shown ->
        androidx.compose.material3.Text(
            text = shown.toString(),
            modifier = modifier,
            style = style,
            color = color,
            fontWeight = fontWeight,
            maxLines = 1
        )
    }
}

/**
 * Slow, low-contrast colour fields used behind the sign-in screen and the
 * celebration. They are pure Canvas work, so they do not allocate on every
 * frame and they keep the rest of the screen readable.
 */
@Composable
fun AmbientBackdrop(
    modifier: Modifier = Modifier,
    first: Color,
    second: Color,
    third: Color
) {
    val transition = rememberInfiniteTransition(label = "ambient")
    val drift by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(9_000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ambient-drift"
    )
    val breathe by transition.animateFloat(
        initialValue = 0.86f,
        targetValue = 1.14f,
        animationSpec = infiniteRepeatable(
            animation = tween(5_400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ambient-breathe"
    )
    Box(modifier = modifier) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(first.copy(alpha = 0.34f), Color.Transparent),
                    center = Offset(w * (0.18f + 0.12f * drift), h * 0.18f),
                    radius = w * 0.72f * breathe
                ),
                radius = w * 0.72f * breathe,
                center = Offset(w * (0.18f + 0.12f * drift), h * 0.18f)
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(second.copy(alpha = 0.3f), Color.Transparent),
                    center = Offset(w * (0.9f - 0.14f * drift), h * 0.42f),
                    radius = w * 0.66f * (2f - breathe)
                ),
                radius = w * 0.66f * (2f - breathe),
                center = Offset(w * (0.9f - 0.14f * drift), h * 0.42f)
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(third.copy(alpha = 0.26f), Color.Transparent),
                    center = Offset(w * (0.48f + 0.1f * (1f - drift)), h * 0.92f),
                    radius = w * 0.7f * breathe
                ),
                radius = w * 0.7f * breathe,
                center = Offset(w * (0.48f + 0.1f * (1f - drift)), h * 0.92f)
            )
        }
    }
}
