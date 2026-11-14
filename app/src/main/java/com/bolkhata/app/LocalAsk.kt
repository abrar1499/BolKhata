package com.bolkhata.app

import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * Questions: totals, balances, what is owed, the report, lists, breakdowns, PDFs, who is in an account. The message is
 * read by rule into the AI's picking fields (types, categories, persons, words, from, to) and answered with the same
 * tools, so every figure comes from the records. A short follow-up ("detail", "aur food?", "kal ka?") goes on from the
 * last look-up.
 */
object LocalAsk : LocalAgent.Reader {
    override fun read(s: LocalAgent.Said): LocalAgent.Reply? {
        // "khan wala" after "Which Salim do you mean…?": the question asked again with that name, or not a question at all.
        LocalAgent.picked(s)?.let { return read(s.again(it)) }
        val m = Msg(s)
        val who = people(m)
        val prev = s.previous.takeIf { it.isNotBlank() }?.let { runCatching { JSONObject(it) }.getOrNull() }
        val p = m.pick(who)
        // A saved name alone ("hamid", "nargis?") opens that person's account.
        val bare = who.names.isNotEmpty() && m.words.indices.all { it in who.at || m.filler(it) || m.canon[it] in setOf("khata", "hisab") }
        if (!bare && !m.asking(who, p, prev != null)) return null
        who.unsure?.let { (word, choices) -> return LocalAgent.Reply("Which ${cap(word)} do you mean: ${or(choices)}?") }
        return when {
            m.has("pdf") -> pdf(s, m, p, prev)
            m.has("rate") -> rate(s, p)
            m.aboutAccounts(who) -> accounts(s, m, who.names)
            m.has("profit", "loss", "worth") || (m.has("report") && who.names.isEmpty()) -> report(s, m)
            who.names.isNotEmpty() && m.aboutBalance(p, prev != null) -> balance(s, m, p, bare)
            who.names.isEmpty() && m.aboutOwing() -> owed(s, m)
            who.names.isEmpty() && m.aboutCash(p) -> cash(s)
            else -> look(s, m, p, prev)
        }
    }

    // ---------------------------------------------------------------- the answers

    /** Totals, lists, breakdowns: [AiAgent] totals for the figures, then show for the card. */
    private fun look(s: LocalAgent.Said, m: Msg, fresh: Pick, prev: JSONObject?): LocalAgent.Reply? {
        val follow = prev != null && m.followUp(fresh)
        val p = if (follow) Pick.of(prev!!).with(fresh) else fresh
        if (!p.what && p.persons.isEmpty() && fresh.unknown.isNotEmpty()) return LocalAgent.Reply("Nothing found for ${fresh.unknown.joinToString(" ") { cap(it) }}.")
        var view = m.view(p)
        // Nothing to look up ("kitna?" out of the blue): the chat's older rules answer.
        if (!follow && !p.any && view == "total" && !m.has("hisab", "batao", "dikhao", "total")) return null
        // "kahan kahan laga", "mahana", "sab se bara": spending, unless the whole account ("hisab") is asked.
        if (!follow && !m.has("hisab") && view in setOf("breakdown", "by_month", "highest", "by_item") && p.types.isEmpty() && !p.what && p.persons.isEmpty()) p.types = listOf("expense")
        if (view == "breakdown" && p.persons.isNotEmpty() && p.types.isEmpty() && !p.what) view = "list"
        val t = s.use("totals", p.json().put("group_by", "type"))
        val n = t.optInt("count")
        if (n == 0) return LocalAgent.Reply(nothing(p, s.now))
        if (view == "total" && t.has("qty_total")) goods(s, p, t)?.let { return it }
        if (view == "total" && p.persons.isNotEmpty()) return LocalAgent.Reply(sentence(p, t, s))
        s.use("show", p.json().put("view", view))
        val said = when (view) {
            "list", "count" -> "$n " + (if (n == 1) "entry" else "entries") + about(p) + period(p, s.now) + "."
            "highest" -> "Your biggest expenses" + period(p, s.now) + "."
            "by_month" -> "Month by month: " + low(sentence(p, t, s))
            "by_person" -> "By person: " + low(sentence(p, t, s))
            "by_item" -> "By item: " + low(sentence(p, t, s))
            else -> sentence(p, t, s)
        }
        return LocalAgent.Reply(said)
    }

    /** Counted work or goods ("kitni bharai ki"): how many and what they came to, item by item. */
    private fun goods(s: LocalAgent.Said, p: Pick, t: JSONObject): LocalAgent.Reply? {
        val g = s.use("totals", p.json().put("group_by", "item")).optJSONArray("groups") ?: return null
        val rows = (0 until g.length()).map { g.getJSONObject(it) }
        if (rows.isEmpty() || rows.any { !it.has("qty_total") }) return null
        val kinds = groups(t).map { it.first }
        val who = p.persons.joinToString(" and ")
        val head = when {
            kinds == listOf("work") && who.isNotBlank() -> "$who's work: "
            kinds == listOf("sale") && who.isNotBlank() -> "Sold to $who: "
            kinds == listOf("work") -> "Work: "
            kinds == listOf("sale") -> "Sold: "
            who.isNotBlank() -> "$who: "
            else -> ""
        }
        val things = rows.joinToString(", ") { num(it.optDouble("qty_total")) + " " + it.optString("name") }
        return LocalAgent.Reply(head + things + ", " + money(t.optDouble("total"), s.host.currency) + period(p, s.now) + ".")
    }

    /** "Hamid Akram owes you 30,000 PKR." With "hisab" or "khata" also the card of their entries, the balance on top. */
    private fun balance(s: LocalAgent.Said, m: Msg, p: Pick, bare: Boolean): LocalAgent.Reply {
        val cur = s.host.currency
        val rows = list(s.use("balances", JSONObject()).optJSONArray("people"))
        val said = mutableListOf<String>()
        val top = mutableListOf<String>()
        for (name in p.persons) {
            val row = rows.firstOrNull { it.optString("name").trim().equals(name.trim(), true) }
            val amount = money(row?.optDouble("amount") ?: 0.0, cur)
            when {
                row == null -> said += if (s.use("totals", JSONObject().put("persons", JSONArray(listOf(name)))).optInt("count") == 0) "No entries for $name yet" else "$name's account is settled"
                row.optString("means") == "owes the owner" -> { said += "$name owes you $amount"; top += "$name will pay you $amount" }
                else -> { said += "You owe $name $amount"; top += "You will pay $name $amount" }
            }
        }
        val pick = Pick(persons = p.persons, from = p.from, to = p.to).json()
        if (bare || m.has("hisab", "khata", "kholo", "dikhao", "list", "entries", "detail")) {
            s.use("show", pick.put("view", "list"))
            if (s.notes.card.isNotBlank() && top.isNotEmpty()) s.notes.card = top.joinToString("\n") + "\n" + s.notes.card
        } else s.use("totals", pick) // so "detail" next goes on from these people
        return LocalAgent.Reply(said.joinToString(". ") + ".")
    }

    /** Who owes the owner, whom the owner owes, or both: a card of people with what is owed. */
    private fun owed(s: LocalAgent.Said, m: Msg): LocalAgent.Reply {
        val cur = s.host.currency
        val side = when {
            // "kis se lene hain" / "kis ne dene hain": they owe the owner; "kisko dene hain": the owner owes.
            m.has("lene") -> if (m.kisRole == "by") -1 else 1
            m.has("dene") -> if (m.kisRole == "by") 1 else -1
            m.has("owe") -> if (m.iOwe) -1 else 1
            else -> 0
        }
        var rows = list(s.use("balances", JSONObject()).optJSONArray("people"))
        val book = m.book()
        if (book != null) {
            val members = list(s.use("accounts", JSONObject()).optJSONArray("party")).firstOrNull { it.optString("name").equals(book, true) }
                ?.optJSONArray("people")?.let { a -> (0 until a.length()).map { a.optString(it).trim().lowercase() } }.orEmpty()
            rows = rows.filter { it.optString("name").trim().lowercase() in members }
        }
        val get = rows.filter { it.optString("means") == "owes the owner" }
        val pay = rows - get.toSet()
        val inBook = book?.let { " in $it" }.orEmpty()
        val shown = when (side) { 1 -> get; -1 -> pay; else -> get + pay }
        if (shown.isEmpty()) return LocalAgent.Reply(when (side) { 1 -> "Nobody$inBook owes you anything."; -1 -> "You owe nobody$inBook."; else -> "Every account$inBook is settled." })
        val all = s.host.entries()
        val setup = s.host.setup()
        fun sum(l: List<JSONObject>) = money(l.sumOf { it.optDouble("amount") }, cur)
        val lines = shown.take(30).joinToString("\n") { r ->
            val name = r.optString("name")
            val sub = if (side == 0) (if (r in get) "To get" else "To give") else Sides.bookOf(name, all, setup)
            "$name · $sub · " + money(r.optDouble("amount"), cur)
        }
        val more = if (shown.size > 30) "\n+${shown.size - 30} more" else ""
        val total = when (side) { 1 -> "To get " + sum(get); -1 -> "To give " + sum(pay); else -> listOfNotNull(get.takeIf { it.isNotEmpty() }?.let { "To get " + sum(it) }, pay.takeIf { it.isNotEmpty() }?.let { "To give " + sum(it) }).joinToString(" · ") }
        s.notes.card = (when (side) { 1 -> "To get"; -1 -> "To give"; else -> "Balances" }) + inBook + ":\n" + lines + more + "\nTotal: " + total
        fun people(n: Int) = if (n == 1) "1 person" else "$n people"
        return LocalAgent.Reply(when (side) {
            1 -> people(get.size) + inBook + (if (get.size == 1) " owes" else " owe") + " you " + sum(get) + "."
            -1 -> "You owe " + people(pay.size) + inBook + " " + sum(pay) + "."
            else -> listOfNotNull(get.takeIf { it.isNotEmpty() }?.let { "People$inBook owe you " + sum(it) }, pay.takeIf { it.isNotEmpty() }?.let { "you owe " + sum(it) }).joinToString("; ").let(::cap) + "."
        })
    }

