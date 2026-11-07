package com.bolkhata.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowDropUp
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

fun blankEntry(): Entry {
    val now = System.currentTimeMillis()
    return Entry(
        id = "e" + System.nanoTime(), type = Type.KHARCHA, amount = 0.0, category = Cat.KHANA,
        item = "", person = "", note = "", date = now, createdAt = now
    )
}

/** Money going out of the owner's hand: expense, lent, paid back, bought, sent. */
private fun isOut(t: Type) = t == Type.KHARCHA || t == Type.DIYA || t == Type.WAPIS_DIYA || t == Type.PURCHASE || t == Type.TRANSFER_OUT

/** A rounded popup that floats over the screen instead of filling it. */
@Composable
fun FormPopup(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    val maxH = (LocalConfiguration.current.screenHeightDp * 0.86f).dp
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.88f)
                .heightIn(max = maxH),
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 12.dp
        ) { content() }
    }
}

/**
 * One clean form for adding, editing and answering "what is this?": Out | In (none picked when [askType]), What for
 * (a drop list), qty × rate, amount, party, Paid | Due, note. The app works out the kind from these (see kind()).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EntryForm(
    initial: Entry,
    title: String,
    question: String?,
    askType: Boolean,
    currency: String,
    saveLabel: String,
    onSave: (Entry) -> Unit,
    onDelete: (() -> Unit)?,
    onCancel: () -> Unit,
    header: @Composable () -> Unit = {},
    /** All saved entries: the party list and each party's balance come from them. */
    all: List<Entry> = emptyList()
) {
    var out by remember(initial.id) { mutableStateOf<Boolean?>(if (askType) null else isOut(initial.type)) }
    val people = remember(all) { partyNames(all) }
    val stockNames = remember(all) { stock(all).map { it.name.trim() }.filter { it.isNotBlank() }.distinctBy { it.lowercase() } }
    /** Names used before on each side, newest first: they join the What for list by themselves. */
    val usedOut = remember(all) { usedNames(all.filter { it.type == Type.KHARCHA }) }
    val usedIn = remember(all) { usedNames(all.filter { it.type == Type.AAMDANI }) }
    var amount by remember(initial.id) { mutableStateOf(if (initial.amount > 0) plain(initial.amount) else "") }
    var item by remember(initial.id) { mutableStateOf(if (initial.item == "Loans" || isTransfer(initial.type) && ownerMove(initial.item) == null) "" else initial.item) }
    /** The category of a name picked from the list; null when typed (then guessed from the words). */
    var pickedCat by remember(initial.id) { mutableStateOf<Cat?>(null) }
    /** A new name added as a stock item, so it is bought or sold rather than spent. */
    var newStock by remember(initial.id) { mutableStateOf(false) }
    var listOpen by remember { mutableStateOf(false) }
    var qty by remember(initial.id) { mutableStateOf(if (initial.qty > 0) plain(initial.qty) else "") }
    var rate by remember(initial.id) { mutableStateOf(if (initial.rate > 0) plain(initial.rate) else "") }
    var person by remember(initial.id) { mutableStateOf(initial.person) }
    /** Due: not paid yet, it stays in the party's khata. Paid: settled now. */
    var due by remember(initial.id) { mutableStateOf(isDue(initial) || (isTrade(initial.type) && initial.person.isNotBlank() && !initial.paid)) }
    var date by remember(initial.id) { mutableLongStateOf(initial.date) }
    var note by remember(initial.id) { mutableStateOf(if (initial.note == initial.item) "" else initial.note) }
    var showDate by remember { mutableStateOf(false) }
    var askDelete by remember { mutableStateOf(false) }

    val amountValue = amount.toDoubleOrNull()
    val accent = when (out) { true -> RedSpend; false -> GreenIn; null -> MaterialTheme.colorScheme.primary }
    val itemT = item.trim()
    val isStock = newStock || stockNames.any { it.equals(itemT, true) }
    // An old loan keeps its goods name ("Loans" aside) without turning into an expense.
    val loanItem = isLoan(initial.type) && itemT.equals(initial.item.trim(), true)
    val withItem = itemT.isNotEmpty() && !loanItem

    /**
     * The entry's kind, worked out from Out / In, the item and the party: a stock item is a sale or purchase; an item
     * with a party is an expense or income in their khata (Due) or settled (Paid); a party alone is money lent or paid
     * back, from their balance; nothing else is a plain expense or income.
     */
    fun kind(o: Boolean): Type {
        val p = person.trim()
        if (ownerMove(itemT) != null) return if (o) Type.TRANSFER_OUT else Type.TRANSFER_IN
        if (!askType && isTransfer(initial.type) && o == isOut(initial.type)) return initial.type
        if (withItem && isStock) return if (o) Type.PURCHASE else Type.SALE
        if (p.isEmpty() || withItem) return if (o) Type.KHARCHA else Type.AAMDANI
        val bal = personAccounts(all.filter { it.id != initial.id }).firstOrNull { it.name.equals(p, true) }?.balance ?: 0.0
        return if (o) (if (bal < -0.005) Type.WAPIS_DIYA else Type.DIYA) else (if (bal > 0.005) Type.WAPIS_MILA else Type.LIYA)
    }
    val chosen = out?.let { kind(it) }

    /** Amount = qty × rate whenever both are filled in. */
    fun fillAmount() {
        val q = qty.toDoubleOrNull() ?: 0.0
        val r = rate.toDoubleOrNull() ?: 0.0
        if (q > 0 && r > 0) amount = plain(Math.round(q * r * 100) / 100.0)
    }

    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        header()

        // Title row
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (question != null) {
                    Text(
                        question, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2, overflow = TextOverflow.Ellipsis
                    )
                }
            }
            IconButton(onClick = onCancel, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Filled.Close, contentDescription = "Close", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
            }
        }

        Row(
            Modifier.fillMaxWidth().height(38.dp).clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.background).padding(3.dp)
        ) {
            Seg("↑ Out", out == true, RedSpend, Modifier.weight(1f)) { out = true }
            Seg("↓ In", out == false, GreenIn, Modifier.weight(1f)) { out = false }
        }

        // What for: type to search; the drop list shows every kind of expense (or income), stock items and names used before.
        Field(
            item, { item = it; pickedCat = null; newStock = false; listOpen = true },
            if (out == false) "From what" else "What for",
            Modifier.fillMaxWidth().onFocusChanged { if (it.hasFocus) listOpen = true }
        ) {
            IconButton(onClick = { listOpen = !listOpen }, modifier = Modifier.size(36.dp)) {
                Icon(if (listOpen) Icons.Filled.ArrowDropUp else Icons.Filled.ArrowDropDown, contentDescription = "List")
            }
        }
        if (listOpen) WhatForList(
            typed = itemT, income = out == false, stockNames = stockNames, used = if (out == false) usedIn else usedOut,
            onPick = { name, cat, stockItem -> item = name; pickedCat = cat; newStock = stockItem; listOpen = false }
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Field(qty, { v -> qty = v.filter { it.isDigit() || it == '.' }; fillAmount() }, "Qty", Modifier.weight(1f), numeric = true)
            Field(rate, { v -> rate = v.filter { it.isDigit() || it == '.' }; fillAmount() }, "Rate", Modifier.weight(1f), numeric = true)
        }

        // Amount + date side by side
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Field(
                amount, { v -> amount = v.filter { it.isDigit() || it == '.' } }, "Amount ($currency)", Modifier.weight(1f),
                numeric = true, style = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Bold, color = accent)
            )
            Row(
                Modifier
                    .height(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.background)
                    .clickable { showDate = true }
                    .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.DateRange, contentDescription = "Date", tint = accent, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(5.dp))
                Text(shortDate(date), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
        }

        var picking by remember { mutableStateOf(false) }
        Box {
            Field(person, { person = it }, "Party (optional)", Modifier.fillMaxWidth(), readOnly = true) {
                if (person.isNotBlank()) IconButton(onClick = { person = "" }, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Filled.Close, contentDescription = "No party", modifier = Modifier.size(18.dp))
                }
                else Icon(Icons.Filled.ArrowDropDown, contentDescription = null, modifier = Modifier.padding(end = 6.dp))
            }
            // The field (not its × button) opens the party list.
            Box(Modifier.matchParentSize().padding(end = 40.dp).clip(RoundedCornerShape(12.dp)).clickable { picking = true })
        }
        if (picking) PartyPicker(
            people, "", "Party",
            confirm = { creating -> if (creating) "Add party" else "Select" },
            onPick = { person = it; picking = false }, onDismiss = { picking = false }
        )
        // With a party and an item the money can be settled now or left due in their khata.
        if (person.isNotBlank() && withItem) Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Pill("Paid", !due, GreenIn) { due = false }
            Pill("Due", due, OrangeOwe) { due = true }
        }
        Field(note, { note = it }, "Note (optional)", Modifier.fillMaxWidth())

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (onDelete != null) {
                if (askDelete) {
                    Button(
                        onClick = onDelete,
                        colors = ButtonDefaults.buttonColors(containerColor = RedSpend),
                        shape = RoundedCornerShape(12.dp)
                    ) { Text("Yes, delete") }
                } else {
                    OutlinedButton(
                        onClick = { askDelete = true },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = RedSpend),
                        shape = RoundedCornerShape(12.dp)
                    ) { Text("Delete") }
                }
            }
            Button(
                onClick = {
                    chosen?.let { t ->
                        // An expense's category: the one picked from the list, else the one already set (an edit),
                        // else guessed from the words ("nashta" -> Food).
                        val kept = initial.category.takeIf { initial.type == t && it != Cat.UDHAAR && it != Cat.OTHER }
                        val guess = Parser.guessCategory((item + " " + note).trim())
                        val cat = pickedCat ?: kept ?: guess?.first ?: Cat.OTHER
                        val finalCat = when {
                            t == Type.SALE -> Cat.SALES
                            t == Type.PURCHASE -> Cat.STOCK
                            isLoan(t) -> Cat.UDHAAR
                            isTransfer(t) -> Cat.OTHER
                            t == Type.AAMDANI && cat == Cat.ASSETS -> Cat.ASSETS
                            t == Type.AAMDANI -> Cat.TANKHWAH
                            else -> cat
                        }
                        val finalItem = when {
                            isLoan(t) -> if (loanItem) initial.item else "Loans"
                            isTransfer(t) -> ownerMove(itemT) ?: initial.item.ifBlank { "Transfer" }
                            isTrade(t) -> spelled(item, stockNames).ifBlank { if (t == Type.SALE) "Sale" else "Purchase" }
                            itemT.isNotEmpty() -> spelled(item, if (t == Type.AAMDANI) usedIn else usedOut)
                            t == Type.AAMDANI -> "Income"
                            else -> guess?.second ?: finalCat.label
                        }
                        val withParty = person.isNotBlank()
                        onSave(
                            initial.copy(
                                type = t,
                                amount = amountValue ?: 0.0,
                                category = finalCat,
                                item = finalItem,
                                qty = qty.toDoubleOrNull() ?: 0.0,
                                rate = rate.toDoubleOrNull() ?: 0.0,
                                person = if (ownerMove(itemT) != null) "" else person.trim(),
                                paid = isTrade(t) && withParty && !due,
                                credit = (t == Type.KHARCHA || t == Type.AAMDANI) && withParty && due,
                                // A new "nashta" today after 9 AM is put at 7 AM, and so on (see Parser.partOfDayTime).
                                date = if (initial.amount == 0.0 && date == initial.date)
                                    Parser.partOfDayTime(date, Parser.dayPartHour("$note $item"), System.currentTimeMillis()) else date,
                                note = note.trim().ifBlank { finalItem }
                            )
                        )
                    }
                },
                enabled = chosen != null && amountValue != null && amountValue > 0,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = accent),
                modifier = Modifier.weight(1f).height(44.dp)
            ) { Text(saveLabel, fontWeight = FontWeight.Bold) }
        }
    }

    if (showDate) {
        val state = rememberDatePickerState(initialSelectedDateMillis = toUtcMidnight(date))
        DatePickerDialog(
            onDismissRequest = { showDate = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { date = fromUtcMidnight(it) }
                    showDate = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showDate = false }) { Text("Cancel") } }
        ) { DatePicker(state = state) }
    }
}

