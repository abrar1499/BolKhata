package com.bolkhata.app

import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Changes to what is saved, read by rule: a correction of what was just saved ("nahi 400 tha", "ye kal ka tha", "ye zubair
 * ko diye thay", "ye naya tariq hai"), a change to an older entry ("kal wali chai 5 ki jagah 8 kar do"), delete (a big one
 * is asked about first and done after a yes), restore, a person's new name, two names of one work put together, and
 * accounts. All of it with the AI's own tools; new entries and questions are left to the other readers.
 */
object LocalEdit : LocalAgent.Reader {

    override fun read(s: LocalAgent.Said): LocalAgent.Reply? {
        val m = Msg(s, s.raw, s.roman)
        if (m.w.isEmpty()) return null
        return answer(m) ?: restore(m) ?: account(m) ?: rename(m) ?: merge(m) ?: delete(m) ?: correct(m) ?: change(m)
    }

    // ---------- the message ----------

    /** One message ready to read: Roman words with numbers in digits, and the books it can refer to. */
    private class Msg(val s: LocalAgent.Said, raw: String, roman: String, justNow: List<Entry>? = null,
        /** The entries already picked, when the answer to "Which one?" named one. */
        val pinned: List<Entry>? = null) {
        val w: List<String> = WORD.findAll(prepare(raw, roman)).map { it.value }.toList()
        val all: List<Entry> = s.host.entries()
        /** What the last message saved ([justNow] when the app asked about it in between). */
        val just: List<Entry> = justNow ?: all.filter { it.id in s.justSaved }
        val names: List<String> = s.host.names()
        /** Words of the work and things in use ("kachi", "eent"), never read as a name. */
        val thingWords: Set<String> = all.flatMap { it.item.lowercase(Locale.ROOT).split(' ') }.filter { it.length >= 3 }.toSet()
        /** A question, left to [LocalAsk]. */
        val asking = "?" in w || w.any { it in askWords } || (w.size > 2 && w.last() in setOf("na", "naa", "nah") && w[w.size - 2] in isWords + pastWords)
        val people: List<Who> by lazy { peopleIn(this) }
        fun has(set: Set<String>) = w.any { it in set }
        fun num(i: Int): Double? = w.getOrNull(i)?.let { if (it[0].isDigit()) it.toDoubleOrNull() else null }
    }

    /** A saved person said: the words they take, their saved name, and the names it could be when several fit. */
    private class Who(val from: Int, val to: Int, val name: String, val choices: List<String> = emptyList())

    // ---------- answers to the app's own question ----------

    /**
     * The answer to what this reader asked last: "haan kar do" after "Move them all to Trash?" does the delete ("nahi"
     * leaves it); "salim raza" after "Which Salim do you mean…?" or "hamid wala" after "Which one: …?" reads the earlier
     * message again with it.
     */
    private fun answer(m: Msg): LocalAgent.Reply? {
        LocalAgent.picked(m.s)?.let { text ->
            val n = again(m, text, recentlySaved(m))
            return rename(n) ?: account(n) ?: merge(n) ?: delete(n) ?: correct(n) ?: change(n)
        }
        val shown = m.s.lastShown.trim()
        val deleting = PENDING.containsMatchIn(shown)
        val one = shown.startsWith("Which one: ")
        if (!deleting && !one) return null
        val lines = m.s.recent.lines()
        val asked = lines.indexOfLast { it.startsWith("App: ") && it.removePrefix("App: ").trim().let { a -> a.isNotBlank() && shown.startsWith(a.take(60)) } }
        val request = lines.take(if (asked >= 0) asked else lines.size).lastOrNull { it.startsWith("Person: ") }?.removePrefix("Person: ")?.trim() ?: return null
        if (deleting && no(m)) return LocalAgent.Reply("Okay, nothing was deleted.")
        if (deleting && yes(m)) return delete(again(m, request), sure = true)
        if (m.w.size > 6 || m.asking) return null
        if (deleting) return delete(again(m, request + " " + m.s.raw))
        // "Which one: Hamid Akram 500 or Zubair Latif 300?": the one named, then the earlier message on it alone.
        val options = shown.removePrefix("Which one: ").removeSuffix("?").split(Regex(""",\s+|\s+or\s+""")).map { it.trim() }
        val pick = pickOption(m, options) ?: return null
        val recent = recentlySaved(m)
        val chosen = recent.filter { said(it) == pick || option(it, m.s.now) == pick }.ifEmpty { m.all.filter { said(it) == pick || option(it, m.s.now) == pick } }
        if (chosen.isEmpty()) return null
        val n = Msg(m.s, request, runCatching { Translit.toRoman(request) }.getOrDefault(request), chosen, chosen)
        return correct(n) ?: change(n)
    }

    /** The option the answer names: first / second, its amount, or a word of its name or day. */
    private fun pickOption(m: Msg, options: List<String>): String? {
        if (m.w.size <= 3) m.w.firstNotNullOfOrNull { ordinals[it] }?.let { return if (it < 0) options.lastOrNull() else options.getOrNull(it) }
        val nums = m.w.mapNotNull { x -> x.toDoubleOrNull()?.takeIf { x[0].isDigit() } }
        if (nums.isNotEmpty()) options.filter { o -> Regex("""\d[\d,]*(?:\.\d+)?""").findAll(o).any { it.value.replace(",", "").toDouble() in nums } }.singleOrNull()?.let { return it }
        val (from, _) = period(m)
        val day = from?.let { dayText(it, m.s.now) }
        val words = m.w.filter { it.length >= 3 && it !in notNames }
        return options.filter { o ->
            val parts = o.lowercase(Locale.ROOT).split(Regex("""[^\p{L}\p{N}]+"""))
            words.any { w -> parts.any { it == w || Normalizer.root(it) == Normalizer.root(w) || (it.length >= 3 && Understand.closeName(w, it)) } } || (day != null && o.endsWith("($day)"))
        }.singleOrNull()
    }

    /** An older entry as a "Which one?" option: "Petrol 60 (12 Nov)". */
    private fun option(e: Entry, now: Long) = said(e) + " (" + dayText(dayOf(e.date), now) + ")"

    private val ordinals = mapOf("pehla" to 0, "pehle" to 0, "pehli" to 0, "first" to 0, "1" to 0, "dusra" to 1, "doosra" to 1, "dusre" to 1, "dusri" to 1,
        "doosri" to 1, "second" to 1, "2" to 1, "teesra" to 2, "tisra" to 2, "teesre" to 2, "teesri" to 2, "third" to 2, "3" to 2, "last" to -1)

    /** [text] read as this message, with what was saved before the app's question. */
    private fun again(m: Msg, text: String, just: List<Entry>? = null) = Msg(m.s, text, runCatching { Translit.toRoman(text) }.getOrDefault(text), just)

    /** Entries saved in the last half hour: what a correction meant when the app asked "which" in between. */
    private fun recentlySaved(m: Msg) = m.all.filter { it.createdAt >= m.s.now - 30 * 60_000L }

    private fun yes(m: Msg) = m.w.any { it in yesWords || it in doWords } && m.w.none { it in noWords || it == "mat" } &&
        m.w.all { it in yesWords || it in doWords || it in allWords || it in entryWords || isDelete(it) || it in politeWords || it in pronouns }

    private fun no(m: Msg) = m.w.any { it in noWords || it in stopWords } && m.w.all { it in noWords || it in stopWords || it in politeWords || it in doWords || it in yesWords }

    private val stopWords = setOf("mat", "rehne", "rehnay", "cancel", "ruko", "ruk", "stop", "chhodo", "chodo", "leave", "wait")

    // ---------- restore ----------

