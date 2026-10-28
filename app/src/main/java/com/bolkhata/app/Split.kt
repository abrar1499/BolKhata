package com.bolkhata.app

import kotlin.math.round

/**
 * A shared bill: "khana 30 ali bilal ke sath", "hum 3 ne 30 ka khana khaya", "khana 30 ali 12 bilal 8",
 * "ali ne pay kiya khana 30 sath". Your own share is the expense; what the others owe you is To receive
 * (lent to them), or, when someone else paid, your share is To pay to them. Fully offline.
 */
object Split {

    class Plan(
        /** The bill as the parser read it (amount = the whole bill, category / item / date). */
        val bill: Entry,
        /** Who paid: null = you. */
        val payer: String?,
        /** The others, as said (payer included when someone else paid). */
        val names: List<String>,
        /** Shares said for a person ("ali 12"). */
        val shares: Map<String, Double>,
        /** How many people ate, you included, when said ("hum 3", "teeno"); 0 when not said. */
        val people: Int
    ) {
        /** How many names are still missing: 0 = none; -1 = not known how many (ask "who was with you?"). */
        val missing: Int get() = when {
            people > 0 -> (people - 1 - names.size).coerceAtLeast(0)
            names.isEmpty() -> -1
            else -> 0
        }
        fun with(more: List<String>) = Plan(bill, payer, (names + more).distinctBy { it.lowercase() }, shares, people)
    }

    private val splitWords = setOf(
        "split", "sath", "saath", "satth", "with", "milke", "milkar", "milkr", "hissa", "hisa", "share", "shared",
        "teeno", "teenon", "tino", "tinon", "dono", "donon", "charo", "charon", "chaaron", "paancho", "panchon"
    )
    private val groupWords = mapOf(
        "teeno" to 3, "teenon" to 3, "tino" to 3, "tinon" to 3, "dono" to 2, "donon" to 2,
        "charo" to 4, "charon" to 4, "chaaron" to 4, "paancho" to 5, "panchon" to 5
    )
    private val peopleWords = setOf("bande", "bando", "bandon", "banday", "log", "logon", "loug", "dost", "doston", "afrad", "people", "persons", "admi", "aadmi")
    private val paidWords = setOf("pay", "paid", "payment", "bhara", "bhare", "bill")
    private val meWords = setOf("mene", "maine", "mainay", "men", "main", "mein", "me", "i")
    /** Words that make it a loan, not a shared bill: "ali ko", "ali se", "ali ne", "ali ka". */
    private val loanMarkers = setOf("ko", "se", "sy", "ne", "nay", "ny", "ka", "ki", "ke", "kay", "udhar", "udhaar", "qarz", "wapis",
        "wapas", "liye", "liya", "lia", "diye", "diya", "dia", "lent", "borrowed", "gave", "took", "salary", "tankhwah")
    private val joinWords = setOf("aur", "or", "and", "&", ",")
    private val stop = setOf(
        "hum", "ham", "humne", "hamne", "ne", "ko", "ke", "ka", "ki", "kay", "se", "sy", "mera", "mere", "meri", "my", "with",
        "kiya", "kia", "kiye", "kie", "kya", "kar", "kr", "karo", "diya", "dia", "diye", "de", "tha", "the", "thi", "hai", "hain",
        "khaya", "khaaya", "khai", "piya", "pi", "liya", "lia", "sab", "sabne", "sb", "riyal", "riyals", "sar", "rs", "rupay",
        "aaj", "kal", "parson", "subah", "shaam", "raat", "dopahar", "abhi", "wala", "wali", "wale", "bhi", "b", "to", "per", "par"
    ) + splitWords + peopleWords + paidWords + meWords + joinWords

    private fun isNum(w: String) = w.isNotEmpty() && w.all { it.isDigit() || it == '.' || it == ',' } && w.any { it.isDigit() }
    private fun num(w: String) = w.replace(",", "").toDoubleOrNull()
    private fun cap(w: String) = w.lowercase().replaceFirstChar { it.uppercase() }

