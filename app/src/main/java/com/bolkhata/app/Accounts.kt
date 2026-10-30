package com.bolkhata.app

import java.util.Locale

/** Khata and record-safety checks shared by the chat engine and the app: stable IDs, khata moves and snapshots. */
object Accounts {

    /** The full stable record ID. Hash-shortened IDs can collide and must never identify a financial record. */
    fun code(e: Entry): String = e.id

    /** Resolve only an exact record ID; never interpret a model-provided number as a list position. */
    fun byStableId(entries: List<Entry>, id: String): Entry? =
        id.takeIf { it.isNotBlank() }?.let { stable -> entries.firstOrNull { it.id == stable } }

    /** Recognize an explicit bulk move request while the user is inside a khata. */
    fun isExplicitBulkKhataMoveRequest(text: String): Boolean {
        val value = text.lowercase(Locale.ROOT)
        val all = Regex("""\b(all|every|sab|sari|saari|tamam|entire|poori|poora|these|in sab)\b""").containsMatchIn(value)
        val records = Regex("""\b(entries|entry|records?|transactions?)\b""").containsMatchIn(value)
        val move = Regex("""\b(move|shift|transfer|bhej(?:o|na)?|send|daal|dal)\b""").containsMatchIn(value)
        val khata = Regex("""\b(khata|account)\b""").containsMatchIn(value)
        return all && records && move && khata
    }

    /** Match a destination khata by its full saved name in a natural-language request. */
    fun khataNameMentionedIn(text: String, currentName: String, knownNames: List<String>): String? {
        fun normalize(value: String): String = value.lowercase(Locale.ROOT)
            .replace(Regex("""[^\p{L}\p{N}]+"""), " ")
            .trim().replace(Regex("""\s+"""), " ")
        val haystack = " " + normalize(text) + " "
        val current = normalize(currentName)
        val matches = knownNames.map { it.trim() }.filter { it.isNotBlank() }
            .distinctBy { normalize(it) }
            .filter { name ->
                val candidate = normalize(name)
                candidate.isNotBlank() && candidate != current && haystack.contains(" $candidate ")
            }
        if (matches.isEmpty()) return null
        val longest = matches.maxOf { normalize(it).length }
        val best = matches.filter { normalize(it).length == longest }
        return best.singleOrNull()
    }

    /** True when multiple candidates need clarification rather than an implicit bulk action. */
    fun requiresTargetClarification(matchCount: Int, explicitlyAll: Boolean, allowAll: Boolean = true): Boolean =
        matchCount > 1 && !(explicitlyAll && allowAll)

    /** Suppress an accidental immediate repeat only after the prior identical request actually changed data. */
    fun isRecentDuplicateMutationRequest(
        previousUserText: String,
        previousUserAt: Long,
        latestAssistantText: String,
        latestAssistantAt: Long,
        previousCreatedEntries: Boolean,
        newText: String,
        now: Long,
        windowMs: Long = 30_000L
    ): Boolean {
        fun normalize(s: String) = s.trim().lowercase().replace(Regex("""\s+"""), " ")
        val sameRequest = normalize(previousUserText).isNotBlank() && normalize(previousUserText) == normalize(newText)
        // A generic "Done / entries updated" bubble is not proof that a mutation succeeded.
        // Suppress a repeated command only when the app confirms a concrete affected-record count.
        val mutationConfirmed = previousCreatedEntries ||
            Regex("""(?i)\b(changed|updated|deleted|removed|moved|converted|fixed|marked paid|marked unpaid)\s+\d+\b""")
                .containsMatchIn(latestAssistantText)
        return sameRequest && mutationConfirmed && latestAssistantAt >= previousUserAt &&
            now >= previousUserAt && now - previousUserAt <= windowMs
    }

    /** True only if every target still exactly matches the live record snapshot used to plan the action. */
    fun snapshotsMatch(entries: List<Entry>, expected: List<Entry>): Boolean {
        val ids = expected.map { it.id }
        if (ids.distinct().size != ids.size) return false
        val live = entries.associateBy { it.id }
        return expected.all { live[it.id] == it }
    }
}
