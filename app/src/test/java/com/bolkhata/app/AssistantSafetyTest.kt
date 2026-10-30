package com.bolkhata.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AssistantSafetyTest {
    @Test fun mutationRequiresTheExactLiveSnapshot() {
        val old = Entry("entry-1", Type.KHARCHA, 10.0, Cat.KHANA, "Tea", "", "Tea", 1L, 1L)
        val changed = old.copy(amount = 12.0)
        assertEquals(true, Accounts.snapshotsMatch(listOf(old), listOf(old)))
        assertEquals(false, Accounts.snapshotsMatch(listOf(changed), listOf(old)))
        assertEquals(false, Accounts.snapshotsMatch(emptyList(), listOf(old)))
    }

    @Test fun modelTargetsResolveOnlyByExactStableId() {
        val first = Entry("entry-alpha", Type.KHARCHA, 10.0, Cat.KHANA, "Tea", "", "Tea", 1L, 1L)
        val second = Entry("entry-beta", Type.KHARCHA, 20.0, Cat.TRANSPORT, "Fuel", "", "Fuel", 2L, 2L)
        val live = listOf(first, second)

        assertEquals("entry-alpha", Accounts.code(first))
        assertEquals("entry-beta", Accounts.byStableId(live, "entry-beta")?.id)
        assertNull("A model-supplied list position must not select a record", Accounts.byStableId(live, "1"))
        assertNull("Unknown IDs must not fall back to the first record", Accounts.byStableId(live, "missing"))
    }
}
