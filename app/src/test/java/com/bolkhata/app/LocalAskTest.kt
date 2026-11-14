package com.bolkhata.app

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

/**
 * The app's own reader of questions ([LocalAsk]) on a small made-up ledger: balances, totals, lists, the report, accounts,
 * PDFs and follow-ups, said in Roman Urdu, English, Urdu or Hindi script, short or misspelt. Every figure comes from the tools.
 */
class LocalAskTest {
    private val now = Calendar.getInstance().apply { set(2026, Calendar.NOVEMBER, 14, 12, 0, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis
    private val day = 86_400_000L

    private fun work(id: String, person: String, item: String, qty: Double, rate: Double, daysAgo: Int) =
        Entry(id, Type.PURCHASE, qty / 1000 * rate, Cat.PRODUCTION, item, person, "", now - daysAgo * day, now - daysAgo * day, qty = qty, unit = "1000", rate = rate)

    private fun entry(id: String, type: Type, amount: Double, cat: Cat, item: String, person: String, daysAgo: Int) =
        Entry(id, type, amount, cat, item, person, "", now - daysAgo * day, now - daysAgo * day)

    /** Staff with work, a customer, loans both ways, expenses this month and last, income. */
    private var entries = listOf(
        work("w1", "Hamid Akram", "Kachi eent", 20_000.0, 1_000.0, 6),
        entry("a1", Type.DIYA, 50_000.0, Cat.UDHAAR, "Loans", "Hamid Akram", 5),
        work("w2", "Zubair Latif", "Bharai", 10_000.0, 300.0, 4),
        Entry("s1", Type.SALE, 60_000.0, Cat.SALES, "Pakki eent", "Nargis Salim Rashid", "", now - 4 * day, now - 4 * day, qty = 5_000.0, unit = "1000", rate = 12_000.0),
        entry("r1", Type.WAPIS_MILA, 20_000.0, Cat.UDHAAR, "Loans", "Nargis Salim Rashid", 3),
        entry("b1", Type.LIYA, 1_000.0, Cat.UDHAAR, "Loans", "Bilal Tariq", 3),
        entry("l1", Type.DIYA, 500.0, Cat.UDHAAR, "Loans", "Salim Khan", 8),
        entry("l2", Type.DIYA, 900.0, Cat.UDHAAR, "Loans", "Salim Raza", 7),
        entry("l3", Type.DIYA, 1_200.0, Cat.UDHAAR, "Loans", "Mohsin Javed", 9),
        entry("x1", Type.KHARCHA, 60.0, Cat.TRANSPORT, "Petrol", "", 2),
        entry("x2", Type.KHARCHA, 300.0, Cat.KHANA, "Groceries", "", 2),
        entry("x3", Type.KHARCHA, 5.0, Cat.KHANA, "Tea", "", 1),
        entry("x4", Type.KHARCHA, 350.0, Cat.BIJLI, "Electricity", "", 40),
        entry("x5", Type.KHARCHA, 80.0, Cat.TRANSPORT, "Petrol", "", 35),
        Entry("t1", Type.KHARCHA, 200.0, Cat.KHANA, "Lunch", "", "", now - 3_600_000L, now - 3_600_000L),
        entry("i1", Type.AAMDANI, 4_000.0, Cat.TANKHWAH, "Salary", "", 30),
        entry("i2", Type.AAMDANI, 4_000.0, Cat.TANKHWAH, "Salary", "", 2),
        entry("i3", Type.AAMDANI, 700.0, Cat.TANKHWAH, "Commission", "", 1)
    )
    private val setup = SideSetup(people = mapOf("Hamid Akram" to Sides.STAFF, "Zubair Latif" to Sides.STAFF, "Nargis Salim Rashid" to Sides.CUSTOMERS))
    private var pdf: PdfDoc? = null
    private val host = AiAgent.Host(
        entries = { entries }, add = { entries = it + entries }, update = {}, trash = {},
        names = { (entries.map { it.person } + setup.people.keys).map { it.trim() }.filter { it.isNotBlank() }.distinct() },
        currency = "PKR", setup = { setup }, pdf = { pdf = it }
    )

    /** What the reader made of one message: its reply (null when it is not a question), the card and the tools used. */
    private class Out(val reply: LocalAgent.Reply?, val notes: AiAgent.Notes, val tools: List<Pair<String, JSONObject>>) {
        val text get() = reply?.text.orEmpty()
        val card get() = notes.card
        /** The sentence and the card, without thousands commas. */
        val told get() = (text + " " + card).replace(",", "")
        fun used(name: String) = tools.lastOrNull { it.first == name }?.second
    }

    private fun ask(said: String, previous: String = "", lastShown: String = ""): Out {
        val roman = Translit.toRoman(said)
        val counted = AiAgent.counted(roman)
        val notes = AiAgent.Notes(said = (AiAgent.numbers(roman) + AiAgent.numbers(counted)).distinct(), lastShown = lastShown)
        val tools = mutableListOf<Pair<String, JSONObject>>()
        val s = LocalAgent.Said(said, roman, counted, host, now, previous, emptyList(), lastShown, "", notes) { name, input, _ -> tools += name to input }
        return Out(LocalAsk.read(s), notes, tools)
    }

    private fun says(part: String, vararg said: String) = said.forEach { val o = ask(it); assertTrue("$it -> ${o.text} | ${o.card}", part in o.text) }

    private fun list(o: JSONObject?, key: String) = o?.optJSONArray(key)?.let { a -> (0 until a.length()).map { a.getString(it) } }.orEmpty()

    private val thisMonth = "\"from\":\"2026-11-01\""

    @Test
    fun workerBalanceAnyWordOrder() = says("Hamid Akram owes you 30,000 PKR.", "hamid ka baqi kitna hai", "hamid ka balance", "baqi kitna hai hamid ka", "hamid kitna", "hamid ka udhaar kitna hai")

    @Test
    fun balanceInEnglish() = says("Hamid Akram owes you 30,000 PKR.", "how much does hamid owe me", "what's hamid's balance", "hamid owes how much", "how much will hamid pay me")

    @Test
    fun whatTheOwnerOwesAWorker() = says("You owe Zubair Latif 3,000 PKR.", "zubair ko kitne dene hain", "how much do I owe zubair", "zubair ka kitna dena hai")

    @Test
    fun whatACustomerOwes() = says("Nargis Salim Rashid owes you 40,000 PKR.", "nargis se kitne lene hain", "nargis se kitna lena hai",
        "how much does nargis have to pay", "nargis say kitnay laynay hain")

    @Test
    fun shortAndMisheardNames() {
        says("Hamid Akram owes you 30,000 PKR.", "hmd ka baqi", "hameed ka baqi kitna hai")
        says("Nargis Salim Rashid owes you 40,000 PKR.", "nrgs se kitne lene")
    }

    @Test
    fun urduScript() {
        val o = ask("حامد کا حساب بتاؤ")
        assertTrue(o.text, "Hamid Akram owes you 30,000 PKR." in o.text)
        assertTrue(o.card.startsWith("Hamid Akram will pay you 30,000 PKR"))
        says("Nargis Salim Rashid owes you 40,000 PKR.", "نرگس سے کتنے لینے ہیں")
        val month = ask("اس مہینے کا خرچہ کتنا ہے")
        assertTrue(month.told, "565" in month.told)
        assertFalse(Regex("[؀-ۿऀ-ॿ]").containsMatchIn(month.text + o.text))
    }

    @Test
    fun hindiScript() {
        says("Hamid Akram owes you 30,000 PKR.", "हामिद का बाकी कितना है")
        says("Spent 565 PKR this month.", "इस महीने का खर्चा")
    }

    @Test
    fun accountShowsEntriesWithTheBalanceOnTop() = listOf("hamid ka hisab batao", "hamid ka khata dikhao", "hamid ka account", "hamid", "hamid ka hisaab dikhaoo").forEach {
        val o = ask(it)
        assertTrue("$it -> ${o.card}", o.card.startsWith("Hamid Akram will pay you 30,000 PKR\n"))
        assertTrue(o.card.contains("Kachi eent"))
        assertEquals(listOf("Hamid Akram"), list(o.used("show"), "persons"))
        assertFalse('?' in o.text)
    }

    @Test
    fun twoPeopleInOneQuestion() {
        val o = ask("hamid aur zubair ka hisab")
        assertEquals("Hamid Akram owes you 30,000 PKR. You owe Zubair Latif 3,000 PKR.", o.text)
        says("Nargis Salim Rashid owes you 40,000 PKR.", "hamid aur nargis ka baqi")
    }

    @Test
    fun aNameSharedByThreeIsAsked() {
        val o = ask("salim ka hisab")
        assertTrue(o.text, '?' in o.text && "Salim Khan" in o.text && "Salim Raza" in o.text && "Nargis Salim Rashid" in o.text)
        assertTrue(o.tools.isEmpty())
        says("Salim Khan owes you 500 PKR.", "salim khan ka baqi")
    }

    @Test
    fun moneyGivenToAPerson() {
        says("You gave Hamid Akram 50,000 PKR.", "hamid ko kitne diye", "kitne diye hamid ko", "how much did i give hamid", "hamid ko total kitne paise diye")
        assertEquals(listOf("lent"), list(ask("hamid ko kitne diye").used("totals"), "types"))
    }

    @Test
    fun moneyReceivedFromAPerson() = says("Nargis Salim Rashid paid you 20,000 PKR.", "nargis ne kitne diye", "nargis se kitne mile",
        "how much did nargis pay", "received from nargis kitna")

    @Test
    fun howManyMadeIsTheCountNotTheMoney() = listOf("zubair ne ab tak kitni bharai ki", "kitni bharai hui zubair ki", "zubair ki bharai kitni hai").forEach {
        assertEquals(it, "Zubair Latif's work: 10,000 Bharai, 3,000 PKR.", ask(it).text)
    }

    @Test
    fun aWorkersTotalWork() = listOf("hamid ne total kitna kaam kiya", "how many bricks did hamid make", "hamid ne kitna maal diya").forEach {
        assertEquals(it, "Hamid Akram's work: 20,000 Kachi eent, 20,000 PKR.", ask(it).text)
    }

    @Test
    fun goodsGivenToACustomer() = listOf("nargis ko kitni eent di", "nargis ko kitna maal diya").forEach {
        assertEquals(it, "Sold to Nargis Salim Rashid: 5,000 Pakki eent, 60,000 PKR.", ask(it).text)
    }

    @Test
    fun expShortForExpenseWithAMonthAndYear() = listOf("nov 2026 ka exp batao", "exp nov 2026").forEach {
        val o = ask(it)
        assertTrue(it + ": " + o.card, o.card.startsWith("Spent 565 PKR"))
        assertEquals(it, listOf("expense"), list(o.used("show"), "types"))
        assertTrue(it, list(o.used("show"), "words").isEmpty())
    }

    @Test
    fun thisMonthsSpending() = listOf("is mahine ka kharcha", "kharcha is mahine kitna hua", "this month expenses kitne", "how much did I spend this month",
        "kharcah kitna hua is mahine").forEach {
        val o = ask(it)
        assertEquals(it, "Spent 565 PKR this month.", o.text)
        assertTrue(o.card.startsWith("Spent 565 PKR"))
        assertEquals(listOf("expense"), list(o.used("show"), "types"))
        assertTrue(thisMonth in o.used("show").toString())
    }

    @Test
    fun otherDays() {
        says("Spent 5 PKR yesterday.", "kal kitna kharcha hua")
        says("Spent 200 PKR today.", "aaj kitna kharcha hua", "kitn kharcha hua aaj")
        says("Spent 360 PKR on 12 Nov.", "parson ka kharcha")
        says("Spent 430 PKR last month.", "pichle mahine ka kharcha", "kharcha kitna hua pichle mahine")
        says("Spent 995 PKR this year.", "is saal ka kharcha")
        says("Spent 565 PKR in Nov 2026.", "nov ka kharcha")
        says("Spent 565 PKR since 8 Nov.", "last 7 days expenses", "pichle 7 din ka kharcha")
        says("Spent 565 PKR since 3 Nov.", "3 nov se ab tak ka kharcha")
        says("Spent 565 PKR since 12 Nov.", "pichle teen din ka kharcha")
        says("Spent 360 PKR on 12 Nov.", "do din pehle ka kharcha", "how much did I spend on 12 november", "12/11 ka kharcha")
    }

    @Test
    fun aDayWithNothingSavedIsSaidPlainly() {
        val o = ask("pichle hafte ka kharcha")
        assertEquals("Nothing found for expenses last week.", o.text)
        assertTrue(o.card.isBlank())
        assertNull(o.used("show"))
    }

    @Test
    fun spentOnOneThing() = says("Spent 140 PKR on petrol.", "petrol pe kitna laga", "ptrl kitna laga", "how much on petrol", "petrol", "what did I spend on petrol")

    @Test
    fun categoriesAndOtherSpellings() {
        says("Spent 505 PKR on Food.", "food pe kitna kharcha hua")
        says("Spent 505 PKR on Food this month.", "khane ka kharcha is mahine")
        says("Spent 5 PKR on tea.", "chai pe kitna laga")
        says("Spent 350 PKR on electricity.", "bijli ka bill kitna aaya")
    }

    @Test
    fun aThingNothingIsSavedFor() {
        val o = ask("generator pe kitna laga")
        assertEquals("Nothing found for Generator.", o.text)
        assertTrue(o.tools.isEmpty())
    }

    @Test
    fun income() {
        val o = ask("income kitni hai")
        assertEquals("Income 8,700 PKR.", o.text)
        assertTrue(o.card.startsWith("Income 8,700 PKR"))
        says("Income 8,000 PKR (salary).", "salary kitni aayi")
        says("Income 4,700 PKR this month.", "is mahine ki kamai")
    }

    @Test
    fun monthByMonth() = listOf("mahana kharcha dikhao", "monthly expenses", "har mahine ka kharcha").forEach {
        val o = ask(it)
        assertEquals(it, "by_month", o.used("show")?.optString("view"))
        assertTrue(o.card.startsWith("By month"))
        assertTrue(o.card.contains("Nov 2026 · 4 entries · 565 PKR"))
    }

    @Test
    fun breakdownAndBiggest() {
        assertEquals("breakdown", ask("kahan kahan laga").used("show")?.optString("view"))
        assertEquals("breakdown", ask("sabse zyada kharcha kis pe hua").used("show")?.optString("view"))
        listOf("sab se bara kharcha", "biggest expenses").forEach {
            val o = ask(it)
            assertEquals(it, "highest", o.used("show")?.optString("view"))
            assertTrue(o.card.contains("Electricity · 350 PKR"))
        }
    }

    @Test
    fun countsAndLists() {
        assertEquals("count", ask("kitni entries hain aaj").used("show")?.optString("view"))
        listOf("last 3 entries dikhao", "akhri 3 entries").forEach { assertEquals(it, 3, ask(it).used("show")?.optInt("last")) }
        val big = ask("100 se zyada kharche dikhao")
        assertEquals(100.0, big.used("show")!!.optDouble("min_amount"), 0.0)
        assertEquals("3 entries.", big.text)
        val today = ask("aaj ki entries")
        assertEquals("list", today.used("show")?.optString("view"))
        assertTrue(today.card.contains("Lunch"))
        assertEquals("2 entries for petrol.", ask("kitni dafa petrol dalwaya").text)
    }

    @Test
    fun followUpsKeepTheLastLookUp() {
        val before = """{"types":["expense"],"from":"2026-11-01","to":"2026-11-14"}"""
        listOf("kahan kahan laga", "detail batao", "detail").forEach {
            val show = ask(it, before).used("show")
            assertEquals(it, "breakdown", show?.optString("view"))
            assertTrue(thisMonth in show.toString())
        }
        val food = ask("aur food?", before)
        assertEquals("Spent 505 PKR on Food this month.", food.text)
        assertEquals(listOf("Food"), list(food.used("show"), "categories"))
        listOf("what about yesterday", "kal ka?").forEach { assertEquals(it, "Spent 5 PKR yesterday.", ask(it, before).text) }
        assertEquals("Spent 60 PKR on petrol this month.", ask("sirf petrol", before).text)
        assertEquals("Income 4,700 PKR this month.", ask("aur income kitni", before).text)
        assertEquals("list", ask("list", before).used("show")?.optString("view"))
    }

    @Test
    fun detailAfterATotalShowsTheEntries() {
        val first = ask("is mahine ka kharcha kitna hai")
        val then = ask("kahan kahan laga", first.notes.filter, first.card)
        assertTrue(then.card.isNotBlank())
        assertNotEquals(first.card, then.card)
        assertTrue(then.notes.listIds.isNotEmpty())
    }

    @Test
    fun detailOfAPersonIsTheirEntries() {
        val first = ask("hamid ka baqi kitna hai")
        val then = ask("detail", first.notes.filter)
        assertEquals("list", then.used("show")?.optString("view"))
        assertEquals(listOf("Hamid Akram"), list(then.used("show"), "persons"))
    }

    @Test
    fun whoOwesTheOwner() = listOf("kis kis se paise lene hain", "who owes me", "kis ne paise dene hain", "mujhe kitne lene hain").forEach {
        val o = ask(it)
        assertEquals(it, "5 people owe you 72,600 PKR.", o.text)
        assertTrue(o.card.startsWith("To get:\nNargis Salim Rashid · Customers · 40,000 PKR\nHamid Akram · Staff · 30,000 PKR"))
        assertTrue(o.card.endsWith("Total: To get 72,600 PKR"))
    }

    @Test
    fun whomTheOwnerOwes() = says("You owe 2 people 4,000 PKR.", "kisko paise dene hain", "whom do i owe", "who do I owe money to")

    @Test
    fun bothSidesAndOneAccount() {
        says("People owe you 72,600 PKR; you owe 4,000 PKR.", "udhaar kitna hai")
        says("1 person in Customers owes you 40,000 PKR.", "customers se kitne lene hain")
        says("You owe 1 person in Staff 3,000 PKR.", "staff ko kitne dene hain")
    }

    @Test
    fun profit() = listOf("profit kitna hai", "kitna munafa hua", "profet kitna hua", "منافع کتنا ہے").forEach {
        val o = ask(it)
        assertEquals(it, "Profit 44,705 PKR.", o.text)
        assertTrue(o.card.startsWith("Profit and loss, all time: Profit 44,705 PKR · Sales 60,000 PKR"))
    }

    @Test
    fun profitThisMonthAndNetWorth() {
        val month = ask("is mahine ka profit")
        assertEquals("Profit 41,135 PKR this month.", month.text)
        assertEquals("2026-11-01", month.used("report")?.optString("from"))
        assertTrue(ask("net worth kitni hai").card.startsWith("Net worth: Net worth 44,705 PKR"))
    }

    @Test
    fun whoIsInAnAccount() {
        listOf("staff mein kaun kaun hai", "who is in staff", "staff ke naam batao").forEach {
            val o = ask(it)
            assertTrue("$it -> ${o.text}", o.text.startsWith("Staff (2):") && "Hamid Akram" in o.text && "Zubair Latif" in o.text)
        }
        says("Customers (1): Nargis Salim Rashid.", "kitne customers hain")
        says("Hamid Akram is in Staff.", "hamid kis khate mein hai", "hamid kis khate me hai")
    }

    @Test
    fun pdfOfWhatWasAsked() {
        listOf("hamid ke hisab ka pdf bana do", "make a pdf of hamid's account").forEach {
            pdf = null
            val o = ask(it)
            assertNotNull(it, pdf)
            assertEquals(listOf("Hamid Akram"), list(o.used("make_pdf"), "persons"))
            assertTrue(list(o.used("make_pdf"), "types").isEmpty())
        }
        assertEquals(listOf("expense"), list(ask("is mahine ke kharche ki pdf").used("make_pdf"), "types"))
        val again = ask("pdf bana do", """{"types":["expense"],"from":"2026-11-01","to":"2026-11-14"}""")
        assertTrue(thisMonth in again.used("make_pdf").toString())
    }

    @Test
    fun byPerson() {
        val gave = ask("kis ko kitne diye").used("show")
        assertEquals("by_person", gave?.optString("view"))
        assertEquals(listOf("lent"), list(gave, "types"))
        assertEquals(listOf("received_back"), list(ask("kis kis ne paise diye").used("show"), "types"))
    }

    @Test
    fun cashInHand() = listOf("cash kitna hai", "mere paas kitne paise hain", "kitne paise bache hain").forEach { assertTrue(it, ask(it).text.startsWith("Cash in hand: ")) }

    @Test
    fun ratesAsSaved() {
        assertEquals("Hamid Akram's rate: Kachi eent 1,000 PKR per 1000.", ask("hamid ka rate kya hai").text)
        assertEquals("Bharai: 300 PKR per 1000 (Zubair Latif).", ask("bharai ka rate").text)
    }

    @Test
    fun whenWasThatAmount() {
        val o = ask("1000 wali entry kab ki thi")
        assertEquals(1000.0, o.used("show")!!.optDouble("max_amount"), 0.0)
        assertTrue(o.card.contains("Bilal Tariq"))
        assertTrue(ask("nargis se 20000 kab mile the").card.startsWith("11 Nov · Loans · Nargis Salim Rashid · 20,000 PKR"))
        assertTrue(ask("900 kis ko diye the").card.startsWith("7 Nov · Loans · Salim Raza · 900 PKR"))
    }

    @Test
    fun theWholeAccountAndWhoGaveMoney() {
        val all = ask("hisab batao").used("show")
        assertEquals("breakdown", all?.optString("view"))
        assertTrue(list(all, "types").isEmpty())
        val who = ask("who gave me money").used("show")
        assertEquals("by_person", who?.optString("view"))
        assertEquals(listOf("received_back"), list(who, "types"))
    }

    @Test
    fun newEntriesAndChangesAreNotQuestions() = listOf(
        "petrol 80", "hamid ko 500 diye", "500 hamid ko diye", "diye hamid ko 500", "hamid 500 diye", "hmd 500", "ptrl 60", "gave hamid 500",
        "hamid ko 500 given", "received 2000 from nargis", "chai 5 aur nashta 10", "kal 200 ka grocery liya", "hamid ne aaj kaam kiya",
        "Hamid made 7000 raw bricks today", "ye delete kar do", "nahi 400 tha", "ye kal ka tha", "akbar ko staff mein daal do",
        "sari entries delete kar do", "haan kar do", "wapas le aao", "salim raza ka naam salim raza butt kar do", "diesel ka naya expense account banao",
        "kachi eent molding ko kachi eent mein kar do", "naya staff rashid hai, usne 5000 kachi eent dali 1100 ke hisab se",
        "nargis ko 2000 pakki eent 13000 ke hisaab se di", "حامد کو پانچ سو دیے", "नरगिस ने दो हज़ार दिए", "chai pi", "petrol dalwaya", "salary aayi"
    ).forEach {
        val o = ask(it)
        assertNull("$it -> ${o.text}", o.reply)
        assertTrue(it, o.tools.isEmpty())
    }
}
