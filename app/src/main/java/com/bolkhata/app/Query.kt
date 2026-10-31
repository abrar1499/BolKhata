package com.bolkhata.app

import java.util.Calendar

/**
 * The app's own query engine. The AI only turns what was said into a [Filter] ("3 din pehle ka kharcha",
 * "ali aur imran wali entries", "petrol ki last 3 entries"); the app picks the records and does the maths.
 */
object Query {

    class Filter(
        val from: Long? = null,
        val to: Long? = null,
        val types: Set<Type> = emptySet(),
        val persons: List<String> = emptyList(),
        val cats: Set<Cat> = emptySet(),
        val items: List<String> = emptyList(),
        val min: Double? = null,
        val max: Double? = null,
        val last: Int = 0,
        /** Leave out money to / from people (transfers, loans): "people ke ilawa". */
        val noPeople: Boolean = false,
        /** Leave out these words: "petrol ke ilawa". */
        val notItems: List<String> = emptyList(),
        /** Exactly these records (the ones on screen: "inko", "ye sari"). */
        val ids: Set<String> = emptySet(),
        /** When the record was entered into the app, distinct from the transaction date. */
        val createdFrom: Long? = null,
        val createdTo: Long? = null
    ) {
        /** True when nothing narrows it down (then deleting or changing is refused). */
        val isEmpty get() = from == null && to == null && createdFrom == null && createdTo == null &&
            types.isEmpty() && persons.isEmpty() && cats.isEmpty() && items.isEmpty() && min == null && max == null &&
            last == 0 && !noPeople && notItems.isEmpty() && ids.isEmpty()
    }

    val typeWords = mapOf(
        "expense" to Type.KHARCHA, "income" to Type.AAMDANI, "lent" to Type.DIYA, "gave" to Type.DIYA, "borrowed" to Type.LIYA,
        "took" to Type.LIYA, "got_back" to Type.WAPIS_MILA, "paid_back" to Type.WAPIS_DIYA, "sale" to Type.SALE, "purchase" to Type.PURCHASE
    )
    val catWords = mapOf(
        "food" to Cat.KHANA, "transport" to Cat.TRANSPORT, "rent" to Cat.RENT, "bills" to Cat.BIJLI, "mobile" to Cat.MOBILE,
        "home" to Cat.GHAR, "shopping" to Cat.SHOPPING, "health" to Cat.SEHAT, "personal" to Cat.PERSONAL, "fees" to Cat.FEES,
        "education" to Cat.EDU, "charity" to Cat.CHARITY, "assets" to Cat.ASSETS, "other" to Cat.OTHER, "loans" to Cat.UDHAAR
    )

    /** "2026-10-25" -> start of that day (or its end when [end]). */
    fun day(s: String, end: Boolean = false): Long? {
        val m = Regex("""^(\d{4})-(\d{1,2})-(\d{1,2})""").find(s.trim()) ?: return null
        val (y, mo, d) = m.destructured
        return Calendar.getInstance().apply {
            set(y.toInt(), mo.toInt() - 1, d.toInt(), if (end) 23 else 0, if (end) 59 else 0, if (end) 59 else 0)
            set(Calendar.MILLISECOND, if (end) 999 else 0)
        }.timeInMillis
    }

    private fun samePerson(a: String, b: String): Boolean {
        val x = a.trim().lowercase(); val y = b.trim().lowercase()
        if (x.isEmpty() || y.isEmpty()) return false
        return x == y || x.split(' ').any { it == y } || y.split(' ').any { it == x } ||
            (x.length >= 4 && y.length >= 4 && Normalizer.similarity(x, y) >= 0.88)
    }

    fun select(entries: List<Entry>, f: Filter): List<Entry> {
        val list = entries.filter { e ->
            (f.ids.isEmpty() || e.id in f.ids) && (f.from == null || e.date >= f.from) && (f.to == null || e.date <= f.to) &&
                (f.createdFrom == null || e.createdAt >= f.createdFrom) && (f.createdTo == null || e.createdAt <= f.createdTo) &&
                (f.types.isEmpty() || e.type in f.types) &&
                (f.persons.isEmpty() || f.persons.any { samePerson(e.person, it) }) &&
                (f.cats.isEmpty() || e.category in f.cats) &&
                (f.items.isEmpty() || f.items.any { w -> val q = w.lowercase(); e.item.lowercase().contains(q) || e.note.lowercase().contains(q) }) &&
                (f.min == null || e.amount >= f.min) && (f.max == null || e.amount <= f.max) &&
                (!f.noPeople || (e.person.isBlank() && !Recat.isTransfer(e) && !e.note.startsWith("To ") && !e.note.startsWith("From "))) &&
                (f.notItems.isEmpty() || f.notItems.none { w -> val q = w.lowercase(); e.item.lowercase().contains(q) || e.note.lowercase().contains(q) })
        }.sortedWith(compareByDescending<Entry> { it.date }.thenByDescending { it.createdAt })
        return if (f.last > 0) list.take(f.last) else list
    }

