package com.bolkhata.app

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Everyday messages read by the app's own assistant (no AI), each with what it should save, on a small made-up ledger.
 * The score and every miss are written to build/everyday.txt; the build fails when fewer pass than [floor], so a change
 * cannot quietly make the app understand less. Misses are fixed by kind (how people say things), not one message at a time.
 *
 * What a message should save, entries split by ";": "out 60 Transport" (spent; a category or a word of the item),
 * "in 5000" (money in), "gave 500 Hamid" (lent or paid a person), "got 2000 Nargis" (a person paid back or paid),
 * "took 1000 Zubair" (borrowed), "back 300 Mohsin" (paid back what was borrowed); "none" saves nothing; "ask" saves
 * nothing and asks.
 */
class EverydayTest {
    private val now = System.currentTimeMillis()
    private val day = 86_400_000L

    private val cases = listOf(
        // ---- one expense, many ways of saying it
        "petrol 60" to "out 60 Transport",
        "60 petrol" to "out 60 Transport",
        "petrol 60 ka dala" to "out 60 Transport",
        "60 ka petrol dalwaya" to "out 60 Transport",
        "petrol dalwaya 60 ka" to "out 60 Transport",
        "petrl 60" to "out 60 Transport",
        "chai 5" to "out 5 Food",
        "5 ki chai pi" to "out 5 Food",
        "chae 10" to "out 10 Food",
        "tea 15" to "out 15 Food",
        "nashta 120" to "out 120 Food",
        "khana 300 ka khaya" to "out 300 Food",
        "lunch 250" to "out 250 Food",
        "dinner 400" to "out 400 Food",
        "doodh 150" to "out 150 Food",
        "sabzi 200 ki li" to "out 200 Food",
        "groceries 1200" to "out 1200 Food",
        "rashan 3500" to "out 3500 Food",
        "roti 40" to "out 40 Food",
        "pani ki bottle 20" to "out 20",
        "rickshaw 150" to "out 150 Transport",
        "taxi 300" to "out 300 Transport",
        "uber 450" to "out 450 Transport",
        "bus ka kiraya 80" to "out 80 Transport",
        "bijli ka bill 3500" to "out 3500 Bills",
        "gas bill 900 diya" to "out 900 Bills",
        "pani ka bill 400" to "out 400 Bills",
        "ghar ka kiraya 25000" to "out 25000 Rent",
        "rent 15000 diya" to "out 15000 Rent",
        "mobile load 500" to "out 500 Mobile",
        "recharge 100" to "out 100 Mobile",
        "internet bill 2500" to "out 2500",
        "dawai 650" to "out 650 Health",
        "medicine 300" to "out 300 Health",
        "doctor ki fees 1500" to "out 1500 Health",
        "school fees 4000" to "out 4000 Education",
        "kapray 2500 ke liye" to "out 2500 Shopping",
        "suit 3000 ka liya" to "out 3000 Shopping",
        "joote 1800" to "out 1800 Shopping",
        "haircut 300" to "out 300 Personal",
        "sadqa 500 diya" to "out 500",
        "zakat 5000" to "out 5000",
        "ghar 20000 bheje" to "out 20000 Sent home",
        "id card fee 1000" to "out 1000",
        "always 2000 ka liya" to "out 2000 Always",
        "100 ki cheez li" to "out 100",
        "kharcha 500" to "out 500",
        "500 kharch kiye" to "out 500",
        "aaj 300 kharch huay" to "out 300",
        "spent 250 on lunch" to "out 250 Food",
        "paid 80 for petrol" to "out 80 Transport",
        "bought milk for 120" to "out 120 Food",
        "tea 5 coffee 20" to "out 5; out 20",
        // ---- numbers said in words or short forms
        "petrol pachas ka" to "out 50 Transport",
        "chai bees ki" to "out 20 Food",
        "kiraya pandra hazar" to "out 15000 Rent",
        "do hazar ka rashan" to "out 2000 Food",
        "derh sau ka nashta" to "out 150 Food",
        "dhai hazar bijli" to "out 2500 Bills",
        "1.5k ka petrol" to "out 1500 Transport",
        "2k groceries" to "out 2000 Food",
        "rent 25,000" to "out 25000 Rent",
        "petrol rs 60" to "out 60 Transport",
        "petrol 60 rupay" to "out 60 Transport",
        "petrol 60 riyal" to "out 60 Transport",
        // ---- several in one message
        "chai 5 aur nashta 10" to "out 5 Food; out 10 Food",
        "chai 5 ar nashta 10" to "out 5 Food; out 10 Food",
        "chai 5, petrol 60, doodh 100" to "out 5 Food; out 60 Transport; out 100 Food",
        "petrol 60 dala aur chai 10 ki pi" to "out 60 Transport; out 10 Food",
        "aaj petrol 50 aur khana 200" to "out 50 Transport; out 200 Food",
        "rickshaw 100 phir chai 20" to "out 100 Transport; out 20 Food",
        "bill 3000 bijli ka aur 800 gas ka" to "out 3000 Bills; out 800 Bills",
        // ---- money in
        "tankhwah 5000 mili" to "in 5000",
        "salary 8000 mili" to "in 8000",
        "salary aayi 8000" to "in 8000",
        "8000 salary" to "in 8000",
        "commission 700 mila" to "in 700",
        "bonus 2000 mila" to "in 2000",
        "got salary 5000" to "in 5000",
        "kiraya 10000 aaya" to "in 10000",
        // ---- lending and paying people
        "hamid ko 500 diye" to "gave 500 Hamid",
        "hamid ko 500 de diye" to "gave 500 Hamid",
        "500 hamid ko diye" to "gave 500 Hamid",
        "hamid 500 diye" to "gave 500 Hamid",
        "hmd ko 300 diy" to "gave 300 Hamid",
        "hamid ko das hazar diye" to "gave 10000 Hamid",
        "hamid ko derh hazar diye" to "gave 1500 Hamid",
        "mohsin ko 1000 udhaar diya" to "gave 1000 Mohsin",
        "mohsin ko 1000 udhar diye" to "gave 1000 Mohsin",
        "mohsin ke naam 1000 likh do" to "gave 1000 Mohsin",
        "mohsin k nam 1000 likho" to "gave 1000 Mohsin",
        "tariq javed ko 300 diya" to "gave 300 Tariq Javed",
        "farooq ko 1500 udhaar diya" to "gave 1500 Farooq",
        "ahmed ko 1000 diye" to "gave 1000 Ahmed",
        "gave ahmed 1000" to "gave 1000 Ahmed",
        "gave 1000 to ahmed" to "gave 1000 Ahmed",
        "lent bilal 2000" to "gave 2000 Bilal",
        "salim khan ko 200 diye" to "gave 200 Salim Khan",
        "salim ko 200 diye" to "ask",
        "hamid ko 500 diye aur zubair ko 300" to "gave 500 Hamid; gave 300 Zubair",
        "hamid ko 500 zubair ko 300 diye" to "gave 500 Hamid; gave 300 Zubair",
        "hamid ko 500 ar zubair ko 300 diye" to "gave 500 Hamid; gave 300 Zubair",
        // ---- people paying back or paying
        "nargis ne 2000 diye" to "got 2000 Nargis",
        "nargis se 2000 mile" to "got 2000 Nargis",
        "nargis se 2000 liye" to "got 2000 Nargis",
        "nargis ne 2000 bheje" to "got 2000 Nargis",
        "nargis se dhai lakh mile" to "got 250000 Nargis",
        "nargis se paune do lakh mile" to "got 175000 Nargis",
        "salim raza ne 400 wapis kar diye" to "got 400 Salim Raza",
        "salim raza ne 400 wapas kiye" to "got 400 Salim Raza",
        "mohsin ne 500 lota diye" to "got 500 Mohsin",
        "got 300 from mohsin" to "got 300 Mohsin",
        "mohsin paid 300" to "got 300 Mohsin",
        // ---- borrowing and paying it back
        "zubair se 1000 udhaar liya" to "took 1000 Zubair",
        "zubair se 1000 udhar liye" to "took 1000 Zubair",
        "borrowed 1000 from zubair" to "took 1000 Zubair",
        "zubair ko 1000 wapis kiye" to "back 1000 Zubair",
        "zubair ka 1000 wapas kar diya" to "back 1000 Zubair",
        // ---- a day said
        "kal 200 ka grocery liya" to "out 200 Food",
        "kal petrol 60" to "out 60 Transport",
        "parso hamid ko 500 diye" to "gave 500 Hamid",
        "aaj subah chai 10" to "out 10 Food",
        // ---- a long story
        "aaj hamid ko 500 diye, petrol 60 dala, chai 10 ki pi aur nargis se 3000 mile" to
            "gave 500 Hamid; out 60 Transport; out 10 Food; got 3000 Nargis",
        "mere pas 10000 thay jis me se mene 1000 ahmed ko diye 100 ki cheez li ar 2000 k always liye" to
            "gave 1000 Ahmed; out 100; out 2000 Always",
        "mere pas 5000 the us me se 2000 ka rashan liya aur 500 petrol" to "out 2000 Food; out 500 Transport",
        "subah 50 ki chai pi phir 200 ka petrol dalwaya aur shaam ko 300 ka khana" to "out 50 Food; out 200 Transport; out 300 Food",
        "aaj ka kharcha: chai 10, roti 40, sabzi 120" to "out 10 Food; out 40 Food; out 120 Food",
        "pehle petrol 60 phir chai 10" to "out 60 Transport; out 10 Food",
        "salary 8000 aayi aur 2000 ghar bheje" to "in 8000; out 2000 Sent home",
        "nargis se 3000 mile aur hamid ko 1000 diye" to "got 3000 Nargis; gave 1000 Hamid",
        // ---- typing slips
        "pertol 60" to "out 60 Transport",
        "petrool 70" to "out 70 Transport",
        "hamid ko 500 diey" to "gave 500 Hamid",
        "hamid ko 500 diyay" to "gave 500 Hamid",
        "nargis se 2000 mily" to "got 2000 Nargis",
        "bijly ka bil 3000" to "out 3000 Bills",
        "gorcery 500" to "out 500 Food",
        // ---- not new entries: questions, talk, changes
        "kitna kharcha hua" to "none",
        "is mahine kitna kharch hua?" to "none",
        "hamid ka hisab" to "none",
        "hamid ka khata dikhao" to "none",
        "petrol kitna dala" to "none",
        "aaj ka total" to "none",
        "hello" to "none",
        "kya hal hai" to "none",
        "kese ho" to "none",
        "shukriya" to "none",
        "thank you" to "none",
        "ok" to "none",
        "petrol wali entry delete karo" to "none",
        "last entry hata do" to "none",
        "ye 70 tha" to "none",
        "nahi 400 tha" to "none",
        "hamid ko kitne diye" to "none",
        "mere pas kitne paise hain" to "none",
        "mere pas 10000 hain" to "none",
        "hamid ke pas mere 500 hain" to "none",
        // ---- a second batch, written after the first was fixed, to see how well new sayings are understood
        "aaj 40 ki chai aur 60 ka samosa" to "out 40 Food; out 60",
        "biryani 350" to "out 350 Food",
        "pizza 1200 ka mangwaya" to "out 1200 Food",
        "fruit 300 ke liye" to "out 300 Food",
        "anday 250" to "out 250 Food",
        "chicken 900 ka liya" to "out 900 Food",
        "atta 1800" to "out 1800 Food",
        "cheeni 300 ki" to "out 300 Food",
        "car wash 500" to "out 500",
        "parking 100" to "out 100",
        "toll tax 200" to "out 200",
        "diesel 5000" to "out 5000 Transport",
        "bike ka petrol 300" to "out 300 Transport",
        "mechanic ko 1500 diye" to "out 1500",
        "tyre 4000 ka" to "out 4000",
        "cylinder 3000" to "out 3000 Bills",
        "wifi bill 2000" to "out 2000",
        "netflix 1100" to "out 1100",
        "light ka bill 2200" to "out 2200 Bills",
        "dentist 3000" to "out 3000 Health",
        "test karwaye 2500 ke" to "out 2500",
        "kitaben 1500 ki li" to "out 1500",
        "tuition fees 3000" to "out 3000 Education",
        "shoes 2500" to "out 2500 Shopping",
        "shirt 1200 ki li" to "out 1200 Shopping",
        "barber 200" to "out 200 Personal",
        "shadi ka gift 5000" to "out 5000",
        "masjid me 1000 diye" to "out 1000",
        "ammi ko 5000 bheje" to "out 5000 Sent home",
        "abbu ko 10000 bheje" to "out 10000 Sent home",
        "visa fee 2000" to "out 2000",
        "spent 60 on fuel" to "out 60 Transport",
        "paid 3500 electricity bill" to "out 3500 Bills",
        "bought vegetables 200" to "out 200 Food",
        "breakfast 150" to "out 150 Food",
        "chai pani 100" to "out 100 Food",
        "do sau ka petrol" to "out 200 Transport",
        "teen hazar kiraya" to "out 3000 Rent",
        "paanch sau ki dawai" to "out 500 Health",
        "saadhe teen hazar ka bill" to "out 3500 Bills",
        "sava do sau ki chai" to "out 225 Food",
        "50 riyal petrol" to "out 50 Transport",
        "petrol 60 sar" to "out 60 Transport",
        "petrol 60.5" to "out 60.5 Transport",
        "chai 5 nashta 10 petrol 50" to "out 5 Food; out 10 Food; out 50 Transport",
        "5 chai 10 nashta" to "out 5 Food; out 10 Food",
        "rickshaw 100 aur wapsi pe 120" to "out 100 Transport; out 120",
        "freelance se 15000 aaye" to "in 15000",
        "dukan se 3000 ki kamai hui" to "in 3000",
        "profit 2000" to "in 2000",
        "received salary 9000" to "in 9000",
        "bilal ko 3000 udhaar diye" to "gave 3000 Bilal",
        "ahmed ko 700 de diye" to "gave 700 Ahmed",
        "usman ko 2500 bheje" to "gave 2500 Usman",
        "kamran ne 1000 diye" to "got 1000 Kamran",
        "kamran se 1000 mil gaye" to "got 1000 Kamran",
        "mohsin ne pese wapis kar diye 1200" to "got 1200 Mohsin",
        "mohsin ne 1200 lauta diye" to "got 1200 Mohsin",
        "tariq se 5000 udhaar liye" to "took 5000 Tariq",
        "tariq ko 5000 lauta diye" to "back 5000 Tariq",
        "hamid ko advance 2000 diya" to "gave 2000 Hamid",
        "zubair ko 500 ki mazdoori di" to "gave 500 Zubair",
        "nargis ne 10000 jama karaye" to "got 10000 Nargis",
        "kal raat khana 600" to "out 600 Food",
        "parson bijli 2000" to "out 2000 Bills",
        "aaj 2 chai 20 ki" to "out 20 Food",
        "chai 10, hamid ko 200 diye" to "out 10 Food; gave 200 Hamid",
        "petrol 100 aur ahmed ko 500" to "out 100 Transport; gave 500 Ahmed",
        "salary 8000 aayi, 3000 ghar bheje, 500 petrol" to "in 8000; out 3000 Sent home; out 500 Transport",
        "mere pas 2000 hain is me se 500 ka petrol dalwaya" to "out 500 Transport",
        "jeb me 1000 the, 200 chai pe lag gaye" to "out 200 Food",
        "chae 10 ki" to "out 10 Food",
        "petrool dala 80" to "out 80 Transport",
        "bijlee 900" to "out 900 Bills",
        "kiraiya 12000" to "out 12000 Rent",
        "hmid ko 400 diye" to "gave 400 Hamid",
        "zubar se 300 udhaar liye" to "took 300 Zubair",
        "kitne paise bache" to "none",
        "pichle mahine ka kharcha" to "none",
        "sab se zyada kharcha kahan hua" to "none",
        "hamid ne kitna kaam kiya" to "none",
        "theek hai" to "none",
        "acha" to "none",
        // ---- heard by the mic: how it was said, what it was like, a word twice
        "1500 ke achcha kapde liye" to "out 1500 Kapde",
        "juti 200 ki li" to "out 200 Shopping",
        "bola food food 500" to "out 500 Food",
    )

