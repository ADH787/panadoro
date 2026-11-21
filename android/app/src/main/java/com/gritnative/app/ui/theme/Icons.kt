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
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gritnative.app.R

private val outlineIcons = FontFamily(Font(R.font.material_symbols))
private val filledIcons = FontFamily(Font(R.font.material_symbols_filled))

private fun codeFor(name: String): Int = GritSymbols.byName[name] ?: GritSymbols.circle

/**
 * Per-glyph optical offsets, measured from each glyph's ink bounding box in
 * the font itself and expressed as a fraction of the icon size. The font's
 * em box is perfectly centred by the line layout, but plenty of glyphs carry
 * ink that leans to one side — the play triangle, the coffee cup, the edit
 * pen — and these nudges put that ink dead-centre in circles and badges at
 * every size and density.
 */
private val opticalOffsets: Map<String, Pair<Float, Float>> = mapOf(
    "add_task" to (-0.0406f to +0.0000f),
    "alarm" to (+0.0000f to +0.0151f),
    "arrow_back" to (-0.0099f to +0.0005f),
    "arrow_forward" to (+0.0099f to -0.0005f),
    "auto_awesome" to (-0.0078f to +0.0026f),
    "bed" to (+0.0000f to +0.0052f),
    "bedtime" to (+0.0047f to +0.0057f),
    "brush" to (+0.0182f to -0.0042f),
    "celebration" to (-0.0396f to -0.0120f),
    "checklist" to (-0.0099f to -0.0224f),
    "chevron_right" to (+0.0125f to +0.0000f),
    "cloud_off" to (+0.0000f to +0.0323f),
    "cloud_sync" to (-0.0870f to +0.0042f),
    "coffee" to (-0.0417f to +0.0000f),
    "content_copy" to (+0.0208f to +0.0000f),
    "directions_run" to (+0.0391f to +0.0068f),
    "dns" to (+0.0000f to +0.0193f),
    "edit_note" to (-0.0417f to +0.0000f),
    "expand_less" to (+0.0000f to -0.0141f),
    "expand_more" to (+0.0000f to -0.0141f),
    "favorite" to (+0.0000f to -0.0219f),
    "flag" to (-0.0208f to +0.0208f),
    "insights" to (+0.0000f to -0.0234f),
    "key" to (-0.0120f to +0.0000f),
    "label" to (+0.0115f to +0.0000f),
    "local_dining" to (+0.0005f to -0.0130f),
    "local_fire_department" to (+0.0000f to +0.0349f),
    "mindfulness" to (-0.0234f to +0.0000f),
    "monitoring" to (+0.0000f to +0.0286f),
    "nightlight" to (+0.0437f to +0.0000f),
    "person" to (+0.0000f to -0.0042f),
    "play_arrow" to (-0.0453f to +0.0000f),
    "psychology" to (-0.0234f to +0.0000f),
    "restart_alt" to (+0.0000f to -0.0089f),
    "restaurant" to (+0.0187f to +0.0000f),
    "savings" to (+0.0000f to -0.0208f),
    "school" to (-0.0255f to +0.0000f),
    "self_improvement" to (+0.0000f to -0.0057f),
    "spa" to (+0.0000f to +0.0073f),
    "timer" to (+0.0000f to -0.0167f),
    "translate" to (+0.0057f to +0.0000f),
    "upload" to (+0.0000f to +0.0099f),
    "visibility" to (+0.0000f to -0.0208f),
    "water_drop" to (+0.0000f to +0.0104f),
    "work" to (+0.0000f to -0.0245f),
)

/**
 * The font's vertical metrics: (hhea ascent + |descent|) / unitsPerEm.
 * A single-line paragraph of this font is genuinely this tall, and Compose
 * clamps negative line-height padding to zero — so a text squeezed into a
 * 1 em box anchors the glyph at its ascent and every icon renders a full
 * descent (~10% of the size) too low. [centerInIconBox] measures the
 * paragraph at its real height and centres it on the icon box instead.
 */
private const val FONT_LINE_HEIGHT_EM = 1.2f

/** Measure the text at its true height, then centre it on the icon box. */
private fun Modifier.centerInIconBox(): Modifier = layout { measurable, constraints ->
    val relaxed = constraints.copy(minWidth = 0, minHeight = 0, maxHeight = Constraints.Infinity)
    val placeable = measurable.measure(relaxed)
    layout(constraints.maxWidth, constraints.maxHeight) {
        val x = (constraints.maxWidth - placeable.width) / 2
        val y = (constraints.maxHeight - placeable.height) / 2
        placeable.place(x, y)
    }
}

/**
 * A Material Symbols Rounded glyph. The font is subset to the symbols this app
 * uses, so it stays a few kilobytes instead of a 15 MB variable font.
 *
 * The glyph's paragraph is measured at its real height and centred on the
 * icon box, and the per-glyph optical offsets above put asymmetric ink
 * dead-centre — so icons sit exactly in the middle of circular buttons at
 * any size.
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
    val nudge = opticalOffsets[name] ?: (0f to 0f)
    Box(
        modifier = modifier
            .size(size)
            .offset(x = size * nudge.first, y = size * nudge.second),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = String(Character.toChars(cp)),
            color = color,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.centerInIconBox(),
            style = TextStyle(
                fontFamily = if (filled) filledIcons else outlineIcons,
                fontSize = size.value.sp,
                lineHeight = (size.value * FONT_LINE_HEIGHT_EM).sp,
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
