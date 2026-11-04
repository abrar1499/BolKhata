package com.bolkhata.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WhisperNoiseTest {
    @Test
    fun dropsMadeUpWordsForSilence() {
        assertTrue(Groq.isNoise("."))
        assertTrue(Groq.isNoise("Υπότιτλοι AUTHORWAVE"))
        assertTrue(Groq.isNoise("Thanks for watching!"))
        assertTrue(Groq.isNoise("National National National National National"))
        assertTrue(Groq.isNoise("Hello friends, welcome to my channel. Today I will show you"))
        assertTrue(Groq.isNoise("موسیقی"))
    }

    @Test
    fun keepsRealSpeech() {
        assertFalse(Groq.isNoise("Hamid ka balance batao"))
        assertFalse(Groq.isNoise("چائے 5"))
        assertFalse(Groq.isNoise("Can you speak in Urdu?"))
        assertFalse(Groq.isNoise("chai chai 10"))
    }
}
