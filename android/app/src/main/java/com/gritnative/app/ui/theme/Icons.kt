package com.gritnative.app.ui.theme

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gritnative.app.R

private val outlineIcons = FontFamily(Font(R.font.material_symbols))
private val filledIcons = FontFamily(Font(R.font.material_symbols_filled))

private fun codeFor(name: String): Int = GritSymbols.byName[name] ?: GritSymbols.circle

/**
 * A Material Symbols Rounded glyph. The font is subset to the symbols this app
 * uses, so it stays a few kilobytes instead of a 15 MB variable font.
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
    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
        Text(
            text = String(Character.toChars(cp)),
            color = color,
            maxLines = 1,
            softWrap = false,
            style = TextStyle(
                fontFamily = if (filled) filledIcons else outlineIcons,
                fontSize = size.value.sp,
                lineHeight = size.value.sp,
                textAlign = TextAlign.Center
            )
        )
    }
}
