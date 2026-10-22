package com.bolkhata.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
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
val GreenNight = Color(0xFF0B0B0F)

/** Emerald gradient for the mic and other accent buttons. */
val AccentBrush = Brush.linearGradient(
    colors = listOf(Color(0xFF047857), Color(0xFF10B981), Color(0xFF34D399)),
    start = Offset(0f, 0f),
    end = Offset(160f, 160f)
)

/** Rich black gradient used by the header, drawer and profile banner. */
val HeroBrush = Brush.linearGradient(
    colors = listOf(Color(0xFF0B0B0F), Color(0xFF1F2937), Color(0xFF374151)),
    start = Offset(0f, 0f),
    end = Offset(1200f, 900f)
)
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
    Cat.OTHER -> Color(0xFF94A3B8)
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
        background = Color(0xFF0E1311),
        surface = Color(0xFF171E1B),
        surfaceVariant = Color(0xFF232C28),
        onSurfaceVariant = Color(0xFFA3B1A9),
        outline = Color(0xFF3A4540)
    ) else lightColorScheme(
        primary = Color(0xFF059669),
        onPrimary = Color.White,
        primaryContainer = Color(0xFFD1FAE5),
        onPrimaryContainer = Color(0xFF065F46),
        secondaryContainer = Color(0xFFD1FAE5),
        onSecondaryContainer = Color(0xFF065F46),
        background = Color(0xFFF2F6F3),
        surface = Color.White,
        surfaceVariant = Color(0xFFEDF2EF),
        onSurfaceVariant = Color(0xFF65736B),
        outline = Color(0xFFD5DED8)
    )
    MaterialTheme(colorScheme = scheme, content = content)
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

/** Time the entry was added, e.g. "7:51 AM". */
fun timeLabel(ms: Long): String = SimpleDateFormat("h:mm a", Locale.US).format(Date(ms))

fun shortDate(d: Long): String = SimpleDateFormat("d MMM", Locale.US).format(Date(d))
fun fullDate(d: Long): String = SimpleDateFormat("EEE, d MMM yyyy", Locale.US).format(Date(d))

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

/** Positive = they owe me, negative = I owe them. */
fun balances(entries: List<Entry>): Map<String, Double> {
    val map = linkedMapOf<String, Double>()
    for (e in entries) {
        if (e.person.isBlank() || !isLoan(e.type)) continue
        val p = e.person.trim()
        val v = map[p] ?: 0.0
        map[p] = when (e.type) {
            Type.DIYA, Type.WAPIS_DIYA -> v + e.amount
            else -> v - e.amount
        }
    }
    return map
}

fun signColor(t: Type) = when (t) {
    Type.KHARCHA, Type.WAPIS_DIYA -> RedSpend
    Type.AAMDANI, Type.WAPIS_MILA -> GreenIn
    Type.DIYA, Type.LIYA -> OrangeOwe
}

fun prefix(t: Type) = when (t) {
    Type.KHARCHA, Type.WAPIS_DIYA -> "−"
    Type.AAMDANI, Type.WAPIS_MILA -> "+"
    else -> ""
}

fun typeLabel(t: Type, person: String): String {
    val p = person.trim()
    return when (t) {
        Type.KHARCHA -> "Expense"
        Type.AAMDANI -> "Income"
        Type.DIYA -> if (p.isEmpty()) "Lent (they owe me)" else "Gave to $p"
        Type.LIYA -> if (p.isEmpty()) "Borrowed (I owe)" else "Took from $p"
        Type.WAPIS_MILA -> if (p.isEmpty()) "Got money back" else "$p returned"
        Type.WAPIS_DIYA -> if (p.isEmpty()) "Paid back" else "Returned to $p"
    }
}

fun entryTitle(e: Entry): String = when {
    isLoan(e.type) -> typeLabel(e.type, e.person)
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
        Surface(onClick = onClick, modifier = modifier, shape = shape, color = MaterialTheme.colorScheme.surface, shadowElevation = 2.dp) {
            Column(Modifier.padding(padding), content = content)
        }
    } else {
        Surface(modifier = modifier, shape = shape, color = MaterialTheme.colorScheme.surface, shadowElevation = 2.dp) {
            Column(Modifier.padding(padding), content = content)
        }
    }
}

@Composable
fun CatBubble(cat: Cat, size: Dp = 44.dp) {
    Box(
        Modifier
            .size(size)
            .clip(RoundedCornerShape(14.dp))
            .background(catColor(cat).copy(alpha = 0.16f)),
        contentAlignment = Alignment.Center
    ) { Text(cat.icon, fontSize = (size.value * 0.45f).sp) }
}

@Composable
fun SmallLabel(s: String) =
    Text(
        s.uppercase(), fontSize = 12.sp, letterSpacing = 1.sp, fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )

@Composable
fun SectionHeader(title: String, count: Int?, expanded: Boolean, total: String? = null, onToggle: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onToggle() }
            .padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, fontWeight = FontWeight.Bold, fontSize = 17.sp)
        if (count != null) {
            Spacer(Modifier.width(8.dp))
            Box(
                Modifier
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .padding(horizontal = 8.dp, vertical = 1.dp)
            ) { Text("$count", fontSize = 12.sp, color = MaterialTheme.colorScheme.onPrimaryContainer) }
        }
        Spacer(Modifier.weight(1f))
        if (total != null) {
            Text(total, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = RedSpend)
            Spacer(Modifier.width(6.dp))
        }
        Icon(
            if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
            contentDescription = if (expanded) "Close" else "Open",
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun TxRow(e: Entry, currency: String, onClick: () -> Unit) {
    Panel(Modifier.fillMaxWidth(), onClick = onClick, padding = 12.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CatBubble(e.category)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(entryTitle(e), fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    dayLabel(e.date) + " · " + e.note,
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                Text(
                    timeLabel(e.createdAt),
                    fontSize = 9.sp, lineHeight = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(
                moneyA(currency, e.amount, prefix(e.type)),
                fontWeight = FontWeight.Bold, color = signColor(e.type), fontSize = 15.sp
            )
        }
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

@Composable
fun ChartGlyph(color: Color, size: Dp = 24.dp) {
    Canvas(Modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val bw = w * 0.2f
        listOf(0.5f, 0.85f, 0.35f).forEachIndexed { i, f ->
            val bh = h * f * 0.8f
            drawRoundRect(color, topLeft = Offset(w * (0.12f + i * 0.28f), h * 0.9f - bh), size = Size(bw, bh), cornerRadius = CornerRadius(bw / 3))
        }
    }
}

/** Gradient header with soft light circles, for a premium feel. */
@Composable
fun HeroBackground(modifier: Modifier = Modifier) {
    Canvas(modifier.background(HeroBrush)) {
        val w = size.width
        val h = size.height
        drawCircle(Color.White.copy(alpha = 0.07f), radius = w * 0.42f, center = Offset(w * 0.95f, h * 0.05f))
        drawCircle(Color.White.copy(alpha = 0.05f), radius = w * 0.28f, center = Offset(w * 0.78f, h * 0.95f))
        drawCircle(Color.White.copy(alpha = 0.04f), radius = w * 0.18f, center = Offset(w * 0.05f, h * 0.15f))
    }
}
