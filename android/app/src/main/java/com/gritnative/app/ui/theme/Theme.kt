package com.gritnative.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.gritnative.app.R
import com.gritnative.app.data.Category
import com.gritnative.app.data.Tag
import com.gritnative.app.data.ThemeMode
import com.gritnative.app.data.AppSettings
import java.util.Locale
import kotlin.math.abs
import kotlin.math.pow

/** Titan One is the app-wide default; the phone's own face is the option. */
val GritDisplayFamily: FontFamily = FontFamily(Font(R.font.titan_one))

private val GritDisplayTypography = Typography(fontFamily = GritDisplayFamily)
private val GritSystemTypography = Typography(fontFamily = FontFamily.Default)

private val GritShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
    largeIncreased = RoundedCornerShape(28.dp),
    extraLargeIncreased = RoundedCornerShape(36.dp),
    extraExtraLarge = RoundedCornerShape(40.dp)
)

@Immutable
data class GritExtraColors(
    val success: Color,
    val onSuccess: Color,
    val successContainer: Color,
    val onSuccessContainer: Color,
    val field: Color,
    val fieldHover: Color,
    val subtext: Color,
    val outline: Color,
    val outlineStrong: Color,
    val isDark: Boolean
)

val LocalGritColors = staticCompositionLocalOf {
    GritExtraColors(
        success = Color(0xFF2E6B4F),
        onSuccess = Color.White,
        successContainer = Color(0xFFB7EFD0),
        onSuccessContainer = Color(0xFF06301C),
        field = Color(0xFFECE6EE),
        fieldHover = Color(0xFFE3DCE6),
        subtext = Color(0xFF807583),
        outline = Color(0xFFD1C7D3),
        outlineStrong = Color(0xFF7E7380),
        isDark = false
    )
}