    /** "wapas le aao", "restore", "undo" after a delete: the entries just deleted come back from Trash. */
    private fun restore(m: Msg): LocalAgent.Reply? {
        val w = m.w
        val deletedLast = lastDeleted(m) != null
        val back = w.indices.any { i -> w[i] in backWords && (w.getOrNull(i + 1) in bringWords || (w.getOrNull(i + 1) in doWords && (deletedLast || w.any(::isDelete)))) } ||
            m.has(restoreWords) || ("back" in w && w.any { it in setOf("bring", "get", "put") }) ||
            ("trash" in w && w.any { it in bringWords || it in setOf("nikalo", "nikal", "nikaldo") })
        val undo = w.any { Normalizer.root(it) == "undo" } && (deletedLast || w.any(::isDelete)) && w.size <= 4
        if (!(back || undo) || m.asking) return null
        // "paise wapas lao" is money to get back, not Trash.
        val trash = deletedLast || w.any { isDelete(it) || it in entryWords || it in pronouns || it in restoreWords || it == "trash" }
        val money = w.indices.any { i -> m.num(i) != null && w.getOrNull(i + 1) !in entryWords } || m.has(moneyWords)
        if (!trash || money) return null
        val who = m.people.filter { it.choices.isEmpty() }.map { it.name }
        if (who.isNotEmpty() && !m.has(entryWords)) return null
        val bin = m.s.host.trashed()
        val input = JSONObject()
        if (who.isNotEmpty()) input.put("ids", JSONArray(bin.filter { e -> who.any { it.equals(e.person.trim(), true) } }.map { it.id }))
        else {
            val n = w.indices.firstNotNullOfOrNull { i -> m.num(i)?.takeIf { w.getOrNull(i + 1) in entryWords } }?.toInt()
            input.put("last", n ?: lastDeleted(m) ?: 1)
        }
        val out = m.s.use("restore_entries", input)
        val back2 = out.optJSONArray("entries")
        return LocalAgent.Reply(when {
            out.has("error") || out.optInt("restored") == 0 -> "Nothing like that is in Trash."
            out.optInt("restored") == 1 && back2 != null -> "Brought back " + said(back2.getJSONObject(0)) + "."
            else -> "Brought back " + out.optInt("restored") + " entries."
        })
    }

    /** How many entries the app's last answer moved to Trash, when it did. */
    private fun lastDeleted(m: Msg): Int? {
        val line = m.s.recent.lines().lastOrNull { it.startsWith("App: ") }?.removePrefix("App: ") ?: m.s.lastShown
        if (!line.contains("to Trash") || line.trim().endsWith("?")) return null
        return Regex("""Moved (\d+) entries""").find(line)?.groupValues?.get(1)?.toInt() ?: 1
    }

    // ---------- accounts ----------

    /** "akbar ko staff mein daal do", "zubair suppliers mein", "diesel ka naya expense account banao", "drivers ka khata banao aur us mein akbar ko daal do". */
    private fun account(m: Msg): LocalAgent.Reply? {
        val w = m.w
        if (m.asking || w.any { it[0].isDigit() || it in shareWords }) return null
        val khata = w.indexOfFirst { it in khataWords }
        val making = khata >= 0 && (w.any { it in makeWords } || w.any { it in newWords })
        if (making) return makeAccount(m, khata)
        val (book, at) = bookSaid(m) ?: return null
        // "akbar ko staff mein daal do aur rashid ko suppliers mein": each part on its own.
        val cut = w.indices.firstOrNull { i -> i > at.last && (w[i] == "aur" || w[i] == "and") && bookSaid(again(m, w.drop(i + 1).joinToString(" "))) != null }
        if (cut != null) {
            val first = account(again(m, w.take(cut).joinToString(" "))) ?: return null
            val second = account(again(m, w.drop(cut + 1).joinToString(" ") + if (w.drop(cut + 1).none { it in putWords || it in doWords }) " daal do" else ""))
            return LocalAgent.Reply(listOfNotNull(first.text, second?.text).joinToString(" "))
        }
        val into = w.getOrNull(at.last + 1) in inWords + putWords + makeWords || w.getOrNull(at.first - 1) in setOf("in", "into", "to", "as", "a", "an")
        val out = w.getOrNull(at.last + 1) in setOf("se", "say", "sy", "from") && w.any { it in outWords || isDelete(it) }
        val put = w.any { it in putWords || it in doWords }
        val state = w.any { it in isWords } && !w.any { isDelete(it) }
        if (!(into && (put || state)) && !out && !(state && w.size <= 5)) return null
        val people = m.people.filter { it.to < at.first || it.from > at.last }
        val names = newcomer(m)?.let { listOf(it) } ?: run {
            people.firstOrNull { it.choices.isNotEmpty() }?.let { return which(it) }
            (people.map { it.name } + newNames(m, at)).distinctBy { it.lowercase() }
        }
        if (names.isEmpty()) return null
        val target = if (out) Sides.PEOPLE else book
        val r = m.s.use("put_in_account", JSONObject().put("account", target).put("people", JSONArray(names)))
        if (r.has("error")) return LocalAgent.Reply("That did not work: " + r.optString("error") + ".")
        return LocalAgent.Reply(listed(names) + (if (names.size > 1) " are" else " is") + " in " + r.optString("account", target) + " now.")
    }

    /** The people account said ("staff", "suppliers", an own one like "Drivers") and where. */
    private fun bookSaid(m: Msg): Pair<String, IntRange>? {
        val own = m.s.host.setup().books.filter { it.people }.map { it.name }
        for (i in m.w.indices) {
            usualBooks.entries.firstOrNull { m.w[i] in it.value }?.let { return it.key to i..i }
            own.firstOrNull { b -> val p = b.lowercase(Locale.ROOT).split(' '); m.w.subList(i, minOf(m.w.size, i + p.size)) == p }
                ?.let { return it to (i until i + it.split(' ').size) }
        }
        return null
    }

    /** Names said that are not saved yet ("akbar ko staff mein"): words like a name before ko or after naya. */
    private fun newNames(m: Msg, skip: IntRange): List<String> {
        val taken = m.people.flatMap { (it.from..it.to).toList() }.toSet()
        val out = mutableListOf<String>()
        var cur = mutableListOf<String>()
        for (i in m.w.indices) {
            val x = m.w[i]
            if (i !in skip && i !in taken && nameLike(x, m)) cur += x
            else { if (cur.isNotEmpty()) out += named(cur); cur = mutableListOf() }
        }
        if (cur.isNotEmpty()) out += named(cur)
        return out
    }

    /** A new account: things (expense or income) by add_account, people by put_in_account with the people said. */
    private fun makeAccount(m: Msg, khata: Int): LocalAgent.Reply? {
        val w = m.w
        // "aur us mein akbar ko daal do": the people part, after the account's name.
        val join = w.indices.firstOrNull { i -> i > khata && w[i] in setOf("aur", "or", "and", "jis", "with") }
        val head = if (join != null) w.subList(0, join) else w
        val nameWords = head.filter { it !in accountFiller && it !in khataWords && it !in makeWords && it !in newWords && it !in expenseWords && it !in incomeWords && it !in doWords }
        if (nameWords.isEmpty()) return LocalAgent.Reply("What should the new account be called?")
        val name = title(nameWords.joinToString(" "))
        val tail = if (join != null) Msg(m.s, w.subList(join + 1, w.size).joinToString(" "), w.subList(join + 1, w.size).joinToString(" ")) else null
        val people = tail?.let { t -> (t.people.filter { it.choices.isEmpty() }.map { it.name } + newNames(t, IntRange.EMPTY)).distinctBy { it.lowercase() } }.orEmpty()
        val saved = m.people.firstOrNull { it.choices.isEmpty() && nameWords.all { x -> x in it.name.lowercase().split(' ') || Understand.closeName(x, it.name.lowercase().split(' ').first()) } }
        return when {
            w.any { it in incomeWords } -> things(m, "income", name)
            w.any { it in expenseWords } -> things(m, "expense", name)
            people.isNotEmpty() -> {
                val r = m.s.use("put_in_account", JSONObject().put("account", name).put("people", JSONArray(people)))
                if (r.has("error")) LocalAgent.Reply("That did not work: " + r.optString("error") + ".")
                else LocalAgent.Reply("Made the $name account with " + listed(people) + " in it.")
            }
            nameWords.any { it in groupWords || it.endsWith("ers") || it.endsWith("wale") } -> LocalAgent.Reply("Who should go in $name?")
            saved != null -> LocalAgent.Reply("${saved.name} already has an account.")
            nameWords.size <= 2 && Understand.isCommonName(nameWords[0]) -> {
                m.s.use("put_in_account", JSONObject().put("account", Sides.PEOPLE).put("people", JSONArray(listOf(name))))
                LocalAgent.Reply("$name has an account in People now.")
            }
            else -> things(m, "expense", name)
        }
    }

    private fun things(m: Msg, side: String, name: String): LocalAgent.Reply {
        val r = m.s.use("add_account", JSONObject().put("side", side).put("name", name))
        return LocalAgent.Reply(if (r.has("already_there")) "$name is already there." else "Made the $name $side account.")
    }

    // ---------- a new name ----------

