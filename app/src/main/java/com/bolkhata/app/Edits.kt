package com.bolkhata.app

/**
 * Changes an existing entry without AI. Understands, in any mix:
 *  - date:     "nashta wali entry 2 din pehle ki hai", "petrol kal ka tha", "cheeni 14 date ki thi", "last entry 13 oct ki"
 *  - amount:   "petrol 50 nahi 40 tha", "hamza wali 500 ko 400 kar do", "cheeni wali entry 150 ki thi"
 *  - quantity: "eint 2000 nahi 1500 thi"
 *  - category: "cheeni grocery mein dal do", "nashta ki category food", "petrol transport mein"
 *  - type:     "cheeni wali income thi", "hamza wali entry udhaar li thi", "ye sale thi"
 *  - person:   "hamza nahi nadir ko diye the", "hamza wali entry nadir ki hai"
 *  - item:     "cheeni nahi chawal the", "nashta nahi lunch tha"
 * Returns null when the sentence is not clearly about an old entry, so it is read as a new one.
 */
object Edits {

    private val refers = setOf("entry", "wali", "wala", "wale", "walay", "waly", "last", "pichli", "pichhli", "akhri", "aakhri", "previous", "ye", "yeh", "isko", "usko")
    private val lastWords = setOf("last", "pichli", "pichhli", "akhri", "aakhri", "previous", "ye", "yeh", "isko", "usko")
    private val strongWords = setOf(
        "change", "badal", "badlo", "update", "correct", "galat", "ghalat", "fix", "theek", "thik", "sahi", "edit",
        "instead", "jagah", "bajaye", "category", "catagory", "katagri"
    )
    private val pastWords = setOf("tha", "thi", "the", "thay", "hai", "he", "hain", "thin")
    private val putWords = setOf("dal", "daal", "dalo", "daalo", "rakho", "rakh", "move", "put", "kar", "kr", "karo", "kardo", "krdo", "do")
    /** Words that describe doing something now: then it is a new entry, not a change. */
    private val actionVerbs = setOf(
        "kiya", "kia", "kiye", "khaya", "khai", "khaye", "piya", "pi", "dalwaya", "bheja", "bheje",
        "kharida", "kharidi", "karwaya", "bharwaya", "bought", "paid", "spent", "gave", "took", "sold"
    )
    private val stop = setOf(
        "entry", "wali", "wala", "wale", "walay", "waly", "ki", "ka", "ke", "ko", "hai", "he", "hain", "thi", "tha", "the", "thay",
        "kar", "kr", "karo", "do", "dein", "kardo", "krdo", "change", "badal", "badlo", "update", "correct", "galat", "ghalat",
        "fix", "theek", "thik", "sahi", "edit", "nahi", "nahin", "nhi", "ni", "not", "date", "tareekh", "tarikh", "din", "days", "day",
        "pehle", "pehly", "pehlay", "pahle", "phle", "phly", "ago", "kal", "kl", "cal", "parson", "parso", "parsu", "aaj", "aj",
        "today", "yesterday", "mein", "me", "men", "main", "se", "ne", "is", "us", "was", "it", "instead", "jagah", "bajaye", "riyal",
        "sar", "rupay", "last", "pichli", "pichhli", "akhri", "aakhri", "previous", "amount", "raqam", "rakam", "honi", "hona",
        "chahye", "chahiye", "should", "be", "to", "make", "category", "catagory", "katagri", "dal", "daal", "dalo", "daalo",
        "rakho", "rakh", "move", "put", "ye", "yeh", "isko", "usko", "mene", "maine", "diye", "diya", "di", "liye", "liya", "li",
        "mile", "mila", "becha", "bechi", "qty", "quantity", "balke", "balki", "bulke", "adad", "ginti", "thin"
    )

    /** Words for the categories, so "grocery mein dal do" knows where to put it. */
    private val catNames: Map<String, Cat> = buildMap {
        Cat.entries.forEach { c -> c.label.lowercase().split(Regex("""[^\p{L}]+""")).filter { it.length >= 3 }.forEach { put(it, c) } }
        listOf("khana", "khane", "food", "grocery", "groceries", "sabzi", "ration", "meals").forEach { put(it, Cat.KHANA) }
        listOf("gari", "gaari", "safar", "taxi", "fuel").forEach { put(it, Cat.TRANSPORT) }
        listOf("kiraya", "rent").forEach { put(it, Cat.RENT) }
        listOf("bill", "bills", "bijli", "pani").forEach { put(it, Cat.BIJLI) }
        listOf("mobile", "recharge", "internet").forEach { put(it, Cat.MOBILE) }
        listOf("ghar", "home", "family").forEach { put(it, Cat.GHAR) }
        listOf("shopping", "kapre", "kapde", "clothes").forEach { put(it, Cat.SHOPPING) }
        listOf("sehat", "health", "dawai", "doctor", "medicine").forEach { put(it, Cat.SEHAT) }
        listOf("personal", "haircut").forEach { put(it, Cat.PERSONAL) }
        listOf("fees", "fee", "govt", "visa").forEach { put(it, Cat.FEES) }
        listOf("education", "school", "parhai").forEach { put(it, Cat.EDU) }
        listOf("charity", "gift", "sadqa", "zakat", "khairat").forEach { put(it, Cat.CHARITY) }
        listOf("asset", "assets").forEach { put(it, Cat.ASSETS) }
        listOf("other", "misc").forEach { put(it, Cat.OTHER) }
        // These words name kinds of entry, handled as types.
        listOf("income", "sales", "purchases", "loans").forEach { remove(it) }
    }

