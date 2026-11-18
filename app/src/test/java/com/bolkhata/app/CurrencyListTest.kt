package com.bolkhata.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The currency list holds only the money countries pay in today. */
class CurrencyListTest {
    @Test fun onlyRealCurrenciesAreOffered() {
        listOf("PKR", "SAR", "USD", "EUR", "GBP", "JPY", "XOF", "XAF").forEach { assertTrue(it, it in CURRENCIES) }
        listOf("XPD", "XPT", "XAU", "XTS", "XXX", "XUA", "XSU", "XRE", "YDD", "ZAL", "USS", "USN").forEach { assertFalse(it, it in CURRENCIES) }
        assertTrue(CURRENCIES.size == CURRENCIES.distinct().size)
    }
}
