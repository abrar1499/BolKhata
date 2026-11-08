package com.bolkhata.app

import androidx.compose.foundation.BorderStroke

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.draw.shadow
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.foundation.clickable
import androidx.compose.runtime.getValue
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.Chair
import androidx.compose.material.icons.outlined.Handshake
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Factory
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.LocalGasStation
import androidx.compose.material.icons.outlined.MedicalServices
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.Sell
import androidx.compose.material.icons.outlined.ShoppingBag
import androidx.compose.material.icons.outlined.VolunteerActivism
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.roundToLong

// ---------- colours & theme ----------

val GreenDeep = Color(0xFF047857)

/** Emerald gradient for the mic and other accent buttons. */
val AccentBrush = Brush.linearGradient(
    colors = listOf(Color(0xFF047857), Color(0xFF10B981), Color(0xFF34D399)),
    start = Offset(0f, 0f),
    end = Offset(160f, 160f)
)

/** One colour theme for the header, side menu and profile banner. */
data class HeaderTheme(val name: String, val c1: Color, val c2: Color, val c3: Color, val light: Boolean) {
    val brush: Brush
        get() = Brush.linearGradient(listOf(c1, c2, c3), start = Offset(0f, 0f), end = Offset(1200f, 900f))

    /** Text and icon colour on top of the header. */
    val on: Color get() = if (light) Color(0xFF1F2937) else Color.White

    /** Background for round buttons on the header. */
    val chip: Color get() = when {
        flat -> on.copy(alpha = 0.06f)
        light -> Color.White.copy(alpha = 0.7f)
        else -> Color.White.copy(alpha = 0.22f)
    }

    /** The plain "Clean" look: one flat colour, no gradient or circles. */
    val flat: Boolean get() = c1 == c2 && c2 == c3

    /** Colour at the very top of the band (its first colour under the glossy sheen), also used for the status bar. */
    val top: Color get() = androidx.compose.ui.graphics.lerp(c1, Color.White, 0.12f)
}

private fun h(name: String, a: Long, b: Long, c: Long, light: Boolean) = HeaderTheme(name, Color(a), Color(b), Color(c), light)

/** All themes the person can pick in Settings. The first one is the default. */
val HEADER_THEMES = listOf(
    h("Red", 0xFFE3342F, 0xFFE3342F, 0xFFDC2F2A, false),
    h("Clean", 0xFFF3F1EF, 0xFFF3F1EF, 0xFFF3F1EF, true),
    h("Sand", 0xFFFED7AA, 0xFFFDE7CC, 0xFFFFF5EA, true),
    h("Mint", 0xFFA7F3D0, 0xFFC6F6E0, 0xFFE3FBF1, true),
    h("Sky", 0xFFBAE6FD, 0xFFD6EFFE, 0xFFEEF8FF, true),
    h("Lavender", 0xFFDDD6FE, 0xFFE9E4FF, 0xFFF5F3FF, true),
    h("Teal", 0xFF0D9488, 0xFF14B8A6, 0xFF5EEAD4, false),
    h("Sky Blue", 0xFF0284C7, 0xFF0EA5E9, 0xFF7DD3FC, false),
    h("Indigo", 0xFF4F46E5, 0xFF6366F1, 0xFFA5B4FC, false),
    h("Violet", 0xFF7C3AED, 0xFF8B5CF6, 0xFFC4B5FD, false),
    h("Green", 0xFF053D2B, 0xFF0B7A45, 0xFF19B36A, false),
    h("Forest", 0xFF022C22, 0xFF064E3B, 0xFF0F766E, false),
    h("Navy", 0xFF0F172A, 0xFF1E3A5F, 0xFF2C5282, false),
    h("Plum", 0xFF2A1033, 0xFF4C1D5E, 0xFF6B2C7A, false),
    h("Slate", 0xFF1E293B, 0xFF334155, 0xFF475569, false),
    h("Black", 0xFF0B0B0F, 0xFF1F2937, 0xFF374151, false)
)

/** The header theme to show; "Clean" follows dark mode so its text stays readable. */
fun headerTheme(name: String, dark: Boolean = false): HeaderTheme {
    val theme = HEADER_THEMES.firstOrNull { it.name == name } ?: HEADER_THEMES.first()
    return if (theme.name == "Clean" && dark) h("Clean", 0xFF0F0F0F, 0xFF0F0F0F, 0xFF0F0F0F, false) else theme
}

val LocalHeader = staticCompositionLocalOf { HEADER_THEMES.first() }

/** Height of the floating message bar; screens scroll underneath it, so lists end with this much empty space. */
val LocalBarSpace = compositionLocalOf { 0.dp }

/** Bottom padding for a full-screen list: room for the floating bar plus [extra]. */
@Composable
fun barSpace(extra: Dp = 24.dp): Dp = LocalBarSpace.current + extra

val Green = Color(0xFF10B981)
val RedSpend = Color(0xFFD6403A)
val GreenIn = Color(0xFF1F9A54)
val OrangeOwe = Color(0xFFE8892B)

