package com.bolkhata.app

import org.junit.Assert.assertEquals
import org.junit.Test

/** One item keeps one spelling. */
class GoodsTest {
    private val known = listOf("Awal eint", "Cement", "Doem eint", "Koila", "Chai")

    @Test fun closeSpellingsTakeTheSavedName() {
        assertEquals("Awal eint", Goods.canonical("awal int", known))
        assertEquals("Awal eint", Goods.canonical("aval eint", known))
        assertEquals("Cement", Goods.canonical("cemnt", known))
        assertEquals("Doem eint", Goods.canonical("doem int", known))
    }

    @Test fun differentGoodsStayDifferent() {
        assertEquals("steel", Goods.canonical("steel", known))
        assertEquals("cake", Goods.canonical("cake", known))
        // A two-letter sound key alone is not enough: "bori" is not "Bharai".
        assertEquals("Bori", Goods.canonical("Bori", listOf("Bharai")))
        assertEquals("Bharai", Goods.canonical("bhrai", listOf("Bharai")))
    }

    private fun sold(t: Type, person: String, rate: Double, day: Long) =
        Entry("e$day", t, 100.0, Cat.STOCK, "Awal eint", person, "", day, day, qty = 1.0, rate = rate, unit = "1000")

    @Test fun lastRateIsThePersonsOwnFirstThenAnyones() {
        val all = listOf(sold(Type.SALE, "Hamid Akram", 17000.0, 1), sold(Type.SALE, "Nargis Salim Rashid", 17500.0, 2),
            sold(Type.PURCHASE, "", 9000.0, 3))
        assertEquals(17000.0, Goods.lastRate("awal eint", "Hamid Akram", false, all)!!.rate, 0.0)
        assertEquals(17500.0, Goods.lastRate("Awal eint", "Someone new", false, all)!!.rate, 0.0)
        assertEquals(9000.0, Goods.lastRate("Awal eint", "", true, all)!!.rate, 0.0)
        assertEquals("1000", Goods.lastRate("Awal eint", "", true, all)!!.unit)
    }
}