    /**
     * Many more, made from parts so every way of saying a thing meets every way of saying a number: things with
     * their account, people with the ways money goes to them and comes from them, and the nods and small talk
     * that must save nothing.
     */
    private val made: List<Pair<String, String>> = run {
        val things = listOf("petrol" to "Transport", "chai" to "Food", "doodh" to "Food", "sabzi" to "Food", "bijli ka bill" to "Bills",
            "kiraya" to "Rent", "mobile load" to "Mobile", "dawai" to "Health", "kapre" to "Shopping", "rickshaw" to "Transport",
            "groceries" to "Food", "internet" to "Mobile", "roti" to "Food", "taxi" to "Transport", "joote" to "Shopping", "gas bill" to "Bills")
        val amounts = listOf("500" to 500.0, "1500" to 1500.0, "1,500" to 1500.0, "2k" to 2000.0, "2 hazar" to 2000.0, "do hazar" to 2000.0,
            "paanch sau" to 500.0, "5 sau" to 500.0, "derh hazar" to 1500.0, "250" to 250.0, "75" to 75.0, "1.5k" to 1500.0)
        val spend = listOf("%t %a", "%a %t", "%a ka %t", "%t %a ka liya", "%t pe %a kharch kiye", "aaj %t %a", "kal %a ka %t", "spent %a on %t")
        val out = mutableListOf<Pair<String, String>>()
        var k = 0
        for ((t, cat) in things) for (form in spend) {
            val (a, n) = amounts[k++ % amounts.size]
            out += form.replace("%t", t).replace("%a", a) to "out ${plainAmount(n)} $cat"
        }
        val people = listOf("hamid" to "Hamid", "mohsin" to "Mohsin", "ahmed" to "Ahmed", "bilal" to "Bilal", "usman" to "Usman", "kamran" to "Kamran",
            "tariq" to "Tariq", "farooq" to "Farooq", "salim khan" to "Salim Khan", "salim raza" to "Salim Raza")
        val give = listOf("%p ko %a diye", "%p ko %a de diye", "%p ko %a dy diye", "%p ko %a diya", "%p ko %a dia", "%p ko %a bheje", "%a %p ko diye",
            "%p ko %a udhaar diye", "gave %p %a", "%p ko %a diey")
        val get = listOf("%p se %a mile", "%p se %a mil gaye", "%p ne %a wapis kiye", "%p ne %a wapas kar diye", "%p ne %a lota diye", "got %a from %p")
        for ((p, who) in people) {
            for (form in give) { val (a, n) = amounts[k++ % amounts.size]; out += form.replace("%p", p).replace("%a", a) to "gave ${plainAmount(n)} $who" }
            for (form in get) { val (a, n) = amounts[k++ % amounts.size]; out += form.replace("%p", p).replace("%a", a) to "got ${plainAmount(n)} $who" }
        }
        val nods = listOf("ji", "jee", "g", "gi", "haan", "han", "ha", "haa", "ok", "okay", "okk", "yes", "theek hai", "thik hai", "acha", "achha",
            "shukriya", "thanks", "thank you", "hello", "hi", "salam", "assalam o alaikum", "kya hal hai", "kaise ho", "kese ho", "kase ho", "hru",
            "good morning", "bye", "allah hafiz", "nahi", "no", "nhi", "kuch nahi", "bas")
        out + nods.map { it to "none" }
    }

