@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package com.bolkhata.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.NoteAdd
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
/** Deep red for titles on the white sheets. */
private val DeepRed = Color(0xFFC62828)
/** Colour at the top of the orange header, also used for the status bar. */
internal val EmberTop = Color(0xFFF0572E)
internal val EmberBrush = Brush.linearGradient(listOf(Color(0xFFF0572E), Color(0xFFE3342F)))

/** Money that left your hand. A sale or purchase on credit with a party moves no cash. */
fun cashOut(e: Entry) = when (e.type) {
    Type.KHARCHA, Type.DIYA, Type.WAPIS_DIYA, Type.TRANSFER_OUT -> true
    Type.PURCHASE -> e.paid || e.person.isBlank()
    else -> false
}

/** Money that came into your hand. */
fun cashIn(e: Entry) = when (e.type) {
    Type.AAMDANI, Type.LIYA, Type.WAPIS_MILA, Type.TRANSFER_IN -> true
    Type.SALE -> e.paid || e.person.isBlank()
    else -> false
}

/** The tabs shown in the bottom bar, in order. */
val KHATA_TABS = listOf(Tab.CHAT, Tab.PARTIES, Tab.CASHBOOK, Tab.EXPENSE, Tab.INCOME)

@Composable
fun KhataNav(tab: Tab, onTab: (Tab) -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 6.dp) {
        Row(Modifier.fillMaxWidth().navigationBarsPadding().height(58.dp)) {
            KHATA_TABS.forEach { t ->
                val (label, icon) = navItem(t)
                val on = t == tab
                val tint = if (on) Ember else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                Column(
                    Modifier.weight(1f).fillMaxHeight().clickable { onTab(t) },
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center
                ) {
                    Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(22.dp))
                    Text(label, fontSize = 10.sp, color = tint, fontWeight = if (on) FontWeight.Bold else FontWeight.Medium)
                }
            }
        }
    }
}

private fun navItem(t: Tab): Pair<String, ImageVector> = when (t) {
    Tab.PARTIES -> "Parties" to Icons.Outlined.People
    Tab.CASHBOOK -> "Cash" to Icons.Outlined.AccountBalanceWallet
    Tab.EXPENSE -> "Expense" to Icons.Outlined.ReceiptLong
    Tab.INCOME -> "Income" to Icons.Outlined.Payments
    else -> "Chat" to Icons.Outlined.ChatBubbleOutline
}

/** Glassy orange header: a warm gradient with a soft white sheen and two faint light circles. */
@Composable
internal fun GlassHeader(modifier: Modifier) {
    Canvas(modifier.background(Brush.linearGradient(listOf(Color(0xFFF46B42), Color(0xFFEC4C35), Color(0xFFE3342F))))) {
        val w = size.width
        val h = size.height
        drawRect(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.16f), Color.Transparent), endY = h * 0.7f))
        drawRect(Brush.linearGradient(
            0.30f to Color.Transparent, 0.45f to Color.White.copy(alpha = 0.18f), 0.60f to Color.Transparent,
            start = Offset(0f, 0f), end = Offset(w, h)
        ))
        drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.22f), Color.Transparent), center = Offset(w * 0.95f, 0f), radius = w * 0.35f),
            radius = w * 0.35f, center = Offset(w * 0.95f, 0f))
        drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.14f), Color.Transparent), center = Offset(0f, h), radius = w * 0.25f),
            radius = w * 0.25f, center = Offset(0f, h))
    }
}

/**
 * The header every khata screen shares: the glassy orange band, the business pill (or a back arrow and title on
 * inner pages), and the summary card hanging half over its bottom edge.
 */
@Composable
private fun KhataTop(
    title: String, subtitle: String, onMenu: () -> Unit, back: Boolean = false,
    business: String? = null, onBusiness: () -> Unit = {}, card: @Composable () -> Unit
) {
    Box(Modifier.fillMaxWidth()) {
        GlassHeader(Modifier.matchParentSize().padding(bottom = 34.dp))
        Column(Modifier.statusBarsPadding().padding(horizontal = 12.dp)) {
            if (business != null) BusinessPill(business, onMenu, onBusiness) else
            Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onMenu) {
                    Icon(if (back) Icons.AutoMirrored.Filled.ArrowBack else Icons.Filled.Menu, contentDescription = if (back) "Back" else "Menu", tint = Color.White)
                }
                if (title.isNotEmpty()) Column {
                    Text(title, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (subtitle.isNotEmpty()) Text(subtitle, color = Color.White.copy(alpha = 0.85f), fontSize = 11.sp)
                }
            }
            Spacer(Modifier.height(10.dp))
            card()
        }
    }
}

