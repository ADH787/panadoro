package com.gritnative.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.math.roundToInt
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gritnative.app.data.Category
import com.gritnative.app.data.Habit
import com.gritnative.app.data.Store
import com.gritnative.app.data.Tag
import com.gritnative.app.data.addDays
import com.gritnative.app.data.categoryById
import com.gritnative.app.data.currentStreak
import com.gritnative.app.data.dateKey
import com.gritnative.app.data.dateNumberOf
import com.gritnative.app.data.habitDone
import com.gritnative.app.data.habitDoneEffective
import com.gritnative.app.data.habitProgress
import com.gritnative.app.data.habitValue
import com.gritnative.app.data.scheduledOn
import com.gritnative.app.data.thisWeekDays
import com.gritnative.app.data.weekDoneDays
import com.gritnative.app.data.weekStartOf
import com.gritnative.app.data.weekdayNarrow
import com.gritnative.app.data.weeklyHabitStreak
import com.gritnative.app.ui.theme.AccentColors
import com.gritnative.app.ui.theme.Grit
import com.gritnative.app.ui.theme.GritShapeNames
import com.gritnative.app.ui.theme.Symbol
import com.gritnative.app.ui.theme.accentColors
import com.gritnative.app.ui.theme.categoryColors
import com.gritnative.app.ui.theme.gritShape
import com.gritnative.app.ui.theme.lerpColor
import com.gritnative.app.ui.theme.mostReadable
import com.gritnative.app.ui.theme.tagColors

// ── Surfaces ────────────────────────────────────────────────────────────

@Composable
fun GritCard(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    borderColor: Color? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        color = color,
        border = borderColor?.let { BorderStroke(1.dp, it) }
    ) {
        Column(content = content)
    }
}

@Composable
fun ShapeBadge(
    symbol: String,
    shapeName: String,
    container: Color,
    content: Color,
    modifier: Modifier = Modifier,
    size: Dp = 46.dp,
    iconSize: Dp = 22.dp,
    rotation: Float = 0f,
    filled: Boolean = false
) {
    val shape = gritShape(shapeName)
    Box(
        modifier = modifier
            .size(size)
            .rotate(rotation)
            .background(container, shape),
        contentAlignment = Alignment.Center
    ) {
        Symbol(symbol, size = iconSize, color = content, filled = filled)
    }
}

@Composable
fun HeroCard(
    symbol: String,
    title: String,
    text: String,
    container: Color,
    onContainer: Color,
    accent: Color,
    shapeName: String = "Cookie9Sided",
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null
) {
    // Do not clip a wide hero with a Material polygon: those polygons are
    // normalised for square badges and become distorted when stretched across
    // the full width. Keep the expressive shape for the square icon badge and
    // use a large rounded corner for the surface itself.
    val shape = RoundedCornerShape(30.dp)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(container)
    ) {
        // A quiet watermark tucked fully inside the card, away from the edges.
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 12.dp, bottom = 8.dp)
                .size(112.dp)
                .rotate(-14f)
                .background(accent.copy(alpha = 0.08f), gritShape("Cookie12Sided"))
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            ShapeBadge(
                symbol = symbol,
                shapeName = shapeName,
                container = accent,
                content = mostReadable(accent, listOf(Color.White, Color(0xFF17130F))),
                size = 58.dp,
                iconSize = 30.dp
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    text = title,
                    color = onContainer,
                    fontSize = 21.sp,
                    lineHeight = 25.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
                if (text.isNotBlank()) {
                    Text(
                        text = text,
                        color = onContainer.copy(alpha = 0.86f),
                        fontSize = 12.5.sp,
                        lineHeight = 16.sp,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            trailing?.invoke()
        }
    }
}

/**
 * The quiet hero used by Habits and Tasks: the sentence that describes the
 * moment, the ring that counts it, and a slow drifting shape behind both.
 */
