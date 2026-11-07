@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package com.bolkhata.app

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Book
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.NoteAdd
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.coerceAtLeast
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

// Khata look: orange-red header, a white summary card over it, pill buttons above the chat bar, and four tabs
// (Parties, Cash book, Expense, Income) next to Home. Every number comes from the saved entries.

private val Ember = Color(0xFFE3342F)
/** Colour at the top of the orange header, also used for the status bar. */
internal val EmberTop = Color(0xFFF0572E)
/** The theme's glass look behind white text (headers, big buttons). */
@Composable
internal fun glassBand(): Modifier {
    val colors = glassColors(LocalHeader.current)
    return Modifier.drawBehind { drawGlass(colors, size.height) }
}

/** Money that left your hand. A sale or purchase on credit with a party moves no cash. */
fun cashOut(e: Entry) = when (e.type) {
    Type.KHARCHA, Type.DIYA, Type.WAPIS_DIYA, Type.TRANSFER_OUT -> true
    // Goods made here moved no cash: they are stock, not a payment.
    Type.PURCHASE -> (e.paid || e.person.isBlank()) && !isMade(e)
    else -> false
}

/** Money that came into your hand. */
fun cashIn(e: Entry) = when (e.type) {
    Type.AAMDANI, Type.LIYA, Type.WAPIS_MILA, Type.TRANSFER_IN -> true
    Type.SALE -> e.paid || e.person.isBlank()
    else -> false
}

/** The tabs shown in the bottom bar, in order. */
val KHATA_TABS = listOf(Tab.CHAT, Tab.CASHBOOK, Tab.PARTIES, Tab.EXPENSE, Tab.INCOME)

@Composable
fun KhataNav(tab: Tab, onTab: (Tab) -> Unit) {
    // No shadow: it joins the white chat strip above into one bottom panel.
    Surface(color = MaterialTheme.colorScheme.surface) {
        Row(Modifier.fillMaxWidth().navigationBarsPadding().height(58.dp)) {
            KHATA_TABS.forEach { t ->
                val (label, icon) = navItem(t)
                val on = t == tab
                val tint = if (on) Ember else MaterialTheme.colorScheme.onSurfaceVariant
                Column(
                    Modifier.weight(1f).fillMaxHeight().clickable { onTab(t) },
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center
                ) {
                    Text(icon, fontSize = 19.sp)
                    Text(label, fontSize = 10.sp, color = tint, fontWeight = if (on) FontWeight.Bold else FontWeight.Medium, modifier = Modifier.padding(top = 3.dp))
                }
            }
        }
    }
}

/** Each tab's name and its picture (as in the mock-up). */
private fun navItem(t: Tab): Pair<String, String> = when (t) {
    Tab.CASHBOOK -> "Cashbook" to "📒"
    Tab.PARTIES -> "Party" to "👥"
    Tab.EXPENSE -> "Expense" to "🧾"
    Tab.INCOME -> "Income" to "💵"
    else -> "Chat" to "💬"
}

/** A glass header behind white text (Reports, To give / To get): the icon's look in the theme's colours. */
@Composable
internal fun GlassHeader(modifier: Modifier) {
    val colors = glassColors(LocalHeader.current)
    Canvas(modifier) { drawGlass(colors, size.height) }
}

/**
 * The header every khata screen shares: the theme's band (none for Clean) from the status bar down to the middle of
 * the search box below, the business pill (or a back arrow and title on inner pages), and the summary card on it.
 */
@Composable
private fun KhataTop(
    title: String, subtitle: String, onMenu: () -> Unit, back: Boolean = false,
    business: String? = null, card: @Composable () -> Unit
) {
    val theme = LocalHeader.current
    val banded = theme.name != "Clean"
    val ink = if (banded) theme.on else MaterialTheme.colorScheme.onSurface
    // The theme band runs from the top of the screen to the middle of the summary card.
    var cardH by remember { mutableIntStateOf(0) }
    Box(Modifier.fillMaxWidth()
        .then(if (banded) Modifier.drawBehind { drawBand(theme, size.height - cardH / 2f) } else Modifier)) {
        Column(Modifier.statusBarsPadding().padding(horizontal = 12.dp)) {
            if (business != null) BusinessPill(business, onMenu) else
            Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onMenu) {
                    Icon(if (back) Icons.AutoMirrored.Filled.ArrowBack else Icons.Filled.Menu, contentDescription = if (back) "Back" else "Menu", tint = ink)
                }
                if (title.isNotEmpty()) Column {
                    Text(title, color = ink, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (subtitle.isNotEmpty()) Text(subtitle, color = if (banded) ink.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                }
            }
            Spacer(Modifier.height(10.dp))
            Box(Modifier.onSizeChanged { cardH = it.height }) { card() }
        }
    }
}

/**
 * The fixed top of a khata tab: the head with its card, then search and chips. It stays put; only the list under it
 * scrolls, and the page colour behind it keeps rows from showing through.
 */
@Composable
private fun TabHead(content: @Composable ColumnScope.() -> Unit) =
    Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background).padding(bottom = 4.dp), content = content)

/** The theme band in the app icon's glass look, with a thin light bottom edge. */
private fun DrawScope.drawBand(theme: HeaderTheme, h: Float) {
    drawGlass(bandColors(theme), h)
    drawRect(Color.White.copy(alpha = 0.35f), topLeft = Offset(0f, h - 1.dp.toPx()), size = Size(size.width, 1.dp.toPx()))
}

/** White pill at the top: the glossy app logo and the open business; a tap opens the side menu (businesses and settings). */
@Composable
internal fun BusinessPill(name: String, onMenu: () -> Unit) {
    Row(
        Modifier.padding(top = 12.dp).shadow(1.dp, CircleShape).clip(CircleShape).background(Color.White)
            .clickable(onClickLabel = "Menu") { onMenu() }.padding(start = 6.dp, end = 14.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        GlassLogo(24.dp)
        Spacer(Modifier.width(6.dp))
        Text(name, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1C1917), maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 170.dp))
    }
}

