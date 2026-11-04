package com.bolkhata.app

import org.junit.Assert.assertEquals
import org.junit.Test

class TransfersToLoansTest {
    @Test fun moneySentAndReceivedBecomesLenaDena() {
        val sent = Entry("t1", Type.TRANSFER_OUT, 500.0, Cat.OTHER, "Transfer", "Hamid Akram", "To Hamid Akram · D360", 1L, 1L)
        val back = Entry("t2", Type.TRANSFER_IN, 200.0, Cat.OTHER, "Transfer", "Hamid Akram", "From Hamid Akram · D360", 2L, 2L)
        val own = Entry("t3", Type.TRANSFER_OUT, 100.0, Cat.OTHER, "To D360", "", "Own account", 3L, 3L)
        val out = transfersToLoans(listOf(sent, back, own)).associateBy { it.id }
        assertEquals(Type.DIYA, out.getValue("t1").type)
        assertEquals(Type.WAPIS_MILA, out.getValue("t2").type)
        assertEquals(Type.TRANSFER_OUT, out.getValue("t3").type)
        assertEquals(300.0, personAccounts(out.values.toList()).single().balance, 0.001)
    }
}
