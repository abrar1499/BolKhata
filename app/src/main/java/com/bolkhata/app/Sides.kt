package com.bolkhata.app

/**
 * The Party, Expense and Income tabs as accounts. Party holds the people accounts (Customers, Suppliers, Staff, People,
 * or one the owner adds); Expense and Income hold things accounts (Food, Bills… with Groceries, Electricity… inside).
 * Everything is read from the saved entries: a person's Paid / Work (or Given / Received) is their khata, and only cash
 * entries reach the Cashbook. What customers bought shows by itself in Income › Sales, and what staff and suppliers
 * did or supplied in Expense › Purchases.
 */
enum class Side { EXP, INC, PARTY }

/**
 * One account. A things account is a category ([cat]), or one the owner named (its entries are Other). A people account
 * keeps the side its columns come from: INC for Given | Received, EXP for Paid | Work; Party lists both.
 */
data class Book(val side: Side, val name: String, val people: Boolean, val cat: Cat? = null)

/** What the owner set up: their own accounts, who sits in which people account, and extra things inside an account. */
data class SideSetup(
    val books: List<Book> = emptyList(),
    /** Person (as typed) to the people account they were put in. */
    val people: Map<String, String> = emptyMap(),
    /** Account name to the extra things the owner added inside it. */
    val kids: Map<String, List<String>> = emptyMap()
)

/** One person or thing inside an account, with its entries newest first. */
class Kid(val name: String, val entries: List<Entry>) {
    /** For a person: what they owe you (+) or you owe them (−); for a thing: what it cost or brought in. */
    val balance = entries.sumOf { personEffect(it) }
    val total = entries.sumOf { it.amount }
}

object Sides {
    const val STAFF = "Staff"
    const val SUPPLIERS = "Suppliers"
    const val CUSTOMERS = "Customers"
    /** People with only loans (gave / took), who are neither staff, suppliers nor customers. */
    const val PEOPLE = "People"
    const val INCOME = "Income received"
    /** Expense › Purchases and Income › Sales: filled from the people's khatas, nothing is added in them. */
    const val PURCHASES = "Purchases"
    const val SALES = "Sales"

    /** Expense categories offered as ready-made accounts, in this order. */
    private val expenseCats = listOf(Cat.KHANA, Cat.TRANSPORT, Cat.BIJLI, Cat.RENT, Cat.MOBILE, Cat.SEHAT, Cat.EDU, Cat.SHOPPING,
        Cat.PERSONAL, Cat.CHARITY, Cat.GHAR, Cat.FEES, Cat.PRODUCTION, Cat.ASSETS, Cat.OTHER)

    /**
     * Every account on [side]: nothing ready-made; a category or people account shows once it has entries, then the
     * owner's own (one named like a category holds that category).
     */
    fun books(side: Side, all: List<Entry>, setup: SideSetup): List<Book> {
        if (side == Side.PARTY) {
            fun hasPeople(name: String) = setup.people.values.any { it.equals(name, true) } ||
                all.any { withPerson(it) && bookOf(it.person, all, setup) == name }
            val ready = listOf(CUSTOMERS to Side.INC, SUPPLIERS to Side.EXP, STAFF to Side.EXP, PEOPLE to Side.EXP)
                .filter { hasPeople(it.first) }.map { (n, s) -> Book(s, n, true) }
            return (ready + setup.books.filter { it.people }).distinctBy { it.name.lowercase() }
        }
        val own = setup.books.filter { it.side == side && !it.people }.map { b ->
            Cat.values().firstOrNull { it.label.equals(b.name, true) && side == Side.EXP }?.let { b.copy(name = it.label, cat = it) } ?: b
        }
        // Entries kept in the owner's own accounts don't make Other or Income received show.
        val mine = ownThings(side, setup)
        fun loose(e: Entry) = mine[thingName(e).lowercase()] == null
        val things = if (side == Side.EXP) {
            val used = all.filter { it.type == Type.KHARCHA && (it.category != Cat.OTHER || loose(it)) }.map { it.category }.toSet()
            expenseCats.filter { it in used }.map { Book(side, it.label, false, it) } +
                listOfNotNull(Book(side, PURCHASES, false).takeIf { all.any { it.type == Type.PURCHASE && it.amount > 0 } })
        } else listOfNotNull(
            Book(side, SALES, false).takeIf { all.any { it.type == Type.SALE && it.amount > 0 } },
            Book(side, INCOME, false).takeIf { all.any { it.type == Type.AAMDANI && loose(it) } }
        )
        return (things + own).distinctBy { it.name.lowercase() }
    }

    /** Whether [book] is Purchases or Sales: people's work, goods and sales, read from their khatas. */
    fun isAuto(book: Book) = !book.people && book.cat == null &&
        (book.side == Side.EXP && book.name == PURCHASES || book.side == Side.INC && book.name == SALES)

