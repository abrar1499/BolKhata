package com.bolkhata.app

/**
 * Typed or spoken commands like "search hamza", "edit food", "delete hamza", "clear all",
 * also in Urdu order: "hamza ka khata mitao", "sab mitao".
 * A sentence with a number is always treated as a new entry, never as a command.
 */
object Commands {
    enum class Action { SEARCH, EDIT, DELETE, DELETE_PERIOD, CLEAR_ALL }

    class Command(val action: Action, val query: String, val period: DatePeriod? = null)

    /** Calendar year, and optionally one calendar month (1 to 12). */
    data class DatePeriod(val year: Int, val month: Int? = null) {
        fun label(): String = if (month == null) "$year"
            else java.text.DateFormatSymbols(java.util.Locale.ENGLISH).months[month - 1] + " " + year
    }

    private val search = setOf("search", "find", "show", "dhoondo", "dhundo", "dhoondho", "dhundho", "talash", "talaash", "dikhao", "تلاش", "دکھاؤ")
    private val edit = setOf("edit", "change", "badlo", "open", "kholo")
    private val delete = setOf("delete", "delet", "dlt", "remove", "mitao", "mita", "hatao", "khatam", "مٹاؤ", "ختم")
    private val clear = setOf("clear")
    private val allWords = setOf("all", "sab", "sara", "saara", "sari", "saari", "everything", "poora", "pura", "puri", "poori", "سب")
    private val filler = setOf(
        "ka", "ki", "ke", "khata", "khaata", "entries", "entry", "wala", "wali", "wale", "karo", "kar", "do", "dein",
        "den", "please", "plz", "the", "for", "of", "record", "records", "data", "ko", "kr", "krdo", "kardo"
    )

    private val periodMonths: Map<String, Int> = buildMap {
        val aliases = listOf(
            "jan january janwari", "feb february febwari farwari", "mar march",
            "apr april", "may", "jun june", "jul july", "aug august",
            "sep sept september sitambar", "oct october aktubar", "nov november",
            "dec december disambar"
        )
        aliases.forEachIndexed { index, group ->
            group.split(" ").forEach { put(it, index + 1) }
        }
    }

    private val periodWords = filler + allWords + setOf(
        "month", "months", "mahina", "mahine", "year", "saal", "sal",
        "ki", "ka", "ke", "ko", "mein", "men", "me", "main",
        "transactions", "transaction", "entri", "sari", "saari", "sabhi", "jitni", "bhi", "hain", "hai"
    )
    private val yearPattern = Regex("""(?:19|20|21)\d{2}""")
    private val yearMonthPattern = Regex("""(?<!\d)((?:19|20|21)\d{2})[/-](0?[1-9]|1[0-2])(?!\d)""")
    private val monthYearPattern = Regex("""(?<!\d)(0?[1-9]|1[0-2])[/-]((?:19|20|21)\d{2})(?!\d)""")

    /**
     * Date-based bulk deletion is deliberately strict: a deletion verb AND an unambiguous
     * calendar year/month are required. "delete 2025 riyal" must not delete anything.
     * A month without a year uses the phone's current year, shown in the confirmation.
     */
    private fun readPeriodDelete(text: String): Command? {
        val lower = text.lowercase(java.util.Locale.ROOT).trim()
        val words = lower.split(Regex("""[^\p{L}\p{N}]+""")).filter { it.isNotEmpty() }
        if (words.none { it in delete || it in clear }) return null
        if (words.any { it in setOf("nahi", "nahin", "nhi", "ni", "mat", "not", "dont", "don't") }) return null

        val years = words.filter { yearPattern.matches(it) }.distinct()
        if (years.size > 1) return null
        val textNumeric = yearMonthPattern.find(lower)?.let {
            it.groupValues[1].toInt() to it.groupValues[2].toInt()
        } ?: monthYearPattern.find(lower)?.let {
            it.groupValues[2].toInt() to it.groupValues[1].toInt()
        }
        val namedMonths = words.mapNotNull { periodMonths[it] }.distinct()
        if (namedMonths.size > 1) return null
        val month = namedMonths.firstOrNull() ?: textNumeric?.second
        val year = years.firstOrNull()?.toInt() ?: textNumeric?.first
        if (month == null && year == null) return null
        if (textNumeric != null && (year != textNumeric.first || month != textNumeric.second)) return null

        val numericMonthToken = textNumeric?.second?.toString()
        val unexpected = words.filterNot { word ->
            word in delete || word in clear || word in periodWords ||
                yearPattern.matches(word) || word in periodMonths ||
                (numericMonthToken != null && word.toIntOrNull() == numericMonthToken.toInt())
        }
        if (unexpected.isNotEmpty()) return null

        val period = DatePeriod(year ?: java.util.Calendar.getInstance().get(java.util.Calendar.YEAR), month)
        return Command(Action.DELETE_PERIOD, period.label(), period)
    }

