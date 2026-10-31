package com.bolkhata.app

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Which account an entry belongs to: a main account (People, Food, Transport…) and the account inside it. */
data class AccountKey(val main: String, val sub: String)

const val PEOPLE = "People"

/** The person's name on an entry: its person, or the name in a transfer note ("To Shahbaz", "From Imran"). */
fun personOf(e: Entry): String {
    if (e.person.isNotBlank()) return e.person.trim()
    val n = e.note.trim()
    return if (n.startsWith("To ") || n.startsWith("From ")) n.substringAfter(' ').trim() else ""
}

fun accountOf(e: Entry): AccountKey {
    val name = personOf(e)
    if (name.isNotBlank()) return AccountKey(PEOPLE, name)
    val main = when (e.type) {
        Type.SALE -> "Sales"
        Type.PURCHASE -> "Purchases"
        Type.AAMDANI -> "Income"
        else -> e.category.label
    }
    return AccountKey(main, e.item.trim().ifBlank { e.category.label })
}

/** Set by the main screen: tapping an entry opens its account. Null inside the account screen (tap edits there). */
val LocalAccountOpener = staticCompositionLocalOf<((Entry) -> Unit)?> { null }

/** A tap does nothing, so entries are never opened by accident; long-press shows Edit / Move / Delete. */
@OptIn(ExperimentalFoundationApi::class)
fun Modifier.entryTap(e: Entry, onEdit: () -> Unit): Modifier = composed {
    val menu = LocalEntryMenu.current
    combinedClickable(onClick = {}, onLongClick = { if (menu != null) menu(e) else onEdit() })
}

private fun moneyIn(e: Entry) = when (e.type) {
    Type.AAMDANI, Type.WAPIS_MILA, Type.LIYA, Type.TRANSFER_IN -> true
    Type.SALE -> e.person.isBlank() || e.paid
    else -> false
}

private class Sub(val name: String, val list: List<Entry>, val inn: Double, val out: Double) {
    val balance get() = inn - out
}