    /** "salim raza ka naam salim raza butt kar do", "rename hamid to hamid ali": rename_person, or an item's new name. */
    private fun rename(m: Msg): LocalAgent.Reply? {
        val w = m.w
        if (m.asking || w.any { it[0].isDigit() }) return null
        val naam = w.indexOfFirst { it in nameWords }
        val rn = w.indexOfFirst { it == "rename" }
        val ko = w.indexOf("ko")
        val (x, y) = when {
            naam > 0 && w[naam - 1] in setOf("ka", "ki", "ke", "kaa", "s") -> w.subList(0, naam - 1) to w.subList(naam + 1, w.size)
            rn >= 0 -> {
                val to = w.indexOfFirst { it == "to" || it == "as" }
                if (to <= rn) return null
                w.subList(rn + 1, to) to w.subList(to + 1, w.size)
            }
            naam >= 0 && w.indexOf("to") > naam -> {
                // "change hamid's name to hamid ali", "change name of hamid to hamid ali"
                val to = w.indexOf("to")
                w.subList(0, naam).filter { it != "change" && it != "s" && it != "of" } + w.subList(naam + 1, to).filter { it != "of" } to w.subList(to + 1, w.size)
            }
            // "hamid ko hamid ali kar do": a longer name that keeps a word of the saved one.
            ko > 0 && w.drop(ko + 1).any { it in doWords || it in setOf("likho", "likh", "bana", "banao", "banado") } -> {
                val x = w.subList(0, ko)
                val y = w.subList(ko + 1, w.size).dropLastWhile { it in doWords || it in yesWords || it in setOf("likho", "likh", "bana", "banao", "banado", "please", "plz") }
                val person = m.people.firstOrNull { it.choices.isEmpty() && it.to < ko }?.name ?: return null
                if (y.isEmpty() || y.none { it in x } || y.any { !nameLike(it, m, savedToo = true) } || person.equals(title(y.joinToString(" ")), true)) return null
                x to y
            }
            else -> return null
        }
        val from = x.filter { it !in pronouns && it !in setOf("change", "the", "of", "mere", "meri", "mera") }
        val filler = doWords + setOf("badalke", "ke", "karke", "kr", "se", "ko", "hai", "rakhna", "please", "plz", "the", "now", "do", "de", "dein", "den", "dena", "dijiye", "na")
        val to = y.dropWhile { it in filler }.dropLastWhile { it in filler }
        if (from.isEmpty() || to.isEmpty() || to.any { it[0].isDigit() }) return null
        val fromText = from.joinToString(" ")
        val person = m.names.firstOrNull { it.equals(fromText, true) }
            ?: Msg(m.s, fromText, fromText).people.let { p ->
                p.firstOrNull { it.choices.isNotEmpty() }?.let { return which(it) }
                p.singleOrNull()?.name
            }
        // Words kept from the old name keep its spelling ("hamd ali" from Urdu script: Hamid Ali).
        val kept = person?.lowercase(Locale.ROOT)?.split(' ').orEmpty()
        val newName = title(to.joinToString(" ") { t -> kept.firstOrNull { it != t && Understand.closeName(t, it) } ?: t })
            .let { t -> m.names.firstOrNull { it.equals(t, true) } ?: t }
        if (person != null) {
            if (person.equals(newName, false)) return LocalAgent.Reply("It is already $newName.")
            val r = m.s.use("rename_person", JSONObject().put("from", person).put("to", newName))
            return LocalAgent.Reply(if (r.has("error")) "No one is saved as $person." else "$person is now $newName.")
        }
        val item = items(m.all).firstOrNull { it.equals(fromText, true) } ?: return LocalAgent.Reply("No one is saved as ${title(fromText)}.")
        return moveItem(m, item, title(to.joinToString(" ")).let { t -> items(m.all).firstOrNull { it.equals(t, true) } ?: t })
    }

    // ---------- two names for one thing ----------

    /**
     * "kachi eent molding ko kachi eent mein kar do", "kachi eent aur kachi eent molding ek hi hain": the entries of one
     * name go under the other (the shorter, unless said); two saved spellings of one person become one.
     */
    private fun merge(m: Msg): LocalAgent.Reply? {
        val w = m.w
        if (m.asking) return null
        val said = itemsSaid(m)
        val one = w.any { it in oneWords } || w.indices.any { i -> w[i] == "ek" && w.getOrNull(i + 1) in setOf("hi", "hee", "he", "khata", "chahiye", "naam") } ||
            w.indices.any { i -> (w[i] == "do" || w[i] == "2" || w[i] == "two") && w.getOrNull(i + 1) in khataWords + setOf("naam", "names") }
        // "A ko B mein kar do": A goes under B.
        val ko = w.indexOfFirst { it == "ko" || it == "into" || it in jagahWords }
        val directed = ko > 0 && (w[ko] != "ko" || w.drop(ko + 1).any { it in inWords || it in putWords || it in doWords || it in setOf("likho", "likh") })
        if (said.size >= 2 && (one || directed)) {
            val first = said.minByOrNull { it.second.first }!!
            val from = if (directed && first.second.last < ko) first.first else said.maxByOrNull { it.first.length }!!.first
            val to = if (directed && first.second.last < ko) said.filter { it.first != from && it.second.first > ko }.minByOrNull { it.second.first }?.first ?: return null
                else said.map { it.first }.filter { it != from }.minByOrNull { it.length }!!
            return moveItem(m, from, to)
        }
        if (said.size == 1 && directed && said[0].second.last < ko) {
            // "petrol ko food mein daal do": a category for that thing.
            val cat = w.drop(ko + 1).firstNotNullOfOrNull { catWords[it] } ?: return null
            return recategorize(m, said[0].first, cat)
        }
        // Two saved spellings of one person ("hamid aur hamid akram ek hi hain").
        val two = m.people.filter { it.choices.isEmpty() }.map { it.name }.distinct()
        if (two.size == 2 && one) {
            val count = two.associateWith { n -> m.all.count { it.person.trim().equals(n, true) } }
            val keep = two.maxWithOrNull(compareBy<String>({ it.split(' ').size }, { count[it] ?: 0 }))!!
            val drop = two.first { it != keep }
            val r = m.s.use("rename_person", JSONObject().put("from", drop).put("to", keep))
            return LocalAgent.Reply(if (r.has("error")) "That did not work." else "$drop is now $keep.")
        }
        return null
    }

    /** Saved item names said in the message, each with its words, not counting one said only inside a longer one. */
    private fun itemsSaid(m: Msg): List<Pair<String, IntRange>> {
        val spans = items(m.all).flatMap { item ->
            val p = item.lowercase(Locale.ROOT).split(' ').filter { it.isNotBlank() }
            m.w.indices.filter { i -> m.w.subList(i, minOf(m.w.size, i + p.size)).let { it.size == p.size && it.indices.all { j -> alike(it[j], p[j]) } } }
                .map { item to (it until it + p.size) }
        }
        val free = spans.filter { (item, r) -> spans.none { (o, r2) -> o != item && r2.first <= r.first && r2.last >= r.last && r2.count() > r.count() } }
        return free.groupBy { it.first.lowercase() }.map { it.value.first() }
    }

    /** Every entry saved as [from] now under [to]. */
    private fun moveItem(m: Msg, from: String, to: String): LocalAgent.Reply {
        val found = m.s.use("find_entries", JSONObject().put("words", JSONArray(listOf(from))))
        val ids = ids(found).filter { id -> m.all.any { it.id == id && it.item.trim().equals(from, true) } }
            .ifEmpty { m.all.filter { it.item.trim().equals(from, true) }.map { it.id } }
        if (ids.isEmpty()) return LocalAgent.Reply("Nothing is saved as $from.")
        val r = m.s.use("update_entries", JSONObject().put("ids", JSONArray(ids)).put("set", JSONObject().put("item", to)))
        if (r.has("error")) return LocalAgent.Reply("That did not work: " + r.optString("error") + ".")
        return LocalAgent.Reply("$from is now $to" + (if (ids.size > 1) " (${ids.size} entries)." else "."))
    }

    private fun recategorize(m: Msg, item: String, cat: String): LocalAgent.Reply {
        val ids = m.all.filter { it.item.trim().equals(item, true) && it.type == Type.KHARCHA }.map { it.id }
        if (ids.isEmpty()) return LocalAgent.Reply("Nothing is saved as $item.")
        m.s.use("update_entries", JSONObject().put("ids", JSONArray(ids)).put("set", JSONObject().put("category", cat)))
        return LocalAgent.Reply("$item is in $cat now.")
    }

    // ---------- delete ----------