/** Compact frosted pill at the top: menu, then the open business with ▾ to switch or add one. */
@Composable
internal fun BusinessPill(name: String, onMenu: () -> Unit, onBusiness: () -> Unit) {
    Row(
        Modifier.padding(top = 10.dp).shadow(6.dp, CircleShape).clip(CircleShape).background(Color.White.copy(alpha = 0.92f))
            .border(1.dp, Color.White.copy(alpha = 0.75f), CircleShape).padding(end = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(32.dp).clip(CircleShape).clickable { onMenu() }, contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.Menu, contentDescription = "Menu", tint = Ember, modifier = Modifier.size(17.dp))
        }
        Row(Modifier.clickable { onBusiness() }.padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(name, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1C1917), maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 170.dp))
            Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Switch business", tint = Ember, modifier = Modifier.padding(start = 3.dp).size(16.dp))
        }
    }
}

/** White card with two figures side by side and a small link at the end. */
@Composable
private fun SumCard(a: Triple<String, String, Color>, b: Triple<String, String, Color>, link: String, onLink: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surface, shadowElevation = 6.dp,
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.9f))
    ) {
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).padding(vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
            listOf(a, b).forEachIndexed { i, (value, label, color) ->
                if (i > 0) VerticalDivider(Modifier.fillMaxHeight(), color = MaterialTheme.colorScheme.outlineVariant)
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(value, color = color, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                    Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            VerticalDivider(Modifier.fillMaxHeight(), color = MaterialTheme.colorScheme.outlineVariant)
            Text(
                link, color = Ember, fontSize = 12.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
                modifier = Modifier.width(72.dp).clickable { onLink() }.padding(vertical = 8.dp)
            )
        }
    }
}

/** A search box with an optional count shown as its hint. */
@Composable
private fun SearchBox(q: String, hint: String, onChange: (String) -> Unit) {
    val sub = MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        Modifier.padding(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 4.dp).fillMaxWidth().clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
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
private fun Initial(name: String) {
    Box(Modifier.size(28.dp).clip(CircleShape).background(MaterialTheme.colorScheme.background), contentAlignment = Alignment.Center) {
        Text(name.trim().take(1).uppercase(), color = Ember, fontWeight = FontWeight.ExtraBold, fontSize = 12.sp)
    }
}

/** Small filter chip (All / Receivable / Payable). */
@Composable
private fun SmallChip(label: String, on: Boolean, onClick: () -> Unit) {
    Text(
        label, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = if (on) Color.White else MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.clip(CircleShape).background(if (on) Ember else MaterialTheme.colorScheme.surface)
            .border(1.dp, if (on) Ember else MaterialTheme.colorScheme.outlineVariant, CircleShape)
            .clickable { onClick() }.padding(horizontal = 11.dp, vertical = 4.dp)
    )
}


private fun lastSeen(ms: Long): String {
    val day = if (sameDay(ms, System.currentTimeMillis())) "Today" else SimpleDateFormat("d MMM", Locale.US).format(Date(ms))
    return day + " · " + SimpleDateFormat("h:mm a", Locale.US).format(Date(ms))
}

// ---------------------------------------------------------------- Parties

@Composable
fun PartiesScreen(
    entries: List<Entry>, currency: String, onMenu: () -> Unit, business: String, onBusiness: () -> Unit,
    onOpen: (PersonAcct) -> Unit, onReport: () -> Unit, onAdd: () -> Unit
) {
    val accts = remember(entries) { personAccounts(entries) }
    var filter by rememberSaveable { mutableIntStateOf(0) }
    var q by rememberSaveable { mutableStateOf("") }
    val pay = accts.filter { it.balance < -0.005 }.sumOf { -it.balance }
    val rec = accts.filter { it.balance > 0.005 }.sumOf { it.balance }
    val shown = accts.filter {
        when (filter) { 1 -> it.balance > 0.005; 2 -> it.balance < -0.005; else -> true } && (q.isBlank() || it.name.contains(q.trim(), true))
    }.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
    Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = barSpace(56.dp))) {
            item {
                KhataTop("", "", onMenu, business = business, onBusiness = onBusiness) {
                    SumCard(Triple(money(currency, pay), "Payable", RedSpend), Triple(money(currency, rec), "Receivable", GreenIn), "Report ›", onReport)
                }
            }
            item { SearchBox(q, if (shown.size == 1) "1 party" else "${shown.size} parties") { q = it } }
            item {
                Row(Modifier.padding(start = 12.dp, top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("All", "Receivable", "Payable").forEachIndexed { i, t -> SmallChip(t, filter == i) { filter = i } }
                }
            }
            item {
                LedgerHead("Parties", "${shown.size} · A–Z",
                    listOf(LCol("Payable", fmt(pay), LKind.OUT), LCol("Receivable", fmt(rec), LKind.IN)), shown.isEmpty(), PartyColW)
            }
            items(shown, key = { it.name.lowercase() }) { p -> PartyRow(p, p === shown.last()) { onOpen(p) } }
            if (shown.isEmpty()) item {
                Text("No parties yet. Say \"Hamid ko 500 diye\" in the chat, or tap ADD PARTY.", color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 14.sp, modifier = Modifier.padding(24.dp))
            }
        }
        PlusButton(onAdd)
    }
}

