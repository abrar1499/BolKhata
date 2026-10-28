package com.bolkhata.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The Nisar example: accounts move between To receive and To pay by their whole balance. */
class PersonAccountsTest {
    private val day = 86_400_000L
    private fun e(id: String, type: Type, amount: Double, d: Int, person: String = "Nisar") =
        Entry(id, type, amount, Cat.UDHAAR, "Loans", person, "", 1_700_000_000_000L + d * day, 1_700_000_000_000L + d * day)

    @Test
    fun nisarMovesBetweenSidesAndStaysWhenSettled() {
        val gave = e("1", Type.DIYA, 1000.0, 0)
        var p = personAccounts(listOf(gave)).single()
        assertTrue(p.toReceive); assertEquals(1000.0, p.balance, 0.001)

        val got = e("2", Type.WAPIS_MILA, 1500.0, 1)
        p = personAccounts(listOf(gave, got)).single()
        assertFalse(p.toReceive); assertEquals(-500.0, p.balance, 0.001)

        val paid = e("3", Type.WAPIS_DIYA, 500.0, 2)
        p = personAccounts(listOf(gave, got, paid)).single()
        assertFalse(p.toReceive); assertEquals(0.0, p.balance, 0.001)   // settled, stays in To pay
    }

    @Test
    fun outOfOrderEntriesUseDates() {
        val list = listOf(e("3", Type.WAPIS_DIYA, 500.0, 2), e("1", Type.DIYA, 1000.0, 0), e("2", Type.WAPIS_MILA, 1500.0, 1))
        val p = personAccounts(list).single()
        assertFalse(p.toReceive); assertEquals(0.0, p.balance, 0.001)
    }

    @Test
    fun personTransfersDoNotCreateDebt() {
        val out = Entry("t1", Type.TRANSFER_OUT, 500.0, Cat.OTHER, "Transfer", "Nisar", "To Nisar", 10L, 10L)
        val inn = Entry("t2", Type.TRANSFER_IN, 100.0, Cat.OTHER, "Transfer", "Nisar", "From Nisar", 20L, 20L)
        assertEquals(0.0, balances(listOf(out, inn))["Nisar"] ?: 0.0, 0.001)
        val account = personAccounts(listOf(out, inn)).single()
        assertEquals(0.0, account.balance, 0.001)
        assertEquals(500.0, account.history.first().first.amount, 0.001)
        assertEquals(100.0, account.history.last().first.amount, 0.001)
    }

    @Test
    fun transferHistoryShowsDirectionWithoutChangingDebtBalance() {
        val out = e("out", Type.TRANSFER_OUT, 50.0, 0)
        val inn = e("in", Type.TRANSFER_IN, 100.0, 1)
        assertTrue(personHistoryIsOutflow(out))
        assertFalse(personHistoryIsOutflow(inn))
        val account = personAccounts(listOf(out, inn)).single()
        assertEquals(0.0, account.balance, 0.001)
    }

    @Test
    fun currentKhataContextAddsPersonOnlyToPersonTransactions() {
        val gave = Entry("c1", Type.DIYA, 50.0, Cat.UDHAAR, "Loans", "", "", 1L, 1L)
        val petrol = Entry("c2", Type.KHARCHA, 20.0, Cat.TRANSPORT, "Petrol", "", "", 2L, 2L)
        val explicit = Entry("c3", Type.TRANSFER_OUT, 10.0, Cat.OTHER, "Transfer", "Bilal", "", 3L, 3L)
        val contextual = applyKhataContext(listOf(gave, petrol, explicit), "Aamir")
        assertEquals("Aamir", contextual[0].person)
        assertEquals("", contextual[1].person)
        assertEquals("Bilal", contextual[2].person)
    }

    @Test
    fun convertsPersonTransfersUsingCurrentKhataBalance() {
        val sent = Entry("sent", Type.TRANSFER_OUT, 100.0, Cat.OTHER, "Transfer", "Aamir", "To Aamir", 1L, 1L)
        val received = Entry("received", Type.TRANSFER_IN, 50.0, Cat.OTHER, "Transfer", "Aamir", "From Aamir", 2L, 2L)
        val changed = convertPersonTransfersToLedgerEntries(listOf(sent, received), "Aamir")
        assertEquals(2, changed.size)
        assertEquals(Type.DIYA, changed[0].second.type)
        assertEquals(Type.WAPIS_MILA, changed[1].second.type)
        assertEquals(Cat.UDHAAR, changed[0].second.category)
        assertEquals(50.0, personAccounts(changed.map { it.second }).single().balance, 0.001)
    }

    @Test
    fun transferOutWhileIAlreadyOwePersonBecomesPaidBack() {
        val borrowed = e("borrowed", Type.LIYA, 200.0, 0)
        val sent = Entry("sent", Type.TRANSFER_OUT, 50.0, Cat.OTHER, "Transfer", "Nisar", "To Nisar", 1_700_000_000_000L + day, 1_700_000_000_000L + day)
        val changed = convertPersonTransfersToLedgerEntries(listOf(borrowed, sent), "Nisar")
        assertEquals(Type.WAPIS_DIYA, changed.single().second.type)
        assertEquals(-150.0, personAccounts(listOf(borrowed, changed.single().second)).single().balance, 0.001)
    }

    @Test
    fun movingAnEntryKeepsItsDetails() {
        val fuel = Entry("9", Type.KHARCHA, 100.0, Cat.OTHER, "Sasco", "", "Sasco, Riyadh", 5L, 5L)
        val moved = movedTo(fuel, listOf(fuel), Cat.TRANSPORT, false, null)
        assertEquals(Cat.TRANSPORT, moved.category); assertEquals(100.0, moved.amount, 0.0)
        assertEquals("Sasco", moved.item); assertEquals(5L, moved.date); assertEquals("Sasco, Riyadh", moved.note)

        // A transfer out moved to Nisar, who owes nothing yet: a loan given.
        val out = Entry("8", Type.KHARCHA, 1000.0, Cat.OTHER, "Transfer", "", "To Nisar", 6L, 6L)
        assertEquals(Type.DIYA, movedTo(out, listOf(out), null, false, "Nisar").type)
        // Money in from Nisar, who owes me: repayment.
        val gave = e("1", Type.DIYA, 1000.0, 0)
        val inn = Entry("7", Type.AAMDANI, 1500.0, Cat.TANKHWAH, "Transfer", "", "From Nisar", 7L, 7L)
        assertEquals(Type.WAPIS_MILA, movedTo(inn, listOf(gave, inn), null, true, "Nisar").type)
    }
}