    /** Likely duplicate expense pairs, computed only from saved records (never from AI-generated examples). */
    fun likelyDuplicateExpenses(entries: List<Entry>): List<Pair<Entry, Entry>> {
        val dayMs = 86_400_000L
        fun norm(s: String) = s.trim().lowercase().replace(Regex("""[^\p{L}\p{N}]+"""), " ").trim()
        val grouped = entries.filter { it.type == Type.KHARCHA && it.amount > 0.0 }
            .groupBy { Math.round(it.amount * 100.0) }
        val pairs = mutableListOf<Pair<Entry, Entry>>()
        grouped.values.forEach { group ->
            val ordered = group.sortedBy { it.date }
            for (i in ordered.indices) for (j in i + 1 until ordered.size) {
                val a = ordered[i]
                val b = ordered[j]
                val closeDate = kotlin.math.abs(a.date - b.date) <= 3 * dayMs
                val ca = Calendar.getInstance().apply { timeInMillis = a.date }
                val cb = Calendar.getInstance().apply { timeInMillis = b.date }
                val sameDayOtherYear = ca.get(Calendar.MONTH) == cb.get(Calendar.MONTH) &&
                    ca.get(Calendar.DAY_OF_MONTH) == cb.get(Calendar.DAY_OF_MONTH) &&
                    ca.get(Calendar.YEAR) != cb.get(Calendar.YEAR)
                val itemA = norm(a.item)
                val itemB = norm(b.item)
                val noteA = norm(a.note)
                val noteB = norm(b.note)
                val sameDescription = (itemA.isNotBlank() && itemA == itemB) ||
                    (noteA.isNotBlank() && noteA == noteB) ||
                    (itemA.length >= 4 && itemB.length >= 4 && Normalizer.similarity(itemA, itemB) >= 0.92)
                if ((closeDate || sameDayOtherYear) && sameDescription) {
                    pairs += a to b
                    if (pairs.size >= 100) return pairs
                }
            }
        }
        return pairs.sortedByDescending { maxOf(it.first.date, it.second.date) }
    }

    /** "28 Oct", "25–28 Oct", "October 2026"… for answers. */
    fun label(f: Filter): String {
        val fmt = java.text.SimpleDateFormat("d MMM", java.util.Locale.US)
        val parts = mutableListOf<String>()
        if (f.items.isNotEmpty()) parts += f.items.joinToString(", ")
        if (f.cats.isNotEmpty()) parts += f.cats.joinToString(", ") { it.label }
        if (f.persons.isNotEmpty()) parts += f.persons.joinToString(", ")
        if (f.from != null || f.to != null) {
            val a = f.from?.let { fmt.format(java.util.Date(it)) }
            val b = f.to?.let { fmt.format(java.util.Date(it)) }
            parts += if (a != null && b != null && a != b) "$a – $b" else (a ?: b)!!
        }
        if (f.createdFrom != null || f.createdTo != null) {
            val a = f.createdFrom?.let { fmt.format(java.util.Date(it)) }
            val b = f.createdTo?.let { fmt.format(java.util.Date(it)) }
            parts += "added " + if (a != null && b != null && a != b) "$a – $b" else (a ?: b)!!
        }
        if (f.last > 0) parts += "last ${f.last}"
        if (f.noPeople) parts += "without transfers / people"
        if (f.ids.isNotEmpty()) parts += "${f.ids.size} on screen"
        if (f.notItems.isNotEmpty()) parts += "without " + f.notItems.joinToString(", ")
        return parts.joinToString(" · ").ifBlank { "all" }
    }

    /** The rows a "list" answer shows, in order (the chat remembers them for "3 no entry"). */
    fun shown(list: List<Entry>): List<Entry> = list.take(10)

