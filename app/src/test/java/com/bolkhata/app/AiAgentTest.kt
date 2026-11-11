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
    fun aListIsShownAsTheAppsOwnCard() {
        val notes = AiAgent.Notes()
        val out = JSONObject(AiAgent.use("show", JSONObject("""{"view": "list", "types": ["expense"]}"""), host, now, notes))
        assertEquals(true, out.getBoolean("shown_to_the_person"))
        // The same text the app writes without the AI: one line per entry, then the total.
        assertEquals(Query.answer(saved.filter { it.type == Type.KHARCHA }.sortedByDescending { it.date }, "list",
            Query.Filter(types = setOf(Type.KHARCHA)), "PKR"), notes.card)
        assertEquals(listOf("new1", "old1", "new2"), notes.listIds)
        // What it picked is kept, so a follow-up ("detail do") stays on the same entries.
        assertEquals("""{"types":["expense"]}""", notes.filter)
    }

    @Test
    fun goodsAndWorkAreMultipliedByTheAppAndShowInTheReport() {
        setup = SideSetup(people = mapOf("Hamid Akram" to Sides.STAFF))
        val notes = AiAgent.Notes()
        AiAgent.use("add_entries", JSONObject("""{"entries": [
            {"type": "sale", "person": "Nargis Salim Rashid", "item": "shirts", "qty": 200, "rate": 350},
            {"type": "work", "person": "Hamid Akram", "item": "stitching", "qty": 5000, "rate": 400, "per_1000": true}]}"""), host, now, notes)
        val sale = saved.first { it.id == notes.added[0] }
        assertEquals(Type.SALE, sale.type); assertEquals(70_000.0, sale.amount, 0.001); assertEquals(Cat.SALES, sale.category)
        assertEquals(200.0, sale.qty, 0.001); assertEquals(350.0, sale.rate, 0.001)
        val work = saved.first { it.id == notes.added[1] }
        // 5000 at 400 per thousand is 2000; work by someone in Staff is a business cost.
        assertEquals(Type.PURCHASE, work.type); assertEquals(2_000.0, work.amount, 0.001)
        assertEquals(Cat.PRODUCTION, work.category); assertEquals("1000", work.unit)
        val period = use("report", "{}").getJSONObject("period")
        assertEquals(70_000.0, period.getDouble("sales"), 0.001)
        assertEquals(2_000.0, period.getDouble("business_costs"), 0.001)
        assertEquals(440.0, period.getDouble("expenses"), 0.001)
        assertEquals(67_560.0, period.getDouble("profit"), 0.001)
    }

    @Test
    fun monthByMonthIsACardWithTheAverage() {
        val month = { t: Long -> java.text.SimpleDateFormat("MMM yyyy", java.util.Locale.US).format(java.util.Date(t)) }
        val list = listOf(
            entry("a", Type.KHARCHA, 100.0, Cat.MOBILE, "Recharge", "", now, now),
            entry("b", Type.KHARCHA, 50.0, Cat.MOBILE, "Recharge", "", now - 1000, now),
            entry("c", Type.KHARCHA, 60.0, Cat.MOBILE, "Recharge", "", now - 40 * 86_400_000L, now)
        )
        // Newest month first, each with its entries and total; then the total and the average a month (210 / 2).
        assertEquals(
            "By month (recharge):\n" + month(now) + " · 2 entries · 150 PKR\n" + month(now - 40 * 86_400_000L) + " · 1 entry · 60 PKR\n" +
                "Total: 210 PKR · Average a month 105 PKR",
            AiAgent.grouped(list, "month", "recharge", "PKR")
        )
        // The chat reads it as a card: a row per month, the total and the average under it.
        val card = Report.parse(AiAgent.grouped(list, "month", "recharge", "PKR"), "PKR")!!
        assertEquals(2, card.rows.size)
        assertEquals(listOf("Total", "Average a month"), card.total.map { it.label })
    }

    @Test
    fun theSameEntryIsNotSavedTwiceWithinMinutes() {
        val loan = """{"entries": [{"type": "borrowed", "amount": 300, "person": "Hamid Akram"}]}"""
        assertEquals(1, use("add_entries", loan).getInt("saved"))
        val before = saved.size
        // Said again a moment later: nothing new is saved, and the AI is told the entry is already there.
        val again = use("add_entries", loan)
        assertEquals(0, again.getInt("saved"))
        assertEquals(1, again.getJSONArray("already_saved_minutes_ago_not_added_again").length())
        assertEquals(before, saved.size)
        // Asked for on purpose, a second one is saved.
        assertEquals(1, use("add_entries", """{"entries": [{"type": "borrowed", "amount": 300, "person": "Hamid Akram", "again": true}]}""").getInt("saved"))
    }

    @Test
    fun aCardThatFindsNothingIsNotShown() {
        val notes = AiAgent.Notes()
        val out = JSONObject(AiAgent.use("show", JSONObject("""{"view": "list", "persons": ["Nobody Here"]}"""), host, now, notes))
        assertEquals(false, out.getBoolean("shown_to_the_person"))
        assertEquals("", notes.card)
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
        val notes = AiAgent.Notes()
        val added = notes.added
        val out = JSONObject(AiAgent.use("add_entries", JSONObject("""{"entries": [{"type": "expense", "amount": 50, "category": "Food", "item": "chai"},
            {"type": "lent", "amount": 200, "person": "Hamid Akram"}]}"""), host, now, notes))
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
