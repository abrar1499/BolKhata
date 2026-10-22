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
You turn what a person said about money into ledger entries. They live in Saudi Arabia and speak Roman Urdu, Urdu, English or Arabic, often mixed. Amounts are in $currency.
Today is $today.

Return ONLY JSON: {"entries":[{"type":"","amount":0,"category":"","item":"","person":"","date":"YYYY-MM-DD","note":""}]}

type, one of:
- "expense": they spent money on something
- "income": salary or money earned
- "gave": they lent or gave money to a person, who now owes them
- "took": they borrowed or received money from a person, and now owe it
- "got_back": a person returned money they owed
- "paid_back": they returned money they owed to a person
category (expense only), one of: food, transport, rent, bills, mobile, home, shopping, health, personal, fees, education, charity, other
  (fees = iqama, visa, fines, insurance, government or bank fees; charity = zakat, sadqa, gifts; home = money sent to family; transport includes petrol, taxi, car repair; food includes groceries, milk, roti, restaurants, drinks)
item: 1 to 3 English words, e.g. "Petrol", "Milk", "Car repair", "Salary".
person: the other person's name, capitalised, only for gave/took/got_back/paid_back, else "".
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
            val amount = o.optDouble("amount", 0.0)
            if (amount.isNaN() || amount <= 0) continue
            val type = when (o.optString("type").lowercase()) {
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
                isLoan(type) -> Cat.UDHAAR
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
                    "fees" -> Cat.FEES
                    "education" -> Cat.EDU
                    "charity" -> Cat.CHARITY
                    else -> Cat.OTHER
                }
            }
            val item = when {
                isLoan(type) -> "Loans"
                else -> o.optString("item").trim().ifBlank { if (type == Type.AAMDANI) "Income" else cat.label }
            }
            out += Entry(
                id = "e" + System.nanoTime() + i,
                type = type,
                amount = amount,
                category = cat,
                item = item,
                person = if (isLoan(type)) person else "",
                note = o.optString("note").trim().ifBlank { said }.take(60),
                date = parseDate(o.optString("date")) ?: now,
                createdAt = now
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