/** White card with two figures side by side and a small link at the end. */
@Composable
private fun SumCard(
    a: Triple<String, String, Color>, b: Triple<String, String, Color>?, link: String, onLink: () -> Unit,
    /** A third figure (Expense: To pay), shown after the second. */
    c: Triple<String, String, Color>? = null
) {
    Surface(
        shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surface, shadowElevation = 1.dp
    ) {
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).padding(vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
            listOfNotNull(a, b, c).forEachIndexed { i, (value, label, color) ->
                if (i > 0) VerticalDivider(Modifier.fillMaxHeight(), color = MaterialTheme.colorScheme.outlineVariant)
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(value, color = color, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
                    Text(label, fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (link.isNotEmpty()) VerticalDivider(Modifier.fillMaxHeight(), color = MaterialTheme.colorScheme.outlineVariant)
            if (link.isNotEmpty()) Text(
                link, color = Ember, fontSize = 10.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
                modifier = Modifier.width(64.dp).clickable { onLink() }.padding(vertical = 8.dp)
            )
        }
    }
}

/** A search box with an optional count shown as its hint. */
@Composable
private fun SearchBox(q: String, hint: String, onChange: (String) -> Unit) {
    val sub = MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        Modifier.padding(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 4.dp).fillMaxWidth()
            .fieldEdge(RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Filled.Search, contentDescription = null, tint = sub, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Box(Modifier.weight(1f)) {
            if (q.isEmpty()) Text(hint, color = sub, fontSize = 12.sp)
            BasicTextField(
                q, onChange, singleLine = true, modifier = Modifier.fillMaxWidth(),
                textStyle = TextStyle(fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface),
                cursorBrush = SolidColor(Ember)
            )
        }
    }
}

/** Round letter badge for a party or an income source. */
@Composable
internal fun Initial(name: String) {
    Box(Modifier.size(30.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
        Text(name.trim().take(1).uppercase(), color = Ember, fontWeight = FontWeight.ExtraBold, fontSize = 12.sp)
    }
}

/** Small filter chip (All / Receivable / Payable). */
@Composable
internal fun SmallChip(label: String, on: Boolean, onClick: () -> Unit) {
    Text(
        label, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = if (on) Color.White else MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.clip(CircleShape).background(if (on) Ember else MaterialTheme.colorScheme.surface)
            .border(1.dp, if (on) Ember else MaterialTheme.colorScheme.outlineVariant, CircleShape)
            .clickable { onClick() }.padding(horizontal = 11.dp, vertical = 6.dp)
    )
}


private fun lastSeen(ms: Long) = if (sameDay(ms, System.currentTimeMillis())) "Today" else shortDay.format(Date(ms))

internal fun plusMinus(v: Double) = (if (v > 0.005) "+" else if (v < -0.005) "−" else "") + fmt(kotlin.math.abs(v))

/** Shown when a list has nothing to show. */
private fun LazyListScope.emptyNote(text: String) = item {
    Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp))
}

/** Shape of a row inside a white sheet (each row casts a soft shadow so the sheet looks raised): the last row rounds the bottom. */
private fun sheetShape(last: Boolean) = if (last) RoundedCornerShape(bottomStart = 14.dp, bottomEnd = 14.dp) else RoundedCornerShape(0.dp)

// ---------------------------------------------------------------- Cash book and party khata

private val shortDay = SimpleDateFormat("d MMM", Locale.US)
private val monthFmt = SimpleDateFormat("MMM yyyy", Locale.US)

private fun title(e: Entry) = e.person.ifBlank { e.item.ifBlank { e.category.label } }

/** The name khata an expense sits in under its category (Muhammad Bin Saeed, Bank fee…), as a lowercase key. */
internal fun nameKey(e: Entry) = title(e).trim().lowercase()
private fun monthKey(ms: Long): Int = cal(ms).let { it.get(Calendar.YEAR) * 12 + it.get(Calendar.MONTH) }

/** Months that have entries, newest first, as (key, a date in that month). [newest] must be sorted newest first. */
private fun monthsOf(newest: List<Entry>) = newest.distinctBy { monthKey(it.date) }.map { monthKey(it.date) to it.date }

/** The "All" month chip: every month listed together. */
private const val ALL_MONTHS = -1
private fun inMonth(ms: Long, month: Int) = month == ALL_MONTHS || monthKey(ms) == month
private fun monthLabel(months: List<Pair<Int, Long>>, month: Int) =
    if (month == ALL_MONTHS) "All" else months.firstOrNull { it.first == month }?.let { monthFmt.format(Date(it.second)) }

/** Month chips; only one month is listed at a time so the page stays short. */
@Composable
private fun MonthChips(months: List<Pair<Int, Long>>, current: Int, onPick: (Int) -> Unit) {
    LazyRow(
        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        item(key = ALL_MONTHS) { SmallChip("All", current == ALL_MONTHS) { onPick(ALL_MONTHS) } }
        items(months, key = { it.first }) { (k, ms) -> SmallChip(monthFmt.format(Date(ms)), k == current) { onPick(k) } }
    }
}

private fun yearOf(ms: Long) = cal(ms).get(Calendar.YEAR)
private fun monthName(key: Int): String = monthFmt.format(Calendar.getInstance().apply { clear(); set(key / 12, key % 12, 1) }.time)

/** What the chips pick: everything, one year, one month (its [monthKey]) or the days [from]..[to]. Kept as text in saved state. */
internal data class Period(val year: Int = 0, val month: Int = ALL_MONTHS, val from: Long = 0L, val to: Long = 0L) {
    fun has(ms: Long): Boolean = when {
        from > 0 -> ms >= from && ms < to + 86_400_000L
        month != ALL_MONTHS -> monthKey(ms) == month
        year > 0 -> yearOf(ms) == year
        else -> true
    }

    /** "Nov 2026", "2026", "1 Oct – 15 Nov" or "All time". */
    fun label(): String = when {
        from > 0 -> shortDay.format(Date(from)) + " – " + shortDay.format(Date(to))
        month != ALL_MONTHS -> monthName(month)
        year > 0 -> year.toString()
        else -> "All time"
    }

    fun encode() = "$year|$month|$from|$to"

    companion object {
        fun decode(s: String): Period? = s.split('|').takeIf { it.size == 4 }?.let { Period(it[0].toInt(), it[1].toInt(), it[2].toLong(), it[3].toLong()) }
        /** The newest month with entries, or All when there are none. [newest] is sorted newest first. */
        fun newestMonth(newest: List<Entry>) = Period(month = newest.firstOrNull()?.let { monthKey(it.date) } ?: ALL_MONTHS)
    }
}

/**
 * The period chips: "All ▾" (or the year or the dates picked) opens a list of All, every year that has entries and
 * Dates…; after it come that year's months. [newest] is sorted newest first.
 */
@Composable
internal fun PeriodChips(newest: List<Entry>, period: Period, onPick: (Period) -> Unit) {
    var open by remember { mutableStateOf(false) }
    var dates by remember { mutableStateOf<Pair<Long, Long>?>(null) }
    val years = remember(newest) { newest.map { yearOf(it.date) }.distinct() }
    val year = period.year.takeIf { it > 0 } ?: years.firstOrNull() ?: yearOf(System.currentTimeMillis())
    val months = remember(newest, year) { monthsOf(newest.filter { yearOf(it.date) == year }) }
    val first = (if (period.from > 0) period.label() else if (period.year > 0) period.year.toString() else "All") + " ▾"
    LazyRow(
        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        item(key = "first") {
            Box {
                SmallChip(first, period.month == ALL_MONTHS) { open = true }
                DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                    (listOf("All") + years.map { it.toString() } + "Dates…").forEach { v ->
                        val on = when (v) { "All" -> period.year == 0 && period.from == 0L; "Dates…" -> period.from > 0; else -> period.year.toString() == v && period.from == 0L }
                        DropdownMenuItem(
                            text = { Text(v, fontSize = 13.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.SemiBold, color = if (on) Ember else MaterialTheme.colorScheme.onSurface) },
                            onClick = {
                                open = false
                                when (v) {
                                    "All" -> onPick(Period())
                                    "Dates…" -> {
                                        val today = cal(System.currentTimeMillis()).apply {
                                            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
                                        }.timeInMillis
                                        dates = (if (period.from > 0) period.from else today - 30 * 86_400_000L) to (if (period.from > 0) period.to else today)
                                    }
                                    else -> onPick(Period(year = v.toInt()))
                                }
                            }
                        )
                    }
                }
            }
        }
        items(months, key = { it.first }) { (k, ms) -> SmallChip(monthFmt.format(Date(ms)), k == period.month) { onPick(Period(year = period.year, month = k)) } }
    }
    dates?.let { (f0, t0) ->
        val context = androidx.compose.ui.platform.LocalContext.current
        var from by remember { mutableLongStateOf(f0) }
        var to by remember { mutableLongStateOf(t0) }
        fun pickDay(ms: Long, set: (Long) -> Unit) {
            val c = cal(ms)
            android.app.DatePickerDialog(context, { _, y, m, d -> set(cal(ms).apply { set(y, m, d) }.timeInMillis) },
                c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show()
        }
        AppPopup("Show dates", "Show", onDismiss = { dates = null }, onAction = { dates = null; onPick(Period(from = minOf(from, to), to = maxOf(from, to))) }) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PickBox(sheetDay.format(Date(from)), Modifier.weight(1f)) { pickDay(from) { from = it } }
                PickBox(sheetDay.format(Date(to)), Modifier.weight(1f)) { pickDay(to) { to = it } }
            }
        }
    }
}

// ---------------------------------------------------------------- the ledger page (Cash book, party khata, Expense / Income)

/** Money out, money in, or a plain figure (quantity, profit) with no colour of its own. */
internal enum class LKind { OUT, IN, PLAIN }

/** One amount column: its title, the month's total shown under it, and whether it is money out or in. */
internal class LCol(val label: String, val total: String, val kind: LKind)

/** One line: title, time and note, and one amount per column (0 = empty cell). */
internal class LRow(
    val key: String, val date: Long, val title: String, val note: String, val amounts: List<Double>, val entry: Entry? = null,
    /** The khata the entry sits in (party or category), shown small after the time; blank to leave it out. */
    val khata: String = "", val onClick: () -> Unit
)

private val clockFmt = SimpleDateFormat("h:mma", Locale.US)
private val dayLineFmt = SimpleDateFormat("EEE, d MMM yyyy", Locale.US)
/** Blue of the small khata name in ledger rows: red and green stay for money. */
private val KhataBlue = Color(0xFF0369A1)
private val Faint = Color(0x14786C6C)
/** The day strip in ledgers: the light grey-blue of a phone keyboard (dark: a deep slate). */
private val DayStrip: Color
    @Composable @ReadOnlyComposable get() = if (MaterialTheme.colorScheme.surface.luminance() < 0.5f) Color(0xFF2B2F3A) else Color(0xFFEEF1F8)
/** Colour of an entry's own amount: plain text ink (dark grey, light in dark mode), so red / green stay for the totals. */
private val AmountInk: Color
    @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)

private fun colW(cols: List<LCol>) = 68.dp
/** Wider columns for Parties and Stock: "Receivable", quantities with units and big totals must fit. */
private val WideColW = 74.dp
private fun entryCount(n: Int) = if (n == 0) "No entries" else if (n == 1) "1 entry" else "$n entries"

/** Ledger text uses the font's own line height, not the theme's tall one, so lines stay compact. */
@Composable
private fun Tight(content: @Composable () -> Unit) =
    ProvideTextStyle(LocalTextStyle.current.copy(lineHeight = TextUnit.Unspecified), content)
private fun kindColor(k: LKind) = when (k) { LKind.OUT -> RedSpend; LKind.IN -> GreenIn; LKind.PLAIN -> Color(0xFF78716C) }
/** − for money out, + for money in; empty when there is nothing. */
private fun signed(k: LKind, v: Double) = if (v == 0.0) "" else (when (k) { LKind.OUT -> "−"; LKind.IN -> "+"; LKind.PLAIN -> "" }) + fmt(v)

/**
 * A white ledger sheet: the head line (month and entry count, then each column's name with its total), and for every
 * day a slim line (soft red fading to white) with that day's totals, then one small
 * line per entry. Lines are faint grey; amounts sit in the middle of their column.
 */
internal fun LazyListScope.ledger(
    title: String, cols: List<LCol>, rows: List<LRow>, keyPrefix: String = "", cards: Boolean = true, onHold: (() -> Unit)? = null
) {
    item(key = keyPrefix + "lh") { LedgerHead(title, entryCount(rows.size), cols, rows.isEmpty(), ids = rows.mapNotNull { it.entry?.id }, onHold = onHold) }
    if (rows.isEmpty()) item(key = keyPrefix + "empty") { EmptyHint() }
    // Each day's rows, found in one pass (filtering the whole list for every day made long lists slow).
    val byDay = rows.groupBy { dayNumber(it.date) }
    rows.forEachIndexed { i, r ->
        if (i == 0 || !sameDay(rows[i - 1].date, r.date)) {
            val day = byDay[dayNumber(r.date)].orEmpty()
            item(key = keyPrefix + "d" + r.key) { DayLine(r.date, cols, day, cards) }
        }
        // As cards, each day is one card: its day line and entries, the last entry closing it.
        val last = i == rows.lastIndex || (cards && !sameDay(rows[i + 1].date, r.date))
        item(key = keyPrefix + r.key) { LedgerRow(r, cols, last, cards) }
    }
}