    /**
     * "ye delete kar do", "akhri entry hata do", "pichle 10 minute ki sari entries delete kar do", "hamid ko jo 50000
     * advance diya tha wo delete kar do". Most of the books, or several when one was meant, is asked about first.
     */
    private fun delete(m: Msg, sure: Boolean = false): LocalAgent.Reply? {
        val w = m.w
        val undo = w.size <= 3 && w.any { Normalizer.root(it) == "undo" } && m.just.isNotEmpty()
        if (!(w.any(::isDelete) || undo) || m.asking) return null
        if (m.has(khataWords) && m.people.isEmpty() && bookSaid(m) == null && !m.has(entryWords) && !m.has(allWords))
            return LocalAgent.Reply("To remove an account, long-press it in its list, then Delete.")
        val wantAll = sure || m.has(allWords) || m.has(manyEntries)
        val picked: List<Entry> = pickFor(m, wantAll) ?: return LocalAgent.Reply("Which entry should I delete?")
        if (picked.isEmpty()) return LocalAgent.Reply("I could not find that entry.")
        if (picked.size > 1 && !wantAll && !picked.all { it.id in m.s.justSaved })
            return LocalAgent.Reply("There are ${picked.size} entries like that. Move all ${picked.size} to Trash?")
        val input = JSONObject().put("ids", JSONArray(picked.map { it.id }))
        if (sure) input.put("confirmed", true)
        val r = m.s.use("delete_entries", input)
        if (r.has("error")) {
            return if (r.optString("error").contains("confirm")) LocalAgent.Reply("That is ${picked.size} of your ${m.all.size} entries. Move them all to Trash?")
            else LocalAgent.Reply("That could not be deleted: " + r.optString("error") + ".")
        }
        val n = r.optInt("moved_to_trash", picked.size)
        return LocalAgent.Reply(if (n == 1) "Moved to Trash: " + said(picked[0]) + "." else "Moved $n entries to Trash.")
    }

    /** The entries a delete means; null when nothing in it says which. */
    private fun pickFor(m: Msg, wantAll: Boolean): List<Entry>? {
        val w = m.w
        val minutes = minutesSaid(w)
        val last = lastSaid(m)
        val d = describe(m)
        if (minutes != null) {
            val p = JSONObject().put("added_in_last_minutes", minutes)
            if (d.persons.isNotEmpty()) p.put("persons", JSONArray(d.persons))
            return found(m, p)
        }
        if (last != null) {
            val pool = if (d.any) narrow(m, d, m.all) else m.all
            return pool.sortedByDescending { it.createdAt }.take(last)
        }
        val pointing = m.has(pronouns)
        if (d.any) {
            val inJust = if (m.just.isNotEmpty()) narrow(m, d, m.just) else emptyList()
            // "ye wali entry hata do": words that name nothing point at what was just saved.
            val vague = d.persons.isEmpty() && d.amount == null && d.from == null && d.words.none { Parser.guessCategory(it) != null || it in m.thingWords }
            return inJust.ifEmpty { if (pointing && vague && m.just.isNotEmpty()) m.just else located(m, d) }
        }
        if (m.just.isNotEmpty() && (pointing || !wantAll || w.size <= 3)) return m.just
        if ((pointing || wantAll) && m.s.previous.isNotBlank() && m.s.previous != "{}") return runCatching { found(m, JSONObject(m.s.previous)) }.getOrNull()
        if (wantAll) return found(m, JSONObject())
        return null
    }

    /** "pichle 10 minute", "last 2 ghante", "pichle ghante": minutes back. */
    private fun minutesSaid(w: List<String>): Int? {
        for (i in w.indices) {
            val x = w[i]
            val n = w.getOrNull(i - 1)?.toDoubleOrNull()
            if (x in minuteWords) return (n ?: 1.0).toInt().coerceAtLeast(1)
            if (x in hourWords) return ((n ?: 1.0) * 60).toInt().coerceAtLeast(1)
        }
        return null
    }

    /** "akhri entry", "last 3 entries": how many of the newest. */
    private fun lastSaid(m: Msg): Int? {
        val w = m.w
        val i = w.indexOfFirst { Normalizer.root(it) == "last" || it in setOf("latest", "newest", "recent", "akhir", "aakhir", "abhi") }
        if (i < 0 || !(m.has(entryWords) || w.size <= 4) || w.getOrNull(i + 1) in monthWords + weekWords + minuteWords + hourWords ||
            w.getOrNull(i + 2) in minuteWords + hourWords) return null
        val n = m.num(i + 1) ?: m.num(i - 1)
        return (n ?: 1.0).toInt().coerceIn(1, 200)
    }

    // ---------- corrections of what was just saved ----------

    /** "nahi 400 tha", "ye kal ka tha", "ye zubair ko diye thay", "ye naya tariq hai", "nahi 3500 thi" on work (its count). */
    private fun correct(m: Msg): LocalAgent.Reply? {
        if (m.just.isEmpty() || m.asking) return null
        val w = m.w
        val pointing = m.has(pronouns)
        val numbers = numbersSaid(m)
        val marked = m.has(noWords) || (pointing && (m.has(pastWords) || m.has(doWords) || m.has(isWords) || m.has(putWords))) ||
            (w.size <= 4 && (m.has(pastWords) || (m.has(doWords) && numbers.isNotEmpty()))) || (m.has(fieldWords) && m.has(doWords)) ||
            (numbers.size == 2 && m.has(doWords))
        if (!marked) return null
        // "hamid ko 500 diye, ye advance hai": a new entry that names itself.
        val fresh = !m.has(noWords) && !m.has(pastWords) && numbers.isNotEmpty() && (m.has(giveWords) || m.has(takeWords)) && m.people.isNotEmpty()
        if (fresh) return null
        m.people.firstOrNull { it.choices.isNotEmpty() }?.let { return which(it) }
        val d = describe(m)
        val (old, new) = oldAndNew(m, numbers)
        var targets = m.just
        if (old != null) targets = targets.filter { near(it.amount, old) || near(it.qty, old) || near(it.rate, old) }.ifEmpty { return null }
        // A thing said picks the one it names ("petrol 70 tha"); a thing none of them is, is its right name ("nahi, petrol tha").
        val named = d.words.filter { narrowWords(targets, listOf(it)).isNotEmpty() }
        val other = d.words.filter { it !in named && it !in catWords && (Parser.guessCategory(it) != null || it in m.thingWords) }
        if (other.isNotEmpty() && !m.has(noWords) && !pointing) return null
        if (targets.size > 1 && named.isNotEmpty()) targets = narrowWords(targets, named)
        val item = other.lastOrNull()?.takeIf { targets.all { e -> e.type == Type.KHARCHA || e.type == Type.AAMDANI } }
        val mentioned = m.people.filter { it.choices.isEmpty() }
        val inTargets = mentioned.filter { p -> targets.any { it.person.trim().equals(p.name, true) } }
        if (targets.size > 1 && inTargets.isNotEmpty()) targets = targets.filter { e -> inTargets.any { it.name.equals(e.person.trim(), true) } }
        val newcomer = newcomer(m)
        val person = newcomer ?: mentioned.lastOrNull { it !in inTargets }?.name ?: markedName(m)?.takeIf { n -> targets.none { it.person.trim().equals(n, true) } }
        val date = d.day
        val cat = item?.let { Parser.guessCategory(it)?.first?.label }?.takeIf { targets.all { e -> e.type == Type.KHARCHA } }
            ?: w.firstNotNullOfOrNull { catWords[it] }?.takeIf { w.size <= 6 }
        // "ye food mein daal do" on a loan: it was spending.
        val type = typeSaid(m, targets) ?: "expense".takeIf { cat != null && targets.any { e -> e.type != Type.KHARCHA } }
        if (new == null && person == null && date == null && type == null && cat == null && item == null) {
            val vague = m.has(noWords) && (pointing || w.size <= 3) && !m.s.lastShown.trim().endsWith("?")
            return if (vague) LocalAgent.Reply("What should it be: the amount, the day or the person?") else null
        }
        if (targets.size > 1 && (new != null || person != null || item != null) && targets.map { it.person.lowercase() + it.item + it.amount }.distinct().size > 1)
            return LocalAgent.Reply("Which one: " + listed(targets.take(4).map { said(it) }) + "?")
        val changes = mutableListOf<String>()
        for (list in targets.groupBy { counted(it) }.values) {
            val set = if (new == null) JSONObject() else valueSet(m, list.first(), new).also { changes += it.second }.first
            person?.let { set.put("person", it) }
            date?.let { set.put("date", it) }
            type?.let { set.put("type", it) }
            item?.let { set.put("item", title(it)) }
            cat?.let { set.put("category", it) }
            val r = m.s.use("update_entries", JSONObject().put("ids", JSONArray(list.map { it.id })).put("set", set))
            if (r.has("error")) return LocalAgent.Reply("That could not be changed: " + r.optString("error") + ".")
        }
        item?.let { changes += title(it) }
        person?.let { changes += if (newcomer != null) "the new person $it" else it }
        if (cat == null) type?.let { changes += typeWords[it] ?: it }
        if (item == null) cat?.let { changes += it }
        val day = date?.let { dayText(it, m.s.now) }
        return LocalAgent.Reply(if (changes.isEmpty()) "Moved to $day." else "Changed to " + changes.distinct().joinToString(", ") + (day?.let { ", dated $it" } ?: "") + ".")
    }