object Grit {
    val colors: GritExtraColors
        @Composable get() = LocalGritColors.current
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun GritTheme(
    settings: AppSettings,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val systemDark = isSystemInDarkTheme()
    val dark = when (settings.themeMode) {
        ThemeMode.SYSTEM -> systemDark
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val dynamicProfile = settings.colorProfile == "dynamic" &&
        settings.dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val base = when {
        dynamicProfile -> if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> expressiveDarkScheme()
        else -> expressiveLightScheme()
    }
    val profiled = if (dynamicProfile) base else applyColorProfile(base, settings.colorProfile, dark)
    val scheme = if (settings.amoled && dark) profiled.amoled() else profiled
    val extras = if (dark) darkExtras(scheme, settings.amoled) else lightExtras(scheme)
    val typography = if (settings.appFont == "system") GritSystemTypography
    else GritDisplayTypography
    // A single comfortable text scale for the whole app instead of touching
    // dozens of individual sizes.
    val baseDensity = LocalDensity.current
    val roomyDensity = Density(baseDensity.density, baseDensity.fontScale * 1.1f)

    CompositionLocalProvider(
        LocalGritColors provides extras,
        LocalDensity provides roomyDensity
    ) {
        MaterialExpressiveTheme(
            colorScheme = scheme,
            motionScheme = MotionScheme.expressive(),
            shapes = GritShapes,
            typography = typography,
            content = content
        )
    }
}

private data class ColorProfile(
    val primary: Long, val onPrimary: Long, val primaryContainer: Long, val onPrimaryContainer: Long,
    val secondary: Long, val onSecondary: Long, val secondaryContainer: Long, val onSecondaryContainer: Long,
    val tertiary: Long, val onTertiary: Long, val tertiaryContainer: Long, val onTertiaryContainer: Long
)

private val LIGHT_PROFILES = mapOf(
    "ocean" to ColorProfile(0xFF2F5D8C, 0xFFFFFFFF, 0xFFD3E4FA, 0xFF0A2138, 0xFF4B6078, 0xFFFFFFFF, 0xFFD8E3F0, 0xFF0D2233, 0xFF2E6F72, 0xFFFFFFFF, 0xFFC8ECEE, 0xFF063234),
    "amber" to ColorProfile(0xFF7A5A11, 0xFFFFFFFF, 0xFFF8E2B0, 0xFF3A2A00, 0xFF6D5B3F, 0xFFFFFFFF, 0xFFF0E1C8, 0xFF2A1D08, 0xFF4F6450, 0xFFFFFFFF, 0xFFD6E8D7, 0xFF10250F),
    "panadoro" to ColorProfile(0xFFC92001, 0xFFFFFFFF, 0xFFC92001, 0xFFFFFFFF, 0xFF207402, 0xFFFFFFFF, 0xFF207402, 0xFFFFFFFF, 0xFFFAA700, 0xFF2B1A00, 0xFFFAA700, 0xFF2B1A00),
    "rose" to ColorProfile(0xFF8C3B52, 0xFFFFFFFF, 0xFFFFD9E2, 0xFF3B0718, 0xFF74565E, 0xFFFFFFFF, 0xFFFFD9E2, 0xFF2E111A, 0xFF82643D, 0xFFFFFFFF, 0xFFF5DFB8, 0xFF2D1E00),
    "violet" to ColorProfile(0xFF63558C, 0xFFFFFFFF, 0xFFE8DEFF, 0xFF1A0F3D, 0xFF625A70, 0xFFFFFFFF, 0xFFE8DFF2, 0xFF1D1628, 0xFF7A5868, 0xFFFFFFFF, 0xFFFFD8E8, 0xFF33101F),
    "slate" to ColorProfile(0xFF4A5C66, 0xFFFFFFFF, 0xFFCDE7F4, 0xFF071E28, 0xFF50606A, 0xFFFFFFFF, 0xFFD5E5EC, 0xFF0B1F27, 0xFF5D5B7A, 0xFFFFFFFF, 0xFFE4E0F5, 0xFF1C1838)
)

private val DARK_PROFILES = mapOf(
    "ocean" to ColorProfile(0xFFA8C8F0, 0xFF0D2A46, 0xFF2B4765, 0xFFD3E4FA, 0xFFB8C7DA, 0xFF243342, 0xFF39485B, 0xFFD8E3F0, 0xFF96D2D5, 0xFF063234, 0xFF235053, 0xFFC8ECEE),
    "amber" to ColorProfile(0xFFE8C36A, 0xFF3A2A00, 0xFF5B471C, 0xFFF8E2B0, 0xFFD6C5A8, 0xFF3A2D1A, 0xFF4C3F2B, 0xFFF0E1C8, 0xFFB5CDB7, 0xFF10250F, 0xFF33452F, 0xFFD6E8D7),
    "panadoro" to ColorProfile(0xFFC92001, 0xFFFFFFFF, 0xFFC92001, 0xFFFFFFFF, 0xFF207402, 0xFFFFFFFF, 0xFF207402, 0xFFFFFFFF, 0xFFFAA700, 0xFF2B1A00, 0xFFFAA700, 0xFF2B1A00),
    "rose" to ColorProfile(0xFFFFB1C8, 0xFF3B0718, 0xFF6E2942, 0xFFFFD9E2, 0xFFE3BDC6, 0xFF422A31, 0xFF5A3C44, 0xFFFFD9E2, 0xFFE5C48D, 0xFF3B2A07, 0xFF5B4522, 0xFFF5DFB8),
    "violet" to ColorProfile(0xFFCBBEFF, 0xFF1A0F3D, 0xFF4A3D73, 0xFFE8DEFF, 0xFFCBC2DA, 0xFF332C3E, 0xFF473F55, 0xFFE8DFF2, 0xFFE5B9CB, 0xFF3B1022, 0xFF5D3C4C, 0xFFFFD8E8),
    "slate" to ColorProfile(0xFFB1CBD8, 0xFF071E28, 0xFF324852, 0xFFCDE7F4, 0xFFB8C9D2, 0xFF1C3039, 0xFF394A52, 0xFFD5E5EC, 0xFFC6C2E0, 0xFF1C1838, 0xFF45435F, 0xFFE4E0F5)
)

private fun applyColorProfile(base: ColorScheme, profile: String, dark: Boolean): ColorScheme {
    val value = (if (dark) DARK_PROFILES else LIGHT_PROFILES)[profile] ?: return base
    return base.copy(
        primary = Color(value.primary),
        onPrimary = Color(value.onPrimary),
        primaryContainer = Color(value.primaryContainer),
        onPrimaryContainer = Color(value.onPrimaryContainer),
        secondary = Color(value.secondary),
        onSecondary = Color(value.onSecondary),
        secondaryContainer = Color(value.secondaryContainer),
        onSecondaryContainer = Color(value.onSecondaryContainer),
        tertiary = Color(value.tertiary),
        onTertiary = Color(value.onTertiary),
        tertiaryContainer = Color(value.tertiaryContainer),
        onTertiaryContainer = Color(value.onTertiaryContainer),
        inversePrimary = Color(value.primary)
    )
}

private fun expressiveLightScheme(): ColorScheme = lightColorScheme(
    primary = Color(0xFF3E6654),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFC0ECD5),
    onPrimaryContainer = Color(0xFF002115),
    secondary = Color(0xFF4E6358),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD1E8DA),
    onSecondaryContainer = Color(0xFF0B1F16),
    tertiary = Color(0xFF755B2F),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFE0A6),
    onTertiaryContainer = Color(0xFF281900),
    error = Color(0xFFBA1A1A),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFF6FBF5),
    onBackground = Color(0xFF171D19),
    surface = Color(0xFFF6FBF5),
    onSurface = Color(0xFF171D19),
    surfaceVariant = Color(0xFFDCE5DC),
    onSurfaceVariant = Color(0xFF404943),
    outline = Color(0xFF707972),
    outlineVariant = Color(0xFFC0C9C1),
    inverseSurface = Color(0xFF2C322E),
    inverseOnSurface = Color(0xFFEDF2EC),
    inversePrimary = Color(0xFFA4D0BA),
    surfaceDim = Color(0xFFD6DBD6),
    surfaceBright = Color(0xFFF6FBF5),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF0F5EF),
    surfaceContainer = Color(0xFFEAEFE9),
    surfaceContainerHigh = Color(0xFFE4EAE4),
    surfaceContainerHighest = Color(0xFFDEE4DE)
)

