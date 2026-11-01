package com.bolkhata.app

import androidx.compose.foundation.BorderStroke

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.Chair
import androidx.compose.material.icons.outlined.Handshake
import androidx.compose.material.icons.outlined.Home
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
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
}

private fun h(name: String, a: Long, b: Long, c: Long, light: Boolean) = HeaderTheme(name, Color(a), Color(b), Color(c), light)

/** All themes the person can pick in Settings. The first one is the default. */
val HEADER_THEMES = listOf(
    h("Clean", 0xFFF9F9F9, 0xFFF9F9F9, 0xFFF9F9F9, true),
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
val RedSpend = Color(0xFFE5484D)
val GreenIn = Color(0xFF1FA463)
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
    Cat.OTHER -> Color(0xFF94A3B8)
}

/** Inter, bundled in res/font (SIL Open Font License, see docs/inter-font-LICENSE.txt). Other scripts fall back to the system font. */
val Inter = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
    Font(R.font.inter_bold, FontWeight.Bold)
)

/** Every Material text style in Inter with tight letter spacing, for a cleaner, more modern look. */
val AppTypography: Typography = Typography().run {
    fun TextStyle.clean() = copy(fontFamily = Inter, letterSpacing = 0.sp)
    copy(
        displayLarge = displayLarge.clean(), displayMedium = displayMedium.clean(), displaySmall = displaySmall.clean(),
        headlineLarge = headlineLarge.clean(), headlineMedium = headlineMedium.clean(), headlineSmall = headlineSmall.clean(),
        titleLarge = titleLarge.clean(), titleMedium = titleMedium.clean(), titleSmall = titleSmall.clean(),
        bodyLarge = bodyLarge.clean(), bodyMedium = bodyMedium.clean(), bodySmall = bodySmall.clean(),
        labelLarge = labelLarge.clean(), labelMedium = labelMedium.clean(), labelSmall = labelSmall.clean()
    )
}

@Composable
fun BolTheme(dark: Boolean, content: @Composable () -> Unit) {
    val scheme = if (dark) darkColorScheme(
        primary = Color(0xFF34D399),
        onPrimary = Color(0xFF00210F),
        primaryContainer = Color(0xFF064E3B),
        onPrimaryContainer = Color(0xFFD1FAE5),
        secondaryContainer = Color(0xFF064E3B),
        onSecondaryContainer = Color(0xFFD1FAE5),
        background = Color(0xFF0F0F0F),
        surface = Color(0xFF181818),
        surfaceVariant = Color(0xFF262626),
        onSurfaceVariant = Color(0xFFA3A3A3),
        outline = Color(0xFF333333),
        outlineVariant = Color(0xFF2A2A2A)
    ) else lightColorScheme(
        primary = Color(0xFF059669),
        onPrimary = Color.White,
        primaryContainer = Color(0xFFD1FAE5),
        onPrimaryContainer = Color(0xFF065F46),
        secondaryContainer = Color(0xFFD1FAE5),
        onSecondaryContainer = Color(0xFF065F46),
        background = Color(0xFFF9F9F9),
        onBackground = Color(0xFF0D0D0D),
        surface = Color.White,
        onSurface = Color(0xFF0D0D0D),
        surfaceVariant = Color(0xFFF2F2F2),
        onSurfaceVariant = Color(0xFF6B6B6B),
        outline = Color(0xFFE5E5E5),
        outlineVariant = Color(0xFFECECEC)
    )
    MaterialTheme(colorScheme = scheme, typography = AppTypography, content = content)
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

fun sameDay(a: Long, b: Long): Boolean {
    val ca = cal(a)
    val cb = cal(b)
    return ca.get(Calendar.YEAR) == cb.get(Calendar.YEAR) && ca.get(Calendar.DAY_OF_YEAR) == cb.get(Calendar.DAY_OF_YEAR)
}

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
        if (!withPerson(e) || e.paid || isTransfer(e.type)) continue
        val p = e.person.trim()
        val v = map[p] ?: 0.0
        map[p] = when (e.type) {
            Type.DIYA, Type.WAPIS_DIYA, Type.SALE -> v + e.amount
            else -> v - e.amount
        }
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

/** Estimated business profit uses recorded item cost before each sale; other Cash In is kept separate. */
fun profitBreakdown(allEntries: List<Entry>, periodEntries: List<Entry>): ProfitBreakdown {
    val sales = periodEntries.filter { it.type == Type.SALE }
    val costBySaleId = linkedMapOf<String, Double>()
    // Purchases grouped by item once, so a year of sales doesn't scan every entry for each sale.
    val purchasesByItem = if (sales.isEmpty()) emptyMap() else allEntries
        .filter { it.type == Type.PURCHASE && it.qty > 0.0 && it.amount > 0.0 }
        .groupBy { it.item.trim().lowercase() }
    sales.forEach { sale ->
        if (sale.qty <= 0.0 || sale.item.isBlank() || sale.item.equals("Sale", ignoreCase = true)) return@forEach
        val purchases = purchasesByItem[sale.item.trim().lowercase()].orEmpty().filter { it.date <= sale.date }
        val purchasedQty = purchases.sumOf { it.qty }
        if (purchasedQty > 0.0) {
            costBySaleId[sale.id] = sale.qty * (purchases.sumOf { it.amount } / purchasedQty)
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
        Type.DIYA -> if (p.isEmpty()) "Lent (they owe me)" else "Gave to $p"
        Type.LIYA -> if (p.isEmpty()) "Borrowed (I owe)" else "Took from $p"
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
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
        }
        if (onMenu != null) {
            IconButton(onClick = onMenu) { Icon(Icons.Filled.Menu, contentDescription = "Menu") }
        }
        Text(title, fontSize = 24.sp, fontWeight = FontWeight.Bold)
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
