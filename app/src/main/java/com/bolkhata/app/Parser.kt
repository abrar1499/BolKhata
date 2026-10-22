package com.bolkhata.app

import java.util.Calendar

/**
 * Turns a spoken sentence (Roman Urdu / English / Urdu script) into ledger entries.
 * Works fully offline. One sentence can hold several transactions:
 * "Bilal se 100 liye, petrol 60 dala" -> 2 entries.
 * Anything it cannot place is returned in [Result.unclear] so the app can ask.
 */
object Parser {

    class Result(val entries: List<Entry>, val unclear: List<Entry>)

    private class Rule(val cat: Cat, val item: String, val keys: List<String>)

    private fun r(cat: Cat, item: String, vararg keys: String) = Rule(cat, item, keys.toList())

    private val carWords = listOf("gaari", "gari", "gaadi", "gadi", "garri", "garhi", "car", "گاڑی")
    private val phoneWords = listOf("mobile", "phone", "fone", "موبائل")
    private val repairWords = listOf("repair", "repairing", "service", "servicing", "theek", "مرمت")

    /** Checked in this order; the first rule that matches wins. */
    private val rules = listOf(
        r(Cat.GHAR, "Sent home", "ghar bhej", "bheje", "bheja", "bhejay", "bhejy", "bheji", "send home", "sent home", "بھیجے", "بھیجا"),

        r(Cat.TRANSPORT, "Petrol", "petrol", "patrol", "petral", "petrool", "patrool", "fuel", "diesel", "deesal", "benzene", "aldrees", "sasco", "petromin", "naft", "پٹرول", "ڈیزل", "بنزين"),
        r(Cat.TRANSPORT, "Car repair", "mechanic", "workshop", "tyre", "tire", "tyres", "puncture", "oil change", "battery", "مستری"),
        r(Cat.TRANSPORT, "Car wash", "car wash", "wash"),
        r(Cat.TRANSPORT, "Parking", "parking"),
        r(Cat.TRANSPORT, "Taxi", "taxi", "uber", "careem", "bolt", "ٹیکسی"),
        r(Cat.TRANSPORT, "Bus / Metro", "bus", "metro", "train"),
        r(Cat.TRANSPORT, "Tickets", "ticket", "flight", "jahaz", "ٹکٹ"),

        r(Cat.RENT, "Rent", "rent", "kiraya", "kiraaya", "kraya", "کرایہ"),

        r(Cat.BIJLI, "Electricity", "bijli", "electricity", "بجلی"),
        r(Cat.BIJLI, "Water bill", "paani ka bill", "pani ka bill", "water bill"),
        r(Cat.BIJLI, "Gas", "gas", "cylinder"),

        r(Cat.MOBILE, "Mobile recharge", "recharge", "sawa", "stc", "mobily", "zain", "package", "balance", "ریچارج"),
        r(Cat.MOBILE, "Internet", "internet", "wifi", "net"),

        r(Cat.SEHAT, "Medicine", "dawai", "dawa", "medicine", "pharmacy", "tablet", "tablets", "nahdi", "aldawaa", "dawaa", "صيدلية", "دوائی", "دوا"),
        r(Cat.SEHAT, "Doctor", "doctor", "hospital", "clinic", "checkup", "ڈاکٹر"),

        r(Cat.PERSONAL, "Haircut", "haircut", "barber", "nai", "hajam", "baal", "حجام"),
        r(Cat.PERSONAL, "Laundry", "laundry", "dhobi", "dhulai"),
        r(Cat.PERSONAL, "Toiletries", "soap", "sabun", "shampoo", "toothpaste", "perfume"),

        r(Cat.SHOPPING, "Clothes", "kapre", "kapray", "kapde", "kapry", "clothes", "shirt", "pant", "kurta", "کپڑے"),
        r(Cat.SHOPPING, "Shoes", "joote", "jootay", "joota", "shoes", "chappal", "جوتے"),
        r(Cat.SHOPPING, "Electronics", "charger", "headphone", "earphone", "laptop", "jarir", "extra", "ehsan"),
        r(Cat.SHOPPING, "Online shopping", "amazon", "noon", "shein", "namshi", "aliexpress", "temu"),
        r(Cat.SHOPPING, "Stores", "ikea", "centrepoint", "centerpoint", "redtag", "homecentre"),

        r(Cat.KHANA, "Bread / Roti", "roti", "naan", "bread", "khubz", "kuboos", "khaboos", "paratha", "روٹی"),
        r(Cat.KHANA, "Milk & Dairy", "milk", "doodh", "dudh", "dood", "laban", "dahi", "yogurt", "cheese", "دودھ", "دہی"),
        r(Cat.KHANA, "Tea & Coffee", "chai", "tea", "coffee", "qahwa", "karak", "چائے"),
        r(Cat.KHANA, "Meat & Chicken", "chicken", "murgi", "murga", "gosht", "meat", "mutton", "beef", "fish", "machli", "گوشت", "مرغی"),
        r(Cat.KHANA, "Fruit & Vegetables", "sabzi", "sabji", "vegetable", "fruit", "phal", "tamatar", "aloo", "piaz", "pyaz", "kela", "apple", "سبزی", "پھل"),
        r(Cat.KHANA, "Groceries", "grocery", "groceries", "atta", "aata", "chawal", "rice", "daal", "dal", "oil", "ghee", "cheeni", "sugar", "anday", "ande", "anda", "egg", "eggs", "masala", "namak", "baqala", "bakala", "panda", "lulu", "danube", "othaim", "carrefour", "tamimi", "bindawood", "nesto", "supermarket", "hypermarket", "آٹا", "چاول"),
        r(Cat.KHANA, "Eating out", "restaurant", "hotel", "biryani", "kabsa", "shawarma", "broast", "pizza", "burger", "mandi", "albaik", "starbucks", "mcdonald", "mcdonalds", "kfc", "kudu", "herfy", "dunkin", "hungerstation", "jahez", "talabat", "mrsool", "toyou", "cafe", "مطعم"),
        r(Cat.KHANA, "Drinks", "bottle", "botel", "botal", "botl", "juice", "cold drink", "pepsi", "coke", "cola", "sprite", "7up", "drink", "drinks", "soda", "water", "paani", "pani", "پانی", "بوتل"),
        r(Cat.KHANA, "Snacks", "biscuit", "chips", "snacks", "mithai", "sweets", "chocolate", "ice cream", "icecream"),
        r(Cat.KHANA, "Meals", "khana", "khaana", "khaya", "khaaya", "khai", "food", "foos", "lunch", "dinner", "nashta", "breakfast", "meal", "کھانا", "کھایا", "ناشتہ"),

        r(Cat.BIJLI, "Other bills", "bill", "بل"),
        r(Cat.SHOPPING, "Other shopping", "shopping", "saman", "samaan", "mall", "سامان")
    )

