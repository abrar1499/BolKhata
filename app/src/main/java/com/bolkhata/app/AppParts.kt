package com.bolkhata.app

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.background
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.border
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.FormatListBulleted
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.unit.sp

// Parts of the main screen kept out of MainActivity: the chat input bar, the side menu and the "what kind of entry?" dialog.

/**
 * The message bar above the tabs on Chat (as in the mock-up): one 38-high rounded field on the page, the text and a
 * white round mic that sends what is typed (or starts speaking when empty) and shows a spinner while the app is answering.
 */
@Composable
internal fun InputBar(
    onSend: (String) -> Unit,
    onMic: () -> Unit,
    placeholder: String = "Type here…",
    busy: Boolean = false,
    voice: VoiceState? = null,
    /** False when a bar below (the bottom tabs) already keeps clear of the system navigation. */
    navPad: Boolean = true
) {
    var text by rememberSaveable { mutableStateOf("") }
    fun send() {
        val t = text.trim()
        if (t.isNotEmpty() && !busy) {
            text = ""
            onSend(t)
        }
    }
    val ink = MaterialTheme.colorScheme.onSurface
    val talking = voice?.on == true
    val hint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
    // The field sits on the page itself (no white strip behind it), 6 above the tabs.
    // While the mic is on: the seconds so far, shown in the bar itself.
    var seconds by remember(talking) { mutableIntStateOf(0) }
    LaunchedEffect(talking) { while (talking) { kotlinx.coroutines.delay(1000); seconds++ } }
    Column(Modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().then(if (navPad) Modifier.navigationBarsPadding() else Modifier)
            .padding(start = 10.dp, end = 10.dp, top = 6.dp, bottom = if (navPad) 8.dp else 6.dp)) {
            Surface(shape = RoundedCornerShape(19.dp), color = Color.Transparent, modifier = Modifier.fillMaxWidth().fieldEdge(RoundedCornerShape(19.dp))
                .then(if (talking) Modifier.border(1.dp, RedSpend, RoundedCornerShape(19.dp)) else Modifier)) {
                Row(Modifier.heightIn(min = 38.dp).padding(start = 12.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    // Recording or listening: a red line with the time (or the words heard so far); the button sends.
                    if (talking) Text(
                        "● " + if (voice!!.live) voice.status + "…"
                            else if (voice.heard.isNotBlank() && voice.heard != "tap to send") voice.heard
                            else voice.status + " " + seconds / 60 + ":" + (seconds % 60).toString().padStart(2, '0') + " · tap to send",
                        color = RedSpend, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f).padding(horizontal = 4.dp, vertical = 7.dp)
                    ) else BasicTextField(
                        value = text,
                        onValueChange = { text = it },
                        modifier = Modifier.weight(1f).padding(horizontal = 4.dp, vertical = 7.dp),
                        maxLines = 4,
                        textStyle = LocalTextStyle.current.copy(fontSize = 13.sp, color = ink),
                        cursorBrush = SolidColor(GreenDeep),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = { send() }),
                        decorationBox = { field ->
                            Box(contentAlignment = Alignment.CenterStart) {
                                if (text.isEmpty()) Text(placeholder, fontSize = 13.sp, color = hint, maxLines = 1)
                                field()
                            }
                        }
                    )
                    if (talking && voice!!.live) {
                        LiveWave(voice.level, voice.status == "Working")
                        Spacer(Modifier.width(8.dp))
                    }
                    // A plain line mic in a white circle with a soft shadow (no colour fill).
                    Box(
                        Modifier
                            .size(30.dp)
                            .shadow(1.dp, CircleShape)
                            .clip(CircleShape)
                            .background(if (talking && !voice!!.live) RedSpend else MaterialTheme.colorScheme.surface)
                            .clickable(enabled = talking || !busy) {
                                when {
                                    talking -> voice!!.toggle()
                                    text.isNotBlank() -> send()
                                    else -> onMic()
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        when {
                            // Live voice: a square to stop; a recording: the arrow sends it.
                            talking && voice!!.live -> Box(Modifier.size(11.dp).clip(RoundedCornerShape(3.dp)).background(ink))
                            talking -> Icon(Icons.Filled.ArrowUpward, contentDescription = "Send", tint = Color.White, modifier = Modifier.size(18.dp))
                            busy -> CircularProgressIndicator(Modifier.size(18.dp), color = ink, strokeWidth = 2.dp)
                            text.isNotBlank() -> Icon(Icons.Filled.ArrowUpward, contentDescription = "Send", tint = ink, modifier = Modifier.size(20.dp))
                            else -> Icon(Icons.Outlined.Mic, contentDescription = "Speak", tint = ink, modifier = Modifier.size(19.dp))
                        }
                    }
                }
            }
            // The ring round the button swells with the voice. It reaches over the field's edge, so it is drawn on top of it.
            if (talking && voice!!.live) {
                val swell by androidx.compose.animation.core.animateFloatAsState(voice.level.coerceIn(0f, 1f), androidx.compose.animation.core.tween(140), label = "ring")
                val working = voice.status == "Working"
                Box(Modifier.align(Alignment.CenterEnd).padding(end = 1.dp).size(36.dp)
                    .graphicsLayer { val s = 1f + swell * 0.16f; scaleX = s; scaleY = s; alpha = if (working) 0.3f else 0.3f + swell * 0.6f }
                    .border(2.dp, GreenIn, CircleShape))
            }
        }
    }
}

