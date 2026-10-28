package com.bolkhata.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test

class RecatTest {
    private fun e(item: String, note: String, a: Double) = Entry("i$item$a", Type.KHARCHA, a, Cat.OTHER, item, "", note, 0, 0)

    @Test fun shopsAreSortedAndTransfersLeft() {
        val g = Recat.plan(listOf(e("PANDA RETAIL CO 123", "POS purchase", 40.0), e("Transfer", "To Muhammad", 300.0),
            e("ALDREES PETROLEUM", "", 70.0), e("Al Nahdi Medical", "", 15.0), e("Ahlam Restaurant", "", 25.5)))
        assertEquals(4, g.size)
        assertEquals(Cat.KHANA, g.first { it.name.startsWith("PANDA") }.guess)
        assertEquals(Cat.TRANSPORT, g.first { it.name.startsWith("ALDREES") }.guess)
        assertEquals(Cat.SEHAT, g.first { it.name.contains("Nahdi") }.guess)
        assertEquals(Cat.KHANA, g.first { it.name.contains("Restaurant") }.guess)
    }

    @Test fun command() {
        assertTrue(Recat.isCommand("jo expense wali entry other me parri hain wo expense me Dalo"))
        assertTrue(Recat.isCommand("other wali entries theek karo"))
        assertFalse(Recat.isCommand("other 50"))
        assertFalse(Recat.isCommand("petrol 70"))
    }
}
