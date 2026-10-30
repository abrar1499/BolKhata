package com.bolkhata.app

import java.util.Locale

/** Conservative receipt reader: only creates a candidate when an explicit total is printed. */
object ReceiptParser {
    fun parse(text: String, now: Long = System.currentTimeMillis()): List<Entry> {
        if (text.isBlank()) return emptyList()
        val lines = text.lines().map { it.trim().replace(Regex("""\s+"""), " ") }.filter { it.isNotBlank() }
        val totalWords = listOf("grand total", "total payable", "amount due", "net amount", "total")
        val totalLine = lines.lastOrNull { line ->
            val lower = line.lowercase(Locale.ROOT)
            totalWords.any { lower.contains(it) } && amountMatches(line).isNotEmpty()
        } ?: return emptyList()
        val amount = amountMatches(totalLine).lastOrNull() ?: return emptyList()
        if (amount <= 0.0) return emptyList()

        val date = runCatching { Parser.extractDate(text, now).first }.getOrNull() ?: now
        val merchant = lines.firstOrNull { line ->
            line.any(Char::isLetter) && !line.contains("receipt", true) && !line.contains("invoice", true)
        }?.take(50).orEmpty()
        val item = lines.firstOrNull { line ->
            line.any(Char::isLetter) && !line.contains("total", true) &&
                !line.contains("subtotal", true) && !line.contains("tax", true) &&
                !line.contains("receipt", true) && !line.contains("invoice", true) &&
                amountMatches(line).isEmpty()
        }?.take(50) ?: merchant.ifBlank { "Receipt" }
        val category = Parser.guessCategory(item)?.first ?: Parser.guessCategory(merchant)?.first ?: Cat.OTHER
        val id = "receipt-" + now + "-" + kotlin.math.abs(text.hashCode())
        return listOf(
            Entry(
                id = id, type = Type.KHARCHA, amount = amount, category = category,
                item = item, person = "", note = (merchant + if (merchant.isBlank()) "" else " · ") + "Receipt total",
                date = date, createdAt = now
            )
        )
    }

    private fun amountMatches(line: String): List<Double> =
        Regex("""(?<![\d/])(\d{1,3}(?:,\d{3})+|\d+)(?:\.(\d{1,2}))?(?!\d)""")
            .findAll(line)
            .mapNotNull { match ->
                val whole = match.groupValues[1].replace(",", "")
                val decimal = match.groupValues[2]
                (whole + if (decimal.isNotEmpty()) ".$decimal" else "").toDoubleOrNull()
            }.toList()
}
