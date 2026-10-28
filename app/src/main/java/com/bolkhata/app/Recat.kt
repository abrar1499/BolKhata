package com.bolkhata.app

/**
 * "Other wali entries theek karo": the app itself puts expenses that sit in Other into the right category,
 * grouped by shop / item name ("PANDA RETAIL" -> Food). Transfers are left alone (sending money is not spending).
 * Fully offline; the person checks the list before anything changes.
 */
object Recat {

    class Group(val name: String, val ids: List<String>, val total: Double, val guess: Cat)

    /** Shops and brands common in Saudi Arabia / Pakistan, by the word that appears in a statement line. */
    private val shops: List<Pair<Cat, List<String>>> = listOf(
        Cat.KHANA to listOf(
            "panda", "othaim", "lulu", "danube", "tamimi", "carrefour", "bindawood", "nesto", "farm", "manuel", "hyper",
            "super", "market", "baqala", "bakala", "grocery", "bakery", "albaik", "kudu", "herfy", "mcdonald", "kfc", "hardee",
            "burger", "pizza", "domino", "maestro", "shawarma", "mandi", "restaurant", "resturant", "cafe", "coffee", "starbucks",
            "dunkin", "tim", "barn", "kabsa", "broast", "food", "foods", "kitchen", "matam", "mataam", "hungerstation", "jahez",
            "talabat", "mrsool", "toyou", "ninja", "keeta", "chefz", "sweets", "juice", "tea", "chai"
        ),
        Cat.TRANSPORT to listOf(
            "aldrees", "sasco", "petromin", "naft", "petrol", "fuel", "station", "gas", "careem", "uber", "bolt", "jeeny",
            "taxi", "saptco", "train", "parking", "mawaqif", "tire", "tyre", "carwash", "wash", "oil"
        ),
        Cat.MOBILE to listOf("stc", "mobily", "zain", "virgin", "salam", "lebara", "friendi", "redbull", "jawwy", "recharge", "sawa"),
        Cat.SEHAT to listOf("nahdi", "dawaa", "pharmacy", "pharma", "hospital", "clinic", "medical", "dental", "lab", "whites"),
        Cat.SHOPPING to listOf(
            "jarir", "extra", "ikea", "centrepoint", "centerpoint", "max", "shein", "noon", "amazon", "namshi", "redtag",
            "splash", "abyat", "saco", "electronics", "store", "mall", "shop", "trading", "tradin", "tamara", "tabby", "online",
            "purchase", "aliexpress", "temu"
        ),
        Cat.BIJLI to listOf("sec", "electricity", "water", "nwc", "bill", "sadad"),
        Cat.PERSONAL to listOf("salon", "barber", "hallaq", "laundry", "gym", "fitness"),
        Cat.FEES to listOf("absher", "muqeem", "jawazat", "iqama", "visa", "moi", "traffic", "fine", "gosi", "qiwa", "fees", "fee"),
        Cat.EDU to listOf("school", "university", "course", "udemy", "books"),
        Cat.CHARITY to listOf("ehsan", "charity", "donation", "zakat", "sadaqa")
    )

    private val transferWords = listOf("transfer", "ips", "sarie", "remittance", "wire", "western", "enjaz", "stcpay", "urpay", "withdrawal", "atm", "cash withdrawal")

    /** The shop or item name of an entry, without numbers and bank words. */
    fun nameOf(e: Entry): String {
        val raw = e.item.ifBlank { e.note }
        return raw.replace(Regex("\\d+"), " ").replace(Regex("[^\\p{L}&' ]+"), " ").replace(Regex("\\s+"), " ").trim()
            .split(' ').take(4).joinToString(" ").ifBlank { e.category.label }
    }

    fun isTransfer(e: Entry): Boolean {
        val t = (e.item + " " + e.note).lowercase()
        return transferWords.any { t.contains(it) }
    }

    /** The category a shop name points to, or null when the app can't tell. */
    fun guess(name: String): Cat? {
        val words = name.lowercase().split(Regex("[^\\p{L}]+")).filter { it.length >= 2 }
        shops.firstOrNull { (_, keys) -> words.any { w -> keys.any { k -> w == k || (k.length >= 5 && w.startsWith(k)) } } }?.let { return it.first }
        // Only shops the person sorted before (exact words); no fuzzy guessing, so "Cash withdrawal" never becomes Food.
        return words.firstNotNullOfOrNull { Parser.learned[it]?.first }?.takeIf { it != Cat.OTHER && it != Cat.UDHAAR }
    }

    /** Expenses in Other, grouped by name, with the app's guess (Other when it can't tell). */
    fun plan(entries: List<Entry>): List<Group> =
        entries.filter { it.type == Type.KHARCHA && it.category == Cat.OTHER && !isTransfer(it) }
            .groupBy { nameOf(it).lowercase() }
            .map { (_, list) ->
                val name = nameOf(list.first())
                Group(name, list.map { it.id }, list.sumOf { it.amount }, guess(name) ?: Cat.OTHER)
            }
            .sortedWith(compareBy<Group> { it.guess == Cat.OTHER }.thenByDescending { it.total })

    /** The categories an expense can be moved to. */
    val expenseCats = Cat.entries.filter { it !in setOf(Cat.TANKHWAH, Cat.UDHAAR, Cat.SALES, Cat.STOCK) }

    /** "other wali entries theek karo", "jo expense other me hain unko set karo", "fix other". */
    fun isCommand(text: String): Boolean {
        val w = text.lowercase().split(Regex("[^\\p{L}]+")).toSet()
        val other = "other" in w || "others" in w || "دیگر" in w
        val act = w.any { it in setOf("theek", "thek", "thik", "sahi", "set", "fix", "correct", "dalo", "daalo", "lagao", "category", "categories", "move", "karo", "kro", "kr") }
        return other && act && text.none { it.isDigit() }
    }
}
