package com.bolkhata.app

import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * New entries read by rule, without the AI: money with people, expenses, income, work and goods, several in one message.
 * The message is cut into clauses ("aur", commas, a finished verb); each clause gets its person (saved spelling, short
 * form, sound, or a new name), what happened, the amount or count and the day. All of it is saved with one add_entries,
 * as the AI would; a person and a number with nothing else get the option card (choose_kind).
 */
object LocalAdd : LocalAgent.Reader {

    override fun read(s: LocalAgent.Said): LocalAgent.Reply? {
        val back = followUp(s)
        val raw = back ?: s.raw
        val roman = if (back == null) s.roman else runCatching { Translit.toRoman(back) }.getOrDefault(back)
        val prepared = prepare(raw, roman)
        val (said, rest) = Parser.extractDate(prepared, s.now).let { (d, cut) -> if (d != null && cut.trim() != prepared.trim()) d to cut else null to prepared }
        val tokens = TOKEN.findAll(rest.lowercase(Locale.ROOT)).map { tokenOf(it.value) }.filter { it.isNotBlank() }.toList()
        if (tokens.isEmpty() || blocked(tokens)) return null
        val all = s.host.entries()
        // Urdu and Arabic script read letter by letter: the account's usual item is kept rather than the sound.
        val c = Ctx(s, all, if (raw.any { it in '\u0600'..'\u06FF' }) raw else null)
        c.date = said?.let { day(it) }
        c.last = s.justSaved.toSet().let { ids -> all.firstOrNull { it.id in ids && it.person.isNotBlank() }?.person }
        val got = split(tokens).map { readClause(it, c) }
        if (got.any { it is Got.Lost }) return null
        got.firstOrNull { it is Got.Ask }?.let { return LocalAgent.Reply((it as Got.Ask).text) }
        val saves = got.filterIsInstance<Got.Save>().flatMap { it.entries }
        val choose = got.filterIsInstance<Got.Choose>()
        if (choose.isNotEmpty()) {
            if (choose.size > 1 || saves.isNotEmpty() || c.setups.isNotEmpty()) return null
            s.use("choose_kind", JSONObject().put("person", choose[0].person).put("number", choose[0].number))
            return LocalAgent.Reply("")
        }
        if (saves.isEmpty()) return null
        saves.firstOrNull { needsRate(it, c) }?.let { e ->
            val n = e.optDouble("qty", 0.0)
            return LocalAgent.Reply("What rate for " + plain(n) + " " + e.optString("item").ifBlank { "of them" } + "?")
        }
        s.notes.said = (s.notes.said + saves.flatMap { e -> listOf("amount", "qty").map { e.optDouble(it, 0.0) } }.filter { it > 0 }).distinct()
        c.setups.forEach { (book, name) -> s.use("put_in_account", JSONObject().put("account", book).put("people", JSONArray(listOf(name)))) }
        val out = s.use("add_entries", JSONObject().put("entries", JSONArray(saves)))
        if (out.has("error")) return LocalAgent.Reply("That could not be saved: " + out.optString("error") + ".")
        return LocalAgent.Reply(
            when {
                s.notes.added.isEmpty() && s.notes.skipped > 0 -> "That is already saved."
                s.notes.skipped > 0 -> "Saved; the same entry from a moment ago was not added again."
                c.setups.isNotEmpty() -> c.setups.joinToString(" ") { (book, name) -> "$name is in $book now." }
                else -> ""
            }
        )
    }

    // ---------- what a clause gives ----------

    private sealed class Got {
        /** Entries to save (add_entries JSON). */
        class Save(val entries: List<JSONObject>) : Got()
        /** A question back to the person. */
        class Ask(val text: String) : Got()
        /** A person and a number, nothing else: the option card. */
        class Choose(val person: String, val number: Double) : Got()
        /** Nothing to save in it ("aaj", "theek hai", "naya staff rashid hai"). */
        object Empty : Got()
        /** Numbers it could not place: the whole message is left to the older rules. */
        object Lost : Got()
    }

    /** The message read so far: who was named last, the day said, people put in accounts, new names. */
    private class Ctx(val s: LocalAgent.Said, val all: List<Entry>, val script: String?) {
        val setup: SideSetup = s.host.setup()
        val names: List<Pair<String, List<String>>> = s.host.names().map { it.trim() }.filter { it.isNotBlank() }.distinctBy { it.lowercase() }
            .map { n -> n to n.lowercase(Locale.ROOT).split(Regex("""[^\p{L}]+""")).filter { it.length >= 2 } }
        val known: List<String> = Goods.known(all)
        /** Names said as new in this message ("naya staff rashid"), by their word. */
        val fresh = mutableMapOf<String, String>()
        /** People put in an account by this message: (account, name). */
        val setups = mutableListOf<Pair<String, String>>()
        var last: String? = null
        var lastKind: String? = null
        var lastGoods = false
        var lastWay: Char? = null
        var date: String? = null
    }

    /** Who a word of the message is. */
    private sealed interface Who

    /** One person named in a clause: the saved (or new) name, where its words are, and which way it goes ('T' to them, 'B' by them, 'F' from them). */
    private class Mention(val name: String, val from: Int, val to: Int, val way: Char?) : Who

    /** A word that fits two or more saved names. */
    private class Unsure(val word: String, val choices: List<String>) : Who

    /** One clause: its day, its people, its numbers and what happened. */
    private fun readClause(all: List<String>, c: Ctx): Got {
        relativeDay(all)?.let { c.date = if (it == 0) null else day(c.s.now - it * DAY) }
        val w = all.filter { it !in dayWords }
        if (w.isEmpty()) return Got.Empty
        val isNew = w.any { it in newWords }
        val book = w.firstNotNullOfOrNull { bookWords[it] }
        val mentions = mutableListOf<Mention>()
        var i = 0
        while (i < w.size) {
            when (val found = person(w, i, c, isNew && book != null)) {
                is Unsure -> return Got.Ask("Which " + found.word.replaceFirstChar { it.uppercase() } + " do you mean: " + listed(found.choices) + "?")
                is Mention -> { mentions += found; c.last = found.name; i = found.to }
                null -> i++
            }
        }
        if (isNew && book != null) mentions.map { it.name }.distinct().filter { n -> c.fresh.values.contains(n) }.forEach { n ->
            if (c.setups.none { it.second == n }) c.setups += book to n
        }
        val used = mentions.flatMap { it.from until it.to }.toSet()
        val f = Flags(w)
        val nums = numbers(w, used)
        val people = mentions.map { it.name }.distinct()
        val way = mentions.firstNotNullOfOrNull { it.way }
        val who = people.firstOrNull() ?: c.last?.takeIf { c.lastGoods }
        val business = who != null && bookOf(who, c) in setOf(Sides.STAFF, Sides.SUPPLIERS, Sides.CUSTOMERS)
        val selling = f.sell || (way == 'T' && !f.make) || (who != null && bookOf(who, c) == Sides.CUSTOMERS)
        val goods = goodsIn(w, used, nums, c, who, nums.rate > 0 || f.sell || (f.make && way != 'T'), business, selling)
        val content = w.indices.filter { it !in used && it !in goods.span && isContent(w[it]) }.map { w[it] }
        val money = nums.money.filter { it.first != goods.qtyAt }
        if (people.isNotEmpty()) return withPeople(w, mentions, people, way, f, nums, goods, money, content, c).also { c.lastWay = way }
        // No one named: the work or money of the person before goes on ("..., 2000 bharai ki"), else an expense or income.
        val last = c.last
        if (last != null && c.lastGoods && goods.item != null && goods.category.let { it == null || it == Cat.PRODUCTION }) {
            if (goods.qty != null) return Got.Save(listOf(goodsEntry(c.lastKind ?: "work", last, goods, nums, money, w, c)))
            if (money.isNotEmpty()) return Got.Save(listOf(JSONObject().put("type", c.lastKind ?: "work").put("amount", money[0].second).put("person", last).put("item", goods.item).dated(c)))
        }
        if (last != null && c.lastKind != null && w.none { isContent(it) } && nums.money.isNotEmpty()) {
            // "hamid ne ... aur 2000 advance liye": he is still the one doing it; after "nargis ko ..." the owner is.
            val kind = moneyKind(c.lastWay.takeIf { it == 'B' }, f, balance(last, c)) ?: c.lastKind?.takeIf { !c.lastGoods } ?: return Got.Lost
            return Got.Save(nums.money.map { (_, n) -> entry(kind, n, last, c).apply { note(w, emptyList())?.let { put("note", it) } } })
        }
        if (nums.money.isEmpty()) return Got.Empty
        c.lastGoods = false; c.lastKind = null; c.lastWay = null
        return spending(w, f, c)
    }