fun catColor(c: Cat): Color = when (c) {
    Cat.KHANA -> Color(0xFFEF6C57)
    Cat.TRANSPORT -> Color(0xFF3B82F6)
    Cat.RENT -> Color(0xFF8B5CF6)
    Cat.BIJLI -> Color(0xFFF5B000)
    Cat.MOBILE -> Color(0xFF06B6D4)
    Cat.GHAR -> Color(0xFF10B981)
    Cat.SHOPPING -> Color(0xFFEC4899)
    Cat.SEHAT -> Color(0xFF14B8A6)
    Cat.PERSONAL -> Color(0xFFF97316)
    Cat.TANKHWAH -> Color(0xFF22C55E)
    Cat.UDHAAR -> Color(0xFF64748B)
    Cat.FEES -> Color(0xFF6366F1)
    Cat.EDU -> Color(0xFF0EA5E9)
    Cat.CHARITY -> Color(0xFFDB2777)
    Cat.ASSETS -> Color(0xFF7C3AED)
    Cat.SALES -> Color(0xFF16A34A)
    Cat.STOCK -> Color(0xFF0891B2)
    Cat.PRODUCTION -> Color(0xFFB45309)
    Cat.OTHER -> Color(0xFF94A3B8)
}

/** Inter, bundled in res/font (SIL Open Font License, see docs/inter-font-LICENSE.txt). Other scripts fall back to the system font. */
val Inter = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
    Font(R.font.inter_bold, FontWeight.Bold)
)

/**
 * Every Material text style in Inter with tight letter spacing. Line height follows the text size (1.25×): the Material
 * default is a fixed 24sp, which made small text in cards and rows far taller than the design.
 */
val AppTypography: Typography = Typography().run {
    fun TextStyle.clean() = copy(fontFamily = Inter, letterSpacing = 0.sp, lineHeight = 1.25.em)
    copy(
        displayLarge = displayLarge.clean(), displayMedium = displayMedium.clean(), displaySmall = displaySmall.clean(),
        headlineLarge = headlineLarge.clean(), headlineMedium = headlineMedium.clean(), headlineSmall = headlineSmall.clean(),
        titleLarge = titleLarge.clean(), titleMedium = titleMedium.clean(), titleSmall = titleSmall.clean(),
        bodyLarge = bodyLarge.clean(), bodyMedium = bodyMedium.clean(), bodySmall = bodySmall.clean(),
        labelLarge = labelLarge.clean(), labelMedium = labelMedium.clean(), labelSmall = labelSmall.clean()
    )
}

/** Light page colours offered in the side menu (Background): warm grey (the default on every new install), light slate. */
val PAGE_COLORS = listOf(0xFFE9E6E1, 0xFFE6E8EB)

/** One dark look; it changes the whole app: page, cards, wells, edges, the ledger's day strip and the search / chat field. */
class DarkLook(val page: Long, val card: Long, val well: Long, val edge: Long, val day: Long, val field: Long)

/** Dark looks, dull rather than pure black: Graphite (the default), Navy. */
val DARK_LOOKS = listOf(
    DarkLook(0xFF3A3D42, 0xFF2A2C30, 0xFF34373C, 0xFF4A4E54, 0xFF33373E, 0xFF45484D),
    DarkLook(0xFF1B2230, 0xFF121826, 0xFF1D2535, 0xFF2C3648, 0xFF222B3C, 0xFF252D3D)
)
val DARK_PAGES = DARK_LOOKS.map { it.page }

/** The dark look in use, or null in light mode. */
val LocalLook = staticCompositionLocalOf<DarkLook?> { null }

/** The page colour of the theme, even under a photo: popups and sheets wear it, with white fields on it. */
val LocalPage = staticCompositionLocalOf { Color(0xFFE9E6E1) }

/** The currency picked in the side menu (PKR, SAR…), shown small after amounts. */
val LocalCurrency = staticCompositionLocalOf { "" }

