package com.bolkhata.app

/**
 * Cleans up what the person typed or said before anything else reads it, fully offline:
 * - common Roman Urdu spellings become one standard spelling ("aj" -> "aaj", "sube" -> "subah", "rote" -> "roti"),
 * - numbers said as words become digits ("sat bajay" -> "7 baje", "dedh sau" -> "150", "seventy" -> "70"),
 * - a word that is close to a known word (an item, a command, a time word) is corrected, but only when it is
 *   clearly the same word; otherwise it is left as it is,
 * - words the person corrected before are fixed the same way again.
 */
object Normalizer {

    class Normalized(val text: String, val fixes: List<Pair<String, String>>)

    /** Spelling variants -> the standard word. */
    private val variants: Map<String, String> = buildMap {
        fun v(std: String, vararg forms: String) = forms.forEach { put(it, std) }
        // Days and times
        v("aaj", "aj", "aaj", "aajj", "aja", "today")
        v("kal", "kl", "kall", "kaal")
        v("parson", "parso", "parsu", "parsoon", "parsun", "prson", "parsuon")
        v("subah", "sube", "subha", "subh", "subeh", "suba", "sobah", "subhe", "subay", "morning")
        v("dopahar", "dopehar", "dopher", "dupehar", "dophar", "dopahr", "duphar", "dopeher", "afternoon")
        v("shaam", "sham", "shm", "shaame", "evening")
        v("raat", "rat", "raa", "rath", "raath", "night")
        v("baje", "bajay", "bjay", "bje", "baja", "bajey", "bajy", "bajai", "bajjay")
        v("abhi", "abi", "abhe", "abhy")
        // Verbs
        v("diya", "dia", "dya", "diaa", "deya", "diay", "dye", "diyay", "diey")
        v("liya", "lia", "lya", "leya", "liay", "liyay")
        v("karwaya", "krwaya", "krwaia", "karvaya", "krvaya", "karwaia", "krwya", "karwaaya", "krwaaya", "karwai", "krwai")
        v("kiya", "kia", "kiaa", "kiyaa")
        v("khaya", "khaaya", "khya", "khaia", "khaye", "khayay", "khayi", "khai", "khaey")
        v("piya", "pia", "pya", "piye", "piyi", "pii")
        v("dalwaya", "dalwaia", "dalwya", "dlwaya", "dalwaaya", "dalaya", "dalwaye", "dalwai", "daala", "dala", "dalwa")
        v("kharida", "khareeda", "khrida", "kharid", "khareed", "kharida", "khride", "kharide")
        v("bheja", "bheje", "bhja", "bhje", "beja", "bheji", "bhija")
        v("wapas", "wapis", "wps", "wapass", "wapes", "waps", "vapas", "vapis")
        v("udhaar", "udhar", "udaar", "udhr", "udhaar", "udhaar", "udar", "udhari", "udhaari", "loan")
        v("kholo", "khlo", "kholoo", "kolo", "khol", "khulo", "kholdo", "open")
        v("dikhao", "dkhao", "dikhaw", "dikao", "dekhao", "dikha", "show")
        v("karo", "kro", "kar", "kr", "krdo", "kardo", "kr do")
        v("delete", "delt", "delet", "dlt", "delte", "dilit", "delit", "deleat", "deleet", "dleet", "deelete", "dilete", "deleete", "mitao", "hatao")
        v("entry", "antry", "entri", "entery", "intry", "entrie", "enrty", "entr", "antri", "intri")
        v("last", "lst", "lastt", "laast", "akhri", "aakhri", "aakhiri", "akhiri", "pichli", "pichhli", "pichle")
        v("khata", "khaata", "khta", "khatta", "khaataa", "kahta", "khataa", "account", "ledger")
        v("undo", "undoo", "andu", "undu", "wapas karo")
        v("summary", "sumary", "summery", "samri", "sammary", "hisaab", "hisab", "hesab", "report")
        // Food and drink
        v("roti", "rote", "rotti", "rooti", "roty", "rotti", "rotiyan", "roteyan")
        v("chai", "chae", "chay", "chaye", "chaai", "tea")
        v("doodh", "dodh", "dudh", "doodh", "doud", "milk")
        v("pani", "paani", "pane", "panni", "water")
        v("khana", "khaana", "khna", "kahana", "food")
        v("nashta", "nashtaa", "nashtta", "nasta", "breakfast")
        v("sabzi", "sabzee", "sabji", "sbzi", "subzi", "sabzie")
        v("chawal", "chaawal", "chawel", "rice")
        v("biryani", "biriyani", "bryani", "biryane", "biriani")
        v("anday", "anda", "ande", "andey", "eggs", "egg")
        v("phal", "fruit", "fruits", "fal")
        v("gosht", "goshat", "meat")
        // Transport
        v("petrol", "ptrol", "patrol", "petrl", "ptrl", "petral", "petrool", "patrool", "pitrol", "petrole", "patrl", "petroll", "petrul", "fuel")
        v("diesel", "deesal", "disel", "dizel", "deezal")
        v("taxi", "texi", "taksi", "tacsi", "taxy", "teksi", "tecsi", "taxie", "cab")
        v("kiraya", "kiraa", "kraya", "kiraaya", "kirya", "rent")
        // Bills and other
        v("bijli", "bjli", "bijlee", "bijle", "electricity")
        v("recharge", "richarge", "recharj", "richarj", "rechrge", "balance dalwaya")
        v("dawai", "dawa", "dwai", "dawaai", "medicine", "dawaee")
        v("doctor", "dactar", "daktar", "doktor", "docter")
        v("mobile", "mobail", "mobil", "mobaile")
        v("ghar", "gher", "gar", "home")
    }

