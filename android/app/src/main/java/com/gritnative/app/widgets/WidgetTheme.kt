package com.gritnative.app.widgets

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import androidx.annotation.DrawableRes
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ColumnScope
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontFamily
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.gritnative.app.data.AppSettings
import com.gritnative.app.data.ThemeMode

/**
 * The in-app theme is Material You first, with a warm green fallback. Widgets
 * use the same rule so a Grit widget feels like a piece of the app instead of
 * a separate remote view.
 */
data class WidgetColors(
    val background: Color,
    val surface: Color,
    val surfaceHigh: Color,
    val text: Color,
    val subtext: Color,
    val primary: Color,
    val onPrimary: Color,
    val primaryContainer: Color,
    val onPrimaryContainer: Color,
    val tertiary: Color,
    val tertiaryContainer: Color,
    val onTertiaryContainer: Color,
    val success: Color,
    val error: Color,
    val outline: Color,
    val isDark: Boolean
)

private val lightWidgetScheme = lightColorScheme(
    primary = Color(0xFF3E6654),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFC0ECD5),
    onPrimaryContainer = Color(0xFF002115),
    tertiary = Color(0xFF755B2F),
    tertiaryContainer = Color(0xFFFFE0A6),
    onTertiaryContainer = Color(0xFF281900),
    error = Color(0xFFBA1A1A),
    background = Color(0xFFF6FBF5),
    onBackground = Color(0xFF171D19),
    surface = Color(0xFFF6FBF5),
    onSurface = Color(0xFF171D19),
    surfaceVariant = Color(0xFFDCE5DC),
    onSurfaceVariant = Color(0xFF404943),
    outline = Color(0xFF707972),
    outlineVariant = Color(0xFFC0C9C1),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFFFFFFF),
    surfaceContainer = Color(0xFFEAEFE9),
    surfaceContainerHigh = Color(0xFFE4EAE4),
    surfaceContainerHighest = Color(0xFFDEE4DE)
)

private val darkWidgetScheme = darkColorScheme(
    primary = Color(0xFFA4D0BA),
    onPrimary = Color(0xFF0B3728),
    primaryContainer = Color(0xFF254E3D),
    onPrimaryContainer = Color(0xFFC0ECD5),
    tertiary = Color(0xFFE4C27C),
    tertiaryContainer = Color(0xFF5A4318),
    onTertiaryContainer = Color(0xFFFFE0A6),
    error = Color(0xFFFFB4AB),
    background = Color(0xFF0F1511),
    onBackground = Color(0xFFDEE4DE),
    surface = Color(0xFF0F1511),
    onSurface = Color(0xFFDEE4DE),
    surfaceVariant = Color(0xFF404943),
    onSurfaceVariant = Color(0xFFC0C9C1),
    outline = Color(0xFF8A938B),
    outlineVariant = Color(0xFF404943),
    surfaceContainerLowest = Color(0xFF0A0F0C),
    surfaceContainerLow = Color(0xFF171D19),
    surfaceContainer = Color(0xFF1B211D),
    surfaceContainerHigh = Color(0xFF252B27),
    surfaceContainerHighest = Color(0xFF303631)
)