    /** A clause naming people: work or goods by count, else money given or taken. */
    private fun withPeople(w: List<String>, mentions: List<Mention>, people: List<String>, way: Char?, f: Flags, nums: Nums, goods: Counted,
                           money: List<Pair<Int, Double>>, content: List<String>, c: Ctx): Got {
        val first = people.first()
        if (goods.item != null && goods.qty != null) {
            val kind = goodsKind(first, way, f, goods.item, c)
            c.lastGoods = true; c.lastKind = kind
            return Got.Save(listOf(goodsEntry(kind, first, goods, nums, money, w, c)))
        }
        if (money.isEmpty()) {
            // "hamid ne aaj kaam kiya": work with nothing to count.
            return if ((f.work || f.make || goods.item != null) && way != 'T' && !f.give && !f.get) Got.Ask("How many did $first make, and of what?") else Got.Empty
        }
        // Money for a sale or work said as a sum ("nargis ko 50000 ki pakki eent di", "hamid ne 5000 ka kaam kiya").
        val plainWork = way != 'T' && !f.give && !f.get && !f.ret && !f.loan
        val sum = when {
            goods.item != null -> goodsKind(first, way, f, goods.item, c).takeIf { (it == "sale" && way == 'T') || (it == "work" && (plainWork || (way == 'F' && f.get && !f.give))) }
            f.work && plainWork -> "work"
            else -> null
        }
        if (sum != null) {
            c.lastGoods = true; c.lastKind = sum
            return Got.Save(listOf(JSONObject().put("type", sum).put("amount", money.first().second).put("person", first).put("item", goods.item.orEmpty()).dated(c)))
        }
        val pairs: List<Pair<String, Double>> = when {
            people.size == money.size -> people.zip(money.map { it.second })
            money.size == 1 -> people.map { it to money[0].second }
            people.size == 1 -> money.map { first to it.second }
            else -> return Got.Lost
        }
        val out = mutableListOf<JSONObject>()
        for ((p, n) in pairs) {
            val own = mentions.firstOrNull { it.name == p }?.way ?: way
            val kind = moneyKind(own, f, balance(p, c)) ?: c.lastKind?.takeIf { !c.lastGoods }
                ?: return if (pairs.size == 1 && content.isEmpty()) Got.Choose(p, n) else Got.Lost
            out += entry(kind, n, p, c).apply { note(w, content)?.let { put("note", it) } }
            c.lastKind = kind
        }
        c.lastGoods = false
        return Got.Save(out)
    }

    /** An expense or income with no person: one per number when several things are said ("chai 5 nashta 10"). */
    private fun spending(w: List<String>, f: Flags, c: Ctx): Got {
        val groups = groups(w)
        val out = mutableListOf<JSONObject>()
        for (g in groups) {
            val n = g.indices.firstOrNull { num(g[it]) != null && !isQty(g, it) }?.let { num(g[it]) } ?: continue
            val gf = if (groups.size == 1) f else Flags(g)
            val content = g.filter { isContent(it) }.map { expand(it) }
            val cat = categoryOf(content, if (groups.size == 1) c.script else null, g.filter { num(it) == null })
            val family = g.firstOrNull { it in familyWords || it == "bhai" }
            val text = (if (family != null && content.size > 1) content - familyWords else content).ifEmpty { listOfNotNull(family) }.take(3).joinToString(" ")
            val income = gf.income || (cat?.first == Cat.RENT && gf.inflow && !gf.give && !gf.spend) || (cat == null && family == null && gf.inflow && !gf.give && !gf.spend)
            val item = when {
                c.script != null && cat != null -> cat.second
                content == listOf("ghar") -> "Sent home"
                else -> text.ifBlank { cat?.second.orEmpty() }
            }
            val e = when {
                // A cash sale with no customer named ("aaj 3000 ki sale hui").
                !income && g.any { it in sellWords || it in saleWords } -> JSONObject().put("type", "sale").put("amount", n).put("paid_now", true).put("item", item.ifBlank { "Sale" })
                income -> JSONObject().put("type", "income").put("amount", n).put("item", item.ifBlank { "Income" })
                cat != null -> JSONObject().put("type", "expense").put("amount", n).put("category", cat.first.label).put("item", item)
                family != null && (gf.give || gf.spend) -> JSONObject().put("type", "expense").put("amount", n).put("category", Cat.GHAR.label).put("item", item)
                content.isNotEmpty() -> JSONObject().put("type", "expense").put("amount", n).put("category", Cat.OTHER.label).put("item", item)
                else -> return Got.Lost
            }
            out += e.dated(c)
        }
        return if (out.isEmpty()) Got.Lost else Got.Save(out)
    }

    /** Words of a clause with no person, cut so each number goes with its own thing: "chai 5 nashta 10", "5 chai 10 nashta". */
    private fun groups(w: List<String>): List<List<String>> {
        val at = w.indices.filter { num(w[it]) != null && !isQty(w, it) }
        if (at.size < 2) return listOf(w)
        val numberFirst = w.indexOfFirst { isContent(it) || num(it) != null }.let { it >= 0 && num(w[it]) != null }
        val out = mutableListOf<List<String>>()
        var from = 0
        for ((k, p) in at.withIndex()) {
            if (numberFirst) { if (k > 0) { out += w.subList(from, p); from = p } }
            else {
                // The words after the number up to the next thing stay with it ("chai 10 ki pi").
                var end = p + 1
                while (end < w.size && !isContent(w[end]) && num(w[end]) == null) end++
                if (k == at.lastIndex) end = w.size
                out += w.subList(from, end); from = end
            }
        }
        if (from < w.size) out += w.subList(from, w.size)
        return out.filter { g -> g.any { num(it) != null } }
    }

    // ---------- people ----------

    /** The person whose name starts at [i], or a question when two saved names fit; null when [i] is no one. */
    private fun person(w: List<String>, i: Int, c: Ctx, setting: Boolean): Who? {
        val x = w[i]
        if (num(x) != null) return null
        pronoun(w, i)?.let { (end, way) ->
            val p = c.last ?: return null
            return Mention(p, i, end, way ?: wayAfter(w, end) ?: wayBefore(w, i))
        }
        if (x.length < 2 || x in stop || x in honorifics) return null
        c.fresh[x]?.let { return Mention(it, i, i + 1, wayAfter(w, i + 1) ?: wayBefore(w, i)) }
        // "naya staff rashid hai": a new person, even when a saved name has that word.
        val near = (maxOf(0, i - 2)..minOf(w.lastIndex, i + 2)).any { w[it] in bookWords || w[it] in newWords }
        if (setting && near && isContent(x) && !isThing(x, c)) {
            var end = i + 1
            while (end < w.size && end - i < 3 && isContent(w[end]) && !isThing(w[end], c)) end++
            val name = w.subList(i, end).joinToString(" ") { it.replaceFirstChar { ch -> ch.uppercase() } }
            for (k in i until end) c.fresh[w[k]] = name
            return Mention(name, i, end, wayAfter(w, end) ?: wayBefore(w, i))
        }
        saved(w, i, c)?.let { return it }
        if (!namePlace(w, i) || x.length < 3) return null
        // A word where a name must be: a saved name misheard ("motion" for Mohsin), a thing ("dhobi ko"), or a new person.
        misheard(x, c)?.let { return Mention(it, i, i + 1, wayAfter(w, i + 1) ?: wayBefore(w, i)) }
        if (isThing(x, c)) return null
        val two = i > 0 && w[i - 1] !in stop && num(w[i - 1]) == null && Understand.isCommonName(w[i - 1]) && !isThing(w[i - 1], c)
        val from = if (two) i - 1 else i
        val name = w.subList(from, i + 1).joinToString(" ") { it.replaceFirstChar { ch -> ch.uppercase() } }
        c.fresh[x] = name
        return Mention(name, i, i + 1, wayAfter(w, i + 1) ?: wayBefore(w, i))
    }