    /**
     * The number said, set on [e] as the AI would: on counted work or goods it is the count, or the rate when "rate" or
     * "hisab" is said (money words: the rate that gives that amount); the app works out the amount. With its words for the reply.
     */
    private fun valueSet(m: Msg, e: Entry, n: Double): Pair<JSONObject, String> = when {
        counted(e) && m.has(rateWords) -> JSONObject().put("rate", n) to "rate " + amountText(n)
        counted(e) && m.has(moneyWords) -> JSONObject().put("rate", n / (if (e.unit == "1000") e.qty / 1000 else e.qty)) to amountText(n)
        counted(e) -> JSONObject().put("qty", n) to amountText(n) + (if (e.item.isNotBlank()) " " + e.item else "")
        else -> JSONObject().put("amount", n) to amountText(n)
    }

    /** "ye naya tariq hai", "alag tariq hai", "tariq new person": a new person, never a saved one with that word. */
    private fun newcomer(m: Msg): String? {
        val w = m.w
        val i = w.indexOfFirst { it in newWords }
        if (i < 0) return null
        val after = w.drop(i + 1).dropWhile { it in notNames && !nameLike(it, m, savedToo = true) }.takeWhile { nameLike(it, m, savedToo = true) }
        val before = w.take(i).takeLastWhile { nameLike(it, m, savedToo = true) }
        val name = after.ifEmpty { before }
        return if (name.isEmpty()) null else named(name)
    }

    /** "ye farooq ko diye thay": an unsaved name said with ko, se, ne or ka. */
    private fun markedName(m: Msg): String? {
        val w = m.w
        val taken = m.people.flatMap { (it.from..it.to).toList() }.toSet()
        val i = w.indices.firstOrNull { i -> i !in taken && nameLike(w[i], m) && (w.getOrNull(i + 1) in markers || w.getOrNull(i - 1) in setOf("to", "from")) } ?: return null
        return named(listOf(w[i]))
    }

    /** "mile thay" on a Paid entry: Received; "diye thay" on a Received one: Paid. */
    private fun typeSaid(m: Msg, targets: List<Entry>): String? {
        val give = m.has(giveWords)
        val take = m.has(takeWords)
        if (give == take) return null
        return when {
            take && targets.all { it.type == Type.DIYA } -> if (m.has(loanWords)) "borrowed" else "received_back"
            give && targets.all { it.type == Type.WAPIS_MILA || it.type == Type.LIYA } -> "lent"
            else -> null
        }
    }

    // ---------- changes to older entries ----------

    /** "kal wali chai 5 ki jagah 8 kar do", "petrol 60 ko 70 kar do", "hamid wali entry 400 kar do". */
    private fun change(m: Msg): LocalAgent.Reply? {
        val w = m.w
        if (m.asking) return null
        val numbers = numbersSaid(m)
        val (old, new) = oldAndNew(m, numbers)
        if (new == null) return movePerson(m)
        if (!(m.has(doWords) || m.has(jagahWords) || m.has(noWords))) return null
        if (old == null && !(m.has(entryWords) || w.any { it in setOf("wali", "wala", "wale", "vali", "wli") })) return null
        val d = describe(m)
        val withOld = d.copy(amount = old)
        if (!withOld.any) return null
        m.people.firstOrNull { it.choices.isNotEmpty() }?.let { return which(it) }
        val list = located(m, withOld)
        if (list.isEmpty()) return LocalAgent.Reply("I could not find that entry.")
        if (list.size > 1) return LocalAgent.Reply("Which one: " + listed(list.take(4).map { option(it, m.s.now) }) + "?")
        val e = list[0]
        val r = m.s.use("update_entries", JSONObject().put("ids", JSONArray(listOf(e.id))).put("set", valueSet(m, e, new).first))
        if (r.has("error")) return LocalAgent.Reply("That could not be changed: " + r.optString("error") + ".")
        val now = r.optJSONArray("entries")?.optJSONObject(0)
        return LocalAgent.Reply("Changed " + e.person.ifBlank { e.item } + " from " + amountText(e.amount) + " to " + amountText(now?.optDouble("amount", new) ?: new) + ".")
    }

    /** "kal wali hamid ki entry zubair ke khate mein daal do": an older entry put under another person. */
    private fun movePerson(m: Msg): LocalAgent.Reply? {
        val saved = m.people.filter { it.choices.isEmpty() }.distinctBy { it.name }
        val pointed = m.has(entryWords) || m.w.any { it in setOf("wali", "wala", "wale", "vali") }
        if (saved.size != 2 || !pointed || !(m.has(khataWords) || m.has(nameWords)) || !(m.has(putWords) || m.has(doWords))) return null
        val (from, to) = saved
        val list = located(m, describe(m).copy(persons = listOf(from.name)))
        if (list.isEmpty()) return LocalAgent.Reply("I could not find that entry.")
        if (list.size > 1 && !m.has(allWords) && !m.w.contains("entries"))
            return LocalAgent.Reply("Which one: " + listed(list.take(4).map { option(it, m.s.now) }) + "?")
        val r = m.s.use("update_entries", JSONObject().put("ids", JSONArray(list.map { it.id })).put("set", JSONObject().put("person", to.name)))
        if (r.has("error")) return LocalAgent.Reply("That could not be changed: " + r.optString("error") + ".")
        return LocalAgent.Reply(if (list.size == 1) "Moved " + said(list[0]) + " to " + to.name + "." else "Moved ${list.size} entries to ${to.name}.")
    }

    // ---------- what a message says about entries ----------

    /** What a message picks entries by: people, a day or month, an amount, a kind, and other words. */
    private data class Described(val persons: List<String>, val from: String?, val to: String?, val amount: Double?, val types: List<String>, val words: List<String>) {
        val any get() = persons.isNotEmpty() || from != null || amount != null || words.isNotEmpty()
        val day get() = if (from != null && from == to) from else null
    }

    private fun describe(m: Msg): Described {
        val w = m.w
        val (from, to) = period(m)
        val taken = m.people.flatMap { (it.from..it.to).toList() }.toSet()
        val nums = numbersSaid(m)
        val words = w.filterIndexed { i, x -> i !in taken && x[0].isLetter() && x.length >= 3 && x !in keywords && !isDelete(x) && Normalizer.root(x) !in keywords }
        return Described(m.people.filter { it.choices.isEmpty() }.map { it.name }, from, to, nums.singleOrNull(), typesSaid(m), words)
    }

    /** The day or month said, as yyyy-MM-dd bounds. */
    private fun period(m: Msg): Pair<String?, String?> {
        val w = m.w
        val now = m.s.now
        val day = 86_400_000L
        fun d(ms: Long) = dayOf(ms)
        when {
            w.any { it in setOf("aaj", "aj", "today", "aajj") } -> return d(now) to d(now)
            w.any { it in setOf("parso", "parson", "parsoon", "prso") } -> return d(now - 2 * day) to d(now - 2 * day)
            w.any { it in setOf("kal", "kl", "kall", "yesterday") } -> return d(now - day) to d(now - day)
        }
        val week = w.indices.firstOrNull { w[it] in weekWords }
        if (week != null) {
            val before = w.getOrNull(week - 1) in setOf("pichle", "pichhle", "last", "previous", "pichla", "guzre")
            return if (before) d(now - 13 * day) to d(now - 7 * day) else d(now - 6 * day) to d(now)
        }
        val month = w.indices.firstOrNull { w[it] in monthWords }
        if (month != null) {
            val c = Calendar.getInstance().apply { timeInMillis = now; set(Calendar.DAY_OF_MONTH, 1) }
            if (w.getOrNull(month - 1) in setOf("pichle", "pichhle", "last", "previous", "guzre", "pichla")) {
                c.add(Calendar.MONTH, -1)
                val end = Calendar.getInstance().apply { timeInMillis = c.timeInMillis; set(Calendar.DAY_OF_MONTH, getActualMaximum(Calendar.DAY_OF_MONTH)) }
                return d(c.timeInMillis) to d(end.timeInMillis)
            }
            if (w.getOrNull(month - 1) in setOf("is", "iss", "this", "current")) return d(c.timeInMillis) to d(now)
        }
        val text = w.joinToString(" ")
        val (ms, _) = runCatching { Parser.extractDate(text, now) }.getOrDefault(null to text)
        return if (ms != null) d(ms) to d(ms) else null to null
    }