@Composable
fun HeroBanner(
    title: String,
    done: Int,
    total: Int,
    container: Color,
    onContainer: Color,
    accent: Color,
    shapeName: String,
    modifier: Modifier = Modifier,
    wavy: Boolean = total > 0 && done < total
) {
    val cardShape = RoundedCornerShape(30.dp)
    val animatedProgress by animateFloatAsState(
        targetValue = if (total > 0) done.toFloat() / total else 0f,
        animationSpec = spring(dampingRatio = 0.72f, stiffness = 240f),
        label = "hero-progress"
    )

    // A single slow loop: the watermark turns all the way round while it
    // drifts and breathes, like it is swimming inside the card.
    val motion = rememberInfiniteTransition(label = "hero-shape")
    val spin by motion.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(52_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "hero-spin"
    )
    // Linear travel plus Reverse gives the crisp turn-around of a bounce the
    // instant the shape reaches an edge, rather than easing through it.
    val driftX by motion.animateFloat(
        initialValue = 0f,
        targetValue = -28f,
        animationSpec = infiniteRepeatable(
            animation = tween(7_200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "hero-drift-x"
    )
    val driftY by motion.animateFloat(
        initialValue = -5f,
        targetValue = 5f,
        animationSpec = infiniteRepeatable(
            animation = tween(9_600, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "hero-drift-y"
    )
    val breathe by motion.animateFloat(
        initialValue = 0.98f,
        targetValue = 1.02f,
        animationSpec = infiniteRepeatable(
            animation = tween(11_000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "hero-breathe"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(cardShape)
            .background(container)
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 1.dp, top = 5.dp, bottom = 5.dp)
                .size(104.dp)
                .graphicsLayer {
                    rotationZ = spin
                    translationX = driftX.dp.toPx()
                    translationY = driftY.dp.toPx()
                    scaleX = breathe
                    scaleY = breathe
                }
                .background(accent.copy(alpha = 0.10f), gritShape(shapeName))
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = title,
                modifier = Modifier
                    .weight(1f)
                    .offset(y = 1.dp),
                color = onContainer,
                fontSize = 26.sp,
                lineHeight = 31.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            WavyRing(
                progress = animatedProgress,
                modifier = Modifier.size(78.dp),
                color = accent,
                track = onContainer.copy(alpha = 0.16f),
                strokeWidth = 6.dp,
                amplitude = 1.6.dp,
                wavy = wavy
            ) {
                Text(
                    text = "$done/$total",
                    color = onContainer,
                    fontSize = 16.sp,
                    lineHeight = 19.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
fun StatTile(
    symbol: String,
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    hint: String = "",
    shapeName: String = "Cookie7Sided",
    container: Color = MaterialTheme.colorScheme.primaryContainer,
    onContainer: Color = MaterialTheme.colorScheme.onPrimaryContainer,
    flame: Boolean = false
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(13.dp)
        ) {
            // The flame only animates while it is actually lit; an idle tile
            // costs zero frames instead of invalidating forever.
            val flaming = flame && value != "0" && value != "—"
            val flameScale = if (flaming) {
                val transition = rememberInfiniteTransition(label = "flame")
                val animated by transition.animateFloat(
                    initialValue = 1f,
                    targetValue = 1.09f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(1400),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "flameScale"
                )
                animated
            } else {
                1f
            }
            Box(modifier = Modifier.rotate((flameScale - 1f) * 120f)) {
                ShapeBadge(
                    symbol = symbol,
                    shapeName = shapeName,
                    container = container,
                    content = mostReadable(container, listOf(onContainer, MaterialTheme.colorScheme.onSurface)),
                    size = 46.dp,
                    iconSize = 22.dp,
                    filled = flame
                )
            }
            Column(Modifier.weight(1f)) {
                Text(
                    text = value,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 23.sp,
                    lineHeight = 27.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = label,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                    lineHeight = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (hint.isNotBlank()) {
                    Text(
                        text = hint,
                        color = Grit.colors.subtext,
                        fontSize = 10.5.sp,
                        lineHeight = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
fun SectionTitle(title: String, modifier: Modifier = Modifier, trailing: (@Composable () -> Unit)? = null) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = title,
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 22.sp,
            lineHeight = 27.sp,
            fontWeight = FontWeight.Bold
        )
        trailing?.invoke()
    }
}

@Composable
fun EmptyState(
    symbol: String,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    shapeName: String = "Cookie9Sided"
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(vertical = 28.dp, horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        ShapeBadge(
            symbol = symbol,
            shapeName = shapeName,
            container = MaterialTheme.colorScheme.secondaryContainer,
            content = MaterialTheme.colorScheme.onSecondaryContainer,
            size = 96.dp,
            iconSize = 44.dp
        )
        Text(
            text = title,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

// ── Chips and buttons ───────────────────────────────────────────────────

@Composable
fun TagChip(
    tag: Tag,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    trailingText: String = "",
    enabled: Boolean = true
) {
    val colors = tagColors(tag)
    val shape = if (selected) RoundedCornerShape(16.dp) else CircleShape
    val container by animateColorAsState(
        targetValue = if (selected) colors.container else colors.softContainer,
        animationSpec = tween(280),
        label = "chip-container"
    )
    val contentColor by animateColorAsState(
        targetValue = if (selected) colors.onContainer else MaterialTheme.colorScheme.onSurface,
        animationSpec = tween(240),
        label = "chip-content"
    )
    val iconRotation by animateFloatAsState(
        targetValue = if (selected) 12f else 0f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = 420f),
        label = "chip-icon-rotation"
    )
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = 36.dp),
        shape = shape,
        color = container,
        contentColor = contentColor
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier.size(22.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .rotate(iconRotation)
                        .background(if (selected) colors.accent else colors.accent.copy(alpha = 0.85f), gritShape(tag.shape))
                )
                Symbol(
                    tag.icon,
                    size = 14.dp,
                    filled = true,
                    color = if (selected) mostReadable(colors.accent, listOf(Color.White, Color(0xFF17130F)))
                    else mostReadable(colors.accent, listOf(MaterialTheme.colorScheme.onSurface, Color.White))
                )
            }
            Text(
                text = tag.name,
                fontSize = 13.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (trailingText.isNotBlank()) {
                Text(
                    text = trailingText,
                    fontSize = 11.sp,
                    color = contentColor.copy(alpha = 0.72f)
                )
            }
        }
    }
}

@Composable
fun FormChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    symbol: String? = null,
    accent: Color = MaterialTheme.colorScheme.primary
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = CircleShape,
        color = if (selected) accent.copy(alpha = 0.18f) else Grit.colors.field,
        contentColor = if (selected) accent else MaterialTheme.colorScheme.onSurfaceVariant
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 13.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (symbol != null) Symbol(symbol, size = 15.dp, filled = selected)
            Text(label, fontSize = 12.5.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium, maxLines = 1)
        }
    }
}

@Composable
fun RoundIconButton(
    symbol: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    container: Color = Grit.colors.field,
    content: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    size: Dp = 40.dp,
    iconSize: Dp = 19.dp,
    enabled: Boolean = true,
    filled: Boolean = false
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.size(size),
        shape = CircleShape,
        color = container,
        contentColor = content,
        tonalElevation = 0.dp
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            Symbol(symbol, size = iconSize, color = if (enabled) content else content.copy(alpha = 0.4f), filled = filled)
        }
    }
}

@Composable
fun ProgressLine(
    progress: Float,
    color: Color,
    modifier: Modifier = Modifier,
    track: Color = Grit.colors.field,
    height: Dp = 6.dp,
    minFraction: Float = 0.03f
) {
    val animated by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = spring(dampingRatio = 0.78f, stiffness = 260f),
        label = "progress-line"
    )
    val animatedColor by animateColorAsState(
        targetValue = color,
        animationSpec = tween(320),
        label = "progress-color"
    )
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(CircleShape)
            .background(track)
    ) {
        // At zero the bar is completely empty — the old minimum fraction drew
        // a tiny dot at the start that looked like a stray value.
        if (animated > 0.0001f) {
            Box(
                Modifier
                    .fillMaxWidth(animated.coerceIn(minFraction, 1f))
                    .fillMaxSize()
                    .background(animatedColor, CircleShape)
            )
        }
    }
}

@Composable
fun Flame(count: Int, color: Color, modifier: Modifier = Modifier, iconSize: Dp = 13.dp, showZero: Boolean = false) {
    if (count <= 0 && !showZero) return
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Box(
            modifier = Modifier
                .size(iconSize + 8.dp)
                .background(lerpColor(MaterialTheme.colorScheme.surfaceContainerLow, color, 0.14f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Symbol("local_fire_department", size = iconSize, color = color, filled = true)
        }
        Text(
            text = count.toString(),
            color = color,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

// ── Forms ───────────────────────────────────────────────────────────────

@Composable
fun GritField(
    value: String,
    onValueChange: (String) -> Unit,
    caption: String,
    modifier: Modifier = Modifier,
    symbol: String = "edit_note",
    placeholder: String = "",
    singleLine: Boolean = true,
    minLines: Int = 1,
    enabled: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    trailing: (@Composable () -> Unit)? = null
) {
    val scheme = MaterialTheme.colorScheme
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 58.dp),
        textStyle = MaterialTheme.typography.bodyMedium,
        label = { Text(caption, fontSize = 12.sp) },
        placeholder = { if (placeholder.isNotBlank()) Text(placeholder, fontSize = 13.sp) },
        leadingIcon = { Symbol(symbol, size = 19.dp, color = scheme.onSurfaceVariant) },
        trailingIcon = trailing,
        enabled = enabled,
        singleLine = singleLine,
        minLines = minLines,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        visualTransformation = visualTransformation,
        shape = RoundedCornerShape(16.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = Grit.colors.field.copy(alpha = 0.55f),
            unfocusedContainerColor = Grit.colors.field.copy(alpha = 0.55f),
            unfocusedBorderColor = Color.Transparent,
            focusedBorderColor = scheme.primary.copy(alpha = 0.6f),
            unfocusedLabelColor = scheme.onSurfaceVariant,
            focusedLabelColor = scheme.primary
        )
    )
}

@Composable
fun Stepper(
    value: Int,
    onChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    from: Int = 0,
    to: Int = 999,
    step: Int = 1,
    format: (Int) -> String = { it.toString() },
    accent: Color = MaterialTheme.colorScheme.primary
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        RoundIconButton(
            symbol = "remove",
            onClick = { onChange((value - step).coerceIn(from, to)) },
            size = 32.dp,
            iconSize = 16.dp,
            container = Grit.colors.field
        )
        Text(
            text = format(value),
            color = accent,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(52.dp),
            maxLines = 1,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        RoundIconButton(
            symbol = "add",
            onClick = { onChange((value + step).coerceIn(from, to)) },
            size = 32.dp,
            iconSize = 16.dp,
            container = Grit.colors.field
        )
    }
}

@Composable
fun ToggleRow(
    symbol: String,
    label: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    accent: Color = MaterialTheme.colorScheme.primary,
    accentContainer: Color = MaterialTheme.colorScheme.primaryContainer,
    accentOn: Color = MaterialTheme.colorScheme.onPrimaryContainer
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Grit.colors.field)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        ShapeBadge(
            symbol = symbol,
            shapeName = "Cookie7Sided",
            container = accentContainer,
            content = accentOn,
            size = 44.dp,
            iconSize = 22.dp
        )
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AccentPicker(
    selected: String,
    onSelect: (String) -> Unit,
    customColor: String,
    accentNames: List<Pair<String, String>> = listOf(
        "primary" to "Primary",
        "secondary" to "Secondary",
        "tertiary" to "Tertiary",
        "success" to "Green",
        "error" to "Red",
        "outline" to "Neutral"
    ),
    modifier: Modifier = Modifier
) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        accentNames.forEach { (id, label) ->
            val colors = accentColors(id)
            FormChip(
                label = label,
                selected = customColor.isBlank() && selected == id,
                onClick = { onSelect(id) },
                symbol = if (customColor.isBlank() && selected == id) "check" else null,
                accent = colors.accent
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ShapePicker(
    selected: String,
    onSelect: (String) -> Unit,
    accent: Color,
    modifier: Modifier = Modifier
) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        GritShapeNames.forEach { name ->
            val active = name == selected
            val shape = gritShape(name, if (active) 18 else 0)
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (active) accent.copy(alpha = 0.16f) else Color.Transparent)
                    .clickable { onSelect(name) },
                contentAlignment = Alignment.Center
            ) {
                Box(
                    Modifier
                        .size(28.dp)
                        .background(if (active) accent else Grit.colors.fieldHover, shape),
                    contentAlignment = Alignment.Center
                ) {
                    if (active) {
                        Symbol(
                            "check",
                            size = 14.dp,
                            filled = true,
                            color = mostReadable(accent, listOf(Color.White, Color(0xFF17130F)))
                        )
                    }
                }
            }
        }
    }
}

// ── Habit / task rows ───────────────────────────────────────────────────

@Composable
fun HabitCard(
    store: Store,
    habit: Habit,
    modifier: Modifier = Modifier,
    weekStartMonday: Boolean = true,
    onEdit: () -> Unit,
    onToggle: () -> Unit,
    onDayClick: (String) -> Unit
) {
    val category: Category = store.categoryById(habit.categoryId)
    val colors = categoryColors(category)
    val weekly = habit.weeklyTimes > 0
    val weekDaysDone = if (weekly) {
        store.weekDoneDays(habit, weekStartOf(dateKey(), weekStartMonday))
    } else 0
    val done = if (weekly) {
        store.habitDoneEffective(habit, dateKey(), weekStartMonday)
    } else {
        store.habitDone(habit)
    }
    val progress = if (weekly) {
        (weekDaysDone.toFloat() / habit.weeklyTimes.coerceIn(1, 7)).coerceIn(0f, 1f)
    } else {
        store.habitProgress(habit)
    }
    val value = store.habitValue(habit.id, dateKey())
    val target = habit.target.coerceAtLeast(1)
    val streak = if (weekly) {
        store.weeklyHabitStreak(habit, dateKey(), weekStartMonday)
    } else {
        store.currentStreak(habit)
    }
    val container by animateColorAsState(
        targetValue = if (done) {
            lerpColor(MaterialTheme.colorScheme.surfaceContainerLow, colors.container, 0.58f)
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        },
        animationSpec = tween(380, easing = androidx.compose.animation.core.FastOutSlowInEasing),
        label = "habit-container"
    )
    val doneContent = mostReadable(colors.accent, listOf(Color.White, Color(0xFF17130F)))
    val badgeRotation by animateFloatAsState(
        targetValue = if (done) 18f else 0f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = 360f),
        label = "habit-badge-rotation"
    )
    val badgeColor by animateColorAsState(
        targetValue = if (done) colors.accent else colors.container,
        animationSpec = tween(320),
        label = "habit-badge-color"
    )
    val toggleColor by animateColorAsState(
        targetValue = if (done) colors.accent else Color.Transparent,
        animationSpec = tween(260),
        label = "habit-toggle-color"
    )
    val toggleContent by animateColorAsState(
        targetValue = if (done) doneContent else colors.accent,
        animationSpec = tween(260),
        label = "habit-toggle-content"
    )
    val toggleScale by animateFloatAsState(
        targetValue = if (done) 1f else 0.94f,
        animationSpec = spring(dampingRatio = 0.48f, stiffness = 440f),
        label = "habit-toggle-scale"
    )

    Surface(
        onClick = onEdit,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = container
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(13.dp)
        ) {
            ShapeBadge(
                symbol = habit.icon,
                shapeName = if (done) "Cookie12Sided" else "Cookie7Sided",
                container = badgeColor,
                content = if (done) doneContent else colors.onContainer,
                size = 47.dp,
                iconSize = 23.dp,
                rotation = badgeRotation
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = habit.name,
                        modifier = Modifier.weight(1f),
                        color = if (done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                        fontSize = 14.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textDecoration = if (done && target == 1) androidx.compose.ui.text.style.TextDecoration.LineThrough else null
                    )
                    Flame(streak, colors.accent)
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = category.name,
                        color = colors.accent,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1
                    )
                    if (habit.time.isNotBlank()) {
                        Text("· ${habit.time}", color = Grit.colors.subtext, fontSize = 11.sp)
                    }
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = when {
                            weekly && done -> "Week done · ${weekDaysDone} of ${habit.weeklyTimes}"
                            weekly -> "${weekDaysDone} of ${habit.weeklyTimes} days this week"
                            target > 1 -> "$value of $target ${habit.unit}"
                            done -> "Done today"
                            else -> "Not yet"
                        },
                        color = if (done) colors.accent else Grit.colors.subtext,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (weekly || target > 1) {
                    ProgressLine(progress = progress, color = colors.accent, height = 5.dp)
                }
                HabitWeek(
                    store = store,
                    habit = habit,
                    accent = colors.accent,
                    weekStartMonday = weekStartMonday,
                    onDayClick = onDayClick,
                    modifier = Modifier.padding(top = 3.dp)
                )
            }
            Surface(
                onClick = onToggle,
                modifier = Modifier
                    .size(46.dp)
                    .then(Modifier.scale(toggleScale)),
                shape = if (done) RoundedCornerShape(10.dp) else CircleShape,
                color = toggleColor,
                contentColor = toggleContent,
                border = if (done) null else BorderStroke(1.5.dp, colors.accent)
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Symbol(
                        if (done) "check" else "add",
                        size = 22.dp,
                        filled = true,
                        color = toggleContent
                    )
                }
            }
        }
    }
}

