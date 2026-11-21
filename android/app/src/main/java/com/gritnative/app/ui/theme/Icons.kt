package com.gritnative.app.ui.theme

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gritnative.app.R

private val outlineIcons = FontFamily(Font(R.font.material_symbols))
private val filledIcons = FontFamily(Font(R.font.material_symbols_filled))

private fun codeFor(name: String): Int = GritSymbols.byName[name] ?: GritSymbols.circle

/**
 * A few glyphs carry more ink on one side of their em box than the other, so
 * they look off-centre inside circles even when they are technically centred.
 * A tiny optical nudge makes them sit right.
 */
private val opticalNudgeX: Map<String, Dp> = mapOf(
    "play_arrow" to 1.dp,
    "skip_next" to 0.5.dp,
    "arrow_forward" to 0.5.dp,
    "chevron_right" to 0.5.dp
)

/**
 * A Material Symbols Rounded glyph. The font is subset to the symbols this app
 * uses, so it stays a few kilobytes instead of a 15 MB variable font.
 *
 * The line box is trimmed of font padding and centred on the glyph, so icons
 * sit dead-centre in circular buttons instead of riding low near the edge.
 */
@Composable
fun Symbol(
    name: String,
    modifier: Modifier = Modifier,
    size: Dp = 20.dp,
    color: Color = LocalContentColor.current,
    filled: Boolean = false
) {
    val cp = codeFor(name)
    Box(
        modifier = modifier
            .size(size)
            .offset(x = opticalNudgeX[name] ?: 0.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = String(Character.toChars(cp)),
            color = color,
            maxLines = 1,
            softWrap = false,
            style = TextStyle(
                fontFamily = if (filled) filledIcons else outlineIcons,
                fontSize = size.value.sp,
                lineHeight = size.value.sp,
                lineHeightStyle = LineHeightStyle(
                    alignment = LineHeightStyle.Alignment.Center,
                    trim = LineHeightStyle.Trim.Both
                ),
                platformStyle = PlatformTextStyle(includeFontPadding = false),
                textAlign = TextAlign.Center
            )
        )
    }
}