/**
 * The mic as the message bar shows it: on/off, what it is doing ("Recording", "Listening"), what it heard so far,
 * and [toggle], which ends it and sends.
 */
class VoiceState(
    val on: Boolean, val status: String, val heard: String,
    val reply: String = "", val level: Float = 0f,
    /** Live voice: the bar says Listening / Working, with a small wave and a ring round the button; a tap stops it. */
    val live: Boolean = false, val toggle: () -> Unit
)

/** Five small green bars in the bar while live voice is on: they rise with the voice, and hop in turn while the answer is made. */
@Composable
private fun LiveWave(level: Float, working: Boolean) {
    val turn by androidx.compose.animation.core.rememberInfiniteTransition(label = "live").animateFloat(
        0f, 1f, androidx.compose.animation.core.infiniteRepeatable(androidx.compose.animation.core.tween(700, easing = androidx.compose.animation.core.LinearEasing)), label = "turn")
    Row(Modifier.height(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.5.dp)) {
        listOf(6f, 12f, 8f, 13f, 5f).forEachIndexed { i, most ->
            val tall by androidx.compose.animation.core.animateDpAsState(
                if (working) 3.dp else (3f + level.coerceIn(0f, 1f) * most).dp, androidx.compose.animation.core.tween(140), label = "bar")
            val hop = if (working) kotlin.math.sin((turn - i * 0.14f) * 2 * Math.PI).toFloat().coerceAtLeast(0f) * 3f else 0f
            Box(Modifier.offset(y = (-hop).dp).width(2.5.dp).height(tall).clip(RoundedCornerShape(2.dp)).background(GreenIn))
        }
    }
}

/** Header themes offered in the side menu (the app's glossy bands), in this order. */
private val MENU_THEMES = listOf("Clean", "Red", "Green", "Navy")

/**
 * The side menu, which is also Settings: the app logo, the businesses (+ Add business), then every setting in plain
 * rows (Currency, Language, Header, Background) and Backup, Trash, Version. Nothing opens another settings page.
 */