    /** Numbers said for amounts: not a day ("3 november"), minutes, a count of entries or a row. */
    private fun numbersSaid(m: Msg): List<Double> {
        val w = m.w
        return w.indices.mapNotNull { i ->
            val n = m.num(i) ?: return@mapNotNull null
            val next = w.getOrNull(i + 1)
            val prev = w.getOrNull(i - 1)
            val rows = next in manyEntries || (next in entryWords && n < 10)
            if (next in monthNames || prev in monthNames || next in dateWords || next in minuteWords || next in hourWords || rows ||
                Normalizer.root(prev ?: "") == "last" || prev in setOf("latest", "newest", "recent") || next == "din" || next == "days" || next == "day") null else n
        }
    }

    /** The wrong value and the right one: "500 nahi 400", "5 ki jagah 8", "60 ko 70 kar do", "change 60 to 70", "400 instead of 500". */
    private fun oldAndNew(m: Msg, numbers: List<Double>): Pair<Double?, Double?> {
        if (numbers.isEmpty()) return null to null
        if (numbers.size == 1) return null to numbers[0]
        val w = m.w
        val first = w.indices.first { m.num(it) == numbers[0] }
        val second = w.indices.last { m.num(it) == numbers[1] }.coerceAtLeast(first + 1)
        val between = w.subList(first + 1, second)
        val after = w.getOrNull(second + 1)
        return if (between.any { it == "instead" || it == "not" } || (after in noWords && between.none { it in noWords })) numbers[1] to numbers[0] else numbers[0] to numbers[1]
    }

    /** Kinds of entry the words say ("diya" lent, "mile" received_back, "kharcha" expense…), for picking among several. */
    private fun typesSaid(m: Msg): List<String> {
        val out = mutableListOf<String>()
        if (m.has(giveWords) || m.has(setOf("advance"))) out += "lent"
        if (m.has(takeWords)) out += "received_back"
        if (m.has(setOf("kharcha", "kharche", "expense", "kharch"))) out += "expense"
        if (m.has(incomeWords)) out += "income"
        if (m.has(setOf("kaam", "work"))) out += "work"
        if (m.has(setOf("sale", "becha", "bechi", "beche", "sold"))) out += "sale"
        return out
    }

    /** The entries a description finds: find_entries by people, days and amount, then narrowed by the other words and kind. */
    private fun located(m: Msg, d: Described): List<Entry> {
        m.pinned?.let { return it }
        val p = JSONObject()
        if (d.persons.isNotEmpty()) p.put("persons", JSONArray(d.persons))
        d.from?.let { p.put("from", it) }
        d.to?.let { p.put("to", it) }
        d.amount?.let { p.put("min_amount", it).put("max_amount", it) }
        return narrow(m, d, found(m, p))
    }

    /** [list] narrowed by the words and kind said, keeping what fits when a narrowing would leave nothing. */
    private fun narrow(m: Msg, d: Described, list: List<Entry>): List<Entry> {
        var out = list.filter { e ->
            (d.persons.isEmpty() || d.persons.any { it.equals(e.person.trim(), true) }) &&
                (d.amount == null || near(e.amount, d.amount) || near(e.qty, d.amount)) &&
                (d.from == null || dayOf(e.date) >= d.from) && (d.to == null || dayOf(e.date) <= d.to)
        }
        if (d.words.isNotEmpty()) {
            val byWords = narrowWords(out, d.words)
            out = if (byWords.isNotEmpty() || d.persons.isEmpty() && d.amount == null && d.from == null) byWords else out
        }
        if (out.size > 1 && d.types.isNotEmpty()) out.filter { e -> d.types.any { typeOf[it] == e.type } }.takeIf { it.isNotEmpty() }?.let { out = it }
        return out
    }

    /** Entries whose item, note or category is one of [words] ("chai" finds Tea, "khana" Food). */
    private fun narrowWords(list: List<Entry>, words: List<String>): List<Entry> {
        val byItem = list.filter { e ->
            val parts = (e.item + " " + e.note).lowercase(Locale.ROOT).split(Regex("""[^\p{L}\p{N}]+""")).filter { it.isNotBlank() }
            words.any { w -> parts.any { it == w || Normalizer.root(it) == Normalizer.root(w) || (w.length >= 4 && alike(w, it)) } }
        }
        if (byItem.isNotEmpty()) return byItem
        val cats = words.mapNotNull { catWords[it] }
        return list.filter { e -> e.category.label in cats }
    }

    /** find_entries with [picking]; every match, also beyond the 40 it lists. */
    private fun found(m: Msg, picking: JSONObject): List<Entry> {
        val r = m.s.use("find_entries", picking)
        if (r.optInt("not_shown", 0) == 0) {
            val ids = ids(r)
            return m.all.filter { it.id in ids }.sortedByDescending { it.date }
        }
        val from = picking.optString("from").takeIf { it.isNotBlank() }
        val to = picking.optString("to").takeIf { it.isNotBlank() }
        val minutes = picking.optInt("added_in_last_minutes", 0)
        val persons = picking.optJSONArray("persons")?.let { a -> (0 until a.length()).map { a.getString(it) } }.orEmpty()
        val min = picking.optDouble("min_amount", Double.NaN)
        val max = picking.optDouble("max_amount", Double.NaN)
        return m.all.filter { e ->
            (from == null || dayOf(e.date) >= from) && (to == null || dayOf(e.date) <= to) && (minutes <= 0 || e.createdAt >= m.s.now - minutes * 60_000L) &&
                (persons.isEmpty() || persons.any { it.equals(e.person.trim(), true) }) && (min.isNaN() || e.amount >= min) && (max.isNaN() || e.amount <= max)
        }.sortedByDescending { it.date }
    }

    private fun ids(r: JSONObject): Set<String> = r.optJSONArray("entries")?.let { a -> (0 until a.length()).map { a.getJSONObject(it).optString("id") } }.orEmpty().toSet()

    // ---------- people ----------

    /** Saved people said in [m] (whole name, a word of it, a short form or a near spelling); a word several share gives its choices. */
    private fun peopleIn(m: Msg): List<Who> {
        val out = mutableListOf<Who>()
        val names = m.names.filter { it.isNotBlank() }.distinctBy { it.lowercase() }
        val parts = names.associateWith { it.lowercase(Locale.ROOT).split(Regex("""\s+""")) }
        var i = 0
        val w = m.w
        while (i < w.size) {
            val x = w[i]
            if (!nameLike(x, m, savedToo = true)) { i++; continue }
            val full = parts.filter { (_, p) -> p.size > 1 && w.subList(i, minOf(w.size, i + p.size)) == p }.maxByOrNull { it.value.size }
                ?: parts.filter { (_, p) -> p.size == 1 && p[0] == x }.entries.firstOrNull()
            if (full != null) { out += Who(i, i + full.value.size - 1, full.key); i += full.value.size; continue }
            var pool = names.filter { n -> parts.getValue(n).any { it == x } }
            if (pool.isEmpty()) pool = names.filter { n -> parts.getValue(n).filter { it.length >= 3 }.any { Understand.closeName(x, it) } }
            if (pool.isEmpty()) { i++; continue }
            // The words after it pick one of several ("salim raza", "salim khan").
            var end = i
            while (end + 1 < w.size && nameLike(w[end + 1], m, savedToo = true) && pool.any { n -> parts.getValue(n).any { it == w[end + 1] || Understand.closeName(w[end + 1], it) } }) end++
            val said = w.subList(i, end + 1)
            val best = if (pool.size > 1) pool.filter { n -> said.all { s -> parts.getValue(n).any { it == s || Understand.closeName(s, it) } } }
                .let { fit -> fit.filter { parts.getValue(it).size == said.size }.ifEmpty { fit } } else pool
            out += if (best.size == 1) Who(i, end, best[0]) else Who(i, end, title(x), best.ifEmpty { pool })
            i = end + 1
        }
        return out
    }

