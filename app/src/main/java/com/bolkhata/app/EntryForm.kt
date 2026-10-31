package com.bolkhata.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
                .fillMaxWidth(0.92f)
                .heightIn(max = maxH),
            shape = RoundedCornerShape(26.dp),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 12.dp
        ) { content() }
    }
}

/**
 * One compact form for adding, editing and answering "what is this?". The owner only picks Cash out or Cash in
 * (none picked when [askType]); the app works out the rest: no party means an expense (its category read from the
 * note and goods) or income; a party means a loan or a repayment, from that party's balance. An edit that keeps the
 * direction and the party keeps the entry's own kind (sale, purchase, transfer…).
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
    var amount by remember(initial.id) { mutableStateOf(if (initial.amount > 0) plain(initial.amount) else "") }
    var item by remember(initial.id) { mutableStateOf(if (initial.item == "Loans") "" else initial.item) }
    var qty by remember(initial.id) { mutableStateOf(if (initial.qty > 0) plain(initial.qty) else "") }
    var person by remember(initial.id) { mutableStateOf(initial.person) }
    var paid by remember(initial.id) { mutableStateOf(initial.paid) }
    var date by remember(initial.id) { mutableLongStateOf(initial.date) }
    var note by remember(initial.id) { mutableStateOf(initial.note) }
    var showDate by remember { mutableStateOf(false) }
    var askDelete by remember { mutableStateOf(false) }

    val amountValue = amount.toDoubleOrNull()
    val accent = when (out) { true -> RedSpend; false -> GreenIn; null -> MaterialTheme.colorScheme.primary }

    /** The entry's kind from Cash out / in and the party (see the class comment). */
    fun kind(o: Boolean): Type {
        val p = person.trim()
        if (!askType && o == isOut(initial.type) && p.equals(initial.person.trim(), true)) return initial.type
        if (p.isEmpty()) return if (o) Type.KHARCHA else Type.AAMDANI
        val bal = personAccounts(all.filter { it.id != initial.id }).firstOrNull { it.name.equals(p, true) }?.balance ?: 0.0
        return if (o) (if (bal < -0.005) Type.WAPIS_DIYA else Type.DIYA) else (if (bal > 0.005) Type.WAPIS_MILA else Type.LIYA)
    }
    val chosen = out?.let { kind(it) }
    val small = TextStyle(fontSize = 15.sp)

    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        header()

        // Title row
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                if (question != null) {
                    Text(
                        question, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2, overflow = TextOverflow.Ellipsis
                    )
                }
            }
            IconButton(onClick = onCancel, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Filled.Close, contentDescription = "Close", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        // Only the direction; the app works out expense / income / loan.
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Pill("↑ Cash out", out == true, RedSpend, Modifier.weight(1f)) { out = true }
            Pill("↓ Cash in", out == false, GreenIn, Modifier.weight(1f)) { out = false }
        }

        // Amount + date side by side
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = amount,
                onValueChange = { v -> amount = v.filter { it.isDigit() || it == '.' } },
                label = { Text("Amount ($currency)", fontSize = 12.sp) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                textStyle = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold, color = accent),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.weight(1f)
            )
            Row(
                Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable { showDate = true }
                    .padding(horizontal = 12.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.DateRange, contentDescription = "Date", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(shortDate(date), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            }
        }

        OutlinedTextField(
            value = note, onValueChange = { note = it },
            label = { Text("Note", fontSize = 12.sp) }, singleLine = true, textStyle = small,
            shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = item, onValueChange = { item = it },
                label = { Text("Goods (optional)", fontSize = 12.sp) },
                singleLine = true, textStyle = small,
                shape = RoundedCornerShape(14.dp), modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                value = qty, onValueChange = { v -> qty = v.filter { it.isDigit() || it == '.' } },
                label = { Text("Qty", fontSize = 12.sp) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true, textStyle = small,
                shape = RoundedCornerShape(14.dp), modifier = Modifier.width(90.dp)
            )
        }

        var picking by remember { mutableStateOf(false) }
        Box {
            OutlinedTextField(
                value = person, onValueChange = { person = it }, readOnly = people.isNotEmpty(),
                label = { Text("Person (optional)", fontSize = 12.sp) }, singleLine = true, textStyle = small,
                placeholder = { if (people.isNotEmpty()) Text("Select party", fontSize = 14.sp) },
                trailingIcon = {
                    if (person.isNotBlank()) IconButton(onClick = { person = "" }) { Icon(Icons.Filled.Close, contentDescription = "No party") }
                    else if (people.isNotEmpty()) Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
                },
                shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()
            )
            // The field (not its × button) opens the party list.
            if (people.isNotEmpty()) Box(Modifier.matchParentSize().padding(top = 8.dp, end = 48.dp).clip(RoundedCornerShape(14.dp)).clickable { picking = true })
        }
        if (picking) PartyPicker(
            people, "", "Select party",
            confirm = { creating -> if (creating) "Create & select" else "Select" },
            onPick = { person = it; picking = false }, onDismiss = { picking = false }
        )
        if (chosen != null && isTrade(chosen) && person.isNotBlank()) {
            // On credit: the amount goes in To pay / To receive. Paid: settled in cash, khata unchanged.
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Pill("On credit", !paid, OrangeOwe) { paid = false }
                Pill("Paid now", paid, GreenIn) { paid = true }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (onDelete != null) {
                if (askDelete) {
                    Button(
                        onClick = onDelete,
                        colors = ButtonDefaults.buttonColors(containerColor = RedSpend),
                        shape = RoundedCornerShape(14.dp)
                    ) { Text("Yes, delete") }
                } else {
                    OutlinedButton(
                        onClick = { askDelete = true },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = RedSpend),
                        shape = RoundedCornerShape(14.dp)
                    ) { Text("Delete") }
                }
            }
            Button(
                onClick = {
                    chosen?.let { t ->
                        // An expense's category comes from what the note and goods say ("nashta" -> Food).
                        val guess = Parser.guessCategory((item + " " + note).trim())
                        val cat = guess?.first
                            ?: initial.category.takeIf { initial.type == Type.KHARCHA && it != Cat.UDHAAR } ?: Cat.OTHER
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
                            isLoan(t) -> item.trim().ifBlank { "Loans" }
                            isTransfer(t) -> item.trim().ifBlank { "Transfer" }
                            isTrade(t) -> item.trim().ifBlank { if (t == Type.SALE) "Sale" else "Purchase" }
                            item.isNotBlank() -> item.trim()
                            t == Type.AAMDANI -> "Income"
                            else -> guess?.second ?: finalCat.label
                        }
                        onSave(
                            initial.copy(
                                type = t,
                                amount = amountValue ?: 0.0,
                                category = finalCat,
                                item = finalItem,
                                qty = qty.toDoubleOrNull() ?: 0.0,
                                person = if (isLoan(t) || isTrade(t) || isTransfer(t)) person.trim() else "",
                                paid = isTrade(t) && person.isNotBlank() && paid,
                                // A new "nashta" today after 9 AM is put at 7 AM, and so on (see Parser.partOfDayTime).
                                date = if (initial.amount == 0.0 && date == initial.date)
                                    Parser.partOfDayTime(date, Parser.dayPartHour("$note $item"), System.currentTimeMillis()) else date,
                                note = note.trim().ifBlank { finalItem }
                            )
                        )
                    }
                },
                enabled = chosen != null && amountValue != null && amountValue > 0 &&
                    (!isLoan(chosen) || person.isNotBlank()),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.weight(1f).height(48.dp)
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