/** What an empty list shows instead of a blank page: the app logo and how to add the first entry. */
@Composable
private fun EmptyHint() {
    Column(Modifier.fillMaxWidth().padding(vertical = 28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        GlassLogo(44.dp)
        Spacer(Modifier.height(10.dp))
        Text("No entries here yet", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        Text("Say \"tea 5\" in the chat, or tap Entry.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
/** [onHold]: a long press on the head opens the khata menu (move all its entries). */
private fun LedgerHead(
    title: String, count: String, cols: List<LCol>, empty: Boolean, w: Dp = colW(cols), ids: List<String> = emptyList(), onHold: (() -> Unit)? = null
) = Tight {
    val sub = MaterialTheme.colorScheme.onSurfaceVariant
    val sel = LocalSelection.current
    Row(
        Modifier.padding(start = 10.dp, end = 10.dp, top = 8.dp).fillMaxWidth().height(IntrinsicSize.Min)
            .then(RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp, bottomStart = if (empty) 14.dp else 0.dp, bottomEnd = if (empty) 14.dp else 0.dp).let { Modifier.shadow(1.dp, it).clip(it) })
            .background(MaterialTheme.colorScheme.surface)
            .then(if (onHold != null) Modifier.combinedClickable(onLongClick = onHold) {} else Modifier)
    ) {
        Column(Modifier.weight(1f).padding(start = 12.dp, top = 7.dp, bottom = 7.dp), verticalArrangement = Arrangement.Center) {
            Text(title, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
            Text(count, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = sub)
        }
        // While picking entries: "All" picks (or drops) every entry of this ledger.
        if (sel?.active == true && ids.isNotEmpty()) {
            val all = ids.all { it in sel.ids }
            Text(
                if (all) "None" else "All", color = Ember, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.CenterVertically).clip(CircleShape)
                    .clickable { if (all) sel.ids.removeAll(ids) else ids.filterNot { it in sel.ids }.forEach { sel.ids.add(it) } }
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            )
        }
        cols.forEach { c ->
            VerticalDivider(Modifier.fillMaxHeight(), color = Faint)
            // Column name, with the month's total right under it.
            Column(
                Modifier.width(w).fillMaxHeight().padding(horizontal = 3.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center
            ) {
                Text(c.label.uppercase(), fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp, maxLines = 1, color = sub)
                Text(c.total, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, softWrap = false, color = kindColor(c.kind))
            }
        }
    }
    if (!empty) HorizontalDivider(Modifier.padding(horizontal = 10.dp), color = MaterialTheme.colorScheme.outlineVariant)
}

/** The slim line above each day: the day's name and its totals in their columns. */
@Composable
private fun DayLine(date: Long, cols: List<LCol>, day: List<LRow>, card: Boolean = false) = Tight {
    val w = colW(cols)
    val totals = cols.indices.map { k -> day.sumOf { it.amounts.getOrElse(k) { 0.0 } } }
    // One light keyboard-grey strip for the whole day: date, then the day's totals in their colours (no + / −).
    Row(
        Modifier.padding(start = 10.dp, end = 10.dp, top = if (card) 6.dp else 0.dp).fillMaxWidth().height(IntrinsicSize.Min).background(DayStrip)
    ) {
        Text(dayLineFmt.format(Date(date)), fontSize = 11.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f).padding(start = 12.dp, top = 4.dp, bottom = 4.dp))
        cols.forEachIndexed { k, c ->
            Box(Modifier.width(w).fillMaxHeight(), contentAlignment = Alignment.Center) {
                Text(if (totals[k] == 0.0) "" else fmt(totals[k]), fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = kindColor(c.kind), maxLines = 1, softWrap = false)
            }
        }
    }
}

/** One entry line; as a [card] it is part of its day's square raised card, [last] closing that card. */
@Composable
private fun LedgerRow(r: LRow, cols: List<LCol>, last: Boolean, card: Boolean = false) = Tight {
    val sub = MaterialTheme.colorScheme.onSurfaceVariant
    val w = colW(cols)
    val shape = if (card) RectangleShape else sheetShape(last)
    val menu = LocalEntryMenu.current
    val sel = LocalSelection.current
    val picking = sel?.active == true && r.entry != null
    val picked = picking && r.entry!!.id in sel!!.ids
    Row(
        Modifier.padding(horizontal = 10.dp).fillMaxWidth().height(IntrinsicSize.Min).shadow(1.dp, shape).clip(shape)
            .background(MaterialTheme.colorScheme.surface).background(if (picked) Ember.copy(alpha = 0.12f) else Color.Transparent)
            .combinedClickable(onLongClick = r.entry?.let { e -> if (picking) ({ sel!!.toggle(e.id) }) else menu?.let { { it(e) } } }) {
                if (picking) sel!!.toggle(r.entry!!.id) else r.onClick()
            }
    ) {
        Column(Modifier.weight(1f).padding(start = 12.dp, end = 4.dp, top = 7.dp, bottom = 7.dp), verticalArrangement = Arrangement.Center) {
            Text(r.title, fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                buildAnnotatedString {
                    append(clockFmt.format(Date(r.date)).lowercase())
                    if (r.khata.isNotBlank() && !r.khata.equals(r.title, true)) {
                        append(" · ")
                        withStyle(SpanStyle(color = KhataBlue)) { append(r.khata) }
                    }
                    if (r.note.isNotBlank() && !r.note.equals(r.title, true)) append(" · " + r.note)
                },
                fontSize = 10.sp, color = sub, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp)
            )
        }
        cols.forEachIndexed { i, _ ->
            VerticalDivider(Modifier.fillMaxHeight(), color = Faint)
            Box(Modifier.width(w).fillMaxHeight(), contentAlignment = Alignment.Center) {
                val v = r.amounts.getOrElse(i) { 0.0 }
                Text(if (v == 0.0) "" else fmt(v), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = AmountInk, maxLines = 1, softWrap = false)
            }
        }
    }
    if (!last) HorizontalDivider(Modifier.padding(horizontal = 10.dp), color = Faint)
}

/** The add button just above the bottom panel: a white card with a khata icon and "Entry". The form has Cash out / in. */
@Composable
private fun BoxScope.PlusButton(onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        Modifier.align(Alignment.BottomEnd).padding(end = 14.dp, bottom = LocalBarSpace.current + 6.dp)
            .height(46.dp).shadow(2.dp, shape).clip(shape).background(MaterialTheme.colorScheme.surface)
            .clickable { onClick() }.padding(start = 10.dp, end = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Outlined.NoteAdd, contentDescription = null, tint = Ember, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(8.dp))
        Text("Entry", color = Ember, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}

/** Round ↑ button that appears once the list is scrolled down. */
@Composable
private fun BoxScope.UpButton(state: LazyListState) {
    val scope = rememberCoroutineScope()
    val show by remember { derivedStateOf { state.firstVisibleItemIndex > 4 } }
    if (show) Box(
        Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = LocalBarSpace.current + 60.dp)
            .size(40.dp).shadow(2.dp, CircleShape).clip(CircleShape).background(MaterialTheme.colorScheme.onSurface)
            .clickable { scope.launch { state.animateScrollToItem(0) } },
        contentAlignment = Alignment.Center
    ) { Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "Back to top", tint = MaterialTheme.colorScheme.surface) }
}

private fun matches(e: Entry, q: String) =
    listOf(e.person, e.item, e.note, e.category.label, fmt(e.amount)).any { it.contains(q.trim(), true) }

@Composable
fun CashBookScreen(
    entries: List<Entry>, currency: String, onMenu: () -> Unit, business: String,
    onEdit: (Entry) -> Unit, onReport: () -> Unit
) {
    val newest = remember(entries) {
        entries.filter { cashIn(it) || cashOut(it) }.sortedWith(compareByDescending<Entry> { it.date }.thenByDescending { it.createdAt })
    }
    val hand = remember(newest) { newest.sumOf { if (cashIn(it)) it.amount else -it.amount } }
    // This month's net cash: in minus out.
    val nowKey = monthKey(System.currentTimeMillis())
    // The head shows what was spent this month (Expense entries), like the Expense tab.
    val spentMonth = remember(newest, nowKey) { newest.filter { it.type == Type.KHARCHA && monthKey(it.date) == nowKey }.sumOf { it.amount } }
    val months = remember(newest) { monthsOf(newest) }
    var month by rememberSaveable(months.firstOrNull()?.first) { mutableIntStateOf(months.firstOrNull()?.first ?: 0) }
    // A remembered month that no longer has entries (the khata changed since) falls back to the newest month.
    if (month != ALL_MONTHS && months.none { it.first == month }) month = months.firstOrNull()?.first ?: ALL_MONTHS
    var q by rememberSaveable { mutableStateOf("") }
    // A search looks through every month; otherwise only the picked month is listed.
    val list = remember(newest, month, q) { if (q.isBlank()) newest.filter { inMonth(it.date, month) } else newest.filter { matches(it, q) } }
    val state = rememberLazyListState()
    Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), state = state, contentPadding = PaddingValues(bottom = barSpace(56.dp))) {
            stickyHeader { TabHead {
                KhataTop("", "", onMenu, business = business) {
                    SumCard(
                        Triple(money(currency, hand), "Cash in hand", if (hand < 0) RedSpend else GreenIn),
                        Triple(money(currency, spentMonth), "Spent this month", RedSpend),
                        "Report ›", onReport
                    )
                }
                SearchBox(q, "Search") { q = it }
                if (q.isBlank() && months.isNotEmpty()) MonthChips(months, month) { month = it }
            } }
            val label = if (q.isNotBlank()) "Found" else monthLabel(months, month) ?: ""
            ledger(
                label,
                listOf(
                    LCol("Out", fmt(list.filter(::cashOut).sumOf { it.amount }), LKind.OUT),
                    LCol("In", fmt(list.filter(::cashIn).sumOf { it.amount }), LKind.IN)
                ),
                list.map { e ->
                    LRow(e.id, e.date, title(e), e.note, listOf(if (cashOut(e)) e.amount else 0.0, if (cashIn(e)) e.amount else 0.0), e,
                        khata = e.person.trim().ifBlank { e.category.label }) { onEdit(e) }
                },
                cards = true
            )
        }
        UpButton(state)
    }
}

// ---------------------------------------------------------------- All entries