    /** A saved name at [i]: its words said ("salim khan"), one word of it, or a short or misspelt form; several fitting is [Unsure]. */
    private fun saved(w: List<String>, i: Int, c: Ctx): Who? {
        val x = w[i]
        var hits = c.names.filter { (_, parts) -> x in parts }
        if (hits.isEmpty()) {
            if (x.length < 3 || isThing(x, c)) return null
            val scored = c.names.mapNotNull { (n, parts) ->
                parts.filter { it.length >= 3 && sameStart(x, it) }.maxOfOrNull { p -> if (Understand.closeName(x, p) || soundAlike(x, p)) Normalizer.similarity(x, p) else 0.0 }
                    ?.takeIf { it > 0 }?.let { (n to parts) to it }
            }.sortedByDescending { it.second }
            if (scored.isEmpty()) return null
            hits = scored.filter { scored[0].second - it.second < 0.03 }.map { it.first }
        }
        var end = i + 1
        while (end < w.size && hits.size > 1) {
            val next = hits.filter { (_, parts) -> parts.any { it == w[end] || (w[end].length >= 3 && Understand.closeName(w[end], it)) } }
            if (next.isEmpty()) break
            hits = next; end++
        }
        if (hits.size > 1) {
            val said = w.subList(i, end)
            hits.singleOrNull { it.second == said }?.let { hits = listOf(it) }
        }
        if (hits.size > 1) return Unsure(x, hits.map { it.first })
        val (name, parts) = hits[0]
        // The rest of the name said too ("nargis salim rashid").
        while (end < w.size && end - i < parts.size && parts.any { it == w[end] }) end++
        return Mention(name, i, end, wayAfter(w, end) ?: wayBefore(w, i))
    }

    /** A plain word standing where a name must be that sounds like one saved name ("motion": Mohsin); not a known first name of its own. */
    private fun misheard(x: String, c: Ctx): String? {
        if (Understand.isCommonName(x) || x in stop) return null
        val scored = c.names.map { (n, parts) -> n to (parts.filter { it.length >= 3 && (it[0] == x[0]) }.maxOfOrNull { Normalizer.similarity(x, it) } ?: 0.0) }
            .filter { it.second >= 0.8 }.sortedByDescending { it.second }
        if (scored.isEmpty() || (scored.size > 1 && scored[0].second - scored[1].second < 0.03)) return null
        return scored[0].first
    }

    /** A misspelt name keeps its first letter, or starts with a vowel when the name does ("ehmad", "ahmed"). */
    private fun sameStart(a: String, b: String) = a[0] == b[0] || (a[0] in "aeiou" && b[0] in "aeiou")

    /** The same consonants in the same order from the same first letter: "hameed" and "hamid", "saleem" and "salim". */
    private fun soundAlike(a: String, b: String): Boolean {
        fun bones(s: String) = s.filter { it !in "aeiouy" }.fold("") { acc, ch -> if (acc.endsWith(ch)) acc else acc + ch }
        return a.length >= 4 && b.length >= 3 && a[0] == b[0] && bones(a).length >= 2 && bones(a) == bones(b) && kotlin.math.abs(a.length - b.length) <= 3
    }

    /** "us ne", "usne", "usko", "uske": the person named before. Its end and the way it goes. */
    private fun pronoun(w: List<String>, i: Int): Pair<Int, Char?>? {
        val x = w[i]
        pronouns[x]?.let { return i + 1 to it.takeIf { ch -> ch != '-' } }
        if (x in setOf("us", "is", "un", "in", "iss", "uss")) {
            val next = w.getOrNull(i + 1) ?: return null
            if (next in toWords || next in byWords || next in fromWords) return i + 1 to null
        }
        return null
    }

    private fun wayAfter(w: List<String>, at: Int): Char? {
        var j = at
        while (j < w.size && w[j] in honorifics) j++
        val x = w.getOrNull(j) ?: return null
        return when {
            x in toWords -> 'T'
            x in byWords -> 'B'
            x in fromWords -> 'F'
            x == "ki" && w.getOrNull(j + 1) == "taraf" -> 'F'
            x in englishBy -> 'B'
            else -> null
        }
    }

    private fun wayBefore(w: List<String>, at: Int): Char? = when (w.getOrNull(at - 1)) {
        "to" -> 'T'
        "from" -> 'F'
        in englishTo -> 'T'
        else -> null
    }

    /** True when the word at [i] stands where a name goes: before ko/ne/se, after "to"/"from"/"gave". */
    private fun namePlace(w: List<String>, i: Int): Boolean {
        val common = Understand.isCommonName(w[i])
        val english = w.getOrNull(i + 1) in englishBy
        return (wayAfter(w, i + 1) != null && (!english || common)) || w.getOrNull(i - 1) in setOf("to", "from") || w.getOrNull(i - 1) in englishTo ||
            (common && w.getOrNull(i + 1)?.let { num(it) } != null)
    }

    /** The person's account: as put by this message, else as saved. */
    private fun bookOf(p: String, c: Ctx) = c.setups.firstOrNull { it.second == p }?.first ?: Sides.bookOf(p, c.all, c.setup)

    private fun balance(p: String, c: Ctx) = c.all.filter { it.person.trim().equals(p, true) }.sumOf { personEffect(it) }

    // ---------- what happened ----------

    /** What the clause's verbs say. */
    private class Flags(w: List<String>) {
        val give = w.any { it in giveWords }
        val get = w.any { it in getWords }
        val ret = w.any { it in returnWords }
        val loan = w.any { it in loanWords }
        val sell = w.any { it in sellWords }
        val make = w.any { it in makeWords }
        val work = w.any { it in workWords }
        val income = w.any { it in incomeWords }
        val inflow = w.any { it in inWords }
        val spend = w.any { it in spendWords }
    }

    /**
     * Money with a person, as the AI is told: to them is lent (Paid), from them is received_back (Received); borrowed and
     * paid_back only when a loan word is said. Null when nothing tells.
     */
    private fun moneyKind(way: Char?, f: Flags, balance: Double): String? {
        if (f.loan) return when {
            f.ret -> if (way == 'T' || (way == null && f.give && !f.get)) "paid_back" else "received_back"
            way == 'T' -> "lent"
            way == 'F' -> "borrowed"
            way == 'B' -> if (f.get && !f.give) "lent" else "borrowed"
            f.get -> "borrowed"
            f.give -> "lent"
            else -> null
        }
        if (f.ret) return when (way) {
            'T' -> if (balance < -0.5) "paid_back" else "lent"
            'B', 'F' -> "received_back"
            else -> if (f.give && !f.get && balance < -0.5) "paid_back" else "received_back"
        }
        return when (way) {
            'T' -> "lent"
            'F' -> "received_back"
            'B' -> if (f.get && !f.give) "lent" else "received_back"
            else -> when {
                f.give && !f.get -> "lent"
                f.get && !f.give -> "received_back"
                else -> null
            }
        }
    }

    /** Goods to a customer are a sale, work and goods from staff or a supplier are work; for anyone else the words and the thing's past tell. */
    private fun goodsKind(person: String, way: Char?, f: Flags, item: String, c: Ctx): String {
        val book = bookOf(person, c)
        val past = c.all.filter { it.item.equals(item, true) }
        return when {
            f.sell -> "sale"
            book == Sides.CUSTOMERS -> "sale"
            book == Sides.STAFF || book == Sides.SUPPLIERS -> "work"
            past.any { it.type == Type.SALE } && past.none { it.type == Type.PURCHASE } -> "sale"
            past.any { it.type == Type.PURCHASE } -> "work"
            way == 'T' && !f.make -> "sale"
            else -> "work"
        }
    }

