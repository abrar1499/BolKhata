package com.bolkhata.app

import java.util.Locale

/**
 * The side menu's settings as the chat names them ("currency PKR karo", "header green", "dark mode"): what each word
 * means, so the AI's app_setting tool and the app's own reader change the same thing.
 */
object AppSettings {
    /** The settings the chat can change, as the app_setting tool names them. */
    val NAMES = listOf("clear_chat", "currency", "language", "header", "background", "open", "business", "add_business", "backup")

    /** A currency code ("pkr", "riyal", "rupay"); null when it is not one. */
    fun currency(v: String): String? {
        val w = v.trim().lowercase(Locale.ROOT)
        currencyWords[w]?.let { return it }
        return w.uppercase(Locale.ROOT).takeIf { Regex("[A-Z]{3}").matches(it) }
    }

    private val currencyWords = mapOf(
        "riyal" to "SAR", "riyals" to "SAR", "saudi" to "SAR", "rupay" to "PKR", "rupees" to "PKR", "rupee" to "PKR", "pakistani" to "PKR",
        "dirham" to "AED", "dirhams" to "AED", "dollar" to "USD", "dollars" to "USD", "euro" to "EUR", "euros" to "EUR", "pound" to "GBP",
        "indian" to "INR", "taka" to "BDT", "qatari" to "QAR", "kuwaiti" to "KWD", "omani" to "OMR", "bahraini" to "BHD"
    )

    /** The language setting for the app's own languages ("" is Auto); null for another language (found by its name). */
    fun language(v: String): String? = when (v.trim().lowercase(Locale.ROOT)) {
        "auto", "automatic", "phone" -> ""
        "roman urdu", "roman", "urdu roman", "hinglish" -> "hi-IN"
        "english", "angrezi", "angraizi" -> "en-US"
        "urdu", "اردو" -> "ur-PK"
        "hindi", "हिन्दी", "हिंदी" -> "hi"
        else -> null
    }

    /** The header theme named by [v] among [names] ("laal" is Red, "hara" Green, "neela" Navy); null when none fits. */
    fun header(v: String, names: List<String>): String? {
        val w = v.trim().lowercase(Locale.ROOT)
        val said = headerWords[w] ?: w
        return names.firstOrNull { it.equals(said, true) }
    }

    private val headerWords = mapOf(
        "laal" to "red", "lal" to "red", "surkh" to "red", "hara" to "green", "sabz" to "green", "neela" to "navy", "nila" to "navy",
        "blue" to "navy", "white" to "clean", "safed" to "clean", "sufaid" to "clean", "plain" to "clean", "simple" to "clean", "kala" to "black"
    )

    /** The page look named by [v]: "light:0" warm grey, "light:1" light slate, "dark:0" graphite, "dark:1" navy; null when none fits. */
    fun background(v: String): String? {
        val w = v.trim().lowercase(Locale.ROOT)
        return when {
            "navy" in w || "neela" in w || "blue" in w -> "dark:1"
            "dark" in w || "graphite" in w || "kala" in w || "black" in w || "night" in w -> "dark:0"
            "slate" in w -> "light:1"
            "light" in w || "grey" in w || "gray" in w || "warm" in w || "default" in w || "normal" in w || "white" in w -> "light:0"
            else -> null
        }
    }

    /** The tab or page named by [v]; null when none fits. */
    fun page(v: String): String? {
        val w = v.trim().lowercase(Locale.ROOT)
        return pages.entries.firstOrNull { (_, words) -> words.any { it == w || w.startsWith("$it ") } }?.key
    }

    private val pages = mapOf(
        "chat" to listOf("chat"), "cashbook" to listOf("cashbook", "cash book"), "party" to listOf("party", "parties"),
        "expense" to listOf("expense", "expenses"), "income" to listOf("income"), "report" to listOf("report", "reports"),
        "trash" to listOf("trash"), "menu" to listOf("menu", "settings", "setting")
    )
}

/**
 * The side menu's settings said in the chat, read by the app itself (no AI): "chat clear karo", "currency PKR karo",
 * "language urdu", "header green karo", "dark mode", "report kholo". Anything longer goes on to the other readers.
 */
object LocalSettings : LocalAgent.Reader {
    private val clearWords = setOf("clear", "saaf", "saf", "delete", "mita", "mitao", "khatam", "hatao", "hata", "erase")
    private val doWords = setOf("karo", "kardo", "kar", "do", "dein", "den", "karein", "please", "plz", "ko", "ki", "ka", "set", "change",
        "badlo", "badal", "to", "the", "my", "mera", "meri", "is", "main", "me", "mein", "kr", "krdo", "kro", "on", "chalao", "laga", "lagao",
        "kholo", "khol", "open", "go", "jao", "par", "pe", "rang", "color", "colour", "mode", "tab", "page")

    override fun read(s: LocalAgent.Said): LocalAgent.Reply? {
        val w = s.words
        if (w.isEmpty() || w.size > 6) return null
        val rest = w.filter { it !in doWords }
        val (setting, value) = when {
            "chat" in w && w.any { it in clearWords } && rest.all { it == "chat" || it in clearWords || it == "history" } -> "clear_chat" to ""
            w.any { it in setOf("currency", "karansi", "kerensi", "currancy") } ->
                "currency" to (rest.firstNotNullOfOrNull { x -> AppSettings.currency(x).takeIf { x !in setOf("currency", "karansi", "kerensi", "currancy") } } ?: return null)
            w.any { it in setOf("language", "zaban", "zuban", "bhasha", "lang") } ->
                "language" to (rest.filter { it !in setOf("language", "zaban", "zuban", "bhasha", "lang") }.joinToString(" ").ifBlank { return null })
            // "dark mode", "light theme", "background slate": never "light bill".
            ("background" in w || (w.any { it == "dark" || it == "light" } && w.any { it == "mode" || it == "theme" })) && rest.size <= 2 ->
                "background" to (rest.filter { it != "background" }.joinToString(" ").ifBlank { w.firstOrNull { it == "dark" || it == "light" } ?: return null })
            w.any { it in setOf("header", "theme") } ->
                "header" to (rest.filter { it !in setOf("header", "theme") }.joinToString(" ").ifBlank { return null })
            w.any { it in setOf("kholo", "khol", "open", "jao") } && rest.size in 1..2 ->
                "open" to (AppSettings.page(rest.joinToString(" ")) ?: return null)
            else -> return null
        }
        val out = s.use("app_setting", org.json.JSONObject().put("setting", setting).put("value", value))
        return LocalAgent.Reply(out.optString("done").ifBlank { out.optString("error").let { if (it.isBlank()) "Done." else "That could not be changed: $it." } })
    }
}