/** A small form field: hint inside, no floating label, so the form stays short. */
@Composable
private fun Field(
    value: String, onChange: (String) -> Unit, hint: String, modifier: Modifier = Modifier,
    numeric: Boolean = false, readOnly: Boolean = false, style: TextStyle = TextStyle(fontSize = 15.sp),
    trailing: (@Composable () -> Unit)? = null
) {
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier.height(44.dp).clip(shape).background(MaterialTheme.colorScheme.background)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape).padding(start = 12.dp, end = if (trailing == null) 10.dp else 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) Text(hint, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            BasicTextField(
                value, onChange, singleLine = true, readOnly = readOnly, modifier = Modifier.fillMaxWidth(),
                textStyle = style.copy(color = if (style.color == Color.Unspecified) MaterialTheme.colorScheme.onSurface else style.color),
                keyboardOptions = if (numeric) KeyboardOptions(keyboardType = KeyboardType.Decimal) else KeyboardOptions.Default,
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary)
            )
        }
        trailing?.invoke()
    }
}

/** One half of the Cash out / Cash in switch. */
@Composable
private fun Seg(label: String, selected: Boolean, color: Color, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier.fillMaxHeight().clip(RoundedCornerShape(9.dp)).background(if (selected) color else Color.Transparent).clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, color = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun Pill(label: String, selected: Boolean, color: Color, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val shape = CircleShape
    Box(
        modifier
            .clip(shape)
            .background(if (selected) color.copy(alpha = 0.18f) else Color.Transparent)
            .border(1.dp, if (selected) color else MaterialTheme.colorScheme.outline, shape)
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label, fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) color else MaterialTheme.colorScheme.onSurface
        )
    }
}

