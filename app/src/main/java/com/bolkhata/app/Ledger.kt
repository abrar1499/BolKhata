package com.bolkhata.app

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.ui.window.Dialog
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.graphics.Color
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
    EXPENSE("Expense"), INCOME("Income"), TO_PAY("To give"), TO_RECEIVE("To get"), CASH("Cash in hand")
}

/** Opens one of the five main accounts. Set by the main screen. */
val LocalMainOpener = staticCompositionLocalOf<((MainAcct) -> Unit)?> { null }

/** Long-press on an entry anywhere opens its action menu (Edit / Move). Set by the main screen. */
val LocalEntryMenu = staticCompositionLocalOf<((Entry) -> Unit)?> { null }

/** Entries picked for a move: long-press → Select starts it, taps add or remove, the bar at the bottom moves them. */
class Selection {
    val ids = mutableStateListOf<String>()
    var active by mutableStateOf(false)
    fun toggle(id: String) { if (id in ids) ids.remove(id) else ids.add(id) }
    fun clear() { ids.clear(); active = false }
}

val LocalSelection = staticCompositionLocalOf<Selection?> { null }

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

/**
 * What an entry does to a person's balance (+ = they owe me more). A bank transfer to a person counts like money
 * given and one from them like money received, so the khata's balance matches its Paid / Received columns.
 */
fun personEffect(e: Entry): Double = when (e.type) {
    Type.DIYA, Type.WAPIS_DIYA, Type.SALE, Type.TRANSFER_OUT -> e.amount
    Type.LIYA, Type.WAPIS_MILA, Type.PURCHASE, Type.TRANSFER_IN -> -e.amount
    else -> 0.0
}

/** Direction shown for a transaction in a person's history. */
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

