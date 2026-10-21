package com.bolkhata.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

enum class Type(val label: String) {
    KHARCHA("Expense"),
    AAMDANI("Income"),
    DIYA("Lent"),
    LIYA("Borrowed"),
    WAPIS_MILA("Received back"),
    WAPIS_DIYA("Paid back")
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
    TANKHWAH("Income", "💰"),
    UDHAAR("Loans", "🤝"),
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
    val createdAt: Long
)

/** Saves entries on the phone itself (no internet needed). */
class Store(context: Context) {
    private val prefs = context.getSharedPreferences("bolkhata", Context.MODE_PRIVATE)

    fun load(): List<Entry> = read("entries")

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
                        createdAt = o.getLong("createdAt")
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
            )
        }
        prefs.edit().putString(key, arr.toString()).apply()
    }

    var language: String
        get() = prefs.getString("lang", "en-IN") ?: "en-IN"
        set(v) = prefs.edit().putString("lang", v).apply()

    var currency: String
        get() = prefs.getString("currency", "SAR") ?: "SAR"
        set(v) = prefs.edit().putString("currency", v).apply()

    var name: String
        get() = prefs.getString("name", "") ?: ""
        set(v) = prefs.edit().putString("name", v).apply()

    var dark: Boolean
        get() = prefs.getBoolean("dark", false)
        set(v) = prefs.edit().putBoolean("dark", v).apply()
}
