package com.gritnative.app.ui.components

import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text as MaterialText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit

/** True while the in-app language is Arabic. Provided once by MainActivity. */
val LocalArabic = staticCompositionLocalOf { false }

/** Global flag for non-composable copy such as notifications. */
object AppLanguage {
    var isArabic by mutableStateOf(false)
}

/** "45m" → "45 د", "1h" → "1 س", "1h 30m" → "1 س 30 د". */
private fun translateDuration(text: String): String = when {
    Regex("""^(\d+)h (\d+)m$""").matches(text) ->
        text.replace(Regex("""^(\d+)h (\d+)m$"""), "$1 س $2 د")
    Regex("""^(\d+)m$""").matches(text) -> text.replace(Regex("""^(\d+)m$"""), "$1 د")
    Regex("""^(\d+)h$""").matches(text) -> text.replace(Regex("""^(\d+)h$"""), "$1 س")
    else -> text
}

private val NUMBER_PATTERNS: List<Pair<Regex, (MatchResult) -> String>> = listOf(
    // Greetings arrive with a trailing period (or the "all done" suffix).
    Regex("""^(Good morning|Good afternoon|Good evening|Still up\?)\.$""") to {
        translateText(it.groupValues[1]) + "."
    },
    Regex("""^(Good morning|Good afternoon|Good evening), all done\.$""") to {
        translateText(it.groupValues[1]) + "، كل شيء منجز."
    },
    Regex("""^(\d+) of (\d+) habits · (.+) focused today$""") to {
        "${it.groupValues[1]} من ${it.groupValues[2]} عادات · " +
            "${translateDuration(it.groupValues[3])} تركيز اليوم"
    },
    Regex("""^(\d+) open · (\d+) due today · (\d+) done$""") to {
        "${it.groupValues[1]} مفتوحة · ${it.groupValues[2]} مستحقة اليوم · ${it.groupValues[3]} منتهية"
    },
    Regex("""^(\d+) open · (\d+) due today$""") to {
        "${it.groupValues[1]} مفتوحة · ${it.groupValues[2]} مستحقة اليوم"
    },
    Regex("""^([\dhm ]+?) open$""") to {
        "${translateDuration(it.groupValues[1])} مفتوحة"
    },
    Regex("""^(\d+) of (\d+) habits$""") to { "${it.groupValues[1]} من ${it.groupValues[2]} عادات" },
    Regex("""^(\d+) accounts?$""") to { "${it.groupValues[1]} حساب" },
    Regex("""^(\d+) public tags? · (today|this week|this month)$""") to {
        val whenText = when (it.groupValues[2]) {
            "today" -> "اليوم"
            "this month" -> "هذا الشهر"
            else -> "هذا الأسبوع"
        }
        "${it.groupValues[1]} وسم عام · $whenText"
    },
    Regex("""^(\d+) of (\d+) tags public · habits, tasks and notes are never uploaded to the board\.$""") to {
        "${it.groupValues[1]} من ${it.groupValues[2]} وسوم عامة · لا تُرفع العادات أو المهام أو الملاحظات إلى اللوحة."
    },
    Regex("""^(.+) · one rhythm, (\d+) days$""") to {
        "${it.groupValues[1]} · إيقاع واحد، ${it.groupValues[2]} أيام"
    },
    Regex("""^(.+) · (\d+)m focus · (\d+) cycles$""") to {
        "${it.groupValues[1]} · ${it.groupValues[2]} د تركيز · ${it.groupValues[3]} دورات"
    },
    // The timer's plan line, in both of its layouts.
    Regex("""^([\dhm ]+?) focus · ([\dhm ]+?) break · (\d+) cycles · ([\dhm ]+?) goal$""") to {
        "${translateDuration(it.groupValues[1])} تركيز · ${translateDuration(it.groupValues[2])} استراحة · " +
            "${it.groupValues[3]} دورات · ${translateDuration(it.groupValues[4])} هدف"
    },
    Regex("""^([\dhm ]+?) focus · ([\dhm ]+?) break · (\d+) cycles · goal off$""") to {
        "${translateDuration(it.groupValues[1])} تركيز · ${translateDuration(it.groupValues[2])} استراحة · " +
            "${it.groupValues[3]} دورات · الهدف معطّل"
    },
    Regex("""^([\dhm ]+?) focus · ([\dhm ]+?) break · (\d+) cycles$""") to {
        "${translateDuration(it.groupValues[1])} تركيز · ${translateDuration(it.groupValues[2])} استراحة · " +
            "${it.groupValues[3]} دورات"
    },
    Regex("""^of ([\dhm ]+?)$""") to { "من ${translateDuration(it.groupValues[1])}" },
    // Durations shown on their own.
    Regex("""^(\d+)h (\d+)m$""") to { translateDuration(it.groupValues[0]) },
    Regex("""^(\d+)m$""") to { translateDuration(it.groupValues[0]) },
    Regex("""^(\d+)h$""") to { translateDuration(it.groupValues[0]) },
    Regex("""^([\dhm ]+?) break time$""") to { "${translateDuration(it.groupValues[1])} استراحة" },
    Regex("""^([\dhm ]+?) per day$""") to { "${translateDuration(it.groupValues[1])} يوميًا" },
    Regex("""^([\dhm ]+?) / ([\dhm ]+?) today$""") to {
        "${translateDuration(it.groupValues[1])} / ${translateDuration(it.groupValues[2])} اليوم"
    },
    Regex("""^([\dhm ]+?) focused today$""") to { "${translateDuration(it.groupValues[1])} تركيز اليوم" },
    Regex("""^(.+) focused across (\d+) days$""") to {
        "${translateDuration(it.groupValues[1])} تركيز خلال ${it.groupValues[2]} يوم"
    },
    Regex("""^(.+)% of focus · ([\dhm ]+?)$""") to {
        "${it.groupValues[1]}% من التركيز · ${translateDuration(it.groupValues[2])}"
    },
    Regex("""^break time in (\d+) days$""") to { "استراحة خلال ${it.groupValues[1]} أيام" },
    Regex("""^focused in (\d+) days · best (.+)$""") to {
        "تركيز خلال ${it.groupValues[1]} أيام · الأفضل ${it.groupValues[2]}"
    },
    Regex("""^focused in (\d+) days · no sessions yet$""") to {
        "تركيز خلال ${it.groupValues[1]} أيام · لا جلسات بعد"
    },
    Regex("""^Rhythm · (\d+) days$""") to { "الإيقاع · ${it.groupValues[1]} أيام" },
    Regex("""^peak (.+)$""") to { "الذروة ${it.groupValues[1]}" },
    Regex("""^(\d+)/(\d+) due$""") to { "${it.groupValues[1]}/${it.groupValues[2]} مستحقة" },
    Regex("""^(\d+) overdue$""") to { "${it.groupValues[1]} متأخرة" },
    Regex("""^(Morning|Afternoon|Evening|Night)  (.+)$""") to {
        "${translateText(it.groupValues[1])}  ${it.groupValues[2]}"
    },
    Regex("""^(\d+) day streak$""") to {
        val days = it.groupValues[1]
        if (days == "1") "حماسة يوم واحد" else "حماسة $days أيام"
    },
    Regex("""^(.+) day streak$""") to { "حماسة ${it.groupValues[1]}" },
    Regex("""^(\d+) sessions$""") to { "${it.groupValues[1]} جلسات" },
    Regex("""^(\d+) of (\d+) (times|glasses|blocks|pages|minutes)$""") to {
        val unit = when (it.groupValues[3]) {
            "times" -> "مرات"
            "glasses" -> "أكواب"
            "blocks" -> "فترات"
            "pages" -> "صفحات"
            "minutes" -> "دقائق"
            else -> it.groupValues[3]
        }
        "${it.groupValues[1]} من ${it.groupValues[2]} $unit"
    },
    // Composite rows around a dynamic middle (names, dates, counts).
    Regex("""^Overdue · (.+)$""") to { "متأخرة · ${translateText(it.groupValues[1])}" },
    Regex("""^To-do · (.+)$""") to { "مهمة · ${it.groupValues[1]}" },
    Regex("""^Last sync (.+)$""") to { "آخر مزامنة ${it.groupValues[1]}" },
    Regex("""^Device (.+)$""") to { "الجهاز ${it.groupValues[1]}" },
    Regex("""^Panadoro (.+)$""") to { "Panadoro ${it.groupValues[1]}" },
    Regex("""^Updated (.+) · pull the refresh button to update$""") to {
        "آخر تحديث ${translateText(it.groupValues[1])} · اضغط زر التحديث للتحديث"
    },
    Regex("""^(\d+) min ago$""") to { "قبل ${it.groupValues[1]} د" },
    Regex("""^(\d+)h ago$""") to { "قبل ${it.groupValues[1]} س" },
    Regex("""^All (\d+) habits done$""") to { "أُنجزت كل العادات الـ${it.groupValues[1]}" },
    Regex("""^Focus complete · (.+)$""") to { "اكتمل التركيز · ${it.groupValues[1]}" },
    Regex("""^(\d+) minute banked\. Let the rest of the day be lighter\.$""") to {
        "${it.groupValues[1]} دقيقة في الحساب. اجعل بقية اليوم أخف."
    },
    Regex("""^(\d+) minutes banked\. Let the rest of the day be lighter\.$""") to {
        "${it.groupValues[1]} دقائق في الحساب. اجعل بقية اليوم أخف."
    },
    Regex("""^(\d+) of (\d+) cleared for today\. Enjoy the quiet — you earned it\.$""") to {
        "أُنجز ${it.groupValues[1]} من ${it.groupValues[2]} اليوم. استمتع بالهدوء — لقد استحققته."
    },
    Regex(
        """^Habits and tasks in (.+) move to another category\. """ +
            """The deletion syncs to your other devices\.$"""
    ) to {
        "تنتقل عادات ومهام «${it.groupValues[1]}» إلى فئة أخرى. تتم مزامنة الحذف مع أجهزتك الأخرى."
    },
    Regex("""^(\d+) habits?$""") to {
        when (it.groupValues[1]) {
            "0" -> "لا عادات"
            "1" -> "عادة واحدة"
            else -> "${it.groupValues[1]} عادات"
        }
    },
    Regex("""^(\d+)% complete$""") to { "${it.groupValues[1]}% مكتمل" }
)

