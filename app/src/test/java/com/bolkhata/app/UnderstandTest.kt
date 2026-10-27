package com.bolkhata.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

/** Smart understanding: misspelled Roman Urdu / English, typed or spoken. */
class UnderstandTest {
    private val contacts = listOf("Ashraf", "Abrar", "Imran", "Muhammad Imran", "Ashfaq", "Nisar", "Bilal")

    private fun read(s: String) = Understand.read(s, contacts)

    /** The entries the sentence makes (with the said clock time applied). */
    private fun entries(s: String): List<Entry> {
        val u = read(s)
        assertEquals("kind of: $s", Understand.Kind.ENTRY, u.kind)
        return Parser.parse(u.text).entries.map { if (u.clock != null) Understand.at(it, u.clock!!) else it }
    }

    private fun one(s: String, type: Type, amount: Double, cat: Cat? = null, item: String? = null, person: String? = null): Entry {
        val list = entries(s)
        assertEquals("one entry from: $s -> ${read(s).text}", 1, list.size)
        val e = list.first()
        assertEquals("type of: $s", type, e.type)
        assertEquals("amount of: $s", amount, e.amount, 0.001)
        if (cat != null) assertEquals("category of: $s", cat, e.category)
        if (item != null) assertEquals("item of: $s", item, e.item)
        if (person != null) assertEquals("person of: $s", person, e.person)
        return e
    }

    private fun hourOf(e: Entry) = Calendar.getInstance().apply { timeInMillis = e.date }.get(Calendar.HOUR_OF_DAY)

    // ---- the six examples

    @Test fun petrolTodayAtSevenInTheMorning() {
        val e = one("aj sube sat bajay 70 ka ptrol dalwaya", Type.KHARCHA, 70.0, Cat.TRANSPORT, "Petrol")
        assertEquals(7, hourOf(e))
        val today = Calendar.getInstance()
        val d = Calendar.getInstance().apply { timeInMillis = e.date }
        assertEquals(today.get(Calendar.DAY_OF_YEAR), d.get(Calendar.DAY_OF_YEAR))
    }

    @Test fun petrolSpellings() {
        for (w in listOf("patrol", "petrl", "petrol", "ptrl", "ptrol", "petrool", "pitrol")) {
            one("$w 50", Type.KHARCHA, 50.0, Cat.TRANSPORT, "Petrol")
        }
    }

    @Test fun deleteLastEntryIsACommand() {
        assertEquals(Understand.Kind.DELETE_LAST, read("last antry delt kr do").kind)
        assertEquals(Understand.Kind.DELETE_LAST, read("delet last entri").kind)
    }

    @Test fun openAshrafsKhata() {
        val u = read("ashrf ka khata khlo")
        assertEquals(Understand.Kind.OPEN_KHATA, u.kind)
        assertEquals("Ashraf", u.contact)
    }

    @Test fun moneyInFromAbrar() {
        val e = one("abraar ne 50 diya", Type.LIYA, 50.0, person = "Abrar")
        assertTrue(e.type == Type.LIYA || e.type == Type.WAPIS_MILA || e.type == Type.AAMDANI)
    }

    @Test fun rotiForTwenty() {
        one("20 ka rote khaya", Type.KHARCHA, 20.0, Cat.KHANA, "Bread / Roti")
    }

    // ---- more misspellings

    @Test fun teaYesterdayEvening() {
        val e = one("kl sham 5 baje chae 3", Type.KHARCHA, 3.0, Cat.KHANA)
        assertEquals(17, hourOf(e))
    }

    @Test fun milk() { one("dodh 6 lia", Type.KHARCHA, 6.0, Cat.KHANA) }
    @Test fun lentToNisar() { one("nisaar ko 500 dia", Type.DIYA, 500.0, person = "Nisar") }
    @Test fun rechargeInWords() { one("pachas ka recharj krwaya", Type.KHARCHA, 50.0, Cat.MOBILE) }
    @Test fun oneAndAHalfHundred() { one("dedh sau ki sabji", Type.KHARCHA, 150.0, Cat.KHANA) }
    @Test fun twoAndAHalfThousandRent() { one("dhai hazar kiraa diya", Type.KHARCHA, 2500.0, Cat.RENT) }

    @Test fun biryaniAtTenAtNight() {
        val e = one("raat 10 baje biryane 25", Type.KHARCHA, 25.0, Cat.KHANA)
        assertEquals(22, hourOf(e))
    }

    @Test fun taxi() { one("teksi 15", Type.KHARCHA, 15.0, Cat.TRANSPORT, "Taxi") }
    @Test fun electricity() { one("bijlee ka bill 120", Type.KHARCHA, 120.0, Cat.BIJLI) }
    @Test fun medicine() { one("dawai 35 ki", Type.KHARCHA, 35.0, Cat.SEHAT) }
    @Test fun sentHome() { one("2 hazar ghar bheje", Type.KHARCHA, 2000.0, Cat.GHAR) }
    @Test fun repaidByAshfaq() { one("ashfq ne 300 wapis kiye", Type.WAPIS_MILA, 300.0, person = "Ashfaq") }
    @Test fun undo() { assertEquals(Understand.Kind.UNDO, read("undoo").kind) }
    @Test fun summary() { assertEquals(Understand.Kind.SUMMARY, read("sumary dikhao").kind) }
    @Test fun breakfastDayBeforeYesterday() { one("parso nashtaa 12", Type.KHARCHA, 12.0, Cat.KHANA) }
    @Test fun hundredTwentyInWords() { one("ek sau bees ka petroll", Type.KHARCHA, 120.0, Cat.TRANSPORT, "Petrol") }
    @Test fun taxiMorning() { one("texi 18 subha", Type.KHARCHA, 18.0, Cat.TRANSPORT, "Taxi") }
    @Test fun waterFiveInWords() { one("panch riyal pani", Type.KHARCHA, 5.0, Cat.KHANA) }
    @Test fun lunchYesterdayTwoPm() {
        val e = one("kal dopahar 2 baje khana 30", Type.KHARCHA, 30.0, Cat.KHANA)
        assertEquals(14, hourOf(e))
    }
    @Test fun openAbrarsKhata() {
        val u = read("abrar ka khata dikhao")
        assertEquals(Understand.Kind.OPEN_KHATA, u.kind); assertEquals("Abrar", u.contact)
    }
    @Test fun dieselForty() { one("chalis ka diesal dalwaya", Type.KHARCHA, 40.0, Cat.TRANSPORT) }
    @Test fun roti() { one("das riyal ki roteyan", Type.KHARCHA, 10.0, Cat.KHANA, "Bread / Roti") }
    @Test fun carWash() { one("car wash krwai 20", Type.KHARCHA, 20.0, Cat.TRANSPORT) }
    @Test fun borrowedFromBilal() { one("bilal se 100 liye", Type.LIYA, 100.0, person = "Bilal") }

    @Test fun twoImransAskWhichOne() {
        val u = read("imraan se 200 udhar liya")
        assertTrue(u.choices.containsAll(listOf("Imran", "Muhammad Imran")) || u.contact != null)
    }

    @Test fun pickedNameIsRemembered() {
        val u = Understand.read("imraan se 200 liya", contacts, mapOf("@imraan" to "Muhammad Imran"))
        assertEquals("Muhammad Imran", u.contact)
    }

    @Test fun doIsNotANumberInKrDo() {
        assertTrue(!read("last antry delt kr do").text.contains(" 2"))
        assertNotNull(read("chai do").text)
    }
}
