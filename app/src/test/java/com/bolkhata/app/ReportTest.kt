package com.bolkhata.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReportTest {
    @Test fun personKhataShowsWhoPaysWhomFirst() {
        val r = Report.parse("Hamid Akram all time: you gave 500 SAR, got back 200 SAR. Hamid Akram will pay you 300 SAR.", "SAR")!!
        assertEquals("Hamid Akram all time", r.title)
        assertEquals(Report.Stat("Hamid Akram will pay you", "300 SAR", 1), r.hero)
        assertEquals(listOf("You gave" to "500 SAR", "Got back" to "200 SAR"), r.stats.dropLast(1).map { it.label to it.amount })
        assertEquals(-1, r.stats[0].tone)
    }

    @Test fun listHasRowsAndTotal() {
        val r = Report.parse("28 Oct · Chai · Nargis Salim Rashid · 50 SAR\n27 Oct · Petrol · 1,200.50 SAR\n+3 more\nTotal: Spent 1,250.50 SAR (5 entries, all)", "SAR")!!
        assertEquals(Report.Line("28 Oct", "Chai", "Nargis Salim Rashid", "50 SAR"), r.rows[0])
        assertEquals(Report.Line("27 Oct", "Petrol", "", "1,200.50 SAR"), r.rows[1])
        assertEquals(Report.Stat("Spent", "1,250.50 SAR", -1), r.total.single())
        assertEquals(listOf("+3 more", "5 entries, all"), r.notes)
    }

    @Test fun monthSpendingWithTopCategories() {
        val r = Report.parse("Spent this month: 1,200 SAR. Most on Food 500, Transport 300", "SAR")!!
        assertEquals(Report.Stat("Spent this month", "1,200 SAR", -1), r.hero)
        assertEquals(listOf("Food" to "500 SAR", "Transport" to "300 SAR"), r.stats.drop(1).map { it.label to it.amount })
    }

    @Test fun secondLineKeepsItsOwnName() {
        val r = Report.parse("Spent 4,699.27 SAR (69 entries, Sep)\nOther money out: Lent 4,688.68 SAR (14 entries, Sep)", "SAR")!!
        assertEquals("", r.title)
        assertEquals(Report.Stat("Spent", "4,699.27 SAR", -1), r.hero)
        assertEquals(Report.Stat("Lent (other money out)", "4,688.68 SAR", -1), r.stats[1])
        assertEquals(listOf("69 entries, Sep", "other money out: 14 entries, Sep"), r.notes)
    }

    @Test fun plainSentencesAndQuestionsStayText() {
        assertNull(Report.parse("Nothing found (Chai · 28 Oct).", "SAR"))
        assertNull(Report.parse("Change chai from 50 SAR to 60 SAR?", "SAR"))
        assertNull(Report.parse("Saved 50 SAR.", "SAR"))
    }
}
