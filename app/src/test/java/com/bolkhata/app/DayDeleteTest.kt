package com.bolkhata.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Calendar

class DayDeleteTest {
    private fun read(s: String) = Commands.read(Understand.read(s).text)

    @Test fun todayAll() {
        val c = read("aj ki sari entry del kr do")!!
        assertEquals(Commands.Action.DELETE_PERIOD, c.action)
        assertEquals(Calendar.getInstance().get(Calendar.DAY_OF_MONTH), c.period!!.day)
    }
    @Test fun yesterdayAll() {
        val y = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
        assertEquals(y.get(Calendar.DAY_OF_MONTH), read("kal ki sab entry delete kr do")!!.period!!.day)
    }
    @Test fun thisMonth() { assertNull(read("is mahine ki sari entries delete karo")!!.period!!.day) }
    @Test fun notWhenSaidNot() { assertNull(read("aaj ki sari entry delete mat karo")) }
    @Test fun entryIsNotDelete() { assertNull(read("aaj petrol 50")) }
}