@Composable
/** [clear]: a background photo shows through, so the page colour is see-through. [page]: the light page colour. */
fun BolTheme(dark: Boolean, clear: Boolean = false, page: Long = PAGE_COLORS[0], content: @Composable () -> Unit) {
    // [page] is one light or one dark colour; a light one in dark mode (or the other way) falls back to the first.
    val look = if (dark) DARK_LOOKS.firstOrNull { it.page == page } ?: DARK_LOOKS[0] else null
    val bg = Color(look?.page ?: page.takeIf { it in PAGE_COLORS } ?: PAGE_COLORS[0])
    val scheme = if (look != null) darkColorScheme(
        primary = Color(0xFF34D399),
        onPrimary = Color(0xFF00210F),
        primaryContainer = Color(0xFF064E3B),
        onPrimaryContainer = Color(0xFFD1FAE5),
        secondaryContainer = Color(0xFF064E3B),
        onSecondaryContainer = Color(0xFFD1FAE5),
        // The dark look's page, cards and lines (Black by default).
        background = bg,
        surface = Color(look.card),
        surfaceVariant = Color(look.well),
        onSurfaceVariant = Color(0xFFA3A3A3),
        outline = Color(look.edge),
        outlineVariant = Color(look.well),
        // Menus white-ish (the card), dialogs and sheets the page: no system purple tint anywhere.
        surfaceContainerLowest = Color(look.card), surfaceContainerLow = bg, surfaceContainer = Color(look.card),
        surfaceContainerHigh = bg, surfaceContainerHighest = Color(look.well), surfaceTint = Color.Transparent
    ) else lightColorScheme(
        primary = Color(0xFF059669),
        onPrimary = Color.White,
        primaryContainer = Color(0xFFD1FAE5),
        onPrimaryContainer = Color(0xFF065F46),
        secondaryContainer = Color(0xFFD1FAE5),
        onSecondaryContainer = Color(0xFF065F46),
        // The light page colour picked in the side menu (grey by default); cards stay white on it.
        background = bg,
        onBackground = Color(0xFF0D0D0D),
        surface = Color.White,
        onSurface = Color(0xFF0D0D0D),
        surfaceVariant = Color(0xFFF7F5F3),
        onSurfaceVariant = Color(0xFF6B6560),
        outline = Color(0xFFE6E3DF),
        outlineVariant = Color(0xFFECE7E3),
        // Menus white, dialogs and sheets the page colour: no system purple tint anywhere.
        surfaceContainerLowest = Color.White, surfaceContainerLow = bg, surfaceContainer = Color.White,
        surfaceContainerHigh = bg, surfaceContainerHighest = Color(0xFFF1F1F2), surfaceTint = Color.Transparent
    )
    androidx.compose.runtime.CompositionLocalProvider(LocalLook provides look, LocalPage provides bg) {
        MaterialTheme(colorScheme = if (clear) scheme.copy(background = Color.Transparent) else scheme, typography = AppTypography, content = content)
    }
}

// ---------- formatting ----------

/** 1,234 or 7.5 or 8.25 — decimals only when the amount has them. */
fun fmt(n: Double): String =
    java.text.DecimalFormat("#,##0.##", java.text.DecimalFormatSymbols(Locale.US)).format(if (kotlin.math.abs(n) < 0.005) 0.0 else n)

/** "15 SAR" as plain text, for sentences. */
fun money(cur: String, n: Double) = "${fmt(n)} $cur"

/** "15 SAR" with the currency in small letters, for amounts shown on their own. */
fun moneyA(cur: String, n: Double, prefix: String = ""): AnnotatedString = buildAnnotatedString {
    append(prefix + fmt(n))
    withStyle(SpanStyle(fontSize = 0.55.em, fontWeight = FontWeight.SemiBold)) { append(" $cur") }
}
fun plain(n: Double): String = if (n == Math.floor(n)) n.roundToLong().toString() else n.toString()

fun cal(ms: Long): Calendar = Calendar.getInstance().apply { timeInMillis = ms }

/** The local calendar day of [ms] as one number: cheap enough to call for every row of a long list. */
fun dayNumber(ms: Long): Long {
    val zone = java.util.TimeZone.getDefault()
    return Math.floorDiv(ms + zone.getOffset(ms), 86_400_000L)
}

fun sameDay(a: Long, b: Long): Boolean = dayNumber(a) == dayNumber(b)

/** Same calendar month and year. */
fun sameMonth(a: Long, b: Long): Boolean = cal(a).let { x -> cal(b).let { y -> x.get(Calendar.YEAR) == y.get(Calendar.YEAR) && x.get(Calendar.MONTH) == y.get(Calendar.MONTH) } }

fun dayLabel(d: Long): String {
    val now = System.currentTimeMillis()
    return when {
        sameDay(d, now) -> "Today"
        sameDay(d, now - 86_400_000L) -> "Yesterday"
        else -> SimpleDateFormat("d MMM yyyy", Locale.US).format(Date(d))
    }
}

/** "31 Oct 2026", always the date (home's day rows add "Today" / "Yesterday" beside it). */
fun dayDate(d: Long): String = SimpleDateFormat("d MMM yyyy", Locale.US).format(Date(d))

/** Time the entry was added, e.g. "7:51 AM". */
fun timeLabel(ms: Long): String = SimpleDateFormat("h:mm a", Locale.US).format(Date(ms))

fun shortDate(d: Long): String = SimpleDateFormat("d MMM", Locale.US).format(Date(d))

/** Local date -> UTC midnight (what the date picker uses). */
fun toUtcMidnight(ms: Long): Long {
    val l = cal(ms)
    return Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
        clear()
        set(l.get(Calendar.YEAR), l.get(Calendar.MONTH), l.get(Calendar.DAY_OF_MONTH))
    }.timeInMillis
}

/** Date picker value -> local noon of that day. */
fun fromUtcMidnight(ms: Long): Long {
    val u = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = ms }
    return Calendar.getInstance().apply {
        clear()
        set(u.get(Calendar.YEAR), u.get(Calendar.MONTH), u.get(Calendar.DAY_OF_MONTH), 12, 0, 0)
    }.timeInMillis
}

