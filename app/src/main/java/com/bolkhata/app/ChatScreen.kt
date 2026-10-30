package com.bolkhata.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Chat tab: the person speaks or types. Entries are saved straight into the khata (shown as a card with Undo),
 * questions are answered, and bank payments wait here to be added.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    messages: List<ChatMessage>,
    loading: Boolean,
    loadingLabel: String = "Thinking…",
    entries: List<Entry>,
    currency: String,
    subtitle: String,
    suggestions: List<Entry>,
    quick: List<Entry>,
    showStock: Boolean,
    onTab: (Tab) -> Unit,
    onMenu: () -> Unit,
    onSend: (String) -> Unit,
    onClear: () -> Unit,
    onShareDiagnostics: () -> Unit = {},
    flaggedIds: Set<String> = emptySet(),
    onFlagWrong: (id: String, expected: String) -> Unit = { _, _ -> },
    onUndo: (List<String>) -> Unit,
    onEditEntry: (Entry) -> Unit,
    onAddSuggestions: (List<Entry>) -> Unit,
    onIgnoreSuggestions: (Set<String>) -> Unit,
    onQuick: (Entry) -> Unit,
    imports: List<Entry> = emptyList(),
    importDupes: Set<String> = emptySet(),
    onAddImports: (List<Entry>) -> Unit = {},
    onDropImports: () -> Unit = {},
    onUpdateSaved: () -> Unit = {},
    voiceOn: Boolean = false,
    pendingChanges: List<String> = emptyList(),
    onApplyChanges: () -> Unit = {},
    onCancelChanges: () -> Unit = {}
) {
    var confirmClear by remember { mutableStateOf(false) }
    var confirmShare by remember { mutableStateOf(false) }
    var flagging by remember { mutableStateOf<ChatMessage?>(null) }
    val listState = rememberLazyListState()
    val byId = remember(entries) { entries.associateBy { it.id } }
    val itemCount = messages.size + (if (messages.isEmpty()) 1 else 0) + (if (loading) 1 else 0) +
        (if (suggestions.isNotEmpty()) 1 else 0) + (if (imports.isNotEmpty()) 1 else 0) + (if (pendingChanges.isNotEmpty()) 1 else 0)

    LaunchedEffect(messages.size, loading, suggestions.size, imports.size, pendingChanges.size) {
        if (itemCount > 0) listState.animateScrollToItem(itemCount - 1)
    }

    Column(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxWidth()) {
            Box(
                Modifier
                    .matchParentSize()
                    .clip(RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp))
            ) { HeroBackground(Modifier.matchParentSize()) }
            Column(Modifier.padding(horizontal = 16.dp).padding(top = 14.dp, bottom = 16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onMenu) {
                        Icon(Icons.Filled.Menu, contentDescription = "Open menu", tint = LocalHeader.current.on)
                    }
                    Spacer(Modifier.width(4.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Assistant", color = LocalHeader.current.on, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Text(subtitle, color = LocalHeader.current.on.copy(alpha = 0.8f), fontSize = 13.sp, maxLines = 1)
                    }
                    Box {
                        var more by remember { mutableStateOf(false) }
                        IconButton(onClick = { more = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = "More options", tint = LocalHeader.current.on)
                        }
                        DropdownMenu(expanded = more, onDismissRequest = { more = false }) {
                            DropdownMenuItem(
                                text = { Text("Share chat log") },
                                leadingIcon = { Icon(Icons.Outlined.Share, contentDescription = null) },
                                enabled = !loading,
                                onClick = { more = false; confirmShare = true }
                            )
                            DropdownMenuItem(
                                text = { Text("Clear chat") },
                                leadingIcon = { Icon(Icons.Outlined.Delete, contentDescription = null) },
                                enabled = messages.isNotEmpty() && !loading,
                                onClick = { more = false; confirmClear = true }
                            )
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                TabSwitcher(Tab.CHAT, showStock, 0, onTab)
            }
        }

        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            state = listState,
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (messages.isEmpty()) {
                item(key = "chat-welcome") {
                    Column(
                        Modifier.fillMaxWidth().padding(vertical = 20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("Your money assistant", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Speak or type an entry and it is saved right away. You can also ask questions, in English or Roman Urdu.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(16.dp))
                        listOf(
                            "petrol 60",
                            "How much did I spend this month?",
                            "Who owes me money?"
                        ).forEach { suggestion ->
                            OutlinedButton(
                                onClick = { onSend(suggestion) },
                                modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)
                            ) { Text(suggestion) }
                        }
                    }
                }
            }
            items(messages, key = { it.id }) { message ->
                if (message.isUser) {
                    UserBubble(message.text, message.attachment)
                } else {
                    Column(Modifier.fillMaxWidth()) {
                        if (message.entryIds.isNotEmpty()) SavedCard(message, byId, currency, onUndo, onEditEntry)
                        else AssistantBubble(message.text)
                        WrongAnswerMark(message.id in flaggedIds) { flagging = message }
                    }
                }
            }
            if (loading) {
                item(key = "chat-loading") {
                    Row(
                        Modifier
                            .clip(RoundedCornerShape(18.dp))
                            .background(MaterialTheme.colorScheme.surface)
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(10.dp))
                        Text(loadingLabel, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            if (pendingChanges.isNotEmpty()) {
                item(key = "chat-changes") {
                    Column(
                        Modifier
                            .fillMaxWidth(0.92f)
                            .clip(RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = 6.dp, bottomEnd = 18.dp))
                            .background(MaterialTheme.colorScheme.surface)
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text("Apply these changes?", color = OrangeOwe, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        pendingChanges.forEach { line ->
                            Text(line, fontSize = 13.sp, lineHeight = 18.sp, color = MaterialTheme.colorScheme.onSurface)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = onApplyChanges, shape = RoundedCornerShape(12.dp), modifier = Modifier.weight(1f)) {
                                Text("Apply")
                            }
                            OutlinedButton(onClick = onCancelChanges, shape = RoundedCornerShape(12.dp)) { Text("Cancel") }
                        }
                    }
                }
            }
            if (imports.isNotEmpty()) {
                item(key = "chat-import") {
                    PickCard(
                        title = "${imports.size} found in your file" +
                            (if (importDupes.isNotEmpty()) " · ${importDupes.size} already saved (unticked)" else ""),
                        titleColor = MaterialTheme.colorScheme.primary,
                        rows = imports,
                        preselected = imports.map { it.id }.toSet() - importDupes,
                        currency = currency,
                        onAdd = onAddImports,
                        ignoreLabel = "Cancel",
                        onIgnore = { onDropImports() },
                        extraLabel = if (importDupes.isNotEmpty()) "Update details of ${importDupes.size} saved" else null,
                        onExtra = onUpdateSaved
                    )
                }
            }
            if (suggestions.isNotEmpty()) {
                item(key = "chat-bank") {
                    PickCard(
                        title = "${suggestions.size} " + (if (suggestions.size == 1) "payment" else "payments") + " from your bank",
                        titleColor = OrangeOwe,
                        rows = suggestions,
                        preselected = suggestions.map { it.id }.toSet(),
                        currency = currency,
                        onAdd = onAddSuggestions,
                        ignoreLabel = "Ignore",
                        onIgnore = onIgnoreSuggestions
                    )
                }
            }
        }

        if (quick.isNotEmpty() && !voiceOn) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                quick.forEach { e ->
                    AssistChip(
                        onClick = { onQuick(e) },
                        label = { Text("+ " + quickLabel(e) + " " + fmt(e.amount), maxLines = 1) },
                        shape = CircleShape
                    )
                }
            }
        }
    }

    flagging?.let { message ->
        var expected by remember(message.id) { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { flagging = null },
            title = { Text("Wrong answer?") },
            text = {
                Column {
                    Text(
                        "\u201C" + message.text.take(160) + (if (message.text.length > 160) "…" else "") + "\u201D",
                        fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = expected,
                        onValueChange = { expected = it },
                        label = { Text("What should the answer be? (optional)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    onFlagWrong(message.id, expected.trim())
                    flagging = null
                }) { Text("Mark wrong") }
            },
            dismissButton = {
                TextButton(onClick = { flagging = null }) { Text("Cancel") }
            }
        )
    }

    if (confirmShare) {
        AlertDialog(
            onDismissRequest = { confirmShare = false },
            title = { Text("Share chat diagnostics?") },
            text = {
                Text("This prepares the full chat log, including answers you marked wrong, and technical debug details for sharing. Clearing the chat does not delete this log. Messages may contain private financial information. No ledger or contact list is attached. Review the content and choose ChatGPT in Android's share menu if available.")
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmShare = false
                    onShareDiagnostics()
                }) { Text("Review & share") }
            },
            dismissButton = {
                TextButton(onClick = { confirmShare = false }) { Text("Cancel") }
            }
        )
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Clear chat history?") },
            text = { Text("This removes the conversation from this phone. Your entries stay.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmClear = false
                    onClear()
                }) { Text("Clear") }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) { Text("Cancel") }
            }
        )
    }
}

