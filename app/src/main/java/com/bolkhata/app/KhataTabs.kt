@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package com.bolkhata.app

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.coerceAtLeast
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

// Khata look: orange-red header, a white summary card over it, pill buttons above the chat bar, and four tabs
// (Parties, Cash book, Expense, Income) next to Home. Every number comes from the saved entries.

private val Ember = Color(0xFFE3342F)
private val EmberBrush = Brush.linearGradient(listOf(Color(0xFFF0572E), Color(0xFFE3342F)))
private val OutBrush = Brush.horizontalGradient(listOf(Color(0xFF8E1414), Color(0xFFD13A33)))
private val InBrush = Brush.horizontalGradient(listOf(Color(0xFF0F7A3A), Color(0xFF3FB46B)))

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
val KHATA_TABS = listOf(Tab.HOME, Tab.PARTIES, Tab.CASHBOOK, Tab.EXPENSE, Tab.INCOME)

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
    else -> "Home" to Icons.Outlined.Home
}

/** Orange-red header with the summary card hanging half over its bottom edge. */
@Composable
private fun KhataTop(title: String, subtitle: String, onMenu: () -> Unit, back: Boolean = false, card: @Composable () -> Unit) {
    Box(Modifier.fillMaxWidth()) {
        Box(Modifier.matchParentSize().padding(bottom = 36.dp).background(EmberBrush))
        Column(Modifier.statusBarsPadding().padding(horizontal = 14.dp)) {
            Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onMenu) {
                    Icon(if (back) Icons.AutoMirrored.Filled.ArrowBack else Icons.Filled.Menu, contentDescription = if (back) "Back" else "Menu", tint = Color.White)
                }
                Column {
                    Text(title, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (subtitle.isNotEmpty()) Text(subtitle, color = Color.White.copy(alpha = 0.85f), fontSize = 11.sp)
                }
            }
            Spacer(Modifier.height(14.dp))
            card()
        }
    }
}

/** White card with two figures side by side and a small link at the end. */
@Composable
private fun SumCard(a: Triple<String, String, Color>, b: Triple<String, String, Color>, link: String, onLink: () -> Unit) {
    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface, shadowElevation = 4.dp) {
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
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
        Modifier.padding(horizontal = 14.dp, vertical = 10.dp).fillMaxWidth().clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Filled.Search, contentDescription = null, tint = sub, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Box(Modifier.weight(1f)) {
            if (q.isEmpty()) Text(hint, color = sub, fontSize = 13.sp)
            BasicTextField(
                q, onChange, singleLine = true, modifier = Modifier.fillMaxWidth(),
                textStyle = TextStyle(fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface),
                cursorBrush = SolidColor(Ember)
            )
        }
    }
}

/** Round letter badge for a party or an income source. */
@Composable
private fun Initial(name: String) {
    Box(Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.background), contentAlignment = Alignment.Center) {
        Text(name.trim().take(1).uppercase(), color = Ember, fontWeight = FontWeight.Bold, fontSize = 15.sp)
    }
}

/** Small filter chip (All / Receivable / Payable). */
@Composable
private fun SmallChip(label: String, on: Boolean, onClick: () -> Unit) {
    Text(
        label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = if (on) Color.White else MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.clip(CircleShape).background(if (on) Ember else MaterialTheme.colorScheme.surface)
            .border(1.dp, if (on) Ember else MaterialTheme.colorScheme.outlineVariant, CircleShape)
            .clickable { onClick() }.padding(horizontal = 14.dp, vertical = 7.dp)
    )
}

/** One small gradient pill button. */
private class Pill(val label: String, val brush: Brush, val onClick: () -> Unit)

/** Compact pill buttons at the bottom right, just above the chat bar. */
@Composable
private fun BoxScope.Pills(vararg pills: Pill) {
    Row(
        Modifier.align(Alignment.BottomEnd).padding(end = 12.dp, bottom = (LocalBarSpace.current - 30.dp).coerceAtLeast(8.dp)),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        pills.forEach { p ->
            Box(
                Modifier.shadow(4.dp, CircleShape).clip(CircleShape).background(p.brush).clickable { p.onClick() }
                    .padding(horizontal = 18.dp, vertical = 9.dp)
            ) { Text(p.label, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.3.sp) }
        }
    }
}