fun isLoan(t: Type) = t == Type.DIYA || t == Type.LIYA || t == Type.WAPIS_MILA || t == Type.WAPIS_DIYA

fun isTrade(t: Type) = t == Type.SALE || t == Type.PURCHASE

fun isTransfer(t: Type) = t == Type.TRANSFER_IN || t == Type.TRANSFER_OUT

/** True when this entry belongs in a named person history: loans, trades, and transfers. */
fun withPerson(e: Entry) = e.person.isNotBlank() && (isLoan(e.type) || isTrade(e.type) || isTransfer(e.type))

/** "On credit" / "Paid" for a sale or purchase with a person, else empty. */
fun creditTag(e: Entry) = if (isTrade(e.type) && e.person.isNotBlank()) (if (e.paid) "Paid" else "On credit") else ""

/** Positive = they owe me, negative = I owe them. */
fun balances(entries: List<Entry>): Map<String, Double> {
    val map = linkedMapOf<String, Double>()
    for (e in entries) {
        if (!withPerson(e) || e.paid) continue
        val p = e.person.trim()
        // Same rule as the khata: loans, credit trade and bank transfers to / from the person.
        map[p] = (map[p] ?: 0.0) + personEffect(e)
    }
    return map
}

/** Items in hand: bought or received minus sold or given, for entries with a quantity. */
data class StockLine(val name: String, val unit: String, val qty: Double)

fun stock(entries: List<Entry>): List<StockLine> {
    val map = linkedMapOf<String, StockLine>()
    for (e in entries) {
        if (e.qty <= 0 || e.item.isBlank() || e.item == "Loans") continue
        val sign = when (e.type) {
            Type.PURCHASE, Type.LIYA -> 1.0
            Type.SALE, Type.DIYA -> -1.0
            else -> continue
        }
        val key = e.item.trim().lowercase()
        val old = map[key]
        map[key] = StockLine(old?.name ?: e.item.trim(), old?.unit?.ifBlank { e.unit } ?: e.unit, (old?.qty ?: 0.0) + sign * e.qty)
    }
    return map.values.toList()
}

data class ProfitItemBreakdown(
    val item: String,
    val salesAmount: Double,
    val costedSalesAmount: Double,
    val costOfGoods: Double,
    val estimatedProfit: Double,
    val salesWithoutCost: Double,
    val entries: List<Entry>
)

data class ProfitBreakdown(
    val salesAmount: Double,
    val costedSalesAmount: Double,
    val costOfGoods: Double,
    val salesWithoutCost: Double,
    val otherIncome: Double,
    val expenses: Double,
    val netProfit: Double,
    val items: List<ProfitItemBreakdown>
)

/** What goods in stock cost: the price paid, or for goods made here (no money paid) qty × the cost per unit. */
fun costOf(e: Entry) = if (e.amount > 0) e.amount else e.rate * e.qty

/** Goods made here (Production): they come into stock at what one cost to make, with no money paid. */
fun isMade(e: Entry) = e.sourceFingerprint.startsWith("made:")

/** What one of [qty] goods made now cost: this month's production costs over everything made this month, these too. */
fun madeRate(all: List<Entry>, qty: Double, now: Long = System.currentTimeMillis()): Double {
    val month = all.filter { sameMonth(it.date, now) }
    val cost = month.filter { it.type == Type.KHARCHA && it.category == Cat.PRODUCTION }.sumOf { it.amount } +
        month.filter { it.type == Type.PURCHASE && it.category == Cat.PRODUCTION && !isMade(it) }.sumOf { it.amount }
    val made = month.filter(::isMade).sumOf { it.qty }
    return if (cost > 0 && made + qty > 0) cost / (made + qty) else 0.0
}

fun madeEntry(item: String, qty: Double, rate: Double, date: Long, unit: String = ""): Entry {
    val id = "e" + System.nanoTime()
    return Entry(id, Type.PURCHASE, 0.0, Cat.STOCK, item, "", "Made", date, System.currentTimeMillis(),
        qty = qty, unit = unit, rate = rate, paid = true, sourceFingerprint = "made:$id")
}

