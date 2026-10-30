package com.bolkhata.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AssistantSafetyTest {
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
