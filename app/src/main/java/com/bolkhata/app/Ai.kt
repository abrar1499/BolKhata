package com.bolkhata.app

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

/**
 * TEST FEATURE (APK build only): one AI key of any provider runs the chat instead of the built-in Groq setup.
 * The key's shape tells the provider (Claude, Groq, OpenRouter, Gemini, ChatGPT). [Groq] keeps its prompts and checks
 * and hands its request here; this file only talks to the provider and counts tokens in and out.
 *
 * To remove it: delete this file, the two "Ai." lines in Groq (post, takeUsage), the AI row in SideMenu,
 * Store.llm* and the "Ai." lines in MainActivity.
 */
object Ai {
    class Provider(val name: String, val url: String, val model: String)

    val CLAUDE = Provider("Claude", "https://api.anthropic.com/v1/messages", "claude-opus-5-5")
    val GROQ = Provider("Groq", "https://api.groq.com/openai/v1/chat/completions", "openai/gpt-oss-120b")
    val OPENROUTER = Provider("OpenRouter", "https://openrouter.ai/api/v1/chat/completions", "anthropic/claude-opus-5.5")
    val GEMINI = Provider("Gemini", "https://generativelanguage.googleapis.com/v1beta/openai/chat/completions", "gemini-2.5-flash")
    val OPENAI = Provider("ChatGPT", "https://api.openai.com/v1/chat/completions", "gpt-5-mini")

    /** The provider a key belongs to, by how the key starts; null when it is not a known kind. */
    fun provider(key: String): Provider? = key.trim().let { k ->
        when {
            k.startsWith("sk-ant-") -> CLAUDE
            k.startsWith("gsk_") -> GROQ
            k.startsWith("sk-or-") -> OPENROUTER
            k.startsWith("AIza") -> GEMINI
            k.startsWith("sk-") -> OPENAI
            else -> null
        }
    }

    /** The saved key and model (blank model: the provider's usual one); set by the app when it opens and on Save. */
    @Volatile var key = ""
    @Volatile var model = ""
    /** Told the tokens of every call, to keep the running total. */
    @Volatile var sink: ((input: Int, output: Int) -> Unit)? = null

    fun handles(k: String) = key.isNotBlank() && k == key
    fun modelNow(): String = model.ifBlank { provider(key)?.model.orEmpty() }
    /** Only Groq has the speech-to-text the mic and the call use. */
    fun hasVoice(k: String) = k.isNotBlank() && (!handles(k) || provider(k) === GROQ)

    /** US dollars per million tokens (in, out) for the Claude models; null when the price is not known here. */
    fun price(model: String): Pair<Double, Double>? = when {
        model.startsWith("claude-fable-5-2") -> 8.0 to 40.0
        model.startsWith("claude-opus-5-5") -> 4.0 to 20.0
        model.startsWith("claude-opus-5") -> 5.0 to 25.0
        model.startsWith("claude-sonnet-5") -> 2.0 to 10.0
        model.startsWith("claude-haiku-5-5") -> 0.10 to 0.50
        model.startsWith("claude-haiku-4-5") -> 1.0 to 5.0
        else -> null
    }

    /** "340 in · 120 out · $0.0038" (no price part when it is not known). */
    fun usageText(input: Long, output: Long, model: String = modelNow()): String =
        "%,d in · %,d out".format(Locale.US, input, output) +
            (price(model)?.let { (i, o) -> " · $" + String.format(Locale.US, "%.4f", (input * i + output * o) / 1_000_000) } ?: "")

    private val usedIn = java.util.concurrent.atomic.AtomicInteger()
    private val usedOut = java.util.concurrent.atomic.AtomicInteger()

    /** What the calls since the last ask used, as "Claude · 1.2s · 340 in · 120 out · $0.0038"; null when this key made none. */
    fun takeUsage(ms: Long): String? {
        val i = usedIn.getAndSet(0)
        val o = usedOut.getAndSet(0)
        if (i == 0 && o == 0) return null
        return (provider(key)?.name ?: "AI") + " · " + String.format(Locale.US, "%.1fs", ms / 1000.0) + " · " + usageText(i.toLong(), o.toLong())
    }

    /**
     * Sends a chat request written the OpenAI way ([body]: model, messages, max_tokens, response_format) to the key's
     * provider and gives the answer back in the same OpenAI shape, so the caller reads every provider alike.
     */
    fun post(body: ByteArray): Pair<Int, String> {
        val p = provider(key) ?: return 401 to ""
        val o = JSONObject(String(body, Charsets.UTF_8))
        val wantsJson = o.has("response_format")
        val (code, text) = if (p === CLAUDE) claude(o) else openAiLike(p, o)
        if (code !in 200..299) return code to text
        val r = JSONObject(text)
        val content = r.optString("content").let { c ->
            // Asked for JSON: keep only the object, in case the model wrapped it in a code fence or a sentence.
            if (wantsJson && c.contains('{') && c.contains('}')) c.substring(c.indexOf('{'), c.lastIndexOf('}') + 1) else c
        }
        val i = r.optInt("in")
        val out = r.optInt("out")
        count(i, out)
        return code to JSONObject().put("choices", JSONArray().put(JSONObject().put("message", JSONObject().put("content", content))))
            .put("usage", JSONObject().put("total_tokens", i + out)).toString()
    }