internal fun isOutflow(e: Entry) = when (e.type) {
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

/** Ready sub-categories (an expense's item) offered under each category when moving or filling an entry. */
val defaultSubs: Map<Cat, List<String>> = mapOf(
    Cat.TRANSPORT to listOf("Petrol", "Car repair", "Oil change", "Tyres", "Car wash", "Parking", "Taxi", "Bus"),
    Cat.KHANA to listOf("Groceries", "Restaurant", "Bread", "Sweets", "Tea", "Fruit & veg", "Water"),
    Cat.BIJLI to listOf("Electricity", "Water bill", "Gas"),
    Cat.MOBILE to listOf("Recharge", "Internet"),
    Cat.SEHAT to listOf("Medicine", "Doctor"),
    Cat.PERSONAL to listOf("Haircut", "Soap & shampoo"),
    Cat.FEES to listOf("ID card", "Bank fee", "Visa"),
    Cat.SHOPPING to listOf("Clothes", "Electronics"),
    Cat.EDU to listOf("School fees", "Books"),
    Cat.RENT to listOf("House rent"),
    Cat.CHARITY to listOf("Gifts", "Charity"),
    Cat.PRODUCTION to listOf("Labour", "Raw material", "Fuel", "Loading", "Unloading", "Office", "Salary", "Transport")
)

/** A category's sub-categories: the ready ones first, then names already used there at least twice. */
fun subsFor(cat: Cat, all: List<Entry>): List<String> {
    val used = all.filter { it.type == Type.KHARCHA && it.category == cat }.groupingBy { it.item.trim() }.eachCount()
        .filter { (n, c) -> c >= 2 && n.length in 2..24 && !n.equals(cat.label, true) && !n.equals("Other", true) }
        .entries.sortedByDescending { it.value }.map { it.key }
    return (defaultSubs[cat].orEmpty() + used).distinctBy { it.lowercase() }
}

/** An entry put under sub-category [sub]: that becomes its item; the old name (a shop) stays at the front of the note. */
fun withSub(e: Entry, sub: String): Entry {
    val old = e.item.trim()
    val keep = old.isNotBlank() && !old.equals(sub, true) && !old.equals(e.category.label, true) && !e.note.contains(old, true)
    return e.copy(item = sub, note = if (keep) listOf(old, e.note.trim()).filter { it.isNotBlank() }.joinToString(" · ") else e.note)
}

/** "Transport › Petrol" to (category label, sub-category); the sub is blank for a plain category. */
fun splitSub(to: String): Pair<String, String> = to.substringBefore(" › ").trim() to to.substringAfter(" › ", "").trim()

/** Every khata to move into, by kind: "party", "business" (stock item), "income" (who paid), "expense" (category label). */
@Composable
fun MoveToKhata(subtitle: String, all: List<Entry>, except: String, onMove: (kind: String, name: String) -> Unit, onDismiss: () -> Unit) {
    val people = remember(all) { partyNames(all) }
    val stockNames = remember(all) { stock(all).map { it.name.trim() }.filter { it.isNotBlank() }.distinctBy { it.lowercase() } }
    val incomeNames = remember(all) { all.filter { it.type == Type.AAMDANI }.map { source(it).trim() }.filter { it.isNotBlank() }.distinctBy { it.lowercase() } }
    PartyPicker(
        people.filterNot { it.equals(except, true) }, "", "Move to",
        confirm = { creating -> if (creating) "Create & move" else "Move" },
        onPick = { onMove("party", it) }, onDismiss = onDismiss,
        categories = expenseCats, onCategory = { onMove("expense", it.label) },
        subs = { subsFor(it, all) }, onSub = { c, s -> onMove("expense", c.label + " › " + s) },
        stockNames = stockNames.filterNot { it.equals(except, true) }, onStock = { onMove("business", it) },
        incomeNames = incomeNames.filterNot { it.equals(except, true) }, onIncome = { onMove("income", it) },
        subtitle = subtitle
    )
}

/** Move entry: pick the party (khata) the entry goes to. */
@Composable
fun MoveDialog(e: Entry, all: List<Entry>, onMove: (Entry) -> Unit, onDismiss: () -> Unit, count: Int = 1) {
    val people = remember(all) { partyNames(all) }
    PartyPicker(
        people, e.person, if (count == 1) "Move entry" else "Move $count entries",
        confirm = { creating -> if (creating) "Create & move" else if (count == 1) "Move" else "Move $count" },
        onPick = { onMove(movedTo(e, all, null, false, it)) }, onDismiss = onDismiss,
        // Or into an expense category ("Other" -> Food, or a party's entry -> a plain expense).
        categories = Cat.entries.filter { it !in setOf(Cat.UDHAAR, Cat.TANKHWAH, Cat.SALES, Cat.STOCK) },
        currentCat = e.category.takeIf { e.type == Type.KHARCHA },
        onCategory = { onMove(movedTo(e, all, it, false, null)) },
        subs = { subsFor(it, all) }, onSub = { c, s -> onMove(withSub(movedTo(e, all, c, false, null), s)) }
    )
}

/** Every saved party (khata) name, A–Z. The real account key is kept; only the shown label is cleaned. */
fun partyNames(all: List<Entry>): List<String> =
    (personAccounts(all).map { it.name } + all.map { it.person })
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .distinctBy { it.lowercase(Locale.ROOT) }
        .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { cleanMoveKhataName(it) })

/**
 * The khata list used by Move and by the entry form: a compact white card with the title, tabs (Party, Business, Income,
 * Expense when given), one search box that also takes a new name, the list, and Cancel / confirm side by side.
 * [current] is marked and can't be picked.
 */
