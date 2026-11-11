package com.bolkhata.app

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

/** The tools the AI works the app with: they act on the saved entries, and the app does the sums. */
class AiAgentTest {
    private val now = 1_800_000_000_000L
    private var saved = listOf(
        entry("old1", Type.KHARCHA, 300.0, Cat.KHANA, "Groceries", "", now - 5 * 86_400_000L, now - 5 * 86_400_000L),
        entry("old2", Type.DIYA, 500.0, Cat.UDHAAR, "Loan", "Hamid Akram", now - 3 * 86_400_000L, now - 3 * 86_400_000L),
        entry("new1", Type.KHARCHA, 100.0, Cat.TRANSPORT, "Petrol", "", now - 10 * 60_000L, now - 10 * 60_000L),
        // Dated last week but typed in just now: "added in the last 20 minutes" goes by when it was entered.
        entry("new2", Type.KHARCHA, 40.0, Cat.KHANA, "Tea", "", now - 7 * 86_400_000L, now - 5 * 60_000L)
    )
    private val trashed = mutableSetOf<String>()
    private var setup = SideSetup()
    private var made: PdfDoc? = null
    private val host = AiAgent.Host(
        entries = { saved }, add = { saved = it + saved },
        update = { changed -> saved = saved.map { e -> changed.firstOrNull { it.id == e.id } ?: e } },
        trash = { ids -> trashed += ids; saved = saved.filter { it.id !in ids } }, names = { listOf("Hamid Akram") }, currency = "PKR",
        setup = { setup }, saveSetup = { setup = it }, pdf = { made = it }
    )

    private fun entry(id: String, type: Type, amount: Double, cat: Cat, item: String, person: String, date: Long, created: Long) =
        Entry(id, type, amount, cat, item, person, "", date, created)
    private fun use(name: String, input: String) = JSONObject(AiAgent.use(name, JSONObject(input), host, now))
    private fun ids(o: JSONObject) = o.getJSONArray("entries").let { a -> (0 until a.length()).map { a.getJSONObject(it).getString("id") } }.toSet()

    @Test
    fun entriesTypedInLatelyAreFoundAndDeleted() {
        val found = use("find_entries", """{"added_in_last_minutes": 20}""")
        assertEquals(setOf("new1", "new2"), ids(found))
        assertEquals(140.0, found.getDouble("total"), 0.001)
        assertEquals(2, use("delete_entries", JSONObject().put("ids", JSONArray(ids(found))).toString()).getInt("moved_to_trash"))
        assertEquals(setOf("new1", "new2"), trashed)
        assertEquals(listOf("old1", "old2"), saved.map { it.id })
    }

    @Test
    fun peopleArePutInAnAccountWithoutAnEntry() {
        use("put_in_account", """{"account": "staff", "people": ["Nargis Salim Rashid", "hamid akram"]}""")
        // New people sit in Staff; one who was elsewhere moves there (one person, one account).
        assertEquals("Staff", Sides.bookOf("Nargis Salim Rashid", saved, setup))
        assertEquals("Staff", Sides.bookOf("Hamid Akram", saved, setup))
        val party = use("accounts", "{}").getJSONArray("party")
        val staff = (0 until party.length()).map { party.getJSONObject(it) }.first { it.getString("name") == "Staff" }
        assertEquals(2, staff.getJSONArray("people").length())
        use("put_in_account", """{"account": "Drivers", "people": ["Hamid Akram"]}""")
        assertEquals("Drivers", Sides.bookOf("Hamid Akram", saved, setup))
        assertEquals("Fuel", use("add_account", """{"side": "expense", "name": "Fuel"}""").getString("made"))
        assertEquals(true, setup.books.any { it.name == "Fuel" && !it.people && it.side == Side.EXP })
    }

    @Test
    fun aPdfHoldsEveryMatchingEntryWithTotals() {
        val out = use("make_pdf", """{"title": "Expenses", "types": ["expense"], "group_by": "category"}""")
        assertEquals(3, out.getInt("entries"))
        val doc = made!!
        assertEquals("Expenses", doc.title)
        // A summary, then Food (2 entries) and Transport (1), the biggest first.
        assertEquals(listOf("Summary", "Food", "Transport"), doc.tables.map { it.heading })
        assertEquals(listOf("Total", "3", "440 PKR"), doc.tables[0].foot)
        assertEquals(2, doc.tables[1].rows.size)
        assertEquals("340 PKR", doc.tables[1].foot!!.last())
        assertEquals("Tea", doc.tables[1].rows[0][1])
    }

    @Test
    fun theAppDoesTheSums() {
        val t = use("totals", """{"types": ["expense"], "group_by": "category"}""")
        assertEquals(440.0, t.getDouble("total"), 0.001)
        val groups = t.getJSONArray("groups")
        assertEquals("Food", groups.getJSONObject(0).getString("name"))
        assertEquals(340.0, groups.getJSONObject(0).getDouble("total"), 0.001)
        val b = use("balances", "{}").getJSONArray("people").getJSONObject(0)
        assertEquals("Hamid Akram", b.getString("name"))
        assertEquals(500.0, b.getDouble("amount"), 0.001)
        assertEquals("owes the owner", b.getString("means"))
    }

    @Test
    fun newAndChangedEntriesAreSaved() {
        val added = mutableListOf<String>()
        val out = JSONObject(AiAgent.use("add_entries", JSONObject("""{"entries": [{"type": "expense", "amount": 50, "category": "Food", "item": "chai"},
            {"type": "lent", "amount": 200, "person": "Hamid Akram"}]}"""), host, now, added))
        assertEquals(2, out.getInt("saved"))
        assertEquals(2, added.size)
        val chai = saved.first { it.id == added[0] }
        assertEquals("Chai", chai.item); assertEquals(Cat.KHANA, chai.category); assertEquals(50.0, chai.amount, 0.001)
        assertEquals(Type.DIYA, saved.first { it.id == added[1] }.type)
        use("update_entries", """{"ids": ["old1"], "set": {"amount": 350, "category": "Shopping"}}""")
        assertEquals(350.0, saved.first { it.id == "old1" }.amount, 0.001)
        assertEquals("Shopping", saved.first { it.id == "old1" }.category.label)
        // A loan without a person is refused, and nothing is saved.
        val before = saved.size
        runCatching { AiAgent.use("add_entries", JSONObject("""{"entries": [{"type": "lent", "amount": 10}]}"""), host, now) }
        assertEquals(before, saved.size)
    }
}
