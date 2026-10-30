package com.bolkhata.app

/**
 * Names spelled in different ways ("Dhurwat Al Afkar" / "zarwa alfqar", "Muhammad" / "Mohammed") are matched by
 * how they sound: a key from the consonants, with sounds that are written differently in English treated the same.
 */
object Names {

    /** The sound key of one word: "dhurwat" and "zarwa" both give "zrw", "afkar" and "alfqar" both "fkr". */
    fun key(word: String): String {
        var w = word.lowercase().filter { it.isLetter() }
        if (w.length > 4 && (w.startsWith("al") || w.startsWith("el"))) w = w.drop(2)
        w = w.replace("dh", "z").replace("th", "s").replace("sh", "S").replace("kh", "k").replace("gh", "g")
            .replace("ph", "f").replace("ck", "k").replace('q', 'k').replace('c', 'k').replace('x', 'k')
            .replace('j', 'g').replace('v', 'f').replace("ee", "i").replace("oo", "u")
        // Vowels and soft letters are spelled in many ways; the consonants carry the name.
        var k = w.filter { it !in "aeiouyh" }
        if (k.length > 3 && k.endsWith("t")) k = k.dropLast(1)          // Arabic ending: "Afkaar" / "Afkarat"
        return k.fold("") { acc, ch -> if (acc.endsWith(ch)) acc else acc + ch }  // "Muhammad" / "Mohamad"
    }
}
