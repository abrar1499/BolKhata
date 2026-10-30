package com.bolkhata.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeMemoryTest {
    @Test fun savesOnlyExplicitFactsAndKeepsThemOnThePhone() {
        val result = NativeMemory.handle("remember that Shahbaz is my brother", emptyList())
        assertNotNull(result)
        assertEquals(listOf("Shahbaz is my brother"), result?.facts)
        assertTrue(result?.reply?.contains("Saved on this phone") == true)
    }

    @Test fun listsAndForgetsAnExactMatchingFact() {
        val saved = listOf("Shahbaz is my brother", "Rent is due on the first")
        val listed = NativeMemory.handle("what do you remember?", saved)
        assertTrue(listed?.reply?.contains("Shahbaz is my brother") == true)
        val forgotten = NativeMemory.handle("forget that Shahbaz is my brother", saved)
        assertEquals(listOf("Rent is due on the first"), forgotten?.facts)
    }

    @Test fun ordinaryChatDoesNotSilentlyCreateMemory() {
        assertNull(NativeMemory.handle("Shahbaz is my brother", emptyList()))
    }
}
