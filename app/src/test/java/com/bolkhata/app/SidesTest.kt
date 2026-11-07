package com.bolkhata.app

import org.junit.Assert.assertEquals
import org.junit.Test

/** Expense / Income accounts: staff Paid | Work, customers Given | Received, items and profit, all from the entries. */
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
        val staff = Sides.books(Side.EXP, all, setup).first { it.name == Sides.STAFF }
        val k = Sides.kids(staff, all, setup).single()
        assertEquals("12,000" to "Advance left", Sides.balanceText(staff, k.balance))
        assertEquals(8000.0, Sides.done(staff, all, setup) { true }, 0.0)
    }

    @Test fun customerGivenMoreThanReceivedIsToGet() {
        val cust = Sides.books(Side.INC, all, setup).first { it.name == Sides.CUSTOMERS }
        val k = Sides.kids(cust, all, setup).single()
        assertEquals("20,000" to "To get", Sides.balanceText(cust, k.balance))
        assertEquals(20000.0 to 0.0, Sides.owed(Side.INC, all, setup))
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

    @Test fun workAddsUpByKindAndWorker() {
        val w = Sides.works(all, setup).single()
        assertEquals("Task A", w.name)
        assertEquals(200.0, w.qty, 0.0)
        assertEquals(8000.0, w.amount, 0.0)
        assertEquals("Hamid Akram", w.who.single().first)
    }

    @Test fun ownTransfersShowInMyAccountsButAreNotIncome() {
        val moved = all + en(Type.TRANSFER_OUT, 200.0, item = "To D360", cat = Cat.OTHER)
        val mine = Sides.books(Side.INC, moved, setup).first { it.name == Sides.MY_ACCOUNTS }
        assertEquals(0.0, Sides.done(mine, moved, setup) { true }, 0.0)
        assertEquals("D360" to 200.0, Sides.kids(mine, moved, setup).single().let { it.name to it.balance })
    }
}
