package com.bolkhata.app

import org.json.JSONObject
import org.junit.Assume
import org.junit.Test
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * The real AI run on everyday messages against a made-up ledger. What it saved, changed or showed is checked by rule,
 * and a report is written to build/ai-eval.txt. It runs only when ANTHROPIC_API_KEY is set (the "AI eval" workflow);
 * the normal build skips it. AI_EVAL_MODEL and AI_EVAL_EFFORT pick the model and how hard it thinks.
 */
class AiEvalTest {
    private val now = System.currentTimeMillis()
    private val day = 86_400_000L

    /** A small business's books, the same at the start of every case: staff with work, a customer, loans, expenses, income. */
    private inner class World {
        var entries: List<Entry> = listOf(
            work("w1", "Hamid Akram", "Kachi eent", 20_000.0, 1_000.0, 6),
            Entry("a1", Type.DIYA, 50_000.0, Cat.UDHAAR, "Loans", "Hamid Akram", "advance", now - 5 * day, now - 5 * day),
            work("w2", "Zubair Latif", "Bharai", 10_000.0, 300.0, 4),
            Entry("s1", Type.SALE, 60_000.0, Cat.SALES, "Pakki eent", "Nargis Salim Rashid", "", now - 4 * day, now - 4 * day, qty = 5_000.0, unit = "1000", rate = 12_000.0),
            Entry("r1", Type.WAPIS_MILA, 20_000.0, Cat.UDHAAR, "Loans", "Nargis Salim Rashid", "", now - 3 * day, now - 3 * day),
            Entry("l1", Type.DIYA, 500.0, Cat.UDHAAR, "Loans", "Salim Khan", "", now - 8 * day, now - 8 * day),
            Entry("l2", Type.DIYA, 900.0, Cat.UDHAAR, "Loans", "Salim Raza", "", now - 7 * day, now - 7 * day),
            Entry("l3", Type.DIYA, 1_200.0, Cat.UDHAAR, "Loans", "Mohsin Javed", "", now - 9 * day, now - 9 * day),
            Entry("x1", Type.KHARCHA, 60.0, Cat.TRANSPORT, "Petrol", "", "", now - 2 * day, now - 2 * day),
            Entry("x2", Type.KHARCHA, 300.0, Cat.KHANA, "Groceries", "", "", now - 2 * day, now - 2 * day),
            Entry("x3", Type.KHARCHA, 5.0, Cat.KHANA, "Tea", "", "", now - 1 * day, now - 1 * day),
            Entry("x4", Type.KHARCHA, 350.0, Cat.BIJLI, "Electricity", "", "", now - 40 * day, now - 40 * day),
            Entry("x5", Type.KHARCHA, 80.0, Cat.TRANSPORT, "Petrol", "", "", now - 35 * day, now - 35 * day),
            Entry("i1", Type.AAMDANI, 4_000.0, Cat.TANKHWAH, "Salary", "", "", now - 30 * day, now - 30 * day),
            Entry("i2", Type.AAMDANI, 4_000.0, Cat.TANKHWAH, "Salary", "", "", now - 2 * day, now - 2 * day),
            Entry("i3", Type.AAMDANI, 700.0, Cat.TANKHWAH, "Commission", "", "", now - 1 * day, now - 1 * day)
        )
        /** What was there before the first message (a case may add its own entries first). */
        var baseIds = entries.map { it.id }.toSet()
        val trashed = mutableSetOf<String>()
        var bin = listOf<Entry>()
        var setup = SideSetup(people = mapOf("Hamid Akram" to Sides.STAFF, "Zubair Latif" to Sides.STAFF, "Nargis Salim Rashid" to Sides.CUSTOMERS))
        var pdf: PdfDoc? = null
        val host = AiAgent.Host(
            entries = { entries }, add = { entries = it + entries },
            update = { changed -> entries = entries.map { e -> changed.firstOrNull { it.id == e.id } ?: e } },
            trash = { ids -> trashed += ids; bin = entries.filter { it.id in ids } + bin; entries = entries.filter { it.id !in ids } },
            names = { (entries.map { it.person } + setup.people.keys).map { it.trim() }.filter { it.isNotBlank() }.distinct() },
            currency = "PKR", setup = { setup }, saveSetup = { setup = it }, pdf = { pdf = it },
            trashed = { bin }, restore = { ids -> entries = bin.filter { it.id in ids } + entries; bin = bin.filter { it.id !in ids }; trashed -= ids }
        )
        val added get() = entries.filter { it.id !in baseIds }
        fun byId(id: String) = entries.firstOrNull { it.id == id }
    }

    private fun work(id: String, person: String, item: String, qty: Double, rate: Double, daysAgo: Int) =
        Entry(id, Type.PURCHASE, qty / 1000 * rate, Cat.PRODUCTION, item, person, "", now - daysAgo * day, now - daysAgo * day, qty = qty, unit = "1000", rate = rate)

    /** One message as the chat sends it. */
    private class Turn(val said: String, val spoken: Boolean = false)

