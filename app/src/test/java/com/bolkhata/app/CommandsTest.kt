package com.bolkhata.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Calendar

class CommandsTest {
    private fun entry(year: Int, month: Int, day: Int = 12): Entry {
        val date = Calendar.getInstance().apply {
            clear()
            set(year, month - 1, day, 12, 0, 0)
        }.timeInMillis
        return Entry(
            id = "$year-$month-$day", type = Type.KHARCHA, amount = 15.0,
            category = Cat.KHANA, item = "Food", person = "", note = "",
            date = date, createdAt = date
        )
    }

    @Test fun deletesAllEntriesOfExplicitYear() {
        val cmd = Commands.read("delete 2025 all entries")
        assertNotNull(cmd)
        assertEquals(Commands.Action.DELETE_PERIOD, cmd!!.action)
        assertEquals(Commands.DatePeriod(2025), cmd.period)
        val matched = matchEntriesByPeriod(listOf(entry(2025, 10), entry(2026, 10), entry(2025, 12)), cmd.period!!)
        assertEquals(2, matched.size)
        assertEquals(listOf("2025-12-12", "2025-10-12"), matched.map { it.id })
    }

    @Test fun supportsMonthAndYearInEitherOrder() {
        for (text in listOf("delete March 2025", "2025 ki march entries delete karo", "clear 2025/03", "delete 03/2025")) {
            val cmd = Commands.read(text)
            assertEquals(text, Commands.DatePeriod(2025, 3), cmd?.period)
        }
        val matched = matchEntriesByPeriod(
            listOf(entry(2025, 3), entry(2025, 4), entry(2026, 3)),
            Commands.DatePeriod(2025, 3)
        )
        assertEquals(listOf("2025-3-12"), matched.map { it.id })
    }

    @Test fun monthAloneUsesCurrentYearExplicitly() {
        val cmd = Commands.read("delete october entries")
        assertEquals(Commands.Action.DELETE_PERIOD, cmd?.action)
        assertEquals(Calendar.getInstance().get(Calendar.YEAR), cmd?.period?.year)
        assertEquals(10, cmd?.period?.month)
    }

    @Test fun neverConfusesAmountsOrAmbiguousDatesWithBulkDelete() {
        for (text in listOf(
            "delete 2025 riyal", "delete 2025 and 2026",
            "delete March April 2025", "don't delete 2025",
            "delete 2025-10-12", "petrol 2025", "delete all"
        )) {
            val cmd = Commands.read(text)
            assertEquals(text, false, cmd?.action == Commands.Action.DELETE_PERIOD)
        }
        assertNull(Commands.read("delete 2025 riyal"))
        assertEquals(Commands.Action.CLEAR_ALL, Commands.read("delete all")?.action)
    }

    @Test fun importingMultiYearStatementNeverChangesOriginalYears() {
        val rows = listOf(
            entry(2025, 10, 9),
            entry(2025, 12, 30),
            entry(2026, 1, 2),
            entry(2026, 8, 18)
        )
        val now = Calendar.getInstance().apply {
            clear()
            set(2026, 9, 27, 12, 0, 0)
        }.timeInMillis
        val corrected = Ai.fixDates(rows, now)
        assertEquals(rows.map { it.date }, corrected.map { it.date })
        assertEquals(rows.map { it.createdAt }, corrected.map { it.createdAt })
    }
}