fun quickLabel(e: Entry): String = e.item.ifBlank { e.note }.ifBlank { e.category.label }.take(18)

/** Long-press a chat message to copy its whole text. */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun Modifier.copyable(text: String): Modifier {
    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
    val context = androidx.compose.ui.platform.LocalContext.current
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    return this.combinedClickable(
        onClick = {},
        onLongClick = {
            clipboard.setText(androidx.compose.ui.text.AnnotatedString(text))
            haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
            android.widget.Toast.makeText(context, "Copied", android.widget.Toast.LENGTH_SHORT).show()
        }
    )
}

@Composable
private fun UserBubble(text: String, attachment: String = "") {
    if (attachment.isNotBlank()) {
        AttachmentBubble(attachment, text)
        return
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Text(
            text,
            color = MaterialTheme.colorScheme.onSurface,
            lineHeight = 21.sp,
            modifier = Modifier
                .widthIn(max = 300.dp)
                .clip(RoundedCornerShape(20.dp))
                .copyable(text)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(horizontal = 14.dp, vertical = 10.dp)
        )
    }
}

/** Small link under each answer so the person can mark it wrong; the mark is kept in the chat log. */
@Composable
private fun WrongAnswerMark(flagged: Boolean, onClick: () -> Unit) {
    Text(
        if (flagged) "✗ Marked wrong" else "Wrong answer?",
        fontSize = 11.sp,
        color = if (flagged) MaterialTheme.colorScheme.error
        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
        modifier = Modifier
            .padding(start = 6.dp, top = 2.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(enabled = !flagged, onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 2.dp)
    )
}

@Composable
private fun AssistantBubble(text: String) {
    // Plain text like ChatGPT; the engine trace is kept in the chat log for review, not shown here.
    Text(
        text,
        color = MaterialTheme.colorScheme.onSurface,
        lineHeight = 21.sp,
        modifier = Modifier
            .widthIn(max = 340.dp)
            .copyable(text)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    )
}

@Composable
private fun SavedCard(
    message: ChatMessage,
    byId: Map<String, Entry>,
    currency: String,
    onUndo: (List<String>) -> Unit,
    onEditEntry: (Entry) -> Unit
) {
    val live = message.entryIds.mapNotNull { byId[it] }
    val copyText = (listOf(message.text) + live.map { entryTitle(it) + " " + fmt(it.amount) + " " + currency }).joinToString("\n")
    Column(
        Modifier
            .fillMaxWidth(0.88f)
            .clip(RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = 6.dp, bottomEnd = 18.dp))
            .copyable(copyText)
            .background(MaterialTheme.colorScheme.surface)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            if (live.isEmpty()) "Removed" else "✓ " + live.size + (if (live.size == 1) " entry saved" else " entries saved"),
            color = if (live.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant else GreenIn,
            fontWeight = FontWeight.Bold, fontSize = 13.sp
        )
        // The assistant's own short line ("Saved: breakfast 7 SAR"), when it wrote one.
        if (live.isNotEmpty() && !message.text.startsWith("✓")) {
            Text(message.text, fontSize = 14.sp, lineHeight = 19.sp, color = MaterialTheme.colorScheme.onSurface)
        }
        live.forEach { e ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                    .clickable { onEditEntry(e) }
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier.clip(CircleShape).background(catColor(e.category).copy(alpha = 0.16f))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) { Text(e.category.label, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface, maxLines = 1) }
                Spacer(Modifier.width(8.dp))
                Text(entryTitle(e), Modifier.weight(1f), fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(moneyA(currency, e.amount, prefix(e.type)), color = signColor(e.type), fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
        }
        if (live.isNotEmpty()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Tap an entry to change it", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                TextButton(onClick = { onUndo(live.map { it.id }) }) { Text("Undo", fontWeight = FontWeight.Bold) }
            }
        }
    }
}