@Composable
fun HabitWeek(
    store: Store,
    habit: Habit,
    accent: Color,
    onDayClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    weekStartMonday: Boolean = true
) {
    val today = dateKey()
    val days = thisWeekDays(today, monday = weekStartMonday)
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        days.forEach { key ->
            val scheduled = store.scheduledOn(habit, key)
            val done = store.habitDone(habit, key)
            val progress = store.habitProgress(habit, key)
            val future = key > today
            val isToday = key == today
            val bg = when {
                done -> lerpColor(Grit.colors.field, accent, 0.35f + 0.65f * progress)
                scheduled && !future -> Grit.colors.field
                else -> Grit.colors.field.copy(alpha = 0.4f)
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .height(38.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(bg)
                    .then(
                        if (isToday) Modifier.border(1.5.dp, accent, RoundedCornerShape(8.dp))
                        else if (scheduled && !done && !future) Modifier.border(1.dp, Grit.colors.outlineStrong.copy(alpha = 0.55f), RoundedCornerShape(8.dp))
                        else Modifier
                    )
                    .clickable(enabled = scheduled && !future) { onDayClick(key) }
                    .padding(vertical = 3.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = weekdayNarrow(key),
                    color = if (done) mostReadable(bg, listOf(Color.White, Color(0xFF17130F))) else Grit.colors.subtext,
                    fontSize = 8.sp,
                    lineHeight = 9.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = dateNumberOf(key).toString(),
                    color = if (done) mostReadable(bg, listOf(Color.White, Color(0xFF17130F))) else if (scheduled) MaterialTheme.colorScheme.onSurface else Grit.colors.subtext,
                    fontSize = 11.sp,
                    lineHeight = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun CategoryChip(category: Category, selected: Boolean, onClick: () -> Unit, count: String = "") {
    val colors = categoryColors(category)
    Surface(
        onClick = onClick,
        shape = if (selected) RoundedCornerShape(14.dp) else CircleShape,
        color = if (selected) colors.container else Grit.colors.field.copy(alpha = 0.7f),
        contentColor = if (selected) colors.onContainer else MaterialTheme.colorScheme.onSurface
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            Symbol(category.icon, size = 16.dp, color = colors.accent, filled = selected)
            Text(category.name, fontSize = 12.5.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium, maxLines = 1)
            if (count.isNotBlank()) Text(count, fontSize = 10.5.sp, color = (if (selected) colors.onContainer else MaterialTheme.colorScheme.onSurface).copy(alpha = 0.7f))
        }
    }
}

@Composable
fun InfoPill(text: String, symbol: String, container: Color, content: Color, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = container,
        contentColor = content
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Symbol(symbol, size = 14.dp)
            Text(text, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
        }
    }
}

@Composable
fun SheetSubtitle(text: String) {
    Text(
        text = text,
        color = Grit.colors.subtext,
        fontSize = 12.5.sp,
        lineHeight = 17.sp
    )
}

@Composable
fun Label(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = 11.5.sp,
        fontWeight = FontWeight.SemiBold
    )
}

@Composable
fun LocalProviderColor(): Color = LocalContentColor.current

// ── Buttons and settings ────────────────────────────────────────────────

enum class GritButtonStyle { FILLED, TONAL, OUTLINED, TEXT }

@Composable
fun GritButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    symbol: String? = null,
    style: GritButtonStyle = GritButtonStyle.TONAL,
    enabled: Boolean = true,
    danger: Boolean = false
) {
    val content: @Composable () -> Unit = {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            if (symbol != null) Symbol(symbol, size = 18.dp, filled = style == GritButtonStyle.FILLED)
            Text(
                text = label,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
    val padding = PaddingValues(horizontal = 18.dp, vertical = 11.dp)
    when (style) {
        GritButtonStyle.FILLED -> Button(
            onClick = onClick,
            modifier = modifier.heightIn(min = 46.dp),
            enabled = enabled,
            shape = CircleShape,
            contentPadding = padding,
            colors = if (danger) ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error,
                contentColor = MaterialTheme.colorScheme.onError
            ) else ButtonDefaults.buttonColors()
        ) { content() }

        GritButtonStyle.TONAL -> FilledTonalButton(
            onClick = onClick,
            modifier = modifier.heightIn(min = 46.dp),
            enabled = enabled,
            shape = CircleShape,
            contentPadding = padding,
            colors = if (danger) ButtonDefaults.filledTonalButtonColors(
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer
            ) else ButtonDefaults.filledTonalButtonColors()
        ) { content() }

        GritButtonStyle.OUTLINED -> OutlinedButton(
            onClick = onClick,
            modifier = modifier.heightIn(min = 46.dp),
            enabled = enabled,
            shape = CircleShape,
            contentPadding = padding,
            colors = if (danger) ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.error
            ) else ButtonDefaults.outlinedButtonColors()
        ) { content() }

        GritButtonStyle.TEXT -> TextButton(
            onClick = onClick,
            modifier = modifier.heightIn(min = 42.dp),
            enabled = enabled,
            shape = CircleShape,
            contentPadding = padding,
            colors = if (danger) ButtonDefaults.textButtonColors(
                contentColor = MaterialTheme.colorScheme.error
            ) else ButtonDefaults.textButtonColors()
        ) { content() }
    }
}

@Composable
fun SettingsSection(
    title: String,
    symbol: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    StaggeredEntry {
        Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ShapeBadge(
                    symbol = symbol,
                    shapeName = "Cookie7Sided",
                    container = MaterialTheme.colorScheme.surfaceContainerHigh,
                    content = MaterialTheme.colorScheme.onSurfaceVariant,
                    size = 34.dp,
                    iconSize = 17.dp
                )
                Text(
                    text = title,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow
            ) {
                Column(
                    modifier = Modifier.padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    content = content
                )
            }
        }
    }
}

