package com.bolkhata.app

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** The five main accounts on the home screen. */
enum class MainAcct(val label: String) {
    EXPENSE("Expense"), INCOME("Income"), TO_PAY("To pay"), TO_RECEIVE("To receive"), CASH("Cash in hand")
}

/** Opens one of the five main accounts. Set by the main screen. */
val LocalMainOpener = staticCompositionLocalOf<((MainAcct) -> Unit)?> { null }

/** Long-press on an entry anywhere opens its action menu (Edit / Move). Set by the main screen. */
val LocalEntryMenu = staticCompositionLocalOf<((Entry) -> Unit)?> { null }

// ---------------------------------------------------------------- people

/**
 * One person's account, worked out from their whole history (loans, repayments and credit sales / purchases).
 * [balance] > 0: they owe me (To receive); < 0: I owe them (To pay). A settled account (0) stays on the side it
 * was last on, until a new entry changes it.
 */
class PersonAcct(
    val name: String,
    val history: List<Pair<Entry, Double>>,   // oldest first, with the balance after each entry
    val balance: Double,
    val toReceive: Boolean,
    val gave: Double,
    val got: Double
)

/** What an entry does to a person's balance (+ = they owe me more). */
fun personEffect(e: Entry): Double = when (e.type) {
    Type.DIYA, Type.WAPIS_DIYA, Type.SALE -> e.amount
    Type.LIYA, Type.WAPIS_MILA, Type.PURCHASE -> -e.amount
    else -> 0.0
}

fun personAccounts(entries: List<Entry>): List<PersonAcct> =
    entries.filter { withPerson(it) && !it.paid }
        .groupBy { it.person.trim().lowercase() }
        .map { (_, list) ->
            val name = list.groupingBy { it.person.trim() }.eachCount().maxByOrNull { it.value }?.key ?: ""
            val sorted = list.sortedWith(compareBy<Entry> { it.date }.thenBy { it.createdAt })
            var run = 0.0
            var receive = personEffect(sorted.first()) >= 0
            val hist = sorted.map { e ->
                run += personEffect(e)
                if (run > 0.005) receive = true else if (run < -0.005) receive = false
                e to run
            }
            PersonAcct(
                name = name, history = hist, balance = run, toReceive = receive,
                gave = sorted.filter { personEffect(it) > 0 }.sumOf { it.amount },
                got = sorted.filter { personEffect(it) < 0 }.sumOf { it.amount }
            )
        }

// ---------------------------------------------------------------- moving an entry

private val expenseCats = Cat.values().filter { it !in setOf(Cat.TANKHWAH, Cat.UDHAAR, Cat.SALES, Cat.STOCK) }
private val incomeCats = listOf(Cat.TANKHWAH, Cat.ASSETS, Cat.OTHER)

private fun isOutflow(e: Entry) = when (e.type) {
    Type.KHARCHA, Type.DIYA, Type.WAPIS_DIYA, Type.PURCHASE -> true
    else -> false
}

/**
 * The same entry moved to another place: date, amount and note stay as they are. Moving to a person turns it into
 * a loan or a repayment, chosen from that person's balance (so 1,000 lent then 1,500 received leaves "I owe 500").
 */
fun movedTo(e: Entry, all: List<Entry>, cat: Cat?, income: Boolean, person: String?): Entry {
    if (person != null) {
        val p = person.trim()
        val bal = personAccounts(all.filter { it.id != e.id })
            .firstOrNull { it.name.equals(p, true) }?.balance ?: 0.0
        val type = if (isOutflow(e)) (if (bal < -0.005) Type.WAPIS_DIYA else Type.DIYA)
        else (if (bal > 0.005) Type.WAPIS_MILA else Type.LIYA)
        return e.copy(type = type, person = p, category = Cat.UDHAAR, item = "Loans", paid = false)
    }
    val c = cat ?: Cat.OTHER
    val item = if (e.item.isBlank() || e.item == "Loans") c.label else e.item
    return e.copy(type = if (income) Type.AAMDANI else Type.KHARCHA, category = c, person = "", item = item, paid = false)
}