/** Estimated business profit uses recorded item cost before each sale; other Cash In is kept separate. */
fun profitBreakdown(allEntries: List<Entry>, periodEntries: List<Entry>): ProfitBreakdown {
    val sales = periodEntries.filter { it.type == Type.SALE }
    val costBySaleId = linkedMapOf<String, Double>()
    // Purchases grouped by item once, so a year of sales doesn't scan every entry for each sale.
    val purchasesByItem = if (sales.isEmpty()) emptyMap() else allEntries
        .filter { it.type == Type.PURCHASE && it.qty > 0.0 && costOf(it) > 0.0 }
        .groupBy { it.item.trim().lowercase() }
    sales.forEach { sale ->
        if (sale.qty <= 0.0 || sale.item.isBlank() || sale.item.equals("Sale", ignoreCase = true)) return@forEach
        val purchases = purchasesByItem[sale.item.trim().lowercase()].orEmpty().filter { it.date <= sale.date }
        val purchasedQty = purchases.sumOf { it.qty }
        if (purchasedQty > 0.0) {
            costBySaleId[sale.id] = sale.qty * (purchases.sumOf { costOf(it) } / purchasedQty)
        }
    }
    val itemLines = sales.groupBy { it.item.trim().ifBlank { "Sale" } }
        .map { (name, list) ->
            val costed = list.filter { costBySaleId.containsKey(it.id) }
            val costedSales = costed.sumOf { it.amount }
            val cost = costed.sumOf { costBySaleId[it.id] ?: 0.0 }
            val withoutCost = list.filterNot { costBySaleId.containsKey(it.id) }.sumOf { it.amount }
            ProfitItemBreakdown(
                item = name,
                salesAmount = list.sumOf { it.amount },
                costedSalesAmount = costedSales,
                costOfGoods = cost,
                estimatedProfit = costedSales - cost,
                salesWithoutCost = withoutCost,
                entries = list.sortedWith(compareByDescending<Entry> { it.date }.thenByDescending { it.createdAt })
            )
        }
        .sortedByDescending { it.estimatedProfit }
    val costedSalesAmount = sales.filter { costBySaleId.containsKey(it.id) }.sumOf { it.amount }
    val costOfGoods = costBySaleId.values.sum()
    val totalSales = sales.sumOf { it.amount }
    val salesWithoutCost = (totalSales - costedSalesAmount).coerceAtLeast(0.0)
    val otherIncome = periodEntries.filter { it.type == Type.AAMDANI }.sumOf { it.amount }
    val expenses = periodEntries.filter { it.type == Type.KHARCHA }.sumOf { it.amount }
    return ProfitBreakdown(
        salesAmount = totalSales,
        costedSalesAmount = costedSalesAmount,
        costOfGoods = costOfGoods,
        salesWithoutCost = salesWithoutCost,
        otherIncome = otherIncome,
        expenses = expenses,
        netProfit = costedSalesAmount - costOfGoods - expenses,
        items = itemLines
    )
}

fun signColor(t: Type) = when (t) {
    Type.KHARCHA, Type.WAPIS_DIYA, Type.PURCHASE, Type.TRANSFER_OUT -> RedSpend
    Type.AAMDANI, Type.WAPIS_MILA, Type.SALE, Type.TRANSFER_IN -> GreenIn
    Type.DIYA, Type.LIYA -> OrangeOwe
}

fun prefix(t: Type) = when (t) {
    Type.KHARCHA, Type.WAPIS_DIYA, Type.PURCHASE, Type.TRANSFER_OUT -> "−"
    Type.AAMDANI, Type.WAPIS_MILA, Type.SALE, Type.TRANSFER_IN -> "+"
    else -> ""
}

fun typeLabel(t: Type, person: String): String {
    val p = person.trim()
    return when (t) {
        Type.KHARCHA -> "Cash Out"
        Type.AAMDANI -> "Cash In"
        Type.DIYA -> if (p.isEmpty()) "Gave (to get back)" else "Gave to $p"
        Type.LIYA -> if (p.isEmpty()) "Took (to give back)" else "Took from $p"
        Type.WAPIS_MILA -> if (p.isEmpty()) "Got money back" else "$p returned"
        Type.WAPIS_DIYA -> if (p.isEmpty()) "Paid back" else "Returned to $p"
        Type.SALE -> if (p.isEmpty()) "Sale" else "Sold to $p"
        Type.PURCHASE -> if (p.isEmpty()) "Purchase" else "Bought from $p"
        Type.TRANSFER_IN -> if (p.isEmpty()) "Transfer in" else "Transfer from $p"
        Type.TRANSFER_OUT -> if (p.isEmpty()) "Transfer out" else "Transfer to $p"
    }
}

/** "1000 Awal eint", "2 kg Cheeni"; empty when no quantity was said. */
fun qtyText(e: Entry): String {
    if (e.qty <= 0) return ""
    val item = if (e.item.isBlank() || e.item == "Loans") "" else e.item
    return listOf(fmt(e.qty), e.unit, item).filter { it.isNotBlank() }.joinToString(" ")
}

fun entryTitle(e: Entry): String = when {
    isTransfer(e.type) -> typeLabel(e.type, e.person) + (if (e.item.isNotBlank() && !e.item.equals("Transfer", true)) ": " + e.item else "")
    isLoan(e.type) -> typeLabel(e.type, e.person)
    isTrade(e.type) && e.person.isNotBlank() -> typeLabel(e.type, e.person)
    isTrade(e.type) -> typeLabel(e.type, "") + (if (e.item.isNotBlank() && e.item != "Loans") ": " + e.item else "")
    e.item.isNotBlank() -> e.item
    else -> e.category.label
}

// ---------- building blocks ----------

