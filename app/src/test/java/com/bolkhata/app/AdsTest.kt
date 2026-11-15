package com.bolkhata.app

import org.junit.Assert.assertEquals
import org.junit.Test

/** Taps on ads are counted per day, so the ads rest after a few and come back the next day. */
class AdsTest {
    @Test
    fun tapsCountOnlyForTheirOwnDay() {
        assertEquals(2, adTapsOn("20261115", "20261115", 2))
        assertEquals(0, adTapsOn("20261116", "20261115", 3))
        assertEquals(0, adTapsOn("20261115", "", 0))
    }

    @Test
    fun adsBetweenRowsTakeTurns() {
        assertEquals(listOf(true, false, true, false), (1..4).map { videoTurn(it) })
    }
}
