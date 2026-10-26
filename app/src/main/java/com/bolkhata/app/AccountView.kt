package com.bolkhata.app

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowRight
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
import java.util.Calendar
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

/** Tap opens the entry's account (where that is set up), long-press edits it. */
@OptIn(ExperimentalFoundationApi::class)
fun Modifier.entryTap(e: Entry, onEdit: () -> Unit): Modifier = composed {
    val open = LocalAccountOpener.current
    combinedClickable(onClick = { if (open != null) open(e) else onEdit() }, onLongClick = onEdit)
}

private fun moneyIn(e: Entry) = when (e.type) {
    Type.AAMDANI, Type.WAPIS_MILA, Type.LIYA -> true
    Type.SALE -> e.person.isBlank() || e.paid
    else -> false
}

private fun monthStart(back: Int): Long = Calendar.getInstance().apply {
    set(Calendar.DAY_OF_MONTH, 1); set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
    set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    add(Calendar.MONTH, -back)
}.timeInMillis

private class Sub(val name: String, val list: List<Entry>, val loan: Double, val thisMonth: Double, val all: Double)

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
    var focus by remember(key) { mutableStateOf(key.sub) }
    val data = remember(entries, key.main) {
        val mine = entries.filter { accountOf(it).main == key.main }
        val m0 = monthStart(0)
        // Spellings of one name or shop ("Shahbaz" / "shahbaz") are one account.
        mine.groupBy { accountOf(it).sub.lowercase() }.map { (_, list) ->
            val name = list.groupingBy { accountOf(it).sub }.eachCount().maxByOrNull { it.value }?.key ?: ""
            val spent = list.filter { !moneyIn(it) }
            Sub(
                name = name,
                list = list.sortedWith(compareByDescending<Entry> { it.date }.thenByDescending { it.createdAt }),
                loan = balances(list).values.sum(),
                thisMonth = spent.filter { it.date >= m0 }.sumOf { it.amount },
                all = if (people) list.sumOf { if (moneyIn(it)) it.amount else -it.amount } else spent.sumOf { it.amount }
            )
        }
    }
    val m0 = monthStart(0)
    val m1 = monthStart(1)
    val allMine = data.flatMap { it.list }
    val current = data.firstOrNull { it.name.equals(focus, true) }
    val others = data.filter { it !== current }.sortedByDescending { if (people) kotlin.math.abs(it.loan) else it.all }
    val df = SimpleDateFormat("d MMM", Locale.US)

    val headBg = MaterialTheme.colorScheme.inverseSurface
    val headFg = MaterialTheme.colorScheme.inverseOnSurface

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            Column(
                Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp))
                    .background(headBg)
                    .padding(horizontal = 16.dp).padding(top = 12.dp, bottom = 18.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = headFg) }
                    Spacer(Modifier.width(4.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Main account", color = headFg.copy(alpha = 0.7f), fontSize = 12.sp)
                        Text(key.main, color = headFg, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)
                    }
                    Text(if (data.size == 1) "1 account" else "${data.size} accounts", color = headFg.copy(alpha = 0.75f), fontSize = 12.sp)
                }
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (people) {
                        val owed = data.sumOf { if (it.loan > 0) it.loan else 0.0 }
                        val owe = data.sumOf { if (it.loan < 0) -it.loan else 0.0 }
                        HeadFigure("They owe you", money(currency, owed), headFg, Modifier.weight(1f))
                        HeadFigure("You owe", money(currency, owe), headFg, Modifier.weight(1f))
                    } else {
                        val out = allMine.filter { !moneyIn(it) }
                        val inn = allMine.filter { moneyIn(it) }
                        val useIn = out.isEmpty() && inn.isNotEmpty()
                        val src = if (useIn) inn else out
                        HeadFigure("This month", money(currency, src.filter { it.date >= m0 }.sumOf { it.amount }), headFg, Modifier.weight(1f))
                        HeadFigure("Last month", money(currency, src.filter { it.date in m1 until m0 }.sumOf { it.amount }), headFg, Modifier.weight(1f))
                    }
                }
            }
        }

        if (current != null) {
            item(key = "focus-head") {
                Surface(
                    Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 14.dp),
                    shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                ) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Letter(current.name, people)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(current.name, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                summary(current, people, currency), fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                                color = if (people && current.loan > 0.005) OrangeOwe else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
            items(current.list, key = { "f-" + it.id }) { e ->
                Surface(Modifier.fillMaxWidth().padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.surface) {
                    Row(
                        Modifier.fillMaxWidth().clickable { onEdit(e) }.padding(horizontal = 14.dp, vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(df.format(Date(e.date)), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(52.dp))
                        Column(Modifier.weight(1f)) {
                            Text(entryTitle(e), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            val sub = (if (e.note.isNotBlank() && !e.note.equals(e.item, true)) e.note else e.type.label)
                            Text(sub, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Text(moneyA(currency, e.amount, prefix(e.type)), fontWeight = FontWeight.Bold, color = signColor(e.type), fontSize = 14.sp)
                    }
                }
            }
            item(key = "focus-foot") {
                Surface(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    shape = RoundedCornerShape(bottomStart = 22.dp, bottomEnd = 22.dp),
                    color = MaterialTheme.colorScheme.surface
                ) {
                    if (people) {
                        Row(Modifier.padding(start = 14.dp, end = 14.dp, top = 6.dp, bottom = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilledTonalButton(
                                onClick = { onNew(template(Type.DIYA, current.name)) }, modifier = Modifier.weight(1f).height(44.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.filledTonalButtonColors(containerColor = RedSpend.copy(alpha = 0.12f), contentColor = RedSpend)
                            ) { Text("+ Gave", fontWeight = FontWeight.Bold) }
                            FilledTonalButton(
                                onClick = { onNew(template(Type.WAPIS_MILA, current.name)) }, modifier = Modifier.weight(1f).height(44.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.filledTonalButtonColors(containerColor = GreenIn.copy(alpha = 0.14f), contentColor = GreenIn)
                            ) { Text("+ Got back", fontWeight = FontWeight.Bold) }
                        }
                    } else {
                        Text(
                            "Tap an entry to edit it.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                        )
                    }
                }
            }
        }

        if (others.isNotEmpty()) {
            item {
                Text(
                    "Other accounts in ${key.main}", fontSize = 13.sp, fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 20.dp, top = 18.dp, bottom = 6.dp)
                )
            }
            items(others, key = { "o-" + it.name.lowercase() }) { s ->
                Surface(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 3.dp), shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface) {
                    Row(
                        Modifier.fillMaxWidth().clickable { focus = s.name }.padding(horizontal = 14.dp, vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Letter(s.name, people)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(s.name, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                if (s.list.size == 1) "1 entry" else "${s.list.size} entries",
                                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            if (people) loanShort(s.loan) else fmt(s.all),
                            fontSize = 13.sp, fontWeight = FontWeight.Bold,
                            color = if (people && s.loan > 0.005) OrangeOwe else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Icon(Icons.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

private fun summary(s: Sub, people: Boolean, currency: String): String = if (people) {
    when {
        s.loan > 0.005 -> "Owes you " + money(currency, s.loan)
        s.loan < -0.005 -> "You owe " + money(currency, -s.loan)
        else -> "No loan · " + (if (s.list.size == 1) "1 entry" else "${s.list.size} entries")
    }
} else {
    "This month " + fmt(s.thisMonth) + " · all time " + money(currency, s.all)
}

private fun loanShort(v: Double) = when {
    v > 0.005 -> "Owes you " + fmt(v)
    v < -0.005 -> "You owe " + fmt(-v)
    else -> "Settled"
}

private fun template(type: Type, person: String): Entry {
    val now = System.currentTimeMillis()
    return Entry(
        id = "e" + System.nanoTime(), type = type, amount = 0.0, category = Cat.UDHAAR, item = "Loans",
        person = person, note = "", date = now, createdAt = now
    )
}

@Composable
private fun Letter(name: String, round: Boolean) {
    Box(
        Modifier.size(38.dp).clip(if (round) CircleShape else RoundedCornerShape(11.dp))
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
        contentAlignment = Alignment.Center
    ) {
        Text(name.trim().take(1).uppercase(), fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun HeadFigure(label: String, value: String, fg: androidx.compose.ui.graphics.Color, modifier: Modifier) {
    Column(modifier.clip(RoundedCornerShape(14.dp)).background(fg.copy(alpha = 0.10f)).padding(horizontal = 12.dp, vertical = 10.dp)) {
        Text(label, color = fg.copy(alpha = 0.8f), fontSize = 12.sp)
        Text(value, color = fg, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
