package com.bolkhata.app

import java.util.Calendar

/**
 * Answers questions about the khata without internet:
 * "hamza ko total kitne de diye", "is mahine food pe kitna kharcha hua", "aaj kitna kharcha hua".
 */
object Questions {

    class Answer(val text: String, val search: String?)

    /** Special answers that open the "To pay" or "To receive" list instead of a search. */
    const val PAY = "@pay"
    const val RECEIVE = "@receive"

    private val questionWords = listOf(
        "kitna", "kitne", "kitni", "kitnay", "kitny", "total", "how much", "how many", "hisaab", "hisab",
        "balance", "baqi", "baki", "bacha", "bache", "bachi", "kul", "sum", "stock", "nafa", "munafa", "profit", "faida",
        "nuqsan", "loss", "کتنا", "کتنے", "کتنی"
    )

    private val filler = setOf(
        "ko", "ki", "ka", "ke", "se", "ne", "pe", "par", "mein", "me", "main", "total", "kitna", "kitne", "kitni", "kitnay",
        "kitny", "de", "diye", "diya", "di", "dye", "liye", "liya", "li", "hua", "huay", "hue", "hain", "hai", "tha", "thay",
        "the", "kya", "kia", "how", "much", "many", "did", "i", "give", "gave", "spend", "spent", "on", "to", "from", "is",
        "this", "month", "mahine", "mahina", "saal", "year", "aaj", "today", "kal", "yesterday", "pichle", "pichhle",
        "last", "kharcha", "kharch", "expense", "expenses", "hisaab", "hisab", "balance", "baqi", "baki", "bacha", "bache",
        "kul", "sum", "abhi", "tak", "ab", "mujhe", "maine", "mene", "men", "my", "mera", "meri", "mere", "wapis", "wapas",
        "lena", "dena", "hai?", "so", "far", "all", "time", "sab", "poora", "pura", "income", "aamdani", "kamaya", "kamai",
        "aayi", "aaya", "aaye", "ayi", "aya", "laga", "lagi", "lage", "huwa", "hui", "lagaya", "kharchi", "kharcha?", "mahiny",
        "mahinay", "total?", "ab", "tak", "es", "iss", "us", "is"
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
            words.any { it in setOf("mahine", "mahina", "mahiny", "mahinay", "month") } -> Period.MONTH
            words.contains("saal") || words.contains("year") -> Period.YEAR
            else -> null
        }

        val period0 = asked ?: Period.MONTH
        val inPeriod0 = entries.filter { inPeriod(it.date, period0) }

        // Profit: "is mahine kitna nafa hua"
        if (words.any { it in setOf("nafa", "munafa", "profit", "faida", "fayda", "nuqsan", "loss") }) {
            val sales = inPeriod0.filter { it.type == Type.SALE }.sumOf { it.amount }
            val income = inPeriod0.filter { it.type == Type.AAMDANI }.sumOf { it.amount }
            val bought = inPeriod0.filter { it.type == Type.PURCHASE }.sumOf { it.amount }
            val spent = inPeriod0.filter { it.type == Type.KHARCHA }.sumOf { it.amount }
            val profit = sales + income - bought - spent
            return Answer(
                (if (profit >= 0) "Profit" else "Loss") + " ${period0.label}: ${money(currency, kotlin.math.abs(profit))} " +
                    "(sales ${fmt(sales)} + income ${fmt(income)} − purchases ${fmt(bought)} − expenses ${fmt(spent)})",
                null
            )
        }

        // Stock: "eint kitni bachi", "stock kitna hai"
        val stockWords = setOf("stock", "bacha", "bachi", "bache", "baqi", "baki", "remaining", "left")
        if (words.any { it in stockWords } && entries.any { it.qty > 0 }) {
            val lines = stock(entries)
            val asked2 = words.filter { it !in filler && it !in stockWords && it.length >= 3 }
            val pick = if (asked2.isEmpty()) lines else lines.filter { l -> asked2.any { l.name.lowercase().contains(it) } }
            if (pick.isNotEmpty()) {
                return Answer(
                    "In stock: " + pick.joinToString(", ") { it.name + " " + fmt(it.qty) + (if (it.unit.isBlank()) "" else " " + it.unit) },
                    asked2.firstOrNull()
                )
            }
        }

        // Sales or purchases total: "aaj kitni bikri hui", "is mahine kitni khareed hui"
        val salesQ = words.any { it in setOf("bikri", "sale", "sales", "becha", "bechi") }
        val buysQ = words.any { it in setOf("khareed", "kharid", "purchase", "purchases", "maal") }
        if ((salesQ || buysQ) && entries.none { it.person.isNotBlank() && words.contains(it.person.trim().lowercase()) }) {
            val t2 = if (salesQ) Type.SALE else Type.PURCHASE
            val list = inPeriod0.filter { it.type == t2 }
            return Answer(
                (if (salesQ) "Sales" else "Purchases") + " ${period0.label}: ${money(currency, list.sumOf { it.amount })} (${list.size} entries)",
                null
            )
        }