    fun read(text: String): Command? {
        readPeriodDelete(text)?.let { return it }
        if (text.any { it.isDigit() }) return null
        val words = text.lowercase().trim().split(Regex("""[\s,.!?]+""")).filter { it.isNotEmpty() }
        if (words.isEmpty()) return null

        fun kind(w: String): Action? = when (w) {
            in search -> Action.SEARCH
            in edit -> Action.EDIT
            in delete -> Action.DELETE
            in clear -> Action.CLEAR_ALL
            else -> null
        }

        // Verb first ("delete hamza") or last ("hamza ka khata mitao karo").
        val meaningful = words.filter { it !in filler }
        if (meaningful.isEmpty()) return null
        val firstKind = kind(meaningful.first())
        val lastKind = kind(meaningful.last())
        val action: Action
        val rest: List<String>
        when {
            firstKind != null -> { action = firstKind; rest = meaningful.drop(1) }
            lastKind != null -> { action = lastKind; rest = meaningful.dropLast(1) }
            else -> return null
        }

        val wantsAll = rest.isEmpty() || rest.all { it in allWords }
        return when (action) {
            Action.CLEAR_ALL -> if (wantsAll) Command(Action.CLEAR_ALL, "") else Command(Action.DELETE, rest.joinToString(" "))
            Action.DELETE -> if (rest.isNotEmpty() && wantsAll) Command(Action.CLEAR_ALL, "") else if (rest.isEmpty()) null else Command(Action.DELETE, rest.joinToString(" "))
            Action.DELETE_PERIOD -> null // Only produced by readPeriodDelete.
            else -> if (rest.isEmpty()) null else Command(action, rest.joinToString(" "))
        }
    }
}

private val catWords = mapOf(
    Cat.KHANA to "food khana khaana roti milk doodh",
    Cat.TRANSPORT to "transport gaari gari car petrol safar",
    Cat.RENT to "rent kiraya",
    Cat.BIJLI to "bills bill bijli",
    Cat.MOBILE to "mobile internet recharge",
    Cat.GHAR to "ghar home family sent",
    Cat.SHOPPING to "shopping kapre",
    Cat.SEHAT to "health dawai doctor",
    Cat.PERSONAL to "personal haircut",
    Cat.TANKHWAH to "income salary tankhwah",
    Cat.UDHAAR to "loans udhaar loan",
    Cat.FEES to "fees visa permit fine govt",
    Cat.EDU to "education school books fees",
    Cat.CHARITY to "charity gift zakat sadqa",
    Cat.ASSETS to "assets asset mobile phone car laptop gold",
    Cat.OTHER to "other"
)

/** Entries whose person, item, note, category or type match the search words. */
fun matchEntries(entries: List<Entry>, query: String): List<Entry> {
    val words = query.lowercase().split(Regex("""\s+""")).filter { it.isNotEmpty() }
    if (words.isEmpty()) return emptyList()
    return entries.filter { e ->
        val hay = listOf(e.person, e.item, e.note, e.category.label, catWords[e.category].orEmpty(), e.type.label)
            .joinToString(" ").lowercase()
        words.all { hay.contains(it) }
    }.sortedWith(compareByDescending<Entry> { it.date }.thenByDescending { it.createdAt })
}

/** Match by the saved transaction date, never a year/month mentioned in the entry note. */
fun matchEntriesByPeriod(entries: List<Entry>, period: Commands.DatePeriod): List<Entry> =
    entries.filter { entry ->
        val date = java.util.Calendar.getInstance().apply { timeInMillis = entry.date }
        date.get(java.util.Calendar.YEAR) == period.year &&
            (period.month == null || date.get(java.util.Calendar.MONTH) + 1 == period.month)
    }.sortedWith(compareByDescending<Entry> { it.date }.thenByDescending { it.createdAt })
