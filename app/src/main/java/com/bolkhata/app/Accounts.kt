package com.bolkhata.app

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Exact figures for the chat AI, worked out here in code. The AI is told to use these numbers instead of
 * adding up entries itself, which is where language models make mistakes.
 */
object Accounts {

    private const val DAY = 86_400_000L

    private fun startOfDay(ms: Long): Long = Calendar.getInstance().apply {
        timeInMillis = ms
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private fun monthStart(back: Int, now: Long): Long = Calendar.getInstance().apply {
        timeInMillis = startOfDay(now)
        set(Calendar.DAY_OF_MONTH, 1)
        add(Calendar.MONTH, -back)
    }.timeInMillis

    private fun isOut(e: Entry) = when (e.type) {
        Type.KHARCHA, Type.DIYA, Type.WAPIS_DIYA, Type.TRANSFER_OUT -> true
        Type.PURCHASE -> e.person.isBlank() || e.paid
        else -> false
    }

    private fun isIn(e: Entry) = when (e.type) {
        Type.AAMDANI, Type.WAPIS_MILA, Type.LIYA, Type.TRANSFER_IN -> true
        Type.SALE -> e.person.isBlank() || e.paid
        else -> false
    }

    private fun f(n: Double) = fmt(n)

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
        val mutationConfirmed = previousCreatedEntries ||
            Regex("""(?i)\b(changed|updated|deleted|removed|moved|converted|marked paid|marked unpaid)\b""")
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

    /** One compact line per entry: cheaper for the AI to read than JSON, so far more entries fit. */
    fun line(e: Entry): String {
        val d = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date(e.createdAt.takeIf { sameDay(it, e.date) } ?: e.date))
        val parts = mutableListOf("id=" + code(e), d, e.type.label, f(e.amount), e.category.label, e.item)
        if (e.person.isNotBlank()) parts += "person:" + e.person
        if (e.note.isNotBlank() && !e.note.equals(e.item, ignoreCase = true)) parts += "note:" + e.note.take(50)
        if (e.qty > 0) parts += "qty:" + f(e.qty) + (if (e.unit.isNotBlank()) " " + e.unit else "")
        return parts.joinToString(" | ")
    }

    /** [compact] gives a shorter version (fewer people and lists) for AI plans with small limits. */
    private val dayFmt = SimpleDateFormat("d MMM yyyy", Locale.US)

    /**
     * One person's khata worked out by the app: every entry with them, money each way, and the loan balance.
     * The AI only has to present it, so the numbers are always right.
     */
    fun personLedger(list: List<Entry>, label: String, currency: String): String {
        val sorted = list.sortedBy { it.date }
        val lines = sorted.joinToString("\n") { e ->
            val sign = when { isIn(e) -> "+"; isOut(e) -> "-"; else -> " " }
            "  " + dayFmt.format(Date(e.date)) + " | " + e.type.label + " | " + e.item +
                (if (e.note.isNotBlank() && !e.note.equals(e.item, true)) " (" + e.note.take(40) + ")" else "") +
                " | " + sign + f(e.amount) + " | id=" + code(e)
        }
        val got = list.filter { isIn(it) }.sumOf { it.amount }
        val gave = list.filter { isOut(it) }.sumOf { it.amount }
        val loan = balances(list).values.sum()
        val loanText = when {
            loan > 0.005 -> "they owe you ${f(loan)} $currency"
            loan < -0.005 -> "you owe them ${f(-loan)} $currency"
            else -> "nothing (settled or no loans)"
        }
        return """
PERSON KHATA for $label (the app searched all saved entries, so this is complete; use these lines and numbers exactly, never add entries that are not here):
$lines
- Came to you from them: ${f(got)} $currency (${list.count { isIn(it) }} entries)
- Went from you to them: ${f(gave)} $currency (${list.count { isOut(it) }} entries)
- Loan / credit balance: $loanText. Only entries saved as lent, borrowed, got back, paid back or credit sales count as loans;
  a plain bank transfer is not a loan. If they ask "kitne dene hain" and only transfers exist, say so and offer to mark it as a loan.
When they ask for this person's khata, hisab or details: list each line as "- 22 Oct · Transfer from Muhammad Imran · +3,500" (newest first),
then one line with the totals and the balance.""".trim()
    }

    /** Every saved entry of one amount, found by the app in all entries. */
    fun amountMatches(list: List<Entry>, amount: Double, total: Int, currency: String): String {
        val lines = list.joinToString("\n") { e ->
            "  " + dayFmt.format(Date(e.date)) + " | " + e.type.label + " | " + e.item +
                (if (e.note.isNotBlank() && !e.note.equals(e.item, true)) " (" + e.note.take(40) + ")" else "") +
                (if (e.person.isNotBlank()) " | " + e.person else "") + " | " + (if (isIn(e)) "+" else if (isOut(e)) "-" else "") + f(e.amount) +
                " | id=" + code(e)
        }
        return "ENTRIES OF ${f(amount)} $currency (the app searched all $total saved entries; this list is complete):\n$lines"
    }