private fun expressiveDarkScheme(): ColorScheme = darkColorScheme(
    primary = Color(0xFFA4D0BA),
    onPrimary = Color(0xFF0B3728),
    primaryContainer = Color(0xFF254E3D),
    onPrimaryContainer = Color(0xFFC0ECD5),
    secondary = Color(0xFFB5CCBF),
    onSecondary = Color(0xFF21352A),
    secondaryContainer = Color(0xFF374B40),
    onSecondaryContainer = Color(0xFFD1E8DA),
    tertiary = Color(0xFFE4C27C),
    onTertiary = Color(0xFF402D04),
    tertiaryContainer = Color(0xFF5A4318),
    onTertiaryContainer = Color(0xFFFFE0A6),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF0F1511),
    onBackground = Color(0xFFDEE4DE),
    surface = Color(0xFF0F1511),
    onSurface = Color(0xFFDEE4DE),
    surfaceVariant = Color(0xFF404943),
    onSurfaceVariant = Color(0xFFC0C9C1),
    outline = Color(0xFF8A938B),
    outlineVariant = Color(0xFF404943),
    inverseSurface = Color(0xFFDEE4DE),
    inverseOnSurface = Color(0xFF2C322E),
    inversePrimary = Color(0xFF3E6654),
    surfaceDim = Color(0xFF0F1511),
    surfaceBright = Color(0xFF353B36),
    surfaceContainerLowest = Color(0xFF0A0F0C),
    surfaceContainerLow = Color(0xFF171D19),
    surfaceContainer = Color(0xFF1B211D),
    surfaceContainerHigh = Color(0xFF252B27),
    surfaceContainerHighest = Color(0xFF303631)
)

private fun ColorScheme.amoled(): ColorScheme = copy(
    background = Color.Black,
    surface = Color.Black,
    surfaceDim = Color.Black,
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = Color(0xFF090909),
    surfaceContainer = Color(0xFF0E0E0E),
    surfaceContainerHigh = Color(0xFF151515),
    surfaceContainerHighest = Color(0xFF1C1C1C),
    onBackground = Color(0xFFE4E4E4),
    onSurface = Color(0xFFE4E4E4)
)

private fun lightExtras(scheme: ColorScheme): GritExtraColors = GritExtraColors(
    success = Color(0xFF2C6B4B),
    onSuccess = Color.White,
    successContainer = Color(0xFFB4F1CE),
    onSuccessContainer = Color(0xFF002114),
    field = scheme.surfaceContainerHighest,
    fieldHover = scheme.surfaceContainerHigh,
    subtext = scheme.onSurfaceVariant.copy(alpha = 0.72f),
    outline = scheme.outlineVariant,
    outlineStrong = scheme.outline,
    isDark = false
)

private fun darkExtras(scheme: ColorScheme, amoled: Boolean): GritExtraColors = GritExtraColors(
    success = Color(0xFF8BD5AC),
    onSuccess = Color(0xFF003821),
    successContainer = Color(0xFF1E5240),
    onSuccessContainer = Color(0xFFB4F1CE),
    field = if (amoled) Color(0xFF1C1C1C) else scheme.surfaceContainerHighest,
    fieldHover = if (amoled) Color(0xFF252525) else scheme.surfaceContainerHigh,
    subtext = scheme.onSurfaceVariant.copy(alpha = 0.72f),
    outline = scheme.outlineVariant,
    outlineStrong = scheme.outline,
    isDark = true
)