    private fun entry(kind: String, n: Double, person: String, c: Ctx) = JSONObject().put("type", kind).put("amount", n).put("person", person).dated(c)

    private fun goodsEntry(kind: String, person: String, g: Counted, nums: Nums, money: List<Pair<Int, Double>>, w: List<String>, c: Ctx): JSONObject {
        val e = JSONObject().put("type", kind).put("qty", g.qty).put("item", g.item).put("person", person).put("again", true)
        if (nums.rate > 0) e.put("rate", nums.rate).put("per_1000", nums.per1000 || per1000(g.item.orEmpty(), nums.rate, person, c))
        else money.firstOrNull()?.let { e.put("amount", it.second) }
        if (w.any { it in cashWords }) e.put("paid_now", true)
        g.unit?.let { e.put("unit", it) }
        c.last = person
        return e.dated(c)
    }

    /** True when a rate said for the thing is per thousand: as its last rate was, or bricks at a rate of 100 or more. */
    private fun per1000(item: String, rate: Double, person: String, c: Ctx): Boolean {
        val last = Goods.lastRate(item, person, null, c.all) ?: return item.lowercase().split(' ').any { it in brickWords } && rate >= 100
        // A rate near the last one per thousand is per thousand too; one near its price for one piece is for each.
        return last.unit == "1000" && kotlin.math.abs(Math.log(rate / last.rate)) <= kotlin.math.abs(Math.log(rate * 1000 / last.rate))
    }

    /** A sale or work with no rate said and none known before: ask for it rather than fail. */
    private fun needsRate(e: JSONObject, c: Ctx): Boolean {
        val type = e.optString("type")
        if ((type != "sale" && type != "work") || e.optDouble("qty", 0.0) <= 0 || e.has("rate") || e.has("amount")) return false
        val person = e.optString("person")
        val t = if (type == "sale") Type.SALE else Type.PURCHASE
        return Goods.lastRate(e.optString("item"), person, t == Type.PURCHASE, c.all) == null &&
            c.all.none { it.type == t && it.rate > 0 && it.person.equals(person, true) }
    }

    private fun note(w: List<String>, content: List<String>): String? = when {
        "advance" in w || "adv" in w || "edvance" in w -> "Advance"
        w.any { it in incomeWords } -> content.firstOrNull { it in incomeWords }?.replaceFirstChar { it.uppercase() } ?: "Salary"
        else -> null
    }

    private fun JSONObject.dated(c: Ctx): JSONObject = apply { c.date?.let { put("date", it) } }

    // ---------- numbers, goods, things ----------

    /** The numbers of a clause: money, the rate ("200 per 1000", "13000 ke hisaab se") and the numbers that are only "per 1000". */
    private class Nums(val money: List<Pair<Int, Double>>, val rate: Double, val per1000: Boolean)

    private fun numbers(w: List<String>, used: Set<Int>): Nums {
        var rate = 0.0
        var per1000 = false
        val taken = mutableSetOf<Int>()
        for (i in w.indices) {
            val n = num(w[i]) ?: continue
            if (i in taken || i in used) continue
            // "12 rupay wali", "40 rs per piece": the money word between the number and the rate words is skipped.
            val skip = if (w.getOrNull(i + 1) in currencyWords) 1 else 0
            val a = w.getOrNull(i + 1 + skip)
            val b = w.getOrNull(i + 2 + skip)
            when {
                // "1000 ka 1100": per thousand
                n == 1000.0 && a in priceWords && b != null && num(b) != null && w.getOrNull(i + 3) !in thingAfterPrice -> { rate = num(b)!!; per1000 = true; taken += i; taken += i + 2 }
                a in priceWords && b in rateWords -> { rate = n; taken += i }
                a in perWords -> {
                    rate = n; taken += i
                    if (b != null && (num(b) == 1000.0 || b in thousandWords)) { per1000 = true; if (num(b) != null) taken += i + 2 + skip }
                }
                a == "rate" || a in eachWords || (a in setOf("wala", "wali", "wale")) -> { rate = n; taken += i }
                a in priceWords && (b in setOf("ek", "aik", "one", "dana") || b in unitWords) -> { rate = n; taken += i }
                w.getOrNull(i - 1) in setOf("rate", "at", "@") -> {
                    rate = n; taken += i
                    if (a in perWords && b != null && (num(b) == 1000.0 || b in thousandWords)) { per1000 = true; if (num(b) != null) taken += i + 2 }
                }
                w.getOrNull(i - 1) in perWords && (n == 1000.0) -> { per1000 = true; taken += i }
            }
        }
        // "fi 1000 200": the rate after the per-thousand words
        for (i in w.indices) if (w[i] in perWords && num(w.getOrNull(i + 1) ?: "") == 1000.0 && num(w.getOrNull(i + 2) ?: "") != null && i + 2 !in taken) {
            rate = num(w[i + 2])!!; per1000 = true; taken += i + 1; taken += i + 2
        }
        // "5 baje", "2 din", "03001234567": a time, a span or a phone number, never money.
        val money = w.indices.filter { it !in taken && it !in used && num(w[it]) != null && w.getOrNull(it + 1) !in timeUnits && !(w[it].startsWith("0") && w[it].length >= 7) }
            .map { it to num(w[it])!! }
        return Nums(money, rate, per1000)
    }

    /** A counted thing in a clause: its name (as already saved when it is the same), how many, and where its words are. */
    private class Counted(val item: String?, val qty: Double?, val qtyAt: Int, val span: Set<Int>, val unit: String?, val category: Cat?)

    private fun goodsIn(w: List<String>, used: Set<Int>, nums: Nums, c: Ctx, person: String?, trade: Boolean, business: Boolean, selling: Boolean): Counted {
        val free = w.indices.filter { it !in used }
        val moneyAt = nums.money.map { it.first }.toSet()
        // A name already in use, every word of it said (in its spelling, by sound, or in English: "raw bricks").
        var best: Pair<String, Set<Int>>? = null
        for (k in c.known) {
            val kw = k.lowercase(Locale.ROOT).split(Regex("""[^\p{L}\p{N}]+""")).filter { it.isNotBlank() }
            if (kw.isEmpty()) continue
            val at = kw.map { part -> free.firstOrNull { j -> num(w[j]) == null && w[j] !in stop && w[j] !in unitWords && alike(w[j], part) } }
            if (at.any { it == null } || at.toSet().size != kw.size) continue
            if (best == null || kw.size > best.second.size) best = k to at.filterNotNull().toSet()
        }
        // Part of a saved name ("eent", "pakki"): the one this person had before, else the only one it fits.
        if (best == null) {
            val one = free.filter { num(w[it]) == null && isContent(w[it]) && w[it] !in unitWords }
            fun fits(names: List<String>) = names.filter { m -> val mw = m.lowercase().split(' '); one.isNotEmpty() && one.all { j -> mw.any { alike(w[j], it) } } }
            val mine = fits(c.all.filter { person != null && it.person.equals(person, true) && it.qty > 0 && it.item.isNotBlank() }.map { it.item }.distinct())
            val any = fits(c.known)
            val side = any.filter { k -> c.all.any { it.item.equals(k, true) && it.type == (if (selling) Type.SALE else Type.PURCHASE) } }
            (mine.singleOrNull() ?: any.singleOrNull() ?: side.singleOrNull())?.let { best = it to one.toSet() }
        }
        if (best != null) {
            val span = best.second
            val qty = qtyNear(w, span, moneyAt)
            return Counted(best.first, qty?.second, qty?.first ?: -1, span, qty?.let { unitAt(w, it.first) }, null)
        }
        // A new thing said right after a count: "500 shirts", "4000 nikasi ki".
        for ((i, n) in nums.money) {
            if (w.getOrNull(i + 1) in priceWords) continue
            var j = i + 1
            val unit = w.getOrNull(j)?.takeIf { it in unitWords }
            if (unit != null) j++
            val start = j
            while (j < w.size && j - start < 3 && j !in used && num(w[j]) == null && isContent(w[j]) && w[j] !in incomeWords) j++
            val words = if (j == start) listOfNotNull(unit) else w.subList(start, j)
            if (words.isEmpty()) continue
            val cat = categoryOf(words.map { expand(it) }, null)?.first
            if (cat != null && cat != Cat.PRODUCTION && unit == null && !trade) continue
            if (cat == null && unit == null && !trade && !business) continue
            if (w.getOrNull(j) in setOf("ke", "ki", "for") && w.getOrNull(j + 1).let { it == null || it in setOf("liye", "lie", "lye") || w[j] == "for" }) continue
            return Counted(words.joinToString(" ").replaceFirstChar { it.uppercase() }, n, i, (start until j).toSet(), unit.takeIf { j > start }, cat)
        }
        // A thing worth a sum ("500 ka saman"): no count, the money is its price.
        for ((i, _) in nums.money) {
            if (w.getOrNull(i + 1) !in priceWords) continue
            val words = (i + 2 until minOf(w.size, i + 4)).takeWhile { it !in used && isContent(w[it]) && w[it] !in incomeWords }
            if (words.isEmpty()) continue
            val cat = categoryOf(words.map { expand(w[it]) }, null)?.first
            if (cat != null && cat != Cat.PRODUCTION && !trade && !business) continue
            return Counted(words.joinToString(" ") { w[it] }.replaceFirstChar { it.uppercase() }, null, -1, words.toSet(), null, cat)
        }
        return Counted(null, null, -1, emptySet(), null, null)
    }

