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
}
