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
                if (o.has("person")) e = e.copy(person = o.optString("person").trim().replaceFirstChar { it.uppercase() })
                if (o.has("date")) parseDate(o.optString("date"))?.let { e = e.copy(date = it) }
                if (o.has("qty")) o.optDouble("qty").takeIf { !it.isNaN() && it >= 0 }?.let { e = e.copy(qty = it) }
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
            EditPlan(changed, deleted, json.optString("message").ifBlank { "Here is the change." })
        }

    /** Sends [prompt] to the first model that answers and returns its JSON text. */
    private fun request(prompt: String, key: String, store: Store): String {
        val first = store.aiModel.takeIf { it in MODELS }
        val order = listOfNotNull(first) + MODELS.filter { it != first }
        var lastError = "AI did not answer"
        for (model in order) {
            val (code, body) = call(model, key, prompt)
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

What they said: "${text.replace("\"", "'")}"
""".trim()
    }

    private fun call(model: String, key: String, prompt: String): Pair<Int, String> {
        val url = URL("https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent")
        val conn = url.openConnection() as HttpURLConnection
        return try {
            conn.requestMethod = "POST"
            conn.connectTimeout = 15_000
            conn.readTimeout = 30_000
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("x-goog-api-key", key)
            val body = JSONObject()
                .put("contents", JSONArray().put(JSONObject().put("parts", JSONArray().put(JSONObject().put("text", prompt)))))
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
            val hour = Parser.dayPartHour(o.optString("note")) ?: Parser.dayPartHour(said)
            val baseDate = parseDate(o.optString("date")) ?: now
            val entryDate = if (hour != null) Parser.atHour(baseDate, hour) else baseDate
            out += Entry(
                id = "e" + System.nanoTime() + i,
                type = type,
                amount = amount,
                category = cat,
                item = item,
                person = if (isLoan(type) || isTrade(type)) person else "",
                note = o.optString("note").trim().ifBlank { said }.take(60),
                date = entryDate,
                createdAt = if (hour != null) entryDate else now,
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