@Composable
fun PartyPicker(
    people: List<String>, current: String, title: String,
    confirm: (creating: Boolean) -> String, onPick: (String) -> Unit, onDismiss: () -> Unit,
    /** When given, an Expense tab: the entry can go to an expense category instead of a party. */
    categories: List<Cat> = emptyList(), currentCat: Cat? = null, onCategory: (Cat) -> Unit = {},
    /** Sub-categories shown under the picked category (Transport: Petrol, Car repair…); picking one moves into it. */
    subs: (Cat) -> List<String> = { emptyList() }, onSub: (Cat, String) -> Unit = { c, _ -> onCategory(c) },
    /** When given, Business (stock items) and Income (who paid) tabs too, so one picker reaches every khata. */
    stockNames: List<String> = emptyList(), onStock: (String) -> Unit = {},
    incomeNames: List<String> = emptyList(), onIncome: (String) -> Unit = {},
    subtitle: String = ""
) {
    val tabs = listOfNotNull("Party", "Business".takeIf { stockNames.isNotEmpty() }, "Income".takeIf { incomeNames.isNotEmpty() },
        "Expense".takeIf { categories.isNotEmpty() })
    var tab by remember { mutableStateOf("Party") }
    var pickedCat by remember { mutableStateOf<Cat?>(null) }
    var pickedSub by remember(pickedCat) { mutableStateOf<String?>(null) }
    var picked by remember(tab) { mutableStateOf<String?>(null) }
    var query by remember(tab) { mutableStateOf("") }
    val q = query.trim()
    val names = when (tab) { "Business" -> stockNames; "Income" -> incomeNames; else -> people }
    val shown = remember(names, q) {
        if (q.isBlank()) names else names.filter { cleanMoveKhataName(it).contains(q, true) || it.contains(q, true) }
    }
    val exact = names.any { cleanMoveKhataName(it).equals(q, true) || it.equals(q, true) }
    val creating = picked != null && names.none { it.equals(picked, true) }
    val sub = MaterialTheme.colorScheme.onSurfaceVariant
    val shape = RoundedCornerShape(12.dp)
    Dialog(onDismissRequest = onDismiss, properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxWidth(0.86f), shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.padding(16.dp)) {
                Text(title, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (subtitle.isNotBlank()) Text(subtitle, fontSize = 11.sp, color = sub, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (tabs.size > 1) Row(
                    Modifier.padding(top = 12.dp).fillMaxWidth().clip(RoundedCornerShape(11.dp)).background(MaterialTheme.colorScheme.background).padding(3.dp)
                ) {
                    tabs.forEach { label ->
                        val on = tab == label
                        Box(
                            Modifier.weight(1f).clip(RoundedCornerShape(9.dp))
                                .background(if (on) MaterialTheme.colorScheme.surface else Color.Transparent)
                                .clickable { tab = label }.padding(vertical = 7.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(label, fontSize = 11.5.sp, maxLines = 1, fontWeight = FontWeight.SemiBold,
                                color = if (on) MaterialTheme.colorScheme.onSurface else sub)
                        }
                    }
                }
                if (tab == "Expense") LazyColumn(Modifier.padding(top = 6.dp).fillMaxWidth().heightIn(max = 260.dp)) {
                    categories.forEach { c ->
                        item(key = c.name) {
                            KhataChoice(c.icon, c.label, c == pickedCat && pickedSub == null, c == currentCat && subs(c).isEmpty()) { pickedCat = c }
                        }
                        // The picked category opens its sub-categories right under it.
                        if (c == pickedCat) items(subs(c), key = { c.name + "/" + it }) { s ->
                            Box(Modifier.padding(start = 26.dp)) { KhataChoice("·", s, pickedSub == s, false) { pickedSub = s } }
                        }
                    }
                } else {
                    // One box: search the list, or type a new name (Party only; Business and Income too).
                    BasicTextField(
                        query, { query = it; picked = null }, singleLine = true,
                        textStyle = LocalTextStyle.current.copy(fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.onSurface),
                        modifier = Modifier.padding(top = 10.dp).fillMaxWidth().height(38.dp).clip(shape)
                            .background(MaterialTheme.colorScheme.background).padding(horizontal = 12.dp),
                        decorationBox = { field ->
                            Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.Search, contentDescription = null, tint = sub, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(8.dp))
                                Box(Modifier.weight(1f)) {
                                    if (query.isEmpty()) Text("Search or type a new name", fontSize = 13.sp, color = sub, maxLines = 1)
                                    field()
                                }
                            }
                        }
                    )
                    LazyColumn(Modifier.padding(top = 6.dp).fillMaxWidth().heightIn(max = 250.dp)) {
                        if (q.isNotBlank() && !exact) item(key = "+new") {
                            KhataChoice("+", "New: $q", picked == q, false) { picked = q }
                        }
                        items(shown, key = { it }) { n ->
                            val isCurrent = current.equals(n, true)
                            KhataChoice(cleanMoveKhataName(n).take(1).uppercase(), cleanMoveKhataName(n), n == picked, isCurrent) { if (!isCurrent) picked = n }
                        }
                        if (shown.isEmpty() && q.isBlank()) item(key = "none") {
                            Text("Nothing here yet. Type a name to add one.", fontSize = 12.sp, color = sub, modifier = Modifier.padding(8.dp))
                        }
                    }
                }
                Row(Modifier.padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onDismiss, shape = shape, modifier = Modifier.weight(1f).height(40.dp)) { Text("Cancel") }
                    Button(
                        enabled = if (tab == "Expense") pickedCat != null else picked != null, shape = shape,
                        colors = ButtonDefaults.buttonColors(containerColor = RedSpend), modifier = Modifier.weight(1f).height(40.dp),
                        onClick = {
                            when (tab) {
                                "Expense" -> { val c = pickedCat ?: return@Button; pickedSub?.let { onSub(c, it) } ?: onCategory(c) }
                                "Business" -> onStock(picked ?: return@Button)
                                "Income" -> onIncome(picked ?: return@Button)
                                else -> onPick(picked ?: return@Button)
                            }
                        }
                    ) { Text(confirm(creating && tab != "Expense"), fontWeight = FontWeight.Bold, maxLines = 1) }
                }
            }
        }
    }
}

