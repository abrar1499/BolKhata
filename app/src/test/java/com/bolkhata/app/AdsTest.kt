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
    fun cashbookAdsComeAfter5ThenBetweenDaysAt12AndEvery12() {
        // The first right after the 5th entry, even inside a day; the others wait for a day line.
        assertEquals(mapOf(5 to 1), cashAds(book(1, 30)))
        // Only 5 entries: nothing under them, no ad.
        assertEquals(emptyMap<Int, Int>(), cashAds(book(1, 5)))
        // 12 a day: the small picture after the 5th, the video before the 2nd day, the next before the 3rd.
        assertEquals(mapOf(5 to 1, 12 to 2, 24 to 3), cashAds(book(3, 12)))
        // 5 a day: the picture after 5 (row 5), the video once 12 are above (row 15), then 12 or more later (row 30).
        assertEquals(mapOf(5 to 1, 15 to 2, 30 to 3), cashAds(book(7, 5)))
    }
}
