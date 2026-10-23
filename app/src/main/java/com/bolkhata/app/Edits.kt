package com.bolkhata.app

/**
 * Changes an existing entry without AI, for the common cases:
 *  - date:   "nashta wali entry 2 din pehle ki hai", "petrol kal ka tha", "hamza wali 14 date ki thi"
 *  - amount: "nashta 7 nahi 5 tha", "hamza wali 500 ko 400 kar do", "nashta wali entry 5 ki thi"
 * Returns null when the sentence is not clearly about an old entry, so it is read as a new one.
 */
object Edits {

    private val refers = setOf("entry", "wali", "wala", "wale", "walay", "waly", "last", "pichli", "pichhli", "akhri", "aakhri", "previous")
    private val strongWords = setOf(
        "change", "badal", "badlo", "update", "correct", "galat", "ghalat", "fix", "theek", "thik", "sahi", "edit",
        "instead", "jagah", "bajaye"
    )
    private val noWords = setOf("nahi", "nahin", "nhi", "ni")
    private val pastWords = setOf("tha", "thi", "the", "thay", "hai", "he")
    /** Words that describe doing something now: then it is a new entry, not a change. */
    private val actionVerbs = setOf(
        "kiya", "kia", "kiye", "liya", "liye", "lia", "li", "diya", "diye", "di", "dia", "khaya", "khai", "khaye", "piya", "pi",
        "dala", "dali", "dalwaya", "bheja", "bheje", "becha", "bechi", "kharida", "kharidi", "karwaya", "bharwaya", "mila", "mile",
        "bought", "paid", "spent", "gave", "took", "sold"
    )
    private val doneWords = setOf("kar", "kr", "karo", "kardo", "krdo", "do", "dein", "den")
    private val stop = setOf(
        "entry", "wali", "wala", "wale", "walay", "waly", "ki", "ka", "ke", "ko", "hai", "he", "thi", "tha", "the", "thay",
        "kar", "kr", "karo", "do", "dein", "kardo", "krdo", "change", "badal", "badlo", "update", "correct", "galat", "ghalat",
        "fix", "theek", "thik", "sahi", "edit", "nahi", "nahin", "nhi", "ni", "date", "tareekh", "tarikh", "din", "days", "day",
        "pehle", "pehly", "pehlay", "pahle", "phle", "phly", "ago", "kal", "kl", "cal", "parson", "parso", "parsu", "aaj", "aj",
        "today", "yesterday", "mein", "me", "men", "se", "ne", "is", "was", "the", "it", "instead", "jagah", "bajaye", "riyal",
        "sar", "rupay", "last", "pichli", "pichhli", "akhri", "aakhri", "previous", "amount", "raqam", "rakam", "honi", "hona",
        "chahye", "chahiye", "should", "be", "to", "make"
    )

    fun plan(text: String, entries: List<Entry>, now: Long = System.currentTimeMillis()): Ai.EditPlan? {
        if (entries.isEmpty()) return null
        val lower = " " + text.lowercase() + " "
        val words = lower.split(Regex("""[^\p{L}\p{N}]+""")).filter { it.isNotEmpty() }
        val (date, rest) = Parser.extractDate(text, now)
        val nums = Regex("""\d+(?:\.\d+)?""").findAll(rest).map { it.value.toDouble() }.toList()
        val strong = words.any { it in strongWords }
        val no = words.any { it in noWords }
        val didSomething = words.any { it in actionVerbs }
        val aboutOld = words.contains("entry") || strong ||
            (no && nums.size >= 2) ||
            (words.any { it in doneWords } && words.contains("ko") && nums.size >= 2) ||
            (!didSomething && words.any { it in refers } && words.any { it in pastWords }) ||
            // "nashta parsu ka tha": no amount, only a day, about something already saved.
            (!didSomething && date != null && nums.isEmpty() && words.any { it in pastWords })
        if (!aboutOld) return null

        // Which entry: the newest one whose item, note, person or category matches the other words.
        val keys = words.filter { it.length >= 3 && it.none { c -> c.isDigit() } && it !in stop }
        val last = words.any { it in setOf("last", "pichli", "pichhli", "akhri", "aakhri", "previous") }
        val newest = entries.sortedByDescending { it.createdAt }
        val target = (if (keys.isNotEmpty()) newest.firstOrNull { e -> keys.any { k -> hits(e, k) } } else null)
            ?: (if (last || keys.isEmpty()) newest.firstOrNull() else null)
            ?: return null

        var changed = target
        var what = ""
        if (date != null && !sameDay(date, target.date)) {
            changed = changed.copy(date = date)
            what = "date → " + dayLabel(date)
        }
        // A new amount: "7 nahi 5", "500 ko 400 kar do", or a single amount for the entry.
        val newAmount = when {
            nums.size >= 2 && (words.contains("nahi") || words.contains("nhi") || words.contains("ni") || words.contains("nahin")) &&
                Regex("""(\d+(?:\.\d+)?)\s+(?:nahi|nhi|ni|nahin)\b""").containsMatchIn(rest.lowercase()) -> {
                // "7 nahi 5": the number after "nahi" is the right one.
                Regex("""(?:nahi|nhi|ni|nahin)\s+(\d+(?:\.\d+)?)""").find(rest.lowercase())?.groupValues?.get(1)?.toDouble() ?: nums.last()
            }
            nums.size >= 2 && words.contains("ko") -> nums.last()
            nums.size == 1 && date == null -> nums.first()
            else -> null
        }
        if (newAmount != null && newAmount > 0 && newAmount != target.amount) {
            changed = changed.copy(amount = newAmount)
            what = (if (what.isEmpty()) "" else "$what, ") + "amount → " + fmt(newAmount)
        }
        if (changed == target) return null
        return Ai.EditPlan(listOf(target to changed), emptyList(), entryTitle(target) + ": " + what)
    }

    private fun hits(e: Entry, k: String): Boolean {
        val hay = (e.item + " " + e.note + " " + e.person + " " + e.category.label).lowercase()
            .split(Regex("""[^\p{L}]+""")).filter { it.length >= 3 }
        return hay.any { h -> h.startsWith(k) || k.startsWith(h) || close(h, k) }
    }

    /** "nshta" ~ "nashta": one letter off. */
    private fun close(a: String, b: String): Boolean {
        if (kotlin.math.abs(a.length - b.length) > 1 || minOf(a.length, b.length) < 4) return false
        val d = Array(a.length + 1) { IntArray(b.length + 1) }
        for (i in 0..a.length) d[i][0] = i
        for (j in 0..b.length) d[0][j] = j
        for (i in 1..a.length) for (j in 1..b.length) {
            d[i][j] = minOf(d[i - 1][j] + 1, d[i][j - 1] + 1, d[i - 1][j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1)
        }
        return d[a.length][b.length] <= 1
    }

    private fun sameDay(a: Long, b: Long): Boolean {
        val x = java.util.Calendar.getInstance().apply { timeInMillis = a }
        val y = java.util.Calendar.getInstance().apply { timeInMillis = b }
        return x.get(java.util.Calendar.YEAR) == y.get(java.util.Calendar.YEAR) &&
            x.get(java.util.Calendar.DAY_OF_YEAR) == y.get(java.util.Calendar.DAY_OF_YEAR)
    }
}