fun translateText(text: String, arabic: Boolean = AppLanguage.isArabic): String {
    if (!arabic) return text
    val direct = ARABIC_STRINGS[text]
    if (direct != null) return direct
    for ((pattern, render) in NUMBER_PATTERNS) {
        val match = pattern.matchEntire(text)
        if (match != null) return render(match)
    }
    return text
}

/**
 * Drop-in replacement for Material 3's Text. It translates the string content
 * while leaving icons, glyphs and text-field values untouched.
 */
@Composable
fun Text(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontStyle: FontStyle? = null,
    fontWeight: FontWeight? = null,
    fontFamily: FontFamily? = null,
    letterSpacing: TextUnit = TextUnit.Unspecified,
    textDecoration: TextDecoration? = null,
    textAlign: TextAlign? = null,
    lineHeight: TextUnit = TextUnit.Unspecified,
    overflow: TextOverflow = TextOverflow.Clip,
    softWrap: Boolean = true,
    maxLines: Int = Int.MAX_VALUE,
    minLines: Int = 1,
    onTextLayout: ((TextLayoutResult) -> Unit)? = null,
    style: TextStyle = LocalTextStyle.current
) {
    val arabic = LocalArabic.current
    MaterialText(
        text = translateText(text, arabic),
        modifier = modifier,
        color = color,
        fontSize = fontSize,
        fontStyle = fontStyle,
        fontWeight = fontWeight,
        fontFamily = fontFamily,
        letterSpacing = letterSpacing,
        textDecoration = textDecoration,
        textAlign = textAlign,
        lineHeight = lineHeight,
        overflow = overflow,
        softWrap = softWrap,
        maxLines = maxLines,
        minLines = minLines,
        onTextLayout = onTextLayout,
        style = style
    )
}
