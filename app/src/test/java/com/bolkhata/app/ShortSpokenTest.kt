package com.bolkhata.app

import org.junit.Assert.assertEquals
import org.junit.Test

class ShortSpokenTest {
    @Test
    fun keepsOnlyTheFirstSentence() {
        assertEquals("Hamid Akram owes you 940.66 SAR.", shortSpoken("Hamid Akram owes you 940.66 SAR. Last paid on 3 Oct."))
    }

    @Test
    fun keepsOnlyTheFirstLine() {
        assertEquals("1 entry saved", shortSpoken("\n1 entry saved\nGroceries 10 SAR"))
    }

    @Test
    fun cutsALongSentenceAtAWord() {
        val long = "word ".repeat(60).trim()
        val spoken = shortSpoken(long)
        assertEquals(true, spoken.length <= 161 && spoken.endsWith("word…"))
    }
}