    /** The count of a thing: the number just before its name (a unit word may stand between) or just after it. */
    private fun qtyNear(w: List<String>, span: Set<Int>, moneyAt: Set<Int>): Pair<Int, Double>? {
        val a = span.min()
        val b = span.max()
        for (j in listOf(a - 1, a - 2)) if (j in moneyAt && (j == a - 1 || w[a - 1] in unitWords)) return j to num(w[j])!!
        for (j in listOf(b + 1, b + 2)) if (j in moneyAt && (j == b + 1 || w[b + 1] in unitWords) && w.getOrNull(j + 1) !in priceWords) return j to num(w[j])!!
        return null
    }

    private fun unitAt(w: List<String>, qtyAt: Int) = w.getOrNull(qtyAt + 1)?.takeIf { it in unitWords }

    /** A number followed by a unit ("5 kg"): a count, never money. */
    private fun isQty(w: List<String>, i: Int) = w.getOrNull(i + 1) in unitWords

    /** Two spellings of one word of a thing's name: the same, one letter apart, the same sound, or its English ("bricks": eent). */
    private fun alike(said: String, part: String): Boolean {
        val s = synonyms[said] ?: said
        if (s == part || said == part) return true
        val ks = Names.key(s)
        val sound = ks == Names.key(part) && (ks.length >= 3 || (ks.length == 2 && kotlin.math.abs(s.length - part.length) <= 1))
        return sound || (s.length >= 4 && part.length >= 4 && Normalizer.distance(s, part) <= 1)
    }

    /** The category and usual item of a thing: the owner's taught words and the app's rules, then a few more; in Urdu or Arabic script by the script. */
    private fun categoryOf(content: List<String>, script: String?, around: List<String> = emptyList()): Pair<Cat, String>? {
        content.firstNotNullOfOrNull { extra[it] }?.let { return it }
        val words = (content + content.map { Normalizer.root(it) } + around).distinct()
        if (words.isNotEmpty()) Parser.guessCategory(words.joinToString(" "))?.let { found ->
            // "doctor ki fees", "mobile ka bill": the fee or bill of a known thing goes with that thing.
            if (found.second != "Fees & charges" && found.second != "Other bills") return found
            return content.filter { it !in setOf("fee", "fees", "bill", "bills", "charges") }.firstNotNullOfOrNull { Parser.guessCategory(it) } ?: found
        }
        if (script == null) return null
        val bare = script.split(Regex("""\s+""")).joinToString(" ") { word ->
            listOf("بال", "لل", "ال", "و").firstOrNull { word.length > it.length + 2 && word.startsWith(it) }?.let { word.removePrefix(it) } ?: word
        }
        return Parser.guessCategory(bare)
    }

    /** A short form of an everyday thing typed without vowels ("ptrl", "dwai"): the full word. */
    private fun expand(w: String) = if (w.length < 3 || Parser.guessCategory(w) != null) w else shortThings.firstOrNull { Understand.shortFor(w, it) } ?: w

    private fun isThing(x: String, c: Ctx): Boolean =
        x in extra || x in familyWords || x in places || Parser.guessCategory(x) != null || shortThings.any { Understand.shortFor(x, it) } ||
            c.known.any { k -> k.lowercase().split(' ').any { alike(x, it) } } || x in synonyms || x in incomeWords || x in moneyNouns || x in unitWords

    private fun isContent(x: String) = num(x) == null && x !in stop && x !in dayWords && x !in moneyNouns && x !in unitWords && x !in honorifics &&
        x !in giveWords && x !in getWords && x !in returnWords && x !in loanWords && x !in sellWords && x !in makeWords && x !in workWords &&
        x !in spendWords && x !in inWords && x !in pronouns && x !in bookWords && x !in newWords && x !in perWords && x !in eachWords && x.length >= 2

    // ---------- the text ----------

    /** The message ready to read: Roman letters, lower case, every number in digits ("2 hazar" 2000, "5k" 5000, "1,500" 1500). */
    private fun prepare(raw: String, roman: String): String {
        val digits = raw.map { ch ->
            when (ch) {
                in '۰'..'۹' -> '0' + (ch - '۰')
                in '٠'..'٩' -> '0' + (ch - '٠')
                in '०'..'९' -> '0' + (ch - '०')
                else -> ch
            }
        }.joinToString("")
        var t = (if (digits != raw) runCatching { Translit.toRoman(digits) }.getOrDefault(digits) else roman).lowercase(Locale.ROOT)
        t = t.replace(Regex("""(?<=\d),(?=\d{2,3}(?!\d))"""), "")
        t = t.replace(Regex("""(?<![\p{L}])(?:rs|pkr|sar|inr|aed)\.?\s*(?=\d)"""), " ").replace("/-", " ").replace("/=", " ")
        t = t.replace(Regex("""(\d+(?:\.\d+)?)k(?![\p{L}])""")) { plain(it.groupValues[1].toDouble() * 1000) }
        t = t.replace(Regex("""(\d)(\p{L})"""), "$1 $2").replace(Regex("""(\p{L})(\d)"""), "$1 $2")
        t = t.replace(Regex("""(?<![\p{L}])(ek|aik|do|teen|char|chaar|panch|paanch|chhe|che|saat|aath|nau|das|dus|\d+)\s+(?:so|sow|sao)(?![\p{L}])"""), "$1 sau")
        t = t.replace(Regex("""(?:(sadhe|saadhe|sarhe|saarhe|sawa|sava|paune|pone)\s+)?(\d+(?:\.\d+)?)\s*(sau|hundred|hazar|hazaar|hazzar|hajar|thousand|lakh|lac|lakhs|lacs|crore|karor|karod)(?![\p{L}])""")) { m ->
            val part = when (m.groupValues[1]) { "" -> 0.0; "sawa", "sava" -> 0.25; "paune", "pone" -> -0.25; else -> 0.5 }
            val times = when (m.groupValues[3]) { "sau", "hundred" -> 100.0; "lakh", "lac", "lakhs", "lacs" -> 100_000.0; "crore", "karor", "karod" -> 10_000_000.0; else -> 1000.0 }
            plain((m.groupValues[2].toDouble() + part) * times)
        }
        t = runCatching { NumberWords.convert(t) }.getOrDefault(t)
        // A lone number word before "ka" or a verb ("pachas diye", "bees ki chai"), which the number reader leaves.
        return t.replace(Regex("""(?<![\p{L}\d])(""" + numberWords.keys.joinToString("|") + """)\s+(?=(?:ka|ki|ke|rupay|rupaye|rs|riyal|diye|diya|di|mile|mila|mili|liye|liya|li|lagaye|lage|laga)(?![\p{L}]))""")) {
            numberWords.getValue(it.groupValues[1]) + " "
        }
    }

