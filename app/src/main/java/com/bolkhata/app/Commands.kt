package com.bolkhata.app

/**
 * Typed or spoken commands like "search hamza", "edit food", "delete hamza", "clear all",
 * also in Urdu order: "hamza ka khata mitao", "sab mitao".
 * A sentence with a number is always treated as a new entry, never as a command.
 */
object Commands {
    enum class Action { SEARCH, EDIT, DELETE, CLEAR_ALL }

    class Command(val action: Action, val query: String)

    private val search = setOf("search", "find", "show", "dhoondo", "dhundo", "dhoondho", "dhundho", "talash", "talaash", "dikhao", "تلاش", "دکھاؤ")
    private val edit = setOf("edit", "change", "badlo", "open", "kholo")
    private val delete = setOf("delete", "delet", "dlt", "remove", "mitao", "mita", "hatao", "khatam", "مٹاؤ", "ختم")
    private val clear = setOf("clear")
    private val allWords = setOf("all", "sab", "sara", "saara", "sari", "saari", "everything", "poora", "pura", "puri", "poori", "سب")
    private val filler = setOf(
        "ka", "ki", "ke", "khata", "khaata", "entries", "entry", "wala", "wali", "wale", "karo", "kar", "do", "dein",
        "den", "please", "plz", "the", "for", "of", "record", "records", "data", "ko", "kr", "krdo", "kardo"
    )

    fun read(text: String): Command? {
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
