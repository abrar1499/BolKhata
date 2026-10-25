package com.bolkhata.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToLong

// ======================= HOME =======================

@Composable
fun HomeScreen(
    name: String,
    entries: List<Entry>,
    currency: String,
    newVersion: UpdateInfo?,
    progress: Int,
    onUpdate: () -> Unit,
    onAdd: () -> Unit,
    onMenu: () -> Unit,
    onSearch: () -> Unit,
    onEdit: (Entry) -> Unit,
    onPeople: (Boolean) -> Unit,
    onReports: () -> Unit,
    onSeeAll: () -> Unit,
    chatBadge: Int = 0,
    onTab: (Tab) -> Unit = {}
) {
    var open by remember { mutableStateOf(setOf("today")) }
    fun toggle(k: String) {
        open = if (k in open) open - k else open + k
    }

    val now = Calendar.getInstance()
    val nowMs = System.currentTimeMillis()
    fun inMonth(ms: Long) = cal(ms).let { it.get(Calendar.YEAR) == now.get(Calendar.YEAR) && it.get(Calendar.MONTH) == now.get(Calendar.MONTH) }

    val monthSpent = entries.filter { it.isCashOutFlow() && inMonth(it.date) }.sumOf { it.amount }
    val monthIncome = entries.filter { it.isCashInFlow() && inMonth(it.date) }.sumOf { it.amount }
    // Shop and business parts appear only once sales or purchases are used.
    val trading = entries.any { isTrade(it.type) }
    val todayEntries = entries.filter { sameDay(it.date, nowMs) }
    val todaySales = todayEntries.filter { it.type == Type.SALE }.sumOf { it.amount }
    val todayBought = todayEntries.filter { it.type == Type.PURCHASE }.sumOf { it.amount }
    val todayProfit = profitBreakdown(entries, todayEntries).netProfit
    val bal = balances(entries)
    val receive = bal.values.filter { it > 0 }.sum()
    val pay = -bal.values.filter { it < 0 }.sum()
    val cashInHand = entries.filter { it.isCashInFlow() }.sumOf { it.amount } - entries.filter { it.isCashOutFlow() }.sumOf { it.amount }
    val hour = now.get(Calendar.HOUR_OF_DAY)
    val greeting = when {
        hour < 12 -> "Good morning"
        hour < 17 -> "Good afternoon"
        else -> "Good evening"
    }
    val monthName = SimpleDateFormat("MMMM", Locale.US).format(now.time)

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            Box(Modifier.fillMaxWidth()) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(214.dp)
                        .clip(RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp))
                ) { HeroBackground(Modifier.matchParentSize()) }
                Column(Modifier.padding(horizontal = 16.dp).padding(top = 14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onMenu) { Icon(Icons.Filled.Menu, contentDescription = "Menu", tint = LocalHeader.current.on) }
                        Spacer(Modifier.width(4.dp))
                        Box(
                            Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(LocalHeader.current.chip),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(name.trim().take(1).uppercase().ifEmpty { "🙂" }, color = LocalHeader.current.on, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(if (name.isBlank()) "Hello!" else "Hello, ${name.trim()}", color = LocalHeader.current.on, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            Text(greeting, color = LocalHeader.current.on.copy(alpha = 0.8f), fontSize = 13.sp)
                        }
                        IconButton(
                            onClick = onSearch,
                            modifier = Modifier.clip(CircleShape).background(LocalHeader.current.chip)
                        ) { Icon(Icons.Filled.Search, contentDescription = "Search", tint = LocalHeader.current.on) }
                        Spacer(Modifier.width(8.dp))
                        IconButton(
                            onClick = onAdd,
                            modifier = Modifier.clip(CircleShape).background(LocalHeader.current.chip)
                        ) { Icon(Icons.Filled.Add, contentDescription = "Add manually", tint = LocalHeader.current.on) }
                    }
                    Spacer(Modifier.height(12.dp))
                    TabSwitcher(Tab.HOME, trading, chatBadge, onTab)
                    Spacer(Modifier.height(14.dp))
                    Panel(Modifier.fillMaxWidth(), onClick = onReports, padding = 12.dp) {
                        Text("In $monthName", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            // Money going out points up, money coming in points down.
                            MainSummaryTile(
                                "Expense", moneyA(currency, monthSpent), Icons.Filled.ArrowUpward, RedSpend,
                                Modifier.weight(1f)
                            ) { onReports() }
                            MainSummaryTile(
                                "Income", moneyA(currency, monthIncome), Icons.Filled.ArrowDownward, GreenIn,
                                Modifier.weight(1f)
                            ) { onReports() }
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            MiniTile(
                                "To pay", moneyA(currency, pay), Icons.Filled.Remove, OrangeOwe, Modifier.weight(1f),
                                valueSize = 12, compact = true
                            ) { onPeople(false) }
                            MiniTile(
                                "To receive", moneyA(currency, receive), Icons.Filled.Add, GreenIn, Modifier.weight(1f),
                                valueSize = 12, compact = true
                            ) { onPeople(true) }
                            MiniTile(
                                "Cash in hand", moneyA(currency, cashInHand), Icons.Filled.AccountBalanceWallet,
                                MaterialTheme.colorScheme.onSurfaceVariant, Modifier.weight(1f),
                                valueSize = 12, valueColor = if (cashInHand >= 0) Color.Unspecified else RedSpend,
                                compact = true
                            ) { onSeeAll() }
                        }
                    }
                }
            }
        }

        if (trading) {
            item {
                Panel(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = 12.dp),
                    onClick = { onTab(Tab.STOCK) }, padding = 12.dp
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        BizFigure("↓ Sales", fmt(todaySales), GreenIn, Modifier.weight(1f))
                        BizFigure("↑ Bought", fmt(todayBought), RedSpend, Modifier.weight(1f))
                        BizFigure(
                            "Profit", (if (todayProfit > 0) "+" else "") + fmt(todayProfit),
                            MaterialTheme.colorScheme.onSurfaceVariant, Modifier.weight(1f),
                            valueColor = if (todayProfit >= 0) GreenIn else RedSpend
                        )
                        Text("Stock ›", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }
        }

        if (newVersion != null) {
            item {
                Panel(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("New version available", fontWeight = FontWeight.SemiBold)
                            Text(
                                if (progress >= 0) "Downloading… $progress%" else "Version ${newVersion.version} · your data stays safe",
                                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Button(onClick = onUpdate, enabled = progress < 0) { Text("Update") }
                    }
                }
            }
        }

        item { Spacer(Modifier.height(10.dp)) }

        val sortedAll = entries.sortedWith(compareByDescending<Entry> { it.date }.thenByDescending { it.createdAt })
        val todayList = sortedAll.filter { sameDay(it.date, nowMs) }
        val dayGroups = sortedAll.filter { !sameDay(it.date, nowMs) && inMonth(it.date) }.groupBy { dayKey(it.date) }
        val monthGroups = sortedAll.filter { !inMonth(it.date) }.groupBy { monthKey(it.date) }

        if (entries.isEmpty()) {
            item {
                Text(
                    "No entries yet. Tap the green mic and speak, or type below.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                )
            }
        }

        if (todayList.isNotEmpty()) {
            item {
                Box(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                    DailyCashHeader("Today", todayList.size, "today" in open, dailyCashSummary(entries, todayList), currency) { toggle("today") }
                }
            }
            if ("today" in open) {
                items(todayList, key = { it.id }) { e ->
                    Box(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) { TxRow(e, currency) { onEdit(e) } }
                }
            }
        }

        dayGroups.forEach { (key, list) ->
            item(key = "dh-$key") {
                Box(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                    DailyCashHeader(dayLabel(list.first().date), list.size, key in open, dailyCashSummary(entries, list), currency) { toggle(key) }
                }
            }
            if (key in open) {
                items(list, key = { it.id }) { e ->
                    Box(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) { TxRow(e, currency) { onEdit(e) } }
                }
            }
        }

        monthGroups.forEach { (key, list) ->
            item(key = "mh-$key") {
                Box(Modifier.padding(horizontal = 16.dp)) {
                    SectionHeader(
                        SimpleDateFormat("MMMM yyyy", Locale.US).format(Date(list.first().date)),
                        list.size, key in open, spentLabel(currency, list)
                    ) { toggle(key) }
                }
            }
            if (key in open) {
                list.groupBy { dayKey(it.date) }.forEach { (day, dayList) ->
                    item(key = "md-$key-$day") {
                        Box(Modifier.padding(horizontal = 16.dp, vertical = 3.dp)) {
                            DailyCashHeader(
                                dayLabel(dayList.first().date),
                                dayList.size,
                                true,
                                dailyCashSummary(entries, dayList),
                                currency,
                                onToggle = null
                            )
                        }
                    }
                    items(dayList, key = { it.id }) { e ->
                        Box(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) { TxRow(e, currency) { onEdit(e) } }
                    }
                }
            }
        }
    }
}