    private val incomeRules = listOf(
        "Salary" to listOf("salary", "tankhwah", "tankhuwa", "tankha", "tanqa", "تنخواہ"),
        "Bonus" to listOf("bonus"),
        "Overtime" to listOf("overtime"),
        "Income" to listOf("income", "aamdani", "amdani", "kamaye", "kamai", "آمدنی", "کمائی")
    )

    private val loanWords = listOf("udhaar", "udhar", "qarz", "karz", "qarza", "loan", "lent", "borrowed", "ادھار", "قرض")
    private val returnWords = listOf("wapis", "wapas", "lauta", "lotaye", "return", "returned", "واپس", "لوٹا")
    private val giveWords = listOf("give", "gave", "given", "diye", "diya", "diay", "dye", "de", "dia", "دیے", "دیا")

    private val notNames = setOf(
        "ghar", "aaj", "kal", "maine", "main", "mujhe", "dukan", "dukaan", "market", "shop", "store", "baqala",
        "bakala", "supermarket", "pump", "workshop", "hotel", "restaurant", "pharmacy", "mall", "us", "is", "un",
        "riyal", "riyals", "rupay", "rupee", "rupees", "sar", "pkr", "ka", "ki", "ke", "ko", "se", "ne", "to",
        "from", "give", "gave", "diye", "diya", "liye", "liya", "the", "and", "aur", "date", "tareekh", "hain",
        "hai", "tha", "thay", "mein", "me", "for", "sab", "kuch", "paise", "paisay", "pay", "paid",
        "گھر", "آج", "کل", "میں", "مجھے", "ریال", "روپے", "دکان"
    )

