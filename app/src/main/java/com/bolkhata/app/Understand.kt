package com.bolkhata.app

import java.util.Calendar

/**
 * The "smart understanding" step for typed and spoken input, fully offline:
 * cleans the spelling ([Normalizer]), matches names to saved contacts ("ashrf" -> "Ashraf"), reads a clock time
 * ("subah 7 baje"), and decides what the sentence is: a new entry, or a command (delete last entry, undo,
 * open someone's khata, summary).
 */
object Understand {

    enum class Kind { ENTRY, DELETE_LAST, UNDO, OPEN_KHATA, SUMMARY }

    /** A clock time said in the sentence. */
    class Clock(val hour: Int, val minute: Int) {
        fun label(): String {
            val h = if (hour % 12 == 0) 12 else hour % 12
            return "$h:" + minute.toString().padStart(2, '0') + if (hour < 12) " AM" else " PM"
        }
    }

    class Result(
        val kind: Kind,
        /** The cleaned sentence, with the contact's saved name in place of how it was said. */
        val text: String,
        /** The contact the sentence is about, when one matched. */
        val contact: String?,
        /** Two or more saved contacts that match equally: ask which one. */
        val choices: List<String>,
        /** The word that matched a contact, as it was said (to replace once the person picks). */
        val nameWord: String?,
        val clock: Clock?,
        val fixes: List<Pair<String, String>>
    )

    private val notNames = setOf(
        "aaj", "kal", "parson", "subah", "dopahar", "shaam", "raat", "baje", "abhi", "diya", "liya", "khaya", "piya",
        "dalwaya", "kharida", "bheja", "wapas", "udhaar", "kholo", "dikhao", "karo", "delete", "entry", "last", "khata",
        "undo", "summary", "petrol", "diesel", "roti", "chai", "doodh", "pani", "khana", "nashta", "sabzi", "chawal",
        "biryani", "taxi", "kiraya", "bijli", "recharge", "dawai", "doctor", "mobile", "ghar", "riyal", "riyals",
        "rupay", "rupees", "wala", "wali", "wale", "liye", "mein", "main", "aur", "kuch", "sab", "nahi", "please",
        // Everyday chat words that sound like short names ("hai", "hal" are close to many 4-letter names).
        "hai", "hain", "kya", "kia", "hal", "haal", "kaise", "kaisa", "kaisi", "yar", "yaar", "acha", "accha", "theek", "thik",
        "han", "haan", "hello", "hey", "salam", "how", "are", "you", "what", "the", "and", "tha", "thi", "the", "mujhe", "mujh",
        "sahi", "lekin", "voice", "urdu", "english", "roman", "transaction", "transactions", "chalta", "karta", "kholo", "dikhao",
        "batao", "kitna", "kitne", "kitni", "hua", "huwa", "tum", "aap", "mera", "meri", "mere", "apna", "apni"
    )

    /** Common names, so a misspelled name is fixed even before it is saved ("ashrf" -> "Ashraf"). */
    private val commonNames = listOf(
        "Ashraf", "Abrar", "Imran", "Nisar", "Bilal", "Ahmed", "Ahmad", "Muhammad", "Ali", "Hassan", "Hussain", "Usman", "Umar",
        "Shahbaz", "Zubair", "Waleed", "Rahim", "Akram", "Afzal", "Nadir", "Tariq", "Khalid", "Arshad", "Asif", "Aslam", "Naeem",
        "Saleem", "Shahid", "Sajid", "Rashid", "Majid", "Kashif", "Faisal", "Farhan", "Imtiaz", "Ijaz", "Javed", "Junaid", "Kamran",
        "Rizwan", "Rehman", "Shafiq", "Shoaib", "Sohail", "Tahir", "Waqas", "Yasir", "Zahid", "Zeeshan", "Abbas", "Adnan", "Amir",
        "Anwar", "Asad", "Babar", "Danish", "Ehsan", "Fahad", "Ghulam", "Haider", "Hamza", "Iqbal", "Irfan", "Jamil", "Latif",
        "Mansoor", "Mushtaq", "Nadeem", "Noman", "Qasim", "Raza", "Sabir", "Saeed", "Shakeel", "Sharif", "Tanveer", "Wasim",
        "Younis", "Zafar", "Ashfaq", "Abdullah", "Aziz", "Sultan", "Shehryar", "Arslan", "Touseef", "Akbar", "Anees", "Aqeel",
        "Arif", "Asim", "Atif", "Azhar", "Fawad", "Hafeez", "Hanif", "Ishaq", "Jabbar", "Kaleem", "Khurram", "Liaqat", "Mehmood",
        "Mohsin", "Munir", "Nasir", "Nawaz", "Obaid", "Pervez", "Rafiq", "Rauf", "Sadiq", "Salman", "Sami", "Shafqat", "Shakir",
        "Sharjeel", "Shaukat", "Talha", "Taimoor", "Waheed", "Yaqoob", "Yousaf", "Zain", "Zaheer", "Fatima", "Ayesha", "Maryam",
        "Sana", "Saima", "Nadia", "Rabia", "Hina", "Amina", "Khadija", "Zainab", "Uzma", "Sadia", "Shazia", "Bushra", "Farah"
    )
    /** Everyday words that sound like a name ("saman" ~ Salman) but are things. */
    private val notNameItems = setOf("saman", "samaan", "zafran", "zafraan", "sona", "chandi", "kamra", "kamre", "dukan", "makan",
        "nasha", "rasta", "raasta", "sabun", "kapra", "kapre", "imli", "amrood", "anaar", "badam", "kishmish", "masala", "haldi")
    private val nameMarkers = setOf("ne", "ko", "se", "ka", "ki", "ke", "nay", "say", "sy", "ny")