/** Title line of a white sheet: a soft red-to-white strip, the title in deep red and a small count on the right. */
@Composable
private fun SheetTitle(title: String, count: String) = Tight {
    Row(
        Modifier.padding(start = 10.dp, end = 10.dp, top = 8.dp).fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp)).background(MaterialTheme.colorScheme.surface)
            .background(Brush.horizontalGradient(listOf(Ember.copy(alpha = 0.14f), Color.Transparent)))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = DeepRed, modifier = Modifier.weight(1f), maxLines = 1)
        Text(count, fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    HorizontalDivider(Modifier.padding(horizontal = 10.dp), color = MaterialTheme.colorScheme.outlineVariant)
}

/** Shape of a row inside a white sheet: the last row rounds the sheet's bottom corners. */
private fun sheetShape(last: Boolean) = if (last) RoundedCornerShape(bottomStart = 14.dp, bottomEnd = 14.dp) else RoundedCornerShape(0.dp)

/** One party as a ledger line: name and last entry, then what you owe (Payable) or will get (Receivable). */
@Composable
private fun PartyRow(p: PersonAcct, last: Boolean, onClick: () -> Unit) = Tight {
    val bal = p.balance
    Row(
        Modifier.padding(horizontal = 10.dp).fillMaxWidth().height(IntrinsicSize.Min).clip(sheetShape(last))
            .background(MaterialTheme.colorScheme.surface).clickable { onClick() }
    ) {
        Column(Modifier.weight(1f).padding(start = 10.dp, end = 4.dp, top = 5.dp, bottom = 5.dp), verticalArrangement = Arrangement.Center) {
            Text(p.name, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            p.history.lastOrNull()?.let { Text("Last " + lastSeen(it.first.date), fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1) }
        }
        listOf(LKind.OUT to (if (bal < -0.005) -bal else 0.0), LKind.IN to (if (bal > 0.005) bal else 0.0)).forEach { (k, v) ->
            VerticalDivider(Modifier.fillMaxHeight(), color = Faint)
            Box(
                Modifier.width(PartyColW).fillMaxHeight().background(if (k == LKind.OUT) RedSpend.copy(alpha = 0.025f) else Color.Transparent),
                contentAlignment = Alignment.Center
            ) {
                Text(signed(k, v), fontSize = 9.sp, fontWeight = FontWeight.Bold, color = AmountInk, maxLines = 1, softWrap = false)
            }
        }
    }
    if (!last) HorizontalDivider(Modifier.padding(horizontal = 10.dp), color = Faint)
}

// ---------------------------------------------------------------- Cash book and party khata

private val shortDay = SimpleDateFormat("d MMM", Locale.US)
private val monthFmt = SimpleDateFormat("MMM yyyy", Locale.US)
private val ColW = 72.dp

private fun title(e: Entry) = e.person.ifBlank { e.item.ifBlank { e.category.label } }
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
        items(months, key = { it.first }) { (k, ms) ->
            val on = k == current
            Text(
                monthFmt.format(Date(ms)), fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                color = if (on) Color.White else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.clip(CircleShape).background(if (on) Ember else MaterialTheme.colorScheme.surface)
                    .border(1.dp, if (on) Ember else MaterialTheme.colorScheme.outlineVariant, CircleShape)
                    .clickable { onPick(k) }.padding(horizontal = 11.dp, vertical = 4.dp)
            )
        }
    }
}

/** Faint line between columns. */
@Composable
private fun ColLine() = VerticalDivider(Modifier.fillMaxHeight(), color = Faint)

