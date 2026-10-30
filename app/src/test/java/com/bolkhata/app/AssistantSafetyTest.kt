package com.bolkhata.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AssistantSafetyTest {
    @Test fun immediateRepeatIsBlockedOnlyAfterConfirmedMutation() {
        val previousAt = 1_000_000L
        assertEquals(true, Accounts.isRecentDuplicateMutationRequest(
            "coffee 5", previousAt, "Saved coffee", previousAt + 1_000, true, " Coffee 5 ", previousAt + 2_000
        ))
        assertEquals(false, Accounts.isRecentDuplicateMutationRequest(
            "coffee 5", previousAt, "What date?", previousAt + 1_000, false, "coffee 5", previousAt + 2_000
        ))
        assertEquals(true, Accounts.isRecentDuplicateMutationRequest(
            "change petrol to 60", previousAt, "Updated 1 entry", previousAt + 1_000, false,
            "change petrol to 60", previousAt + 2_000
        ))
        assertEquals(false, Accounts.isRecentDuplicateMutationRequest(
            "coffee 5", previousAt, "Saved coffee", previousAt + 1_000, true, "coffee 5", previousAt + 40_000
        ))
    }

    @Test fun romanUrduBulkMoveRequestIsRecognized() {
        assertEquals(true, Accounts.isExplicitBulkKhataMoveRequest(
            "es khata ki sari entry haji ashiq waly khata me move kr do"
        ))
        assertEquals(false, Accounts.isExplicitBulkKhataMoveRequest(
            "move the petrol expense to food category"
        ))
        assertEquals(false, Accounts.isExplicitBulkKhataMoveRequest(
            "show all entries in this khata"
        ))
    }

    @Test fun naturalLanguageDestinationKhataMatchesSavedName() {
        assertEquals(
            "Haji Ashiq",
            Accounts.khataNameMentionedIn(
                "es khata ki sari entry haji ashiq waly khata me move kr do",
                "Affan Ahmed Ilyas Ahmed",
                listOf("Affan Ahmed Ilyas Ahmed", "Haji Ashiq", "Haji")
            )
        )
        assertEquals(
            "Haji Ashiq Ahmed",
            Accounts.khataNameMentionedIn(
                "move all entries to haji ashiq ahmed khata",
                "Affan",
                listOf("Haji Ashiq", "Haji Ashiq Ahmed")
            )
        )
        assertEquals(null, Accounts.khataNameMentionedIn(
            "move all entries to this khata",
            "Affan",
            listOf("Affan", "Haji Ashiq")
        ))
    }

    @Test fun ambiguousMultiMatchRequiresClarificationUnlessAllWasExplicit() {
        assertEquals(true, Accounts.requiresTargetClarification(3, explicitlyAll = false))
        assertEquals(false, Accounts.requiresTargetClarification(3, explicitlyAll = true))
        assertEquals(true, Accounts.requiresTargetClarification(3, explicitlyAll = true, allowAll = false))
        assertEquals(false, Accounts.requiresTargetClarification(1, explicitlyAll = false))
    }

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
