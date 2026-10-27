package com.bolkhata.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

enum class Type(val label: String) {
    KHARCHA("Cash Out"),
    AAMDANI("Cash In"),
    DIYA("Lent"),
    LIYA("Borrowed"),
    WAPIS_MILA("Received back"),
    WAPIS_DIYA("Paid back"),
    SALE("Sale"),
    PURCHASE("Purchase")
}

enum class Cat(val label: String, val icon: String) {
    KHANA("Food", "🍛"),
    TRANSPORT("Transport", "🚗"),
    RENT("Rent", "🏠"),
    BIJLI("Bills", "💡"),
    MOBILE("Mobile & Internet", "📱"),
    GHAR("Sent home", "💸"),
    SHOPPING("Shopping", "🛍️"),
    SEHAT("Health", "💊"),
    PERSONAL("Personal care", "💈"),
    TANKHWAH("Cash In", "💰"),
    UDHAAR("Loans", "🤝"),
    FEES("Fees & Govt", "🏛️"),
    EDU("Education", "📚"),
    CHARITY("Gifts & Charity", "🎁"),
    ASSETS("Assets", "🏷️"),
    SALES("Sales", "🧾"),
    STOCK("Purchases", "📦"),
    OTHER("Other", "📝")
}

val CURRENCIES = listOf("SAR", "PKR", "AED", "QAR", "KWD", "OMR", "BHD", "INR", "USD", "EUR", "GBP")

data class Entry(
    val id: String,
    val type: Type,
    val amount: Double,
    val category: Cat,
    val item: String,
    val person: String,
    val note: String,
    val date: Long,
    val createdAt: Long,
    /** How many, e.g. 1000 bricks or 2 kg. 0 when not said. */
    val qty: Double = 0.0,
    /** Unit for [qty], e.g. "kg", "pcs". Empty when not said. */
    val unit: String = "",
    /** Price per unit (or per 1000 for bricks) when said. 0 when not said. */
    val rate: Double = 0.0,
    /** Sale or purchase with a person that was settled in cash, so it does not go in the khata. */
    val paid: Boolean = false
)

data class ChatMessage(
    val id: String,
    val isUser: Boolean,
    val text: String,
    val timestamp: Long = System.currentTimeMillis(),
    /** Entries this message saved, shown as a card with Undo. */
    val entryIds: List<String> = emptyList()
)

/** Saves entries on the phone itself (no internet needed). */
class Store(context: Context) {
    private val prefs = context.getSharedPreferences("bolkhata", Context.MODE_PRIVATE)

    fun load(): List<Entry> = read("entries")

    // ---------- Backup: everything the app keeps, in one file ----------

    /** Keys left out of a backup: AI keys are private and stay on this phone. */
    private val notBackedUp = setOf("aiKey", "fastKey", "aiModel")

    /** All saved data (entries, trash, chat, memory, settings) as one JSON text. */
    fun exportJson(): String {
        val data = JSONObject()
        for ((k, v) in prefs.all) {
            if (k in notBackedUp || v == null) continue
            val item = when (v) {
                is String -> JSONObject().put("t", "s").put("v", v)
                is Boolean -> JSONObject().put("t", "b").put("v", v)
                is Int -> JSONObject().put("t", "i").put("v", v)
                is Long -> JSONObject().put("t", "l").put("v", v)
                is Float -> JSONObject().put("t", "f").put("v", v.toDouble())
                is Set<*> -> JSONObject().put("t", "set").put("v", JSONArray(v.map { it.toString() }))
                else -> null
            } ?: continue
            data.put(k, item)
        }
        return JSONObject()
            .put("app", "Smart Expense")
            .put("backup", 1)
            .put("at", System.currentTimeMillis())
            .put("entries", backupEntryCount(data))
            .put("data", data)
            .toString()
    }

    private fun backupEntryCount(data: JSONObject): Int =
        runCatching { JSONArray(data.getJSONObject("entries").getString("v")).length() }.getOrDefault(0)

    /** How many entries a backup file holds, or -1 if it is not a Smart Expense backup. */
    fun backupEntries(text: String): Int = runCatching {
        val o = JSONObject(text)
        if (o.optString("app") != "Smart Expense" || !o.has("data")) -1 else backupEntryCount(o.getJSONObject("data"))
    }.getOrDefault(-1)

