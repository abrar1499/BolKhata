package com.bolkhata.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class SplitTest {
    private val contacts = listOf("Ali", "Bilal", "Ashraf", "Imran")

    private fun plan(s: String) = Split.detect(Understand.read(s, contacts).text, contacts)

    /** "Expense 10; Ali 10 to receive; Bilal 10 to receive" style check. */
    private fun check(list: List<Entry>, mine: Double, vararg owe: Pair<String, Double>, owedType: Type = Type.DIYA) {
        val exp = list.filter { it.type == Type.KHARCHA }
        assertEquals("my share", mine, exp.sumOf { it.amount }, 0.01)
        owe.forEach { (who, amt) ->
            val e = list.single { it.person == who }
            assertEquals("type for $who", owedType, e.type)
            assertEquals("amount for $who", amt, e.amount, 0.01)
        }
        assertEquals(1 + owe.size, list.size)
    }

    @Test fun withTwoNamed() {
        val p = plan("khana 30 ali bilal ke sath")!!
        assertEquals(0, p.missing)
        check(Split.entries(p), 10.0, "Ali" to 10.0, "Bilal" to 10.0)
        assertEquals(Cat.KHANA, Split.entries(p).first { it.type == Type.KHARCHA }.category)
    }

    @Test fun namesFirst() { check(Split.entries(plan("ali bilal sath khana 30")!!), 10.0, "Ali" to 10.0, "Bilal" to 10.0) }
    @Test fun splitWord() { check(Split.entries(plan("khana 30 split ali bilal")!!), 10.0, "Ali" to 10.0, "Bilal" to 10.0) }
    @Test fun aurBetweenNames() { check(Split.entries(plan("khana 30 ali aur bilal ke sath")!!), 10.0, "Ali" to 10.0, "Bilal" to 10.0) }

    @Test fun humTeenWithNames() {
        val p = plan("hum 3 ne 30 ka khana khaya ali bilal")!!
        assertEquals(30.0, p.bill.amount, 0.01)
        check(Split.entries(p), 10.0, "Ali" to 10.0, "Bilal" to 10.0)
    }

    @Test fun noNamesAsksForTwo() {
        val p = plan("hum 3 bando ne 30 riyal ka khana khaya")!!
        assertEquals(2, p.missing)
        val full = p.with(Split.namesIn("ali aur bilal", contacts))
        assertEquals(0, full.missing)
        check(Split.entries(full), 10.0, "Ali" to 10.0, "Bilal" to 10.0)
    }

    @Test fun teeno() { assertEquals(2, plan("teeno ne khana khaya 30")!!.missing) }
    @Test fun splitWithoutAnyone() { assertEquals(-1, plan("khana 30 split")!!.missing) }

    @Test fun unequalShares() { check(Split.entries(plan("khana 30 ali 12 bilal 8")!!), 10.0, "Ali" to 12.0, "Bilal" to 8.0) }

    @Test fun someoneElsePaid() {
        val list = Split.entries(plan("ali ne pay kiya khana 30 sath")!!)
        assertEquals(15.0, list.filter { it.type == Type.KHARCHA }.sumOf { it.amount }, 0.01)
        val owe = list.single { it.type == Type.LIYA }
        assertEquals("Ali", owe.person); assertEquals(15.0, owe.amount, 0.01)
    }

    @Test fun unknownNameJoinedByAur() {
        val p = plan("khana 30 jamshed aur bilal ke sath")!!
        check(Split.entries(p), 10.0, "Jamshed" to 10.0, "Bilal" to 10.0)
    }

    @Test fun misspelledNameInAnswer() { assertEquals(listOf("Ashraf", "Bilal"), Split.namesIn("ashrf aur bilal", contacts)) }

    @Test fun ordinaryEntriesAreNotSplit() {
        assertNull(plan("petrol 70"))
        assertNull(plan("ali ko 50 diye"))
        assertNull(plan("khana 30"))
        assertNotNull(plan("pizza 45 ali imran ke sath"))
    }

    @Test fun moneyAddsUp() {
        val list = Split.entries(plan("khana 100 ali bilal sath")!!)
        assertEquals(100.0, list.sumOf { it.amount }, 0.001)
    }

    @Test fun withWord() { check(Split.entries(plan("khana with nadeem 30")!!), 15.0, "Nadeem" to 15.0) }
    @Test fun foodWithAName() { check(Split.entries(plan("khana ali 40")!!), 20.0, "Ali" to 20.0) }
    @Test fun loanIsNotSplit() {
        assertNull(plan("ali ko khana 40 diya"))
        assertNull(plan("ali se 40 liye"))
        assertNull(plan("petrol ali 40"))
    }
}