/** Every entry on the ledger page, a month at a time, with chips for expense, income, loans and trade. */
@Composable
fun TransactionsScreen(entries: List<Entry>, currency: String, onEdit: (Entry) -> Unit, onAdd: () -> Unit, onMenu: () -> Unit) {
    val kinds = listOf("All", "Expense", "Income", "Loans", "Trade")
    var filter by rememberSaveable { mutableIntStateOf(0) }
    var q by rememberSaveable { mutableStateOf("") }
    val newest = remember(entries, filter) {
        entries.filter {
            when (filter) { 1 -> it.type == Type.KHARCHA; 2 -> it.type == Type.AAMDANI; 3 -> isLoan(it.type); 4 -> isTrade(it.type); else -> true }
        }.sortedWith(compareByDescending<Entry> { it.date }.thenByDescending { it.createdAt })
    }
    val months = remember(newest) { monthsOf(newest) }
    var month by rememberSaveable(months.firstOrNull()?.first) { mutableIntStateOf(months.firstOrNull()?.first ?: 0) }
    // A remembered month that no longer has entries (the khata changed since) falls back to the newest month.
    if (month != ALL_MONTHS && months.none { it.first == month }) month = months.firstOrNull()?.first ?: ALL_MONTHS
    val list = remember(newest, month, q) { if (q.isBlank()) newest.filter { inMonth(it.date, month) } else newest.filter { matches(it, q) } }
    // Money that left you (or a purchase) goes in Out; everything else in In.
    fun out(e: Entry) = cashOut(e) || e.type == Type.PURCHASE
    val outT = list.filter(::out).sumOf { it.amount }
    val inT = list.filterNot(::out).sumOf { it.amount }
    val state = rememberLazyListState()
    Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), state = state, contentPadding = PaddingValues(bottom = barSpace(56.dp))) {
            stickyHeader { TabHead {
                KhataTop("All entries", if (entries.size == 1) "1 entry" else "${entries.size} entries", onMenu) {
                    SumCard(Triple(money(currency, outT), "Out", RedSpend), Triple(money(currency, inT), "In", GreenIn), "Add", onAdd)
                }
                Row(Modifier.horizontalScroll(rememberScrollState()).padding(start = 14.dp, end = 14.dp, top = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    kinds.forEachIndexed { i, t -> SmallChip(t, filter == i) { filter = i } }
                }
                SearchBox(q, "Search") { q = it }
                if (q.isBlank() && months.isNotEmpty()) MonthChips(months, month) { month = it }
            } }
            val label = if (q.isNotBlank()) "Found" else monthLabel(months, month) ?: ""
            ledger(
                label, listOf(LCol("Out", fmt(outT), LKind.OUT), LCol("In", fmt(inT), LKind.IN)),
                list.map { e -> LRow(e.id, e.date, title(e), e.note, listOf(if (out(e)) e.amount else 0.0, if (!out(e)) e.amount else 0.0), e) { onEdit(e) } }
            )
        }
        UpButton(state)
        PlusButton(onAdd)
    }
}

// ---------------------------------------------------------------- Trash

/** Deleted entries (kept 30 days) on the ledger page; tap one to restore it. */
@Composable
fun TrashScreen(
    trash: List<Entry>, currency: String, onMenu: () -> Unit,
    onRestore: (Entry) -> Unit, onRestoreAll: () -> Unit, onEmpty: () -> Unit
) {
    var askEmpty by remember { mutableStateOf(false) }
    val list = remember(trash) { trash.sortedWith(compareByDescending<Entry> { it.date }.thenByDescending { it.createdAt }) }
    fun out(e: Entry) = cashOut(e) || e.type == Type.PURCHASE
    val listState = rememberLazyListState()
    LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = PaddingValues(bottom = barSpace(24.dp))) {
        stickyHeader { TabHead {
            KhataTop("Trash", "Deleted entries stay here for 30 days", onMenu) {
                SumCard(
                    Triple("${trash.size}", if (trash.size == 1) "Entry" else "Entries", MaterialTheme.colorScheme.onSurface),
                    Triple(money(currency, trash.sumOf { it.amount }), "Total", RedSpend),
                    "Restore all", onRestoreAll
                )
            }
        } }
        if (trash.isNotEmpty()) item {
            TextButton(onClick = { askEmpty = true }, modifier = Modifier.padding(start = 6.dp, top = 6.dp)) { Text("Empty trash", color = RedSpend) }
        }
        ledger(
            "Tap an entry to restore it",
            listOf(LCol("Out", fmt(list.filter(::out).sumOf { it.amount }), LKind.OUT), LCol("In", fmt(list.filterNot(::out).sumOf { it.amount }), LKind.IN)),
            list.map { e -> LRow(e.id, e.date, title(e), e.note, listOf(if (out(e)) e.amount else 0.0, if (!out(e)) e.amount else 0.0)) { onRestore(e) } }
        )
    }
    if (askEmpty) {
        AlertDialog(
            onDismissRequest = { askEmpty = false },
            title = { Text("Empty trash?") },
            text = { Text("${trash.size} entries will be deleted forever. This cannot be undone.") },
            confirmButton = {
                Button(onClick = { askEmpty = false; onEmpty() }, colors = ButtonDefaults.buttonColors(containerColor = RedSpend)) { Text("Delete forever") }
            },
            dismissButton = { TextButton(onClick = { askEmpty = false }) { Text("Cancel") } }
        )
    }
}

// ---------------------------------------------------------------- Expense / Income

/** Who an income came from: the payer's name, or "From X" read from a bank alert's note. */
internal fun source(e: Entry): String {
    val generic = e.item.isBlank() || e.item.equals("Transfer", true) || e.item.equals("Income", true)
    if (!generic) return e.item.trim()
    Regex("""(?i)\bfrom\s+([^.,\d]+)""").find(e.note)?.let { return it.groupValues[1].trim() }
    return e.person.ifBlank { e.item.ifBlank { "Income" } }
}

/** A blank Cash in / Cash out entry for the form; the category is guessed from what is typed. */
internal fun cashTemplate(income: Boolean): Entry =
    template(if (income) Type.AAMDANI else Type.KHARCHA, "").copy(category = if (income) Cat.TANKHWAH else Cat.OTHER, item = "")

// ---------------------------------------------------------------- businesses

/** This month's cash out and cash in of one book, for the book list. */
internal fun monthCash(list: List<Entry>, now: Long = System.currentTimeMillis()): Pair<Double, Double> {
    val m = monthKey(now)
    val month = list.filter { monthKey(it.date) == m }
    return month.filter(::cashOut).sumOf { it.amount } to month.filter(::cashIn).sumOf { it.amount }
}

/**
 * Full-screen list of businesses: all books' cash this month on top, then each book with its own out / in,
 * a tick on the open one, ⋮ to rename or delete, and Add New Business.
 */
@Composable
fun BusinessPicker(
    list: List<Triple<String, String, Int>>, current: String, onBack: () -> Unit, onPick: (String) -> Unit,
    onAdd: (String) -> Unit, onRename: (String, String) -> Unit, onDelete: (String) -> Unit,
    /** Book id to (cash out, cash in) this month. */
    totals: Map<String, Pair<Double, Double>> = emptyMap(), currency: String = ""
) {
    var naming by remember { mutableStateOf<Pair<String?, String>?>(null) }   // (id or null for new, text)
    var deleting by remember { mutableStateOf<Pair<String, String>?>(null) }
    androidx.compose.ui.window.Dialog(onDismissRequest = onBack, properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column {
                Row(Modifier.fillMaxWidth().then(glassBand()).padding(horizontal = 6.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White) }
                    Text("Select Business", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                }
                LazyColumn(Modifier.weight(1f).fillMaxWidth().padding(top = 10.dp)) {
                    // Every book together this month, so the kiln, the home and personal spending add up in one place.
                    if (list.size > 1 && totals.isNotEmpty()) item(key = "all") {
                        val out = totals.values.sumOf { it.first }
                        val inn = totals.values.sumOf { it.second }
                        Column(Modifier.padding(start = 12.dp, end = 12.dp, bottom = 10.dp)) {
                            Text("All books · this month", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = 4.dp, bottom = 6.dp))
                            SumCard(Triple(money(currency, out), "Out", RedSpend), Triple(money(currency, inn), "In", GreenIn),
                                plusMinus(inn - out), onLink = {})
                        }
                    }
                    items(list, key = { it.first }) { (id, name, parties) ->
                        var more by remember { mutableStateOf(false) }
                        Row(
                            Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).clickable { onPick(id) }
                                .padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Outlined.Storefront, contentDescription = null, tint = Ember, modifier = Modifier.size(26.dp))
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text(name, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                val t = totals[id]
                                Text(
                                    (if (parties == 1) "1 party" else "$parties parties") +
                                        (if (t != null && (t.first > 0 || t.second > 0)) " · this month −" + fmt(t.first) + " / +" + fmt(t.second) else ""),
                                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis
                                )
                            }
                            if (id == current) Icon(Icons.Filled.Check, contentDescription = "Open", tint = Ember)
                            Box {
                                IconButton(onClick = { more = true }) { Icon(Icons.Filled.MoreVert, contentDescription = "More", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                                DropdownMenu(expanded = more, onDismissRequest = { more = false }) {
                                    DropdownMenuItem(text = { Text("Rename") }, onClick = { more = false; naming = id to name })
                                    if (id.isNotEmpty()) DropdownMenuItem(text = { Text("Delete", color = RedSpend) }, onClick = { more = false; deleting = id to name })
                                }
                            }
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                    }
                }
                Box(
                    Modifier.navigationBarsPadding().padding(16.dp).fillMaxWidth().height(50.dp).clip(CircleShape).then(glassBand())
                        .clickable { naming = null to "" },
                    contentAlignment = Alignment.Center
                ) { Text("+  Add New Business", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold) }
            }
        }
    }
    naming?.let { (id, start) ->
        var text by remember(id) { mutableStateOf(start) }
        AlertDialog(
            onDismissRequest = { naming = null },
            title = { Text(if (id == null) "New business" else "Rename business") },
            text = { OutlinedTextField(text, { text = it }, singleLine = true, placeholder = { Text("Business name") }) },
            confirmButton = {
                TextButton(enabled = text.isNotBlank(), onClick = {
                    if (id == null) onAdd(text.trim()) else onRename(id, text.trim())
                    naming = null
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { naming = null }) { Text("Cancel") } }
        )
    }
    deleting?.let { (id, name) ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete $name?") },
            text = { Text("All its entries will be deleted too.") },
            confirmButton = { TextButton(onClick = { onDelete(id); deleting = null }) { Text("Delete", color = RedSpend) } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } }
        )
    }
}

// ---------------------------------------------------------------- Expense / Income as accounts

/** Orange of work done and goods given: owed, not cash. */
private val OweInk = Color(0xFFB45309)
/** The orange of the + Work / + Given buttons. */
private val OweButton = Color(0xFFE8892B)

/** Soft square behind a row's icon. */
private val Well: Color
    @Composable @ReadOnlyComposable get() = if (MaterialTheme.colorScheme.surface.luminance() < 0.5f) Color(0xFF262626) else Color(0xFFEFECE8)

/** A picture for a name (see [NameIcon]); nothing fits: 💸 for money out, 💵 for money in. */
private fun iconFor(name: String, income: Boolean = false) = NameIcon.of(name, income)
private fun bookIcon(b: Book) = if (b.people) "👤" else b.cat?.icon ?: iconFor(b.name, b.side == Side.INC)