    /** Replaces all saved data with a backup (AI keys on this phone are kept). */
    fun importJson(text: String) {
        val data = JSONObject(text).getJSONObject("data")
        val keep = notBackedUp.associateWith { prefs.all[it] }
        val ed = prefs.edit().clear()
        for (k in data.keys()) {
            val item = data.getJSONObject(k)
            when (item.optString("t")) {
                "s" -> ed.putString(k, item.getString("v"))
                "b" -> ed.putBoolean(k, item.getBoolean("v"))
                "i" -> ed.putInt(k, item.getInt("v"))
                "l" -> ed.putLong(k, item.getLong("v"))
                "f" -> ed.putFloat(k, item.getDouble("v").toFloat())
                "set" -> {
                    val a = item.getJSONArray("v")
                    ed.putStringSet(k, (0 until a.length()).map { a.getString(it) }.toSet())
                }
            }
        }
        keep.forEach { (k, v) -> if (v is String) ed.putString(k, v) }
        ed.commit()
    }

    /** Deleted entries; createdAt is reused as the time it was deleted. Kept for 30 days. */
    fun loadTrash(): List<Entry> {
        val cutoff = System.currentTimeMillis() - 30L * 86_400_000L
        val times = trashTimes
        return read("trash").filter { (times[it.id] ?: now()) > cutoff }
    }

    fun saveTrash(list: List<Entry>) = write("trash", list)

    private val trashTimes: Map<String, Long>
        get() = try {
            val o = JSONObject(prefs.getString("trashTimes", "{}") ?: "{}")
            o.keys().asSequence().associateWith { o.getLong(it) }
        } catch (e: Exception) {
            emptyMap()
        }

    fun markTrashed(ids: Collection<String>) {
        val o = try { JSONObject(prefs.getString("trashTimes", "{}") ?: "{}") } catch (e: Exception) { JSONObject() }
        val t = now()
        ids.forEach { o.put(it, t) }
        prefs.edit().putString("trashTimes", o.toString()).apply()
    }

    private fun now() = System.currentTimeMillis()

    /** Payments found in bank notifications, waiting for the person to add or ignore. */
    fun loadSuggestions(): List<Entry> = read("suggestions")

    fun saveSuggestions(list: List<Entry>) = write("suggestions", list)