/** [typed] in the spelling already used in [names] ("cement" → "Cement"), else as typed. */
private fun spelled(typed: String, names: List<String>): String = typed.trim().let { t -> names.firstOrNull { it.equals(t, true) } ?: t }

/** Names used before on one side, newest first, for the What for list. */
private fun usedNames(list: List<Entry>): List<String> = list.sortedByDescending { it.date }.map { it.item.trim() }
    .filter { it.isNotBlank() && it != "Loans" && it != "Income" }.distinctBy { it.lowercase() }.take(15)

/** One name in the What for list: the category it files under, and whether it is a stock item. */
private class WhatFor(val name: String, val cat: Cat?, val stock: Boolean)

/** Every common kind of expense, grouped, each with its category. */
private val OUT_GROUPS: List<Pair<String, List<Pair<String, Cat>>>> = listOf(
    "Home" to listOf(
        "Groceries" to Cat.KHANA, "Food" to Cat.KHANA, "Rent" to Cat.RENT, "Electricity" to Cat.BIJLI, "Gas" to Cat.BIJLI,
        "Water" to Cat.BIJLI, "Internet" to Cat.MOBILE, "Mobile" to Cat.MOBILE, "Household" to Cat.SHOPPING, "Maid" to Cat.OTHER
    ),
    "Travel" to listOf("Fuel" to Cat.TRANSPORT, "Transport" to Cat.TRANSPORT, "Car repair" to Cat.TRANSPORT, "Parking" to Cat.TRANSPORT, "Travel" to Cat.TRANSPORT),
    "Family" to listOf(
        "School fee" to Cat.EDU, "Health" to Cat.SEHAT, "Medicine" to Cat.SEHAT, "Clothes" to Cat.SHOPPING,
        "Personal care" to Cat.PERSONAL, "Gifts" to Cat.CHARITY, "Charity" to Cat.CHARITY, "Sent home" to Cat.GHAR
    ),
    "Business" to listOf(
        "Raw material" to Cat.PRODUCTION, "Wages" to Cat.PRODUCTION, "Salary" to Cat.PRODUCTION, "Shop rent" to Cat.RENT,
        "Shop electricity" to Cat.BIJLI, "Machine fuel" to Cat.PRODUCTION, "Repair" to Cat.PRODUCTION, "Freight" to Cat.TRANSPORT,
        "Packing" to Cat.PRODUCTION, "Tools" to Cat.ASSETS, "Office" to Cat.OTHER, "Marketing" to Cat.OTHER,
        "Commission" to Cat.OTHER, "Bank charges" to Cat.FEES, "Taxes & fees" to Cat.FEES
    ),
    "Other" to listOf("Entertainment" to Cat.OTHER, "Subscriptions" to Cat.MOBILE, "Insurance" to Cat.FEES, "Other" to Cat.OTHER),
    "Owner" to listOf("Taken for home" to Cat.OTHER, "Put in business" to Cat.OTHER)
)

