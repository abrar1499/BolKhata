package com.bolkhata.app

import org.junit.Assert.assertEquals
import org.junit.Test

/** Only languages the app does not read itself go through a translation. */
class LingoTest {
    @Test
    fun theAppsOwnLanguagesAndAutoAreNotTranslated() {
        listOf("", "en-US", "hi-IN", "ur-PK", "hi-Latn").forEach { assertEquals("", Lingo.code(it)) }
    }

    @Test
    fun anotherLanguageGivesItsCode() {
        assertEquals("nl", Lingo.code("nl"))
        assertEquals("ar", Lingo.code("ar-SA"))
    }

    @Test
    fun romanLettersAreNeverALanguageWithAScriptOfItsOwn() {
        assertEquals(false, Lingo.fits("ar", "ashraf ko cheni d"))
        assertEquals(true, Lingo.fits("ar", "دفعت 40"))
        assertEquals(true, Lingo.fits("nl", "ik heb 20 euro betaald"))
    }
}