    /** A word that can be a person's name: letters, not an everyday word of these messages, not a thing in use. */
    private fun nameLike(x: String, m: Msg, savedToo: Boolean = false): Boolean =
        x.length >= 3 && x.all { it.isLetter() } && x !in notNames && Normalizer.root(x) !in notNames && !isDelete(x) && x !in catWords &&
            (savedToo || (x !in m.thingWords && Parser.guessCategory(x) == null))

    /** "Which Salim do you mean: Salim Khan or Salim Raza?", with the word as the names spell it. */
    private fun which(p: Who): LocalAgent.Reply {
        val word = p.choices.first().split(' ').firstOrNull { it.equals(p.name, true) || Understand.closeName(p.name.lowercase(Locale.ROOT), it.lowercase(Locale.ROOT)) } ?: p.name
        return LocalAgent.Reply("Which $word do you mean: " + listed(p.choices) + "?")
    }

    /** A new person's name as said, a misspelled common name put right ("akbr" from Urdu script: Akbar). */
    private fun named(words: List<String>) = title(words.joinToString(" ") { Understand.fixName(it)?.lowercase(Locale.ROOT) ?: it })

    // ---------- small helpers ----------

    /** Saved item names, most used first. */
    private fun items(all: List<Entry>): List<String> = all.map { it.item.trim() }.filter { it.isNotBlank() && it.lowercase() !in genericItems }
        .groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.map { it.key }.distinctBy { it.lowercase() }

    private fun alike(said: String, part: String) = said == part || Normalizer.root(said) == Normalizer.root(part) ||
        (part.length >= 4 && said.length >= 3 && (Understand.shortFor(said, part) || (Normalizer.distance(said, part) <= 1 && said.length >= 4)))

    private fun counted(e: Entry) = e.qty > 0 && e.rate > 0

    private fun near(a: Double, b: Double) = kotlin.math.abs(a - b) < 0.005

    private fun isDelete(x: String) = Normalizer.root(x) == "delete" || x in deleteWords