// ---------------------------------------------------------------- the ledger page (Cash book, party khata, Expense / Income)

internal enum class LKind { OUT, IN }

/** One amount column: its title, the month's total shown under it, and whether it is money out or in. */
internal class LCol(val label: String, val total: String, val kind: LKind)

/** One line: title, time and note, and one amount per column (0 = empty cell). */
internal class LRow(val key: String, val date: Long, val title: String, val note: String, val amounts: List<Double>, val onClick: () -> Unit)

private val clockFmt = SimpleDateFormat("h:mma", Locale.US)
private val dayLineFmt = SimpleDateFormat("EEE, d MMM", Locale.US)
/** Same light blue behind every day line. */
private val DayTint = Color(0x050284C7)
private val Faint = Color(0x14786C6C)
/** Colour of an entry's own amount: dark grey, so red / green stay for the totals. */
private val AmountInk = Color(0xFF3F3A36)

private fun colW(cols: List<LCol>) = if (cols.size == 1) 70.dp else 62.dp
/** Party columns are a little wider: "Receivable" and big totals must fit. */
private val PartyColW = 74.dp
private fun entryCount(n: Int) = if (n == 0) "No entries" else if (n == 1) "1 entry" else "$n entries"

/** Ledger text uses the font's own line height, not the theme's tall one, so lines stay compact. */
@Composable
private fun Tight(content: @Composable () -> Unit) =
    ProvideTextStyle(LocalTextStyle.current.copy(lineHeight = TextUnit.Unspecified), content)
private fun kindColor(k: LKind) = if (k == LKind.OUT) RedSpend else GreenIn
private fun cellTint(k: LKind, a: Float) = if (k == LKind.OUT) RedSpend.copy(alpha = a) else GreenIn.copy(alpha = a)
/** − for money out, + for money in; empty when there is nothing. */
private fun signed(k: LKind, v: Double) = if (v == 0.0) "" else (if (k == LKind.OUT) "−" else "+") + fmt(v)

/**
 * A white ledger sheet: the head line (month and entry count, then each column's name with its total), and for every
 * day a slim light-blue line with that day's totals, then one small
 * line per entry. Lines are faint grey; amounts sit in the middle of their column.
 */
internal fun LazyListScope.ledger(title: String, cols: List<LCol>, rows: List<LRow>, keyPrefix: String = "") {
    item(key = keyPrefix + "lh") { LedgerHead(title, entryCount(rows.size), cols, rows.isEmpty()) }
    rows.forEachIndexed { i, r ->
        if (i == 0 || !sameDay(rows[i - 1].date, r.date)) {
            val day = rows.filter { sameDay(it.date, r.date) }
            item(key = keyPrefix + "d" + r.key) { DayLine(r.date, cols, day) }
        }
        item(key = keyPrefix + r.key) { LedgerRow(r, cols, i == rows.lastIndex) }
    }
}

@Composable
private fun LedgerHead(title: String, count: String, cols: List<LCol>, empty: Boolean, w: Dp = colW(cols)) = Tight {
    val sub = MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        Modifier.padding(start = 10.dp, end = 10.dp, top = 8.dp).fillMaxWidth().height(IntrinsicSize.Min)
            .clip(RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp, bottomStart = if (empty) 14.dp else 0.dp, bottomEnd = if (empty) 14.dp else 0.dp))
            .background(MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.weight(1f).padding(start = 10.dp, top = 7.dp, bottom = 7.dp), verticalArrangement = Arrangement.Center) {
            Text(title, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
            Text(count, fontSize = 9.sp, color = sub)
        }
        cols.forEach { c ->
            VerticalDivider(Modifier.fillMaxHeight(), color = Faint)
            // Column name, with the month's total right under it.
            Column(
                Modifier.width(w).fillMaxHeight().background(cellTint(c.kind, 0.06f)).padding(horizontal = 3.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center
            ) {
                Text(c.label.uppercase(), fontSize = 8.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.4.sp, maxLines = 1, color = kindColor(c.kind))
                Text(c.total, fontSize = 10.sp, fontWeight = FontWeight.Black, maxLines = 1, softWrap = false, color = kindColor(c.kind))
            }
        }
    }
    if (!empty) HorizontalDivider(Modifier.padding(horizontal = 10.dp), color = MaterialTheme.colorScheme.outlineVariant)
}

