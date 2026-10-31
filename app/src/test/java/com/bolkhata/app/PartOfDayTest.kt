package com.bolkhata.app

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar

class PartOfDayTest {
    private fun at(day: Int, h: Int, m: Int = 0) = Calendar.getInstance().apply {
        set(2026, Calendar.OCTOBER, day, h, m, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    @Test fun nashtaBeforeNineIsNow() {
        val now = at(31, 8, 20)
        assertEquals(now, Parser.partOfDayTime(now, Parser.dayPartHour("nashta 6"), now))
    }

    @Test fun nashtaAfterNineIsSevenInTheMorning() {
        val now = at(31, 11, 5)
        assertEquals(at(31, 7), Parser.partOfDayTime(now, Parser.dayPartHour("nashta 6"), now))
    }

    @Test fun pastDayUsesTheTypicalHour() {
        assertEquals(at(30, 13), Parser.partOfDayTime(at(30, 9), Parser.dayPartHour("kal lunch 20"), at(31, 10)))
    }

    @Test fun noPartOfDayKeepsTheTime() {
        val now = at(31, 16, 40)
        assertEquals(now, Parser.partOfDayTime(now, Parser.dayPartHour("khana 50"), now))
    }
}

class SubAccountTest {
    private fun e(cat: Cat, item: String, note: String = "") = Entry("x", Type.KHARCHA, 10.0, cat, item, "", note, 0L, 0L)

    @Test fun shopsAndWordsFallIntoFixedSubAccounts() {
        assertEquals("Petrol", Parser.subAccount(e(Cat.TRANSPORT, "NAFT Services Company")))
        assertEquals("Groceries", Parser.subAccount(e(Cat.KHANA, "Panda 241")))
        assertEquals("Meals", Parser.subAccount(e(Cat.KHANA, "Nashta")))
        assertEquals("Traffic fines", Parser.subAccount(e(Cat.TRANSPORT, "Traffic violation")))
        assertEquals("Car repair", Parser.subAccount(e(Cat.TRANSPORT, "Workshop", "gari repair")))
        assertEquals("Other", Parser.subAccount(e(Cat.TRANSPORT, "Something")))
    }
}
