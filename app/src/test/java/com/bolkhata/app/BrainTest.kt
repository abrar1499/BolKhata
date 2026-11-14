package com.bolkhata.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Only lessons with open places for people and numbers, and no saved or common name, may go to the shared brain. */
class BrainTest {
    private val names = listOf("Hamid Akram", "Nargis Salim Rashid")

    @Test
    fun aSaveWithOpenPlacesIsShared() =
        assertTrue(Brain.safe("@ ko # diye", """[{"type":"lent","amount":"#0","person":"@0"}]""", names))

    @Test
    fun aLookUpAndAPlainReplyAreShared() {
        assertTrue(Brain.safe("@ ka mahana hisab", """{"calls":[{"name":"show","input":{"persons":["@0"],"view":"by_month"}}],"reply":""}""", names))
        assertTrue(Brain.safe("kya ho raha hai", """{"calls":[],"reply":"Sab theek, aap batayen kya chahiye?"}""", names))
    }

    @Test
    fun aNameWrittenOutStaysOnThePhone() {
        assertFalse(Brain.safe("farooq ko # diye", """[{"type":"lent","amount":"#0","person":"Farooq"}]""", names))
        assertFalse(Brain.safe("hamid wala hisab", """{"calls":[],"reply":"Hamid is fine."}""", names))
        assertFalse(Brain.safe("kaise ho", """{"calls":[],"reply":"Ali is here."}""", names))
    }

    @Test
    fun accountsTrashAndFiguresStayOnThePhone() {
        assertFalse(Brain.safe("@ ko staff mein daal do", """{"calls":[{"name":"put_in_account","input":{"account":"Staff","people":["@0"]}}],"reply":""}""", names))
        assertFalse(Brain.safe("kitna bacha", """{"calls":[],"reply":"Left 300."}""", names))
    }
}