/** The slim line above each day: the day's name and its totals in their columns. */
@Composable
private fun DayLine(date: Long, cols: List<LCol>, day: List<LRow>) = Tight {
    val w = colW(cols)
    val totals = cols.indices.map { k -> day.sumOf { it.amounts.getOrElse(k) { 0.0 } } }
    Row(
        Modifier.padding(horizontal = 10.dp).fillMaxWidth().height(IntrinsicSize.Min)
            .background(MaterialTheme.colorScheme.surface).background(DayTint)
    ) {
        Text(dayLineFmt.format(Date(date)), fontSize = 9.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f).padding(start = 10.dp, top = 5.dp, bottom = 4.dp))
        cols.forEachIndexed { k, c ->
            VerticalDivider(Modifier.fillMaxHeight(), color = Faint)
            Box(Modifier.width(w).fillMaxHeight(), contentAlignment = Alignment.Center) {
                Text(signed(c.kind, totals[k]), fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, color = kindColor(c.kind), maxLines = 1, softWrap = false)
            }
        }
    }
    HorizontalDivider(Modifier.padding(horizontal = 10.dp), color = Faint)
}

@Composable
private fun LedgerRow(r: LRow, cols: List<LCol>, last: Boolean) = Tight {
    val sub = MaterialTheme.colorScheme.onSurfaceVariant
    val w = colW(cols)
    Row(
        Modifier.padding(horizontal = 10.dp).fillMaxWidth().height(IntrinsicSize.Min).clip(sheetShape(last))
            .background(MaterialTheme.colorScheme.surface).clickable { r.onClick() }
    ) {
        Column(Modifier.weight(1f).padding(start = 10.dp, end = 4.dp, top = 5.dp, bottom = 5.dp), verticalArrangement = Arrangement.Center) {
            Text(r.title, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(fontSize = 8.sp)) { append(clockFmt.format(Date(r.date)).lowercase()) }
                    if (r.note.isNotBlank() && !r.note.equals(r.title, true)) append(" · " + r.note)
                },
                fontSize = 9.sp, color = sub, maxLines = 1, overflow = TextOverflow.Ellipsis
            )
        }
        cols.forEachIndexed { i, c ->
            VerticalDivider(Modifier.fillMaxHeight(), color = Faint)
            Box(
                Modifier.width(w).fillMaxHeight().background(if (c.kind == LKind.OUT) RedSpend.copy(alpha = 0.025f) else Color.Transparent),
                contentAlignment = Alignment.Center
            ) {
                Text(signed(c.kind, r.amounts.getOrElse(i) { 0.0 }), fontSize = 9.sp, fontWeight = FontWeight.Bold, color = AmountInk, maxLines = 1, softWrap = false)
            }
        }
    }
    if (!last) HorizontalDivider(Modifier.padding(horizontal = 10.dp), color = Faint)
}

/** The add button above the chat bar: a white card with a khata icon and "Entry". The form has Cash out / in. */
@Composable
private fun BoxScope.PlusButton(onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        Modifier.align(Alignment.BottomEnd).padding(end = 14.dp, bottom = (LocalBarSpace.current - 30.dp).coerceAtLeast(8.dp))
            .height(46.dp).shadow(6.dp, shape).clip(shape).background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape).clickable { onClick() }.padding(start = 10.dp, end = 14.dp),
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
        Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = (LocalBarSpace.current - 30.dp).coerceAtLeast(8.dp) + 62.dp)
            .size(40.dp).shadow(4.dp, CircleShape).clip(CircleShape).background(MaterialTheme.colorScheme.onSurface)
            .clickable { scope.launch { state.animateScrollToItem(0) } },
        contentAlignment = Alignment.Center
    ) { Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "Back to top", tint = MaterialTheme.colorScheme.surface) }
}

private fun matches(e: Entry, q: String) =
    listOf(e.person, e.item, e.note, e.category.label, fmt(e.amount)).any { it.contains(q.trim(), true) }

