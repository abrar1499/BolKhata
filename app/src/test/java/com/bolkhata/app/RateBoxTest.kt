package com.bolkhata.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** A rate typed or said in the chat bar while the rate box is open fills the box. */
class RateBoxTest {
    @Test fun aRateAndItsUnitAreRead() {
        assertEquals("900" to "1000", RateBox.parse("900 per 1000"))
        assertEquals("900" to "1000", RateBox.parse("900 hazar"))
        assertEquals("50" to "kg", RateBox.parse("50 kg"))
        assertEquals("12.5" to "", RateBox.parse("12.5 each"))
        assertEquals("1200" to null, RateBox.parse("1,200"))
    }

    @Test fun otherMessagesAreNotARate() {
        assertNull(RateBox.parse("hamid ko 500 diye"))
        assertNull(RateBox.parse("is mahine ka kharcha?"))
    }
}
