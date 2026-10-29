package com.bolkhata.app

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
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
    Type.TRANSFER_IN, Type.TRANSFER_OUT -> 0.0
    else -> 0.0
}

/** Direction shown for a transaction in a person's history; transfer direction is independent of debt balance. */
fun personHistoryIsOutflow(e: Entry): Boolean = when (e.type) {
    Type.TRANSFER_OUT -> true
    Type.TRANSFER_IN -> false
    else -> personEffect(e) > 0
}

/** Apply the open person's khata as the default for new loan/trade/transfer entries without a named person. */
fun applyKhataContext(entries: List<Entry>, person: String?): List<Entry> {
    val name = person?.trim()?.takeIf { it.isNotEmpty() } ?: return entries
    return entries.map { e ->
        if (e.person.isBlank() && (isLoan(e.type) || isTrade(e.type) || isTransfer(e.type))) e.copy(person = name)
        else e
    }
}

/**
 * Converts named-person bank transfers into khata entries when the user explicitly asks.
 * Positive balance means they owe me; negative means I owe them.
 */
fun convertPersonTransfersToLedgerEntries(entries: List<Entry>, person: String): List<Pair<Entry, Entry>> {
    val history = entries
        .filter { it.person.equals(person, true) && withPerson(it) && !it.paid }
        .sortedWith(compareBy<Entry> { it.date }.thenBy { it.createdAt })
    var balance = 0.0
    val changed = mutableListOf<Pair<Entry, Entry>>()
    for (e in history) {
        val converted = when (e.type) {
            Type.TRANSFER_OUT -> e.copy(
                type = if (balance < -0.005) Type.WAPIS_DIYA else Type.DIYA,
                category = Cat.UDHAAR,
                item = if (e.item.isBlank() || e.item.equals("Transfer", true)) "Loans" else e.item
            )
            Type.TRANSFER_IN -> e.copy(
                type = if (balance > 0.005) Type.WAPIS_MILA else Type.LIYA,
                category = Cat.UDHAAR,
                item = if (e.item.isBlank() || e.item.equals("Transfer", true)) "Loans" else e.item
            )
            else -> null
        }
        if (converted != null) {
            changed += e to converted
            balance += personEffect(converted)
        } else {
            balance += personEffect(e)
        }
    }
    return changed
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
    Type.KHARCHA, Type.DIYA, Type.WAPIS_DIYA, Type.PURCHASE, Type.TRANSFER_OUT -> true
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

/** Strip common bank-transfer metadata from names shown in the Move Entry picker. */
private fun cleanMoveKhataName(value: String): String {
    var name = value.trim()
    name = Regex("""(?i)\.\s*(?:from|to)\s+the account of.*$""").replace(name, "")
    name = Regex("""(?i)/TOACCT/.*$""").replace(name, "")
    name = Regex("""(?i)\s+W-\s*$""").replace(name, "")
    if (name.contains("/") && name.substringBefore("/").all { it.isDigit() }) {
        name = name.substringAfterLast("/")
    }
    return name.trim().trim('.', '-', '/', ' ')
}

/** Search-first khata picker: the large category lists stay out of the Move Entry flow. */
@Composable
fun MoveDialog(e: Entry, all: List<Entry>, onMove: (Entry) -> Unit, onDismiss: () -> Unit) {
    // Keep the real account key for moving entries; clean only the display label.
    val people = remember(all) {
        (personAccounts(all).map { it.name } + all.map { it.person })
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinctBy { it.lowercase(Locale.ROOT) }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { cleanMoveKhataName(it) })
    }
    var searchOpen by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedPerson by remember { mutableStateOf<String?>(null) }
    var creatingKhata by remember { mutableStateOf(false) }
    var newKhataName by remember { mutableStateOf("") }
    val query = searchQuery.trim()
    val filteredPeople = remember(people, query) {
        if (query.isBlank()) emptyList()
        else people.filter {
            cleanMoveKhataName(it).contains(query, ignoreCase = true) ||
                it.contains(query, ignoreCase = true)
        }
    }
    val exactMatch = people.any {
        cleanMoveKhataName(it).equals(query, ignoreCase = true) ||
            it.equals(query, ignoreCase = true)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(26.dp),
        title = {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("Move entry", fontWeight = FontWeight.Bold, fontSize = 21.sp)
                    Text(
                        if (selectedPerson != null) "Confirm the destination"
                        else "Find the right khata",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    IconButton(
                        onClick = {
                            creatingKhata = false
                            selectedPerson = null
                            searchOpen = !searchOpen
                            if (!searchOpen) searchQuery = ""
                        },
                        modifier = Modifier.size(40.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer)
                    ) {
                        Icon(if (searchOpen && !creatingKhata) Icons.Filled.Close else Icons.Filled.Search,
                            contentDescription = "Search khata",
                            tint = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                    IconButton(
                        onClick = {
                            selectedPerson = null
                            searchOpen = false
                            creatingKhata = true
                            newKhataName = ""
                        },
                        modifier = Modifier.size(40.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer)
                    ) {
                        Text("+", fontWeight = FontWeight.Bold, fontSize = 24.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                }
            }
        },
        text = {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (selectedPerson != null) {
                    Text("Move this entry to:", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Text(
                            cleanMoveKhataName(selectedPerson!!),
                            modifier = Modifier.padding(16.dp),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                    Text(
                        "Amount, date and note will stay unchanged.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else if (creatingKhata) {
                    OutlinedTextField(
                        value = newKhataName,
                        onValueChange = { newKhataName = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("New khata name") },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp)
                    )
                    if (people.any { it.equals(newKhataName.trim(), ignoreCase = true) }) {
                        Text("This khata already exists. Use Search to select it.",
                            fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
                    } else {
                        Text("Create the khata and move this entry after confirmation.",
                            fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else if (!searchOpen) {
                    Text(
                        "Tap the search icon to find a khata.",
                        modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Search khata by name") },
                        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Filled.Close, contentDescription = "Clear search")
                                }
                            }
                        },
                        shape = RoundedCornerShape(16.dp),
                        singleLine = true
                    )
                    if (query.isBlank()) {
                        Text(
                            "Start typing to see matching khatas · A–Z",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Text(
                            "${filteredPeople.size} matching khata${if (filteredPeople.size == 1) "" else "s"} · A–Z",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        LazyColumn(
                            modifier = Modifier.fillMaxWidth().heightIn(max = 320.dp),
                            verticalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            items(filteredPeople, key = { it }) { person ->
                                val current = e.person.equals(person, ignoreCase = true)
                                Row(
                                    modifier = Modifier.fillMaxWidth()
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(
                                            if (current) MaterialTheme.colorScheme.primaryContainer
                                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.50f)
                                        )
                                        .clickable { selectedPerson = person }
                                        .padding(horizontal = 14.dp, vertical = 13.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        cleanMoveKhataName(person),
                                        modifier = Modifier.weight(1f),
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                        fontWeight = FontWeight.Medium
                                    )
                                    if (current) {
                                        Text("Current", fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.primary)
                                    } else {
                                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                        if (filteredPeople.isEmpty()) {
                            Text("No matching khata found.",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (!exactMatch && query.isNotBlank()) {
                            TextButton(onClick = { selectedPerson = query }) {
                                Text("+ Create new khata: \"$query\"")
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (creatingKhata) {
                Button(
                    enabled = newKhataName.trim().isNotBlank() &&
                        people.none { it.equals(newKhataName.trim(), ignoreCase = true) },
                    onClick = {
                        selectedPerson = newKhataName.trim()
                        creatingKhata = false
                    }
                ) { Text("Create & move") }
            } else if (selectedPerson != null) {
                Button(onClick = {
                    val person = selectedPerson ?: return@Button
                    onMove(movedTo(e, all, null, false, person))
                }) { Text("Confirm move") }
            }
        },
        dismissButton = {
            TextButton(onClick = {
                if (selectedPerson != null) selectedPerson = null
                else if (creatingKhata) creatingKhata = false
                else onDismiss()
            }) { Text(if (selectedPerson != null) "Back" else "Cancel") }
        }
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
    person: String? = null,
    onRenamePerson: (String, String) -> Unit = { _, _ -> }
) {
    val stack = remember(start, person) {
        mutableStateListOf<Level>(Level.Main(start)).apply { if (person != null) add(Level.Person(person)) }
    }
    // Keep the account list's scroll position alive while a person's khata is open.
    val peopleListState = rememberLazyListState()
    var savedPeopleIndex by remember { mutableIntStateOf(0) }
    var savedPeopleOffset by remember { mutableIntStateOf(0) }
    // Explicitly restore position because the list leaves composition while a khata is open.
    LaunchedEffect(stack.last()) {
        if (stack.last() is Level.Main) {
            peopleListState.scrollToItem(savedPeopleIndex, savedPeopleOffset)
        }
    }
    BackHandler(enabled = stack.size > 1) {
        if (stack.last() is Level.Person && stack[stack.lastIndex - 1] is Level.Main) {
            savedPeopleIndex = peopleListState.firstVisibleItemIndex
            savedPeopleOffset = peopleListState.firstVisibleItemScrollOffset
        }
        stack.removeAt(stack.lastIndex)
    }
    fun back() {
        if (stack.size > 1) {
            if (stack.last() is Level.Person && stack[stack.lastIndex - 1] is Level.Main) {
                savedPeopleIndex = peopleListState.firstVisibleItemIndex
                savedPeopleOffset = peopleListState.firstVisibleItemScrollOffset
            }
            stack.removeAt(stack.lastIndex)
        } else onClose()
    }
    when (val level = stack.last()) {
        is Level.Main -> when (level.acct) {
            MainAcct.EXPENSE, MainAcct.INCOME -> TreeView(level.acct == MainAcct.INCOME, entries, currency, ::back, onEdit)
            else -> PeopleView(
                level.acct == MainAcct.TO_RECEIVE, entries, currency, ::back,
                onOpen = {
                    savedPeopleIndex = peopleListState.firstVisibleItemIndex
                    savedPeopleOffset = peopleListState.firstVisibleItemScrollOffset
                    stack.add(Level.Person(it))
                },
                listState = peopleListState,
                onRenamePerson = onRenamePerson
            )
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
private fun PeopleView(
    receive: Boolean, entries: List<Entry>, currency: String,
    onBack: () -> Unit, onOpen: (String) -> Unit,
    listState: LazyListState, onRenamePerson: (String, String) -> Unit
) {
    val list = remember(entries, receive) {
        personAccounts(entries).filter { it.toReceive == receive }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name.trim() })
    }
    var actionPerson by remember { mutableStateOf<String?>(null) }
    var actionSource by remember { mutableStateOf<String?>(null) }
    var editAction by remember { mutableStateOf<String?>(null) }
    var actionName by remember { mutableStateOf("") }
    val total = list.sumOf { kotlin.math.abs(it.balance) }
    val color = if (receive) GreenIn else OrangeOwe
    LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = PaddingValues(bottom = 24.dp)) {
        item { Header(if (receive) "To receive" else "To pay", if (list.size == 1) "1 person" else "${list.size} people", onBack) }
        item { TotalsStrip((if (receive) "They owe you" else "You owe") to money(currency, total), color = color) }
        if (list.isEmpty()) item {
            Text(
                if (receive) "Nobody owes you right now." else "You don't owe anyone right now.",
                modifier = Modifier.padding(20.dp), color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        itemsIndexed(list, key = { _, p -> "p-" + p.name.lowercase() }) { i, p ->
            val settled = kotlin.math.abs(p.balance) < 0.005
            CardLine(
                i == 0, i == list.lastIndex, 0, 46.dp,
                modifier = Modifier.pointerInput(p.name) {
                    detectTapGestures(
                        onTap = { onOpen(p.name) },
                        onLongPress = { actionPerson = p.name }
                    )
                }
            ) {
                Box(
                    Modifier.size(26.dp).clip(RoundedCornerShape(13.dp)).background(color.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center
                ) { Text(p.name.trim().take(1).uppercase(), fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = color) }
                Spacer(Modifier.width(10.dp))
                Text(p.name, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Text(if (settled) "settled" else "${p.history.size}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    if (settled) "0" else fmt(kotlin.math.abs(p.balance)), fontWeight = FontWeight.ExtraBold, fontSize = 15.sp,
                    color = if (settled) MaterialTheme.colorScheme.onSurfaceVariant else color,
                    textAlign = androidx.compose.ui.text.style.TextAlign.End, modifier = Modifier.widthIn(min = 78.dp)
                )
            }
        }
    }

    if (actionPerson != null) {
        AlertDialog(
            onDismissRequest = { actionPerson = null },
            shape = RoundedCornerShape(26.dp),
            title = { Text(actionPerson.orEmpty(), fontWeight = FontWeight.Bold, fontSize = 21.sp) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Khata actions", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Surface(Modifier.fillMaxWidth().clickable {
                        actionSource = actionPerson
                        actionName = actionPerson.orEmpty()
                        editAction = "Rename"
                        actionPerson = null
                    }, shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant) {
                        Text("✎   Rename khata", Modifier.padding(16.dp), fontWeight = FontWeight.SemiBold)
                    }
                    Surface(Modifier.fillMaxWidth().clickable {
                        actionSource = actionPerson
                        actionName = ""
                        editAction = "Move"
                        actionPerson = null
                    }, shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.primaryContainer) {
                        Text("⇄   Move / merge khata", Modifier.padding(16.dp), fontWeight = FontWeight.SemiBold)
                    }
                }
            },
            confirmButton = { TextButton(onClick = { actionPerson = null }) { Text("Close") } }
        )
    }
    if (editAction != null) {
        val oldName = actionSource.orEmpty()
        var searchOpen by remember(editAction) { mutableStateOf(false) }
        var confirmMerge by remember(editAction) { mutableStateOf(false) }
        val targets = remember(entries, oldName) {
            personAccounts(entries).map { it.name }.filterNot { it.equals(oldName, true) }
                .distinctBy { it.lowercase(Locale.ROOT) }
                .sortedWith(String.CASE_INSENSITIVE_ORDER)
        }
        val filtered = remember(targets, actionName) {
            if (actionName.isBlank()) emptyList()
            else targets.filter { it.contains(actionName.trim(), ignoreCase = true) }
        }
        AlertDialog(
            onDismissRequest = { editAction = null },
            shape = RoundedCornerShape(26.dp),
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(if (editAction == "Rename") "Rename khata" else "Move / merge", fontWeight = FontWeight.Bold)
                        Text(oldName, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (editAction != "Rename") IconButton(onClick = {
                        searchOpen = !searchOpen
                        actionName = ""
                    }) { Icon(if (searchOpen) Icons.Filled.Close else Icons.Filled.Search, contentDescription = "Search destination") }
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (editAction == "Rename") {
                        OutlinedTextField(actionName, { actionName = it }, modifier = Modifier.fillMaxWidth(),
                            label = { Text("New name") }, singleLine = true, shape = RoundedCornerShape(16.dp))
                    } else if (confirmMerge) {
                        Text("Merge $oldName into ${actionName.trim()}?")
                        Text("All entries will move to the selected khata.",
                            fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else if (!searchOpen) {
                        Text("Tap the search icon to find a khata.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        OutlinedTextField(actionName, { actionName = it }, modifier = Modifier.fillMaxWidth(),
                            label = { Text("Search khata") }, singleLine = true, shape = RoundedCornerShape(16.dp),
                            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) })
                        Text("${filtered.size} results · A–Z", fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 280.dp)) {
                            items(filtered, key = { it }) { target ->
                                Surface(Modifier.fillMaxWidth().padding(vertical = 3.dp).clickable {
                                    actionName = target
                                    confirmMerge = true
                                }, shape = RoundedCornerShape(14.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant) {
                                    Text(target, Modifier.padding(14.dp))
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(enabled = if (editAction == "Rename")
                    actionName.isNotBlank() && !actionName.trim().equals(oldName, true)
                    else confirmMerge && targets.any { it.equals(actionName.trim(), true) },
                    onClick = {
                        if (oldName.isNotBlank() && actionName.isNotBlank())
                            onRenamePerson(oldName, actionName.trim())
                        editAction = null
                        actionSource = null
                        actionName = ""
                    }) { Text(if (editAction == "Rename") "Save" else "Confirm merge") }
            },
            dismissButton = {
                TextButton(onClick = {
                    if (confirmMerge) { confirmMerge = false; searchOpen = true }
                    else { editAction = null; actionSource = null; actionName = "" }
                }) { Text(if (confirmMerge) "Back" else "Cancel") }
            }
        )
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
            val hist = p.history.reversed()
            itemsIndexed(hist, key = { _, h -> "h-" + h.first.id }) { i, (e, after) ->
                val outflow = personHistoryIsOutflow(e)
                CardLine(i == 0, i == hist.lastIndex, 0, 46.dp, modifier = Modifier.entryTap(e) { onEdit(e) }) {
                    Text(SimpleDateFormat("d MMM", Locale.US).format(Date(e.date)), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(48.dp))
                    Text(
                        e.type.label + (if (e.note.isNotBlank() && e.note != "Loans") " · " + e.note else ""),
                        fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f)
                    )
                    Column(horizontalAlignment = Alignment.End) {
                        Text((if (outflow) "−" else "+") + fmt(e.amount), fontWeight = FontWeight.Bold, fontSize = 14.sp, color = if (outflow) RedSpend else GreenIn)
                        Text(
                            when {
                                after > 0.005 -> "owes you " + fmt(after)
                                after < -0.005 -> "you owe " + fmt(-after)
                                else -> "settled"
                            },
                            fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
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

/** The thin "This month · All time" strip at the top of a tree. */
@Composable
private fun TotalsStrip(vararg items: Pair<String, String>, color: androidx.compose.ui.graphics.Color) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp).clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface).height(IntrinsicSize.Min)
    ) {
        items.forEachIndexed { i, (label, value) ->
            if (i > 0) Box(Modifier.width(1.dp).fillMaxHeight().background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)))
            Column(Modifier.weight(1f).padding(horizontal = 12.dp, vertical = 8.dp)) {
                Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(value, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/**
 * One line of a compact list inside a single card: [first] / [last] round the card's corners,
 * [depth] indents it with guide lines, [highlight] marks an open branch.
 */
@Composable
private fun CardLine(
    first: Boolean, last: Boolean, depth: Int, height: androidx.compose.ui.unit.Dp,
    highlight: Boolean = false, shade: androidx.compose.ui.graphics.Color? = null,
    modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit
) {
    val top = if (first) 16.dp else 0.dp
    val bottom = if (last) 16.dp else 0.dp
    val primary = MaterialTheme.colorScheme.primary
    val bg = when {
        highlight -> primary.copy(alpha = 0.12f)
        shade != null -> shade
        depth == 0 -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        else -> MaterialTheme.colorScheme.surface
    }
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(topStart = top, topEnd = top, bottomStart = bottom, bottomEnd = bottom))
            .background(MaterialTheme.colorScheme.surface)
    ) {
        if (!first && depth == 0) HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
        Row(Modifier.fillMaxWidth().height(height).background(bg).then(modifier), verticalAlignment = Alignment.CenterVertically) {
            if (highlight) Box(Modifier.width(3.dp).fillMaxHeight().background(primary))
            // Guide lines: one per level, so it's clear what sits under what.
            for (d in 1..depth) {
                Spacer(Modifier.width(if (d == 1) 28.dp else 14.dp))
                Box(Modifier.width(2.dp).fillMaxHeight().background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = if (d == 1) 0.8f else 0.55f)))
            }
            Spacer(Modifier.width(if (highlight) 9.dp else 12.dp))
            content()
            Spacer(Modifier.width(12.dp))
        }
    }
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
    val timeFmt = SimpleDateFormat("d MMM", Locale.US)
    val clockFmt = SimpleDateFormat("h:mm a", Locale.US)
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item { Header(if (income) "Income" else "Expense", count(mine.size), onBack) }
        item {
            TotalsStrip(
                "This month" to fmt(mine.filter { it.date >= m0 }.sumOf { it.amount }),
                "All time" to fmt(mine.sumOf { it.amount }), color = color
            )
        }
        if (tree.isEmpty()) item { Text("Nothing here yet.", modifier = Modifier.padding(20.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
        itemsIndexed(rows, key = { _, (_, x) -> if (x is Node) "n-" + x.key else "e-" + (x as Entry).id }) { i, (depth, x) ->
            val first = i == 0
            val last = i == rows.lastIndex
            if (x is Node) {
                val isOpen = x.key in open
                val toggle = Modifier.clickable { open = if (isOpen) open.filterNot { it == x.key || it.startsWith(x.key + "/") }.toSet() else open + x.key }
                if (depth == 0) {
                    CardLine(first, last, 0, 46.dp, highlight = isOpen, modifier = toggle) {
                        Text(if (isOpen) "▾" else "▸", fontSize = 12.sp, color = if (isOpen) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(14.dp))
                        CatBubble(x.list.first().category, 26.dp)
                        Spacer(Modifier.width(10.dp))
                        Text(x.name, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        Text("${x.list.size}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(fmt(x.list.sumOf { it.amount }), fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, color = color, textAlign = androidx.compose.ui.text.style.TextAlign.End, modifier = Modifier.widthIn(min = 78.dp))
                    }
                } else {
                    CardLine(first, last, depth, 38.dp, modifier = toggle) {
                        Text(if (isOpen) "▾" else "▸", fontSize = 11.sp, color = if (isOpen) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(12.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(x.name, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        Text("${x.list.size}×", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(fmt(x.list.sumOf { it.amount }), fontWeight = FontWeight.Bold, fontSize = 14.sp, color = color, textAlign = androidx.compose.ui.text.style.TextAlign.End, modifier = Modifier.widthIn(min = 64.dp))
                    }
                }
            } else {
                val e = x as Entry
                val place = e.note.substringAfter(", ", "").ifBlank { e.note.takeIf { !it.equals(e.item, true) }.orEmpty() }
                CardLine(
                    first, last, depth, 34.dp, shade = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
                    modifier = Modifier.entryTap(e) { onEdit(e) }
                ) {
                    Text(timeFmt.format(Date(e.date)), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(48.dp))
                    Text(
                        listOf(place, clockFmt.format(Date(e.date))).filter { it.isNotBlank() }.joinToString(" · "),
                        fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f)
                    )
                    Text((if (income) "+" else "−") + fmt(e.amount), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = color)
                }
            }
        }
    }
}