    /** The answer, worked out by the app: a total, a count or a short list. */
    fun answer(list: List<Entry>, what: String, f: Filter, currency: String): String {
        val head = label(f)
        if (list.isEmpty()) return "Nothing found ($head)."
        return when (what) {
            "count" -> "${list.size} " + (if (list.size == 1) "entry" else "entries") + " ($head)."
            "list" -> {
                val dateFmt = java.text.SimpleDateFormat("d MMM", java.util.Locale.US)
                val rows = shown(list).joinToString("\n") { e ->
                    dateFmt.format(java.util.Date(e.date)) + " · " + (e.item.ifBlank { e.category.label }) +
                        (if (e.person.isNotBlank()) " · " + e.person else "") + " · " + fmt(e.amount) + " " + currency
                } + if (list.size > 10) "\n+${list.size - 10} more" else ""
                rows + "\nTotal: " + answer(list, "sum", f, currency)
            }
            "highest" -> {
                val expenses = list.filter { it.type == Type.KHARCHA }
                val top = expenses.sortedWith(compareByDescending<Entry> { it.amount }.thenByDescending { it.date }).take(5)
                if (top.isEmpty()) "No expenses found ($head)." else {
                    val dateFmt = java.text.SimpleDateFormat("d MMM", java.util.Locale.US)
                    "Highest expenses ($head):\n" +
                        top.joinToString("\n") { e ->
                            dateFmt.format(java.util.Date(e.date)) + " · " + (e.item.ifBlank { e.category.label }) +
                                " · " + fmt(e.amount) + " " + currency
                        } + "\nMatching expense total: " + fmt(expenses.sumOf { it.amount }) + " " + currency +
                        " (${expenses.size} entries)"
                }
            }
            else -> {
                // Totals by kind: "Spent 120 SAR · Lent 50 SAR (4 entries, 25 Oct)".
                val byType = list.groupBy { it.type }.mapValues { (_, v) -> v.sumOf { it.amount } }
                val names = mapOf(Type.KHARCHA to "Spent", Type.AAMDANI to "Income", Type.DIYA to "Lent", Type.LIYA to "Borrowed",
                    Type.WAPIS_MILA to "Got back", Type.WAPIS_DIYA to "Paid back", Type.SALE to "Sales", Type.PURCHASE to "Bought")
                Type.entries.filter { it in byType }.joinToString(" · ") { (names[it] ?: it.label) + " " + fmt(byType[it]!!) + " " + currency } +
                    " (${list.size} " + (if (list.size == 1) "entry" else "entries") + ", $head)"
            }
        }
    }

    private fun fmt(x: Double): String = if (x == Math.floor(x)) String.format(java.util.Locale.US, "%,d", x.toLong())
        else String.format(java.util.Locale.US, "%,.2f", x)

    /** The record with the asked changes: amount, category, date, person, kind, item. */
    fun change(e: Entry, set: Map<String, String>): Entry {
        var x = e
        set["amount"]?.toDoubleOrNull()?.takeIf { it > 0 }?.let { x = x.copy(amount = it) }
        set["category"]?.let { catWords[it.lowercase()] }?.let { x = x.copy(category = it) }
        set["date"]?.let { day(it) }?.let { d ->
            val c = Calendar.getInstance().apply { timeInMillis = e.date }
            val n = Calendar.getInstance().apply {
                timeInMillis = d; set(Calendar.HOUR_OF_DAY, c.get(Calendar.HOUR_OF_DAY)); set(Calendar.MINUTE, c.get(Calendar.MINUTE))
            }.timeInMillis
            x = x.copy(date = n)
        }
        set["person"]?.trim()?.takeIf { it.isNotEmpty() }?.let { x = x.copy(person = it.replaceFirstChar { c -> c.uppercase() }) }
        set["type"]?.let { typeWords[it.lowercase()] }?.let { t ->
            x = x.copy(type = t, category = if (isLoan(t)) Cat.UDHAAR else if (x.category == Cat.UDHAAR) Cat.OTHER else x.category)
        }
        set["item"]?.trim()?.takeIf { it.isNotEmpty() }?.let { x = x.copy(item = it) }
        set["note"]?.trim()?.takeIf { it.isNotEmpty() }?.let { x = x.copy(note = it.take(500)) }
        return x
    }
}