    /** Words for the kinds of entry. */
    private val typeNames = mapOf(
        "income" to Type.AAMDANI, "aamdani" to Type.AAMDANI, "amdani" to Type.AAMDANI, "kamai" to Type.AAMDANI,
        "expense" to Type.KHARCHA, "kharcha" to Type.KHARCHA, "kharch" to Type.KHARCHA,
        "sale" to Type.SALE, "purchase" to Type.PURCHASE
    )
    private val loanWords = setOf("udhaar", "udhar", "qarz", "karz", "qarza", "loan")

    fun plan(text: String, entries: List<Entry>, now: Long = System.currentTimeMillis()): EditPlan? {
        if (entries.isEmpty()) return null
        val words = text.lowercase().split(Regex("""[^\p{L}\p{N}]+""")).filter { it.isNotEmpty() }
        val (date, rest) = Parser.extractDate(text, now)
        val restLower = rest.lowercase()
        val nums = Regex("""\d+(?:\.\d+)?""").findAll(rest).map { it.value.toDouble() }.toList()

        // "A nahi B": what was wrong (A) and what is right (B).
        val swap = Regex("""(\p{L}+|\d+(?:\.\d+)?)\s+(?:nahi|nahin|nhi|ni|not)\s+(?:balke\s+|balki\s+|bulke\s+)?(\p{L}+|\d+(?:\.\d+)?)""").find(restLower)
        val swapOld = swap?.groupValues?.get(1)?.takeIf { it !in stop }
        val swapNew = swap?.groupValues?.get(2)?.takeIf { it !in stop }

        val strong = words.any { it in strongWords }
        val didSomething = words.any { it in actionVerbs }
        val catWord = categoryWord(words)
        val typeWord = words.firstOrNull { it in typeNames || it in loanWords }
        val aboutOld = words.contains("entry") || strong ||
            (swapOld != null && swapNew != null && !didSomething) ||
            (catWord != null && words.any { it in putWords || it in pastWords } && !didSomething && nums.isEmpty()) ||
            (words.any { it in setOf("kar", "kr", "karo", "kardo", "krdo", "do") } && words.contains("ko") && nums.size >= 2) ||
            (!didSomething && words.any { it in refers } && words.any { it in pastWords }) ||
            (!didSomething && date != null && nums.isEmpty() && words.any { it in pastWords })
        if (!aboutOld) return null

        // Which entry: the newest one whose item, note, person or category matches the other words.
        val skip = setOfNotNull(catWord, typeWord, swapNew)
        val keys = words.filter { it.length >= 3 && it.none { c -> c.isDigit() } && it !in stop && it !in skip }
        val newest = entries.sortedByDescending { it.createdAt }
        val matching = if (keys.isEmpty()) emptyList() else newest.filter { e -> keys.any { k -> hits(e, k) } }
        // Several match: prefer the one with the amount or quantity said ("hamza wali 500 ko 400").
        val target = matching.firstOrNull { e -> nums.any { it == e.amount || (e.qty > 0 && it == e.qty) } }
            ?: matching.firstOrNull()
            ?: (if (keys.isEmpty() || words.any { it in lastWords }) newest.firstOrNull() else null)
            ?: return null

        var changed = target
        val what = mutableListOf<String>()

        // Date
        if (date != null && !sameDay(date, target.date)) {
            changed = changed.copy(date = date)
            what += "date " + dayLabel(date)
        }

        // Numbers: amount or quantity.
        val oldNum = swapOld?.toDoubleOrNull()
        val newNum = swapNew?.toDoubleOrNull()
        val saysQty = words.any { it in setOf("qty", "quantity", "adad", "ginti") }
        fun setQty(n: Double) {
            val amount = if (target.rate > 0) Parser.total(n, target.rate, target.item) else target.amount
            changed = changed.copy(qty = n, amount = amount)
            what += "qty " + fmt(n) + (if (amount != target.amount) ", amount " + fmt(amount) else "")
        }
        when {
            newNum != null && newNum > 0 && target.qty > 0 && (saysQty || (oldNum == target.qty && oldNum != target.amount)) -> setQty(newNum)
            newNum != null && newNum > 0 -> { changed = changed.copy(amount = newNum); what += "amount " + fmt(newNum) }
            nums.size >= 2 && words.contains("ko") -> { changed = changed.copy(amount = nums.last()); what += "amount " + fmt(nums.last()) }
            nums.size == 1 && swap == null -> {
                val n = nums.first()
                if (saysQty && target.qty > 0) setQty(n) else { changed = changed.copy(amount = n); what += "amount " + fmt(n) }
            }
        }

        // A word swap: a person or an item.
        if (swapOld != null && swapNew != null && newNum == null && swapNew !in catNames && swapNew !in typeNames) {
            if (target.person.isNotBlank() && (target.person.equals(swapOld, true) || close(target.person.lowercase(), swapOld))) {
                val p = cap(swapNew); changed = changed.copy(person = p); what += "person $p"
            } else {
                val item = cap(swapNew)
                val guess = if (target.type == Type.KHARCHA) Parser.guessCategory(swapNew) else null
                changed = changed.copy(
                    item = item,
                    note = item,
                    category = guess?.first ?: changed.category
                )
                what += "item $item"
            }
        }

        // "hamza wali entry nadir ki hai": another person named next to "ki/ka/ko".
        if (target.person.isNotBlank() && swap == null) {
            val other = Regex("""(\p{L}+)\s+(?:ki|ka|ke|ko|se|ne)\s+(?:thi|tha|the|hai|hain|entry|di|diye|diya|liye|liya|li)\b""").find(restLower)
                ?.groupValues?.get(1)
            if (other != null && other.length >= 3 && other !in stop && other !in catNames && other !in typeNames && other !in loanWords &&
                !other.equals(target.person, true) && !close(other, target.person.lowercase()) &&
                !hits(target.copy(person = ""), other) && Parser.guessCategory(other) == null
            ) {
                val p = cap(other); changed = changed.copy(person = p); what += "person $p"
            }
        }

        // Category: the entry becomes an expense in that category.
        val newCat = catWord?.let { catNames[it] }
        if (newCat != null && newCat != target.category) {
            val itemNow = changed.item
            changed = changed.copy(
                type = Type.KHARCHA,
                category = newCat,
                item = if (itemNow.isBlank() || itemNow == target.category.label || itemNow == "Loans" || itemNow == "Income") newCat.label else itemNow,
                person = ""
            )
            what += "category ${newCat.label}"
        }

        // Type
        if (typeWord != null) {
            val t: Type? = if (typeWord in loanWords) {
                if (words.any { it in setOf("liya", "liye", "li", "lia", "took", "borrowed", "se") }) Type.LIYA else Type.DIYA
            } else typeNames[typeWord]
            if (t != null && t != changed.type) {
                val who = if (isLoan(t) || isTrade(t)) changed.person else ""
                if (!isLoan(t) || who.isNotBlank()) {
                    changed = changed.copy(
                        type = t,
                        person = who,
                        category = when {
                            isLoan(t) -> Cat.UDHAAR
                            t == Type.AAMDANI -> Cat.TANKHWAH
                            t == Type.SALE -> Cat.SALES
                            t == Type.PURCHASE -> Cat.STOCK
                            changed.category in setOf(Cat.TANKHWAH, Cat.UDHAAR, Cat.SALES, Cat.STOCK) -> Cat.OTHER
                            else -> changed.category
                        },
                        item = when {
                            isLoan(t) -> "Loans"
                            changed.item == "Loans" || changed.item.isBlank() -> t.label
                            else -> changed.item
                        }
                    )
                    what += t.label.lowercase()
                }
            }
        }

        if (changed == target) return null
        return EditPlan(listOf(target to changed), emptyList(), entryTitle(target) + " → " + what.joinToString(", "))
    }

