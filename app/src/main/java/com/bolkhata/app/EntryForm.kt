package com.bolkhata.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
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

private fun shortType(t: Type) = when (t) {
    Type.KHARCHA -> "Expense"
    Type.AAMDANI -> "Income"
    Type.DIYA -> "Gave (to receive)"
    Type.LIYA -> "Took (to pay)"
    Type.WAPIS_MILA -> "Got back"
    Type.WAPIS_DIYA -> "Paid back"
}

private fun typeColor(t: Type) = when (t) {
    Type.KHARCHA -> RedSpend
    Type.AAMDANI, Type.WAPIS_MILA -> GreenIn
    else -> OrangeOwe
}

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
 * One compact form for adding, editing and answering "what is this?".
 * When [askType] is true no type is pre-selected, so the user has to choose.
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
    header: @Composable () -> Unit = {}
) {
    var type by remember(initial.id) { mutableStateOf<Type?>(if (askType) null else initial.type) }
    var cat by remember(initial.id) { mutableStateOf(if (initial.category == Cat.UDHAAR || initial.category == Cat.TANKHWAH) Cat.OTHER else initial.category) }
    var amount by remember(initial.id) { mutableStateOf(if (initial.amount > 0) plain(initial.amount) else "") }
    var item by remember(initial.id) { mutableStateOf(initial.item) }
    var person by remember(initial.id) { mutableStateOf(initial.person) }
    var date by remember(initial.id) { mutableLongStateOf(initial.date) }
    var note by remember(initial.id) { mutableStateOf(initial.note) }
    var showDate by remember { mutableStateOf(false) }
    var askDelete by remember { mutableStateOf(false) }

    val chosen = type
    val amountValue = amount.toDoubleOrNull()
    val accent = chosen?.let { typeColor(it) } ?: MaterialTheme.colorScheme.primary
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

        // Type: one scrollable row of coloured pills
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Type.entries.forEach { t ->
                Pill(shortType(t), chosen == t, typeColor(t)) { type = t }
            }
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

        if (chosen != null && isLoan(chosen)) {
            OutlinedTextField(
                value = person, onValueChange = { person = it },
                label = { Text("Person", fontSize = 12.sp) }, singleLine = true, textStyle = small,
                shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()
            )
        }

        if (chosen == Type.KHARCHA) {
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Cat.entries.filter { it != Cat.UDHAAR && it != Cat.TANKHWAH }.forEach { c ->
                    Pill("${c.icon} ${c.label}", cat == c, catColor(c)) { cat = c }
                }
            }
        }

        if (chosen == Type.KHARCHA || chosen == Type.AAMDANI) {
            OutlinedTextField(
                value = item, onValueChange = { item = it },
                label = { Text(if (chosen == Type.AAMDANI) "Item (Salary, Bonus…)" else "Item (Petrol, Milk…)", fontSize = 12.sp) },
                singleLine = true, textStyle = small,
                shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()
            )
        }

        OutlinedTextField(
            value = note, onValueChange = { note = it },
            label = { Text("Note", fontSize = 12.sp) }, singleLine = true, textStyle = small,
            shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()
        )

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
                        val finalCat = when {
                            isLoan(t) -> Cat.UDHAAR
                            t == Type.AAMDANI -> Cat.TANKHWAH
                            else -> cat
                        }
                        val finalItem = when {
                            isLoan(t) -> "Loans"
                            item.isNotBlank() -> item.trim()
                            t == Type.AAMDANI -> "Income"
                            else -> finalCat.label
                        }
                        onSave(
                            initial.copy(
                                type = t,
                                amount = amountValue ?: 0.0,
                                category = finalCat,
                                item = finalItem,
                                person = if (isLoan(t)) person.trim() else "",
                                date = date,
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
private fun Pill(label: String, selected: Boolean, color: Color, onClick: () -> Unit) {
    val shape = CircleShape
    Box(
        Modifier
            .clip(shape)
            .background(if (selected) color.copy(alpha = 0.18f) else Color.Transparent)
            .border(1.dp, if (selected) color else MaterialTheme.colorScheme.outline, shape)
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 7.dp)
    ) {
        Text(
            label, fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) color else MaterialTheme.colorScheme.onSurface
        )
    }
}