    // ---------- Tools: the AI works the app itself (see AiAgent) ----------

    class Tool(val name: String, val description: String, val schema: JSONObject)
    class Call(val id: String, val name: String, val input: JSONObject)
    /** One answer of the model: its text, and the tools it wants run before it goes on. */
    class Step(val text: String, val calls: List<Call>)

    /** The conversation so far in the provider's own format; starts with what the person said. */
    fun opening(said: String): JSONArray = JSONArray().put(JSONObject().put("role", "user").put("content", said))

    /** Asks the model for its next step and adds its turn to [messages]; null when the provider failed or declined. */
    fun step(system: String, tools: List<Tool>, messages: JSONArray): Step? {
        val p = provider(key) ?: return null
        val m = modelNow()
        if (p === CLAUDE) {
            val req = JSONObject().put("model", m).put("max_tokens", 4000).put("system", system).put("messages", messages)
                .put("tools", JSONArray().apply { tools.forEach { put(JSONObject().put("name", it.name).put("description", it.description).put("input_schema", it.schema)) } })
                // The instructions and tools are the same every time: cached, they cost a fraction on the next call.
                .put("cache_control", JSONObject().put("type", "ephemeral"))
            if (!m.contains("haiku-4") && !m.contains("sonnet-4-5")) req.put("output_config", JSONObject().put("effort", "low"))
            val fallback = listOf("claude-fable-5", "claude-opus-5", "claude-sonnet-5-5").any { m.startsWith(it) }
            if (fallback) req.put("fallbacks", "default")
            val headers = mutableMapOf("x-api-key" to key, "anthropic-version" to "2026-10-21")
            if (fallback) headers["anthropic-beta"] = "server-side-fallback-2026-07-01"
            val (code, text) = send(CLAUDE.url, headers, req.toString())
            if (code !in 200..299) { lastError = "$code " + errorOf(text); return null }
            val r = JSONObject(text)
            val usage = r.optJSONObject("usage") ?: JSONObject()
            count(usage.optInt("input_tokens") + usage.optInt("cache_creation_input_tokens") + usage.optInt("cache_read_input_tokens"), usage.optInt("output_tokens"))
            if (r.optString("stop_reason") == "refusal") { lastError = "declined"; return null }
            // The whole turn goes back unchanged (thinking blocks included), as the API asks.
            val blocks = r.optJSONArray("content") ?: JSONArray()
            messages.put(JSONObject().put("role", "assistant").put("content", blocks))
            val all = (0 until blocks.length()).map { blocks.getJSONObject(it) }
            return Step(
                all.filter { it.optString("type") == "text" }.joinToString("") { it.optString("text") }.trim(),
                all.filter { it.optString("type") == "tool_use" }.map { Call(it.optString("id"), it.optString("name"), it.optJSONObject("input") ?: JSONObject()) }
            )
        }
        val req = JSONObject().put("model", m)
            .put("messages", JSONArray().put(JSONObject().put("role", "system").put("content", system)).apply { for (i in 0 until messages.length()) put(messages.get(i)) })
            .put("tools", JSONArray().apply { tools.forEach { put(JSONObject().put("type", "function").put("function", JSONObject().put("name", it.name).put("description", it.description).put("parameters", it.schema))) } })
            .put(if (p === OPENAI) "max_completion_tokens" else "max_tokens", 4000)
        val (code, text) = send(p.url, mapOf("Authorization" to "Bearer $key"), req.toString())
        if (code !in 200..299) { lastError = "$code " + errorOf(text); return null }
        val r = JSONObject(text)
        val usage = r.optJSONObject("usage") ?: JSONObject()
        count(usage.optInt("prompt_tokens"), usage.optInt("completion_tokens"))
        val message = r.getJSONArray("choices").getJSONObject(0).getJSONObject("message")
        messages.put(message)
        val calls = message.optJSONArray("tool_calls") ?: JSONArray()
        return Step(
            (if (message.isNull("content")) "" else message.optString("content")).trim(),
            (0 until calls.length()).map { calls.getJSONObject(it) }.map { c ->
                val f = c.optJSONObject("function") ?: JSONObject()
                Call(c.optString("id"), f.optString("name"), runCatching { JSONObject(f.optString("arguments")) }.getOrDefault(JSONObject()))
            }
        )
    }

    /** Adds what the tools gave back to [messages], each result with its call. */
    fun results(messages: JSONArray, done: List<Pair<Call, String>>) {
        if (provider(key) === CLAUDE) messages.put(JSONObject().put("role", "user").put("content", JSONArray().apply {
            done.forEach { (c, out) -> put(JSONObject().put("type", "tool_result").put("tool_use_id", c.id).put("content", out)) }
        }))
        else done.forEach { (c, out) -> messages.put(JSONObject().put("role", "tool").put("tool_call_id", c.id).put("content", out)) }
    }