/** Shape of row [i] of [n] in a white rounded sheet: the first and last rows round the sheet's corners. */
private fun sheetRow(i: Int, n: Int) = RoundedCornerShape(
    topStart = if (i == 0) 16.dp else 0.dp, topEnd = if (i == 0) 16.dp else 0.dp,
    bottomStart = if (i == n - 1) 16.dp else 0.dp, bottomEnd = if (i == n - 1) 16.dp else 0.dp
)

/** One row of an account list: icon square, name and a small line, the amount with its word under it, and ›. */
@Composable
private fun ItRow(
    icon: String, title: String, sub: String, amount: String, label: String, color: Color, i: Int, n: Int,
    onLong: (() -> Unit)? = null, onClick: (() -> Unit)?
) = Tight {
    val shape = sheetRow(i, n)
    Row(
        Modifier.padding(start = 10.dp, end = 10.dp, top = if (i == 0) 6.dp else 0.dp).fillMaxWidth().shadow(1.dp, shape).clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .then(if (onClick != null || onLong != null) Modifier.combinedClickable(onLongClick = onLong) { onClick?.invoke() } else Modifier)
            .padding(start = 12.dp, end = 10.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(32.dp).clip(RoundedCornerShape(10.dp)).background(Well), contentAlignment = Alignment.Center) { Text(icon, fontSize = 16.sp) }
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (sub.isNotBlank()) Text(sub, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(amount, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = color, maxLines = 1)
            if (label.isNotBlank()) Text(label, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
        if (onClick != null) Text("›", fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 8.dp))
    }
    if (i < n - 1) HorizontalDivider(Modifier.padding(horizontal = 10.dp), color = Faint)
}

/** "+ Add account" / "+ Add in Food": a dashed rounded box under a list. */
@Composable
private fun DashedAdd(label: String, onClick: () -> Unit) {
    val edge = MaterialTheme.colorScheme.outline
    Text(
        label, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
        modifier = Modifier.padding(start = 10.dp, end = 10.dp, top = 10.dp).fillMaxWidth().clip(RoundedCornerShape(14.dp))
            .drawBehind {
                drawRoundRect(edge, cornerRadius = androidx.compose.ui.geometry.CornerRadius(14.dp.toPx()),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(1.5.dp.toPx(), pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()))))
            }
            .clickable { onClick() }.padding(12.dp)
    )
}

