package com.bolkhata.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
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
 * One compact form for adding, editing and answering "what is this?". Tabs Out · In · Sale · Purchase · Work (none
 * picked when [askType]), then the same order on each: item (chips of names used before), qty × rate, amount, party,
 * note. On Out / In the app works out the rest: no party means an expense (its category read from the item and note)
 * or income; a party means a loan or a repayment, from that party's balance. An edit that keeps the direction and the
 * party keeps the entry's own kind (transfer…). The khata list on Out / In also offers Income and Expense khatas.
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
    // The other khatas the entry can go to: stock items (sale / purchase), who paid income, expense categories.
    val stockNames = remember(all) { stock(all).map { it.name.trim() }.filter { it.isNotBlank() }.distinctBy { it.lowercase() } }
    val incomeNames = remember(all) { all.filter { it.type == Type.AAMDANI }.map { source(it).trim() }.filter { it.isNotBlank() }.distinctBy { it.lowercase() } }
    /** What money went on before, newest first, offered as chips on the Out tab. */
    val expenseNames = remember(all) {
        all.filter { it.type == Type.KHARCHA && !isWork(it) }.sortedByDescending { it.date }.map { it.item.trim() }
            .filter { it.isNotBlank() }.distinctBy { it.lowercase() }.take(12)
    }
    val expenseCats = remember { Cat.entries.filter { it !in setOf(Cat.UDHAAR, Cat.TANKHWAH, Cat.SALES, Cat.STOCK) } }
    /** A non-party khata picked in the list: "income" (name in [item]) or "expense" (in [pickCat]); null = party. */
    // A production cost (Add cost) opens with its khata shown: Expense · Production.
    val prodCost = !askType && initial.type == Type.KHARCHA && initial.category == Cat.PRODUCTION && !isWork(initial)
    var khata by remember(initial.id) { mutableStateOf(if (prodCost) "expense" else null) }
    var pickCat by remember(initial.id) { mutableStateOf(if (prodCost) Cat.PRODUCTION else null) }
    var amount by remember(initial.id) { mutableStateOf(if (initial.amount > 0) plain(initial.amount) else "") }
    var item by remember(initial.id) { mutableStateOf(if (initial.item == "Loans") "" else initial.item) }
    var qty by remember(initial.id) { mutableStateOf(if (initial.qty > 0) plain(initial.qty) else "") }
    var person by remember(initial.id) { mutableStateOf(initial.person) }
    var paid by remember(initial.id) { mutableStateOf(initial.paid) }
    /** Work mode: what a worker earned (qty × rate) or a salary, owed to the party; no cash moves until paid. */
    var work by remember(initial.id) { mutableStateOf(isWork(initial)) }
    var salary by remember(initial.id) { mutableStateOf(isWork(initial) && initial.qty <= 0) }
    /** The Sale or Purchase tab; null on Out, In and Work. */
    var trade by remember(initial.id) { mutableStateOf(if (askType) null else initial.type.takeIf { it == Type.SALE || it == Type.PURCHASE }) }
    var rate by remember(initial.id) { mutableStateOf(if (initial.rate > 0) plain(initial.rate) else "") }
    var date by remember(initial.id) { mutableLongStateOf(initial.date) }
    var note by remember(initial.id) { mutableStateOf(initial.note) }
    var showDate by remember { mutableStateOf(false) }
    var askDelete by remember { mutableStateOf(false) }

    val amountValue = amount.toDoubleOrNull()
    val accent = when {
        work -> OrangeOwe
        trade == Type.SALE -> GreenIn
        trade == Type.PURCHASE -> RedSpend
        else -> when (out) { true -> RedSpend; false -> GreenIn; null -> MaterialTheme.colorScheme.primary }
    }

    /** The entry's kind from the tab and the party (see the class comment). */
    fun kind(o: Boolean): Type {
        if (work) return Type.KHARCHA
        trade?.let { return it }
        if (khata == "income" || khata == "expense") return if (o) Type.KHARCHA else Type.AAMDANI
        val p = person.trim()
        if (!askType && o == isOut(initial.type) && p.equals(initial.person.trim(), true)) return initial.type
        if (p.isEmpty()) return if (o) Type.KHARCHA else Type.AAMDANI
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
    /** Work types used before, newest first, one spelling each (Moulding and moulding are the same). */
    val workTypes = remember(all) {
        all.filter { isWork(it) && it.qty > 0 }.sortedByDescending { it.date }.map { it.item.trim() }
            .filter { it.isNotBlank() && it != "Work" }.distinctBy { it.lowercase() }
    }
    /** The rate last used for this party's work, so the next entry fills it in. */
    fun lastRate(p: String) = all.filter { isWork(it) && it.rate > 0 && it.person.trim().equals(p.trim(), true) }
        .maxByOrNull { it.date }?.rate
    /** Sale or Purchase tab: stock items in the chips, the party is the customer or supplier. */
    fun toTrade(t: Type) {
        if (khata != null) item = ""
        khata = null; pickCat = null; work = false; trade = t; out = t == Type.PURCHASE
    }
    // The chips under the item field: what was used before on this tab, so a name is never spelt two ways.
    val chipNames = when {
        work -> workTypes
        trade != null -> stockNames
        out == false -> incomeNames
        else -> expenseNames
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

        // Where the entry goes; on Out / In the app works out expense / income / loan from the khata.
        Row(
            Modifier.fillMaxWidth().height(38.dp).clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.background).padding(3.dp)
        ) {
            Seg("Out", out == true && !work && trade == null, RedSpend, Modifier.weight(1f)) { out = true; work = false; trade = null }
            Seg("In", out == false && trade == null, GreenIn, Modifier.weight(1f)) { out = false; work = false; trade = null }
            Seg("Sale", trade == Type.SALE, GreenIn, Modifier.weight(1f)) { toTrade(Type.SALE) }
            Seg("Purchase", trade == Type.PURCHASE, RedSpend, Modifier.weight(1.25f)) { toTrade(Type.PURCHASE) }
            Seg("Work", work, OrangeOwe, Modifier.weight(1f)) {
                if (khata != null) item = ""
                khata = null; pickCat = null; out = true; work = true; trade = null
                if (rate.isBlank() && person.isNotBlank()) lastRate(person)?.let { rate = plain(it); fillAmount() }
            }
        }

        // Work = quantity × rate; Salary = a fixed amount. Both go in the party's khata.
        if (work) Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Pill("Work", !salary, OrangeOwe) { salary = false }
            Pill("Salary", salary, OrangeOwe) { salary = true }
        }
        if (!(work && salary)) {
            Field(
                item, { item = it },
                when {
                    work -> "Work type"
                    trade == Type.SALE -> "Item sold"
                    trade == Type.PURCHASE -> "Item bought"
                    out == false -> "From what (optional)"
                    else -> "What for (optional)"
                },
                Modifier.fillMaxWidth()
            )
            val typed = item.trim()
            val offered = chipNames.filter { !it.equals(typed, true) && (typed.isEmpty() || it.contains(typed, true)) }
            if (offered.isNotEmpty()) Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                offered.forEach { w -> Pill(w, false, accent) { item = w } }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Field(qty, { v -> qty = v.filter { it.isDigit() || it == '.' }; fillAmount() }, "Qty", Modifier.weight(1f), numeric = true)
                Field(rate, { v -> rate = v.filter { it.isDigit() || it == '.' }; fillAmount() }, "Rate", Modifier.weight(1f), numeric = true)
            }
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
        val shown = when (khata) {
            "income" -> "Income · $item"
            "expense" -> "Expense · " + (pickCat?.label ?: "")
            else -> person
        }
        Box {
            Field(
                shown, { person = it },
                when {
                    work -> "Party · who did the work"
                    trade == Type.SALE -> "Customer (optional)"
                    trade == Type.PURCHASE -> "Supplier (optional)"
                    else -> "Khata (optional) · party, income, expense"
                },
                Modifier.fillMaxWidth(), readOnly = true
            ) {
                if (shown.isNotBlank()) IconButton(onClick = { person = ""; if (khata != null) item = ""; khata = null; pickCat = null }, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Filled.Close, contentDescription = "No khata", modifier = Modifier.size(18.dp))
                }
                else Icon(Icons.Filled.ArrowDropDown, contentDescription = null, modifier = Modifier.padding(end = 6.dp))
            }
            // The field (not its × button) opens the khata list.
            Box(Modifier.matchParentSize().padding(end = 40.dp).clip(RoundedCornerShape(12.dp)).clickable { picking = true })
        }
        // Only Out / In can go to an expense category, an income or a stock item; the other tabs pick a party.
        val anyKhata = !work && trade == null
        if (picking) PartyPicker(
            people, "",
            when {
                work -> "Who did the work"
                trade == Type.SALE -> "Customer"
                trade == Type.PURCHASE -> "Supplier"
                out == false -> "Party or income"
                else -> "Party or expense"
            },
            confirm = { creating -> if (creating) "Create & select" else "Select" },
            onPick = {
                person = it; khata = null; pickCat = null; picking = false
                if (work) lastRate(it)?.let { r -> rate = plain(r); fillAmount() }
            }, onDismiss = { picking = false },
            categories = if (anyKhata) expenseCats else emptyList(),
            onCategory = { pickCat = it; khata = "expense"; person = ""; out = true; picking = false },
            stockNames = if (anyKhata) stockNames else emptyList(),
            onStock = { toTrade(if (out == false) Type.SALE else Type.PURCHASE); item = it; person = ""; picking = false },
            incomeNames = if (anyKhata) incomeNames else emptyList(),
            onIncome = { item = it; khata = "income"; person = ""; out = false; picking = false }
        )
        if (chosen != null && isTrade(chosen) && person.isNotBlank()) {
            // On credit: the amount goes in To pay / To receive. Paid: settled in cash, khata unchanged.
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Pill("On credit", !paid, OrangeOwe) { paid = false }
                Pill("Paid now", paid, GreenIn) { paid = true }
            }
        }
        Field(note, { note = it }, "Note (optional)", Modifier.fillMaxWidth())
        if (work) {
            val sum = amountValue ?: 0.0
            val who = person.trim().ifBlank { "the party" }
            val q = qty.toDoubleOrNull() ?: 0.0
            val r = rate.toDoubleOrNull() ?: 0.0
            Text(
                (if (!salary && q > 0 && r > 0) "${fmt(q)} × ${plain(r)} = " else "") +
                    "${fmt(sum)} owed to $who. It counts in Production cost; paying them is a Cash out to $who.",
                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

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
                        // An expense's category comes from what the note and goods say ("nashta" -> Food), but a category
                        // already set (Transport) stays when the entry is only renamed: the guess only fills Other.
                        val kept = initial.category.takeIf { initial.type == Type.KHARCHA && it != Cat.UDHAAR && it != Cat.OTHER }
                        val guess = Parser.guessCategory((item + " " + note).trim())
                        val cat = pickCat ?: kept ?: guess?.first ?: Cat.OTHER
                        val finalCat = when {
                            work -> Cat.PRODUCTION
                            t == Type.SALE -> Cat.SALES
                            t == Type.PURCHASE -> Cat.STOCK
                            isLoan(t) -> Cat.UDHAAR
                            isTransfer(t) -> Cat.OTHER
                            t == Type.AAMDANI && cat == Cat.ASSETS -> Cat.ASSETS
                            t == Type.AAMDANI -> Cat.TANKHWAH
                            else -> cat
                        }
                        val finalItem = when {
                            work && salary -> "Salary"
                            work -> spelled(item, workTypes).ifBlank { "Work" }
                            isLoan(t) -> item.trim().ifBlank { "Loans" }
                            isTransfer(t) -> item.trim().ifBlank { "Transfer" }
                            isTrade(t) -> spelled(item, stockNames).ifBlank { if (t == Type.SALE) "Sale" else "Purchase" }
                            item.isNotBlank() -> spelled(item, chipNames)
                            t == Type.AAMDANI -> "Income"
                            else -> guess?.second ?: finalCat.label
                        }
                        onSave(
                            initial.copy(
                                type = t,
                                amount = amountValue ?: 0.0,
                                category = finalCat,
                                item = finalItem,
                                qty = if (work && salary) 0.0 else qty.toDoubleOrNull() ?: 0.0,
                                rate = if (work && salary) 0.0 else rate.toDoubleOrNull() ?: 0.0,
                                person = if (work || isLoan(t) || isTrade(t) || isTransfer(t)) person.trim() else "",
                                paid = isTrade(t) && person.isNotBlank() && paid,
                                credit = work,
                                // A new "nashta" today after 9 AM is put at 7 AM, and so on (see Parser.partOfDayTime).
                                date = if (initial.amount == 0.0 && date == initial.date)
                                    Parser.partOfDayTime(date, Parser.dayPartHour("$note $item"), System.currentTimeMillis()) else date,
                                note = note.trim().ifBlank { if (work && finalItem != "Work" && !salary) "Work · $finalItem" else finalItem }
                            )
                        )
                    }
                },
                enabled = chosen != null && amountValue != null && amountValue > 0 &&
                    (!isLoan(chosen) || person.isNotBlank()) && (!work || (person.isNotBlank() && khata == null)),
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