@Composable
fun CashBookScreen(
    entries: List<Entry>, currency: String, onMenu: () -> Unit, business: String, onBusiness: () -> Unit,
    onEdit: (Entry) -> Unit, onHistory: () -> Unit, onAdd: (income: Boolean) -> Unit
) {
    val newest = remember(entries) {
        entries.filter { cashIn(it) || cashOut(it) }.sortedWith(compareByDescending<Entry> { it.date }.thenByDescending { it.createdAt })
    }
    val hand = remember(newest) { newest.sumOf { if (cashIn(it)) it.amount else -it.amount } }
    // This month's net cash: in minus out.
    val nowKey = monthKey(System.currentTimeMillis())
    val thisMonth = newest.filter { monthKey(it.date) == nowKey }.sumOf { if (cashIn(it)) it.amount else -it.amount }
    val months = remember(newest) { monthsOf(newest) }
    var month by rememberSaveable(months.firstOrNull()?.first) { mutableIntStateOf(months.firstOrNull()?.first ?: 0) }
    var q by rememberSaveable { mutableStateOf("") }
    // A search looks through every month; otherwise only the picked month is listed.
    val list = if (q.isBlank()) newest.filter { inMonth(it.date, month) } else newest.filter { matches(it, q) }
    val state = rememberLazyListState()
    Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), state = state, contentPadding = PaddingValues(bottom = barSpace(56.dp))) {
            item {
                KhataTop("", "", onMenu, business = business, onBusiness = onBusiness) {
                    SumCard(
                        Triple(money(currency, hand), "Cash in hand", if (hand < 0) RedSpend else GreenIn),
                        Triple(money(currency, thisMonth), "This month", if (thisMonth < 0) RedSpend else GreenIn),
                        "History", onHistory
                    )
                }
            }
            item { SearchBox(q, "Search") { q = it } }
            if (q.isBlank() && months.isNotEmpty()) stickyHeader { MonthChips(months, month) { month = it } }
            val label = if (q.isNotBlank()) "Found" else monthLabel(months, month) ?: ""
            ledger(
                label,
                listOf(
                    LCol("Out", fmt(list.filter(::cashOut).sumOf { it.amount }), LKind.OUT),
                    LCol("In", fmt(list.filter(::cashIn).sumOf { it.amount }), LKind.IN)
                ),
                list.map { e ->
                    LRow(e.id, e.date, title(e), e.note, listOf(if (cashOut(e)) e.amount else 0.0, if (cashIn(e)) e.amount else 0.0)) { onEdit(e) }
                }
            )
        }
        UpButton(state)
        PlusButton { onAdd(false) }
    }
}