    fun isCommonName(word: String) = commonNames.any { it.equals(word, true) }

    /** A word said like a name ("ashrf ne", "nisaar ko") that is a misspelled common name -> the right spelling. */
    fun fixName(word: String): String? {
        if (word.length < 4 || word in notNames || word in notNameItems) return null
        commonNames.firstOrNull { it.equals(word, true) }?.let { return null }  // already right
        return commonNames.filter { closeName(word, it.lowercase()) }
            .maxByOrNull { Normalizer.similarity(word, it.lowercase()) }
    }

    /** Saved contacts whose name matches a word of the sentence (by spelling or by sound). */
    fun matchContact(words: List<String>, contacts: Collection<String>): Triple<String?, List<String>, String?> {
        val names = contacts.filter { it.isNotBlank() }.distinctBy { it.lowercase() }
        for (w in words) {
            if (w.length < 3 || w in notNames || w.any { it.isDigit() }) continue
            // A contact named exactly this word wins straight away, unless another saved name has it too
            // ("ali" with "Ali" and "Ali Abrar" saved): then all of them are offered.
            names.firstOrNull { it.equals(w, true) }?.let { exact ->
                val shared = names.filter { n -> n != exact && n.lowercase().split(Regex("[^\\p{L}]+")).contains(w.lowercase()) }
                if (shared.isEmpty()) return Triple(exact, emptyList(), w)
            }
            val scored = names.mapNotNull { n ->
                val best = n.lowercase().split(Regex("[^\\p{L}]+")).filter { it.length >= 3 }
                    .maxOfOrNull { part -> if (closeName(w, part)) Normalizer.similarity(w, part) else 0.0 } ?: 0.0
                if (best > 0) n to best else null
            }.sortedByDescending { it.second }
            if (scored.isEmpty()) continue
            val top = scored.first().second
            val tied = scored.filter { top - it.second < 0.03 }.map { it.first }
            if (tied.size == 1) return Triple(tied.first(), emptyList(), w)
            // Several names share this word ("Salim"): the one matching the most words said wins ("Salim Rashid").
            val best = mostWordsMatched(words, tied)
            return if (best.size == 1) Triple(best.first(), emptyList(), w) else Triple(null, best, w)
        }
        return Triple(null, emptyList(), null)
    }

    /** Saved names that look like a word of the message (sound, spelling or first letters), at most [limit]. */
    fun similarNames(words: List<String>, contacts: Collection<String>, limit: Int = 8): List<String> {
        val ws = words.map { it.lowercase() }.filter { it.length >= 3 && it !in notNames && it.none(Char::isDigit) }
        if (ws.isEmpty()) return emptyList()
        return contacts.filter { it.isNotBlank() }.distinctBy { it.lowercase() }.filter { n ->
            val parts = n.lowercase().split(Regex("[^\\p{L}]+")).filter { it.length >= 3 }
            ws.any { w -> parts.any { p -> closeName(w, p) || p.startsWith(w.take(3)) } }
        }.take(limit)
    }

    /** Of [names], those that match the largest number of the [words] said (by spelling or sound). */
    fun mostWordsMatched(words: List<String>, names: List<String>): List<String> {
        val counts = names.associateWith { n ->
            val parts = n.lowercase().split(Regex("[^\\p{L}]+")).filter { it.length >= 3 }
            words.count { w -> w.length >= 3 && w !in notNames && parts.any { p -> closeName(w, p) } }
        }
        val max = counts.values.maxOrNull() ?: return names
        return counts.filterValues { it == max }.keys.toList()
    }

