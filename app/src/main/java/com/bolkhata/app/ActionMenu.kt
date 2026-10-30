package com.bolkhata.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MergeType
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
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

private val MoveBlue = Color(0xFF5B5FF0)

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
    badge: @Composable () -> Unit,
    actions: List<MenuAction>,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            Modifier.fillMaxSize().clickable(remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
            contentAlignment = Alignment.Center
        ) {
            // Taps on the card itself must not close the menu.
            Column(Modifier.padding(horizontal = 20.dp).clickable(remember { MutableInteractionSource() }, indication = null) {}) {
                Surface(shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.surface, shadowElevation = 10.dp) {
                    Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        badge()
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(title, fontWeight = FontWeight.Bold, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(amount, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, color = amountColor)
                    }
                }
                Spacer(Modifier.height(10.dp))
                Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface, shadowElevation = 10.dp) {
                    Row(Modifier.fillMaxWidth().padding(6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        actions.forEachIndexed { i, a -> ActionPill(a, main = i == 0, Modifier.weight(1f)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun ActionPill(a: MenuAction, main: Boolean, modifier: Modifier) {
    val tint = when {
        main -> GreenDeep
        a.danger -> RedSpend
        else -> MoveBlue
    }
    val content = if (main) Color.White else tint
    Row(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(if (main) tint else tint.copy(alpha = 0.12f))
            .clickable(onClick = a.onClick)
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(a.icon, contentDescription = null, tint = content, modifier = Modifier.size(17.dp))
        Spacer(Modifier.width(6.dp))
        Text(a.label, color = content, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 1)
    }
}

/** Long-press menu of one entry: Edit, Move, Delete. */
@Composable
fun EntryMenu(e: Entry, currency: String, onEdit: () -> Unit, onMove: () -> Unit, onDelete: () -> Unit, onDismiss: () -> Unit) {
    val out = isOutflowType(e.type)
    ActionMenu(
        title = entryTitle(e),
        subtitle = listOf(shortDateYear(e.date), e.category.label).joinToString(" · "),
        amount = (if (out) "−" else "+") + money(currency, e.amount),
        amountColor = if (out) RedSpend else GreenIn,
        badge = { CatBubble(e.category) },
        actions = listOf(
            MenuAction("Edit", Icons.Filled.Edit, onClick = onEdit),
            MenuAction("Move", Icons.Filled.SwapHoriz, onClick = onMove),
            MenuAction("Delete", Icons.Filled.Delete, danger = true, onClick = onDelete)
        ),
        onDismiss = onDismiss
    )
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
        badge = {
            Box(
                Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(color.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) { Text(name.trim().take(1).uppercase(), fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, color = color) }
        },
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
