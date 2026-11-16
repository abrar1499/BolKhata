package com.bolkhata.app

import android.content.Context
import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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
import org.json.JSONObject
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * The app's own words in the person's language: the side menu's Language, or the phone's language on Auto. Each
 * language is a table in assets/i18n (English → its words), made by tools/i18n.py; a line with {0} in it is a pattern
 * ("Already in {0}"), its parts put in their own words too. A word with no entry stays in English.
 */
object I18n {
    /** The languages the app's words come in, besides English. */
    val CODES = listOf("ur", "hi", "ar", "bn", "es", "fr", "de", "pt", "tr")
    private val RTL = setOf("ar", "ur", "fa", "he")

    /** The language shown now ("en", "ur"…); reading it makes a screen redraw when it changes. */
    var code by mutableStateOf("en")
        private set
    @Volatile private var table: Map<String, String> = emptyMap()
    @Volatile private var patterns: List<Pair<Regex, String>> = emptyList()
    private val done = ConcurrentHashMap<String, String>()

    /** Written right to left (Arabic, Urdu). */
    val rtl get() = code in RTL

    /** The app's language for the Language setting [lang] ("" Auto: the phone's; "hi-IN" is Roman Urdu, in English letters). */
    fun of(lang: String): String {
        val base = when {
            lang.isBlank() -> Locale.getDefault().language
            lang.equals("hi-IN", true) -> "en"
            else -> lang.substringBefore('-').lowercase(Locale.ROOT)
        }
        return if (base in CODES) base else "en"
    }

    /** Shows the app in the language of the setting [lang]. */
    fun use(context: Context, lang: String) {
        val c = of(lang)
        if (c == code) return
        val map = if (c == "en") emptyMap() else runCatching {
            val o = JSONObject(context.assets.open("i18n/$c.json").bufferedReader().use { it.readText() })
            o.keys().asSequence().associateWith { o.optString(it) }.filterValues { it.isNotBlank() }
        }.getOrDefault(emptyMap())
        table = map
        patterns = map.filterKeys { "{0}" in it }.map { (k, v) -> pattern(k) to v }
        done.clear()
        code = c
    }

    /** "Already in {0}" as a pattern whose {n} take any words. */
    internal fun pattern(key: String): Regex =
        Regex("^" + Regex("""\{(\d)\}""").split(key).joinToString("(.+?)") { Regex.escape(it) } + "$", RegexOption.DOT_MATCHES_ALL)

    /** [en] in the app's language: its own entry, else a pattern it fits (its parts in the language too), else as it is. */
    fun t(en: String): String {
        if (table.isEmpty() || en.isBlank()) return en
        table[en]?.let { return it }
        done[en]?.let { return it }
        val out = patterns.firstNotNullOfOrNull { (re, to) ->
            re.matchEntire(en)?.let { m ->
                m.groupValues.drop(1).foldIndexed(to) { i, s, part -> s.replace("{$i}", table[part] ?: part) }
            }
        } ?: en
        if (done.size < 5_000) done[en] = out
        return out
    }
}

/** [en] in the app's language (see [I18n]). */
fun t(en: String): String = I18n.t(en)

/**
 * Material's Text with the app's words in the person's language: every screen's Text("…") comes here (this package's
 * Text is found before the material3.* one), so nothing has to be wrapped by hand.
 */
@Composable
fun Text(
    text: String, modifier: Modifier = Modifier, color: Color = Color.Unspecified, fontSize: TextUnit = TextUnit.Unspecified,
    fontStyle: FontStyle? = null, fontWeight: FontWeight? = null, fontFamily: FontFamily? = null, letterSpacing: TextUnit = TextUnit.Unspecified,
    textDecoration: TextDecoration? = null, textAlign: TextAlign? = null, lineHeight: TextUnit = TextUnit.Unspecified,
    overflow: TextOverflow = TextOverflow.Clip, softWrap: Boolean = true, maxLines: Int = Int.MAX_VALUE, minLines: Int = 1,
    onTextLayout: ((TextLayoutResult) -> Unit)? = null, style: TextStyle = LocalTextStyle.current
) {
    // Read here so a change of language redraws every text.
    I18n.code
    androidx.compose.material3.Text(
        text = t(text), modifier = modifier, color = color, fontSize = fontSize, fontStyle = fontStyle, fontWeight = fontWeight,
        fontFamily = fontFamily, letterSpacing = letterSpacing, textDecoration = textDecoration, textAlign = textAlign,
        lineHeight = lineHeight, overflow = overflow, softWrap = softWrap, maxLines = maxLines, minLines = minLines,
        onTextLayout = onTextLayout, style = style
    )
}
