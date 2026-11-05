package com.bolkhata.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Optional help from Groq, used only when the owner saved their own key in Settings.
 *
 * - [correct]: every chat message is first corrected by Groq (spelling, Urdu/Hindi script to Roman, names to the
 *   saved spelling: "nrgs ka khata kholo" -> "Nargis Rashid ka khata"). Groq only corrects; the app's own engine
 *   then reads the message and answers from the saved records. Only the message, the app's last reply and a few
 *   similar saved names are sent; entries never are.
 * - [transcribe]: Whisper speech-to-text for the mic button and the voice call.
 * - [talk]: in the voice call, the assistant answers small talk itself and passes requests on to the app.
 * - [spoken]: in the voice call, turns the app's answer into one or two natural spoken sentences. The facts and
 *   numbers still come only from the app's answer.
 */
object Groq {
    private const val CHAT_URL = "https://api.groq.com/openai/v1/chat/completions"
    private const val VOICE_URL = "https://api.groq.com/openai/v1/audio/transcriptions"

    /** Stands in for a key: use the app's built-in key (the GROQ_KEY secret, added at build time). */
    const val SHARED = "shared"
    val sharedReady: Boolean get() = BuildConfig.GROQ_KEY.isNotBlank()
    private const val CHAT_MODEL = "openai/gpt-oss-120b"
    private const val VOICE_MODEL = "whisper-large-v3"
    /** Words every person says to a khata app, in Roman Urdu, so Whisper spells them the usual way for everyone. */
    private const val COMMON_WORDS = "Hamid ka khata kholo. Chai 50 riyal likho. Kal nashta 10 riyal, lunch 25, dinner 30. " +
        "Petrol 60, kiraya 1500, bijli bill, mobile recharge. Ali ko 500 udhaar diye, 200 wapas mile. Salary aayi 3000. " +
        "Is mahine kitna kharcha hua? Pichle mahine ka hisaab. Ye entry delete karo. Rupay, riyal, dirham."

    /** The expense categories Groq may pick from (declared before the instructions that name them). */
    private val CATEGORY_NAMES = Cat.entries.filter { it !in setOf(Cat.TANKHWAH, Cat.UDHAAR, Cat.SALES, Cat.STOCK) }
        .joinToString(", ") { it.label }

    private val instructions = """
        You are the brain of a khata (ledger and expense) app. People type or speak Roman Urdu, Urdu or Hindi script,
        or English, often misspelled. Read every message and reply with JSON only: {"command": ..., "reply": ...}.
        - If it is about their money or the app's data (add, show, search, count, total, balance, report, cashbook,
          a khata, edit, move, rename, delete, undo, a follow-up like "detail do" or "sirf food"), put ONE short
          clear message for the app in "command", in simple Roman Urdu/English like these: "chai 50", "Ali ko 500
          diye", "is mahine food kitna", "19 aug se aaj tak expense", "Ali ka khata", "cashbook", "expense report",
          "last 5 entries", "2800 wali entry", "petrol 50 nahi 40 tha", "other me kitni entries". Keep every number
          exactly and use the matching saved name. Leave "reply" empty: the app answers from its own records.
        - Otherwise (greeting, general question, help, advice), leave "command" empty and answer in "reply" in 1 to 3
          short lines, in the language they used. You cannot see their records: never state their amounts or totals.
        - When the message records NEW money (one or more entries, e.g. "chai 50 aur petrol 60", "Ali ko 500 diye",
          "kal salary 3000 aayi"), also fill "entries": a list of {"type", "amount", "category", "item", "person",
          "note", "date", "qty", "unit"}. type is one of expense, income, lent (gave a loan / paid someone on credit),
          borrowed, received_back, paid_back. category (for expense) is one of $CATEGORY_NAMES. item is 1-3 words
          (e.g. "Petrol", "Chai"), person is the other person's name (saved name if it matches) or "", note is any
          extra detail or "", date is "YYYY-MM-DD" when a day is said (today is TODAY) else "", qty and unit only
          when a quantity is said. Amounts exactly as said. For edits, questions and anything else, "entries" is [].
    """.trimIndent()

    /** What Groq made of a message: a [command] for the app (blank for none), or its own [reply]; [entries] to save. */
    data class Routed(val command: String, val reply: String, val entries: List<Entry> = emptyList())