fun widgetColors(context: Context, settings: AppSettings): WidgetColors {
    val nightMode = context.resources.configuration.uiMode and
        Configuration.UI_MODE_NIGHT_MASK
    val systemDark = nightMode == Configuration.UI_MODE_NIGHT_YES
    val dark = when (settings.themeMode) {
        ThemeMode.SYSTEM -> systemDark
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val dynamic = settings.dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val base = when {
        dynamic && dark -> dynamicDarkColorScheme(context)
        dynamic -> dynamicLightColorScheme(context)
        dark -> darkWidgetScheme
        else -> lightWidgetScheme
    }
    return paletteFromScheme(base, dark, settings.amoled)
}

private fun paletteFromScheme(
    scheme: ColorScheme,
    dark: Boolean,
    amoled: Boolean
): WidgetColors {
    val background = if (amoled && dark) Color.Black else scheme.background
    val surface = if (amoled && dark) Color(0xFF0E0E0E) else scheme.surfaceContainerLow
    val surfaceHigh = if (amoled && dark) Color(0xFF1C1C1C) else scheme.surfaceContainerHigh
    return WidgetColors(
        background = background,
        surface = surface,
        surfaceHigh = surfaceHigh,
        text = scheme.onSurface,
        subtext = scheme.onSurfaceVariant,
        primary = scheme.primary,
        onPrimary = scheme.onPrimary,
        primaryContainer = scheme.primaryContainer,
        onPrimaryContainer = scheme.onPrimaryContainer,
        tertiary = scheme.tertiary,
        tertiaryContainer = scheme.tertiaryContainer,
        onTertiaryContainer = scheme.onTertiaryContainer,
        success = if (dark) Color(0xFF8BD5AC) else Color(0xFF2C6B4B),
        error = scheme.error,
        outline = if (amoled && dark) Color(0xFF303631) else scheme.outlineVariant,
        isDark = dark
    )
}

internal val WidgetFont: FontFamily = FontFamily("sans-serif-rounded")

internal fun widgetTextStyle(
    color: Color,
    size: TextUnit,
    weight: FontWeight = FontWeight.Normal,
    align: TextAlign? = null
): TextStyle = TextStyle(
    color = ColorProvider(color),
    fontSize = size,
    fontWeight = weight,
    textAlign = align,
    fontFamily = WidgetFont
)

@Composable
internal fun WidgetText(
    text: String,
    color: Color,
    size: TextUnit,
    weight: FontWeight = FontWeight.Normal,
    modifier: GlanceModifier = GlanceModifier,
    maxLines: Int = 1,
    align: TextAlign? = null
) {
    Text(
        text = text,
        modifier = modifier,
        style = widgetTextStyle(color, size, weight, align),
        maxLines = maxLines
    )
}

@Composable
internal fun WidgetIcon(
    @DrawableRes icon: Int,
    tint: Color,
    size: Dp = 18.dp
) {
    Image(
        provider = ImageProvider(icon),
        contentDescription = null,
        modifier = GlanceModifier.size(size),
        colorFilter = ColorFilter.tint(ColorProvider(tint))
    )
}

@Composable
internal fun GritWidgetRoot(
    colors: WidgetColors,
    dense: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(colors.background)
            .cornerRadius(if (dense) 22.dp else 26.dp)
            .padding(
                horizontal = if (dense) 12.dp else 15.dp,
                vertical = if (dense) 10.dp else 14.dp
            ),
        verticalAlignment = Alignment.Top,
        horizontalAlignment = Alignment.Start,
        content = content
    )
}

@Composable
internal fun WidgetHeader(
    @DrawableRes icon: Int,
    title: String,
    subtitle: String,
    colors: WidgetColors,
    trailing: String? = null,
    dense: Boolean = false
) {
    Row(
        modifier = GlanceModifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = GlanceModifier
                .size(if (dense) 28.dp else 34.dp)
                .cornerRadius(if (dense) 10.dp else 12.dp)
                .background(colors.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            WidgetIcon(icon, colors.onPrimaryContainer, if (dense) 15.dp else 18.dp)
        }
        Spacer(GlanceModifier.width(if (dense) 7.dp else 9.dp))
        Column(modifier = GlanceModifier.defaultWeight()) {
            WidgetText(
                text = title,
                color = colors.text,
                size = if (dense) 14.sp else 16.sp,
                weight = FontWeight.Bold
            )
            if (subtitle.isNotBlank()) {
                WidgetText(
                    text = subtitle,
                    color = colors.subtext,
                    size = if (dense) 9.sp else 10.5.sp,
                    maxLines = 2
                )
            }
        }
        if (!trailing.isNullOrBlank()) {
            WidgetText(
                text = trailing,
                color = colors.primary,
                size = if (dense) 9.5.sp else 11.sp,
                weight = FontWeight.Bold,
                align = TextAlign.End
            )
        }
    }
}

@Composable
internal fun WidgetProgress(
    progress: Float,
    colors: WidgetColors,
    modifier: GlanceModifier = GlanceModifier
) {
    LinearProgressIndicator(
        progress = progress.coerceIn(0f, 1f),
        modifier = modifier.fillMaxWidth().height(6.dp).cornerRadius(3.dp),
        color = ColorProvider(colors.primary),
        backgroundColor = ColorProvider(colors.surfaceHigh)
    )
}

@Composable
internal fun WidgetCheck(
    done: Boolean,
    colors: WidgetColors,
    size: Dp = 23.dp
) {
    Box(
        modifier = GlanceModifier
            .size(size)
            .cornerRadius(8.dp)
            .background(if (done) colors.primary else colors.surfaceHigh),
        contentAlignment = Alignment.Center
    ) {
        if (done) {
            WidgetIcon(
                icon = com.gritnative.app.R.drawable.ic_widget_check,
                tint = colors.onPrimary,
                size = 15.dp
            )
        }
    }
}

