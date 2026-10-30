package com.bolkhata.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReceiptParserTest {
    @Test fun readsOnlyTheExplicitPrintedGrandTotal() {
        val text = """
            CORNER CAFE
            Latte 12.00
            Sandwich 18.00
            Subtotal 30.00
            Tax 1.50
            GRAND TOTAL SAR 31.50
            30/10/2026
        """.trimIndent()
        val result = ReceiptParser.parse(text)
        assertEquals(1, result.size)
        assertEquals(31.50, result.single().amount, 0.001)
        assertEquals(Type.KHARCHA, result.single().type)
        assertTrue(result.single().note.contains("CORNER CAFE"))
    }

    @Test fun refusesToInventATotalWhenTheReceiptDoesNotPrintOne() {
        val result = ReceiptParser.parse("CORNER CAFE\nLatte 12.00\nSandwich 18.00")
        assertTrue(result.isEmpty())
    }
}
