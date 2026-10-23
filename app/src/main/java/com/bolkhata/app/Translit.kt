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

    fun hasDevanagari(s: String) = s.any { it in 'ऀ'..'ॿ' }

    fun toRoman(text: String): String {
        if (!hasDevanagari(text)) return text
        val sb = StringBuilder()
        var i = 0
        // Joins the base letter and the nukta dot into one letter first.
        val chars = mutableListOf<Char>()
        for (c in text) {
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
        "kredit" to "credit", "intarnet" to "internet", "fon" to "phone", "sim" to "sim", "kard" to "card"
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