@Composable
internal fun WidgetDivider(colors: WidgetColors) {
    Spacer(
        GlanceModifier
            .fillMaxWidth()
            .height(1.dp)
            .background(colors.outline.copy(alpha = 0.45f))
    )
}

@Composable
internal fun WidgetTextAction(
    label: String,
    @DrawableRes icon: Int,
    colors: WidgetColors,
    action: Action
) {
    Row(
        modifier = GlanceModifier
            .height(32.dp)
            .cornerRadius(16.dp)
            .background(colors.surfaceHigh)
            .clickable(action)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        WidgetIcon(icon, colors.primary, 15.dp)
        Spacer(GlanceModifier.width(5.dp))
        WidgetText(label, colors.text, 10.5.sp, FontWeight.Medium)
    }
}

@Composable
internal fun WidgetIconAction(
    @DrawableRes icon: Int,
    colors: WidgetColors,
    action: Action,
    modifier: GlanceModifier = GlanceModifier,
    background: Color = colors.surfaceHigh,
    tint: Color = colors.primary,
    size: Dp = 32.dp
) {
    Box(
        modifier = modifier
            .size(size)
            .cornerRadius(size / 2)
            .background(background)
            .clickable(action),
        contentAlignment = Alignment.Center
    ) {
        WidgetIcon(icon, tint, size * 0.46f)
    }
}

@Composable
internal fun WidgetPrimaryAction(
    label: String,
    @DrawableRes icon: Int,
    colors: WidgetColors,
    action: Action,
    height: Dp = 34.dp
) {
    Row(
        modifier = GlanceModifier
            .height(height)
            .cornerRadius(height / 2)
            .background(colors.primary)
            .clickable(action)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        WidgetIcon(icon, colors.onPrimary, 15.dp)
        Spacer(GlanceModifier.width(5.dp))
        WidgetText(
            text = label,
            color = colors.onPrimary,
            size = 10.5.sp,
            weight = FontWeight.Bold
        )
    }
}

@Composable
internal fun WidgetStat(
    label: String,
    value: String,
    colors: WidgetColors,
    modifier: GlanceModifier = GlanceModifier,
    valueSize: TextUnit = 17.sp
) {
    Column(
        modifier = modifier
            .cornerRadius(15.dp)
            .background(colors.surface)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.Top
    ) {
        WidgetText(label, colors.subtext, 9.5.sp)
        Spacer(GlanceModifier.height(2.dp))
        WidgetText(value, colors.text, valueSize, FontWeight.Bold)
    }
}

@Composable
internal fun WidgetBarChart(
    values: List<Float>,
    labels: List<String>,
    todayIndex: Int,
    colors: WidgetColors,
    modifier: GlanceModifier = GlanceModifier,
    chartHeight: Dp = 62.dp
) {
    val max = values.maxOrNull()?.coerceAtLeast(1f) ?: 1f
    val maxBar = (chartHeight.value - 25f).coerceAtLeast(8f)
    Row(
        modifier = modifier.fillMaxWidth().height(chartHeight),
        verticalAlignment = Alignment.Bottom
    ) {
        values.forEachIndexed { index, value ->
            val active = index == todayIndex
            val ratio = (value / max).coerceIn(0f, 1f)
            Column(
                modifier = GlanceModifier
                    .defaultWeight()
                    .height(chartHeight),
                verticalAlignment = Alignment.Bottom,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = GlanceModifier
                        .width(11.dp)
                        .height((5f + ratio * maxBar).dp)
                        .cornerRadius(4.dp)
                        .background(
                            when {
                                active -> colors.primary
                                value > 0f -> colors.primaryContainer
                                else -> colors.surfaceHigh
                            }
                        )
                ) { }
                Spacer(GlanceModifier.height(4.dp))
                WidgetText(
                    text = labels.getOrElse(index) { "" },
                    color = if (active) colors.primary else colors.subtext,
                    size = 9.sp,
                    weight = if (active) FontWeight.Bold else FontWeight.Normal
                )
            }
            if (index != values.lastIndex) {
                Spacer(GlanceModifier.width(2.dp))
            }
        }
    }
}