private fun lastSeen(ms: Long): String {
    val day = if (sameDay(ms, System.currentTimeMillis())) "Today" else SimpleDateFormat("d MMM", Locale.US).format(Date(ms))
    return day + " · " + SimpleDateFormat("h:mm a", Locale.US).format(Date(ms))
}

// ---------------------------------------------------------------- Parties

@Composable
fun PartiesScreen(
    entries: List<Entry>, currency: String, onMenu: () -> Unit,
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
                KhataTop("Parties", if (accts.size == 1) "1 party" else "${accts.size} parties", onMenu) {
                    SumCard(Triple(money(currency, pay), "Payable", RedSpend), Triple(money(currency, rec), "Receivable", GreenIn), "Report ›", onReport)
                }
            }
            item {
                Row(Modifier.padding(start = 14.dp, top = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("All", "Receivable", "Payable").forEachIndexed { i, t -> SmallChip(t, filter == i) { filter = i } }
                }
            }
            item { SearchBox(q, if (shown.size == 1) "1 party" else "${shown.size} parties") { q = it } }
            items(shown, key = { it.name.lowercase() }) { p -> PartyRow(p, currency) { onOpen(p) } }
            if (shown.isEmpty()) item {
                Text("No parties yet. Say \"Hamid ko 500 diye\" in the chat, or tap ADD PARTY.", color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 14.sp, modifier = Modifier.padding(24.dp))
            }
        }
        Pills(Pill("+ ADD PARTY", EmberBrush, onAdd))
    }
}

