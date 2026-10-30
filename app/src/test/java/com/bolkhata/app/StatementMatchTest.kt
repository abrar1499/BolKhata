package com.bolkhata.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StatementMatchTest {
    private val minute = 1_700_000_040_000L

    private fun entry(id: String, note: String, person: String = "", item: String = "", date: Long = minute, amount: Double = 250.0) =
        Entry(id, Type.TRANSFER_OUT, amount, Cat.OTHER, item, person, note, date, date)

    @Test
    fun extraSpacesInTheNoteStillMatchTheSavedEntry() {
        val saved = listOf(entry("s1", "To Ahmad  Khan"))
        val found = listOf(entry("f1", "to ahmad khan"))
        assertEquals(mapOf("f1" to "s1"), BankTransactionDeduplicator.matchSaved(found, saved))
    }

    @Test
    fun samePersonAndItemMatchWhenTheNoteIsShort() {
        val saved = listOf(entry("s1", "", person = "Ali", item = "Rent"))
        val found = listOf(entry("f1", "", person = " ali ", item = "rent"))
        assertEquals(mapOf("f1" to "s1"), BankTransactionDeduplicator.matchSaved(found, saved))
    }

    @Test
    fun differentMinuteOrAmountNeverMatches() {
        val saved = listOf(entry("s1", "To Ahmad Khan"))
        val found = listOf(
            entry("f1", "To Ahmad Khan", date = minute + 60_000L),
            entry("f2", "To Ahmad Khan", amount = 251.0)
        )
        assertTrue(BankTransactionDeduplicator.matchSaved(found, saved).isEmpty())
    }

    @Test
    fun oneSavedEntryIsMatchedOnlyOnce() {
        val saved = listOf(entry("s1", "To Ahmad Khan"))
        val found = listOf(entry("f1", "To Ahmad Khan"), entry("f2", "To Ahmad Khan"))
        assertEquals(mapOf("f1" to "s1"), BankTransactionDeduplicator.matchSaved(found, saved))
    }
}