    /** A word that is a person: a saved contact (spelling or sound) or a common name (fixed: "ashrf" -> "Ashraf"). */
    private fun person(w: String, contacts: Collection<String>): String? {
        if (w.length < 3 || w in stop || isNum(w)) return null
        contacts.firstOrNull { it.equals(w, true) }?.let { return it }
        Understand.matchContact(listOf(w), contacts).first?.let { return it }
        Understand.fixName(w)?.let { return it }
        if (Understand.isCommonName(w)) return cap(w)
        return null
    }

    /** Is this word something to eat or buy ("khana", "pizza") rather than a name? */
    private fun isThing(w: String): Boolean =
        Parser.parse("$w 1").entries.firstOrNull()?.let { it.type == Type.KHARCHA && it.category != Cat.OTHER } == true

    private fun tokens(text: String) = text.replace(",", " , ").split(Regex("\\s+")).filter { it.isNotBlank() }

    /** A shared bill said in the sentence, or null when it is an ordinary entry. */
    fun detect(text: String, contacts: Collection<String> = emptyList()): Plan? {
        val raw = tokens(text)
        val ws = raw.map { it.lowercase() }
        // Count of people: "hum 3", "3 bande", "teeno".
        var people = 0
        val drop = mutableSetOf<Int>()
        ws.forEachIndexed { i, w ->
            groupWords[w]?.let { people = it; drop += i }
            if (isNum(w) && w.none { it == '.' }) {
                val n = w.toInt()
                val before = ws.getOrNull(i - 1); val after = ws.getOrNull(i + 1)
                if (n in 2..20 && (before == "hum" || before == "ham" || after in peopleWords)) {
                    people = n; drop += i
                }
            }
        }
        // Who paid: "ali ne pay kiya" / "mene pay kiya".
        var payer: String? = null
        ws.forEachIndexed { i, w ->
            if (w in paidWords) {
                val who = (i - 1 downTo maxOf(0, i - 3)).map { it to ws[it] }.firstOrNull { (_, x) -> x !in setOf("ne", "nay", "ny") }
                if (who != null && who.second !in meWords) person(who.second, contacts)?.let { payer = it; drop += who.first }
                drop += i
            }
        }
        val said = people > 0 || ws.any { it in splitWords }
        // Without a split word: "khana 30 ali 12 bilal 8" (names with shares), or food with a name and no
        // ko / se / ne ("khana ali 40") is a shared bill.
        val foodWithName = !said && ws.none { it in loanMarkers } && ws.count { isNum(it) } == 1
        if (!said && !foodWithName && payer == null && ws.count { isNum(it) } < 3) return null


        // Names: contacts / common names; an unknown word joined to a name by "aur"/","; a name with a number is its share.
        val names = mutableListOf<String>()
        val shares = mutableMapOf<String, Double>()
        val isName = BooleanArray(ws.size)
        ws.forEachIndexed { i, w -> if (i !in drop && person(w, contacts) != null) isName[i] = true }
        ws.forEachIndexed { i, w ->
            if (isName[i] || i in drop || w in stop || isNum(w) || w.length < 3 || w.any { it.isDigit() }) return@forEachIndexed
            val joined = (ws.getOrNull(i - 1) in joinWords && isName.getOrElse(i - 2) { false }) ||
                (ws.getOrNull(i + 1) in joinWords && isName.getOrElse(i + 2) { false }) ||
                (ws.getOrNull(i + 1) in setOf("ke", "ki", "kay") && ws.getOrNull(i + 2) in setOf("sath", "saath"))
            if (joined && !isThing(w)) isName[i] = true
        }
        var numbersLeft = ws.withIndex().count { (i, w) -> isNum(w) && i !in drop }
        ws.forEachIndexed { i, w ->
            if (!isName[i]) return@forEachIndexed
            val name = person(w, contacts) ?: cap(raw[i])
            if (names.none { it.equals(name, true) }) names += name
            drop += i
            val next = ws.getOrNull(i + 1)
            // "ali 12" is Ali's share only when another number is left for the bill ("khana ali 40" = bill 40).
            if (next != null && isNum(next) && numbersLeft > 1) { num(next)?.let { shares[name] = it }; drop += i + 1; numbersLeft-- }
        }
        payer?.let { p -> if (names.none { it.equals(p, true) }) names += p }

        // What's left is the bill itself: "khana 30".
        val rest = raw.filterIndexed { i, w -> i !in drop && w.lowercase() !in splitWords && w.lowercase() !in peopleWords && w.lowercase() !in setOf("hum", "ham") }
            .joinToString(" ")
        val e = Parser.parse(rest).entries.firstOrNull() ?: return null
        val bill = e.copy(type = Type.KHARCHA, person = "", category = if (e.category == Cat.UDHAAR) Cat.OTHER else e.category,
            item = if (isLoan(e.type)) e.item.takeIf { it != "Loans" } ?: "Shared bill" else e.item)
        if (bill.amount <= 0) return null
        if (!said && payer == null && shares.size < 2 && !(foodWithName && names.isNotEmpty() && bill.category == Cat.KHANA)) return null
        // "nadeem ne pay kiya khana 40" (no sath / with): only I ate, he paid it all.
        if (payer != null && !said && names.all { it.equals(payer, true) }) return Plan(bill, payer, names, shares, 1)
        return Plan(bill, payer, names, shares, people)
    }