    /** One expected entry: what kind, how much, and a word of its person, category or item. */
    private class Want(val kind: String, val amount: Double, val word: String)

    private fun wants(spec: String): List<Want> = spec.split(";").map { it.trim() }.filter { it != "none" && it != "ask" }.map { p ->
        val t = p.split(Regex("""\s+"""), limit = 3)
        Want(t[0], t[1].toDouble(), t.getOrElse(2) { "" })
    }

    private val kinds = mapOf("out" to Type.KHARCHA, "in" to Type.AAMDANI, "gave" to Type.DIYA, "got" to Type.WAPIS_MILA, "took" to Type.LIYA, "back" to Type.WAPIS_DIYA)

    private fun fits(e: Entry, w: Want): Boolean = e.type == kinds[w.kind] && kotlin.math.abs(e.amount - w.amount) < 0.01 && (w.word.isBlank() ||
        e.person.contains(w.word, true) || e.category.label.contains(w.word, true) || e.item.contains(w.word, true))

    /** The books every message starts from: two workers, a customer, three people with loans. */
    private fun books(): Triple<AiAgent.Host, () -> List<Entry>, Set<String>> {
        var entries = listOf(
            Entry("w1", Type.PURCHASE, 20_000.0, Cat.PRODUCTION, "Kachi eent", "Hamid Akram", "", now - 6 * day, now - 6 * day, qty = 20_000.0, unit = "1000", rate = 1_000.0),
            Entry("s1", Type.SALE, 60_000.0, Cat.SALES, "Pakki eent", "Nargis Salim Rashid", "", now - 4 * day, now - 4 * day, qty = 5_000.0, unit = "1000", rate = 12_000.0),
            Entry("l1", Type.DIYA, 500.0, Cat.UDHAAR, "Loans", "Salim Khan", "", now - 8 * day, now - 8 * day),
            Entry("l2", Type.DIYA, 900.0, Cat.UDHAAR, "Loans", "Salim Raza", "", now - 7 * day, now - 7 * day),
            Entry("l3", Type.DIYA, 1_200.0, Cat.UDHAAR, "Loans", "Mohsin Javed", "", now - 9 * day, now - 9 * day),
            Entry("b1", Type.LIYA, 2_000.0, Cat.UDHAAR, "Loans", "Zubair Latif", "", now - 9 * day, now - 9 * day),
            Entry("x1", Type.KHARCHA, 60.0, Cat.TRANSPORT, "Petrol", "", "", now - 2 * day, now - 2 * day)
        )
        val base = entries.map { it.id }.toSet()
        var setup = SideSetup(people = mapOf("Hamid Akram" to Sides.STAFF, "Zubair Latif" to Sides.STAFF, "Nargis Salim Rashid" to Sides.CUSTOMERS))
        val host = AiAgent.Host(
            entries = { entries }, add = { entries = it + entries },
            update = { changed -> entries = entries.map { e -> changed.firstOrNull { it.id == e.id } ?: e } }, trash = { ids -> entries = entries.filter { it.id !in ids } },
            names = { (entries.map { it.person } + setup.people.keys).map { it.trim() }.filter { it.isNotBlank() }.distinct() },
            currency = "PKR", setup = { setup }, saveSetup = { setup = it }
        )
        return Triple(host, { entries }, base)
    }