    private val TYPES = mapOf(
        "expense" to Type.KHARCHA, "income" to Type.AAMDANI, "lent" to Type.DIYA, "borrowed" to Type.LIYA,
        "received_back" to Type.WAPIS_MILA, "paid_back" to Type.WAPIS_DIYA
    )

    /**
     * Groq's "entries" as app entries. Null (so the app's own reader is used) when any part is off: an unknown type,
     * or an amount that was not said in [said] (numbers said as words count), so money is never made up.
     */
    fun entriesFrom(list: JSONArray?, said: String, now: Long): List<Entry>? {
        if (list == null || list.length() == 0) return emptyList()
        val spoken = Regex("""\d+(?:\.\d+)?""").findAll(NumberWords.convert(said.lowercase()).replace(",", ""))
            .map { it.value.toDouble() }.toSet()
        val out = mutableListOf<Entry>()
        for (i in 0 until list.length()) {
            val o = list.optJSONObject(i) ?: return null
            val type = TYPES[o.optString("type").trim().lowercase()] ?: return null
            val amount = o.optDouble("amount", 0.0)
            if (amount <= 0.0 || amount !in spoken) return null
            val person = o.optString("person").trim()
            val category = when (type) {
                Type.KHARCHA -> Cat.entries.firstOrNull { it.label.equals(o.optString("category").trim(), true) } ?: Cat.OTHER
                Type.AAMDANI -> Cat.TANKHWAH
                else -> Cat.UDHAAR
            }
            if (type != Type.KHARCHA && type != Type.AAMDANI && person.isBlank()) return null
            val day = runCatching {
                java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).parse(o.optString("date").trim())!!.time
            }.getOrNull()
            // A said day keeps the current time of day, so the entries of one message stay in order.
            val date = if (day == null) now else day + (now - dayStart(now))
            out += Entry(
                id = "e" + System.nanoTime() + i,
                type = type, amount = amount, category = category,
                item = o.optString("item").trim().take(40).replaceFirstChar { it.uppercase() }.ifBlank { category.label },
                person = person.take(40), note = o.optString("note").trim().take(120),
                date = date + i, createdAt = now + i,
                qty = o.optDouble("qty", 0.0).takeIf { it > 0 } ?: 0.0, unit = o.optString("unit").trim().take(10)
            )
        }
        return out
    }

    private fun dayStart(ms: Long) = java.util.Calendar.getInstance().apply {
        timeInMillis = ms; set(java.util.Calendar.HOUR_OF_DAY, 0); set(java.util.Calendar.MINUTE, 0)
        set(java.util.Calendar.SECOND, 0); set(java.util.Calendar.MILLISECOND, 0)
    }.timeInMillis

    /**
     * Every chat message passes through here first when Groq is on. Null when Groq is unreachable (the app then reads
     * the message as typed). A command whose numbers differ from the message is replaced by the message, so money is
     * never changed. [lastAnswer] lets a follow-up be understood; [names] are only the saved names that look like
     * words in the message, to keep tokens low.
     */
    suspend fun route(
        key: String, said: String, lastAnswer: String, names: List<String>, now: Long = System.currentTimeMillis()
    ): Routed? = withContext(Dispatchers.IO) {
        runCatching {
            val prompt = buildString {
                if (names.isNotEmpty()) append("Saved names: ").append(names.joinToString(", ")).append('\n')
                if (lastAnswer.isNotBlank()) append("App's last reply: ").append(lastAnswer.take(150)).append('\n')
                append("Message: ").append(said.take(300))
            }
            val body = JSONObject()
                .put("model", CHAT_MODEL)
                .put("temperature", 0)
                .put("max_tokens", 1200)
                .put("reasoning_effort", "low")
                .put("response_format", JSONObject().put("type", "json_object"))
                .put("messages", JSONArray()
                    .put(JSONObject().put("role", "system").put("content", instructions
                        .replace("TODAY", java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date(now)))))
                    .put(JSONObject().put("role", "user").put("content", prompt)))
                .toString().toByteArray(Charsets.UTF_8)
            val (code, text) = post(CHAT_URL, key, "application/json", body)
            if (code !in 200..299) error(describe(code))
            val o = JSONObject(JSONObject(text).getJSONArray("choices").getJSONObject(0).getJSONObject("message").optString("content"))
            // Never let Groq change money: a command keeps exactly the digits that were said.
            fun digits(s: String) = Regex("""\d+(?:[.,]\d+)?""").findAll(s).map { it.value.replace(",", "") }.toList()
            val command = o.optString("command").trim().take(400)
            val reply = o.optString("reply").trim().take(600)
            when {
                command.isNotBlank() -> Routed(
                    if (digits(command) == digits(said)) command else said, "",
                    entriesFrom(o.optJSONArray("entries"), said, now).orEmpty()
                )
                reply.isNotBlank() -> Routed("", reply)
                else -> error("empty")
            }
        }.getOrNull()
    }

    // The voices read Roman Urdu like English, so the line to be heard is written in Hindi (Devanagari) script.
    private const val SPEAK = "the same sentences for a Hindi voice: in Devanagari script if Urdu or Hindi, unchanged if English"

    /** A line for the call: [text] is shown on screen, [speech] is what the voice reads. */
    data class Line(val text: String, val speech: String)

    private val voiceInstructions = """
        You are the friendly voice of a khata (ledger) app on a phone call. Say the app's answer back to the person
        in ONE short natural sentence (at most 10 words), in the language they spoke (Roman Urdu or English).
        Use ONLY the facts in the app's answer and keep every number exactly, written in digits; never add, guess or
        calculate.
        No lists, symbols or emojis. Reply with JSON only: {"say": the sentences, "speak": $SPEAK}.
    """.trimIndent()

    /**
     * The voice call's answer as natural speech, or null when Groq is unreachable or the reply has a number the
     * app's answer doesn't (the call then says the app's own first sentence).
     */
    suspend fun spoken(key: String, said: String, answer: String): Line? = withContext(Dispatchers.IO) {
        runCatching {
            val body = JSONObject()
                .put("model", CHAT_MODEL)
                .put("temperature", 0.3)
                .put("max_tokens", 600)
                .put("reasoning_effort", "low")
                .put("response_format", JSONObject().put("type", "json_object"))
                .put("messages", JSONArray()
                    .put(JSONObject().put("role", "system").put("content", voiceInstructions))
                    .put(JSONObject().put("role", "user").put("content", "Person said: " + said.take(300) + "\nApp's answer: " + answer.take(800))))
                .toString().toByteArray(Charsets.UTF_8)
            val (code, text) = post(CHAT_URL, key, "application/json", body)
            if (code !in 200..299) error(describe(code))
            val reply = JSONObject(JSONObject(text).getJSONArray("choices").getJSONObject(0).getJSONObject("message").optString("content"))
            val say = reply.optString("say").trim()
            val speak = reply.optString("speak").trim().ifBlank { say }
            fun digits(s: String) = Regex("""\d+(?:[.,]\d+)?""").findAll(s).map { it.value.replace(",", "") }.toSet()
            Line(say, speak).takeIf {
                say.isNotBlank() && say.length <= 300 && speak.length <= 400 &&
                    digits(answer).containsAll(digits(say)) && digits(answer).containsAll(digits(speak))
            }
        }.getOrNull()
    }

    private val callInstructions = """
        You are the friendly female voice assistant of a khata (ledger) app, on a phone call with its owner.
        If they ask the app to do or show anything (open a khata, add or edit an entry, a balance, totals, a search,
        a reminder, delete or undo; e.g. "Hamid ka khata kholo", "chai 50 likho", "kitna kharcha hua"), reply
        {"say": a very short "one moment" line like "Ek minute.", "speak": $SPEAK, "command": their request as one
        short clear Roman Urdu message for the app, every number and name kept exactly}.
        For greetings or small talk, reply {"say": a natural reply of at most 6 words, "speak": $SPEAK, "command": ""}.
        Talk like a real person: follow the conversation, never repeat a line you already said, don't keep asking
        "aap kaise hain". You know nothing about their money: never state amounts or balances yourself.
        If the words are noise or make no sense, reply {"say": "", "command": ""}.
        Speak the way they spoke: Roman Urdu for Urdu or Hindi, else English. Reply with the JSON only.
    """.trimIndent()

    /** One turn of the voice call: what to say now, and the request to send to the app (blank for none). */
    data class Turn(val say: Line, val command: String)

    /**
     * Lets the call feel like talking to an assistant. Null when Groq is unreachable (the call then sends what
     * was heard straight to the app). A command whose numbers differ from what was heard is replaced by the words
     * as heard, so money is never changed.
     */
    suspend fun talk(key: String, said: String, recent: String): Turn? = withContext(Dispatchers.IO) {
        runCatching {
            val prompt = buildString {
                if (recent.isNotBlank()) append("Conversation so far:\n").append(recent.takeLast(1200)).append("\n\n")
                append("Person said: ").append(said.take(300))
            }
            val body = JSONObject()
                .put("model", CHAT_MODEL)
                .put("temperature", 0.3)
                .put("max_tokens", 600)
                .put("reasoning_effort", "low")
                .put("response_format", JSONObject().put("type", "json_object"))
                .put("messages", JSONArray()
                    .put(JSONObject().put("role", "system").put("content", callInstructions))
                    .put(JSONObject().put("role", "user").put("content", prompt)))
                .toString().toByteArray(Charsets.UTF_8)
            val (code, text) = post(CHAT_URL, key, "application/json", body)
            if (code !in 200..299) error(describe(code))
            val reply = JSONObject(JSONObject(text).getJSONArray("choices").getJSONObject(0).getJSONObject("message").optString("content"))
            fun digits(s: String) = Regex("""\d+(?:[.,]\d+)?""").findAll(s).map { it.value.replace(",", "") }.toList()
            val command = reply.optString("command").trim().take(400)
            val say = reply.optString("say").trim().take(300)
            Turn(
                say = Line(say, reply.optString("speak").trim().take(400).ifBlank { say }),
                command = if (command.isNotBlank() && digits(command) != digits(said)) said else command
            )
        }.getOrNull()
    }

    private val answerInstructions = """
        You are the helpful assistant inside a khata (ledger and expense) app. The app could not match this message
        to its own commands, so answer it yourself: general questions, how to use the app, advice, small talk.
        You cannot see their saved entries: never state or guess their amounts, balances or totals. If they ask
        about their own money, say in one line how to ask the app, e.g. "is mahine ka kharcha", "Ali ka khata".
        Answer in 1 to 3 short lines, in the language they used (Roman Urdu for Urdu or Hindi, else English).
    """.trimIndent()

    /** Groq's own short answer to a message the app's engine did not understand. Null when Groq is unreachable. */
    suspend fun answer(key: String, said: String, recent: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            val prompt = buildString {
                if (recent.isNotBlank()) append("Conversation so far:\n").append(recent.takeLast(1200)).append("\n\n")
                append("Message: ").append(said.take(500))
            }
            val body = JSONObject()
                .put("model", CHAT_MODEL)
                .put("temperature", 0.4)
                .put("max_tokens", 700)
                .put("reasoning_effort", "low")
                .put("messages", JSONArray()
                    .put(JSONObject().put("role", "system").put("content", answerInstructions))
                    .put(JSONObject().put("role", "user").put("content", prompt)))
                .toString().toByteArray(Charsets.UTF_8)
            val (code, text) = post(CHAT_URL, key, "application/json", body)
            if (code !in 200..299) error(describe(code))
            JSONObject(text).getJSONArray("choices").getJSONObject(0).getJSONObject("message").optString("content")
                .trim().take(600).ifBlank { null }
        }.getOrNull()
    }

    /**
     * Whisper makes up words for silence and noise ("Subtitles by…", "Thanks for watching", a lone "."). True for
     * such text, so it is never sent as a message.
     */
    fun isNoise(text: String): Boolean {
        val t = text.trim().lowercase()
        if (t.none { it.isLetterOrDigit() }) return true
        if (t.any { it in '\u0370'..'\u03FF' || it in '\u0400'..'\u04FF' || it in '\u3040'..'\u9FFF' }) return true
        if (listOf("subtitle", "thanks for watching", "thank you for watching", "amara.org", "subscribe", "authorwave",
                "welcome to my channel", "موسیقی", "mosiqi", "moseeqi", "mausiqi", "musiqi", "¿").any { it in t }) return true
        // "National National National National": one word said over and over is noise, not a message.
        val words = t.split(Regex("""\s+""")).filter { it.isNotBlank() }
        return words.size >= 3 && words.distinct().size == 1
    }

    /**
     * Whisper speech-to-text. The language is detected by itself (Urdu, Hindi, English or a mix). The prompt holds a
     * short example and the saved khata [names], so names are spelled the way they are saved. Parts Whisper itself
     * marks as no speech (silence, music) are dropped.
     */
    suspend fun transcribe(key: String, audio: File, names: List<String> = emptyList()): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val boundary = "----bolkhata" + System.nanoTime()
            val out = java.io.ByteArrayOutputStream()
            fun field(name: String, value: String) {
                out.write("--$boundary\r\nContent-Disposition: form-data; name=\"$name\"\r\n\r\n$value\r\n".toByteArray())
            }
            field("model", VOICE_MODEL)
            field("response_format", "verbose_json")
            field("temperature", "0")
            val known = names.map { it.trim() }.filter { it.isNotBlank() }.distinct().joinToString(", ").take(600)
            field("prompt", COMMON_WORDS + if (known.isEmpty()) "" else " Khata: $known.")
            out.write("--$boundary\r\nContent-Disposition: form-data; name=\"file\"; filename=\"voice.m4a\"\r\nContent-Type: audio/mp4\r\n\r\n".toByteArray())
            out.write(audio.readBytes())
            out.write("\r\n--$boundary--\r\n".toByteArray())
            val (code, text) = post(VOICE_URL, key, "multipart/form-data; boundary=$boundary", out.toByteArray())
            if (code !in 200..299) error(describe(code))
            val o = JSONObject(text)
            val segments = o.optJSONArray("segments")
            val heard = if (segments == null || segments.length() == 0) o.optString("text") else
                (0 until segments.length()).map { segments.getJSONObject(it) }
                    // Whisper's own rule: a part is silence only when it says so AND is unsure; mixed Urdu/English speech is often unsure.
                    .filterNot { it.optDouble("no_speech_prob", 0.0) > 0.6 && it.optDouble("avg_logprob", 0.0) < -1.0 }
                    .joinToString(" ") { it.optString("text").trim() }
            // Urdu or Hindi script is written in English letters, the way the app and the owner type.
            heard.trim().let { runCatching { Translit.toRoman(it) }.getOrDefault(it).trim() }
        }
    }

    private fun describe(code: Int) = when (code) {
        401 -> "Groq key is wrong or expired"
        413 -> "Recording is too long"
        429 -> "Groq limit reached; try again in a minute"
        else -> "Groq error $code"
    }

    private val usedTokens = java.util.concurrent.atomic.AtomicInteger()
    private val usedMs = java.util.concurrent.atomic.AtomicLong()

    /** Time and tokens Groq used since the last call, as "Groq · 1.2s · 340 tokens" (blank for none); then resets. */
    fun takeUsage(): String {
        val ms = usedMs.getAndSet(0)
        val tokens = usedTokens.getAndSet(0)
        if (ms == 0L) return ""
        return "Groq · " + String.format(java.util.Locale.US, "%.1fs", ms / 1000.0) + if (tokens > 0) " · $tokens tokens" else ""
    }

    private fun post(url: String, key: String, type: String, body: ByteArray): Pair<Int, String> {
        val started = System.currentTimeMillis()
        try { return send(url, key, type, body).also { (_, text) ->
            usedTokens.addAndGet(runCatching { JSONObject(text).getJSONObject("usage").optInt("total_tokens") }.getOrDefault(0))
        } } finally { usedMs.addAndGet(System.currentTimeMillis() - started) }
    }

    private fun send(url: String, key: String, type: String, body: ByteArray): Pair<Int, String> {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.connectTimeout = 10_000
        conn.readTimeout = 25_000
        conn.doOutput = true
        conn.setRequestProperty("Authorization", "Bearer " + if (key == SHARED) BuildConfig.GROQ_KEY else key)
        conn.setRequestProperty("Content-Type", type)
        try {
            conn.outputStream.use { it.write(body) }
            val code = conn.responseCode
            val text = (if (code < 400) conn.inputStream else conn.errorStream)?.bufferedReader()?.use { it.readText() } ?: ""
            return code to text
        } finally {
            conn.disconnect()
        }
    }
}
