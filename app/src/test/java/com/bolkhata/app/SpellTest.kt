package com.bolkhata.app

import org.junit.Assert.assertEquals
import org.junit.Test

/** A message the app did not understand is read once more with its misspelt words put right: only words that are clearly one known word. */
class SpellTest {
    private val names = listOf("Hamid Akram", "Nargis Salim Rashid", "Zubair Latif")

    @Test
    fun aWordOneLetterOffIsPutRight() {
        assertEquals("zubair ne 8000 bharai ki", Spell.fix("zubair ne 8000 bhrai ki", names))
        assertEquals("expense this month", Spell.fix("expanse this month", names))
        assertEquals("report dikhao", Spell.fix("reprt dikhaoo", names))
    }

    @Test
    fun knownWordsNamesNumbersAndShortWordsStay() {
        listOf("hamid ka hisab batao", "nargis ko 3000 pakki eent di", "tea 5", "zubair latif 500").forEach { assertEquals(it, Spell.fix(it, names)) }
    }

    /** "kase" is as near to "kaise" (how) as to "kise" (whom): it is not guessed. */
    @Test
    fun aWordAsNearToTwoMeaningsIsLeft() = assertEquals("kase ho", Spell.fix("kase ho", names))
}