/** A list to tick and add: bank payments, or transactions read from a statement. */
@Composable
private fun PickCard(
    title: String,
    titleColor: Color,
    rows: List<Entry>,
    preselected: Set<String>,
    currency: String,
    onAdd: (List<Entry>) -> Unit,
    ignoreLabel: String,
    onIgnore: (Set<String>) -> Unit,
    extraLabel: String? = null,
    onExtra: () -> Unit = {}
) {
    val suggestions = rows
    var picked by remember(rows.map { it.id }) { mutableStateOf(preselected) }
    Column(
        Modifier
            .fillMaxWidth(0.92f)
            .clip(RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = 6.dp, bottomEnd = 18.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(title, color = titleColor, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        Column(
            Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())
        ) {
        suggestions.forEach { e ->
            Row(
                Modifier.fillMaxWidth().clickable {
                    picked = if (e.id in picked) picked - e.id else picked + e.id
                },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = e.id in picked,
                    onCheckedChange = { on -> picked = if (on) picked + e.id else picked - e.id }
                )
                Column(Modifier.weight(1f)) {
                    if (e.person.isNotBlank() && !e.note.contains(e.person, ignoreCase = true)) {
                        Text(e.person, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    }
                    Text(e.note.ifBlank { entryTitle(e) }, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    Text(
                        e.category.label + " · " + dayLabel(e.date) + " " + timeLabel(e.date),
                        fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1
                    )
                }
                Text(moneyA(currency, e.amount, prefix(e.type)), color = signColor(e.type), fontWeight = FontWeight.Bold)
            }
        }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { onAdd(suggestions.filter { it.id in picked }) },
                enabled = picked.isNotEmpty(),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.weight(1f)
            ) { Text(if (picked.size == suggestions.size && picked.size > 1) "Add all ${picked.size}" else "Add ${picked.size}") }
            OutlinedButton(
                onClick = { onIgnore(picked) },
                enabled = picked.isNotEmpty() || ignoreLabel == "Cancel",
                shape = RoundedCornerShape(12.dp)
            ) { Text(ignoreLabel) }
        }
        if (extraLabel != null) {
            // Entries already saved get the clearer names, notes and times from the file, amounts unchanged.
            FilledTonalButton(onClick = onExtra, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                Text(extraLabel)
            }
        }
    }
}

/** A sent photo or PDF page, shown like a picture in a messaging app. Tap to see it full screen. */
@Composable
private fun AttachmentBubble(path: String, name: String) {
    val bitmap by produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, path) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching { android.graphics.BitmapFactory.decodeFile(path)?.asImageBitmap() }.getOrNull()
        }
    }
    var full by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = 18.dp, bottomEnd = 6.dp)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Column(
            Modifier
                .widthIn(max = 250.dp)
                .clip(shape)
                .background(MaterialTheme.colorScheme.primary)
                .padding(4.dp)
        ) {
            val img = bitmap
            if (img != null) {
                androidx.compose.foundation.Image(
                    img, contentDescription = name,
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                    modifier = Modifier
                        .width(242.dp)
                        .aspectRatio((img.width.toFloat() / img.height).coerceIn(0.6f, 1.6f))
                        .clip(RoundedCornerShape(15.dp))
                        .clickable { full = true }
                )
            } else {
                Box(
                    Modifier.width(242.dp).height(160.dp).clip(RoundedCornerShape(15.dp))
                        .background(MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.12f))
                )
            }
            if (name.lowercase().endsWith(".pdf")) {
                Text(
                    name, color = MaterialTheme.colorScheme.onPrimary, fontSize = 13.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                )
            }
        }
    }
    val img = bitmap
    if (full && img != null) {
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { full = false },
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                Modifier.fillMaxSize().background(Color.Black).clickable { full = false },
                contentAlignment = Alignment.Center
            ) {
                androidx.compose.foundation.Image(
                    img, contentDescription = name,
                    contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
