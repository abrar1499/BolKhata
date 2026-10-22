package com.bolkhata.app

import java.util.Calendar

/**
 * Answers questions about the khata without internet:
 * "hamza ko total kitne de diye", "is mahine food pe kitna kharcha hua", "aaj kitna kharcha hua".
 */
object Questions {

    class Answer(val text: String, val search: String?)

    private val questionWords = listOf(
        "kitna", "kitne", "kitni", "kitnay", "kitny", "total", "how much", "how many", "hisaab", "hisab",
        "balance", "baqi", "baki", "bacha", "bache", "kul", "sum", "کتنا", "کتنے", "کتنی"
    )

    private val filler = setOf(
        "ko", "ki", "ka", "ke", "se", "ne", "pe", "par", "mein", "me", "main", "total", "kitna", "kitne", "kitni", "kitnay",
        "kitny", "de", "diye", "diya", "di", "dye", "liye", "liya", "li", "hua", "huay", "hue", "hain", "hai", "tha", "thay",
        "the", "kya", "kia", "how", "much", "many", "did", "i", "give", "gave", "spend", "spent", "on", "to", "from", "is",
        "this", "month", "mahine", "mahina", "saal", "year", "aaj", "today", "kal", "yesterday", "pichle", "pichhle",
        "last", "kharcha", "kharch", "expense", "expenses", "hisaab", "hisab", "balance", "baqi", "baki", "bacha", "bache",
        "kul", "sum", "abhi", "tak", "ab", "mujhe", "maine", "mene", "men", "my", "mera", "meri", "mere", "wapis", "wapas",
        "lena", "dena", "hai?", "so", "far", "all", "time", "sab", "poora", "pura", "income", "aamdani", "kamaya", "kamai"
    )

    private enum class Period(val label: String) { TODAY("today"), YESTERDAY("yesterday"), MONTH("this month"), LAST_MONTH("last month"), YEAR("this year"), ALL("so far") }

    fun isQuestion(text: String): Boolean {
        val t = " " + text.lowercase() + " "
        if (text.any { it.isDigit() }) return false
        return questionWords.any { t.contains(it) } || text.trim().endsWith("?")
    }

    fun answer(text: String, entries: List<Entry>, currency: String): Answer? {
        val t = text.lowercase()
        val words = t.split(Regex("""[^\p{L}]+""")).filter { it.isNotEmpty() }
        val asked = when {
            words.contains("aaj") || words.contains("today") -> Period.TODAY
            words.contains("kal") || words.contains("yesterday") -> Period.YESTERDAY
            (words.contains("pichle") || words.contains("pichhle") || words.contains("last")) &&
                (words.contains("mahine") || words.contains("month")) -> Period.LAST_MONTH
            words.contains("mahine") || words.contains("mahina") || words.contains("month") -> Period.MONTH
            words.contains("saal") || words.contains("year") -> Period.YEAR
            else -> null
        }

        // 1. A person in the khata
        val people = entries.filter { it.person.isNotBlank() }.map { it.person.trim() }.distinct()
        val person = people.firstOrNull { p -> words.contains(p.lowercase()) }
        if (person != null) return personAnswer(person, entries, currency, asked ?: Period.ALL)

        val rest = words.filter { it !in filler && it.length >= 3 }
        val period = asked ?: Period.MONTH
        val inPeriod = entries.filter { inPeriod(it.date, period) }

        // 2. Income
        if (words.any { it in setOf("income", "aamdani", "kamaya", "kamai", "salary", "tankhwah") } && rest.none { it != "salary" && it != "tankhwah" }) {
            val sum = inPeriod.filter { it.type == Type.AAMDANI }.sumOf { it.amount }
            return Answer("Income ${period.label}: ${money(currency, sum)}", "income")
        }

        // 3. A category or item ("food", "petrol", "roti")
        if (rest.isNotEmpty()) {
            val query = rest.joinToString(" ")
            val byCat = Cat.entries.firstOrNull { c -> rest.any { w -> c.label.lowercase().split(" ", "&", "/").any { it == w } } }
            val guess = Parser.guessCategory(query)
            val spends = inPeriod.filter { it.type == Type.KHARCHA }
            val list = when {
                byCat != null -> spends.filter { it.category == byCat }
                guess != null -> spends.filter { e ->
                    e.category == guess.first && (e.item == guess.second || matchEntries(listOf(e), query).isNotEmpty())
                }
                else -> spends.filter { matchEntries(listOf(it), query).isNotEmpty() }
            }
            if (list.isEmpty() && byCat == null && guess == null) return null
            val label = byCat?.label ?: guess?.second ?: query
            val sum = list.sumOf { it.amount }
            return Answer(
                "$label ${period.label}: ${money(currency, sum)} (${list.size} " + (if (list.size == 1) "entry" else "entries") + ")",
                rest.first()
            )
        }

        // 4. Overall spending
        val spent = inPeriod.filter { it.type == Type.KHARCHA }.sumOf { it.amount }
        val top = inPeriod.filter { it.type == Type.KHARCHA }.groupBy { it.category }
            .mapValues { (_, v) -> v.sumOf { it.amount } }.entries.sortedByDescending { it.value }.take(3)
        val topText = if (top.isEmpty()) "" else ". Most on " + top.joinToString(", ") { it.key.label + " " + fmt(it.value) }
        return Answer("Spent ${period.label}: ${money(currency, spent)}$topText", null)
    }