@Composable
fun Panel(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    padding: Dp = 14.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(22.dp)
    if (onClick != null) {
        Surface(onClick = onClick, modifier = modifier, shape = shape, color = MaterialTheme.colorScheme.surface, shadowElevation = 0.dp, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))) {
            Column(Modifier.padding(padding), content = content)
        }
    } else {
        Surface(modifier = modifier, shape = shape, color = MaterialTheme.colorScheme.surface, shadowElevation = 0.dp, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))) {
            Column(Modifier.padding(padding), content = content)
        }
    }
}

@Composable
fun CatBubble(cat: Cat, size: Dp = 44.dp) {
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(catColor(cat).copy(alpha = 0.13f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(catIcon(cat), contentDescription = cat.label, tint = catColor(cat), modifier = Modifier.size(size * 0.48f))
    }
}

/** A simple line icon for each category, used instead of emoji. */
fun catIcon(c: Cat): ImageVector = when (c) {
    Cat.KHANA -> Icons.Outlined.Restaurant
    Cat.TRANSPORT -> Icons.Outlined.LocalGasStation
    Cat.RENT -> Icons.Outlined.Home
    Cat.BIJLI -> Icons.Outlined.Bolt
    Cat.MOBILE -> Icons.Outlined.PhoneAndroid
    Cat.GHAR -> Icons.Outlined.Chair
    Cat.SHOPPING -> Icons.Outlined.ShoppingBag
    Cat.SEHAT -> Icons.Outlined.MedicalServices
    Cat.PERSONAL -> Icons.Outlined.Person
    Cat.TANKHWAH -> Icons.Outlined.Payments
    Cat.UDHAAR -> Icons.Outlined.Handshake
    Cat.FEES -> Icons.Outlined.AccountBalance
    Cat.EDU -> Icons.Outlined.School
    Cat.CHARITY -> Icons.Outlined.VolunteerActivism
    Cat.ASSETS -> Icons.Outlined.Sell
    Cat.SALES -> Icons.Outlined.ReceiptLong
    Cat.STOCK -> Icons.Outlined.Inventory2
    Cat.PRODUCTION -> Icons.Outlined.Factory
    Cat.OTHER -> Icons.Outlined.Category
}

@Composable
fun SmallLabel(s: String) =
    Text(
        s.uppercase(), fontSize = 12.sp, letterSpacing = 1.sp, fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )

@Composable
fun TxRow(e: Entry, currency: String, showDate: Boolean = true, onClick: () -> Unit) {
    // Long-press opens the Edit / Move / Delete menu; a tap does nothing.
    Panel(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).entryTap(e, onClick), padding = 0.dp) {
        TxRowContent(e, currency, showDate)
    }
}

/** The inside of an entry row: icon, title, one line of details, amount. */
@Composable
fun TxRowContent(e: Entry, currency: String, showDate: Boolean) {
    val detail = qtyText(e).ifBlank {
        if ((isLoan(e.type) || isTrade(e.type)) && e.item.isNotBlank() && e.item != "Loans") e.item else e.note
    }
    val sub = listOfNotNull(
        if (showDate) dayLabel(e.date) else null,
        detail.takeIf { it.isNotBlank() },
        timeLabel(e.createdAt),
        creditTag(e).takeIf { it.isNotEmpty() }
    ).joinToString(" · ")
    Row(
        Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CatBubble(e.category, 36.dp)
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f)) {
            Text(entryTitle(e), fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 18.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                sub, fontSize = 12.sp, lineHeight = 15.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(
            moneyA(currency, e.amount, prefix(e.type)),
            fontWeight = FontWeight.Bold, color = signColor(e.type), fontSize = 15.sp
        )
    }
}