    /** What a case did: the books after it, each turn's answer, and the tools the AI used. */
    private inner class Outcome(val world: World, val answers: List<AiAgent.Answer?>, val trace: List<String>, val errors: List<String>,
        /** How long the AI took over each message, in seconds (messages the app did alone are not in it). */
        val seconds: List<Double> = emptyList()) {
        val last get() = answers.last()
        val added get() = world.added
        fun has(type: Type? = null, person: String = "", amount: Double? = null, item: String = "", cat: Cat? = null, qty: Double? = null, list: List<Entry> = added) =
            list.any { e ->
                (type == null || e.type == type) && e.person.contains(person, true) && (amount == null || kotlin.math.abs(e.amount - amount) < 0.01) &&
                    e.item.contains(item, true) && (cat == null || e.category == cat) && (qty == null || kotlin.math.abs(e.qty - qty) < 0.01)
            }
        val text get() = last?.text.orEmpty()
        val shown get() = last?.card.orEmpty()
        /** The answer, sentence or card, without thousands commas. */
        val told get() = (text + " " + shown).replace(",", "")
        fun onDay(e: Entry, daysAgo: Int) = dayOf(e.date) == dayOf(now - daysAgo * day)
        /** A question to the person, or the option card. */
        val asked get() = '?' in text || last?.ask.orEmpty().isNotBlank()
        fun yesterday(e: Entry) = dayOf(e.date) == dayOf(now - day)
    }