    private fun dayOf(ms: Long) = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(ms))

    private fun dayText(day: String, now: Long): String = when (day) {
        dayOf(now) -> "today"
        dayOf(now - 86_400_000L) -> "yesterday"
        else -> runCatching { SimpleDateFormat("d MMM", Locale.US).format(SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(day)!!) }.getOrDefault(day)
    }

    private fun amountText(n: Double) = if (n == Math.floor(n)) String.format(Locale.US, "%,d", n.toLong()) else String.format(Locale.US, "%,.2f", n)

    /** An entry as the chat names it: "Tea 15", "Hamid Akram 500". */
    private fun said(e: Entry) = e.person.ifBlank { e.item.ifBlank { e.category.label } } + " " + amountText(e.amount)

    private fun said(o: JSONObject) = o.optString("person").ifBlank { o.optString("item") } + " " + amountText(o.optDouble("amount", 0.0))

    private fun listed(names: List<String>) = if (names.size < 2) names.joinToString() else names.dropLast(1).joinToString(", ") + " or " + names.last()

    private fun title(x: String) = x.trim().split(Regex("""\s+""")).joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }

    /** Roman, lower case, every number in digits ("2 hazar" 2000, "5k" 5000, "1,500" 1500, Urdu digits too). */
    private fun prepare(raw: String, roman: String): String {
        val digits = raw.map { c ->
            when (c) {
                in '۰'..'۹' -> '0' + (c - '۰')
                in '٠'..'٩' -> '0' + (c - '٠')
                in '०'..'९' -> '0' + (c - '०')
                else -> c
            }
        }.joinToString("")
        var t = (if (digits != raw) runCatching { Translit.toRoman(digits) }.getOrDefault(digits) else roman).lowercase(Locale.ROOT)
        t = t.replace(Regex("""(?<=\d),(?=\d{3}(?!\d))"""), "")
        t = t.replace(Regex("""(\d+(?:\.\d+)?)k(?![\p{L}])""")) { plain(it.groupValues[1].toDouble() * 1000) }
        t = t.replace(Regex("""(\d)(\p{L})"""), "$1 $2").replace(Regex("""(\p{L})(\d)"""), "$1 $2")
        t = t.replace(Regex("""(?<![\p{L}\d.])(\d+(?:\.\d+)?)\s+(sau|hundred|hazar|hazaar|hajar|thousand|lakh|lac)(?![\p{L}])""")) { m ->
            val times = when (m.groupValues[2]) { "sau", "hundred" -> 100.0; "lakh", "lac" -> 100_000.0; else -> 1000.0 }
            plain(m.groupValues[1].toDouble() * times)
        }
        return runCatching { NumberWords.convert(t) }.getOrDefault(t)
    }

    private fun plain(n: Double) = if (n == Math.floor(n)) n.toLong().toString() else n.toString()

    private val WORD = Regex("""\d+(?:\.\d+)?|[\p{L}\p{M}]+|\?""")
    private val PENDING = Regex("""to Trash\?\s*$""")

    // ---------- words ----------

    private val pronouns = setOf("ye", "yeh", "yah", "ya", "yahi", "yehi", "is", "isko", "isse", "ise", "isay", "isey", "iska", "iski", "iske", "ase",
        "isku", "us", "usko", "usay", "usey", "uska", "uski", "wo", "woh", "vo", "voh", "wohi", "it", "this", "that", "these", "those", "inko", "unko", "inhe", "inhen")
    private val pastWords = setOf("tha", "thi", "the", "thay", "thain", "thin", "thy", "thee", "was", "were")
    private val isWords = setOf("hai", "hain", "he", "hy", "h", "is", "are", "be", "should", "hona", "hone", "chahiye", "chahie", "chaiye")
    private val noWords = setOf("nahi", "nahin", "nhi", "nai", "nahee", "nehi", "nae", "no", "nope", "not", "galat", "ghalat", "glt", "galt", "wrong",
        "sorry", "oops", "actually", "asal", "asl", "balki", "correction", "mistake", "ghalti", "galti")
    private val doWords = setOf("kar", "karo", "kardo", "krdo", "kr", "kro", "karde", "kardein", "karden", "kardena", "kariye", "kijiye", "karein", "karen",
        "likho", "likh", "likhdo", "rakho", "rakh", "rakhdo", "set", "make", "change", "badal", "badlo", "badaldo", "update", "edit", "fix", "correct")
    private val yesWords = setOf("haan", "han", "haa", "ha", "hn", "haanji", "ji", "jee", "jii", "yes", "yeah", "yep", "yup", "ok", "okay", "okk", "theek", "thik",
        "sahi", "bilkul", "zaroor", "zarur", "done", "confirm", "sure", "go", "ahead", "do", "dein", "den", "de")
    private val politeWords = setOf("please", "plz", "pls", "bhai", "yaar", "yar", "jaldi", "abhi", "them", "all", "everything", "sab", "hi")
    private val allWords = setOf("sab", "sari", "saari", "sare", "saare", "sara", "saara", "tamam", "tmam", "all", "every", "everything", "poori", "puri", "sabhi", "saray", "sarey", "dono", "both")
    private val entryWords = setOf("entry", "entries", "entri", "entrie", "entery", "antry", "intry", "entr", "entriyan", "entrian", "entris", "antrian", "antriyan", "intriyan", "record", "records", "transaction", "transactions")
    private val manyEntries = setOf("entries", "entriyan", "entrian", "antrian", "antriyan", "intriyan", "entris", "records", "transactions")
    private val restoreWords = setOf("restore", "restor", "ristor", "ristore", "recover", "undelete")
    private val deleteWords = setOf("delete", "deleted", "hata", "hta", "htao", "hatado", "htado", "hatana", "hatao", "mita", "mitado", "mitana", "mitao", "remove", "remov",
        "erase", "cancel", "khatam", "trash", "udao", "uda", "urao", "ura", "kaat", "kato", "kaato", "katdo", "kaatdo", "saaf", "clear", "del", "dlt", "nikal", "nikalo", "nikaldo")
    private val backWords = setOf("wapas", "wapis", "vapas", "vapis", "waps", "wps", "wapass", "wapes", "back")
    private val bringWords = setOf("la", "lao", "laao", "laa", "le", "lay", "laye", "layen", "lana", "lado", "laado", "lena", "aao", "ao", "lau")
    private val askWords = setOf("kitna", "kitne", "kitni", "kya", "kia", "kaun", "kon", "konsa", "konsi", "kis", "kab", "kahan", "kaise", "kesay", "how", "what", "who",
        "which", "when", "where", "why", "batao", "bata", "btao", "dikhao", "dikha", "show", "tell", "kyun", "kyon")
    private val fieldWords = setOf("amount", "raqam", "rakam", "date", "tarikh", "tareekh", "din", "rate", "qty", "count", "ginti", "tadaad", "person", "banda", "category", "item")
    private val rateWords = setOf("rate", "hisab", "hisaab", "per", "fi", "each", "har")
    private val moneyWords = setOf("rupay", "rupaye", "rupees", "rs", "pkr", "paise", "paisay", "amount", "raqam", "total", "riyal")
    private val newWords = setOf("naya", "nya", "nia", "nayi", "naye", "new", "alag", "dusra", "doosra", "dusri", "doosri", "another", "different")
    private val markers = setOf("ko", "se", "ne", "ka", "ki", "ke", "say", "sy", "ny", "nay")
    private val giveWords = setOf("diye", "diya", "di", "dia", "diay", "dye", "gave", "given", "paid", "bheje", "bheja")
    private val takeWords = setOf("mile", "mila", "mili", "liye", "liya", "li", "lia", "received", "got", "aaye", "aaya", "aye", "aya")
    private val loanWords = setOf("udhaar", "udhar", "loan", "qarz", "karz", "qarza")
    private val minuteWords = setOf("minute", "minutes", "min", "mins", "mint", "minat", "minton", "mnt")
    private val hourWords = setOf("ghanta", "ghante", "ghanton", "ghantay", "hour", "hours", "hr", "hrs")
    private val jagahWords = setOf("jagah", "jaga", "jgah", "instead")
    private val dateWords = setOf("date", "tarikh", "tareekh", "tarik", "taarikh")
    private val shareWords = setOf("pdf", "report", "statement", "print", "share", "bhejo", "send")
    private val weekWords = setOf("hafte", "hafta", "haftay", "hafty", "week")
    private val monthWords = setOf("mahine", "mahina", "mahinay", "month", "maheene", "mahene")
    private val monthNames = setOf("january", "jan", "february", "feb", "march", "mar", "april", "apr", "may", "june", "jun", "july", "jul", "august", "aug",
        "september", "sep", "sept", "october", "oct", "november", "nov", "december", "dec")
    private val khataWords = setOf("khata", "khate", "khaata", "khatta", "khaate", "khatay", "account", "accounts", "ledger")
    private val makeWords = setOf("bana", "banao", "banado", "banaen", "banayen", "banaye", "bnao", "bnado", "banana", "banaiye", "create", "make", "open", "kholo", "khol", "add")
    private val putWords = setOf("daal", "dal", "daalo", "dalo", "daldo", "daaldo", "dalna", "daalna", "dalden", "daalden", "rakh", "rakho", "rakhdo", "add", "put", "move",
        "shift", "bhej", "bhejo", "include", "shamil", "mila", "milao", "bana", "banao", "banado")
    private val outWords = setOf("nikal", "nikalo", "nikaldo", "hata", "hatao", "hatado", "remove")
    private val inWords = setOf("mein", "me", "main", "men", "mai", "may", "ma", "mn", "in", "into", "to")
    private val oneWords = setOf("same", "merge", "mila", "milao", "milado", "jor", "jod", "jodo", "joro", "combine", "ikatha", "ikattha", "ekatha")
    private val expenseWords = setOf("expense", "expenses", "kharcha", "kharche", "kharchay", "kharch", "kharchon", "exp")
    private val incomeWords = setOf("income", "aamdani", "amdani", "kamai", "kamaai", "aamdan")
    private val groupWords = setOf("log", "logon", "wale", "workers", "drivers", "mazdoor", "mazdooron", "karigar", "dost", "friends", "family", "rishtedar", "mistri", "labour")
    private val accountFiller = setOf("ka", "ki", "ke", "kaa", "ek", "a", "an", "the", "for", "named", "called", "naam", "se", "mera", "meri", "mere", "hamara", "liye", "lie",
        "please", "plz", "do", "de", "dein", "den", "karo", "kar", "new", "hai", "chahiye", "of", "with", "us", "is", "mein", "me", "main", "bhi")
    private val nameWords = setOf("naam", "nam", "name", "nama", "naame")
    private val typeWords = mapOf("lent" to "Paid", "received_back" to "Received", "borrowed" to "Borrowed", "paid_back" to "Paid back", "expense" to "Expense", "income" to "Income")
    private val typeOf = mapOf("lent" to Type.DIYA, "received_back" to Type.WAPIS_MILA, "expense" to Type.KHARCHA, "income" to Type.AAMDANI, "work" to Type.PURCHASE, "sale" to Type.SALE)
    private val genericItems = setOf("loans", "cash in", "sales", "purchases", "other")
    private val usualBooks = mapOf(
        Sides.STAFF to setOf("staff", "staf", "istaf", "worker", "workers", "mazdoor", "mazdur", "labour", "labor", "employee", "employees", "karigar", "kaarigar", "mulazim", "mulazmeen", "naukar"),
        Sides.CUSTOMERS to setOf("customer", "customers", "kstmr", "kastamar", "kastmar", "kustomer", "costumer", "costumers", "custmer", "gahak", "gahek", "grahak", "client", "clients", "khareedar", "kharidar"),
        Sides.SUPPLIERS to setOf("supplier", "suppliers", "splar", "saplayar", "saplaiar", "suplayar", "sapplayar", "suplier", "supliers", "supplyer", "vendor", "vendors", "dealer", "dealers", "beopari"),
        Sides.PEOPLE to setOf("people", "personal")
    )
    /** Category words to the app's labels. */
    private val catWords = mapOf("food" to "Food", "khana" to "Food", "khane" to "Food", "transport" to "Transport", "safar" to "Transport", "rent" to "Rent",
        "kiraya" to "Rent", "bills" to "Bills", "bill" to "Bills", "bijli" to "Bills", "health" to "Health", "sehat" to "Health", "dawai" to "Health",
        "education" to "Education", "taleem" to "Education", "shopping" to "Shopping", "mobile" to "Mobile & Internet", "internet" to "Mobile & Internet",
        "gifts" to "Gifts & Charity", "charity" to "Gifts & Charity", "sadqa" to "Gifts & Charity", "assets" to "Assets", "other" to "Other", "fees" to "Fees & Govt")
    /** Small words that only join a sentence or fill it ("wali", "kal", "acha"), and the months. */
    private val glueWords = setOf("par", "pe", "aur", "and", "or", "to", "toh", "bhi", "hi", "wala", "wali", "wale", "vali", "wli", "jo", "jis", "jab", "ho",
        "hua", "hui", "gaya", "gayi", "gaye", "kal", "aaj", "parso", "parson", "kiya", "kiye", "last", "the", "for", "from", "with", "my", "mera", "meri", "mere",
        "today", "yesterday", "a", "an", "of", "on", "in", "at", "ek", "karna", "karni", "abhi", "undo", "acha", "accha", "achha", "achcha", "hmm", "umm", "um",
        "uh", "arre", "arey", "oh", "suno", "sun", "dekho", "chalo", "bas", "zara", "jara") + monthNames
    /** Every word these messages use to say what to do, never a thing or a name. */
    private val keywords = glueWords + pronouns + pastWords + isWords + noWords + doWords + yesWords + politeWords + allWords + entryWords + backWords +
        bringWords + askWords + fieldWords + rateWords + moneyWords + newWords + giveWords + takeWords + loanWords + minuteWords + hourWords + jagahWords +
        dateWords + monthWords + weekWords + khataWords + makeWords + putWords + outWords + inWords + expenseWords + incomeWords + nameWords + deleteWords + oneWords +
        markers + accountFiller + usualBooks.values.flatten()
    private val notNames = keywords + groupWords + setOf("advance", "udhaar", "udhar", "wapis", "kaam", "kam", "work", "sale",
        "becha", "rename", "merge", "same", "ban", "bane", "gaye", "mujhe", "mujh", "hum", "hamein", "apna", "apni", "dono", "sath", "saath", "sahab", "sahib",
        "lo", "pehle", "baad", "phir", "bas", "sirf", "only", "just", "now", "ab", "tab", "kuch", "koi", "aik", "hamara", "hamari", "mera", "banda", "person")
}