    /** Words that are already right and must never be "corrected" into something else. */
    private val keep = setOf(
        "ka", "ki", "ke", "ko", "se", "ne", "me", "mein", "main", "par", "pe", "aur", "or", "hai", "hain", "tha", "thi", "the",
        "wala", "wali", "wale", "ek", "do", "sab", "kuch", "bhi", "nahi", "nhi", "na", "haan", "han", "to", "toh", "is", "us",
        "liye", "lye", "diye", "kiye", "kiya", "karwaya", "riyal", "riyals", "rupay", "rupees", "rs", "sar", "sr", "dirham", "aed", "pkr", "inr", "usd"
    )

    /** Known words a close misspelling can be corrected to (beyond the variants above). */
    private val vocabulary: List<String> by lazy {
        (variants.values.toSet() + setOf(
            "subah", "dopahar", "shaam", "raat", "baje", "aaj", "kal", "parson", "diya", "liya", "khaya", "piya",
            "dalwaya", "karwaya", "kharida", "bheja", "wapas", "udhaar", "kholo", "delete", "entry", "last", "khata", "undo",
            "summary", "edit", "change", "search", "sabzi", "biryani", "shawarma", "burger", "pizza", "juice", "coffee",
            "groceries", "grocery", "mechanic", "service", "parking", "internet", "salary", "tankhwa", "iqama", "fees",
            "school", "kitab", "kapre", "joote", "barber", "haircut", "laundry", "zakat", "sadqa", "gift"
        )).filter { it.length >= 4 && ' ' !in it }.distinct()
    }

    // ---------------------------------------------------------------- numbers