    private val splitRegex = Regex("""[,\n،۔]|(?<!\d)\.(?!\d)|\baur\b|\bphir\b|\band\b| اور | پھر """, RegexOption.IGNORE_CASE)
    private val numberRegex = Regex("""(\d[\d,]*(?:\.\d+)?)(?:\s*(hazar|hazaar|hazzar|thousand|k|lakh|lac|hundred|sau|ہزار|لاکھ|سو)(?!\p{L}))?""", RegexOption.IGNORE_CASE)

    fun parse(input: String, now: Long = System.currentTimeMillis()): Result {
        val found = extractDate(NumberWords.convert(normalizeDigits(input)), now)
        val date = found.first ?: now
        val text = found.second
        val entries = mutableListOf<Entry>()
        val unclear = mutableListOf<Entry>()

        for (raw in text.split(splitRegex)) {
            val part = raw.trim().replace(Regex("""\s+"""), " ")
            if (part.isEmpty()) continue
            val num = numberRegex.find(part) ?: continue
            var amount = num.groupValues[1].replace(",", "").toDoubleOrNull() ?: continue
            val mult = num.groupValues[2].lowercase()
            if (mult.isNotEmpty()) {
                amount *= when {
                    mult.startsWith("l") || mult == "لاکھ" -> 100_000.0
                    mult == "hundred" || mult == "sau" || mult == "سو" -> 100.0
                    else -> 1_000.0
                }
            }
            if (amount <= 0) continue

            val t = part.lowercase()
            val words = t.split(Regex("""[^\p{L}\p{N}]+""")).filter { it.isNotEmpty() }
            val padded = " $t "
            val hasKo = padded.contains(" ko ") || padded.contains(" کو ") || padded.contains(" to ")
            val hasSe = padded.contains(" se ") || padded.contains(" سے ") || padded.contains(" from ")
            val hasNe = padded.contains(" ne ") || padded.contains(" نے ")
            val person = findPerson(part)

            fun make(type: Type, cat: Cat, item: String, who: String) = Entry(
                id = "e" + System.nanoTime() + entries.size + unclear.size,
                type = type, amount = amount, category = cat, item = item, person = who,
                note = part, date = date, createdAt = System.currentTimeMillis()
            )

            val spend = matchSpending(t, words)
            val income = incomeRules.firstOrNull { (_, keys) -> keys.any { matches(it, t, words) } }
            val isReturn = returnWords.any { matches(it, t, words) }
            val isLoan = loanWords.any { matches(it, t, words) }

            when {
                isReturn && person.isNotEmpty() -> {
                    val type = if (hasSe || hasNe || has(t, "mile", "mila", "ملے", "ملا", "received", "got")) Type.WAPIS_MILA else Type.WAPIS_DIYA
                    entries += make(type, Cat.UDHAAR, "Loans", person)
                }
                isLoan && person.isNotEmpty() -> {
                    val type = if ((hasSe && !hasKo) || words.contains("borrowed")) Type.LIYA else Type.DIYA
                    entries += make(type, Cat.UDHAAR, "Loans", person)
                }
                income != null -> entries += make(Type.AAMDANI, Cat.TANKHWAH, income.first, "")
                spend != null -> entries += make(Type.KHARCHA, spend.first, spend.second, "")
                person.isNotEmpty() && (hasKo || hasSe || hasNe || giveWords.any { words.contains(it) }) -> {
                    val type = if (hasSe && !hasKo) Type.LIYA else Type.DIYA
                    entries += make(type, Cat.UDHAAR, "Loans", person)
                }
                else -> {
                    // Not sure what this is: ask the user.
                    val guess = person.ifEmpty { guessName(words) }
                    unclear += make(Type.KHARCHA, Cat.OTHER, "", guess)
                }
            }
        }
        return Result(entries, unclear)
    }

    // ---------- spending ----------

    /** Category for a shop or item name, e.g. "PANDA RETAIL" -> Groceries. Null if unknown. */
    fun guessCategory(text: String): Pair<Cat, String>? {
        val t = text.lowercase()
        val words = t.split(Regex("""[^\p{L}\p{N}]+""")).filter { it.isNotEmpty() }
        return matchSpending(t, words)
    }

