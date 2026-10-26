package com.bolkhata.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Understands any spoken sentence with Google Gemini and turns it into entries.
 * The key is the person's own and stays on the phone. If anything fails the app
 * falls back to the offline parser.
 */
object Ai {

    /** Tried in order; the first one that answers is remembered. */
    val MODELS = listOf("gemini-3.5-flash-lite", "gemini-3.1-flash-lite", "gemini-flash-lite-latest", "gemini-2.5-flash-lite")

    /** Everything uses the fast Flash-Lite models; null = the one that last answered. */
    val CHAT_MODEL: String? = null

    /**
     * Stronger models, used only when a message is long or asks for changes (edits, deletes, splitting a bill, settling up),
     * or when the fast model's answer doesn't check out. They think a little before answering.
     */
    val STRONG = listOf("gemini-3.8-flash", "gemini-flash-latest", "gemini-2.5-flash")

    /** Messages that need real reasoning: long ones, several numbers, or corrections and settlements. */
    private fun needsStrong(message: String): Boolean {
        val m = message.lowercase()
        if (m.length > 140) return true
        if (Regex("""\d+(\.\d+)?""").findAll(m).count() >= 4) return true
        return Regex(
            """\b(minus|plus|hissa|hisa|share|split|adha|aadha|delete|delet|hata|hatao|galat|ghalat|wrong|change|badal|badlo|instead|""" +
                """theek kar|thek kar|correct|fix|settle|hisab|hisaab|baqi|baaki|owe|udhaar|udhar|wapis|return|duplicate|double|""" +
                """merge|shift|move|edit|update|nahi tha|nahin tha|not \d)""" + """\b"""
        ).containsMatchIn(m)
    }

    /** Photos and statement pages use the same fast model (many pages are read at the same time). */
    val FILE_MODEL: String? = null

    /** Models that just failed (over the limit, too slow) are skipped for a while, so nobody waits on them again. */
    private val resting = java.util.concurrent.ConcurrentHashMap<String, Long>()

    /** Models that refused the speed settings; they are sent plain requests from then on. */
    private val plain = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

    /** "gemini-3.5-flash-lite" -> "3.5 Flash-Lite", for the small line under each answer. */
    fun shortName(model: String) = model.removePrefix("gemini-")
        .replace("-flash-lite", " Flash-Lite").replace("-flash", " Flash")
        .replace("flash-lite-latest", "Flash-Lite latest").replace("flash-latest", "Flash latest")

    class Failure(message: String) : Exception(message)

    /** Exact figures (worked out in code), the entries as compact numbered lines, and likely duplicates. */
    private fun ledgerContext(
        all: List<Entry>, recent: List<Entry>, currency: String, compact: Boolean = false,
        facts: Boolean = true, withDupes: Boolean = true
    ): String {
        val entryLines = recent.joinToString("\n") { e -> Accounts.line(e) }
        // Likely duplicates, worked out here so the AI doesn't have to spot them: same kind and amount, and either
        // within 3 days of each other or on the same day and month in another year (a misread date).
        val day = 86_400_000L
        val dupes = mutableListOf<String>()
        // Only entries with the same kind and amount can be duplicates, so compare within those groups.
        recent.withIndex()
            .groupBy { (_, e) -> e.type.name + "|" + Math.round(e.amount * 100) }
            .values.filter { it.size > 1 }
            .forEach { group ->
                for (x in group.indices) for (y in x + 1 until group.size) {
                    if (dupes.size >= 40) return@forEach
                    val (i, a) = group[x]
                    val (j, b) = group[y]
                    val ca = Calendar.getInstance().apply { timeInMillis = a.date }
                    val cb = Calendar.getInstance().apply { timeInMillis = b.date }
                    val near = kotlin.math.abs(a.date - b.date) <= 3 * day
                    val sameDayOtherYear = ca.get(Calendar.MONTH) == cb.get(Calendar.MONTH) &&
                        kotlin.math.abs(ca.get(Calendar.DAY_OF_MONTH) - cb.get(Calendar.DAY_OF_MONTH)) <= 2 &&
                        ca.get(Calendar.YEAR) != cb.get(Calendar.YEAR)
                    if (near || sameDayOtherYear) dupes += "id=${Accounts.code(a)} and id=${Accounts.code(b)} (${fmt(a.amount)})"
                }
            }
        val dupeText = if (dupes.isEmpty()) "none found" else dupes.joinToString("; ")
        return (if (facts) Accounts.facts(all, currency, compact = compact) + "\n\n" else "") +
            "Saved entries that matter for this message, newest first (only these are shown; there are ${all.size} saved in all. " +
            "id is the entry's fixed code to use in edits and delete; columns: id | date time | kind | amount | category | item | extras):\n" +
            entryLines.ifBlank { "(none)" } +
            (if (withDupes) "\n\nPossible duplicates (same kind and amount, close dates or the same date in another year; check item and note): $dupeText" else "")
    }

    /** What a chat message is about, and the saved entries worth sending with it. */
    class Focus(
        val small: Boolean,
        val newMoney: Boolean,
        val edit: Boolean,
        val question: Boolean,
        val report: Boolean,
        val story: Boolean,
        val help: Boolean,
        val dupes: Boolean,
        val entries: List<Entry>,
        /** Khata, item and period summaries worked out by the app. */
        val worked: String = ""
    )

    private val editRx = Regex(
        """\b(minus|plus|hissa|hisa|share|split|delete|delet|hata|hatao|hatado|galat|ghalat|wrong|change|badal|badlo|instead|""" +
            """theek kar|thek kar|correct|fix|shift|move|edit|update|nahi tha|nahin tha|nahi thi|merge|undo|remove|kam kar|zyada kar)\b"""
    )
    private val questionRx = Regex(
        """\b(kitna|kitne|kitni|kab|kahan|kaha|total|report|summary|balance|hisab|hisaab|khata|kharch|kharcha|kharche|spent|spend|""" +
            """income|aamdani|amdani|bacha|bachat|save|saving|owe|udhaar|udhar|lena|dena|baqi|baaki|list|search|find|show|dikhao|batao|""" +
            """how much|when did|where did|compare|average|sab se zyada|most|least|transactions?|entries|entry)\b"""
    )
    private val reportRx = Regex("""\b(report|summary|mahine|month|monthly|week|hafte|hafta|saal|year|total|compare|average|category|categories)\b""")
    private val helpRx = Regex("""\b(kaise|kese|how (do|to|can)|app|feature|option|button|settings?|import|statement|pdf)\b""")
    private val dupeRx = Regex("""\b(duplicate|duplicates|double|do dafa|2 dafa|dobara|same entry|twice)\b""")
    private val pronounRx = Regex("""\b(us|uska|uski|uske|unka|unki|unke|iska|iski|iske|is ka|us ka|his|her|their|him|them)\b""")
    private val stopWords = setOf(
        "kya", "kitna", "kitne", "kitni", "hai", "hain", "tha", "thi", "the", "aur", "mein", "main", "men", "wala", "wali", "wale",
        "kar", "karo", "kro", "diya", "diye", "liya", "liye", "aaj", "kal", "abhi", "bhi", "nahi", "nahin", "yar", "yaar", "please",
        "plz", "batao", "dikhao", "and", "for", "how", "much", "what", "this", "that", "with", "total", "kharch", "kharcha", "kharche",
        "spent", "entry", "entries", "mahine", "month", "week", "hafte", "din", "sab", "kuch", "koi", "tum", "mujhe", "mera", "meri",
        "mere", "hua", "hue", "hui", "gaya", "gaye", "gayi", "raha", "rahe", "rahi", "khata", "hisab", "hisaab", "balance", "kab",
        "kahan", "kaha", "par", "per", "pay", "paid", "riyal", "riyals", "sar", "rupay", "rupees", "wapis", "dena", "lena", "baqi",
        "baaki", "udhaar", "udhar", "report", "summary", "list", "show", "find", "search", "delete", "change", "edit", "galat",
        "uska", "uski", "uske", "unka", "iska", "iski", "time", "date", "last", "pichla", "pichli", "agla", "kitny", "hoty", "hota",
        "kiya", "kiye", "karna", "krna", "bheja", "bheje", "bheji", "send", "sent", "rahay", "hoon", "woh", "osne", "usne", "jab",
        "phir", "phr", "abi", "ab", "sath", "saath", "liye", "lye", "wohi", "same", "day", "today", "yesterday", "week", "year"
    )

    private fun wordsOf(s: String) = s.lowercase().split(Regex("[^\\p{L}\\p{N}]+")).filter { it.length >= 3 }

    @Volatile private var currencyNow = ""

    private class Subject(
        val people: List<Pair<String, List<Entry>>>,
        val items: List<Entry>,
        val itemLabel: String,
        val range: Pair<Long, Long>?,
        val rangeLabel: String,
        val periodEntries: List<Entry>
    ) {
        fun isEmpty() = people.isEmpty() && items.isEmpty() && range == null
    }

    /** "muhammed" and "muhammad" are the same name. */
    private fun sameWord(a: String, b: String) =
        a == b || (a.length >= 5 && b.length >= 5 && a.take(5) == b.take(5) && kotlin.math.abs(a.length - b.length) <= 2)

