package com.bolkhata.app

import org.junit.Assert.assertEquals
import org.junit.Test

/** Every message is read with its misspelt words put right first: only words that are clearly one known word. */
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

    @Test
    fun slipsOnThePhoneArePutRight() {
        assertEquals("zubair ko 500 wapis kiye", Spell.fix("zubair ko 500 wapiis kiye", names))
        assertEquals("salary 900 aayi", Spell.fix("salaary 900 aayi", names))
        assertEquals("borrowed 2000 from asif", Spell.fix("borrwoed 2000 frm asif", names))
        assertEquals("rickshaw 200", Spell.fix("rikcshaw 200", names))
        // Next to a number, the number word ("lkh" is lakh, not likh).
        assertEquals("1 lakh diye", Spell.fix("1 lkh diye", names))
    }

    /** New people's names, things that are words too, and a misheard name where a name stands are left for the readers. */
    @Test
    fun namesAndThingsStay() {
        listOf("bilal ko 500 diye", "tariq se 300 mile", "ande 500", "dahi 200", "motion ko 2000 udhaar diye").forEach { assertEquals(it, Spell.fix(it, names)) }
    }

    /** "kase" is as near to "kaise" (how) as to "kise" (whom): it is not guessed. */
    @Test
    fun aWordAsNearToTwoMeaningsIsLeft() = assertEquals("kase ho", Spell.fix("kase ho", names))
}