    private val TOKEN = Regex("""\d+(?:\.\d+)?|[\p{L}\p{M}']+|[,;!?\n&+@/]|(?<!\d)\.|\.(?!\d)""")

    private fun tokenOf(x: String) = when (x) {
        "&", "+" -> "aur"
        "@" -> "at"
        "/" -> "per"
        "\n", ";", "!", "." -> ","
        else -> x.trim('\'')
    }

    private fun num(x: String): Double? = if (x.isNotEmpty() && x[0].isDigit()) x.toDoubleOrNull() else null

    /** Clauses: cut at commas, at "aur" once a number was said, and after a finished verb when another number follows. */
    private fun split(t: List<String>): List<List<String>> {
        val out = mutableListOf<List<String>>()
        var cur = mutableListOf<String>()
        var counted = false
        fun push() { if (cur.isNotEmpty()) out += cur; cur = mutableListOf(); counted = false }
        for (i in t.indices) {
            val x = t[i]
            when {
                x == "," -> if (counted) push()
                x in joiners && counted -> push()
                counted && t.getOrNull(i - 1) in endVerbs && x !in endVerbs && x !in stop && numberAhead(t, i) && !ratePhrase(t, i) -> { push(); cur += x }
                else -> cur += x
            }
            // "3 din", "5 baje": a time, not an amount.
            if (num(x) != null && t.getOrNull(i + 1) !in timeUnits) counted = true
        }
        push()
        return out
    }

    private fun numberAhead(t: List<String>, i: Int): Boolean {
        for (j in i until t.size) { if (t[j] == "," || t[j] in joiners) return false; if (num(t[j]) != null) return true }
        return false
    }

    private fun ratePhrase(t: List<String>, i: Int): Boolean {
        val skip = if (t.getOrNull(i + 1) in currencyWords) 1 else 0
        val a = t.getOrNull(i + 1 + skip)
        val b = t.getOrNull(i + 2 + skip)
        return t[i] in perWords || t[i] in setOf("rate", "at") || (num(t[i]) != null && (a in perWords || a == "rate" || a in eachWords ||
            a in setOf("wala", "wali", "wale") || (a in priceWords && (b in rateWords || b in unitWords || b in setOf("ek", "aik")))))
    }

    /** Messages that are not new entries: questions, changes, deletes, "nahi 400 tha". */
    private fun blocked(w: List<String>): Boolean {
        if ("?" in w || w.any { it in askWords || it in editWords || it in noWords }) return true
        // "hisab" is a question unless it is a rate: "1100 ke hisab se".
        if (w.indices.any { w[it] in rateWords && w[it] != "rate" && w.getOrNull(it + 1) !in fromWords }) return true
        val command = w.indices.any { i -> (w[i] in setOf("kar", "kr") && w.getOrNull(i + 1) in setOf("do", "dein", "den", "dena", "dijiye", "dain")) || w[i] in commandWords }
        val entry = w.any { it.startsWith("entr") }
        val save = w.any { it in saveWords }
        if (command) return !(save || entry)
        if (entry) return !save
        // "ye 60 tha": a correction of what was saved.
        if (w.firstOrNull() in setOf("ye", "yeh", "yah", "is", "isko", "ise", "wo", "woh") && w.any { it in setOf("tha", "thi", "the", "thay") }) return true
        return false
    }

    // ---------- answers to the app's own questions ----------

    private val COUNT = Regex("""How many did (.+) make, and of what\?""")
    private val RATE = Regex("""What rate for (.+)\?""")

    /** The message to read when this one answers the app's last question: the earlier message with the person picked, the count or the rate. */
    private fun followUp(s: LocalAgent.Said): String? {
        LocalAgent.picked(s)?.let { return it }
        val asked = s.lastShown.trim()
        if (!asked.endsWith("?")) return null
        val before = s.recent.lines().lastOrNull { it.startsWith("Person: ") }?.removePrefix("Person: ")?.trim()
        if (s.lower.isBlank()) return null
        // An answer is short, names no one and says no money word; anything else is a new message.
        val answer = s.counted.any { it.isDigit() } && s.words.size <= 4 && s.words.none { w -> w in giveWords || w in getWords || w in spendWords } &&
            s.host.names().none { n -> n.lowercase(Locale.ROOT).split(' ').any { it.length >= 3 && it in s.words } }
        COUNT.matchEntire(asked)?.let { m ->
            if (!answer) return null
            val name = m.groupValues[1]
            val usual = s.host.entries().filter { it.type == Type.PURCHASE && it.person.equals(name, true) && it.item.isNotBlank() }.maxByOrNull { it.date }?.item
            return "$name ne " + s.counted + if (usual != null && s.words.all { num(it) != null }) " $usual" else ""
        }
        RATE.matchEntire(asked)?.let {
            if (before == null || !answer) return null
            val bare = s.words.size == 1 && num(s.words[0]) != null
            return before + " " + s.counted + if (bare) " ke hisab se" else ""
        }
        return null
    }

    // ---------- small helpers ----------

    private const val DAY = 86_400_000L