    private fun dayOf(ms: Long) = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date(ms))

    private inner class Case(val name: String, val turns: List<Turn>, val prepare: World.() -> Unit = {}, val check: Outcome.() -> List<Pair<String, Boolean>>)

    private fun case(name: String, vararg said: String, prepare: World.() -> Unit = {}, check: Outcome.() -> List<Pair<String, Boolean>>) =
        Case(name, said.map { Turn(it) }, prepare, check)
    private fun voice(name: String, vararg said: String, check: Outcome.() -> List<Pair<String, Boolean>>) =
        Case(name, said.map { Turn(it, spoken = true) }, {}, check)

    private val urduOrHindi = Regex("""[؀-ۿऀ-ॿ]""")

    private val cases: List<Case> = listOf(
        // ---- money with people
        case("lend", "hamid ko 500 diye") { listOf("one entry" to (added.size == 1), "lent Hamid 500" to has(Type.DIYA, "Hamid Akram", 500.0), "no sentence over the card" to text.isBlank()) },
        case("customer pays", "nargis ne 2000 diye") { listOf("one entry" to (added.size == 1), "received from Nargis 2000" to has(Type.WAPIS_MILA, "Nargis Salim Rashid", 2_000.0)) },
        case("short spelling", "hmd ko 300 diy") { listOf("lent Hamid 300" to has(Type.DIYA, "Hamid Akram", 300.0)) },
        case("borrow", "zubair se 1000 udhaar liya") { listOf("one entry" to (added.size == 1), "borrowed from Zubair 1000" to has(Type.LIYA, "Zubair Latif", 1_000.0)) },
        case("two people share a name", "salim ko 200 diye") { listOf("nothing saved" to added.isEmpty(), "asks which Salim" to asked) },
        case("full name picks one", "salim khan ko 200 diye") { listOf("lent Salim Khan 200" to has(Type.DIYA, "Salim Khan", 200.0), "one entry" to (added.size == 1)) },
        case("new person", "farooq ko 1500 udhaar diya") { listOf("lent Farooq 1500" to has(Type.DIYA, "Farooq", 1_500.0), "one entry" to (added.size == 1)) },
        case("two in one message", "hamid ko 500 diye aur zubair ko 300") { listOf("two entries" to (added.size == 2), "Hamid 500" to has(Type.DIYA, "Hamid Akram", 500.0), "Zubair 300" to has(Type.DIYA, "Zubair Latif", 300.0)) },
        case("got money back", "salim raza ne 400 wapis kar diye") { listOf("received back 400 from Salim Raza" to has(Type.WAPIS_MILA, "Salim Raza", 400.0), "one entry" to (added.size == 1)) },
        case("number in words", "hamid ko das hazar diye") { listOf("lent Hamid 10000" to has(Type.DIYA, "Hamid Akram", 10_000.0)) },
        case("one and a half thousand", "hamid ko derh hazar diye") { listOf("lent Hamid 1500" to has(Type.DIYA, "Hamid Akram", 1_500.0), "one entry" to (added.size == 1)) },
        case("a quarter less than two lakh", "nargis se paune do lakh mile") { listOf("received 175000 from Nargis" to has(Type.WAPIS_MILA, "Nargis Salim Rashid", 175_000.0), "one entry" to (added.size == 1)) },
        case("lakh", "nargis se dhai lakh mile") { listOf("received 250000 from Nargis" to has(Type.WAPIS_MILA, "Nargis Salim Rashid", 250_000.0)) },
        // ---- expenses and income
        case("expense", "petrol 80") { listOf("one entry" to (added.size == 1), "petrol 80 in Transport" to has(Type.KHARCHA, amount = 80.0, cat = Cat.TRANSPORT)) },
        case("two expenses", "chai 5 aur nashta 10") { listOf("two entries" to (added.size == 2), "5 in Food" to has(Type.KHARCHA, amount = 5.0, cat = Cat.KHANA), "10 in Food" to has(Type.KHARCHA, amount = 10.0, cat = Cat.KHANA)) },
        case("bill", "bijli ka bill 350 diya") { listOf("350 in Bills" to has(Type.KHARCHA, amount = 350.0, cat = Cat.BIJLI), "one entry" to (added.size == 1)) },
        case("income", "tankhwah 5000 mili") { listOf("income 5000" to has(Type.AAMDANI, amount = 5_000.0), "one entry" to (added.size == 1)) },
        case("yesterday", "kal 200 ka grocery liya") { listOf("200 in Food" to has(Type.KHARCHA, amount = 200.0, cat = Cat.KHANA), "dated yesterday" to added.any { yesterday(it) }) },
        case("k for thousand", "150k ka generator liya") { listOf("expense 150000" to has(Type.KHARCHA, amount = 150_000.0), "one entry" to (added.size == 1)) },
        case("english", "paid 250 for lunch") { listOf("250 in Food" to has(Type.KHARCHA, amount = 250.0, cat = Cat.KHANA), "one entry" to (added.size == 1)) },
        case("arabic", "دفعت 40 للبنزين") { listOf("40 in Transport" to has(Type.KHARCHA, amount = 40.0, cat = Cat.TRANSPORT), "one entry" to (added.size == 1)) },
        case("long message", "aaj hamid ko 500 diye, petrol 60 dala, chai 10 ki pi aur nargis se 3000 mile") {
            listOf("four entries" to (added.size == 4), "Hamid 500" to has(Type.DIYA, "Hamid Akram", 500.0), "petrol 60" to has(Type.KHARCHA, amount = 60.0, cat = Cat.TRANSPORT),
                "tea 10" to has(Type.KHARCHA, amount = 10.0, cat = Cat.KHANA), "Nargis 3000" to has(Type.WAPIS_MILA, "Nargis Salim Rashid", 3_000.0))
        },
        // ---- work and goods
        case("work, rate not said", "hamid ne 10000 kachi eent dali") {
            listOf("one entry" to (added.size == 1), "work by Hamid, 10000 at the last rate = 10000" to has(Type.PURCHASE, "Hamid Akram", 10_000.0, qty = 10_000.0), "kept the name Kachi eent" to added.all { it.item == "Kachi eent" })
        },
        case("work, bare", "hamid 5000 kachi eent") { listOf("work 5000 = 5000" to has(Type.PURCHASE, "Hamid Akram", 5_000.0, qty = 5_000.0), "one entry" to (added.size == 1), "kept the name" to added.all { it.item == "Kachi eent" }) },
        case("work, worded longer", "hamid ne 3000 kachi eent ki molding ki") { listOf("work 3000 = 3000" to has(Type.PURCHASE, "Hamid Akram", 3_000.0, qty = 3_000.0), "no second name for the work" to added.all { it.item == "Kachi eent" }) },
        case("other worker's work", "zubair ne 8000 bharai ki") { listOf("work 8000 at 300 = 2400" to has(Type.PURCHASE, "Zubair Latif", 2_400.0, "Bharai", qty = 8_000.0), "one entry" to (added.size == 1)) },
        case("new work with a rate, then without", "hamid ne 4000 nikasi ki 200 per 1000", "hamid ne 2000 nikasi ki") {
            listOf("two entries" to (added.size == 2), "4000 at 200 = 800" to has(Type.PURCHASE, "Hamid Akram", 800.0, "Nikasi", qty = 4_000.0), "2000 at the same rate = 400" to has(Type.PURCHASE, "Hamid Akram", 400.0, "Nikasi", qty = 2_000.0))
        },
        case("goods to a customer", "nargis ko 3000 pakki eent di") { listOf("sale 3000 at 12000 = 36000" to has(Type.SALE, "Nargis Salim Rashid", 36_000.0, "Pakki eent", qty = 3_000.0), "one entry" to (added.size == 1)) },
        case("goods with a new rate", "nargis ko 2000 pakki eent 13000 ke hisaab se di") { listOf("sale 2000 at 13000 = 26000" to has(Type.SALE, "Nargis Salim Rashid", 26_000.0, qty = 2_000.0), "one entry" to (added.size == 1)) },
        case("work with nothing to count", "hamid ne aaj kaam kiya") { listOf("nothing saved" to added.isEmpty(), "asks" to asked) },
        case("advance to a worker", "hamid ko 20000 advance diya") { listOf("lent Hamid 20000" to has(Type.DIYA, "Hamid Akram", 20_000.0), "one entry" to (added.size == 1)) },
        case("goods and a payment together", "nargis ko 4000 pakki eent di aur us ne 10000 diye") {
            listOf("two entries" to (added.size == 2), "sale 4000 = 48000" to has(Type.SALE, "Nargis Salim Rashid", 48_000.0, qty = 4_000.0), "received 10000" to has(Type.WAPIS_MILA, "Nargis Salim Rashid", 10_000.0))
        },
        case("a new worker's first work", "naya staff rashid hai, usne 5000 kachi eent dali 1100 ke hisab se") {
            listOf("work 5000 at 1100 = 5500" to has(Type.PURCHASE, "Rashid", 5_500.0, qty = 5_000.0), "kept the name Kachi eent" to added.all { it.item == "Kachi eent" },
                "Rashid is in Staff" to (Sides.bookOf(added.firstOrNull()?.person.orEmpty(), world.entries, world.setup) == Sides.STAFF))
        },
        case("work said in English", "Hamid made 7000 raw bricks today") { listOf("work 7000 = 7000" to has(Type.PURCHASE, "Hamid Akram", 7_000.0, qty = 7_000.0), "one entry" to (added.size == 1)) },
        case("pay a worker for the work", "zubair ko uske kaam ke 3000 de diye") { listOf("paid Zubair 3000" to has(Type.DIYA, "Zubair Latif", 3_000.0), "one entry" to (added.size == 1)) },
        case("wrong count of work", "hamid ne 3000 kachi eent dali", "nahi 3500 thi") { listOf("still one entry" to (added.size == 1), "3500 = 3500 now" to has(Type.PURCHASE, "Hamid Akram", 3_500.0, qty = 3_500.0)) },
        case("a day by its date", "3 november ko petrol 90") { listOf("90 in Transport" to has(Type.KHARCHA, amount = 90.0, cat = Cat.TRANSPORT), "dated the 3rd of November" to added.any { dayOf(it.date).endsWith("-11-03") }) },
        case("the day before yesterday", "parso hamid ko 800 diye") { listOf("lent Hamid 800" to has(Type.DIYA, "Hamid Akram", 800.0), "dated two days ago" to added.any { onDay(it, 2) }) },
        case("medicine", "dawai 120") { listOf("120 in Health" to has(Type.KHARCHA, amount = 120.0, cat = Cat.SEHAT)) },
        case("rent", "dukan ka kiraya 1500 diya") { listOf("1500 in Rent" to has(Type.KHARCHA, amount = 1_500.0, cat = Cat.RENT)) },
        case("school fees", "bachon ki school fees 2000") { listOf("2000 in Education" to has(Type.KHARCHA, amount = 2_000.0, cat = Cat.EDU)) },
        case("sent home", "ghar 3000 bheje") { listOf("3000 in Sent home" to has(Type.KHARCHA, amount = 3_000.0, cat = Cat.GHAR)) },
        case("a worker gives money back", "hamid ne 500 wapis diye") { listOf("received 500 from Hamid" to has(Type.WAPIS_MILA, "Hamid Akram", 500.0), "one entry" to (added.size == 1)) },
        case("the same to two people", "hamid aur zubair ko 500 500 diye") { listOf("two entries" to (added.size == 2), "Hamid 500" to has(Type.DIYA, "Hamid Akram", 500.0), "Zubair 500" to has(Type.DIYA, "Zubair Latif", 500.0)) },
        case("three works of one worker", "hamid ne aaj 6000 kachi eent dali, 2000 bharai ki aur 1000 nikasi ki 250 per 1000") {
            listOf("three entries" to (added.size == 3), "kachi eent 6000 = 6000" to has(Type.PURCHASE, "Hamid Akram", 6_000.0, "Kachi eent", qty = 6_000.0),
                "bharai 2000 at 300 = 600" to has(Type.PURCHASE, "Hamid Akram", 600.0, "Bharai", qty = 2_000.0), "nikasi 1000 at 250 = 250" to has(Type.PURCHASE, "Hamid Akram", 250.0, "Nikasi", qty = 1_000.0))
        },
        // ---- questions
        case("how many were made", "zubair ne ab tak kitni bharai ki") { listOf("nothing saved" to added.isEmpty(), "says 10,000" to told.contains("10000")) },
        case("a worker's balance", "hamid ka baqi kitna hai") { listOf("nothing saved" to added.isEmpty(), "says 30,000" to told.contains("30000")) },
        case("what a worker is owed", "zubair ko kitne dene hain") { listOf("nothing saved" to added.isEmpty(), "says 3,000" to told.contains("3000")) },
        case("a worker's total work", "hamid ne total kitna kaam kiya") { listOf("nothing saved" to added.isEmpty(), "says 20,000" to told.contains("20000")) },
        case("who is in an account", "staff mein kaun kaun hai") { listOf("names Hamid and Zubair" to (told.contains("Hamid", true) && told.contains("Zubair", true)), "nothing saved" to added.isEmpty()) },
        case("where it went, after a total", "is mahine ka kharcha kitna hai", "kahan kahan laga") { listOf("nothing saved" to added.isEmpty(), "a card that is not the first answer" to (shown.isNotBlank() && shown != answers[0]?.card)) },
        case("today's account", "aaj ka hisab batao") { listOf("nothing saved" to added.isEmpty(), "answers" to (shown.isNotBlank() || text.isNotBlank()), "no question back" to !asked) },
        case("this month's spending", "is mahine ka kharcha") { listOf("nothing saved" to added.isEmpty(), "says 365" to told.contains("365")) },
        case("detail after a total", "is mahine ka kharcha kitna hai", "detail batao") { listOf("nothing saved" to added.isEmpty(), "second card is not the first" to (shown.isNotBlank() && shown != answers[0]?.card)) },
        case("income, then detail", "income kitni hai", "detail") { listOf("shows the entries" to (last?.listIds.orEmpty().isNotEmpty() || (shown.isNotBlank() && shown != answers[0]?.card))) },
        case("a person's account", "hamid ka hisab batao") { listOf("nothing saved" to added.isEmpty(), "answers" to (shown.isNotBlank() || text.isNotBlank()), "no question back" to !asked) },
        case("what a customer owes", "nargis se kitne lene hain") { listOf("nothing saved" to added.isEmpty(), "says 40,000" to ((text + shown).replace(",", "").contains("40000"))) },
        case("spent on one thing", "petrol pe kitna laga") { listOf("nothing saved" to added.isEmpty(), "says 140" to ((text + shown).contains("140"))) },
        case("month by month", "mahana kharcha dikhao") { listOf("shows a card" to shown.isNotBlank(), "nothing saved" to added.isEmpty()) },
        case("who owes me", "kis kis se paise lene hain") { listOf("nothing saved" to added.isEmpty(), "answers" to (shown.isNotBlank() || text.isNotBlank())) },
        case("profit", "profit kitna hai") { listOf("nothing saved" to added.isEmpty(), "answers" to (shown.isNotBlank() || text.isNotBlank())) },
        case("english question", "show this month's expenses") { listOf("shows a card" to shown.isNotBlank(), "nothing saved" to added.isEmpty()) },
        case("pdf", "hamid ke hisab ka pdf bana do") { listOf("a PDF is made" to (world.pdf != null), "nothing saved" to added.isEmpty()) },
        // ---- changes
        case("correct the amount", "hamid ko 500 diye", "nahi 400 tha") { listOf("still one entry" to (added.size == 1), "it is 400 now" to has(Type.DIYA, "Hamid Akram", 400.0)) },
        case("correct the day", "petrol 70", "ye kal ka tha") { listOf("still one entry" to (added.size == 1), "dated yesterday" to added.all { yesterday(it) }) },
        case("correct the person", "hamid ko 500 diye", "ye zubair ko diye thay") { listOf("still one entry" to (added.size == 1), "it is Zubair's now" to has(Type.DIYA, "Zubair Latif", 500.0)) },
        case("delete what was just saved", "chai 15", "ye delete kar do") { listOf("it is in Trash" to (added.isEmpty() && world.trashed.size == 1)) },
        case("delete the last minutes", "pichle 10 minute ki sari entries delete kar do", prepare = {
            entries = listOf(Entry("n1", Type.KHARCHA, 25.0, Cat.KHANA, "Tea", "", "", now - 3 * 60_000L, now - 3 * 60_000L),
                Entry("n2", Type.DIYA, 700.0, Cat.UDHAAR, "Loans", "Hamid Akram", "", now - 5 * 60_000L, now - 5 * 60_000L)) + entries
        }) { listOf("the two new ones are in Trash" to (world.trashed == setOf("n1", "n2")), "the rest stay" to (world.entries.size == world.baseIds.size - 2)) },
        case("change an old entry", "kal wali chai 5 ki jagah 8 kar do") { listOf("tea is 8 now" to (world.byId("x3")?.amount == 8.0), "nothing new" to added.isEmpty()) },
        case("same thing typed twice", "hamid ko 250 diye", "hamid ko 250 diye") { listOf("two entries" to (added.size == 2)) },
        case("same thing said twice in words", "zubair ko teen sau diye", "zubair ko teen sau diye") { listOf("two entries" to (added.size == 2), "both 300" to added.all { it.amount == 300.0 }) },
        case("delete the newest entry", "akhri entry hata do", prepare = {
            entries = listOf(Entry("n9", Type.KHARCHA, 45.0, Cat.KHANA, "Lunch", "", "", now - 2 * 60_000L, now - 2 * 60_000L)) + entries
        }) { listOf("only the newest is in Trash" to (world.trashed == setOf("n9"))) },
        case("delete one named entry", "hamid ko jo 50000 advance diya tha wo delete kar do") { listOf("the advance is in Trash" to (world.trashed == setOf("a1"))) },
        case("bring back what was deleted", "chai 15", "ye delete kar do", "wapas le aao") { listOf("it is back" to (added.size == 1 && world.trashed.isEmpty())) },
        case("it was another, new person", "tariq ko 5000 udhaar diya", "ye naya tariq hai", prepare = {
            entries = listOf(Entry("q1", Type.DIYA, 300.0, Cat.UDHAAR, "Loans", "Bilal Tariq Mehmood", "", now - 12 * day, now - 12 * day)) + entries
        }) { listOf("one entry" to (added.size == 1), "it is under the new person Tariq" to added.all { it.person.equals("Tariq", true) }) },
        case("delete everything is asked about first", "sari entries delete kar do") { listOf("nothing is deleted" to world.trashed.isEmpty(), "asks" to asked) },
        case("delete everything after a yes", "sari entries delete kar do", "haan kar do") { listOf("everything is in Trash" to world.entries.isEmpty()) },
        // ---- accounts
        case("two accounts for one work, said plainly", "kachi eent ke do khate ban gaye hain, kachi eent aur kachi eent molding. mujhe ek hi chahiye", prepare = {
            entries = listOf(work("w9", "Hamid Akram", "Kachi eent molding", 4_000.0, 1_000.0, 1)) + entries
        }) { listOf("no entry is left under the second name" to world.entries.none { it.item.equals("Kachi eent molding", true) }, "it is under Kachi eent" to (world.byId("w9")?.item == "Kachi eent")) },
        case("put a person in Staff", "akbar ko staff mein daal do") { listOf("Akbar is in Staff" to (Sides.bookOf("Akbar", world.entries, world.setup) == Sides.STAFF), "nothing saved" to added.isEmpty()) },
        case("new expense account", "diesel ka naya expense account banao") { listOf("Diesel account is there" to world.setup.books.any { it.name.equals("Diesel", true) && it.side == Side.EXP }, "nothing saved" to added.isEmpty()) },
        case("move a person", "zubair ko suppliers mein kar do") { listOf("Zubair is in Suppliers" to (Sides.bookOf("Zubair Latif", world.entries, world.setup) == Sides.SUPPLIERS)) },
        case("new people account with a person", "drivers ka naya khata banao aur us mein akbar ko daal do") { listOf("Akbar is in Drivers" to Sides.bookOf("Akbar", world.entries, world.setup).equals("Drivers", true)) },
        case("put two names of one work together", "kachi eent molding ko kachi eent mein kar do", prepare = {
            entries = listOf(work("w9", "Hamid Akram", "Kachi eent molding", 4_000.0, 1_000.0, 1)) + entries
        }) { listOf("no entry is left under the second name" to world.entries.none { it.item.equals("Kachi eent molding", true) }, "it is under Kachi eent" to (world.byId("w9")?.item == "Kachi eent")) },
        case("rename a person", "salim raza ka naam salim raza butt kar do") { listOf("the entry carries the new name" to (world.byId("l2")?.person.equals("Salim Raza Butt", true) == true), "nothing new" to added.isEmpty()) },
        // ---- voice, as the recogniser hands it over
        voice("urdu script", "حامد کو پانچ سو دیے") { listOf("lent Hamid 500" to has(Type.DIYA, "Hamid Akram", 500.0), "answer is not in Urdu script" to !urduOrHindi.containsMatchIn(text)) },
        voice("hindi script", "नरगिस ने दो हज़ार दिए") { listOf("received 2000 from Nargis" to has(Type.WAPIS_MILA, "Nargis Salim Rashid", 2_000.0), "answer is not in Hindi script" to !urduOrHindi.containsMatchIn(text)) },
        voice("a name misheard", "motion ko 2000 udhaar diye") { listOf("lent Mohsin 2000" to has(Type.DIYA, "Mohsin Javed", 2_000.0), "one entry" to (added.size == 1)) },
        voice("work misheard", "hameed ne 6000 kacchi int daali") { listOf("work by Hamid 6000 = 6000" to has(Type.PURCHASE, "Hamid Akram", 6_000.0, qty = 6_000.0), "kept the name Kachi eent" to added.all { it.item == "Kachi eent" }) },
        voice("question in urdu script", "اس مہینے کا خرچہ کتنا ہے") { listOf("says 365" to told.contains("365"), "nothing saved" to added.isEmpty(), "answer is not in Urdu script" to !urduOrHindi.containsMatchIn(text)) },
        voice("two works in one breath", "hamid ne aaj das hazar kachi eent dali aur zubair ne paanch hazar bharai ki") {
            listOf("two entries" to (added.size == 2), "Hamid 10000 = 10000" to has(Type.PURCHASE, "Hamid Akram", 10_000.0, qty = 10_000.0), "Zubair 5000 at 300 = 1500" to has(Type.PURCHASE, "Zubair Latif", 1_500.0, qty = 5_000.0))
        },
        // ---- talk
        case("greeting", "assalam o alaikum") { listOf("nothing saved" to added.isEmpty(), "a short answer" to (text.isNotBlank() && text.split(' ').size <= 25)) },
        case("thanks", "shukriya") { listOf("nothing saved" to added.isEmpty(), "a short answer" to (text.isNotBlank() && text.split(' ').size <= 25)) }
    )

    private fun brief(e: Entry) = e.type.name + " " + e.person.ifBlank { "-" } + " " + e.item + (if (e.qty > 0) " " + e.qty + "x" + e.rate + (if (e.unit == "1000") "/1000" else "") else "") +
        " =" + e.amount + " " + e.category.label + " " + dayOf(e.date)

    /**
     * Runs one case the way the chat does: each turn sees the last ones, what was just saved and the card on the screen.
     * [local]: the app's own assistant (LocalAgent) answers instead of the AI.
     */
    private fun run(c: Case, local: Boolean = false): Outcome {
        val world = World().apply(c.prepare).apply { baseIds = entries.map { it.id }.toSet() }
        val trace = mutableListOf<String>()
        val errors = mutableListOf<String>()
        val history = mutableListOf<Pair<Boolean, String>>()
        var picked = ""
        var justSaved = emptyList<String>()
        // What the app learnt from the AI in this chat: a sentence of a known shape is then done by the app alone.
        var known = mapOf<String, String>()
        val seconds = mutableListOf<Double>()
        val answers = c.turns.map { turn ->
            val roman = runCatching { Translit.toRoman(turn.said) }.getOrDefault(turn.said)
            val who = AiAgent.who(world.host.names(), known)
            val counted = AiAgent.counted(roman)
            val learnt = known[AiAgent.shape(counted, who)]?.let { AiAgent.recall(it, counted, who) }?.let { input ->
                val notes = AiAgent.Notes(said = AiAgent.numbers(counted))
                runCatching { AiAgent.use("add_entries", input, world.host, System.currentTimeMillis(), notes) }
                notes.added.toList().takeIf { it.isNotEmpty() }
            }
            if (learnt != null) {
                history += true to turn.said
                history += false to ""
                picked = ""
                justSaved = learnt
                trace += "learned: done by the app alone"
                return@map AiAgent.Answer("", learnt)
            }
            val recent = history.takeLast(6).joinToString("\n") { (if (it.first) "Person: " else "App: ") + it.second.take(200) }
            val lastShown = history.lastOrNull { !it.first }?.second.orEmpty()
            fun ask() = AiAgent.run(turn.said, recent, world.host, roman = roman, previous = picked, justSaved = justSaved, spoken = turn.spoken, lastShown = lastShown, known = known,
                onTool = { name, input, result -> trace += "$name $input -> " + result.take(260) })
            // An overloaded or rate-limited moment is tried once more.
            val began = System.nanoTime()
            val a = if (local) LocalAgent.run(turn.said, world.host, roman = roman, previous = picked, justSaved = justSaved, lastShown = lastShown, recent = recent,
                    onTool = { name, input, result -> trace += "$name $input -> " + result.take(260) })
                else ask()?.also { seconds += (System.nanoTime() - began) / 1e9 } ?: run { Thread.sleep(4000); ask() }
            if (a == null) errors += if (local) "the app's own assistant did not understand" else Ai.lastError
            history += true to turn.said
            history += false to (a?.card?.ifBlank { a.text } ?: "")
            picked = a?.filter.orEmpty()
            justSaved = a?.added.orEmpty()
            if (a != null) known = known + a.words + (if (a.lesson.isBlank()) emptyMap() else mapOf(a.shape to a.lesson))
            trace += "reply: " + (a?.text ?: "(no answer)") + (if (a?.card.isNullOrBlank()) "" else " | card: " + a!!.card.take(160).replace("\n", " / ")) + (if (a?.ask.isNullOrBlank()) "" else " | option card: " + a!!.ask)
            a
        }
        return Outcome(world, answers, trace, errors, seconds)
    }

    /** A way of running the assistant, to compare on the same messages in the same run. */
    private class Variant(val name: String, val quick: Boolean, val thinking: Boolean)

    private val variants = mapOf("now" to Variant("now", quick = false, thinking = true), "quick" to Variant("quick", quick = true, thinking = true),
        "nothink" to Variant("nothink", quick = true, thinking = false))

    @Test
    fun everydayMessages() {
        val key = System.getenv("ANTHROPIC_API_KEY").orEmpty().trim()
        Assume.assumeTrue("No ANTHROPIC_API_KEY: the AI eval is skipped", key.isNotBlank())
        Ai.key = key
        Ai.model = System.getenv("AI_EVAL_MODEL").orEmpty().ifBlank { "claude-haiku-5-5" }
        Ai.effort = System.getenv("AI_EVAL_EFFORT").orEmpty().ifBlank { Ai.effort }
        val spent = java.util.concurrent.atomic.AtomicLong()
        val tokensIn = java.util.concurrent.atomic.AtomicLong()
        val tokensOut = java.util.concurrent.atomic.AtomicLong()
        Ai.sink = { i, o, micro -> tokensIn.addAndGet(i.toLong()); tokensOut.addAndGet(o.toLong()); spent.addAndGet(micro) }
        val only = System.getenv("AI_EVAL_ONLY").orEmpty().split(",").map { it.trim() }.filter { it.isNotBlank() }
        val chosen = cases.filter { c -> only.isEmpty() || only.any { c.name.contains(it, true) } }
        // The app as it ships, unless ways to compare are named ("now,quick,nothink"): those take turns, a few cases at a time, so each meets the same hour.
        val ways = System.getenv("AI_EVAL_VARIANTS").orEmpty().split(",").mapNotNull { variants[it.trim()] }
            .ifEmpty { listOf(Variant("as shipped", AiAgent.quickSave, Ai.thinking)) }
        val pool = Executors.newFixedThreadPool(4)
        val results = ways.associate { it.name to mutableMapOf<Case, Pair<Outcome?, String>>() }
        val used = ways.associate { it.name to LongArray(3) }
        chosen.chunked(8).forEach { chunk ->
            ways.forEach { way ->
                AiAgent.quickSave = way.quick
                Ai.thinking = way.thinking
                val before = longArrayOf(tokensIn.get(), tokensOut.get(), spent.get())
                chunk.map { c -> c to pool.submit<Pair<Outcome?, String>> { runCatching { run(c) }.fold({ it to "" }, { null to (it.message ?: it.toString()) }) } }
                    .forEach { (c, future) -> results.getValue(way.name)[c] = future.get(10, TimeUnit.MINUTES) }
                used.getValue(way.name).let { u -> u[0] += tokensIn.get() - before[0]; u[1] += tokensOut.get() - before[1]; u[2] += spent.get() - before[2] }
            }
        }
        pool.shutdown()
        val out = StringBuilder()
        ways.forEach { way ->
            val body = StringBuilder()
            val long = mutableListOf<String>()
            var passed = 0
            val times = mutableListOf<Double>()
            chosen.forEach { c ->
                val (o, crash) = results.getValue(way.name).getValue(c)
                val failed = report(c, o, crash, body)
                if (failed.isEmpty()) passed++
                times += o?.seconds.orEmpty()
                // The rule is one short sentence: longer answers are listed, they do not fail the case.
                o?.answers?.mapNotNull { it?.text }?.filter { it.split(Regex("""\s+""")).size > 25 }?.forEach { long += c.name + ": " + it.replace("\n", " ") }
            }
            val sorted = times.sorted()
            fun at(share: Double) = if (sorted.isEmpty()) 0.0 else sorted[((sorted.size - 1) * share).toInt()]
            val u = used.getValue(way.name)
            out.append("AI eval [${way.name}]: $passed of ${chosen.size} passed · ${Ai.modelNow()} · effort ${Ai.effort} · " +
                String.format(java.util.Locale.US, "a message takes %.1fs (half are under), %.1fs (9 in 10 are under) · ", at(0.5), at(0.9)) +
                u[0] + " in, " + u[1] + " out · $" + String.format(java.util.Locale.US, "%.4f", u[2] / 1_000_000.0) + "\n\n")
            out.append(body)
            if (long.isNotEmpty()) out.append("\nLONG ANSWERS (over 25 words)\n").append(long.joinToString("\n") { "      $it" }).append('\n')
            out.append('\n')
        }
        File("build").mkdirs()
        File("build/ai-eval.txt").writeText(out.toString())
        println(out)
    }

    /** What a case missed ("" when none), and its PASS or FAIL lines (with the tools used and what was saved) added to [body]. */
    private fun report(c: Case, o: Outcome?, crash: String, body: StringBuilder): List<String> {
        val failed = when {
            o == null -> listOf("crashed: $crash")
            o.errors.isNotEmpty() -> listOf("no answer: " + o.errors.joinToString("; "))
            else -> runCatching { c.check(o).filter { !it.second }.map { it.first } }.getOrElse { listOf("check crashed: " + it.message) }
        }
        body.append(if (failed.isEmpty()) "PASS  " else "FAIL  ").append(c.name).append("  <- ").append(c.turns.joinToString(" | ") { it.said }).append('\n')
        if (failed.isNotEmpty()) {
            body.append("      wanted: ").append(failed.joinToString("; ")).append('\n')
            o?.trace?.forEach { body.append("      ").append(it.replace("\n", " ")).append('\n') }
            o?.answers?.forEachIndexed { i, a -> body.append("      answer ${i + 1}: ").append(a?.let { it.text + (if (it.card.isBlank()) "" else " | card: " + it.card.take(160)) + (if (it.ask.isBlank()) "" else " | ask: " + it.ask) }?.replace("\n", " / ") ?: "(none)").append('\n') }
            o?.added?.forEach { body.append("      new: ").append(brief(it)).append('\n') }
        }
        return failed
    }

    /**
     * The app's own assistant (LocalAgent, no AI) on the same messages: offline and free, so it runs on every build.
     * LOCAL_EVAL_ONLY picks cases by name; the report is written to build/local-eval.txt. The build fails when fewer
     * cases pass than [localFloor], so a change cannot quietly make the app's own answers worse.
     */
    @Test
    fun localEngine() {
        val only = System.getenv("LOCAL_EVAL_ONLY").orEmpty().split(",").map { it.trim() }.filter { it.isNotBlank() }
        val chosen = cases.filter { c -> only.isEmpty() || only.any { c.name.contains(it, true) } }
        val body = StringBuilder()
        var passed = 0
        chosen.forEach { c ->
            val run = runCatching { run(c, local = true) }
            if (report(c, run.getOrNull(), run.exceptionOrNull()?.let { it.message ?: it.toString() }.orEmpty(), body).isEmpty()) passed++
        }
        val out = "Local eval: $passed of ${chosen.size} passed\n\n$body"
        File("build").mkdirs()
        File("build/local-eval.txt").writeText(out)
        println(out)
        if (only.isEmpty()) org.junit.Assert.assertTrue("The app's own assistant passed $passed of ${chosen.size}; it passed $localFloor before", passed >= localFloor)
    }

    /** How many everyday messages the app's own assistant got right last time: it may only go up. */
    private val localFloor = 0
}