    private fun matchSpending(t: String, words: List<String>): Pair<Cat, String>? {
        if (repairWords.any { matches(it, t, words) }) {
            return when {
                carWords.any { matches(it, t, words) } -> Cat.TRANSPORT to "Car repair"
                phoneWords.any { matches(it, t, words) } -> Cat.MOBILE to "Phone repair"
                else -> Cat.OTHER to "Repairs"
            }
        }
        rules.firstOrNull { rule -> rule.keys.any { matches(it, t, words) } }?.let { return it.cat to it.item }
        if (carWords.any { matches(it, t, words) }) return Cat.TRANSPORT to "Car (other)"
        if (phoneWords.any { matches(it, t, words) }) return Cat.MOBILE to "Mobile recharge"
        // Second pass: allow one wrong letter in longer words (petrl, bottel, choclate…)
        rules.firstOrNull { rule ->
            rule.keys.any { k -> !k.contains(' ') && k.length >= 5 && words.any { w -> w.length >= 5 && near(w, k) } }
        }?.let { return it.cat to it.item }
        return null
    }

    /** Multi-word keys match anywhere; short keys need the whole word; longer keys also match word starts (rotiyan, chickens). */
    private fun matches(key: String, t: String, words: List<String>): Boolean {
        if (key.contains(' ')) return t.contains(key)
        if (key.length < 4) return words.contains(key)
        return words.any { it == key || it.startsWith(key) }
    }

    /** True when the two words differ by at most one letter (edit distance ≤ 1). */
    private fun near(a: String, b: String): Boolean {
        if (kotlin.math.abs(a.length - b.length) > 1) return false
        var i = 0
        var j = 0
        var edits = 0
        while (i < a.length && j < b.length) {
            if (a[i] == b[j]) {
                i++; j++
            } else {
                edits++
                if (edits > 1) return false
                when {
                    a.length > b.length -> i++
                    a.length < b.length -> j++
                    else -> { i++; j++ }
                }
            }
        }
        edits += (a.length - i) + (b.length - j)
        return edits <= 1
    }

    // ---------- people ----------

    private fun findPerson(part: String): String {
        val en = Regex("""\b(?:to|from)\s+(\p{L}+)""", RegexOption.IGNORE_CASE).find(part)
        val ur = Regex("""(\p{L}+)\s+(?:bhai\s+|sahab\s+|sahib\s+)?(ko|se|ne|کو|سے|نے)(?:\s|$)""", RegexOption.IGNORE_CASE).find(part)
        for (m in listOfNotNull(ur, en)) {
            val name = m.groupValues[1]
            if (name.lowercase() !in notNames && name.length >= 2) return cap(name)
        }
        return ""
    }

    private fun guessName(words: List<String>): String {
        val w = words.firstOrNull { it.length >= 3 && it.none { c -> c.isDigit() } && it !in notNames }
        return if (w != null) cap(w) else ""
    }

    private fun cap(s: String) = s.lowercase().replaceFirstChar { it.uppercase() }

    // ---------- dates ----------

    private val months = linkedMapOf(
        "january" to 0, "february" to 1, "march" to 2, "april" to 3, "june" to 5, "july" to 6, "august" to 7,
        "september" to 8, "october" to 9, "november" to 10, "december" to 11,
        "sept" to 8, "jan" to 0, "feb" to 1, "mar" to 2, "apr" to 3, "may" to 4, "jun" to 5, "jul" to 6,
        "aug" to 7, "sep" to 8, "oct" to 9, "nov" to 10, "dec" to 11
    )
    private val monthAlt = months.keys.joinToString("|")
    private const val DATE_WORDS = "date|tareekh|tarikh|tarekh|tareek|tarik|tarix|تاریخ"

