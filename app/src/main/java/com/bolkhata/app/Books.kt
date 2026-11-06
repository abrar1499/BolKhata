package com.bolkhata.app

/**
 * One person often keeps several books (a kiln, the home, personal spending). "ghar ka bijli 3000" or
 * "bhatte ka koila 50000" names the book, so the entry goes there without switching. Offline and exact:
 * only a book's own name (any ending: bhatta / bhatte / bhatty) followed by ka / ki / ke / mein / me / main / for.
 */
object Books {
    private val markers = setOf("ka", "ki", "ke", "kay", "mein", "me", "main", "men", "wala", "wali", "wale", "liye")

    /** A word without its last vowels, so "bhatta", "bhatte" and "bhatty" all read "bhatt". */
    private fun stem(w: String) = w.lowercase().trimEnd('a', 'e', 'i', 'y', 'o').ifEmpty { w.lowercase() }

    /**
     * The book [text] names, as (book id, the text without the name), or null when it names none.
     * [books] are (id, name) as the app keeps them; a name of several words must all be said in order.
     */
    fun route(text: String, books: List<Pair<String, String>>): Pair<String, String>? {
        val words = text.trim().split(Regex("""\s+""")).filter { it.isNotEmpty() }
        if (words.size < 3) return null
        for ((id, name) in books.sortedByDescending { it.second.length }) {
            val nameWords = name.trim().split(Regex("""\s+""")).filter { it.isNotEmpty() }.map(::stem)
            if (nameWords.isEmpty() || nameWords.joinToString("").length < 3) continue
            for (i in 0..words.size - nameWords.size - 1) {
                val said = words.subList(i, i + nameWords.size).map { stem(it.trim(',', '.', ':')) }
                if (said != nameWords) continue
                val next = words[i + nameWords.size].lowercase().trim(',', '.', ':')
                val before = words.getOrNull(i - 1)?.lowercase()
                if (next !in markers && before != "for") continue
                val skipNext = if (next in markers) 1 else 0
                val rest = (words.subList(0, if (before == "for") i - 1 else i) + words.subList(i + nameWords.size + skipNext, words.size))
                    .joinToString(" ").trim()
                if (rest.none { it.isDigit() }) return null
                return id to rest
            }
        }
        return null
    }
}