    /** Why the last call failed, for the chat's small line. */
    @Volatile var lastError = ""

    private fun errorOf(text: String) = runCatching {
        JSONObject(text).optJSONObject("error")?.optString("message") ?: JSONArray(text).getJSONObject(0).getJSONObject("error").optString("message")
    }.getOrDefault("").take(120)

    private fun count(input: Int, output: Int) {
        usedIn.addAndGet(input)
        usedOut.addAndGet(output)
        sink?.invoke(input, output)
    }

    /** A tiny request to check a new key: "" when it works, else what the provider said. */
    fun test(): String = runCatching {
        val (code, text) = post(JSONObject().put("max_tokens", 50).put("messages", JSONArray()
            .put(JSONObject().put("role", "user").put("content", "Reply with the single word OK."))).toString().toByteArray())
        if (code in 200..299) "" else "$code " + errorOf(text)
    }.getOrElse { it.message ?: "no internet" }

    /** Claude's Messages API: the system text apart, thinking kept light for a quick chat answer. */
    private fun claude(o: JSONObject): Pair<Int, String> {
        val m = modelNow()
        val system = StringBuilder()
        val messages = JSONArray()
        o.getJSONArray("messages").let { a ->
            for (n in 0 until a.length()) a.getJSONObject(n).let { x ->
                if (x.optString("role") == "system") system.append(x.optString("content")) else messages.put(JSONObject().put("role", x.optString("role")).put("content", x.optString("content")))
            }
        }
        // Thinking tokens count towards max_tokens, so the short limits written for Groq get more room.
        val req = JSONObject().put("model", m).put("max_tokens", maxOf(o.optInt("max_tokens", 1000), 4000)).put("messages", messages)
        if (system.isNotEmpty()) req.put("system", system.toString())
        if (!m.contains("haiku-4") && !m.contains("sonnet-4-5")) req.put("output_config", JSONObject().put("effort", "low"))
        // If a safety check declines the request, Anthropic re-runs it on a fallback model inside the same call.
        val fallback = listOf("claude-fable-5", "claude-opus-5", "claude-sonnet-5-5").any { m.startsWith(it) }
        if (fallback) req.put("fallbacks", "default")
        val headers = mutableMapOf("x-api-key" to key, "anthropic-version" to "2026-10-21")
        if (fallback) headers["anthropic-beta"] = "server-side-fallback-2026-07-01"
        val (code, text) = send(CLAUDE.url, headers, req.toString())
        if (code !in 200..299) return code to text
        val r = JSONObject(text)
        // Declined after all: the app answers by itself.
        if (r.optString("stop_reason") == "refusal") return 422 to text
        val blocks = r.optJSONArray("content") ?: JSONArray()
        val content = (0 until blocks.length()).map { blocks.getJSONObject(it) }.filter { it.optString("type") == "text" }.joinToString("") { it.optString("text") }
        val usage = r.optJSONObject("usage") ?: JSONObject()
        val input = usage.optInt("input_tokens") + usage.optInt("cache_creation_input_tokens") + usage.optInt("cache_read_input_tokens")
        return code to JSONObject().put("content", content).put("in", input).put("out", usage.optInt("output_tokens")).toString()
    }

    /** Groq, OpenRouter, Gemini and ChatGPT all take the OpenAI chat format. */
    private fun openAiLike(p: Provider, o: JSONObject): Pair<Int, String> {
        o.put("model", modelNow())
        if (p !== GROQ) {
            // These settings were written for Groq's model; other models refuse some of them or need more room to think.
            o.remove("temperature")
            o.remove("reasoning_effort")
            val max = maxOf(o.optInt("max_tokens", 1000), 4000)
            o.remove("max_tokens")
            o.put(if (p === OPENAI) "max_completion_tokens" else "max_tokens", max)
        }
        val (code, text) = send(p.url, mapOf("Authorization" to "Bearer $key"), o.toString())
        if (code !in 200..299) return code to text
        val r = JSONObject(text)
        val usage = r.optJSONObject("usage") ?: JSONObject()
        return code to JSONObject().put("content", r.getJSONArray("choices").getJSONObject(0).getJSONObject("message").optString("content"))
            .put("in", usage.optInt("prompt_tokens")).put("out", usage.optInt("completion_tokens")).toString()
    }

    private fun send(url: String, headers: Map<String, String>, body: String): Pair<Int, String> {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.connectTimeout = 8_000
        // Longer than this and the app answers by itself, so the chat never hangs.
        conn.readTimeout = 30_000
        conn.doOutput = true
        conn.setRequestProperty("Content-Type", "application/json")
        headers.forEach { (k, v) -> conn.setRequestProperty(k, v) }
        try {
            conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            return code to ((if (code < 400) conn.inputStream else conn.errorStream)?.bufferedReader()?.use { it.readText() } ?: "")
        } finally {
            conn.disconnect()
        }
    }
}