// ── Tag / category accent resolution ────────────────────────────────────

@Immutable
data class TagColors(
    val accent: Color,
    val container: Color,
    val onContainer: Color,
    val onSolid: Color,
    val softContainer: Color
)

@Composable
fun tagColors(tag: Tag?): TagColors {
    val scheme = MaterialTheme.colorScheme
    val custom = parseHexColor(tag?.color)
    if (custom != null) {
        val container = lerpColor(scheme.surfaceContainerLow, custom, 0.24f)
        return TagColors(
            accent = custom,
            container = container,
            onContainer = mostReadable(container, listOf(scheme.onSurface, custom)),
            onSolid = mostReadable(custom, listOf(Color.White, Color(0xFF17130F))),
            softContainer = lerpColor(scheme.surfaceContainerLow, custom, 0.13f)
        )
    }
    val base = accentColors(tag?.accent ?: "primary")
    return TagColors(
        accent = base.accent,
        container = base.container,
        onContainer = base.onContainer,
        onSolid = base.onSolid,
        softContainer = lerpColor(scheme.surfaceContainerLow, base.accent, 0.13f)
    )
}

@Immutable
data class AccentColors(
    val accent: Color,
    val container: Color,
    val onContainer: Color,
    val onSolid: Color
)

@Composable
fun accentColors(name: String?): AccentColors {
    val scheme = MaterialTheme.colorScheme
    val extras = Grit.colors
    return when (name) {
        "secondary" -> AccentColors(
            scheme.secondary, scheme.secondaryContainer, scheme.onSecondaryContainer, scheme.onSecondary
        )
        "tertiary" -> AccentColors(
            scheme.tertiary, scheme.tertiaryContainer, scheme.onTertiaryContainer, scheme.onTertiary
        )
        "success" -> AccentColors(
            extras.success, extras.successContainer, extras.onSuccessContainer, extras.onSuccess
        )
        "error" -> AccentColors(
            scheme.error, scheme.errorContainer, scheme.onErrorContainer, scheme.onError
        )
        "outline" -> AccentColors(
            scheme.onSurfaceVariant, extras.field, scheme.onSurface, scheme.surface
        )
        else -> AccentColors(
            scheme.primary, scheme.primaryContainer, scheme.onPrimaryContainer, scheme.onPrimary
        )
    }
}

@Composable
fun categoryColors(category: Category?): AccentColors = accentColors(category?.accent ?: "outline")

fun parseHexColor(value: String?): Color? {
    val text = value?.trim().orEmpty()
    if (!text.startsWith("#")) return null
    val hex = text.substring(1)
    if (hex.length != 6 && hex.length != 8) return null
    return runCatching {
        val parsed = android.graphics.Color.parseColor(text)
        Color(parsed)
    }.getOrNull()
}

fun isHexColor(value: String?): Boolean {
    val text = value?.trim().orEmpty()
    return text.matches(Regex("^#[0-9a-fA-F]{6}([0-9a-fA-F]{2})?$"))
}

fun lerpColor(start: Color, end: Color, fraction: Float): Color {
    val f = fraction.coerceIn(0f, 1f)
    return Color(
        red = start.red + (end.red - start.red) * f,
        green = start.green + (end.green - start.green) * f,
        blue = start.blue + (end.blue - start.blue) * f,
        alpha = start.alpha + (end.alpha - start.alpha) * f
    )
}

fun mostReadable(background: Color, candidates: List<Color>): Color {
    val bg = background.luminance()
    return candidates.maxByOrNull { candidate ->
        val fg = candidate.luminance()
        val light = (maxOf(bg, fg) + 0.05f) / (minOf(bg, fg) + 0.05f)
        val dark = (minOf(bg, fg) + 0.05f) / (maxOf(bg, fg) + 0.05f)
        if (bg > 0.35f) dark else light
    } ?: Color.White
}

fun durationShort(minutes: Int): String {
    val value = minutes.coerceAtLeast(0)
    if (value < 60) return "${value}m"
    val hours = value / 60
    val rest = value % 60
    return if (rest == 0) "${hours}h" else "${hours}h ${rest}m"
}

fun clockText(seconds: Int): String {
    val total = seconds.coerceAtLeast(0)
    val hours = total / 3600
    val minutes = (total % 3600) / 60
    val rest = total % 60
    return if (hours > 0) String.format(Locale.US, "%d:%02d:%02d", hours, minutes, rest)
    else String.format(Locale.US, "%02d:%02d", minutes, rest)
}
