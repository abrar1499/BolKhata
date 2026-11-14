package com.bolkhata.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** New entries read by the app's own assistant ([LocalAdd]): many everyday ways of saying them, on a small made-up ledger. */
class LocalAddTest {
    private val now = System.currentTimeMillis()
    private val day = 86_400_000L

    /** Two workers, a customer and people with loans; each test starts from the same books. */
    private inner class Books {
        var entries: List<Entry> = listOf(
            Entry("w1", Type.PURCHASE, 20_000.0, Cat.PRODUCTION, "Kachi eent", "Hamid Akram", "", now - 6 * day, now - 6 * day, qty = 20_000.0, unit = "1000", rate = 1_000.0),
            Entry("w2", Type.PURCHASE, 3_000.0, Cat.PRODUCTION, "Bharai", "Zubair Latif", "", now - 4 * day, now - 4 * day, qty = 10_000.0, unit = "1000", rate = 300.0),
            Entry("s1", Type.SALE, 60_000.0, Cat.SALES, "Pakki eent", "Nargis Salim Rashid", "", now - 4 * day, now - 4 * day, qty = 5_000.0, unit = "1000", rate = 12_000.0),
            Entry("l1", Type.DIYA, 500.0, Cat.UDHAAR, "Loans", "Salim Khan", "", now - 8 * day, now - 8 * day),
            Entry("l2", Type.DIYA, 900.0, Cat.UDHAAR, "Loans", "Salim Raza", "", now - 7 * day, now - 7 * day),
            Entry("l3", Type.DIYA, 1_200.0, Cat.UDHAAR, "Loans", "Mohsin Javed", "", now - 9 * day, now - 9 * day),
            Entry("x1", Type.KHARCHA, 60.0, Cat.TRANSPORT, "Petrol", "", "", now - 2 * day, now - 2 * day)
        )
        val base = entries.map { it.id }.toSet()
        var setup = SideSetup(people = mapOf("Hamid Akram" to Sides.STAFF, "Zubair Latif" to Sides.STAFF, "Nargis Salim Rashid" to Sides.CUSTOMERS))
        val host = AiAgent.Host(
            entries = { entries }, add = { entries = it + entries },
            update = { changed -> entries = entries.map { e -> changed.firstOrNull { it.id == e.id } ?: e } }, trash = {},
            names = { (entries.map { it.person } + setup.people.keys).map { it.trim() }.filter { it.isNotBlank() }.distinct() },
            currency = "PKR", setup = { setup }, saveSetup = { setup = it }
        )
        private val history = mutableListOf<String>()
        private var justSaved = emptyList<String>()
        /** The option card of the last message ("person|number"), when it asked with one. */
        var ask = ""
        val added get() = entries.filter { it.id !in base }

        /** One message read by LocalAdd alone, with the chat before it as LocalAgent passes it. */
        fun say(text: String): LocalAgent.Reply? {
            val roman = runCatching { Translit.toRoman(text) }.getOrDefault(text)
            val counted = AiAgent.counted(roman)
            val lastShown = history.lastOrNull { it.startsWith("App: ") }?.removePrefix("App: ").orEmpty()
            val notes = AiAgent.Notes(said = (AiAgent.numbers(roman) + AiAgent.numbers(counted)).distinct(), lastShown = lastShown)
            val s = LocalAgent.Said(text, roman, counted, host, now, "", justSaved, lastShown, history.takeLast(6).joinToString("\n"), notes) { _, _, _ -> }
            val reply = LocalAdd.read(s)
            history += "Person: $text"
            history += "App: " + reply?.text.orEmpty()
            justSaved = notes.added.toList()
            ask = notes.ask
            return reply
        }

        fun has(type: Type, person: String = "", amount: Double, cat: Cat? = null, qty: Double? = null, item: String = "") = added.any { e ->
            e.type == type && e.person.contains(person, true) && kotlin.math.abs(e.amount - amount) < 0.01 && (cat == null || e.category == cat) &&
                (qty == null || kotlin.math.abs(e.qty - qty) < 0.01) && e.item.contains(item, true)
        }
    }

