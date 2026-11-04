package com.bolkhata.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Cash book: what moves cash in hand, and what stays only on a party's khata. */
class CashBookTest {
    private fun e(type: Type, person: String = "", paid: Boolean = false) =
        Entry("1", type, 500.0, Cat.OTHER, "Cement", person, "", 0L, 0L, paid = paid)

    @Test
    fun expensesAndLoansMoveCash() {
        assertTrue(cashOut(e(Type.KHARCHA)))
        assertTrue(cashOut(e(Type.DIYA, "Hamid Akram")))
        assertTrue(cashIn(e(Type.AAMDANI)))
        assertTrue(cashIn(e(Type.WAPIS_MILA, "Hamid Akram")))
    }

    @Test
    fun creditSaleOrPurchaseMovesNoCash() {
        assertFalse(cashIn(e(Type.SALE, "Hamid Akram")))
        assertFalse(cashOut(e(Type.PURCHASE, "Nargis Salim Rashid")))
        assertTrue(cashIn(e(Type.SALE, "Hamid Akram", paid = true)))
        assertTrue(cashOut(e(Type.PURCHASE)))
    }

    @Test
    fun incomeSourceIsThePayer() {
        val alert = Entry("2", Type.AAMDANI, 1500.0, Cat.TANKHWAH, "Transfer", "", "From Hamid Akram. 20261007REF", 0L, 0L)
        assertEquals("Hamid Akram", source(alert))
        assertEquals("Nargis Trading Est.", source(alert.copy(item = "Nargis Trading Est.", note = "Company income")))
    }

    @Test
    fun businessViewsPickTheirEntries() {
        val all = listOf(e(Type.SALE).copy(id = "s"), e(Type.PURCHASE).copy(id = "p"), e(Type.DIYA, "Hamid Akram").copy(id = "d"), e(Type.KHARCHA).copy(id = "k"))
        assertEquals(listOf("d"), aboutView(0, all).map { it.id })
        assertEquals(listOf("s", "p"), aboutView(1, all).map { it.id })
        assertEquals(listOf("s"), aboutView(2, all).map { it.id })
        assertEquals(listOf("p"), aboutView(3, all).map { it.id })
        assertEquals("+1,150", plusMinus(1150.0))
        assertEquals("−400", plusMinus(-400.0))
    }

    @Test
    fun movingOneShopExpenseMovesTheRest() {
        fun x(id: String, note: String, cat: Cat = Cat.OTHER) = Entry(id, Type.KHARCHA, 2.0, cat, "Other", "", note, 0L, 0L)
        val all = listOf(x("1", "NESTO MARKET 12"), x("2", "NESTO MARKET 99"), x("3", "Card payment"), x("4", "Card payment"), x("5", "OTHER SHOP"))
        val fixed = all[0].copy(category = Cat.KHANA)
        val out = Recat.sameShop(all.map { if (it.id == "1") fixed else it }, all[0], fixed)
        assertEquals(listOf(Cat.KHANA, Cat.KHANA, Cat.OTHER, Cat.OTHER, Cat.OTHER), out.map { it.category })
        // A generic name never drags other entries along.
        val card = all[2].copy(category = Cat.KHANA)
        assertEquals(Cat.OTHER, Recat.sameShop(all, all[2], card)[3].category)
    }

    @Test
    fun pastedBankSmsLooksLikeTheShopsEarlierEntries() {
        val sms = "PoS Purchase\nBy:1234;mada-Pay\nAmount: SAR 5\nAt:ESTABLISH\n4/11/26 12:36"
        val parsed = (Parser.parse(sms).entries + Parser.parse(sms).unclear).single()
        val earlier = Entry("old", Type.KHARCHA, 3.0, Cat.KHANA, "ESTABLISHMENT NOORAN A", "", "Riyadh · card · Al Rajhi", 1L, 1L)
        val e = Recat.fromHistory(parsed, listOf(earlier))
        assertEquals("ESTABLISHMENT NOORAN A", e.item)
        assertEquals(Cat.KHANA, e.category)
        assertEquals("Riyadh · card · Al Rajhi", e.note)
        assertEquals(5.0, e.amount, 0.001)
        // A statement note ("City · card · Bank") is not a shop: fixing one entry never drags that city's others along.
        val a = Entry("a", Type.KHARCHA, 2.0, Cat.OTHER, "SHOP ONE", "", "Riyadh · card · Al Rajhi", 0L, 0L)
        val b = Entry("b", Type.KHARCHA, 2.0, Cat.OTHER, "SHOP TWO", "", "Riyadh · card · Al Rajhi", 0L, 0L)
        val fixed = a.copy(category = Cat.KHANA)
        assertEquals(Cat.OTHER, Recat.sameShop(listOf(fixed, b), a, fixed)[1].category)
    }
}