    private fun subjectOf(m: String, sorted: List<Entry>): Subject {
        val msgWords = wordsOf(m)
        // People: names on entries (person, and "To X" / "From X" notes on transfers).
        fun namesOf(e: Entry): List<String> = listOfNotNull(
            e.person.trim().takeIf { it.isNotEmpty() },
            if (e.note.startsWith("To ") || e.note.startsWith("From ")) e.note.substringAfter(' ').trim().takeIf { it.isNotEmpty() } else null
        )
        val candidates = sorted.flatMap { namesOf(it) }.distinctBy { it.lowercase() }
        // A name word shared by many people (Muhammad, Ahmed…) only counts together with another word of the name.
        val wordUse = candidates.flatMap { c -> wordsOf(c).distinct() }.groupingBy { it }.eachCount()
        val people = mutableListOf<Pair<String, List<Entry>>>()
        val used = mutableSetOf<String>()
        for (w in msgWords) {
            if (w in stopWords) continue
            val token = wordUse.keys.firstOrNull { sameWord(it, w) } ?: continue
            if ((wordUse[token] ?: 0) > 2) continue
            if (!used.add(token)) continue
            val names = candidates.filter { c -> wordsOf(c).any { it == token } }
            val list = sorted.filter { e -> namesOf(e).any { n -> names.any { it.equals(n, true) } } }.take(80)
            if (list.isNotEmpty() && people.none { p -> p.second.map { it.id }.toSet() == list.map { it.id }.toSet() }) {
                people += (names.maxByOrNull { it.length } ?: token) to list
            }
        }
        val personWords = people.flatMap { p -> wordsOf(p.first) }.toSet()

        // Items, shops, categories: other words of the message found in entries.
        val keys = msgWords.filter { w -> w !in stopWords && personWords.none { sameWord(it, w) } && w.toDoubleOrNull() == null }
        val items = if (keys.isEmpty()) emptyList() else sorted.filter { e ->
            val hay = (e.item + " " + e.note + " " + e.category.label).lowercase()
            keys.any { hay.contains(it) }
        }.take(40)
        val itemLabel = keys.filter { k -> items.any { e -> (e.item + " " + e.note + " " + e.category.label).lowercase().contains(k) } }.joinToString(" ")

        // A period: today, yesterday, this / last week or month, or a month's name.
        val now = Calendar.getInstance()
        fun dayStart(c: Calendar) = (c.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        val today = dayStart(now).timeInMillis
        val day = 86_400_000L
        val months = listOf("jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec")
        var rangeLabel = ""
        val range: Pair<Long, Long>? = when {
            Regex("""\b(aaj|aj|today)\b""").containsMatchIn(m) -> { rangeLabel = "today"; today to today + day }
            Regex("""\b(kal|yesterday)\b""").containsMatchIn(m) -> { rangeLabel = "yesterday"; today - day to today }
            Regex("""\b(is hafte|this week|is week)\b""").containsMatchIn(m) -> { rangeLabel = "last 7 days"; today - 6 * day to today + day }
            Regex("""\b(pichle hafte|pichly hafte|last week)\b""").containsMatchIn(m) -> { rangeLabel = "the 7 days before that"; today - 13 * day to today - 6 * day }
            Regex("""\b(is mahine|is month|this month)\b""").containsMatchIn(m) -> {
                rangeLabel = "this month so far"
                dayStart(now).apply { set(Calendar.DAY_OF_MONTH, 1) }.timeInMillis to today + day
            }
            Regex("""\b(pichle mahine|pichly mahine|last month)\b""").containsMatchIn(m) -> {
                val a = dayStart(now).apply { set(Calendar.DAY_OF_MONTH, 1); add(Calendar.MONTH, -1) }
                rangeLabel = SimpleDateFormat("MMMM yyyy", Locale.US).format(a.time)
                a.timeInMillis to (a.clone() as Calendar).apply { add(Calendar.MONTH, 1) }.timeInMillis
            }
            else -> months.indexOfFirst { mo -> Regex("""\b$mo[a-z]*\b""").containsMatchIn(m) }.takeIf { it >= 0 }?.let { mo ->
                val a = dayStart(now).apply {
                    set(Calendar.DAY_OF_MONTH, 1); set(Calendar.MONTH, mo)
                    if (timeInMillis > today) add(Calendar.YEAR, -1)
                }
                rangeLabel = SimpleDateFormat("MMMM yyyy", Locale.US).format(a.time)
                a.timeInMillis to (a.clone() as Calendar).apply { add(Calendar.MONTH, 1) }.timeInMillis
            }
        }
        val periodEntries = if (range == null) emptyList() else sorted.filter { it.date >= range.first && it.date < range.second }
        return Subject(people, items, itemLabel, range, rangeLabel, periodEntries)
    }

    /** Picks what to send for [message]: the matching person, item, shop or period, recent and talked-about entries. */
    private fun focusOf(message: String, history: List<ChatMessage>, sorted: List<Entry>): Focus {
        val m = message.lowercase()
        val hasDigits = Regex("""\d""").containsMatchIn(m)
        val edit = editRx.containsMatchIn(m)
        val question = questionRx.containsMatchIn(m) || m.contains('?')
        val report = reportRx.containsMatchIn(m)
        val help = helpRx.containsMatchIn(m)
        val dupes = dupeRx.containsMatchIn(m)
        val story = m.length > 140

        // What the message is about: people, items / shops, a period. A short follow-up ("details batao", "dikhao",
        // "ye kahan se aye") with nothing of its own is about what the last messages were about.
        var subject = subjectOf(m, sorted)
        if (subject.isEmpty() && !(hasDigits && !question) && wordsOf(m).size <= 7) {
            for (prev in history.filter { it.isUser }.takeLast(3).reversed()) {
                val s = subjectOf(prev.text.lowercase(), sorted)
                if (!s.isEmpty()) { subject = s; break }
            }
        }
        val people = subject.people
        val personEntries = people.flatMap { it.second }.distinctBy { it.id }
        val itemEntries = subject.items
        val range = subject.range
        val periodEntries = subject.periodEntries
        val newMoney = hasDigits && !question && !edit
        val small = !hasDigits && !edit && !question && !report && !help && !dupes && !story &&
            people.isEmpty() && itemEntries.isEmpty() && range == null
        if (small) return Focus(true, false, false, false, false, false, false, false, emptyList())

        // Entries the recent chat talked about, so "that one" / "wo wali" can be found.
        val talked = history.takeLast(12).flatMap { it.entryIds }.toSet()
        val talkedEntries = sorted.filter { it.id in talked }.take(20)
        // With a clear subject (a person, item or period) only a few of the newest entries are added, so the
        // answer sticks to that subject.
        val newest = sorted.take(if (edit) 15 else if (!subject.isEmpty()) 3 else 8)
        val nowMs = System.currentTimeMillis()
        val day = 86_400_000L
        val odd = if (edit || dupes) sorted.filter { it.date > nowMs + day || it.date < nowMs - 300L * day }.take(15) else emptyList()
        val dupeScope = if (dupes) sorted.take(60) else emptyList()
        val chosen = (newest + personEntries + itemEntries + periodEntries.take(60) + talkedEntries + odd + dupeScope).distinctBy { it.id }
            .sortedWith(compareByDescending<Entry> { it.date }.thenByDescending { it.createdAt })
        // A question names nothing specific ("kitna kharcha hua?"): the exact figures answer it.
        val worked = buildString {
            people.forEach { (label, list) -> append(Accounts.personLedger(list, label, currencyNow)).append("\n\n") }
            if (itemEntries.isNotEmpty() && subject.itemLabel.isNotBlank()) append(Accounts.itemSummary(itemEntries, subject.itemLabel, currencyNow)).append("\n")
            if (range != null) append(Accounts.periodSummary(periodEntries, subject.rangeLabel, currencyNow)).append("\n")
        }.trim()
        return Focus(
            small = false, newMoney = newMoney, edit = edit, question = question || report || people.isNotEmpty() || range != null,
            report = report, story = story || (edit && people.isNotEmpty()), help = help, dupes = dupes, entries = chosen,
            worked = worked
        )
    }

    /**
     * The chat assistant. One call decides what the message is: money to record (returned as entries,
     * saved straight away) or anything else (a greeting, question, report, search or advice), answered briefly.
     */
    suspend fun assistant(
        message: String,
        history: List<ChatMessage>,
        all: List<Entry>,
        key: String,
        store: Store,
        currency: String,
        memory: List<String> = emptyList(),
        voice: Boolean = false
    ): AssistantResult = withContext(Dispatchers.IO) {
        if (key.isBlank()) throw Failure("AI is not configured.")
        val fastKey = store.fastKey.trim()
        val groq = isGroq(fastKey)
        val sorted = all.sortedWith(compareByDescending<Entry> { it.date }.thenByDescending { it.createdAt })
        // Only what this message needs goes to the AI: a greeting gets no data, "Imran ka khata" only Imran's entries,
        // "aaj kitna" only today's. Smaller requests answer faster and fit free-plan limits.
        currencyNow = currency
        val focus = focusOf(message, history, sorted)
        val recent = focus.entries
        val codeOf = recent.associate { e -> e.id to Accounts.code(e) }
        val today = SimpleDateFormat("yyyy-MM-dd (EEEE)", Locale.US).format(Date())
        val said = message.replace("\"", "'").take(2000)
        val memoryList = if (memory.isEmpty()) "(nothing yet)" else memory.joinToString("\n") { "- $it" }
        val speechField = if (voice) ",\"speech\":\"\"" else ""
        val speechRule = if (voice) "\"speech\": the reply as you would SAY it in a phone call: 1 to 3 short natural sentences, no lists, no symbols, numbers as\ndigits. If they write or speak Urdu or Roman Urdu, write \"speech\" in Urdu script (اردو) so an Urdu voice can read it; otherwise English.\n" else ""
        val prompt = if (focus.small) {
            val chat = history.takeLast(4).joinToString("\n") { m -> (if (m.isUser) "User: " else "Assistant: ") + m.text.take(200) }
                .ifBlank { "(new conversation)" }
            """
You are the friendly assistant inside Smart Expense, a money diary app. Today is $today.
Reply in 1 or 2 short lines, in the same language and script as the person (Roman Urdu if they write Roman Urdu). Plain text.
If they seem to want something about their money, tell them they can just ask, e.g. "aaj kitna kharcha hua" or "chai 5".
What you know about them: $memoryList

Previous conversation:
$chat

Latest message: "$said"

Return ONLY JSON: {"reply":""$speechField}
$speechRule""".trim()
        } else {
            val conversation = history.takeLast(if (groq) 6 else 8).joinToString("\n") { m ->
                val refs = m.entryIds.mapNotNull { codeOf[it] }
                (if (m.isUser) "User: " else "Assistant: ") + m.text.take(if (groq) 300 else 400) +
                    (if (refs.isNotEmpty()) " [these entries: id=" + refs.joinToString(", ") + "]" else "")
            }.ifBlank { "(new conversation)" }
            val sb = StringBuilder()
            sb.append("""
You are the assistant inside Smart Expense, a money diary app. Today is $today. Currency is $currency.
The person writes English, Roman Urdu, Urdu, Hindi or Arabic, often mixed.

Decide what their latest message is:
A) It records NEW money that moved (spent, earned, lent, borrowed, returned, sold, bought). Put it in "entries" using the entry rules below,
   and set "reply" to one short line confirming it, e.g. "Saved: breakfast 7 $currency".
B) It corrects, moves or removes money that is ALREADY in the saved entries: a different day ("aaj nahi kal tha", "that was yesterday"),
   a different amount, category, name or note, or "delete / hata do / galat hai". Do NOT add a new entry for it.
   Put the change in "edits" (the entry's id, only the fields that change; "date" as YYYY-MM-DD for a new day)
   or its id in "delete". Changes are applied straight away. Look at the previous conversation to know which entry they mean.
   The reply says exactly what changed.
C) Anything else: a greeting, a question, a report, a search, advice. Answer in "reply" with no entries, edits or deletes.
   A time can be set with "time" ("HH:MM"); "a bit before / after" another saved entry means a few minutes from that entry's time.
   If the same money was saved twice by mistake (same item and amount on two days, and they say it happened once), keep the one on
   the right day and put the other in "delete".
If it is not clear which saved entry they mean, ask one short question and change nothing. But when they say "you check" /
"tum check kar lo" / "jo date usme hai", decide yourself from the data (dates in notes, the conversation, the entry list) and act;
don't ask the same question twice.
"[these entries: id=...]" after a message in the conversation tells you exactly which saved entries that message added.
Never show ids or "n=" to the person; describe entries by date, item and amount.
""")
            if (focus.dupes) sb.append("""For "find duplicates" list the pairs from "Possible duplicates" that really look the same (item, note, person), and ask whether to
delete the extra ones; when they agree, put those ids in "delete".
""")
            sb.append("""Never say you added, changed or deleted something unless it is in "entries", "edits" or "delete" of THIS answer.
If you could not do it, say so plainly and what you need.

""")
            if (focus.edit || focus.story || focus.newMoney) sb.append("""Worked examples (follow the same pattern):
1. "chai 5 aur sandwich 12" -> entries: expense 5 food Chai, expense 12 food Sandwich. reply "Saved: chai 5, sandwich 12 $currency".
2. "kal wala petrol 60 nahi 65 tha" -> edits: [{"id":"<id of yesterday's Petrol 60>","amount":65}]. reply "Petrol on <date> changed from 60 to 65".
3. "Shehryar ne 4 riyal bheje, wo usi din ke lunch mein uska hissa tha; lunch se minus karo aur Shehryar wali delete karo"
   -> find the Shehryar income/transfer of 4 and the lunch/food expense on the SAME day (say 24).
   edits: [{"id":"<lunch id>","amount":20}], delete: ["<Shehryar 4 id>"]. reply "Lunch on <date> is now 20 (24 - 4), Shehryar's 4 removed".
4. "Ali ko 100 diye" -> entries: gave 100, person "Ali". reply "Saved: gave Ali 100 $currency".
5. "is mahine khane pe kitna" -> no changes; reply with the food figure from EXACT FIGURES.
When a message asks for two changes, do both in the same answer.

""")
            if (focus.story) sb.append("""Long stories about money with a person (who paid what for whom, what was kept, what was sent back):
- Work it out in "work": list every amount, who it belongs to, and the running balance with that person.
- Then fix the ledger to match: wrong kinds become loans with that person (gave / took / got_back / paid_back),
  things they paid for you become expenses (with the right category) plus "took" from that person, and so on.
- Put new money in "entries" and changes to saved entries in "edits" / "delete", all in one answer, and say in "reply"
  the final balance with that person ("Imran owes you X" or "you owe Imran X") and what you changed.
- Never ask the person to repeat details they already gave in the conversation; use them.

""")
            if (focus.question) sb.append("""Getting numbers right (most important):
- For any total, average, comparison or balance, take the number from EXACT FIGURES when it is there. Don't re-add entries.
- If you must add entries yourself (a custom filter like "petrol in September"), first write the matching entries with their
  amounts in "work", add them step by step there, and only then give the result in "reply". Count every matching line.
- Say which period a number is for ("this month so far", "last week"). Show amounts with $currency.
- If the data can't answer it (no entries for that), say so; never guess a figure.

""")
            sb.append("""How to reply:
- Short and to the point. Lead with the answer. Usually 1 to 4 short lines.
- Reports and summaries: short lines like "- Food: 144 $currency", then one line with the total or main point.
- Searches ("petrol kab dala", "Ahmed ke sath kya hua"): list matching entries as "- 23 Oct · Petrol · 60", at most 10, newest first.
- When they ask how to save or what to do, give 2 or 3 practical tips based on their own numbers.
- Reply in the same language and script as their latest message (Roman Urdu if they write Roman Urdu).
- Plain text only, no markdown headings or bold. Use "-" for lists, each item on its own line, never inside brackets.
- "aaj" / "today" means today only, "kal" yesterday, "is mahine" this month. Answer for exactly the period asked, and list what was spent on when they ask "kahan" / "where".
- Use only the saved data for personal facts. Never invent entries or amounts. If the data does not have it, say so in one line.
- Questions about things outside money: answer briefly and helpfully, then they can carry on.
""")
            if (focus.help) sb.append("""- App help, when they ask how to do something: the paperclip next to the message box imports a statement, receipt or bill (photo or PDF); the Overview tab shows totals and days; Reports in the menu show Cash Out, Cash In,
  Sales, Purchases, Stock, Profit, Cash Balance and Dues; tapping an entry edits it; "last entry was 400 not 500" changes an entry.

""")
            if (focus.newMoney || focus.edit || focus.story) {
                sb.append("\nEntry rules:\n").append(entryRules())
                sb.append("\nEach entry: {\"type\":\"\",\"amount\":0,\"category\":\"\",\"item\":\"\",\"qty\":0,\"unit\":\"\",\"rate\":0,\"person\":\"\",\"date\":\"YYYY-MM-DD\",\"note\":\"\"}\n")
            }
            if (focus.worked.isNotBlank()) sb.append("\n").append(focus.worked).append("\n")
            sb.append("\n").append(ledgerContext(all, recent, currency, compact = groq || !focus.report, facts = focus.question && focus.worked.isBlank() || focus.report, withDupes = focus.dupes))
            sb.append("\n\nWhat you remember about this person from earlier chats (use it; it may be out of date if they say otherwise):\n")
                .append(memoryList).append("\n\n")
            sb.append("""Memory: when the person tells you something lasting about themselves or their money — who someone is ("Shahbaz is my brother"),
a regular payment ("mess 170 every month"), a habit, what a shop is ("Aldrees is petrol"), how they want answers — put a short
fact in "remember" (max 15 words, English). When they correct or cancel a remembered fact, put its exact old text in "forget".
Don't remember single transactions, one-off questions or anything already in memory. If they ask what you remember, list it.

""")
            sb.append("Previous conversation:\n").append(conversation).append("\n\nLatest message: \"").append(said).append("\"\n\n")
            sb.append("""Return ONLY JSON: {"work":"","reply":""${if (voice) ",\"speech\":\"\"" else ""},"entries":[],"edits":[{"id":"","type":"","amount":0,"category":"","item":"","qty":0,"person":"","date":"YYYY-MM-DD","time":"","note":""}],"delete":[],"remember":[],"forget":[]}
${if (voice) "\"speech\": the reply as you would SAY it in a phone call: 1 to 3 short natural sentences, no lists, no symbols, numbers as\ndigits. If they write or speak Urdu or Roman Urdu, write \"speech\" in Urdu script (اردو) so an Urdu voice can read it; otherwise English.\n" else ""}("work" is your private scratch space: keep it short, only for working out numbers; the person never sees it. edits and delete are [] unless case B.)
""")
            sb.toString().trim()
        }
        val knownCodes = recent.map { Accounts.code(it) }.toSet()
        val trace = StringBuilder()
        fun ask(strong: Boolean): JSONObject {
            val models = if (strong) STRONG + MODELS else MODELS
            return try {
                trace.setLength(0)
                JSONObject(request(prompt, key.trim(), store, prefer = if (strong) STRONG.first() else CHAT_MODEL, models = models, trace = trace))
            } catch (e: Failure) {
                throw e
            } catch (e: Exception) {
                throw Failure("AI returned an unreadable answer.")
            }
        }
        /** Signs the fast answer is wrong: ids that don't exist, or a reply that claims a change it didn't make. */
        fun doubtful(j: JSONObject): Boolean {
            val ids = mutableListOf<String>()
            j.optJSONArray("edits")?.let { a -> for (i in 0 until a.length()) a.optJSONObject(i)?.optString("id")?.let { ids += it.trim() } }
            j.optJSONArray("delete")?.let { a -> for (i in 0 until a.length()) ids += a.optString(i).trim() }
            if (ids.any { it.isNotEmpty() && it !in knownCodes }) return true
            val nothing = ids.isEmpty() && (j.optJSONArray("entries")?.length() ?: 0) == 0
            val claims = Regex("""(?i)\b(changed|deleted|removed|updated|moved|kar diya|kar di|hata diya|hata di)\b""")
            return nothing && claims.containsMatchIn(j.optString("reply"))
        }
        // Simple messages go to the fast model. Long ones and corrections go to the stronger model straight away,
        // and a fast answer that doesn't check out is asked again from the stronger model.
        // A fast key (Groq / OpenRouter) answers everything in a second or two with a strong model; Gemini is the backup.
        fastError = ""
        val viaFast = if (fastKey.isNotBlank()) runCatching {
            trace.setLength(0)
            JSONObject(fastCall(prompt, fastKey, trace))
        }.onFailure { e -> fastError = e.message ?: "" }.getOrNull() else null
        val strongFirst = needsStrong(message)
        var json = viaFast ?: try { ask(strongFirst) } catch (e: Failure) { if (strongFirst) throw e else ask(true) }
        if (viaFast == null && fastKey.isNotBlank() && fastError.isNotBlank()) trace.append(" · fast AI failed: " + fastError.take(60))
        if (viaFast == null && !strongFirst && doubtful(json)) {
            json = runCatching { ask(true) }.getOrNull() ?: json
        }
        val list = toEntries(json.toString(), message)
        val reply = json.optString("reply").trim()
        val plan = planFrom(json, recent, reply.ifBlank { "Updated." })
        fun strings(name: String): List<String> = json.optJSONArray(name)?.let { a ->
            (0 until a.length()).map { a.optString(it).trim() }.filter { it.isNotEmpty() }
        } ?: emptyList()
        val remember = strings("remember").map { it.take(120) }
        val forget = strings("forget")
        if (reply.isBlank() && list.isEmpty() && plan.changed.isEmpty() && plan.deleted.isEmpty() && remember.isEmpty()) {
            throw Failure("AI returned an empty answer.")
        }
        AssistantResult(
            reply, list, plan.takeIf { it.changed.isNotEmpty() || it.deleted.isNotEmpty() }, remember, forget,
            speech = json.optString("speech").trim(),
            trace = trace.toString()
        )
    }

    class AssistantResult(
        val reply: String,
        val entries: List<Entry>,
        val plan: EditPlan?,
        val remember: List<String> = emptyList(),
        val forget: List<String> = emptyList(),
        /** The reply as it should be spoken aloud in voice mode. */
        val speech: String = "",
        /** Which model answered and how long it took, shown small under the answer. */
        val trace: String = ""
    )

    /**
     * Reads a bank statement, receipt or bill (photo or PDF) and returns every transaction in it as entries.
     * Nothing is saved here; the person reviews them first.
     */
    suspend fun readDocument(
        bytes: ByteArray,
        mime: String,
        note: String,
        key: String,
        store: Store,
        currency: String
    ): List<Entry> = withContext(Dispatchers.IO) {
        if (key.isBlank()) throw Failure("Reading statements needs AI. Turn it on in Settings.")
        fixDates(readOne(bytes, mime, note, key, store, currency))
    }

    class PagesResult(val entries: List<Entry>, val failedPages: List<Int>)

    /**
     * Reads a statement page by page, several pages at the same time, so a long statement takes about as long
     * as one page. A page that fails is tried once more; pages that still fail are reported, the rest kept.
     */
    suspend fun readPages(
        pages: List<ByteArray>,
        note: String,
        key: String,
        store: Store,
        currency: String,
        onPage: suspend (done: Int, total: Int) -> Unit
    ): PagesResult = kotlinx.coroutines.coroutineScope {
        if (key.isBlank()) throw Failure("Reading statements needs AI. Turn it on in Settings.")
        val gate = kotlinx.coroutines.sync.Semaphore(4)
        val done = java.util.concurrent.atomic.AtomicInteger(0)
        val results = pages.map { page ->
            async(Dispatchers.IO) {
                gate.acquire()
                try {
                    val r = runCatching { readOne(page, "image/jpeg", note, key, store, currency) }
                        .recoverCatching { readOne(page, "image/jpeg", note, key, store, currency) }
                    onPage(done.incrementAndGet(), pages.size)
                    r
                } finally {
                    gate.release()
                }
            }
        }.awaitAll()
        val failed = results.withIndex().filter { it.value.isFailure }.map { it.index + 1 }
        if (failed.size == pages.size) {
            throw results.first().exceptionOrNull() ?: Failure("Could not read this file.")
        }
        PagesResult(fixDates(results.mapNotNull { it.getOrNull() }.flatten()), failed)
    }

    private fun readOne(bytes: ByteArray, mime: String, note: String, key: String, store: Store, currency: String): List<Entry> {
        val data = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
        val today = SimpleDateFormat("yyyy-MM-dd (EEEE)", Locale.US).format(Date())
        val prompt = """
The attached file is a bank statement (or one page of it), card statement, receipt or bill. Today is $today.
A page with no transactions (a cover or summary page) gives {"rows":[]}. The person's currency is $currency.
Turn every money movement in it into a ledger entry, using the date printed for each line.
Dates: short dates are ambiguous. "25/10/26" and "26/10/24" can be day/month/year or year/month/day, even inside the same file.
Choose the reading that gives a date on or before today and closest to today (the newest possible), never a date a year or more
back unless a 4-digit year says so. Labels a phone shows next to a message ("Today", "Yesterday", a weekday) win over the printed date.
- Money taken from the account or paid (purchase, POS, mada, Apple Pay, ATM, transfer out, bill, fee) is "expense",
  unless it is clearly a loan to a named person ("gave") or money returned to someone ("paid_back").
- Money that came in (salary, deposit, refund, transfer in) is "income", unless it is clearly a named person repaying ("got_back").
- A receipt or bill with several items is ONE expense for its total, with the shop name as item.
- Transfers to or from a person (IPS, SARIE, internal transfer, "TO<NAME>", "/NAME" in the notes): item "Transfer",
  note "To <Name>" or "From <Name>" with the person's name in normal capitals (e.g. "To Waleed Ali Ahmed", "From Zubair").
  Never write only "Credit Transfer" when a name is shown.
- Bank charges for a transfer: expense, category fees, item "Transfer fee", note "Fee: transfer to <Name>".
- Shops and card purchases: item is the shop's clean name (e.g. "Sasco", "Express Food"), note the shop and city.
- time: the time printed for the line as "HH:MM" (24-hour), else "".
- Skip opening and closing balances, running balances, totals, and declined or reversed lines.
- note: the merchant or a short clean description, max 6 words.
${if (note.isNotBlank()) "The person said about this file: \"" + note.replace("\"", "'").take(300) + "\"" else ""}

Entry rules:
${entryRules()}

Return ONLY JSON, one short object per transaction with these short keys (statements can have hundreds, so keep it compact):
{"rows":[{"t":"type","a":0,"c":"category","i":"item","p":"person","d":"YYYY-MM-DD","h":"HH:MM","n":"note"}]}
Every row MUST have "d", the transaction's own date from the file. Example:
{"rows":[{"t":"expense","a":19,"c":"food","i":"Express Food","p":"","d":"2026-10-25","h":"13:40","n":"Express Food Jeddah"}]}
""".trim()
        val json = try {
            rowsToEntries(request(prompt, key.trim(), store, mime to data, prefer = FILE_MODEL))
        } catch (e: Failure) {
            throw e
        } catch (e: Exception) {
            throw Failure("Could not read this file. Try a clearer photo or another PDF.")
        }
        return try {
            toEntries(json, note)
        } catch (e: Exception) {
            throw Failure("Could not read this file. Try a clearer photo or another PDF.")
        }
    }

    /** Turns the compact rows of a statement into the usual {"entries":[...]} JSON. */
    private fun rowsToEntries(text: String): String {
        val obj = JSONObject(text)
        if (obj.has("entries")) return text
        val rows = obj.optJSONArray("rows") ?: return text
        val entries = JSONArray()
        val dateRx = Regex("""^\d{4}-\d{1,2}-\d{1,2}""")
        val timeRx = Regex("""^\d{1,2}:\d{2}""")
        for (i in 0 until rows.length()) {
            val o = rows.optJSONObject(i)
            if (o != null) {
                entries.put(
                    JSONObject()
                        .put("type", o.optString("t", o.optString("type")))
                        .put("amount", o.optDouble("a", o.optDouble("amount", 0.0)))
                        .put("category", o.optString("c", o.optString("category")))
                        .put("item", o.optString("i", o.optString("item")))
                        .put("person", o.optString("p", o.optString("person")))
                        .put("date", o.optString("d", o.optString("date")))
                        .put("time", o.optString("h", o.optString("time")))
                        .put("note", o.optString("n", o.optString("note")))
                )
                continue
            }
            // A plain list: find each value by what it looks like, not by its position.
            val r = rows.optJSONArray(i) ?: continue
            val values = (0 until r.length()).map { r.opt(it) }
            val strings = values.filterIsInstance<String>().map { it.trim() }
            val date = strings.firstOrNull { dateRx.containsMatchIn(it) }.orEmpty()
            val time = strings.firstOrNull { timeRx.matches(it) }.orEmpty()
            val amount = values.firstOrNull { it is Number }?.let { (it as Number).toDouble() }
                ?: strings.firstNotNullOfOrNull { it.replace(",", "").toDoubleOrNull() } ?: 0.0
            val words = strings.filter { it.isNotBlank() && it != date && it != time && it.replace(",", "").toDoubleOrNull() == null }
            entries.put(
                JSONObject()
                    .put("type", words.getOrElse(0) { "" })
                    .put("amount", amount)
                    .put("category", words.getOrElse(1) { "" })
                    .put("item", words.getOrElse(2) { "" })
                    .put("date", date)
                    .put("time", time)
                    .put("note", words.drop(3).lastOrNull().orEmpty())
            )
        }
        // Never put a whole statement on today's date because its dates went missing.
        val noDate = (0 until entries.length()).count { !dateRx.containsMatchIn(entries.getJSONObject(it).optString("date")) }
        if (entries.length() > 2 && noDate * 2 > entries.length()) {
            throw Failure("I couldn't read the dates in this file. Please send it again.")
        }
        return JSONObject().put("entries", entries).toString()
    }

    /**
     * Second check on dates read from a file, using the other lines as context. A date far from the rest
     * (more than 45 days from the middle date) or in the future is probably a misread short date, so the
     * other readings are tried: day and year swapped ("26/10/24" as 2026-10-24 instead of 2024-10-26), or
     * the same day and month in the year the other lines use. The reading closest to the rest, and not in
     * the future, is kept. The time of day stays as it was.
     */
    fun fixDates(list: List<Entry>, now: Long = System.currentTimeMillis()): List<Entry> {
        if (list.isEmpty()) return list
        val day = 86_400_000L
        // Files are usually recent: the middle of the lines from the last 4 months is the reference;
        // if none are that recent, the middle of all of them.
        val recentDates = list.map { it.date }.filter { it in (now - 120 * day)..(now + day) }.sorted()
        val allDates = list.map { it.date }.sorted()
        val anchor = when {
            recentDates.isNotEmpty() -> recentDates[recentDates.size / 2]
            allDates.size >= 3 -> allDates[allDates.size / 2]
            else -> now
        }
        val anchorYear = Calendar.getInstance().apply { timeInMillis = anchor }.get(Calendar.YEAR)
        fun far(ms: Long) = kotlin.math.abs(ms - anchor) > 45 * day || ms > now + day
        return list.map { e ->
            if (!far(e.date)) return@map e
            val c = Calendar.getInstance().apply { timeInMillis = e.date }
            val y = c.get(Calendar.YEAR)
            val m = c.get(Calendar.MONTH)
            val d = c.get(Calendar.DAY_OF_MONTH)
            fun at(year: Int, month: Int, dom: Int): Long? = try {
                Calendar.getInstance().apply {
                    isLenient = false
                    timeInMillis = e.date
                    set(Calendar.YEAR, year)
                    set(Calendar.MONTH, month)
                    set(Calendar.DAY_OF_MONTH, dom)
                }.timeInMillis
            } catch (x: Exception) {
                null
            }
            val options = listOfNotNull(
                at(2000 + d, m, y % 100),          // year and day swapped
                at(anchorYear, m, d),              // same day and month, in the year of the other lines
                at(anchorYear - 1, m, d)
            ).filter { it <= now + day }
            val best = (options + e.date).filter { it <= now + day }.minByOrNull { kotlin.math.abs(it - anchor) } ?: return@map e
            if (best == e.date) e else e.copy(date = best, createdAt = e.createdAt - e.date + best)
        }
    }

    /** Answers questions about the saved khata without changing any entries. */
    suspend fun chat(
        question: String,
        history: List<ChatMessage>,
        all: List<Entry>,
        key: String,
        store: Store,
        currency: String
    ): String = withContext(Dispatchers.IO) {
        if (key.isBlank()) throw Failure("AI is not configured. Add a Gemini API key in Settings.")

        val today = SimpleDateFormat("yyyy-MM-dd (EEEE)", Locale.US).format(Date())
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val recent = all.sortedWith(compareByDescending<Entry> { it.date }.thenByDescending { it.createdAt }).take(80)
        val recentJson = JSONArray()
        recent.forEach { entry ->
            recentJson.put(
                JSONObject()
                    .put("date", dateFormat.format(Date(entry.date)))
                    .put("type", entry.type.label)
                    .put("amount", entry.amount)
                    .put("category", entry.category.label)
                    .put("item", entry.item)
                    .put("person", entry.person)
                    .put("note", entry.note)
                    .put("qty", entry.qty)
                    .put("unit", entry.unit)
                    .put("paid", entry.paid)
            )
        }

        val currentMonth = Calendar.getInstance()
        fun isCurrentMonth(entry: Entry): Boolean {
            val date = Calendar.getInstance().apply { timeInMillis = entry.date }
            return date.get(Calendar.YEAR) == currentMonth.get(Calendar.YEAR) &&
                date.get(Calendar.MONTH) == currentMonth.get(Calendar.MONTH)
        }
        val allExpenses = all.filter { it.type == Type.KHARCHA }.sumOf { it.amount }
        val allIncome = all.filter { it.type == Type.AAMDANI }.sumOf { it.amount }
        val monthExpenses = all.filter { it.type == Type.KHARCHA && isCurrentMonth(it) }.sumOf { it.amount }
        val monthIncome = all.filter { it.type == Type.AAMDANI && isCurrentMonth(it) }.sumOf { it.amount }
        val lent = all.filter { it.type == Type.DIYA }.sumOf { it.amount }
        val receivedBack = all.filter { it.type == Type.WAPIS_MILA }.sumOf { it.amount }
        val borrowed = all.filter { it.type == Type.LIYA }.sumOf { it.amount }
        val paidBack = all.filter { it.type == Type.WAPIS_DIYA }.sumOf { it.amount }
        val personBalances = balances(all).entries
            .sortedByDescending { kotlin.math.abs(it.value) }
            .joinToString("; ") { entry ->
                entry.key + ": " + (if (entry.value >= 0) "owes you " else "you owe ") +
                    fmt(kotlin.math.abs(entry.value)) + " " + currency
            }.ifBlank { "No outstanding balances." }
        val summary = listOf(
            "Saved entries: " + all.size,
            "All-time expenses: " + fmt(allExpenses) + " " + currency,
            "All-time income: " + fmt(allIncome) + " " + currency,
            "This month's expenses: " + fmt(monthExpenses) + " " + currency,
            "This month's income: " + fmt(monthIncome) + " " + currency,
            "Total lent entries: " + fmt(lent) + " " + currency,
            "Received back entries: " + fmt(receivedBack) + " " + currency,
            "Total borrowed entries: " + fmt(borrowed) + " " + currency,
            "Paid back entries: " + fmt(paidBack) + " " + currency,
            "Current balances (positive means they owe you): " + personBalances
        ).joinToString("\n")
        val conversation = history.takeLast(12).joinToString("\n") { message ->
            (if (message.isUser) "User: " else "Assistant: ") + message.text.take(1000)
        }.ifBlank { "(new conversation)" }
        val safeQuestion = question.replace("\"", "'").take(3000)
        val prompt = """
You are the friendly assistant inside BolKhata, a personal expense and khata app.
Today is $today. Currency is $currency.
The person may write English, Roman Urdu, Urdu, Hindi or Arabic. Reply in the same language as their latest question, in a helpful and concise style.
Use only the saved data below for personal financial facts. If it does not contain enough information, say so clearly. Never invent transactions or amounts.
This chat is read-only: do not claim to add, edit or delete entries. If asked to change the ledger, explain that they can use the app's entry input or edit controls.
You may explain calculations, summarize spending, compare periods when the data supports it, and answer questions about people, categories and saved transactions.

Saved data summary:
$summary

Recent saved entries, newest first (up to 80):
$recentJson

Previous conversation:
$conversation

Latest user question: "$safeQuestion"

Return ONLY valid JSON in this exact shape: {"reply":"your answer here"}
""".trimIndent()
        val answer = try {
            JSONObject(request(prompt, key.trim(), store)).optString("reply").trim()
        } catch (e: Failure) {
            throw e
        } catch (e: Exception) {
            throw Failure("AI returned an unreadable answer. Please try again.")
        }
        answer.ifBlank { throw Failure("AI returned an empty answer. Please try again.") }
    }

    suspend fun parse(text: String, key: String, store: Store, currency: String): List<Entry> = withContext(Dispatchers.IO) {
        val prompt = buildPrompt(text, currency)
        val fast = store.fastKey.trim()
        val viaFast = if (fast.isNotBlank()) runCatching { fastCall(prompt, fast, null) }.getOrNull() else null
        toEntries(viaFast ?: request(prompt, key.trim(), store), text)
    }

    /** A change the AI proposes; nothing is applied until the person taps Apply. */
    class EditPlan(val changed: List<Pair<Entry, Entry>>, val deleted: List<Entry>, val message: String)

    private val typeNames = mapOf(
        Type.KHARCHA to "expense", Type.AAMDANI to "income", Type.DIYA to "gave", Type.LIYA to "took",
        Type.WAPIS_MILA to "got_back", Type.WAPIS_DIYA to "paid_back", Type.SALE to "sale", Type.PURCHASE to "purchase"
    )
    private val catNames = mapOf(
        Cat.KHANA to "food", Cat.TRANSPORT to "transport", Cat.RENT to "rent", Cat.BIJLI to "bills",
        Cat.MOBILE to "mobile", Cat.GHAR to "home", Cat.SHOPPING to "shopping", Cat.SEHAT to "health",
        Cat.PERSONAL to "personal", Cat.ASSETS to "assets", Cat.FEES to "fees", Cat.EDU to "education", Cat.CHARITY to "charity",
        Cat.TANKHWAH to "income", Cat.UDHAAR to "loans", Cat.SALES to "sales", Cat.STOCK to "purchases", Cat.OTHER to "other"
    )

    /** Asks Gemini which existing entries to change for an instruction like "hamza wali 500 ko 400 kar do". */
    suspend fun edit(instruction: String, all: List<Entry>, key: String, store: Store, currency: String): EditPlan =
        withContext(Dispatchers.IO) {
            val recent = all.sortedWith(compareByDescending<Entry> { it.date }.thenByDescending { it.createdAt }).take(80)
            val day = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            val list = JSONArray()
            recent.forEachIndexed { i, e ->
                list.put(
                    JSONObject().put("n", i + 1).put("date", day.format(Date(e.date)))
                        .put("type", typeNames[e.type]).put("amount", e.amount)
                        .put("category", catNames[e.category]).put("item", e.item)
                        .put("person", e.person).put("note", e.note).put("qty", e.qty)
                )
            }
            val today = SimpleDateFormat("yyyy-MM-dd (EEEE)", Locale.US).format(Date())
            val prompt = """
You edit a personal money ledger. Today is $today. Amounts are in $currency.
The person may write English, Roman Urdu, Urdu, Hindi or Arabic.
Recent entries (n = number, newest first):
$list

Instruction: "${instruction.replace("\"", "'")}"

Find the entries the instruction is about and say what to change. Change only what was asked.
Return ONLY JSON:
{"edits":[{"n":1,"type":"","amount":0,"category":"","item":"","qty":0,"person":"","date":"YYYY-MM-DD"}],"delete":[n,...],"message":"one short English sentence describing the change"}
In edits include only fields that change. type: expense, income, gave, took, got_back, paid_back, sale, purchase.
category: food, transport, rent, bills, mobile, home, shopping, health, personal, assets, fees, education, charity, other.
If nothing matches, return {"edits":[],"delete":[],"message":"I couldn't find that entry."}
""".trim()
            val json = JSONObject(request(prompt, key, store))
            planFrom(json, recent, json.optString("message").ifBlank { "Here is the change." })
        }

    /** Turns {"edits":[{"n":..}], "delete":[n]} into changes, n counting from 1 in [recent]. */
    private fun planFrom(json: JSONObject, recent: List<Entry>, message: String): EditPlan {
            val changed = mutableListOf<Pair<Entry, Entry>>()
        val edits = json.optJSONArray("edits") ?: JSONArray()
        for (i in 0 until edits.length()) {
            val o = edits.optJSONObject(i) ?: continue
            val byCode = o.optString("id").trim().takeIf { it.isNotEmpty() }?.let { c -> recent.firstOrNull { Accounts.code(it) == c } }
            val old = byCode ?: recent.getOrNull(o.optInt("n", 0) - 1) ?: continue
            var e = old
            if (o.has("type")) typeNames.entries.firstOrNull { it.value == o.optString("type") }?.let { e = e.copy(type = it.key) }
            if (o.has("amount")) o.optDouble("amount").takeIf { !it.isNaN() && it > 0 }?.let { e = e.copy(amount = it) }
            if (o.has("category")) catNames.entries.firstOrNull { it.value == o.optString("category") && it.key != Cat.UDHAAR && it.key != Cat.TANKHWAH }
                ?.let { e = e.copy(category = it.key, item = if (o.has("item")) e.item else it.key.label) }
            if (o.has("item")) o.optString("item").trim().takeIf { it.isNotEmpty() }?.let { e = e.copy(item = it) }
            // Empty fields mean "no change".
            o.optString("person").trim().takeIf { it.isNotEmpty() }?.let { v -> e = e.copy(person = v.replaceFirstChar { it.uppercase() }) }
            // A new day keeps the entry's time of day.
            if (o.has("date")) parseDate(o.optString("date"))?.let { d ->
                val keep = Calendar.getInstance().apply { timeInMillis = e.date }
                e = e.copy(date = Calendar.getInstance().apply {
                    timeInMillis = d
                    set(Calendar.HOUR_OF_DAY, keep.get(Calendar.HOUR_OF_DAY))
                    set(Calendar.MINUTE, keep.get(Calendar.MINUTE))
                }.timeInMillis)
            }
            if (o.has("qty")) o.optDouble("qty").takeIf { !it.isNaN() && it > 0 }?.let { e = e.copy(qty = it) }
        if (o.has("note")) o.optString("note").trim().takeIf { it.isNotEmpty() }?.let { e = e.copy(note = it.take(60)) }
        Regex("""^(\d{1,2}):(\d{2})""").find(o.optString("time").trim())?.let { m ->
            val at = Calendar.getInstance().apply {
                timeInMillis = e.date
                set(Calendar.HOUR_OF_DAY, m.groupValues[1].toInt().coerceIn(0, 23))
                set(Calendar.MINUTE, m.groupValues[2].toInt().coerceIn(0, 59))
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
            e = e.copy(date = at, createdAt = at)
        }
            // keep category consistent with the type
            e = when {
                e.type == Type.SALE -> e.copy(category = Cat.SALES)
                e.type == Type.PURCHASE -> e.copy(category = Cat.STOCK)
                isLoan(e.type) -> e.copy(category = Cat.UDHAAR, item = e.item.ifBlank { "Loans" })
                e.type == Type.AAMDANI && e.category == Cat.ASSETS -> e.copy(person = "")
                e.type == Type.AAMDANI -> e.copy(category = Cat.TANKHWAH, person = "")
                e.category == Cat.UDHAAR || e.category == Cat.TANKHWAH || e.category == Cat.SALES || e.category == Cat.STOCK ->
                    e.copy(category = Cat.OTHER, item = "Other", person = "")
                else -> e.copy(person = "")
            }
            if (isLoan(e.type) && e.person.isBlank()) continue
            if (e != old) changed += old to e
        }
        val del = json.optJSONArray("delete") ?: JSONArray()
        val deleted = (0 until del.length()).mapNotNull { i ->
            val v = del.opt(i)
            if (v is String && v.toIntOrNull() == null) recent.firstOrNull { Accounts.code(it) == v.trim() }
            else recent.getOrNull((v?.toString()?.toIntOrNull() ?: 0) - 1)
        }.distinctBy { it.id }
            .filter { d -> changed.none { it.first.id == d.id } }
        return EditPlan(changed, deleted, message)
    }


    /** Sends [prompt] to the first model that answers and returns its JSON text. */
    /** [file] is an attached image or PDF: its media type and base64 data. */
    private fun request(
        prompt: String, key: String, store: Store, file: Pair<String, String>? = null, prefer: String? = null,
        models: List<String> = MODELS,
        trace: StringBuilder? = null
    ): String {
        val started = System.currentTimeMillis()
        val skipped = mutableListOf<String>()
        fun secs(from: Long) = String.format(Locale.US, "%.1fs", (System.currentTimeMillis() - from) / 1000.0)
        val first = prefer ?: store.aiModel.takeIf { it in models }
        val all = listOfNotNull(first) + models.filter { it != first }
        // Skip models that just failed, unless every model is resting.
        val now = System.currentTimeMillis()
        val order = all.filter { (resting[it] ?: 0L) < now }.ifEmpty { all }
        var lastError = "AI did not answer"
        // If one model is busy, over its free limit, not allowed for this key, or too slow, the next one is tried,
        // so the chat keeps working. Only a key that is really invalid stops right away.
        for (model in order) {
            val t0 = System.currentTimeMillis()
            fun rest(seconds: Int) { resting[model] = System.currentTimeMillis() + seconds * 1000L }
            var result = try {
                call(model, key, prompt, file, think = model !in plain)
            } catch (e: Failure) {
                lastError = e.message ?: lastError
                skipped += shortName(model) + " no answer " + secs(t0)
                rest(120)
                continue
            }
            // A model that refuses the speed settings is asked again with a plain request, and remembered.
            if (result.first == 400 && model !in plain && !errorMessage(result.second).contains("API key", ignoreCase = true)) {
                val retry = try { call(model, key, prompt, file, think = false) } catch (e: Failure) { null }
                if (retry != null && retry.first == 200) plain += model
                if (retry != null) result = retry
            }
            val (code, body) = result
            if (code != 200) skipped += shortName(model) + " " + (if (code == 429) "busy" else "error $code") + " " + secs(t0)
            when (code) {
                200 -> {
                    if (prefer == null && model in MODELS) store.aiModel = model
                    resting.remove(model)
                    val text = try { readText(body) } catch (e: CutOff) {
                        lastError = "The file is too long for one go. Send fewer pages (for example one month) at a time."
                        continue
                    } catch (e: Exception) { lastError = "AI sent an empty answer"; continue }
                    if (text.isBlank()) { lastError = "AI sent an empty answer"; continue }
                    val tm = timing.get()!!
                    val usage = runCatching { JSONObject(body).optJSONObject("usageMetadata") }.getOrNull()
                    fun k(n: Int) = if (n >= 1000) String.format(Locale.US, "%.1fk", n / 1000.0) else n.toString()
                    val tokens = usage?.let {
                        " · in " + k(it.optInt("promptTokenCount")) + ", out " + k(it.optInt("candidatesTokenCount")) +
                            (it.optInt("thoughtsTokenCount").takeIf { n -> n > 0 }?.let { n -> ", think " + k(n) } ?: "")
                    } ?: ""
                    trace?.append(
                        shortName(model) + " · " + secs(started) +
                            " (connect " + tm.connect + "ms, AI " + String.format(Locale.US, "%.1fs", tm.wait / 1000.0) + tokens +
                            (if (model in plain) ", plain" else "") + ")" +
                            (if (skipped.isNotEmpty()) " · skipped: " + skipped.joinToString(", ") else "")
                    )
                    return text
                }
                400, 401, 403 -> {
                    val msg = errorMessage(body)
                    if (msg.contains("API key not valid", ignoreCase = true) || msg.contains("API_KEY_INVALID", ignoreCase = true)) {
                        throw Failure("AI key not accepted. Check it in Settings.")
                    }
                    lastError = "$model: $msg"
                    rest(600)
                }
                404 -> { lastError = "Model not available"; rest(3600) }
                429 -> { lastError = "AI limit reached for now (free tier). Try again in a minute."; rest(60) }
                else -> { lastError = "AI error $code"; rest(60) }
            }
        }
        throw Failure(lastError)
    }

    /** Sends a tiny request to check the key. Returns null when it works, else a message. */
    suspend fun test(key: String, store: Store): String? = try {
        parse("test 1 riyal chai", key, store, "SAR")
        null
    } catch (e: Exception) {
        e.message ?: "AI did not answer"
    }

    private fun buildPrompt(text: String, currency: String): String {
        val today = SimpleDateFormat("yyyy-MM-dd (EEEE)", Locale.US).format(Date())
        return """
You turn what a person said about money into ledger entries. They may speak English, Roman Urdu, Urdu, Hindi or Arabic, often mixed. Amounts are in $currency.
Today is $today.

Return ONLY JSON: {"entries":[{"type":"","amount":0,"category":"","item":"","qty":0,"unit":"","rate":0,"person":"","date":"YYYY-MM-DD","note":""}]}

${entryRules()}

What they said: "${text.replace("\"", "'")}"
""".trim()
    }

    /** How to turn a sentence into entries; shared by the entry reader and the chat assistant. */
    private fun entryRules(): String = """
type, one of:
- "expense": they spent money on something
- "income": salary or money earned
- "gave": they lent or gave money to a person, who now owes them
- "took": they borrowed or received money from a person, and now owe it
- "got_back": a person returned money they owed
- "paid_back": they returned money they owed to a person
- "sale": they sold goods (to a customer if named; on credit the customer then owes them)
- "purchase": they bought goods or stock for a shop or business (from a supplier if named; on credit they then owe the supplier)
  Everyday personal shopping (food, petrol, clothes) is "expense", not "purchase".
  A part payment in the same sentence is a separate "got_back" (customer paid) or "paid_back" (they paid the supplier) entry.
category (expense and income), one of: food, transport, rent, bills, mobile, home, shopping, health, personal, assets, fees, education, charity, other
  (fees = visa, residence permit, fines, insurance, government or bank fees; charity = zakat, sadqa, gifts; home = money sent to family; transport includes petrol, taxi, car repair; food includes groceries, milk, roti, restaurants, drinks)
item: the thing, 1 to 3 words. Keep the person's own words for goods, e.g. "Awwal eint", "Kachi eint", "Cheeni", "Mobile". Otherwise English, e.g. "Petrol", "Milk", "Salary".
qty / unit / rate: how many, the unit (kg, pcs, ltr, bags; empty for bricks or items) and the price per unit, only if said, else 0 / "" / 0.
  "1000 awal eint 17000 ki" = qty 1000, amount 17000. "1000 kachi eint, 1000 rate" = qty 1000, rate 1000, amount = the total if clear, else the rate.
Goods with a person: if the person took or received goods from them ("Ashraf ne li", "Ashraf ko di", "Ahmed ko bechi") use "sale";
  if the person delivered or gave goods to them ("Ashraf ne dali", "Ashraf se li", "supplier se kharida") use "purchase". Keep the goods in item.
Assets: buying something lasting (mobile, laptop, car, bike, gold, furniture, land) is "expense" with category "assets"; selling it is "income" with category "assets" and item like "Mobile (sold)".
person: the other person's name, capitalised, for gave/took/got_back/paid_back (required) and sale/purchase (if named), else "".
Ignore sentences that are only remarks or questions.
date: resolve words like kal, parson, 14 date, 13 oct. Default today.
note: a short clean English summary, max 6 words.
Make one entry per transaction. Skip anything without an amount. Number words count ("seven", "do sau" = 200, "dedh hazar" = 1500).
""".trim()

    private class CutOff : Exception()

    /**
     * Settings per model family. Gemini 3 is made for temperature 1 (lower makes it loop and repeat) and is told to
     * think briefly, which makes it several times faster. 2.5 models get a small thinking budget instead.
     */
    private fun config(model: String, file: Boolean, think: Boolean): JSONObject {
        val c = JSONObject().put("responseMimeType", "application/json")
            .put("maxOutputTokens", if (file) 65_536 else 8_192)
        when {
            model.startsWith("gemini-3") -> {
                // Gemini 3 ignores temperature (newer versions refuse it), so it isn't sent.
                // "minimal" = answer almost straight away; the JSON's "work" field still gives room to work out numbers.
                // Lite models answer almost straight away; the stronger ones think briefly.
                if (think) c.put("thinkingConfig", JSONObject().put("thinkingLevel", if (model.contains("lite")) "minimal" else "low"))
            }
            model.startsWith("gemini-2.5") -> {
                if (think) c.put("temperature", 0)
                if (think) c.put("thinkingConfig", JSONObject().put("thinkingBudget", if (model.contains("lite")) 0 else 512))
            }
            else -> c.put("temperature", 0)
        }
        return c
    }

    /** Where the time of the last call on this thread went: opening the connection, waiting for the AI, reading. */
    private class Timing(var connect: Long = 0, var wait: Long = 0, var read: Long = 0)
    private val timing = ThreadLocal.withInitial { Timing() }

    private fun call(model: String, key: String, prompt: String, file: Pair<String, String>? = null, think: Boolean = true): Pair<Int, String> {
        val url = URL("https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent")
        val conn = url.openConnection() as HttpURLConnection
        val tm = timing.get()!!
        var ok = false
        return try {
            conn.requestMethod = "POST"
            conn.connectTimeout = 15_000
            // A model that hasn't answered by then is stuck; the next one is tried instead of waiting.
            // Reading a whole statement takes longer than a sentence.
            conn.readTimeout = if (file != null) 90_000 else 30_000
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("x-goog-api-key", key)
            val body = JSONObject()
                .put("contents", JSONArray().put(JSONObject().put("parts", JSONArray().apply {
                    if (file != null) {
                        put(JSONObject().put("inline_data", JSONObject().put("mime_type", file.first).put("data", file.second)))
                    }
                    put(JSONObject().put("text", prompt))
                })))
                .put("generationConfig", config(model, file != null, think))
            val t0 = System.currentTimeMillis()
            conn.connect()
            val t1 = System.currentTimeMillis()
            conn.outputStream.use { it.write(body.toString().toByteArray()) }
            val code = conn.responseCode
            val t2 = System.currentTimeMillis()
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() } ?: ""
            val t3 = System.currentTimeMillis()
            tm.connect = t1 - t0; tm.wait = t2 - t1; tm.read = t3 - t2
            ok = true
            code to text
        } catch (e: java.net.SocketTimeoutException) {
            throw Failure("AI took too long to answer.")
        } catch (e: Exception) {
            throw Failure("No internet. Using offline mode.")
        } finally {
            // A fully read answer leaves the connection open for the next message (saves the secure handshake).
            if (!ok) conn.disconnect()
        }
    }

    // ---------- Fast chat: Groq or OpenRouter (OpenAI-style API), much quicker than Gemini's free tier ----------

    /** The last reason the fast AI didn't answer, shown small under the answer. */
    @Volatile var fastError = ""

    fun isGroq(key: String) = key.startsWith("gsk_")
    fun isOpenRouter(key: String) = key.startsWith("sk-or-")
    fun isClaude(key: String) = key.startsWith("sk-ant-")
    fun isFastKey(key: String) = isGroq(key.trim()) || isOpenRouter(key.trim()) || isClaude(key.trim())

    /** Claude (Anthropic) answers with its fast Haiku model. */
    private fun claudeCall(prompt: String, key: String, trace: StringBuilder?): String {
        val started = System.currentTimeMillis()
        val body = JSONObject()
            .put("model", "claude-haiku-5-5")
            .put("max_tokens", 1500)
            .put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", prompt)))
        val conn = URL("https://api.anthropic.com/v1/messages").openConnection() as HttpURLConnection
        val (code, text) = try {
            conn.requestMethod = "POST"
            conn.connectTimeout = 10_000
            conn.readTimeout = 30_000
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("x-api-key", key)
            conn.setRequestProperty("anthropic-version", "2023-06-01")
            conn.outputStream.use { it.write(body.toString().toByteArray()) }
            val c = conn.responseCode
            c to ((if (c in 200..299) conn.inputStream else conn.errorStream)?.bufferedReader()?.use { it.readText() } ?: "")
        } catch (e: java.net.SocketTimeoutException) {
            conn.disconnect(); throw Failure("Claude took too long")
        } catch (e: Exception) {
            conn.disconnect(); throw Failure("Claude not reachable")
        }
        val msg = runCatching { JSONObject(text).optJSONObject("error")?.optString("message").orEmpty() }.getOrDefault("")
        when (code) {
            200 -> {
                val o = JSONObject(text)
                val parts = o.getJSONArray("content")
                val sb = StringBuilder()
                for (i in 0 until parts.length()) parts.getJSONObject(i).takeIf { it.optString("type") == "text" }?.let { sb.append(it.optString("text")) }
                val content = sb.toString().trim()
                val clean = content.substring(content.indexOf('{').coerceAtLeast(0), (content.lastIndexOf('}') + 1).coerceAtLeast(0))
                if (clean.isBlank()) throw Failure("Claude sent an empty answer")
                val u = o.optJSONObject("usage")
                trace?.append(
                    "Claude Haiku · " + String.format(Locale.US, "%.1fs", (System.currentTimeMillis() - started) / 1000.0) +
                        (u?.let { " (in " + it.optInt("input_tokens") + ", out " + it.optInt("output_tokens") + ")" } ?: "")
                )
                return clean
            }
            401 -> throw Failure("Claude key not accepted")
            402, 403 -> throw Failure("Claude: no credit or not allowed. " + msg.take(60))
            429 -> throw Failure("Claude limit reached for now")
            529 -> throw Failure("Claude is busy right now")
            else -> throw Failure("Claude error $code " + msg.take(60))
        }
    }

    /** Sends [prompt] to Groq or OpenRouter (picked from the key) and returns the JSON text of the answer. */
    private fun fastCall(prompt: String, key: String, trace: StringBuilder?): String {
        if (isClaude(key)) return claudeCall(prompt, key, trace)
        val groq = isGroq(key)
        val base = if (groq) "https://api.groq.com/openai/v1" else "https://openrouter.ai/api/v1"
        val provider = if (groq) "Groq" else "OpenRouter"
        var model = "openai/gpt-oss-120b"
        var jsonMode = true
        val started = System.currentTimeMillis()
        repeat(3) {
            val body = JSONObject()
                .put("model", model)
                .put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", prompt)))
                // Groq counts the requested maximum toward its 8,000-tokens-a-minute limit, so ask for less there.
                .put("max_tokens", if (groq) 1500 else 4096)
            if (jsonMode) body.put("response_format", JSONObject().put("type", "json_object"))
            if (groq) {
                body.put("reasoning_effort", "low")
            } else {
                body.put("reasoning", JSONObject().put("effort", "low"))
                // Send it to whichever host is fastest right now (Groq, Cerebras, …).
                body.put("provider", JSONObject().put("sort", "throughput"))
            }
            val conn = URL("$base/chat/completions").openConnection() as HttpURLConnection
            val (code, text) = try {
                conn.requestMethod = "POST"
                conn.connectTimeout = 10_000
                conn.readTimeout = 25_000
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json")
                conn.setRequestProperty("Authorization", "Bearer $key")
                if (!groq) conn.setRequestProperty("X-Title", "Smart Expense")
                conn.outputStream.use { it.write(body.toString().toByteArray()) }
                val c = conn.responseCode
                c to ((if (c in 200..299) conn.inputStream else conn.errorStream)?.bufferedReader()?.use { it.readText() } ?: "")
            } catch (e: java.net.SocketTimeoutException) {
                conn.disconnect()
                throw Failure("$provider took too long")
            } catch (e: Exception) {
                conn.disconnect()
                throw Failure("$provider not reachable")
            }
            val msg = runCatching { JSONObject(text).optJSONObject("error")?.optString("message").orEmpty() }.getOrDefault("")
            when {
                code == 200 -> {
                    val o = JSONObject(text)
                    val content = o.getJSONArray("choices").getJSONObject(0).getJSONObject("message").optString("content")
                        .trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
                    val clean = content.substring(content.indexOf('{').coerceAtLeast(0), (content.lastIndexOf('}') + 1).coerceAtLeast(0))
                    if (clean.isBlank()) throw Failure("$provider sent an empty answer")
                    val u = o.optJSONObject("usage")
                    trace?.append(
                        "gpt-oss-120b ($provider) · " + String.format(Locale.US, "%.1fs", (System.currentTimeMillis() - started) / 1000.0) +
                            (u?.let { " (in " + it.optInt("prompt_tokens") + ", out " + it.optInt("completion_tokens") + ")" } ?: "")
                    )
                    return clean
                }
                // No credit on OpenRouter: use the free version of the same model.
                code == 402 && !groq && !model.endsWith(":free") -> model = "openai/gpt-oss-120b:free"
                // A model that doesn't take JSON mode is asked again without it.
                code == 400 && jsonMode && (msg.contains("json", true) || msg.contains("response_format", true)) -> jsonMode = false
                code == 401 -> throw Failure("$provider key not accepted")
                code == 429 -> throw Failure("$provider limit reached for now")
                code == 413 -> throw Failure("$provider: message too large for the free plan")
                else -> throw Failure("$provider error $code " + msg.take(60))
            }
        }
        throw Failure("$provider did not answer")
    }

    /** Opens the secure connection to Google early (when the chat opens), so the first message doesn't pay for it. */
    fun warmUp(key: String) {
        if (key.isBlank()) return
        runCatching {
            val conn = URL("https://generativelanguage.googleapis.com/v1beta/models?pageSize=1").openConnection() as HttpURLConnection
            conn.connectTimeout = 10_000
            conn.readTimeout = 10_000
            conn.setRequestProperty("x-goog-api-key", key.trim())
            val code = conn.responseCode
            (if (code in 200..299) conn.inputStream else conn.errorStream)?.use { it.readBytes() }
        }
    }

    /** Asks every model a tiny question and reports how long each takes. Typed in the chat as "speed test". */
    fun speedTest(key: String, fast: String = ""): String {
        val prompt = "Reply with exactly this JSON and nothing else: {\"ok\":1}"
        val fastLine = if (fast.isBlank()) "" else {
            val tr = StringBuilder()
            val r = runCatching { fastCall(prompt, fast.trim(), tr) }
            "- Fast chat: " + (if (r.isSuccess) tr.toString() + ", OK" else (r.exceptionOrNull()?.message ?: "failed")) + "\n"
        }
        val lines = (MODELS + STRONG).distinct().map { model ->
            val t0 = System.currentTimeMillis()
            val res = runCatching { call(model, key.trim(), prompt, think = model !in plain) }
            val secs = String.format(Locale.US, "%.1fs", (System.currentTimeMillis() - t0) / 1000.0)
            val tm = timing.get()!!
            val r = res.getOrNull()
            val status = when {
                r == null -> res.exceptionOrNull()?.message ?: "failed"
                r.first == 200 -> "OK"
                r.first == 429 -> "busy (limit)"
                else -> "error ${r.first}: " + errorMessage(r.second).take(80)
            }
            "- ${shortName(model)}: $secs, $status" +
                (if (r != null) " (connect ${tm.connect}ms, AI ${tm.wait}ms)" else "")
        }
        return "Speed test (tiny question to each model):\n" + fastLine + lines.joinToString("\n")
    }

    private fun errorMessage(body: String): String = try {
        JSONObject(body).getJSONObject("error").optString("message", "AI error")
    } catch (e: Exception) {
        "AI error"
    }

    private fun readText(body: String): String {
        val candidate = JSONObject(body).getJSONArray("candidates").getJSONObject(0)
        if (candidate.optString("finishReason") == "MAX_TOKENS") throw CutOff()
        val parts = candidate.getJSONObject("content").getJSONArray("parts")
        val sb = StringBuilder()
        for (i in 0 until parts.length()) sb.append(parts.getJSONObject(i).optString("text"))
        return sb.toString().trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
    }

    private fun toEntries(json: String, said: String): List<Entry> {
        val arr = JSONObject(json).optJSONArray("entries") ?: return emptyList()
        val now = System.currentTimeMillis()
        val out = mutableListOf<Entry>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            var amount = o.optDouble("amount", 0.0)
            if (amount.isNaN() || amount <= 0) {
                val q = o.optDouble("qty", 0.0)
                val r = o.optDouble("rate", 0.0)
                amount = if (!q.isNaN() && !r.isNaN() && q > 0 && r > 0) q * r else 0.0
            }
            if (amount <= 0) continue
            val type = when (o.optString("type").lowercase()) {
                "sale" -> Type.SALE
                "purchase" -> Type.PURCHASE
                "income" -> Type.AAMDANI
                "gave" -> Type.DIYA
                "took" -> Type.LIYA
                "got_back" -> Type.WAPIS_MILA
                "paid_back" -> Type.WAPIS_DIYA
                else -> Type.KHARCHA
            }
            val person = o.optString("person").trim().replaceFirstChar { it.uppercase() }
            if (isLoan(type) && person.isEmpty()) continue
            val cat = when {
                type == Type.SALE -> Cat.SALES
                type == Type.PURCHASE -> Cat.STOCK
                isLoan(type) -> Cat.UDHAAR
                type == Type.AAMDANI && o.optString("category").lowercase() == "assets" -> Cat.ASSETS
                type == Type.AAMDANI -> Cat.TANKHWAH
                else -> when (o.optString("category").lowercase()) {
                    "food" -> Cat.KHANA
                    "transport" -> Cat.TRANSPORT
                    "rent" -> Cat.RENT
                    "bills" -> Cat.BIJLI
                    "mobile" -> Cat.MOBILE
                    "home" -> Cat.GHAR
                    "shopping" -> Cat.SHOPPING
                    "health" -> Cat.SEHAT
                    "personal" -> Cat.PERSONAL
                    "assets" -> Cat.ASSETS
                    "fees" -> Cat.FEES
                    "education" -> Cat.EDU
                    "charity" -> Cat.CHARITY
                    else -> Cat.OTHER
                }
            }
            val item = when {
                isLoan(type) -> o.optString("item").trim().ifBlank { "Loans" }
                isTrade(type) -> o.optString("item").trim().ifBlank { if (type == Type.SALE) "Sale" else "Purchase" }
                else -> o.optString("item").trim().ifBlank { if (type == Type.AAMDANI) "Income" else cat.label }
            }
            val qty = o.optDouble("qty", 0.0).let { if (it.isNaN() || it < 0) 0.0 else it }
            val rate = o.optDouble("rate", 0.0).let { if (it.isNaN() || it < 0) 0.0 else it }
            // A said part of the day ("subah", "lunch", "dinner") sets the entry's time.
            // An exact time ("21:48", from a statement) wins over a said part of the day.
            val exact = Regex("""^(\d{1,2}):(\d{2})""").find(o.optString("time").trim())
            val hour = Parser.dayPartHour(o.optString("note")) ?: Parser.dayPartHour(said)
            val baseDate = parseDate(o.optString("date")) ?: now
            val entryDate = when {
                exact != null -> java.util.Calendar.getInstance().apply {
                    timeInMillis = baseDate
                    set(java.util.Calendar.HOUR_OF_DAY, exact.groupValues[1].toInt().coerceIn(0, 23))
                    set(java.util.Calendar.MINUTE, exact.groupValues[2].toInt().coerceIn(0, 59))
                    set(java.util.Calendar.SECOND, 0)
                    set(java.util.Calendar.MILLISECOND, 0)
                }.timeInMillis
                hour != null -> Parser.atHour(baseDate, hour)
                else -> baseDate
            }
            out += Entry(
                id = "e" + System.nanoTime() + i,
                type = type,
                amount = amount,
                category = cat,
                item = item,
                person = if (isLoan(type) || isTrade(type)) person else "",
                note = o.optString("note").trim().ifBlank { said }.take(60),
                date = entryDate,
                createdAt = if (exact != null || hour != null) entryDate else now,
                qty = qty,
                unit = o.optString("unit").trim().take(10),
                rate = rate
            )
        }
        return out
    }

    private fun parseDate(s: String): Long? = try {
        val d = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { isLenient = false }.parse(s)
        if (d == null) null else Calendar.getInstance().apply {
            time = d
            set(Calendar.HOUR_OF_DAY, 12)
        }.timeInMillis.takeIf { it <= System.currentTimeMillis() + 86_400_000L }
    } catch (e: Exception) {
        null
    }
}