    /** Who sits in an account, the accounts there are, or which account a person is in. */
    private fun accounts(s: LocalAgent.Said, m: Msg, persons: List<String>): LocalAgent.Reply {
        val r = s.use("accounts", JSONObject())
        val party = list(r.optJSONArray("party")).map { b -> b.optString("name") to strings(b.optJSONArray("people")) }
        if (persons.isNotEmpty()) return LocalAgent.Reply(persons.joinToString(". ") { n ->
            party.firstOrNull { (_, ppl) -> ppl.any { it.trim().equals(n.trim(), true) } }?.let { "$n is in ${it.first}" } ?: "$n is in no account yet"
        } + ".")
        val book = m.book(party.map { it.first })
        if (book != null) {
            val ppl = party.firstOrNull { it.first.equals(book, true) }?.second.orEmpty()
            return LocalAgent.Reply(if (ppl.isEmpty()) "No one is in $book yet." else "$book (${ppl.size}): ${ppl.joinToString(", ")}.")
        }
        val expense = strings(r.optJSONArray("expense"))
        val income = strings(r.optJSONArray("income"))
        return LocalAgent.Reply(when {
            m.has("kharcha") -> if (expense.isEmpty()) "No expense accounts yet." else "Expense accounts: ${expense.joinToString(", ")}."
            m.has("income") -> if (income.isEmpty()) "No income accounts yet." else "Income accounts: ${income.joinToString(", ")}."
            party.isEmpty() -> "No people accounts yet."
            else -> party.joinToString(". ") { (b, ppl) -> "$b: " + ppl.joinToString(", ").ifBlank { "no one" } } + "."
        })
    }

    /** Profit or loss for the period, and net worth today: the report tool, shown as a card. */
    private fun report(s: LocalAgent.Said, m: Msg): LocalAgent.Reply {
        val cur = s.host.currency
        val input = JSONObject().apply { m.from?.let { put("from", it) }; m.to?.let { put("to", it) } }
        val r = s.use("report", input)
        val per = r.optJSONObject("period") ?: return LocalAgent.Reply("The report could not be made.")
        val now = r.optJSONObject("today") ?: JSONObject()
        fun v(o: JSONObject, k: String) = money(o.optDouble(k, 0.0), cur)
        val profit = per.has("profit")
        val pl = (if (profit) "Profit " else "Loss ") + v(per, if (profit) "profit" else "loss")
        val within = period(Pick(from = m.from, to = m.to), s.now)
        val plLine = "Profit and loss, " + within.trim().ifBlank { "all time" } + ": $pl · Sales ${v(per, "sales")} · Other income ${v(per, "other_income")} · " +
            "Business costs ${v(per, "business_costs")} · Expenses ${v(per, "expenses")}"
        val worth = "Net worth ${v(now, "net_worth")} · Cash in hand ${v(now, "cash_in_hand")} · To get ${v(now, "to_get")} · Stock ${v(now, "stock_at_cost")} · " +
            "Assets ${v(now, "assets")} · To pay ${v(now, "to_pay")}"
        val onlyWorth = m.has("worth") && !m.has("profit", "loss", "report")
        s.notes.card = when {
            onlyWorth -> "Net worth: $worth"
            m.has("report") -> "$plLine\nToday: $worth"
            else -> plLine
        }
        s.notes.filter = input.toString()
        return LocalAgent.Reply(if (onlyWorth) "Net worth ${v(now, "net_worth")}." else "$pl$within.")
    }

    /** "Hamid ka rate kya hai", "kachi eent ka rate": the last rate saved for each work or thing, as the AI is told it. */
    private fun rate(s: LocalAgent.Said, p: Pick): LocalAgent.Reply {
        val found = s.host.entries().filter { e ->
            e.rate > 0 && (p.persons.isEmpty() || p.persons.any { it.equals(e.person.trim(), true) }) &&
                (p.words.isEmpty() || p.words.any { e.item.replace(" ", "").contains(it.replace(" ", ""), true) })
        }.sortedByDescending { it.date }.distinctBy { it.item.trim().lowercase() + "|" + it.type }
        val whose = p.persons.joinToString(" and ").ifBlank { p.words.joinToString(", ") { cap(it) } }
        if (found.isEmpty()) return LocalAgent.Reply("No rate is saved" + (if (whose.isBlank()) "" else " for $whose") + " yet.")
        val mine = p.persons.isNotEmpty()
        return LocalAgent.Reply((if (mine) "$whose's rate: " else "") + found.take(5).joinToString("; ") { e ->
            e.item + (if (mine) " " else ": ") + money(e.rate, s.host.currency) + (if (e.unit == "1000") " per 1000" else " each") +
                (if (!mine && e.person.isNotBlank()) " (${e.person.trim()})" else "")
        } + ".")
    }

    /** "Cash kitna hai": money in hand, from the balances tool. */
    private fun cash(s: LocalAgent.Said): LocalAgent.Reply =
        LocalAgent.Reply("Cash in hand: " + money(s.use("balances", JSONObject()).optDouble("cash_in_hand", 0.0), s.host.currency) + ".")

    /** A PDF of what was asked (or of the last look-up) to share. */
    private fun pdf(s: LocalAgent.Said, m: Msg, fresh: Pick, prev: JSONObject?): LocalAgent.Reply {
        val p = if (!fresh.any && prev != null) Pick.of(prev).with(fresh) else fresh
        val title = when {
            p.persons.isNotEmpty() -> p.persons.joinToString(", ")
            p.what -> (p.cats + p.words.map(::cap)).joinToString(", ")
            p.types.size == 1 -> titles[p.types[0]] ?: "Entries"
            else -> "Entries"
        }
        val input = p.json().put("title", title)
        when {
            m.monthly -> input.put("group_by", "month")
            m.byPerson -> input.put("group_by", "person")
            m.has("category", "kahan") -> input.put("group_by", "category")
        }
        val r = s.use("make_pdf", input)
        if (r.has("error")) return LocalAgent.Reply("No entries match, so no PDF was made.")
        return LocalAgent.Reply("The PDF of $title is ready (${r.optInt("entries")} " + (if (r.optInt("entries") == 1) "entry" else "entries") + ").")
    }

    // ---------------------------------------------------------------- sentences

    private val titles = mapOf("expense" to "Expenses", "income" to "Income", "lent" to "Money given", "received_back" to "Money received",
        "borrowed" to "Borrowed", "paid_back" to "Paid back", "sale" to "Sales", "work" to "Work")

    /** "Spent 365 PKR this month.", "You gave Hamid Akram 50,000 PKR." from the totals grouped by type. */
    private fun sentence(p: Pick, t: JSONObject, s: LocalAgent.Said): String {
        val cur = s.host.currency
        val who = p.persons.joinToString(" and ")
        val gs = groups(t)
        val parts = gs.map { (kind, total) ->
            val v = money(total, cur)
            when (kind) {
                "lent" -> if (who.isBlank()) "given $v" else "you gave $who $v"
                "received_back" -> if (who.isBlank()) "received $v" else "$who paid you $v"
                "borrowed" -> if (who.isBlank()) "borrowed $v" else "you borrowed $v from $who"
                "paid_back" -> if (who.isBlank()) "paid back $v" else "you paid back $v to $who"
                "sale" -> if (who.isBlank()) "sales $v" else "sales to $who $v"
                "work" -> if (who.isBlank()) "work $v" else "$who's work $v"
                "expense" -> "spent $v"
                "income" -> "income $v"
                else -> "$kind $v"
            }
        }
        val on = if (!p.what) "" else if (gs.all { it.first == "expense" }) " on " + whatText(p) else " (" + whatText(p) + ")"
        return cap(parts.joinToString(", ")) + on + period(p, s.now) + "."
    }