/** An inner page's top: a round white back button, the path above the title, then the summary card. */
@Composable
private fun BackHead(title: String, crumb: String, onBack: () -> Unit, card: @Composable () -> Unit) {
    Column(Modifier.statusBarsPadding().padding(start = 12.dp, end = 12.dp, top = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(34.dp).shadow(2.dp, CircleShape).clip(CircleShape).background(MaterialTheme.colorScheme.surface).clickable { onBack() },
                contentAlignment = Alignment.Center
            ) { Text("‹", fontSize = 20.sp, fontWeight = FontWeight.Bold) }
            Spacer(Modifier.width(10.dp))
            Column {
                Text(crumb, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(title, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Spacer(Modifier.height(10.dp))
        card()
    }
}

/** The big add buttons of a person or thing: solid colours, side by side just above the bottom tabs. */
@Composable
private fun BoxScope.SolidButtons(buttons: List<Pair<String, Color>>, full: Boolean = false, onClick: (Int) -> Unit) {
    Row(
        Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(start = 10.dp, end = 10.dp, bottom = LocalBarSpace.current + 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        buttons.forEachIndexed { i, (label, color) ->
            Box(
                Modifier.weight(1f).height(38.dp).clip(RoundedCornerShape(12.dp)).background(color).clickable { onClick(i) },
                contentAlignment = Alignment.Center
            ) { Text(label, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold) }
        }
        // One button takes half the width (like + Out), unless it is meant to fill the row.
        if (buttons.size == 1 && !full) Spacer(Modifier.weight(1f))
    }
}

private val dayTimeFmt = SimpleDateFormat("EEE, d MMM yyyy · h:mma", Locale.US)

/**
 * A person's or thing's own ledger, plain and white: the head (month or All, entry count, each column's name and
 * total), then one line per entry with its date and time under it. Tap edits an entry; hold opens its menu.
 */
private fun LazyListScope.simpleLedger(cols: List<Pair<String, Color>>, totals: List<Double>, rows: List<Pair<Entry, List<Double>>>, title: (Entry) -> String, onEdit: (Entry) -> Unit) {
    val months = rows.map { monthKey(it.first.date) }.distinct()
    val head = if (months.size == 1) monthFmt.format(Date(rows.first().first.date)) else "All"
    item(key = "klh") {
        Tight {
            val shape = RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp, bottomStart = if (rows.isEmpty()) 14.dp else 0.dp, bottomEnd = if (rows.isEmpty()) 14.dp else 0.dp)
            Row(Modifier.padding(start = 10.dp, end = 10.dp, top = 8.dp).fillMaxWidth().height(IntrinsicSize.Min).shadow(1.dp, shape).clip(shape).background(MaterialTheme.colorScheme.surface)) {
                Column(Modifier.weight(1f).padding(start = 12.dp, top = 7.dp, bottom = 7.dp)) {
                    Text(head, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
                    Text(entryCount(rows.size), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                cols.forEachIndexed { k, (label, color) ->
                    VerticalDivider(Modifier.fillMaxHeight(), color = Faint)
                    Column(Modifier.width(68.dp).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        Text(label.uppercase(), fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (label.isNotBlank()) Text(fmt(totals[k]), fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = color, maxLines = 1, softWrap = false)
                    }
                }
            }
        }
    }
    if (rows.isEmpty()) item(key = "kle") { EmptyHint() }
    rows.forEachIndexed { i, (e, amounts) ->
        item(key = "kl" + e.id) {
            Tight {
                val last = i == rows.lastIndex
                val shape = if (last) RoundedCornerShape(bottomStart = 14.dp, bottomEnd = 14.dp) else RectangleShape
                val menu = LocalEntryMenu.current
                Row(
                    Modifier.padding(horizontal = 10.dp).fillMaxWidth().height(IntrinsicSize.Min).shadow(1.dp, shape).clip(shape)
                        .background(MaterialTheme.colorScheme.surface).combinedClickable(onLongClick = menu?.let { { it(e) } }) { onEdit(e) }
                ) {
                    Column(Modifier.weight(1f).padding(start = 12.dp, end = 4.dp, top = 7.dp, bottom = 7.dp)) {
                        Text(title(e), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(dayTimeFmt.format(Date(e.date)).replace("AM", "am").replace("PM", "pm"), fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, modifier = Modifier.padding(top = 2.dp))
                    }
                    cols.indices.forEach { k ->
                        VerticalDivider(Modifier.fillMaxHeight(), color = Faint)
                        Box(Modifier.width(68.dp).fillMaxHeight().padding(top = 7.dp), contentAlignment = Alignment.TopCenter) {
                            val v = amounts.getOrElse(k) { 0.0 }
                            Text(if (v == 0.0) "" else fmt(v), fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                        }
                    }
                }
                if (!last) HorizontalDivider(Modifier.padding(horizontal = 10.dp), color = Faint)
            }
        }
    }
}

/** What a person's entry shows: goods as "item · qty × rate", money as its note (or Paid / Received). */
private fun personLine(e: Entry) = if (e.qty > 0)
    (e.item.takeIf { it.isNotBlank() && it !in setOf("Sale", "Purchase", "Loans") }?.let { "$it · " } ?: "") + fmt(e.qty) +
        (if (e.rate > 0) " × " + fmt(e.rate) else "")
else e.note.ifBlank { if (personEffect(e) > 0) "Paid" else "Received" }

/** Colour of a person's balance word: green when they owe you, red when you owe them, orange for goods still to give. */
private fun balanceColor(what: String) = when (what) {
    "To get", "Advance left" -> GreenIn
    "Advance taken" -> OweInk
    "Settled" -> Color(0xFF78716C)
    else -> RedSpend
}

/**
 * Party, Expense or Income as accounts. Party: people accounts (Customers, Suppliers, Staff, People…); a person opens
 * their khata with Paid | Work (or Given | Received) and the two add buttons. Expense and Income: things accounts (Food,
 * Bills…, a thing opens its ledger and + Out / + In), plus Purchases and Sales filled from the khatas. The chips pick the
 * period of the figures. Entries are added only inside a person or thing.
 */
@Composable
fun SideScreen(
    side: Side, entries: List<Entry>, currency: String, setup: SideSetup, onSetup: (SideSetup) -> Unit,
    onMenu: () -> Unit, business: String, onEdit: (Entry) -> Unit, onSave: (Entry) -> Unit, onReport: () -> Unit,
    /** Opens this account and person straight away (from the report's To get / To pay). */
    jump: Pair<String, String>? = null, onJumped: () -> Unit = {},
    /** Move or delete (to Trash) all entries of a category account. */
    onMoveKhata: (String) -> Unit = {}, onDeleteKhata: (String) -> Unit = {}
) {
    val party = side == Side.PARTY
    val income = side == Side.INC
    var bookName by rememberSaveable(side) { mutableStateOf<String?>(null) }
    var kidName by rememberSaveable(side) { mutableStateOf<String?>(null) }
    var period by rememberSaveable(side) { mutableStateOf("") }
    var q by rememberSaveable(side) { mutableStateOf("") }
    var adding by remember { mutableStateOf<String?>(null) }   // "book" or "kid"
    var sheet by remember { mutableStateOf<AddReq?>(null) }
    var acct by remember { mutableStateOf<Book?>(null) }        // long-pressed account
    var renaming by remember { mutableStateOf<Book?>(null) }
    LaunchedEffect(jump) { jump?.let { (b, k) -> bookName = b; kidName = k; onJumped() } }
    val books = remember(entries, setup, side) { Sides.books(side, entries, setup) }
    val book = books.firstOrNull { it.name == bookName }
    val newest = remember(entries) { entries.sortedByDescending { it.date } }
    val per = remember(period, newest) { Period.decode(period) ?: Period.newestMonth(newest) }
    val inPer = { ms: Long -> per.has(ms) }
    BackHandler(enabled = bookName != null) { if (kidName != null) kidName = null else bookName = null }
    val ink = if (income) GreenIn else RedSpend
    val crumb = when (side) { Side.PARTY -> "Party"; Side.INC -> "Income"; Side.EXP -> "Expense" }
    val khataMenu = LocalKhataMenu.current
    val state = rememberLazyListState()
    /** Whether [b] is one the owner added (not Staff, Food… read from the entries). */
    fun own(b: Book) = setup.books.any { it.name.equals(b.name, true) && it.people == b.people && (b.people || it.side == side) }
    /** The word under an account's figure. */
    fun word(b: Book, inside: Boolean) = if (Sides.isAuto(b)) (if (income) "sold" else "work") else if (income) (if (inside) "income" else "received") else "spent"
    Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), state = state, contentPadding = PaddingValues(bottom = barSpace(56.dp))) {
            when {
                book != null && kidName != null -> {
                    val auto = Sides.isAuto(book)
                    val kid = Sides.kids(book, entries, setup).firstOrNull { it.name.equals(kidName, true) } ?: Kid(kidName!!, emptyList())
                    item(key = "kh") {
                        BackHead(kid.name, "$crumb › ${book.name} ›", { kidName = null }) {
                            if (book.people) {
                                val (amt, what) = Sides.balanceText(book, kid.balance)
                                SumCard(Triple(amt, what.lowercase(), balanceColor(what)), null, "", {})
                            } else SumCard(Triple(fmt(kid.total), word(book, true), ink), null, "", {})
                        }
                    }
                    if (book.people) {
                        val (plus, minus) = Sides.columns(book)
                        val rows = kid.entries.map { e -> e to (if (personEffect(e) > 0) listOf(e.amount, 0.0) else listOf(0.0, e.amount)) }
                        simpleLedger(listOf(plus to RedSpend, minus to GreenIn), listOf(rows.sumOf { it.second[0] }, rows.sumOf { it.second[1] }), rows, ::personLine, onEdit)
                    } else {
                        val rows = kid.entries.map { e -> e to (if (income) listOf(0.0, e.amount) else listOf(e.amount, 0.0)) }
                        val label = if (auto) (if (income) "Sales" else "Work") else if (income) "In" else "Out"
                        val cols = if (income) listOf("" to ink, label to ink) else listOf(label to ink, "" to ink)
                        simpleLedger(cols, listOf(if (income) 0.0 else kid.total, if (income) kid.total else 0.0), rows,
                            { e -> if (auto) personLine(e) else e.note.ifBlank { label } }, onEdit)
                    }
                }
                book != null -> {
                    val auto = Sides.isAuto(book)
                    val kids = Sides.kids(book, entries, setup)
                    val inc = book.side == Side.INC
                    stickyHeader { TabHead {
                        BackHead(book.name, "$crumb ›", { bookName = null }) {
                            if (book.people) {
                                val pos = kids.filter { it.balance > 0.5 }.sumOf { it.balance }
                                val neg = kids.filter { it.balance < -0.5 }.sumOf { -it.balance }
                                SumCard(Triple(fmt(pos), Sides.balanceText(book, 1.0).second, if (inc) GreenIn else OweInk),
                                    Triple(fmt(neg), Sides.balanceText(book, -1.0).second, if (inc) OweInk else RedSpend), "", {})
                            } else SumCard(Triple(fmt(kids.sumOf { it.total }), word(book, true).replaceFirstChar { it.uppercase() }, ink), null, "", {})
                        }
                    } }
                    kids.forEachIndexed { i, k ->
                        item(key = "k" + k.name.lowercase()) {
                            val (amt, what) = if (book.people) Sides.balanceText(book, k.balance) else fmt(k.total) to word(book, true)
                            ItRow(
                                if (book.people || auto) "👤" else iconFor(k.name, income), k.name, entryCount(k.entries.size), amt, what.lowercase(),
                                if (book.people) balanceColor(what) else ink, i, kids.size,
                                onLong = if (auto) null else khataMenu?.let { m -> { m(if (book.people) "party" else if (income) "income" else "expense-name", k.name) } }
                            ) { kidName = k.name }
                        }
                    }
                    if (auto) item(key = "autonote") {
                        Text(
                            if (income) "Sales come from Party › Customers (+ Given)." else "Work and goods come from Party › Staff and Suppliers (+ Work).",
                            fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                        )
                    } else item(key = "addkid") { DashedAdd("+ Add in ${book.name}") { adding = "kid" } }
                }
                else -> {
                    stickyHeader { TabHead {
                        KhataTop("", "", onMenu, business = business) {
                            if (party) {
                                val (get, give) = Sides.owed(entries, setup, inPer)
                                SumCard(Triple(fmt(get), "To get", GreenIn), Triple(fmt(give), "To give", RedSpend), "Report ›", onReport)
                            } else {
                                val total = books.sumOf { Sides.done(it, entries, setup, inPer) }
                                SumCard(Triple(fmt(total), (if (income) "Income · " else "Spent · ") + per.label(), ink), null, "Report ›", onReport)
                            }
                        }
                        PeriodChips(newest, per) { period = it.encode() }
                        SearchBox(q, "Search") { q = it }
                    } }
                    val shown = books.filter { q.isBlank() || it.name.contains(q.trim(), true) ||
                        Sides.kids(it, entries, setup).any { k -> k.name.contains(q.trim(), true) } }
                    shown.forEachIndexed { i, b ->
                        item(key = "b" + b.name) {
                            val kids = Sides.kids(b, entries, setup)
                            if (b.people) {
                                // The period's net on the row; what is owed all time in the small line, as the account shows inside.
                                val net = kids.sumOf { k -> k.entries.filter { inPer(it.date) }.sumOf { personEffect(it) } }
                                val p = kids.filter { it.balance > 0.5 }.sumOf { it.balance }
                                val m = kids.filter { it.balance < -0.5 }.sumOf { -it.balance }
                                val sub = (if (kids.size == 1) "1 person" else "${kids.size} people") +
                                    (if (p > 0.5) " · " + fmt(p) + " to get" else "") + (if (m > 0.5) " · " + fmt(m) + " to give" else "")
                                ItRow("👤", b.name, sub, fmt(kotlin.math.abs(net)), (if (net >= 0) "to get" else "to give") + " · net",
                                    if (net >= 0) GreenIn else RedSpend, i, shown.size, onLong = { acct = b }) { bookName = b.name; q = "" }
                            } else {
                                ItRow(bookIcon(b), b.name, kids.take(4).joinToString(", ") { it.name }, fmt(Sides.done(b, entries, setup, inPer)), word(b, false), ink, i, shown.size,
                                    onLong = if (Sides.isAuto(b)) null else ({ acct = b })) { bookName = b.name; q = "" }
                            }
                        }
                    }
                    if (shown.isEmpty()) emptyNote("Nothing found")
                    item(key = "addbook") { DashedAdd("+ Add account") { adding = "book" } }
                }
            }
        }
        // A person or thing: its add buttons. Lists, Purchases and Sales have none (entries go inside a person or thing).
        if (book != null && kidName != null && !Sides.isAuto(book)) {
            val name = kidName!!
            val staff = Sides.bookOf(name, entries, setup) == Sides.STAFF
            val crumbs = "${book.name} › $name"
            fun add(t: Entry, label: String, color: Color, qty: Boolean = false) { sheet = AddReq(t, label, color, crumbs, qty) }
            when {
                !book.people -> SolidButtons(listOf((if (income) "+ In" else "+ Out") to ink)) {
                    add(cashTemplate(income).copy(category = if (income) Cat.TANKHWAH else book.cat ?: Cat.OTHER, item = name), if (income) "In" else "Out", ink)
                }
                book.side == Side.INC -> SolidButtons(listOf("+ Received" to GreenIn, "+ Given" to OweButton)) { i ->
                    if (i == 0) add(template(Type.WAPIS_MILA, name), "Received", GreenIn)
                    else add(template(Type.SALE, name).copy(category = Cat.SALES, item = ""), "Given", OweButton, qty = true)
                }
                book.name == Sides.PEOPLE -> SolidButtons(listOf("+ Paid" to RedSpend, "+ Received" to GreenIn)) { i ->
                    if (i == 0) add(template(Type.DIYA, name), "Paid", RedSpend) else add(template(Type.WAPIS_MILA, name), "Received", GreenIn)
                }
                else -> SolidButtons(listOf("+ Paid" to RedSpend, "+ Work" to OweButton)) { i ->
                    if (i == 0) add(template(Type.DIYA, name), "Paid", RedSpend)
                    else add(template(Type.PURCHASE, name).copy(category = if (staff) Cat.PRODUCTION else Cat.STOCK, item = ""), "Work", OweButton, qty = true)
                }
            }
        } else UpButton(state)
    }
    adding?.let { what ->
        val done = { name: String ->
            val n = name.trim()
            if (n.isNotEmpty()) onSetup(when {
                // A new Party account keeps Paid | Work columns, Customers Given | Received.
                what == "book" -> setup.copy(books = setup.books + Book(if (n.equals(Sides.CUSTOMERS, true)) Side.INC else if (party) Side.EXP else side, n, party))
                book != null && book.people -> setup.copy(people = setup.people + (n to book.name))
                book != null -> setup.copy(kids = setup.kids + (book.name to (setup.kids[book.name].orEmpty() + n)))
                else -> setup
            })
            adding = null
        }
        if (what == "book") AccountDialog(side, books.map { it.name.lowercase() }.toSet(), onDismiss = { adding = null }, onSave = done)
        else {
            // Things: the category's usual sub-accounts not made yet, as chips.
            val have = book?.let { b -> Sides.kids(b, entries, setup).map { it.name.lowercase() }.toSet() }.orEmpty()
            val picks = book?.takeIf { !it.people }?.cat?.let { defaultSubs[it] }.orEmpty().filter { it.lowercase() !in have }
            NamePopup("Add in ${book?.name}", if (book?.people == true) "Name" else "Name (e.g. Groceries)", onDismiss = { adding = null }, picks = picks, onSave = done)
        }
    }
    sheet?.let { r -> AddSheet(r, entries, onDismiss = { sheet = null }) { e -> onSave(e); sheet = null } }
    // An account's long-press menu: Rename (own and people accounts), Move and Delete (a category's entries, or an own account).
    acct?.let { b ->
        val mine = own(b)
        val n = Sides.kids(b, entries, setup).sumOf { it.entries.size }
        ActionMenu(
            title = b.name, subtitle = if (n == 1) "1 entry" else "$n entries", amount = "", amountColor = Color.Unspecified,
            actions = listOfNotNull(
                if (mine || b.people) MenuAction("Rename", Icons.Filled.Edit) { acct = null; renaming = b } else null,
                if (b.cat != null && n > 0) MenuAction("Move", Icons.Filled.SwapHoriz) { acct = null; onMoveKhata(b.name) } else null,
                when {
                    mine -> MenuAction("Delete", Icons.Filled.Delete, danger = true) {
                        acct = null
                        onSetup(setup.copy(books = setup.books.filterNot { it.name.equals(b.name, true) && it.people == b.people && (b.people || it.side == side) },
                            people = setup.people.filterValues { !it.equals(b.name, true) }, kids = setup.kids - b.name))
                    }
                    b.cat != null && n > 0 -> MenuAction("Delete", Icons.Filled.Delete, danger = true) { acct = null; onDeleteKhata(b.name) }
                    else -> null
                }
            ),
            onDismiss = { acct = null }
        )
    }
    renaming?.let { b ->
        RenameDialog("Rename account", b.name, onRename = { new ->
            renaming = null
            val mine = own(b)
            // A ready people account (Staff…) becomes the owner's own under the new name, its people with it.
            val people = Sides.kids(b, entries, setup).map { it.name }
            onSetup(setup.copy(
                books = if (mine) setup.books.map { if (it.name.equals(b.name, true) && it.people == b.people && (b.people || it.side == side)) it.copy(name = new) else it }
                    else setup.books + Book(b.side, new, true),
                people = setup.people.mapValues { (_, v) -> if (v.equals(b.name, true)) new else v } + (if (mine) emptyMap() else people.associateWith { new }),
                kids = setup.kids[b.name]?.let { (setup.kids - b.name) + (new to it) } ?: setup.kids
            ))
        }, onDismiss = { renaming = null })
    }
}

/** The app's popup: a white rounded card with its title and ✕, the content, then Cancel and a coloured main button. */
@Composable
private fun AppPopup(title: String, action: String, onDismiss: () -> Unit, onAction: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.surface).padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
                Box(Modifier.size(28.dp).clip(CircleShape).background(Well).clickable { onDismiss() }, contentAlignment = Alignment.Center) { Text("✕", fontSize = 12.sp) }
            }
            content()
            Row(Modifier.padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.weight(1f).height(40.dp).clip(RoundedCornerShape(12.dp)).border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
                    .clickable { onDismiss() }, contentAlignment = Alignment.Center) { Text("Cancel", fontSize = 14.sp, fontWeight = FontWeight.Bold) }
                Box(Modifier.weight(1f).height(40.dp).clip(RoundedCornerShape(12.dp)).background(EmberTop).clickable { onAction() },
                    contentAlignment = Alignment.Center) { Text(action, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White) }
            }
        }
    }
}

/** A plain field in the popups and the add sheet: grey well, thin edge, hint inside. */
@Composable
private fun WellField(value: String, onChange: (String) -> Unit, hint: String, modifier: Modifier = Modifier, big: Boolean = false, number: Boolean = false) {
    val style = TextStyle(fontSize = if (big) 20.sp else 14.sp, fontWeight = if (big) FontWeight.ExtraBold else FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
    Box(
        modifier.padding(top = 10.dp).fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Well)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp)).padding(horizontal = 12.dp, vertical = 11.dp)
    ) {
        if (value.isEmpty()) Text(hint, style = style.copy(color = MaterialTheme.colorScheme.onSurfaceVariant))
        BasicTextField(
            value, { v -> onChange(if (number) v.filter { it.isDigit() || it == '.' } else v) }, singleLine = true, textStyle = style,
            cursorBrush = SolidColor(EmberTop), modifier = Modifier.fillMaxWidth(),
            keyboardOptions = if (number) androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal)
            else androidx.compose.foundation.text.KeyboardOptions.Default
        )
    }
}