    /** The names said in an answer to "who was with you?": "ali aur bilal" -> [Ali, Bilal]. */
    fun namesIn(answer: String, contacts: Collection<String> = emptyList()): List<String> =
        tokens(answer).map { it.lowercase() }.filter { it.length >= 2 && it !in stop && !isNum(it) }
            .map { w -> person(w, contacts) ?: cap(w) }.distinctBy { it.lowercase() }

    private fun r2(x: Double) = round(x * 100) / 100

    /** The entries for a plan: your share as the expense, and the others' shares in their khata. */
    fun entries(plan: Plan): List<Entry> {
        val b = plan.bill
        val total = b.amount
        // Someone paid for what only I had: it's my expense, and I owe them all of it.
        if (plan.people == 1 && plan.payer != null) {
            val what = b.item.ifBlank { b.category.label }
            return listOf(
                b.copy(id = "e" + System.nanoTime() + "s0", note = "Paid by ${plan.payer}"),
                b.copy(id = "e" + System.nanoTime() + "s1", type = Type.LIYA, category = Cat.UDHAAR, item = what, person = plan.payer,
                    note = "${plan.payer} paid my $what")
            )
        }
        val others = plan.names
        val count = maxOf(plan.people, others.size + 1)
        val fixed = plan.shares.values.sum()
        val equal = r2((total - fixed) / (count - plan.shares.size).coerceAtLeast(1))
        val shareOf = others.associateWith { plan.shares[it] ?: equal }
        val mine = r2(total - shareOf.values.sum() - equal * (count - 1 - others.size).coerceAtLeast(0))
        val what = b.item.ifBlank { b.category.label }
        var n = 0
        fun id() = "e" + System.nanoTime() + "s" + (n++)
        val list = mutableListOf<Entry>()
        val withText = "shared " + fmtAmount(total) + " with " + others.joinToString(", ")
        if (mine > 0) list += b.copy(id = id(), amount = mine, note = "My share · $withText")
        val payer = plan.payer
        if (payer == null) {
            // You paid: each one owes you their share.
            shareOf.forEach { (who, amt) ->
                if (amt > 0) list += b.copy(id = id(), type = Type.DIYA, amount = amt, category = Cat.UDHAAR, item = what, person = who,
                    note = "$who's share of $what (" + fmtAmount(total) + ")")
            }
        } else if (mine > 0) {
            // Someone else paid: you owe them your share.
            list += b.copy(id = id(), type = Type.LIYA, amount = mine, category = Cat.UDHAAR, item = what, person = payer,
                note = "$payer paid $what (" + fmtAmount(total) + "), my share")
        }
        return list
    }

    private fun fmtAmount(x: Double) = if (x == Math.floor(x)) x.toLong().toString() else x.toString()
}
