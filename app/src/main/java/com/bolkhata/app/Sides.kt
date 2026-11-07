package com.bolkhata.app

/**
 * The Expense and Income tabs as accounts: people accounts (Staff, Suppliers, Customers, or one the owner adds) and
 * things accounts (Food, Bills… with Groceries, Electricity… inside). Everything is read from the saved entries:
 * a person's Paid / Work (or Received / Given) is their khata, and only cash entries reach the Cashbook.
 */
enum class Side { EXP, INC }

/** One account on a side. A things account is a category ([cat]), or one the owner named (its entries are Other). */
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

    /** Expense categories offered as ready-made accounts, in this order. */
    private val expenseCats = listOf(Cat.KHANA, Cat.TRANSPORT, Cat.BIJLI, Cat.RENT, Cat.MOBILE, Cat.SEHAT, Cat.EDU, Cat.SHOPPING,
        Cat.PERSONAL, Cat.CHARITY, Cat.GHAR, Cat.FEES, Cat.PRODUCTION, Cat.ASSETS, Cat.OTHER)

    /**
     * Every account on [side]: nothing ready-made; a category or people account shows once it has entries, then the
     * owner's own (one named like a category holds that category).
     */
    fun books(side: Side, all: List<Entry>, setup: SideSetup): List<Book> {
        val own = setup.books.filter { it.side == side }.map { b ->
            if (b.people) b else Cat.values().firstOrNull { it.label.equals(b.name, true) && side == Side.EXP }?.let { b.copy(name = it.label, cat = it) } ?: b
        }
        fun hasPeople(name: String) = setup.people.values.any { it.equals(name, true) } ||
            all.any { withPerson(it) && bookOf(it.person, all, setup) == name }
        val people = if (side == Side.EXP) listOf(STAFF, SUPPLIERS, PEOPLE).filter(::hasPeople).map { Book(side, it, true) }
        else listOf(CUSTOMERS).filter(::hasPeople).map { Book(side, it, true) }
        val things = if (side == Side.EXP) {
            val used = all.filter { it.type == Type.KHARCHA }.map { it.category }.toSet()
            expenseCats.filter { it in used }.map { Book(side, it.label, false, it) }
        } else listOfNotNull(Book(side, INCOME, false).takeIf { all.any { it.type == Type.AAMDANI } })
        // Expense lists its things (Food, Bills…) first and the people after; Income starts with Customers.
        val p = people + own.filter { it.people }
        val t = things + own.filter { !it.people }
        return (if (side == Side.EXP) t + p else p + t).distinctBy { it.name.lowercase() }
    }

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

    /** The side a people account belongs to. */
    fun sideOf(book: String, setup: SideSetup) =
        if (book == CUSTOMERS) Side.INC else setup.books.firstOrNull { it.name.equals(book, true) }?.side ?: Side.EXP

    /** A person's khata entries: everything with them that is not a sale or purchase settled in cash on the spot. */
    private fun personEntries(all: List<Entry>) = all.filter { withPerson(it) && !it.paid && it.person.isNotBlank() }

    /** The people or things inside [book], each with its entries newest first. */
    fun kids(book: Book, all: List<Entry>, setup: SideSetup): List<Kid> {
        val newest = all.sortedWith(compareByDescending<Entry> { it.date }.thenByDescending { it.createdAt })
        val extra = setup.kids[book.name].orEmpty()
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

    /** What a thing entry is called inside its account: the payer for income, else its item (or "Other"). */
    fun thingName(e: Entry): String =
        if (e.type == Type.AAMDANI) source(e) else e.item.trim().takeIf { it.isNotBlank() && !it.equals(e.category.label, true) } ?: "Other"

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
    fun counts(book: Book, e: Entry): Boolean = !book.people || when (book.side) {
        Side.EXP -> book.name != PEOPLE && e.type == Type.PURCHASE
        Side.INC -> e.type == Type.SALE
    }

    /** What the people accounts on [side] hold: (advance left or to get, to pay or advance taken), both as plus figures. */
    fun owed(side: Side, all: List<Entry>, setup: SideSetup): Pair<Double, Double> {
        val ks = books(side, all, setup).filter { it.people && it.name != PEOPLE }.flatMap { kids(it, all, setup) }
        return ks.filter { it.balance > 0.5 }.sumOf { it.balance } to ks.filter { it.balance < -0.5 }.sumOf { -it.balance }
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

    /** Items: each one sold (goods given), to whom, and what is left of what came in (made or bought). */
    class Item(val name: String, val given: Double, val amount: Double, val inStock: Double, val who: List<Pair<String, Double>>, val stockIn: List<Entry>)

    fun items(all: List<Entry>, setup: SideSetup): List<Item> {
        val staff = all.filter { it.person.isNotBlank() && bookOf(it.person, all, setup) == STAFF }.map { it.id }.toSet()
        val sales = all.filter { it.type == Type.SALE && it.qty > 0 && it.item.isNotBlank() && !it.item.equals("Sale", true) }
        val ins = all.filter { it.type == Type.PURCHASE && it.qty > 0 && it.item.isNotBlank() && it.id !in staff && !it.item.equals("Purchase", true) }
            .sortedByDescending { it.date }
        val names = (sales.map { it.item.trim() } + ins.map { it.item.trim() }).distinctBy { it.lowercase() }
        return names.map { n ->
            val s = sales.filter { it.item.trim().equals(n, true) }
            val i = ins.filter { it.item.trim().equals(n, true) }
            Item(n, s.sumOf { it.qty }, s.sumOf { it.amount }, i.sumOf { it.qty } - s.sumOf { it.qty },
                s.groupBy { it.person.trim().ifBlank { "Cash sale" } }.map { (p, l) -> p to l.sumOf { it.qty } }.sortedByDescending { it.second }, i)
        }.sortedByDescending { it.amount }
    }

    /** The profit report: sale, other income, work and purchases, expenses, and what is left. */
    class Profit(val sale: Double, val income: Double, val work: Double, val spent: Double) {
        val profit = sale + income - work - spent
    }

    fun profit(period: List<Entry>): Profit = Profit(
        period.filter { it.type == Type.SALE }.sumOf { it.amount },
        period.filter { it.type == Type.AAMDANI }.sumOf { it.amount },
        period.filter { it.type == Type.PURCHASE }.sumOf { it.amount },
        period.filter { it.type == Type.KHARCHA }.sumOf { it.amount }
    )
}
