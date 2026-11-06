package com.bolkhata.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** An entry that names a book goes to that book. */
class BooksTest {
    private val books = listOf("" to "My business", "b1" to "Bhatta", "b2" to "Ghar", "b3" to "Personal")

    @Test fun theNamedBookGetsTheEntry() {
        assertEquals("b2" to "bijli 3000", Books.route("ghar ka bijli 3000", books))
        assertEquals("b1" to "koila 50000", Books.route("bhatte ka koila 50000", books))
        assertEquals("b1" to "koila 50000", Books.route("koila 50000 bhatty mein", books))
        assertEquals("b3" to "shoes 200", Books.route("for personal shoes 200", books))
    }

    @Test fun noBookNamedNoRoute() {
        assertNull(Books.route("ghar bheje 5000", books))
        assertNull(Books.route("petrol 50", books))
        assertNull(Books.route("ghar ka khata kholo", books))
    }
}