    private fun dayOf(ms: Long) = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date(ms))

    /** [text] saves exactly [count] entries, each matching one check, with no sentence over the Saved card. */
    private fun saves(text: String, count: Int = 1, check: Books.() -> Boolean) = Books().run {
        val reply = say(text)
        assertTrue("$text: no reply", reply != null)
        assertEquals("$text: entries saved " + added.map { it.type.name + " " + it.person + " " + it.amount + " " + it.item + " " + it.category }, count, added.size)
        assertTrue("$text: saved " + added.map { it.type.name + " " + it.person + " " + it.amount + " " + it.qty + " " + it.item + " " + it.category }, check())
    }

    /** [text] is not a new entry: LocalAdd leaves it to the other readers and saves nothing. */
    private fun leaves(text: String) = Books().run {
        assertNull("$text was read as an entry", say(text))
        assertTrue(added.isEmpty())
    }

    // ---- word order

    @Test fun amountFirst() = saves("500 hamid ko diye") { has(Type.DIYA, "Hamid Akram", 500.0) }
    @Test fun verbFirst() = saves("diye hamid ko 500") { has(Type.DIYA, "Hamid Akram", 500.0) }
    @Test fun noMarker() = saves("hamid 500 diye") { has(Type.DIYA, "Hamid Akram", 500.0) }
    @Test fun customerPays() = saves("nargis ne 2000 diye") { has(Type.WAPIS_MILA, "Nargis Salim Rashid", 2_000.0) }
    @Test fun takenFromSomeone() = saves("salim khan se 300 liye") { has(Type.WAPIS_MILA, "Salim Khan", 300.0) }

    // ---- short chat and typos

    @Test fun personAndNumberAsks() = Books().run {
        assertEquals("", say("hmd 500")?.text)
        assertEquals("Hamid Akram|500.0", ask)
        assertTrue(added.isEmpty())
    }
    @Test fun shortThing() = saves("ptrl 60") { has(Type.KHARCHA, amount = 60.0, cat = Cat.TRANSPORT) }
    @Test fun misspeltNameAndLoan() = saves("zubar se 1000 udhaar liya") { has(Type.LIYA, "Zubair Latif", 1_000.0) }
    @Test fun heardLikeAName() = saves("hameed ko paanch so diye") { has(Type.DIYA, "Hamid Akram", 500.0) }
    @Test fun moneyWritten() = saves("Hamid ko Rs.1,500/- diye") { has(Type.DIYA, "Hamid Akram", 1_500.0) }
    @Test fun digitAndThousand() = saves("hamid ko 2 hazar diye") { has(Type.DIYA, "Hamid Akram", 2_000.0) }
    @Test fun halfMore() = saves("hamid ko sadhe 3 hazar diye") { has(Type.DIYA, "Hamid Akram", 3_500.0) }
    @Test fun kForThousand() = saves("petrol 2.5k") { has(Type.KHARCHA, amount = 2_500.0, cat = Cat.TRANSPORT) }
    @Test fun countedThingIsNotTheMoney() = saves("5 kg chawal 600") { has(Type.KHARCHA, amount = 600.0, cat = Cat.KHANA) }

    // ---- English and mixed

    @Test fun gaveSomeone() = saves("gave hamid 500") { has(Type.DIYA, "Hamid Akram", 500.0) }
    @Test fun mixedGiven() = saves("hamid ko 500 given") { has(Type.DIYA, "Hamid Akram", 500.0) }
    @Test fun receivedFrom() = saves("received 2000 from nargis") { has(Type.WAPIS_MILA, "Nargis Salim Rashid", 2_000.0) }
    @Test fun someonePaid() = saves("nargis paid 2000") { has(Type.WAPIS_MILA, "Nargis Salim Rashid", 2_000.0) }
    @Test fun paidSomeone() = saves("paid zubair 700") { has(Type.DIYA, "Zubair Latif", 700.0) }
    @Test fun lentToNewPerson() = saves("lent 1500 to farooq") { has(Type.DIYA, "Farooq", 1_500.0) }
    @Test fun borrowedFrom() = saves("borrowed 1000 from zubair") { has(Type.LIYA, "Zubair Latif", 1_000.0) }
    @Test fun returned() = saves("hamid returned 500") { has(Type.WAPIS_MILA, "Hamid Akram", 500.0) }
    @Test fun spentOn() = saves("spent 300 on groceries") { has(Type.KHARCHA, amount = 300.0, cat = Cat.KHANA) }
    @Test fun soldToCustomer() = saves("sold 3000 pakki eent to nargis") { has(Type.SALE, "Nargis Salim Rashid", 36_000.0, qty = 3_000.0, item = "Pakki eent") }
    @Test fun aQuestionIsNotAnEntry() = leaves("how much hamid owes")

    // ---- script

    @Test fun urduScriptDigits() = saves("حامد کو 500 دیے") { has(Type.DIYA, "Hamid Akram", 500.0) }
    @Test fun hindiScript() = saves("नरगिस से 3000 मिले") { has(Type.WAPIS_MILA, "Nargis Salim Rashid", 3_000.0) }

    // ---- several in one message

    @Test fun voiceWithoutCommas() = saves("hamid ko 500 diye zubair ko 300 diye petrol 50", 3) {
        has(Type.DIYA, "Hamid Akram", 500.0) && has(Type.DIYA, "Zubair Latif", 300.0) && has(Type.KHARCHA, amount = 50.0, cat = Cat.TRANSPORT)
    }
    @Test fun oneVerbForTwo() = saves("hamid ko 500 aur zubair ko 300 diye", 2) { has(Type.DIYA, "Hamid Akram", 500.0) && has(Type.DIYA, "Zubair Latif", 300.0) }
    @Test fun twoThingsNoJoiner() = saves("chai 20 nashta 50", 2) { has(Type.KHARCHA, amount = 20.0, cat = Cat.KHANA) && has(Type.KHARCHA, amount = 50.0, cat = Cat.KHANA) }
    @Test fun sameToBoth() = saves("hamid aur zubair ko 1000 diye", 2) { has(Type.DIYA, "Hamid Akram", 1_000.0) && has(Type.DIYA, "Zubair Latif", 1_000.0) }
    @Test fun givenThenReturned() = saves("hamid ko 500 diye aur us ne 200 wapis diye", 2) { has(Type.DIYA, "Hamid Akram", 500.0) && has(Type.WAPIS_MILA, "Hamid Akram", 200.0) }
    @Test fun eachDayItsOwn() = saves("kal hamid ko 500 diye aur aaj zubair ko 300", 2) {
        added.any { it.person == "Hamid Akram" && dayOf(it.date) == dayOf(now - day) } && added.any { it.person == "Zubair Latif" && dayOf(it.date) == dayOf(now) }
    }

    // ---- work and goods

    @Test fun newWorkerNewThingWithRate() = saves("akbar ne 500 shirts silai ki 40 ke hisaab se") { has(Type.PURCHASE, "Akbar", 20_000.0, qty = 500.0, item = "Shirts") }
    @Test fun ratePerThousand() = saves("hamid ne 5000 kachi eent dali 1100 per 1000") { has(Type.PURCHASE, "Hamid Akram", 5_500.0, qty = 5_000.0, item = "Kachi eent") }
    @Test fun oneWordOfTheThing() = saves("nargis ko 3000 eent di") { has(Type.SALE, "Nargis Salim Rashid", 36_000.0, qty = 3_000.0, item = "Pakki eent") }
    @Test fun advanceToWorker() = saves("hamid ko 10000 advance") { has(Type.DIYA, "Hamid Akram", 10_000.0) && added[0].note == "Advance" }

    // ---- expenses and income

    @Test fun sentToFamily() = saves("ammi ko 5000 bheje") { has(Type.KHARCHA, amount = 5_000.0, cat = Cat.GHAR) }
    @Test fun seller() = saves("doodh wale ko 300 diye") { has(Type.KHARCHA, amount = 300.0, cat = Cat.KHANA) }
    @Test fun salaryCame() = saves("salary 30000 aayi") { has(Type.AAMDANI, amount = 30_000.0) }
    @Test fun rentReceived() = saves("dukan ka kiraya 5000 mila") { has(Type.AAMDANI, amount = 5_000.0) }
    @Test fun daysAgo() = saves("3 din pehle petrol 100") { has(Type.KHARCHA, amount = 100.0, cat = Cat.TRANSPORT) && dayOf(added[0].date) == dayOf(now - 3 * day) }

    // ---- questions back, and their answers

    @Test fun whichOfTwoNames() = Books().run {
        assertTrue(say("salim ko 200 diye")?.text.orEmpty().contains("?"))
        assertTrue(added.isEmpty())
        say("khan wala")
        assertEquals(1, added.size)
        assertTrue(has(Type.DIYA, "Salim Khan", 200.0))
    }
    @Test fun workWithNoCount() = Books().run {
        assertTrue(say("hamid ne kaam kiya")?.text.orEmpty().contains("?"))
        say("4000 kachi eent")
        assertTrue(has(Type.PURCHASE, "Hamid Akram", 4_000.0, qty = 4_000.0, item = "Kachi eent"))
    }
    @Test fun rateNotKnownYet() = Books().run {
        assertTrue(say("akbar ne 300 jootay banaye")?.text.orEmpty().contains("?"))
        assertTrue(added.isEmpty())
        say("50")
        assertTrue(has(Type.PURCHASE, "Akbar", 15_000.0, qty = 300.0))
    }
    @Test fun heBeforeIsTheLastPerson() = Books().run {
        say("hamid ko 500 diye")
        say("us ne 200 wapis kiye")
        assertTrue(has(Type.WAPIS_MILA, "Hamid Akram", 200.0))
    }

    @Test fun bareCountAnswersHowMany() = Books().run {
        say("hamid ne aaj kaam kiya")
        say("3000")
        assertTrue(has(Type.PURCHASE, "Hamid Akram", 3_000.0, qty = 3_000.0, item = "Kachi eent"))
    }

    // ---- more everyday ways

    @Test fun numberWordBeforeVerb() = saves("hamid ko pachas diye") { has(Type.DIYA, "Hamid Akram", 50.0) }
    @Test fun numberWordBeforeKa() = saves("bees ki chai pi") { has(Type.KHARCHA, amount = 20.0, cat = Cat.KHANA) }
    @Test fun namesListedWithCommas() = saves("hamid, zubair aur salim khan ko 500 500 300 diye", 3) {
        has(Type.DIYA, "Hamid Akram", 500.0) && has(Type.DIYA, "Zubair Latif", 500.0) && has(Type.DIYA, "Salim Khan", 300.0)
    }
    @Test fun ratePerPiece() = saves("nargis ko 3000 pakki eent 12 rupay wali di") { has(Type.SALE, "Nargis Salim Rashid", 36_000.0, qty = 3_000.0) && added[0].unit != "1000" }
    @Test fun cashSaleToCustomer() = saves("nargis ko 3000 pakki eent cash mein di") { has(Type.SALE, "Nargis Salim Rashid", 36_000.0) && added[0].paid }
    @Test fun cashSaleWithNoOne() = saves("aaj 3000 ki sale hui") { has(Type.SALE, amount = 3_000.0) && added[0].paid }
    @Test fun goodsWorthASum() = saves("nargis ko 500 ka saman diya") { has(Type.SALE, "Nargis Salim Rashid", 500.0) }
    @Test fun workWorthASum() = saves("hamid ne 3000 ki bharai ki") { has(Type.PURCHASE, "Hamid Akram", 3_000.0, item = "Bharai") }
    @Test fun englishWorkWord() = saves("zubair loaded 6000 bricks") { has(Type.PURCHASE, "Zubair Latif", 1_800.0, qty = 6_000.0, item = "Bharai") }
    @Test fun honorificIsNotAPerson() = saves("hamid bhai ko 500 diye") { has(Type.DIYA, "Hamid Akram", 500.0) }
    @Test fun greetingFirst() = saves("assalam o alaikum hamid ko 500 diye") { has(Type.DIYA, "Hamid Akram", 500.0) }
    @Test fun daysAreNotTheAmount() = saves("zubair ne 3 din ki dihari 1500 li") { has(Type.DIYA, "Zubair Latif", 1_500.0) }
    @Test fun cashAfterGoods() = saves("nargis ko 2000 eent di aur 5000 cash liye", 2) {
        has(Type.SALE, "Nargis Salim Rashid", 24_000.0, qty = 2_000.0) && has(Type.WAPIS_MILA, "Nargis Salim Rashid", 5_000.0) && added.none { it.paid }
    }
    @Test fun advanceAfterWork() = saves("hamid ne 5000 kachi eent dali aur 2000 advance liye", 2) {
        has(Type.PURCHASE, "Hamid Akram", 5_000.0, qty = 5_000.0) && has(Type.DIYA, "Hamid Akram", 2_000.0)
    }
    @Test fun moreWorkWorthASum() = saves("hamid ne 5000 eent dali, 1000 ki bharai ki", 2) {
        has(Type.PURCHASE, "Hamid Akram", 5_000.0, qty = 5_000.0, item = "Kachi eent") && has(Type.PURCHASE, "Hamid Akram", 1_000.0, item = "Bharai")
    }
    @Test fun longVoiceMessage() = saves("aaj subah hamid ko 500 diye phir dopahar ko petrol 300 ka dalwaya aur shaam ko nargis se 2000 mile", 3) {
        has(Type.DIYA, "Hamid Akram", 500.0) && has(Type.KHARCHA, amount = 300.0, cat = Cat.TRANSPORT) && has(Type.WAPIS_MILA, "Nargis Salim Rashid", 2_000.0)
    }
    @Test fun paymentChannelIsNoOne() = saves("jazzcash se hamid ko 500 bheje") { has(Type.DIYA, "Hamid Akram", 500.0) }

    // ---- not new entries

    @Test fun timeIsNotMoney() = leaves("kal 5 baje hamid aayega")
    @Test fun phoneIsNotMoney() = leaves("mera number 03001234567 hai")
    @Test fun owedLaterIsNotPaid() = leaves("hamid ko 500 dene hain")
    @Test fun owesIsLeft() = leaves("hamid owes me 500")
    @Test fun correctionIsLeft() = leaves("nahi 400 tha")
    @Test fun deleteIsLeft() = leaves("ye delete kar do")
    @Test fun lookUpIsLeft() = leaves("hamid ka hisab batao")
    @Test fun changeIsLeft() = leaves("kal wali chai 5 ki jagah 8 kar do")
    @Test fun bareNumberIsLeft() = leaves("500")
}