    /** Totals for entries matching an item, shop or category, by month. */
    fun itemSummary(list: List<Entry>, label: String, currency: String): String {
        val out = list.filter { isOut(it) }
        val inn = list.filter { isIn(it) }
        val byMonth = out.groupBy { SimpleDateFormat("MMM yyyy", Locale.US).format(Date(it.date)) }
            .entries.joinToString("; ") { (mo, g) -> "$mo ${f(g.sumOf { it.amount })} (${g.size}x)" }
        return "SUMMARY for \"$label\" (the app searched all saved entries; use as is): ${list.size} entries; money out ${f(out.sumOf { it.amount })} $currency" +
            (if (inn.isNotEmpty()) ", money in ${f(inn.sumOf { it.amount })} $currency" else "") +
            (if (byMonth.isNotBlank()) "; by month: $byMonth" else "") + "."
    }

    /** Totals for a period the person asked about. */
    fun periodSummary(list: List<Entry>, label: String, currency: String): String {
        val spent = list.filter { it.type == Type.KHARCHA }
        val total = spent.sumOf { it.amount }
        val cats = spent.groupBy { it.category.label }.mapValues { g -> g.value.sumOf { it.amount } }
            .entries.sortedByDescending { it.value }.joinToString("; ") { "${it.key} ${f(it.value)}" }
        return "PERIOD $label (worked out by the app, use as is): expenses ${f(total)} $currency in ${spent.size} entries" +
            (if (cats.isNotBlank()) " ($cats)" else "") +
            "; all money out ${f(list.filter { isOut(it) }.sumOf { it.amount })}, all money in ${f(list.filter { isIn(it) }.sumOf { it.amount })} $currency."
    }