    private val units = mapOf(
        "ek" to 1, "aik" to 1, "one" to 1, "do" to 2, "two" to 2, "teen" to 3, "tin" to 3, "three" to 3,
        "char" to 4, "chaar" to 4, "four" to 4, "panch" to 5, "paanch" to 5, "panj" to 5, "five" to 5,
        "che" to 6, "chay" to 6, "chhe" to 6, "chhay" to 6, "cheh" to 6, "six" to 6,
        "sat" to 7, "saat" to 7, "seven" to 7, "aath" to 8, "ath" to 8, "aat" to 8, "eight" to 8,
        "nau" to 9, "no" to 9, "nao" to 9, "nine" to 9, "das" to 10, "dus" to 10, "ten" to 10,
        "gyarah" to 11, "giyara" to 11, "eleven" to 11, "barah" to 12, "bara" to 12, "twelve" to 12,
        "terah" to 13, "thirteen" to 13, "chodah" to 14, "fourteen" to 14, "pandrah" to 15, "pandra" to 15, "fifteen" to 15,
        "solah" to 16, "sola" to 16, "sixteen" to 16, "satrah" to 17, "seventeen" to 17, "atharah" to 18, "athara" to 18,
        "eighteen" to 18, "unees" to 19, "nineteen" to 19,
        "bees" to 20, "bis" to 20, "twenty" to 20, "pachees" to 25, "pachis" to 25, "tees" to 30, "thirty" to 30,
        "paintees" to 35, "chalees" to 40, "chalis" to 40, "forty" to 40, "pentalees" to 45,
        "pachas" to 50, "pachaas" to 50, "pachaas" to 50, "fifty" to 50, "saath" to 60, "sixty" to 60,
        "sattar" to 70, "satar" to 70, "seventy" to 70, "pachattar" to 75, "assi" to 80, "eighty" to 80,
        "nabbe" to 90, "nabbay" to 90, "ninety" to 90
    )
    private val multipliers = mapOf(
        "sau" to 100, "so" to 100, "saw" to 100, "hundred" to 100,
        "hazar" to 1000, "hazaar" to 1000, "hzar" to 1000, "hajar" to 1000, "thousand" to 1000, "k" to 1000,
        "lakh" to 100_000, "lac" to 100_000, "lakh" to 100_000
    )
    private val halves = mapOf("dedh" to 1.5, "dhai" to 2.5, "dhaai" to 2.5, "adha" to 0.5, "aadha" to 0.5)
    /** Number words that are also ordinary words ("do" = do it, "sat", "saath" = with, "no", "so"). */
    private val ambiguous = setOf("ek", "aik", "one", "do", "sat", "saath", "no", "so", "che", "chay", "tin", "bis", "ath", "aat", "nao", "k", "bara")
    private val numberContext = setOf("baje", "ka", "ki", "ke", "riyal", "riyals", "rupay", "rupees", "rs", "sar", "sr", "ko")

    private fun isNumberWord(w: String) = w in units || w in multipliers || w in halves || w == "saadhe" || w == "sade"

    /** Turns runs of number words into digits, only where they clearly are numbers. */
    private fun numbers(words: List<String>, fixes: MutableList<Pair<String, String>>): List<String> {
        val out = mutableListOf<String>()
        var i = 0
        while (i < words.size) {
            val w = words[i]
            if (!isNumberWord(w)) { out += w; i++; continue }
            // Collect the run of number words.
            var j = i
            while (j < words.size && isNumberWord(words[j])) j++
            val run = words.subList(i, j)
            val next = words.getOrNull(j)
            val prevIsDigit = out.lastOrNull()?.toDoubleOrNull() != null
            // A run is a number when it has several number words ("dedh sau"), a clear number word ("pachas"),
            // or is followed by baje / ka / riyal ("sat baje", "do ka").
            val single = run.size == 1
            val clear = !single || (run[0] !in ambiguous && run[0] !in multipliers) || (next != null && next in numberContext)
            val value = if (clear) valueOf(run) else null
            if (value == null) {
                // A multiplier after a digit: "2 hazar" -> 2000, "5 sau" -> 500.
                if (prevIsDigit && w in multipliers && run.size == 1) {
                    val prev = out.removeAt(out.lastIndex).toDouble()
                    val v = prev * multipliers.getValue(w)
                    out += fmtNum(v)
                    fixes += "$prev $w" to fmtNum(v)
                } else {
                    out += run
                }
            } else {
                // "2 hazar" style: a digit right before a multiplier-led run.
                val v = if (prevIsDigit && run.first() in multipliers) {
                    out.removeAt(out.lastIndex).toDouble() * value
                } else value
                out += fmtNum(v)
                fixes += run.joinToString(" ") to fmtNum(v)
            }
            i = j
        }
        return out
    }

    private fun valueOf(run: List<String>): Double? {
        var total = 0.0
        var current = 0.0
        var half = 0.0
        var any = false
        for (w in run) {
            when {
                w == "saadhe" || w == "sade" -> half = 0.5
                w in halves -> { current += halves.getValue(w); any = true }
                w in units -> { current += units.getValue(w); any = true }
                w in multipliers -> {
                    val m = multipliers.getValue(w)
                    if (current == 0.0) current = 1.0
                    current = (current + half) * m
                    half = 0.0
                    if (m >= 1000) { total += current; current = 0.0 }
                    any = true
                }
            }
        }
        if (!any) return null
        return total + current + half
    }

    private fun fmtNum(v: Double) = if (v % 1.0 == 0.0) v.toLong().toString() else v.toString()

    // ---------------------------------------------------------------- similarity