    private fun personAnswer(person: String, entries: List<Entry>, currency: String, period: Period): Answer {
        val mine = entries.filter { it.person.trim().equals(person, true) && isLoan(it.type) && inPeriod(it.date, period) }
        fun sum(t: Type) = mine.filter { it.type == t }.sumOf { it.amount }
        val gave = sum(Type.DIYA)
        val took = sum(Type.LIYA)
        val gotBack = sum(Type.WAPIS_MILA)
        val paidBack = sum(Type.WAPIS_DIYA)
        val all = entries.filter { it.person.trim().equals(person, true) && isLoan(it.type) }
        val bal = balances(all)[person] ?: balances(all).entries.firstOrNull { it.key.equals(person, true) }?.value ?: 0.0
        val parts = mutableListOf<String>()
        if (gave > 0) parts += "you gave ${money(currency, gave)}"
        if (gotBack > 0) parts += "got back ${money(currency, gotBack)}"
        if (took > 0) parts += "you took ${money(currency, took)}"
        if (paidBack > 0) parts += "paid back ${money(currency, paidBack)}"
        val head = if (parts.isEmpty()) "No entries with $person ${period.label}" else "$person ${period.label}: " + parts.joinToString(", ")
        val tail = when {
            bal > 0.005 -> ". $person will pay you ${money(currency, bal)}."
            bal < -0.005 -> ". You will pay $person ${money(currency, -bal)}."
            parts.isNotEmpty() -> ". All settled."
            else -> "."
        }
        return Answer(head + tail, person)
    }

    private fun inPeriod(ms: Long, p: Period): Boolean {
        val now = Calendar.getInstance()
        val c = cal(ms)
        return when (p) {
            Period.TODAY -> sameDay(ms, now.timeInMillis)
            Period.YESTERDAY -> sameDay(ms, now.timeInMillis - 86_400_000L)
            Period.MONTH -> c.get(Calendar.YEAR) == now.get(Calendar.YEAR) && c.get(Calendar.MONTH) == now.get(Calendar.MONTH)
            Period.LAST_MONTH -> {
                val last = Calendar.getInstance().apply { add(Calendar.MONTH, -1) }
                c.get(Calendar.YEAR) == last.get(Calendar.YEAR) && c.get(Calendar.MONTH) == last.get(Calendar.MONTH)
            }
            Period.YEAR -> c.get(Calendar.YEAR) == now.get(Calendar.YEAR)
            Period.ALL -> true
        }
    }
}
