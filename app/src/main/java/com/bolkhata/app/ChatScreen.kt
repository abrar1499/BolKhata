package com.bolkhata.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ThumbDown as ThumbDownFilled
import androidx.compose.material.icons.filled.ThumbUp as ThumbUpFilled
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.ThumbDown
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
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
    onMenu: () -> Unit,
    business: String = "",
    onSend: (String) -> Unit,
    onClear: () -> Unit,
    onShareDiagnostics: () -> Unit = {},
    /** 👍 (true) or 👎 (false) given to answers, by message id. */
    ratings: Map<String, Boolean> = emptyMap(),
    onRate: (id: String, good: Boolean) -> Unit = { _, _ -> },
    onUndo: (List<String>) -> Unit,
    onEditEntry: (Entry) -> Unit,
    /** An option card was answered: the entry to save for that message, or null when it was closed. */
    onAsked: (id: String, e: Entry?) -> Unit = { _, _ -> },
    onAddSuggestions: (List<Entry>) -> Unit,
    onIgnoreSuggestions: (Set<String>) -> Unit,
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
    val listState = rememberLazyListState()
    val byId = remember(entries) { entries.associateBy { it.id } }
    val itemCount = messages.size + (if (messages.isEmpty()) 1 else 0) + (if (loading) 1 else 0) +
        (if (suggestions.isNotEmpty()) 1 else 0) + (if (imports.isNotEmpty()) 1 else 0) + (if (pendingChanges.isNotEmpty()) 1 else 0)

    LaunchedEffect(messages.size, loading, suggestions.size, imports.size, pendingChanges.size) {
        if (itemCount > 0) listState.animateScrollToItem(itemCount - 1)
    }

    Column(Modifier.fillMaxSize()) {
        // One slim line with the business pill on the page colour (no band on Chat).
        val ink = MaterialTheme.colorScheme.onSurfaceVariant
        Box(Modifier.fillMaxWidth()) {
            Column(Modifier.statusBarsPadding().padding(horizontal = 12.dp).padding(bottom = 10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    BusinessPill(business.ifBlank { "Chat" }, onMenu)
                    Text(subtitle, color = ink, fontSize = 11.sp, maxLines = 1,
                        overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(start = 10.dp, top = 10.dp))
                    Box {
                        var more by remember { mutableStateOf(false) }
                        IconButton(onClick = { more = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = "More options", tint = ink)
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
            }
        }

        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            state = listState,
            contentPadding = PaddingValues(start = 12.dp, top = 6.dp, end = 12.dp, bottom = barSpace(14.dp)),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (messages.isEmpty()) {
                item(key = "chat-welcome") {
                    Column(
                        Modifier.fillMaxWidth().padding(vertical = 20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        GlassLogo(64.dp)
                        Spacer(Modifier.height(10.dp))
                        Text("Smart Expense", fontSize = 18.sp, fontWeight = FontWeight.Bold)
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
                            Text(
                                suggestion, fontSize = 13.sp, textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp).glassEdge(RoundedCornerShape(16.dp))
                                    .clickable { onSend(suggestion) }.padding(vertical = 10.dp)
                            )
                        }
                    }
                }
            }
            items(messages, key = { it.id }) { message ->
                if (message.isUser) {
                    UserBubble(message.text, message.attachment)
                } else {
                    // Each answer starts with the app's small logo, like a chat assistant's picture.
                    Row(Modifier.fillMaxWidth()) {
                    GlassLogo(20.dp, Modifier.padding(top = 8.dp))
                    Column(Modifier.weight(1f)) {
                        if (message.entryIds.isEmpty() && message.meta.startsWith("ask=")) OptionCard(message, byId.values) { onAsked(message.id, it) }
                        else if (message.entryIds.isNotEmpty()) SavedCard(message, byId, currency, onUndo, onEditEntry)
                        // The AI's own sentences ("plain") are never read as a report.
                        else remember(message.text, currency) { if (message.meta.contains("; plain")) null else Report.parse(message.text, currency) }
                            ?.let { ReportCard(it, message.text) } ?: AssistantBubble(message.text)
                        // "Which A or B do you mean?": one tap on a name opens that khata.
                        if (message == messages.lastOrNull()) {
                            val names = NativeChatEngine.nameChoices(message.text)
                            // "Move to which khata: A or B?": the tap repeats the move with the full name.
                            val moving = message.text.startsWith("Move to which account") || message.text.startsWith("Move to which khata")
                            val asked = messages.lastOrNull { it.isUser }?.text.orEmpty()
                            if (names.isNotEmpty()) NameChoices(names) { name -> onSend(if (moving) "$asked $name" else "$name account") }
                            // After a total, the likely next questions as chips.
                            val hero = remember(message.text, currency) { Report.parse(message.text, currency)?.hero?.label.orEmpty() }
                            val asks = when {
                                hero.startsWith("Spent") -> listOf("Details", "Income?", "By category")
                                hero.startsWith("Income") -> listOf("Details", "Spending?")
                                else -> emptyList()
                            }
                            if (names.isEmpty() && asks.isNotEmpty()) FollowChips(asks, onSend)
                        }
                        AnswerActions(ratings[message.id]) { good -> onRate(message.id, good) }
                        // "Groq · 1.2s · 340 tokens" under an answer Groq helped with.
                        Regex("""usage=([^;]+)""").find(message.meta)?.let {
                            Text(it.groupValues[1], fontSize = 7.sp, lineHeight = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                modifier = Modifier.padding(start = 14.dp).offset(y = (-6).dp))
                        }
                    }
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

    }

    if (confirmShare) {
        AppAlert(
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
        AppAlert(
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

/** Chat bubbles like the mock-up: white cards with one small corner pointing at the speaker. */
/**
 * A voice note in the dark bubble: a round play button, the sound's bars and its length ("0:04", kept in the file's
 * name). A tap plays the recording, another stops it.
 */
@Composable
private fun VoiceBubble(path: String) {
    var player by remember(path) { mutableStateOf<android.media.MediaPlayer?>(null) }
    DisposableEffect(path) { onDispose { runCatching { player?.release() } } }
    val seconds = (path.substringAfterLast('-').removeSuffix(".m4a").toLongOrNull() ?: 0L) / 1000
    val bars = remember(path) { listOf(8, 14, 20, 11, 17, 22, 9, 15, 19, 12, 7, 16, 21, 10, 13, 18, 8, 12) }
    val light = MaterialTheme.colorScheme.surface
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Row(
            Modifier.clip(MeShape).background(MaterialTheme.colorScheme.onSurface).clickable {
                val playing = player
                if (playing != null) { runCatching { playing.release() }; player = null }
                else runCatching {
                    player = android.media.MediaPlayer().apply {
                        setDataSource(path)
                        setOnCompletionListener { runCatching { it.release() }; player = null }
                        prepare()
                        start()
                    }
                }
            }.padding(start = 8.dp, end = 12.dp, top = 7.dp, bottom = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.size(26.dp).clip(CircleShape).background(light), contentAlignment = Alignment.Center) {
                Text(if (player == null) "▶" else "■", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface)
            }
            Spacer(Modifier.width(8.dp))
            Row(Modifier.height(22.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                bars.forEach { Box(Modifier.width(2.5.dp).height(it.dp).clip(RoundedCornerShape(2.dp)).background(light.copy(alpha = 0.85f))) }
            }
            Spacer(Modifier.width(8.dp))
            Text("%d:%02d".format(seconds / 60, seconds % 60), fontSize = 10.sp, color = light.copy(alpha = 0.75f))
        }
    }
}

private val MeShape = RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp, bottomStart = 14.dp, bottomEnd = 4.dp)
private val BotShape = RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp, bottomStart = 4.dp, bottomEnd = 14.dp)

@Composable
private fun UserBubble(text: String, attachment: String = "") {
    // A message spoken with the mic shows as a voice note, not as its words.
    if (attachment.endsWith(".m4a")) {
        VoiceBubble(attachment)
        return
    }
    if (attachment.isNotBlank()) {
        AttachmentBubble(attachment, text)
        return
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Text(
            text,
            // The question in a dark bubble, the answer in a white card (like the Yaad mock-up).
            color = MaterialTheme.colorScheme.surface,
            fontSize = 13.sp,
            lineHeight = 17.sp,
            modifier = Modifier
                .widthIn(max = 300.dp)
                .clip(MeShape)
                .copyable(text)
                .background(MaterialTheme.colorScheme.onSurface)
                .padding(horizontal = 12.dp, vertical = 8.dp)
        )
    }
}

/** 👍 and 👎 under each answer, like ChatGPT's. One tap saves the rating; tapping the other one changes it. */
@Composable
private fun AnswerActions(rating: Boolean?, onRate: (good: Boolean) -> Unit) {
    val tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
    Row(Modifier.padding(start = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { onRate(true) }, enabled = rating != true, modifier = Modifier.size(34.dp)) {
            Icon(
                if (rating == true) Icons.Filled.ThumbUpFilled else Icons.Outlined.ThumbUp,
                contentDescription = if (rating == true) "Marked good" else "Good answer",
                tint = if (rating == true) GreenDeep else tint, modifier = Modifier.size(17.dp)
            )
        }
        IconButton(onClick = { onRate(false) }, enabled = rating != false, modifier = Modifier.size(34.dp)) {
            Icon(
                if (rating == false) Icons.Filled.ThumbDownFilled else Icons.Outlined.ThumbDown,
                contentDescription = if (rating == false) "Marked wrong" else "Wrong answer",
                tint = if (rating == false) MaterialTheme.colorScheme.error else tint, modifier = Modifier.size(17.dp)
            )
        }
    }
}

/** Tappable names under a "which one?" question, so the person picks instead of typing. */
@OptIn(ExperimentalLayoutApi::class)
/** Saved names to pick from, in one card; a tap opens that khata (or finishes the move that asked). */
@Composable
private fun NameChoices(names: List<String>, onPick: (String) -> Unit) {
    Column(
        Modifier
            .fillMaxWidth(0.92f)
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(vertical = 6.dp)
    ) {
        names.forEachIndexed { i, name ->
            if (i > 0) HorizontalDivider(Modifier.padding(start = 60.dp), color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.12f))
            Row(
                Modifier.fillMaxWidth().clickable { onPick(name) }.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.size(34.dp).clip(CircleShape).background(GreenDeep.copy(alpha = 0.10f)), contentAlignment = Alignment.Center) {
                    Text(name.trim().take(1).uppercase(), color = GreenDeep, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
                Spacer(Modifier.width(12.dp))
                Text(name, Modifier.weight(1f), fontSize = 15.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("›", fontSize = 20.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun AssistantBubble(text: String) {
    // A white raised card like the mock-up; the engine trace is kept in the chat log for review, not shown here.
    Text(
        text,
        color = MaterialTheme.colorScheme.onSurface,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        modifier = Modifier
            .padding(start = 8.dp)
            .widthIn(max = 320.dp)
            .shadow(1.dp, BotShape)
            .clip(BotShape)
            .copyable(text)
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    )
}

/** A khata, report or list answer as a card: the main amount big, every other amount on its own line. */
@Composable
private fun ReportCard(r: Report, text: String) {
    // Like the Yaad mock-up: a small headline amount, then the detail rows in one bordered box.
    val grey = MaterialTheme.colorScheme.onSurfaceVariant
    val line = grey.copy(alpha = 0.14f)
    fun color(tone: Int) = when (tone) { 1 -> GreenIn; -1 -> RedSpend; else -> Color.Unspecified }
    val hero = r.hero
    Column(
        Modifier
            .fillMaxWidth(0.92f)
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp, bottomEnd = 14.dp, bottomStart = 4.dp))
            .copyable(text)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, line, RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp, bottomEnd = 14.dp, bottomStart = 4.dp))
            .padding(horizontal = 10.dp, vertical = 9.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        if (r.title.isNotBlank() && r.title != hero?.label) Text(r.title, fontWeight = FontWeight.SemiBold, fontSize = 11.sp)
        if (hero != null) {
            Text(hero.label, fontSize = 10.sp, color = grey)
            Text(hero.amount, fontSize = 15.sp, fontWeight = FontWeight.Black, color = color(hero.tone))
        }
        r.stats.filter { it !== hero }.forEach { s ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(s.label, Modifier.weight(1f), fontSize = 10.5.sp, color = MaterialTheme.colorScheme.onSurface)
                Text(s.amount, fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold, color = color(s.tone))
            }
        }
        if (r.notes.isNotEmpty()) Text(r.notes.joinToString(" · "), fontSize = 9.5.sp, color = grey, lineHeight = 12.sp)
        // "Biggest", "Daily average" and "Saved" are facts about the whole answer: shown under the box, not in it.
        val facts = r.rows.filter { it.title.startsWith("Biggest") || it.title == "Daily average" || it.title == "Saved" }
        val rows = r.rows - facts.toSet()
        if (rows.isNotEmpty() || r.total.isNotEmpty()) Column(
            Modifier.padding(top = 4.dp).fillMaxWidth().clip(RoundedCornerShape(10.dp)).border(1.dp, line, RoundedCornerShape(10.dp))
        ) {
            rows.forEachIndexed { i, l ->
                if (i > 0) HorizontalDivider(color = line)
                // One line per row: name, its note (count · share, a date) in small grey, the amount.
                Row(Modifier.padding(horizontal = 8.dp, vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(l.title, Modifier.weight(1f), fontSize = 9.5.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    val note = listOf(l.date, l.sub).filter { it.isNotBlank() }.joinToString(" · ")
                    if (note.isNotBlank()) Text(note, Modifier.padding(horizontal = 6.dp), fontSize = 8.5.sp, color = grey, maxLines = 1)
                    Text(l.amount, fontSize = 9.5.sp, fontWeight = FontWeight.Bold, color = color(hero?.tone ?: 0))
                }
            }
            r.total.forEach { s ->
                HorizontalDivider(color = line)
                Row(Modifier.background(grey.copy(alpha = 0.06f)).padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(if (s.label == "Total") "Total" else "Total · " + s.label, Modifier.weight(1f), fontWeight = FontWeight.Bold, fontSize = 10.sp)
                    Text(s.amount, fontWeight = FontWeight.Bold, fontSize = 10.5.sp, color = color(s.tone))
                }
            }
        }
        facts.forEach { l ->
            Row(Modifier.padding(start = 2.dp, top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(l.title.removePrefix("Biggest: ").let { if (l.title.startsWith("Biggest")) "Biggest · $it" else it },
                    Modifier.weight(1f), fontSize = 9.5.sp, color = grey, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val note = listOf(l.date, l.sub).filter { it.isNotBlank() }.joinToString(" · ")
                if (note.isNotBlank()) Text(note, Modifier.padding(horizontal = 6.dp), fontSize = 8.5.sp, color = grey)
                Text(l.amount, fontSize = 9.5.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

/** Next questions under the last answer, one tap each ("Income kya thi?", "Detail dikhao"). */
@Composable
private fun FollowChips(asks: List<String>, onPick: (String) -> Unit) {
    Row(Modifier.padding(start = 8.dp, top = 2.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        asks.forEach { a ->
            Text(
                a, fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surface)
                    .border(1.dp, MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.18f), RoundedCornerShape(14.dp))
                    .clickable { onPick(a) }.padding(horizontal = 10.dp, vertical = 5.dp)
            )
        }
    }
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
    val sub = MaterialTheme.colorScheme.onSurfaceVariant
    // Deleted since: one small grey line, no card.
    if (live.isEmpty()) {
        Text("Entry deleted", fontSize = 11.sp, color = sub, modifier = Modifier.padding(start = 10.dp, top = 10.dp))
        return
    }
    val copyText = (listOf(message.text) + live.map { entryTitle(it) + " " + fmt(it.amount) + " " + currency }).filter { it.isNotBlank() }.joinToString("\n")
    Column(Modifier.padding(start = 8.dp).copyable(copyText), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        // The assistant's own line, when it had something to say or ask.
        if (message.text.isNotBlank() && !message.text.startsWith("✓")) Text(message.text, fontSize = 12.sp, lineHeight = 16.sp,
            color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.fillMaxWidth(0.85f).shadow(1.dp, BotShape).clip(BotShape)
                .background(MaterialTheme.colorScheme.surface).padding(horizontal = 10.dp, vertical = 7.dp))
        // One small row per entry, like an account row: icon, name over one word, amount, Undo. A tap opens the person's account.
        live.forEach { e ->
            val shape = RoundedCornerShape(12.dp)
            Row(
                Modifier.widthIn(max = 300.dp).shadow(1.dp, shape).clip(shape).background(MaterialTheme.colorScheme.surface)
                    .clickable { onEditEntry(e) }.padding(start = 5.dp, end = 4.dp, top = 5.dp, bottom = 5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.size(26.dp).clip(RoundedCornerShape(8.dp)).background(signColor(e.type).copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
                    Text(if (e.person.isNotBlank()) "👥" else if (signColor(e.type) == GreenIn) "💵" else "🧾", fontSize = 13.sp)
                }
                Column(Modifier.padding(start = 8.dp).weight(1f, fill = false)) {
                    Text(e.person.trim().ifBlank { entryTitle(e) }, fontSize = 12.sp, lineHeight = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(savedWord(e), fontSize = 9.5.sp, lineHeight = 11.sp, color = sub, maxLines = 1)
                }
                Text(moneyA(currency, e.amount), color = signColor(e.type), fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1,
                    modifier = Modifier.padding(start = 18.dp, end = 8.dp))
                Box(Modifier.width(1.dp).height(14.dp).background(sub.copy(alpha = 0.25f)))
                Text("Undo", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = GreenIn,
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { onUndo(listOf(e.id)) }.padding(horizontal = 8.dp, vertical = 4.dp))
            }
        }
    }
}

/**
 * "hamid 10000" says who and how much, not what happened: a small card asks with pills (Paid, Work, Received). Work and
 * Given then offer the person's last items with their rate, or two small fields for another one. Meta: ask=person|n|kinds|staff.
 */
@Composable
private fun OptionCard(message: ChatMessage, all: Collection<Entry>, onDone: (Entry?) -> Unit) {
    val parts = message.meta.removePrefix("ask=").split("|")
    val person = parts[0]
    val n = parts.getOrNull(1)?.toDoubleOrNull() ?: return
    val kinds = parts.getOrNull(2).orEmpty().split(",").filter { it.isNotBlank() }
    val staff = parts.getOrNull(3) == "staff"
    // "" asks what happened; then the picked goods kind; "+" with it types another item.
    var kind by remember(message.id) { mutableStateOf("") }
    var other by remember(message.id) { mutableStateOf(false) }
    var item by remember(message.id) { mutableStateOf("") }
    var rate by remember(message.id) { mutableStateOf("") }
    var per1000 by remember(message.id) { mutableStateOf(false) }
    val sub = MaterialTheme.colorScheme.onSurfaceVariant
    val shape = RoundedCornerShape(10.dp)
    fun save(k: String, i: String = "", r: Double = 0.0, p: Boolean = false) = onDone(Ask.entry(k, person, n, staff, System.currentTimeMillis(), i, r, p))
    Column(
        Modifier.padding(start = 8.dp).widthIn(max = 250.dp).shadow(1.dp, shape).clip(shape).background(MaterialTheme.colorScheme.surface)
            .padding(start = 3.dp, top = 3.dp, end = 7.dp, bottom = 6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(22.dp).clip(RoundedCornerShape(7.dp)).background(OrangeOwe.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) { Text("👥", fontSize = 11.sp) }
            Text(person + (if (kind.isEmpty()) "" else " · $kind"), Modifier.padding(start = 6.dp).weight(1f, fill = false), fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(fmt(n), Modifier.padding(start = 18.dp), fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
        }
        if (other) Row(Modifier.padding(start = 3.dp, top = 5.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            SmallField(item, { item = it }, if (kind == "Work") "Work name" else "Item", Modifier.weight(1f))
            SmallField(rate, { rate = it.filter { c -> c.isDigit() || c == '.' } }, "Rate", Modifier.width(54.dp), number = true)
            Text((if (per1000) "☑" else "☐") + " /1000", fontSize = 9.sp, color = sub, maxLines = 1, modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable { per1000 = !per1000 }.padding(2.dp))
        }
        Row(Modifier.padding(start = 3.dp, top = 5.dp).horizontalScroll(rememberScrollState()).padding(bottom = 2.dp, end = 2.dp), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            when {
                kind.isEmpty() -> {
                    kinds.forEach { k ->
                        val (sym, color) = when (k) { "Paid" -> "↑" to RedSpend; "Received" -> "↓" to GreenIn; "Work" -> "🛠" to OrangeOwe; else -> "📦" to OrangeOwe }
                        OptionPill(sym, k, color) { if (Ask.goods(k)) kind = k else save(k) }
                    }
                    OptionPill("", "✕", sub) { onDone(null) }
                }
                other -> {
                    OptionPill("✓", "Save", GreenIn) { rate.toDoubleOrNull()?.takeIf { it > 0 && item.isNotBlank() }?.let { save(kind, item, it, per1000) } }
                    OptionPill("", "‹", sub) { other = false }
                }
                else -> {
                    Ask.usual(kind, person, all).forEach { e ->
                        OptionPill(if (kind == "Work") "🛠" else "📦", e.item + " · " + fmt(e.rate) + (if (e.unit == "1000") "/1000" else ""), MaterialTheme.colorScheme.onSurface) { save(kind, e.item, e.rate, e.unit == "1000") }
                    }
                    OptionPill("+", "Other", MaterialTheme.colorScheme.onSurface) { other = true }
                    OptionPill("", "‹", sub) { kind = "" }
                }
            }
        }
    }
}

/** One choice in an option card: a small white pill with a soft shadow, like the mic button. */
@Composable
private fun OptionPill(symbol: String, label: String, color: Color, onClick: () -> Unit) {
    Row(
        Modifier.height(22.dp).shadow(2.dp, CircleShape).clip(CircleShape).background(MaterialTheme.colorScheme.surface).clickable { onClick() }.padding(start = 6.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        if (symbol.isNotEmpty()) Text(symbol, fontSize = 10.sp, color = color)
        Text(label, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = color, maxLines = 1)
    }
}

/** A small one-line field inside an option card. */
@Composable
private fun SmallField(value: String, onChange: (String) -> Unit, hint: String, modifier: Modifier, number: Boolean = false) {
    androidx.compose.foundation.text.BasicTextField(
        value, onChange, singleLine = true,
        textStyle = LocalTextStyle.current.copy(fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface),
        cursorBrush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.onSurface),
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = if (number) androidx.compose.ui.text.input.KeyboardType.Decimal else androidx.compose.ui.text.input.KeyboardType.Text),
        modifier = modifier.height(24.dp).fieldEdge(RoundedCornerShape(8.dp)).padding(horizontal = 7.dp),
        decorationBox = { field ->
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.CenterStart) {
                if (value.isEmpty()) Text(hint, fontSize = 10.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                field()
            }
        }
    )
}

/** The one word under a saved entry: what happened with a person (Given, Received), else its category. */
private fun savedWord(e: Entry): String = if (e.person.isBlank()) e.category.label else when (e.type) {
    Type.DIYA -> "Given"; Type.LIYA -> "Taken"; Type.WAPIS_MILA, Type.AAMDANI -> "Received"; Type.WAPIS_DIYA, Type.KHARCHA -> "Paid"
    Type.SALE -> "Sold"; Type.PURCHASE -> if (e.category == Cat.PRODUCTION) "Work" else "Bought"; Type.TRANSFER_IN, Type.TRANSFER_OUT -> "Transfer"
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
                    Text(e.note.ifBlank { entryTitle(e) }, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
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
