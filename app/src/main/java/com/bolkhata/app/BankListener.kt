package com.bolkhata.app

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/**
 * Reads notifications from bank, wallet and SMS apps. When one looks like a payment
 * ("Purchase SAR 25.00 at PANDA"), it is saved as a suggestion for the person to review.
 * Nothing leaves the phone.
 */
class BankListener : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName == packageName) return
        val extras = sbn.notification?.extras ?: return
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
            ?: extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        val full = "$title\n$text".trim()
        val entry = BankParser.parse(full, sbn.postTime) ?: return
        Store(this).addSuggestion(entry, key = sbn.packageName + "|" + text.trim())
    }

    companion object {
        fun isEnabled(context: Context): Boolean {
            val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners") ?: return false
            val me = ComponentName(context, BankListener::class.java).flattenToString()
            return flat.split(":").any { it == me }
        }
    }
}

object BankParser {

    private val cur = """SAR|SR|S\.R|ر\.س|ريال|PKR|Rs\.?|AED|USD|\$"""
    private val num = """\d{1,3}(?:,\d{3})*(?:\.\d{1,2})?|\d+(?:\.\d{1,2})?"""
    private val amountAfter = Regex("""(?i)(?:$cur)\s*:?\s*($num)""")
    private val amountBefore = Regex("""(?i)($num)\s*(?:$cur)""")

    private val ignore = listOf(
        "otp", "one time", "one-time", "verification", "verify", "code", "password", "رمز", "كلمة المرور",
        "declined", "rejected", "failed", "مرفوض", "offer", "discount", "عرض"
    )
    private val incoming = listOf(
        "credited", "deposit", "deposited", "received", "incoming", "refund", "reversal", "salary", "cashback",
        "إيداع", "ايداع", "راتب", "استرداد", "واردة", "وارد", "مستلمة"
    )
    /** Words that clearly mean money went out. */
    private val outgoing = listOf(
        "purchase", "purchased", "pos", "debited", "debit", "paid", "payment", "spent", "withdraw", "withdrawal",
        "atm", "mada", "apple pay", "شراء", "مشتريات", "سحب", "خصم", "مدى", "دفع", "صادرة"
    )

    /** Words that mean money moved, direction unclear: treated as going out unless an incoming word is present. */
    private val neutral = listOf("transfer", "sent", "حوالة", "تحويل")

    private val merchantRegex = Regex(
        """(?i)(?:\bat\b|\bfrom\b|\bto\b|@|لدى|من|في)\s*:?\s*([A-Za-z؀-ۿ][A-Za-z0-9؀-ۿ&'.\- ]{1,40})"""
    )
    private val merchantStop = Regex("""(?i)\s+(on|with|using|card|via|ref|balance|date|at)\b.*$""")

    /** English keywords must match a whole word ("pos" must not match "deposit"); Arabic ones match anywhere. */
    private fun has(t: String, key: String): Boolean =
        if (key.all { it.code < 128 }) Regex("""(?<![a-z])""" + Regex.escape(key) + """(?![a-z])""").containsMatchIn(t)
        else t.contains(key)

    /** Returns an entry when the text is a payment or money received, else null. */
    fun parse(text: String, time: Long): Entry? {
        val t = text.lowercase()
        if (ignore.any { has(t, it) }) return null
        val m = amountAfter.find(text) ?: amountBefore.find(text) ?: return null
        val amount = m.groupValues[1].replace(",", "").toDoubleOrNull() ?: return null
        if (amount <= 0) return null

        val isIn = incoming.any { has(t, it) }
        val isOut = outgoing.any { has(t, it) }
        val moved = neutral.any { has(t, it) }
        if (!isIn && !isOut && !moved) return null
        val type = if (isIn && !isOut) Type.AAMDANI else Type.KHARCHA

        val raw = merchantRegex.find(text.substringAfter(m.value, text))?.groupValues?.get(1)
            ?: merchantRegex.find(text)?.groupValues?.get(1)
            ?: ""
        val cleaned = raw.replace(merchantStop, "").trim().trimEnd('.', ',', '-')
        val low = cleaned.lowercase()
        val merchant = if (low.startsWith("your") || low.contains("account") || cleaned.contains("حساب")) "" else cleaned

        val guess = Parser.guessCategory(merchant) ?: Parser.guessCategory(text)
        val cat = if (type == Type.AAMDANI) Cat.TANKHWAH else guess?.first ?: Cat.OTHER
        val item = when {
            type == Type.AAMDANI -> if (has(t, "salary") || t.contains("راتب")) "Salary" else "Money received"
            guess != null -> guess.second
            else -> "Other"
        }
        val label = merchant.ifBlank { if (type == Type.AAMDANI) "Money received" else "Card payment" }
        return Entry(
            id = "b" + time + "_" + amount.hashCode(),
            type = type, amount = amount, category = cat, item = item, person = "",
            note = label.take(40), date = time, createdAt = time
        )
    }
}
