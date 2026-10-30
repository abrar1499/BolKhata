package com.bolkhata.app

import android.content.Context
import org.json.JSONObject
import java.io.File

/** One recorded chat turn, plus the person's "this answer was wrong" mark if they gave one. */
data class ChatLogRecord(
    val id: String,
    val timestamp: Long,
    val isUser: Boolean,
    val text: String,
    val meta: String,
    val flagged: Boolean = false,
    val expected: String = ""
)

/**
 * A permanent record of every chat message and answer, kept on the phone so mistakes can be reviewed
 * and fixed later. Unlike the visible chat it survives "Clear chat" and keeps far more than 100 messages.
 * Lines are JSON: a "msg" line per message and a "flag" line whenever an answer is marked wrong.
 */
class ChatLog(context: Context) {
    private val file = File(context.filesDir, "chat-log.jsonl")

    fun record(message: ChatMessage) = append(
        JSONObject()
            .put("kind", "msg")
            .put("id", message.id)
            .put("ts", message.timestamp)
            .put("role", if (message.isUser) "user" else "assistant")
            .put("text", message.text.take(MAX_TEXT))
            .put("meta", message.meta)
    )

    /** Marks an answer as wrong. [expected] is what the person says the answer should have been. */
    fun flag(id: String, expected: String) = append(
        JSONObject()
            .put("kind", "flag")
            .put("id", id)
            .put("ts", System.currentTimeMillis())
            .put("expected", expected.take(MAX_TEXT))
    )

    fun load(): List<ChatLogRecord> =
        if (file.exists()) parseChatLog(runCatching { file.readLines(Charsets.UTF_8) }.getOrDefault(emptyList()))
        else emptyList()

    fun clear() {
        runCatching { file.delete() }
    }

    private fun append(line: JSONObject) {
        runCatching {
            file.appendText(line.toString() + "\n", Charsets.UTF_8)
            if (file.length() > MAX_BYTES) {
                // Keep the newest half so the log never grows without limit.
                val lines = file.readLines(Charsets.UTF_8)
                file.writeText(lines.drop(lines.size / 2).joinToString("\n", postfix = "\n"), Charsets.UTF_8)
            }
        }
    }

    companion object {
        private const val MAX_TEXT = 4000
        private const val MAX_BYTES = 3_000_000L
    }
}

/** Turns log lines into records, applying each "flag" line to the message it points at. */
fun parseChatLog(lines: List<String>): List<ChatLogRecord> {
    val records = LinkedHashMap<String, ChatLogRecord>()
    lines.forEach { line ->
        val o = runCatching { JSONObject(line) }.getOrNull() ?: return@forEach
        val id = o.optString("id")
        if (id.isBlank()) return@forEach
        when (o.optString("kind")) {
            "msg" -> records[id] = ChatLogRecord(
                id = id,
                timestamp = o.optLong("ts"),
                isUser = o.optString("role") == "user",
                text = o.optString("text"),
                meta = o.optString("meta")
            )
            "flag" -> records[id]?.let { records[id] = it.copy(flagged = true, expected = o.optString("expected")) }
        }
    }
    return records.values.toList()
}

/** Writes records as JSON lines, the format of the uploaded daily log files. */
fun chatLogJsonl(records: List<ChatLogRecord>): String = records.joinToString("") { r ->
    JSONObject()
        .put("ts", r.timestamp)
        .put("role", if (r.isUser) "user" else "assistant")
        .put("text", r.text)
        .put("meta", r.meta)
        .apply { if (r.flagged) put("markedWrong", true).put("expected", r.expected) }
        .toString() + "\n"
}

/** Groups records by local day ("yyyy-MM-dd"), keeping only days at or after the day of [since]. */
fun chatLogDays(records: List<ChatLogRecord>, since: Long, zone: java.util.TimeZone = java.util.TimeZone.getDefault()): Map<String, List<ChatLogRecord>> {
    val fmt = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).apply { timeZone = zone }
    val firstDay = if (since > 0) fmt.format(java.util.Date(since)) else ""
    return records.groupBy { fmt.format(java.util.Date(it.timestamp)) }
        .filterKeys { it >= firstDay }
        .toSortedMap()
}

/**
 * Sends the chat log to a private GitHub repository the owner chose, one file per day, so wrong
 * answers can be reviewed and fixed without the person marking them. Only runs when the owner has
 * saved their own token in Settings; the token never ships inside the app.
 */
object ChatLogUploader {
    /** Uploads every day changed since [since]. Returns a short status line. */
    suspend fun upload(repo: String, token: String, records: List<ChatLogRecord>, since: Long): Result<String> =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching {
                val days = chatLogDays(records, since)
                days.forEach { (day, list) -> putFile(repo, token, "chat-logs/$day.jsonl", chatLogJsonl(list), "Chat log $day") }
                if (days.isEmpty()) "Nothing new to send" else "Sent ${days.values.sumOf { it.size }} messages"
            }
        }

    private fun putFile(repo: String, token: String, path: String, content: String, message: String) {
        val url = "https://api.github.com/repos/$repo/contents/$path"
        val sha = request("GET", url, token, null).let { (code, body) ->
            when (code) {
                200 -> JSONObject(body).optString("sha")
                404 -> ""
                else -> error(describe(code))
            }
        }
        val payload = JSONObject()
            .put("message", message)
            .put("content", android.util.Base64.encodeToString(content.toByteArray(Charsets.UTF_8), android.util.Base64.NO_WRAP))
            .apply { if (sha.isNotBlank()) put("sha", sha) }
        val (code, _) = request("PUT", url, token, payload.toString())
        if (code !in 200..201) error(describe(code))
    }

    private fun describe(code: Int) = when (code) {
        401 -> "Token is wrong or expired"
        403 -> "Token has no write access to this repository"
        404 -> "Repository not found (check the name and token access)"
        else -> "GitHub error $code"
    }

    private fun request(method: String, url: String, token: String, body: String?): Pair<Int, String> {
        val conn = java.net.URL(url).openConnection() as java.net.HttpURLConnection
        conn.requestMethod = method
        conn.connectTimeout = 15_000
        conn.readTimeout = 20_000
        conn.setRequestProperty("Accept", "application/vnd.github+json")
        conn.setRequestProperty("Authorization", "Bearer $token")
        try {
            if (body != null) {
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json")
                conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }
            val code = conn.responseCode
            val text = (if (code < 400) conn.inputStream else conn.errorStream)?.bufferedReader()?.use { it.readText() } ?: ""
            return code to text
        } finally {
            conn.disconnect()
        }
    }
}
