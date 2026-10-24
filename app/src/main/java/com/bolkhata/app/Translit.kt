package com.bolkhata.app

/**
 * Google's Hindi voice understands spoken Urdu/Hindi far better than English voice does,
 * but writes it in Hindi letters ("कल 7 रियाल का नाश्ता किया").
 * This turns those letters into Roman Urdu ("kal 7 riyal ka nashta kiya") so the app can read it.
 */
object Translit {

    private val consonants = mapOf(
        'क' to "k", 'ख' to "kh", 'ग' to "g", 'घ' to "gh", 'ङ' to "n",
        'च' to "ch", 'छ' to "chh", 'ज' to "j", 'झ' to "jh", 'ञ' to "n",
        'ट' to "t", 'ठ' to "th", 'ड' to "d", 'ढ' to "dh", 'ण' to "n",
        'त' to "t", 'थ' to "th", 'द' to "d", 'ध' to "dh", 'न' to "n",
        'प' to "p", 'फ' to "f", 'ब' to "b", 'भ' to "bh", 'म' to "m",
        'य' to "y", 'र' to "r", 'ल' to "l", 'व' to "w", 'श' to "sh", 'ष' to "sh", 'स' to "s", 'ह' to "h",
        '\u0958' to "q", '\u0959' to "kh", '\u095A' to "gh", '\u095B' to "z", '\u095C' to "r", '\u095D' to "rh", '\u095E' to "f", '\u095F' to "y"
    )
    // Letters written as a base letter plus the nukta dot.
    private val nuktaForms = mapOf('क' to '\u0958', 'ख' to '\u0959', 'ग' to '\u095A', 'ज' to '\u095B', 'ड' to '\u095C', 'ढ' to '\u095D', 'फ' to '\u095E', 'य' to '\u095F')

    private val vowels = mapOf(
        'अ' to "a", 'आ' to "aa", 'इ' to "i", 'ई' to "i", 'उ' to "u", 'ऊ' to "u",
        'ए' to "e", 'ऐ' to "ai", 'ओ' to "o", 'औ' to "au", 'ऋ' to "ri", 'ऑ' to "o", 'ऍ' to "e"
    )
    private val signs = mapOf(
        'ा' to "a", 'ि' to "i", 'ी' to "i", 'ु' to "u", 'ू' to "u",
        'े' to "e", 'ै' to "ai", 'ो' to "o", 'ौ' to "au", 'ृ' to "ri", 'ॉ' to "o", 'ॅ' to "e"
    )
    private const val VIRAMA = '्'
    private const val NUKTA = '़'

    private val urduLetters = Regex("""[\u0621-\u064A\u066E-\u06D3\u06FA-\u06FF\u0750-\u077F]+|[^\u0621-\u064A\u066E-\u06D3\u06FA-\u06FF\u0750-\u077F]+""")

    /** Common Urdu-script khata words returned by Urdu speech recognition. */
    private val urduFixes = mapOf(
        "پیسے" to "paise", "پیسہ" to "paisa", "پیسوں" to "paison",
        "پیمنٹ" to "payment", "پےمنٹ" to "payment", "ادائیگی" to "adaigi",
        "نہیں" to "nahi", "نہيں" to "nahi", "نہی" to "nahi", "ابھی" to "abhi", "اب" to "ab",
        "آج" to "aaj", "کل" to "kal", "آخری" to "akhri", "پچھلی" to "pichli", "پچھلے" to "pichhle", "پچھلا" to "pichla",
        "انٹری" to "entry", "اینٹری" to "entry", "اسٹاک" to "stock", "سٹاک" to "stock",
        "سیل" to "sale", "سیلز" to "sales", "خرید" to "khareed", "خریدا" to "kharida",
        "خریدی" to "kharidi", "خریدے" to "kharide", "خریداری" to "khareedari",
        "بیچا" to "becha", "بیچی" to "bechi", "بیچے" to "beche",
        "وصول" to "wasool", "وصولی" to "wasooli", "ریسیو" to "receive", "ریسیوڈ" to "received",
        "ملا" to "mila", "ملی" to "mili", "ملے" to "mile", "ہوئی" to "hui", "ہوا" to "hua", "ہوئے" to "hue",
        "دیے" to "diye", "دیا" to "diya", "دی" to "di", "دیں" to "dein",
        "جو" to "jo", "اس" to "os", "اسکی" to "oski", "کی" to "ki", "کا" to "ka", "کے" to "ke",
        "ہے" to "hai", "ہیں" to "hain", "میں" to "mein", "کو" to "ko", "سے" to "se", "پر" to "par",
        "کر" to "kar", "کرو" to "karo", "خرچہ" to "kharcha", "آمدنی" to "aamdani",
        "تنخواہ" to "tankhwah", "پٹرول" to "petrol", "کرایہ" to "kiraya", "کھانا" to "khana",
        "رقم" to "raqam", "ادھار" to "udhaar", "نقد" to "naqad", "کیش" to "cash", "بینک" to "bank",
        "ریال" to "riyal", "روپے" to "rupay", "درہم" to "dirham"
    )

