package com.bolkhata.app

import kotlin.math.roundToLong

/**
 * Voice typing sometimes writes numbers as words ("seven riyal", "do sau", "two hundred").
 * This turns them into digits before the sentence is read: "seven riyal" -> "7 riyal".
 *
 * English number words are always converted when the sentence has no digits.
 * Urdu words (and English words in a sentence that already has digits) are only converted
 * when a money or size word follows, because words like "do", "ek" and "das" also mean other things.
 */
object NumberWords {

    private val english = mapOf(
        "zero" to 0.0, "one" to 1.0, "two" to 2.0, "three" to 3.0, "four" to 4.0, "five" to 5.0,
        "six" to 6.0, "seven" to 7.0, "eight" to 8.0, "nine" to 9.0, "ten" to 10.0,
        "eleven" to 11.0, "twelve" to 12.0, "thirteen" to 13.0, "fourteen" to 14.0, "fifteen" to 15.0,
        "sixteen" to 16.0, "seventeen" to 17.0, "eighteen" to 18.0, "nineteen" to 19.0,
        "twenty" to 20.0, "thirty" to 30.0, "forty" to 40.0, "fourty" to 40.0, "fifty" to 50.0,
        "sixty" to 60.0, "seventy" to 70.0, "eighty" to 80.0, "ninety" to 90.0
    )

    private val urdu = mapOf(
        "ek" to 1.0, "aik" to 1.0, "do" to 2.0, "teen" to 3.0, "char" to 4.0, "chaar" to 4.0,
        "panch" to 5.0, "paanch" to 5.0, "chhe" to 6.0, "chay" to 6.0, "che" to 6.0, "saat" to 7.0,
        "aath" to 8.0, "nau" to 9.0, "das" to 10.0, "dus" to 10.0, "gyarah" to 11.0, "barah" to 12.0,
        "baarah" to 12.0, "terah" to 13.0, "chaudah" to 14.0, "pandrah" to 15.0, "solah" to 16.0,
        "satrah" to 17.0, "atharah" to 18.0, "unees" to 19.0, "bees" to 20.0, "pachees" to 25.0,
        "tees" to 30.0, "chalees" to 40.0, "chalis" to 40.0, "pachas" to 50.0, "pachaas" to 50.0,
        "sattar" to 70.0, "assi" to 80.0,
        "dedh" to 1.5, "dhai" to 2.5, "dhaai" to 2.5, "adha" to 0.5, "aadha" to 0.5,
        "ایک" to 1.0, "دو" to 2.0, "تین" to 3.0, "چار" to 4.0, "پانچ" to 5.0, "چھ" to 6.0,
        "سات" to 7.0, "آٹھ" to 8.0, "نو" to 9.0, "دس" to 10.0, "بیس" to 20.0, "پچاس" to 50.0,
        "ڈیڑھ" to 1.5, "ڈھائی" to 2.5
    )

    private val multipliers = mapOf(
        "hundred" to 100.0, "sau" to 100.0, "سو" to 100.0,
        "thousand" to 1_000.0, "hazar" to 1_000.0, "hazaar" to 1_000.0, "hazzar" to 1_000.0, "ہزار" to 1_000.0,
        "lakh" to 100_000.0, "lac" to 100_000.0, "لاکھ" to 100_000.0,
        "million" to 1_000_000.0
    )

    private val money = setOf(
        "riyal", "riyals", "rial", "rials", "sar", "sr", "rupay", "rupaye", "rupee", "rupees", "rs",
        "dirham", "dirhams", "dollar", "dollars", "ریال", "روپے"
    )

    /** Words that mark a number as a date: "fourteen date", "thirteen october". */
    private val dateWords = setOf(
        "date", "tareekh", "tarikh", "tarekh", "tareek", "tarik", "تاریخ", "din", "days", "day",
        "january", "february", "march", "april", "may", "june", "july", "august", "september", "october",
        "november", "december", "jan", "feb", "mar", "apr", "jun", "jul", "aug", "sep", "sept", "oct", "nov", "dec"
    )

    private val trailing = charArrayOf(',', '.', '!', '?', '،', '۔', ':', ';')

    fun convert(text: String): String {
        val tokens = text.split(" ")
        val hasDigits = text.any { it.isDigit() }
        val out = mutableListOf<String>()
        var i = 0
        while (i < tokens.size) {
            val run = readRun(tokens, i)
            if (run == null) {
                out += tokens[i]
                i++
                continue
            }
            val (end, value, allEnglish, hasMultiplier) = run
            val next = tokens.getOrNull(end)?.let { core(it).lowercase() }
            val accept = hasMultiplier || (next != null && (next in money || next in dateWords)) || (allEnglish && !hasDigits)
            if (accept && value > 0) {
                val suffix = tokens[end - 1].substring(core(tokens[end - 1]).length)
                out += format(value) + suffix
                i = end
            } else {
                out += tokens[i]
                i++
            }
        }
        return out.joinToString(" ")
    }

    private data class Run(val end: Int, val value: Double, val allEnglish: Boolean, val hasMultiplier: Boolean)

    /** Reads a run of number words starting at [start]; null if the word there is not a number word. */
    private fun readRun(tokens: List<String>, start: Int): Run? {
        var total = 0.0
        var current = 0.0
        var allEnglish = true
        var hasMultiplier = false
        var j = start
        while (j < tokens.size) {
            val raw = tokens[j]
            val w = core(raw).lowercase()
            if (w.isEmpty()) break
            val unit = english[w] ?: urdu[w]
            val mult = multipliers[w]
            when {
                unit != null -> {
                    if (urdu.containsKey(w) && !english.containsKey(w)) allEnglish = false
                    current += unit
                }
                mult != null -> {
                    hasMultiplier = true
                    if (w != "hundred" && w != "thousand" && w != "million") allEnglish = false
                    if (mult == 100.0) {
                        current = (if (current == 0.0) 1.0 else current) * 100.0
                    } else {
                        total += (if (current == 0.0) 1.0 else current) * mult
                        current = 0.0
                    }
                }
                // "two hundred and fifty"
                w == "and" && j > start && multipliers.containsKey(core(tokens[j - 1]).lowercase()) &&
                    tokens.getOrNull(j + 1)?.let { english.containsKey(core(it).lowercase()) } == true -> {}
                else -> break
            }
            j++
            // A word ending with a comma or full stop closes the number.
            if (raw.length > core(raw).length) break
        }
        if (j == start) return null
        return Run(j, total + current, allEnglish, hasMultiplier)
    }

    private fun core(token: String): String = token.trimEnd(*trailing)

    private fun format(v: Double): String =
        if (v == Math.floor(v)) v.roundToLong().toString() else v.toString()
}
