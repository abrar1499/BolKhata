package com.bolkhata.app

/**
 * Misspelt words put right, for a message the app did not understand: "khta dikhaoo" is read as "khata dikhao". The
 * known words are every word list of the app's own readers (taken from them directly, so a word added there is known
 * here too). A word is only changed when it is clearly one known word: the same consonants in the same order, one
 * letter off (two in a long word), and no other known word of another meaning just as near.
 */
object Spell {
    /** Every known spelling, with the lists it is in (words sharing a list mean the same kind of thing). */
    private val known: Map<String, Set<String>> by lazy {
        val out = HashMap<String, MutableSet<String>>()
        fun put(w: String, list: String) { out.getOrPut(w) { HashSet() }.add(list) }
        fun fields(o: Any) = o.javaClass.declaredFields.mapNotNull { f -> runCatching { f.isAccessible = true; f.name to (f.get(o) ?: f.get(null)) }.getOrNull() }
        listOf(LocalAsk, LocalAdd, LocalEdit, LocalAgent, NativeChatEngine, Normalizer, NumberWords, Parser).forEach { reader ->
            fields(reader).forEach { (name, value) ->
                val list = reader.javaClass.simpleName + "." + name
                when (value) {
                    is Set<*> -> value.filterIsInstance<String>().forEach { put(it, list) }
                    // A map of spellings to their plain form (or number): all spellings of one form are one list.
                    is Map<*, *> -> value.forEach { (k, v) ->
                        if (k is String && v is String) { put(k, "$list=$v"); put(v, "$list=$v") }
                        else if (k is String && v is Number) put(k, "$list=$v")
                    }
                    // Parser's things: each thing's own spellings are one list.
                    is List<*> -> value.forEachIndexed { i, rule ->
                        if (rule is String) put(rule, list)
                        else if (rule != null) fields(rule).forEach { (_, keys) -> if (keys is List<*>) keys.filterIsInstance<String>().forEach { put(it, "$list#$i") } }
                    }
                }
            }
        }
        out.filterKeys { w -> w.length >= 3 && w.all { it in 'a'..'z' } }
    }

    /** Whether the app's own readers know [word] (the brain may only add words it does not). */
    fun knows(word: String) = word.lowercase() in known || (Understand.isCommonName(word) && !Lexicon.isName(word))

    /** A list of number words: "dedh", "sau", "lakh". */
    private fun numberList(list: String) = list.startsWith("NumberWords.") || "multipliers" in list || "halves" in list

    private val byBones: Map<String, List<String>> by lazy { known.keys.groupBy(::bones) }

    /** A word's consonants in order, written one way: q as k, ph as f, w as v, a doubled letter once; y counts as a vowel. */
    internal fun bones(word: String): String {
        val w = word.lowercase().replace("ph", "f").replace('q', 'k').replace('w', 'v')
        val out = StringBuilder()
        for (c in w) if (c !in "aeiouy" && (out.isEmpty() || out.last() != c)) out.append(c)
        return out.toString()
    }

    /** Letters to change, put in or leave out to make [a] into [b], two letters swapped counting as one ("ntoe", "note"). */
    internal fun distance(a: String, b: String): Int {
        val d = Array(a.length + 1) { i -> IntArray(b.length + 1) { j -> if (i == 0) j else if (j == 0) i else 0 } }
        for (i in 1..a.length) for (j in 1..b.length) {
            val cost = if (a[i - 1] == b[j - 1]) 0 else 1
            d[i][j] = minOf(d[i - 1][j] + 1, d[i][j - 1] + 1, d[i - 1][j - 1] + cost)
            if (i > 1 && j > 1 && a[i - 1] == b[j - 2] && a[i - 2] == b[j - 1]) d[i][j] = minOf(d[i][j], d[i - 2][j - 2] + 1)
        }
        return d[a.length][b.length]
    }

    /**
     * [text] with its misspelt words put right; unchanged when no word is clearly a known one. [names]: people and things
     * saved, never changed. Short words become only longer known words ("frm": from, "lkh": lakh), never "ande" → "and".
     */
    fun fix(text: String, names: Collection<String> = emptyList()): String {
        val own = names.flatMap { it.lowercase().split(Regex("[^\\p{L}]+")) }.toSet()
        val words = text.split(' ')
        return words.mapIndexed { at, raw ->
            val w = raw.lowercase()
            // A new person's name ("bilal", "tariq") is not a misspelt word, nor is a word where a name stands ("motion ko":
            // Mohsin misheard, which the readers match to the saved name).
            val namePlace = words.getOrNull(at + 1)?.lowercase() in setOf("ko", "ku", "ne", "nay", "ny", "se", "sy", "say") ||
                words.getOrNull(at - 1)?.lowercase() in setOf("to", "from")
            if (w.length < 3 || !w.all { it in 'a'..'z' } || w in known || w in own || Understand.isCommonName(w) || Lexicon.isName(w) ||
                Lexicon.thing(listOf(w)) != null) return@mapIndexed raw
            val edits = if (w.length <= 5) 1 else 2
            // Known words with the same consonants, also when two letters were swapped ("rikcshaw", "paacnh").
            val swaps = (0 until w.length - 1).map { i -> w.substring(0, i) + w[i + 1] + w[i] + w.substring(i + 2) }
            val near = (listOf(w) + swaps).flatMap { byBones[bones(it)].orEmpty() }.distinct().map { it to distance(w, it) }.filter { (k, d) ->
                d <= edits && kotlin.math.abs(k.length - w.length) <= 2 && k.length >= 4 &&
                    // Three letters: only the same word with a vowel left out ("frm", "qrz", "ddh").
                    (w.length > 3 || (k.length == 4 && (0 until 4).any { i -> k[i] in "aeiou" && k.removeRange(i, i + 1) == w }))
            }
            val best = near.minOfOrNull { it.second } ?: return@mapIndexed raw
            // Just as near: a letter missing or wrong ("bhrai": bharai) before two letters swapped ("bhari").
            val tied = near.filter { it.second == best }.map { it.first }
            val plain = tied.minOf { Normalizer.distance(w, it) }
            // Next to a number ("1 lkh", "ddh sau"), a number word before any other ("likh", "dudh").
            val counting = words.getOrNull(at - 1)?.firstOrNull()?.isDigit() == true || known[words.getOrNull(at + 1)?.lowercase()].orEmpty().any(::numberList)
            val nearest = tied.filter { Normalizer.distance(w, it) == plain }.let { n -> if (counting) n.filter { known[it].orEmpty().any(::numberList) }.ifEmpty { n } else n }
            // Where a name stands, a thing is not put for it: "motion ko" is Mohsin misheard, not mutton ("saham ko" is shaam).
            if (namePlace && nearest.all { n -> known[n].orEmpty().all { it.startsWith("Parser.rules#") } }) return@mapIndexed raw
            // Several known words just as near with no list in common: it is not clear which was meant.
            if (nearest.map { known[it].orEmpty() }.reduce { a, b -> a intersect b }.isEmpty()) raw else nearest.maxByOrNull { Normalizer.similarity(w, it) } ?: raw
        }.joinToString(" ")
    }
}
