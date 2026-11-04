package com.bolkhata.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class UniqueIdsTest {
    private fun e(id: String, amount: Double) = Entry(id, Type.KHARCHA, amount, Cat.KHANA, "Tea", "", "", 1L, 1L)

    @Test fun aRepeatedIdGetsANewOne() {
        val fixed = uniqueIds(listOf(e("a", 1.0), e("a", 2.0), e("b", 3.0), e("a", 4.0)))
        assertEquals(listOf("a", "a-2", "b", "a-3"), fixed.map { it.id })
        assertEquals(listOf(1.0, 2.0, 3.0, 4.0), fixed.map { it.amount })
    }

    @Test fun uniqueListsStayAsTheyAre() {
        val list = listOf(e("a", 1.0), e("b", 2.0))
        assertSame(list, uniqueIds(list))
    }
}
