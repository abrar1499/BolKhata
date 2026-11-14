package com.bolkhata.app

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * The shared brain: lessons (a message's shape and what to do for it, see AiAgent.learn) kept in brain.json on the
 * repository's "brain" branch. Every install reads it each time it opens, so the app gets better without a new version.
 * The file holds only shapes with open places for names and numbers, never anyone's names or chat.
 */
object Brain {
    const val URL = "https://raw.githubusercontent.com/abrar1499/BolKhata/brain/brain.json"

    /** The lessons in the shared file ({"lessons": {shape: lesson}}); null when it could not be read. */
    fun fetch(): Map<String, String>? = runCatching {
        val conn = URL(URL).openConnection() as HttpURLConnection
        conn.connectTimeout = 8_000
        conn.readTimeout = 15_000
        if (conn.responseCode != 200) return@runCatching null
        val lessons = JSONObject(conn.inputStream.bufferedReader().use { it.readText() }).optJSONObject("lessons") ?: JSONObject()
        lessons.keys().asSequence().associateWith { lessons.getString(it) }
    }.getOrNull()
}
