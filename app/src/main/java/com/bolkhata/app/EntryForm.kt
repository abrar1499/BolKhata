package com.bolkhata.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

fun blankEntry(): Entry {
    val now = System.currentTimeMillis()
    return Entry(
        id = "e" + System.nanoTime(), type = Type.KHARCHA, amount = 0.0, category = Cat.KHANA,
        item = "", person = "", note = "", date = now, createdAt = now
    )
}

/**
 * One form for adding, editing and answering "what is this?".
 * When [askType] is true no type is pre-selected, so the user has to choose.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
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

    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        header()
        Text(title, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        if (question != null) {
            Text(question, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        SmallLabel("What is it?")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Type.entries.forEach { t ->
                FilterChip(
                    selected = chosen == t,
                    onClick = { type = t },
                    label = { Text(typeLabel(t, person)) }
                )
            }
        }

        OutlinedTextField(
            value = amount,
            onValueChange = { v -> amount = v.filter { it.isDigit() || it == '.' } },
            label = { Text("Amount ($currency)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        if (chosen != null && isLoan(chosen)) {
            OutlinedTextField(
                value = person, onValueChange = { person = it },
                label = { Text("Person") }, singleLine = true, modifier = Modifier.fillMaxWidth()
            )
        }

        if (chosen == Type.KHARCHA) {
            SmallLabel("Category")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Cat.entries.filter { it != Cat.UDHAAR && it != Cat.TANKHWAH }.forEach { c ->
                    FilterChip(selected = cat == c, onClick = { cat = c }, label = { Text("${c.icon} ${c.label}") })
                }
            }
        }
        if (chosen == Type.KHARCHA || chosen == Type.AAMDANI) {
            OutlinedTextField(
                value = item, onValueChange = { item = it },
                label = { Text(if (chosen == Type.AAMDANI) "Item (e.g. Salary, Bonus)" else "Item (e.g. Petrol, Milk)") },
                singleLine = true, modifier = Modifier.fillMaxWidth()
            )
        }

        OutlinedButton(onClick = { showDate = true }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Filled.DateRange, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(fullDate(date))
        }

        OutlinedTextField(
            value = note, onValueChange = { note = it },
            label = { Text("Note") }, modifier = Modifier.fillMaxWidth()
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
                modifier = Modifier.weight(1f)
            ) { Text(saveLabel) }

            if (onDelete != null) {
                if (askDelete) {
                    Button(onClick = onDelete, colors = ButtonDefaults.buttonColors(containerColor = RedSpend)) { Text("Yes, delete") }
                } else {
                    OutlinedButton(onClick = { askDelete = true }) { Text("Delete") }
                }
            } else {
                OutlinedButton(onClick = onCancel) { Text("Skip") }
            }
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