@Composable
internal fun SideMenu(
    currency: String, lang: String, dark: Boolean, photo: Boolean, themeName: String, version: String,
    businesses: List<Pair<String, String>>, current: String,
    onBusiness: (String) -> Unit,
    /** + Add business: opens the business page for a new one. */
    onAddBusiness: () -> Unit,
    /** The open business's own page (details and card). */
    onBusinessPage: () -> Unit,
    onRenameBusiness: (id: String, name: String) -> Unit, onDeleteBusiness: (id: String) -> Unit,
    onCurrency: (String) -> Unit, onLang: (String) -> Unit, onTheme: (String) -> Unit,
    page: Long, onPage: (Long) -> Unit, onDark: (Long) -> Unit, onPhoto: () -> Unit,
    onExport: () -> Unit, onImport: () -> Unit, onTrash: () -> Unit, onVersion: () -> Unit,
    /** The Gmail synced with Google Drive ("" when not signed in) and how the last save went. */
    driveEmail: String = "", driveStatus: String = "",
    /** Signing in and fetching the data: the Gmail once known; null when not. */
    driveBusy: String? = null,
    /** Test feature: the AI key row (null: not in this build). */
    ai: AiKeys? = null, onAiOpen: () -> Unit = {}, aiModels: (String) -> List<String> = { emptyList() },
    onAiSave: (name: String, key: String, model: String) -> Unit = { _, _, _ -> }, onAiSwitch: (name: String, on: Boolean) -> Unit = { _, _ -> },
    onSignIn: () -> Unit = {}, onSyncNow: () -> Unit = {}, onSignOut: () -> Unit = {},
    /** 20 quick taps on the menu's empty space. */
    onSecret: () -> Unit = {}
) {
    // Taps on the empty space and when the last one was; a pause of over a second starts the count again.
    val secret = remember { longArrayOf(0, 0) }
    var driveOpen by remember { mutableStateOf(false) }
    var aiOpen by remember { mutableStateOf(false) }
    if (aiOpen && ai != null) AiKeysSheet(ai, aiModels, onAiSave, onAiSwitch) { aiOpen = false }
    if (driveOpen) {
        // The app's own popup: the Gmail in a white box, then Sign out and Sync now.
        AppPopup("Google Drive", "Sync now", onDismiss = { driveOpen = false }, onAction = { driveOpen = false; onSyncNow() },
            second = "Sign out", onSecond = { driveOpen = false; onSignOut() }) {
            DriveRow(driveEmail, driveStatus, Modifier.padding(top = 10.dp).fillMaxWidth().clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surface).padding(horizontal = 12.dp, vertical = 10.dp))
        }
    }
    var bizMenu by remember { mutableStateOf<Pair<String, String>?>(null) }
    var bizRename by remember { mutableStateOf<Pair<String, String>?>(null) }
    var bizDelete by remember { mutableStateOf<Pair<String, String>?>(null) }
    bizMenu?.let { (id, n) ->
        // The first business holds the app's main data and stays; it can still be renamed.
        ActionMenu(
            title = n, subtitle = if (id.isEmpty()) "Main business" else "Business", amount = "", amountColor = Color.Unspecified,
            actions = listOfNotNull(
                MenuAction("Rename", Icons.Filled.DriveFileRenameOutline) { bizMenu = null; bizRename = id to n },
                if (id.isEmpty()) null else MenuAction("Delete", Icons.Filled.Delete, danger = true) { bizMenu = null; bizDelete = id to n }
            ),
            onDismiss = { bizMenu = null }
        )
    }
    bizRename?.let { (id, n) ->
        RenameDialog("Rename business", n, onRename = { onRenameBusiness(id, it.trim()); bizRename = null }, onDismiss = { bizRename = null })
    }
    bizDelete?.let { (id, n) ->
        AppAlert(
            onDismissRequest = { bizDelete = null },
            title = { Text("Delete \"$n\"?") },
            text = { Text("All its entries are deleted too. This can't be undone.") },
            confirmButton = { TextButton(onClick = { bizDelete = null; onDeleteBusiness(id) }) { Text("Delete", color = RedSpend) } },
            dismissButton = { TextButton(onClick = { bizDelete = null }) { Text("Cancel") } }
        )
    }
    ModalDrawerSheet(
        modifier = Modifier.width(252.dp),
        drawerContainerColor = MaterialTheme.colorScheme.background,
        drawerShape = RoundedCornerShape(0.dp),
        windowInsets = WindowInsets(0, 0, 0, 0)
    ) {
        Box(Modifier.fillMaxSize().clickable(remember { MutableInteractionSource() }, indication = null) {
            val now = System.currentTimeMillis()
            secret[0] = if (now - secret[1] < 1000) secret[0] + 1 else 1
            secret[1] = now
            if (secret[0] >= 20) { secret[0] = 0; onSecret() }
        }) {
        Column(Modifier.verticalScroll(rememberScrollState()).statusBarsPadding().padding(bottom = 16.dp)) {
            Row(Modifier.padding(start = 14.dp, end = 14.dp, top = 16.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                GlassLogo(34.dp)
                Spacer(Modifier.width(10.dp))
                Column {
                    Text("Smart Expense", fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
                    Text("Speak it. Track it.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            MenuCard {
                businesses.forEach { (id, n) ->
                    // Tap opens the business; long-press: Rename / Delete, the same menu as accounts.
                    // Tap: the open business shows its page; another one opens.
                    MenuRow(onClick = { if (id == current) onBusinessPage() else onBusiness(id) }, onLong = { bizMenu = id to n }) {
                        GlassLogo(20.dp)
                        Spacer(Modifier.width(10.dp))
                        Text(n, fontSize = 13.sp, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (id == current) Text("✓", fontSize = 13.sp, color = GreenIn)
                    }
                }
                MenuRow(onClick = onAddBusiness, last = true) {
                    Text("+ Add business", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = EmberTop)
                }
            }
            Spacer(Modifier.height(8.dp))
            MenuCard {
                if (driveBusy != null) {
                    // Fetching: a ring turns round the Gmail's letter and a thin line runs under the row.
                    MenuRow(onClick = null, last = true) { DriveRow(driveBusy.ifBlank { "Signing in…" }, "", Modifier.weight(1f), busy = true) }
                    LinearProgressIndicator(Modifier.fillMaxWidth().height(3.dp), color = EmberTop, trackColor = Color.Transparent)
                } else MenuRow(onClick = { if (driveEmail.isBlank()) onSignIn() else driveOpen = true }, last = true) {
                    DriveRow(driveEmail, driveStatus, Modifier.weight(1f))
                    MenuValue("›")
                }
            }
            Spacer(Modifier.height(8.dp))
            MenuCard {
                Box {
                    var open by remember { mutableStateOf(false) }
                    MenuRow(onClick = { open = true }) { MenuLabel("💱", "Currency"); MenuValue("$currency ▾") }
                    val all = remember { CURRENCIES.map { it to currencyName(it) } }
                    CompactPicker(open, all, currency, search = true, onDismiss = { open = false }) { onCurrency(it); open = false }
                }
                Box {
                    var open by remember { mutableStateOf(false) }
                    val langs = listOf("" to "Auto", "hi-IN" to "Roman Urdu", "en-US" to "English", "ur-PK" to "Urdu")
                    MenuRow(onClick = { open = true }) { MenuLabel("🌐", "Language"); MenuValue((langs.firstOrNull { it.first == lang }?.second ?: "Auto") + " ▾") }
                    // The language's name shows (the code means nothing to people).
                    CompactPicker(open, langs.map { it.second to "" }, langs.firstOrNull { it.first == lang }?.second ?: "Auto",
                        search = false, onDismiss = { open = false }) { picked -> onLang(langs.first { it.second == picked }.first); open = false }
                }
                MenuRow(onClick = null) {
                    MenuLabel("🖌", "Header")
                    MENU_THEMES.mapNotNull { n -> HEADER_THEMES.firstOrNull { it.name == n } }.forEach { t ->
                        Dot(Brush.linearGradient(listOf(t.c1, t.c2, t.c3)), themeName == t.name) { onTheme(t.name) }
                    }
                }
                MenuRow(onClick = null, last = true) {
                    // Like Header: the label, the light colours, the dull dark ones, then a photo.
                    MenuLabel("🎨", "Background")
                    PAGE_COLORS.forEach { c -> Dot(SolidColor(Color(c)), !dark && !photo && page == c) { onPage(c) } }
                    DARK_PAGES.forEach { c -> Dot(SolidColor(Color(c)), dark && !photo && (page == c || (c == DARK_PAGES[0] && page !in DARK_PAGES))) { onDark(c) } }
                    Text("🖼", fontSize = 16.sp, modifier = Modifier.padding(start = 5.dp).clip(CircleShape).clickable(onClick = onPhoto)
                                .then(if (photo) Modifier.border(2.dp, EmberTop, CircleShape) else Modifier).padding(2.dp))
                }
            }
            Spacer(Modifier.height(8.dp))
            MenuCard {
                Box {
                    var open by remember { mutableStateOf(false) }
                    MenuRow(onClick = { open = true }) { MenuLabel("💾", "Backup"); MenuValue("Export · Import") }
                    PickMenu(open, { open = false }) {
                        DropdownMenuItem(text = { Text("Export data") }, onClick = { open = false; onExport() })
                        DropdownMenuItem(text = { Text("Import data") }, onClick = { open = false; onImport() })
                    }
                }
                if (ai != null) MenuRow(onClick = { onAiOpen(); aiOpen = true }) { MenuLabel("🤖", "AI keys"); MenuValue(ai.live().firstOrNull()?.name ?: "Off") }
                MenuRow(onClick = onTrash) { MenuLabel("🗑", "Trash"); MenuValue("›") }
                MenuRow(onClick = onVersion, last = true) { MenuLabel("⬆️", "Version"); MenuValue(version) }
            }
        }
        }
    }
}

/** The AI keys as the side menu shows them: each provider's box, the one that answered last and why others did not. */
class AiKeys(val boxes: Map<String, AiBox>, val answered: String, val failed: Map<String, String>) {
    /** The providers turned on with a key, best first: the order they answer in. */
    fun live() = Ai.ALL.filter { p -> boxes[p.name]?.let { it.on && it.key.isNotBlank() } == true }
}

/** Each provider's colour and a few words on what it costs. */
private val aiLook = mapOf(
    "Claude" to (Color(0xFFD97757) to "Paid · best"), "ChatGPT" to (Color(0xFF10A37F) to "Paid"),
    "Gemini" to (Color(0xFF4285F4) to "Free with limits"), "Groq" to (Color(0xFFF55036) to "Free · fast"),
    "Cerebras" to (Color(0xFF111111) to "Free with limits"), "Mistral" to (Color(0xFFFA520F) to "Free with limits"),
    "OpenRouter" to (Color(0xFF6466F1) to "Free models + paid")
)

/** The AI keys page: one box per provider, best first; the ones turned on answer in that order, each handing over to the next. */
@Composable
private fun AiKeysSheet(ai: AiKeys, models: (String) -> List<String>, onSave: (String, String, String) -> Unit, onSwitch: (String, Boolean) -> Unit, onClose: () -> Unit) {
    val sub = MaterialTheme.colorScheme.onSurfaceVariant
    val live = ai.live()
    val now = live.firstOrNull { it.name == ai.answered } ?: live.firstOrNull { it.name !in ai.failed }
    Dialog(onDismissRequest = onClose) {
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(LocalPage.current).padding(vertical = 14.dp)) {
            Row(Modifier.padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(34.dp).shadow(3.dp, CircleShape).clip(CircleShape).background(MaterialTheme.colorScheme.surface).clickable { onClose() },
                    contentAlignment = Alignment.Center) { Text("‹", fontSize = 16.sp) }
                Text("AI keys", fontSize = 17.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 10.dp))
            }
            Text("Put a key in each box you have. Best first: the best one turned on answers. When it is busy or out of credit, the next best answers by itself.",
                fontSize = 11.sp, lineHeight = 15.sp, color = sub, modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 10.dp))
            // Who answers, in turn; the one answering now in green.
            Row(Modifier.padding(start = 12.dp, end = 12.dp, bottom = 10.dp).fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surface)
                .horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (live.isEmpty()) Text("Turn on a key to use the AI", fontSize = 11.sp, color = sub)
                live.forEachIndexed { i, p ->
                    if (i > 0) Text("→", fontSize = 11.sp, modifier = Modifier.padding(horizontal = 5.dp))
                    Text(p.name, fontSize = 11.sp, fontWeight = FontWeight.Medium, color = if (p == now) GreenIn else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.clip(RoundedCornerShape(7.dp)).background(if (p == now) GreenIn.copy(alpha = 0.1f) else LocalPage.current).padding(horizontal = 7.dp, vertical = 2.dp))
                }
            }
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Ai.ALL.forEachIndexed { i, p ->
                    val box = ai.boxes[p.name] ?: AiBox()
                    val why = ai.failed[p.name].orEmpty().lowercase()
                    val status = when {
                        box.key.isBlank() -> "No key yet" to sub
                        !box.on -> "Off · key kept" to sub
                        p == now -> "● Answering now" to GreenIn
                        why.isBlank() -> "● Ready · answers if the ones above can't" to sub
                        else -> (when {
                            why.startsWith("429") || "rate" in why || "busy" in why -> "● Busy for a minute"
                            "credit" in why || why.startsWith("402") -> "● Out of credit"
                            why.startsWith("401") || why.startsWith("403") -> "● Key not working"
                            else -> "● Did not answer"
                        } + if (live.indexOf(p) < live.size - 1) " · next one answers" else "") to OrangeOwe
                    }
                    AiBoxCard(i + 1, p, box, status, models(p.name), { k, m -> onSave(p.name, k, m) }) { onSwitch(p.name, it) }
                }
            }
        }
    }
}

