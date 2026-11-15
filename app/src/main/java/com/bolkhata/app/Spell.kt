package com.bolkhata.app

/**
 * Misspelt words put right, for a message the app did not understand: "khta dikhaoo" is read as "khata dikhao". The
 * known words are every word list of the app's own readers (taken from them directly, so a word added there is known
 * here too). A word is only changed when it is clearly one known word: the same consonants in the same order, one
 * letter off (two in a long word), and no other known word of another meaning just as near.
 */
object Spell {
    /** Every known spelling, with the list it belongs to (words of one list mean the same kind of thing). */
    private val known: Map<String, String> by lazy {
        val out = HashMap<String, String>()
        listOf(LocalAsk, LocalAdd, LocalEdit, LocalAgent, NativeChatEngine, Normalizer).forEach { reader ->
            reader.javaClass.declaredFields.forEach { f ->
                val value = runCatching { f.isAccessible = true; f.get(reader) ?: f.get(null) }.getOrNull()
                val list = reader.javaClass.simpleName + "." + f.name
                when (value) {
                    is Set<*> -> value.filterIsInstance<String>().forEach { out.putIfAbsent(it, list) }
                    // A map of spellings to their plain form: all spellings of one form are one list.
                    is Map<*, *> -> value.forEach { (k, v) -> if (k is String && v is String) { out.putIfAbsent(k, "$list=$v"); out.putIfAbsent(v, "$list=$v") } }
                }
            }
        }
        out.filterKeys { w -> w.length >= 3 && w.all { it in 'a'..'z' } }
    }

    private val byBones: Map<String, List<String>> by lazy { known.keys.groupBy(::bones) }

    /** A word's consonants in order, written one way: q as k, ph as f, w as v, a doubled letter once; y counts as a vowel. */
    internal fun bones(word: String): String {
        val w = word.lowercase().replace("ph", "f").replace('q', 'k').replace('w', 'v')
        val out = StringBuilder()
        for (c in w) if (c !in "aeiouy" && (out.isEmpty() || out.last() != c)) out.append(c)
        return out.toString()
    }

    /** [text] with its misspelt words put right; unchanged when no word is clearly a known one. [names]: people and things saved, never changed. */
    fun fix(text: String, names: Collection<String> = emptyList()): String {
        val own = names.flatMap { it.lowercase().split(Regex("[^\\p{L}]+")) }.toSet()
        return text.split(' ').joinToString(" ") { raw ->
            val w = raw.lowercase()
            if (w.length < 4 || !w.all { it in 'a'..'z' } || w in known || w in own) return@joinToString raw
            val edits = if (w.length <= 5) 1 else 2
            val near = byBones[bones(w)].orEmpty().map { it to Normalizer.distance(w, it) }.filter { it.second <= edits && kotlin.math.abs(it.first.length - w.length) <= 2 }
            val best = near.minOfOrNull { it.second } ?: return@joinToString raw
            val nearest = near.filter { it.second == best }.map { it.first }
            // Several known words just as near, of different lists: it is not clear which was meant.
            if (nearest.map { known[it] }.toSet().size != 1) raw else nearest.maxByOrNull { Normalizer.similarity(w, it) } ?: raw
        }
    }
}
