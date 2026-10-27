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
            .replace('j', 'g').replace('v', 'f').replace('d', 'd').replace("ee", "i").replace("oo", "u")
        // Vowels and soft letters are spelled in many ways; the consonants carry the name.
        var k = w.filter { it !in "aeiouyh" }
        if (k.length > 3 && k.endsWith("t")) k = k.dropLast(1)          // Arabic ending: "Afkaar" / "Afkarat"
        return k.fold("") { acc, ch -> if (acc.endsWith(ch)) acc else acc + ch }  // "Muhammad" / "Mohamad"
    }

    private fun words(name: String) = name.lowercase().split(Regex("[^\\p{L}]+"))
        .filter { it.length >= 2 && it !in setOf("al", "el", "bin", "ibn", "abu", "the", "and", "co", "est", "ltd", "llc") }

    /** Same word: equal, or the same sound when long enough to be safe. */
    fun sameWord(a: String, b: String): Boolean {
        if (a.equals(b, true)) return true
        val ka = key(a)
        val kb = key(b)
        return ka.length >= 3 && ka == kb
    }

    /** Same name: every word of the shorter name sounds like a word of the longer one. */
    fun sameName(a: String, b: String): Boolean {
        val wa = words(a)
        val wb = words(b)
        if (wa.isEmpty() || wb.isEmpty()) return false
        val (short, long) = if (wa.size <= wb.size) wa to wb else wb to wa
        return short.all { s -> long.any { sameWord(s, it) } }
    }

    /**
     * New entries (from a statement, a photo or the chat) take the spelling of a name already saved,
     * so one person or shop is one account: "MOHAMMED IMRAN" becomes the saved "Muhammad Imran".
     */
    fun unify(new: List<Entry>, saved: List<Entry>): List<Entry> {
        val people = saved.map { it.person.trim() }.filter { it.isNotEmpty() }.distinct()
        val shops = saved.map { it.item.trim() }.filter { it.isNotEmpty() && it !in setOf("Transfer", "Transfer fee", "Loans") }.distinct()
        fun pick(name: String, known: List<String>): String {
            if (name.isBlank()) return name
            known.firstOrNull { it.equals(name, true) }?.let { return it }
            // Only a full match counts, and only when it is the one saved name that fits.
            val hits = known.filter { words(it).size == words(name).size && sameName(it, name) }
            return if (hits.size == 1) hits.first() else name
        }
        fun noteName(note: String): String {
            val head = when {
                note.startsWith("To ") -> "To "
                note.startsWith("From ") -> "From "
                else -> return note
            }
            val n = note.removePrefix(head).trim()
            val fixed = pick(n, people + saved.mapNotNull { e ->
                e.note.trim().takeIf { it.startsWith("To ") || it.startsWith("From ") }?.substringAfter(' ')?.trim()
            }.distinct())
            return head + fixed
        }
        return new.map { e -> e.copy(person = pick(e.person, people), item = pick(e.item, shops), note = noteName(e.note)) }
    }
}