    /** Jaro-Winkler similarity, 0..1. */
    fun similarity(a: String, b: String): Double {
        if (a == b) return 1.0
        if (a.isEmpty() || b.isEmpty()) return 0.0
        val range = (maxOf(a.length, b.length) / 2 - 1).coerceAtLeast(0)
        val am = BooleanArray(a.length)
        val bm = BooleanArray(b.length)
        var matches = 0
        for (i in a.indices) {
            val lo = maxOf(0, i - range)
            val hi = minOf(b.length - 1, i + range)
            for (j in lo..hi) {
                if (!bm[j] && a[i] == b[j]) { am[i] = true; bm[j] = true; matches++; break }
            }
        }
        if (matches == 0) return 0.0
        var t = 0
        var k = 0
        for (i in a.indices) {
            if (!am[i]) continue
            while (!bm[k]) k++
            if (a[i] != b[k]) t++
            k++
        }
        val m = matches.toDouble()
        val jaro = (m / a.length + m / b.length + (m - t / 2.0) / m) / 3.0
        var prefix = 0
        while (prefix < minOf(4, a.length, b.length) && a[prefix] == b[prefix]) prefix++
        return jaro + prefix * 0.1 * (1 - jaro)
    }

    /** Levenshtein distance. */
    fun distance(a: String, b: String): Int {
        val d = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            var prev = d[0]
            d[0] = i
            for (j in 1..b.length) {
                val tmp = d[j]
                d[j] = minOf(d[j] + 1, d[j - 1] + 1, prev + if (a[i - 1] == b[j - 1]) 0 else 1)
                prev = tmp
            }
        }
        return d[b.length]
    }

    /** The closest word in [candidates] when it is clearly the same word, else null. */
    fun closest(word: String, candidates: Collection<String>, minScore: Double = 0.9): String? {
        if (word.length < 4) return null
        var best: String? = null
        var bestScore = 0.0
        for (c in candidates) {
            if (kotlin.math.abs(c.length - word.length) > 2) continue
            val s = similarity(word, c)
            val dist = distance(word, c)
            // Strict: high similarity AND at most 2 edits (1 for short words).
            val maxEdits = if (word.length <= 5) 1 else 2
            if (s >= minScore && dist <= maxEdits && s > bestScore) { best = c; bestScore = s }
        }
        return best
    }

    // ---------------------------------------------------------------- the whole sentence

    /**
     * [learned]: words the person corrected before (their word -> the right word).
     * Names are not touched here; [Understand.matchContact] handles them.
     */
    fun normalize(input: String, learned: Map<String, String> = emptyMap(), names: Collection<String> = emptyList()): Normalized {
        val fixes = mutableListOf<Pair<String, String>>()
        val nameWords = names.flatMap { n -> n.lowercase().split(Regex("[^\\p{L}]+")) }.filter { it.length >= 3 }.toSet()
        // Split digits from letters ("70ka" -> "70 ka", "sar70" -> "sar 70") and drop punctuation except decimal points.
        val spaced = input.lowercase()
            .replace(Regex("(\\d)(\\p{L})"), "$1 $2").replace(Regex("(\\p{L})(\\d)"), "$1 $2")
            // Commas, question marks and the like split sentences, so they stay (as their own words).
            .replace(Regex("([,;?!])"), " $1 ")
            .replace(Regex("[^\\p{L}\\p{N}.:/\\-,;?! ]"), " ")
        var words = spaced.split(Regex("\\s+")).filter { it.isNotBlank() }.map { if (it.length > 1) it.trim('.', '-') else it }.filter { it.isNotBlank() }
        // 1. Learned fixes and spelling variants.
        words = words.map { w ->
            val fixed = learned[w] ?: if (w in nameWords) w else variants[w]
            if (fixed != null && fixed != w) { fixes += w to fixed; fixed } else w
        }
        // 2. Numbers said as words.
        words = numbers(words, fixes)
        // 3. Close misspellings of known words ("petrolll", "kharidaa").
        words = words.map { w ->
            if (w in keep || w in nameWords || w.any { it.isDigit() } || w in vocabulary) w
            else closest(w, vocabulary)?.let { c -> fixes += w to c; c } ?: w
        }
        return Normalized(words.joinToString(" ").replace(Regex(" ([,;?!])"), "$1"), fixes)
    }
}
