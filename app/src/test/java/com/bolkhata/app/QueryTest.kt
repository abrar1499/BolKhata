package com.bolkhata.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class QueryTest {
    private fun daysAgo(n: Int) = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -n); set(Calendar.HOUR_OF_DAY, 12) }.timeInMillis
    private fun ymd(n: Int) = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date(daysAgo(n)))
    private fun e(id: String, t: Type, a: Double, c: Cat, item: String, person: String, ago: Int) =
        Entry(id, t, a, c, item, person, item, daysAgo(ago), daysAgo(ago))

    private val all = listOf(
        e("1", Type.KHARCHA, 30.0, Cat.KHANA, "Meals", "", 3), e("2", Type.KHARCHA, 70.0, Cat.TRANSPORT, "Petrol", "", 3),
        e("3", Type.DIYA, 13.33, Cat.UDHAAR, "Meals", "Ali", 0), e("4", Type.DIYA, 13.33, Cat.UDHAAR, "Meals", "Imran", 0),
        e("5", Type.KHARCHA, 20.0, Cat.KHANA, "Meals", "", 0)
    )

    @Test fun threeDaysAgoSpending() {
        val f = Query.Filter(from = Query.day(ymd(3)), to = Query.day(ymd(3), end = true), types = setOf(Type.KHARCHA))
        val list = Query.select(all, f)
        assertEquals(2, list.size)
        assertTrue(Query.answer(list, "sum", f, "SAR").startsWith("Spent 100 SAR"))
    }

    @Test fun aliAndImranToday() {
        val f = Query.Filter(from = Query.day(ymd(0)), to = Query.day(ymd(0), end = true), persons = listOf("ali", "IMRAN"))
        assertEquals(setOf("3", "4"), Query.select(all, f).map { it.id }.toSet())
    }

    @Test fun lastOne() { assertEquals(1, Query.select(all, Query.Filter(last = 1)).size) }

    @Test fun changeTransactionDescription() {
        val changed = Query.change(all[0], mapOf("note" to "Client lunch at cafe"))
        assertEquals("Client lunch at cafe", changed.note)
    }

    @Test fun changeAmountAndCategory() {
        val x = Query.change(all[0], mapOf("amount" to "40", "category" to "transport"))
        assertEquals(40.0, x.amount, 0.001); assertEquals(Cat.TRANSPORT, x.category)
    }

    @Test fun exactAmountDateAndCategoryFiltersCompose() {
        val f = Query.Filter(
            from = Query.day(ymd(3)),
            to = Query.day(ymd(3), end = true),
            types = setOf(Type.KHARCHA),
            cats = setOf(Cat.TRANSPORT),
            min = 70.0,
            max = 70.0
        )
        assertEquals(listOf("2"), Query.select(all, f).map { it.id })
    }

    @Test fun addedYesterdayUsesCreatedAtNotTransactionDate() {
        val oldTransaction = e("added-yesterday", Type.KHARCHA, 55.0, Cat.KHANA, "Dinner", "", 30)
            .copy(createdAt = daysAgo(1))
        val f = Query.Filter(createdFrom = Query.day(ymd(1)), createdTo = Query.day(ymd(1), end = true))
        assertEquals(listOf("added-yesterday"), Query.select(listOf(oldTransaction), f).map { it.id })
    }

    @Test fun listResultsIncludeTheComputedTotal() {
        val f = Query.Filter(types = setOf(Type.KHARCHA))
        val result = Query.answer(Query.select(all, f), "list", f, "SAR")
        assertTrue(result.contains("Total: Spent 120 SAR"))
    }

    @Test fun highestExpensesAreRankedByAmount() {
        val f = Query.Filter(types = setOf(Type.KHARCHA))
        val result = Query.answer(Query.select(all, f), "highest", f, "SAR")
        assertTrue(result.indexOf("Petrol") < result.indexOf("Meals"))
        assertTrue(result.contains("Matching expense total: 120 SAR"))
    }

    @Test fun emptyFilterIsRefused() { assertTrue(Query.Filter().isEmpty) }

    @Test fun withoutPeople() {
        val list = all + Entry("6", Type.KHARCHA, 300.0, Cat.OTHER, "Transfer", "", "To Muhammad", daysAgo(1), daysAgo(1)) +
            Entry("7", Type.KHARCHA, 40.0, Cat.OTHER, "Panda", "", "Panda", daysAgo(1), daysAgo(1))
        val f = Query.Filter(cats = setOf(Cat.OTHER), types = setOf(Type.KHARCHA), noPeople = true)
        assertEquals(listOf("7"), Query.select(list, f).map { it.id })
    }
}