/** One provider's box: number, colour square, name and cost, a switch; with a key, the key field, the model to pick, Save and how it is doing. */
@Composable
private fun AiBoxCard(n: Int, p: Ai.Provider, box: AiBox, status: Pair<String, Color>, models: List<String>, onSave: (String, String) -> Unit, onSwitch: (Boolean) -> Unit) {
    val sub = MaterialTheme.colorScheme.onSurfaceVariant
    var open by remember { mutableStateOf(false) }
    var key by remember { mutableStateOf("") }
    var model by remember(box.model) { mutableStateOf(box.model.ifBlank { p.model }) }
    var pick by remember { mutableStateOf(false) }
    val shown = open || box.key.isNotBlank()
    val on = box.on && box.key.isNotBlank()
    fun label(m: String) = m + if (Ai.free(p, m)) " · Free" else " · Paid"
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surface)
        .clickable(enabled = !shown) { open = true }.padding(horizontal = 12.dp, vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(18.dp).clip(CircleShape).background(LocalPage.current), contentAlignment = Alignment.Center) {
                Text("$n", fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = sub)
            }
            Box(Modifier.padding(start = 8.dp).size(26.dp).clip(RoundedCornerShape(8.dp)).background(aiLook[p.name]?.first ?: EmberTop), contentAlignment = Alignment.Center) {
                Text(p.name.take(1), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
            Column(Modifier.padding(start = 8.dp).weight(1f)) {
                Text(p.name, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Text(aiLook[p.name]?.second.orEmpty(), fontSize = 10.sp, color = sub)
            }
            // The switch: without a key it opens the box for one.
            Box(Modifier.size(34.dp, 20.dp).clip(RoundedCornerShape(10.dp)).background(if (on) GreenIn else Color(0xFFD6D2CC))
                .clickable { if (box.key.isBlank()) open = !open else onSwitch(!box.on) }) {
                Box(Modifier.align(if (on) Alignment.CenterEnd else Alignment.CenterStart).padding(2.dp).size(16.dp).shadow(1.dp, CircleShape).clip(CircleShape).background(Color.White))
            }
        }
        if (shown) {
            WellField(key, { key = it.trim() }, if (box.key.isBlank()) "Paste ${p.name} key" else box.key.take(6) + "••••••" + box.key.takeLast(3))
            Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) {
                    Row(Modifier.fillMaxWidth().fieldEdge(RoundedCornerShape(12.dp)).clickable { pick = true }.padding(horizontal = 12.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Text(label(model), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        Text("▾", fontSize = 11.sp, color = sub)
                    }
                    PickMenu(pick, { pick = false }) {
                        (listOf(model) + models).distinct().forEach { m ->
                            DropdownMenuItem(text = { Text(label(m), fontSize = 13.sp) }, onClick = { pick = false; model = m })
                        }
                    }
                }
                Text("Save", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF1D1D1F), modifier = Modifier.padding(start = 6.dp).shadow(3.dp, RoundedCornerShape(16.dp))
                    .clip(RoundedCornerShape(16.dp)).background(Color.White).clickable { onSave(key, model); key = "" }.padding(horizontal = 14.dp, vertical = 8.dp))
            }
            Text(status.first, fontSize = 10.sp, color = status.second, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

/** The Google sign-in line: the G and "Sign in with Google", or the Gmail's first letter, the Gmail and how syncing went ([busy]: fetching). */
@Composable
private fun DriveRow(email: String, status: String, modifier: Modifier = Modifier, busy: Boolean = false) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        // The ring is drawn a little outside the 30 circle without making the row taller.
        Box(Modifier.size(30.dp), contentAlignment = Alignment.Center) {
            if (busy) CircularProgressIndicator(Modifier.requiredSize(38.dp), color = EmberTop, strokeWidth = 2.5.dp)
            Box(
                Modifier.size(30.dp).shadow(if (email.isBlank()) 1.dp else 0.dp, CircleShape).clip(CircleShape)
                    .background(if (email.isBlank()) Color.White else Color(0xFF7C3AED)),
                contentAlignment = Alignment.Center
            ) {
                if (email.isBlank()) Text("G", fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF4285F4))
                else Text(email.take(1).uppercase(), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
        }
        Spacer(Modifier.width(10.dp))
        Column {
            Text(email.ifBlank { "Sign in with Google" }, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                // Not signed in with a [status]: why the last sign-in failed, in red and in full.
                if (busy) "Getting your data…" else if (email.isBlank()) status.ifBlank { "Keep your data on every phone" } else status.ifBlank { "✓ Synced" },
                fontSize = 10.sp, lineHeight = 13.sp, maxLines = if (email.isBlank()) 3 else 1,
                color = if (busy) EmberTop else if (email.isBlank() && status.isNotBlank()) RedSpend
                    else if (email.isNotBlank() && !status.startsWith("Not")) GreenIn else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** A white rounded card of side-menu rows. */
@Composable
private fun MenuCard(content: @Composable ColumnScope.() -> Unit) =
    Column(Modifier.padding(horizontal = 10.dp).fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surface), content = content)

/** One side-menu row with a faint line under it (none under the [last]). */
@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun MenuRow(onClick: (() -> Unit)?, last: Boolean = false, onLong: (() -> Unit)? = null, content: @Composable RowScope.() -> Unit) {
    Row(
        Modifier.fillMaxWidth().then(
            if (onLong != null) Modifier.combinedClickable(onLongClick = onLong) { onClick?.invoke() }
            else if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
        ).padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically, content = content
    )
    if (!last) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
}

@Composable
private fun RowScope.MenuLabel(icon: String, label: String) {
    Text("$icon $label", fontSize = 13.sp, maxLines = 1, modifier = Modifier.weight(1f))
}

@Composable
private fun MenuValue(value: String) {
    Text(value, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
}

/** A small round colour to pick (header theme or background), ringed when chosen. */
@Composable
private fun Dot(fill: Brush, on: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.padding(start = 4.dp).size(18.dp)
            .then(if (on) Modifier.border(2.dp, EmberTop, CircleShape).padding(3.dp) else Modifier.border(1.dp, MaterialTheme.colorScheme.outline, CircleShape))
            .clip(CircleShape).background(fill).clickable(onClick = onClick)
    )
}

/** A friendly one-tap question for things the app did not understand. */
@Composable
internal fun AskDialog(entry: Entry, currency: String, onPick: (Entry) -> Unit, onMore: () -> Unit, onSkip: () -> Unit) {
    val who = entry.person.ifBlank { "Someone" }
    val goods = entry.qty > 0
    // Goods said without a price ("1000 eint Ashraf ko di"): ask the rate, the total is worked out.
    val needRate = goods && entry.amount <= 0
    val knownTrade = isTrade(entry.type) && (entry.category == Cat.SALES || entry.category == Cat.STOCK)
    var rate by remember(entry.id) { mutableStateOf("") }
    val rateValue = rate.toDoubleOrNull() ?: 0.0
    val total = if (needRate) Parser.total(entry.qty, rateValue, entry.item + " " + entry.note) else entry.amount
    val ready = total > 0
    fun trade(t: Type) = entry.copy(
        type = t, amount = total, category = if (t == Type.SALE) Cat.SALES else Cat.STOCK,
        item = entry.item.ifBlank { if (t == Type.SALE) "Sale" else "Purchase" },
        rate = if (needRate) rateValue else entry.rate
    )
    Dialog(onDismissRequest = onSkip) {
        Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surface, shadowElevation = 12.dp) {
            Column(Modifier.padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier.size(56.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) { Text(if (goods) "📦" else "🤔", fontSize = 28.sp) }
                Spacer(Modifier.height(12.dp))
                if (goods) {
                    Text(qtyText(entry), fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
                    if (!needRate) Text(moneyA(currency, entry.amount), fontSize = 18.sp, fontWeight = FontWeight.Bold)
                } else {
                    Text(moneyA(currency, entry.amount), fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
                }
                Text(
                    "\"${entry.note}\"",
                    color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                if (needRate) {
                    Spacer(Modifier.height(12.dp))
                    val perK = Parser.total(1000.0, 1.0, entry.item) == 1.0
                    OutlinedTextField(
                        value = rate,
                        onValueChange = { v -> rate = v.filter { it.isDigit() || it == '.' } },
                        label = { Text(if (perK) "Rate per 1000 ($currency)" else "Rate per item ($currency)", fontSize = 12.sp) },
                        keyboardOptions = KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal),
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        if (ready) "Total: " + money(currency, total) else "What is the rate?",
                        fontWeight = FontWeight.SemiBold, fontSize = 15.sp, modifier = Modifier.padding(top = 6.dp)
                    )
                } else {
                    Spacer(Modifier.height(6.dp))
                    Text("What is this?", fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
                }
                Spacer(Modifier.height(14.dp))
                when {
                    knownTrade -> Button(
                        onClick = { onPick(trade(entry.type)) },
                        enabled = ready,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth().height(48.dp)
                    ) {
                        Text(
                            (if (entry.type == Type.SALE) "Save · sold" else "Save · bought") +
                                (if (entry.person.isNotBlank()) (if (entry.type == Type.SALE) " to " else " from ") + entry.person else ""),
                            fontWeight = FontWeight.Bold
                        )
                    }
                    goods -> Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        ChoiceTile("Bought", if (entry.person.isBlank()) "Purchase" else "from $who", "📦", RedSpend, Modifier.weight(1f)) {
                            if (ready) onPick(trade(Type.PURCHASE))
                        }
                        ChoiceTile("Sold", if (entry.person.isBlank()) "Sale" else "to $who", "🧾", GreenIn, Modifier.weight(1f)) {
                            if (ready) onPick(trade(Type.SALE))
                        }
                    }
                    else -> {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            ChoiceTile("Cash Out", "I spent it", "−", RedSpend, Modifier.weight(1f)) {
                                onPick(entry.copy(type = Type.KHARCHA, category = Cat.OTHER, item = "Other", person = ""))
                            }
                            ChoiceTile("Cash In", "I got paid", "+", GreenIn, Modifier.weight(1f)) {
                                onPick(entry.copy(type = Type.AAMDANI, category = Cat.TANKHWAH, item = "Cash In", person = ""))
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            ChoiceTile("To give", "I will give $who", "💸", OrangeOwe, Modifier.weight(1f)) {
                                onPick(entry.copy(type = Type.LIYA, category = Cat.UDHAAR, item = "Loans", person = who))
                            }
                            ChoiceTile("To get", "$who will give me", "🤝", GreenIn, Modifier.weight(1f)) {
                                onPick(entry.copy(type = Type.DIYA, category = Cat.UDHAAR, item = "Loans", person = who))
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row {
                    TextButton(onClick = onMore) { Text("More options") }
                    TextButton(onClick = onSkip) { Text("Skip") }
                }
            }
        }
    }
}

@Composable
private fun ChoiceTile(title: String, sub: String, glyph: String, color: Color, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier
            .clip(RoundedCornerShape(20.dp))
            .background(color.copy(alpha = 0.14f))
            .clickable { onClick() }
            .padding(vertical = 16.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(glyph, fontSize = 22.sp, color = color, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text(title, fontWeight = FontWeight.Bold, color = color, fontSize = 16.sp)
        Text(sub, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
    }
}