    private fun day(ms: Long) = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(ms))

    private fun plain(n: Double) = if (n == Math.floor(n)) n.toLong().toString() else n.toString()

    private fun listed(names: List<String>) = if (names.size < 2) names.joinToString() else names.dropLast(1).joinToString(", ") + " or " + names.last()

    /** Days back said in a clause: aaj 0, kal 1, parso 2. */
    private fun relativeDay(w: List<String>): Int? = when {
        w.any { it in setOf("parso", "parson", "parsu", "parsun", "parsoon", "prso", "prson") } -> 2
        w.windowed(3).any { it == listOf("day", "before", "yesterday") } -> 2
        w.any { it in setOf("kal", "kl", "kall", "yesterday", "cal") } -> 1
        w.any { it in setOf("aaj", "aj", "today", "abhi") } -> 0
        else -> null
    }

    // ---------- words ----------

    private val toWords = setOf("ko", "ku", "kou", "koo")
    private val byWords = setOf("ne", "nay", "ny", "nei", "n")
    private val fromWords = setOf("se", "say", "sy", "sey", "sai", "sae")
    private val englishTo = setOf("gave", "give", "paid", "pay", "lent", "lend", "sent", "send", "transferred", "transfer", "giving", "paying")
    private val englishBy = setOf("gave", "paid", "returned", "sent", "lent", "gives", "pays", "made", "did", "brought", "bought", "sold", "supplied", "delivered",
        "took", "borrowed", "received", "got", "owes", "has", "produced")
    private val pronouns = mapOf(
        "usne" to 'B', "usnay" to 'B', "usny" to 'B', "isne" to 'B', "isnay" to 'B', "unhone" to 'B', "unhon" to 'B', "unhonne" to 'B', "inhone" to 'B',
        "usko" to 'T', "isko" to 'T', "unko" to 'T', "inko" to 'T', "use" to 'T', "usay" to 'T', "ise" to 'T', "isay" to 'T', "unhe" to 'T', "unhen" to 'T',
        "him" to 'T', "them" to 'T', "usse" to 'F', "isse" to 'F', "unse" to 'F', "inse" to 'F', "ussay" to 'F',
        "uske" to '-', "uska" to '-', "uski" to '-', "iske" to '-', "iska" to '-', "iski" to '-', "unke" to '-', "unka" to '-', "unki" to '-', "his" to '-'
    )
    private val honorifics = setOf("bhai", "bhae", "sahab", "sahib", "saab", "sb", "ji", "jee", "bhaijan", "bhaijaan", "seth", "mian", "miyan")
    private val giveWords = setOf("diye", "diya", "di", "dia", "diy", "diay", "dye", "de", "dey", "dee", "dii", "diyay", "diae", "diyey", "diyee", "dena", "dedo",
        "dediye", "dediya", "dedi", "deye", "dein", "gave", "give", "given", "gives", "paid", "pay", "lent", "lend", "send", "sent", "bheje", "bheja", "bheji",
        "bhejay", "bhejy", "bhejo", "bhej", "transfer", "transferred", "diyea", "diey", "dya", "dyi", "diyye", "diyain")
    private val getWords = setOf("mile", "mila", "mili", "mil", "mily", "milay", "miley", "mle", "mla", "mli", "liye", "liya", "li", "le", "lia", "lie", "lea",
        "liay", "liyay", "aaye", "aaya", "aayi", "aye", "aya", "ayi", "aai", "aae", "received", "recieved", "receive", "recieve", "got", "get", "took", "take",
        "taken", "wasool", "wasul", "vasool", "wusool", "collected", "collect", "earned", "borrowed", "borrow")
    private val inWords = setOf("mile", "mila", "mili", "mil", "mily", "milay", "miley", "aaye", "aaya", "aayi", "aye", "aya", "ayi", "aai", "received",
        "recieved", "got", "earned", "wasool", "wasul", "vasool", "kamaye", "kamaya")
    private val returnWords = setOf("wapis", "wapas", "waps", "wapsi", "vapis", "vapas", "vaapis", "waapis", "lautaye", "lotaye", "lautaya", "lotaya", "lautai",
        "lotai", "lota", "lotaa", "lauta", "return", "returned", "back", "repaid", "repay")
    private val loanWords = setOf("udhaar", "udhar", "udaar", "udhari", "udhaari", "udar", "qarz", "qarza", "karz", "karza", "qarze", "karze", "loan", "loans",
        "borrow", "borrowed", "lend", "lent")
    private val sellWords = setOf("becha", "bechi", "beche", "bechay", "bechy", "bech", "sold", "sell", "sells", "farokht")
    private val makeWords = setOf("dali", "daali", "dala", "dale", "daale", "dalwai", "banai", "banaye", "banaya", "banayi", "bani", "bane", "bnai", "kiya", "kiye",
        "kia", "kari", "kare", "kri", "made", "make", "did", "done", "produced", "laya", "layi", "laye", "lai", "laaya", "supplied", "supply", "delivered",
        "brought", "tayyar", "tayar", "nikali", "nikale", "bhari", "bhare")
    private val workWords = setOf("kaam", "work", "worked", "mazdoori", "mazduri", "molding", "moulding")
    private val spendWords = setOf("kharch", "kharcha", "kharche", "kharchay", "spent", "spend", "lagaye", "lagay", "lage", "laga", "lagi", "lagai", "lgaye",
        "dalwaya", "dalwaye", "bhara", "bharwaya", "bharwaye", "khaya", "khaye", "khai", "khaaya", "piya", "pi", "pee", "bought", "buy", "purchased", "kharida",
        "kharide", "kharidi", "khareeda", "khareedi", "khareede")
    private val incomeWords = setOf("salary", "tankhwah", "tankhwa", "tankha", "tankhuwa", "tankhah", "tanqa", "income", "aamdani", "amdani", "kamai", "kamaai",
        "kamaye", "kamaya", "commission", "comission", "bonus", "overtime", "profit", "munafa", "nafa", "interest", "sood", "tip", "tips", "dihari", "dehari",
        "wages", "earning", "earnings")
    private val moneyNouns = setOf("advance", "edvance", "adv", "paise", "pese", "paisay", "pesay", "paisa", "pesa", "rupay", "rupaye", "rupees", "rupee", "rs",
        "riyal", "riyals", "rial", "sar", "pkr", "dollar", "dollars", "dirham", "rupy", "rupe", "rupaiye", "rupaya", "rupya", "rupiya", "rakam", "raqam", "amount", "cash", "naqad", "naqd", "payment", "total", "kul",
        "money", "price", "keemat", "qeemat", "jama", "jamaa", "baqi", "baki", "baaqi", "bakaya", "balance", "remaining", "due")
    private val unitWords = setOf("pcs", "pc", "piece", "pieces", "nag", "adad", "bori", "boriyan", "bag", "bags", "kg", "kilo", "kilos", "dozen", "darjan",
        "maund", "mann", "ton", "tons", "trolley", "trolly", "truck", "feet", "ft", "foot", "meter", "metre", "gaz", "litre", "liter", "ltr", "packet", "packets",
        "carton", "cartons", "box", "boxes", "than", "thaan", "jora", "joray", "suit", "suits")
    private val priceWords = setOf("ka", "ki", "ke", "kay", "ky", "kaa")
    private val rateWords = setOf("hisab", "hisaab", "hesab", "hisb", "rate")
    private val perWords = setOf("per", "fi", "har", "/")
    private val eachWords = setOf("each", "apiece")
    private val thousandWords = setOf("hazar", "hazaar", "thousand", "1000")
    private val thingAfterPrice = setOf("ek", "aik")
    private val bookWords = mapOf(
        "staff" to Sides.STAFF, "worker" to Sides.STAFF, "workers" to Sides.STAFF, "mazdoor" to Sides.STAFF, "mazdur" to Sides.STAFF, "karigar" to Sides.STAFF,
        "kaarigar" to Sides.STAFF, "labour" to Sides.STAFF, "labor" to Sides.STAFF, "employee" to Sides.STAFF, "mulazim" to Sides.STAFF, "naukar" to Sides.STAFF,
        "customer" to Sides.CUSTOMERS, "customers" to Sides.CUSTOMERS, "grahak" to Sides.CUSTOMERS, "gahak" to Sides.CUSTOMERS, "client" to Sides.CUSTOMERS,
        "buyer" to Sides.CUSTOMERS, "supplier" to Sides.SUPPLIERS, "suppliers" to Sides.SUPPLIERS, "vendor" to Sides.SUPPLIERS, "dealer" to Sides.SUPPLIERS
    )
    private val newWords = setOf("naya", "nayi", "naye", "new", "nya", "nye")
    private val familyWords = setOf("ammi", "ami", "amma", "abbu", "abu", "abba", "walid", "walida", "biwi", "bivi", "wife", "husband", "shohar", "beta", "beti",
        "bacha", "bache", "bachay", "bachon", "bachoon", "bacho", "bachchon", "kids", "children", "family", "behen", "bhen", "behan", "sister", "brother", "mother",
        "father", "parents", "gharwalon", "gharwale", "nana", "nani", "dada", "dadi", "chacha", "chachi", "mamu", "khala", "phuphi")
    private val places = setOf("ghar", "home", "dukan", "dukaan", "shop", "office", "bank", "market", "bazar", "bazaar", "masjid", "pump", "workshop", "godown",
        "jazzcash", "easypaisa", "online", "card", "atm", "raast", "wallet", "account", "khata", "khate")
    private val brickWords = setOf("eent", "eint", "int", "ent", "brick", "bricks", "ainth", "eenth")
    /** English words for the things the owner names in Urdu, to find the name in use ("raw bricks": Kachi eent). */
    private val synonyms = mapOf("raw" to "kachi", "unbaked" to "kachi", "green" to "kachi", "baked" to "pakki", "fired" to "pakki", "burnt" to "pakki",
        "brick" to "eent", "bricks" to "eent", "loading" to "bharai", "loaded" to "bharai", "unloading" to "nikasi", "unloaded" to "nikasi",
        "moulding" to "thapai", "molding" to "thapai")
    private val dayWords = setOf("aaj", "aj", "today", "kal", "kl", "kall", "yesterday", "parso", "parson", "parsu", "parsun", "parsoon", "prso", "prson", "abhi")
    private val joiners = setOf("aur", "or", "and", "phir", "fir", "then", "also", "plus", "orr")
    private val endVerbs = setOf("diye", "diya", "di", "dia", "diy", "de", "dye", "mile", "mila", "mili", "liye", "liya", "li", "le", "lia", "dala", "dali", "daali",
        "dale", "dalwaya", "bhara", "bhari", "khaya", "khai", "khaye", "piya", "pi", "kharida", "kharide", "kharidi", "bheje", "bheja", "bheji", "kiya", "kiye", "ki",
        "kia", "aaye", "aaya", "aayi", "aye", "aya", "gave", "paid", "spent", "bought", "got", "received", "sent", "took", "lent", "borrowed", "returned", "sold",
        "becha", "bechi", "banai", "banaye", "lagaye", "laga", "lage", "hai", "hain", "tha", "thi", "the")
    private val askWords = setOf("kitna", "kitne", "kitni", "kitnay", "kitny", "kya", "kyun", "kyon", "kab", "kahan", "kaun", "kon", "kis", "kisne",
        "kisko", "kese", "kaise", "how", "what", "when", "where", "who", "whom", "which", "why", "show", "dikhao", "dikha", "dikhana", "batao", "bata", "btao",
        "bataen", "bataiye", "list", "report", "summary", "pdf", "statement", "dekho", "dekh", "search", "find")
    private val editWords = setOf("delete", "del", "dlt", "hata", "hatao", "hatado", "hatain", "mita", "mitao", "remove", "cancel", "undo", "restore", "badal",
        "badlo", "badla", "badli", "change", "changed", "update", "edit", "galat", "ghalat", "glt", "wrong", "jagah", "jaga", "instead", "rename", "naam",
        "merge", "sahi", "correct", "fix", "jo", "trash")
    private val noWords = setOf("nahi", "nahin", "nhi", "nahee", "not", "didnt", "didn't", "mat", "agar", "if", "owe", "owes", "owed", "order", "dena", "dene",
        "lena", "lene", "dunga", "doonga", "dungi", "denge", "lunga", "loonga", "lenge", "will", "chahiye", "karunga", "karenge", "dega", "degi", "dengi",
        "lega", "legi", "aayega", "ayega", "aaega", "number", "nmbr")
    private val numberWords = mapOf("teen" to "3", "char" to "4", "chaar" to "4", "panch" to "5", "paanch" to "5", "chhe" to "6", "saat" to "7", "aath" to "8",
        "das" to "10", "dus" to "10", "gyarah" to "11", "barah" to "12", "terah" to "13", "chaudah" to "14", "pandrah" to "15", "solah" to "16", "satrah" to "17",
        "atharah" to "18", "unees" to "19", "bees" to "20", "pachees" to "25", "tees" to "30", "chalees" to "40", "chalis" to "40", "pachas" to "50",
        "pachaas" to "50", "sattar" to "70", "assi" to "80")
    private val timeUnits = setOf("baje", "bje", "bajay", "am", "pm", "din", "days", "day", "ghante", "ghanta", "hours", "hour", "minute", "minutes", "mint",
        "mahine", "mahina", "month", "months", "saal", "sal", "year", "years", "hafte", "hafta", "week", "weeks", "tareekh", "date")
    private val currencyWords = setOf("rupay", "rupaye", "rupees", "rupee", "rupy", "rupe", "rupaiye", "rupaya", "rupya", "rupiya", "rs", "riyal", "riyals", "rial", "sar", "pkr", "dollar", "dollars", "dirham")
    private val saleWords = setOf("sale", "sales", "bikri", "bikree")
    private val cashWords = setOf("cash", "naqad", "naqd", "nakad")
    private val commandWords = setOf("karo", "kro", "kardo", "krdo", "kariye", "kijiye", "kijie", "karein", "karen", "daldo", "banao", "bnao")
    private val saveWords = setOf("likh", "likho", "likhdo", "likhlo", "save", "add", "darj", "note", "record")
    private val stop = setOf("ko", "ku", "kou", "koo", "ne", "nay", "ny", "nei", "se", "say", "sy", "sey", "sai", "sae", "ka", "ki", "ke", "kay", "ky", "kaa",
        "mein", "me", "mai", "main", "mujhe", "mjhe", "maine", "mene", "mainay", "hum", "ham", "humne", "hamne", "apne", "apni", "apna", "mera", "meri", "mere",
        "hamara", "hamari", "hamare", "tum", "tumne", "aap", "aapne", "ap", "par", "pe", "pr", "py", "tak", "liye", "lie", "lye", "wala", "wali", "wale", "walay",
        "walon", "waly", "walo", "aur", "or", "orr", "and", "ya", "bhi", "bi", "hi", "to", "tou", "from", "for", "of", "on", "at", "the", "a", "an", "my", "our",
        "your", "i", "we", "it", "its", "this", "that", "these", "those", "ye", "yeh", "yah", "wo", "woh", "vo", "voh", "us", "is", "un", "in", "iss", "uss",
        "ab", "phir", "fir", "then", "also", "plus", "jab", "tab", "sath", "saath", "sirf", "only", "just", "bas", "hai", "hain", "he", "hy", "ha", "hn", "tha",
        "thi", "thay", "thy", "thee", "hua", "hui", "hue", "huwa", "huwi", "gaya", "gayi", "gaye", "gya", "gyi", "gye", "gai", "raha", "rahi", "rahe", "kar",
        "kr", "karke", "krke", "karo", "kro", "do", "lo", "hoga", "hogi", "ho", "was", "were", "are", "am", "be", "been", "has", "have", "had", "with", "by",
        "ek", "aik", "one", "koi", "kuch", "sab", "sabko", "sare", "sari", "saray", "sara", "bohat", "bahut", "zyada", "thora", "thoda", "please", "plz",
        "pls", "yar", "yaar", "ok", "okay", "acha", "theek", "haan", "han", "taraf", "likh", "likho", "likhdo", "likhlo", "save", "add", "darj", "note", "record",
        "entry", "entries", "per", "fi", "har", "rate", "hisab", "hisaab", "hesab", "each", "naya", "nayi", "naye", "new", "nya", "nye", "dain", "dein", "den",
        "jee", "g", "sir", "madam", "karaye", "karwaye", "karwaya", "karaya", "karwai", "karwayi", "kraye", "raat", "subah", "shaam",
        "sham", "dopahar", "morning", "evening", "night", "tonight", "assalam", "salam", "aoa", "alaikum", "walaikum", "hello", "hey", "mahine", "pichle", "pichhle", "agle", "last", "next", "month",
        "dono", "teeno", "both", "everyone", "sabhi", "upar", "andar", "baad", "pehle", "pehlay", "ago", "before", "day", "din", "date", "tareekh", "week", "hafta", "mahina")
    /** Things the app's rules do not know, by their usual account. */
    private val extra: Map<String, Pair<Cat, String>> = buildMap {
        listOf("generator", "fridge", "refrigerator", "freezer", "inverter", "ups", "solar", "motorcycle", "motorbike", "furniture", "sofa", "almari", "cupboard",
            "tv", "television", "computer", "machine", "plot", "zameen").forEach { put(it, Cat.ASSETS to it.replaceFirstChar { c -> c.uppercase() }) }
        listOf("rickshaw", "riksha", "rikshaw", "qingqi", "chingchi", "fare").forEach { put(it, Cat.TRANSPORT to "Rickshaw") }
        listOf("plumber", "electrician", "mistri", "carpenter", "painter", "mazdoori").forEach { put(it, Cat.OTHER to "Repairs") }
        listOf("darzi", "tailor", "silai").forEach { put(it, Cat.SHOPPING to "Clothes") }
        listOf("samosa", "samosay", "pakora", "pakoray", "sandwich", "halwa", "jalebi", "lassi", "nimko", "paratha").forEach { put(it, Cat.KHANA to "Snacks") }
        listOf("easyload", "load").forEach { put(it, Cat.MOBILE to "Mobile recharge") }
        listOf("naai", "nai", "hajjam", "hajam").forEach { put(it, Cat.PERSONAL to "Haircut") }
        listOf("chini", "cheeni", "sugar", "shakar").forEach { put(it, Cat.KHANA to "Groceries") }
    }
    /** Everyday things, so a short form typed without vowels ("ptrl") is read. */
    private val shortThings = listOf("petrol", "diesel", "chai", "khana", "nashta", "bijli", "kiraya", "dawai", "doodh", "sabzi", "grocery", "recharge",
        "mobile", "internet", "taxi", "rickshaw", "school", "fees", "gas", "pani", "roti", "gosht", "chicken", "fruit", "kapre", "joote", "bill", "salary",
        "tankhwah", "commission", "medicine", "doctor", "lunch", "dinner", "breakfast", "rent", "electricity", "water", "parking", "biryani", "burger", "pizza")
}
