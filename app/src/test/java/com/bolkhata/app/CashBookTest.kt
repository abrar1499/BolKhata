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
}