@Composable
fun AccountScreen(
    key: AccountKey,
    entries: List<Entry>,
    currency: String,
    onBack: () -> Unit,
    onEdit: (Entry) -> Unit,
    onNew: (Entry) -> Unit
) {
    val people = key.main == PEOPLE
    val data = remember(entries, key.main) {
        entries.filter { accountOf(it).main == key.main }
            // Spellings of one name or shop ("Shahbaz" / "shahbaz") are one account.
            .groupBy { accountOf(it).sub.lowercase() }
            .map { (_, list) ->
                Sub(
                    name = list.groupingBy { accountOf(it).sub }.eachCount().maxByOrNull { it.value }?.key ?: "",
                    list = list.sortedWith(compareByDescending<Entry> { it.date }.thenByDescending { it.createdAt }),
                    inn = list.filter { moneyIn(it) }.sumOf { it.amount },
                    out = list.filter { !moneyIn(it) }.sumOf { it.amount }
                )
            }
    }
    // The order is fixed when the account opens (the tapped one first), so opening another never moves the list.
    val order = remember(key) {
        data.sortedWith(
            compareByDescending<Sub> { it.name.equals(key.sub, true) }
                .thenBy(String.CASE_INSENSITIVE_ORDER) { it.name.trim() } // then A–Z
        ).map { it.name.lowercase() }
    }
    val subs = order.mapNotNull { n -> data.firstOrNull { it.name.lowercase() == n } } +
        data.filter { it.name.lowercase() !in order }
    var open by remember(key) { mutableStateOf(setOf(key.sub.lowercase())) }
    val totalIn = data.sumOf { it.inn }
    val totalOut = data.sumOf { it.out }
    val df = SimpleDateFormat("d MMM yyyy", Locale.US)

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = barSpace())) {
        item {
            Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 16.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                Column(Modifier.weight(1f)) {
                    Text(key.main, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)
                    Text(
                        (if (subs.size == 1) "1 account" else "${subs.size} accounts") + " · " +
                            (data.sumOf { it.list.size }.let { if (it == 1) "1 entry" else "$it entries" }),
                        fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        item {
            Panel(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), padding = 12.dp) {
                Totals(totalIn, totalOut, currency, people, big = true)
            }
        }
        subs.forEach { s ->
            val isOpen = s.name.lowercase() in open
            item(key = "h-" + s.name.lowercase()) {
                val shape = if (isOpen) RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp) else RoundedCornerShape(20.dp)
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = 8.dp)
                        .clip(shape).background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable { open = if (isOpen) open - s.name.lowercase() else open + s.name.lowercase() }
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(s.name, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            if (s.list.size == 1) "1 entry" else "${s.list.size} entries",
                            fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    BalanceChip(s.balance, currency, people)
                    Icon(
                        if (isOpen) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                        contentDescription = if (isOpen) "Close" else "Open",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (isOpen) {
                item(key = "t-" + s.name.lowercase()) {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).background(MaterialTheme.colorScheme.surface).padding(12.dp)) {
                        Totals(s.inn, s.out, currency, people, big = false)
                    }
                }
                items(s.list, key = { "e-" + it.id }) { e ->
                    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).background(MaterialTheme.colorScheme.surface)) {
                        HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                        Row(
                            Modifier.fillMaxWidth().clickable { onEdit(e) }.padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(entryTitle(e), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                val note = if (e.note.isNotBlank() && !e.note.equals(e.item, true)) e.note else e.type.label
                                Text(df.format(Date(e.date)) + " · " + note, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            Text(
                                (if (moneyIn(e)) "+" else "−") + fmt(e.amount),
                                fontWeight = FontWeight.Bold, fontSize = 14.sp,
                                color = if (moneyIn(e)) GreenIn else RedSpend
                            )
                        }
                    }
                }
                item(key = "f-" + s.name.lowercase()) {
                    Column(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                            .clip(RoundedCornerShape(bottomStart = 20.dp, bottomEnd = 20.dp))
                            .background(MaterialTheme.colorScheme.surface)
                    ) {
                        if (people) {
                            Row(Modifier.padding(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilledTonalButton(
                                    onClick = { onNew(template(Type.DIYA, s.name)) }, modifier = Modifier.weight(1f).height(44.dp),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.filledTonalButtonColors(containerColor = RedSpend.copy(alpha = 0.12f), contentColor = RedSpend)
                                ) { Text("+ Gave", fontWeight = FontWeight.Bold) }
                                FilledTonalButton(
                                    onClick = { onNew(template(Type.WAPIS_MILA, s.name)) }, modifier = Modifier.weight(1f).height(44.dp),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.filledTonalButtonColors(containerColor = GreenIn.copy(alpha = 0.14f), contentColor = GreenIn)
                                ) { Text("+ Got back", fontWeight = FontWeight.Bold) }
                            }
                        } else {
                            Spacer(Modifier.height(6.dp))
                        }
                    }
                }
            }
        }
    }
}

/** Total in, total out and the balance, side by side. */
@Composable
private fun Totals(inn: Double, out: Double, currency: String, people: Boolean, big: Boolean) {
    val bal = inn - out
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Figure("Total in", "+" + fmt(inn), GreenIn, Modifier.weight(1f), big)
        Figure("Total out", "−" + fmt(out), RedSpend, Modifier.weight(1f), big)
        Figure(
            if (people) (if (bal < -0.005) "They owe you" else if (bal > 0.005) "You owe" else "Balance") else "Balance",
            if (people) fmt(kotlin.math.abs(bal)) else (if (bal > 0.005) "+" else if (bal < -0.005) "−" else "") + fmt(kotlin.math.abs(bal)),
            if (people && bal < -0.005) OrangeOwe else MaterialTheme.colorScheme.onSurface,
            Modifier.weight(1f), big
        )
    }
    if (big) {
        Text(
            "Amounts in $currency",
            fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp)
        )
    }
}

@Composable
private fun Figure(label: String, value: String, color: androidx.compose.ui.graphics.Color, modifier: Modifier, big: Boolean) {
    Column(
        modifier.clip(RoundedCornerShape(12.dp)).background(color.copy(alpha = 0.08f))
            .padding(horizontal = 10.dp, vertical = if (big) 10.dp else 7.dp)
    ) {
        Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(value, fontSize = if (big) 17.sp else 14.sp, fontWeight = FontWeight.ExtraBold, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun BalanceChip(bal: Double, currency: String, people: Boolean) {
    val text = when {
        people && bal < -0.005 -> "Owes you " + fmt(-bal)
        people && bal > 0.005 -> "You owe " + fmt(bal)
        people -> "Settled"
        else -> (if (bal > 0.005) "+" else if (bal < -0.005) "−" else "") + fmt(kotlin.math.abs(bal))
    }
    val color = when {
        people && bal < -0.005 -> OrangeOwe
        !people && bal < -0.005 -> RedSpend
        bal > 0.005 -> GreenIn
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Text(
        text, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = color,
        modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(color.copy(alpha = 0.10f)).padding(horizontal = 8.dp, vertical = 4.dp)
    )
}