@Composable
fun EntryMenu(e: Entry, currency: String, onEdit: () -> Unit, onMove: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(entryTitle(e), maxLines = 1, overflow = TextOverflow.Ellipsis) },
        text = { Text(SimpleDateFormat("d MMM yyyy", Locale.US).format(Date(e.date)) + " · " + money(currency, e.amount)) },
        confirmButton = {
            Column(horizontalAlignment = Alignment.End) {
                TextButton(onClick = onEdit) { Text("Edit entry") }
                TextButton(onClick = onMove) { Text("Move entry") }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

/** Where to move an entry: an expense category, an income category, or a person's account. */
@Composable
fun MoveDialog(e: Entry, all: List<Entry>, onMove: (Entry) -> Unit, onDismiss: () -> Unit) {
    val people = remember(all) {
        (personAccounts(all).map { it.name } + all.mapNotNull { x ->
            x.note.trim().takeIf { it.startsWith("To ") || it.startsWith("From ") }?.substringAfter(' ')?.trim()
        }).filter { it.isNotBlank() }.distinctBy { it.lowercase() }.sorted()
    }
    var newName by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Move entry to") },
        text = {
            Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState())) {
                Text("Date, amount and note stay the same.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                MoveSection("Expense")
                expenseCats.forEach { c ->
                    MoveRow(c.label, selected = e.type == Type.KHARCHA && e.category == c) { onMove(movedTo(e, all, c, false, null)) }
                }
                MoveSection("Income")
                incomeCats.forEach { c ->
                    MoveRow(
                        if (c == Cat.TANKHWAH) "Salary / income" else c.label + " (income)",
                        selected = e.type == Type.AAMDANI && e.category == c
                    ) { onMove(movedTo(e, all, c, true, null)) }
                }
                MoveSection("A person (To pay / To receive)")
                people.forEach { p ->
                    MoveRow(p, selected = e.person.equals(p, true)) { onMove(movedTo(e, all, null, false, p)) }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = newName, onValueChange = { newName = it }, singleLine = true,
                        label = { Text("New person") }, modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = { onMove(movedTo(e, all, null, false, newName)) }, enabled = newName.isNotBlank()) { Text("Move") }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun MoveSection(title: String) {
    Text(title, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 12.dp, bottom = 2.dp))
}

@Composable
private fun MoveRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 44.dp).clip(RoundedCornerShape(10.dp)).clickable(onClick = onClick)
            .background(if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.10f) else MaterialTheme.colorScheme.surface)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, modifier = Modifier.weight(1f), fontSize = 15.sp)
        if (selected) Text("now", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
    }
}

// ---------------------------------------------------------------- the account screens

/** One level of the account screens. */
private sealed class Level {
    data class Main(val acct: MainAcct) : Level()
    data class Sub(val income: Boolean, val cat: Cat) : Level()
    data class Vendor(val income: Boolean, val cat: Cat, val vendor: String) : Level()
    data class Person(val name: String) : Level()
}

private fun monthStart(): Long = Calendar.getInstance().apply {
    set(Calendar.DAY_OF_MONTH, 1); set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
    set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
}.timeInMillis

private fun vendorKey(e: Entry) = e.item.trim().ifBlank { e.category.label }.lowercase()

/**
 * Expense → categories → transactions and shops → one shop's history; Income the same way;
 * To pay / To receive → people → one person's account. Back goes one level up.
 */
@Composable
fun LedgerScreen(
    start: MainAcct,
    entries: List<Entry>,
    currency: String,
    onClose: () -> Unit,
    onEdit: (Entry) -> Unit,
    onNew: (Entry) -> Unit,
    person: String? = null
) {
    val stack = remember(start, person) {
        mutableStateListOf<Level>(Level.Main(start)).apply { if (person != null) add(Level.Person(person)) }
    }
    BackHandler(enabled = stack.size > 1) { stack.removeAt(stack.lastIndex) }
    fun back() { if (stack.size > 1) stack.removeAt(stack.lastIndex) else onClose() }
    when (val level = stack.last()) {
        is Level.Main -> when (level.acct) {
            MainAcct.EXPENSE, MainAcct.INCOME -> TreeView(level.acct == MainAcct.INCOME, entries, currency, ::back, onEdit)
            else -> PeopleView(level.acct == MainAcct.TO_RECEIVE, entries, currency, ::back) { stack.add(Level.Person(it)) }
        }
        is Level.Sub -> SubView(level.income, level.cat, entries, currency, ::back, onEdit) { stack.add(Level.Vendor(level.income, level.cat, it)) }
        is Level.Vendor -> VendorView(level, entries, currency, ::back, onEdit)
        is Level.Person -> PersonView(level.name, entries, currency, ::back, onEdit, onNew)
    }
}

