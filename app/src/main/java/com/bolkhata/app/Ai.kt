package com.bolkhata.app

import kotlinx.coroutines.Dispatchers
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
    val MODELS = listOf("gemini-3.5-flash-lite", "gemini-3.8-flash", "gemini-3.1-flash-lite", "gemini-flash-latest", "gemini-2.5-flash")

    class Failure(message: String) : Exception(message)

    /** Totals, balances and recent entries the AI may use for answers. */
    private fun ledgerContext(all: List<Entry>, recent: List<Entry>, currency: String): String {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val timeFormat = SimpleDateFormat("HH:mm", Locale.US)
        val recentJson = JSONArray()
        recent.forEachIndexed { i, entry ->
            recentJson.put(
                JSONObject()
                    .put("n", i + 1)
                    .put("date", dateFormat.format(Date(entry.date)))
                    .put("time", timeFormat.format(Date(entry.createdAt)))
                    .put("type", entry.type.label)
                    .put("amount", entry.amount)
                    .put("category", entry.category.label)
                    .put("item", entry.item)
                    .put("person", entry.person)
                    .put("note", entry.note)
                    .put("qty", entry.qty)
                    .put("unit", entry.unit)
            )
        }
        val now = Calendar.getInstance()
        fun inMonth(e: Entry, back: Int): Boolean {
            val c = Calendar.getInstance().apply { add(Calendar.MONTH, -back) }
            val d = Calendar.getInstance().apply { timeInMillis = e.date }
            return d.get(Calendar.YEAR) == c.get(Calendar.YEAR) && d.get(Calendar.MONTH) == c.get(Calendar.MONTH)
        }
        val monthName = SimpleDateFormat("MMMM", Locale.US).format(now.time)
        val byCat = all.filter { it.type == Type.KHARCHA && inMonth(it, 0) }
            .groupBy { it.category.label }.mapValues { g -> g.value.sumOf { it.amount } }
            .entries.sortedByDescending { it.value }
            .joinToString("; ") { it.key + " " + fmt(it.value) }.ifBlank { "none" }
        val balancesText = balances(all).entries
            .sortedByDescending { kotlin.math.abs(it.value) }
            .joinToString("; ") { e ->
                e.key + ": " + (if (e.value >= 0) "owes you " else "you owe ") + fmt(kotlin.math.abs(e.value))
            }.ifBlank { "none" }
        val todayStart = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val todays = all.filter { it.date >= todayStart && it.date < todayStart + 86_400_000L }
        val todayText = todays.joinToString("; ") { e ->
            e.type.label + " " + fmt(e.amount) + " " + e.item.ifBlank { e.category.label } +
                (if (e.note.isNotBlank()) " (" + e.note + ")" else "")
        }.ifBlank { "nothing yet" }
        val lines = listOf(
            "Saved entries: " + all.size,
            "Today's expenses: " + fmt(todays.filter { it.type == Type.KHARCHA }.sumOf { it.amount }) + " $currency",
            "Today's entries: $todayText",
            "$monthName expenses: " + fmt(all.filter { it.type == Type.KHARCHA && inMonth(it, 0) }.sumOf { it.amount }) + " $currency",
            "$monthName income: " + fmt(all.filter { it.type == Type.AAMDANI && inMonth(it, 0) }.sumOf { it.amount }) + " $currency",
            "Last month expenses: " + fmt(all.filter { it.type == Type.KHARCHA && inMonth(it, 1) }.sumOf { it.amount }) + " $currency",
            "$monthName expenses by category: $byCat",
            "People balances (owes you = they must pay you): $balancesText"
        ).joinToString("\n")
        return "Saved data summary:\n$lines\n\nSaved entries, newest first (up to 150; n is the number to use in edits and delete):\n$recentJson"
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
        currency: String
    ): AssistantResult = withContext(Dispatchers.IO) {
        if (key.isBlank()) throw Failure("AI is not configured.")
        val recent = all.sortedWith(compareByDescending<Entry> { it.date }.thenByDescending { it.createdAt }).take(150)
        val today = SimpleDateFormat("yyyy-MM-dd (EEEE)", Locale.US).format(Date())
        val conversation = history.takeLast(10).joinToString("\n") { m ->
            (if (m.isUser) "User: " else "Assistant: ") + m.text.take(600)
        }.ifBlank { "(new conversation)" }
        val said = message.replace("\"", "'").take(2000)
        val prompt = """
You are the assistant inside Smart Expense, a money diary app. Today is $today. Currency is $currency.
The person writes English, Roman Urdu, Urdu, Hindi or Arabic, often mixed.

Decide what their latest message is:
A) It records NEW money that moved (spent, earned, lent, borrowed, returned, sold, bought). Put it in "entries" using the entry rules below,
   and set "reply" to one short line confirming it, e.g. "Saved: breakfast 7 $currency".
B) It corrects, moves or removes money that is ALREADY in the saved entries: a different day ("aaj nahi kal tha", "that was yesterday"),
   a different amount, category, name or note, or "delete / hata do / galat hai". Do NOT add a new entry for it.
   Put the change in "edits" (n from the saved entries, only the fields that change; "date" as YYYY-MM-DD for a new day)
   or the n in "delete". Look at the previous conversation to know which entry they mean. The reply says what changed.
C) Anything else: a greeting, a question, a report, a search, advice. Answer in "reply" with no entries, edits or deletes.
   A time can be set with "time" ("HH:MM"); "a bit before / after" another saved entry means a few minutes from that entry's time.
   If the same money was saved twice by mistake (same item and amount on two days, and they say it happened once), keep the one on
   the right day and put the other in "delete".
If it is not clear which saved entry they mean, ask one short question and change nothing.
Never say you added, changed or deleted something unless it is in "entries", "edits" or "delete".

How to reply:
- Short and to the point. Lead with the answer. Usually 1 to 4 short lines.
- Reports and summaries: short lines like "- Food: 144 $currency", then one line with the total or main point.
- Searches ("petrol kab dala", "Ahmed ke sath kya hua"): list matching entries as "- 23 Oct · Petrol · 60", at most 10, newest first.
- When they ask how to save or what to do, give 2 or 3 practical tips based on their own numbers.
- Reply in the same language and script as their latest message (Roman Urdu if they write Roman Urdu).
- Plain text only, no markdown headings or bold. Use "-" for lists, each item on its own line, never inside brackets.
- "aaj" / "today" means today only, "kal" yesterday, "is mahine" this month. Answer for exactly the period asked, and list what was spent on when they ask "kahan" / "where".
- Use only the saved data for personal facts. Never invent entries or amounts. If the data does not have it, say so in one line.
- Questions about things outside money: answer briefly and helpfully, then they can carry on.
- App help, when they ask how to do something: the paperclip next to the message box imports a statement, receipt or bill (photo or PDF)
  and old bank SMS (last month, 3 or 6 months); the Overview tab shows totals and days; Reports in the menu show Cash Out, Cash In,
  Sales, Purchases, Stock, Profit, Cash Balance and Dues; tapping an entry edits it; "last entry was 400 not 500" changes an entry.

Entry rules:
${entryRules()}
Each entry: {"type":"","amount":0,"category":"","item":"","qty":0,"unit":"","rate":0,"person":"","date":"YYYY-MM-DD","note":""}

${ledgerContext(all, recent, currency)}

Previous conversation:
$conversation

Latest message: "$said"

Return ONLY JSON: {"reply":"","entries":[],"edits":[{"n":1,"type":"","amount":0,"category":"","item":"","qty":0,"person":"","date":"YYYY-MM-DD","time":"","note":""}],"delete":[]}
(edits and delete are [] unless case B.)
""".trim()
        val json = try {
            JSONObject(request(prompt, key.trim(), store))
        } catch (e: Failure) {
            throw e
        } catch (e: Exception) {
            throw Failure("AI returned an unreadable answer.")
        }
        val list = toEntries(json.toString(), message)
        val reply = json.optString("reply").trim()
        val plan = planFrom(json, recent, reply.ifBlank { "Updated." })
        if (reply.isBlank() && list.isEmpty() && plan.changed.isEmpty() && plan.deleted.isEmpty()) {
            throw Failure("AI returned an empty answer.")
        }
        AssistantResult(reply, list, plan.takeIf { it.changed.isNotEmpty() || it.deleted.isNotEmpty() })
    }

    class AssistantResult(val reply: String, val entries: List<Entry>, val plan: EditPlan?)

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
        val today = SimpleDateFormat("yyyy-MM-dd (EEEE)", Locale.US).format(Date())
        val data = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
        val prompt = """
The attached file is a bank statement, card statement, receipt or bill. Today is $today. The person's currency is $currency.
Turn every money movement in it into a ledger entry, using the date printed for each line.
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

Return ONLY JSON: {"entries":[{"type":"","amount":0,"category":"","item":"","qty":0,"unit":"","rate":0,"person":"","date":"YYYY-MM-DD","time":"","note":""}]}
""".trim()
        val json = try {
            request(prompt, key.trim(), store, mime to data)
        } catch (e: Failure) {
            throw e
        } catch (e: Exception) {
            throw Failure("Could not read this file. Try a clearer photo or another PDF.")
        }
        try {
            toEntries(json, note)
        } catch (e: Exception) {
            throw Failure("Could not read this file. Try a clearer photo or another PDF.")
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
        val first = store.aiModel.takeIf { it in MODELS }
        val order = listOfNotNull(first) + MODELS.filter { it != first }
        var lastError = "AI did not answer"
        for (model in order) {
            val (code, body) = call(model, key, prompt)
            when (code) {
                200 -> {
                    store.aiModel = model
                    return@withContext toEntries(readText(body), text)
                }
                400, 401, 403 -> {
                    val msg = errorMessage(body)
                    if (msg.contains("API key", ignoreCase = true) || code != 400) throw Failure("AI key not accepted. Check it in Settings.")
                    lastError = msg
                }
                404 -> lastError = "Model not available"
                429 -> throw Failure("AI daily limit reached. Using offline mode.")
                else -> lastError = "AI error $code"
            }
        }
        throw Failure(lastError)
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
            val old = recent.getOrNull(o.optInt("n", 0) - 1) ?: continue
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
        val deleted = (0 until del.length()).mapNotNull { recent.getOrNull(del.optInt(it, 0) - 1) }
            .filter { d -> changed.none { it.first.id == d.id } }
        return EditPlan(changed, deleted, message)
    }


    /** Sends [prompt] to the first model that answers and returns its JSON text. */
    /** [file] is an attached image or PDF: its media type and base64 data. */
    private fun request(prompt: String, key: String, store: Store, file: Pair<String, String>? = null): String {
        val first = store.aiModel.takeIf { it in MODELS }
        val order = listOfNotNull(first) + MODELS.filter { it != first }
        var lastError = "AI did not answer"
        for (model in order) {
            val (code, body) = call(model, key, prompt, file)
            when (code) {
                200 -> {
                    store.aiModel = model
                    return readText(body)
                }
                400, 401, 403 -> {
                    val msg = errorMessage(body)
                    if (msg.contains("API key", ignoreCase = true) || code != 400) throw Failure("AI key not accepted. Check it in Settings.")
                    lastError = msg
                }
                404 -> lastError = "Model not available"
                429 -> throw Failure("AI daily limit reached. Try again later.")
                else -> lastError = "AI error $code"
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

    private fun call(model: String, key: String, prompt: String, file: Pair<String, String>? = null): Pair<Int, String> {
        val url = URL("https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent")
        val conn = url.openConnection() as HttpURLConnection
        return try {
            conn.requestMethod = "POST"
            conn.connectTimeout = 15_000
            // Reading a whole statement takes longer than a sentence.
            conn.readTimeout = if (file != null) 120_000 else 30_000
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
                .put("generationConfig", JSONObject().put("temperature", 0).put("responseMimeType", "application/json"))
            conn.outputStream.use { it.write(body.toString().toByteArray()) }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            code to (stream?.bufferedReader()?.use { it.readText() } ?: "")
        } catch (e: Exception) {
            throw Failure("No internet. Using offline mode.")
        } finally {
            conn.disconnect()
        }
    }

    private fun errorMessage(body: String): String = try {
        JSONObject(body).getJSONObject("error").optString("message", "AI error")
    } catch (e: Exception) {
        "AI error"
    }

    private fun readText(body: String): String {
        val parts = JSONObject(body).getJSONArray("candidates").getJSONObject(0)
            .getJSONObject("content").getJSONArray("parts")
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