@Composable
fun SettingsRow(
    symbol: String,
    title: String,
    description: String = "",
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    danger: Boolean = false,
    trailing: (@Composable () -> Unit)? = null
) {
    val base = modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(14.dp))
    Row(
        modifier = (if (onClick != null) base.clickable(onClick = onClick) else base)
            .padding(horizontal = 8.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        ShapeBadge(
            symbol = symbol,
            shapeName = "Cookie4Sided",
            container = if (danger) MaterialTheme.colorScheme.errorContainer
            else MaterialTheme.colorScheme.surfaceContainerHigh,
            content = if (danger) MaterialTheme.colorScheme.onErrorContainer
            else MaterialTheme.colorScheme.onSurfaceVariant,
            size = 44.dp,
            iconSize = 22.dp
        )
        Text(
            text = title,
            modifier = Modifier.weight(1f),
            color = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            fontSize = 14.sp,
            lineHeight = 18.sp,
            fontWeight = FontWeight.SemiBold
        )
        trailing?.invoke()
    }
}

@Composable
fun MiniStatTile(
    symbol: String,
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    hint: String = "",
    shapeName: String = "Cookie7Sided",
    container: Color = MaterialTheme.colorScheme.primaryContainer,
    onContainer: Color = MaterialTheme.colorScheme.onPrimaryContainer,
    flame: Boolean = false
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(modifier = Modifier.padding(11.dp)) {
            ShapeBadge(
                symbol = symbol,
                shapeName = shapeName,
                container = container,
                content = mostReadable(container, listOf(onContainer, MaterialTheme.colorScheme.onSurface)),
                size = 34.dp,
                iconSize = 17.dp,
                filled = flame
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = value,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 20.sp,
                lineHeight = 24.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = label,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 10.5.sp,
                lineHeight = 13.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * One small confirming tick for the timer's controls. Every Android build has
 * the classic feedback constants; R adds a slightly firmer confirmation pulse
 * that suits a deliberate press like play/pause.
 */
fun tapHaptic(view: android.view.View, strong: Boolean = false) {
    val constant = when {
        strong && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R ->
            android.view.HapticFeedbackConstants.CONFIRM
        else -> android.view.HapticFeedbackConstants.VIRTUAL_KEY
    }
    view.performHapticFeedback(constant)
}

/**
 * A thick, round, Material-You-flavoured slider: a pill track with a filled
 * accent portion and a big round thumb with a soft gap ring around it. Taps
 * jump straight to the finger, drags follow it, and values snap to [step]
 * multiples. Drawn in one canvas so it never flickers.
 */
@Composable
fun GritSlider(
    value: Int,
    onValueChange: (Int) -> Unit,
    from: Int,
    to: Int,
    accent: Color,
    modifier: Modifier = Modifier,
    step: Int = 1
) {
    if (to <= from) return
    val span = to - from
    val steps = (span.toFloat() / step.coerceAtLeast(1)).roundToInt().coerceAtLeast(1)
    val position = ((value - from).toFloat() / span).coerceIn(0f, 1f)
    var pressed by remember { mutableStateOf(false) }
    val thumbScale by animateFloatAsState(
        targetValue = if (pressed) 1.14f else 1f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = 600f),
        label = "slider-thumb"
    )

    fun snapped(x: Float, width: Float): Int {
        val fraction = (x / width).coerceIn(0f, 1f)
        return (from + (fraction * steps).roundToInt() * step).coerceIn(from, to)
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(30.dp)
            .pointerInput(from, to, step) {
                detectTapGestures(
                    onTap = { offset -> onValueChange(snapped(offset.x, size.width.toFloat())) }
                )
            }
            .pointerInput(from, to, step) {
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        pressed = true
                        onValueChange(snapped(offset.x, size.width.toFloat()))
                    },
                    onDragEnd = { pressed = false },
                    onDragCancel = { pressed = false }
                ) { change, _ ->
                    change.consume()
                    onValueChange(snapped(change.position.x, size.width.toFloat()))
                }
            }
    ) {
        val trackColor = Grit.colors.field
        Canvas(Modifier.fillMaxSize()) {
            val cy = size.height / 2f
            val trackHeight = 9.dp.toPx()
            val thumbRadius = 11.dp.toPx() * thumbScale
            val inset = thumbRadius + 1.dp.toPx()
            val thumbX = inset + (size.width - 2 * inset) * position
            drawRoundRect(
                color = trackColor,
                topLeft = Offset(0f, cy - trackHeight / 2f),
                size = Size(size.width, trackHeight),
                cornerRadius = CornerRadius(trackHeight / 2f)
            )
            if (position > 0f) {
                drawRoundRect(
                    color = accent,
                    topLeft = Offset(0f, cy - trackHeight / 2f),
                    size = Size(maxOf(thumbX, trackHeight), trackHeight),
                    cornerRadius = CornerRadius(trackHeight / 2f)
                )
            }
            // The soft gap ring around the thumb gives the Material "stop
            // indicator" look: a quiet hole in the track, then the thumb.
            drawCircle(
                color = trackColor,
                radius = thumbRadius + 2.5.dp.toPx(),
                center = Offset(thumbX, cy)
            )
            drawCircle(
                color = accent,
                radius = thumbRadius,
                center = Offset(thumbX, cy)
            )
        }
    }
}
