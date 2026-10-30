package com.bolkhata.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * ElevenLabs text-to-speech for the voice call's answers, used only when the owner saved their own key and voice
 * in Settings. Only the short spoken answer is sent; without internet the phone's own voice is used.
 */
object Eleven {
    // One model: the fastest and cheapest (half the credits), good with Hindi/Urdu and English.
    private const val MODEL = "eleven_v4_turbo"

    /** Turns [text] into speech with [voice] (a Voice ID from the ElevenLabs voice library) and saves it to [out]. */
    suspend fun speech(key: String, voice: String, text: String, out: File): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val url = "https://api.elevenlabs.io/v1/text-to-speech/" + URLEncoder.encode(voice.trim(), "UTF-8") +
                "?output_format=mp3_44100_64"
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.connectTimeout = 10_000
            conn.readTimeout = 20_000
            conn.doOutput = true
            conn.setRequestProperty("xi-api-key", key)
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("Accept", "audio/mpeg")
            try {
                conn.outputStream.use {
                    it.write(JSONObject().put("text", text).put("model_id", MODEL).toString().toByteArray(Charsets.UTF_8))
                }
                val code = conn.responseCode
                if (code !in 200..299) {
                    val body = conn.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                    error(describe(code, body))
                }
                conn.inputStream.use { input -> out.outputStream().use { input.copyTo(it) } }
                out
            } finally {
                conn.disconnect()
            }
        }
    }

    /** ElevenLabs' own reason when it gives one (a free plan can't use Voice Library voices, quota used up…). */
    private fun describe(code: Int, body: String): String {
        val reason = runCatching {
            val detail = JSONObject(body).opt("detail")
            if (detail is JSONObject) detail.optString("message") else detail?.toString().orEmpty()
        }.getOrDefault("").trim().take(160)
        return when {
            reason.isNotEmpty() -> "ElevenLabs: $reason"
            code == 401 -> "ElevenLabs key is wrong"
            code == 402 || code == 429 -> "ElevenLabs limit reached"
            code == 404 || code == 422 -> "ElevenLabs Voice ID not found"
            else -> "ElevenLabs error $code"
        }
    }
}

/**
 * What the call says aloud: only the first sentence (or line) of the answer, so it sounds like talk and uses few
 * voice credits. The full answer stays in the chat.
 */
fun shortSpoken(text: String): String {
    val first = text.lines().map { it.trim() }.firstOrNull { it.isNotEmpty() }.orEmpty()
    val sentence = Regex("""^.+?[.!?۔](?=\s|$)""").find(first)?.value ?: first
    return if (sentence.length <= 160) sentence else sentence.take(160).substringBeforeLast(' ') + "…"
}