    /** The people account [person] sits in: the one the owner picked, else Customers for sales, Suppliers for purchases. */
    fun bookOf(person: String, all: List<Entry>, setup: SideSetup): String {
        setup.people.entries.firstOrNull { it.key.trim().equals(person.trim(), true) }?.let { return it.value }
        val mine = all.filter { it.person.trim().equals(person.trim(), true) }
        return when {
            mine.any { it.type == Type.SALE } -> CUSTOMERS
            mine.any { it.type == Type.PURCHASE } -> SUPPLIERS
            else -> PEOPLE
        }
    }

    /** A person's khata entries: everything with them that is not a sale or purchase settled in cash on the spot. */
    private fun personEntries(all: List<Entry>) = all.filter { withPerson(it) && !it.paid && it.person.isNotBlank() }

    /** The people or things inside [book], each with its entries newest first. */
    fun kids(book: Book, all: List<Entry>, setup: SideSetup): List<Kid> {
        val newest = all.sortedWith(compareByDescending<Entry> { it.date }.thenByDescending { it.createdAt })
        val extra = setup.kids[book.name].orEmpty()
        if (isAuto(book)) {
            val t = if (book.side == Side.EXP) Type.PURCHASE else Type.SALE
            return newest.filter { it.type == t && it.amount > 0 }.groupBy { it.person.trim().lowercase() }.values
                .map { l -> Kid(l.first().person.trim().ifBlank { if (t == Type.SALE) "Cash sale" else "Cash purchase" }, l) }
                .sortedByDescending { it.entries.first().date }
        }
        if (book.people) {
            val byPerson = personEntries(newest).groupBy { it.person.trim().lowercase() }
                .filter { (_, l) -> bookOf(l.first().person, all, setup).equals(book.name, true) }
            val added = setup.people.filterValues { it.equals(book.name, true) }.keys.map { it.trim() }
            val names = byPerson.map { (_, l) -> l.groupingBy { it.person.trim() }.eachCount().maxByOrNull { it.value }!!.key } +
                added.filter { it.lowercase() !in byPerson.keys }
            return names.map { n -> Kid(n, byPerson[n.lowercase()].orEmpty()) }
                .sortedByDescending { it.entries.firstOrNull()?.date ?: 0L }
        }
        val list = thingEntries(book, newest, setup)
        val groups = list.groupBy { thingName(it).lowercase() }
        val names = (groups.values.map { thingName(it.first()) } + extra)
            .distinctBy { it.lowercase() }
        return names.map { n -> Kid(n, groups[n.lowercase()].orEmpty()) }
            .sortedWith(compareByDescending<Kid> { it.entries.firstOrNull()?.date ?: 0L })
    }

    /** What a thing entry is called inside its account: the payer for income, else its item ("Sent home" stays itself). */
    fun thingName(e: Entry): String =
        if (e.type == Type.AAMDANI) source(e) else e.item.trim().ifBlank { "Other" }

    /** Things inside the owner's own accounts, by lowercase name, so their entries leave Other. */
    private fun ownThings(side: Side, setup: SideSetup): Map<String, String> =
        setup.books.filter { it.side == side && !it.people }.flatMap { b -> setup.kids[b.name].orEmpty().map { it.lowercase() to b.name } }.toMap()

    private fun thingEntries(book: Book, newest: List<Entry>, setup: SideSetup): List<Entry> {
        val own = ownThings(book.side, setup)
        return if (book.side == Side.INC) {
            val inc = newest.filter { it.type == Type.AAMDANI }
            if (book.cat == null && book.name != INCOME) inc.filter { own[thingName(it).lowercase()] == book.name }
            else inc.filter { own[thingName(it).lowercase()] == null }
        } else {
            val out = newest.filter { it.type == Type.KHARCHA }
            when {
                book.cat == null -> out.filter { it.category == Cat.OTHER && own[thingName(it).lowercase()] == book.name }
                book.cat == Cat.OTHER -> out.filter { it.category == Cat.OTHER && own[thingName(it).lowercase()] == null }
                else -> out.filter { it.category == book.cat }
            }
        }
    }

    /** What an account did in the month [inMonth] picks: everything spent and worked (Expense) or earned and given (Income). */
    fun done(book: Book, all: List<Entry>, setup: SideSetup, inMonth: (Long) -> Boolean): Double =
        kids(book, all, setup).sumOf { k -> k.entries.filter { inMonth(it.date) && counts(book, it) }.sumOf { it.amount } }

    /** Whether an entry is a cost (Expense) or an earning (Income): a person's work or goods given, a thing's amount. */
    fun counts(book: Book, e: Entry): Boolean = !book.people ||
        (if (book.side == Side.INC) e.type == Type.SALE else book.name != PEOPLE && e.type == Type.PURCHASE)