    /** Chat history stays on this phone, just like the khata entries. */
    fun loadChatMessages(): List<ChatMessage> = try {
        val arr = JSONArray(prefs.getString("chatMessages", "[]") ?: "[]")
        (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i)
            if (o == null) null else {
                val text = o.optString("text")
                if (text.isBlank()) null else ChatMessage(
                    id = o.optString("id").ifBlank { "chat-" + i + "-" + o.optLong("timestamp", now()) },
                    isUser = o.optBoolean("isUser"),
                    text = text,
                    timestamp = o.optLong("timestamp", now()),
                    entryIds = o.optJSONArray("entryIds")?.let { ids ->
                        (0 until ids.length()).map { ids.optString(it) }.filter { it.isNotBlank() }
                    } ?: emptyList()
                )
            }
        }.takeLast(100)
    } catch (e: Exception) {
        emptyList()
    }

    fun saveChatMessages(messages: List<ChatMessage>) {
        val arr = JSONArray()
        messages.takeLast(100).forEach { message ->
            arr.put(
                JSONObject()
                    .put("id", message.id)
                    .put("isUser", message.isUser)
                    .put("text", message.text.take(8000))
                    .put("timestamp", message.timestamp)
                    .put("entryIds", JSONArray(message.entryIds))
            )
        }
        prefs.edit().putString("chatMessages", arr.toString()).apply()
    }

    /** Adds a suggestion unless the same notification was already seen. */
    @Synchronized
    fun addSuggestion(e: Entry, key: String) {
        val seen = (prefs.getString("seenBank", "") ?: "").split("\n").filter { it.isNotEmpty() }
        val k = key.hashCode().toString()
        if (k in seen) return
        prefs.edit().putString("seenBank", (seen.takeLast(300) + k).joinToString("\n")).apply()
        saveSuggestions(listOf(e) + loadSuggestions())
    }

    /** Calls [onChange] whenever suggestions change. Returns a function that stops listening. */
    fun watchSuggestions(onChange: () -> Unit): () -> Unit {
        val l = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == "suggestions") onChange()
        }
        prefs.registerOnSharedPreferenceChangeListener(l)
        return { prefs.unregisterOnSharedPreferenceChangeListener(l) }
    }

    private fun read(key: String): List<Entry> {
        val raw = prefs.getString(key, "[]") ?: "[]"
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.getJSONObject(i)
                try {
                    val cat = Cat.valueOf(o.getString("category"))
                    Entry(
                        id = o.getString("id"),
                        type = Type.valueOf(o.getString("type")),
                        amount = o.getDouble("amount"),
                        category = cat,
                        item = o.optString("item", "").ifBlank { cat.label },
                        person = o.optString("person", ""),
                        note = o.optString("note", ""),
                        date = o.getLong("date"),
                        createdAt = o.getLong("createdAt"),
                        qty = o.optDouble("qty", 0.0).let { if (it.isNaN()) 0.0 else it },
                        unit = o.optString("unit", ""),
                        rate = o.optDouble("rate", 0.0).let { if (it.isNaN()) 0.0 else it },
                        paid = o.optBoolean("paid", false)
                    )
                } catch (e: Exception) {
                    null
                }
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun save(list: List<Entry>) = write("entries", list)

    private fun write(key: String, list: List<Entry>) {
        val arr = JSONArray()
        list.forEach { e ->
            arr.put(
                JSONObject()
                    .put("id", e.id)
                    .put("type", e.type.name)
                    .put("amount", e.amount)
                    .put("category", e.category.name)
                    .put("item", e.item)
                    .put("person", e.person)
                    .put("note", e.note)
                    .put("date", e.date)
                    .put("createdAt", e.createdAt)
                    .put("qty", e.qty)
                    .put("unit", e.unit)
                    .put("rate", e.rate)
                    .put("paid", e.paid)
            )
        }
        prefs.edit().putString(key, arr.toString()).apply()
    }

    /** Voice language. Hindi voice understands spoken Urdu best; its letters are turned into Roman Urdu. */
    var language: String
        get() {
            // Move everyone from the old English voice to the better Hindi voice once.
            if (!prefs.getBoolean("langV2", false)) {
                prefs.edit().putBoolean("langV2", true).putString("lang", "hi-IN").apply()
            }
            return prefs.getString("lang", "hi-IN") ?: "hi-IN"
        }
        set(v) = prefs.edit().putString("lang", v).apply()

    var currency: String
        get() = prefs.getString("currency", "SAR") ?: "SAR"
        set(v) = prefs.edit().putString("currency", v).apply()

    var name: String
        get() = prefs.getString("name", "") ?: ""
        set(v) = prefs.edit().putString("name", v).apply()

    /** Words the person taught the app by correcting a category, e.g. "shawarma" -> Food. */
    fun loadLearned(): Map<String, Pair<Cat, String>> = try {
        val o = JSONObject(prefs.getString("learned", "{}") ?: "{}")
        o.keys().asSequence().mapNotNull { k ->
            val v = o.getString(k).split("|", limit = 2)
            try { k to (Cat.valueOf(v[0]) to v.getOrElse(1) { "" }) } catch (e: Exception) { null }
        }.toMap()
    } catch (e: Exception) {
        emptyMap()
    }

    fun learn(words: List<String>, cat: Cat, item: String) {
        if (words.isEmpty()) return
        val o = try { JSONObject(prefs.getString("learned", "{}") ?: "{}") } catch (e: Exception) { JSONObject() }
        words.forEach { o.put(it, cat.name + "|" + item) }
        prefs.edit().putString("learned", o.toString()).apply()
    }

    var aiKey: String
        get() = prefs.getString("aiKey", "") ?: ""
        set(v) = prefs.edit().putString("aiKey", v).apply()

    var aiModel: String
        get() = prefs.getString("aiModel", "") ?: ""
        set(v) = prefs.edit().putString("aiModel", v).apply()

    var theme: String
        get() = prefs.getString("theme", "Sand") ?: "Sand"
        set(v) = prefs.edit().putString("theme", v).apply()

    var dark: Boolean
        get() = prefs.getBoolean("dark", false)
        set(v) = prefs.edit().putBoolean("dark", v).apply()
}