/** Overview | Stock | Chat switch shown at the top of the main screens. Stock only appears for shops. */
@Composable
fun TabSwitcher(current: Tab, showStock: Boolean, chatBadge: Int, onTab: (Tab) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(CircleShape)
            .background(LocalHeader.current.chip)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        TabSegment("Overview", current == Tab.HOME, 0, Modifier.weight(1f)) { onTab(Tab.HOME) }
        if (showStock) TabSegment("Stock", current == Tab.STOCK, 0, Modifier.weight(1f)) { onTab(Tab.STOCK) }
        TabSegment("Chat", current == Tab.CHAT, chatBadge, Modifier.weight(1f)) { onTab(Tab.CHAT) }
    }
}

@Composable
private fun TabSegment(label: String, selected: Boolean, badge: Int, modifier: Modifier, onClick: () -> Unit) {
    Row(
        modifier
            .height(40.dp)
            .clip(CircleShape)
            .background(if (selected) MaterialTheme.colorScheme.surface else Color.Transparent)
            .clickable { onClick() },
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            color = if (selected) MaterialTheme.colorScheme.primary else LocalHeader.current.on,
            fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.SemiBold,
            fontSize = 14.sp
        )
        if (badge > 0) {
            Spacer(Modifier.width(6.dp))
            Box(
                Modifier.clip(CircleShape).background(OrangeOwe).padding(horizontal = 7.dp, vertical = 1.dp),
                contentAlignment = Alignment.Center
            ) { Text(badge.toString(), color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
        }
    }
}

@Composable
private fun BizFigure(label: String, value: String, labelColor: Color, modifier: Modifier, valueColor: Color = Color.Unspecified) {
    Column(modifier) {
        Text(label, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = labelColor, maxLines = 1)
        Text(value, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = valueColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Small "↑ 214" / "↓ 50" pill used on day headers. */
@Composable
private fun FlowChip(text: String, color: Color) {
    Box(
        Modifier
            .clip(CircleShape)
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 9.dp, vertical = 4.dp)
    ) { Text(text, color = color, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1) }
}

private fun dayKey(ms: Long): String = cal(ms).let { "d" + it.get(Calendar.YEAR) + "-" + it.get(Calendar.DAY_OF_YEAR) }
private fun monthKey(ms: Long): String = cal(ms).let { "m" + it.get(Calendar.YEAR) + "-" + it.get(Calendar.MONTH) }
private fun spentLabel(currency: String, list: List<Entry>): String? {
    val spent = list.filter { it.type == Type.KHARCHA }.sumOf { it.amount }
    return if (spent > 0) money(currency, spent) else null
}

private data class DailyCashSummary(
    val cashIn: Double,
    val cashOut: Double,
    val cashInHand: Double
)

private fun localDayStart(ms: Long): Long = cal(ms).apply {
    set(Calendar.HOUR_OF_DAY, 0)
    set(Calendar.MINUTE, 0)
    set(Calendar.SECOND, 0)
    set(Calendar.MILLISECOND, 0)
}.timeInMillis

/** Running cash balance through the selected local calendar day; only recorded cash flows are counted. */
private fun dailyCashSummary(allEntries: List<Entry>, dayEntries: List<Entry>): DailyCashSummary {
    val dayStart = localDayStart(dayEntries.firstOrNull()?.date ?: System.currentTimeMillis())
    val cashIn = dayEntries.filter { it.isCashInFlow() }.sumOf { it.amount }
    val cashOut = dayEntries.filter { it.isCashOutFlow() }.sumOf { it.amount }
    val throughDay = allEntries.filter { localDayStart(it.date) <= dayStart }
    val totalIn = throughDay.filter { it.isCashInFlow() }.sumOf { it.amount }
    val totalOut = throughDay.filter { it.isCashOutFlow() }.sumOf { it.amount }
    return DailyCashSummary(cashIn, cashOut, totalIn - totalOut)
}

@Composable
private fun DailyCashHeader(
    title: String,
    count: Int,
    expanded: Boolean,
    summary: DailyCashSummary,
    currency: String,
    onToggle: (() -> Unit)?
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surface)
            .then(if (onToggle != null) Modifier.clickable { onToggle() } else Modifier)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1)
        Spacer(Modifier.width(8.dp))
        Box(
            Modifier
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer)
                .padding(horizontal = 7.dp, vertical = 3.dp)
        ) {
            Text(count.toString(), fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.weight(1f))
        // Money out points up (red), money in points down (green). Only what happened that day is shown.
        if (summary.cashOut > 0) FlowChip("↑ " + fmt(summary.cashOut), RedSpend)
        if (summary.cashOut > 0 && summary.cashIn > 0) Spacer(Modifier.width(6.dp))
        if (summary.cashIn > 0) FlowChip("↓ " + fmt(summary.cashIn), GreenIn)
        if (onToggle != null) {
            Spacer(Modifier.width(6.dp))
            Text(
                if (expanded) "⌃" else "⌄",
                fontSize = 18.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun DailyCashMetric(
    label: String,
    amount: Double,
    currency: String,
    glyph: ImageVector,
    color: Color,
    modifier: Modifier,
    valueColor: Color = color
) {
    Row(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(color.copy(alpha = 0.075f))
            .padding(horizontal = 7.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                label,
                fontSize = 10.sp,
                lineHeight = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                moneyA(currency, amount),
                fontSize = 12.sp,
                lineHeight = 14.sp,
                fontWeight = FontWeight.Bold,
                color = valueColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.width(3.dp))
        Box(
            Modifier.size(23.dp).clip(CircleShape).background(color.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(glyph, contentDescription = null, tint = color, modifier = Modifier.size(15.dp))
        }
    }
}


@Composable
private fun MainSummaryTile(
    label: String,
    value: AnnotatedString,
    glyph: ImageVector,
    color: Color,
    modifier: Modifier,
    onClick: () -> Unit
) {
    Row(
        modifier
            .clip(RoundedCornerShape(18.dp))
            .background(color.copy(alpha = 0.09f))
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(38.dp).clip(CircleShape).background(color.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(glyph, contentDescription = null, tint = color, modifier = Modifier.size(24.dp))
        }
        Spacer(Modifier.width(9.dp))
        Column(Modifier.weight(1f)) {
            Text(label, fontSize = 14.sp, lineHeight = 17.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                value,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 23.sp,
                lineHeight = 28.sp,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun MiniTile(
    label: String,
    value: AnnotatedString,
    glyph: ImageVector,
    color: Color,
    modifier: Modifier,
    valueSize: Int = 15,
    valueColor: Color = Color.Unspecified,
    tinted: Boolean = false,
    compact: Boolean = false,
    onClick: () -> Unit
) {
    Row(
        modifier
            .clip(RoundedCornerShape(if (compact) 13.dp else 14.dp))
            .background(
                if (tinted) color.copy(alpha = 0.12f)
                else color.copy(alpha = if (compact) 0.07f else 0.10f)
            )
            .clickable { onClick() }
            .padding(
                start = if (compact) 4.dp else 12.dp,
                end = if (compact) 4.dp else 10.dp,
                top = if (compact) 8.dp else 8.dp,
                bottom = if (compact) 8.dp else 8.dp
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                label,
                fontSize = if (compact) 9.sp else 11.sp,
                lineHeight = if (compact) 11.sp else 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                value,
                fontWeight = FontWeight.Bold,
                fontSize = valueSize.sp,
                lineHeight = (valueSize + 3).sp,
                color = valueColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.width(if (compact) 2.dp else 6.dp))
        Box(
            Modifier.size(if (compact) 16.dp else 28.dp)
                .clip(CircleShape)
                .background(color.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(glyph, contentDescription = null, tint = color, modifier = Modifier.size(if (compact) 11.dp else 16.dp))
        }
    }
}

@Composable
fun Avatar(name: String) {
    Box(
        Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center
    ) {
        Text(name.take(1).uppercase(), color = MaterialTheme.colorScheme.onPrimaryContainer, fontWeight = FontWeight.Bold)
    }
}

// ======================= TRANSACTIONS =======================

enum class TxFilter(val label: String) { ALL("All"), EXPENSE("Cash Out"), INCOME("Cash In"), LOANS("Loans"), TRADE("Sales & purchases") }

@Composable
fun TransactionsScreen(entries: List<Entry>, currency: String, onEdit: (Entry) -> Unit, onAdd: () -> Unit, onMenu: () -> Unit) {
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf(TxFilter.ALL) }

    val q = query.trim().lowercase()
    val shown = entries
        .filter {
            when (filter) {
                TxFilter.ALL -> true
                TxFilter.EXPENSE -> it.type == Type.KHARCHA
                TxFilter.INCOME -> it.type == Type.AAMDANI
                TxFilter.LOANS -> isLoan(it.type)
                TxFilter.TRADE -> isTrade(it.type)
            }
        }
        .filter { e ->
            q.isEmpty() || listOf(e.note, e.item, e.person, e.category.label, e.type.label).any { it.lowercase().contains(q) }
        }
        .sortedWith(compareByDescending<Entry> { it.date }.thenByDescending { it.createdAt })
    val total = shown.sumOf { if (it.type == Type.KHARCHA || it.type == Type.WAPIS_DIYA) -it.amount else if (it.type == Type.AAMDANI || it.type == Type.WAPIS_MILA) it.amount else 0.0 }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { ScreenTitle("Entries", onMenu = onMenu) }
                FilledTonalButton(onClick = onAdd) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Text("Add")
                }
            }
        }
        item {
            OutlinedTextField(
                value = query, onValueChange = { query = it },
                placeholder = { Text("Search entries, people, items…") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                singleLine = true, shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            )
        }
        item {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TxFilter.entries.forEach { f ->
                    FilterChip(selected = filter == f, onClick = { filter = f }, label = { Text(f.label) })
                }
            }
        }
        if (filter == TxFilter.EXPENSE || filter == TxFilter.INCOME) {
            item {
                Text(
                    "${shown.size} entries · ${money(currency, abs(total))}",
                    fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (shown.isEmpty()) {
            item { Text("Nothing found.", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 12.dp)) }
        }
        var lastMonth = ""
        shown.forEach { e ->
            val m = SimpleDateFormat("MMMM yyyy", Locale.US).format(Date(e.date))
            if (m != lastMonth) {
                lastMonth = m
                item(key = "m-" + e.id) { Text(m, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp)) }
            }
            item(key = e.id) { TxRow(e, currency) { onEdit(e) } }
        }
    }
}

// ======================= REPORTS =======================

enum class Span(val label: String) { WEEK("Weekly"), MONTH("Monthly"), YEAR("Yearly") }

private fun spanRange(span: Span, offset: Int): Triple<Long, Long, String> {
    val c = Calendar.getInstance()
    c.set(Calendar.HOUR_OF_DAY, 0)
    c.set(Calendar.MINUTE, 0)
    c.set(Calendar.SECOND, 0)
    c.set(Calendar.MILLISECOND, 0)
    return when (span) {
        Span.WEEK -> {
            val back = (c.get(Calendar.DAY_OF_WEEK) - Calendar.SUNDAY + 7) % 7
            c.add(Calendar.DAY_OF_YEAR, -back)
            c.add(Calendar.DAY_OF_YEAR, 7 * offset)
            val start = c.timeInMillis
            c.add(Calendar.DAY_OF_YEAR, 7)
            val end = c.timeInMillis
            Triple(start, end, "${shortDate(start)} – ${shortDate(end - 1)}")
        }
        Span.MONTH -> {
            c.set(Calendar.DAY_OF_MONTH, 1)
            c.add(Calendar.MONTH, offset)
            val start = c.timeInMillis
            val label = SimpleDateFormat("MMMM yyyy", Locale.US).format(Date(start))
            c.add(Calendar.MONTH, 1)
            Triple(start, c.timeInMillis, label)
        }
        Span.YEAR -> {
            c.set(Calendar.DAY_OF_YEAR, 1)
            c.add(Calendar.YEAR, offset)
            val start = c.timeInMillis
            val label = c.get(Calendar.YEAR).toString()
            c.add(Calendar.YEAR, 1)
            Triple(start, c.timeInMillis, label)
        }
    }
}

private data class ReportGroup(
    val key: String,
    val title: String,
    val icon: String,
    val amount: Double,
    val entries: List<Entry>,
    val color: Color
)

private data class StockReportRow(
    val name: String,
    val unit: String,
    val currentQty: Double,
    val receivedQty: Double,
    val givenQty: Double,
    val boughtAmount: Double,
    val soldAmount: Double,
    val history: List<Entry>
)

enum class ReportKind(val label: String) {
    CASH_OUT("Cash Out"), CASH_IN("Cash In"), SALES("Sales"), PURCHASES("Purchases"),
    STOCK("Stock"), PROFIT("Profit"), CASH_BALANCE("Cash Balance"), DUES("Baqaya")
}

private fun Entry.isCashInFlow(): Boolean = when (type) {
    Type.AAMDANI, Type.WAPIS_MILA, Type.LIYA -> true
    Type.SALE -> person.isBlank() || paid
    else -> false
}

private fun Entry.isCashOutFlow(): Boolean = when (type) {
    Type.KHARCHA, Type.WAPIS_DIYA, Type.DIYA -> true
    Type.PURCHASE -> person.isBlank() || paid
    else -> false
}

private fun Entry.changesStock(): Boolean =
    qty > 0.0 && item.isNotBlank() && item != "Loans" &&
        type in setOf(Type.PURCHASE, Type.SALE, Type.DIYA, Type.LIYA)

private fun cashFlowCategory(entry: Entry): Cat = when (entry.type) {
    Type.SALE -> Cat.SALES
    Type.PURCHASE -> Cat.STOCK
    Type.DIYA, Type.LIYA, Type.WAPIS_MILA, Type.WAPIS_DIYA -> Cat.UDHAAR
    else -> entry.category
}

private fun cashFlowGroupTitle(category: Cat, kind: ReportKind): String = when (category) {
    Cat.SALES -> if (kind == ReportKind.CASH_IN) "Sales collected" else "Sales"
    Cat.STOCK -> "Stock purchases"
    Cat.UDHAAR -> if (kind == ReportKind.CASH_IN) "Borrowed / received back" else "Lent / paid back"
    Cat.TANKHWAH -> "Other cash received"
    else -> category.label
}

private fun flowGroups(entries: List<Entry>, kind: ReportKind): List<ReportGroup> =
    entries.groupBy { cashFlowCategory(it) }
        .map { (category, list) ->
            ReportGroup(
                key = category.name,
                title = cashFlowGroupTitle(category, kind),
                icon = category.icon,
                amount = list.sumOf { it.amount },
                entries = list.sortedWith(compareByDescending<Entry> { it.date }.thenByDescending { it.createdAt }),
                color = catColor(category)
            )
        }
        .sortedByDescending { it.amount }

private val reportPalette = listOf(
    Color(0xFF16A34A), Color(0xFF2563EB), Color(0xFFEA580C), Color(0xFF9333EA),
    Color(0xFF0891B2), Color(0xFFDB2777), Color(0xFFCA8A04), Color(0xFF0F766E),
    Color(0xFF4F46E5), Color(0xFF64748B), Color(0xFFB45309), Color(0xFFBE123C)
)

private fun itemGroups(entries: List<Entry>, kind: ReportKind): List<ReportGroup> =
    entries.groupBy { it.item.trim().ifBlank { if (kind == ReportKind.SALES) "Sale" else "Purchase" } }
        .toList()
        .sortedByDescending { (_, list) -> list.sumOf { it.amount } }
        .mapIndexed { index, (name, list) ->
            ReportGroup(
                key = name.lowercase(),
                title = name,
                icon = if (kind == ReportKind.SALES) "🧾" else "📦",
                amount = list.sumOf { it.amount },
                entries = list.sortedWith(compareByDescending<Entry> { it.date }.thenByDescending { it.createdAt }),
                color = reportPalette[index % reportPalette.size]
            )
        }

@Composable
private fun ReportDonut(
    currency: String,
    chartTotal: Double,
    centerValue: Double,
    centerLabel: String,
    groups: List<ReportGroup>
) {
    Box(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
        val ring = MaterialTheme.colorScheme.surfaceVariant
        Canvas(Modifier.size(210.dp)) {
            val stroke = 34.dp.toPx()
            val inset = stroke / 2
            val arcSize = androidx.compose.ui.geometry.Size(size.width - stroke, size.height - stroke)
            val topLeft = androidx.compose.ui.geometry.Offset(inset, inset)
            val positiveGroups = groups.filter { it.amount > 0.0 }
            if (chartTotal <= 0.0 || positiveGroups.isEmpty()) {
                drawArc(ring, 0f, 360f, false, topLeft = topLeft, size = arcSize, style = Stroke(stroke))
            } else {
                var startAngle = -90f
                positiveGroups.forEach { group ->
                    val sweep = (group.amount / chartTotal * 360f).toFloat()
                    drawArc(group.color, startAngle, sweep, false, topLeft = topLeft, size = arcSize, style = Stroke(stroke, cap = StrokeCap.Butt))
                    startAngle += sweep
                }
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(moneyA(currency, centerValue), fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
            Text(centerLabel, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ReportGroupCard(
    group: ReportGroup,
    total: Double,
    currency: String,
    expanded: Boolean,
    onClick: () -> Unit
) {
    val share = if (total > 0.0) (group.amount / total).coerceIn(0.0, 1.0) else 0.0
    Panel(Modifier.fillMaxWidth(), onClick = onClick, padding = 12.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(12.dp).clip(CircleShape).background(group.color))
            Spacer(Modifier.width(10.dp))
            Text("${group.icon} ${group.title}", Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
            Text("${(share * 100).roundToLong()}%", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(10.dp))
            Text(moneyA(currency, group.amount), fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(8.dp))
        LinearProgressIndicator(
            progress = { share.toFloat() },
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp)),
            color = group.color,
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        )
        Text("${group.entries.size} entries", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
        if (expanded) {
            HorizontalDivider(Modifier.padding(vertical = 6.dp))
            group.entries.forEach { e ->
                val item = e.item.takeIf { it.isNotBlank() && it != "Loans" }
                val party = e.person.takeIf { it.isNotBlank() }?.let {
                    when (e.type) {
                        Type.SALE, Type.DIYA -> "to $it"
                        Type.PURCHASE, Type.LIYA -> "from $it"
                        else -> it
                    }
                }
                val quantity = if (e.qty > 0.0) fmt(e.qty) + if (e.unit.isBlank()) "" else " ${e.unit}" else null
                val detail = listOf(item, party, e.note.takeIf { it.isNotBlank() }, quantity)
                    .filterNotNull().joinToString(" · ").ifBlank { typeLabel(e.type, e.person) }
                Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(shortDate(e.date), Modifier.width(56.dp), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(detail, Modifier.weight(1f), fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(moneyA(currency, e.amount), fontSize = 12.sp)
                }
            }
        } else {
            Text("Tap to see every entry", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun ReportsScreen(entries: List<Entry>, currency: String, onMenu: () -> Unit) {
    var span by remember { mutableStateOf(Span.MONTH) }
    var offset by remember { mutableIntStateOf(0) }
    var reportKind by remember { mutableStateOf(ReportKind.CASH_OUT) }
    var openGroups by remember { mutableStateOf(setOf<String>()) }

    val (start, end, label) = spanRange(span, offset)
    val inRange = entries.filter { it.date in start until end }
    val cashInEntries = inRange.filter { it.isCashInFlow() }
    val cashOutEntries = inRange.filter { it.isCashOutFlow() }
    val cashInTotal = cashInEntries.sumOf { it.amount }
    val cashOutTotal = cashOutEntries.sumOf { it.amount }
    val allCashIn = entries.filter { it.isCashInFlow() }.sumOf { it.amount }
    val allCashOut = entries.filter { it.isCashOutFlow() }.sumOf { it.amount }
    val cashBalance = allCashIn - allCashOut

    val sales = inRange.filter { it.type == Type.SALE }
    val purchases = inRange.filter { it.type == Type.PURCHASE }
    val totalSales = sales.sumOf { it.amount }
    val totalPurchases = purchases.sumOf { it.amount }
    val salesGroups = itemGroups(sales, ReportKind.SALES)
    val purchaseGroups = itemGroups(purchases, ReportKind.PURCHASES)
    val cashInGroups = flowGroups(cashInEntries, ReportKind.CASH_IN)
    val cashOutGroups = flowGroups(cashOutEntries, ReportKind.CASH_OUT)

    val currentStock = stock(entries)
    val stockByName = currentStock.associateBy { it.name.trim().lowercase() }
    val stockNames = (currentStock.map { it.name } + entries.filter { it.changesStock() }.map { it.item.trim() })
        .distinctBy { it.trim().lowercase() }
        .sortedBy { it.lowercase() }
    val stockRows = stockNames.map { name ->
        val history = inRange.filter { it.changesStock() && it.item.trim().equals(name.trim(), ignoreCase = true) }
            .sortedWith(compareByDescending<Entry> { it.date }.thenByDescending { it.createdAt })
        val line = stockByName[name.trim().lowercase()]
        val unit = line?.unit?.ifBlank { history.firstOrNull { it.unit.isNotBlank() }?.unit.orEmpty() }
            ?: history.firstOrNull { it.unit.isNotBlank() }?.unit.orEmpty()
        StockReportRow(
            name = name,
            unit = unit,
            currentQty = line?.qty ?: 0.0,
            receivedQty = history.filter { it.type == Type.PURCHASE || it.type == Type.LIYA }.sumOf { it.qty },
            givenQty = history.filter { it.type == Type.SALE || it.type == Type.DIYA }.sumOf { it.qty },
            boughtAmount = history.filter { it.type == Type.PURCHASE }.sumOf { it.amount },
            soldAmount = history.filter { it.type == Type.SALE }.sumOf { it.amount },
            history = history
        )
    }
    val receivedQty = inRange.filter { (it.type == Type.PURCHASE || it.type == Type.LIYA) && it.changesStock() }.sumOf { it.qty }
    val givenQty = inRange.filter { (it.type == Type.SALE || it.type == Type.DIYA) && it.changesStock() }.sumOf { it.qty }

    val profit = profitBreakdown(entries, inRange)
    val outstanding = balances(entries).filterValues { kotlin.math.abs(it) > 0.005 }
        .toList().sortedByDescending { kotlin.math.abs(it.second) }
    val toReceive = outstanding.filter { it.second > 0 }.sumOf { it.second }
    val toPay = outstanding.filter { it.second < 0 }.sumOf { -it.second }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { ScreenTitle("Reports", onMenu = onMenu) }
        item {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ReportKind.entries.forEach { kind ->
                    FilterChip(selected = reportKind == kind, onClick = { reportKind = kind; openGroups = emptySet() }, label = { Text(kind.label) })
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceVariant).padding(4.dp)) {
                Span.entries.forEach { s ->
                    val selected = s == span
                    Box(Modifier.weight(1f).clip(RoundedCornerShape(10.dp))
                        .background(if (selected) MaterialTheme.colorScheme.primary else Color.Transparent)
                        .clickable { span = s; offset = 0 }.padding(vertical = 9.dp), contentAlignment = Alignment.Center) {
                        Text(s.label, color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { offset-- }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous") }
                Text(label, Modifier.weight(1f), fontWeight = FontWeight.SemiBold, fontSize = 16.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                IconButton(onClick = { offset++ }, enabled = offset < 0) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next") }
            }
        }

        when (reportKind) {
            ReportKind.CASH_OUT -> {
                item { ReportDonut(currency, cashOutTotal, cashOutTotal, "Total Cash Out", cashOutGroups) }
                item {
                    Panel(Modifier.fillMaxWidth(), padding = 14.dp) {
                        Text("Cash Out · $label", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                        Text(moneyA(currency, cashOutTotal), fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, color = RedSpend)
                        Text("${cashOutEntries.size} entries, including expenses, paid purchases and money lent", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (cashOutGroups.isEmpty()) item { Text("No cash-out entries in this period.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                items(cashOutGroups, key = { it.key }) { group ->
                    ReportGroupCard(group, cashOutTotal, currency, group.key in openGroups) {
                        openGroups = if (group.key in openGroups) openGroups - group.key else openGroups + group.key
                    }
                }
            }
            ReportKind.CASH_IN -> {
                item { ReportDonut(currency, cashInTotal, cashInTotal, "Total Cash In", cashInGroups) }
                item {
                    Panel(Modifier.fillMaxWidth(), padding = 14.dp) {
                        Text("Cash In · $label", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                        Text(moneyA(currency, cashInTotal), fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, color = GreenIn)
                        Text("${cashInEntries.size} entries, including other income, paid sales and money received back", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (cashInGroups.isEmpty()) item { Text("No cash-in entries in this period.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                items(cashInGroups, key = { it.key }) { group ->
                    ReportGroupCard(group, cashInTotal, currency, group.key in openGroups) {
                        openGroups = if (group.key in openGroups) openGroups - group.key else openGroups + group.key
                    }
                }
            }
            ReportKind.SALES -> {
                item { ReportDonut(currency, totalSales, totalSales, "Total Sales", salesGroups) }
                item {
                    Panel(Modifier.fillMaxWidth(), padding = 14.dp) {
                        Text("Sales · $label", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                        Text(moneyA(currency, totalSales), fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, color = GreenIn)
                        Text("${sales.size} sales entries · ${fmt(sales.sumOf { it.qty })} units sold", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (salesGroups.isEmpty()) item { Text("No sales in this period.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                items(salesGroups, key = { it.key }) { group ->
                    ReportGroupCard(group, totalSales, currency, group.key in openGroups) {
                        openGroups = if (group.key in openGroups) openGroups - group.key else openGroups + group.key
                    }
                }
            }
            ReportKind.PURCHASES -> {
                item { ReportDonut(currency, totalPurchases, totalPurchases, "Total Purchases", purchaseGroups) }
                item {
                    Panel(Modifier.fillMaxWidth(), padding = 14.dp) {
                        Text("Purchases · $label", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                        Text(moneyA(currency, totalPurchases), fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, color = catColor(Cat.STOCK))
                        Text("${purchases.size} purchase entries · ${fmt(purchases.sumOf { it.qty })} units bought", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (purchaseGroups.isEmpty()) item { Text("No purchases in this period.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                items(purchaseGroups, key = { it.key }) { group ->
                    ReportGroupCard(group, totalPurchases, currency, group.key in openGroups) {
                        openGroups = if (group.key in openGroups) openGroups - group.key else openGroups + group.key
                    }
                }
            }
            ReportKind.STOCK -> {
                item {
                    Panel(Modifier.fillMaxWidth(), padding = 14.dp) {
                        Text("Stock movement · $label", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Column(Modifier.weight(1f)) {
                                Text("Received", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(fmt(receivedQty), fontWeight = FontWeight.Bold, color = catColor(Cat.STOCK))
                            }
                            Column(Modifier.weight(1f)) {
                                Text("Sold / given", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(fmt(givenQty), fontWeight = FontWeight.Bold, color = GreenIn)
                            }
                        }
                        HorizontalDivider(Modifier.padding(vertical = 10.dp))
                        Text("Current stock on hand", fontWeight = FontWeight.SemiBold)
                        Text("Quantity remaining from all recorded stock movements.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (stockRows.isEmpty()) item { Text("No stock quantities recorded yet. Add item quantities to purchases or sales.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                items(stockRows, key = { it.name.trim().lowercase() }) { row ->
                    val key = "stock:" + row.name.trim().lowercase()
                    val expanded = key in openGroups
                    val maxQty = stockRows.maxOfOrNull { kotlin.math.abs(it.currentQty) } ?: 0.0
                    val share = if (maxQty > 0) (kotlin.math.abs(row.currentQty) / maxQty).coerceIn(0.0, 1.0) else 0.0
                    Panel(Modifier.fillMaxWidth(), onClick = { openGroups = if (expanded) openGroups - key else openGroups + key }, padding = 12.dp) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("📦", fontSize = 18.sp)
                            Spacer(Modifier.width(10.dp))
                            Text(row.name, Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                            Text(fmt(row.currentQty) + if (row.unit.isBlank()) "" else " ${row.unit}", fontWeight = FontWeight.Bold,
                                color = if (row.currentQty < 0) RedSpend else MaterialTheme.colorScheme.onSurface)
                        }
                        Spacer(Modifier.height(7.dp))
                        LinearProgressIndicator(progress = { share.toFloat() }, modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp)),
                            color = if (row.currentQty < 0) RedSpend else GreenIn, trackColor = MaterialTheme.colorScheme.surfaceVariant)
                        Text("Received ${fmt(row.receivedQty)} · Sold/given ${fmt(row.givenQty)}", fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
                        Text("Purchase ${fmt(row.boughtAmount)} · Sales ${fmt(row.soldAmount)} $currency", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (expanded) {
                            HorizontalDivider(Modifier.padding(vertical = 6.dp))
                            if (row.history.isEmpty()) Text("No movement in this period.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            row.history.forEach { e ->
                                val direction = when (e.type) {
                                    Type.PURCHASE -> "Bought"
                                    Type.LIYA -> "Received"
                                    Type.SALE -> "Sold"
                                    else -> "Given"
                                }
                                val party = e.person.takeIf { it.isNotBlank() }?.let { if (e.type == Type.PURCHASE || e.type == Type.LIYA) "from $it" else "to $it" }
                                val quantity = fmt(e.qty) + if (e.unit.isBlank()) "" else " ${e.unit}"
                                val detail = listOf(direction, party, quantity, e.note.takeIf { it.isNotBlank() }).filterNotNull().joinToString(" · ")
                                Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text(shortDate(e.date), Modifier.width(56.dp), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(detail, Modifier.weight(1f), fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    Text(moneyA(currency, e.amount), fontSize = 12.sp)
                                }
                            }
                        } else Text("Tap to see stock movements", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            ReportKind.PROFIT -> {
                item {
                    Panel(Modifier.fillMaxWidth(), padding = 16.dp) {
                        Text("Estimated net profit · $label", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(moneyA(currency, profit.netProfit), fontSize = 30.sp, fontWeight = FontWeight.ExtraBold,
                            color = if (profit.netProfit >= 0) GreenIn else RedSpend)
                        Text("Costed sales margin − Cash Out expenses (Cash In excluded)", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                item {
                    Panel(Modifier.fillMaxWidth(), padding = 14.dp) {
                        ProfitSummaryRow("Total sales", profit.salesAmount, currency, GreenIn)
                        ProfitSummaryRow("Sales with known cost", profit.costedSalesAmount, currency, MaterialTheme.colorScheme.onSurface)
                        ProfitSummaryRow("Estimated cost of sold items", -profit.costOfGoods, currency, RedSpend)
                        ProfitSummaryRow("Cash In (not included in profit)", profit.otherIncome, currency, GreenIn)
                        ProfitSummaryRow("Cash-out expenses", -profit.expenses, currency, RedSpend)
                        HorizontalDivider(Modifier.padding(vertical = 8.dp))
                        ProfitSummaryRow("Estimated net profit", profit.netProfit, currency, if (profit.netProfit >= 0) GreenIn else RedSpend, bold = true)
                        if (profit.salesWithoutCost > 0.005) {
                            Text("Sales without recorded item cost: ${money(currency, profit.salesWithoutCost)}. These sales are not included in estimated profit. Add purchase quantity and price for a better estimate.",
                                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
                        }
                        Text("Stock purchases are not all deducted at once; only the estimated cost of items sold is deducted.", fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
                    }
                }
                if (profit.items.isEmpty()) item { Text("No sales in this period. Add item quantities and purchase costs to calculate trade profit.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                items(profit.items, key = { "profit:" + it.item.lowercase() }) { line ->
                    val key = "profit:" + line.item.lowercase()
                    val expanded = key in openGroups
                    val share = if (profit.salesAmount > 0) (line.salesAmount / profit.salesAmount).coerceIn(0.0, 1.0) else 0.0
                    val colorIndex = (line.item.lowercase().hashCode().toLong().and(Long.MAX_VALUE) % reportPalette.size).toInt()
                    Panel(Modifier.fillMaxWidth(), onClick = { openGroups = if (expanded) openGroups - key else openGroups + key }, padding = 12.dp) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("📊", fontSize = 17.sp)
                            Spacer(Modifier.width(8.dp))
                            Text(line.item, Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                            Text(moneyA(currency, line.estimatedProfit), fontWeight = FontWeight.Bold,
                                color = if (line.estimatedProfit >= 0) GreenIn else RedSpend)
                        }
                        Spacer(Modifier.height(6.dp))
                        LinearProgressIndicator(progress = { share.toFloat() }, modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp)),
                            color = reportPalette[colorIndex], trackColor = MaterialTheme.colorScheme.surfaceVariant)
                        Text("Sales ${fmt(line.salesAmount)} · Costed sales ${fmt(line.costedSalesAmount)} · Cost ${fmt(line.costOfGoods)} $currency",
                            fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
                        if (line.salesWithoutCost > 0.005) Text("Cost missing for sales: ${money(currency, line.salesWithoutCost)}", fontSize = 11.sp, color = RedSpend)
                        if (expanded) {
                            HorizontalDivider(Modifier.padding(vertical = 6.dp))
                            line.entries.forEach { e ->
                                val quantity = if (e.qty > 0) fmt(e.qty) + if (e.unit.isBlank()) "" else " ${e.unit}" else null
                                val details = listOf(e.person.takeIf { it.isNotBlank() }?.let { "to $it" }, quantity,
                                    e.note.takeIf { it.isNotBlank() }).filterNotNull().joinToString(" · ").ifBlank { "Sale" }
                                Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text(shortDate(e.date), Modifier.width(56.dp), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(details, Modifier.weight(1f), fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    Text(moneyA(currency, e.amount), fontSize = 12.sp)
                                }
                            }
                        } else Text("Tap to see sale entries", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            ReportKind.CASH_BALANCE -> {
                val balanceGroups = listOf(
                    ReportGroup("all-cash-in", "Cash In", "＋", allCashIn, emptyList(), GreenIn),
                    ReportGroup("all-cash-out", "Cash Out", "−", allCashOut, emptyList(), RedSpend)
                )
                item { ReportDonut(currency, allCashIn + allCashOut, cashBalance, "Cash Balance", balanceGroups) }
                item {
                    Panel(Modifier.fillMaxWidth(), padding = 16.dp) {
                        Text("Current cash balance · all recorded entries", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(moneyA(currency, cashBalance), fontSize = 30.sp, fontWeight = FontWeight.ExtraBold,
                            color = if (cashBalance >= 0) GreenIn else RedSpend)
                        HorizontalDivider(Modifier.padding(vertical = 10.dp))
                        ProfitSummaryRow("Cash In (all time)", allCashIn, currency, GreenIn)
                        ProfitSummaryRow("Cash Out (all time)", -allCashOut, currency, RedSpend)
                        HorizontalDivider(Modifier.padding(vertical = 8.dp))
                        ProfitSummaryRow("Cash movement · $label", cashInTotal - cashOutTotal, currency,
                            if (cashInTotal >= cashOutTotal) GreenIn else RedSpend, bold = true)
                        Text("This balance is calculated from entries saved in the app. Any cash you had before recording entries is not included.", fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
                    }
                }
            }
            ReportKind.DUES -> {
                item {
                    Panel(Modifier.fillMaxWidth(), padding = 14.dp) {
                        Text("Current outstanding · all time", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Column(Modifier.weight(1f)) {
                                Text("To receive", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(moneyA(currency, toReceive), fontWeight = FontWeight.Bold, color = GreenIn)
                            }
                            Column(Modifier.weight(1f)) {
                                Text("To pay", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(moneyA(currency, toPay), fontWeight = FontWeight.Bold, color = OrangeOwe)
                            }
                        }
                        Text("${outstanding.size} people with an outstanding balance", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
                    }
                }
                if (outstanding.isEmpty()) item { Text("No outstanding balances.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                items(outstanding, key = { it.first.lowercase() }) { (person, balance) ->
                    val key = "dues:" + person.lowercase()
                    val expanded = key in openGroups
                    val history = entries.filter { it.person.trim().equals(person, ignoreCase = true) && withPerson(it) }
                        .sortedWith(compareByDescending<Entry> { it.date }.thenByDescending { it.createdAt })
                    Panel(Modifier.fillMaxWidth(), onClick = { openGroups = if (expanded) openGroups - key else openGroups + key }, padding = 12.dp) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Avatar(person)
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(person, fontWeight = FontWeight.SemiBold)
                                Text(if (balance > 0) "They owe you" else "You owe them", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text(moneyA(currency, kotlin.math.abs(balance)), fontWeight = FontWeight.Bold,
                                color = if (balance > 0) GreenIn else OrangeOwe)
                        }
                        if (expanded) {
                            HorizontalDivider(Modifier.padding(vertical = 6.dp))
                            history.forEach { e ->
                                val detail = listOf(typeLabel(e.type, e.person), e.item.takeIf { it.isNotBlank() && it != "Loans" },
                                    e.note.takeIf { it.isNotBlank() }).filterNotNull().joinToString(" · ")
                                Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text(shortDate(e.date), Modifier.width(56.dp), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(detail, Modifier.weight(1f), fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    Text(moneyA(currency, e.amount), fontSize = 12.sp)
                                }
                            }
                        } else Text("Tap to see outstanding transactions", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfitSummaryRow(label: String, amount: Double, currency: String, color: Color, bold: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), fontSize = 13.sp, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal)
        Text(moneyA(currency, amount), fontSize = 13.sp, fontWeight = if (bold) FontWeight.Bold else FontWeight.Medium, color = color)
    }
}

// ======================= PEOPLE =======================

@Composable
fun PeopleScreen(entries: List<Entry>, currency: String, receive: Boolean, onBack: () -> Unit, onEdit: (Entry) -> Unit) {
    val bal = balances(entries)
        .filter { if (receive) it.value.roundToLong() > 0 else it.value.roundToLong() < 0 }
        .toList().sortedByDescending { abs(it.second) }
    val total = bal.sumOf { abs(it.second) }
    val color = if (receive) GreenIn else OrangeOwe
    var open by remember { mutableStateOf(setOf<String>()) }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item { ScreenTitle(if (receive) "To receive" else "To pay", onBack) }
        item {
            Panel(Modifier.fillMaxWidth(), padding = 16.dp) {
                Text(if (receive) "People owe you" else "You owe people", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                Text(moneyA(currency, total), fontSize = 30.sp, fontWeight = FontWeight.ExtraBold, color = color)
                Text("${bal.size} " + if (bal.size == 1) "person" else "people", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (bal.isEmpty()) {
            item { Text(if (receive) "Nobody owes you right now." else "You don't owe anyone right now.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        items(bal, key = { it.first }) { (person, v) ->
            val expanded = person in open
            val history = entries.filter { it.person.trim() == person && withPerson(it) }.sortedByDescending { it.date }
            Panel(Modifier.fillMaxWidth(), onClick = { open = if (expanded) open - person else open + person }, padding = 12.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Avatar(person)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(person, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
                        Text("${history.size} entries · tap to " + if (expanded) "close" else "open", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(moneyA(currency, abs(v)), fontWeight = FontWeight.Bold, color = color, fontSize = 17.sp)
                }
                if (expanded) {
                    HorizontalDivider(Modifier.padding(vertical = 8.dp))
                    history.forEach { e ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onEdit(e) }
                                .padding(vertical = 5.dp)
                        ) {
                            Text(shortDate(e.date), Modifier.width(60.dp), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(typeLabel(e.type, ""), Modifier.weight(1f), fontSize = 13.sp)
                            Text(fmt(e.amount), fontSize = 13.sp, color = signColor(e.type), fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }
}

// ======================= SETTINGS =======================

@Composable
fun SettingsScreen(
    name: String,
    currency: String,
    lang: String,
    dark: Boolean,
    version: String,
    checking: Boolean,
    onName: (String) -> Unit,
    onCurrency: (String) -> Unit,
    onLang: (String) -> Unit,
    onDark: (Boolean) -> Unit,
    onCheckUpdate: () -> Unit,
    onMenu: () -> Unit,
    themeName: String,
    onTheme: (String) -> Unit,
    bankOn: Boolean = false,
    onBank: () -> Unit = {},
    aiKey: String = "",
    aiBuiltIn: Boolean = false,
    onAiKey: (String) -> Unit = {}
) {
    var aiText by remember(aiKey) { mutableStateOf(aiKey) }
    var nameText by remember(name) { mutableStateOf(name) }
    var currencyMenu by remember { mutableStateOf(false) }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp))
                    .background(LocalHeader.current.brush)
                    .padding(20.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onMenu) { Icon(Icons.Filled.Menu, contentDescription = "Menu", tint = LocalHeader.current.on) }
                    Text("Settings", color = LocalHeader.current.on, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(58.dp).clip(CircleShape).background(Color.White),
                        contentAlignment = Alignment.Center
                    ) { Text(name.trim().take(1).uppercase().ifEmpty { "🙂" }, color = GreenDeep, fontWeight = FontWeight.Bold, fontSize = 24.sp) }
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text(name.ifBlank { "Your name" }, color = LocalHeader.current.on, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        Text("Smart Expense $version", color = LocalHeader.current.on.copy(alpha = 0.8f), fontSize = 13.sp)
                    }
                }
            }
        }
        item {
            Panel(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp)) {
                SmallLabel("Theme")
                Spacer(Modifier.height(4.dp))
                Text(themeName, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(10.dp))
                ThemeGroup("Light", HEADER_THEMES.filter { it.light }, themeName, onTheme)
                Spacer(Modifier.height(8.dp))
                ThemeGroup("Bold & dark", HEADER_THEMES.filter { !it.light }, themeName, onTheme)
            }
        }
        item {
            Panel(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SmallLabel("✨ AI help (Gemini)")
                    Spacer(Modifier.weight(1f))
                    val aiOn = aiKey.isNotBlank() || aiBuiltIn
                    Text(
                        if (aiOn) "On" else "Off",
                        fontWeight = FontWeight.Bold,
                        color = if (aiOn) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    "Simple entries are understood offline. Only sentences the app is unsure about are sent to Gemini. " +
                        "Without internet the app works as before.",
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                if (aiBuiltIn && aiKey.isBlank()) {
                    Text(
                        "✓ AI is built in. You don't need a key. Add your own only if you want to use it instead.",
                        fontSize = 12.sp, color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(6.dp))
                }
                OutlinedTextField(
                    value = aiText, onValueChange = { aiText = it.trim() },
                    singleLine = true, placeholder = { Text(if (aiBuiltIn) "Your own key (optional)" else "Paste your Gemini API key") },
                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (aiKey.isNotBlank()) {
                        OutlinedButton(onClick = { aiText = ""; onAiKey("") }) { Text("Remove") }
                    }
                    Button(
                        onClick = { onAiKey(aiText) },
                        enabled = aiText.isNotBlank() && aiText != aiKey,
                        modifier = Modifier.weight(1f)
                    ) { Text("Save key") }
                }
                Text(
                    "Get a free key at aistudio.google.com → Get API key. Keep it private.",
                    fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
        }
        item {
            Panel(Modifier.fillMaxWidth().padding(16.dp)) {
                SmallLabel("Your name")
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = nameText, onValueChange = { nameText = it },
                        singleLine = true, placeholder = { Text("Your name") },
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = { onName(nameText.trim()) }, enabled = nameText.trim() != name) { Text("Save") }
                }
            }
        }
        item {
            Panel(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                SettingRow("Currency", "All amounts use this, whatever you say") {
                    Box {
                        OutlinedButton(onClick = { currencyMenu = true }) { Text("$currency ▾") }
                        DropdownMenu(expanded = currencyMenu, onDismissRequest = { currencyMenu = false }) {
                            CURRENCIES.forEach { c ->
                                DropdownMenuItem(text = { Text(c) }, onClick = { onCurrency(c); currencyMenu = false })
                            }
                        }
                    }
                }
                HorizontalDivider(Modifier.padding(vertical = 6.dp))
                SettingRow(
                    "Voice language",
                    when (lang) {
                        "ur-PK" -> "Urdu script (اردو)"
                        "en-IN" -> "English only"
                        else -> "Urdu / Hindi speech, written in Roman (best)"
                    }
                ) {
                    Row {
                        FilterChip(selected = lang != "ur-PK" && lang != "en-IN", onClick = { onLang("hi-IN") }, label = { Text("Roman") })
                        Spacer(Modifier.width(4.dp))
                        FilterChip(selected = lang == "en-IN", onClick = { onLang("en-IN") }, label = { Text("Eng") })
                        Spacer(Modifier.width(4.dp))
                        FilterChip(selected = lang == "ur-PK", onClick = { onLang("ur-PK") }, label = { Text("اردو") })
                    }
                }
                HorizontalDivider(Modifier.padding(vertical = 6.dp))
                SettingRow("Dark mode", null) {
                    Switch(checked = dark, onCheckedChange = onDark)
                }
                HorizontalDivider(Modifier.padding(vertical = 6.dp))
                SettingRow(
                    "Bank alerts",
                    if (bankOn) "On: payments from bank notifications are suggested" else "Off: turn on to catch card payments"
                ) {
                    if (bankOn) {
                        Text("On ✓", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    } else {
                        Button(onClick = onBank) { Text("Turn on") }
                    }
                }
                HorizontalDivider(Modifier.padding(vertical = 6.dp))
                SettingRow("Updates", "Version $version") {
                    OutlinedButton(onClick = onCheckUpdate, enabled = !checking) { Text(if (checking) "Checking…" else "Check now") }
                }
            }
        }
        item {
            Text(
                "Your entries are saved only on this phone.",
                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(20.dp)
            )
        }
    }
}

@Composable
private fun SettingRow(title: String, sub: String?, trailing: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            if (sub != null) Text(sub, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        trailing()
    }
}

// ======================= SEARCH / EDIT RESULTS =======================

@Composable
fun ResultsScreen(
    query: String,
    editMode: Boolean,
    entries: List<Entry>,
    currency: String,
    onBack: () -> Unit,
    onEdit: (Entry) -> Unit,
    onDeleteAll: (List<Entry>) -> Unit,
    answer: String? = null
) {
    // When the query is a person's name, show that person's khata.
    val person = entries.map { it.person.trim() }.firstOrNull { it.isNotEmpty() && it.equals(query.trim(), ignoreCase = true) }
    val found = if (person != null) {
        entries.filter { it.person.trim().equals(person, ignoreCase = true) && withPerson(it) }
            .sortedWith(compareByDescending<Entry> { it.date }.thenByDescending { it.createdAt })
    } else {
        matchEntries(entries, query)
    }
    val spent = found.filter { it.type == Type.KHARCHA }.sumOf { it.amount }
    val owed = balances(found).filter { it.value.roundToLong() != 0L }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            ScreenTitle(
                when {
                    person != null -> "$person's khata"
                    editMode -> "Edit: $query"
                    else -> "Search: $query"
                },
                onBack
            )
        }
        if (answer != null && person == null) {
            item {
                Panel(Modifier.fillMaxWidth(), padding = 14.dp) {
                    Text(answer, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                }
            }
        }
        if (person != null) {
            item {
                val bal = owed.values.firstOrNull() ?: 0.0
                fun sum(t: Type) = found.filter { it.type == t }.sumOf { it.amount }
                Panel(Modifier.fillMaxWidth(), padding = 16.dp) {
                    Text(
                        when {
                            bal > 0.005 -> "$person will pay you"
                            bal < -0.005 -> "You will pay $person"
                            else -> "All settled with $person"
                        },
                        fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        moneyA(currency, kotlin.math.abs(bal)),
                        fontSize = 30.sp, fontWeight = FontWeight.ExtraBold,
                        color = if (bal > 0.005) GreenIn else if (bal < -0.005) OrangeOwe else MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        KhataStat("You gave", sum(Type.DIYA), currency, OrangeOwe, Modifier.weight(1f))
                        KhataStat("Got back", sum(Type.WAPIS_MILA), currency, GreenIn, Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        KhataStat("You took", sum(Type.LIYA), currency, OrangeOwe, Modifier.weight(1f))
                        KhataStat("Paid back", sum(Type.WAPIS_DIYA), currency, RedSpend, Modifier.weight(1f))
                    }
                    if (found.any { isTrade(it.type) }) {
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            KhataStat("Sold to them", sum(Type.SALE), currency, GreenIn, Modifier.weight(1f))
                            KhataStat("Bought from them", sum(Type.PURCHASE), currency, RedSpend, Modifier.weight(1f))
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text("${found.size} entries · tap one to change it", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else item {
            Panel(Modifier.fillMaxWidth(), padding = 16.dp) {
                Text(
                    "${found.size} " + (if (found.size == 1) "entry" else "entries") + " found",
                    fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (spent > 0) {
                    Text("Spent ${money(currency, spent)}", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = RedSpend)
                }
                owed.forEach { (p, v) ->
                    Text(
                        if (v > 0) "$p will pay you ${money(currency, v)}" else "You will pay $p ${money(currency, -v)}",
                        fontWeight = FontWeight.SemiBold, color = if (v > 0) GreenIn else OrangeOwe
                    )
                }
                if (editMode && found.isNotEmpty()) {
                    Text("Tap any entry to change or delete it.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        // Items with their quantities and totals ("Awal eint 1000 · 17,000 SAR")
        val byItem = found.filter { it.item.isNotBlank() && it.item != "Loans" }
            .groupBy { it.item }
            .filter { (_, v) -> v.any { it.qty > 0 } || found.map { it.item }.distinct().size > 1 }
        if (byItem.isNotEmpty()) {
            item {
                Panel(Modifier.fillMaxWidth(), padding = 14.dp) {
                    SmallLabel("Items")
                    Spacer(Modifier.height(6.dp))
                    byItem.entries.sortedByDescending { e -> e.value.sumOf { it.amount } }.forEach { (name, list) ->
                        val q = list.filter { it.qty > 0 }
                        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(name, fontWeight = FontWeight.SemiBold)
                                if (q.isNotEmpty()) {
                                    Text(
                                        "Qty " + fmt(q.sumOf { it.qty }) + (q.first().unit.let { if (it.isBlank()) "" else " $it" }) +
                                            " · ${list.size} " + (if (list.size == 1) "entry" else "entries"),
                                        fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Text(moneyA(currency, list.sumOf { it.amount }), fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
        if (found.isEmpty()) {
            item {
                Text(
                    "Nothing found for \"$query\". Try a name, an item or a category.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        items(found, key = { it.id }) { e -> TxRow(e, currency) { onEdit(e) } }
        if (found.isNotEmpty()) {
            item {
                OutlinedButton(
                    onClick = { onDeleteAll(found) },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = RedSpend)
                ) { Text("Delete these ${found.size}") }
            }
        }
    }
}

// ======================= TRASH =======================

@Composable
fun TrashScreen(
    trash: List<Entry>,
    currency: String,
    onMenu: () -> Unit,
    onRestore: (Entry) -> Unit,
    onRestoreAll: () -> Unit,
    onEmpty: () -> Unit
) {
    var askEmpty by remember { mutableStateOf(false) }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item { ScreenTitle("Trash", onMenu = onMenu) }
        item {
            Text(
                "Deleted entries stay here for 30 days. Restore anything you deleted by mistake.",
                fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (trash.isEmpty()) {
            item {
                Panel(Modifier.fillMaxWidth().padding(top = 8.dp), padding = 20.dp) {
                    Text("🗑️", fontSize = 32.sp)
                    Text("Trash is empty", fontWeight = FontWeight.SemiBold)
                }
            }
        } else {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(onClick = onRestoreAll, modifier = Modifier.weight(1f)) { Text("Restore all") }
                    OutlinedButton(
                        onClick = { askEmpty = true }, modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = RedSpend)
                    ) { Text("Empty trash") }
                }
            }
            items(trash, key = { "t-" + it.id }) { e ->
                Panel(Modifier.fillMaxWidth(), padding = 12.dp) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CatBubble(e.category)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(entryTitle(e), fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                dayLabel(e.date) + " · " + money(currency, e.amount),
                                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        TextButton(onClick = { onRestore(e) }) { Text("Restore") }
                    }
                }
            }
        }
    }

    if (askEmpty) {
        AlertDialog(
            onDismissRequest = { askEmpty = false },
            title = { Text("Empty trash?") },
            text = { Text("${trash.size} entries will be deleted forever. This cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = { askEmpty = false; onEmpty() },
                    colors = ButtonDefaults.buttonColors(containerColor = RedSpend)
                ) { Text("Delete forever") }
            },
            dismissButton = { TextButton(onClick = { askEmpty = false }) { Text("Cancel") } }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ThemeGroup(title: String, themes: List<HeaderTheme>, selected: String, onPick: (String) -> Unit) {
    Text(title, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(6.dp))
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        themes.forEach { t ->
            val isOn = t.name == selected
            Column(
                Modifier
                    .width(56.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onPick(t.name) },
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(t.brush)
                        .border(
                            if (isOn) 3.dp else 1.dp,
                            if (isOn) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) { if (isOn) Text("✓", color = t.on, fontWeight = FontWeight.Bold) }
                Text(t.name, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun KhataStat(label: String, amount: Double, currency: String, color: Color, modifier: Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(color.copy(alpha = 0.10f))
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(moneyA(currency, amount), fontWeight = FontWeight.Bold, fontSize = 16.sp, color = color)
    }
}
