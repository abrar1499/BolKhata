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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
    bankCount: Int = 0,
    onReviewBank: () -> Unit = {}
) {
    var open by remember { mutableStateOf(setOf("today")) }
    fun toggle(k: String) {
        open = if (k in open) open - k else open + k
    }

    val now = Calendar.getInstance()
    fun inMonth(ms: Long) = cal(ms).let { it.get(Calendar.YEAR) == now.get(Calendar.YEAR) && it.get(Calendar.MONTH) == now.get(Calendar.MONTH) }
    fun inYear(ms: Long) = cal(ms).get(Calendar.YEAR) == now.get(Calendar.YEAR)

    val monthSpent = entries.filter { it.type == Type.KHARCHA && inMonth(it.date) }.sumOf { it.amount }
    val yearSpent = entries.filter { it.type == Type.KHARCHA && inYear(it.date) }.sumOf { it.amount }
    val monthIncome = entries.filter { it.type == Type.AAMDANI && inMonth(it.date) }.sumOf { it.amount }
    // Shop and business cards appear only once sales or purchases are used.
    val trading = entries.any { isTrade(it.type) }
    val todaySales = entries.filter { it.type == Type.SALE && sameDay(it.date, System.currentTimeMillis()) }.sumOf { it.amount }
    val monthSales = entries.filter { it.type == Type.SALE && inMonth(it.date) }.sumOf { it.amount }
    val monthPurchases = entries.filter { it.type == Type.PURCHASE && inMonth(it.date) }.sumOf { it.amount }
    val monthProfit = monthSales + monthIncome - monthPurchases - monthSpent
    val stockLines = if (trading) stock(entries).filter { kotlin.math.abs(it.qty) > 0.0001 } else emptyList()
    val bal = balances(entries)
    val receive = bal.values.filter { it > 0 }.sum()
    val pay = -bal.values.filter { it < 0 }.sum()
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
                        .height(160.dp)
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
                    Spacer(Modifier.height(18.dp))
                    Panel(Modifier.fillMaxWidth(), onClick = onReports, padding = 12.dp) {
                        Text("Spent in $monthName", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(moneyA(currency, monthSpent), fontSize = 32.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 36.sp, letterSpacing = (-0.5).sp)
                        Text(
                            "This year ${money(currency, yearSpent)}  ·  See report ›",
                            fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            MiniTile("To pay", moneyA(currency, pay), "−", OrangeOwe, Modifier.weight(1f)) { onPeople(false) }
                            MiniTile("To receive", moneyA(currency, receive), "+", GreenIn, Modifier.weight(1f)) { onPeople(true) }
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            MiniTile(
                                "Expenses", moneyA(currency, monthSpent), "−", RedSpend, Modifier.weight(1f),
                                valueSize = 18, valueColor = RedSpend, tinted = true
                            ) { onReports() }
                            MiniTile(
                                "Income", moneyA(currency, monthIncome), "+", GreenIn, Modifier.weight(1f),
                                valueSize = 18, valueColor = GreenIn, tinted = true
                            ) { onReports() }
                        }
                        if (trading) {
                            Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                MiniTile(
                                    "Sales today", moneyA(currency, todaySales), "🧾", Cat.SALES.let { catColor(it) }, Modifier.weight(1f),
                                    valueSize = 16
                                ) { onSeeAll() }
                                MiniTile(
                                    "Profit this month", moneyA(currency, monthProfit), if (monthProfit >= 0) "↑" else "↓",
                                    if (monthProfit >= 0) GreenIn else RedSpend, Modifier.weight(1f),
                                    valueSize = 16, valueColor = if (monthProfit >= 0) GreenIn else RedSpend
                                ) { onReports() }
                            }
                        }
                    }
                }
            }
        }

        if (bankCount > 0) {
            item {
                Panel(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                    onClick = onReviewBank, padding = 12.dp
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(36.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) { Text("🔔", fontSize = 18.sp) }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                "$bankCount bank " + (if (bankCount == 1) "payment" else "payments") + " found",
                                fontWeight = FontWeight.SemiBold
                            )
                            Text("Tap to review and add", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text("Review ›", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
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

        item { Spacer(Modifier.height(6.dp)) }

        val nowMs = System.currentTimeMillis()
        val sortedAll = entries.sortedWith(compareByDescending<Entry> { it.date }.thenByDescending { it.createdAt })
        val todayList = sortedAll.filter { sameDay(it.date, nowMs) }
        val dayGroups = sortedAll.filter { !sameDay(it.date, nowMs) && inMonth(it.date) }.groupBy { dayKey(it.date) }
        val monthGroups = sortedAll.filter { !inMonth(it.date) }.groupBy { monthKey(it.date) }

        if (stockLines.isNotEmpty()) {
            item {
                Box(Modifier.padding(horizontal = 16.dp).padding(top = 6.dp)) {
                    SectionHeader("Stock", stockLines.size, "stock" in open, null) { toggle("stock") }
                }
            }
            if ("stock" in open) {
                item {
                    Panel(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), padding = 12.dp) {
                        stockLines.sortedBy { it.name.lowercase() }.forEach { line ->
                            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text("📦", fontSize = 16.sp)
                                Spacer(Modifier.width(10.dp))
                                Text(line.name, Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                                Text(
                                    fmt(line.qty) + (if (line.unit.isBlank()) "" else " " + line.unit),
                                    fontWeight = FontWeight.Bold,
                                    color = if (line.qty < 0) RedSpend else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                        Text(
                            "Bought or received minus sold or given. Tap an entry to fix a quantity.",
                            fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        item {
            Box(Modifier.padding(horizontal = 16.dp).padding(top = 6.dp)) {
                SectionHeader("Today", todayList.size, "today" in open, spentLabel(currency, todayList)) { toggle("today") }
            }
        }
        if ("today" in open) {
            if (todayList.isEmpty()) {
                item {
                    Text(
                        if (entries.isEmpty()) "No entries yet. Tap the green mic and speak, or type below."
                        else "Nothing added today yet.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
                    )
                }
            }
            items(todayList, key = { it.id }) { e ->
                Box(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) { TxRow(e, currency) { onEdit(e) } }
            }
        }

        dayGroups.forEach { (key, list) ->
            item(key = "dh-$key") {
                Box(Modifier.padding(horizontal = 16.dp)) {
                    SectionHeader(dayLabel(list.first().date), list.size, key in open, spentLabel(currency, list)) { toggle(key) }
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
                var lastDay = ""
                list.forEach { e ->
                    val d = dayKey(e.date)
                    if (d != lastDay) {
                        lastDay = d
                        item(key = "md-$key-$d") {
                            Text(
                                dayLabel(e.date), fontWeight = FontWeight.SemiBold, fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 24.dp).padding(top = 8.dp)
                            )
                        }
                    }
                    item(key = e.id) {
                        Box(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) { TxRow(e, currency) { onEdit(e) } }
                    }
                }
            }
        }
    }
}

private fun dayKey(ms: Long): String = cal(ms).let { "d" + it.get(Calendar.YEAR) + "-" + it.get(Calendar.DAY_OF_YEAR) }
private fun monthKey(ms: Long): String = cal(ms).let { "m" + it.get(Calendar.YEAR) + "-" + it.get(Calendar.MONTH) }
private fun spentLabel(currency: String, list: List<Entry>): String? {
    val spent = list.filter { it.type == Type.KHARCHA }.sumOf { it.amount }
    return if (spent > 0) money(currency, spent) else null
}

@Composable
private fun MiniTile(
    label: String,
    value: AnnotatedString,
    glyph: String,
    color: Color,
    modifier: Modifier,
    valueSize: Int = 15,
    valueColor: Color = Color.Unspecified,
    tinted: Boolean = false,
    onClick: () -> Unit
) {
    Row(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(if (tinted) color.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant)
            .clickable { onClick() }
            .padding(start = 12.dp, end = 10.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, fontSize = 11.sp, lineHeight = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                value, fontWeight = FontWeight.Bold, fontSize = valueSize.sp, lineHeight = (valueSize + 4).sp,
                color = valueColor, maxLines = 1, overflow = TextOverflow.Ellipsis
            )
        }
        Box(
            Modifier.size(28.dp).clip(CircleShape).background(color.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center
        ) { Text(glyph, color = color, fontWeight = FontWeight.Bold) }
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

enum class TxFilter(val label: String) { ALL("All"), EXPENSE("Expenses"), INCOME("Income"), LOANS("Loans"), TRADE("Sales & purchases") }

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

@Composable
fun ReportsScreen(entries: List<Entry>, currency: String, onMenu: () -> Unit) {
    var span by remember { mutableStateOf(Span.MONTH) }
    var offset by remember { mutableIntStateOf(0) }
    var open by remember { mutableStateOf(setOf<Cat>()) }

    val (start, end, label) = spanRange(span, offset)
    val inRange = entries.filter { it.date in start until end }
    val spent = inRange.filter { it.type == Type.KHARCHA }
    val income = inRange.filter { it.type == Type.AAMDANI }.sumOf { it.amount }
    val total = spent.sumOf { it.amount }
    val byCat = spent.groupBy { it.category }
        .map { (c, list) -> Triple(c, list.sumOf { it.amount }, list) }
        .sortedByDescending { it.second }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item { ScreenTitle("Reports", onMenu = onMenu) }
        item {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(4.dp)
            ) {
                Span.entries.forEach { s ->
                    val sel = s == span
                    Box(
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (sel) MaterialTheme.colorScheme.primary else Color.Transparent)
                            .clickable { span = s; offset = 0 }
                            .padding(vertical = 9.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(s.label, color = if (sel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold)
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
        item {
            Box(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                val ring = MaterialTheme.colorScheme.surfaceVariant
                Canvas(Modifier.size(210.dp)) {
                    val stroke = 34.dp.toPx()
                    val inset = stroke / 2
                    val arcSize = androidx.compose.ui.geometry.Size(size.width - stroke, size.height - stroke)
                    val topLeft = androidx.compose.ui.geometry.Offset(inset, inset)
                    if (total <= 0) {
                        drawArc(ring, 0f, 360f, false, topLeft = topLeft, size = arcSize, style = Stroke(stroke))
                    } else {
                        var startAngle = -90f
                        byCat.forEach { (c, sum, _) ->
                            val sweep = (sum / total * 360f).toFloat()
                            drawArc(catColor(c), startAngle, sweep, false, topLeft = topLeft, size = arcSize, style = Stroke(stroke, cap = StrokeCap.Butt))
                            startAngle += sweep
                        }
                    }
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(moneyA(currency, total), fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
                    Text("Total expenses", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        if (income > 0) {
            item {
                Text(
                    "Income ${money(currency, income)}  ·  Saved ${money(currency, income - total)}",
                    color = if (income - total >= 0) GreenIn else RedSpend, fontSize = 13.sp,
                    modifier = Modifier.fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }
        if (byCat.isEmpty()) {
            item { Text("No expenses in this period.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        items(byCat, key = { it.first.name }) { (c, sum, list) ->
            val share = if (total > 0) sum / total else 0.0
            val expanded = c in open
            Panel(Modifier.fillMaxWidth(), onClick = { open = if (expanded) open - c else open + c }, padding = 12.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(12.dp).clip(CircleShape).background(catColor(c)))
                    Spacer(Modifier.width(10.dp))
                    Text("${c.icon} ${c.label}", Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                    Text("${(share * 100).roundToLong()}%", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(12.dp))
                    Text(moneyA(currency, sum), fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { share.toFloat() },
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp)),
                    color = catColor(c),
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
                val byItem = list.groupBy { it.item.ifBlank { c.label } }
                    .map { (k, v) -> k to v.sumOf { it.amount } }
                    .sortedByDescending { it.second }
                Spacer(Modifier.height(6.dp))
                byItem.forEach { (n, amt) ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                        Text("•  $n", Modifier.weight(1f), fontSize = 14.sp)
                        Text(fmt(amt), fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    }
                }
                if (expanded) {
                    HorizontalDivider(Modifier.padding(vertical = 6.dp))
                    list.sortedByDescending { it.date }.forEach { e ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                            Text(shortDate(e.date), Modifier.width(56.dp), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(e.note, Modifier.weight(1f), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(fmt(e.amount), fontSize = 12.sp)
                        }
                    }
                } else {
                    Text("Tap to see every entry", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
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
