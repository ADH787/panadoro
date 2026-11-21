package com.gritnative.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
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
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import com.gritnative.app.ui.components.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gritnative.app.ui.components.GritButton
import com.gritnative.app.ui.components.GritButtonStyle
import com.gritnative.app.ui.components.ShapeBadge
import com.gritnative.app.ui.theme.Grit
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * The polished completion moment: a spring-loaded card, a confetti burst and a
 * quiet timer that lets the user read it before it melts away. Everything is
 * drawn with Canvas primitives, so it costs no assets and works in every theme.
 */
@Composable
fun CelebrationOverlay(
    seed: Long,
    title: String,
    subtitle: String,
    symbol: String,
    success: Boolean,
    onDismiss: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val accent = if (success) Grit.colors.success else MaterialTheme.colorScheme.primary
    val onAccent = if (success) Grit.colors.onSuccess else MaterialTheme.colorScheme.onPrimary
    val container = if (success) Grit.colors.successContainer
    else MaterialTheme.colorScheme.surfaceContainerLow
    val onContainer = if (success) Grit.colors.onSuccessContainer
    else MaterialTheme.colorScheme.onSurface

    var visible by remember(seed) { mutableStateOf(false) }
    val confetti = remember(seed) { rememberConfetti(seed, accent) }
    val burst = remember(seed) { Animatable(0f) }
    val life = remember(seed) { Animatable(1f) }

    fun close() {
        if (!visible) return
        visible = false
        scope.launch {
            delay(230)
            onDismiss()
        }
    }

    LaunchedEffect(seed) {
        visible = true
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        burst.animateTo(1f, tween(1_450, easing = FastOutSlowInEasing))
    }
    LaunchedEffect(seed) {
        life.animateTo(0f, tween(4_200, easing = FastOutSlowInEasing))
        close()
    }

    Box(Modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(tween(220)),
            exit = fadeOut(tween(200))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.38f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { close() }
            )
        }

        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(tween(180)) +
                scaleIn(initialScale = 0.72f, animationSpec = spring(dampingRatio = 0.55f, stiffness = 320f)),
            exit = fadeOut(tween(190)) + scaleOut(targetScale = 0.9f, animationSpec = tween(190))
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val cx = size.width / 2f
                val cy = size.height * 0.42f
                val span = minOf(size.width, size.height)
                confetti.forEach { particle ->
                    val t = ((burst.value - particle.delay) / (1f - particle.delay)).coerceIn(0f, 1f)
                    if (t <= 0f) return@forEach
                    val distance = span * particle.speed * t
                    val x = cx + cos(particle.angle) * distance
                    val y = cy + sin(particle.angle) * distance + span * 0.42f * t * t
                    val alpha = (1f - t).coerceIn(0f, 1f)
                    rotate(degrees = particle.spin * t * 360f, pivot = Offset(x, y)) {
                        drawRoundRect(
                            color = particle.color.copy(alpha = particle.color.alpha * alpha),
                            topLeft = Offset(x - particle.size / 2f, y - particle.size / 4f),
                            size = androidx.compose.ui.geometry.Size(
                                particle.size,
                                particle.size * particle.stretch
                            ),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(particle.size / 3f)
                        )
                    }
                }
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxSize()
                .padding(horizontal = 26.dp),
            contentAlignment = Alignment.Center
        ) {
            AnimatedVisibility(
                visible = visible,
                enter = scaleIn(
                    initialScale = 0.76f,
                    animationSpec = spring(dampingRatio = 0.62f, stiffness = 340f)
                ) + fadeIn(tween(180)),
                exit = fadeOut(tween(180)) + scaleOut(targetScale = 0.9f, animationSpec = tween(180))
            ) {
                Box(modifier = Modifier.widthIn(max = 400.dp)) {
                    // A wide card keeps a calm rounded silhouette: the expressive
                    // polygons are normalised for square badges and stretch into
                    // spikes across a card this wide.
                    Surface(
                        shape = RoundedCornerShape(34.dp),
                        color = container,
                        contentColor = onContainer,
                        shadowElevation = 20.dp
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 22.dp, vertical = 22.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            BurstBadge(
                                symbol = symbol,
                                accent = accent,
                                onAccent = onAccent,
                                burst = burst.value
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = title,
                                color = onContainer,
                                fontSize = 20.sp,
                                lineHeight = 24.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center,
                                maxLines = 3
                            )
                            Text(
                                text = subtitle,
                                color = onContainer.copy(alpha = 0.8f),
                                fontSize = 12.sp,
                                lineHeight = 16.sp,
                                textAlign = TextAlign.Center,
                                maxLines = 3
                            )
                            Spacer(Modifier.height(4.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(4.dp)
                                    .background(onContainer.copy(alpha = 0.12f), CircleShape)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(life.value.coerceIn(0f, 1f))
                                        .height(4.dp)
                                        .background(accent, CircleShape)
                                )
                            }
                            Spacer(Modifier.height(2.dp))
                            GritButton(
                                label = if (success) "Lovely" else "Back to it",
                                onClick = { close() },
                                symbol = "check",
                                style = GritButtonStyle.FILLED
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BurstBadge(
    symbol: String,
    accent: Color,
    onAccent: Color,
    burst: Float
) {
    val transition = rememberInfiniteTransition(label = "celebration-pulse")
    val pulse by transition.animateFloat(
        initialValue = 0.97f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1_400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "celebration-pulse-value"
    )
    Box(contentAlignment = Alignment.Center) {
        Canvas(Modifier.height(118.dp).fillMaxWidth()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val rays = 14
            for (index in 0 until rays) {
                val angle = Math.toRadians((index * 360.0 / rays) + burst * 32.0)
                val inner = 34.dp.toPx()
                val outer = (44.dp.toPx() + 20.dp.toPx() * burst) * if (index % 2 == 0) 1f else 0.8f
                val start = Offset(
                    center.x + cos(angle).toFloat() * inner,
                    center.y + sin(angle).toFloat() * inner
                )
                val end = Offset(
                    center.x + cos(angle).toFloat() * outer,
                    center.y + sin(angle).toFloat() * outer
                )
                drawLine(
                    color = accent.copy(alpha = 0.45f),
                    start = start,
                    end = end,
                    strokeWidth = 3.dp.toPx(),
                    cap = androidx.compose.ui.graphics.StrokeCap.Round
                )
            }
        }
        Box(Modifier.scale(pulse * (0.9f + 0.1f * burst))) {
            ShapeBadge(
                symbol = symbol,
                shapeName = "SoftBurst",
                container = accent,
                content = onAccent,
                size = 76.dp,
                iconSize = 38.dp,
                filled = true
            )
        }
    }
}

private data class ConfettiParticle(
    val angle: Float,
    val speed: Float,
    val size: Float,
    val stretch: Float,
    val spin: Float,
    val delay: Float,
    val color: Color
)

private fun rememberConfetti(seed: Long, accent: Color): List<ConfettiParticle> {
    val random = Random(seed)
    val palette = listOf(
        accent,
        accent.copy(alpha = 0.72f),
        Color(0xFFF3B64B),
        Color(0xFF7BD8A8),
        Color(0xFF8AB4F8),
        Color(0xFFF28DB2)
    )
    return List(86) { index ->
        val angle = random.nextFloat() * (Math.PI * 2).toFloat()
        ConfettiParticle(
            angle = angle,
            speed = 0.16f + random.nextFloat() * 0.42f,
            size = 5f + random.nextFloat() * 9f,
            stretch = 0.6f + random.nextFloat() * 1.2f,
            spin = -2.5f + random.nextFloat() * 5f,
            delay = random.nextFloat() * 0.22f,
            color = palette[index % palette.size]
        )
    }
}