    /** "ashrf" ~ "ashraf", "abraar" ~ "abrar", "imraan" ~ "imran": close in spelling or in sound. */
    fun closeName(said: String, name: String): Boolean {
        if (said == name) return true
        val sim = Normalizer.similarity(said, name)
        val dist = Normalizer.distance(said, name)
        val maxEdits = if (name.length <= 4) 1 else 2
        return (sim >= 0.88 && dist <= maxEdits) || (Names.key(said).length >= 3 && Names.key(said) == Names.key(name))
    }

    private val clockRx = Regex("""(?<![\d.])(\d{1,2})(?:[:.](\d{2}))?\s*baje\b""")

    /** "subah 7 baje" -> 7:00, "shaam 6 baje" -> 18:00, "raat 10:30 baje" -> 22:30. Removed from the text. */
    fun clock(text: String): Pair<Clock?, String> {
        val m = clockRx.find(text) ?: return null to text
        var h = m.groupValues[1].toInt()
        val min = m.groupValues[2].toIntOrNull() ?: 0
        if (h > 23 || min > 59) return null to text
        val w = text.split(Regex("\\s+")).toSet()
        if (h in 1..11) {
            when {
                "shaam" in w -> h += 12
                "raat" in w -> if (h >= 6) h += 12
                "dopahar" in w -> if (h <= 5) h += 12
            }
        }
        val rest = text.removeRange(m.range).replace(Regex("\\s+"), " ").trim()
        return Clock(h, min) to rest
    }

    /** The entry's own day at the said time. */
    fun at(e: Entry, c: Clock): Entry {
        val t = Calendar.getInstance().apply {
            timeInMillis = e.date
            set(Calendar.HOUR_OF_DAY, c.hour); set(Calendar.MINUTE, c.minute)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        return e.copy(date = t, createdAt = t)
    }

    fun read(input: String, contacts: Collection<String> = emptyList(), learned: Map<String, String> = emptyMap()): Result {
        val n = Normalizer.normalize(input, learned, contacts)
        val (clock, noClock) = clock(n.text)
        val words = noClock.split(" ").filter { it.isNotBlank() }
        // A name the person picked before ("imraan" -> "Muhammad Imran") is used straight away.
        val picked = words.firstNotNullOfOrNull { w -> learned["@$w"]?.let { n -> contacts.firstOrNull { it.equals(n, true) }?.let { w to it } } }
        // Misspelled common names are fixed first ("ashrf ne" -> "Ashraf ne"); a saved contact with the
        // misspelling ("Ashrf") then gives way to the right spelling.
        val fixedNames = mutableMapOf<String, String>()
        words.forEachIndexed { i, w ->
            if (i + 1 < words.size && words[i + 1] in nameMarkers && w.none { it.isDigit() }) fixName(w)?.let { fixedNames[w] = it }
        }
        val contactsPlus = contacts.filter { c -> fixName(c) == null || contacts.none { it.equals(fixName(c), true) } } +
            fixedNames.values.filter { n -> contacts.none { it.equals(n, true) } }
        val matched = if (picked != null) Triple(picked.second, emptyList(), picked.first) else matchContact(words, contactsPlus)
        // A saved contact that is itself a misspelling of a common name ("Ashrf") is written the right way.
        val contact0 = matched.first?.let { c -> if (c.contains(' ')) c else (fixName(c) ?: c) }
        val (contact, choices, nameWord) = Triple(contact0, matched.second, matched.third)
        val text = if (contact != null && nameWord != null && !nameWord.equals(contact, true)) {
            words.joinToString(" ") { if (it == nameWord) contact else it }
        } else noClock
        val ws = words.toSet()
        val hasNumber = words.any { w -> w.any { it.isDigit() } }
        val kind = when {
            "delete" in ws && "last" in ws -> Kind.DELETE_LAST
            "undo" in ws && !hasNumber && words.size <= 4 -> Kind.UNDO
            // "<name> ka khata"; a full name said in several words ("nargis salim rashid ka khata") counts as one.
            "khata" in ws && ("kholo" in ws || "dikhao" in ws || words.size <= 4 + (contact ?: "").count { it == ' ' }) &&
                !hasNumber && (contact != null || choices.isNotEmpty()) -> Kind.OPEN_KHATA
            "summary" in ws && !hasNumber && contact != null -> Kind.OPEN_KHATA
            "summary" in ws && !hasNumber -> Kind.SUMMARY
            else -> Kind.ENTRY
        }
        return Result(kind, text, contact, choices, nameWord, clock, n.fixes)
    }
}