    /** What every person's khata adds up to in the period: (to get, to give), both as plus figures. */
    fun owed(all: List<Entry>, setup: SideSetup, inPeriod: (Long) -> Boolean = { true }): Pair<Double, Double> {
        val v = books(Side.PARTY, all, setup).flatMap { kids(it, all, setup) }
            .map { k -> k.entries.filter { inPeriod(it.date) }.sumOf { personEffect(it) } }
        return v.filter { it > 0.5 }.sum() to v.filter { it < -0.5 }.sumOf { -it }
    }

    /** Labels of a people account's two columns and the add buttons: (plus side, minus side). */
    fun columns(book: Book): Pair<String, String> = when {
        book.side == Side.INC -> "Given" to "Received"
        book.name == PEOPLE -> "Paid" to "Received"
        else -> "Paid" to "Work"
    }

    /** A person's balance in words: (amount, what it means). */
    fun balanceText(book: Book, v: Double): Pair<String, String> = when {
        kotlin.math.abs(v) < 0.5 -> fmt(0.0) to "Settled"
        book.side == Side.INC -> fmt(kotlin.math.abs(v)) to if (v > 0) "To get" else "Advance taken"
        book.name == PEOPLE -> fmt(kotlin.math.abs(v)) to if (v > 0) "To get" else "To give"
        else -> fmt(kotlin.math.abs(v)) to if (v > 0) "Advance left" else "To pay"
    }

    /** Items: each one sold or made, to whom it was given, and what is left of what came in (made or bought). */
    class Item(val name: String, val given: Double, val amount: Double, val inStock: Double, val who: List<Pair<String, Double>>, val stockIn: List<Entry>)

    fun items(all: List<Entry>, setup: SideSetup): List<Item> {
        val staff = all.filter { it.person.isNotBlank() && bookOf(it.person, all, setup) == STAFF }.map { it.id }.toSet()
        val sales = all.filter { it.type == Type.SALE && it.qty > 0 && it.item.isNotBlank() && !it.item.equals("Sale", true) }
        val ins = all.filter { it.type == Type.PURCHASE && it.qty > 0 && it.item.isNotBlank() && it.id !in staff && !it.item.equals("Purchase", true) }
            .sortedByDescending { it.date }
        // Only goods you sell or make show here: raw material (kachi eint, coal…) used up in making is a cost, not stock.
        val names = (sales.map { it.item.trim() } + ins.filter(::isMade).map { it.item.trim() }).distinctBy { it.lowercase() }
        return names.map { n ->
            val s = sales.filter { it.item.trim().equals(n, true) }
            val i = ins.filter { it.item.trim().equals(n, true) }
            Item(n, s.sumOf { it.qty }, s.sumOf { it.amount }, i.sumOf { it.qty } - s.sumOf { it.qty },
                s.groupBy { it.person.trim().ifBlank { "Cash sale" } }.map { (p, l) -> p to l.sumOf { it.qty } }.sortedByDescending { it.second }, i)
        }.sortedByDescending { it.amount }
    }

    /** What one of [item] cost to make or buy, from what came in; 0 when nothing came in with a cost. */
    fun unitCost(item: Item): Double {
        val qty = item.stockIn.sumOf { it.qty }
        return if (qty > 0) item.stockIn.sumOf(::costOf) / qty else 0.0
    }

    /** The profit report: sales, other income, purchases, expenses, and what is left. Assets bought are not an expense. */
    class Profit(val sale: Double, val income: Double, val work: Double, val spent: Double) {
        val profit = sale + income - work - spent
    }

    fun profit(period: List<Entry>): Profit = Profit(
        period.filter { it.type == Type.SALE }.sumOf { it.amount },
        period.filter { it.type == Type.AAMDANI }.sumOf { it.amount },
        period.filter { it.type == Type.PURCHASE }.sumOf { it.amount },
        period.filter { it.type == Type.KHARCHA && it.category != Cat.ASSETS }.sumOf { it.amount }
    )

    /** Net worth today: cash in hand, dues to get, stock and assets at what they cost, less what you owe. */
    class Worth(val cash: Double, val toGet: Double, val stock: Double, val assets: Double, val toPay: Double) {
        val net = cash + toGet + stock + assets - toPay
    }

    fun worth(all: List<Entry>, setup: SideSetup): Worth {
        val (get, pay) = owed(all, setup)
        return Worth(
            all.filter(::cashIn).sumOf { it.amount } - all.filter(::cashOut).sumOf { it.amount }, get,
            items(all, setup).sumOf { maxOf(0.0, it.inStock) * unitCost(it) },
            all.filter { it.type == Type.KHARCHA && it.category == Cat.ASSETS }.sumOf { it.amount }, pay
        )
    }
}
