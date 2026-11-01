package com.bolkhata.app

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
}
