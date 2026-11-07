package com.bolkhata.app

import org.junit.Assert.assertEquals
import org.junit.Test

/** Party, Expense and Income accounts: staff Paid | Work, customers Given | Received, Purchases / Sales, profit and net worth, all from the entries. */
class SidesTest {
    private var n = 0L
    private fun en(t: Type, amt: Double, person: String = "", item: String = "", cat: Cat = Cat.UDHAAR, qty: Double = 0.0): Entry {
        n++; return Entry("e$n", t, amt, cat, item, person, "", n, n, qty = qty)
    }
    private val all = listOf(
        en(Type.DIYA, 20000.0, "Hamid Akram"), en(Type.PURCHASE, 8000.0, "Hamid Akram", "Task A", Cat.PRODUCTION, 200.0),
        en(Type.SALE, 70000.0, "Nargis Salim Rashid", "Item A", Cat.SALES, 400.0), en(Type.WAPIS_MILA, 50000.0, "Nargis Salim Rashid"),
        en(Type.KHARCHA, 1500.0, item = "Groceries", cat = Cat.KHANA), en(Type.PURCHASE, 0.0, "", "Item A", Cat.STOCK, 5000.0)
    )
    private val setup = SideSetup(people = mapOf("Hamid Akram" to Sides.STAFF))

    @Test fun staffPaidMoreThanWorkIsAdvanceLeft() {
        val staff = Sides.books(Side.PARTY, all, setup).first { it.name == Sides.STAFF }
        val k = Sides.kids(staff, all, setup).single()
        assertEquals("12,000" to "Advance left", Sides.balanceText(staff, k.balance))
        assertEquals(8000.0, Sides.done(staff, all, setup) { true }, 0.0)
    }

    @Test fun customerGivenMoreThanReceivedIsToGet() {
        val cust = Sides.books(Side.PARTY, all, setup).first { it.name == Sides.CUSTOMERS }
        val k = Sides.kids(cust, all, setup).single()
        assertEquals("20,000" to "To get", Sides.balanceText(cust, k.balance))
        // Customers owe 20,000; the staff advance of 12,000 is also to get.
        assertEquals(32000.0 to 0.0, Sides.owed(all, setup))
    }

    @Test fun salesAndPurchasesFillThemselvesFromTheKhatas() {
        assertEquals(listOf(Sides.SALES), Sides.books(Side.INC, all, setup).map { it.name })
        val sales = Sides.books(Side.INC, all, setup).first { it.name == Sides.SALES }
        assertEquals("Nargis Salim Rashid" to 70000.0, Sides.kids(sales, all, setup).single().let { it.name to it.total })
        val buys = Sides.books(Side.EXP, all, setup).first { it.name == Sides.PURCHASES }
        assertEquals("Hamid Akram" to 8000.0, Sides.kids(buys, all, setup).single().let { it.name to it.total })
        assertEquals(8000.0, Sides.done(buys, all, setup) { true }, 0.0)
        // No people accounts sit in Expense or Income any more.
        assertEquals(emptyList<String>(), (Sides.books(Side.EXP, all, setup) + Sides.books(Side.INC, all, setup)).filter { it.people }.map { it.name })
    }

    @Test fun itemsShowGivenAndWhatIsLeft() {
        val item = Sides.items(all, setup).single()
        assertEquals(400.0, item.given, 0.0)
        assertEquals(4600.0, item.inStock, 0.0)
    }

    @Test fun profitIsSaleAndIncomeLessWorkAndSpent() {
        assertEquals(60500.0, Sides.profit(all).profit, 0.0)
    }

    @Test fun foodHoldsItsThings() {
        val food = Sides.books(Side.EXP, all, setup).first { it.cat == Cat.KHANA }
        assertEquals(1500.0, Sides.kids(food, all, setup).first { it.name == "Groceries" }.total, 0.0)
    }

    @Test fun rawMaterialIsNotAnItem() {
        val raw = all + en(Type.PURCHASE, 30000.0, "Sabir Coal Co", "Kachi eint", Cat.PRODUCTION, 50000.0)
        assertEquals(listOf("Item A"), Sides.items(raw, setup).map { it.name })
    }

    @Test fun aRenamedAccountTakesItsThingsAndOtherGoesAway() {
        val moved = listOf(en(Type.KHARCHA, 700.0, item = "Groceries", cat = Cat.OTHER))
        val own = SideSetup(books = listOf(Book(Side.EXP, "Kitchen", false)), kids = mapOf("Kitchen" to listOf("Groceries")))
        val books = Sides.books(Side.EXP, moved, own)
        assertEquals(listOf("Kitchen"), books.map { it.name })
        assertEquals(700.0, Sides.done(books.single(), moved, own) { true }, 0.0)
    }

    @Test fun assetsAreNotAnExpenseButCountInNetWorth() {
        val withAsset = all + en(Type.KHARCHA, 9000.0, item = "Laptop", cat = Cat.ASSETS)
        assertEquals(60500.0, Sides.profit(withAsset).profit, 0.0)
        val w = Sides.worth(withAsset, setup)
        assertEquals(9000.0, w.assets, 0.0)
        // Cash: 50,000 in − 20,000 − 1,500 − 9,000 out; to get 32,000; nothing in stock cost anything; nothing to pay.
        assertEquals(19500.0, w.cash, 0.0)
        assertEquals(19500.0 + 32000.0 + 9000.0, w.net, 0.0)
    }
}
