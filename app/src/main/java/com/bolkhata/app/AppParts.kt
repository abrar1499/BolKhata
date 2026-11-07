package com.bolkhata.app

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowUpward
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
    Column(Modifier.fillMaxWidth()) {
        if (talking) VoicePanel(voice!!)
        Box(Modifier.fillMaxWidth().then(if (navPad) Modifier.navigationBarsPadding() else Modifier)
            .padding(start = 10.dp, end = 10.dp, top = 6.dp, bottom = if (navPad) 8.dp else 6.dp)) {
            Surface(shape = RoundedCornerShape(19.dp), color = Color.Transparent, modifier = Modifier.fillMaxWidth().fieldEdge(RoundedCornerShape(19.dp))) {
                Row(Modifier.heightIn(min = 38.dp).padding(start = 12.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    BasicTextField(
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
                    // A plain line mic in a white circle with a soft shadow (no colour fill).
                    Box(
                        Modifier
                            .size(30.dp)
                            .shadow(1.dp, CircleShape)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surface)
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
                            talking -> Box(Modifier.size(12.dp).clip(RoundedCornerShape(3.dp)).background(ink))
                            busy -> CircularProgressIndicator(Modifier.size(18.dp), color = ink, strokeWidth = 2.dp)
                            text.isNotBlank() -> Icon(Icons.Filled.ArrowUpward, contentDescription = "Send", tint = ink, modifier = Modifier.size(20.dp))
                            else -> Icon(Icons.Outlined.Mic, contentDescription = "Speak", tint = ink, modifier = Modifier.size(19.dp))
                        }
                    }
                }
            }
        }
    }
}

/**
 * The voice call as the message bar shows it: on/off, what it is doing, what it heard so far, what the assistant
 * is saying ([reply]) and how loud the mic is (0..1); [toggle] connects or stops.
 */
class VoiceState(
    val on: Boolean, val status: String, val heard: String,
    val reply: String = "", val level: Float = 0f, val toggle: () -> Unit
)

/** Above the bar while voice is on: the assistant's words (or the words heard), then a sound wave across the bar. */
@Composable
private fun VoicePanel(voice: VoiceState) {
    val speaking = voice.status.startsWith("Speaking")
    val words = if (speaking || voice.heard.isBlank()) voice.reply else voice.heard
    Column(Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 6.dp)) {
        if (words.isNotBlank()) Text(
            words, fontSize = 15.sp, lineHeight = 20.sp, maxLines = 4, overflow = TextOverflow.Ellipsis,
            color = if (words == voice.reply) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 6.dp)
        )
        val strength = when {
            speaking -> 1f
            voice.status.startsWith("Listening") -> 0.25f + 0.75f * voice.level.coerceIn(0f, 1f)
            else -> 0.15f
        }
        VoiceWave(GreenDeep, strength, Modifier.fillMaxWidth().height(36.dp))
    }
}

/** Moving bars like a voice wave; taller with more [strength] (0..1). */
@Composable
private fun VoiceWave(color: Color, strength: Float, modifier: Modifier) {
    val phase by rememberInfiniteTransition(label = "wave").animateFloat(
        0f, (2 * Math.PI).toFloat(),
        infiniteRepeatable(tween(1200, easing = LinearEasing), RepeatMode.Restart), label = "phase"
    )
    Canvas(modifier) {
        val bars = 36
        val gap = size.width / bars
        val w = (gap * 0.45f).coerceAtMost(4.dp.toPx())
        for (i in 0 until bars) {
            val wave = (kotlin.math.sin(phase + i * 0.55f) * 0.5f + 0.5f) * (kotlin.math.sin(i * 0.33f) * 0.3f + 0.7f)
            val h = (size.height * (0.12f + 0.88f * wave * strength)).coerceAtLeast(w)
            val x = gap * i + gap / 2
            drawLine(color, Offset(x, (size.height - h) / 2), Offset(x, (size.height + h) / 2), w, StrokeCap.Round)
        }
    }
}

/** Header themes offered in the side menu (the app's glossy bands), in this order. */
private val MENU_THEMES = listOf("Clean", "Red", "Green", "Sky Blue", "Violet", "Navy")

/**
 * The side menu, which is also Settings: the app logo, the businesses (+ Add business), then every setting in plain
 * rows (Name, Currency, Language, Header, Background) and Backup, Trash, Version. Nothing opens another settings page.
 */
