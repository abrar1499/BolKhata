@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package com.bolkhata.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.NoteAdd
import androidx.compose.material.icons.outlined.People
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
    Type.KHARCHA -> !isWork(e)
    Type.DIYA, Type.WAPIS_DIYA, Type.TRANSFER_OUT -> true
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

/** The book for home money: kept apart so it never counts as a business cost. */
const val PERSONAL = "personal"

/** The sections of each book, shown as chips under the search box. */
val BUSINESS_SECTIONS = listOf("Cashbook", "Parties", "Stock", "Production", "Profit")
val PERSONAL_SECTIONS = listOf("Cashbook", "Expense", "Income")

/** Bottom bar: Chat · Business · Personal · + (the one place to add an entry). */
@Composable
fun KhataNav(tab: Tab, personal: Boolean, onChat: () -> Unit, onBusiness: () -> Unit, onPersonal: () -> Unit, onAdd: () -> Unit) {
    // No shadow: it joins the white chat strip above into one bottom panel.
    Surface(color = MaterialTheme.colorScheme.surface) {
        Row(Modifier.fillMaxWidth().navigationBarsPadding().height(58.dp)) {
            NavItem("Chat", Icons.Outlined.ChatBubbleOutline, tab == Tab.CHAT, onChat)
            NavItem("Business", Icons.Outlined.Storefront, tab != Tab.CHAT && !personal, onBusiness)
            NavItem("Personal", Icons.Outlined.Home, tab != Tab.CHAT && personal, onPersonal)
            Column(
                Modifier.weight(1f).fillMaxHeight().clickable { onAdd() },
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center
            ) {
                Box(Modifier.size(34.dp).clip(CircleShape).background(Ember), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Add, contentDescription = "Add entry", tint = Color.White, modifier = Modifier.size(22.dp))
                }
                Text("Entry", fontSize = 10.sp, color = Ember, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun RowScope.NavItem(label: String, icon: ImageVector, on: Boolean, onClick: () -> Unit) {
    val tint = if (on) Ember else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
    Column(
        Modifier.weight(1f).fillMaxHeight().clickable { onClick() },
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center
    ) {
        Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(22.dp))
        Text(label, fontSize = 10.sp, color = tint, fontWeight = if (on) FontWeight.Bold else FontWeight.Medium)
    }
}

/** The section chips under the search box (Cashbook · Parties · Stock… or Cashbook · Expense · Income). */
@Composable
fun SectionChips(names: List<String>, selected: String, onPick: (String) -> Unit) {
    LazyRow(contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        items(names) { n -> SmallChip(n, n == selected) { onPick(n) } }
    }
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
    business: String? = null, onBusiness: () -> Unit = {}, card: @Composable () -> Unit
) {
    val theme = LocalHeader.current
    val banded = theme.name != "Clean"
    val ink = if (banded) theme.on else MaterialTheme.colorScheme.onSurface
    // The theme band runs from the top of the screen to the middle of the summary card.
    var cardH by remember { mutableIntStateOf(0) }
    Box(Modifier.fillMaxWidth()
        .then(if (banded) Modifier.drawBehind { drawBand(theme, size.height - cardH / 2f) } else Modifier)) {
        Column(Modifier.statusBarsPadding().padding(horizontal = 12.dp)) {
            if (business != null) BusinessPill(business, onMenu, onBusiness) else
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

/** Compact frosted pill at the top: menu, then the open business with ▾ to switch or add one. */
@Composable
internal fun BusinessPill(name: String, onMenu: () -> Unit, onBusiness: () -> Unit) {
    Row(
        Modifier.padding(top = 12.dp).shadow(1.dp, CircleShape).clip(CircleShape).background(Color.White).padding(end = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // The app's logo opens the side menu.
        Box(Modifier.size(38.dp).clip(CircleShape).clickable(onClickLabel = "Menu") { onMenu() }, contentAlignment = Alignment.Center) {
            GlassLogo(24.dp)
        }
        Row(Modifier.clickable { onBusiness() }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
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
        shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surface, shadowElevation = 1.dp
    ) {
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).padding(vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
            listOf(a, b).forEachIndexed { i, (value, label, color) ->
                if (i > 0) VerticalDivider(Modifier.fillMaxHeight(), color = MaterialTheme.colorScheme.outlineVariant)
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(value, color = color, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
                    Text(label, fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            VerticalDivider(Modifier.fillMaxHeight(), color = MaterialTheme.colorScheme.outlineVariant)
            Text(
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

// ---------------------------------------------------------------- Business: Parties, Stock, Sale, Purchase, Profit

private val BusinessViews = listOf("Parties", "Stock", "Sale", "Purchase", "Profit", "Production")

/** The entries a Business view is about, newest first; their months make that view's month chips. */
internal fun aboutView(view: Int, newest: List<Entry>) = when (view) {
    0 -> newest.filter { it.person.isNotBlank() }
    1 -> newest.filter { (it.type == Type.SALE || it.type == Type.PURCHASE) && it.item.isNotBlank() }
    2, 4 -> newest.filter { it.type == Type.SALE }
    3 -> newest.filter { it.type == Type.PURCHASE }
    else -> newest.filter { it.type == Type.KHARCHA && it.category == Cat.PRODUCTION }
}

/** Sale, Purchase and Production open on their newest month like the Cash book; the other views on All. */
private fun firstMonth(view: Int, newest: List<Entry>) =
    if (view == 2 || view == 3 || view == 5) monthsOf(aboutView(view, newest)).firstOrNull()?.first ?: ALL_MONTHS else ALL_MONTHS

private fun yearOf(ms: Long) = cal(ms).get(Calendar.YEAR)

/** Profit of some sales: sales with a known cost minus that cost (same rule as the Profit report). */
private fun profitOf(all: List<Entry>, sales: List<Entry>) = profitBreakdown(all, sales).let { it.costedSalesAmount - it.costOfGoods }

internal fun plusMinus(v: Double) = (if (v > 0.005) "+" else if (v < -0.005) "−" else "") + fmt(kotlin.math.abs(v))

/**
 * The Business tab. Chips switch between Parties (who owes whom), Stock (what is left and what it is worth), Sale and
 * Purchase (ledgers like the Cash book) and Profit (month by month). Every view has the month chips; All lists everything.
 */
@Composable
fun PartiesScreen(
    entries: List<Entry>, currency: String, onMenu: () -> Unit, business: String, onBusiness: () -> Unit,
    onOpen: (PersonAcct) -> Unit, onReport: (ReportKind) -> Unit, onAdd: (Entry) -> Unit, onEdit: (Entry) -> Unit,
    /** Units made in a month (Production) and saving it. */
    made: (Int) -> Double = { 0.0 }, onSetMade: (Int, Double) -> Unit = { _, _ -> },
    /** The section chip to open on, and what to do when Cashbook (another screen) is tapped. */
    section: String = "Parties", onSection: (String) -> Unit = {}
) {
    val newest = remember(entries) { entries.sortedWith(compareByDescending<Entry> { it.date }.thenByDescending { it.createdAt }) }
    var view by rememberSaveable(section) { mutableIntStateOf(BusinessViews.indexOf(section).coerceAtLeast(0)) }
    var month by rememberSaveable { mutableIntStateOf(ALL_MONTHS) }
    var q by rememberSaveable { mutableStateOf("") }
    val about = remember(newest, view) { aboutView(view, newest) }
    val months = remember(about) { monthsOf(about) }
    val pick = if (month == ALL_MONTHS || months.any { it.first == month }) month else ALL_MONTHS
    val picked = remember(about, pick) { about.filter { inMonth(it.date, pick) } }
    val now = System.currentTimeMillis()
    val accts = remember(entries) { personAccounts(entries) }
    val stock = remember(entries) { stockItems(entries) }

    // The card: the two figures that matter most for this view (worked out again only when the data or view changes).
    val card: Triple<Triple<String, String, Color>, Triple<String, String, Color>, ReportKind> = remember(about, accts, stock, view, currency) { when (view) {
        0 -> Triple(
            Triple(money(currency, accts.filter { it.balance < -0.005 }.sumOf { -it.balance }), "To give", RedSpend),
            Triple(money(currency, accts.filter { it.balance > 0.005 }.sumOf { it.balance }), "To get", GreenIn), ReportKind.TO_RECEIVE
        )
        1 -> Triple(
            Triple(money(currency, stock.sumOf { if (it.line.qty > 0) it.line.qty * it.buyRate else 0.0 }), "Stock value", GreenIn),
            Triple(stock.size.toString(), if (stock.size == 1) "Item" else "Items", GreenIn), ReportKind.STOCK
        )
        2, 3 -> {
            val c = if (view == 2) GreenIn else RedSpend
            Triple(
                Triple(money(currency, about.filter { monthKey(it.date) == monthKey(now) }.sumOf { it.amount }), "This month", c),
                Triple(money(currency, about.filter { yearOf(it.date) == yearOf(now) }.sumOf { it.amount }), "Year ${yearOf(now)}", c),
                if (view == 2) ReportKind.SALES else ReportKind.PURCHASES
            )
        }
        5 -> Triple(Triple("", "", RedSpend), Triple("", "", GreenIn), ReportKind.CASH_OUT)
        else -> {
            val m = profitOf(entries, about.filter { monthKey(it.date) == monthKey(now) })
            val y = profitOf(entries, about.filter { yearOf(it.date) == yearOf(now) })
            Triple(
                Triple(plusMinus(m), "This month", if (m < 0) RedSpend else GreenIn),
                Triple(plusMinus(y), "Year ${yearOf(now)}", if (y < 0) RedSpend else GreenIn), ReportKind.PROFIT
            )
        }
    } }
    // Production: what the picked month (or every month, on All) cost, how many were made, and the cost of one.
    var madeTick by remember { mutableIntStateOf(0) }
    var askMade by remember { mutableStateOf(false) }
    val madeQty = remember(view, pick, months, madeTick) {
        if (view != 5) 0.0 else if (pick == ALL_MONTHS) months.sumOf { made(it.first) } else made(pick)
    }
    val prodCost = if (view == 5) picked.sumOf { it.amount } else 0.0
    val perUnit = if (madeQty > 0) prodCost / madeQty else 0.0
    val shownCard = if (view != 5) card else Triple(
        Triple(if (madeQty > 0) money(currency, perUnit) else "—", "Cost per unit", RedSpend),
        Triple(if (madeQty > 0) fmt(madeQty) else "—", "Made", GreenIn), ReportKind.CASH_OUT
    )
    val state = rememberLazyListState()
    Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), state = state, contentPadding = PaddingValues(bottom = barSpace(56.dp))) {
            stickyHeader { TabHead {
                KhataTop("", "", onMenu, business = business, onBusiness = onBusiness) {
                    SumCard(shownCard.first, shownCard.second, "Report ›") { onReport(shownCard.third) }
                }
                SearchBox(q, when (view) { 0 -> "Search parties"; 1 -> "Search items"; 2 -> "Search sales"; 3 -> "Search purchases"; 5 -> "Search costs"; else -> "Search months" }) { q = it }
                SectionChips(BUSINESS_SECTIONS, BusinessViews[view]) { n ->
                    val i = BusinessViews.indexOf(n)
                    if (i < 0) onSection(n) else { view = i; month = firstMonth(i, newest); q = "" }
                }
                if (months.isNotEmpty()) MonthChips(months, pick) { month = it }
            } }
            when (view) {
                0 -> partiesView(accts, picked, pick, q, onOpen)
                1 -> stockView(stock, picked, pick, q, onAdd)
                2, 3 -> tradeView(view == 2, picked, months, pick, q, onEdit)
                5 -> productionView(picked, months, pick, q, madeQty, prodCost, perUnit, currency, onEdit) { askMade = true }
                else -> profitView(entries, about, months, pick, q) { m -> view = 2; month = m; q = "" }
            }
        }
        UpButton(state)
    }
    if (askMade) MadeDialog(madeQty.takeIf { pick != ALL_MONTHS }, onDismiss = { askMade = false }) { qty ->
        val m = if (pick == ALL_MONTHS) monthKey(now) else pick
        onSetMade(m, qty); madeTick++; askMade = false
    }
}

/**
 * Production for a month (or all months): each cost head (Moulding, Fuel, Labour… the entries' names) with its
 * share, then how many were made and what one cost, then the entries themselves like the Cash book.
 */
private fun LazyListScope.productionView(
    picked: List<Entry>, months: List<Pair<Int, Long>>, month: Int, q: String, made: Double, cost: Double, perUnit: Double,
    currency: String, onEdit: (Entry) -> Unit, onMade: () -> Unit
) {
    val list = if (q.isBlank()) picked else picked.filter { matches(it, q) }
    val heads = list.groupBy { it.item.trim().ifBlank { "Other" }.lowercase() }
        .map { (_, l) -> l.first().item.trim().ifBlank { "Other" } to l.sumOf { it.amount } }.sortedByDescending { it.second }
    item(key = "pdh") {
        LedgerHead("Cost of making", if (heads.size == 1) "1 head" else "${heads.size} heads",
            // Never rounded below: the Made and Cost per unit rows always follow it.
            listOf(LCol("Cost", fmt(cost), LKind.OUT)), false, WideColW)
    }
    items(heads, key = { "pd" + it.first.lowercase() }) { (n, v) ->
        GridRow(n, if (cost > 0) "${Math.round(v / cost * 100)}% of the cost" else "", listOf(GCell(fmt(v), LKind.OUT)), WideColW, false) {}
    }
    // How many were made (tap to say) and what one cost; per 1000 too, for bricks and other small things.
    item(key = "pdm") {
        GridRow("Made", if (made > 0) "Tap to change" else "Tap to say how many were made",
            listOf(GCell(if (made > 0) fmt(made) else "—", LKind.PLAIN)), WideColW, false) { onMade() }
    }
    item(key = "pdu") {
        GridRow("Cost per unit", if (made > 0 && perUnit < 100) "Per 1000: " + money(currency, perUnit * 1000) else "",
            listOf(GCell(if (made > 0) fmt(perUnit) else "—", LKind.OUT)), WideColW, true) {}
    }
    if (list.isEmpty()) emptyNote("No production costs yet. Tap Add cost to add one.")
    else ledger(
        if (month == ALL_MONTHS) "All" else monthLabel(months, month) ?: "All",
        listOf(LCol("Cost", fmt(cost), LKind.OUT)),
        list.map { e -> LRow(e.id, e.date, title(e), e.note, listOf(e.amount), e) { onEdit(e) } },
        keyPrefix = "pde"
    )
}

/** Asks how many units were made in the month. */
@Composable
private fun MadeDialog(current: Double?, onDismiss: () -> Unit, onSave: (Double) -> Unit) {
    var text by remember { mutableStateOf(current?.takeIf { it > 0 }?.let { fmt(it).replace(",", "") } ?: "") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("How many were made?") },
        text = {
            OutlinedTextField(text, { t -> text = t.filter { it.isDigit() || it == '.' } }, singleLine = true,
                label = { Text("Units made this month") },
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number))
        },
        confirmButton = { TextButton(onClick = { text.toDoubleOrNull()?.let(onSave) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

/** Shown when a view has nothing to list. */
private fun LazyListScope.emptyNote(text: String) = item {
    Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp))
}

/** Parties A–Z with what you owe (Payable) and will get (Receivable); a month shows only parties active in it. */
private fun LazyListScope.partiesView(accts: List<PersonAcct>, picked: List<Entry>, month: Int, q: String, onOpen: (PersonAcct) -> Unit) {
    val active = picked.map { it.person.trim().lowercase() }.toSet()
    val shown = accts.filter { (month == ALL_MONTHS || it.name.trim().lowercase() in active) && (q.isBlank() || it.name.contains(q.trim(), true)) }
        .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
    val pay = shown.filter { it.balance < -0.005 }.sumOf { -it.balance }
    val rec = shown.filter { it.balance > 0.005 }.sumOf { it.balance }
    item(key = "ph") {
        LedgerHead("Parties", if (shown.size == 1) "1 · A–Z" else "${shown.size} · A–Z",
            // Only this head is rounded, at the top; every party below is its own raised square card with the same columns.
            listOf(LCol("To give", fmt(pay), LKind.OUT), LCol("To get", fmt(rec), LKind.IN)), shown.isEmpty(), WideColW)
    }
    items(shown, key = { "p" + it.name.lowercase() }) { p ->
        val bal = p.balance
        val khataMenu = LocalKhataMenu.current
        GridRow(
            p.name, listOfNotNull(partyRole(p.history.map { it.first }).ifBlank { null },
                p.history.lastOrNull()?.let { "Last " + lastSeen(it.first.date) }).joinToString(" · "),
            listOf(GCell(if (bal < -0.005) signed(LKind.OUT, -bal) else "", LKind.OUT), GCell(if (bal > 0.005) signed(LKind.IN, bal) else "", LKind.IN)),
            WideColW, p === shown.last(), card = true, onLong = khataMenu?.let { { it("party", p.name) } }
        ) { onOpen(p) }
    }
    if (shown.isEmpty()) emptyNote("No parties yet. Say \"gave Hamid 500\" in the chat.")
}

/** Items in stock with quantity and value; tap one to record a sale or a purchase of it. */
private fun LazyListScope.stockView(stock: List<StockItem>, picked: List<Entry>, month: Int, q: String, onAdd: (Entry) -> Unit) {
    val active = picked.map { it.item.trim().lowercase() }.toSet()
    val shown = stock.filter { (month == ALL_MONTHS || it.line.name.trim().lowercase() in active) && (q.isBlank() || it.line.name.contains(q.trim(), true)) }
    val value = shown.sumOf { if (it.line.qty > 0) it.line.qty * it.buyRate else 0.0 }
    item(key = "sh") {
        LedgerHead("Stock", if (shown.size == 1) "1 item" else "${shown.size} items",
            listOf(LCol("Qty", fmt(shown.sumOf { it.line.qty }), LKind.PLAIN), LCol("Value", fmt(value), LKind.IN)), shown.isEmpty(), WideColW)
    }
    items(shown, key = { "s" + it.line.name.lowercase() }) { s ->
        var menu by remember { mutableStateOf(false) }
        val rates = listOfNotNull(s.buyRate.takeIf { it > 0 }?.let { "Buy " + fmt(it) }, s.sellRate.takeIf { it > 0 }?.let { "Sell " + fmt(it) })
        Column {
            GridRow(
                s.line.name, rates.joinToString(" · "),
                listOf(
                    GCell(fmt(s.line.qty) + (if (s.line.unit.isBlank()) "" else " " + s.line.unit), LKind.PLAIN, if (s.line.qty < 0) RedSpend else AmountInk),
                    GCell(if (s.line.qty > 0 && s.buyRate > 0) fmt(s.line.qty * s.buyRate) else "", LKind.IN)
                ),
                WideColW, s === shown.last()
            ) { menu = true }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text("Sold " + s.line.name) }, onClick = { menu = false; onAdd(tradeTemplate(s, sale = true)) })
                DropdownMenuItem(text = { Text("Bought " + s.line.name) }, onClick = { menu = false; onAdd(tradeTemplate(s, sale = false)) })
            }
        }
    }
    if (shown.isEmpty()) emptyNote("No stock yet. Say: \"bought 10 rice bags at 75\"")
}

/** Sales (in) or purchases (out) as a ledger like the Cash book: day lines, then one line per entry. */
private fun LazyListScope.tradeView(sale: Boolean, picked: List<Entry>, months: List<Pair<Int, Long>>, month: Int, q: String, onEdit: (Entry) -> Unit) {
    val list = if (q.isBlank()) picked else picked.filter { matches(it, q) }
    val kind = if (sale) LKind.IN else LKind.OUT
    ledger(
        if (q.isNotBlank()) "Found" else monthLabel(months, month) ?: "All",
        listOf(LCol(if (sale) "Sale" else "Purchase", fmt(list.sumOf { it.amount }), kind)),
        list.map { e ->
            val what = if (e.item.isNotBlank() && e.qty > 0) e.item + " × " + fmt(e.qty) else title(e)
            LRow(e.id, e.date, what, e.person.ifBlank { e.note }, listOf(e.amount), e) { onEdit(e) }
        },
        keyPrefix = if (sale) "sa" else "pu"
    )
    if (list.isEmpty()) emptyNote(if (sale) "No sales yet. Say: \"sold 5 bags at 30\"" else "No purchases yet. Say: \"bought 10 bags at 25\"")
}

/** Profit month by month: the cost of what was sold, sales, and the profit (money out first, like Payable before Receivable). Tap a month to see its sales. */
private fun LazyListScope.profitView(all: List<Entry>, sales: List<Entry>, months: List<Pair<Int, Long>>, month: Int, q: String, onMonth: (Int) -> Unit) {
    class Line(val key: Int, val label: String, val count: Int, val sale: Double, val cost: Double, val profit: Double)
    val lines = months.filter { month == ALL_MONTHS || it.first == month }.map { (k, ms) ->
        val inMonth = sales.filter { monthKey(it.date) == k }
        val pb = profitBreakdown(all, inMonth)
        Line(k, monthFmt.format(Date(ms)), inMonth.size, inMonth.sumOf { it.amount }, pb.costOfGoods, pb.costedSalesAmount - pb.costOfGoods)
    }.filter { q.isBlank() || it.label.contains(q.trim(), true) }
    item(key = "fh") {
        LedgerHead("Profit", "by month", listOf(
            LCol("Cost", fmt(lines.sumOf { it.cost }), LKind.OUT), LCol("Sale", fmt(lines.sumOf { it.sale }), LKind.IN),
            LCol("Profit", plusMinus(lines.sumOf { it.profit }), LKind.PLAIN)
        ), lines.isEmpty())
    }
    items(lines, key = { "f" + it.key }) { l ->
        GridRow(
            l.label, if (l.count == 1) "1 sale" else "${l.count} sales",
            listOf(
                GCell(signed(LKind.OUT, l.cost), LKind.OUT), GCell(signed(LKind.IN, l.sale), LKind.IN),
                GCell(plusMinus(l.profit), LKind.PLAIN, if (l.profit < 0) RedSpend else GreenIn)
            ),
            62.dp, l === lines.last()
        ) { onMonth(l.key) }
    }
    if (lines.isEmpty()) emptyNote("No sales yet, so no profit to show.")
}

/** One grid cell: its text, the column it sits in (for the tint) and its ink. */
private class GCell(val text: String, val kind: LKind, val ink: Color = Color.Unspecified)

/** A ledger-style line that is not an entry: name and a small line under it, then one cell per column. As a [card] it stands alone, raised, with square corners. */
@Composable
private fun GridRow(title: String, sub: String, cells: List<GCell>, w: Dp, last: Boolean, card: Boolean = true, onLong: (() -> Unit)? = null, onClick: () -> Unit) = Tight {
    val shape = if (card) RectangleShape else sheetShape(last)
    Row(
        Modifier.padding(start = 10.dp, end = 10.dp, top = if (card) 2.dp else 0.dp).fillMaxWidth().height(IntrinsicSize.Min).shadow(1.dp, shape).clip(shape)
            .background(MaterialTheme.colorScheme.surface).combinedClickable(onLongClick = onLong) { onClick() }
    ) {
        Column(Modifier.weight(1f).padding(start = 10.dp, end = 4.dp, top = 5.dp, bottom = 5.dp), verticalArrangement = Arrangement.Center) {
            Text(title, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (sub.isNotBlank()) Text(sub, fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        cells.forEach { c ->
            VerticalDivider(Modifier.fillMaxHeight(), color = Faint)
            Box(
                Modifier.width(w).fillMaxHeight().background(if (c.kind == LKind.OUT) RedSpend.copy(alpha = 0.025f) else Color.Transparent),
                contentAlignment = Alignment.Center
            ) {
                Text(c.text, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = c.ink.takeOrElse { AmountInk }, maxLines = 1, softWrap = false)
            }
        }
    }
    if (!last && !card) HorizontalDivider(Modifier.padding(horizontal = 10.dp), color = Faint)
}

/** Shape of a row inside a white sheet (each row casts a soft shadow so the sheet looks raised): the last row rounds the bottom corners. */
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
/** Colour of an entry's own amount: plain text ink (dark grey, light in dark mode), so red / green stay for the totals. */
private val AmountInk: Color
    @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)

private fun colW(cols: List<LCol>) = if (cols.size == 1) 70.dp else 62.dp
/** Wider columns for Parties and Stock: "Receivable", quantities with units and big totals must fit. */
private val WideColW = 74.dp
private fun entryCount(n: Int) = if (n == 0) "No entries" else if (n == 1) "1 entry" else "$n entries"

/** Ledger text uses the font's own line height, not the theme's tall one, so lines stay compact. */
@Composable
private fun Tight(content: @Composable () -> Unit) =
    ProvideTextStyle(LocalTextStyle.current.copy(lineHeight = TextUnit.Unspecified), content)
private fun kindColor(k: LKind) = when (k) { LKind.OUT -> RedSpend; LKind.IN -> GreenIn; LKind.PLAIN -> Color(0xFF78716C) }
private fun cellTint(k: LKind, a: Float) = when (k) { LKind.OUT -> RedSpend.copy(alpha = a); LKind.IN -> GreenIn.copy(alpha = a); LKind.PLAIN -> Color.Transparent }
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
        Text("Tap the Add button below, or tell the Chat.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
        Column(Modifier.weight(1f).padding(start = 10.dp, top = 7.dp, bottom = 7.dp), verticalArrangement = Arrangement.Center) {
            Text(title, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
            Text(count, fontSize = 9.sp, color = sub)
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
private fun DayLine(date: Long, cols: List<LCol>, day: List<LRow>, card: Boolean = false) = Tight {
    val w = colW(cols)
    val totals = cols.indices.map { k -> day.sumOf { it.amounts.getOrElse(k) { 0.0 } } }
    Row(
        Modifier.padding(start = 10.dp, end = 10.dp, top = if (card) 2.dp else 0.dp).fillMaxWidth().height(IntrinsicSize.Min)
            .shadow(1.dp).background(MaterialTheme.colorScheme.surface)
    ) {
        // The red-to-white shade stays on the date; the amount columns keep the same tint as the entries under them.
        Text(dayLineFmt.format(Date(date)), fontSize = 9.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f).fillMaxHeight().background(Brush.horizontalGradient(listOf(Ember.copy(alpha = 0.10f), Color.Transparent)))
                .padding(start = 10.dp, top = 5.dp, bottom = 4.dp))
        cols.forEachIndexed { k, c ->
            VerticalDivider(Modifier.fillMaxHeight(), color = Faint)
            Box(
                Modifier.width(w).fillMaxHeight().background(if (c.kind == LKind.OUT) RedSpend.copy(alpha = 0.025f) else Color.Transparent),
                contentAlignment = Alignment.Center
            ) {
                Text(signed(c.kind, totals[k]), fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, color = kindColor(c.kind), maxLines = 1, softWrap = false)
            }
        }
    }
    HorizontalDivider(Modifier.padding(horizontal = 10.dp), color = Faint)
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
        Column(Modifier.weight(1f).padding(start = 10.dp, end = 4.dp, top = 5.dp, bottom = 5.dp), verticalArrangement = Arrangement.Center) {
            Text(r.title, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(fontSize = 8.sp)) { append(clockFmt.format(Date(r.date)).lowercase()) }
                    if (r.khata.isNotBlank() && !r.khata.equals(r.title, true)) {
                        append(" · ")
                        withStyle(SpanStyle(fontSize = 8.sp, fontWeight = FontWeight.SemiBold, color = KhataBlue)) { append(r.khata) }
                    }
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

/** The add button just above the bottom panel: a white card with a khata icon and what it adds ("Add expense"). */
@Composable
private fun BoxScope.PlusButton(label: String = "Add entry", onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        Modifier.align(Alignment.BottomEnd).padding(end = 14.dp, bottom = LocalBarSpace.current + 6.dp)
            .height(46.dp).shadow(2.dp, shape).clip(shape).background(MaterialTheme.colorScheme.surface)
            .clickable { onClick() }.padding(start = 10.dp, end = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Outlined.NoteAdd, contentDescription = null, tint = Ember, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(8.dp))
        Text(label, color = Ember, fontSize = 13.sp, fontWeight = FontWeight.Bold)
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
    entries: List<Entry>, currency: String, onMenu: () -> Unit, business: String, onBusiness: () -> Unit,
    onEdit: (Entry) -> Unit, onHistory: () -> Unit,
    /** The book's section chips (Cashbook · Parties… or Cashbook · Expense · Income). */
    sections: @Composable () -> Unit = {}
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
                KhataTop("", "", onMenu, business = business, onBusiness = onBusiness) {
                    SumCard(
                        Triple(money(currency, hand), "Cash in hand", if (hand < 0) RedSpend else GreenIn),
                        Triple(money(currency, spentMonth), "Spent this month", RedSpend),
                        "History", onHistory
                    )
                }
                SearchBox(q, "Search") { q = it }
                sections()
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

/** One party's khata: date and time, a Bal. tag, and You paid / You received columns, one month at a time. */
@Composable
fun PartyKhataScreen(
    name: String, entries: List<Entry>, currency: String, onBack: () -> Unit,
    onEdit: (Entry) -> Unit, onReport: () -> Unit, onAdd: (paid: Boolean) -> Unit
) {
    val p = remember(entries, name) { personAccounts(entries).firstOrNull { it.name.equals(name, true) } }
    val hist = remember(p) { p?.history?.asReversed() ?: emptyList() }
    val months = remember(hist) { monthsOf(hist.map { it.first }) }
    // A party's khata opens on All (its whole history); a month chip narrows it.
    var month by rememberSaveable(name) { mutableIntStateOf(ALL_MONTHS) }
    // A remembered month that no longer has entries (the khata changed since) falls back to the newest month.
    if (month != ALL_MONTHS && months.none { it.first == month }) month = months.firstOrNull()?.first ?: ALL_MONTHS
    var q by rememberSaveable(name) { mutableStateOf("") }
    val list = remember(hist, month, q) { if (q.isBlank()) hist.filter { inMonth(it.first.date, month) } else hist.filter { matches(it.first, q) } }
    val bal = p?.balance ?: 0.0
    val state = rememberLazyListState()
    Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), state = state, contentPadding = PaddingValues(bottom = barSpace(56.dp))) {
            stickyHeader { TabHead {
                KhataTop(name, if (hist.size == 1) "1 entry" else "${hist.size} entries", onBack, back = true) {
                    SumCard(
                        Triple(money(currency, kotlin.math.abs(bal)), if (bal > 0.005) "To get" else if (bal < -0.005) "To give" else "Settled",
                            if (bal > 0.005) GreenIn else if (bal < -0.005) RedSpend else MaterialTheme.colorScheme.onSurfaceVariant),
                        Triple(money(currency, p?.gave ?: 0.0), "You paid", RedSpend),
                        "Report ›", onReport
                    )
                }
                SearchBox(q, "Search") { q = it }
                if (q.isBlank() && months.size > 1) MonthChips(months, month) { month = it }
            } }
            val label = if (q.isNotBlank()) "Found" else if (month == ALL_MONTHS) name else monthLabel(months, month) ?: name
            ledger(
                label,
                listOf(
                    LCol("Paid", fmt(list.filter { personHistoryIsOutflow(it.first) }.sumOf { it.first.amount }), LKind.OUT),
                    LCol("Received", fmt(list.filter { !personHistoryIsOutflow(it.first) }.sumOf { it.first.amount }), LKind.IN)
                ),
                list.map { (e, _) ->
                    val out = personHistoryIsOutflow(e)
                    val note = if (isWork(e) && e.qty > 0 && e.rate > 0) "${plain(e.qty)} × ${plain(e.rate)}"
                        else e.item.takeIf { it != "Loans" && !it.equals(e.note, true) } ?: ""
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
        PlusButton(onClick = onAdd)
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

@Composable
fun MoneyScreen(
    income: Boolean, entries: List<Entry>, currency: String, onMenu: () -> Unit, business: String, onBusiness: () -> Unit,
    onEdit: (Entry) -> Unit, onReport: () -> Unit,
    /** The book's section chips. */
    sections: @Composable () -> Unit = {}
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
    var q by rememberSaveable(income) { mutableStateOf("") }
    val shown = remember(list, pick, q) { list.filter { inMonth(it.date, pick) && (q.isBlank() || matches(it, q)) } }
    val total = shown.sumOf { it.amount }
    // Expense by category; income by who paid (like the party list).
    val groups = remember(shown, income) {
        (if (income) shown.groupBy { source(it).lowercase() }.map { (_, l) -> Group(source(l.first()), null, l) }
        else shown.groupBy { it.category }.map { (c, l) -> Group(c.label, c, l) })
            // Categories biggest first (they show a share); payers newest first, like every dated list.
            .sortedByDescending { if (income) it.list.maxOf { e -> e.date }.toDouble() else it.total }
    }
    val open = remember(income) { mutableStateListOf<String>() }
    val khataMenu = LocalKhataMenu.current
    Box(Modifier.fillMaxSize()) {
        val listState = rememberLazyListState()
        LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = PaddingValues(bottom = barSpace(56.dp))) {
            stickyHeader { TabHead {
                KhataTop("", "", onMenu, business = business, onBusiness = onBusiness) {
                    SumCard(Triple(money(currency, thisYear), "Year $year", color), Triple(money(currency, thisMonth), "This month", color), "Report ›", onReport)
                }
                SearchBox(q, "Search") { q = it }
                sections()
                if (months.isNotEmpty()) MonthChips(months, pick) { pick = it }
            } }
            val kind = if (income) LKind.IN else LKind.OUT
            if (groups.isNotEmpty()) item {
                LedgerHead(if (income) "Income" else "Expense", if (groups.size == 1) "1 group" else "${groups.size} groups",
                    listOf(LCol("Total", fmt(total), kind)), false, WideColW)
            }
            groups.forEach { g ->
                val isOpen = g.name in open
                item(key = "g" + g.name) {
                    val share = if (total > 0) (g.total / total).toFloat() else 0f
                    // Its own square raised card: icon, name with a share bar (expense) or last date and count (income), then the
                    // amount in dark ink (only the head's Total is red / green), in a column with the party cards' light tint (red for expense, green for income).
                    Row(
                        Modifier.padding(start = 10.dp, end = 10.dp, top = 2.dp).fillMaxWidth().height(IntrinsicSize.Min).shadow(1.dp)
                            .background(MaterialTheme.colorScheme.surface)
                            .combinedClickable(onLongClick = khataMenu?.let { { it(if (income) "income" else "expense", g.name) } }) {
                                if (isOpen) open.remove(g.name) else open.add(g.name)
                            }.padding(start = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (g.cat != null) CatBubble(g.cat, 30.dp) else Initial(g.name)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
                            Text(g.name, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (g.cat != null) {
                                Box(Modifier.padding(top = 4.dp).fillMaxWidth().height(4.dp).clip(CircleShape).background(MaterialTheme.colorScheme.outlineVariant)) {
                                    Box(Modifier.fillMaxWidth(share).fillMaxHeight().clip(CircleShape).background(catColor(g.cat)))
                                }
                            } else {
                                Text(shortDay.format(Date(g.list.maxOf { it.date })) + " · " + (if (g.list.size == 1) "1 entry" else "${g.list.size} entries"),
                                    fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                            }
                        }
                        Spacer(Modifier.width(10.dp))
                        VerticalDivider(Modifier.fillMaxHeight(), color = Faint)
                        Column(
                            Modifier.width(WideColW).fillMaxHeight().background(cellTint(kind, 0.025f)),
                            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center
                        ) {
                            Text(fmt(g.total), fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, color = AmountInk)
                            Text(if (g.cat != null) "${Math.round(share * 100)}%" else if (isOpen) "⌄" else "›",
                                fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                // A group's own entries open right under it, on the same ledger page; an expense category with
                // several names (Bank fee, Iqama fee…) shows one ledger per name, newest first.
                if (isOpen) {
                    val subs = if (g.cat == null) listOf(g.list) else g.list.groupBy { nameKey(it) }.values.sortedByDescending { l -> l.maxOf { it.date } }
                    subs.forEach { list ->
                        ledger(
                            if (subs.size == 1) g.name else title(list.first()), listOf(LCol("Amount", fmt(list.sumOf { it.amount }), kind)),
                            list.map { e -> LRow(e.id, e.date, title(e), e.note, listOf(e.amount), e) { onEdit(e) } },
                            keyPrefix = "g${g.name}:${title(list.first()).lowercase()}:",
                            // Hold the head to move the whole name khata (all of Muhammad Bin Saeed) to another category.
                            onHold = khataMenu?.let { m ->
                                { if (subs.size == 1) m(if (income) "income" else "expense", g.name) else m("expense-name", title(list.first()).trim()) }
                            }
                        )
                    }
                    item(key = "g${g.name}:end") { Spacer(Modifier.height(8.dp)) }
                }
            }
            if (list.isEmpty()) item {
                Text(if (income) "No income yet." else "No expenses yet.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp, modifier = Modifier.padding(24.dp))
            }
        }
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
                                plusMinus(inn - out)) {}
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
