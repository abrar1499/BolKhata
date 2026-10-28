package com.bolkhata.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** "Petrol – 70 – Today 7:00 AM": how an entry was understood, in plain English. */
fun understoodLine(e: Entry, currency: String): String {
    val c = Calendar.getInstance().apply { timeInMillis = e.date }
    val now = Calendar.getInstance()
    fun sameDay(a: Calendar, b: Calendar) = a.get(Calendar.YEAR) == b.get(Calendar.YEAR) && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)
    val yesterday = (now.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -1) }
    val day = when {
        sameDay(c, now) -> "Today"
        sameDay(c, yesterday) -> "Yesterday"
        else -> SimpleDateFormat("d MMM", Locale.US).format(Date(e.date))
    }
    val time = SimpleDateFormat("h:mm a", Locale.US).format(Date(e.date))
    val what = when (e.type) {
        Type.DIYA -> "Gave ${e.person}"
        Type.LIYA -> "Money in from ${e.person}"
        Type.WAPIS_MILA -> "${e.person} paid back"
        Type.WAPIS_DIYA -> "Paid back ${e.person}"
        Type.AAMDANI -> "Income · " + e.item
        else -> entryTitle(e)
    }
    return "$what – ${fmt(e.amount)} $currency – $day $time"
}

/** Shown before saving when the app wasn't sure what was meant (the AI read it). */
@Composable
fun ConfirmCard(list: List<Entry>, currency: String, onSave: () -> Unit, onEdit: () -> Unit, onCancel: () -> Unit) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(if (list.size == 1) "Is this right?" else "Are these right?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                list.forEach { e ->
                    Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                        Column(Modifier.fillMaxWidth().padding(12.dp)) {
                            Text(understoodLine(e, currency), fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            Text(e.category.label + (if (e.note.isNotBlank()) " · " + e.note else ""), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        },
        confirmButton = { Button(onClick = onSave) { Text("Save") } },
        dismissButton = {
            Row {
                TextButton(onClick = onCancel) { Text("Cancel") }
                TextButton(onClick = onEdit) { Text("Edit") }
            }
        }
    )
}

/** Two or more saved people match the name that was said: which one? */
@Composable
fun WhoDialog(said: String, choices: List<String>, onPick: (String?) -> Unit, onCancel: () -> Unit) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Who is \"$said\"?") },
        text = {
            Column {
                choices.forEach { n -> TextButton(onClick = { onPick(n) }, modifier = Modifier.fillMaxWidth()) { Text(n, fontSize = 16.sp) } }
                TextButton(onClick = { onPick(null) }, modifier = Modifier.fillMaxWidth()) { Text("Someone new: \"$said\"") }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onCancel) { Text("Cancel") } }
    )
}

/** "Who were the other 2?" after a shared bill said without names: type or speak the names ("ali aur bilal"). */
@Composable
fun SplitAskDialog(bill: String, missing: Int, onAnswer: (String) -> Unit, onMic: () -> Unit, onCancel: () -> Unit) {
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(if (missing > 0) "Who were the other $missing?" else "Who was with you?") },
        text = {
            Column {
                Text("$bill, split equally", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = text, onValueChange = { text = it }, singleLine = true,
                    placeholder = { Text("e.g. Ali and Bilal") },
                    trailingIcon = {
                        IconButton(onClick = onMic) {
                            Icon(Icons.Filled.Mic, contentDescription = "Speak the names")
                        }
                    },
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Done),
                    keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = { if (text.isNotBlank()) onAnswer(text) }),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = { TextButton(onClick = { if (text.isNotBlank()) onAnswer(text) }) { Text("Add", fontWeight = FontWeight.Bold) } },
        dismissButton = { TextButton(onClick = onCancel) { Text("Cancel") } }
    )
}

/** The list for "Other wali entries theek karo": each shop with the app's category; tap a category to change it. */
@Composable
fun RecatDialog(groups: List<Recat.Group>, currency: String, onApply: (Map<String, Cat>) -> Unit, onCancel: () -> Unit) {
    val picked = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateMapOf<String, Cat>().apply { groups.forEach { put(it.name, it.guess) } } }
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Sort \"Other\" expenses", fontWeight = FontWeight.Bold) },
        text = {
            if (groups.isEmpty()) Text("No expenses in Other (transfers are left as they are).")
            else androidx.compose.foundation.lazy.LazyColumn(Modifier.heightIn(max = 420.dp)) {
                items(groups.size) { i ->
                    val g = groups[i]
                    var open by androidx.compose.runtime.remember { mutableStateOf(false) }
                    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(g.name, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                            Text("${g.ids.size}× · ${money(currency, g.total)}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Box {
                            val c = picked[g.name] ?: Cat.OTHER
                            AssistChip(onClick = { open = true }, label = { Text(c.icon + " " + c.label, fontSize = 12.sp, maxLines = 1) })
                            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                                Recat.expenseCats.forEach { cat ->
                                    DropdownMenuItem(text = { Text(cat.icon + " " + cat.label) }, onClick = { picked[g.name] = cat; open = false })
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            val changes = groups.filter { (picked[it.name] ?: Cat.OTHER) != Cat.OTHER }
            TextButton(enabled = changes.isNotEmpty(), onClick = {
                onApply(changes.flatMap { g -> g.ids.map { it to (picked[g.name] ?: Cat.OTHER) } }.toMap())
            }) { Text("Apply (${changes.sumOf { it.ids.size }})", fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onClick = onCancel) { Text("Cancel") } }
    )
}
