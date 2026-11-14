package com.bolkhata.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Changes to what is saved, read by the app's own assistant ([LocalEdit]): many everyday ways of saying them, on a small made-up ledger. */
class LocalEditTest {
    private val now = System.currentTimeMillis()
    private val day = 86_400_000L
    private val minute = 60_000L

    /** Two workers, a customer, loans and expenses; each test starts from the same books. */
    private inner class Books {
        var entries: List<Entry> = listOf(
            Entry("w1", Type.PURCHASE, 20_000.0, Cat.PRODUCTION, "Kachi eent", "Hamid Akram", "", now - 6 * day, now - 6 * day, qty = 20_000.0, unit = "1000", rate = 1_000.0),
            Entry("a1", Type.DIYA, 50_000.0, Cat.UDHAAR, "Loans", "Hamid Akram", "advance", now - 5 * day, now - 5 * day),
            Entry("w2", Type.PURCHASE, 3_000.0, Cat.PRODUCTION, "Bharai", "Zubair Latif", "", now - 4 * day, now - 4 * day, qty = 10_000.0, unit = "1000", rate = 300.0),
            Entry("s1", Type.SALE, 60_000.0, Cat.SALES, "Pakki eent", "Nargis Salim Rashid", "", now - 4 * day, now - 4 * day, qty = 5_000.0, unit = "1000", rate = 12_000.0),
            Entry("l1", Type.DIYA, 500.0, Cat.UDHAAR, "Loans", "Salim Khan", "", now - 8 * day, now - 8 * day),
            Entry("l2", Type.DIYA, 900.0, Cat.UDHAAR, "Loans", "Salim Raza", "", now - 7 * day, now - 7 * day),
            Entry("l3", Type.DIYA, 1_200.0, Cat.UDHAAR, "Loans", "Mohsin Javed", "", now - 9 * day, now - 9 * day),
            Entry("q1", Type.DIYA, 300.0, Cat.UDHAAR, "Loans", "Bilal Tariq Mehmood", "", now - 12 * day, now - 12 * day),
            Entry("x1", Type.KHARCHA, 60.0, Cat.TRANSPORT, "Petrol", "", "", now - 2 * day, now - 2 * day),
            Entry("x3", Type.KHARCHA, 5.0, Cat.KHANA, "Tea", "", "", now - 1 * day, now - 1 * day),
            Entry("x5", Type.KHARCHA, 80.0, Cat.TRANSPORT, "Petrol", "", "", now - 35 * day, now - 35 * day),
            Entry("i1", Type.AAMDANI, 4_000.0, Cat.TANKHWAH, "Salary", "", "", now - 30 * day, now - 30 * day)
        )
        val base = entries.map { it.id }.toSet()
        val trashed = mutableSetOf<String>()
        var bin = listOf<Entry>()
        var setup = SideSetup(people = mapOf("Hamid Akram" to Sides.STAFF, "Zubair Latif" to Sides.STAFF, "Nargis Salim Rashid" to Sides.CUSTOMERS))
        val host = AiAgent.Host(
            entries = { entries }, add = { entries = it + entries },
            update = { changed -> entries = entries.map { e -> changed.firstOrNull { it.id == e.id } ?: e } },
            trash = { ids -> trashed += ids; bin = entries.filter { it.id in ids } + bin; entries = entries.filter { it.id !in ids } },
            names = { (entries.map { it.person } + setup.people.keys).map { it.trim() }.filter { it.isNotBlank() }.distinct() },
            currency = "PKR", setup = { setup }, saveSetup = { setup = it },
            trashed = { bin }, restore = { ids -> entries = bin.filter { it.id in ids } + entries; bin = bin.filter { it.id !in ids }; trashed -= ids }
        )
        private val history = mutableListOf<String>()
        private var justSaved = emptyList<String>()
        /** The tools the last message used. */
        val tools = mutableListOf<String>()

        /** The message before: [said] saved [e] a moment ago, with the Saved card as the answer. */
        fun saved(said: String, vararg e: Entry) {
            entries = e.toList() + entries
            justSaved = e.map { it.id }
            history += "Person: $said"
            history += "App: "
        }

        /** One message read by LocalEdit alone, with the chat before it as LocalAgent passes it. */
        fun say(text: String): LocalAgent.Reply? {
            val roman = runCatching { Translit.toRoman(text) }.getOrDefault(text)
            val counted = AiAgent.counted(roman)
            val lastShown = history.lastOrNull { it.startsWith("App: ") }?.removePrefix("App: ").orEmpty()
            val notes = AiAgent.Notes(said = (AiAgent.numbers(roman) + AiAgent.numbers(counted)).distinct(), lastShown = lastShown)
            tools.clear()
            val s = LocalAgent.Said(text, roman, counted, host, now, "", justSaved, lastShown, history.takeLast(6).joinToString("\n"), notes) { name, _, _ -> tools += name }
            val reply = LocalEdit.read(s)
            history += "Person: $text"
            history += "App: " + reply?.text.orEmpty()
            justSaved = notes.added.toList()
            return reply
        }

        fun byId(id: String) = entries.firstOrNull { it.id == id }
        fun bookOf(person: String) = Sides.bookOf(person, entries, setup)
    }