    /** The category the sentence moves the entry to: "grocery mein", "category food", "transport wali hai". */
    private fun categoryWord(words: List<String>): String? {
        for (i in words.indices) {
            val w = words[i]
            if (w !in catNames) continue
            val next = words.getOrNull(i + 1)
            val prev = words.getOrNull(i - 1)
            if (next in setOf("mein", "me", "men", "main", "category", "catagory") ||
                prev in setOf("category", "catagory", "katagri")
            ) return w
        }
        return null
    }

    private fun hits(e: Entry, k: String): Boolean {
        val hay = (e.item + " " + e.note + " " + e.person + " " + e.category.label).lowercase()
            .split(Regex("""[^\p{L}]+""")).filter { it.length >= 3 }
        return hay.any { h -> h.startsWith(k) || k.startsWith(h) || close(h, k) }
    }

    /** "nshta" ~ "nashta": one letter off. */
    private fun close(a: String, b: String): Boolean {
        if (kotlin.math.abs(a.length - b.length) > 1 || minOf(a.length, b.length) < 4) return false
        val d = Array(a.length + 1) { IntArray(b.length + 1) }
        for (i in 0..a.length) d[i][0] = i
        for (j in 0..b.length) d[0][j] = j
        for (i in 1..a.length) for (j in 1..b.length) {
            d[i][j] = minOf(d[i - 1][j] + 1, d[i][j - 1] + 1, d[i - 1][j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1)
        }
        return d[a.length][b.length] <= 1
    }

    private fun cap(s: String) = s.lowercase().replaceFirstChar { it.uppercase() }
}
