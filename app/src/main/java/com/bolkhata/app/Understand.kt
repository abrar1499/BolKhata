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
        "rupay", "rupees", "wala", "wali", "wale", "liye", "mein", "main", "aur", "kuch", "sab", "nahi", "please"
    )

    /** Saved contacts whose name matches a word of the sentence (by spelling or by sound). */
    fun matchContact(words: List<String>, contacts: Collection<String>): Triple<String?, List<String>, String?> {
        val names = contacts.filter { it.isNotBlank() }.distinctBy { it.lowercase() }
        for (w in words) {
            if (w.length < 3 || w in notNames || w.any { it.isDigit() }) continue
            // A contact named exactly this word wins straight away.
            names.firstOrNull { it.equals(w, true) }?.let { return Triple(it, emptyList(), w) }
            val scored = names.mapNotNull { n ->
                val best = n.lowercase().split(Regex("[^\\p{L}]+")).filter { it.length >= 3 }
                    .maxOfOrNull { part -> if (closeName(w, part)) Normalizer.similarity(w, part) else 0.0 } ?: 0.0
                if (best > 0) n to best else null
            }.sortedByDescending { it.second }
            if (scored.isEmpty()) continue
            val top = scored.first().second
            val tied = scored.filter { top - it.second < 0.03 }.map { it.first }
            return if (tied.size == 1) Triple(tied.first(), emptyList(), w) else Triple(null, tied, w)
        }
        return Triple(null, emptyList(), null)
    }

    /** "ashrf" ~ "ashraf", "abraar" ~ "abrar", "imraan" ~ "imran": close in spelling or in sound. */
    private fun closeName(said: String, name: String): Boolean {
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
        val (contact, choices, nameWord) = if (picked != null) Triple(picked.second, emptyList(), picked.first) else matchContact(words, contacts)
        val text = if (contact != null && nameWord != null && !nameWord.equals(contact, true)) {
            words.joinToString(" ") { if (it == nameWord) contact else it }
        } else noClock
        val ws = words.toSet()
        val hasNumber = words.any { w -> w.any { it.isDigit() } }
        val kind = when {
            "delete" in ws && "last" in ws -> Kind.DELETE_LAST
            "undo" in ws && !hasNumber && words.size <= 4 -> Kind.UNDO
            "khata" in ws && ("kholo" in ws || "dikhao" in ws || words.size <= 4) && !hasNumber && (contact != null || choices.isNotEmpty()) -> Kind.OPEN_KHATA
            "summary" in ws && !hasNumber && contact != null -> Kind.OPEN_KHATA
            "summary" in ws && !hasNumber -> Kind.SUMMARY
            else -> Kind.ENTRY
        }
        return Result(kind, text, contact, choices, nameWord, clock, n.fixes)
    }
}
