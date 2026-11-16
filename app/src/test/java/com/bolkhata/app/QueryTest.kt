package com.bolkhata.app

import org.junit.Assert.assertEquals
import org.junit.Test

/** What a listed row says about goods: how much of it and at what rate. */
class QueryTest {
    private val now = System.currentTimeMillis()

    @Test
    fun aRowOfGoodsShowsItsCountAndRate() {
        val sugar = Entry("g1", Type.SALE, 400.0, Cat.SALES, "Cheeni", "Hamid Akram", "", now, now, qty = 5.0, unit = "kg", rate = 80.0)
        val bricks = Entry("g2", Type.PURCHASE, 9_000.0, Cat.PRODUCTION, "Kachi eent", "Zubair Latif", "", now, now, qty = 10_000.0, unit = "1000", rate = 900.0)
        val loan = Entry("l1", Type.DIYA, 500.0, Cat.UDHAAR, "Loans", "Hamid Akram", "", now, now)
        assertEquals(" · 5 kg × 80", Query.goods(sugar))
        assertEquals(" · 10,000 × 900 per 1000", Query.goods(bricks))
        assertEquals("", Query.goods(loan))
    }
}
