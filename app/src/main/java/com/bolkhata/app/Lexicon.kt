package com.bolkhata.app

import org.json.JSONObject

/**
 * The chat's language from the shared brain ("words" in brain.json), so new ways of saying things reach every phone when
 * it opens, without a new version:
 * - "same": a word or phrase people say → the word the app already knows ("utarwa" → "wapis", "easypaisa" → "cash");
 *   "" leaves the word out.
 * - "things": a thing → its account ("biryani" → "Food").
 * - "names": first names, never read as a thing or put right as a misspelling.
 * The brain only adds: a word the app already knows keeps its meaning, so a bad line cannot turn "diye" into "mile".
 */
object Lexicon {
    @Volatile private var same: List<Pair<Regex, String>> = emptyList()
    @Volatile private var things: Map<String, Cat> = emptyMap()
    @Volatile private var names: Set<String> = emptySet()

    /** What was last loaded, to keep on the phone for the next start (also without internet). */
    @Volatile var saved = ""
        private set

    /** Reads the brain's "words" ([json] as text); anything that is not a plain new word is left out. */
    fun load(json: String) {
        val o = runCatching { JSONObject(json) }.getOrNull() ?: return
        fun plain(w: String) = w.trim().lowercase().takeIf { it.isNotEmpty() && it.length <= 40 && it.all { c -> c.isLetter() || c == ' ' } }
        val newWord = { w: String -> w.split(' ').any { !Spell.knows(it) } }
        same = o.optJSONObject("same")?.let { m ->
            m.keys().asSequence().mapNotNull { k ->
                val key = plain(k) ?: return@mapNotNull null
                val to = m.optString(k).trim().lowercase()
                // Only to words the app knows, and only for a word or phrase it does not.
                if (!newWord(key) || (to.isNotEmpty() && to.split(' ').any { !Spell.knows(it) })) null else key to to
            }.sortedByDescending { it.first.length }
                .map { (k, v) -> Regex("""(?<![\p{L}])""" + Regex.escape(k) + """(?![\p{L}])""", RegexOption.IGNORE_CASE) to v }.toList()
        }.orEmpty()
        things = o.optJSONObject("things")?.let { m ->
            m.keys().asSequence().mapNotNull { k ->
                val key = plain(k)?.takeIf { ' ' !in it && newWord(it) } ?: return@mapNotNull null
                val v = m.optString(k)
                Cat.entries.firstOrNull { it.name.equals(v, true) || it.label.equals(v, true) }?.let { key to it }
            }.toMap()
        }.orEmpty()
        names = o.optJSONArray("names")?.let { a -> (0 until a.length()).mapNotNull { i -> plain(a.optString(i))?.takeIf { ' ' !in it && newWord(it) } }.toSet() }.orEmpty()
        saved = json
    }

    /** [text] with the brain's words put as the app knows them ("zubair ko 500 utarwa diye" → "... wapis diye"). */
    fun apply(text: String): String {
        val rules = same
        if (rules.isEmpty()) return text
        return rules.fold(text) { t, (k, v) -> k.replace(t, v) }.replace(Regex("""\s{2,}"""), " ").trim()
    }

    /** The account of a thing the brain knows, as Parser gives it (account and the thing's name). */
    fun thing(words: List<String>): Pair<Cat, String>? =
        words.firstNotNullOfOrNull { w -> things[w]?.let { it to w.replaceFirstChar { c -> c.uppercase() } } }

    fun isName(word: String) = word.lowercase() in names
}
