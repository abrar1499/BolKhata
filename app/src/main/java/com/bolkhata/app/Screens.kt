package com.bolkhata.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
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
    onReports: (ReportKind) -> Unit,
    onSeeAll: () -> Unit,
    chatBadge: Int = 0,
    onTab: (Tab) -> Unit = {}
) {
    // Today and this year's months start open; older years start closed.
    // Today, this year and this month start open; days inside a month open on tap.
    var open by remember {
        val c = Calendar.getInstance()
        mutableStateOf(setOf("today", "y" + c.get(Calendar.YEAR), "m" + c.get(Calendar.YEAR) + "-" + c.get(Calendar.MONTH)))
    }
    // The five main accounts open their own screens (Expense / Income / To pay / To receive / Cash in hand).
    val openMain = LocalMainOpener.current
    fun toggle(k: String) {
        open = if (k in open) open - k else open + k
    }

    val now = Calendar.getInstance()
    val nowMs = System.currentTimeMillis()
    fun inMonth(ms: Long) = cal(ms).let { it.get(Calendar.YEAR) == now.get(Calendar.YEAR) && it.get(Calendar.MONTH) == now.get(Calendar.MONTH) }

    // Everything below is worked out once per change to the entries, not on every frame.
    val d = remember(entries) {
        val todayEntries = entries.filter { sameDay(it.date, nowMs) }
        val bal = balances(entries)
        val sortedAll = entries.sortedWith(compareByDescending<Entry> { it.date }.thenByDescending { it.createdAt })
        HomeData(
            // Only real spending and real income: money lent, borrowed or paid back is in To pay / To receive.
            monthSpent = entries.filter { it.type == Type.KHARCHA && inMonth(it.date) }.sumOf { it.amount },
            monthIncome = entries.filter { it.type == Type.AAMDANI && inMonth(it.date) }.sumOf { it.amount },
            trading = entries.any { isTrade(it.type) },
            todaySales = todayEntries.filter { it.type == Type.SALE }.sumOf { it.amount },
            todayBought = todayEntries.filter { it.type == Type.PURCHASE }.sumOf { it.amount },
            todayProfit = profitBreakdown(entries, todayEntries).let { it.costedSalesAmount - it.costOfGoods },
            receive = bal.values.filter { it > 0 }.sum(),
            pay = -bal.values.filter { it < 0 }.sum(),
            cashInHand = entries.filter { it.isCashInFlow() }.sumOf { it.amount } - entries.filter { it.isCashOutFlow() }.sumOf { it.amount },
            todayList = sortedAll.filter { sameDay(it.date, nowMs) },
            // Every day but today sits inside its year and month: 2026 -> October -> 27 Oct.
            monthGroups = sortedAll.filter { !sameDay(it.date, nowMs) }.groupBy { monthKey(it.date) }
        ).also { h ->
            h.monthDays = h.monthGroups.mapValues { m -> m.value.groupBy { dayKey(it.date) } }
            // Real expense and income per year (loans are not counted), for the year headings.
            h.yearTotals = entries.groupBy { cal(it.date).get(Calendar.YEAR) }.mapValues { (_, l) ->
                l.filter { it.type == Type.AAMDANI }.sumOf { it.amount } to l.filter { it.type == Type.KHARCHA }.sumOf { it.amount }
            }
        }
    }
    val monthSpent = d.monthSpent
    val monthIncome = d.monthIncome
    val trading = d.trading
    val todaySales = d.todaySales
    val todayBought = d.todayBought
    val todayProfit = d.todayProfit
    val receive = d.receive
    val pay = d.pay
    val cashInHand = d.cashInHand
    val hour = now.get(Calendar.HOUR_OF_DAY)
    val greeting = when {
        hour < 12 -> "Good morning"
        hour < 17 -> "Good afternoon"
        else -> "Good evening"
    }
    val monthName = SimpleDateFormat("MMMM", Locale.US).format(now.time)

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = barSpace())) {
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
                    Panel(Modifier.fillMaxWidth(), padding = 14.dp) { OverviewSummary(
                        monthName, currency, monthSpent, monthIncome, cashInHand, pay, receive,
                        onExpense = { openMain?.invoke(MainAcct.EXPENSE) ?: onReports(ReportKind.CASH_OUT) },
                        onIncome = { openMain?.invoke(MainAcct.INCOME) ?: onReports(ReportKind.CASH_IN) },
                        onCash = { onReports(ReportKind.CASH_BALANCE) },
                        onPay = { openMain?.invoke(MainAcct.TO_PAY) ?: onPeople(false) },
                        onReceive = { openMain?.invoke(MainAcct.TO_RECEIVE) ?: onPeople(true) }
                    ) }
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
                        BizFigure("Sales", fmt(todaySales), GreenIn, Modifier.weight(1f), icon = Icons.Filled.ArrowDownward)
                        BizFigure("Bought", fmt(todayBought), RedSpend, Modifier.weight(1f), icon = Icons.Filled.ArrowUpward)
                        BizFigure(
                            "Profit", (if (todayProfit > 0) "+" else "") + fmt(todayProfit),
                            MaterialTheme.colorScheme.onSurfaceVariant, Modifier.weight(1f),
                            valueColor = if (todayProfit >= 0) GreenIn else RedSpend
                        )
                        Text("Stock", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp)
                        )
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
                                if (progress >= 0) "Downloading… $progress%" else "Version 1.${newVersion.version} · your data stays safe",
                                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Button(onClick = onUpdate, enabled = progress < 0) { Text("Update") }
                    }
                }
            }
        }

        item { Spacer(Modifier.height(10.dp)) }

        val todayList = d.todayList
        val monthGroups = d.monthGroups

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
                Box(Modifier.padding(horizontal = 16.dp)) {
                    DailyCashHeader("Today", todayList.size, "today" in open, dailyCashSummary(entries, todayList), currency) { toggle("today") }
                }
            }
            if ("today" in open) {
                dayEntries(todayList, currency, onEdit)
            }
        }

        // Past months sit inside their year: "2026" (year in / out) -> "September" (month in / out) -> days.
        var lastYear = -1
        monthGroups.forEach { (key, list) ->
            val year = cal(list.first().date).get(Calendar.YEAR)
            val yKey = "y$year"
            // This year's months show straight away; a year gets its own heading only once it is over.
            val thisYear = year == Calendar.getInstance().get(Calendar.YEAR)
            if (year != lastYear && !thisYear) {
                lastYear = year
                val (yIn, yOut) = d.yearTotals[year] ?: (0.0 to 0.0)
                item(key = "yh-$year") {
                    Box(Modifier.padding(horizontal = 16.dp)) {
                        YearHeader(year.toString(), yIn, yOut, yKey in open) { toggle(yKey) }
                    }
                }
            }
            if (!thisYear && yKey !in open) return@forEach
            item(key = "mh-$key") {
                Box(Modifier.padding(horizontal = 16.dp)) {
                    MonthHeader(
                        SimpleDateFormat("MMMM", Locale.US).format(Date(list.first().date)),
                        list.size,
                        list.filter { it.type == Type.AAMDANI }.sumOf { it.amount },
                        list.filter { it.type == Type.KHARCHA }.sumOf { it.amount },
                        key in open
                    ) { toggle(key) }
                }
            }
            if (key in open) {
                (d.monthDays[key] ?: emptyMap()).forEach { (day, dayList) ->
                    val dKey = "day-$day"
                    item(key = "md-$key-$day") {
                        Box(Modifier.padding(horizontal = 16.dp)) {
                            val s = dailyCashSummary(entries, dayList)
                            DayLine(dayLabel(dayList.first().date), dayList.size, dKey in open, s.cashIn, s.cashOut) { toggle(dKey) }
                        }
                    }
                    if (dKey in open) dayEntries(dayList, currency, onEdit)
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
private fun BizFigure(
    label: String,
    value: String,
    labelColor: Color,
    modifier: Modifier,
    valueColor: Color = Color.Unspecified,
    icon: ImageVector? = null
) {
    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Box(
                    Modifier.size(18.dp).clip(CircleShape).background(labelColor.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) { Icon(icon, contentDescription = null, tint = labelColor, modifier = Modifier.size(12.dp)) }
                Spacer(Modifier.width(5.dp))
            }
            Text(label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = labelColor, maxLines = 1)
        }
        Spacer(Modifier.height(2.dp))
        Text(value, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = valueColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Small out/in pill used on day headers: an arrow icon and the amount. */
@Composable
private fun FlowChip(text: String, color: Color, icon: ImageVector) {
    Row(
        Modifier
            .clip(CircleShape)
            .background(color.copy(alpha = 0.10f))
            .padding(start = 7.dp, end = 10.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(3.dp))
        Text(text, color = color, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

private class HomeData(
    val monthSpent: Double,
    val monthIncome: Double,
    val trading: Boolean,
    val todaySales: Double,
    val todayBought: Double,
    val todayProfit: Double,
    val receive: Double,
    val pay: Double,
    val cashInHand: Double,
    val todayList: List<Entry>,
    val monthGroups: Map<String, List<Entry>>
) {
    var monthDays: Map<String, Map<String, List<Entry>>> = emptyMap()
    var yearTotals: Map<Int, Pair<Double, Double>> = emptyMap()
}

/** A year on the home list: the year, its total income and expense, open / close. */
@Composable
private fun YearHeader(title: String, income: Double, expense: Double, expanded: Boolean, onToggle: () -> Unit) {
    Row(
        Modifier
            .padding(top = 14.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.primaryContainer)
            .clickable { onToggle() }
            .padding(start = 14.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, color = MaterialTheme.colorScheme.onPrimaryContainer)
        Spacer(Modifier.weight(1f))
        InOutFigures(income, expense)
        Icon(
            if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
            contentDescription = if (expanded) "Close" else "Open",
            tint = MaterialTheme.colorScheme.onPrimaryContainer
        )
    }
}

/** A month: a premium card with a colour edge, the month's name, entry count, income and expense. */
@Composable
private fun MonthHeader(title: String, count: Int, income: Double, expense: Double, expanded: Boolean, onToggle: () -> Unit) {
    val primary = MaterialTheme.colorScheme.primary
    val tertiary = MaterialTheme.colorScheme.tertiary
    Row(
        Modifier
            .padding(top = 10.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface)
            .background(Brush.horizontalGradient(listOf(primary.copy(alpha = 0.10f), tertiary.copy(alpha = 0.04f))))
            .border(1.dp, primary.copy(alpha = if (expanded) 0.35f else 0.15f), RoundedCornerShape(14.dp))
            .clickable { onToggle() }
            .height(IntrinsicSize.Min),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.width(5.dp).fillMaxHeight().background(Brush.verticalGradient(listOf(primary, tertiary))))
        // One line: name, count, in / out — slim but still stands out from the days.
        Text(title, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, maxLines = 1,
            modifier = Modifier.padding(start = 12.dp, top = 9.dp, bottom = 9.dp))
        Spacer(Modifier.width(6.dp))
        Text("$count", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        Spacer(Modifier.weight(1f))
        if (expense > 0) MiniFlowChip(fmt(expense), RedSpend, Icons.Filled.ArrowUpward, 13)
        if (expense > 0 && income > 0) Spacer(Modifier.width(5.dp))
        if (income > 0) MiniFlowChip(fmt(income), GreenIn, Icons.Filled.ArrowDownward, 13)
        Icon(
            if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
            contentDescription = if (expanded) "Close" else "Open",
            tint = primary, modifier = Modifier.padding(start = 4.dp, end = 8.dp).size(20.dp)
        )
    }
}

/** The small out / in pill (arrow icon + amount) used on month and day lines, like the day headers. */
@Composable
private fun MiniFlowChip(text: String, color: Color, icon: ImageVector, size: Int) {
    Row(
        Modifier.clip(CircleShape).background(color.copy(alpha = 0.10f)).padding(start = 5.dp, end = 8.dp, top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size((size - 1).dp))
        Spacer(Modifier.width(2.dp))
        Text(text, color = color, fontSize = size.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

/** A day inside a month: one slim line (date, count, small in / out), opens on tap. */
@Composable
private fun DayLine(title: String, count: Int, expanded: Boolean, cashIn: Double, cashOut: Double, onToggle: () -> Unit) {
    val (bar, ink, sub) = slateColors()
    val shape = if (expanded) RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp) else RoundedCornerShape(12.dp)
    Row(
        Modifier
            .padding(top = 6.dp)
            .fillMaxWidth()
            .clip(shape)
            .background(bar.copy(alpha = 0.7f))
            .clickable { onToggle() }
            .padding(start = 12.dp, end = 6.dp, top = 7.dp, bottom = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = ink, maxLines = 1)
        Spacer(Modifier.width(6.dp))
        Text("$count", fontSize = 11.sp, color = sub, maxLines = 1)
        Spacer(Modifier.weight(1f))
        if (cashOut > 0) MiniFlowChip(fmt(cashOut), RedSpend, Icons.Filled.ArrowUpward, 12)
        if (cashOut > 0 && cashIn > 0) Spacer(Modifier.width(5.dp))
        if (cashIn > 0) MiniFlowChip(fmt(cashIn), GreenIn, Icons.Filled.ArrowDownward, 12)
        Icon(
            if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
            contentDescription = if (expanded) "Close" else "Open", tint = sub, modifier = Modifier.size(18.dp).padding(start = 2.dp)
        )
    }
}

/** Income (green, in) and expense (red, out) chips; a zero side is left out. */
@Composable
private fun InOutFigures(income: Double, expense: Double) {
    if (income > 0) FlowChip(fmt(income), GreenIn, Icons.Filled.ArrowDownward)
    if (income > 0 && expense > 0) Spacer(Modifier.width(6.dp))
    if (expense > 0) FlowChip(fmt(expense), RedSpend, Icons.Filled.ArrowUpward)
    Spacer(Modifier.width(4.dp))
}

private fun dayKey(ms: Long): String = cal(ms).let { "d" + it.get(Calendar.YEAR) + "-" + it.get(Calendar.DAY_OF_YEAR) }
private fun monthKey(ms: Long): String = cal(ms).let { "m" + it.get(Calendar.YEAR) + "-" + it.get(Calendar.MONTH) }

private data class DailyCashSummary(
    val cashIn: Double,
    val cashOut: Double,
    val cashInHand: Double
)

/** Running cash balance through the selected local calendar day; only recorded cash flows are counted. */
private fun dailyCashSummary(allEntries: List<Entry>, dayEntries: List<Entry>): DailyCashSummary {
    // Only the day's own in and out are shown, so the running balance is not worked out here (it made long lists slow).
    val cashIn = dayEntries.filter { it.isCashInFlow() }.sumOf { it.amount }
    val cashOut = dayEntries.filter { it.isCashOutFlow() }.sumOf { it.amount }
    return DailyCashSummary(cashIn, cashOut, 0.0)
}

/** Neutral slate tones for day bars, so the red and green money chips stand out on any header theme. */
@Composable
private fun slateColors(): Triple<Color, Color, Color> {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    return if (dark) Triple(Color(0xFF2A313B), Color(0xFFE5E9EF), Color(0xFFAAB3BF))
    else Triple(Color(0xFFECEFF3), Color(0xFF1F2A37), Color(0xFF4B5563))
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
    // A day is a slate bar, never a white card, so it can't be mistaken for an entry.
    // An open day's bar is the top of one card that holds its entries; a closed day is a single bar.
    val (bar, ink, sub) = slateColors()
    val shape = if (expanded) RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp) else RoundedCornerShape(16.dp)
    Row(
        Modifier
            .padding(top = if (expanded) 12.dp else 8.dp)
            .fillMaxWidth()
            .clip(shape)
            .background(bar)
            .then(if (onToggle != null) Modifier.clickable { onToggle() } else Modifier)
            .padding(start = 14.dp, end = 8.dp, top = 9.dp, bottom = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, color = ink, maxLines = 1)
        Spacer(Modifier.width(8.dp))
        Text(
            if (count == 1) "1 entry" else "$count entries",
            fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = sub, maxLines = 1
        )
        Spacer(Modifier.weight(1f))
        // Money out points up (red), money in points down (green). Only what happened that day is shown.
        if (summary.cashOut > 0) FlowChip(fmt(summary.cashOut), RedSpend, Icons.Filled.ArrowUpward)
        if (summary.cashOut > 0 && summary.cashIn > 0) Spacer(Modifier.width(6.dp))
        if (summary.cashIn > 0) FlowChip(fmt(summary.cashIn), GreenIn, Icons.Filled.ArrowDownward)
        if (onToggle != null) {
            Spacer(Modifier.width(2.dp))
            Icon(
                if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                contentDescription = if (expanded) "Close" else "Open",
                tint = ink
            )
        } else {
            Spacer(Modifier.width(6.dp))
        }
    }
}

/** All entries of one day inside a single card, with thin lines between them. */
private fun LazyListScope.dayEntries(list: List<Entry>, currency: String, onEdit: (Entry) -> Unit) {
    itemsIndexed(list, key = { _, e -> e.id }) { i, e ->
        GroupedTxRow(e, currency, first = i == 0, last = i == list.lastIndex) { onEdit(e) }
    }
}

@Composable
private fun GroupedTxRow(e: Entry, currency: String, first: Boolean, last: Boolean, onClick: () -> Unit) {
    val top = 0.dp
    val bottom = if (last) 20.dp else 0.dp
    val shape = RoundedCornerShape(topStart = top, topEnd = top, bottomStart = bottom, bottomEnd = bottom)
    Column(
        Modifier
            .padding(horizontal = 16.dp)
            .padding(bottom = if (last) 6.dp else 0.dp)
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .entryTap(e, onClick)
    ) {
        if (!first) {
            HorizontalDivider(
                Modifier.padding(start = 59.dp),
                thickness = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
            )
        }
        TxRowContent(e, currency, showDate = false)
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
    val shown = remember(entries, filter, q) { entries
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
        .sortedWith(compareByDescending<Entry> { it.date }.thenByDescending { it.createdAt }) }
    // Month headings worked out once per list, with one date formatter, not one per row.
    val monthOf = remember(shown) {
        val f = SimpleDateFormat("MMMM yyyy", Locale.US)
        shown.map { f.format(Date(it.date)) }
    }
    val total = shown.sumOf { if (it.type == Type.KHARCHA || it.type == Type.WAPIS_DIYA) -it.amount else if (it.type == Type.AAMDANI || it.type == Type.WAPIS_MILA) it.amount else 0.0 }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = barSpace(16.dp)),
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
        shown.forEachIndexed { i, e ->
            val m = monthOf[i]
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
    STOCK("Stock"), PROFIT("Profit"), CASH_BALANCE("Cash Balance"), DUES("Dues")
}

private fun Entry.isCashInFlow(): Boolean = when (type) {
    Type.AAMDANI, Type.WAPIS_MILA, Type.LIYA, Type.TRANSFER_IN -> true
    Type.SALE -> person.isBlank() || paid
    else -> false
}

private fun Entry.isCashOutFlow(): Boolean = when (type) {
    Type.KHARCHA, Type.WAPIS_DIYA, Type.DIYA, Type.TRANSFER_OUT -> true
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
    Type.TRANSFER_IN, Type.TRANSFER_OUT -> Cat.OTHER
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
    entries.groupBy { if (isTransfer(it.type)) "TRANSFERS" else cashFlowCategory(it).name }
        .map { (key, list) ->
            val transfer = key == "TRANSFERS"
            val category = if (transfer) Cat.OTHER else Cat.valueOf(key)
            ReportGroup(
                key = key,
                title = if (transfer) "Transfers" else cashFlowGroupTitle(category, kind),
                icon = if (transfer) "🔁" else category.icon,
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
                ReportLine(e, detail, currency)
            }
        } else {
            Text("Tap to see every entry", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/**
 * The month at a glance: what was spent (red) in medium-large figures, then income and cash in hand on one line,
 * and what is owed each way. Every figure opens its account or report.
 */
@Composable
private fun OverviewSummary(
    monthName: String, currency: String, spent: Double, income: Double, cash: Double, pay: Double, receive: Double,
    onExpense: () -> Unit, onIncome: () -> Unit, onCash: () -> Unit, onPay: () -> Unit, onReceive: () -> Unit
) {
    val faint = MaterialTheme.colorScheme.onSurfaceVariant
    Column(Modifier.fillMaxWidth()) {
        Column(Modifier.clip(RoundedCornerShape(14.dp)).clickable(onClick = onExpense).padding(vertical = 4.dp, horizontal = 2.dp)) {
            Text("$monthName spent", fontSize = 13.sp, color = faint)
            Text(
                buildAnnotatedString {
                    append(fmt(spent))
                    withStyle(SpanStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium, color = faint)) { append(" $currency") }
                },
                fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold, color = RedSpend, maxLines = 1
            )
        }
        Row(Modifier.padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Income " + fmt(income), fontSize = 13.sp, color = GreenIn, fontWeight = FontWeight.Medium,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onIncome).padding(2.dp))
            Text("  ·  ", fontSize = 13.sp, color = faint)
            Text("Cash in hand " + fmt(cash), fontSize = 13.sp, color = if (cash < 0) RedSpend else faint,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onCash).padding(2.dp))
        }
        Spacer(Modifier.height(12.dp))
        Row(
            Modifier.fillMaxWidth().height(IntrinsicSize.Min).clip(RoundedCornerShape(16.dp))
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
        ) {
            OweCell("To pay", pay, OrangeOwe, Modifier.weight(1f), onPay)
            Box(Modifier.width(1.dp).fillMaxHeight().background(MaterialTheme.colorScheme.outlineVariant))
            OweCell("To receive", receive, GreenIn, Modifier.weight(1f), onReceive)
        }
    }
}

@Composable
private fun OweCell(label: String, amount: Double, color: Color, modifier: Modifier, onClick: () -> Unit) {
    Column(modifier.clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 10.dp)) {
        Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(fmt(amount), fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** One entry under a report row: date, what it was, amount. */
@Composable
private fun ReportLine(e: Entry, detail: String, currency: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(shortDate(e.date), Modifier.width(56.dp), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(detail, Modifier.weight(1f), fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(moneyA(currency, e.amount), fontSize = 12.sp)
    }
}

/** Everything a report shows, worked out once per change of entries or period (not on every frame). */
private class ReportData(
    val start: Long,
    val end: Long,
    val label: String,
    val startDay: Calendar,
    val inRange: List<Entry>,
    val cashInEntries: List<Entry>,
    val cashOutEntries: List<Entry>,
    val cashInTotal: Double,
    val cashOutTotal: Double,
    val allCashIn: Double,
    val allCashOut: Double,
    val cashBalance: Double,
    val sales: List<Entry>,
    val purchases: List<Entry>,
    val totalSales: Double,
    val totalPurchases: Double,
    val salesGroups: List<ReportGroup>,
    val purchaseGroups: List<ReportGroup>,
    val cashInGroups: List<ReportGroup>,
    val cashOutGroups: List<ReportGroup>,
    val currentStock: List<StockLine>,
    val stockByName: Map<String, StockLine>,
    val stockNames: List<String>,
    val stockRows: List<StockReportRow>,
    val receivedQty: Double,
    val givenQty: Double,
    val profit: ProfitBreakdown,
    val outstanding: List<Pair<String, Double>>,
    val toReceive: Double,
    val toPay: Double
)

private fun reportData(entries: List<Entry>, span: Span, offset: Int): ReportData {
    val (start, end, label) = spanRange(span, offset)
    // Compared by calendar day, the same way the home screen counts the month.
    val startDay = cal(start)
    val inRange = entries.filter { e ->
        val d = cal(e.date)
        when (span) {
            Span.MONTH -> d.get(Calendar.YEAR) == startDay.get(Calendar.YEAR) && d.get(Calendar.MONTH) == startDay.get(Calendar.MONTH)
            Span.YEAR -> d.get(Calendar.YEAR) == startDay.get(Calendar.YEAR)
            Span.WEEK -> e.date >= start && e.date < end
        }
    }
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
    return ReportData(start, end, label, startDay, inRange, cashInEntries, cashOutEntries, cashInTotal, cashOutTotal, allCashIn, allCashOut, cashBalance, sales, purchases, totalSales, totalPurchases, salesGroups, purchaseGroups, cashInGroups, cashOutGroups, currentStock, stockByName, stockNames, stockRows, receivedQty, givenQty, profit, outstanding, toReceive, toPay)
}

@Composable
fun ReportsScreen(entries: List<Entry>, currency: String, onMenu: () -> Unit, startKind: ReportKind = ReportKind.CASH_OUT) {
    var span by remember { mutableStateOf(Span.MONTH) }
    var offset by remember { mutableIntStateOf(0) }
    var reportKind by remember(startKind) { mutableStateOf(startKind) }
    var openGroups by remember { mutableStateOf(setOf<String>()) }

    val rd = remember(entries, span, offset) { reportData(entries, span, offset) }
    val start = rd.start
    val end = rd.end
    val label = rd.label
    val startDay = rd.startDay
    val inRange = rd.inRange
    val cashInEntries = rd.cashInEntries
    val cashOutEntries = rd.cashOutEntries
    val cashInTotal = rd.cashInTotal
    val cashOutTotal = rd.cashOutTotal
    val allCashIn = rd.allCashIn
    val allCashOut = rd.allCashOut
    val cashBalance = rd.cashBalance
    val sales = rd.sales
    val purchases = rd.purchases
    val totalSales = rd.totalSales
    val totalPurchases = rd.totalPurchases
    val salesGroups = rd.salesGroups
    val purchaseGroups = rd.purchaseGroups
    val cashInGroups = rd.cashInGroups
    val cashOutGroups = rd.cashOutGroups
    val currentStock = rd.currentStock
    val stockByName = rd.stockByName
    val stockNames = rd.stockNames
    val stockRows = rd.stockRows
    val receivedQty = rd.receivedQty
    val givenQty = rd.givenQty
    val profit = rd.profit
    val outstanding = rd.outstanding
    val toReceive = rd.toReceive
    val toPay = rd.toPay

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = barSpace(16.dp)), verticalArrangement = Arrangement.spacedBy(10.dp)) {
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
                        Text(moneyA(currency, cashOutTotal), fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = RedSpend)
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
                        Text(moneyA(currency, cashInTotal), fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = GreenIn)
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
                        Text(moneyA(currency, totalSales), fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = GreenIn)
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
                        Text(moneyA(currency, totalPurchases), fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = catColor(Cat.STOCK))
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
                                ReportLine(e, detail, currency)
                            }
                        } else Text("Tap to see stock movements", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            ReportKind.PROFIT -> {
                item {
                    Panel(Modifier.fillMaxWidth(), padding = 16.dp) {
                        Text("Estimated net profit · $label", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(moneyA(currency, profit.netProfit), fontSize = 22.sp, fontWeight = FontWeight.ExtraBold,
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
                        Text(moneyA(currency, cashBalance), fontSize = 22.sp, fontWeight = FontWeight.ExtraBold,
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
                                ReportLine(e, detail, currency)
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
        .toList().sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.first.trim() })
    val total = bal.sumOf { abs(it.second) }
    val color = if (receive) GreenIn else OrangeOwe
    var open by remember { mutableStateOf(setOf<String>()) }
    val listState = rememberLazyListState()

    LazyColumn(
        Modifier.fillMaxSize(),
        state = listState,
        contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = barSpace(16.dp)),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item { ScreenTitle(if (receive) "To receive" else "To pay", onBack) }
        item {
            Panel(Modifier.fillMaxWidth(), padding = 16.dp) {
                Text(if (receive) "People owe you" else "You owe people", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                Text(moneyA(currency, total), fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = color)
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

/**
 * Lets the owner send every chat question and answer to their own private GitHub repository, where
 * wrong answers are reviewed and fixed in later updates. Off until a token is saved here.
 */
@Composable
private fun ChatReviewPanel(
    repo: String,
    tokenSet: Boolean,
    status: String,
    onSave: (repo: String, token: String) -> Unit,
    onSendNow: () -> Unit,
    onOff: () -> Unit
) {
    var repoText by remember(repo) { mutableStateOf(repo) }
    var tokenText by remember { mutableStateOf("") }
    Panel(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SmallLabel("🛠 Chat review")
            Spacer(Modifier.weight(1f))
            Text(
                if (tokenSet) "On" else "Off", fontWeight = FontWeight.Bold,
                color = if (tokenSet) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            "When on, every chat question and answer is sent to your own private GitHub repository so mistakes can be found and fixed in the next update. Chat text may include amounts and names; your khata records and contacts are not sent.",
            fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = repoText, onValueChange = { repoText = it },
            label = { Text("Private repository (owner/name)") },
            singleLine = true, modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(6.dp))
        OutlinedTextField(
            value = tokenText, onValueChange = { tokenText = it },
            label = { Text(if (tokenSet) "Token saved · paste to replace" else "GitHub token") },
            singleLine = true,
            visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Button(
                onClick = { onSave(repoText, tokenText); tokenText = "" },
                enabled = tokenText.isNotBlank() && repoText.contains('/')
            ) { Text("Save") }
            if (tokenSet) {
                Spacer(Modifier.width(8.dp))
                OutlinedButton(onClick = onSendNow) { Text("Send now") }
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = onOff) { Text("Turn off") }
            }
        }
        if (status.isNotBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(status, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

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
    onExport: () -> Unit = {},
    onImport: () -> Unit = {},
    logRepo: String = "",
    logTokenSet: Boolean = false,
    logStatus: String = "",
    onLogSave: (repo: String, token: String) -> Unit = { _, _ -> },
    onLogSendNow: () -> Unit = {},
    onLogOff: () -> Unit = {},
    groqOn: Boolean = false,
    groqVoice: Boolean = true,
    onGroqSave: (String) -> Unit = {},
    onGroqVoice: (Boolean) -> Unit = {},
    onGroqOff: () -> Unit = {},
    elevenOn: Boolean = false,
    elevenVoice: String = "",
    onElevenSave: (key: String, voice: String) -> Unit = { _, _ -> },
    onElevenOff: () -> Unit = {},
) {
    var dialog by remember { mutableStateOf<String?>(null) }   // "name", "theme", "groq", "eleven" or "review"
    val faint = MaterialTheme.colorScheme.onSurfaceVariant
    val langLabel = when {
        lang == "ur-PK" -> "Urdu"
        lang.startsWith("en") -> "English"
        else -> "Roman Urdu"
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = barSpace())) {
        item {
            Row(Modifier.fillMaxWidth().padding(start = 4.dp, top = 14.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onMenu) { Icon(Icons.Filled.Menu, contentDescription = "Menu") }
                Text("Settings", fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        item {
            Surface(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp), shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface
            ) {
                Column {
                    ListRow("Name", name.ifBlank { "Add" }) { dialog = "name" }
                    ListDivider()
                    Box {
                        var open by remember { mutableStateOf(false) }
                        ListRow("Currency", "$currency ▾") { open = true }
                        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                            CURRENCIES.forEach { c -> DropdownMenuItem(text = { Text(c) }, onClick = { onCurrency(c); open = false }) }
                        }
                    }
                    ListDivider()
                    Box {
                        var open by remember { mutableStateOf(false) }
                        ListRow("Language", "$langLabel ▾") { open = true }
                        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                            listOf("Roman Urdu" to "hi-IN", "English" to "en-US", "Urdu" to "ur-PK").forEach { (label, code) ->
                                DropdownMenuItem(text = { Text(label) }, onClick = { onLang(code); open = false })
                            }
                        }
                    }
                    ListDivider()
                    ListRow("Dark mode", null, trailing = { Switch(checked = dark, onCheckedChange = onDark) }) { onDark(!dark) }
                    ListDivider()
                    ListRow("Theme", themeName) { dialog = "theme" }
                    ListDivider()
                    ListRow("Bank alerts", null, trailing = { Switch(checked = bankOn, onCheckedChange = { onBank() }) }, onClick = onBank)
                    ListDivider()
                    ListRow("Groq AI", if (groqOn) "On" else "Off", valueColor = if (groqOn) GreenIn else faint) { dialog = "groq" }
                    ListDivider()
                    ListRow("Call voice", if (elevenOn) "ElevenLabs" else "Phone", valueColor = if (elevenOn) GreenIn else faint) { dialog = "eleven" }
                    ListDivider()
                    ListRow("Chat review", if (logTokenSet) "On" else "Off", valueColor = if (logTokenSet) GreenIn else faint) { dialog = "review" }
                    ListDivider()
                    Box {
                        var open by remember { mutableStateOf(false) }
                        ListRow("Backup", "Export · Import") { open = true }
                        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                            DropdownMenuItem(text = { Text("Export data") }, onClick = { open = false; onExport() })
                            DropdownMenuItem(text = { Text("Import data") }, onClick = { open = false; onImport() })
                        }
                    }
                    ListDivider()
                    ListRow("Version", if (checking) "Checking…" else version) { if (!checking) onCheckUpdate() }
                }
            }
        }
        item {
            Text(
                "Tap Version to check for updates. Your entries stay on this phone and the assistant works offline; " +
                    "chat leaves the phone only when Groq AI or Chat review is on.",
                fontSize = 12.sp, color = faint, modifier = Modifier.padding(horizontal = 24.dp, vertical = 14.dp)
            )
        }
    }

    when (dialog) {
        "name" -> {
            var text by remember { mutableStateOf(name) }
            AlertDialog(
                onDismissRequest = { dialog = null },
                title = { Text("Your name") },
                text = { OutlinedTextField(text, { text = it }, singleLine = true, modifier = Modifier.fillMaxWidth()) },
                confirmButton = { TextButton(onClick = { onName(text.trim()); dialog = null }, enabled = text.trim() != name) { Text("Save") } },
                dismissButton = { TextButton(onClick = { dialog = null }) { Text("Cancel") } }
            )
        }
        "theme" -> AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text("Theme") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    ThemeGroup("Light", HEADER_THEMES.filter { it.light }, themeName) { onTheme(it); dialog = null }
                    Spacer(Modifier.height(8.dp))
                    ThemeGroup("Bold & dark", HEADER_THEMES.filter { !it.light }, themeName) { onTheme(it); dialog = null }
                }
            },
            confirmButton = { TextButton(onClick = { dialog = null }) { Text("Close") } }
        )
        "groq" -> {
            var key by remember { mutableStateOf("") }
            AlertDialog(
                onDismissRequest = { dialog = null },
                title = { Text("Groq AI") },
                text = {
                    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            "Optional. Every chat message is first corrected by Groq (spelling, Urdu script, names), then the " +
                                "app answers from your own records. Groq never answers or changes amounts. Only the message, the " +
                                "app's last reply and a few similar khata names are sent, never your entries. Needs internet; " +
                                "without it the app works as before.",
                            fontSize = 13.sp, color = faint
                        )
                        OutlinedTextField(
                            key, { key = it }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                            label = { Text(if (groqOn) "Key saved · paste to replace" else "Groq API key") },
                            visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation()
                        )
                        Text("Get a free key at console.groq.com → API Keys.", fontSize = 12.sp, color = faint)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Groq voice for the mic and call", fontSize = 15.sp)
                                Text("Whisper hears Urdu and mixed speech better", fontSize = 12.sp, color = faint)
                            }
                            Switch(checked = groqVoice, onCheckedChange = onGroqVoice, enabled = groqOn)
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { onGroqSave(key.trim()); dialog = null }, enabled = key.trim().length > 20) { Text("Save") }
                },
                dismissButton = {
                    Row {
                        if (groqOn) TextButton(onClick = { onGroqOff(); dialog = null }) { Text("Turn off") }
                        TextButton(onClick = { dialog = null }) { Text("Close") }
                    }
                }
            )
        }
        "eleven" -> {
            var key by remember { mutableStateOf("") }
            var voiceId by remember { mutableStateOf(elevenVoice) }
            AlertDialog(
                onDismissRequest = { dialog = null },
                title = { Text("Call voice") },
                text = {
                    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            "Optional. The voice call answers in a real ElevenLabs voice. Only the short spoken answer is " +
                                "sent, never your entries. Without internet or credits the phone's voice is used.",
                            fontSize = 13.sp, color = faint
                        )
                        OutlinedTextField(
                            key, { key = it }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                            label = { Text(if (elevenOn) "Key saved · paste to replace" else "ElevenLabs API key") },
                            visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation()
                        )
                        OutlinedTextField(
                            voiceId, { voiceId = it }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                            label = { Text("Voice ID") }
                        )
                        Text("elevenlabs.io → API Keys for the key; Voice Library → a voice → copy its Voice ID.", fontSize = 12.sp, color = faint)
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = { onElevenSave(key.trim(), voiceId.trim()); dialog = null },
                        enabled = voiceId.trim().length >= 10 && (elevenOn || key.trim().length > 20)
                    ) { Text("Save") }
                },
                dismissButton = {
                    Row {
                        if (elevenOn) TextButton(onClick = { onElevenOff(); dialog = null }) { Text("Turn off") }
                        TextButton(onClick = { dialog = null }) { Text("Close") }
                    }
                }
            )
        }
        "review" -> AlertDialog(
            onDismissRequest = { dialog = null },
            text = { Column(Modifier.verticalScroll(rememberScrollState())) { ChatReviewPanel(logRepo, logTokenSet, logStatus, onLogSave, onLogSendNow, onLogOff) } },
            confirmButton = { TextButton(onClick = { dialog = null }) { Text("Close") } }
        )
    }
}

/** One line of the Settings list: name on the left, current value (or a switch) on the right. */
@Composable
private fun ListRow(
    label: String, value: String?, valueColor: Color = Color.Unspecified,
    trailing: (@Composable () -> Unit)? = null, onClick: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).heightIn(min = 54.dp).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = 16.sp, modifier = Modifier.weight(1f))
        if (value != null) Text(
            value, fontSize = 14.sp, maxLines = 1,
            color = if (valueColor == Color.Unspecified) MaterialTheme.colorScheme.onSurfaceVariant else valueColor
        )
        trailing?.invoke()
    }
}

@Composable
private fun ListDivider() = HorizontalDivider(Modifier.padding(start = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)


@OptIn(ExperimentalLayoutApi::class)
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
        contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = barSpace(16.dp)),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            ScreenTitle(
                when {
                    person != null -> "$person's account"
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
                        fontSize = 22.sp, fontWeight = FontWeight.ExtraBold,
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
        contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = barSpace(16.dp)),
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