    fun facts(all: List<Entry>, currency: String, now: Long = System.currentTimeMillis(), compact: Boolean = false): String {
        val c = currency
        val today = startOfDay(now)
        val weekStart = Calendar.getInstance().apply {
            timeInMillis = today
            add(Calendar.DAY_OF_YEAR, -((get(Calendar.DAY_OF_WEEK) - Calendar.SUNDAY + 7) % 7))
        }.timeInMillis
        val yearStart = Calendar.getInstance().apply { timeInMillis = today; set(Calendar.DAY_OF_YEAR, 1) }.timeInMillis
        fun between(a: Long, b: Long) = all.filter { it.date >= a && it.date < b }
        fun period(name: String, list: List<Entry>): String {
            val spent = list.filter { it.type == Type.KHARCHA }.sumOf { it.amount }
            val income = list.filter { it.type == Type.AAMDANI }.sumOf { it.amount }
            val out = list.filter { isOut(it) }.sumOf { it.amount }
            val inn = list.filter { isIn(it) }.sumOf { it.amount }
            return "- $name: expenses ${f(spent)}, income ${f(income)}, all money out ${f(out)}, all money in ${f(inn)}, " +
                "net ${f(inn - out)} $c (${list.size} entries)"
        }
        val sb = StringBuilder()
        sb.appendLine("EXACT FIGURES (worked out by the app; use these, don't add up entries yourself). Amounts in $c.")
        sb.appendLine("\"expenses\" = Cash Out entries only. \"all money out\" also counts money lent, paid back and paid purchases;")
        sb.appendLine("\"all money in\" also counts money borrowed, received back and paid sales.")
        sb.appendLine(period("Today", between(today, today + DAY)))
        sb.appendLine(period("Yesterday", between(today - DAY, today)))
        sb.appendLine(period("This week (from Sunday)", between(weekStart, today + DAY)))
        sb.appendLine(period("Last week", between(weekStart - 7 * DAY, weekStart)))
        val monthNames = SimpleDateFormat("MMMM yyyy", Locale.US)
        for (back in 0..(if (compact) 2 else 5)) {
            val a = monthStart(back, now)
            val b = if (back == 0) today + DAY else monthStart(back - 1, now)
            val list = between(a, b)
            if (back > 0 && list.isEmpty()) continue
            sb.appendLine(period(monthNames.format(Date(a)) + (if (back == 0) " (this month so far)" else ""), list))
        }
        sb.appendLine(period("This year", between(yearStart, today + DAY)))
        sb.appendLine(period("All time", all))

        // This month in detail.
        val thisMonth = between(monthStart(0, now), today + DAY)
        val lastMonth = between(monthStart(1, now), monthStart(0, now))
        val spentNow = thisMonth.filter { it.type == Type.KHARCHA }
        val total = spentNow.sumOf { it.amount }
        val dayOfMonth = Calendar.getInstance().apply { timeInMillis = now }.get(Calendar.DAY_OF_MONTH)
        val daysInMonth = Calendar.getInstance().apply { timeInMillis = now }.getActualMaximum(Calendar.DAY_OF_MONTH)
        if (total > 0) {
            val perDay = total / dayOfMonth
            sb.appendLine("- This month: average ${f(perDay)} $c a day over $dayOfMonth days; at this pace about ${f(perDay * daysInMonth)} $c by month end.")
        }
        fun byCategory(list: List<Entry>): String {
            val sum = list.sumOf { it.amount }.takeIf { it > 0 } ?: return "none"
            return list.groupBy { it.category.label }.mapValues { g -> g.value.sumOf { it.amount } }
                .entries.sortedByDescending { it.value }
                .joinToString("; ") { "${it.key} ${f(it.value)} (${(it.value / sum * 100).roundToInt()}%)" }
        }
        sb.appendLine("- Expenses by category, this month: " + byCategory(spentNow))
        sb.appendLine("- Expenses by category, last month: " + byCategory(lastMonth.filter { it.type == Type.KHARCHA }))
        val topItems = spentNow.groupBy { it.item.trim().lowercase() }
            .map { (_, g) -> Triple(g.first().item, g.sumOf { it.amount }, g.size) }
            .sortedByDescending { it.second }.take(if (compact) 5 else 10)
            .joinToString("; ") { "${it.first} ${f(it.second)} (${it.third}x)" }
        if (topItems.isNotEmpty()) sb.appendLine("- Top items/shops this month: $topItems")
        val biggest = spentNow.sortedByDescending { it.amount }.take(5)
            .joinToString("; ") { SimpleDateFormat("d MMM", Locale.US).format(Date(it.date)) + " " + it.item + " " + f(it.amount) }
        if (biggest.isNotEmpty()) sb.appendLine("- Biggest expenses this month: $biggest")

        // People.
        val people = all.filter { it.person.isNotBlank() && (isLoan(it.type) || isTrade(it.type)) }
            .groupBy { it.person.trim().lowercase() }
        if (people.isNotEmpty()) {
            val bal = balances(all).mapKeys { it.key.trim().lowercase() }
            sb.appendLine("- People (balance > 0 means they owe you):")
            people.entries.sortedByDescending { abs(bal[it.key] ?: 0.0) }.take(if (compact) 10 else 25).forEach { (key, list) ->
                fun s(t: Type) = list.filter { it.type == t }.sumOf { it.amount }
                val parts = listOfNotNull(
                    s(Type.DIYA).takeIf { it > 0 }?.let { "lent ${f(it)}" },
                    s(Type.WAPIS_MILA).takeIf { it > 0 }?.let { "got back ${f(it)}" },
                    s(Type.LIYA).takeIf { it > 0 }?.let { "borrowed ${f(it)}" },
                    s(Type.WAPIS_DIYA).takeIf { it > 0 }?.let { "paid back ${f(it)}" },
                    s(Type.SALE).takeIf { it > 0 }?.let { "sold to them ${f(it)}" },
                    s(Type.PURCHASE).takeIf { it > 0 }?.let { "bought from them ${f(it)}" }
                )
                sb.appendLine("  ${list.first().person}: ${parts.joinToString(", ")}; balance ${f(bal[key] ?: 0.0)}")
            }
        }
        // Transfers by name (bank transfers are often to or from people without being loans).
        val transferNames = all.filter { isTransfer(it.type) && it.person.isNotBlank() }
            .groupBy { it.person.trim().lowercase() }
            .map { (_, g) ->
                val sent = g.filter { it.type == Type.TRANSFER_OUT }.sumOf { it.amount }
                val got = g.filter { it.type == Type.TRANSFER_IN }.sumOf { it.amount }
                "${g.first().person.trim()}: sent ${f(sent)}, received ${f(got)}"
            }.take(25)
        if (!compact && transferNames.isNotEmpty()) sb.appendLine("- Bank transfers by person, all time: " + transferNames.joinToString("; "))

        // Regular payments: the same item in at least 2 of the last 3 months.
        val recentMonths = between(monthStart(2, now), today + DAY).filter { it.type == Type.KHARCHA }
        val regular = recentMonths.groupBy { it.item.trim().lowercase() }
            .filter { (_, g) -> g.map { Calendar.getInstance().apply { timeInMillis = it.date }.get(Calendar.MONTH) }.toSet().size >= 2 }
            .map { (_, g) -> "${g.first().item} (${g.size}x, about ${f(g.sumOf { it.amount } / g.size)} each)" }
            .take(15)
        if (!compact && regular.isNotEmpty()) sb.appendLine("- Regular payments seen in 2+ of the last 3 months: " + regular.joinToString("; "))

        val cash = all.filter { isIn(it) }.sumOf { it.amount } - all.filter { isOut(it) }.sumOf { it.amount }
        sb.appendLine("- Cash in hand by the app's count: ${f(cash)} $c (all money in minus all money out since the first entry; " +
            "if there is no opening balance entry, the real balance is higher by whatever was there before).")
        return sb.toString().trim()
    }
}