        // 0. Loans overall: "maine total kitna karza liya / wapis karna hai", "mujhe kitne paise lene hain"
        val loanTalk = words.any { it in setOf("karza", "karz", "qarz", "qarza", "udhaar", "udhar", "loan", "loans", "debt", "owe", "owed") } ||
            Regex("""wap[ai]s\s*(karna|krna|kerna|dena)""").containsMatchIn(t) || t.contains("lene hain") || t.contains("lena hai") ||
            t.contains("dene hain") || t.contains("dena hai") || t.contains("milne hain") || t.contains("to pay") || t.contains("to receive")
        if (loanTalk && entries.none { it.person.isNotBlank() && words.contains(it.person.trim().lowercase()) }) {
            val bal = balances(entries)
            val pay = -bal.values.filter { it < 0 }.sum()
            val receive = bal.values.filter { it > 0 }.sum()
            val wantsPay = words.any { it in setOf("liya", "liye", "li", "took", "borrowed", "dena", "dene", "owe", "debt") } ||
                Regex("""wap[ai]s\s*(karna|krna|kerna|dena)""").containsMatchIn(t) || t.contains("to pay")
            val wantsReceive = words.any { it in setOf("lena", "lene", "milne", "milna", "diya", "diye", "lent", "owed") } ||
                t.contains("to receive")
            return when {
                wantsPay && !wantsReceive -> Answer("You will pay ${money(currency, pay)} in total", PAY)
                wantsReceive && !wantsPay -> Answer("People will pay you ${money(currency, receive)} in total", RECEIVE)
                else -> Answer("You will pay ${money(currency, pay)} · People will pay you ${money(currency, receive)}", PAY)
            }
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

        // 3. A category or item ("food", "petrol", "cheeni", "eint", "mobile")
        if (rest.isNotEmpty()) {
            val query = rest.joinToString(" ")
            val byCat = Cat.entries.firstOrNull { c -> rest.any { w -> c.label.lowercase().split(" ", "&", "/").any { it == w } } }
            // Exact words in the item or note come first ("cheeni" means cheeni, not all groceries).
            val textHits = inPeriod.filter { e -> rest.any { w -> e.item.lowercase().contains(w) || e.note.lowercase().contains(w) } }
            val guess = Parser.guessCategory(query)
            val list = when {
                byCat != null -> inPeriod.filter { it.category == byCat }
                textHits.isNotEmpty() -> textHits
                guess != null -> inPeriod.filter { it.type == Type.KHARCHA && it.category == guess.first }
                else -> emptyList()
            }
            if (list.isEmpty() && byCat == null && guess == null) return null
            val label = byCat?.label ?: query.replaceFirstChar { it.uppercase() }
            val spent = list.filter { it.type == Type.KHARCHA }.sumOf { it.amount }
            val other = list.filter { it.type != Type.KHARCHA }.sumOf { it.amount }
            val qty = list.filter { it.qty > 0 }.groupBy { it.unit }.map { (u, v) -> fmt(v.sumOf { it.qty }) + (if (u.isBlank()) "" else " $u") }
            val qtyText = if (qty.isEmpty()) "" else " · qty " + qty.joinToString(", ")
            val moneyText = if (spent > 0 || other == 0.0) money(currency, spent + other) else money(currency, other)
            return Answer(
                "$label ${period.label}: $moneyText$qtyText (${list.size} " + (if (list.size == 1) "entry" else "entries") + ")",
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
        val mine = entries.filter { it.person.trim().equals(person, true) && withPerson(it) && inPeriod(it.date, period) }
        fun sum(t: Type) = mine.filter { it.type == t }.sumOf { it.amount }
        val gave = sum(Type.DIYA)
        val took = sum(Type.LIYA)
        val gotBack = sum(Type.WAPIS_MILA)
        val paidBack = sum(Type.WAPIS_DIYA)
        val all = entries.filter { it.person.trim().equals(person, true) && withPerson(it) }
        val bal = balances(all)[person] ?: balances(all).entries.firstOrNull { it.key.equals(person, true) }?.value ?: 0.0
        val parts = mutableListOf<String>()
        if (gave > 0) parts += "you gave ${money(currency, gave)}"
        if (gotBack > 0) parts += "got back ${money(currency, gotBack)}"
        if (took > 0) parts += "you took ${money(currency, took)}"
        if (paidBack > 0) parts += "paid back ${money(currency, paidBack)}"
        val sold = sum(Type.SALE)
        val bought = sum(Type.PURCHASE)
        if (sold > 0) parts += "sold ${money(currency, sold)}"
        if (bought > 0) parts += "bought ${money(currency, bought)}"
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
