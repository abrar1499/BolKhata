package com.bolkhata.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Regression tests: restoring a move must not overwrite newer account changes. */
class MoveHistoryTest {
    private fun entry(id: String, person: String, type: Type = Type.DIYA) =
        Entry(id, type, 100.0, Cat.UDHAAR, "Loans", person, "Original note", 10L, 10L)

    private fun change(before: Entry, after: Entry) = MoveChange(
        before.id, before.person, before.type, before.category, before.item, before.paid,
        after.person, after.type, after.category, after.item, after.paid
    )

    @Test
    fun restoresSingleMoveWithoutOverwritingUnrelatedDetails() {
        val original = entry("1", "Ali")
        val moved = original.copy(person = "Bilal")
        val batch = MoveBatch("move-1", 20L, listOf(change(original, moved)))
        val updated = moved.copy(note = "Corrected later", amount = 125.0)
        val restored = restoreMoveBatch(listOf(updated), batch)!!.single()
        assertEquals("Ali", restored.person)
        assertEquals("Corrected later", restored.note)
        assertEquals(125.0, restored.amount, 0.0)
    }

    @Test
    fun rejectsRestoreAfterAnotherMove() {
        val original = entry("1", "Ali")
        val moved = original.copy(person = "Bilal")
        val batch = MoveBatch("move-1", 20L, listOf(change(original, moved)))
        assertNull(restoreMoveBatch(listOf(moved.copy(person = "Zaid")), batch))
    }

    @Test
    fun rejectsMissingEntriesAndDoesNotPartiallyRestoreBatch() {
        val first = entry("1", "Ali")
        val second = entry("2", "Ali", Type.LIYA)
        val firstMoved = first.copy(person = "Bilal")
        val secondMoved = second.copy(person = "Bilal")
        val batch = MoveBatch("move-2", 20L, listOf(change(first, firstMoved), change(second, secondMoved)))
        assertNull(restoreMoveBatch(listOf(firstMoved), batch))
        assertNull(restoreMoveBatch(listOf(firstMoved, secondMoved.copy(type = Type.DIYA)), batch))
        val restored = restoreMoveBatch(listOf(firstMoved, secondMoved), batch)!!
        assertTrue(restored.all { it.person == "Ali" })
        assertEquals(Type.LIYA, restored[1].type)
    }
}
