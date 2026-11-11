package com.bolkhata.app

import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * TEST FEATURE (APK build only, with [Ai]): with an AI key saved, the AI does every chat job itself through tools:
 * it finds, adds, changes and deletes entries and asks the app for totals and balances, then answers. The app still
 * does all the arithmetic (the AI is told to state only numbers a tool gave it), and deleted entries go to Trash.
 *
 * To remove it: delete this file and the "AiAgent" block in MainActivity's chatInput.
 */
object AiAgent {
    /** What the AI may do to the app. */
    class Host(
        val entries: () -> List<Entry>, val add: (List<Entry>) -> Unit, val update: (List<Entry>) -> Unit,
        val trash: (Set<String>) -> Unit, val names: () -> List<String>, val currency: String,
        /** The owner's accounts and who sits in which people account. */
        val setup: () -> SideSetup = { SideSetup() }, val saveSetup: (SideSetup) -> Unit = {},
        /** Builds the PDF and opens the share sheet. */
        val pdf: (PdfDoc) -> Unit = {}
    )

    /**
     * The AI's reply and the entries it saved (shown as the Saved card). When it asked the app to show a list or a
     * breakdown, [card] is the app's own answer text (drawn as the usual card) and [listIds] the rows in it.
     */
    class Answer(val text: String, val added: List<String>, val card: String = "", val listIds: List<String> = emptyList(), val filter: String = "",
        /** What the app may remember from this answer (see [lesson]); "" when it is not something to learn. */
        val lesson: String = "", val shape: String = "", val words: Map<String, String> = emptyMap())

    /** What the tools of one message leave for the chat: saved entries and the card to show. */
    class Notes(val added: MutableList<String> = mutableListOf(), var card: String = "", var listIds: List<String> = emptyList(),
        /** How the last look-up picked its entries, so "detail do" or "aur food?" can go on from it. */
        var filter: String = "", var found: Int = 0,
        /** The amounts written in the message itself: saving one of them is meant, even when the same entry was saved minutes ago. */
        var said: List<Double> = emptyList(), var skipped: Int = 0)

    /** A long list in the chat shows this many rows, then "+N more" and the total of all. */
    private const val LIST_ROWS = 30

