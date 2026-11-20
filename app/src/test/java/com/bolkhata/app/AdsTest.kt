package com.bolkhata.app

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar

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

    /** Entries newest first: [perDay] entries on each of [days] days. */
    private fun book(days: Int, perDay: Int): List<Long> = (0 until days).flatMap { d ->
        (0 until perDay).map { j -> Calendar.getInstance().apply { set(2026, 10, 20, 20, 0, 0); add(Calendar.DAY_OF_MONTH, -d); add(Calendar.MINUTE, -j) }.timeInMillis }
    }

    @Test
    fun cashbookAdsComeBetweenDaysAfter5ThenEvery12Entries() {
        // One busy day: no place between two days, no ad.
        assertEquals(emptyMap<Int, Int>(), cashAds(book(1, 30)))
        // Fewer than 5 above the first day line: no ad yet.
        assertEquals(emptyMap<Int, Int>(), cashAds(book(2, 3)))
        // 12 a day: an ad before the 2nd and the 3rd day, a video then a picture.
        assertEquals(mapOf(12 to 1, 24 to 2), cashAds(book(3, 12)))
        // 5 a day: the first after day 1 (row 5), the next at the first day line 12 or more later (row 20).
        assertEquals(mapOf(5 to 1, 20 to 2), cashAds(book(7, 5)))
    }
}
