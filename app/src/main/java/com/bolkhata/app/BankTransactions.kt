package com.bolkhata.app

/** Idempotency guard for structured bank notifications; ordinary manually entered transactions are untouched. */
object BankTransactionDeduplicator {
    class Result(val fresh: List<Entry>, val skipped: Int)

    fun filterNew(candidates: List<Entry>, saved: List<Entry>): Result {
        val known = saved.mapNotNull { it.sourceFingerprint.takeIf(String::isNotBlank) }.toMutableSet()
        var skipped = 0
        val fresh = candidates.filter { candidate ->
            val fingerprint = candidate.sourceFingerprint
            if (fingerprint.isNotBlank() && !known.add(fingerprint)) {
                skipped++
                false
            } else true
        }
        return Result(fresh, skipped)
    }

    /**
     * Pairs imported statement rows with entries already saved, so they are not added twice. A pair needs the same
     * minute, amount and type, plus the same long note or the same person and item. Returns imported id -> saved id.
     */
    fun matchSaved(found: List<Entry>, saved: List<Entry>): Map<String, String> {
        fun normal(s: String) = s.trim().lowercase().replace(Regex("\\s+"), " ")
        val used = mutableSetOf<String>()
        val out = linkedMapOf<String, String>()
        found.forEach { imported ->
            val hit = saved.firstOrNull { s ->
                s.id !in used && s.type == imported.type &&
                    kotlin.math.abs(s.amount - imported.amount) < 0.01 &&
                    s.date / 60_000L == imported.date / 60_000L && run {
                        val note = normal(s.note)
                        val person = normal(s.person)
                        val item = normal(s.item)
                        (note.length >= 6 && note == normal(imported.note)) ||
                            (person.isNotBlank() && person == normal(imported.person) &&
                                item.isNotBlank() && item == normal(imported.item))
                    }
            } ?: return@forEach
            used += hit.id
            out[imported.id] = hit.id
        }
        return out
    }
}
