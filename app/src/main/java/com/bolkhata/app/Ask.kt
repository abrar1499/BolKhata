package com.bolkhata.app

/** The option card in the chat: a person and a number with no word for what happened ("hamid 10000"). */
object Ask {
    /** The saved person and the number of a message that says nothing else; null for any other message. */
    fun of(said: String, who: (String) -> String?): Pair<String, Double>? {
        val shape = AiAgent.shape(said, who)
        if (shape != "@ #" && shape != "# @") return null
        return AiAgent.people(said, who).single() to AiAgent.numbers(said).single()
    }

    /** What can have happened with someone in the account [book], in the order shown. */
    fun kinds(book: String): List<String> = when (book) {
        Sides.CUSTOMERS -> listOf("Received", "Given", "Paid")
        Sides.PEOPLE -> listOf("Paid", "Received")
        else -> listOf("Paid", "Work", "Received")
    }

    /** Work and Given count things at a rate; Paid and Received are money. */
    fun goods(kind: String) = kind == "Work" || kind == "Given"

    private fun type(kind: String) = when (kind) {
        "Paid" -> Type.DIYA
        "Received" -> Type.WAPIS_MILA
        "Work" -> Type.PURCHASE
        else -> Type.SALE
    }

    /** The person's last works or goods of this kind, newest first, one per item: offered with their rate. */
    fun usual(kind: String, person: String, all: Collection<Entry>): List<Entry> =
        all.filter { it.type == type(kind) && it.rate > 0 && it.item.isNotBlank() && it.person.equals(person, true) }
            .sortedByDescending { it.date }.distinctBy { it.item.lowercase() }.take(2)

    /** The entry a picked option saves: money as said, or [n] things of [item] at [rate] (per thousand when [per1000]). */
    fun entry(kind: String, person: String, n: Double, staff: Boolean, now: Long, item: String = "", rate: Double = 0.0, per1000: Boolean = false): Entry {
        val goods = goods(kind)
        val category = when (kind) { "Work" -> if (staff) Cat.PRODUCTION else Cat.STOCK; "Given" -> Cat.SALES; else -> Cat.UDHAAR }
        return Entry(
            id = "e" + System.nanoTime(), type = type(kind), amount = if (!goods) n else if (per1000) n / 1000 * rate else n * rate,
            category = category, item = if (goods) item.trim() else category.label, person = person, note = "", date = now, createdAt = now,
            qty = if (goods) n else 0.0, unit = if (goods && per1000) "1000" else "", rate = if (goods) rate else 0.0
        )
    }
}
