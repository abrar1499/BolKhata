package com.bolkhata.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class NativeChatEngineTest {
    private val now = Calendar.getInstance().apply {
        set(2026, Calendar.OCTOBER, 30, 12, 0, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private val entries = listOf(
        entry("tea-today", "Tea", 5.0, Cat.KHANA, 0),
        entry("petrol-yesterday", "Petrol", 60.0, Cat.TRANSPORT, 1),
        entry("food-last-week", "Lunch", 35.0, Cat.KHANA, 5),
        entry("salary", "Salary", 3000.0, Cat.TANKHWAH, 2, Type.AAMDANI)
    )

    private fun entry(id: String, item: String, amount: Double, cat: Cat, daysAgo: Int, type: Type = Type.KHARCHA): Entry {
        val date = Calendar.getInstance().apply {
            timeInMillis = now
            add(Calendar.DAY_OF_YEAR, -daysAgo)
        }.timeInMillis
        return Entry(id, type, amount, cat, item, "", item, date, date)
    }

    private fun read(text: String, history: List<ChatMessage> = emptyList()) =
        NativeChatEngine.interpret(text, entries, now = now, history = history)

    @Test fun recognizesLastFiveTransactionsEvenThoughTheRequestContainsANumber() {
        val result = read("show last 5 transactions")
        assertEquals("ask", result.kind)
        assertEquals("list", result.what)
        assertEquals(5, result.filter.last)
    }

    @Test fun composesFoodCategoryExpenseTypeAndThisMonth() {
        val result = read("how much did I spend on food this month?")
        assertEquals("ask", result.kind)
        assertEquals("sum", result.what)
        assertEquals(setOf(Type.KHARCHA), result.filter.types)
        assertEquals(setOf(Cat.KHANA), result.filter.cats)
        assertTrue(result.filter.from != null)
        assertTrue(result.filter.to != null)
    }

    @Test fun understandsRomanUrduAndYesterdayItemSearch() {
        val result = read("kal petrol pe kitna kharcha hua?")
        assertEquals("ask", result.kind)
        assertEquals(listOf("petrol"), result.filter.items)
        assertEquals(setOf(Type.KHARCHA), result.filter.types)
        assertTrue(result.filter.from != null)
        assertEquals(result.filter.from, Query.day("2026-10-29"))
    }

    @Test fun lastFiveExpensesBecomeAConfirmedDeleteFilter() {
        val result = read("delete last 5 expenses")
        assertEquals("delete", result.kind)
        assertEquals(5, result.filter.last)
        assertEquals(setOf(Type.KHARCHA), result.filter.types)
    }

    @Test fun newExpenseIsReturnedAsAnEntryIntent() {
        val result = read("chai 12")
        assertEquals("entry", result.kind)
        assertTrue(result.text.contains("12"))
    }

    @Test fun greetingsDoNotBecomeTransactions() {
        val result = read("hello")
        assertEquals("chat", result.kind)
        assertTrue(result.reply.isNotBlank())
    }

    @Test fun dateFollowUpKeepsThePreviousQueryFilters() {
        val history = listOf(
            ChatMessage("u1", true, "how much did I spend on food this month?"),
            ChatMessage("a1", false, "Spent this month")
        )
        val result = read("what about yesterday?", history)
        assertEquals("ask", result.kind)
        assertEquals(setOf(Cat.KHANA), result.filter.cats)
        assertEquals(setOf(Type.KHARCHA), result.filter.types)
        assertEquals(Query.day("2026-10-29"), result.filter.from)
    }

    @Test fun explicitDayMonthSearchUsesTheExactDayAndDoesNotTreatMonthAsAnItem() {
        val result = read("28 oct expense")
        assertEquals("ask", result.kind)
        assertEquals(setOf(Type.KHARCHA), result.filter.types)
        assertTrue(result.filter.items.isEmpty())
        assertEquals(Query.day("2026-10-28"), result.filter.from)
        assertEquals(result.filter.from, result.filter.to?.let { Query.day("2026-10-28") })
    }

    @Test fun dateOnlyFollowUpInheritsThePreviousExpenseSearch() {
        val history = listOf(
            ChatMessage("u1", true, "today expense"),
            ChatMessage("a1", false, "Nothing found")
        )
        val result = read("28 oct", history)
        assertEquals("ask", result.kind)
        assertEquals(setOf(Type.KHARCHA), result.filter.types)
        assertEquals(Query.day("2026-10-28"), result.filter.from)
    }

    @Test fun searchAgainRepeatsTheLastDatabaseSearch() {
        val history = listOf(
            ChatMessage("u1", true, "show expenses between 10 and 50"),
            ChatMessage("a1", false, "Found 2 entries")
        )
        val result = read("search again", history)
        assertEquals("ask", result.kind)
        assertEquals(10.0, result.filter.min!!, 0.001)
        assertEquals(50.0, result.filter.max!!, 0.001)
        assertEquals(setOf(Type.KHARCHA), result.filter.types)
    }

    @Test fun pronounDeleteUsesOnlyTheExactEntryIdsFromTheLastAssistantResult() {
        val history = listOf(
            ChatMessage("u1", true, "show petrol yesterday"),
            ChatMessage("a1", false, "Petrol · 60", entryIds = listOf("petrol-yesterday"))
        )
        val result = read("delete that", history)
        assertEquals("delete", result.kind)
        assertEquals(setOf("petrol-yesterday"), result.filter.ids)
    }

    @Test fun vagueDeleteDoesNotCreateAnUnboundedMutation() {
        val result = read("delete it")
        assertEquals("chat", result.kind)
        assertTrue(result.reply.contains("which", ignoreCase = true))
    }

    @Test fun recognizesSavedKhataDestinationForExplicitBulkMove() {
        val result = NativeChatEngine.interpret(
            "move all entries to Haji Ashiq khata", entries, contacts = listOf("Haji Ashiq"), now = now
        )
        assertEquals("to_khata", result.kind)
        assertEquals("Haji Ashiq", result.set["person"])
        assertTrue(result.filter.isEmpty)
    }

    @Test fun attributesAnExistingExpenseToTheNamedPayer() {
        val result = NativeChatEngine.interpret(
            "lunch was paid by Ali yesterday", entries, contacts = listOf("Ali"), now = now
        )
        assertEquals("paid_by", result.kind)
        assertEquals("Ali", result.payer)
        assertTrue(result.filter.from != null)
    }

    @Test fun routesWrongEntryRequestsToTheLocalRereader() {
        val result = read("laptop wali entry galat hai theek karo")
        assertEquals("reread", result.kind)
        assertTrue(!result.filter.isEmpty)
    }

    @Test fun recognizesMonthNamesAndLastNDays() {
        val month = read("show expenses in September 2026")
        assertEquals(Query.day("2026-09-01"), month.filter.from)
        val recent = read("show expenses in last 7 days")
        assertEquals(7L * 86_400_000L, now - recent.filter.from!!)
        assertEquals(now, recent.filter.to)
    }

    @Test fun amountRangeIsAppliedToAListQuery() {
        val result = read("show expenses between 10 and 50")
        assertEquals(10.0, result.filter.min!!, 0.001)
        assertEquals(50.0, result.filter.max!!, 0.001)
    }

    @Test fun asksClarificationInsteadOfGuessingForUnknownMoneyQuestion() {
        val result = read("what is my crypto portfolio worth?")
        assertEquals("chat", result.kind)
        assertTrue(result.reply.isNotBlank())
    }
}