/** One party's khata: date and time, a Bal. tag, and You paid / You received columns, one month at a time. */
@Composable
fun PartyKhataScreen(
    name: String, entries: List<Entry>, currency: String, onBack: () -> Unit,
    onEdit: (Entry) -> Unit, onReport: () -> Unit, onAdd: (paid: Boolean) -> Unit
) {
    val p = remember(entries, name) { personAccounts(entries).firstOrNull { it.name.equals(name, true) } }
    val hist = remember(p) { p?.history?.asReversed() ?: emptyList() }
    val months = remember(hist) { monthsOf(hist.map { it.first }) }
    var month by rememberSaveable(name, months.firstOrNull()?.first) { mutableIntStateOf(months.firstOrNull()?.first ?: 0) }
    var q by rememberSaveable(name) { mutableStateOf("") }
    val list = if (q.isBlank()) hist.filter { inMonth(it.first.date, month) } else hist.filter { matches(it.first, q) }
    val bal = p?.balance ?: 0.0
    val state = rememberLazyListState()
    Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), state = state, contentPadding = PaddingValues(bottom = barSpace(56.dp))) {
            item {
                KhataTop(name, if (hist.size == 1) "1 entry" else "${hist.size} entries", onBack, back = true) {
                    SumCard(
                        Triple(money(currency, kotlin.math.abs(bal)), if (bal > 0.005) "Receivable" else if (bal < -0.005) "Payable" else "Settled",
                            if (bal > 0.005) GreenIn else if (bal < -0.005) RedSpend else MaterialTheme.colorScheme.onSurfaceVariant),
                        Triple(money(currency, p?.gave ?: 0.0), "You paid", RedSpend),
                        "Report ›", onReport
                    )
                }
            }
            item { SearchBox(q, "Search") { q = it } }
            if (q.isBlank() && months.size > 1) stickyHeader { MonthChips(months, month) { month = it } }
            val label = if (q.isNotBlank()) "Found" else if (month == ALL_MONTHS) name else monthLabel(months, month) ?: name
            ledger(
                label,
                listOf(
                    LCol("Paid", fmt(list.filter { personHistoryIsOutflow(it.first) }.sumOf { it.first.amount }), LKind.OUT),
                    LCol("Received", fmt(list.filter { !personHistoryIsOutflow(it.first) }.sumOf { it.first.amount }), LKind.IN)
                ),
                list.map { (e, _) ->
                    val out = personHistoryIsOutflow(e)
                    val note = e.item.takeIf { it != "Loans" && !it.equals(e.note, true) } ?: ""
                    LRow(e.id, e.date, e.note.ifBlank { if (out) "Cash given" else "Cash received" }, note,
                        listOf(if (out) e.amount else 0.0, if (!out) e.amount else 0.0)) { onEdit(e) }
                }
            )
        }
        UpButton(state)
        PlusButton { onAdd(true) }
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
    val list = if (q.isBlank()) newest.filter { inMonth(it.date, month) } else newest.filter { matches(it, q) }
    // Money that left you (or a purchase) goes in Out; everything else in In.
    fun out(e: Entry) = cashOut(e) || e.type == Type.PURCHASE
    val outT = list.filter(::out).sumOf { it.amount }
    val inT = list.filterNot(::out).sumOf { it.amount }
    val state = rememberLazyListState()
    Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), state = state, contentPadding = PaddingValues(bottom = barSpace(56.dp))) {
            item {
                KhataTop("All entries", if (entries.size == 1) "1 entry" else "${entries.size} entries", onMenu) {
                    SumCard(Triple(money(currency, outT), "Out", RedSpend), Triple(money(currency, inT), "In", GreenIn), "Add", onAdd)
                }
            }
            item {
                Row(Modifier.horizontalScroll(rememberScrollState()).padding(start = 14.dp, end = 14.dp, top = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    kinds.forEachIndexed { i, t -> SmallChip(t, filter == i) { filter = i } }
                }
            }
            item { SearchBox(q, "Search") { q = it } }
            if (q.isBlank() && months.isNotEmpty()) stickyHeader { MonthChips(months, month) { month = it } }
            val label = if (q.isNotBlank()) "Found" else monthLabel(months, month) ?: ""
            ledger(
                label, listOf(LCol("Out", fmt(outT), LKind.OUT), LCol("In", fmt(inT), LKind.IN)),
                list.map { e -> LRow(e.id, e.date, title(e), e.note, listOf(if (out(e)) e.amount else 0.0, if (!out(e)) e.amount else 0.0)) { onEdit(e) } }
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
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = barSpace(24.dp))) {
        item {
            KhataTop("Trash", "Deleted entries stay here for 30 days", onMenu) {
                SumCard(
                    Triple("${trash.size}", if (trash.size == 1) "Entry" else "Entries", MaterialTheme.colorScheme.onSurface),
                    Triple(money(currency, trash.sumOf { it.amount }), "Total", RedSpend),
                    "Restore all", onRestoreAll
                )
            }
        }
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

@Composable
fun MoneyScreen(
    income: Boolean, entries: List<Entry>, currency: String, onMenu: () -> Unit, business: String, onBusiness: () -> Unit,
    onEdit: (Entry) -> Unit, onReport: () -> Unit, onAdd: () -> Unit
) {
    val type = if (income) Type.AAMDANI else Type.KHARCHA
    val list = remember(entries, income) {
        entries.filter { it.type == type }.sortedWith(compareByDescending<Entry> { it.date }.thenByDescending { it.createdAt })
    }
    val color = if (income) GreenIn else RedSpend
    val start = cal(System.currentTimeMillis()).apply {
        set(java.util.Calendar.DAY_OF_MONTH, 1); set(java.util.Calendar.HOUR_OF_DAY, 0)
        set(java.util.Calendar.MINUTE, 0); set(java.util.Calendar.SECOND, 0); set(java.util.Calendar.MILLISECOND, 0)
    }
    val thisStart = start.timeInMillis
    val year = start.get(java.util.Calendar.YEAR)
    val yearStart = start.apply { set(java.util.Calendar.MONTH, java.util.Calendar.JANUARY) }.timeInMillis
    val thisMonth = list.filter { it.date >= thisStart }.sumOf { it.amount }
    val thisYear = list.filter { it.date >= yearStart }.sumOf { it.amount }
    // Month chips like the Cash book, with All first (the default here).
    val months = remember(list) { monthsOf(list) }
    var pick by rememberSaveable(income) { mutableIntStateOf(ALL_MONTHS) }
    val shown = list.filter { inMonth(it.date, pick) }
    val total = shown.sumOf { it.amount }
    // Expense by category; income by who paid (like the party list).
    val groups = (if (income) shown.groupBy { source(it).lowercase() }.map { (_, l) -> Group(source(l.first()), null, l) }
    else shown.groupBy { it.category }.map { (c, l) -> Group(c.label, c, l) }).sortedByDescending { it.total }
    val open = remember(income) { mutableStateListOf<String>() }
    Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = barSpace(56.dp))) {
            item {
                KhataTop("", "", onMenu, business = business, onBusiness = onBusiness) {
                    SumCard(Triple(money(currency, thisYear), "Year $year", color), Triple(money(currency, thisMonth), "This month", color), "Report ›", onReport)
                }
            }
            if (months.isNotEmpty()) item { MonthChips(months, pick) { pick = it } }
            if (groups.isNotEmpty()) item { SheetTitle(if (income) "Income" else "Expense", "Total " + fmt(total)) }
            groups.forEach { g ->
                val isOpen = g.name in open
                val lastGroup = g === groups.last()
                item(key = "g" + g.name) {
                    val share = if (total > 0) (g.total / total).toFloat() else 0f
                    Row(
                        Modifier.padding(horizontal = 10.dp).fillMaxWidth().height(IntrinsicSize.Min).clip(sheetShape(lastGroup && !isOpen))
                            .background(MaterialTheme.colorScheme.surface)
                            .clickable { if (isOpen) open.remove(g.name) else open.add(g.name) }.padding(start = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (g.cat != null) CatBubble(g.cat, 28.dp) else Initial(g.name)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f).padding(vertical = 7.dp)) {
                            Text(g.name, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (g.cat != null) {
                                Box(Modifier.padding(top = 4.dp).fillMaxWidth().height(4.dp).clip(CircleShape).background(MaterialTheme.colorScheme.outlineVariant)) {
                                    Box(Modifier.fillMaxWidth(share).fillMaxHeight().clip(CircleShape).background(catColor(g.cat)))
                                }
                            } else {
                                Text(lastSeen(g.list.maxOf { it.date }), fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Icon(
                            if (isOpen) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown, contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f), modifier = Modifier.padding(horizontal = 4.dp).size(20.dp)
                        )
                        // Total and share in the same column as the entries' amounts.
                        ColLine()
                        Column(
                            Modifier.width(ColW).fillMaxHeight(),
                            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center
                        ) {
                            Text(fmt(g.total), fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, color = if (g.cat == null) color else MaterialTheme.colorScheme.onSurface)
                            Text(if (g.cat != null) "${Math.round(share * 100)}%" else if (g.list.size == 1) "1 entry" else "${g.list.size} entries",
                                fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    if (!(lastGroup && !isOpen)) HorizontalDivider(Modifier.padding(horizontal = 10.dp), color = Faint)
                }
                // A group's own entries open right under it, on the same ledger page.
                if (isOpen) {
                    ledger(
                        g.name, listOf(LCol("Amount", fmt(g.total), if (income) LKind.IN else LKind.OUT)),
                        g.list.map { e -> LRow(e.id, e.date, title(e), e.note, listOf(e.amount)) { onEdit(e) } },
                        keyPrefix = "g${g.name}:"
                    )
                    item(key = "g${g.name}:end") { Spacer(Modifier.height(8.dp)) }
                }
            }
            if (list.isEmpty()) item {
                Text(if (income) "No income yet." else "No expenses yet.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp, modifier = Modifier.padding(24.dp))
            }
        }
        PlusButton(onAdd)
    }
}

/** A row on the Expense / Income page: a category, or for income the one who paid. */
private class Group(val name: String, val cat: Cat?, val list: List<Entry>) {
    val total = list.sumOf { it.amount }
}

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

/** Full-screen list of businesses: tick on the open one, ⋮ to rename or delete, and Add New Business. */
@Composable
fun BusinessPicker(
    list: List<Triple<String, String, Int>>, current: String, onBack: () -> Unit, onPick: (String) -> Unit,
    onAdd: (String) -> Unit, onRename: (String, String) -> Unit, onDelete: (String) -> Unit
) {
    var naming by remember { mutableStateOf<Pair<String?, String>?>(null) }   // (id or null for new, text)
    var deleting by remember { mutableStateOf<Pair<String, String>?>(null) }
    androidx.compose.ui.window.Dialog(onDismissRequest = onBack, properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column {
                Row(Modifier.fillMaxWidth().background(EmberBrush).padding(horizontal = 6.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White) }
                    Text("Select Business", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                }
                LazyColumn(Modifier.weight(1f).fillMaxWidth().padding(top = 10.dp)) {
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
                                Text(if (parties == 1) "1 party" else "$parties parties", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                    Modifier.navigationBarsPadding().padding(16.dp).fillMaxWidth().height(50.dp).clip(CircleShape).background(EmberBrush)
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