    /** "Nothing found for petrol this month.", "No entries today." */
    private fun nothing(p: Pick, now: Long): String {
        val subject = (p.persons + p.cats + p.words.map(::cap) + p.types.mapNotNull { nouns[it] }).joinToString(", ")
        return if (subject.isBlank()) "No entries" + period(p, now) + "." else "Nothing found for $subject" + period(p, now) + "."
    }

    private val nouns = mapOf("expense" to "expenses", "income" to "income", "lent" to "money given", "received_back" to "money received",
        "borrowed" to "borrowing", "paid_back" to "paying back", "sale" to "sales", "work" to "work")

    private fun whatText(p: Pick) = p.words.ifEmpty { p.cats }.joinToString(", ")

    /** " for petrol" in a count or list sentence. */
    private fun about(p: Pick) = if (p.what || p.persons.isNotEmpty()) " for " + (p.persons + p.cats + p.words).joinToString(", ") else ""

    /** The days picked, as said in a sentence: " today", " this month", " in Oct 2026", "" for all time. */
    private fun period(p: Pick, now: Long): String {
        val from = p.from
        val to = p.to
        if (from == null && to == null) return ""
        val today = cal(now)
        fun d(off: Int) = day((today.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, off) })
        val firstOfMonth = day((today.clone() as Calendar).apply { set(Calendar.DAY_OF_MONTH, 1) })
        val lastMonth = (today.clone() as Calendar).apply { set(Calendar.DAY_OF_MONTH, 1); add(Calendar.MONTH, -1) }
        val monday = (today.clone() as Calendar).apply { firstDayOfWeek = Calendar.MONDAY; set(Calendar.DAY_OF_WEEK, Calendar.MONDAY) }
        val pretty = SimpleDateFormat("d MMM", Locale.US)
        fun show(x: String) = parse(x)?.let { pretty.format(it.time) } ?: x
        val a = from?.let(::parse)
        val b = to?.let(::parse)
        fun wholeMonth() = a != null && b != null && a.get(Calendar.DAY_OF_MONTH) == 1 && a.get(Calendar.MONTH) == b.get(Calendar.MONTH) &&
            a.get(Calendar.YEAR) == b.get(Calendar.YEAR) && b.get(Calendar.DAY_OF_MONTH) == b.getActualMaximum(Calendar.DAY_OF_MONTH)
        fun wholeYear() = a != null && b != null && a.get(Calendar.DAY_OF_YEAR) == 1 && a.get(Calendar.YEAR) == b.get(Calendar.YEAR) &&
            b.get(Calendar.MONTH) == Calendar.DECEMBER && b.get(Calendar.DAY_OF_MONTH) == 31
        return when {
            from == to -> when (from) { d(0) -> " today"; d(-1) -> " yesterday"; else -> " on " + show(from!!) }
            from == firstOfMonth && to == d(0) -> " this month"
            from == day(lastMonth) && wholeMonth() -> " last month"
            wholeMonth() -> " in " + SimpleDateFormat("MMM yyyy", Locale.US).format(a!!.time)
            from == day(monday) && to == d(0) -> " this week"
            from == day((monday.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -7) }) && to == day((monday.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -1) }) -> " last week"
            a != null && a.get(Calendar.DAY_OF_YEAR) == 1 && a.get(Calendar.YEAR) == today.get(Calendar.YEAR) && to == d(0) -> " this year"
            wholeYear() -> " in " + a!!.get(Calendar.YEAR)
            from == null -> " up to " + show(to!!)
            to == null || to == d(0) -> " since " + show(from)
            else -> " from " + show(from) + " to " + show(to)
        }
    }

    // ---------------------------------------------------------------- what was picked

    /** The AI's picking fields, as one look-up asks them. */
    private class Pick(
        var types: List<String> = emptyList(), var cats: List<String> = emptyList(), var persons: List<String> = emptyList(),
        var words: List<String> = emptyList(), var from: String? = null, var to: String? = null, var last: Int = 0,
        var min: Double? = null, var max: Double? = null, var minutes: Double? = null,
        /** Days were said (or "ab tak": all of them). */
        var span: Boolean = false,
        /** Words that name something nothing is saved for ("generator pe kitna laga"). */
        var unknown: List<String> = emptyList()
    ) {
        val what get() = cats.isNotEmpty() || words.isNotEmpty()
        val any get() = types.isNotEmpty() || what || persons.isNotEmpty() || span || last > 0 || min != null || max != null || minutes != null

        fun json(): JSONObject = JSONObject().apply {
            if (types.isNotEmpty()) put("types", JSONArray(types))
            if (cats.isNotEmpty()) put("categories", JSONArray(cats))
            if (persons.isNotEmpty()) put("persons", JSONArray(persons))
            if (words.isNotEmpty()) put("words", JSONArray(words))
            from?.let { put("from", it) }
            to?.let { put("to", it) }
            if (last > 0) put("last", last)
            min?.let { put("min_amount", it) }
            max?.let { put("max_amount", it) }
            minutes?.let { put("added_in_last_minutes", it) }
        }

        /** This look-up changed by what a follow-up says; the rest stays. */
        fun with(n: Pick): Pick {
            val r = Pick(types, cats, persons, words, from, to, last, min, max, minutes, span, n.unknown)
            if (n.span) { r.from = n.from; r.to = n.to; r.span = true }
            if (n.types.isNotEmpty()) {
                if (n.types != types && !n.what) { r.cats = emptyList(); r.words = emptyList() }
                r.types = n.types
            }
            if (n.what) {
                r.cats = n.cats; r.words = n.words
                if (n.types.isEmpty() && types != listOf("expense")) r.types = emptyList()
            }
            if (n.persons.isNotEmpty()) r.persons = n.persons
            if (n.last > 0) r.last = n.last
            if (n.min != null) r.min = n.min
            if (n.max != null) r.max = n.max
            return r
        }

        companion object {
            fun of(o: JSONObject) = Pick(strings(o.optJSONArray("types")), strings(o.optJSONArray("categories")), strings(o.optJSONArray("persons")),
                strings(o.optJSONArray("words")), o.optString("from").ifBlank { null }, o.optString("to").ifBlank { null }, o.optInt("last", 0),
                if (o.has("min_amount")) o.optDouble("min_amount") else null, if (o.has("max_amount")) o.optDouble("max_amount") else null,
                if (o.has("added_in_last_minutes")) o.optDouble("added_in_last_minutes") else null)
        }
    }

    /** Saved people the message names ([at]: the word positions that named them), or the word that fits several. */
    private class Who(val names: List<String>, val at: Set<Int>, val ends: Map<String, Int>, val starts: Map<String, Int>, val unsure: Pair<String, List<String>>? = null)

    private fun people(m: Msg): Who {
        val ws = m.words
        val at = mutableSetOf<Int>()
        val starts = linkedMapOf<String, Int>()
        val ends = mutableMapOf<String, Int>()
        fun take(n: String, a: Int, b: Int) { if (n !in starts) { starts[n] = a; ends[n] = b }; at += a..b }
        // Whole names first ("salim khan"), the longest first.
        for (n in m.names.sortedByDescending { words(it).size }) {
            val nw = words(n)
            if (nw.size < 2) continue
            for (i in 0..ws.size - nw.size) if ((i until i + nw.size).none { it in at } && nw.indices.all { ws[i + it] == nw[it] }) { take(n, i, i + nw.size - 1); break }
        }
        for ((i, w) in ws.withIndex()) {
            if (i in at || w.length < 3 || w.any(Char::isDigit) || m.vocab(i) || w in cats) continue
            var hits = m.names.filter { n -> w in words(n) }
            if (hits.size > 1) {
                // "salim raza": the next or the one before narrows it.
                val near = hits.filter { n -> words(n).let { nw -> ws.getOrNull(i + 1) in nw || ws.getOrNull(i - 1) in nw } }
                if (near.size == 1) hits = near
            }
            if (hits.isEmpty() && !m.isItem(w)) hits = close(w, m.names)
            when {
                hits.size == 1 -> take(hits[0], i, i)
                hits.size > 1 && hits.none { it in starts } -> return Who(emptyList(), at, ends, starts, w to hits)
            }
        }
        return Who(starts.keys.toList(), at, ends, starts)
    }

    /** Saved names a word is a misspelling or short form of ("hmd", "nrgs", "hameed"): the closest, or the tied ones. */
    private fun close(w: String, names: List<String>): List<String> {
        // Voice gives "hameed" for Hamid: the same first letter and at most two letters off.
        fun heard(part: String) = w.length >= 5 && part.first() == w.first() && Normalizer.distance(w, part) <= 2 && Normalizer.similarity(w, part) >= 0.85
        val scored = names.mapNotNull { n ->
            words(n).filter { it.length >= 3 }.filter { Understand.closeName(w, it) || heard(it) }.maxOfOrNull { Normalizer.similarity(w, it) }?.let { n to it }
        }.sortedByDescending { it.second }
        if (scored.isEmpty()) return emptyList()
        return scored.filter { scored[0].second - it.second < 0.03 }.map { it.first }
    }

    // ---------------------------------------------------------------- reading the message

    /** The message as words, each also in its plain form ("kitne" kitna, "dikha" dikhao), with the days and filters it says. */
    private class Msg(val s: LocalAgent.Said) {
        val entries = s.host.entries()
        val names = s.host.names().map { it.trim() }.filter { it.isNotBlank() }.distinctBy { it.lowercase() }
        private val nameWords = names.flatMap(::words).toSet()
        private val items = mutableMapOf<String, Boolean>()
        val words = s.words
        val canon = words.map { w -> if (w in nameWords) w else forms[w] ?: if (w in cats || w in stop || isItem(w)) w else fuzzy(w) ?: w }.mapIndexed { i, c ->
            // "has to pay", "will pay": what is owed, not what was paid.
            val before = words.getOrNull(i - 1)
            val will = "will" in words.subList((i - 3).coerceAtLeast(0), i)
            if (c == "diye" && words[i] in setOf("pay", "give") && (will || (before == "to" && words.getOrNull(i - 2) in setOf("has", "have", "need", "needs")))) "owe" else c
        }
        /** Positions read as a day, a filter or "last N". */
        val used = mutableSetOf<Int>()
        var from: String? = null
        var to: String? = null
        var all = false
        var last = 0
        var min: Double? = null
        var max: Double? = null

        init {
            readDays()
            readFilters()
        }

        /** Numbers that are amounts (not a day, a filter or "last N"): a message with one is a new entry, not a question. */
        val amounts = words.indices.filter { it !in used && words[it].first().isDigit() }

        fun isItem(w: String) = items.getOrPut(w) { w.length >= 3 && Query.select(entries, Query.Filter(items = listOf(w))).isNotEmpty() }

        /** A thing as saved when the word only finds it by another spelling ("ptrl": petrol). */
        fun shown(w: String): String {
            val found = Query.select(entries, Query.Filter(items = listOf(w))).map { it.item.trim() }.filter { it.isNotBlank() }
            if (found.any { it.lowercase().replace(" ", "").contains(w) }) return w
            return found.groupingBy { it.lowercase() }.eachCount().maxByOrNull { it.value }?.key ?: w
        }
        fun has(vararg c: String) = canon.indices.any { it !in used && canon[it] in c }
        fun vocab(i: Int) = canon[i] in vocabulary || words[i] in stop || canon[i] in stop
        fun filler(i: Int) = words[i] in stop || canon[i] in setOf("kitna", "batao", "dikhao", "kholo", "what", "kya") || i in used

        private val raw = s.lower
        val command = has("cmd", "chahiye") || words.indices.any { i -> words[i] in pairVerbs && words.getOrNull(i + 1) in pairEnds } ||
            Regex("""\b(?:wapas|wapis|vapas)\s+(?:la|lao|le\s+aao|le\s+ao|laao|kar)\b|\b(?:naya|nayi|naye|new)\s+(?:khata|account|khaata)\b""").containsMatchIn(raw)
        val marker = canon.firstOrNull() in setOf("aur", "and", "also") || has("sirf", "only") || Regex("""\b(?:what|how)\s+about\b""").containsMatchIn(raw)
        val verb = has("did", "diye", "mile", "liye", "dali", "kharida")
        val monthly = has("monthly") || (has("har") && has("mahina")) || (has("wise") && has("mahina")) ||
            Regex("""\bmonth\s+by\s+month\b|\bper\s+month\b|\beach\s+month\b|\bevery\s+month\b""").containsMatchIn(raw)
        val byPerson = ((has("wise") || has("har")) && has("bande")) || Regex("""\bkis\s+kis\b|\bkin\s+kin\b|\bby\s+person\b|\bper\s+person\b""").containsMatchIn(raw) ||
            (has("kis") && has("kitna") && has("diye", "mile", "udhaar")) || (has("kaun") && has("diye", "mile"))
        val byItem = ((has("wise") || has("har")) && has("cheez")) || (has("kis") && has("cheez")) || Regex("""\bby\s+item\b""").containsMatchIn(raw)
        /** "kis ne diye" asks who gave (money came in): "by"; "kis ko" whom it went to: "to"; "kis se": "from". */
        val kisRole = if (Regex("""\bwho\s+(?:gave|paid|sent|returned)\b""").containsMatchIn(raw)) "by"
            else canon.indexOfLast { it == "kis" }.takeIf { it >= 0 }?.let { i -> words[i].removePrefix("kis").ifBlank { words.getOrNull(i + 1) } }
                .let { when (it) { "ne", "nay", "ny" -> "by"; "se", "sy", "say", "sey" -> "from"; "ko", "ku" -> "to"; else -> "" } }
        /** Saved work and goods names. */
        val known = Goods.known(entries)
        /** "biggest", "sab se bara": the highest expenses. */
        val highest = has("highest") || Regex("""\bsab\s*se\s+(?:bara|bada|barra|bari|badi|mehnga|mehngi|zyada|ziada)\b""").containsMatchIn(raw)
        /** "I owe", "do I owe": the owner pays. */
        val iOwe = Regex("""\b(?:i|we)\s+(?:still\s+)?owe\b|\bdo\s+(?:i|we)\s+owe\b""").containsMatchIn(raw)

        /** A question (or a look-up) rather than a new entry or a change. */
        fun asking(who: Who, p: Pick, prev: Boolean): Boolean {
            if ((command && !has("pdf")) || amounts.isNotEmpty()) return false
            val asked = s.raw.trim().let { it.endsWith("?") || it.endsWith("؟") }
            val q = has("kitna", "kaun", "kis", "kahan", "kab", "which") || (has("how") && has("much", "many"))
            val weak = has("what", "kya") && (p.any || who.names.isNotEmpty() || book() != null)
            val show = has("dikhao", "batao", "list", "detail", "hisab", "report", "balance", "pdf", "entries", "kholo", "total", "owe", "lene", "dene",
                "profit", "loss", "worth", "count", "rate") || highest || monthly || byPerson || byItem || (has("khata") && who.names.isNotEmpty())
            val subject = has("kharcha", "income", "sale") || p.what
            return q || weak || show || asked || (subject && !verb && who.names.isEmpty() && (p.span || words.size <= 2)) ||
                (prev && (marker || words.size <= 3) && (subject || p.span || who.names.isNotEmpty()) && !verb)
        }

        /** A follow-up of the last look-up: short, or "aur …", "sirf …", "what about …". */
        fun followUp(fresh: Pick) = marker || words.size <= 4 || !(fresh.types.isNotEmpty() || fresh.what || fresh.persons.isNotEmpty())

        /** About what a person owes or is owed, rather than one kind of their entries. */
        fun aboutBalance(p: Pick, prev: Boolean): Boolean {
            if (has("balance", "lene", "dene", "owe")) return true
            if (prev && marker) return false
            if (has("hisab", "khata", "kholo")) return true
            return p.types.isEmpty() && !p.what && !p.span && p.last == 0 && p.min == null && p.max == null &&
                !has("list", "entries", "detail", "monthly", "highest", "count", "kahan", "category", "cheez", "wise", "har", "dikhao")
        }

        fun aboutOwing() = has("owe", "lene", "dene") || (has("udhaar") && !has("diye", "liye")) ||
            (has("balance") && (has("kis", "kaun", "sab") || book() != null)) || (book() != null && has("hisab", "kitna", "paise", "udhaar"))

        fun aboutCash(p: Pick) = has("cash") || Regex("""\bhath\s+(?:mein|me|main)\b|\bin\s+hand\b|\bjeb\b|\b(?:mere|hamare|apne)\s+(?:paas|pas)\b""").containsMatchIn(raw) ||
            (has("balance") && p.types.isEmpty() && !p.what)

        fun aboutAccounts(who: Who): Boolean {
            val owing = has("owe", "lene", "dene", "balance", "paise", "diye", "mile", "liye", "udhaar", "profit", "hisab", "laga")
            val listing = has("kaun", "naam", "list", "kitna", "which", "kis", "dikhao", "batao", "what", "kya")
            return when {
                who.names.isNotEmpty() -> has("which", "kis") && has("khata") && has("mein")
                book() != null -> listing && !owing && !has("kharcha", "income", "kaam", "sale")
                else -> has("khata") && listing && !owing
            }
        }

        /** The people account the message names ("staff", "customers", or one of the owner's own). */
        fun book(own: List<String> = s.host.setup().books.filter { it.people }.map { it.name }): String? {
            canon.forEach { c -> books[c]?.let { if (it != Sides.PEOPLE || !has("owe", "lene", "dene")) return it } }
            return own.firstOrNull { b -> words(b).let { bw -> bw.isNotEmpty() && bw.all { w -> w in words || "${w}s" in words || w.removeSuffix("s") in words } } }
        }

        /** The look-up the message asks for, by its words. */
        fun pick(who: Who): Pick {
            val p = Pick(persons = who.names, from = from, to = to, last = last, min = min, max = max, span = from != null || all)
            // Things and categories: the free words, side by side ("kachi eent") as one name.
            val free = words.indices.filter { it !in used && it !in who.at && !vocab(it) && !words[it].first().isDigit() }
            val runs = mutableListOf<MutableList<Int>>()
            free.forEach { i -> if (runs.lastOrNull()?.last() == i - 1) runs.last() += i else runs += mutableListOf(i) }
            val cs = mutableListOf<String>()
            val ws = mutableListOf<String>()
            val unknown = mutableListOf<String>()
            for (run in runs) {
                val phrase = run.joinToString(" ") { words[it] }
                if (run.size > 1 && run.none { words[it] in cats } && isItem(phrase)) { ws += phrase; continue }
                // Another spelling of a saved work or thing ("kacchi int"): one or two letters off.
                fun good(x: String) = Goods.canonical(x, known).takeIf { it in known && (x.contains(' ') || Normalizer.distance(x, it.lowercase()) <= 2) }
                val same = if (run.size > 1) good(phrase) else null
                if (same != null) { ws += same.lowercase(); continue }
                for (i in run) {
                    val w = words[i]
                    when {
                        w in cats -> cs += cats.getValue(w)
                        isItem(w) -> ws += shown(w)
                        w.length >= 4 && good(w) != null -> ws += good(w)!!.lowercase()
                        else -> {
                            val guess = runCatching { Parser.guessCategory(w)?.first }.getOrNull()
                            if (guess != null) cs += guess.label
                            else if (words.getOrNull(i + 1) in setOf("pe", "par", "pr", "per", "ka", "ki", "ke", "wali", "wala", "wale") || words.getOrNull(i - 1) in setOf("on", "for", "of")) unknown += w
                        }
                    }
                }
            }
            p.cats = cs.distinct()
            p.words = ws.distinct()
            p.unknown = unknown
            p.types = types(who, p)
            return p
        }

        /** The kinds of entry the words ask about; a person's role ("ko", "ne", "se") says which way money went. */
        private fun types(who: Who, p: Pick): List<String> {
            val t = linkedSetOf<String>()
            val name = who.names.firstOrNull()
            val role = name?.let { role(who, it) } ?: kisRole
            val loan = has("udhaar")
            if (has("maal")) t += when (role) { "to" -> "sale"; "by", "from" -> "work"; else -> "sale" }
            if (t.isEmpty() && has("diye", "mile", "liye") && !p.what) when {
                has("diye") && loan && role != "by" -> t += "lent"
                has("liye") && loan -> t += "borrowed"
                has("diye") && has("mile") && role == "to" -> t += "paid_back"
                has("diye") && role == "to" -> t += "lent"
                has("diye") && (role == "by" || role == "from") -> t += "received_back"
                has("mile") -> t += "received_back"
                has("liye") && role == "from" -> t += "received_back"
                has("diye") && name == null && role == "" -> t += "lent"
            }
            if (t.isEmpty()) {
                if (has("kharcha", "laga")) t += "expense"
                if (has("income")) t += "income"
                if (has("sale")) t += "sale"
                // "dali", "banai" are work when a worker or a saved work is named ("petrol dalwaya" is not).
                if (has("kaam", "kharida") || (has("dali", "bana") && !has("pdf") && (name != null || p.words.any { w -> known.any { it.contains(w, true) } }))) t += "work"
            }
            return t.toList()
        }

        /** "to" (hamid ko), "by" (hamid ne), "from" (hamid se) or "" for one named person. */
        private fun role(who: Who, name: String): String {
            val after = words.getOrNull((who.ends[name] ?: return "") + 1)
            val before = words.getOrNull((who.starts[name] ?: return "") - 1)
            val verbAt = canon.indexOfFirst { it == "diye" || it == "mile" || it == "liye" }
            return when {
                after in setOf("ko", "ku", "kou", "kon") -> "to"
                after in setOf("ne", "nay", "ny", "nai") -> "by"
                after in setOf("se", "sy", "say", "sey", "sai") -> "from"
                before == "to" -> "to"
                before == "from" -> "from"
                verbAt >= 0 && after !in setOf("ka", "ki", "ke", "kay", "ky") -> if ((who.starts[name] ?: 0) < verbAt) "by" else "to"
                else -> ""
            }
        }

        /** The view the message asks for: list, breakdown, total, count, highest or by month, person or item. */
        fun view(p: Pick): String {
            val howMuch = has("kitna", "total") || (has("how") && has("much"))
            return when {
                monthly -> "by_month"
                byPerson -> "by_person"
                byItem -> "by_item"
                has("kahan", "category") || (has("kis") && words.any { it in setOf("pe", "par", "pr", "on") }) -> "breakdown"
                highest -> "highest"
                has("count", "baar") || ((has("kitna") || (has("how") && has("many"))) && has("entries")) -> "count"
                has("detail") -> "breakdown"
                (has("list", "entries", "dikhao", "kholo", "khata") && !howMuch) || has("kab") -> "list"
                (p.last > 0 || (p.min != null && p.min == p.max)) && !howMuch -> "list"
                has("hisab") && !howMuch -> if (p.from != null && p.from == p.to) "list" else "breakdown"
                else -> "total"
            }
        }

        private fun mark(vararg i: Int) { i.forEach { if (it in words.indices) used += it } }

        /** Days said: "aaj", "kal", "is mahine", "pichle hafte", "3 nov", "november", "pichle 10 din", "ab tak"… */
        private fun readDays() {
            val today = cal(s.now)
            fun at(field: Int, n: Int) = (today.clone() as Calendar).apply { add(field, n) }
            fun span(a: Calendar, b: Calendar) { from = day(a); to = day(b) }
            // Dates: "3 nov", "nov 3", "13/10/26".
            val points = mutableListOf<Calendar>()
            fun point(d: Int, mo: Int, y: Int?) {
                val c = (today.clone() as Calendar).apply { isLenient = false; set(Calendar.YEAR, y ?: today.get(Calendar.YEAR)); set(Calendar.DAY_OF_MONTH, 1); set(Calendar.MONTH, mo) }
                if (d > c.getActualMaximum(Calendar.DAY_OF_MONTH)) return
                c.set(Calendar.DAY_OF_MONTH, d)
                if (y == null && c.after(today)) c.add(Calendar.YEAR, -1)
                points += c
            }
            fun year(i: Int): Int? = words.getOrNull(i)?.toIntOrNull()?.let { if (it < 100) 2000 + it else it }?.takeIf { kotlin.math.abs(it - today.get(Calendar.YEAR)) <= 5 }
            for (i in words.indices) {
                if (i in used) continue
                val d = dayNumber(words[i])
                val mo = months[words.getOrNull(i + 1)]
                val mo2 = months[words[i]]
                val d2 = dayNumber(words.getOrNull(i + 1))
                when {
                    d != null && mo != null -> { val y = year(i + 2); point(d, mo, y); mark(i, i + 1); if (y != null) mark(i + 2) }
                    mo2 != null && d2 != null -> { val y = year(i + 2); point(d2, mo2, y); mark(i, i + 1); if (y != null) mark(i + 2) }
                }
            }
            Regex("""(?<![\d.])(\d{1,2})[/-](\d{1,2})(?:[/-](\d{2,4}))?(?![\d.])""").findAll(s.lower).forEach { r ->
                val (dd, mm, yy) = r.destructured
                val i = words.indices.firstOrNull { words[it] == dd && words.getOrNull(it + 1) == mm && it !in used } ?: return@forEach
                if (mm.toInt() in 1..12) { point(dd.toInt(), mm.toInt() - 1, yy.toIntOrNull()?.let { if (it < 100) 2000 + it else it }); mark(i, i + 1); if (yy.isNotBlank()) mark(i + 2) }
            }
            val soFar = canon.indices.filter { canon[it] in setOf("aaj", "ab") && canon.getOrNull(it + 1) == "tak" }
            if (points.isNotEmpty()) {
                val since = soFar.isNotEmpty() || has("since") || (points.size == 1 && words.any { it in setOf("se", "from") })
                soFar.forEach { mark(it, it + 1) }
                if (points.size >= 2) span(points.minByOrNull { it.timeInMillis }!!, points.maxByOrNull { it.timeInMillis }!!)
                else if (since) span(points[0], today) else span(points[0], points[0])
                return
            }
            // "ab tak", "aaj tak", "all time": everything so far.
            soFar.forEach { all = true; mark(it, it + 1) }
            canon.indices.filter { canon[it] == "alltime" }.forEach { all = true; mark(it) }
            if (Regex("""\ball\s+time\b|\bso\s+far\b|\btill\s+now\b|\buntil\s+now\b|\bshuru\s+se\b""").containsMatchIn(s.lower)) all = true
            if (all) return
            // "pichle 10 din", "last 2 months", "3 din ka"; "2 din pehle" is that one day.
            for (i in canon.indices) {
                if (i in used || canon[i] !in units) continue
                val n = number(words.getOrNull(i - 1)) ?: continue
                if (n <= 0 || n > 400) continue
                if (words.getOrNull(i + 1) in setOf("pehle", "pahle", "pehlay", "phle", "ago", "before") && canon[i] == "din") {
                    val d = at(Calendar.DAY_OF_YEAR, -n)
                    span(d, d)
                    mark(i - 1, i, i + 1)
                    return
                }
                val start = when (canon[i]) {
                    "din" -> at(Calendar.DAY_OF_YEAR, -(n - 1))
                    "hafta" -> at(Calendar.DAY_OF_YEAR, -(7 * n - 1))
                    "mahina" -> at(Calendar.MONTH, -n).apply { add(Calendar.DAY_OF_YEAR, 1) }
                    else -> at(Calendar.YEAR, -n).apply { add(Calendar.DAY_OF_YEAR, 1) }
                }
                span(start, today)
                mark(i - 1, i)
                if (canon.getOrNull(i - 2) == "pichla") mark(i - 2)
                return
            }
            val dayAgo = when {
                has("parson") -> 2
                has("kal") -> 1
                has("aaj") -> 0
                else -> null
            }
            if (dayAgo != null) {
                val d = at(Calendar.DAY_OF_YEAR, -dayAgo)
                span(d, d)
                canon.indices.filter { canon[it] in setOf("parson", "kal", "aaj") }.forEach { mark(it) }
                return
            }
            // "pichle mahine", "is hafte", "this year"; "mahine ka kharcha" is this month.
            for (i in canon.indices) {
                if (i in used || canon[i] !in units || canon[i] == "din") continue
                if (has("monthly", "har", "wise") || Regex("""\bmonth\s+by\s+month\b|\bper\s+month\b""").containsMatchIn(s.lower)) break
                val lastOne = canon.getOrNull(i - 1) == "pichla" || canon.getOrNull(i - 2) == "pichla"
                when (canon[i]) {
                    "mahina" -> {
                        val c = (today.clone() as Calendar).apply { set(Calendar.DAY_OF_MONTH, 1) }
                        if (lastOne) { c.add(Calendar.MONTH, -1); span(c, (c.clone() as Calendar).apply { set(Calendar.DAY_OF_MONTH, getActualMaximum(Calendar.DAY_OF_MONTH)) }) }
                        else span(c, today)
                    }
                    "hafta" -> {
                        val c = (today.clone() as Calendar).apply { firstDayOfWeek = Calendar.MONDAY; set(Calendar.DAY_OF_WEEK, Calendar.MONDAY) }
                        if (lastOne) { c.add(Calendar.DAY_OF_YEAR, -7); span(c, (c.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, 6) }) } else span(c, today)
                    }
                    else -> {
                        val c = (today.clone() as Calendar).apply { set(Calendar.DAY_OF_YEAR, 1) }
                        if (lastOne) { c.add(Calendar.YEAR, -1); span(c, (c.clone() as Calendar).apply { set(Calendar.MONTH, Calendar.DECEMBER); set(Calendar.DAY_OF_MONTH, 31) }) } else span(c, today)
                    }
                }
                mark(i)
                if (lastOne) mark(if (canon.getOrNull(i - 1) == "pichla") i - 1 else i - 2)
                if (canon.getOrNull(i - 1) == "is") mark(i - 1)
                return
            }
            // A weekday: the latest one ("pichle juma" a week before when that is today).
            for (i in words.indices) {
                val wd = weekdays[words[i]] ?: continue
                val back = ((today.get(Calendar.DAY_OF_WEEK) - wd + 7) % 7).let { if (it == 0 && canon.getOrNull(i - 1) == "pichla") 7 else it }
                val d = at(Calendar.DAY_OF_YEAR, -back)
                span(d, d)
                mark(i)
                if (canon.getOrNull(i - 1) == "pichla") mark(i - 1)
                return
            }
            // A month by name ("november", "oct 2025"), or a year ("2025 ka").
            for (i in words.indices) {
                val mo = months[words[i]] ?: continue
                if (words[i] == "may" && year(i + 1) == null) continue
                val y = year(i + 1) ?: year(i - 1)
                val c = (today.clone() as Calendar).apply { set(Calendar.DAY_OF_MONTH, 1); set(Calendar.MONTH, mo); if (y != null) set(Calendar.YEAR, y) }
                if (y == null && c.after(today)) c.add(Calendar.YEAR, -1)
                span(c, (c.clone() as Calendar).apply { set(Calendar.DAY_OF_MONTH, getActualMaximum(Calendar.DAY_OF_MONTH)) })
                mark(i)
                if (year(i + 1) != null) mark(i + 1) else if (y != null) mark(i - 1)
                return
            }
            for (i in words.indices) {
                val y = words[i].toIntOrNull()?.takeIf { it in 2015..2035 } ?: continue
                if (words.getOrNull(i + 1) !in setOf("ka", "ki", "ke", "mein", "me", "main") && words.getOrNull(i - 1) !in setOf("in", "saal", "sal", "year")) continue
                val c = (today.clone() as Calendar).apply { set(Calendar.YEAR, y); set(Calendar.DAY_OF_YEAR, 1) }
                span(c, (c.clone() as Calendar).apply { set(Calendar.MONTH, Calendar.DECEMBER); set(Calendar.DAY_OF_MONTH, 31) })
                mark(i)
                return
            }
        }

        /** "500 se zyada", "under 100", "1000 wali", "last 5 entries", "akhri entry". */
        private fun readFilters() {
            val bigger = setOf("zyada", "ziada", "zayada", "ziyada", "jyada", "upar", "uper", "oper", "above", "over", "more", "greater", "bara", "bari", "bade", "badi")
            val smaller = setOf("kam", "neeche", "niche", "nichay", "below", "under", "less", "chota", "choti", "chote", "chhoti")
            val joint = setOf("se", "sy", "say", "sey", "than", "ke", "ka", "ki")
            for (i in words.indices) {
                if (i in used) continue
                val n = words[i].replace(",", "").toDoubleOrNull() ?: continue
                val a = words.getOrNull(i + 1)
                val b = words.getOrNull(i + 2)
                val before = words.getOrNull(i - 1)
                when {
                    a in bigger -> { min = n; mark(i, i + 1) }
                    a in joint && b in bigger -> { min = n; mark(i, i + 1, i + 2) }
                    a in smaller -> { max = n; mark(i, i + 1) }
                    a in joint && b in smaller -> { max = n; mark(i, i + 1, i + 2) }
                    a in setOf("wali", "wala", "wale", "walay", "wali") -> { min = n; max = n; mark(i, i + 1) }
                    before in setOf("above", "over", "than") && words.getOrNull(i - 2) != "less" -> { min = n; mark(i - 1, i, if (before == "than") i - 2 else i) }
                    before in setOf("below", "under") || (before == "than" && words.getOrNull(i - 2) == "less") -> { max = n; mark(i - 1, i, if (before == "than") i - 2 else i) }
                    canon.getOrNull(i - 1) == "pichla" && n >= 1 && n <= 500 && n == Math.floor(n) -> { last = n.toInt(); mark(i - 1, i) }
                    canon.getOrNull(i + 1) == "entries" && n >= 1 && n <= 500 && n == Math.floor(n) -> { last = n.toInt(); mark(i) }
                }
            }
            if (last == 0) canon.indices.firstOrNull { it !in used && canon[it] == "pichla" && canon.getOrNull(it + 1) == "entries" }?.let { last = 1; mark(it) }
            // "hamid ko 500 kab diye the", "500 kis ko diye": a question about that amount, not a new entry.
            val left = words.indices.filter { it !in used && words[it].first().isDigit() }
            if (has("kab", "kis", "kaun", "kahan", "which") && left.size == 1 && min == null && max == null) words[left[0]].replace(",", "").toDoubleOrNull()?.let { min = it; max = it; mark(left[0]) }
        }
    }

    // ---------------------------------------------------------------- words

    /** Ways people type the words that matter here, to one plain form. */
    private val forms: Map<String, String> = buildMap {
        fun v(std: String, vararg f: String) { put(std, std); f.forEach { put(it, std) } }
        v("kitna", "kitne", "kitni", "kitnay", "kitny", "kitnai", "kitney", "kitnaa", "kitnee", "ktna", "ktne", "ktni", "kitan", "ketna", "ketne", "kinta", "kitno", "kitnah", "kitn")
        v("kaun", "kon", "koun", "kaon", "kown", "who", "whom", "whose")
        v("kis", "kisko", "kisne", "kisse", "kise", "kin", "kinko", "kinhon", "kinse", "kisy", "kesko")
        v("kahan", "kahaan", "kahn", "kaha", "kidhar", "kidhr", "kidher", "where", "kahaa")
        v("kab", "when")
        v("which", "konsa", "konsi", "konse", "kaunsa", "kaunsi", "kaunse", "kounsa", "kounsi")
        v("what", "whats")
        v("kya", "kyaa")
        v("dikhao", "dikha", "dikhado", "dikhaao", "dekhao", "dikhana", "dikhaye", "dikhaen", "dikhain", "dkhao", "dikhaw", "dikao", "dikhau", "show",
            "display", "view", "see", "dekhna", "dekhni", "dekho", "dekhain", "dikhayen", "dikhaiye")
        v("batao", "bata", "btao", "bta", "batado", "batana", "bataen", "bataye", "batain", "btaw", "batau", "bataiye", "batayen", "batayo", "tell", "bolo", "bataao")
        v("list", "lists", "fehrist")
        v("detail", "details", "detailed", "tafseel", "tafsil", "tafseeli", "breakdown")
        v("hisab", "hisaab", "hesab", "hsab", "hisb", "hisaap", "hissab", "statement")
        v("report", "summary", "overview", "sumary", "summery")
        v("khata", "khaata", "khate", "khaate", "khatay", "khta", "account", "accounts", "ledger", "accaunt", "acount", "khatey")
        v("balance", "balnce", "blance", "ballance", "bakaya", "baqaya", "baqi", "baki", "baaki", "baqee", "bqaya", "bqi", "bki", "remaining", "outstanding", "pending", "due", "dues")
        v("pdf", "pdff")
        v("entries", "entry", "entri", "entrys", "entery", "enteries", "intry", "records", "record", "transactions", "transaction")
        v("kholo", "khol", "kholdo", "khulo", "open")
        v("lene", "lena", "leny", "lenay", "lainay", "laine", "laina", "lainey", "line", "lenne", "laynay", "layne", "lenay", "laini")
        v("dene", "dena", "deny", "denay", "dainay", "daine", "deney", "denne", "daina", "daynay", "dayne", "daini")
        v("owe", "owes", "owed", "owing")
        v("aaj", "aj", "aja", "today", "aaja", "aajj")
        v("kal", "kl", "kall", "yesterday")
        v("parson", "parso", "parsu", "parsoon", "parsun", "prson", "parsuon")
        v("mahina", "mahine", "mahiny", "mahinay", "mahene", "mahinah", "mahino", "mahinon", "month", "months", "mhine", "mahne")
        v("hafta", "hafte", "haftay", "hafty", "haftey", "week", "weeks")
        v("saal", "sal", "year", "years", "baras", "saalon")
        v("din", "dino", "dinon", "day", "days", "dinn")
        v("is", "iss", "es", "ess", "os", "this", "current", "isi")
        v("pichla", "pichle", "pichli", "pichlay", "pichly", "pichhle", "pichhla", "pichhli", "guzishta", "previous", "last", "past", "akhri", "aakhri",
            "akhiri", "aakhiri", "lst", "latest", "recent")
        v("tak", "tk", "till", "until", "upto")
        v("ab", "abhi", "abi", "now")
        v("alltime", "overall", "lifetime", "ever")
        v("kharcha", "kharche", "kharch", "kharchay", "kharchey", "kharchy", "khrcha", "khrch", "khrche", "kharchon", "kharcho", "expense", "expenses",
            "spent", "spend", "spending", "spends", "expenditure", "kharchaa", "karcha", "kharaj", "kharj", "kharchah")
        v("laga", "lga", "lagay", "lage", "lagy", "lgy", "lagi", "lgi", "lagaya", "lagaye", "lgaya", "lagae", "laggay")
        v("income", "aamdani", "amdani", "aamdni", "amdni", "aamadni", "kamai", "kamaai", "kamayi", "earning", "earnings", "earned", "earn", "kamaya",
            "kamaye", "kamaaya", "incom")
        v("sale", "sales", "sell", "sold", "selling", "bikri", "bechi", "becha", "beche", "bechay", "bika", "biki", "farokht")
        v("kaam", "work", "worked", "kaaam")
        v("bana", "bani", "banai", "banayi", "banaye", "bane", "banaya", "banaai", "made", "make", "tayyar", "tayar", "tiyar", "produced")
        v("dali", "daali", "dala", "daala", "dalwai", "dalwaya", "dalvai", "daalin", "dalin")
        v("diye", "diya", "dia", "di", "de", "diy", "dye", "diey", "dya", "gave", "given", "give", "paid", "pay", "payed", "diyay", "diyey", "bheje", "bheja", "bheji")
        v("mile", "mila", "mili", "milay", "mily", "milain", "milen", "aaye", "aaya", "aya", "aye", "ayi", "aayi", "aai", "aae", "received", "receive",
            "got", "recieved", "recived", "wapis", "wapas", "vapas", "vapis", "returned")
        v("liye", "liya", "lia", "liay", "liyay", "liey", "took", "taken", "borrowed", "borrow")
        v("udhaar", "udhar", "udaar", "udhr", "udar", "udhari", "udhaari", "qarz", "qarza", "karz", "karza", "loan", "loans", "debt", "debts")
        v("kharida", "kharide", "khareeda", "kharidi", "khareedi", "kharid", "khareed", "purchase", "purchases", "purchased", "bought", "buy",
            "kharidari", "khareedari", "kharidaari")
        v("did", "kiya", "kiye", "kari", "kia", "hua", "hui", "hue", "huwa", "howa", "huye", "pi", "piya", "piyi", "piye", "khaya", "khayi", "khaye",
            "khai", "bhara", "bhari", "bhare", "bharwaya", "lagwaya", "karwaya", "mangwaya", "mangaya", "ate", "drank")
        v("profit", "profits", "nafa", "nafaa", "naffa", "munafa", "munaafa", "mnafa", "manafa", "faida", "faaida", "fayda", "faiyda")
        v("loss", "nuksan", "nuqsan", "nuksaan", "nuqsaan", "ghata", "ghaata", "khasara")
        v("worth", "networth", "maliyat", "haisiyat")
        v("cash", "naqd", "naqad", "galla", "galle", "gallay", "bacha", "bache", "bachay", "bachi", "left")
        v("rate", "rates", "bhao", "bhaav", "bhav", "reet")
        v("paise", "pese", "paisay", "paisa", "paison", "pesay", "rupay", "rupees", "rupaye", "money", "amount", "raqam", "rakam")
        v("monthly", "mahana", "mahwar", "mahinewar", "monthwise", "mahanah", "maahana")
        v("wise", "wais", "vise", "wize")
        v("har", "every", "each", "per")
        v("highest", "biggest", "largest", "max", "maximum", "most", "top")
        v("count", "ginti", "tadad")
        v("baar", "bar", "dafa", "dafaa", "martaba", "times")
        v("total", "kul", "sum", "totl", "totel")
        v("maal", "mal", "goods")
        v("cheez", "chiz", "cheezein", "cheezon", "item", "items", "things", "thing", "saman", "samaan")
        v("bande", "banda", "log", "logon", "person", "persons", "insan")
        v("category", "categories", "catagory", "catagories", "categorywise", "qisam")
        v("naam", "name", "names", "naamon", "members", "member")
        v("staff", "workers", "worker", "mazdoor", "mazdur", "labour", "labor", "employees", "employee", "karigar", "kaarigar", "karkun", "mulazim", "mulazmeen", "staf")
        v("customers", "customer", "gahak", "gaahak", "grahak", "client", "clients", "costumer", "costumers", "coustomer")
        v("suppliers", "supplier", "vendor", "vendors", "suplier", "supliers")
        v("people")
        v("mein", "me", "main", "mai", "men", "mn", "mei", "andar", "in")
        v("sab", "sabhi", "saara", "sara", "sari", "saari", "sare", "saare", "all", "tamam", "sabse", "sbse")
        v("aur", "or")
        v("sirf", "srf", "bas")
        v("cmd", "delete", "dlt", "delet", "hata", "hatao", "hatado", "mita", "mitao", "remove", "badal", "badlo", "badla", "change", "update", "edit",
            "rename", "merge", "milao", "move", "shift", "restore", "undo", "save", "likh", "likho", "add", "darj", "set", "put", "jagah", "daalo", "dalo",
            "banao", "bnao", "banado", "banaen", "banayen", "create", "karo", "kardo", "krdo", "kro", "karden", "kardein", "karein", "karen", "kijiye")
        v("chahiye", "chahye", "chaiye", "chahie", "want")
    }

    /** Every plain form above: these words are never a thing or a name. */
    private val vocabulary = forms.values.toSet()

    /** Small words that carry nothing to look up. */
    private val stop = setOf(
        "ka", "ki", "ke", "kay", "ky", "ko", "ku", "kou", "se", "sy", "say", "sey", "ne", "nay", "ny", "par", "pe", "pr", "per", "hai", "hain", "he", "hy",
        "hn", "ha", "haan", "tha", "thi", "the", "thay", "thy", "gaya", "gaye", "gayi", "gai", "gya", "gye", "gyi", "kar", "kr", "karna", "karta", "karte",
        "and", "bhi", "b", "to", "toh", "bhai", "bhae", "yar", "yaar", "ji", "jee", "zara", "jara", "plz", "please", "pls", "na", "naa", "do", "dein", "den",
        "mera", "meri", "mere", "mujhe", "mujhy", "mjhe", "maine", "mene", "humne", "hum", "hamara", "hamare", "hamari", "apna", "apni", "apne", "wala",
        "wali", "wale", "walay", "jo", "jis", "jin", "us", "uss", "ye", "yeh", "wo", "woh", "inka", "unka", "uska", "iska", "unki", "uski", "iski", "apka",
        "aapka", "aap", "ap", "tum", "tera", "teri", "tumhara", "ek", "aik", "a", "an", "of", "for", "on", "at", "from", "with", "by", "about", "my", "i",
        "you", "your", "we", "our", "does", "was", "were", "are", "be", "been", "have", "has", "had", "am", "it", "its", "that", "there", "these", "those",
        "so", "far", "s", "can", "could", "would", "will", "need", "kuch", "koi", "hota", "hoti", "hote", "hoga", "hogi", "honge", "rahe", "raha", "rahi",
        "chuka", "chuke", "ho", "hoe", "hoa", "hoi", "huay", "abtak", "since", "much", "many", "how", "unhon", "unho", "usne", "usse", "unse", "unko",
        "isko", "usko", "inko", "time", "upar", "neeche", "zyada", "kam", "than", "more", "less", "just", "only", "also", "baad", "pehle", "tell",
        "me", "mujhko", "humein", "hume", "humen", "ok", "okay", "acha", "theek", "thik", "bara", "bada", "barra", "bari", "badi", "mehnga", "mehngi"
    )

    /** Words that are a category, to its label (a question says "food", the entry says Groceries). */
    private val cats: Map<String, String> = buildMap {
        fun c(label: String, vararg f: String) = f.forEach { put(it, label) }
        c("Food", "food", "foods", "khana", "khaana", "khane", "khaane", "khanay", "khaanay", "khanna", "khane-peene")
        c("Transport", "transport", "safar", "travel", "gari", "gaari", "gaadi", "gadi", "conveyance", "safari")
        c("Bills", "bill", "bills", "bil", "bilz")
        c("Rent", "rent", "kiraya", "kiraaya", "kiraye", "kiray", "kraya")
        c("Health", "health", "sehat", "ilaj", "ilaaj", "medical", "dawai", "dawa", "dawaai", "medicine", "medicines", "doctor", "hospital")
        c("Education", "education", "school", "padhai", "parhai", "parhayi", "tuition", "fees", "fee")
        c("Sent home", "ghar", "home")
        c("Shopping", "shopping", "kapre", "kapray", "kapde", "clothes", "kapra")
        c("Mobile & Internet", "mobile", "internet", "recharge", "wifi")
        c("Personal care", "personal")
        c("Gifts & Charity", "gift", "gifts", "charity", "sadqa", "sadqah", "zakat", "khairat", "donation")
        c("Assets", "asset", "assets")
        c("Other", "other", "others")
        c("Production", "production")
        c("Fees & Govt", "govt", "government", "tax", "taxes")
    }

    private val books = mapOf("staff" to Sides.STAFF, "customers" to Sides.CUSTOMERS, "suppliers" to Sides.SUPPLIERS, "people" to Sides.PEOPLE)
    private val units = setOf("din", "hafta", "mahina", "saal")
    private val pairVerbs = setOf("kar", "kr", "daal", "dal", "bana", "hata", "mita", "badal", "likh", "rakh", "nikal", "bhej", "mila", "jod")
    private val pairEnds = setOf("do", "dein", "den", "dijiye", "dena", "dain", "dyn", "diya")

    private val months = mapOf("jan" to 0, "january" to 0, "janwari" to 0, "feb" to 1, "february" to 1, "farwari" to 1, "mar" to 2, "march" to 2,
        "apr" to 3, "april" to 3, "aprail" to 3, "may" to 4, "jun" to 5, "june" to 5, "joon" to 5, "jul" to 6, "july" to 6, "julai" to 6, "aug" to 7,
        "august" to 7, "agast" to 7, "sep" to 8, "sept" to 8, "september" to 8, "sitambar" to 8, "oct" to 9, "october" to 9, "aktubar" to 9,
        "nov" to 10, "november" to 10, "navambar" to 10, "nawambar" to 10, "dec" to 11, "december" to 11, "disambar" to 11)

    private val weekdays = mapOf("sunday" to Calendar.SUNDAY, "itwar" to Calendar.SUNDAY, "itwaar" to Calendar.SUNDAY, "monday" to Calendar.MONDAY,
        "peer" to Calendar.MONDAY, "somwar" to Calendar.MONDAY, "tuesday" to Calendar.TUESDAY, "mangal" to Calendar.TUESDAY,
        "wednesday" to Calendar.WEDNESDAY, "budh" to Calendar.WEDNESDAY, "thursday" to Calendar.THURSDAY, "jumerat" to Calendar.THURSDAY,
        "jumeraat" to Calendar.THURSDAY, "friday" to Calendar.FRIDAY, "juma" to Calendar.FRIDAY, "jumma" to Calendar.FRIDAY,
        "saturday" to Calendar.SATURDAY)

    private val small = mapOf("ek" to 1, "aik" to 1, "one" to 1, "do" to 2, "two" to 2, "teen" to 3, "three" to 3, "char" to 4, "chaar" to 4,
        "four" to 4, "panch" to 5, "paanch" to 5, "five" to 5, "chay" to 6, "chhe" to 6, "six" to 6, "saat" to 7, "seven" to 7, "aath" to 8,
        "eight" to 8, "nau" to 9, "nine" to 9, "das" to 10, "dus" to 10, "ten" to 10)

    private fun number(w: String?): Int? = w?.toIntOrNull() ?: small[w]

    private fun dayNumber(w: String?): Int? = w?.let { Regex("""^(\d{1,2})(?:st|nd|rd|th)?$""").find(it) }?.groupValues?.get(1)?.toInt()?.takeIf { it in 1..31 }

    /** A misspelt word one letter off (or two swapped) from a known form: "dikhaoo", "kharhca". */
    private fun fuzzy(w: String): String? {
        if (w.length < 5 || w.any { !it.isLetter() }) return null
        fun swapped(a: String, b: String) = a.length == b.length && a.indices.count { a[it] != b[it] } == 2 && a.toList().sorted() == b.toList().sorted()
        return forms.entries.filter { (k, _) -> k.length >= 5 && kotlin.math.abs(k.length - w.length) <= 1 && (Normalizer.distance(w, k) <= 1 || swapped(w, k)) }
            .map { it.value }.distinct().singleOrNull()
    }

    // ---------------------------------------------------------------- small helpers

    private fun words(name: String) = name.lowercase(Locale.ROOT).split(Regex("""[^\p{L}\p{N}]+""")).filter { it.isNotBlank() }

    private fun strings(a: JSONArray?) = a?.let { (0 until it.length()).map { i -> it.optString(i).trim() }.filter { s -> s.isNotBlank() } }.orEmpty()

    private fun list(a: JSONArray?) = a?.let { (0 until it.length()).mapNotNull { i -> it.optJSONObject(i) } }.orEmpty()

    /** The totals tool's groups by type, as (type, total). */
    private fun groups(t: JSONObject) = list(t.optJSONArray("groups")).map { it.optString("name") to it.optDouble("total") }

    private fun cap(w: String) = w.replaceFirstChar { it.uppercase() }

    private fun low(w: String) = w.replaceFirstChar { it.lowercase() }

    private fun or(names: List<String>) = if (names.size < 2) names.joinToString() else names.dropLast(1).joinToString(", ") + " or " + names.last()

    private fun num(v: Double) = if (v == Math.floor(v) && !v.isInfinite()) String.format(Locale.US, "%,d", v.toLong()) else String.format(Locale.US, "%,.2f", v)

    private fun money(v: Double, currency: String) = num(v) + if (currency.isBlank()) "" else " $currency"

    private fun cal(ms: Long): Calendar = Calendar.getInstance().apply {
        timeInMillis = ms; set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }

    private fun day(c: Calendar): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(c.time)

    private fun parse(d: String): Calendar? = runCatching { cal(SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(d)!!.time) }.getOrNull()
}