@Composable
private fun Header(title: String, subtitle: String, onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 16.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle.isNotBlank()) Text(subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun Figures(vararg items: Triple<String, String, androidx.compose.ui.graphics.Color>) {
    Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items.forEach { (label, value, color) ->
            Column(
                Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).background(color.copy(alpha = 0.08f))
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                Text(value, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun NavRow(title: String, subtitle: String, value: String, valueColor: androidx.compose.ui.graphics.Color, onClick: () -> Unit) {
    Surface(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 3.dp), shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface) {
        Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
            Text(value, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = valueColor)
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 20.dp, top = 14.dp, bottom = 4.dp))
}

private fun count(n: Int) = if (n == 1) "1 entry" else "$n entries"

@Composable
private fun CategoriesView(income: Boolean, entries: List<Entry>, currency: String, onBack: () -> Unit, onOpen: (Cat) -> Unit) {
    val type = if (income) Type.AAMDANI else Type.KHARCHA
    val mine = remember(entries, income) { entries.filter { it.type == type } }
    val m0 = monthStart()
    val groups = remember(mine) { mine.groupBy { it.category }.entries.sortedByDescending { g -> g.value.sumOf { it.amount } } }
    val color = if (income) GreenIn else RedSpend
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item { Header(if (income) "Income" else "Expense", count(mine.size), onBack) }
        item {
            Figures(
                Triple("This month", fmt(mine.filter { it.date >= m0 }.sumOf { it.amount }), color),
                Triple("All time", fmt(mine.sumOf { it.amount }), color)
            )
        }
        item { SectionTitle(if (income) "Income categories" else "Expense categories") }
        if (groups.isEmpty()) item { Text("Nothing here yet.", modifier = Modifier.padding(20.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
        items(groups, key = { it.key.name }) { g ->
            val month = g.value.filter { it.date >= m0 }.sumOf { it.amount }
            NavRow(
                g.key.label, count(g.value.size) + " · this month " + fmt(month),
                fmt(g.value.sumOf { it.amount }), color
            ) { onOpen(g.key) }
        }
    }
}

@Composable
private fun SubView(
    income: Boolean, cat: Cat, entries: List<Entry>, currency: String,
    onBack: () -> Unit, onEdit: (Entry) -> Unit, onVendor: (String) -> Unit
) {
    val type = if (income) Type.AAMDANI else Type.KHARCHA
    val mine = remember(entries, cat, income) {
        entries.filter { it.type == type && it.category == cat }.sortedWith(compareByDescending<Entry> { it.date }.thenByDescending { it.createdAt })
    }
    val vendors = remember(mine) { mine.groupBy { vendorKey(it) }.values.sortedByDescending { v -> v.sumOf { it.amount } } }
    val m0 = monthStart()
    val color = if (income) GreenIn else RedSpend
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item { Header(cat.label, (if (income) "Income" else "Expense") + " · " + count(mine.size), onBack) }
        item {
            Figures(
                Triple("This month", fmt(mine.filter { it.date >= m0 }.sumOf { it.amount }), color),
                Triple("All time", fmt(mine.sumOf { it.amount }), color)
            )
        }
        if (vendors.size > 1) {
            item { SectionTitle(if (income) "Sources" else "Shops and places") }
            items(vendors, key = { "v-" + vendorKey(it.first()) }) { v ->
                NavRow(v.first().item.ifBlank { cat.label }, (if (v.size == 1) "1 time" else "${v.size} times"), fmt(v.sumOf { it.amount }), color) {
                    onVendor(vendorKey(v.first()))
                }
            }
        }
        item { SectionTitle("All transactions") }
        items(mine, key = { "e-" + it.id }) { e ->
            Box(Modifier.padding(horizontal = 16.dp, vertical = 3.dp)) { TxRow(e, currency) { onEdit(e) } }
        }
    }
}

@Composable
private fun VendorView(level: Level.Vendor, entries: List<Entry>, currency: String, onBack: () -> Unit, onEdit: (Entry) -> Unit) {
    val type = if (level.income) Type.AAMDANI else Type.KHARCHA
    val mine = remember(entries, level) {
        entries.filter { it.type == type && it.category == level.cat && vendorKey(it) == level.vendor }
            .sortedWith(compareByDescending<Entry> { it.date }.thenByDescending { it.createdAt })
    }
    val df = SimpleDateFormat("d MMM yyyy", Locale.US)
    val color = if (level.income) GreenIn else RedSpend
    val name = mine.firstOrNull()?.item?.ifBlank { level.cat.label } ?: level.vendor
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item { Header(name, level.cat.label + " · " + (if (mine.size == 1) "1 time" else "${mine.size} times"), onBack) }
        item {
            Figures(
                Triple("Total", fmt(mine.sumOf { it.amount }), color),
                Triple("Average", fmt(if (mine.isEmpty()) 0.0 else mine.sumOf { it.amount } / mine.size), color)
            )
        }
        if (mine.isNotEmpty()) {
            item {
                Text(
                    "First " + df.format(Date(mine.last().date)) + " · last " + df.format(Date(mine.first().date)),
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 20.dp)
                )
            }
        }
        item { SectionTitle("History") }
        items(mine, key = { "e-" + it.id }) { e ->
            Box(Modifier.padding(horizontal = 16.dp, vertical = 3.dp)) { TxRow(e, currency) { onEdit(e) } }
        }
    }
}

@Composable
private fun PeopleView(receive: Boolean, entries: List<Entry>, currency: String, onBack: () -> Unit, onOpen: (String) -> Unit) {
    val list = remember(entries, receive) {
        personAccounts(entries).filter { it.toReceive == receive }
            .sortedWith(compareByDescending<PersonAcct> { kotlin.math.abs(it.balance) }.thenBy { it.name.lowercase() })
    }
    val total = list.sumOf { kotlin.math.abs(it.balance) }
    val color = if (receive) GreenIn else OrangeOwe
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item { Header(if (receive) "To receive" else "To pay", if (list.size == 1) "1 person" else "${list.size} people", onBack) }
        item { Figures(Triple(if (receive) "They owe you" else "You owe", money(currency, total), color)) }
        if (list.isEmpty()) item {
            Text(
                if (receive) "Nobody owes you right now." else "You don't owe anyone right now.",
                modifier = Modifier.padding(20.dp), color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        items(list, key = { "p-" + it.name.lowercase() }) { p ->
            val settled = kotlin.math.abs(p.balance) < 0.005
            NavRow(
                p.name, count(p.history.size) + (if (settled) " · settled" else ""),
                if (settled) "0" else fmt(kotlin.math.abs(p.balance)),
                if (settled) MaterialTheme.colorScheme.onSurfaceVariant else color
            ) { onOpen(p.name) }
        }
    }
}

@Composable
private fun PersonView(
    name: String, entries: List<Entry>, currency: String,
    onBack: () -> Unit, onEdit: (Entry) -> Unit, onNew: (Entry) -> Unit
) {
    val p = remember(entries, name) { personAccounts(entries).firstOrNull { it.name.equals(name, true) } }
    val df = SimpleDateFormat("d MMM yyyy", Locale.US)
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item { Header(name, if (p == null) "" else (if (p.toReceive) "To receive" else "To pay") + " · " + count(p.history.size), onBack) }
        if (p == null) {
            item { Text("No entries with $name.", modifier = Modifier.padding(20.dp)) }
        } else {
            item {
                val bal = p.balance
                val text = when {
                    bal > 0.005 -> "$name owes you " + money(currency, bal)
                    bal < -0.005 -> "You owe $name " + money(currency, -bal)
                    else -> "Settled · nothing owed"
                }
                Text(
                    text, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold,
                    color = if (bal > 0.005) GreenIn else if (bal < -0.005) OrangeOwe else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)
                )
            }
            item {
                Figures(
                    Triple("You gave / sold", fmt(p.gave), RedSpend),
                    Triple("You got / bought", fmt(p.got), GreenIn)
                )
            }
            item {
                Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(
                        onClick = { onNew(template(Type.DIYA, name)) }, modifier = Modifier.weight(1f).height(44.dp), shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(containerColor = RedSpend.copy(alpha = 0.12f), contentColor = RedSpend)
                    ) { Text("+ Gave", fontWeight = FontWeight.Bold) }
                    FilledTonalButton(
                        onClick = { onNew(template(Type.WAPIS_MILA, name)) }, modifier = Modifier.weight(1f).height(44.dp), shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(containerColor = GreenIn.copy(alpha = 0.14f), contentColor = GreenIn)
                    ) { Text("+ Got", fontWeight = FontWeight.Bold) }
                }
            }
            item { SectionTitle("History (balance after each entry)") }
            items(p.history.reversed(), key = { "h-" + it.first.id }) { (e, after) ->
                Surface(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 3.dp), shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface) {
                    Row(Modifier.fillMaxWidth().entryTap(e) { onEdit(e) }.padding(horizontal = 14.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(e.type.label + (if (e.note.isNotBlank()) " · " + e.note else ""), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(df.format(Date(e.date)), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            val eff = personEffect(e)
                            Text((if (eff > 0) "−" else "+") + fmt(e.amount), fontWeight = FontWeight.Bold, color = if (eff > 0) RedSpend else GreenIn)
                            Text(
                                when {
                                    after > 0.005 -> "owes you " + fmt(after)
                                    after < -0.005 -> "you owe " + fmt(-after)
                                    else -> "settled"
                                },
                                fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun template(type: Type, person: String): Entry {
    val now = System.currentTimeMillis()
    return Entry(
        id = "e" + System.nanoTime(), type = type, amount = 0.0, category = Cat.UDHAAR, item = "Loans",
        person = person, note = "", date = now, createdAt = now
    )
}


// ---------------------------------------------------------------- tree view

/** One branch of the Expense / Income tree: a name, its total, how many entries, and what's under it. */
private class Node(val key: String, val name: String, val list: List<Entry>, val children: List<Node>)

private fun transfer(e: Entry) = e.item.equals("Transfer", true) || e.item.equals("Transfer fee", true)

private fun personOnNote(e: Entry): String {
    val n = e.note.trim()
    return when {
        n.startsWith("To ") || n.startsWith("From ") -> n.substringAfter(' ').trim()
        n.startsWith("Fee: transfer to ") -> n.removePrefix("Fee: transfer to ").trim()
        else -> ""
    }
}

/** The shop or place on a statement note ("Sasco, Riyadh" -> "Sasco"). */
private fun placeOnNote(e: Entry): String = if (',' in e.note) e.note.substringBefore(',').trim() else ""

private fun sortedNewest(l: List<Entry>) = l.sortedWith(compareByDescending<Entry> { it.date }.thenByDescending { it.createdAt })

private fun groupNodes(prefix: String, list: List<Entry>, keyOf: (Entry) -> String, nameOf: (List<Entry>) -> String, children: (String, List<Entry>) -> List<Node>): List<Node> =
    list.groupBy { keyOf(it).lowercase().ifBlank { "-" } }
        .map { (k, l) -> Node("$prefix/$k", nameOf(l), sortedNewest(l), children("$prefix/$k", l)) }
        .sortedByDescending { n -> n.list.sumOf { it.amount } }

/**
 * Category -> item (Petrol, Taxi, Roti; transfers grouped as "Transfers to people") -> shop or person -> entries.
 * A level with only one branch is skipped, so the tree never shows a pointless step.
 */
private fun buildTree(list: List<Entry>): List<Node> =
    groupNodes("c", list, { it.category.name }, { it.first().category.label }) { cp, cl ->
        groupNodes(cp, cl,
            { if (transfer(it)) "~transfers" else it.item.trim().ifBlank { it.category.label } },
            { l -> if (transfer(l.first())) "Transfers to people" else l.first().item.trim().ifBlank { l.first().category.label } }
        ) { ip, il ->
            val third = groupNodes(ip, il,
                { if (transfer(it)) personOnNote(it).ifBlank { "Others" } else placeOnNote(it).ifBlank { it.item } },
                { l -> if (transfer(l.first())) personOnNote(l.first()).ifBlank { "Others" } else placeOnNote(l.first()).ifBlank { l.first().item } }
            ) { _, _ -> emptyList() }
            if (third.size > 1) third else emptyList()
        }.let { items -> if (items.size == 1) items.first().children else items }
    }

@Composable
private fun TreeView(income: Boolean, entries: List<Entry>, currency: String, onBack: () -> Unit, onEdit: (Entry) -> Unit) {
    val type = if (income) Type.AAMDANI else Type.KHARCHA
    val mine = remember(entries, income) { entries.filter { it.type == type } }
    val tree = remember(mine) { buildTree(mine) }
    var open by remember(income) { mutableStateOf(setOf<String>()) }
    val m0 = monthStart()
    val color = if (income) GreenIn else RedSpend
    // The visible rows: each open branch shows its children, a branch without children shows its entries.
    val rows = remember(tree, open) {
        val out = mutableListOf<Pair<Int, Any>>()
        fun walk(nodes: List<Node>, depth: Int) {
            for (n in nodes) {
                out += depth to n
                if (n.key in open) {
                    if (n.children.isNotEmpty()) walk(n.children, depth + 1)
                    else n.list.forEach { out += (depth + 1) to it }
                }
            }
        }
        walk(tree, 0)
        out
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item { Header(if (income) "Income" else "Expense", count(mine.size) + " · tap a line to open it", onBack) }
        item {
            Figures(
                Triple("This month", fmt(mine.filter { it.date >= m0 }.sumOf { it.amount }), color),
                Triple("All time", fmt(mine.sumOf { it.amount }), color)
            )
        }
        if (tree.isEmpty()) item { Text("Nothing here yet.", modifier = Modifier.padding(20.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
        items(rows, key = { (_, x) -> if (x is Node) "n-" + x.key else "e-" + (x as Entry).id }) { (depth, x) ->
            val indent = (16 + depth * 18).dp
            if (x is Node) {
                val isOpen = x.key in open
                val total = x.list.sumOf { it.amount }
                val month = x.list.filter { it.date >= m0 }.sumOf { it.amount }
                Surface(
                    Modifier.fillMaxWidth().padding(start = indent, end = 16.dp, top = if (depth == 0) 6.dp else 2.dp),
                    shape = RoundedCornerShape(14.dp),
                    color = if (depth == 0) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                ) {
                    Row(
                        Modifier.fillMaxWidth().clickable { open = if (isOpen) open.filterNot { it == x.key || it.startsWith(x.key + "/") }.toSet() else open + x.key }
                            .padding(horizontal = 12.dp, vertical = if (depth == 0) 12.dp else 9.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(if (isOpen) "▾" else "▸", fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(18.dp))
                        Column(Modifier.weight(1f)) {
                            Text(x.name, fontWeight = if (depth == 0) FontWeight.ExtraBold else FontWeight.Bold, fontSize = if (depth == 0) 16.sp else 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                (if (x.list.size == 1) "1 time" else "${x.list.size} times") + (if (month > 0) " · this month " + fmt(month) else ""),
                                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1
                            )
                        }
                        Text(fmt(total), fontWeight = FontWeight.Bold, fontSize = if (depth == 0) 16.sp else 14.sp, color = color)
                    }
                }
            } else {
                val e = x as Entry
                Box(Modifier.padding(start = indent, end = 16.dp, top = 2.dp)) { TxRow(e, currency) { onEdit(e) } }
            }
        }
    }
}
