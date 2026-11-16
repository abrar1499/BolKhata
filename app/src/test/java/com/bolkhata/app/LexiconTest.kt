package com.bolkhata.app

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The shared brain's words: new ways of saying things are read like the app's own; known words keep their meaning. */
class LexiconTest {
    private val now = System.currentTimeMillis()
    private val brain = """{"same": {"utarwa": "wapis", "diye": "mile", "jazz": "kashh", "net banking": "online"},
        "things": {"gajrela": "Food", "chai": "Rent", "parlour": "nothing"}, "names": ["zorawar", "petrol"]}"""

    @After fun forget() = Lexicon.load("{}")

    /** One message on a small made-up ledger: a person who lent money, the rest new. */
    private fun saves(text: String): List<Entry> {
        var entries = listOf(Entry("b1", Type.LIYA, 2_000.0, Cat.UDHAAR, "Loans", "Zubair Latif", "", now, now))
        val host = AiAgent.Host(entries = { entries }, add = { entries = it + entries }, update = {}, trash = {},
            names = { entries.map { it.person }.distinct() }, currency = "PKR", setup = { SideSetup() }, saveSetup = {})
        LocalAgent.run(text, host, now)
        return entries.filter { it.id != "b1" }
    }

    @Test
    fun onlyNewWordsAreTaken() {
        Lexicon.load(brain)
        assertEquals("zubair ko 500 wapis diye", Lexicon.apply("zubair ko 500 utarwa diye"))
        // A word the app knows, or a meaning it does not, is left as it was.
        assertEquals("hamid ko 500 diye jazz", Lexicon.apply("hamid ko 500 diye jazz"))
        assertEquals("500 online bheje", Lexicon.apply("500 net banking bheje"))
        assertEquals(Cat.KHANA, Lexicon.thing(listOf("gajrela"))?.first)
        assertEquals(null, Lexicon.thing(listOf("chai")))
        assertTrue(Lexicon.isName("zorawar") && !Lexicon.isName("petrol"))
    }

    @Test
    fun theBrainsWordsSave() {
        Lexicon.load(brain)
        Lexicon.load(Lexicon.saved)
        assertTrue(saves("zubair ko 500 utarwa diye").single().let { it.type == Type.WAPIS_DIYA && it.amount == 500.0 })
        assertTrue(saves("gajrela 300").single().let { it.type == Type.KHARCHA && it.category == Cat.KHANA })
        assertTrue(saves("zorawar ko 700 diye").single().let { it.type == Type.DIYA && it.person == "Zorawar" })
    }
}