    /** Finds a date anywhere in the sentence, returns it and the sentence without the date words. */
    fun extractDate(text: String, now: Long): Pair<Long?, String> {
        val lower = text.lowercase()
        val today = Calendar.getInstance().apply { timeInMillis = now }
        val thisYear = today.get(Calendar.YEAR)

        fun cut(range: IntRange) = text.removeRange(range).let { " $it " }

        // 13-10-26, 13/10/2026, 13.10.26
        Regex("""(?<![\d,])(\d{1,2})[-/.](\d{1,2})[-/.](\d{4}|\d{2})(?!\d)""").find(lower)?.let { m ->
            val y = m.groupValues[3].toInt().let { if (it < 100) 2000 + it else it }
            build(m.groupValues[1].toInt(), m.groupValues[2].toInt() - 1, y)?.let { return it to cut(m.range) }
        }
        // 13 oct, 13th October 2026
        Regex("""(?<!\d)(\d{1,2})\s*(?:st|nd|rd|th)?\s*(?:of\s+)?($monthAlt)(?!\p{L})(?:\s*,?\s*(20\d{2})(?!\d))?""").find(lower)?.let { m ->
            val month = months[m.groupValues[2]] ?: return@let
            val y = m.groupValues[3].toIntOrNull()?.takeIf { it in thisYear - 5..thisYear + 1 }
            val ms = build(m.groupValues[1].toInt(), month, y ?: thisYear)
            if (ms != null) {
                val fixed = if (y == null && ms > now + 86_400_000L) build(m.groupValues[1].toInt(), month, thisYear - 1) else ms
                return fixed to cut(m.range)
            }
        }
        // oct 13
        Regex("""(?<!\p{L})($monthAlt)\s+(\d{1,2})(?:st|nd|rd|th)?(?!\d)""").find(lower)?.let { m ->
            val month = months[m.groupValues[1]] ?: return@let
            val ms = build(m.groupValues[2].toInt(), month, thisYear)
            if (ms != null) {
                val fixed = if (ms > now + 86_400_000L) build(m.groupValues[2].toInt(), month, thisYear - 1) else ms
                return fixed to cut(m.range)
            }
        }
        // 14 date / 14 tareekh / tareekh 14
        val dayOnly = Regex("""(?<![\d,.])(\d{1,2})\s*(?:st|nd|rd|th)?\s*(?:$DATE_WORDS)(?!\p{L})""").find(lower)
            ?: Regex("""(?<!\p{L})(?:$DATE_WORDS)\s*(?:ko\s+)?(\d{1,2})(?!\d)""").find(lower)
        dayOnly?.let { m ->
            val day = m.groupValues[1].toInt()
            var ms = build(day, today.get(Calendar.MONTH), thisYear)
            if (ms == null || ms > now + 86_400_000L) {
                val prev = Calendar.getInstance().apply { timeInMillis = now; add(Calendar.MONTH, -1) }
                ms = build(day, prev.get(Calendar.MONTH), prev.get(Calendar.YEAR))
            }
            if (ms != null) return ms to cut(m.range)
        }
        // 13/10 (this year)
        Regex("""(?<![\d,.])(\d{1,2})[-/](\d{1,2})(?![-/\d])""").find(lower)?.let { m ->
            val month = m.groupValues[2].toInt() - 1
            var ms = build(m.groupValues[1].toInt(), month, thisYear)
            if (ms != null && ms > now + 86_400_000L) ms = build(m.groupValues[1].toInt(), month, thisYear - 1)
            if (ms != null) return ms to cut(m.range)
        }
        // 3 din pehle / 3 days ago
        Regex("""(?<!\d)(\d{1,2})\s*(?:din|days?|دن)\s*(?:pehle|pehlay|pahle|ago|qabl|پہلے)""").find(lower)?.let { m ->
            return (now - m.groupValues[1].toLong() * 86_400_000L) to cut(m.range)
        }
        // parson / kal / yesterday
        if (Regex("""(?<!\p{L})(?:parson|parso|پرسوں)(?!\p{L})""").containsMatchIn(lower)) return (now - 2 * 86_400_000L) to text
        if (Regex("""(?<!\p{L})(?:kal|yesterday|کل)(?!\p{L})""").containsMatchIn(lower) && !lower.contains("aaj")) {
            return (now - 86_400_000L) to text
        }
        return null to text
    }

    private fun build(day: Int, month: Int, year: Int): Long? {
        if (day !in 1..31 || month !in 0..11) return null
        return try {
            Calendar.getInstance().apply {
                isLenient = false
                clear()
                set(year, month, day, 12, 0, 0)
            }.timeInMillis
        } catch (e: Exception) {
            null
        }
    }

    private fun has(t: String, vararg words: String) = words.any { t.contains(it) }

    private fun normalizeDigits(s: String): String {
        val sb = StringBuilder()
        for (c in s) {
            sb.append(
                when (c) {
                    in '۰'..'۹' -> '0' + (c - '۰')
                    in '٠'..'٩' -> '0' + (c - '٠')
                    else -> c
                }
            )
        }
        return sb.toString()
    }
}