@Composable
private fun PartyRow(p: PersonAcct, currency: String, onClick: () -> Unit) {
    val bal = p.balance
    val color = if (bal > 0.005) GreenIn else if (bal < -0.005) RedSpend else MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).clickable { onClick() }.padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Initial(p.name)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(p.name, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            p.history.lastOrNull()?.let { Text(lastSeen(it.first.date), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(money(currency, kotlin.math.abs(bal)), color = color, fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.padding(start = 8.dp))
            Text(if (bal > 0.005) "Receivable" else if (bal < -0.005) "Payable" else "Settled", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    HorizontalDivider(Modifier.padding(start = 66.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
}

// ---------------------------------------------------------------- Cash book and party khata (one register look)

private val dayFmt = SimpleDateFormat("EEE, dd MMM yy", Locale.US)
private val timeFmt = SimpleDateFormat("h:mm a", Locale.US)
private val shortDay = SimpleDateFormat("d MMM", Locale.US)
private val monthFmt = SimpleDateFormat("MMM yyyy", Locale.US)
private val OutTint = Color(0xFFE5484D).copy(alpha = 0.06f)
private val ColW = 88.dp

private fun title(e: Entry) = e.person.ifBlank { e.item.ifBlank { e.category.label } }
private fun monthKey(ms: Long): Int = cal(ms).let { it.get(Calendar.YEAR) * 12 + it.get(Calendar.MONTH) }

/** Months that have entries, newest first, as (key, a date in that month). [newest] must be sorted newest first. */
private fun monthsOf(newest: List<Entry>) = newest.distinctBy { monthKey(it.date) }.map { monthKey(it.date) to it.date }

/** Month chips; only one month is listed at a time so the page stays short. */
@Composable
private fun MonthChips(months: List<Pair<Int, Long>>, current: Int, onPick: (Int) -> Unit) {
    LazyRow(
        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        items(months, key = { it.first }) { (k, ms) ->
            val on = k == current
            Text(
                monthFmt.format(Date(ms)), fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                color = if (on) Color.White else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.clip(CircleShape).background(if (on) Ember else MaterialTheme.colorScheme.surface)
                    .border(1.dp, if (on) Ember else MaterialTheme.colorScheme.outlineVariant, CircleShape)
                    .clickable { onPick(k) }.padding(horizontal = 12.dp, vertical = 5.dp)
            )
        }
    }
}

/** Column titles: Entries | Out | In (or You paid | You received). */
@Composable
private fun RegHead(left: String, out: String, inn: String, outSub: String = "", inSub: String = "") {
    val sub = MaterialTheme.colorScheme.onSurfaceVariant
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).background(MaterialTheme.colorScheme.background), verticalAlignment = Alignment.CenterVertically) {
        Text(left, fontSize = 12.sp, color = sub, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f).padding(start = 14.dp))
        listOf(Triple(out, outSub, RedSpend), Triple(inn, inSub, GreenIn)).forEach { (t, s, c) ->
            ColLine()
            Column(Modifier.width(ColW).padding(end = 12.dp, top = 6.dp, bottom = 6.dp), horizontalAlignment = Alignment.End) {
                Text(t, fontSize = 11.sp, color = sub, fontWeight = FontWeight.SemiBold, maxLines = 1)
                if (s.isNotEmpty()) Text(s, fontSize = 12.sp, color = c, fontWeight = FontWeight.Bold, maxLines = 1)
            }
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

/** Faint line between the Entries, Out and In columns. */
@Composable
private fun ColLine() = VerticalDivider(Modifier.fillMaxHeight(), color = MaterialTheme.colorScheme.outlineVariant)

/** One entry: optional title, date or time, a Bal. tag, and the amount in the Out (tinted) or In column. */
@Composable
private fun RegRow(title: String?, whenText: String, bal: Double, out: Boolean, amount: Double, currency: String, onClick: () -> Unit) {
    val sub = MaterialTheme.colorScheme.onSurfaceVariant
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).background(MaterialTheme.colorScheme.surface).clickable { onClick() }) {
        Column(Modifier.weight(1f).padding(horizontal = 14.dp, vertical = 5.dp)) {
            if (title != null) Text(title, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(whenText, fontSize = 10.sp, color = sub, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                "Bal. " + money(currency, kotlin.math.abs(bal)), fontSize = 10.sp, color = sub,
                modifier = Modifier.padding(top = 2.dp).clip(RoundedCornerShape(4.dp))
                    .background((if (out) RedSpend else GreenIn).copy(alpha = 0.12f)).padding(horizontal = 6.dp, vertical = 1.dp)
            )
        }
        ColLine()
        Box(Modifier.width(ColW).fillMaxHeight().background(OutTint).padding(end = 12.dp), contentAlignment = Alignment.CenterEnd) {
            if (out) Text(fmt(amount), color = RedSpend, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        }
        ColLine()
        Box(Modifier.width(ColW).fillMaxHeight().padding(end = 12.dp), contentAlignment = Alignment.CenterEnd) {
            if (!out) Text(fmt(amount), color = GreenIn, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f))
}

/** A day's line in the cash book: tap to fold or open its entries. */
@Composable
private fun DayLine(day: String, list: List<Entry>, open: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(IntrinsicSize.Min).background(MaterialTheme.colorScheme.background).clickable { onClick() },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f).padding(start = 14.dp, top = 7.dp, bottom = 7.dp)) {
            Text(day.uppercase(), fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
            Text((if (list.size == 1) "1 entry" else "${list.size} entries") + if (open) "" else " · tap to open",
                fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        val o = list.filter(::cashOut).sumOf { it.amount }
        val i = list.filter(::cashIn).sumOf { it.amount }
        ColLine()
        Text(if (o > 0) fmt(o) else "", Modifier.width(ColW).padding(end = 12.dp), fontSize = 12.sp, color = RedSpend, fontWeight = FontWeight.Bold, textAlign = TextAlign.End)
        ColLine()
        Text(if (i > 0) fmt(i) else "", Modifier.width(ColW).padding(end = 12.dp), fontSize = 12.sp, color = GreenIn, fontWeight = FontWeight.Bold, textAlign = TextAlign.End)
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

/** One round + button above the chat bar; the form itself has the Cash out / Cash in switch. */
@Composable
private fun BoxScope.PlusButton(onClick: () -> Unit) {
    Box(
        Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = (LocalBarSpace.current - 30.dp).coerceAtLeast(8.dp))
            .size(52.dp).shadow(6.dp, CircleShape).clip(CircleShape).background(EmberBrush).clickable { onClick() },
        contentAlignment = Alignment.Center
    ) { Icon(Icons.Filled.Add, contentDescription = "Add entry", tint = Color.White, modifier = Modifier.size(28.dp)) }
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
    entries: List<Entry>, currency: String, onMenu: () -> Unit,
    onEdit: (Entry) -> Unit, onHistory: () -> Unit, onAdd: (income: Boolean) -> Unit
) {
    val newest = remember(entries) {
        entries.filter { cashIn(it) || cashOut(it) }.sortedWith(compareByDescending<Entry> { it.date }.thenByDescending { it.createdAt })
    }
    // Cash in hand after each entry, counted from the oldest.
    val bal = remember(newest) {
        var run = 0.0
        newest.asReversed().associate { e -> run += if (cashIn(e)) e.amount else -e.amount; e.id to run }
    }
    val hand = newest.firstOrNull()?.let { bal[it.id] } ?: 0.0
    val todayOut = newest.filter { sameDay(it.date, System.currentTimeMillis()) && cashOut(it) }.sumOf { it.amount }
    val months = remember(newest) { monthsOf(newest) }
    var month by rememberSaveable(months.firstOrNull()?.first) { mutableIntStateOf(months.firstOrNull()?.first ?: 0) }
    var q by rememberSaveable { mutableStateOf("") }
    val shut = remember { mutableStateListOf<String>() }
    // A search looks through every month; otherwise only the picked month is listed.
    val list = if (q.isBlank()) newest.filter { monthKey(it.date) == month } else newest.filter { matches(it, q) }
    val days = list.groupBy { dayFmt.format(Date(it.date)) }
    val state = rememberLazyListState()
    Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), state = state, contentPadding = PaddingValues(bottom = barSpace(56.dp))) {
            item {
                KhataTop("Cash Book", "Parties, expenses and income in one place", onMenu) {
                    SumCard(
                        Triple(money(currency, hand), "Cash in hand", if (hand < 0) RedSpend else GreenIn),
                        Triple(money(currency, todayOut), "Today out", RedSpend),
                        "History", onHistory
                    )
                }
            }
            item { SearchBox(q, "Search") { q = it } }
            if (q.isBlank() && months.isNotEmpty()) stickyHeader { MonthChips(months, month) { month = it } }
            item {
                val label = if (q.isNotBlank()) "Found" else months.firstOrNull { it.first == month }?.let { monthFmt.format(Date(it.second)) } ?: ""
                RegHead(
                    "$label · " + (if (list.size == 1) "1 entry" else "${list.size} entries"), "OUT", "IN",
                    fmt(list.filter(::cashOut).sumOf { it.amount }), fmt(list.filter(::cashIn).sumOf { it.amount })
                )
            }
            days.forEach { (day, l) ->
                val open = day !in shut
                item(key = "d$day") { DayLine(day, l, open) { if (open) shut.add(day) else shut.remove(day) } }
                if (open) items(l, key = { it.id }) { e ->
                    RegRow(title(e), timeFmt.format(Date(e.date)) + if (e.note.isNotBlank()) " · " + e.note else "", bal[e.id] ?: 0.0, cashOut(e), e.amount, currency) { onEdit(e) }
                }
            }
            if (list.isEmpty()) item {
                Text("No cash entries here.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp, modifier = Modifier.padding(24.dp))
            }
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
    val list = if (q.isBlank()) hist.filter { monthKey(it.first.date) == month } else hist.filter { matches(it.first, q) }
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
            item { RegHead("Entries", "YOU PAID", "YOU RECEIVED") }
            items(list, key = { it.first.id }) { (e, b) ->
                val note = e.note.ifBlank { e.item.takeIf { it != "Loans" } ?: "" }
                RegRow(
                    null, dayFmt.format(Date(e.date)) + " • " + timeFmt.format(Date(e.date)) + if (note.isNotBlank()) " · $note" else "",
                    b, personHistoryIsOutflow(e), e.amount, currency
                ) { onEdit(e) }
            }
            if (list.isEmpty()) item {
                Text("No entries with $name yet.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp, modifier = Modifier.padding(24.dp))
            }
        }
        UpButton(state)
        Pills(Pill("YOU PAID", OutBrush) { onAdd(true) }, Pill("YOU RECEIVED", InBrush) { onAdd(false) })
    }
}


// ---------------------------------------------------------------- Expense / Income

@Composable
fun MoneyScreen(
    income: Boolean, entries: List<Entry>, currency: String, onMenu: () -> Unit,
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
    val lastStart = start.apply { add(java.util.Calendar.MONTH, -1) }.timeInMillis
    val thisMonth = list.filter { it.date >= thisStart }.sumOf { it.amount }
    val lastMonth = list.filter { it.date in lastStart until thisStart }.sumOf { it.amount }
    val total = list.sumOf { it.amount }
    // Expense by category; income by who paid (like the party list).
    val groups = (if (income) list.groupBy { source(it).lowercase() }.map { (_, l) -> Group(source(l.first()), null, l) }
    else list.groupBy { it.category }.map { (c, l) -> Group(c.label, c, l) }).sortedByDescending { it.total }
    val open = remember(income) { mutableStateListOf<String>() }
    Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = barSpace(56.dp))) {
            item {
                KhataTop(if (income) "Income" else "Expense", if (income) "Where your money came from" else "Where your money went", onMenu) {
                    SumCard(Triple(money(currency, lastMonth), "Last month", color), Triple(money(currency, thisMonth), "This month", color), "Report ›", onReport)
                }
            }
            if (groups.isNotEmpty()) item { Heading(if (income) "By source" else "By category", "Total " + money(currency, total)) }
            groups.forEach { g ->
                val isOpen = g.name in open
                item(key = "g" + g.name) {
                    val share = if (total > 0) (g.total / total).toFloat() else 0f
                    Row(
                        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface)
                            .clickable { if (isOpen) open.remove(g.name) else open.add(g.name) }.padding(start = 14.dp, end = 8.dp, top = 9.dp, bottom = 9.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (g.cat != null) CatBubble(g.cat, 36.dp) else Initial(g.name)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(g.name, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (g.cat != null) {
                                Box(Modifier.padding(top = 5.dp).fillMaxWidth().height(6.dp).clip(CircleShape).background(MaterialTheme.colorScheme.outlineVariant)) {
                                    Box(Modifier.fillMaxWidth(share).fillMaxHeight().clip(CircleShape).background(catColor(g.cat)))
                                }
                            } else {
                                Text(lastSeen(g.list.maxOf { it.date }), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Column(Modifier.padding(start = 12.dp), horizontalAlignment = Alignment.End) {
                            Text(fmt(g.total), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = if (g.cat == null) color else MaterialTheme.colorScheme.onSurface)
                            Text(if (g.cat != null) "${Math.round(share * 100)}%" else if (g.list.size == 1) "1 entry" else "${g.list.size} entries",
                                fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Icon(
                            if (isOpen) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown, contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f), modifier = Modifier.padding(start = 4.dp).size(20.dp)
                        )
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                }
                // A group's own entries open right under it.
                if (isOpen) items(g.list, key = { it.id }) { e ->
                    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).background(MaterialTheme.colorScheme.surface).clickable { onEdit(e) }) {
                        // Date and time in the left space, under the category icon.
                        Column(Modifier.width(62.dp).padding(start = 14.dp, top = 5.dp, bottom = 5.dp)) {
                            Text(shortDay.format(Date(e.date)), fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                            Text(timeFmt.format(Date(e.date)), fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                        }
                        Column(Modifier.weight(1f).padding(end = 8.dp, top = 5.dp, bottom = 5.dp)) {
                            Text(title(e), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (e.note.isNotBlank()) Text(e.note, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        ColLine()
                        Box(
                            Modifier.width(ColW).fillMaxHeight().background(if (income) GreenIn.copy(alpha = 0.06f) else OutTint).padding(end = 12.dp),
                            contentAlignment = Alignment.CenterEnd
                        ) { Text(fmt(e.amount), color = color, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1) }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f))
                }
            }
            if (list.isEmpty()) item {
                Text(if (income) "No income yet." else "No expenses yet.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp, modifier = Modifier.padding(24.dp))
            }
        }
        Pills(Pill(if (income) "+ ADD INCOME" else "+ ADD EXPENSE", if (income) InBrush else OutBrush, onAdd))
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

@Composable
private fun Heading(left: String, right: String) {
    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 6.dp)) {
        Text(left, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(right, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** A blank Cash in / Cash out entry for the form; the category is guessed from what is typed. */
internal fun cashTemplate(income: Boolean): Entry =
    template(if (income) Type.AAMDANI else Type.KHARCHA, "").copy(category = if (income) Cat.TANKHWAH else Cat.OTHER, item = "")