@Composable
internal fun SideMenu(
    name: String, currency: String, lang: String, dark: Boolean, photo: Boolean, themeName: String, version: String,
    businesses: List<Pair<String, String>>, current: String,
    onBusiness: (String) -> Unit, onManage: () -> Unit, onAddBusiness: (String) -> Unit,
    onName: (String) -> Unit, onCurrency: (String) -> Unit, onLang: (String) -> Unit, onTheme: (String) -> Unit,
    onGray: () -> Unit, onDark: () -> Unit, onPhoto: () -> Unit,
    onExport: () -> Unit, onImport: () -> Unit, onTrash: () -> Unit, onVersion: () -> Unit
) {
    var naming by remember { mutableStateOf(false) }
    var addingBusiness by remember { mutableStateOf(false) }
    ModalDrawerSheet(
        modifier = Modifier.width(252.dp),
        drawerContainerColor = MaterialTheme.colorScheme.background,
        drawerShape = RoundedCornerShape(0.dp),
        windowInsets = WindowInsets(0, 0, 0, 0)
    ) {
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
                    MenuRow(onClick = { if (id == current) onManage() else onBusiness(id) }) {
                        GlassLogo(20.dp)
                        Spacer(Modifier.width(10.dp))
                        Text(n, fontSize = 13.sp, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (id == current) Text("✓", fontSize = 13.sp, color = GreenIn)
                    }
                }
                MenuRow(onClick = { addingBusiness = true }, last = true) {
                    Text("+ Add business", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = EmberTop)
                }
            }
            Spacer(Modifier.height(8.dp))
            MenuCard {
                MenuRow(onClick = { naming = true }) { MenuLabel("👤", "Name"); MenuValue(name.ifBlank { "Add" }) }
                Box {
                    var open by remember { mutableStateOf(false) }
                    MenuRow(onClick = { open = true }) { MenuLabel("💱", "Currency"); MenuValue("$currency ▾") }
                    PickMenu(open, { open = false }) {
                        CURRENCIES.forEach { c ->
                            DropdownMenuItem(text = { Text(currencyLabel(c), fontSize = 13.sp, fontWeight = if (c == currency) FontWeight.SemiBold else FontWeight.Normal) },
                                onClick = { onCurrency(c); open = false })
                        }
                    }
                }
                Box {
                    var open by remember { mutableStateOf(false) }
                    val langs = listOf("" to "Auto", "hi-IN" to "Roman Urdu", "en-US" to "English", "ur-PK" to "Urdu")
                    MenuRow(onClick = { open = true }) { MenuLabel("🌐", "Language"); MenuValue((langs.firstOrNull { it.first == lang }?.second ?: "Auto") + " ▾") }
                    PickMenu(open, { open = false }) {
                        langs.forEach { (code, label) -> DropdownMenuItem(text = { Text(label, fontSize = 13.sp) }, onClick = { onLang(code); open = false }) }
                    }
                }
                MenuRow(onClick = null) {
                    MenuLabel("🖌", "Header")
                    MENU_THEMES.mapNotNull { n -> HEADER_THEMES.firstOrNull { it.name == n } }.forEach { t ->
                        Dot(Brush.linearGradient(listOf(t.c1, t.c2, t.c3)), themeName == t.name) { onTheme(t.name) }
                    }
                }
                MenuRow(onClick = null, last = true) {
                    MenuLabel("🎨", "Background")
                    Dot(SolidColor(Color(0xFFEDEDED)), !dark && !photo, onGray)
                    Dot(SolidColor(Color(0xFF2A2F36)), dark && !photo, onDark)
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
                MenuRow(onClick = onTrash) { MenuLabel("🗑", "Trash"); MenuValue("›") }
                MenuRow(onClick = onVersion, last = true) { MenuLabel("⬆️", "Version"); MenuValue(version) }
            }
        }
    }
    if (addingBusiness) NamePopup("New business", "Business name", onDismiss = { addingBusiness = false }) { addingBusiness = false; onAddBusiness(it) }
    if (naming) {
        var text by remember { mutableStateOf(name) }
        AlertDialog(
            onDismissRequest = { naming = false },
            title = { Text("Your name") },
            text = { OutlinedTextField(text, { text = it }, singleLine = true) },
            confirmButton = { TextButton(onClick = { onName(text.trim()); naming = false }) { Text("Save") } },
            dismissButton = { TextButton(onClick = { naming = false }) { Text("Cancel") } }
        )
    }
}

/** A white rounded card of side-menu rows. */
@Composable
private fun MenuCard(content: @Composable ColumnScope.() -> Unit) =
    Column(Modifier.padding(horizontal = 10.dp).fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surface), content = content)

/** One side-menu row with a faint line under it (none under the [last]). */
@Composable
private fun MenuRow(onClick: (() -> Unit)?, last: Boolean = false, content: @Composable RowScope.() -> Unit) {
    Row(
        Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier).padding(horizontal = 12.dp, vertical = 9.dp),
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
