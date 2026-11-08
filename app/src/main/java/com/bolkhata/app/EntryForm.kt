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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
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
internal fun isOut(t: Type) = t == Type.KHARCHA || t == Type.DIYA || t == Type.WAPIS_DIYA || t == Type.PURCHASE || t == Type.TRANSFER_OUT

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
 * One compact form for adding, editing and answering "what is this?". The owner only picks Cash out or Cash in
 * (none picked when [askType]); the app works out the rest: no party means an expense (its category read from the
 * note and goods) or income; a party means a loan or a repayment, from that party's balance. An edit that keeps the
 * direction and the party keeps the entry's own kind (sale, purchase, transfer…). The khata list also offers
 * Business (a stock item: sale or purchase), Income (who paid) and Expense (a category).
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
    val expenseCats = remember { Cat.entries.filter { it !in setOf(Cat.UDHAAR, Cat.TANKHWAH, Cat.SALES, Cat.STOCK) } }
    /** A non-party khata picked in the list: "stock" / "income" (name in [item]) or "expense" (in [pickCat]); null = party. */
    var khata by remember(initial.id) { mutableStateOf<String?>(null) }
    var pickCat by remember(initial.id) { mutableStateOf<Cat?>(null) }
    var amount by remember(initial.id) { mutableStateOf(if (initial.amount > 0) plain(initial.amount) else "") }
    var item by remember(initial.id) { mutableStateOf(if (initial.item == "Loans") "" else initial.item) }
    var qty by remember(initial.id) { mutableStateOf(if (initial.qty > 0) plain(initial.qty) else "") }
    var rate by remember(initial.id) { mutableStateOf(if (initial.rate > 0) plain(initial.rate) else "") }
    var unit by remember(initial.id) { mutableStateOf(initial.unit) }
    val knownGoods = remember(all) { Goods.known(all) }
    var person by remember(initial.id) { mutableStateOf(initial.person) }
    /** Qty × rate fills the amount (a rate "per 1000" counts thousands, as bricks are sold). */
    fun fillAmount() {
        val q = qty.toDoubleOrNull() ?: return
        val r = rate.toDoubleOrNull() ?: return
        amount = plain(if (unit == "1000") q / 1000 * r else q * r)
    }
    /** A goods name used before brings its last rate and unit (this person's first), unless a rate is typed. */
    fun fillRate(name: String) {
        if (rate.isNotBlank()) return
        Goods.lastRate(name, person, out, all)?.let { rate = plain(it.rate); unit = it.unit; fillAmount() }
    }
    var paid by remember(initial.id) { mutableStateOf(initial.paid) }
    var date by remember(initial.id) { mutableLongStateOf(initial.date) }
    var note by remember(initial.id) { mutableStateOf(initial.note) }
    var showDate by remember { mutableStateOf(false) }
    var askDelete by remember { mutableStateOf(false) }

    val amountValue = amount.toDoubleOrNull()
    val accent = when (out) { true -> RedSpend; false -> GreenIn; null -> MaterialTheme.colorScheme.primary }

    /** The entry's kind from Cash out / in and the party (see the class comment). */
    fun kind(o: Boolean): Type {
        when (khata) {
            "stock" -> return if (o) Type.PURCHASE else Type.SALE
            "income", "expense" -> return if (o) Type.KHARCHA else Type.AAMDANI
        }
        val p = person.trim()
        if (!askType && o == isOut(initial.type) && p.equals(initial.person.trim(), true)) return initial.type
        if (p.isEmpty()) return if (o) Type.KHARCHA else Type.AAMDANI
        val bal = personAccounts(all.filter { it.id != initial.id }).firstOrNull { it.name.equals(p, true) }?.balance ?: 0.0
        return if (o) (if (bal < -0.005) Type.WAPIS_DIYA else Type.DIYA) else (if (bal > 0.005) Type.WAPIS_MILA else Type.LIYA)
    }
    val chosen = out?.let { kind(it) }

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

        // Only the direction; the app works out expense / income / loan.
        // A light neutral track; the picked side is a white chip with its colour on the words.
        Row(
            Modifier.fillMaxWidth().height(38.dp).clip(RoundedCornerShape(12.dp))
                .background(LocalLook.current?.let { Color(it.well) } ?: Color(0xFFF1F1F2)).padding(3.dp)
        ) {
            Seg("↑ Cash out", out == true, RedSpend, Modifier.weight(1f)) { out = true }
            Seg("↓ Cash in", out == false, GreenIn, Modifier.weight(1f)) { out = false }
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
                    .fieldEdge(RoundedCornerShape(12.dp))
                    .clickable { showDate = true }
                    .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.DateRange, contentDescription = "Date", tint = accent, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(5.dp))
                Text(shortDate(date), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
        }

        Field(note, { note = it }, "Note (what for?)", Modifier.fillMaxWidth())

        // Goods: saved names show under the field as you type, so one item keeps one spelling.
        var goodsOpen by remember { mutableStateOf(false) }
        val goodsHints = remember(item, knownGoods) { Goods.suggest(item, knownGoods) }
        Box {
            Field(item, { item = it; goodsOpen = true; knownGoods.firstOrNull { k -> k.equals(it.trim(), true) }?.let(::fillRate) }, "Goods (optional)", Modifier.fillMaxWidth())
            DropdownMenu(expanded = goodsOpen && goodsHints.isNotEmpty(), onDismissRequest = { goodsOpen = false },
                properties = androidx.compose.ui.window.PopupProperties(focusable = false)) {
                goodsHints.forEach { g -> DropdownMenuItem(text = { Text(g) }, onClick = { item = g; goodsOpen = false; fillRate(g) }) }
            }
        }
        // Qty, unit and rate: the amount fills itself from qty × rate.
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Field(qty, { v -> qty = v.filter { it.isDigit() || it == '.' }; fillAmount() }, "Qty", Modifier.weight(1f), numeric = true)
            var unitOpen by remember { mutableStateOf(false) }
            Box {
                Field(if (unit == "1000") "per 1000" else unit, {}, "Unit", Modifier.width(92.dp), readOnly = true,
                    trailing = { Icon(Icons.Filled.ArrowDropDown, contentDescription = "Unit", modifier = Modifier.size(20.dp)) })
                // A cover over the field, so a tap anywhere on it opens the list.
                Box(Modifier.matchParentSize().clickable { unitOpen = true })
                DropdownMenu(expanded = unitOpen, onDismissRequest = { unitOpen = false }) {
                    listOf("" to "None", "piece" to "Piece", "kg" to "Kg", "bag" to "Bag", "ton" to "Ton", "litre" to "Litre", "1000" to "Per 1000")
                        .forEach { (u, l) -> DropdownMenuItem(text = { Text(l) }, onClick = { unit = u; unitOpen = false; fillAmount() }) }
                }
            }
            Field(rate, { v -> rate = v.filter { it.isDigit() || it == '.' }; fillAmount() }, "Rate", Modifier.weight(1f), numeric = true)
        }

        var picking by remember { mutableStateOf(false) }
        val shown = when (khata) {
            "stock" -> "Business · $item"
            "income" -> "Income · $item"
            "expense" -> "Expense · " + (pickCat?.label ?: "")
            else -> person
        }
        Box {
            Field(
                shown, { person = it }, "Account (optional) · person, business, income, expense",
                Modifier.fillMaxWidth(), readOnly = true
            ) {
                if (shown.isNotBlank()) IconButton(onClick = { person = ""; if (khata != null) item = ""; khata = null; pickCat = null }, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Filled.Close, contentDescription = "No account", modifier = Modifier.size(18.dp))
                }
                else Icon(Icons.Filled.ArrowDropDown, contentDescription = null, modifier = Modifier.padding(end = 6.dp))
            }
            // The field (not its × button) opens the khata list.
            Box(Modifier.matchParentSize().padding(end = 40.dp).clip(RoundedCornerShape(12.dp)).clickable { picking = true })
        }
        if (picking) PartyPicker(
            people, "", "Select account",
            confirm = { creating -> if (creating) "Create & select" else "Select" },
            onPick = { person = it; khata = null; pickCat = null; picking = false }, onDismiss = { picking = false },
            categories = expenseCats, onCategory = { pickCat = it; khata = "expense"; person = ""; out = true; picking = false },
            stockNames = stockNames, onStock = { item = it; khata = "stock"; person = ""; picking = false },
            incomeNames = incomeNames, onIncome = { item = it; khata = "income"; person = ""; out = false; picking = false }
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
                            t == Type.SALE -> Cat.SALES
                            t == Type.PURCHASE -> Cat.STOCK
                            isLoan(t) -> Cat.UDHAAR
                            isTransfer(t) -> Cat.OTHER
                            t == Type.AAMDANI && cat == Cat.ASSETS -> Cat.ASSETS
                            t == Type.AAMDANI -> Cat.TANKHWAH
                            else -> cat
                        }
                        val itemName = Goods.canonical(item, knownGoods)
                        val finalItem = when {
                            isLoan(t) -> itemName.ifBlank { "Loans" }
                            isTransfer(t) -> itemName.ifBlank { "Transfer" }
                            isTrade(t) -> itemName.ifBlank { if (t == Type.SALE) "Sale" else "Purchase" }
                            itemName.isNotBlank() -> itemName
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
                                rate = rate.toDoubleOrNull() ?: 0.0,
                                unit = unit,
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
        // White with a soft edge, like the search box and chat bar.
        modifier.height(44.dp).fieldEdge(shape).padding(start = 12.dp, end = if (trailing == null) 10.dp else 2.dp),
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

/** One half of the Cash out / Cash in switch: picked, a white chip with the words in its colour. */
@Composable
private fun Seg(label: String, selected: Boolean, color: Color, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(9.dp)
    Box(
        modifier.fillMaxHeight().then(if (selected) Modifier.shadow(1.dp, shape) else Modifier).clip(shape)
            .background(if (selected) MaterialTheme.colorScheme.surface else Color.Transparent).clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(label, fontSize = 13.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
            color = if (selected) color else MaterialTheme.colorScheme.onSurfaceVariant)
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
