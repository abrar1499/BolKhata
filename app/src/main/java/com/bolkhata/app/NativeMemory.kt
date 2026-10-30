package com.bolkhata.app

import java.util.Locale

/** Small, explicit, user-controlled facts stored only in the app's local preferences. */
object NativeMemory {
    data class Outcome(val facts: List<String>, val reply: String)

    fun handle(input: String, saved: List<String>): Outcome? {
        val text = runCatching { Translit.toRoman(input) }.getOrDefault(input).trim()
        val lower = text.lowercase(Locale.ROOT)
        val facts = saved.toMutableList()

        if (lower.matches(Regex("""(?:what do you remember|what have you remembered|tumhe kya yaad hai|mujhe kya yaad hai)\??"""))) {
            val reply = if (facts.isEmpty()) "I don't have any saved personal notes yet. Say 'remember that …' to save a fact on this phone."
            else "Things you asked me to remember:\n" + facts.takeLast(12).joinToString("\n") { "• $it" }
            return Outcome(facts, reply)
        }

        val rememberPrefix = Regex("""^(?:please\s+)?(?:remember(?:\s+that)?|yaad\s+rakh(?:na|o|lena)?|yaad\s+kar\s+lo)\s*[:,-]?\s*""", RegexOption.IGNORE_CASE)
        val rememberMatch = rememberPrefix.find(text)
        if (rememberMatch != null) {
            val fact = text.substring(rememberMatch.range.last + 1).trim().trimEnd('.', '!', '?')
            if (fact.length < 4) return Outcome(facts, "Tell me the full fact after 'remember that'.")
            val existing = facts.firstOrNull { it.equals(fact, true) }
            if (existing != null) return Outcome(facts, "I already have that note saved.")
            facts += fact.take(180)
            return Outcome(facts.takeLast(80), "Saved on this phone: $fact")
        }

        val forgetPrefix = Regex("""^(?:please\s+)?(?:forget(?:\s+that)?|remove\s+memory|bhool\s+jao|yaad\s+se\s+nikaal(?:o)?)\s*[:,-]?\s*""", RegexOption.IGNORE_CASE)
        val forgetMatch = forgetPrefix.find(text)
        if (forgetMatch != null) {
            val query = text.substring(forgetMatch.range.last + 1).trim()
            if (query.isBlank()) return Outcome(facts, "Which saved note should I forget?")
            val remaining = facts.filterNot { it.contains(query, true) || query.contains(it, true) }
            return if (remaining.size == facts.size) Outcome(facts, "I couldn't find a saved note matching '$query'.")
            else Outcome(remaining, "Removed the matching note from this phone.")
        }
        return null
    }
}
