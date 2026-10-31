package com.bolkhata.app

/**
 * Reads a chat answer that has money in it ("Hamid Akram all time: you gave 500 SAR, got back 200 SAR. Hamid Akram
 * will pay you 300 SAR.", a list of entries with a Total, a month's totals…) so the chat can show it as a clean card
 * instead of one long line. The numbers are never changed: they are taken from the app's own answer text.
 */
data class Report(
    val title: String,
    val stats: List<Stat>,
    val rows: List<Line>,
    val total: List<Stat>,
    val notes: List<String>,
) {
    /** One amount with what it is. [tone]: -1 money out (red), 1 money in (green), 0 neither. */
    data class Stat(val label: String, val amount: String, val tone: Int)

    /** One entry of a list: "28 Oct · Chai · Hamid Akram · 50 SAR". */
    data class Line(val date: String, val title: String, val sub: String, val amount: String)

    /** The amount the eye should go to first: who pays whom, else the first amount. */
    val hero: Stat? get() = stats.firstOrNull { "will pay" in it.label.lowercase() } ?: stats.firstOrNull()

    companion object {
        private val date = Regex("""^\d{1,2} [A-Z][a-z]{2}( \d{4})?$""")
        private val more = Regex("""^\+\d+ more$""")
        private val number = Regex("""-?\d[\d,]*(?:\.\d+)?""")

        /** The card for [text], or null when it is an ordinary sentence (it is then shown as plain text). */
        fun parse(text: String, currency: String): Report? {
            // A question back to the person ("Change chai from 50 to 60 SAR?") stays a sentence.
            if (currency.isBlank() || text.trimEnd().endsWith("?")) return null
            val cur = Regex.escape(currency)
            val money = Regex("""(-?\d[\d,]*(?:\.\d+)?) ?$cur(?![A-Za-z])""")
            var title = ""
            val stats = mutableListOf<Stat>()
            val rows = mutableListOf<Line>()
            val total = mutableListOf<Stat>()
            val notes = mutableListOf<String>()

            /** "you gave 500 SAR, got back 200 SAR. Hamid will pay you 300 SAR (2 entries)" → amounts and notes. */
            fun amounts(part: String, into: MutableList<Stat>, fallback: String = "Amount", tag: String = "") {
                fun tagged(s: String) = if (tag.isEmpty() || s == tag) s else "$s ($tag)"
                Regex("""\(([^()]*)\)""").findAll(part).forEach { notes += (if (tag.isEmpty()) "" else "$tag: ") + it.groupValues[1].trim() }
                var spending = false
                Regex("""\s·\s|[.;]\s+|,\s+|[.;]$""").split(part.replace(Regex("""\s*\([^()]*\)"""), ""))
                    .map { it.trim() }.filter { it.isNotEmpty() }.forEach { seg0 ->
                        var seg = seg0
                        if (seg.startsWith("Most on ")) { spending = true; seg = seg.removePrefix("Most on ") }
                        val m = money.find(seg) ?: number.findAll(seg).lastOrNull()?.takeIf { spending && it.range.last == seg.lastIndex }
                        if (m == null) { notes += seg; return@forEach }
                        val amount = m.groupValues.getOrElse(1) { "" }.ifEmpty { m.value }
                        val label = (seg.removeRange(m.range)).trim().trim(':', '-', ' ')
                            .replaceFirstChar { it.uppercase() }.ifBlank { fallback }
                        into += Stat(tagged(label), "$amount $currency", if (spending) -1 else tone(label))
                    }
            }

            for (raw in text.lines().map { it.trim() }.filter { it.isNotEmpty() }) {
                val parts = raw.split(" · ")
                when {
                    more.matches(raw) -> notes += raw
                    parts.size >= 3 && money.matchEntire(parts.last()) != null -> {
                        val dated = date.matches(parts[0])
                        val body = if (dated) parts.drop(1).dropLast(1) else parts.dropLast(1)
                        rows += Line(if (dated) parts[0] else "", body.first(), body.drop(1).joinToString(" · "), parts.last())
                    }
                    raw.startsWith("Total: ") -> amounts(raw.removePrefix("Total: "), total, "Total")
                    raw.endsWith(":") && money.find(raw) == null -> title = raw.removeSuffix(":")
                    else -> {
                        val colon = raw.indexOf(": ")
                        val first = money.find(raw)?.range?.first ?: -1
                        val head = if (colon in 1 until first) raw.substring(0, colon) else ""
                        when {
                            head.isEmpty() -> amounts(raw, stats)
                            // Only the first line names the card; a later "Other money out: Lent 50 SAR" keeps its own name.
                            title.isEmpty() && stats.isEmpty() -> { title = head; amounts(raw.substring(colon + 2), stats, title) }
                            else -> amounts(raw.substring(colon + 2), stats, head, head.lowercase())
                        }
                    }
                }
            }
            val worth = rows.isNotEmpty() || stats.size + total.size >= 2 || (stats.size == 1 && title.isNotBlank())
            return if (worth) Report(title, stats, rows, total, notes.filter { it.isNotBlank() }) else null
        }

        /** Red for money going out, green for money coming in, from the words before the amount. */
        fun tone(label: String): Int {
            val l = label.lowercase()
            return when {
                listOf("will pay you", "income", "got back", "sales", "sold", "profit").any { it in l } -> 1
                listOf("you will pay", "spent", "lent", "you gave", "paid back", "bought", "purchases", "expense", "loss", "cash out")
                    .any { it in l } -> -1
                else -> 0
            }
        }
    }
}
