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
    private const val CHAT_MODEL = "openai/gpt-oss-120b"
    private const val VOICE_MODEL = "whisper-large-v3"

    private val instructions = """
        Fix a message for an offline khata (ledger) app. People type Roman Urdu, Urdu or Hindi script, or English,
        often misspelled or from voice typing. Return the SAME message in simple Roman Urdu/English the app can read:
        fix spelling, write Urdu/Hindi script in Roman letters, and replace a name with the matching saved name.
        Keep every number exactly. Do not answer, add or drop meaning. Short replies like "detail do" stay as they are.
        Reply with the corrected message only.
    """.trimIndent()

    /**
     * Every chat message passes through here first when Groq is on. Returns the corrected message, or null when Groq
     * is unreachable or changed a number (the app then uses the message as typed). [lastAnswer] lets a follow-up
     * ("dlip shhzad aqbal" after "Which A or B?") be corrected; [names] are only the saved names that look like words
     * in the message, to keep tokens low.
     */
    suspend fun correct(key: String, said: String, lastAnswer: String, names: List<String>): String? = withContext(Dispatchers.IO) {
        runCatching {
            val prompt = buildString {
                if (names.isNotEmpty()) append("Saved names: ").append(names.joinToString(", ")).append('\n')
                if (lastAnswer.isNotBlank()) append("App's last reply: ").append(lastAnswer.take(150)).append('\n')
                append("Message: ").append(said.take(300))
            }
            fun body(lowEffort: Boolean) = JSONObject()
                .put("model", CHAT_MODEL)
                .put("temperature", 0)
                .put("max_tokens", 500)
                .apply { if (lowEffort) put("reasoning_effort", "low") }
                .put("messages", JSONArray()
                    .put(JSONObject().put("role", "system").put("content", instructions))
                    .put(JSONObject().put("role", "user").put("content", prompt)))
                .toString().toByteArray(Charsets.UTF_8)
            var (code, text) = post(CHAT_URL, key, "application/json", body(true))
            if (code == 400) post(CHAT_URL, key, "application/json", body(false)).let { code = it.first; text = it.second }
            if (code !in 200..299) error(describe(code))
            val fixed = JSONObject(text).getJSONArray("choices").getJSONObject(0).getJSONObject("message")
                .optString("content").trim().removeSurrounding("\"").lines().firstOrNull { it.isNotBlank() }?.trim().orEmpty()
            // Never let a correction change money: the digits must stay exactly the same.
            fun digits(s: String) = Regex("""\d+(?:[.,]\d+)?""").findAll(s).map { it.value.replace(",", "") }.toList()
            fixed.takeIf { it.isNotBlank() && it.length <= 400 && digits(it) == digits(said) }
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

    /**
     * Whisper makes up words for silence and noise ("Subtitles by…", "Thanks for watching", a lone "."). True for
     * such text, so it is never sent as a message.
     */
    fun isNoise(text: String): Boolean {
        val t = text.trim().lowercase()
        if (t.none { it.isLetterOrDigit() }) return true
        if (t.any { it in '\u0370'..'\u03FF' || it in '\u0400'..'\u04FF' || it in '\u3040'..'\u9FFF' }) return true
        return listOf("subtitle", "thanks for watching", "thank you for watching", "amara.org", "subscribe", "authorwave")
            .any { it in t }
    }

    /**
     * Whisper speech-to-text. The language is detected by itself (Urdu, Hindi, English or a mix); the short example
     * nudges it to write the way people type here, in Roman Urdu.
     */
    suspend fun transcribe(key: String, audio: File): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val boundary = "----bolkhata" + System.nanoTime()
            val out = java.io.ByteArrayOutputStream()
            fun field(name: String, value: String) {
                out.write("--$boundary\r\nContent-Disposition: form-data; name=\"$name\"\r\n\r\n$value\r\n".toByteArray())
            }
            field("model", VOICE_MODEL)
            field("response_format", "json")
            field("prompt", "Hamid ka khata kholo. Chai 50 riyal likho. Is mahine kitna kharcha hua?")
            out.write("--$boundary\r\nContent-Disposition: form-data; name=\"file\"; filename=\"voice.m4a\"\r\nContent-Type: audio/mp4\r\n\r\n".toByteArray())
            out.write(audio.readBytes())
            out.write("\r\n--$boundary--\r\n".toByteArray())
            val (code, text) = post(VOICE_URL, key, "multipart/form-data; boundary=$boundary", out.toByteArray())
            if (code !in 200..299) error(describe(code))
            // Urdu or Hindi script is written in English letters, the way the app and the owner type.
            JSONObject(text).optString("text").trim().let { runCatching { Translit.toRoman(it) }.getOrDefault(it).trim() }
        }
    }

    private fun describe(code: Int) = when (code) {
        401 -> "Groq key is wrong or expired"
        413 -> "Recording is too long"
        429 -> "Groq limit reached; try again in a minute"
        else -> "Groq error $code"
    }

    private fun post(url: String, key: String, type: String, body: ByteArray): Pair<Int, String> {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.connectTimeout = 10_000
        conn.readTimeout = 25_000
        conn.doOutput = true
        conn.setRequestProperty("Authorization", "Bearer $key")
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
