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
 * - [rephrase]: when the local engine does not understand a sentence, Groq rewrites it as a plain command the app
 *   already knows ("nrgs ka khata kholo" -> "Nargis Rashid ka khata"). The app then runs that command itself, so
 *   every amount and total still comes from the saved records, never from the model. Only the sentence, the last
 *   few chat lines and the saved khata names are sent; entries are not.
 * - [transcribe]: Whisper speech-to-text for the mic button.
 */
object Groq {
    private const val CHAT_URL = "https://api.groq.com/openai/v1/chat/completions"
    private const val VOICE_URL = "https://api.groq.com/openai/v1/audio/transcriptions"
    private const val CHAT_MODEL = "openai/gpt-oss-20b"
    private const val VOICE_MODEL = "whisper-large-v3"

    /** [command] for the app to run, or [reply] for small talk. Either may be blank. */
    class Help(val command: String, val reply: String)

    private val instructions = """
        You help an offline khata (personal ledger) app in Pakistan/Gulf. People write Roman Urdu, Urdu script, Hindi
        or English, often misspelled or from voice typing. Rewrite the user's last message as ONE short command the app
        understands, using these forms:
        - add money: "chai 5", "petrol 60", "Ali ko 500 diye", "Ali se 300 liye", "salary 3000 mili"
        - questions: "aaj kitna kharcha", "kal kitna kharcha", "food this month", "last 5 transactions", "28 oct ka kharcha"
        - a person's account: "<exact saved name> ka khata", "<exact saved name> ka balance"
        - edits: "last entry 400 karo", "petrol 50 nahi 40 tha", "last entry delete"
        Use a saved name exactly as listed when the user means one of them. Never invent amounts, dates or totals.
        If the message is greeting/small talk or not about money, leave "command" empty and put a short friendly reply
        in the user's language in "reply". Answer with JSON only: {"command": "...", "reply": "..."}
    """.trimIndent()

    suspend fun rephrase(key: String, said: String, names: List<String>, recent: List<ChatMessage>): Result<Help> =
        withContext(Dispatchers.IO) {
            runCatching {
                val context = buildString {
                    append("Saved khata names: ").append(names.take(200).joinToString(", ").ifBlank { "(none)" }).append('\n')
                    recent.takeLast(4).forEach { append(if (it.isUser) "User: " else "App: ").append(it.text.take(300)).append('\n') }
                    append("User's last message: ").append(said.take(500))
                }
                val body = JSONObject()
                    .put("model", CHAT_MODEL)
                    .put("temperature", 0)
                    .put("max_tokens", 400)
                    .put("messages", JSONArray()
                        .put(JSONObject().put("role", "system").put("content", instructions))
                        .put(JSONObject().put("role", "user").put("content", context)))
                val (code, text) = post(CHAT_URL, key, "application/json", body.toString().toByteArray(Charsets.UTF_8))
                if (code !in 200..299) error(describe(code))
                val content = JSONObject(text).getJSONArray("choices").getJSONObject(0).getJSONObject("message").optString("content")
                // The model is asked for JSON only; take the first {...} in case it adds words around it.
                val json = Regex("""\{[\s\S]*\}""").find(content)?.value ?: error("No JSON in reply")
                val o = JSONObject(json)
                Help(o.optString("command").trim(), o.optString("reply").trim())
            }
        }

    /** Whisper speech-to-text. [language] is "ur", "en" or "" to let Whisper detect it. */
    suspend fun transcribe(key: String, audio: File, language: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val boundary = "----bolkhata" + System.nanoTime()
            val out = java.io.ByteArrayOutputStream()
            fun field(name: String, value: String) {
                out.write("--$boundary\r\nContent-Disposition: form-data; name=\"$name\"\r\n\r\n$value\r\n".toByteArray())
            }
            field("model", VOICE_MODEL)
            field("response_format", "json")
            if (language.isNotBlank()) field("language", language)
            out.write("--$boundary\r\nContent-Disposition: form-data; name=\"file\"; filename=\"voice.m4a\"\r\nContent-Type: audio/mp4\r\n\r\n".toByteArray())
            out.write(audio.readBytes())
            out.write("\r\n--$boundary--\r\n".toByteArray())
            val (code, text) = post(VOICE_URL, key, "multipart/form-data; boundary=$boundary", out.toByteArray())
            if (code !in 200..299) error(describe(code))
            JSONObject(text).optString("text").trim()
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
