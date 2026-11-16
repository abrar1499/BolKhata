package com.bolkhata.app

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * The shared brain: lessons (a message's shape and what to do for it, see AiAgent.learn) in brain.json of the public
 * repository abrar1499/bolkhata-brain. Every install reads it each time it opens, so the app gets better without a new
 * version. Every phone that learns from the AI adds it by itself (with the app's token for that repository only), but
 * only lessons [safe] to share: open places for names and numbers, never anyone's name or figures. What is read is
 * checked the same way ([clean]), so a bad line in the file does nothing.
 */
object Brain {
    private const val REPO = "abrar1499/bolkhata-brain"
    const val URL = "https://raw.githubusercontent.com/$REPO/main/brain.json"
    private const val API = "https://api.github.com/repos/$REPO/contents/brain.json"

    /** Whether the owner's built-in AI key answers for everyone ("ai" in the shared file; on unless the owner turned it off). */
    @Volatile var aiForAll = true

    /** The lessons in the shared file ({"lessons": {shape: lesson}, "ai": true}); null when it could not be read. Reads [aiForAll] and the words (see [Lexicon]) too. */
    fun fetch(): Map<String, String>? = runCatching {
        // GitHub keeps a copy of the file for 5 minutes; asking with the minute in the address gets one at most a minute old.
        val conn = URL(URL + "?m=" + System.currentTimeMillis() / 60_000).openConnection() as HttpURLConnection
        conn.connectTimeout = 8_000
        conn.readTimeout = 15_000
        if (conn.responseCode != 200) return@runCatching null
        val o = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
        aiForAll = o.optBoolean("ai", true)
        o.optJSONObject("words")?.let { Lexicon.load(it.toString()) }
        clean(lessonsOf(o))
    }.getOrNull()

    /** Turns the owner's built-in AI on or off for every install (they read it when they open); false when GitHub could not be reached. */
    fun setAiForAll(token: String, on: Boolean): Boolean = runCatching {
        val (code, text) = call("GET", "$API?ref=main", token, null)
        if (code != 200) return@runCatching false
        val now = JSONObject(text)
        val file = JSONObject(String(android.util.Base64.decode(now.getString("content"), android.util.Base64.DEFAULT)))
        file.put("ai", on).put("version", file.optInt("version") + 1)
        val body = JSONObject().put("message", "AI for everyone: " + if (on) "on" else "off").put("branch", "main").put("sha", now.getString("sha"))
            .put("content", android.util.Base64.encodeToString(file.toString(1).toByteArray(), android.util.Base64.NO_WRAP))
        val ok = call("PUT", API, token, body.toString()).first in 200..201
        if (ok) aiForAll = on
        ok
    }.getOrDefault(false)

    /** Only the lessons of the shared file that are [safe]: anyone's bad line in it is left out. */
    fun clean(lessons: Map<String, String>): Map<String, String> = lessons.filter { (shape, lesson) -> safe(shape, lesson) }

    /** The app's own token for the brain repository as the build wrote it (BuildConfig.BRAIN_TOKEN: backwards, in Base64). */
    fun unhide(hidden: String): String = runCatching { String(android.util.Base64.decode(hidden, android.util.Base64.DEFAULT)).reversed() }.getOrDefault("")

    private fun lessonsOf(o: JSONObject): Map<String, String> =
        (o.optJSONObject("lessons") ?: JSONObject()).let { l -> l.keys().asSequence().associateWith { l.getString(it) } }

    private fun words(text: String) = text.lowercase().split(Regex("""[^\p{L}\p{N}#@.]+""")).filter { it.isNotBlank() }

    /** Every text value in a lesson's JSON (not the keys), to check each word. */
    private fun texts(v: Any?): List<String> = when (v) {
        is JSONObject -> v.keys().asSequence().flatMap { k -> texts(v.get(k)) }.toList()
        is JSONArray -> (0 until v.length()).flatMap { texts(v.get(it)) }
        is String -> listOf(v)
        else -> emptyList()
    }

    /** The people in a lesson ("person", "persons"): each must be an open place (@0), never a name written out. */
    private fun people(v: Any?): List<String> = when (v) {
        is JSONObject -> v.keys().asSequence().flatMap { k -> if (k == "person" || k == "persons") texts(v.get(k)) else people(v.get(k)) }.toList()
        is JSONArray -> (0 until v.length()).flatMap { people(v.get(it)) }
        else -> emptyList()
    }

    /** Takes the lessons for [shapes] out of the shared file: an answer marked ✗ is not given to anyone again. False when GitHub could not be reached. */
    fun forget(token: String, shapes: Collection<String>): Boolean = runCatching {
        val (code, text) = call("GET", "$API?ref=main", token, null)
        if (code != 200) return@runCatching false
        val now = JSONObject(text)
        val file = JSONObject(String(android.util.Base64.decode(now.getString("content"), android.util.Base64.DEFAULT)))
        val lessons = file.optJSONObject("lessons") ?: return@runCatching true
        val gone = shapes.filter { lessons.has(it) }
        if (gone.isEmpty()) return@runCatching true
        gone.forEach { lessons.remove(it) }
        file.put("version", file.optInt("version") + 1)
        val body = JSONObject().put("message", "Brain: ${gone.size} lesson(s) marked wrong").put("branch", "main").put("sha", now.getString("sha"))
            .put("content", android.util.Base64.encodeToString(file.toString(1).toByteArray(), android.util.Base64.NO_WRAP))
        call("PUT", API, token, body.toString()).first in 200..201
    }.getOrDefault(false)

    /**
     * Whether a lesson may go to everyone: only tools that show, save or make a PDF (not ones that name an account or
     * reach into Trash), every person an open place, and no word that is part of a saved name ([names]), a common name
     * or a number of its own. So no one's name or figures go out.
     */
    fun safe(shape: String, lesson: String, names: List<String> = emptyList()): Boolean = runCatching {
        if (shape.isBlank() || shape.startsWith("=")) return@runCatching false
        val json: Any = if (lesson.trimStart().startsWith("[")) JSONArray(lesson) else JSONObject(lesson)
        val calls = (json as? JSONObject)?.optJSONArray("calls") ?: JSONArray()
        if ((0 until calls.length()).any { calls.getJSONObject(it).optString("name") !in setOf("show", "add_entries", "make_pdf") }) return@runCatching false
        if (people(json).any { !Regex("""@\d+""").matches(it.trim()) }) return@runCatching false
        val nameParts = names.flatMap { words(it) }.filter { it.length >= 3 }.toSet()
        (words(shape) + texts(json).flatMap { words(it) }).none { w ->
            w in nameParts || Understand.isCommonName(w) || (w.any { it.isDigit() } && !Regex("""[#@]\d+""").matches(w))
        }
    }.getOrDefault(false)

    /**
     * Adds the [learnt] lessons that are [safe] (checked against the saved [names]) and not yet in the shared file, with
     * the [token] for that repository only. Returns how many went in, or null when GitHub could not be reached.
     */
    fun publish(token: String, learnt: Map<String, String>, names: List<String>): Int? = runCatching {
        val (code, text) = call("GET", "$API?ref=main", token, null)
        val now = if (code == 200) JSONObject(text) else null
        val file = now?.let { JSONObject(String(android.util.Base64.decode(it.getString("content"), android.util.Base64.DEFAULT))) }
        val have = file?.let { lessonsOf(it) }.orEmpty()
        val add = learnt.filter { (s, l) -> have[s] != l && safe(s, l, names) }
        if (add.isEmpty()) return@runCatching 0
        val next = (file ?: JSONObject()).put("version", (file?.optInt("version") ?: 0) + 1).put("lessons", JSONObject(have + add))
        val body = JSONObject().put("message", "Brain: ${add.size} new lessons").put("branch", "main")
            .put("content", android.util.Base64.encodeToString(next.toString(1).toByteArray(), android.util.Base64.NO_WRAP))
            .apply { now?.optString("sha")?.takeIf { it.isNotBlank() }?.let { put("sha", it) } }
        val (put, _) = call("PUT", API, token, body.toString())
        if (put in 200..201) add.size else null
    }.getOrNull()

    private fun call(method: String, url: String, token: String, body: String?): Pair<Int, String> {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.requestMethod = method
        conn.connectTimeout = 10_000
        conn.readTimeout = 20_000
        conn.setRequestProperty("Accept", "application/vnd.github+json")
        conn.setRequestProperty("Authorization", "Bearer $token")
        if (body != null) {
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json")
            conn.outputStream.use { it.write(body.toByteArray()) }
        }
        val code = conn.responseCode
        return code to ((if (code < 400) conn.inputStream else conn.errorStream)?.bufferedReader()?.use { it.readText() } ?: "")
    }
}