@Composable
fun ScreenTitle(title: String, onBack: (() -> Unit)? = null, onMenu: (() -> Unit)? = null) {
    // The same glass orange as the header on every tab.
    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))) {
        GlassHeader(Modifier.matchParentSize())
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 4.dp, end = 14.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (onBack != null) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White) }
            }
            if (onMenu != null) {
                IconButton(onClick = onMenu) { Icon(Icons.Filled.Menu, contentDescription = "Menu", tint = Color.White) }
            }
            Text(title, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

// ---------- drawn icons (no extra icon library needed) ----------

@Composable
fun MicGlyph(color: Color, size: Dp = 26.dp) {
    Canvas(Modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val sw = w * 0.09f
        drawRoundRect(color, topLeft = Offset(w * 0.35f, h * 0.06f), size = Size(w * 0.30f, h * 0.50f), cornerRadius = CornerRadius(w * 0.15f))
        drawArc(
            color, startAngle = 0f, sweepAngle = 180f, useCenter = false,
            topLeft = Offset(w * 0.22f, h * 0.24f), size = Size(w * 0.56f, h * 0.50f),
            style = Stroke(width = sw, cap = StrokeCap.Round)
        )
        drawLine(color, start = Offset(w * 0.5f, h * 0.74f), end = Offset(w * 0.5f, h * 0.90f), strokeWidth = sw, cap = StrokeCap.Round)
        drawLine(color, start = Offset(w * 0.34f, h * 0.92f), end = Offset(w * 0.66f, h * 0.92f), strokeWidth = sw, cap = StrokeCap.Round)
    }
}

/** Gradient header with soft light circles, for a premium feel. */
@Composable
fun HeroBackground(modifier: Modifier = Modifier) {
    val theme = LocalHeader.current
    if (theme.flat) {
        Box(modifier.background(theme.c1))
        return
    }
    val k = if (theme.light) 5f else 1f
    Canvas(modifier.background(theme.brush)) {
        val w = size.width
        val h = size.height
        drawCircle(Color.White.copy(alpha = 0.07f * k), radius = w * 0.42f, center = Offset(w * 0.95f, h * 0.05f))
        drawCircle(Color.White.copy(alpha = 0.05f * k), radius = w * 0.28f, center = Offset(w * 0.78f, h * 0.95f))
        drawCircle(Color.White.copy(alpha = 0.04f * k), radius = w * 0.18f, center = Offset(w * 0.05f, h * 0.15f))
    }
}

/** A clean pop-up list (white, rounded) that opens at the right edge of the row whose Box it is placed in. */
@Composable
fun BoxScope.PickMenu(open: Boolean, onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Box(Modifier.align(Alignment.BottomEnd).padding(end = 16.dp)) {
        DropdownMenu(
            expanded = open, onDismissRequest = onDismiss, shape = RoundedCornerShape(16.dp),
            containerColor = MaterialTheme.colorScheme.surface, tonalElevation = 0.dp, shadowElevation = 6.dp,
            content = content
        )
    }
}

/**
 * A compact pick list in a small white card under the row whose Box it is placed in: a search box when [search],
 * then one short line per item in full ("QAR · Qatari Riyal"), the picked one bold in the app's colour.
 */
@Composable
fun BoxScope.CompactPicker(open: Boolean, items: List<Pair<String, String>>, selected: String, search: Boolean, onDismiss: () -> Unit, onPick: (String) -> Unit) {
    if (!open) return
    var q by remember { mutableStateOf("") }
    Box(Modifier.align(Alignment.BottomEnd).padding(end = 12.dp)) {
        androidx.compose.ui.window.Popup(
            alignment = Alignment.TopEnd, onDismissRequest = onDismiss,
            properties = androidx.compose.ui.window.PopupProperties(focusable = true)
        ) {
            val shape = RoundedCornerShape(14.dp)
            Column(Modifier.width(270.dp).shadow(8.dp, shape).clip(shape).background(MaterialTheme.colorScheme.surface).padding(vertical = 6.dp)) {
                if (search) androidx.compose.foundation.text.BasicTextField(
                    q, { q = it }, singleLine = true,
                    textStyle = TextStyle(fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface),
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp).fillMaxWidth().height(34.dp).fieldEdge(RoundedCornerShape(10.dp))
                        .padding(horizontal = 10.dp).wrapContentHeight(Alignment.CenterVertically),
                    decorationBox = { f -> Box { if (q.isEmpty()) Text("Search", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant); f() } }
                )
                val shown = items.filter { (k, n) -> q.isBlank() || k.contains(q.trim(), true) || n.contains(q.trim(), true) }
                androidx.compose.foundation.lazy.LazyColumn(Modifier.heightIn(max = 300.dp)) {
                    items(shown.size) { i ->
                        val (k, n) = shown[i]
                        val on = k == selected
                        Text(
                            if (n.isBlank() || n == k) k else "$k · $n", fontSize = 13.sp, maxLines = 1,
                            fontWeight = if (on) FontWeight.Bold else FontWeight.Normal,
                            color = if (on) Color(0xFFF0572E) else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.fillMaxWidth().clickable { onPick(k) }.padding(horizontal = 14.dp, vertical = 7.dp)
                        )
                    }
                }
            }
        }
    }
}

// ---------- the glass look of the app icon ----------

/** The app icon's colours: red-orange to deep red. */
val BrandGlass = listOf(Color(0xFFFF7A45), Color(0xFFE3342F), Color(0xFF9F1239))

/** Colours of a header band: the icon's for Red, else the theme's own. */
fun bandColors(theme: HeaderTheme) = if (theme.name == "Red") BrandGlass else listOf(theme.c1, theme.c2, theme.c3)

/** Colours behind white marks (the logo, headers with white text): the icon's on Red and the light themes. */
fun glassColors(theme: HeaderTheme) = if (theme.light || theme.name == "Red") BrandGlass else listOf(theme.c1, theme.c2, theme.c3)

/** A header band like the icon: the colours on a diagonal and one soft round light at the top right, [h] tall. */
fun DrawScope.drawGlass(colors: List<Color>, h: Float) {
    val w = size.width
    drawRect(Brush.linearGradient(0f to colors[0], 0.55f to colors[1], 1f to colors[2], start = Offset.Zero, end = Offset(w, h)), size = Size(w, h))
    clipRect(right = w, bottom = h) {
        drawCircle(Color.White.copy(alpha = 0.2f), radius = w * 0.42f, center = Offset(w * 0.92f, -w * 0.12f))
    }
}