    @Test
    fun everydayMessages() {
        val body = StringBuilder()
        var passed = 0
        for ((said, spec) in cases + made) {
            val (host, entries, base) = books()
            val answer = runCatching { LocalAgent.run(said, host, now) }.getOrNull()
            val added = entries().filter { it.id !in base }
            val want = wants(spec)
            val asked = answer != null && ('?' in answer.text || answer.ask.isNotBlank())
            val left = added.toMutableList()
            val ok = want.all { w -> left.firstOrNull { fits(it, w) }?.also { left.remove(it) } != null } && left.isEmpty() && (spec.trim() != "ask" || asked)
            if (ok) passed++ else body.append("MISS  ").append(said).append("\n      wanted: ").append(spec)
                .append("\n      got: ").append(added.joinToString("; ") { "${it.type} ${plainAmount(it.amount)} ${it.person.ifBlank { it.item }} (${it.category.label})" }.ifBlank { "nothing" })
                .append(answer?.text?.takeIf { it.isNotBlank() }?.let { " | said: " + it.take(100).replace("\n", " ") }.orEmpty()).append('\n')
        }
        val all = cases.size + made.size
        val out = "Everyday messages: $passed of $all understood\n\n$body"
        File("build").mkdirs()
        File("build/everyday.txt").writeText(out)
        println(out)
        assertTrue("The app understood $passed of $all everyday messages; it understood $floor before", passed >= floor)
    }

    /** Every way of saying yes answers the app's question: after "Move them all to Trash?" the entries go. */
    @Test
    fun everyYesAnswersTheAppsQuestion() {
        for (yes in listOf("ji", "jee", "g", "gi", "haan", "han", "ha", "haa", "ok", "okay", "yes", "theek hai", "kar do", "haan kar do", "ji kar do")) {
            val (host, entries, _) = books()
            val ask = LocalAgent.run("sari entries delete kar do", host, now)?.text.orEmpty()
            assertTrue("asks first: $ask", ask.endsWith("?"))
            LocalAgent.run(yes, host, now, lastShown = ask, recent = "Person: sari entries delete kar do\nApp: $ask")
            assertTrue("\"$yes\" did the delete", entries().isEmpty())
        }
    }

    private fun plainAmount(v: Double) = if (v % 1.0 == 0.0) v.toLong().toString() else v.toString()

    /** How many of [cases] the app understood last time: it may only go up. */
    private val floor = 566
}
