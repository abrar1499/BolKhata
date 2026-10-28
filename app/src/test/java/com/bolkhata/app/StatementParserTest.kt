package com.bolkhata.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Made-up statements in the shapes banks print, read without AI. */
class StatementParserTest {

    @Test
    fun readsTypeLineAboveDateAndFollowsBalance() {
        val text = """
            Opening Balance 1,000.00 SAR
            Closing Balance 435.42 SAR
            Number Of Deposits 1
            Number Of Withdrawals 3
            Total Deposits 200.00 SAR
            Total Withdrawals 764.58 SAR
            Date Transaction Details Debit Credit Balance
            POS purchase mada pay (Domestic)
            2026/08/18 Time:21:48:52, Notes:(1234567890123456-123456789012) 14.00 SAR 0.00 SAR 986.00 SAR
            TEST CAFE, RIYADH, SA
            Outward IPS Credit Transfer
            2026/08/19 Time:10:29:42, Notes:9118082600301463/JOHN TEST PERSON
            750.00 SAR 0.00 SAR 236.00 SAR
            Outward IPS Credit Transaction Charges
            2026/08/19 Time:10:29:42, Notes:9118082600301463/JOHN TEST PERSON
            0.58 SAR 0.00 SAR 235.42 SAR
            Internal Transfer
            2026/08/20 Time:20:38:13, Notes:W-/FRACCT/07703608010906118FRMUHAMMADTEST
            0.00 SAR 200.00 SAR 435.42 SAR
        """.trimIndent()
        val r = StatementParser.parse(text)
        assertTrue(r.why, r.ok)
        assertEquals(4, r.rows.size)
        assertEquals("To John Test Person", r.rows[1].note)
        assertEquals("Transfer fee", r.rows[2].item)
        assertEquals("Transfer in", r.rows[3].note)
        assertEquals("transfer_in", r.rows[3].kind)
        assertTrue(r.rows[3].`in`)
        assertEquals(0.58, r.rows[2].amount, 0.001)
    }

    @Test
    fun readsNewestFirstWithCrDr() {
        val text = """
            Balance brought forward 300.00
            05 Oct 2026 BANK CHARGES VAT 5.25 DR 1,249.75 CR
            03 Oct 2026 INWARD REMITTANCE FROM SARA ALI 1,000.00 CR 1,255.00 CR
            01 Oct 2026 CARD PURCHASE CAREEM DUBAI 45.00 DR 255.00 CR
            Closing balance 1,249.75
        """.trimIndent()
        val r = StatementParser.parse(text)
        assertTrue(r.why, r.ok)
        assertEquals(3, r.rows.size)
        assertEquals("2026-10-01", r.rows[0].date)
        assertEquals("TRANSPORT", r.rows[0].category)
        assertTrue(r.rows[1].`in`)
    }

    @Test
    fun classifiesTransfersAtmAndCompanyCreditsSeparately() {
        val text = """
            Opening Balance 1,000.00 SAR
            Closing Balance 1,150.00 SAR
            Number Of Deposits 2
            Number Of Withdrawals 2
            Total Deposits 400.00 SAR
            Total Withdrawals 250.00 SAR
            Date Transaction Details Debit Credit Balance
            Outward IPS Credit Transfer
            2026/08/18 Time:10:00:00, Notes:9118082600301463/JOHN TEST PERSON
            200.00 SAR 0.00 SAR 800.00 SAR
            Internal Transfer
            2026/08/19 Time:10:00:00, Notes:W-/FRACCT/07703608010906118FRMUHAMMADTEST
            0.00 SAR 100.00 SAR 900.00 SAR
            ATM cash withdrawal
            2026/08/20 Time:10:00:00
            50.00 SAR 0.00 SAR 850.00 SAR
            Inward IPS Credit Transfer ACME TRADING LLC
            2026/08/21 Time:10:00:00, Notes:ACME TRADING LLC
            0.00 SAR 300.00 SAR 1,150.00 SAR
        """.trimIndent()
        val r = StatementParser.parse(text)
        assertTrue(r.why, r.ok)
        assertEquals(4, r.rows.size)
        assertEquals("transfer_out", r.rows[0].kind)
        assertEquals("John Test Person", r.rows[0].person)
        assertEquals("transfer_in", r.rows[1].kind)
        assertEquals("", r.rows[1].person)
        assertEquals("transfer_out", r.rows[2].kind)
        assertEquals("ATM withdrawal", r.rows[2].item)
        assertEquals("income", r.rows[3].kind)
        assertEquals("Company credit", r.rows[3].item)
    }

    @Test
    fun refusesWhenBalanceDoesNotAddUp() {
        val text = """
            Opening Balance 100.00
            01/10/2026 SHOP ONE 10.00 0.00 90.00
            02/10/2026 SHOP TWO 10.00 0.00 50.00
        """.trimIndent()
        assertTrue(!StatementParser.parse(text).ok)
    }
}