/** A small tap-to-pick chip in the popups (ringed in orange when picked). */
@Composable
private fun PickChip(label: String, on: Boolean, onClick: () -> Unit) {
    Text(
        label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = if (on) EmberTop else MaterialTheme.colorScheme.onSurface, maxLines = 1,
        modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(if (on) EmberTop.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surface)
            .border(1.dp, if (on) EmberTop else MaterialTheme.colorScheme.outline, RoundedCornerShape(10.dp)).clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 7.dp)
    )
}

/** Ready accounts offered in New account: name, picture, and whether it holds people. */
private val accountPicks = mapOf(
    Side.EXP to listOf(
        "Food" to "🍛", "Transport" to "🚗", "Bills" to "💡", "Rent" to "🏠", "Mobile & Internet" to "📱", "Health" to "💊", "Education" to "📚",
        "Shopping" to "🛍️", "Personal care" to "💈", "Fees & Govt" to "🏛️", "Gifts & Charity" to "🎁", "Production" to "🏭"
    ),
    Side.INC to listOf("Salary" to "💼", "Rent received" to "🏘️", "Other income" to "💰"),
    Side.PARTY to listOf(Sides.CUSTOMERS to "👤", Sides.SUPPLIERS to "👤", Sides.STAFF to "👤", Sides.PEOPLE to "👤")
)

/** New account: type a name or pick a ready one, then Add. On Party it is a people account. */
@Composable
private fun AccountDialog(side: Side, have: Set<String>, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    AppPopup("New account", "Add", onDismiss, { if (name.isNotBlank()) onSave(name) }) {
        WellField(name, { name = it }, "Account name")
        val picks = accountPicks[side].orEmpty().filter { it.first.lowercase() !in have }
        if (picks.isNotEmpty()) {
            Text("OR PICK ONE", fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 0.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 12.dp, bottom = 6.dp))
            // Two chips a row keeps every pick visible without a wrapping layout.
            Column(Modifier.heightIn(max = 190.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                picks.chunked(2).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        row.forEach { (n, icon) -> PickChip("$icon $n", name == n) { name = n } }
                    }
                }
            }
        }
    }
}

/** A one-field popup (a name to add inside an account, or a new business). */
@Composable
internal fun NamePopup(title: String, hint: String, onDismiss: () -> Unit, picks: List<String> = emptyList(), onSave: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    AppPopup(title, "Add", onDismiss, { if (name.isNotBlank()) onSave(name.trim()) }) {
        WellField(name, { name = it }, hint)
        // Ready names (Groceries, Petrol…) to tap instead of typing.
        if (picks.isNotEmpty()) {
            Text("OR PICK ONE", fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 0.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 12.dp, bottom = 6.dp))
            Column(Modifier.heightIn(max = 190.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                picks.chunked(2).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { row.forEach { n -> PickChip("${iconFor(n)} $n", name == n) { name = n } } }
                }
            }
        }
    }
}

/** What an add button opens: the blank entry, its word (Out, Paid, Work…), colour, where it goes, and whether it takes goods. */
private class AddReq(val template: Entry, val label: String, val color: Color, val crumb: String, val goods: Boolean)

private val sheetDay = SimpleDateFormat("dd/MM/yyyy", Locale.US)
private val sheetTime = SimpleDateFormat("h:mm a", Locale.US)

/**
 * The add sheet from the bottom: amount (or goods, qty and rate for Work / Given, the last rate filling itself),
 * note, date and time, and Save in the kind's colour. A rate "per 1000" (bricks) counts thousands.
 */
@Composable
private fun AddSheet(r: AddReq, all: List<Entry>, onDismiss: () -> Unit, onSave: (Entry) -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var amount by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var item by remember { mutableStateOf("") }
    var qty by remember { mutableStateOf("") }
    var rate by remember { mutableStateOf("") }
    var per1000 by remember { mutableStateOf(false) }
    var date by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val out = isOut(r.template.type)
    val known = remember(all) { Goods.known(all) }
    fun pick(n: String) {
        item = n
        Goods.lastRate(n, r.template.person, out, all)?.let { rate = plain(it.rate); per1000 = it.unit == "1000" }
    }
    val q = qty.toDoubleOrNull() ?: 0.0
    val rt = rate.toDoubleOrNull() ?: 0.0
    val total = if (r.goods) (if (per1000) q / 1000 * rt else q * rt) else amount.toDoubleOrNull() ?: 0.0
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surface, dragHandle = null,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)) {
        Column(Modifier.padding(16.dp).navigationBarsPadding().imePadding()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("+ ${r.label}", fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
                    Text(r.crumb, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Box(Modifier.size(28.dp).clip(CircleShape).background(Well).clickable { onDismiss() }, contentAlignment = Alignment.Center) { Text("✕", fontSize = 12.sp) }
            }
            if (r.goods) {
                WellField(item, { item = it; known.firstOrNull { k -> k.equals(it.trim(), true) }?.let(::pick) }, if (r.label == "Given") "Item (type new or tap one)" else "Work (type new or tap one)")
                val hints = remember(item, known) { Goods.suggest(item, known) }
                if (hints.isNotEmpty()) LazyRow(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(hints) { h -> PickChip(h, item == h) { pick(h) } }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    WellField(qty, { qty = it }, "Qty", Modifier.weight(1f), big = true, number = true)
                    WellField(rate, { rate = it }, if (per1000) "Rate per 1000" else "Rate", Modifier.weight(1f), number = true)
                }
                Text(if (q > 0 && rt > 0) "Amount = " + fmt(q) + (if (per1000) " ÷ 1000" else "") + " × " + fmt(rt) + " = " + fmt(total) else "Amount = qty × rate",
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 10.dp))
            } else WellField(amount, { amount = it }, "Amount", big = true, number = true)
            WellField(note, { note = it }, "Note (optional)")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val c = cal(date)
                PickBox(sheetDay.format(Date(date)), Modifier.weight(1f)) {
                    android.app.DatePickerDialog(context, { _, y, m, d -> date = cal(date).apply { set(y, m, d) }.timeInMillis },
                        c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show()
                }
                PickBox(sheetTime.format(Date(date)).replace("AM", "am").replace("PM", "pm"), Modifier.weight(1f)) {
                    android.app.TimePickerDialog(context, { _, h, m -> date = cal(date).apply { set(Calendar.HOUR_OF_DAY, h); set(Calendar.MINUTE, m) }.timeInMillis },
                        c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE), false).show()
                }
            }
            Text(
                when (r.label) { "Given" -> "Money comes later with + Received."; "Work" -> "Pay later with + Paid."; else -> "Goes in Cashbook too." },
                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 10.dp)
            )
            Box(
                Modifier.padding(top = 12.dp).fillMaxWidth().height(42.dp).clip(RoundedCornerShape(12.dp)).background(r.color).clickable {
                    if (total <= 0.0 || (r.goods && item.isBlank())) return@clickable
                    val now = System.currentTimeMillis()
                    onSave(r.template.copy(
                        id = "e" + System.nanoTime(), amount = total, note = note.trim(), date = date, createdAt = now,
                        item = if (r.goods) Goods.canonical(item, known) else r.template.item,
                        qty = if (r.goods) q else 0.0, rate = if (r.goods) rt else 0.0, unit = if (r.goods && per1000) "1000" else ""
                    ))
                },
                contentAlignment = Alignment.Center
            ) { Text("Save", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold) }
        }
    }
}