    private val TYPES = mapOf(
        "expense" to Type.KHARCHA, "income" to Type.AAMDANI, "lent" to Type.DIYA, "borrowed" to Type.LIYA,
        "received_back" to Type.WAPIS_MILA, "paid_back" to Type.WAPIS_DIYA, "sale" to Type.SALE, "work" to Type.PURCHASE, "purchase" to Type.PURCHASE
    )
    private val EXPENSE_CATS: List<Cat> = Cat.entries.filter { it !in setOf(Cat.TANKHWAH, Cat.UDHAAR, Cat.SALES, Cat.STOCK) }
    private val dayFmt get() = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private val timeFmt get() = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)

    private fun text(about: String) = JSONObject().put("type", "string").put("description", about)
    private fun number(about: String) = JSONObject().put("type", "number").put("description", about)
    private fun words(about: String, of: List<String>? = null) = JSONObject().put("type", "array").put("description", about)
        .put("items", JSONObject().put("type", "string").apply { if (of != null) put("enum", JSONArray(of)) })
    private fun shape(vararg fields: Pair<String, JSONObject>, required: List<String> = emptyList()) = JSONObject().put("type", "object")
        .put("properties", JSONObject().apply { fields.forEach { put(it.first, it.second) } }).put("required", JSONArray(required))

    /** How entries are picked; every part is optional and they narrow each other. */
    private val picking: Array<Pair<String, JSONObject>> = arrayOf(
        "types" to words("Kinds of entry", TYPES.keys.toList()),
        "categories" to words("Expense categories", EXPENSE_CATS.map { it.label }),
        "persons" to words("Saved names of people or shops"),
        "words" to words("Words to look for in the item or note, e.g. petrol"),
        "from" to text("First day, YYYY-MM-DD"),
        "to" to text("Last day, YYYY-MM-DD"),
        "added_in_last_minutes" to number("Only entries typed into the app within this many minutes (not their date)"),
        "min_amount" to number("Smallest amount"),
        "max_amount" to number("Largest amount"),
        "last" to number("Only the latest N entries")
    )

    val tools: List<Ai.Tool> = listOf(
        Ai.Tool("find_entries", "Lists saved entries that match (newest first, at most 40 shown) with their ids, the count and the total.", shape(*picking)),
        Ai.Tool("totals", "Adds up the matching entries: the total and count, and the same per group when group_by is given. Use it for every sum.",
            shape(*picking, "group_by" to JSONObject().put("type", "string").put("enum", JSONArray(listOf("category", "person", "type", "month", "item"))))),
        Ai.Tool("report", "The business report worked out by the app: sales, other income, business costs, expenses and profit for a period (all time when no dates), and the net worth today (cash in hand, to get, stock, assets, to pay).",
            shape("from" to text("First day, YYYY-MM-DD"), "to" to text("Last day, YYYY-MM-DD"))),
        Ai.Tool("balances", "What each person owes or is owed (all time) and the cash in hand.", shape()),
        Ai.Tool("add_entries", "Saves new entries.", shape(
            "entries" to JSONObject().put("type", "array").put("items", shape(
                "type" to JSONObject().put("type", "string").put("enum", JSONArray(listOf("expense", "income", "lent", "borrowed", "received_back", "paid_back", "sale", "work"))),
                "amount" to number("Exactly as said; leave out for a sale or work with qty and rate (the app multiplies)"),
                "qty" to number("How many, when said"),
                "rate" to number("Price for one (or for 1000 when per_1000), for a sale or work; leave out when not said (the app uses this person's last rate)"),
                "per_1000" to JSONObject().put("type", "boolean").put("description", "True when the rate is per thousand"),
                "unit" to text("kg, piece, bag... when said"),
                "paid_now" to JSONObject().put("type", "boolean").put("description", "A sale or work settled in cash on the spot (not on credit)"),
                "again" to JSONObject().put("type", "boolean").put("description", "True only when the person clearly wants a second, identical entry"),
                "category" to JSONObject().put("type", "string").put("enum", JSONArray(EXPENSE_CATS.map { it.label })).put("description", "For an expense"),
                "item" to text("1-3 words, e.g. Petrol"),
                "person" to text("The other person, saved spelling if it matches; needed for lent, borrowed, received_back, paid_back"),
                "note" to text("Any extra detail"),
                "date" to text("YYYY-MM-DD when a day was said, else leave out (today)"),
                required = listOf("type")
            )), required = listOf("entries"))),
        Ai.Tool("update_entries", "Changes saved entries (ids from find_entries); only the fields given change.", shape(
            "ids" to words("Entry ids"),
            "set" to shape("amount" to number("New amount"), "item" to text("New item"), "category" to text("New expense category"),
                "person" to text("New person"), "note" to text("New note"), "date" to text("New day, YYYY-MM-DD"),
                "type" to JSONObject().put("type", "string").put("enum", JSONArray(TYPES.keys.toList()))),
            required = listOf("ids", "set"))),
        Ai.Tool("accounts", "Lists the accounts: the Party accounts (Customers, Suppliers, Staff, People or the owner's own) with the people in each, and the Expense and Income accounts.", shape()),
        Ai.Tool("put_in_account", "Puts people into a Party account, making the account and the people if new (no entry is needed). A person sits in one account only.", shape(
            "account" to text("Customers, Suppliers, Staff, People, or the owner's own account name"),
            "people" to words("Names, saved spelling if they match"), required = listOf("account", "people"))),
        Ai.Tool("add_account", "Makes a new empty Expense or Income account (a thing money goes to or comes from).", shape(
            "side" to JSONObject().put("type", "string").put("enum", JSONArray(listOf("expense", "income"))), "name" to text("Account name"),
            required = listOf("side", "name"))),
        Ai.Tool("show", "Shows the person the matching entries as a proper card drawn by the app: a list of entries, a breakdown by category with totals, a total, a count, or the highest expenses. Use it whenever they want to see a list, detail or breakdown.",
            shape(*picking, "view" to JSONObject().put("type", "string").put("enum", JSONArray(listOf("list", "breakdown", "total", "count", "highest", "by_month", "by_person", "by_item"))), required = listOf("view"))),
        Ai.Tool("make_pdf", "Makes a PDF of every matching entry (date, item, detail, amount, with totals) and opens the share sheet. Use it when a PDF, record, statement or report to share is asked for.",
            shape(*picking, "title" to text("Heading of the PDF, e.g. Expenses"),
                "group_by" to JSONObject().put("type", "string").put("enum", JSONArray(listOf("category", "person", "month"))).put("description", "A table per group, with a summary first"),
                required = listOf("title"))),
        Ai.Tool("delete_entries", "Moves saved entries to Trash (ids from find_entries); the person can restore them.", shape("ids" to words("Entry ids"), required = listOf("ids")))
    )

    /** The same from message to message (until a new name is saved), so the provider can cache it; the time goes with the message. */
    fun system(currency: String, names: List<String>) = """
        You run a ledger and expense app for its owner through the tools. Currency: ${currency.ifBlank { "none shown" }}.
        People write Roman Urdu, Urdu or Hindi script, or English, often misspelled or heard from voice.
        - Do what is asked with the tools, then answer in one or two short sentences, in the language and script of the
          latest message (English gets English, Roman Urdu gets Roman Urdu). Plain text only: no tables, no markdown.
        - Every amount, total, count or balance you state must come from a tool result of this turn. Never add up or
          guess money yourself: ask the totals tool.
        - When the person wants to see entries, a detail, a list or where the money went, call show: the app draws
          it as a card. Never write lists, rows or tables yourself; after show, say one short sentence at most and do
          not repeat the rows or amounts. "detail", "details", "tafseel" or "kahan kahan laga" after a total is the
          breakdown view; "list" or "entries" is the list view; "category" or "main category" is the breakdown;
          "monthly", "per month", "mahana", "har mahine" is by_month (it also gives the average a month); person-wise
          is by_person; item-wise is by_item.
        - A follow-up ("detail do", "aur food?", "list me batao", "main category bola tha") keeps what the previous
          look-up picked (types, dates, people, categories) and changes only what the new message changes.
        - A word in Urdu, Arabic or Hindi script is read by its sound and may be an English word written that way
          (the sound "blij" is bleach, not bill). Keep the thing that was said as the item; never swap it for another word.
        - New money ("chai 50", "Ali ko 500 diye", "kal salary 3000 aayi") is add_entries, amounts exactly as said.
          lent = gave a loan or paid someone on credit; borrowed = took one; received_back / paid_back = a loan returning.
        - Money with a person needs no question about its kind: "X ne diye", "X se mile", "X se liye" (money came from
          X) is received_back, the app's "Received"; "X ko diye" (money went to X) is lent, the app's "Paid". The app
          keeps one running balance per person, so both loans and returns fit these two. Use borrowed or paid_back only
          when the words qarz, udhaar or loan are said about money the owner took.
        - Business: goods given to a customer on credit ("Nargis ko 200 shirts 350 ke hisaab se di") is type sale with
          person, item, qty and rate. Work done or goods supplied by a worker or supplier, to be paid for ("Hamid ne 500
          shirts silai ki 40 ke hisaab se"), is type work. The app multiplies qty by rate; per_1000 when the rate is per
          thousand; paid_now when settled in cash on the spot. When no rate is said, save with qty and no rate: the
          app uses the person's last rate. Never ask for a rate unless the tool says none is known. A worker or
          supplier with a count of things they made, did or brought and no money word ("Hamid ne 10000 kachi eent
          dali", "Hamid 300 shirts") is work with that qty: save it, do not ask whether it is money. Money a customer pays later is received_back; money paid
          to a worker or supplier is lent. Profit, loss, sales, costs, net worth: the report tool.
          Use a saved name only when it is the same person (the same name, or a misspelling or short form of it). A
          saved name that joins two people ("A & B", "A and B") is its own account: a message naming only one of them
          means a separate, single person. A new name keeps the spelling that reads most like a real name.
        - A voice message may be misheard: read it with its other hearings and take the words that make sense for a
          ledger. A person's name is kept as heard: change it to a saved name only when the two sound almost the same
          (Hamid / Hameed); a different name (Ahmed is not Ashraf) is a new person. Amounts must agree between the
          hearings, else ask.
        - A correction of what was just saved ("sirf Hamid se", "nahi 40 tha", "ye kal ka tha") changes those entries
          with update_entries (their ids are under "Just saved"); never add the same entry a second time.
        - A khata or account for a person, with no kind said, is put_in_account People; do not ask which kind.
        - To change or delete, first find_entries for the ids. When it is clear which entries are meant, do it without
          asking again; when it is not, ask one short question instead of guessing. Deleted entries go to Trash.
        - "is mahine" is the 1st of this month to today, "kal" yesterday, "aaj tak" / "ab tak" all time (no dates).
        - People are kept in Party accounts: Customers, Suppliers, Staff (workers, employees, labour), People (anyone
          else), or an account the owner names. To make an account or put people in one, use put_in_account; it needs
          no entry. An advance or payment to a worker is "lent" with that person, and the person goes in Staff.
        - A PDF, record, statement or report to share is make_pdf with the same picking fields: the app puts every
          matching entry in it and opens the share sheet; then say shortly that it is ready.
        - Greet only when the message itself greets. Small talk: answer briefly, no tools. You only know this app;
          for anything else say so shortly.
    """.trimIndent() + if (names.isEmpty()) "" else "\nSaved names: " + names.joinToString(", ")

    /**
     * Lets the AI work until it answers (at most 8 rounds); null when the provider failed (see [Ai.lastError]).
     * [onMain] runs a tool where the app's data may be changed.
     */
    fun run(
        said: String, recent: String, host: Host, now: Long = System.currentTimeMillis(), roman: String = said, previous: String = "",
        justSaved: List<String> = emptyList(), heardAlso: List<String> = emptyList(), spoken: Boolean = heardAlso.isNotEmpty(),
        known: Map<String, String> = emptyMap(),
        onMain: (() -> String) -> String = { it() }
    ): Answer? = runCatching {
        val notes = Notes(said = numbers(roman))
        val calls = mutableListOf<Pair<String, JSONObject>>()
        val names = host.names().take(80)
        val messages = Ai.opening(
            "Now: " + timeFmt.format(Date(now)) + " (" + SimpleDateFormat("EEEE", Locale.US).format(Date(now)) + ")\n" +
                (if (recent.isBlank()) "" else "Conversation so far:\n" + recent.takeLast(1200) + "\n") +
                (if (previous.isBlank()) "" else "Previous look-up picked: " + previous.take(400) + "\n") +
                // What the last message saved, with ids, so "sirf Hamid se" or "nahi 40 tha" can change it.
                host.entries().filter { it.id in justSaved }.take(12).let { l -> if (l.isEmpty()) "" else "Just saved: " + JSONArray(l.map { show(it) }) + "\n" } +
                // Who the named people are, so "Hamid ne 3000 banai" is read as a worker's work, at their usual rate.
                people(roman, who(names, known)).take(3).joinToString("") { p ->
                    val last = host.entries().filter { it.person.equals(p, true) && it.rate > 0 }.maxByOrNull { it.date }
                    "About $p: in " + Sides.bookOf(p, host.entries(), host.setup()) +
                        (if (last == null) "" else "; last " + (if (last.type == Type.SALE) "sale" else "work") + ": " + last.item + " at " + plainNumber(last.rate) + (if (last.unit == "1000") " per 1000" else "")) + "\n"
                } +
                (if (!spoken) "" else "The message came by voice and may be misheard." +
                    (if (heardAlso.isEmpty()) "" else " It was also heard as: " + heardAlso.joinToString(" | ") { it.take(200) }) + "\n") +
                "Message: " + said.take(600) +
                // Urdu or Hindi script also goes in Roman letters by its sound, so a word is not swapped for another.
                (if (roman.trim().equals(said.trim(), true)) "" else "\nThe same message by sound, in Roman letters: " + roman.take(600))
        )
        val system = system(host.currency, names)
        for (round in 1..8) {
            val step = Ai.step(system, tools, messages) ?: return@runCatching null
            if (step.calls.isEmpty()) {
                val learnt = if (notes.added.isEmpty()) Learnt() else learn(calls, roman, host.names(), known)
                // Only saved, all of it and nothing to ask: the Saved card says it, no sentence over it.
                val onlySaved = notes.added.isNotEmpty() && notes.skipped == 0 && calls.all { it.first == "add_entries" } && '?' !in step.text
                return@runCatching Answer(if (onlySaved) "" else step.text.ifBlank { "Done." }, notes.added, notes.card, notes.listIds, notes.filter,
                    learnt.lesson, learnt.shape, learnt.words)
            }
            calls += step.calls.map { it.name to it.input }
            val done = step.calls.map { c ->
                c to onMain { runCatching { use(c.name, c.input, host, now, notes) }.getOrElse { JSONObject().put("error", it.message ?: "failed").toString() } }
            }
            // Only asked to show a card, and it is shown: the chat shows the card, not the AI's closing sentence, so that step is not paid for.
            if (step.calls.all { it.name == "show" } && notes.card.isNotBlank() && notes.found > 0 && done.none { it.second.contains("\"error\"") })
                return@runCatching Answer(step.text, notes.added, notes.card, notes.listIds, notes.filter)
            Ai.results(messages, done)
        }
        Ai.lastError = "too many steps"
        null
    }.getOrElse { Ai.lastError = it.message ?: "no internet"; null }

    // ---------- The app learns from the AI: a sentence it has read once is done by the app alone the next time ----------

    private val NUMBER = Regex("""(\d[\d,]*(?:\.\d+)?)(k)?(?![\p{L}\p{N}])""")

    private fun plainNumber(n: Double) = if (n == Math.floor(n)) n.toLong().toString() else n.toString()

    private fun plain(said: String) = NUMBER.replace(said.lowercase(Locale.ROOT), "#").replace(Regex("""[^\p{L}\p{N}#]+"""), " ").trim()

    /**
     * Who a typed word is: a spelling learnt from the AI ("=ahmd" in [learned]), else the one saved name with that word,
     * else the one saved name it is short for ("hmd" for Hamid). Null when it is no name, or could be two people.
     */
    fun who(names: List<String>, learned: Map<String, String>): (String) -> String? = { w ->
        if (w.length < 3) null else learned["=$w"]
            ?: names.filter { n -> n.lowercase(Locale.ROOT).split(' ').any { it == w } }.distinct().singleOrNull()
            ?: names.filter { Understand.shortFor(w, it.lowercase(Locale.ROOT).substringBefore(' ')) }.distinct().singleOrNull()
    }

    /** The words of a message, each with the person it names (null for other words); a name of two words counts once. */
    private fun read(said: String, who: (String) -> String?): List<Pair<String, String?>> {
        val out = mutableListOf<Pair<String, String?>>()
        for (w in plain(said).split(' ')) {
            val p = if (w == "#") null else who(w)
            if (p == null) out += w to null else if (out.lastOrNull()?.second != p) out += "@" to p
        }
        return out
    }

    /**
     * A message's shape: lower case, every number a "#" and every person's name an "@", so "hamid ko 50 diye" and
     * "nargis ko 60 diye" are one shape: a sentence learnt once works for every person and amount.
     */
    fun shape(said: String, who: (String) -> String? = { null }): String = read(said, who).joinToString(" ") { it.first }

    /** The people named in a message, in the order said. */
    fun people(said: String, who: (String) -> String?): List<String> = read(said, who).mapNotNull { it.second }

    /** The numbers of a message in the order said ("150k" is 150000). */
    fun numbers(said: String): List<Double> = NUMBER.findAll(said.lowercase(Locale.ROOT))
        .map { it.groupValues[1].replace(",", "").toDouble() * if (it.groupValues[2].isNotEmpty()) 1000 else 1 }.toList()

    /**
     * What to remember from the AI's work on [said]: its new entries with each number swapped for its place in the
     * message ("#0", "#1") and each person for theirs ("@0"), as JSON text. Null unless the AI did exactly one thing,
     * saving entries, with every amount and every named person of the message used and no day worked out (a day like
     * "kal" must be worked out afresh each time).
     */
    fun lesson(calls: List<Pair<String, JSONObject>>, said: String, who: (String) -> String? = { null }): String? {
        val (name, input) = calls.singleOrNull() ?: return null
        val list = input.optJSONArray("entries") ?: return null
        val people = people(said, who)
        val used = mutableSetOf<Int>()
        val said = numbers(said)
        if (name != "add_entries" || said.isEmpty() || list.length() == 0) return null
        val out = JSONArray()
        for (i in 0 until list.length()) {
            val e = JSONObject(list.getJSONObject(i).toString())
            if (e.optString("date").isNotBlank()) return null
            for (field in listOf("amount", "qty", "rate")) if (e.has(field)) {
                val at = said.indexOf(e.optDouble(field))
                if (at < 0) return null
                e.put(field, "#$at")
            }
            val at = people.indexOfFirst { it.equals(e.optString("person").trim(), true) }
            if (at >= 0) { e.put("person", "@$at"); used += at }
            out.put(e)
        }
        // A name in the message that the AI put nowhere: the sentence means something else, nothing to learn.
        return if (used.size == people.size) out.toString() else null
    }

    /** The one word of [said] that was typed for [person] ("mdsr" for Mudassar), when the app could not tell by itself. */
    fun word(said: String, person: String, who: (String) -> String?): String? {
        val first = person.trim().lowercase(Locale.ROOT).substringBefore(' ')
        return plain(said).split(' ').filter { w -> w.length >= 3 && w != "#" && who(w) == null && (w == first || Understand.shortFor(w, first) || Understand.closeName(w, first)) }
            .distinct().singleOrNull()
    }

    /** What one answer of the AI teaches: new spellings of names ("=mdsr" to the person) and the sentence's shape with its lesson. */
    class Learnt(val shape: String = "", val lesson: String = "", val words: Map<String, String> = emptyMap())

    /** Each new thing is learnt by itself: a new spelling of a name is one word learnt, the sentence around it stays known. */
    fun learn(calls: List<Pair<String, JSONObject>>, said: String, names: List<String>, known: Map<String, String>): Learnt {
        val list = calls.singleOrNull()?.takeIf { it.first == "add_entries" }?.second?.optJSONArray("entries") ?: return Learnt()
        val before = who(names, known)
        val named = people(said, before)
        val words = (0 until list.length()).map { list.getJSONObject(it).optString("person").trim() }
            .filter { p -> p.isNotBlank() && named.none { it.equals(p, true) } }
            .mapNotNull { p -> word(said, p, before)?.let { "=$it" to p } }.toMap()
        val after = who(names, known + words)
        val lesson = lesson(calls, said, after) ?: return Learnt(words = words)
        return Learnt(shape(said, after), lesson, words)
    }

    /** A remembered [lesson] filled with the numbers and people of a new message of the same shape, ready for add_entries; null when it does not fit. */
    fun recall(lesson: String, said: String, who: (String) -> String? = { null }): JSONObject? = runCatching {
        val now = numbers(said)
        val people = people(said, who)
        val list = JSONArray(lesson)
        for (i in 0 until list.length()) list.getJSONObject(i).let { e ->
            for (field in listOf("amount", "qty", "rate")) if (e.has(field)) e.put(field, now[e.getString(field).removePrefix("#").toInt()])
            if (e.optString("person").startsWith("@")) e.put("person", people[e.getString("person").removePrefix("@").toInt()])
        }
        JSONObject().put("entries", list)
    }.getOrNull()

    /** Runs one tool on the app's data and gives its result as JSON text; what it saved or showed is noted in [notes]. */
    fun use(name: String, input: JSONObject, host: Host, now: Long, notes: Notes = Notes()): String = when (name) {
        "show" -> {
            val f = filterOf(input, now)
            val list = Query.select(host.entries(), f).sortedByDescending { it.date }
            val view = input.optString("view")
            val what = when (view) { "breakdown" -> "report"; "total" -> "sum"; "count" -> "count"; "highest" -> "highest"; else -> "list" }
            // The app's own answer, the same text and card as without the AI; month by month (or by person or item) is a card of its own.
            notes.card = if (view.startsWith("by_") && list.isNotEmpty()) Query.grouped(list, view.removePrefix("by_"), Query.label(f), host.currency)
                else Query.answer(list, what, f, host.currency, LIST_ROWS)
            notes.listIds = if (what == "list") list.take(LIST_ROWS).map { it.id } else emptyList()
            notes.filter = picked(input)
            notes.found = list.size
            // Nothing matched: no card, and the AI is told, so it can look again or say so itself.
            if (list.isEmpty()) notes.card = ""
            JSONObject().put("shown_to_the_person", list.isNotEmpty()).put("count", list.size).put("total", list.sumOf { it.amount }).toString()
        }
        "find_entries" -> {
            val list = pick(input, host, now)
            notes.filter = picked(input)
            JSONObject().put("count", list.size).put("total", list.sumOf { it.amount })
                .put("entries", JSONArray(list.take(40).map { show(it, input.has("added_in_last_minutes")) })).put("not_shown", (list.size - 40).coerceAtLeast(0)).toString()
        }
        "totals" -> {
            val list = pick(input, host, now)
            notes.filter = picked(input)
            val out = JSONObject().put("count", list.size).put("total", list.sumOf { it.amount })
            val by: ((Entry) -> String)? = when (input.optString("group_by")) {
                "category" -> { e -> e.category.label }
                "person" -> { e -> e.person.ifBlank { "(no person)" } }
                "type" -> { e -> typeName(e.type) }
                "month" -> { e -> SimpleDateFormat("yyyy-MM", Locale.US).format(Date(e.date)) }
                "item" -> { e -> e.item.ifBlank { e.category.label } }
                else -> null
            }
            if (by != null && list.isNotEmpty()) out.put("average_per_group", Math.round(list.sumOf { it.amount } / list.groupBy(by).size * 100) / 100.0)
            if (by != null) out.put("groups", JSONArray(list.groupBy(by).map { (k, l) -> JSONObject().put("name", k).put("total", l.sumOf { it.amount }).put("count", l.size) }
                .sortedByDescending { it.getDouble("total") }.take(40)))
            out.toString()
        }
        "report" -> {
            val all = host.entries()
            val f = filterOf(input, now)
            val p = Sides.profit(all.filter { (f.from == null || it.date >= f.from) && (f.to == null || it.date <= f.to) })
            val w = Sides.worth(all, host.setup())
            JSONObject().put("period", JSONObject().put("sales", p.sale).put("other_income", p.income).put("business_costs", p.work)
                .put("expenses", p.spent).put(if (p.profit < 0) "loss" else "profit", kotlin.math.abs(p.profit)))
                .put("today", JSONObject().put("cash_in_hand", w.cash).put("to_get", w.toGet).put("stock_at_cost", w.stock).put("assets", w.assets)
                    .put("to_pay", w.toPay).put("net_worth", w.net)).toString()
        }
        "balances" -> {
            val all = host.entries()
            val people = all.filter { it.person.isNotBlank() }.groupBy { it.person.trim().lowercase() }
                .map { (_, l) -> l.first().person.trim() to l.sumOf { personEffect(it) } }.filter { kotlin.math.abs(it.second) > 0.5 }
            JSONObject().put("cash_in_hand", all.filter(::cashIn).sumOf { it.amount } - all.filter(::cashOut).sumOf { it.amount })
                .put("people", JSONArray(people.sortedByDescending { kotlin.math.abs(it.second) }.take(60).map { (n, b) ->
                    JSONObject().put("name", n).put("amount", kotlin.math.abs(b)).put("means", if (b > 0) "owes the owner" else "the owner owes them")
                })).toString()
        }
        "add_entries" -> {
            val list = input.optJSONArray("entries") ?: JSONArray()
            val made = (0 until list.length()).map { make(list.getJSONObject(it), now, it, host).let { e -> e to (list.getJSONObject(it).optBoolean("again", false) || e.amount in notes.said) } }
            // The same entry saved minutes ago (same kind, amount, person and day) is not saved twice, unless asked for or
            // its amount is written in this very message: typing it again means one more.
            val recent = host.entries().filter { now - it.createdAt in 0..15 * 60_000L }
            fun twin(e: Entry) = recent.firstOrNull { o -> o.type == e.type && o.amount == e.amount && o.person.trim().equals(e.person.trim(), true) &&
                o.item.equals(e.item, true) && dayFmt.format(Date(o.date)) == dayFmt.format(Date(e.date)) }
            val fresh = made.filter { (e, again) -> again || twin(e) == null }.map { it.first }
            val twins = made.filter { (e, again) -> !again && twin(e) != null }.map { twin(it.first)!! }
            host.add(fresh)
            notes.skipped += twins.size
            notes.added += fresh.map { it.id }
            JSONObject().put("saved", fresh.size).put("entries", JSONArray(fresh.map { show(it) })).apply {
                if (twins.isNotEmpty()) put("already_saved_minutes_ago_not_added_again", JSONArray(twins.map { show(it) }))
            }.toString()
        }
        "update_entries" -> {
            val ids = strings(input, "ids").toSet()
            val set = input.optJSONObject("set") ?: JSONObject()
            val changed = host.entries().filter { it.id in ids }.map { e ->
                var x = Query.change(e, listOf("amount", "date", "person", "item", "note").filter { set.has(it) }.associateWith { set.get(it).toString() })
                TYPES[set.optString("type").lowercase()]?.let { t -> x = x.copy(type = t) }
                Cat.entries.firstOrNull { it.label.equals(set.optString("category").trim(), true) }?.let { c -> x = x.copy(category = c) }
                x
            }
            host.update(changed)
            JSONObject().put("changed", changed.size).put("entries", JSONArray(changed.take(40).map { show(it) })).toString()
        }
        "make_pdf" -> {
            val list = pick(input, host, now).sortedBy { it.date }
            if (list.isEmpty()) error("no entries match")
            val show = SimpleDateFormat("d MMM yyyy", Locale.US)
            fun money(v: Double) = (if (v == Math.floor(v)) String.format(Locale.US, "%,d", v.toLong()) else String.format(Locale.US, "%,.2f", v)) +
                if (host.currency.isBlank()) "" else " " + host.currency
            fun rows(l: List<Entry>) = l.map { e ->
                listOf(show.format(Date(e.date)), e.item.ifBlank { e.category.label }, e.person.ifBlank { if (e.type == Type.KHARCHA) e.category.label else e.type.label }, money(e.amount))
            }
            val cols = listOf("Date", "Item", "Detail", "Amount")
            val by: ((Entry) -> String)? = when (input.optString("group_by")) {
                "category" -> { e -> e.category.label }
                "person" -> { e -> e.person.ifBlank { "No person" } }
                "month" -> { e -> SimpleDateFormat("MMM yyyy", Locale.US).format(Date(e.date)) }
                else -> null
            }
            val total = list.sumOf { it.amount }
            val tables = if (by == null) listOf(PdfTable("", cols, rows(list), listOf("Total", "", "", money(total)), left = 3))
            else list.groupBy(by).entries.sortedByDescending { g -> g.value.sumOf { it.amount } }.let { groups ->
                // A summary first, then every group with its own entries and total.
                listOf(PdfTable("Summary", listOf("", "Entries", "Amount"), groups.map { (k, l) -> listOf(k, l.size.toString(), money(l.sumOf { it.amount })) },
                    listOf("Total", list.size.toString(), money(total)))) +
                    groups.map { (k, l) -> PdfTable(k, cols, rows(l), listOf("Total", "", "", money(l.sumOf { it.amount })), left = 3) }
            }
            val period = listOf("from", "to").map { input.optString(it).trim() }.let { (a, b) ->
                fun nice(s: String) = runCatching { show.format(dayFmt.parse(s)!!) }.getOrDefault(s)
                if (a.isBlank() && b.isBlank()) show.format(Date(list.first().date)) + " – " + show.format(Date(list.last().date))
                else (if (a.isBlank()) "Up to" else nice(a)) + (if (b.isBlank()) " onwards" else (if (a.isBlank()) " " else " – ") + nice(b))
            }
            host.pdf(PdfDoc(input.optString("title").trim().ifBlank { "Entries" }, period, listOf(money(total) to "Total", list.size.toString() to "Entries"), tables))
            JSONObject().put("pdf_ready", true).put("entries", list.size).put("total", total).toString()
        }
        "accounts" -> {
            val all = host.entries()
            val setup = host.setup()
            JSONObject().put("party", JSONArray(Sides.books(Side.PARTY, all, setup).map { b ->
                JSONObject().put("name", b.name).put("people", JSONArray(Sides.kids(b, all, setup).map { it.name }.take(60)))
            })).put("expense", JSONArray(Sides.books(Side.EXP, all, setup).map { it.name }))
                .put("income", JSONArray(Sides.books(Side.INC, all, setup).map { it.name })).toString()
        }
        "put_in_account" -> {
            val account = input.optString("account").trim().ifBlank { error("account is needed") }
            val people = strings(input, "people").ifEmpty { error("people are needed") }
            val setup = host.setup()
            // The usual accounts show by themselves once someone sits in them; an own one is made (Customers keep Given | Received).
            val usual = listOf(Sides.CUSTOMERS, Sides.SUPPLIERS, Sides.STAFF, Sides.PEOPLE).firstOrNull { it.equals(account, true) }
            val name = usual ?: setup.books.firstOrNull { it.people && it.name.equals(account, true) }?.name ?: account
            val books = if (usual != null || setup.books.any { it.people && it.name.equals(name, true) }) setup.books else setup.books + Book(Side.EXP, name, true)
            // One person, one account: putting them here takes them out of any other.
            host.saveSetup(setup.copy(books = books, people = setup.people.filterKeys { k -> people.none { it.equals(k.trim(), true) } } + people.associateWith { name }))
            JSONObject().put("account", name).put("people_now_in_it", JSONArray(people)).toString()
        }
        "add_account" -> {
            val side = if (input.optString("side") == "income") Side.INC else Side.EXP
            val name = input.optString("name").trim().ifBlank { error("name is needed") }
            val setup = host.setup()
            if (Sides.books(side, host.entries(), setup).any { it.name.equals(name, true) }) JSONObject().put("already_there", name).toString()
            else { host.saveSetup(setup.copy(books = setup.books + Book(side, name, false))); JSONObject().put("made", name).toString() }
        }
        "delete_entries" -> {
            val ids = strings(input, "ids").toSet().intersect(host.entries().map { it.id }.toSet())
            host.trash(ids)
            JSONObject().put("moved_to_trash", ids.size).toString()
        }
        else -> JSONObject().put("error", "no such tool").toString()
    }

    /** Only the picking fields of a tool call, as JSON text. */
    private fun picked(o: JSONObject): String = JSONObject().apply { picking.forEach { (k, _) -> if (o.has(k)) put(k, o.get(k)) } }.toString()

    private fun strings(o: JSONObject, key: String) = o.optJSONArray(key)?.let { a -> (0 until a.length()).map { a.optString(it).trim() } }.orEmpty().filter { it.isNotBlank() }

    private fun typeName(t: Type) = TYPES.entries.firstOrNull { it.value == t }?.key ?: t.label

    /** An entry as the AI sees it, kept short (every token is paid for): empty fields are left out, [added] only when asked by it. */
    private fun show(e: Entry, added: Boolean = false): JSONObject = JSONObject().put("id", e.id).put("type", typeName(e.type)).put("amount", e.amount)
        .put("item", e.item).put("date", dayFmt.format(Date(e.date))).apply {
            if (e.type == Type.KHARCHA) put("category", e.category.label)
            if (e.person.isNotBlank()) put("person", e.person)
            if (e.note.isNotBlank() && !e.note.equals(e.item, true)) put("note", e.note.take(60))
            if (added) put("added", timeFmt.format(Date(e.createdAt)))
        }

    /** The saved entries a tool's filter picks, newest first. */
    private fun pick(o: JSONObject, host: Host, now: Long): List<Entry> = Query.select(host.entries(), filterOf(o, now)).sortedByDescending { it.date }

    private fun filterOf(o: JSONObject, now: Long): Query.Filter {
        fun day(key: String, end: Boolean) = runCatching { dayFmt.parse(o.optString(key).trim())!!.time + if (end) 86_400_000L - 1 else 0 }.getOrNull()
        fun amount(key: String) = if (o.has(key)) o.optDouble(key).takeIf { !it.isNaN() } else null
        return Query.Filter(
            from = day("from", false), to = day("to", true), types = strings(o, "types").mapNotNull { TYPES[it.lowercase()] }.toSet(),
            persons = strings(o, "persons"), cats = strings(o, "categories").mapNotNull { n -> Cat.entries.firstOrNull { it.label.equals(n, true) } }.toSet(),
            items = strings(o, "words").map { it.lowercase() }, min = amount("min_amount"), max = amount("max_amount"),
            last = o.optInt("last", 0).coerceIn(0, 500),
            createdFrom = amount("added_in_last_minutes")?.takeIf { it > 0 }?.let { now - (it * 60_000).toLong() }
        )
    }

    /** A new entry from the AI's fields; a bad one throws, and the AI is told. Goods and work get their amount from qty × rate, worked out here. */
    private fun make(o: JSONObject, now: Long, i: Int, host: Host): Entry {
        val type = TYPES[o.optString("type").trim().lowercase()] ?: error("unknown type")
        val goods = type == Type.SALE || type == Type.PURCHASE
        val qty = o.optDouble("qty", 0.0).takeIf { !it.isNaN() && it > 0 } ?: 0.0
        val person = o.optString("person").trim()
        val said = o.optDouble("rate", 0.0).takeIf { !it.isNaN() && it > 0 } ?: 0.0
        // No rate said: the last one for this item and person, else the person's last of this kind ("same as before").
        val before = if (!goods || qty <= 0 || said > 0 || o.optDouble("amount", 0.0) > 0) null
            else Goods.lastRate(o.optString("item"), person, type == Type.PURCHASE, host.entries())
                ?: host.entries().filter { it.type == type && it.rate > 0 && person.isNotBlank() && it.person.equals(person, true) }.maxByOrNull { it.date }
        val rate = before?.rate ?: said
        val per1000 = goods && (before?.let { it.unit == "1000" } ?: o.optBoolean("per_1000", false))
        val amount = if (goods && qty > 0 && rate > 0) (if (per1000) qty / 1000 * rate else qty * rate) else o.optDouble("amount", 0.0)
        if (amount.isNaN() || amount <= 0.0) error(if (goods && qty > 0) "no rate is known for this yet: ask the person for the rate" else "amount (or qty and rate) is needed")
        if (type != Type.KHARCHA && type != Type.AAMDANI && person.isBlank() && !(goods && o.optBoolean("paid_now", false))) error("person is needed for " + o.optString("type"))
        val category = when (type) {
            Type.KHARCHA -> Cat.entries.firstOrNull { it.label.equals(o.optString("category").trim(), true) } ?: Cat.OTHER
            Type.AAMDANI -> Cat.TANKHWAH
            Type.SALE -> Cat.SALES
            // Work by staff is a business cost; goods from anyone else go to stock.
            Type.PURCHASE -> if (person.isNotBlank() && Sides.bookOf(person, host.entries(), host.setup()) == Sides.STAFF) Cat.PRODUCTION else Cat.STOCK
            else -> Cat.UDHAAR
        }
        // A said day keeps the current time of day, so the entries of one message stay in order.
        val day = runCatching { dayFmt.parse(o.optString("date").trim())!!.time }.getOrNull()
        val date = if (day == null) now else day + (now - dayFmt.parse(dayFmt.format(Date(now)))!!.time)
        val item = o.optString("item").trim().take(40).replaceFirstChar { it.uppercase() }
        return Entry(
            id = "e" + System.nanoTime() + i, type = type, amount = amount, category = category,
            item = if (goods) item else item.ifBlank { category.label },
            person = person.take(40), note = o.optString("note").trim().take(120), date = date + i, createdAt = now + i,
            qty = if (goods || o.has("qty")) qty else 0.0, unit = if (per1000) "1000" else o.optString("unit").trim().take(10),
            rate = if (goods) rate else 0.0, paid = goods && o.optBoolean("paid_now", false)
        )
    }
}
