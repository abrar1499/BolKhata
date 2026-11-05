package com.bolkhata.app

import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GroqEntriesTest {
    private val now = 1_790_000_000_000L

    @Test fun expensesAndALoanBecomeEntries() {
        val list = JSONArray("""[
            {"type":"expense","amount":50,"category":"Food","item":"chai","person":"","note":"","date":""},
            {"type":"lent","amount":500,"category":"","item":"","person":"Hamid Akram","note":"","date":""}
        ]""")
        val e = Groq.entriesFrom(list, "chai 50 aur Hamid Akram ko 500 diye", now)!!
        assertEquals(2, e.size)
        assertEquals(Type.KHARCHA, e[0].type)
        assertEquals(Cat.KHANA, e[0].category)
        assertEquals("Chai", e[0].item)
        assertEquals(Type.DIYA, e[1].type)
        assertEquals("Hamid Akram", e[1].person)
        assertEquals(500.0, e[1].amount, 0.0)
    }

    @Test fun anAmountThatWasNotSaidIsRefused() {
        val list = JSONArray("""[{"type":"expense","amount":60,"category":"Transport","item":"Petrol"}]""")
        assertNull(Groq.entriesFrom(list, "petrol 50", now))
    }

    @Test fun aReplyWithAMadeUpAmountIsNotShown() {
        assertEquals(false, Groq.replyIsSafe("Kal ka total expense 93.87 SAR tha.", "kal kitna huwa tha"))
        assertEquals(true, Groq.replyIsSafe("Wa alaikum assalam! Batayein?", "salam"))
    }

    @Test fun aQueryBecomesAFilter() {
        val (what, f) = Groq.queryFrom(org.json.JSONObject(
            """{"mode":"report","types":["expense"],"categories":["Food","Nonsense"],"from":"2026-11-01","to":"2026-11-30"}"""
        ))!!
        assertEquals("report", what)
        assertEquals(setOf(Type.KHARCHA), f.types)
        assertEquals(setOf(Cat.KHANA), f.cats)
        assertEquals(true, f.to!! > f.from!!)
        assertNull(Groq.queryFrom(org.json.JSONObject("""{"mode":"delete"}""")))
    }

    @Test fun aReportShowsSpendingByCategory() {
        val e = listOf(
            Entry("a", Type.KHARCHA, 70.0, Cat.KHANA, "Chai", "", "", now, now),
            Entry("b", Type.KHARCHA, 30.0, Cat.TRANSPORT, "Petrol", "", "", now, now)
        )
        val text = Query.answer(e, "report", Query.Filter(), "SAR")
        assertEquals(true, text.startsWith("Spent: 100 SAR"))
        assertEquals(true, text.indexOf("Food 70 SAR") < text.indexOf("Transport 30 SAR"))
    }

    @Test fun aLoanWithoutAPersonIsRefused() {
        val list = JSONArray("""[{"type":"lent","amount":200,"person":""}]""")
        assertNull(Groq.entriesFrom(list, "200 udhaar diye", now))
    }
}
