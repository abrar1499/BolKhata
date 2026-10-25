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

        r(Cat.EDU, "School fees", "school fee", "school fees", "tuition", "tution", "admission"),
        r(Cat.EDU, "Books & study", "books", "kitab", "kitaab", "kitabein", "copy", "stationery", "course", "college", "university", "uniform", "school"),
        r(Cat.FEES, "Visa & permits", "iqama", "iqaama", "visa", "muqeem", "passport", "permit", "residency", "reentry", "re-entry", "exit", "huroob", "اقامہ", "ویزا"),
        r(Cat.FEES, "Fines", "fine", "fines", "challan", "mukhalfa", "mukhalafa", "traffic fine", "violation"),
        r(Cat.FEES, "Insurance", "insurance", "tameen", "tamin", "medical insurance"),
        r(Cat.FEES, "Fees & charges", "fee", "fees", "charges", "tax", "vat"),
        r(Cat.CHARITY, "Charity", "zakat", "zakaat", "sadqa", "sadaqa", "sadqah", "khairat", "khayrat", "donation", "charity", "masjid", "mosque", "fitrana", "زکوۃ", "صدقہ"),
        r(Cat.CHARITY, "Gifts", "gift", "gifts", "tohfa", "tohfay", "eidi", "salami"),
        r(Cat.PERSONAL, "Gym & fitness", "gym", "fitness"),
        r(Cat.PERSONAL, "Cigarettes", "cigarette", "cigarettes", "sigret", "cigrette", "shisha", "vape"),
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
    private val takeWords = listOf("liye", "liya", "li", "lia", "took", "borrowed", "لیے", "لیا")
    private val giveWords = listOf("give", "gave", "given", "diye", "diya", "diay", "dye", "de", "dia", "d", "دیے", "دیا")

    private val notNames = setOf(
        "ghar", "aaj", "kal", "maine", "main", "mujhe", "dukan", "dukaan", "market", "shop", "store", "baqala",
        "bakala", "supermarket", "pump", "workshop", "hotel", "restaurant", "pharmacy", "mall", "us", "is", "un",
        "riyal", "riyals", "rupay", "rupee", "rupees", "sar", "pkr", "ka", "ki", "ke", "ko", "se", "ne", "to",
        "from", "give", "gave", "diye", "diya", "liye", "liya", "the", "and", "aur", "date", "tareekh", "hain",
        "hai", "tha", "thay", "mein", "me", "for", "sab", "sabko", "sabhi", "inko", "unko", "in", "all", "everyone", "kuch", "paise", "paisay", "pay", "paid",
        "گھر", "آج", "کل", "میں", "مجھے", "ریال", "روپے", "دکان"
    )

    /** Words that usually end one spoken entry: "40 hamza ko diye | 10 ki roti khai | ...". */
    private val clauseEnd = setOf(
        "diye", "diya", "di", "diay", "dye", "liye", "liya", "li", "lia", "le", "khai", "khaya", "khaye", "khaaya",
        "khaai", "piya", "pi", "pee", "bheje", "bheja", "bheji", "bhejay", "dala", "dali", "dalwaya", "dalwaye",
        "kharida", "kharide", "kharidi", "bharwaya", "karwaya", "karwayi", "mile", "mila", "mili", "aaye", "aya",
        "gave", "paid", "spent", "bought", "took", "received", "sent", "returned", "lent", "borrowed", "d",
        "becha", "bechi", "beche", "sold", "dali", "daali", "dalwai", "laya", "layi", "kharida", "khareeda",
        "hai", "hain", "tha", "thay", "the", "دیے", "دیا", "لیے", "لیا", "کھائی", "کھایا", "بھیجے"
    )

    private val currencyWords = setOf("riyal", "riyals", "rial", "rials", "sar", "sr", "rupay", "rupaye", "rupees", "rupee", "rs", "ریال", "روپے")

    /** A number written right before a currency word: "10 riyal". Preferred over other numbers. */
    private val moneyRegex = Regex(
        """(\d[\d,]*(?:\.\d+)?)\s*(?:riyals?|rials?|sar|sr|rupay|rupaye|rupees?|rs|ریال|روپے)(?!\p{L})""",
        RegexOption.IGNORE_CASE
    )

    private fun isNumber(w: String) = w.any { it.isDigit() }

    /** Words that start a new spoken entry. */
    private val clauseStart = setOf("mene", "maine", "men", "main", "aaj", "aj", "kal", "ar", "aur", "phir", "then", "today", "yesterday")

    /** Words after a number that mark it as the price: "17000 ki", "13000 ka", "1000 rate". */
    private val priceMarkers = setOf("ka", "ki", "ke", "kay", "wala", "wali", "wale", "rate")

    private val priceRegex = Regex("""(\d[\d,]*(?:\.\d+)?)\s*(?:ka|ki|ke|kay|wala|wali|wale)(?!\p{L})""", RegexOption.IGNORE_CASE)
    private val rateRegex = Regex("""(?:rate\s*(?:hai|he|is|=|:)?\s*(\d[\d,]*(?:\.\d+)?))|(?:(\d[\d,]*(?:\.\d+)?)\s*(?:ka\s*|ki\s*)?rate)""", RegexOption.IGNORE_CASE)

    private val assetWords = linkedMapOf(
        "mobile" to "Mobile", "phone" to "Mobile", "iphone" to "Mobile", "laptop" to "Laptop", "computer" to "Computer",
        "tv" to "TV", "fridge" to "Fridge", "ac" to "AC", "car" to "Car", "gaari" to "Car", "gari" to "Car", "gaadi" to "Car",
        "bike" to "Bike", "motorcycle" to "Motorcycle", "gold" to "Gold", "sona" to "Gold", "plot" to "Plot",
        "zameen" to "Land", "land" to "Land", "furniture" to "Furniture", "sofa" to "Sofa", "watch" to "Watch", "ghari" to "Watch"
    )
    private val buyWords = setOf("liya", "li", "lia", "liye", "kharida", "kharidi", "kharide", "khareeda", "khareedi", "bought", "purchased", "purchase", "new", "naya", "nayi")
    private val sellWords = setOf("becha", "bechi", "beche", "bech", "bechdiya", "sold", "sell", "sale", "فروخت")
    private val neGiveWords = setOf("di", "diya", "diye", "d", "dali", "dala", "daali", "dalwai", "bheji", "bheja", "bheje", "laya", "layi", "gave", "sent", "delivered")

    /** Words that are never part of a goods name. */
    private val goodsStop = setOf(
        "ne", "ko", "se", "ka", "ki", "ke", "kay", "rate", "hai", "he", "tha", "the", "aur", "ar", "mene", "maine", "men",
        "aaj", "aj", "kal", "wala", "wali", "wale"
    )

    private fun overlaps(a: IntRange, b: IntRange) = a.first <= b.last && b.first <= a.last

    /** "2 kilo", "3 packet": a count, not money. */
    private val unitWords = setOf(
        "kilo", "kg", "kgs", "kilogram", "gram", "grams", "gm", "liter", "litre", "liters", "ltr", "packet", "packets",
        "pack", "piece", "pieces", "pcs", "dozen", "darjan", "dabba", "dabbe", "plate", "plates", "cup", "cups",
        "glass", "box", "boxes", "carton", "bag", "bags", "pair", "kilos", "bori", "boriyan", "boriya", "ton", "tons",
        "truck", "trucks", "trolley", "foot", "feet", "ft", "sqft", "meter", "meters", "roll", "rolls", "gaz", "maund", "mann",
        "کلو", "پیکٹ", "درجن"
    )

    private fun isQuantity(text: String, numberEnd: Int): Boolean {
        val next = text.substring(minOf(numberEnd + 1, text.length)).trim().split(Regex("""\s+""")).firstOrNull()?.lowercase()
        return next != null && next in unitWords
    }

    /**
     * Splits "40 hamza ko diye 10 ki roti khai 10 ki medicine li" into one piece per entry.
     * Cuts after a verb when both sides have an amount; a piece that still has two amounts
     * and no currency word ("petrol 50 roti 10") is cut between the two amounts.
     */
    private fun splitClauses(s: String): List<String> {
        val words = s.trim().split(Regex("""\s+""")).filter { it.isNotEmpty() }
        val pieces = mutableListOf<List<String>>()
        var cur = mutableListOf<String>()
        var cutAt = -1
        words.forEachIndexed { i, w ->
            cur.add(w)
            val rest = words.subList(i + 1, words.size)
            val lw = w.lowercase()
            if (i == cutAt) {
                if (rest.any { isNumber(it) }) {
                    pieces.add(cur)
                    cur = mutableListOf()
                }
                cutAt = -1
                return@forEachIndexed
            }
            if (cutAt > i) return@forEachIndexed
            val curHasNumber = cur.any { isNumber(it) }
            // "...li 17000 ki": the price right after the verb belongs to this entry.
            // A price is a suffix ("li 17000 ki | 2000 ...") only when what follows starts a new entry,
            // not an item ("diye | 10 ki roti khai").
            val priceNext = rest.size >= 2 && isNumber(rest[0]) && rest[1].lowercase() in priceMarkers &&
                (rest[1].lowercase() == "rate" || rest.size == 2 || isNumber(rest[2]) || rest[2].lowercase() in clauseStart)
            // "...dali rate 17000": the rate right after the verb belongs to this entry.
            val rateNext = rest.size >= 2 && rest[0].lowercase() == "rate" && isNumber(rest[1])
            if (lw in clauseEnd && curHasNumber && rest.any { isNumber(it) }) {
                if (priceNext || rateNext) {
                    cutAt = i + 2
                } else {
                    pieces.add(cur)
                    cur = mutableListOf()
                }
            } else if (lw in priceMarkers && i > 0 && isNumber(words[i - 1]) && rest.any { isNumber(it) } &&
                cur.dropLast(2).any { !isNumber(it) } &&
                (isNumber(rest[0]) || rest[0].lowercase() in clauseStart || rest.getOrNull(1)?.lowercase() in setOf("ne", "ko", "se"))
            ) {
                // "mobile becha 13000 ka | aaj mobile liya 20000 ka": a price after the item ends the entry.
                pieces.add(cur)
                cur = mutableListOf()
            }
        }
        if (cur.isNotEmpty()) pieces.add(cur)

        val out = mutableListOf<String>()
        for (p in pieces) {
            val hasCurrency = p.any { it.lowercase() in currencyWords } ||
                p.indices.any { i -> p[i].lowercase() in priceMarkers && i > 0 && isNumber(p[i - 1]) } ||
                p.any { it.lowercase() == "rate" }
            val numIdx = p.indices.filter { isNumber(p[it]) && p.getOrNull(it + 1)?.lowercase() !in unitWords }
            if (hasCurrency || numIdx.size < 2) {
                out.add(p.joinToString(" "))
                continue
            }
            // "50 petrol 10 roti": cut before each later amount. "petrol 50 roti 10": cut after each amount.
            val cuts = if (isNumber(p[0])) numIdx.drop(1) else numIdx.dropLast(1).map { it + 1 }
            var start = 0
            for (k in cuts) {
                if (k > start) out.add(p.subList(start, k).joinToString(" "))
                start = k
            }
            if (start < p.size) out.add(p.subList(start, p.size).joinToString(" "))
        }
        return out
    }

    private val splitRegex = Regex("""[,\n،۔]|(?<!\d)\.(?!\d)|\baur\b|\bar\b|\bphir\b|\band\b| اور | پھر """, RegexOption.IGNORE_CASE)
    private val numberRegex = Regex("""(\d[\d,]*(?:\.\d+)?)(?:\s*(hazar|hazaar|hazzar|thousand|k|lakh|lac|hundred|sau|ہزار|لاکھ|سو)(?!\p{L}))?""", RegexOption.IGNORE_CASE)

    private val digitMult = Regex("""(\d[\d,]*(?:\.\d+)?)\s*(hazar|hazaar|hazzar|thousand|lakh|lac|hundred|sau|ہزار|لاکھ|سو)(?!\p{L})""", RegexOption.IGNORE_CASE)

    /**
     * The part of the day a sentence talks about, as a typical hour:
     * morning / breakfast 8 AM, lunch 1 PM, evening / dinner 7 PM, night 9 PM. Null when no time is said.
     */
    fun dayPartHour(text: String): Int? {
        val w = text.lowercase().split(Regex("""[^\p{L}]+""")).filter { it.isNotEmpty() }.toSet()
        fun any(vararg k: String) = k.any { it in w }
        return when {
            any("raat", "rat", "night", "tonight", "رات") -> 21
            any("dinner", "sham", "shaam", "shām", "evening", "شام") -> 19
            any("dopahar", "dopehar", "dopehr", "dupehr", "dupahar", "dupehar", "dopahr", "dophar", "lunch", "noon", "afternoon", "دوپہر") -> 13
            any("suba", "subah", "subha", "sobah", "morning", "nashta", "nashtay", "nashte", "breakfast", "صبح", "ناشتہ") -> 8
            else -> null
        }
    }

    /** The same day as [ms], at [hour]:00. */
    fun atHour(ms: Long, hour: Int): Long = Calendar.getInstance().apply {
        timeInMillis = ms
        set(Calendar.HOUR_OF_DAY, hour)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    fun parse(input: String, now: Long = System.currentTimeMillis()): Result {
        // "2 hazar" -> "2000" before the number words are read, so it is not seen as "2" and "1000".
        val joined = digitMult.replace(normalizeDigits(input)) { m ->
            val n = m.groupValues[1].replace(",", "").toDoubleOrNull() ?: return@replace m.value
            val w = m.groupValues[2].lowercase()
            val x = when {
                w.startsWith("l") || w == "لاکھ" -> 100_000.0
                w == "hundred" || w == "sau" || w == "سو" -> 100.0
                else -> 1_000.0
            }
            val v = n * x
            if (v % 1.0 == 0.0) v.toLong().toString() else v.toString()
        }
        val found = extractDate(NumberWords.convert(joined), now)
        val date = found.first ?: now
        val text = found.second
        // "subah nashta 7": the entry gets a morning time instead of the time it was typed.
        val sentenceHour = dayPartHour(joined)
        val entries = mutableListOf<Entry>()
        val unclear = mutableListOf<Entry>()

        val parts = text.split(splitRegex).flatMap { splitClauses(it) }

        // "akram 500, afzal 100, nadir 2000 in sab ko diye": the verb at the end applies to every name before it.
        val lastWords = parts.lastOrNull()?.lowercase()?.split(Regex("""[^\p{L}\p{N}]+"""))?.filter { it.isNotEmpty() }.orEmpty()
        val groupType: Type? = when {
            parts.size < 2 -> null
            giveWords.any { it in lastWords } && ("ko" in lastWords || "to" in lastWords) -> Type.DIYA
            takeWords.any { it in lastWords } && ("se" in lastWords || "from" in lastWords) -> Type.LIYA
            else -> null
        }

        var lastTrade: Entry? = null

        for (raw in parts) {
            val part = raw.trim().replace(Regex("""\s+"""), " ")
            if (part.isEmpty()) continue
            val rateMatch = rateRegex.find(part)
            val rateValue = rateMatch?.let { m -> (m.groupValues[1].ifEmpty { m.groupValues[2] }).replace(",", "").toDoubleOrNull() } ?: 0.0
            val num = moneyRegex.find(part)
                ?: priceRegex.find(part)
                ?: numberRegex.findAll(part).firstOrNull { !isQuantity(part, it.range.last) && (rateMatch == null || !overlaps(it.range, rateMatch.range)) }
                ?: rateMatch?.let { m -> numberRegex.find(part, m.range.first) }
                ?: numberRegex.find(part)
                ?: continue
            var amount = num.groupValues[1].replace(",", "").toDoubleOrNull() ?: continue
            val mult = num.groupValues.getOrElse(2) { "" }.lowercase()
            if (mult.isNotEmpty()) {
                amount *= when {
                    mult.startsWith("l") || mult == "لاکھ" -> 100_000.0
                    mult == "hundred" || mult == "sau" || mult == "سو" -> 100.0
                    else -> 1_000.0
                }
            }
            // "2000 awal eint, 17000 rate": no price was said, so the number is how many and the total is qty x rate
            // (bricks are priced per 1000).
            val priced = moneyRegex.containsMatchIn(part) || priceRegex.containsMatchIn(part)
            var rateQty = 0.0
            if (!priced && rateValue > 0 && rateMatch != null && !overlaps(num.range, rateMatch.range)) {
                rateQty = amount
                amount = total(rateQty, rateValue, part)
            }
            if (amount <= 0) continue

            val t = part.lowercase()
            val words = t.split(Regex("""[^\p{L}\p{N}]+""")).filter { it.isNotEmpty() }
            val padded = " $t "
            val hasKo = padded.contains(" ko ") || padded.contains(" کو ") || padded.contains(" to ")
            val hasSe = padded.contains(" se ") || padded.contains(" سے ") || padded.contains(" from ")
            val hasNe = padded.contains(" ne ") || padded.contains(" نے ")
            val person = findPerson(part).ifEmpty {
                words.firstOrNull { it in knownPeople }?.let { cap(it) } ?: ""
            }
            val takes = takeWords.any { words.contains(it) }
            // "1000 eint Ashraf ko di": the number is how many bricks, not money. The rate is asked for.
            val firstAfter = part.substring(num.range.last + 1).trim().split(Regex("""\s+""")).filter { it.isNotEmpty() }
                .let { w -> if (w.firstOrNull()?.lowercase() in unitWords) w.drop(1) else w }.firstOrNull()?.lowercase() ?: ""
            val bareQty = !priced && rateMatch == null && numberRegex.findAll(part).count() == 1 && mult.isEmpty() &&
                looksLikeGoods(firstAfter, person) &&
                (person.isNotEmpty() || sellWords.any { words.contains(it) } || buyWords.any { words.contains(it) })

            // Another number that is not the price: a quantity, with the goods named after it ("1000 awal eint").
            val qtyMatch = if (rateQty > 0 || bareQty) num else numberRegex.findAll(part).firstOrNull { m ->
                !overlaps(m.range, num.range) && (rateMatch == null || !overlaps(m.range, rateMatch.range))
            }
            val qty = if (rateQty > 0) rateQty else if (bareQty) amount else qtyMatch?.groupValues?.get(1)?.replace(",", "")?.toDoubleOrNull() ?: 0.0
            // "10 bori cement rate 1500": the number read was the rate, so the total is qty x rate.
            if (!priced && rateQty == 0.0 && rateValue > 0 && qty > 0 && rateMatch != null && overlaps(num.range, rateMatch.range)) {
                amount = total(qty, rateValue, part)
            }
            val afterQty = qtyMatch?.let { part.substring(it.range.last + 1).trim().split(Regex("""\s+""")) }.orEmpty()
            val unit = afterQty.firstOrNull()?.lowercase()?.takeIf { it in unitWords } ?: ""
            val goods = (if (unit.isEmpty()) afterQty else afterQty.drop(1))
                .takeWhile { w ->
                    val lw = w.lowercase()
                    !isNumber(w) && lw !in goodsStop && lw !in cashWords && lw !in creditWords && lw !in clauseEnd && lw != person.lowercase() && lw !in knownPeople
                }
                .take(3).joinToString(" ").trim().replaceFirstChar { it.uppercase() }

            val partHour = dayPartHour(part) ?: sentenceHour
            val partDate = if (partHour != null) atHour(date, partHour) else date
            fun make(type: Type, cat: Cat, item: String, who: String) = Entry(
                id = "e" + System.nanoTime() + entries.size + unclear.size,
                type = type, amount = amount, category = cat,
                item = if (isLoan(type) && goods.isNotEmpty()) goods else item,
                person = who,
                note = cleanNote(part, who).ifBlank { item }, date = partDate,
                createdAt = if (partHour != null) partDate else System.currentTimeMillis(),
                qty = if (qty > 0 && (goods.isNotEmpty() || unit.isNotEmpty())) qty else 0.0,
                unit = unit, rate = rateValue
            )

            val asset = assetWords.entries.firstOrNull { (k, _) -> words.contains(k) }?.value
            val sells = sellWords.any { words.contains(it) }
            val buys = buyWords.any { words.contains(it) }
            val trade = goods.isNotEmpty() && qty > 0
            val before = entries.size
            val owes = owed[person.lowercase()] ?: 0.0

            val spend = matchSpending(t, words)
            val income = incomeRules.firstOrNull { (_, keys) -> keys.any { matches(it, t, words) } }
            val isReturn = returnWords.any { matches(it, t, words) }
            val isLoan = loanWords.any { matches(it, t, words) }
            // "Afzal se 1000 ki eint cash li": paid on the spot, so nobody owes anybody.
            val cashNow = words.any { it in cashWords } && words.none { it in creditWords }

            when {
                // "..., 10000 diye baqi udhaar" right after a sale or purchase: a part payment from or to that person.
                person.isEmpty() && lastTrade?.person?.isNotEmpty() == true && paidWords.any { words.contains(it) } && !trade -> {
                    val lt = lastTrade ?: return Result(entries, unclear)
                    entries += make(if (lt.type == Type.SALE) Type.WAPIS_MILA else Type.WAPIS_DIYA, Cat.UDHAAR, "Loans", lt.person)
                }
                asset != null && sells -> entries += make(Type.AAMDANI, Cat.ASSETS, "$asset (sold)", "")
                asset != null && buys && person.isEmpty() -> entries += make(Type.KHARCHA, Cat.ASSETS, asset, "")
                sells && person.isEmpty() -> entries += make(Type.SALE, Cat.SALES, goods.ifEmpty { "Sale" }, "")
                sells && person.isNotEmpty() -> entries += make(Type.SALE, Cat.SALES, goods.ifEmpty { "Sale" }, person).copy(paid = cashNow)
                buys && trade && person.isEmpty() && matchSpending(t, words) == null ->
                    entries += make(Type.PURCHASE, Cat.STOCK, goods, "")
                isReturn && person.isNotEmpty() -> {
                    val type = if (hasSe || hasNe || has(t, "mile", "mila", "ملے", "ملا", "received", "got")) Type.WAPIS_MILA else Type.WAPIS_DIYA
                    entries += make(type, Cat.UDHAAR, "Loans", person)
                }
                isLoan && person.isNotEmpty() && !trade -> {
                    val type = if ((hasSe && !hasKo) || words.contains("borrowed")) Type.LIYA else Type.DIYA
                    entries += make(type, Cat.UDHAAR, "Loans", person)
                }
                income != null -> entries += make(Type.AAMDANI, Cat.TANKHWAH, income.first, "")
                spend != null -> entries += make(Type.KHARCHA, spend.first, spend.second, "")
                person.isNotEmpty() && (hasKo || hasSe || hasNe || takes || giveWords.any { words.contains(it) } ||
                    neGiveWords.any { words.contains(it) }) -> {
                    val type = when {
                        // "Ashraf ne li": Ashraf took from me, so he owes me.
                        hasNe && takes -> Type.DIYA
                        // "Ashraf ne dali / di": Ashraf gave or delivered to me, so I owe him.
                        hasNe && neGiveWords.any { words.contains(it) } -> Type.LIYA
                        (hasSe && !hasKo) || (takes && !hasKo) -> Type.LIYA
                        else -> Type.DIYA
                    }
                    when {
                        // Goods with a quantity are trade: a sale on credit or a purchase on credit.
                        trade && type == Type.DIYA -> entries += make(Type.SALE, Cat.SALES, goods, person).copy(paid = cashNow)
                        trade && type == Type.LIYA -> entries += make(Type.PURCHASE, Cat.STOCK, goods, person).copy(paid = cashNow)
                        // "Ahmed ne 5000 diye" when Ahmed owes me: he is paying back.
                        type == Type.LIYA && hasNe && owes > 0.005 -> entries += make(Type.WAPIS_MILA, Cat.UDHAAR, "Loans", person)
                        // "Ahmed ko 5000 diye" when I owe Ahmed: I am paying back.
                        type == Type.DIYA && hasKo && owes < -0.005 -> entries += make(Type.WAPIS_DIYA, Cat.UDHAAR, "Loans", person)
                        else -> entries += make(type, Cat.UDHAAR, "Loans", person)
                    }
                }
                else -> {
                    val guess = person.ifEmpty { guessName(words) }
                    if (groupType != null && guess.isNotEmpty()) {
                        entries += make(groupType, Cat.UDHAAR, "Loans", guess)
                    } else {
                        // Not sure what this is: ask the user.
                        unclear += make(Type.KHARCHA, Cat.OTHER, goods, guess).copy(note = part)
                    }
                }
            }
            if (bareQty) {
                // Move what was just read to the questions, with the goods filled in and no amount yet.
                val made = entries.drop(before)
                repeat(made.size) { entries.removeAt(entries.size - 1) }
                val lastUnclear = if (made.isEmpty() && unclear.isNotEmpty()) unclear.removeAt(unclear.size - 1) else null
                (made.ifEmpty { listOfNotNull(lastUnclear) }).forEach { m ->
                    val type = when (m.type) {
                        Type.DIYA, Type.SALE -> Type.SALE
                        Type.LIYA, Type.PURCHASE -> Type.PURCHASE
                        else -> null
                    }
                    unclear += m.copy(
                        type = type ?: Type.KHARCHA,
                        category = when (type) { Type.SALE -> Cat.SALES; Type.PURCHASE -> Cat.STOCK; else -> Cat.OTHER },
                        item = goods.ifEmpty { m.item }, amount = 0.0, qty = amount, unit = unit, note = part
                    )
                }
                continue
            }
            val newTrade = entries.drop(before).lastOrNull { isTrade(it.type) && it.person.isNotEmpty() }
            if (newTrade != null) lastTrade = newTrade
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

    /** Words the person taught the app by correcting an entry. Checked before everything else. */
    @Volatile var learned: Map<String, Pair<Cat, String>> = emptyMap()

    /** What each person owes now (lowercase name), positive = they owe me. Used to read payments correctly. */
    @Volatile var owed: Map<String, Double> = emptyMap()

    private val cashWords = setOf("cash", "naqad", "nakad", "naqd", "nakd")
    private val creditWords = setOf("udhaar", "udhar", "udhaari", "udhari", "credit", "baqi", "baki", "unpaid", "ادھار")

    /** "kachi eint ke pese nahi diye abhi" / "last stock entry ki payment receive nahi hui". */
    class PayStatus(val paid: Boolean, val keywords: List<String>, val person: String, val mentionsMoney: Boolean)

    private val noWords = setOf("ni", "nhi", "nahi", "nahin", "nai", "nahi.", "not", "na", "didnt", "nahe", "nhe")
    private val moneyTalk = setOf("pese", "paise", "paisay", "paisa", "pesa", "pesay", "payment", "rakam", "raqam", "adaigi", "money", "amount", "bill")
    private val payVerbs = setOf(
        "diye", "diya", "di", "dia", "dye", "mil", "mile", "mila", "mili", "paid", "pay", "ada", "chuka", "chukaye",
        "clear", "receive", "received", "recieve", "recieved", "receives", "vasool", "vasooli", "collected", "aayi", "aai", "aaya", "aaye"
    )
    private val latestPayRefs = setOf("last", "latest", "newest", "recent", "pichli", "pichhli", "akhri", "aakhri", "previous")
    private val payEntryRefs = setOf("entry", "entries", "record", "transaction", "stock", "sale", "sales", "purchase", "purchases", "item", "payment")
    private val payStop = setOf(
        "abhi", "abi", "tak", "ke", "ka", "ki", "ko", "se", "ne", "hain", "hai", "the", "thay", "tha", "thi", "wale", "wali", "wala",
        "mene", "maine", "men", "main", "mein", "me", "usne", "us", "os", "osne", "oski", "oska", "oske", "unhon", "unho", "kar", "kr",
        "de", "do", "diye", "diya", "liye", "liya", "li", "mujhe", "bhi", "bi", "b", "and", "the", "for", "yet", "still",
        "last", "latest", "newest", "recent", "pichli", "pichhli", "akhri", "aakhri", "previous", "entry", "entries", "record", "transaction",
        "transactions", "stock", "sale", "sales", "purchase", "purchases", "item", "items", "jo", "jiski", "jiska", "jiske",
        "dala", "daala", "dali", "daali", "hoi", "hua", "hue", "hui", "gayi", "gaya", "gaye", "customer", "buyer", "supplier", "vendor"
    ) + noWords + moneyTalk + payVerbs + creditWords + cashWords

    fun payStatus(input: String): PayStatus? {
        val t = input.lowercase().replace("didn't", "didnt")
        val words = t.split(Regex("""[^\p{L}]+""")).filter { it.isNotEmpty() }
        val latestRef = words.any { it in latestPayRefs } && words.any { it in payEntryRefs }
        // Digits normally mean a new transaction. Allow them only when the user explicitly points
        // to an existing/latest entry, so "last 500 stock entry unpaid" remains an edit command.
        if (t.any { it.isDigit() } && !latestRef) return null
        val neg = words.any { it in noWords }
        val money = words.any { it in moneyTalk }
        val verb = words.any { it in payVerbs }
        val credit = words.any { it in creditWords }
        val cash = words.any { it in cashWords }
        val paid = when {
            credit || (neg && (money || verb)) -> false
            cash || (money && verb) || words.contains("paid") -> true
            else -> return null
        }
        val keys = words.filter { it.length >= 3 && it !in payStop && it.none { c -> c.isDigit() } }
        return PayStatus(paid, keys, findPerson(input), money)
    }
    private val noteDrop = goodsStop + clauseEnd + currencyWords + priceMarkers + cashWords + setOf(
        "kiya", "kia", "kiye", "kie", "kar", "kr", "karna", "karo", "kardiya", "krdiya", "hua", "hui", "hue", "gaya", "gayi", "gae",
        "kal", "kl", "cal", "kall", "parson", "parso", "today", "yesterday", "aj", "abhi", "abi", "mein", "me", "par", "pe", "pay",
        "for", "on", "of", "a", "an", "i", "my", "mera", "meri", "mere", "mene", "maine", "main", "men", "mujhe", "apna", "apni",
        "is", "was", "were", "it", "to", "from", "bhai", "sahab", "sahib", "k", "ky", "kay", "b", "bhi", "bi", "sath", "saath"
    )

    /** "cal 7 Riyal ka Subah nashta kiya tha" -> "Subah nashta": the sentence without amount, date and filler words. */
    fun cleanNote(part: String, person: String = ""): String {
        val keep = part.split(Regex("""\s+""")).map { it.trim('.', ',', '!', '?', ':', ';', '"', '\'') }.filter { w ->
            val lw = w.lowercase()
            lw.isNotEmpty() && lw.none { it.isDigit() } && lw !in noteDrop && lw !in unitWords && !lw.equals(person, true)
        }
        return keep.joinToString(" ").take(40).replaceFirstChar { it.uppercase() }
    }

    private val perThousand = setOf("eint", "eent", "int", "ent", "eenth", "brick", "bricks", "اینٹ", "اینٹیں")

    /** Total for [qty] at [rate]; bricks are priced per 1000. */
    fun total(qty: Double, rate: Double, text: String): Double {
        val words = text.lowercase().split(Regex("""[^\p{L}]+"""))
        return if (words.any { it in perThousand }) qty * rate / 1000.0 else qty * rate
    }

    /** True when [w] is the name of goods ("eint", "cement"), not money, a person or a spending word. */
    private fun looksLikeGoods(w: String, person: String): Boolean {
        if (w.length < 3 || !w.all { it.isLetter() }) return false
        if (w == person.lowercase() || w in knownPeople || w in notNames) return false
        val blocked = listOf(goodsStop, clauseEnd, currencyWords, cashWords, creditWords, paidWords, sellWords, buyWords,
            neGiveWords, giveWords.toSet(), takeWords.toSet(), returnWords.toSet(), loanWords.toSet())
        if (blocked.any { w in it }) return false
        if (learned.containsKey(w)) return false
        if (matchSpending(w, listOf(w)) != null) return false
        if (incomeRules.any { (_, keys) -> keys.any { matches(it, w, listOf(w)) } }) return false
        return true
    }

    private val paidWords = setOf("diye", "diya", "di", "d", "cash", "naqad", "nakad", "paid", "advance", "jama", "mile", "mila", "received", "payment")

    /** Names of people already in the khata, lowercase. "1000 hamza diye" works even without "ko". */
    @Volatile var knownPeople: Set<String> = emptySet()

    private val learnStop = setOf(
        "ka", "ki", "ke", "ko", "se", "ne", "aaj", "kal", "liya", "liye", "lia", "diya", "diye", "khaya", "khai", "khaye",
        "kiya", "kiye", "hai", "hain", "tha", "the", "thay", "riyal", "riyals", "sar", "rupay", "rupees", "and", "for",
        "from", "with", "mein", "par", "pay", "wala", "wali", "wale", "dala", "dali", "bheja", "bheje", "liye", "mila",
        "mile", "sath", "saath", "mera", "meri", "mere", "apna", "apni", "today", "yesterday", "date"
    )

    /** Words from a note worth remembering for a category ("10 ki shawarma khai" -> ["shawarma"]). */
    fun learnWords(note: String): List<String> =
        note.lowercase().split(Regex("""[^\p{L}]+"""))
            .filter { it.length >= 3 && it !in learnStop && it !in notNames && it !in knownPeople }
            .distinct()

    private fun matchSpending(t: String, words: List<String>): Pair<Cat, String>? {
        words.firstNotNullOfOrNull { learned[it] }?.let { return it }
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
            rule.keys.any { k ->
                !k.contains(' ') && k.length >= 5 &&
                    words.any { w -> w.length >= 5 && distance(w, k) <= (if (k.length >= 8) 2 else 1) }
            }
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
    /** Number of single-letter changes to turn [a] into [b] (stops early above 2). */
    private fun distance(a: String, b: String): Int {
        if (kotlin.math.abs(a.length - b.length) > 2) return 3
        var prev = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            val cur = IntArray(b.length + 1)
            cur[0] = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                cur[j] = minOf(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + cost)
            }
            prev = cur
        }
        return prev[b.length]
    }

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
        Regex("""(?<!\d)(\d{1,2})\s*(?:din|days?|دن)\s*(?:pehle|pehlay|pehly|pehli|pahle|pahly|phle|phly|pehley|ago|qabl|پہلے)""").find(lower)?.let { m ->
            return (now - m.groupValues[1].toLong() * 86_400_000L) to cut(m.range)
        }
        // parson / kal / yesterday
        if (Regex("""(?<!\p{L})(?:parson|parso|parsu|parsun|parsoon|parsun|prso|prson|prsu|parsoun|پرسوں)(?!\p{L})""").containsMatchIn(lower)) return (now - 2 * 86_400_000L) to text
        if (Regex("""(?<!\p{L})(?:kal|kl|cal|kall|yesterday|کل)(?!\p{L})""").containsMatchIn(lower) && !lower.contains("aaj")) {
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