/** Every common kind of income. */
private val IN_GROUPS: List<Pair<String, List<Pair<String, Cat>>>> = listOf(
    "Income" to listOf("Salary", "Sale", "Business income", "Rent received", "Commission", "Profit", "Bonus", "Gift received", "Refund", "Other")
        .map { it to Cat.TANKHWAH },
    "Owner" to listOf("From business" to Cat.OTHER, "From home" to Cat.OTHER)
)

/**
 * The What for drop list: names used before, stock items, then every common kind, all filtered by what is typed.
 * A name that is not there can be added (as an expense / income, or as a stock item); once saved it is in the list.
 */
@Composable
private fun WhatForList(typed: String, income: Boolean, stockNames: List<String>, used: List<String>, onPick: (String, Cat?, Boolean) -> Unit) {
    val seen = HashSet<String>()
    fun keep(w: WhatFor) = (typed.isEmpty() || w.name.contains(typed, true)) && seen.add(w.name.lowercase())
    val sections = buildList {
        add("Used before" to used.map { WhatFor(it, null, false) }.filter(::keep))
        add("Stock" to stockNames.map { WhatFor(it, null, true) }.filter(::keep))
        (if (income) IN_GROUPS else OUT_GROUPS).forEach { (g, l) -> add(g to l.map { (n, c) -> WhatFor(n, c, false) }.filter(::keep)) }
    }.filter { it.second.isNotEmpty() }
    val exact = sections.any { (_, l) -> l.any { it.name.equals(typed, true) } }
    val shape = RoundedCornerShape(12.dp)
    Column(
        Modifier.fillMaxWidth().heightIn(max = 280.dp).clip(shape)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape).background(MaterialTheme.colorScheme.surface)
            .verticalScroll(rememberScrollState())
    ) {
        if (typed.isNotEmpty() && !exact) {
            ListRow("+ Add “$typed”", GreenIn) { onPick(typed, null, false) }
            ListRow("+ Add “$typed” as a stock item", GreenIn) { onPick(typed, null, true) }
        }
        sections.forEach { (g, l) ->
            Text(
                g.uppercase(), fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.6.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background).padding(horizontal = 12.dp, vertical = 5.dp)
            )
            l.forEach { w -> ListRow(w.name, MaterialTheme.colorScheme.onSurface) { onPick(w.name, w.cat, w.stock) } }
        }
    }
}

@Composable
private fun ListRow(text: String, color: Color, onClick: () -> Unit) {
    Text(
        text, fontSize = 14.sp, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis,
        modifier = Modifier.fillMaxWidth().clickable { onClick() }.padding(horizontal = 12.dp, vertical = 10.dp)
    )
}
