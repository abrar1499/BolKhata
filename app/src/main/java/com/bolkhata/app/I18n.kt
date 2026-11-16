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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.ui.draw.clip
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

/** The app's languages as the person picks them: "" Auto (the phone's), then each by its own name. */
val APP_LANGS = listOf("" to "Auto", "en" to "English", "ur" to "اردو", "hi" to "हिन्दी", "ar" to "العربية", "bn" to "বাংলা",
    "es" to "Español", "fr" to "Français", "de" to "Deutsch", "pt" to "Português", "tr" to "Türkçe")

/** How the 🌐 pill changes the money and the app's language; MainActivity hands these over. */
object LangMoney {
    var onCurrency: (String) -> Unit = {}
    var onAppLang: (String) -> Unit = {}
}

/**
 * The small white "🌐 SAR" pill at the top right of every tab, as the mock: a compact list opens under it, over the
 * page, with Language and Currency at its top; picking one closes it.
 */
@Composable
fun LangMoneyPill() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val currency = LocalCurrency.current
    var open by androidx.compose.runtime.remember { mutableStateOf(false) }
    var tab by androidx.compose.runtime.remember { androidx.compose.runtime.mutableIntStateOf(0) }
    var q by androidx.compose.runtime.remember { mutableStateOf("") }
    val sub = Color(0xFF8A8580)
    val red = Color(0xFFE3342F)
    androidx.compose.foundation.layout.Box {
        androidx.compose.foundation.layout.Row(
            Modifier.clip(androidx.compose.foundation.shape.RoundedCornerShape(16.dp)).background(androidx.compose.material3.MaterialTheme.colorScheme.surface)
                .clickable { open = true; q = "" }.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
        ) {
            Text("🌐", fontSize = 11.5.sp)
            androidx.compose.foundation.layout.Spacer(Modifier.width(4.dp))
            Text(currency.ifBlank { "Money" }, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
        }
        androidx.compose.material3.DropdownMenu(open, { open = false }, Modifier.width(196.dp).heightIn(max = 330.dp),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp), containerColor = Color.White) {
            androidx.compose.foundation.layout.Row(Modifier.padding(horizontal = 8.dp, vertical = 4.dp).fillMaxWidth()
                .clip(androidx.compose.foundation.shape.RoundedCornerShape(10.dp)).background(Color(0xFFF1EEEA)).padding(3.dp)) {
                listOf("🌐 Language", "💱 Currency").forEachIndexed { i, label ->
                    Text(label, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, color = if (tab == i) Color(0xFF1D1D1F) else sub,
                        textAlign = TextAlign.Center, maxLines = 1,
                        modifier = Modifier.weight(1f).clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
                            .background(if (tab == i) Color.White else Color.Transparent).clickable { tab = i }.padding(vertical = 5.dp))
                }
            }
            if (tab == 0) {
                val now = androidx.compose.runtime.remember(open) { Store(context).appLanguage }
                APP_LANGS.forEach { (code, name) -> PillItem(name, null, code == now, red) { LangMoney.onAppLang(code); open = false } }
            } else {
                androidx.compose.foundation.text.BasicTextField(q, { q = it }, singleLine = true,
                    textStyle = TextStyle(fontSize = 12.5.sp, color = Color(0xFF1D1D1F)),
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp).fillMaxWidth().height(30.dp)
                        .border(1.dp, Color(0xFFE2DED8), androidx.compose.foundation.shape.RoundedCornerShape(10.dp)).padding(horizontal = 10.dp),
                    decorationBox = { field ->
                        androidx.compose.foundation.layout.Box(contentAlignment = androidx.compose.ui.Alignment.CenterStart) {
                            if (q.isEmpty()) Text("Search: riyal, PKR…", fontSize = 12.5.sp, color = sub)
                            field()
                        }
                    })
                CURRENCIES.map { it to currencyName(it) }.filter { (c, n) -> q.isBlank() || "$c $n".contains(q.trim(), true) }.forEach { (c, n) ->
                    PillItem(c, n, c == currency, red) { LangMoney.onCurrency(c); open = false }
                }
            }
        }
    }
}

/** One compact row of the 🌐 list: the name (and a grey line), red with a ✓ when it is the one in use. */
@Composable
private fun PillItem(name: String, line: String?, on: Boolean, red: Color, onClick: () -> Unit) {
    androidx.compose.foundation.layout.Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        Text(name, fontSize = 12.5.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.Normal, color = if (on) red else Color(0xFF1D1D1F))
        if (line != null) Text("  $line", fontSize = 11.sp, color = Color(0xFF8A8580), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        else androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
        if (on) Text("✓", fontSize = 12.sp, color = red)
    }
}
