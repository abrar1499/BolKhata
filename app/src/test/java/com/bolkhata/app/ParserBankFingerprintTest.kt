package com.bolkhata.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ParserBankFingerprintTest {
    private val fullAlert = """
        PoS Purchase
        By:9954;mada-mada Pay
        Amount: SAR 29
        At:AlJidar A
        29/10/26 12:41
    """.trimIndent()

    private val shortenedAlert = """
        Amount: SAR 29
        At:AlJidar A
        29/10/26 12:41
    """.trimIndent()

    @Test fun differentFragmentsOfTheSameBankAlertHaveTheSameFingerprint() {
        val first = Parser.bankFingerprint(fullAlert, 29.0)
        val second = Parser.bankFingerprint(shortenedAlert, 29.0)
        assertTrue(first.isNotBlank())
        assertEquals(first, second)

        val normalizedFull = Normalizer.normalize(fullAlert).text
        val normalizedShort = Normalizer.normalize(shortenedAlert).text
        assertEquals(
            Parser.bankFingerprint(normalizedFull, 29.0),
            Parser.bankFingerprint(normalizedShort, 29.0)
        )
    }

    @Test fun differentAmountOrTimestampDoesNotShareTheFingerprint() {
        val original = Parser.bankFingerprint(fullAlert, 29.0)
        val otherAmount = Parser.bankFingerprint(shortenedAlert.replace("SAR 29", "SAR 30"), 30.0)
        val otherTime = Parser.bankFingerprint(shortenedAlert.replace("12:41", "12:42"), 29.0)
        assertNotEquals(original, otherAmount)
        assertNotEquals(original, otherTime)
    }

    @Test fun deduplicatorSkipsOnlyMatchingStructuredBankAlerts() {
        val saved = Entry("saved", Type.KHARCHA, 29.0, Cat.OTHER, "Other", "", "AlJidar A", 1L, 1L,
            sourceFingerprint = "bank:exact")
        val sameAlert = saved.copy(id = "incoming")
        val manualEntry = saved.copy(id = "manual", sourceFingerprint = "")
        val result = BankTransactionDeduplicator.filterNew(listOf(sameAlert, manualEntry), listOf(saved))
        assertEquals(1, result.skipped)
        assertEquals(listOf("manual"), result.fresh.map { it.id })
    }

    @Test fun parsedBankEntryCarriesItsFingerprint() {
        val parsed = Parser.parse(fullAlert)
        assertEquals(1, parsed.entries.size)
        assertTrue(parsed.entries.single().sourceFingerprint.isNotBlank())
    }
}