    fun hasDevanagari(s: String) = s.any { it in 'ऀ'..'ॿ' }

    fun toRoman(text: String): String {
        var input = text
        if (input.any { it in '\u0600'..'\u06FF' || it in '\u0750'..'\u077F' }) {
            input = urduLetters.replace(input) { match ->
                urduFixes[match.value] ?: match.value
            }
        }
        if (!hasDevanagari(input)) return input
        val sb = StringBuilder()
        var i = 0
        // Joins the base letter and the nukta dot into one letter first.
        val chars = mutableListOf<Char>()
        for (c in input) {
            if (c == NUKTA && chars.isNotEmpty() && nuktaForms.containsKey(chars.last())) {
                chars[chars.size - 1] = nuktaForms.getValue(chars.last())
            } else if (c != NUKTA) chars += c
        }
        // A word is kept as pieces: consonants, vowels, and the short "a" (null) every consonant carries.
        val word = mutableListOf<String?>()
        val kinds = mutableListOf<Char>() // 'C' consonant, 'V' vowel, 'S' short a
        fun flush() {
            if (word.isNotEmpty()) { sb.append(fix(word, kinds)); word.clear(); kinds.clear() }
        }
        while (i < chars.size) {
            val c = chars[i]
            when {
                c in '०'..'९' -> { flush(); sb.append('0' + (c - '०')) }
                consonants.containsKey(c) -> {
                    word += consonants.getValue(c); kinds += 'C'
                    val next = chars.getOrNull(i + 1)
                    when {
                        next == VIRAMA -> i++
                        next != null && signs.containsKey(next) -> { word += signs.getValue(next); kinds += 'V'; i++ }
                        else -> { word += null; kinds += 'S' }
                    }
                }
                vowels.containsKey(c) -> { word += vowels.getValue(c); kinds += 'V' }
                c == 'ं' || c == 'ँ' -> { word += "n"; kinds += 'C' }
                c == 'ः' -> { word += "h"; kinds += 'C' }
                c == '।' || c == '॥' -> { flush(); sb.append('.') }
                c in '\u0900'..'\u097F' -> {}
                else -> { flush(); sb.append(c) }
            }
            i++
        }
        flush()
        return sb.toString().split(" ").joinToString(" ") { w -> fixes[w.lowercase()] ?: w }
    }

    /** English and common words as Hindi voice spells them, in the spelling the app knows. */
    private val fixes = mapOf(
        "ret" to "rate", "bil" to "bill", "mobail" to "mobile", "richarj" to "recharge", "mainne" to "maine",
        "die" to "diye", "lie" to "liye", "kie" to "kiye", "gae" to "gaye", "rupye" to "rupaye", "rupe" to "rupay",
        "selri" to "salary", "saileri" to "salary", "taiksi" to "taxi", "baink" to "bank", "kaish" to "cash",
        "kredit" to "credit", "intarnet" to "internet", "fon" to "phone", "sim" to "sim", "kard" to "card",
        "stok" to "stock", "stak" to "stock", "entri" to "entry", "entree" to "entry", "intree" to "entry",
        "pement" to "payment", "paymant" to "payment", "peymant" to "payment", "piment" to "payment",
        "risiv" to "receive", "risiiv" to "receive", "resiv" to "receive",
        "risivd" to "received", "risiivd" to "received", "resivd" to "received",
        "pichali" to "pichli", "pichhali" to "pichhli", "akhari" to "akhri",
        "anpaid" to "unpaid", "unped" to "unpaid"
    )

    /**
     * Drops the short "a" where it is not said: at the end of a word, and between a vowel+consonant and a
     * consonant+vowel ("a-sh(a)-ra-f" -> ashraf, "na-sh-ta" -> nashta). Works right to left like Hindi speech does.
     */
    private fun fix(word: List<String?>, kinds: List<Char>): String {
        val drop = BooleanArray(word.size)
        fun isVowel(k: Int) = k in kinds.indices && (kinds[k] == 'V' || (kinds[k] == 'S' && !drop[k]))
        for (k in kinds.indices.reversed()) {
            if (kinds[k] != 'S') continue
            if (k == kinds.size - 1) { if (k > 1) drop[k] = true; continue }
            val before = isVowel(k - 2) && kinds[k - 1] == 'C'
            val after = kinds.getOrNull(k + 1) == 'C' && isVowel(k + 2)
            if (before && after) drop[k] = true
        }
        val s = word.indices.filter { !drop[it] }.joinToString("") { word[it] ?: "a" }
        // Long "aa" is usually written as one "a" in Roman Urdu ("riyaal" -> riyal), except at the start ("aaj").
        return if (s.length > 3) s.substring(0, 1) + s.substring(1).replace("aa", "a") else s
    }
}