    private fun lent(id: String, person: String, amount: Double) = Entry(id, Type.DIYA, amount, Cat.UDHAAR, "Loans", person, "", now, now)
    private fun spent(id: String, item: String, amount: Double, cat: Cat) = Entry(id, Type.KHARCHA, amount, cat, item, "", "", now, now)
    private fun dayOf(ms: Long) = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date(ms))

    /** After "hamid ko 500 diye" was saved, [text] makes that entry pass [check], with nothing new saved. */
    private fun fixes(text: String, check: (Entry) -> Boolean) = Books().run {
        saved("hamid ko 500 diye", lent("j1", "Hamid Akram", 500.0))
        val reply = say(text)
        assertTrue("$text: no reply", reply != null && reply.text.isNotBlank())
        val e = byId("j1")!!
        assertTrue("$text: now ${e.type} ${e.person} ${e.amount} ${dayOf(e.date)}", check(e))
        assertEquals("$text: nothing new", base + "j1", entries.map { it.id }.toSet())
    }

    /** [text] is not this reader's kind: it gives null and uses no tool. */
    private fun leaves(text: String, before: Books.() -> Unit = {}) = Books().run {
        before()
        val start = entries
        assertNull("$text was read as a change", say(text))
        assertTrue("$text used " + tools, tools.isEmpty())
        assertEquals(start, entries)
    }

    // ---- the amount just saved

    @Test fun amountWithNahi() = fixes("nahi 400 tha") { it.amount == 400.0 }
    @Test fun amountShort() = fixes("400 tha") { it.amount == 400.0 }
    @Test fun amountEnglish() = fixes("no it was 400") { it.amount == 400.0 }
    @Test fun amountOldAndNew() = fixes("500 nahi 400") { it.amount == 400.0 }
    @Test fun amountMakeIt() = fixes("make it 450") { it.amount == 450.0 }
    @Test fun amountInWords() = fixes("nahi do hazar tha") { it.amount == 2_000.0 }
    @Test fun amountDigitAndWord() = fixes("nahi 2 hazar tha") { it.amount == 2_000.0 }
    @Test fun amountWithK() = fixes("nhi 2k tha") { it.amount == 2_000.0 }
    @Test fun amountUrduScript() = fixes("نہیں 400 تھا") { it.amount == 400.0 }
    @Test fun amountHindiScript() = fixes("नहीं 400 था") { it.amount == 400.0 }
    @Test fun amountEnglishNot() = fixes("it was 400 not 500") { it.amount == 400.0 }
    @Test fun amountShouldBe() = fixes("it should be 450") { it.amount == 450.0 }

    // ---- the day, the person, the kind

    @Test fun dayYesterday() = fixes("ye kal ka tha") { dayOf(it.date) == dayOf(now - day) }
    @Test fun dayUrdu() = fixes("یہ کل کا تھا") { dayOf(it.date) == dayOf(now - day) }
    @Test fun personHindi() = fixes("नहीं ज़ुबैर को दिए थे") { it.person == "Zubair Latif" }
    @Test fun dayShort() = fixes("kal ka tha") { dayOf(it.date) == dayOf(now - day) }
    @Test fun dayBeforeYesterday() = fixes("ye parso ki entry thi") { dayOf(it.date) == dayOf(now - 2 * day) }
    @Test fun daysAgo() = fixes("ye 3 din pehle ka tha") { dayOf(it.date) == dayOf(now - 3 * day) }
    @Test fun amountAndDayTogether() = fixes("nahi 400 tha aur kal ka") { it.amount == 400.0 && dayOf(it.date) == dayOf(now - day) }
    @Test fun personSaved() = fixes("ye zubair ko diye thay") { it.person == "Zubair Latif" && it.amount == 500.0 }
    @Test fun personEnglish() = fixes("it was zubair") { it.person == "Zubair Latif" }
    @Test fun personSwapped() = fixes("hamid nahi zubair ko") { it.person == "Zubair Latif" }
    @Test fun personNotSavedYet() = fixes("ye farooq ko diye thay") { it.person == "Farooq" }
    @Test fun voiceFiller() = fixes("umm nahi wo 400 tha") { it.amount == 400.0 }

    @Test fun personAskedWhichSalim() = Books().run {
        saved("hamid ko 500 diye", lent("j1", "Hamid Akram", 500.0))
        assertTrue('?' in say("ye salim ko diye the")!!.text)
        say("salim khan")
        assertEquals("Salim Khan", byId("j1")!!.person)
    }

    @Test fun receivedNotPaid() = fixes("nahi, mile thay") { it.type == Type.WAPIS_MILA && it.person == "Hamid Akram" }
    @Test fun spendingNotALoan() = fixes("ye food mein daal do") { it.type == Type.KHARCHA && it.category == Cat.KHANA }

    @Test fun aNewPersonIsNotARename() = Books().run {
        saved("tariq ko 5000 udhaar diya", lent("j1", "Bilal Tariq Mehmood", 5_000.0))
        say("ye naya tariq hai")
        assertEquals("Tariq", byId("j1")!!.person)
        assertEquals("Bilal Tariq Mehmood", byId("q1")!!.person)
    }

    @Test fun intoAPersonsAccount() = Books().run {
        saved("zubair ko 700 diye", lent("j1", "Zubair Latif", 700.0))
        say("ye hamid ke khate mein daal do")
        assertEquals("Hamid Akram", byId("j1")!!.person)
    }

    // ---- work: the count or the rate, never the amount alone

    private fun work() = Entry("j1", Type.PURCHASE, 3_000.0, Cat.PRODUCTION, "Kachi eent", "Hamid Akram", "", now, now, qty = 3_000.0, unit = "1000", rate = 1_000.0)

    @Test fun workCount() = Books().run {
        saved("hamid ne 3000 kachi eent dali", work())
        say("nahi 3500 thi")
        byId("j1")!!.let { assertEquals(3_500.0, it.qty, 0.01); assertEquals(3_500.0, it.amount, 0.01) }
    }

    @Test fun workRate() = Books().run {
        saved("hamid ne 3000 kachi eent dali", work())
        say("rate 1100 tha")
        byId("j1")!!.let { assertEquals(1_100.0, it.rate, 0.01); assertEquals(3_300.0, it.amount, 0.01) }
    }

    // ---- several just saved

    @Test fun theThingNamedPicksOne() = Books().run {
        saved("petrol 60 aur chai 10", spent("j1", "Petrol", 60.0, Cat.TRANSPORT), spent("j2", "Tea", 10.0, Cat.KHANA))
        say("petrol 70 tha")
        assertEquals(70.0, byId("j1")!!.amount, 0.01)
        assertEquals(10.0, byId("j2")!!.amount, 0.01)
    }

    @Test fun theRightThing() = Books().run {
        saved("chai 15", spent("j1", "Tea", 15.0, Cat.KHANA))
        say("nahi petrol tha")
        byId("j1")!!.let { assertEquals("Petrol", it.item); assertEquals(Cat.TRANSPORT, it.category); assertEquals(15.0, it.amount, 0.01) }
    }

    @Test fun asksWhichThenTheAnswerPicks() = Books().run {
        saved("hamid ko 500 aur zubair ko 300 diye", lent("j1", "Hamid Akram", 500.0), lent("j2", "Zubair Latif", 300.0))
        assertTrue('?' in say("nahi 400 tha")!!.text)
        assertEquals(500.0, byId("j1")!!.amount, 0.01)
        say("hamid wala")
        assertEquals(400.0, byId("j1")!!.amount, 0.01)
        assertEquals(300.0, byId("j2")!!.amount, 0.01)
    }

    // ---- delete

    private fun deletesJust(text: String) = Books().run {
        saved("chai 15", spent("j1", "Tea", 15.0, Cat.KHANA))
        assertTrue(say(text)!!.text.contains("Trash"))
        assertEquals(setOf("j1"), trashed)
    }

    @Test fun deleteJustSaved() = deletesJust("ye delete kar do")
    @Test fun deleteJustSavedTypos() = deletesJust("isko dlt krdo")
    @Test fun deleteJustSavedEnglish() = deletesJust("delete it")
    @Test fun deleteJustSavedUrdu() = deletesJust("یہ ڈیلیٹ کر دو")
    @Test fun deleteJustSavedHindi() = deletesJust("ये डिलीट कर दो")
    @Test fun deleteJustSavedUndo() = deletesJust("undo")

    @Test fun deleteJustSavedVoice() = deletesJust("acha ye wali entry delete kar dein please")

    @Test fun deleteLastHour() = Books().run {
        entries = listOf(Entry("n1", Type.KHARCHA, 25.0, Cat.KHANA, "Tea", "", "", now - 40 * minute, now - 40 * minute)) + entries
        say("pichle ghante ki entries delete karo")
        assertEquals(setOf("n1"), trashed)
    }

    @Test fun deleteNewest() = Books().run {
        entries = listOf(Entry("n9", Type.KHARCHA, 45.0, Cat.KHANA, "Lunch", "", "", now - 2 * minute, now - 2 * minute)) + entries
        say("akhri entri hta do")
        assertEquals(setOf("n9"), trashed)
    }

    @Test fun deleteLastTwo() = Books().run {
        entries = listOf(Entry("n8", Type.KHARCHA, 45.0, Cat.KHANA, "Lunch", "", "", now - 3 * minute, now - 3 * minute),
            Entry("n9", Type.KHARCHA, 20.0, Cat.KHANA, "Tea", "", "", now - 2 * minute, now - 2 * minute)) + entries
        say("last 2 entries delete karo")
        assertEquals(setOf("n8", "n9"), trashed)
    }

    @Test fun deleteLastMinutes() = Books().run {
        entries = listOf(Entry("n1", Type.KHARCHA, 25.0, Cat.KHANA, "Tea", "", "", now - 3 * minute, now - 3 * minute),
            Entry("n2", Type.DIYA, 700.0, Cat.UDHAAR, "Loans", "Hamid Akram", "", now - 5 * minute, now - 5 * minute)) + entries
        say("pichle 10 minute ki sari entries delete kar do")
        assertEquals(setOf("n1", "n2"), trashed)
    }

    @Test fun deleteNamed() = Books().run {
        say("hamid ko jo 50000 advance diya tha wo delete kar do")
        assertEquals(setOf("a1"), trashed)
    }

    @Test fun deleteNamedEnglish() = Books().run {
        say("delete hamid's 50000 entry")
        assertEquals(setOf("a1"), trashed)
    }

    @Test fun deleteOldByThingAndDay() = Books().run {
        say("kal ki chai wali entry delete karo")
        assertEquals(setOf("x3"), trashed)
    }

    @Test fun deleteOneOfSeveralAsksThenTheAnswerPicks() = Books().run {
        assertTrue('?' in say("petrol ki entry delete karo")!!.text)
        assertTrue(trashed.isEmpty())
        say("60 wali")
        assertEquals(setOf("x1"), trashed)
    }

    @Test fun deleteEverythingAsksThenYes() = Books().run {
        assertTrue('?' in say("sari entries delete kar do")!!.text)
        assertTrue(trashed.isEmpty())
        say("haan kar do")
        assertTrue(entries.isEmpty())
    }

    @Test fun deleteEverythingThenNo() = Books().run {
        say("sab entries mita do")
        say("nahi")
        assertTrue(trashed.isEmpty())
        assertEquals(base, entries.map { it.id }.toSet())
    }

    // ---- restore

    private fun bringsBack(text: String) = Books().run {
        saved("chai 15", spent("j1", "Tea", 15.0, Cat.KHANA))
        say("ye delete kar do")
        assertEquals(setOf("j1"), trashed)
        say(text)
        assertTrue("$text did not bring it back", trashed.isEmpty() && byId("j1") != null)
    }

    @Test fun restoreRoman() = bringsBack("wapas le aao")
    @Test fun restoreUrdu() = bringsBack("اسے واپس لاؤ")
    @Test fun restoreEnglish() = bringsBack("bring it back")
    @Test fun restoreUndo() = bringsBack("undo")
    @Test fun restoreUndoDelete() = bringsBack("undo delete")

    // ---- accounts

    @Test fun putNewPersonInStaff() = Books().run { say("akbar ko staff mein daal do"); assertEquals(Sides.STAFF, bookOf("Akbar")) }
    @Test fun putInStaffUrdu() = Books().run { say("اکبر کو سٹاف میں ڈال دو"); assertEquals(Sides.STAFF, bookOf("Akbar")) }
    @Test fun moveSavedPersonHindi() = Books().run { say("ज़ुबैर को सप्लायर में डाल दो"); assertEquals(Sides.SUPPLIERS, bookOf("Zubair Latif")) }
    @Test fun moveWithTypos() = Books().run { say("zubar ko suplier mein kardo"); assertEquals(Sides.SUPPLIERS, bookOf("Zubair Latif")) }
    @Test fun putInStaffEnglish() = Books().run { say("put akbar in staff"); assertEquals(Sides.STAFF, bookOf("Akbar")) }
    @Test fun saidAsAFact() = Books().run { say("akbar hamara worker hai"); assertEquals(Sides.STAFF, bookOf("Akbar")); assertEquals(setOf("Akbar"), setup.people.keys - "Hamid Akram" - "Zubair Latif" - "Nargis Salim Rashid") }
    @Test fun moveSavedPerson() = Books().run { say("zubair ko suppliers mein kar do"); assertEquals(Sides.SUPPLIERS, bookOf("Zubair Latif")) }
    @Test fun moveTwoPeople() = Books().run { say("hamid aur zubair ko customers me dalo"); assertEquals(Sides.CUSTOMERS, bookOf("Hamid Akram")); assertEquals(Sides.CUSTOMERS, bookOf("Zubair Latif")) }
    @Test fun twoAccountsInOneMessage() = Books().run {
        say("akbar ko staff mein daal do aur farhan ko suppliers mein")
        assertEquals(Sides.STAFF, bookOf("Akbar"))
        assertEquals(Sides.SUPPLIERS, bookOf("Farhan"))
    }

    @Test fun aNewWorkerIsNotASavedName() = Books().run {
        say("naya staff rashid hai")
        assertEquals(Sides.STAFF, bookOf("Rashid"))
        assertEquals(Sides.CUSTOMERS, bookOf("Nargis Salim Rashid"))
    }

    @Test fun outOfStaff() = Books().run { say("hamid ko staff se nikal do"); assertEquals(Sides.PEOPLE, bookOf("Hamid Akram")) }

    @Test fun newExpenseAccount() = Books().run {
        say("diesel ka naya expense account banao")
        assertTrue(setup.books.any { it.name == "Diesel" && it.side == Side.EXP && !it.people })
    }

    @Test fun newIncomeAccountEnglish() = Books().run {
        say("make a new income account rent")
        assertTrue(setup.books.any { it.name == "Rent" && it.side == Side.INC })
    }

    @Test fun newPeopleAccountWithAPerson() = Books().run {
        say("drivers ka naya khata banao aur us mein akbar ko daal do")
        assertEquals("Drivers", bookOf("Akbar"))
    }

    @Test fun anAccountForAPerson() = Books().run { say("akbar ka khata bana do"); assertEquals(Sides.PEOPLE, bookOf("Akbar")); assertTrue("Akbar" in setup.people.keys) }

    @Test fun aGroupWithNoOneAsks() = Books().run { assertTrue('?' in say("drivers ka khata banao")!!.text); assertTrue(setup.books.isEmpty()) }

    // ---- names

    @Test fun renamePerson() = Books().run { say("salim raza ka naam salim raza butt kar do"); assertEquals("Salim Raza Butt", byId("l2")!!.person) }
    @Test fun renameEnglish() = Books().run { say("rename mohsin to mohsin javed khan"); assertEquals("Mohsin Javed Khan", byId("l3")!!.person) }
    @Test fun renameUrdu() = Books().run { say("محسن کا نام محسن علی کر دو"); assertEquals("Mohsin Ali", byId("l3")!!.person) }
    @Test fun renameShortForm() = Books().run { say("hmd ka naam hamid ali kar do"); assertEquals("Hamid Ali", byId("a1")!!.person); assertEquals("Hamid Ali", byId("w1")!!.person) }
    @Test fun renameWithKo() = Books().run { say("mohsin ko mohsin ali kar do"); assertEquals("Mohsin Ali", byId("l3")!!.person) }
    @Test fun renameBadalKe() = Books().run { say("mohsin ka naam badal ke mohsin ali kar do"); assertEquals("Mohsin Ali", byId("l3")!!.person) }

    @Test fun renameAsksWhichSalim() = Books().run {
        assertTrue('?' in say("salim ka naam salim ali kar do")!!.text)
        say("salim khan")
        assertEquals("Salim Ali", byId("l1")!!.person)
        assertEquals("Salim Raza", byId("l2")!!.person)
    }

    @Test fun twoSpellingsOfOnePerson() = Books().run {
        entries = listOf(Entry("h1", Type.DIYA, 100.0, Cat.UDHAAR, "Loans", "Hamid", "", now - day, now - day)) + entries
        say("hamid aur hamid akram ek hi banda hai")
        assertEquals("Hamid Akram", byId("h1")!!.person)
    }

    private fun mergesWork(text: String) = Books().run {
        entries = listOf(Entry("w9", Type.PURCHASE, 4_000.0, Cat.PRODUCTION, "Kachi eent molding", "Hamid Akram", "", now - day, now - day, qty = 4_000.0, unit = "1000", rate = 1_000.0)) + entries
        say(text)
        assertEquals(text, "Kachi eent", byId("w9")!!.item)
        assertEquals("Kachi eent", byId("w1")!!.item)
    }

    @Test fun mergeWorkDirected() = mergesWork("kachi eent molding ko kachi eent mein kar do")
    @Test fun mergeWorkPlain() = mergesWork("kachi eent aur kachi eent molding ek hi hain")
    @Test fun mergeWorkEnglish() = mergesWork("merge kachi eent molding into kachi eent")
    @Test fun mergeWorkJagah() = mergesWork("kachi eent molding ki jagah kachi eent likho")

    // ---- older entries

    @Test fun changeOldWithJagah() = Books().run { say("kal wali chai 5 ki jagah 8 kar do"); assertEquals(8.0, byId("x3")!!.amount, 0.01) }
    @Test fun changeOldWithKo() = Books().run { say("petrol 60 ko 70 kar do"); assertEquals(70.0, byId("x1")!!.amount, 0.01); assertEquals(80.0, byId("x5")!!.amount, 0.01) }
    @Test fun changeOldEnglish() = Books().run { say("change petrol 60 to 70"); assertEquals(70.0, byId("x1")!!.amount, 0.01) }
    @Test fun changeOldSaidTheOtherWay() = Books().run { say("kal wali chai 8 ki thi 5 nahi"); assertEquals(8.0, byId("x3")!!.amount, 0.01) }

    @Test fun changeOldAsksWhichThenTheAnswerPicks() = Books().run {
        assertTrue('?' in say("petrol ki entry 70 kar do")!!.text)
        say("pehli")
        assertEquals(70.0, byId("x1")!!.amount, 0.01)
        assertEquals(80.0, byId("x5")!!.amount, 0.01)
    }

    @Test fun oldEntryToAnotherPerson() = Books().run {
        say("hamid wali advance ki entry zubair ke khate mein daal do")
        assertEquals("Zubair Latif", byId("a1")!!.person)
        assertEquals("Hamid Akram", byId("w1")!!.person)
    }

    @Test fun oldEntryToAnotherPersonAsksWhich() = Books().run {
        assertTrue('?' in say("hamid wali entry zubair ke naam kar do")!!.text)
        say("50000 wali")
        assertEquals("Zubair Latif", byId("a1")!!.person)
        assertEquals("Hamid Akram", byId("w1")!!.person)
    }

    @Test fun changeOldByPerson() = Books().run { say("hamid wali 50000 ki entry 45000 kar do"); assertEquals(45_000.0, byId("a1")!!.amount, 0.01) }

    // ---- not changes: new entries and questions are left to the other readers

    @Test fun newEntriesAreLeft() = listOf(
        "hamid ko 500 diye", "500 hamid ko diye", "diye hamid ko 500", "hamid 500 diye", "hmd 500", "ptrl 60", "gave hamid 500", "hamid ko 500 given",
        "received 2000 from nargis", "hamid ne 500 wapis diye", "kal 200 ka grocery liya", "naya staff rashid hai, usne 5000 kachi eent dali 1100 ke hisab se",
        "nargis ko 4000 pakki eent di aur us ne 10000 diye", "staff ko 5000 tankhwah di", "hamid ko 500 wapas kar diye"
    ).forEach { leaves(it) }

    @Test fun questionsAreLeft() = listOf(
        "how much hamid owes", "hamid ka baqi kitna hai", "staff mein kaun kaun hai", "hamid ka hisab batao", "hamid ke hisab ka pdf bana do",
        "is mahine ka kharcha kitna hai", "kahan kahan laga", "kya hamid staff mein hai?", "hamid staff hai na", "hamid ka naam kya hai",
        "delete kaise karte hain", "paise wapas lao", "mera naam abrar hai"
    ).forEach { leaves(it) }

    @Test fun moreAfterASaveIsLeft() = listOf("hamid ko 250 diye", "us ne 10000 diye", "hamid ko 500 aur diye", "kal hamid ko 300 diye the", "petrol 70 tha").forEach { text ->
        leaves(text) { saved("hamid ko 250 diye", lent("j1", "Hamid Akram", 250.0)) }
    }
}