/** One khata in the list: initial, name, and a red tick when picked ("Current" for the entry's own khata). */
@Composable
private fun KhataChoice(initial: String, name: String, picked: Boolean, current: Boolean, onClick: () -> Unit) {
    val red = RedSpend
    Row(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (picked) red.copy(alpha = 0.08f) else Color.Transparent)
            .clickable(enabled = !current) { onClick() }
            .padding(horizontal = 6.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(28.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
            Text(initial, color = red, fontWeight = FontWeight.ExtraBold, fontSize = 12.sp)
        }
        Spacer(Modifier.width(10.dp))
        Text(name, Modifier.weight(1f), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
        when {
            current -> Text("Current", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            picked -> Text("✓", color = red, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
        }
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
    onRenamePerson: (String, String) -> Unit = { _, _ -> },
    onMergePerson: (String, String) -> Unit = { _, _ -> },
    onDeletePerson: (String) -> Unit = {},
    onBulkMove: (Set<String>, String) -> Unit = { _, _ -> },
    onActivePerson: (String?) -> Unit = {},
    onMoveHistory: () -> Unit = {}
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
    // A khata opened straight to one person (from chat) goes back to where it was opened from.
    val direct = person != null
    fun back() {
        if (direct && stack.size == 2) onClose()
        else if (stack.size > 1) {
            if (stack.last() is Level.Person && stack[stack.lastIndex - 1] is Level.Main) {
                savedPeopleIndex = peopleListState.firstVisibleItemIndex
                savedPeopleOffset = peopleListState.firstVisibleItemScrollOffset
            }
            stack.removeAt(stack.lastIndex)
        } else onClose()
    }
    BackHandler(enabled = stack.size > 1) { back() }
    SideEffect { onActivePerson((stack.last() as? Level.Person)?.name) }
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
                onRenamePerson = onRenamePerson,
                onMergePerson = onMergePerson,
                onDeletePerson = onDeletePerson
            )
        }
        is Level.Sub -> SubView(level.income, level.cat, entries, currency, ::back, onEdit) { stack.add(Level.Vendor(level.income, level.cat, it)) }
        is Level.Vendor -> VendorView(level, entries, currency, ::back, onEdit)
        is Level.Person -> PersonView(level.name, entries, currency, ::back, onEdit, onNew, onBulkMove, onMoveHistory)
    }
}

@Composable
private fun Header(title: String, subtitle: String, onBack: () -> Unit) {
    // The same glass band as the khata screens (white text, so the icon's colours on light themes).
    Row(
        Modifier.fillMaxWidth().then(LocalHeader.current.let { t -> Modifier.drawBehind { drawGlass(glassColors(t), size.height) } })
            .statusBarsPadding().padding(start = 4.dp, end = 16.dp, top = 6.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White) }
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 17.sp, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle.isNotBlank()) Text(subtitle, fontSize = 11.sp, color = Color.White.copy(alpha = 0.85f))
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
private fun SubView(
    income: Boolean, cat: Cat, entries: List<Entry>, currency: String,
    onBack: () -> Unit, onEdit: (Entry) -> Unit, onVendor: (String) -> Unit
) {
    val type = if (income) Type.AAMDANI else Type.KHARCHA
    val mine = remember(entries, cat, income) {
        entries.filter { it.type == type && it.category == cat }.sortedWith(compareByDescending<Entry> { it.date }.thenByDescending { it.createdAt })
    }
    val vendors = remember(mine) { mine.groupBy { vendorKey(it) }.values.sortedBy { vendorKey(it.first()) } }
    val m0 = monthStart()
    val color = if (income) GreenIn else RedSpend
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = barSpace())) {
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
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = barSpace())) {
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
    listState: LazyListState, onRenamePerson: (String, String) -> Unit,
    onMergePerson: (String, String) -> Unit, onDeletePerson: (String) -> Unit
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
    LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = PaddingValues(bottom = barSpace())) {
        item { Header(if (receive) "To get" else "To give", if (list.size == 1) "1 person" else "${list.size} people", onBack) }
        item { TotalsStrip((if (receive) "You will get" else "You will give") to money(currency, total), color = color) }
        if (list.isEmpty()) item {
            Text(
                if (receive) "Nothing to get right now." else "Nothing to give right now.",
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

    actionPerson?.let { name ->
        val account = list.firstOrNull { it.name == name }
        fun open(action: String, initial: String) {
            actionSource = name
            actionName = initial
            editAction = action
            actionPerson = null
        }
        KhataMenu(
            name, account?.history?.size ?: 0, account?.balance ?: 0.0, currency,
            onRename = { open("Rename", name) },
            onMerge = { open("Move", "") },
            onDelete = { actionPerson = null; onDeletePerson(name) },
            onDismiss = { actionPerson = null }
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
                        if (targets.any { it.equals(actionName.trim(), true) }) {
                            Text("This khata already exists. Use Move / merge instead.", color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                        }
                    } else if (confirmMerge) {
                        Text("Merge $oldName into ${actionName.trim()}?")
                        Text("${entries.count { it.person.equals(oldName, true) }} entries will move into a khata with ${entries.count { it.person.equals(actionName.trim(), true) }} existing entries.",
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
                    actionName.isNotBlank() && !actionName.trim().equals(oldName, true) &&
                        targets.none { it.equals(actionName.trim(), true) }
                    else confirmMerge && targets.any { it.equals(actionName.trim(), true) },
                    onClick = {
                        if (oldName.isNotBlank() && actionName.isNotBlank()) {
                            if (editAction == "Rename") onRenamePerson(oldName, actionName.trim())
                            else if (confirmMerge) onMergePerson(oldName, actionName.trim())
                        }
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
    onBack: () -> Unit, onEdit: (Entry) -> Unit, onNew: (Entry) -> Unit,
    onBulkMove: (Set<String>, String) -> Unit,
    onMoveHistory: () -> Unit
) {
    val selectedIds = remember(name) { mutableStateListOf<String>() }
    var pickingDestination by remember(name) { mutableStateOf(false) }
    val p = remember(entries, name) { personAccounts(entries).firstOrNull { it.name.equals(name, true) } }
    val df = SimpleDateFormat("d MMM yyyy", Locale.US)
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = barSpace())) {
        item { Header(name, if (p == null) "" else (if (p.toReceive) "To get" else "To give") + " · " + count(p.history.size), onBack) }
        item { TextButton(onClick = onMoveHistory, modifier = Modifier.padding(horizontal = 16.dp)) { Text("Move history & restore") } }
        if (p == null) {
            item { Text("No entries with $name.", modifier = Modifier.padding(20.dp)) }
        } else {
            item {
                val bal = p.balance
                val text = when {
                    bal > 0.005 -> "$name will pay you " + money(currency, bal)
                    bal < -0.005 -> "You will pay $name " + money(currency, -bal)
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
            val hist = p.history.reversed()
            item {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text("History", modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold)
                    TextButton(onClick = {
                        if (selectedIds.size == hist.size) selectedIds.clear()
                        else { selectedIds.clear(); selectedIds.addAll(hist.map { it.first.id }) }
                    }) { Text(if (selectedIds.size == hist.size && hist.isNotEmpty()) "Clear all" else "Select all") }
                    if (selectedIds.isNotEmpty()) {
                        Button(onClick = { pickingDestination = true }) { Text("Move ${selectedIds.size}") }
                    }
                }
            }
            itemsIndexed(hist, key = { _, h -> "h-" + h.first.id }) { i, (e, after) ->
                val outflow = personHistoryIsOutflow(e)
                CardLine(i == 0, i == hist.lastIndex, 0, 46.dp,
                    modifier = Modifier.pointerInput(e.id, selectedIds.size) {
                        detectTapGestures(
                            onLongPress = { if (e.id !in selectedIds) selectedIds.add(e.id) },
                            onTap = {
                                if (selectedIds.isEmpty()) onEdit(e)
                                else if (e.id in selectedIds) selectedIds.remove(e.id)
                                else selectedIds.add(e.id)
                            }
                        )
                    }) {
                    if (selectedIds.isNotEmpty()) {
                        Checkbox(checked = e.id in selectedIds,
                            onCheckedChange = { checked ->
                                if (checked && e.id !in selectedIds) selectedIds.add(e.id)
                                if (!checked) selectedIds.remove(e.id)
                            })
                    }
                    Text(SimpleDateFormat("d MMM", Locale.US).format(Date(e.date)), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(48.dp))
                    Text(
                        e.type.label + (if (e.note.isNotBlank() && e.note != "Loans") " · " + e.note else ""),
                        fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f)
                    )
                    Column(horizontalAlignment = Alignment.End) {
                        Text((if (outflow) "−" else "+") + fmt(e.amount), fontWeight = FontWeight.Bold, fontSize = 14.sp, color = if (outflow) RedSpend else GreenIn)
                        Text(
                            when {
                                after > 0.005 -> "to get " + fmt(after)
                                after < -0.005 -> "to give " + fmt(-after)
                                else -> "settled"
                            },
                            fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
    if (pickingDestination && selectedIds.isNotEmpty()) {
        val sample = p?.history?.firstOrNull { it.first.id in selectedIds }?.first
        if (sample != null) {
            MoveDialog(sample, entries, onMove = { moved ->
                val ids = selectedIds.toSet()
                pickingDestination = false
                selectedIds.clear()
                if (!moved.person.equals(name, ignoreCase = true)) onBulkMove(ids, moved.person)
            }, onDismiss = { pickingDestination = false }, count = selectedIds.size)
        }
    }
}

/** Recent moves can be restored even after the app is restarted. */
@Composable
fun MoveHistoryDialog(
    history: List<MoveBatch>,
    onRestore: (MoveBatch) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Move history") },
        text = {
            if (history.isEmpty()) {
                Text("No recent account moves to restore.")
            } else {
                LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 390.dp)) {
                    items(history, key = { it.id }) { batch ->
                        val first = batch.changes.firstOrNull()
                        Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                            Text(
                                (first?.fromPerson?.ifBlank { "Unassigned" } ?: "Unassigned") +
                                    " → " + (first?.toPerson ?: "") +
                                    " · " + batch.changes.size + " entries",
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 2
                            )
                            Text(
                                SimpleDateFormat("d MMM, h:mm a", Locale.US).format(Date(batch.timestamp)),
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            TextButton(onClick = { onRestore(batch) }) { Text("Restore this move") }
                            HorizontalDivider()
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}

/** A blank loan entry with [person], used to open the form from a khata. */
internal fun template(type: Type, person: String): Entry {
    val now = System.currentTimeMillis()
    return Entry(
        id = "e" + System.nanoTime(), type = type, amount = 0.0, category = Cat.UDHAAR, item = "Loans",
        person = person, note = "", date = now, createdAt = now
    )
}

// ---------------------------------------------------------------- tree view

/** One branch of the Expense / Income tree: a name, its total, how many entries, and what's under it. */
private class Node(val key: String, val name: String, val list: List<Entry>, val children: List<Node>)

private fun sortedNewest(l: List<Entry>) = l.sortedWith(compareByDescending<Entry> { it.date }.thenByDescending { it.createdAt })

private fun groupNodes(prefix: String, list: List<Entry>, keyOf: (Entry) -> String, nameOf: (List<Entry>) -> String, children: (String, List<Entry>) -> List<Node>): List<Node> =
    list.groupBy { keyOf(it).lowercase().ifBlank { "-" } }
        .map { (k, l) -> Node("$prefix/$k", nameOf(l), sortedNewest(l), children("$prefix/$k", l)) }
        .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name.trim() }) // khatas always A–Z

/** Expense / income list: each category with its entries straight away (the detail is in Reports). */
private fun buildTree(list: List<Entry>): List<Node> =
    groupNodes("c", list, { it.category.name }, { it.first().category.label }) { _, _ -> emptyList() }

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
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = barSpace())) {
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
                // A clean ">" at the end that turns down when open (instead of a small triangle in front).
                val chevron = @Composable {
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = if (isOpen) "Close" else "Open",
                        tint = if (isOpen) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp).rotate(if (isOpen) 90f else 0f)
                    )
                }
                if (depth == 0) Column {
                    // A line between main headings.
                    if (!first) HorizontalDivider(Modifier.padding(horizontal = 20.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                    CardLine(first, last, 0, 46.dp, highlight = isOpen, modifier = toggle) {
                        CatBubble(x.list.first().category, 26.dp)
                        Spacer(Modifier.width(10.dp))
                        Text(x.name, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        Text("${x.list.size}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(fmt(x.list.sumOf { it.amount }), fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, color = color, textAlign = androidx.compose.ui.text.style.TextAlign.End, modifier = Modifier.widthIn(min = 78.dp))
                        Spacer(Modifier.width(4.dp))
                        chevron()
                    }
                } else {
                    CardLine(first, last, depth, 38.dp, modifier = toggle) {
                        Text(x.name, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        Text("${x.list.size}×", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(fmt(x.list.sumOf { it.amount }), fontWeight = FontWeight.Bold, fontSize = 14.sp, color = color, textAlign = androidx.compose.ui.text.style.TextAlign.End, modifier = Modifier.widthIn(min = 64.dp))
                        Spacer(Modifier.width(4.dp))
                        chevron()
                    }
                }
            } else {
                val e = x as Entry
                // What it is first ("Bank transfer fee"), then the bank's long note in grey.
                val title = entryTitle(e)
                val note = e.note.trim().takeIf { it.isNotBlank() && !it.equals(title, true) && !it.equals(e.item, true) }
                CardLine(
                    first, last, depth, 34.dp, shade = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
                    modifier = Modifier.entryTap(e) { onEdit(e) }
                ) {
                    // The date, and the time small under it.
                    Column(Modifier.width(52.dp)) {
                        Text(timeFmt.format(Date(e.date)), fontSize = 12.sp, lineHeight = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(clockFmt.format(Date(e.date)), fontSize = 9.sp, lineHeight = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f))
                    }
                    val sub = MaterialTheme.colorScheme.onSurfaceVariant
                    Text(
                        androidx.compose.ui.text.buildAnnotatedString {
                            withStyle(androidx.compose.ui.text.SpanStyle(fontWeight = FontWeight.SemiBold)) { append(title) }
                            if (note != null) withStyle(androidx.compose.ui.text.SpanStyle(color = sub)) { append(" · $note") }
                        },
                        fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f)
                    )
                    Text((if (income) "+" else "−") + fmt(e.amount), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = color)
                }
            }
        }
    }
}
