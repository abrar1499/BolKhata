package com.bolkhata.app

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
            Query.grouped(list, "month", "recharge", "PKR")
        )
        // The chat reads it as a card: a row per month, the total and the average under it.
        val card = Report.parse(Query.grouped(list, "month", "recharge", "PKR"), "PKR")!!
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
    fun workWithNoRateSaidTakesThePersonsLastRate() {
        setup = SideSetup(people = mapOf("Hamid Akram" to Sides.STAFF))
        // Nothing done before: the AI is told to ask for the rate.
        assertTrue(runCatching { AiAgent.use("add_entries", JSONObject("""{"entries": [{"type": "work", "person": "Hamid Akram", "item": "stitching", "qty": 3000}]}"""), host, now) }.isFailure)
        use("add_entries", """{"entries": [{"type": "work", "person": "Hamid Akram", "item": "stitching", "qty": 5000, "rate": 400, "per_1000": true}]}""")
        // The same work again, and other work by the same person, with only a quantity: the last rate, per thousand.
        val notes = AiAgent.Notes()
        AiAgent.use("add_entries", JSONObject("""{"entries": [{"type": "work", "person": "Hamid Akram", "item": "stitching", "qty": 3000},
            {"type": "work", "person": "Hamid Akram", "item": "cutting", "qty": 2000}]}"""), host, now, notes)
        val made = saved.filter { it.id in notes.added }.sortedByDescending { it.amount }
        assertEquals(listOf(1_200.0, 800.0), made.map { it.amount })
        assertEquals(listOf("1000", "1000"), made.map { it.unit })
    }

    @Test
    fun aPersonAndANumberAloneAskWhatHappened() {
        val who = AiAgent.who(listOf("Hamid Akram", "Nargis Salim Rashid"), emptyMap())
        assertEquals("Hamid Akram" to 10_000.0, Ask.of("hmd 10000", who))
        assertEquals("Nargis Salim Rashid" to 500.0, Ask.of("500 nargis salim", who))
        // A word for what happened, or a thing, is not for the card.
        assertEquals(null, Ask.of("hamid ko 500 diye", who)); assertEquals(null, Ask.of("petrol 60", who))
        assertEquals(listOf("Paid", "Work", "Received"), Ask.kinds(Sides.STAFF))
        assertEquals(listOf("Paid", "Received"), Ask.kinds(Sides.PEOPLE))
        // Paid is money as said; Work counts things at the rate, per thousand, and is a business cost for staff.
        val paid = Ask.entry("Paid", "Hamid Akram", 10_000.0, true, now)
        assertEquals(Type.DIYA, paid.type); assertEquals(10_000.0, paid.amount, 0.0)
        val work = Ask.entry("Work", "Hamid Akram", 10_000.0, true, now, "Stitching", 400.0, true)
        assertEquals(Type.PURCHASE, work.type); assertEquals(4_000.0, work.amount, 0.0); assertEquals(Cat.PRODUCTION, work.category)
        assertEquals(10_000.0, work.qty, 0.0); assertEquals("1000", work.unit)
        // The person's last work is offered with its rate.
        assertEquals(listOf("Stitching"), Ask.usual("Work", "hamid akram", listOf(work, paid)).map { it.item })
    }

    @Test
    fun theSameCardAskedAgainShowsTheEntries() {
        use("add_entries", """{"entries": [{"type": "income", "amount": 500}, {"type": "income", "amount": 700}]}""")
        val first = AiAgent.Notes()
        AiAgent.use("show", JSONObject("""{"view": "total", "types": ["income"]}"""), host, now, first)
        // "Detail" brings the same total again: the list of entries is shown instead.
        val second = AiAgent.Notes(lastShown = first.card)
        AiAgent.use("show", JSONObject("""{"view": "total", "types": ["income"]}"""), host, now, second)
        assertTrue(second.card != first.card)
        assertEquals(2, second.listIds.size)
    }

    @Test
    fun oneWorkKeepsOneName() {
        setup = SideSetup(people = mapOf("Hamid Akram" to Sides.STAFF))
        use("add_entries", """{"entries": [{"type": "work", "person": "Hamid Akram", "item": "Raw shirts", "qty": 5000, "rate": 400, "per_1000": true},
            {"type": "work", "person": "Hamid Akram", "item": "Loading", "qty": 5000, "rate": 100, "per_1000": true}]}""")
        // The AI words the same work longer, or spells it a little differently: it stays under the first name.
        val notes = AiAgent.Notes()
        AiAgent.use("add_entries", JSONObject("""{"entries": [{"type": "work", "person": "Hamid Akram", "item": "raw shirts stitching", "qty": 1000},
            {"type": "work", "person": "Hamid Akram", "item": "raw shirt", "qty": 1000},
            {"type": "work", "person": "Hamid Akram", "item": "Unloading", "qty": 1000, "rate": 100, "per_1000": true}]}"""), host, now, notes)
        assertEquals(listOf("Raw shirts", "Raw shirts", "Unloading"), notes.added.map { id -> saved.first { it.id == id }.item })
        assertEquals("Loading", Goods.sameAs("loading", listOf("Raw shirts", "Loading")))
    }

    @Test
    fun numbersSaidInWordsAreWorkedOutByTheApp() {
        assertEquals("nargis se 250000 mile", NumberWords.convert("nargis se dhai lakh mile"))
        assertEquals("1500 diye", NumberWords.convert("derh hazar diye"))
        // A quarter more, a quarter less, and a half more than the number that follows.
        assertEquals("225000 mile", NumberWords.convert("sawa do lakh mile"))
        assertEquals("175000 diye", NumberWords.convert("paune do lakh diye"))
        assertEquals("3500 diye", NumberWords.convert("sadhe teen hazar diye"))
        assertEquals("125000 ka generator", NumberWords.convert("sawa lakh ka generator"))
    }

    @Test
    fun anAmountTypedAgainIsSavedAgain() {
        val loan = JSONObject("""{"entries": [{"type": "lent", "amount": 30, "person": "Hamid Akram"}]}""")
        val first = AiAgent.Notes(said = listOf(30.0))
        AiAgent.use("add_entries", loan, host, now, first)
        // The person typed "hamid ko 30 diye" once more: the amount is in the message, so it is one more entry.
        val second = AiAgent.Notes(said = listOf(30.0))
        AiAgent.use("add_entries", loan, host, now, second)
        assertEquals(1, second.added.size)
        assertEquals(0, second.skipped)
    }

    @Test
    fun aNameIsLearntAsOneWordAndTheSentenceStaysKnown() {
        val names = listOf("Hamid Akram", "Nargis Salim Rashid")
        // "hamid ko 50 diye" was read by the AI once: the name and the number are open places in what is kept.
        val gave = listOf("add_entries" to JSONObject("""{"entries": [{"type": "lent", "amount": 50, "person": "Hamid Akram"}]}"""))
        val first = AiAgent.learn(gave, "hamid ko 50 diye", names, emptyMap())
        assertEquals("@ ko # diye", first.shape)
        var known = first.words + (first.shape to first.lesson)
        // Another saved person, typed short, in the same sentence: done by the app alone.
        var who = AiAgent.who(names, known)
        assertEquals(first.shape, AiAgent.shape("nrgs ko 70 diye", who))
        val entry = AiAgent.recall(first.lesson, "nrgs ko 70 diye", who)!!.getJSONArray("entries").getJSONObject(0)
        assertEquals("Nargis Salim Rashid", entry.getString("person"))
        assertEquals(70.0, entry.getDouble("amount"), 0.0)
        // A spelling the app cannot tell goes to the AI once; only that word is new, and is kept by itself.
        assertEquals(false, AiAgent.shape("zbr ko 20 diye", who) == first.shape)
        val zubair = listOf("add_entries" to JSONObject("""{"entries": [{"type": "lent", "amount": 20, "person": "Zubair"}]}"""))
        val second = AiAgent.learn(zubair, "zbr ko 20 diye", names, known)
        assertEquals(mapOf("=zbr" to "Zubair"), second.words)
        assertEquals(first.shape, second.shape)
        known = known + second.words
        // The new word now works in every sentence the app already knows.
        who = AiAgent.who(names, known)
        assertEquals("Zubair", AiAgent.recall(first.lesson, "zbr ko 5 diye", who)!!.getJSONArray("entries").getJSONObject(0).getString("person"))
        // A name the AI used for nothing means the sentence is something else: not learnt.
        val other = listOf("add_entries" to JSONObject("""{"entries": [{"type": "expense", "amount": 50, "item": "Chai"}]}"""))
        assertEquals("", AiAgent.learn(other, "hamid ke sath chai 50", names, known).lesson)
    }

    @Test
    fun theAppLearnsASentenceFromTheAi() {
        // The AI read "hamid ne 50 diye aur chai pr 20 lagay" as two entries: the app keeps them, each number by its place.
        val said = "Hamid ne 50 diye aur chai pr 20 lagay"
        val lesson = AiAgent.lesson(listOf("add_entries" to JSONObject("""{"entries": [
            {"type": "received_back", "amount": 50, "person": "Hamid Akram"}, {"type": "expense", "amount": 20, "category": "Food", "item": "Chai"}]}""")), said)!!
        // The same sentence with other numbers has the same shape, and is done by the app alone.
        val again = "hamid ne 300 diye aur chai pr 15 lagay"
        assertEquals(AiAgent.shape(said), AiAgent.shape(again))
        val notes = AiAgent.Notes()
        AiAgent.use("add_entries", AiAgent.recall(lesson, again)!!, host, now, notes)
        val made = saved.filter { it.id in notes.added }.sortedByDescending { it.amount }
        assertEquals(listOf(300.0, 15.0), made.map { it.amount })
        assertEquals(Type.WAPIS_MILA, made[0].type); assertEquals("Hamid Akram", made[0].person)
        assertEquals("Chai", made[1].item); assertEquals(Cat.KHANA, made[1].category)
        // "150k" is one number; other words give another shape.
        assertEquals(listOf(150_000.0, 120_400.0), AiAgent.numbers("salim 150k and rashid 120.4k"))
        assertEquals(false, AiAgent.shape("hamid ko 50 diye") == AiAgent.shape("hamid ne 50 diye"))
        // Not learnt: a day the AI worked out, an amount that was not said, or more than one step.
        assertEquals(null, AiAgent.lesson(listOf("add_entries" to JSONObject("""{"entries": [{"type": "expense", "amount": 50, "date": "2026-11-10"}]}""")), "kal chai 50"))
        assertEquals(null, AiAgent.lesson(listOf("add_entries" to JSONObject("""{"entries": [{"type": "expense", "amount": 75}]}""")), "chai 50"))
        assertEquals(null, AiAgent.lesson(listOf("find_entries" to JSONObject("{}"), "add_entries" to JSONObject("""{"entries": [{"type": "expense", "amount": 50}]}""")), "chai 50"))
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

    @Test
    fun answerInHindiOrUrduScriptIsShownInRoman() {
        val hindi = AiAgent.roman("मैं ठीक हूँ, आप कैसे हैं?")
        assertTrue(hindi, hindi.none { it in '\u0900'..'\u097F' })
        assertTrue(AiAgent.roman("میں ٹھیک ہوں").none { it in '\u0600'..'\u06FF' })
        assertEquals("Saved 500 for Hamid Akram.", AiAgent.roman("Saved 500 for Hamid Akram."))
    }

    @Test
    fun aLookUpAndAPlainAnswerAreLearntWithTheirPeopleOpen() {
        val names = listOf("Hamid Akram", "Zubair Latif")
        val calls = listOf("totals" to JSONObject().put("persons", JSONArray().put("Hamid Akram")).put("group_by", "month"))
        val learnt = AiAgent.learn(calls, "hamid ka mahana hisab", names, emptyMap(), "Here it is.")
        assertTrue(learnt.lesson, learnt.lesson.contains("by_month"))
        val r = AiAgent.replay(learnt.lesson, "zubair ka mahana hisab", AiAgent.who(names, emptyMap()))!!
        assertEquals("show", r.calls.single().first)
        assertEquals("Zubair Latif", r.calls.single().second.getJSONArray("persons").getString(0))
        // A change to chosen entries, or an answer with its own figures, is not learnt.
        assertEquals("", AiAgent.learn(listOf("delete_entries" to JSONObject().put("ids", JSONArray().put("x1"))), "chai hata do", names, emptyMap(), "Done.").lesson)
        assertEquals("", AiAgent.learn(emptyList(), "kitna bacha", names, emptyMap(), "You have 300 left.").lesson)
        assertEquals("Great!", AiAgent.replay(AiAgent.learn(emptyList(), "zabardast", names, emptyMap(), "Great!").lesson, "zabardast")!!.reply)
    }
}
