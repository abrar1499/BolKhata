@file:OptIn(ExperimentalMaterial3Api::class)

package com.bolkhata.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
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
import java.util.Date
import java.util.Locale

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
                    Text(label, fontSize = 11.sp, color = tint, fontWeight = if (on) FontWeight.Bold else FontWeight.Medium)
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
private fun KhataTop(title: String, subtitle: String, onMenu: () -> Unit, card: @Composable () -> Unit) {
    Box(Modifier.fillMaxWidth()) {
        Box(Modifier.matchParentSize().padding(bottom = 36.dp).background(EmberBrush))
        Column(Modifier.statusBarsPadding().padding(horizontal = 14.dp)) {
            Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onMenu) { Icon(Icons.Filled.Menu, contentDescription = "Menu", tint = Color.White) }
                Column {
                    Text(title, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    if (subtitle.isNotEmpty()) Text(subtitle, color = Color.White.copy(alpha = 0.85f), fontSize = 12.sp)
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
                    Text(value, color = color, fontSize = 19.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                    Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            VerticalDivider(Modifier.fillMaxHeight(), color = MaterialTheme.colorScheme.outlineVariant)
            Text(
                link, color = Ember, fontSize = 13.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
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
            if (q.isEmpty()) Text(hint, color = sub, fontSize = 14.sp)
            BasicTextField(
                q, onChange, singleLine = true, modifier = Modifier.fillMaxWidth(),
                textStyle = TextStyle(fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface),
                cursorBrush = SolidColor(Ember)
            )
        }
    }
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
            ) { Text(p.label, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.3.sp) }
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
                    listOf("All", "Receivable", "Payable").forEachIndexed { i, t ->
                        FilterChip(selected = filter == i, onClick = { filter = i }, label = { Text(t, fontSize = 13.sp) }, shape = CircleShape)
                    }
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
        Box(Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.background), contentAlignment = Alignment.Center) {
            Text(p.name.take(1).uppercase(), color = Ember, fontWeight = FontWeight.Bold, fontSize = 17.sp)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(p.name, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            p.history.lastOrNull()?.let { Text(lastSeen(it.first.date), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(money(currency, kotlin.math.abs(bal)), color = color, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Text(if (bal > 0.005) "Receivable" else if (bal < -0.005) "Payable" else "Settled", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    HorizontalDivider(Modifier.padding(start = 66.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
}

// ---------------------------------------------------------------- Cash book

private val dayFmt = SimpleDateFormat("EEE, dd MMM yyyy", Locale.US)
private val timeFmt = SimpleDateFormat("h:mm a", Locale.US)

private fun title(e: Entry) = e.person.ifBlank { e.item.ifBlank { e.category.label } }

@Composable
fun CashBookScreen(
    entries: List<Entry>, currency: String, onMenu: () -> Unit,
    onEdit: (Entry) -> Unit, onHistory: () -> Unit, onAdd: (income: Boolean) -> Unit
) {
    val cash = remember(entries) {
        entries.filter { cashIn(it) || cashOut(it) }.sortedWith(compareByDescending<Entry> { it.date }.thenByDescending { it.createdAt })
    }
    var q by rememberSaveable { mutableStateOf("") }
    fun net(l: List<Entry>) = l.filter(::cashIn).sumOf { it.amount } - l.filter(::cashOut).sumOf { it.amount }
    val hand = net(cash)
    val today = net(cash.filter { sameDay(it.date, System.currentTimeMillis()) })
    val shown = if (q.isBlank()) cash else cash.filter { e ->
        listOf(e.person, e.item, e.note, e.category.label, fmt(e.amount)).any { it.contains(q.trim(), true) }
    }
    val days = shown.groupBy { dayFmt.format(Date(it.date)) }
    Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = barSpace(56.dp))) {
            item {
                KhataTop("Cash Book", "Parties, expenses and income in one place", onMenu) {
                    SumCard(
                        Triple(money(currency, hand), "Cash in hand", if (hand < 0) RedSpend else GreenIn),
                        Triple(money(currency, today), "Today balance", if (today < 0) RedSpend else GreenIn),
                        "History", onHistory
                    )
                }
            }
            item { SearchBox(q, "Search") { q = it } }
            days.forEach { (day, list) ->
                item(key = "d$day") {
                    Row(
                        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(day.uppercase(), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Text(if (list.size == 1) "1 entry" else "${list.size} entries", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Total("Out", list.filter(::cashOut).sumOf { it.amount }, RedSpend)
                        Total("In", list.filter(::cashIn).sumOf { it.amount }, GreenIn)
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
                items(list, key = { it.id }) { e -> CashRow(e) { onEdit(e) } }
                item(key = "s$day") { Spacer(Modifier.height(8.dp)) }
            }
            if (shown.isEmpty()) item {
                Text("No cash entries yet.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp, modifier = Modifier.padding(24.dp))
            }
        }
        Pills(Pill("CASH OUT", OutBrush) { onAdd(false) }, Pill("CASH IN", InBrush) { onAdd(true) })
    }
}

@Composable
private fun Total(label: String, v: Double, color: Color) {
    Column(Modifier.width(84.dp), horizontalAlignment = Alignment.End) {
        Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(fmt(v), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = color)
    }
}

@Composable
private fun CashRow(e: Entry, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).clickable { onClick() }.padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title(e), fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                (timeFmt.format(Date(e.date)) + if (e.note.isNotBlank()) " · " + e.note else ""),
                fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis
            )
        }
        Text(if (cashOut(e)) fmt(e.amount) else "", Modifier.width(84.dp), color = RedSpend, fontWeight = FontWeight.Bold, fontSize = 14.sp, textAlign = TextAlign.End)
        Text(if (cashIn(e)) fmt(e.amount) else "", Modifier.width(84.dp), color = GreenIn, fontWeight = FontWeight.Bold, fontSize = 14.sp, textAlign = TextAlign.End)
    }
    HorizontalDivider(Modifier.padding(horizontal = 14.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
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
    val byCat = list.groupBy { it.category }.map { (c, l) -> c to l.sumOf { it.amount } }.sortedByDescending { it.second }
    Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = barSpace(56.dp))) {
            item {
                KhataTop(if (income) "Income" else "Expense", if (income) "Where your money came from" else "Where your money went", onMenu) {
                    SumCard(Triple(money(currency, lastMonth), "Last month", color), Triple(money(currency, thisMonth), "This month", color), "Report ›", onReport)
                }
            }
            if (byCat.isNotEmpty()) item { Heading("By category", "Total " + money(currency, total)) }
            items(byCat, key = { "c" + it.first.name }) { (c, v) ->
                val share = if (total > 0) (v / total).toFloat() else 0f
                Row(
                    Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(horizontal = 14.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CatBubble(c, 36.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(c.label, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        Box(Modifier.padding(top = 5.dp).fillMaxWidth().height(6.dp).clip(CircleShape).background(MaterialTheme.colorScheme.outlineVariant)) {
                            Box(Modifier.fillMaxWidth(share).fillMaxHeight().clip(CircleShape).background(catColor(c)))
                        }
                    }
                    Column(Modifier.padding(start = 12.dp), horizontalAlignment = Alignment.End) {
                        Text(fmt(v), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Text("${Math.round(share * 100)}%", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            if (list.isNotEmpty()) item { Heading("Entries", "Newest first") }
            items(list, key = { it.id }) { e ->
                Row(
                    Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).clickable { onEdit(e) }.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(title(e), fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(lastSeen(e.date) + if (e.note.isNotBlank()) " · " + e.note else "", fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Text(fmt(e.amount), color = color, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
                HorizontalDivider(Modifier.padding(horizontal = 14.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
            }
            if (list.isEmpty()) item {
                Text(if (income) "No income yet." else "No expenses yet.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp, modifier = Modifier.padding(24.dp))
            }
        }
        Pills(Pill(if (income) "+ ADD INCOME" else "+ ADD EXPENSE", if (income) InBrush else OutBrush, onAdd))
    }
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
