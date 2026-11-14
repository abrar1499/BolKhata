package com.bolkhata.app

/**
 * Goods names said in different spellings ("awal eint" / "awal int" / "aval eint") stay one item: a new name that
 * sounds like (Names.key, word by word) or is one letter away from a saved one takes the saved name.
 */
object Goods {

    /** Every goods name already used (stock, sales, purchases, production), most used first. */
    fun known(all: List<Entry>): List<String> =
        all.filter { (it.qty > 0 || isTrade(it.type) || it.category == Cat.PRODUCTION) && it.item.isNotBlank() &&
            it.item !in setOf("Sale", "Purchase", "Loans", "Transfer", "Income") }
            .groupingBy { it.item.trim() }.eachCount()
            .entries.sortedByDescending { it.value }.map { it.key }.distinctBy { it.lowercase() }

    /** [name] as it is already saved in [known], or [name] itself when nothing close is saved. */
    fun canonical(name: String, known: List<String>): String {
        val n = name.trim()
        if (n.isEmpty()) return n
        known.firstOrNull { it.equals(n, true) }?.let { return it }
        val words = n.lowercase().split(Regex("""\s+"""))
        return known.firstOrNull { k ->
            val kw = k.lowercase().split(Regex("""\s+"""))
            (kw.size == words.size && kw.zip(words).all { (a, b) -> sounds(a, b) }) ||
                (n.length >= 5 && distance(k.lowercase(), n.lowercase()) <= 1)
        } ?: n
    }

    /** One word said two ways ("int", "eent"): the same sound key, and a two-letter key only on words near in length ("bori" is not "bharai"). */
    private fun sounds(a: String, b: String): Boolean {
        val k = Names.key(a)
        return k == Names.key(b) && (k.length >= 3 || (k.length == 2 && kotlin.math.abs(a.length - b.length) <= 1))
    }

    /**
     * [name] as the saved name it is only a longer or shorter way of saying ("Kachi eent molding" for "Kachi eent"), so
     * one work or thing keeps one name. At least two words must agree; a one-word name ("Loading") stays its own.
     */
    fun sameAs(name: String, known: List<String>): String {
        val c = canonical(name, known)
        if (known.any { it == c }) return c
        fun words(s: String) = s.lowercase().split(Regex("""[^\p{L}\p{N}]+""")).filter { it.isNotBlank() }
        fun alike(a: String, b: String) = a == b || sounds(a, b) || (a.length >= 4 && distance(a, b) <= 1)
        val mine = words(name)
        return known.firstOrNull { k ->
            val theirs = words(k)
            val (small, big) = if (theirs.size <= mine.size) theirs to mine else mine to theirs
            small.size >= 2 && small.all { s -> big.any { alike(s, it) } }
        } ?: c
    }

    /** Saved names that start with (or contain) what is typed, for the suggestions under the Goods field. */
    fun suggest(typed: String, known: List<String>): List<String> {
        val t = typed.trim()
        if (t.isEmpty()) return known.take(6)
        return (known.filter { it.startsWith(t, true) } + known.filter { it.contains(t, true) })
            .distinct().filterNot { it.equals(t, true) }.take(6)
    }

    /**
     * The last rate (and unit) used for [item]: this person's own first, then anyone's, same direction (money out or in)
     * when [out] is known. Null when the item was never given a rate.
     */
    fun lastRate(item: String, person: String, out: Boolean?, all: List<Entry>): Entry? {
        val k = item.trim().lowercase()
        if (k.isEmpty()) return null
        val used = all.filter { it.rate > 0 && it.item.trim().lowercase() == k && !isMade(it) && (out == null || isOut(it.type) == out) }
            .sortedByDescending { it.date }
        return used.firstOrNull { person.isNotBlank() && it.person.equals(person.trim(), true) } ?: used.firstOrNull()
    }

    private val madeWords = setOf("tayyar", "tayar", "taiyar", "tyar", "tiyar", "made", "produced", "manufactured", "ready")
    private val madeFillers = setOf("hui", "hue", "huwi", "ho", "gai", "gayi", "gaye", "gaya", "hain", "hai", "he", "ki", "ka", "ke",
        "kar", "kr", "li", "lin", "lee", "have", "has", "been", "were", "was", "are", "is", "we", "i", "the", "today", "aaj")

    /** "120000 awal eint tayyar hui" / "made 500 shirts": how many and of what; null when it doesn't say goods were made. */
    fun readMade(text: String): Pair<Double, String>? {
        val words = text.lowercase().split(Regex("""[^\p{L}\p{N}.]+""")).filter { it.isNotEmpty() }
        if (words.none { it in madeWords }) return null
        val nums = words.filter { w -> w.any { it.isDigit() } && w.all { it.isDigit() || it == '.' } }
        if (nums.size != 1 || words.any { it in setOf("rate", "riyal", "rs", "sar", "pkr", "rupay", "rupees") }) return null
        val qty = nums[0].toDoubleOrNull()?.takeIf { it > 0 } ?: return null
        val item = words.filter { it !in madeWords && it !in madeFillers && it != nums[0] }.joinToString(" ")
        return qty to item.replaceFirstChar { it.uppercase() }
    }

    private fun distance(a: String, b: String): Int {
        if (kotlin.math.abs(a.length - b.length) > 1) return 2
        val d = Array(a.length + 1) { IntArray(b.length + 1) }
        for (i in 0..a.length) d[i][0] = i
        for (j in 0..b.length) d[0][j] = j
        for (i in 1..a.length) for (j in 1..b.length)
            d[i][j] = minOf(d[i - 1][j] + 1, d[i][j - 1] + 1, d[i - 1][j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1)
        return d[a.length][b.length]
    }
}