/**
 * The app icon (a frosted wallet on the glass colours) as a small logo, in the theme's colours. [onBand]: it sits on a
 * band of the same colours, so it gets a white edge and a deeper shadow to stand apart.
 */
@Composable
fun GlassLogo(size: Dp, modifier: Modifier = Modifier, onBand: Boolean = false) {
    val c = glassColors(LocalHeader.current)
    val shape = RoundedCornerShape(size * 0.26f)
    Canvas(
        modifier.size(size)
            .shadow(if (onBand) size * 0.12f else 2.dp, shape)
            .then(if (onBand) Modifier.border(1.dp, Color.White.copy(alpha = 0.95f), shape) else Modifier)
            .clip(shape)
    ) {
        val s = this.size.width / 108f
        // The wallet is drawn 1.25× around the middle, as in the launcher icon's artwork.
        fun p(x: Float, y: Float) = Offset((54 + 1.25f * (x - 56)) * s, (54 + 1.25f * (y - 51)) * s)
        fun l(v: Float) = v * 1.25f * s
        drawRect(Brush.linearGradient(0f to c[0], 0.55f to c[1], 1f to c[2], start = Offset.Zero, end = Offset(this.size.width, this.size.height)))
        drawCircle(Color.White.copy(alpha = 0.18f), radius = 26 * s, center = Offset(84 * s, 22 * s))
        drawPath(androidx.compose.ui.graphics.Path().apply {
            p(42f, 30f).let { moveTo(it.x, it.y) }; p(68f, 26f).let { lineTo(it.x, it.y) }
            p(72.7f, 43f).let { lineTo(it.x, it.y) }; p(42f, 43f).let { lineTo(it.x, it.y) }; close()
        }, Color.White.copy(alpha = 0.45f))
        drawRoundRect(Color.White.copy(alpha = 0.7f), topLeft = p(36f, 33f), size = Size(l(32f), l(10f)), cornerRadius = CornerRadius(l(2f)))
        drawRoundRect(Color.White.copy(alpha = 0.35f), topLeft = p(29f, 40f), size = Size(l(54f), l(36f)), cornerRadius = CornerRadius(l(7f)))
        drawRoundRect(Color.White.copy(alpha = 0.8f), topLeft = p(29f, 40f), size = Size(l(54f), l(36f)), cornerRadius = CornerRadius(l(7f)),
            style = Stroke(l(1.5f)))
        drawRoundRect(Color.White, topLeft = p(59f, 51f), size = Size(l(22f), l(15f)), cornerRadius = CornerRadius(l(5f)))
        drawCircle(c[1], radius = l(3f), center = p(67f, 58.5f))
    }
}

/**
 * The glass edge of a field, as in the mock-up: a thin border in the theme's glass colours over a faint
 * tint of them (light pink on Red).
 */
@Composable
fun Modifier.glassEdge(shape: androidx.compose.ui.graphics.Shape, width: Dp = 1.5.dp): Modifier {
    val c = glassColors(LocalHeader.current)
    val fill = androidx.compose.ui.graphics.lerp(MaterialTheme.colorScheme.surface, c[1], 0.05f)
    return this.clip(shape).background(fill, shape).border(width, Brush.linearGradient(c), shape)
}

/**
 * One line of text (an amount) that shrinks until it fits its space, so lakhs and crores are never cut off; it is
 * drawn only once it fits, so it never flickers.
 */
@Composable
fun FitText(
    text: String, fontSize: androidx.compose.ui.unit.TextUnit, fontWeight: FontWeight, color: Color = Color.Unspecified,
    modifier: Modifier = Modifier, textAlign: androidx.compose.ui.text.style.TextAlign? = null
) {
    var size by androidx.compose.runtime.remember(text, fontSize) { androidx.compose.runtime.mutableStateOf(fontSize) }
    var ready by androidx.compose.runtime.remember(text, fontSize) { androidx.compose.runtime.mutableStateOf(false) }
    Text(
        text, modifier.drawWithContent { if (ready) drawContent() }, color = color, fontSize = size, fontWeight = fontWeight,
        maxLines = 1, softWrap = false, textAlign = textAlign,
        onTextLayout = { r -> if (r.didOverflowWidth && size.value > 7f) size *= 0.9f else ready = true }
    )
}

/**
 * The chat field and search box, as in the Claude app: white (in dark, a shade lighter than the page), a soft
 * see-through edge and a faint shadow.
 */
@Composable
fun Modifier.fieldEdge(shape: androidx.compose.ui.graphics.Shape): Modifier {
    val look = LocalLook.current
    val fill = look?.let { Color(it.field) } ?: Color.White
    val edge = if (look != null) Color(0x26DEDCD1) else Color(0x261F1E1D)
    return this.shadow(3.dp, shape, ambientColor = Color.Black.copy(alpha = 0.05f), spotColor = Color.Black.copy(alpha = 0.05f))
        .clip(shape).background(fill, shape).border(1.dp, edge, shape)
}
