package com.bolkhata.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MergeType
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties


/** One button of an [ActionMenu]. The first action is the main one and is filled. */
class MenuAction(val label: String, val icon: ImageVector, val danger: Boolean = false, val onClick: () -> Unit)

/**
 * The long-press menu for an entry or a khata: a small card naming what was pressed, with one row of
 * buttons under it. Tapping outside closes it.
 */
@Composable
fun ActionMenu(
    title: String,
    subtitle: String,
    amount: String,
    amountColor: Color,
    actions: List<MenuAction>,
    onDismiss: () -> Unit
) {
    // One small white card: the name and its line (amount at the end), then one plain row per action, Delete in red.
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            Modifier.fillMaxSize().clickable(remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                Modifier.width(240.dp).clickable(remember { MutableInteractionSource() }, indication = null) {},
                shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surface, shadowElevation = 8.dp
            ) {
                Column(Modifier.padding(vertical = 4.dp)) {
                    Row(Modifier.padding(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(title, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(subtitle, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        if (amount.isNotBlank()) Text(amount, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp, color = amountColor, modifier = Modifier.padding(start = 8.dp))
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    actions.forEach { a ->
                        val tint = if (a.danger) RedSpend else MaterialTheme.colorScheme.onSurface
                        Row(Modifier.fillMaxWidth().clickable(onClick = a.onClick).padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(a.icon, contentDescription = null, tint = if (a.danger) RedSpend else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(10.dp))
                            Text(a.label, color = tint, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}

/** Long-press menu of one entry: Edit, Move, Delete. */
@Composable
fun EntryMenu(
    e: Entry, currency: String, onEdit: () -> Unit, onRename: () -> Unit, onMove: () -> Unit, onSelect: () -> Unit, onDelete: () -> Unit, onDismiss: () -> Unit,
    /** A bill for goods given to a customer (shown only for such entries). */
    onBill: (() -> Unit)? = null
) {
    val out = isOutflowType(e.type)
    ActionMenu(
        title = entryTitle(e),
        subtitle = listOf(shortDateYear(e.date), e.category.label).joinToString(" · "),
        amount = (if (out) "−" else "+") + money(currency, e.amount),
        amountColor = if (out) RedSpend else GreenIn,
        actions = listOfNotNull(
            onBill?.let { MenuAction("Bill", Icons.Filled.Description, onClick = it) },
            MenuAction("Edit", Icons.Filled.Edit, onClick = onEdit),
            MenuAction("Rename", Icons.Filled.DriveFileRenameOutline, onClick = onRename),
            MenuAction("Move", Icons.Filled.SwapHoriz, onClick = onMove),
            MenuAction("Select", Icons.Filled.CheckCircle, onClick = onSelect),
            MenuAction("Delete", Icons.Filled.Delete, danger = true, onClick = onDelete)
        ),
        onDismiss = onDismiss
    )
}

/** A small rename card: the old name, one field with the new name, Cancel and Rename side by side. */
@Composable
fun RenameDialog(title: String, old: String, info: String = "", onRename: (String) -> Unit, onDismiss: () -> Unit) {
    var name by remember(old) { mutableStateOf(old) }
    val ok = name.isNotBlank() && !name.trim().equals(old.trim(), true)
    val shape = RoundedCornerShape(12.dp)
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxWidth(0.82f), shape = RoundedCornerShape(18.dp), color = LocalPage.current) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Column {
                    Text(title, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Text(listOf(old, info).filter { it.isNotBlank() }.joinToString(" · "), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                BasicTextField(
                    name, { name = it }, singleLine = true,
                    textStyle = TextStyle(fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.onSurface),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { if (ok) onRename(name.trim()) }),
                    modifier = Modifier.fillMaxWidth().height(42.dp).fieldEdge(shape)
                        .padding(horizontal = 12.dp).wrapContentHeight(Alignment.CenterVertically)
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onDismiss, shape = shape, modifier = Modifier.weight(1f).height(40.dp)) { Text("Cancel") }
                    Button(onClick = { onRename(name.trim()) }, enabled = ok, shape = shape, modifier = Modifier.weight(1f).height(40.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = RedSpend)) { Text("Rename", fontWeight = FontWeight.Bold) }
                }
            }
        }
    }
}

/** Long-press menu of one khata: Rename, Merge, Delete. */
@Composable
fun KhataMenu(
    name: String, entryCount: Int, balance: Double, currency: String,
    onRename: () -> Unit, onMerge: () -> Unit, onDelete: () -> Unit, onDismiss: () -> Unit
) {
    val receive = balance > 0.005
    val settled = kotlin.math.abs(balance) < 0.005
    val color = if (receive) GreenIn else OrangeOwe
    ActionMenu(
        title = name,
        subtitle = (if (entryCount == 1) "1 entry" else "$entryCount entries") +
            when { settled -> " · settled"; receive -> " · you'll receive"; else -> " · you'll pay" },
        amount = if (settled) "0" else money(currency, kotlin.math.abs(balance)),
        amountColor = if (settled) MaterialTheme.colorScheme.onSurfaceVariant else color,
        actions = listOf(
            MenuAction("Rename", Icons.Filled.DriveFileRenameOutline, onClick = onRename),
            MenuAction("Merge", Icons.Filled.MergeType, onClick = onMerge),
            MenuAction("Delete", Icons.Filled.Delete, danger = true, onClick = onDelete)
        ),
        onDismiss = onDismiss
    )
}

private fun isOutflowType(t: Type) = when (t) {
    Type.KHARCHA, Type.DIYA, Type.WAPIS_DIYA, Type.PURCHASE, Type.TRANSFER_OUT -> true
    else -> false
}

private fun shortDateYear(d: Long): String = java.text.SimpleDateFormat("d MMM yyyy", java.util.Locale.US).format(java.util.Date(d))