/** A grey box showing a date or time; tap to change it. */
@Composable
private fun PickBox(text: String, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier.padding(top = 10.dp).clip(RoundedCornerShape(12.dp)).background(Well)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp)).clickable { onClick() }.padding(horizontal = 12.dp, vertical = 11.dp)
    ) { Text(text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold) }
}

/** One line of the profit report: name and a small line, then the amount in its colour; tap opens its detail. */
@Composable
private fun ReportRow(title: String, sub: String, amount: String, color: Color, onClick: (() -> Unit)?) = Tight {
    Row(
        Modifier.padding(start = 10.dp, end = 10.dp, top = 2.dp).fillMaxWidth().height(IntrinsicSize.Min).shadow(1.dp)
            .background(MaterialTheme.colorScheme.surface).then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
    ) {
        Column(Modifier.weight(1f).padding(start = 12.dp, end = 4.dp, top = 10.dp, bottom = 10.dp), verticalArrangement = Arrangement.Center) {
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
            if (sub.isNotBlank()) Text(sub, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        VerticalDivider(Modifier.fillMaxHeight(), color = Faint)
        Box(Modifier.width(96.dp).fillMaxHeight(), contentAlignment = Alignment.Center) {
            Text(amount, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = color, maxLines = 1, softWrap = false)
        }
    }
}

/** "+1,200" / "−1,200", or a plain 0 when there is nothing. */
private fun withSign(v: Double, sign: String) = if (v > 0.5) sign + fmt(v) else fmt(0.0)

/**
 * Report · Profit for the period: sales and other income less purchases and expenses. Then net worth today: cash in
 * hand, dues to get, stock and assets at cost, less what you owe. Every line opens its entries or people ([onOpen]
 * opens a person's khata in Party).
 */
@Composable
fun ProfitScreen(entries: List<Entry>, currency: String, setup: SideSetup, onBack: () -> Unit, onEdit: (Entry) -> Unit, onOpen: (book: String, kid: String) -> Unit) {
    val newest = remember(entries) { entries.sortedWith(compareByDescending<Entry> { it.date }.thenByDescending { it.createdAt }) }
    var period by rememberSaveable { mutableStateOf("") }
    val per = remember(period, newest) { Period.decode(period) ?: Period.newestMonth(newest) }
    var open by rememberSaveable { mutableStateOf<String?>(null) }
    val picked = remember(newest, per) { newest.filter { per.has(it.date) } }
    val p = remember(picked) { Sides.profit(picked) }
    val worth = remember(entries, setup) { Sides.worth(entries, setup) }
    // Everyone with a balance, from every Party account: + they owe you, − you owe them.
    val people = remember(entries, setup) {
        Sides.books(Side.PARTY, entries, setup).flatMap { b -> Sides.kids(b, entries, setup).map { k -> b to k } }
    }
    val get = people.filter { it.second.balance > 0.5 }
    val pay = people.filter { it.second.balance < -0.5 }
    val plain = MaterialTheme.colorScheme.onSurface
    BackHandler { if (open != null) open = null else onBack() }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = barSpace(56.dp))) {
        val o = open
        when {
            o == "To get" || o == "To pay" -> {
                val list = if (o == "To get") get else pay
                val color = if (o == "To get") GreenIn else RedSpend
                item(key = "oh") { BackHead(o, "Cashbook › Report ›", { open = null }) { SumCard(Triple(fmt(list.sumOf { kotlin.math.abs(it.second.balance) }), o, color), null, "", {}) } }
                if (list.isEmpty()) emptyNote("Nobody here.")
                list.forEachIndexed { i, (b, k) ->
                    item(key = "op" + b.name + k.name) {
                        ItRow("👤", k.name, b.name, fmt(kotlin.math.abs(k.balance)), Sides.balanceText(b, k.balance).second.lowercase(), color, i, list.size) { onOpen(b.name, k.name) }
                    }
                }
            }
            o == "Stock" -> {
                val list = Sides.items(entries, setup).filter { it.inStock > 0.5 }
                item(key = "sh") { BackHead(o, "Cashbook › Report ›", { open = null }) { SumCard(Triple(fmt(worth.stock), "Stock, at cost", GreenIn), null, "", {}) } }
                if (list.isEmpty()) emptyNote("Nothing in stock.")
                list.forEachIndexed { i, x ->
                    item(key = "st" + x.name) { ItRow(iconFor(x.name), x.name, fmt(x.inStock) + " in stock", fmt(x.inStock * Sides.unitCost(x)), "at cost", plain, i, list.size, onClick = null) }
                }
            }
            o == "Cash in hand" || o == "Assets" -> {
                val cash = o == "Cash in hand"
                val list = if (cash) newest.filter { cashIn(it) || cashOut(it) } else newest.filter { it.type == Type.KHARCHA && it.category == Cat.ASSETS }
                val total = if (cash) worth.cash else worth.assets
                item(key = "ch") { BackHead(o, "Cashbook › Report ›", { open = null }) { SumCard(Triple(plusMinus(total).trimStart('+'), o, if (total < 0) RedSpend else GreenIn), null, "", {}) } }
                simpleLedger(
                    if (cash) listOf("Out" to RedSpend, "In" to GreenIn) else listOf("Cost" to RedSpend, "" to RedSpend),
                    if (cash) listOf(list.filter(::cashOut).sumOf { it.amount }, list.filter(::cashIn).sumOf { it.amount }) else listOf(total, 0.0),
                    list.map { e -> e to (if (cash && cashIn(e)) listOf(0.0, e.amount) else listOf(e.amount, 0.0)) },
                    { e -> (if (e.person.isNotBlank()) e.person + " · " else "") + e.item.ifBlank { e.category.label } },
                    onEdit
                )
            }
            o != null -> {
                val type = when (o) { "Sales" -> Type.SALE; "Other income" -> Type.AAMDANI; "Purchases" -> Type.PURCHASE; else -> Type.KHARCHA }
                val inn = type == Type.SALE || type == Type.AAMDANI
                val color = if (inn) GreenIn else RedSpend
                val list = picked.filter { it.type == type && it.amount > 0 && !(type == Type.KHARCHA && it.category == Cat.ASSETS) }
                val total = list.sumOf { it.amount }
                item(key = "dh") { BackHead(o, "Cashbook › Report ›", { open = null }) { SumCard(Triple(fmt(total), o, color), null, "", {}) } }
                val label = when (o) { "Sales" -> "Sales"; "Other income" -> "In"; "Purchases" -> "Work"; else -> "Out" }
                simpleLedger(
                    if (inn) listOf("" to color, label to color) else listOf(label to color, "" to color),
                    if (inn) listOf(0.0, total) else listOf(total, 0.0),
                    list.map { e -> e to (if (inn) listOf(0.0, e.amount) else listOf(e.amount, 0.0)) },
                    { e -> (if (e.person.isNotBlank()) e.person + " · " else "") + if (e.qty > 0) personLine(e) else e.item.ifBlank { e.category.label } },
                    onEdit
                )
            }
            else -> {
                stickyHeader { TabHead {
                    BackHead("Report · Profit", "Cashbook ›", onBack) {
                        SumCard(Triple(fmt(kotlin.math.abs(p.profit)), if (p.profit < 0) "Loss" else "Profit", if (p.profit < 0) RedSpend else GreenIn),
                            Triple(fmt(p.sale + p.income), "Earned", GreenIn), "", {})
                    }
                    PeriodChips(newest, per) { period = it.encode() }
                } }
                item(key = "r1") { ReportRow("Sales", "goods given to customers", withSign(p.sale, "+"), GreenIn) { open = "Sales" } }
                item(key = "r2") { ReportRow("Other income", "salary, rent…", withSign(p.income, "+"), GreenIn) { open = "Other income" } }
                item(key = "r3") { ReportRow("Purchases", "work and goods from staff and suppliers", withSign(p.work, "−"), RedSpend) { open = "Purchases" } }
                item(key = "r4") { ReportRow("Expenses", "home, bills…", withSign(p.spent, "−"), RedSpend) { open = "Expenses" } }
                item(key = "r5") { ReportRow("Profit", "", plusMinus(p.profit), if (p.profit < 0) RedSpend else GreenIn, null) }
                item(key = "nw") {
                    Text("NET WORTH · WHAT YOU OWN", fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 0.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 14.dp, top = 14.dp, bottom = 4.dp))
                }
                item(key = "n1") { ReportRow("Cash in hand", "cash and bank, from the Cashbook", plusMinus(worth.cash).trimStart('+'), if (worth.cash < 0) RedSpend else GreenIn) { open = "Cash in hand" } }
                item(key = "n2") { ReportRow("To get", "money others owe you", withSign(worth.toGet, "+"), GreenIn) { open = "To get" } }
                item(key = "n3") { ReportRow("Stock", "goods in stock, at what they cost", withSign(worth.stock, "+"), GreenIn) { open = "Stock" } }
                item(key = "n4") { ReportRow("Assets", "laptop, car, machines… at cost", withSign(worth.assets, "+"), GreenIn) { open = "Assets" } }
                item(key = "n5") { ReportRow("To pay", "money you owe", withSign(worth.toPay, "−"), RedSpend) { open = "To pay" } }
                item(key = "n6") { ReportRow("Net worth", "what you are worth today", plusMinus(worth.net).trimStart('+'), if (worth.net < 0) RedSpend else GreenIn, null) }
            }
        }
    }
}
