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
 * The message bar at the bottom of Home and Chat, in the style of ChatGPT: a floating white pill with a thin +,
 * the text and a round green button with a mic that sends what is typed (or starts speaking when empty) and
 * shows a spinner while the app is answering. A soft fade above it keeps the list from being cut off sharply.
 */
@Composable
internal fun InputBar(
    onSend: (String) -> Unit,
    onMic: () -> Unit,
    placeholder: String = "Type here…",
    onAttach: (() -> Unit)? = null,
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
    val bg = MaterialTheme.colorScheme.background
    val ink = MaterialTheme.colorScheme.onSurface
    val talking = voice?.on == true
    val hint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
    // One white strip with the tabs below it (no floating pill, no shadow, so its edges stay crisp):
    // rounded at the top, the field inside on the page colour.
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)).background(MaterialTheme.colorScheme.surface)) {
        if (talking) VoicePanel(voice!!)
        Box(Modifier.fillMaxWidth().then(if (navPad) Modifier.navigationBarsPadding() else Modifier)
            .padding(start = 10.dp, end = 10.dp, top = 8.dp, bottom = if (navPad) 8.dp else 0.dp)) {
            Surface(shape = RoundedCornerShape(12.dp), color = bg, modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.heightIn(min = 44.dp).padding(start = 2.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (onAttach != null) {
                        Box(Modifier.size(40.dp).clip(CircleShape).clickable(onClick = onAttach), contentAlignment = Alignment.Center) {
                            ThinPlus(ink)
                        }
                    } else Spacer(Modifier.width(12.dp))
                    BasicTextField(
                        value = text,
                        onValueChange = { text = it },
                        modifier = Modifier.weight(1f).padding(horizontal = 4.dp, vertical = 10.dp),
                        maxLines = 4,
                        textStyle = LocalTextStyle.current.copy(fontSize = 16.sp, color = ink),
                        cursorBrush = SolidColor(GreenDeep),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = { send() }),
                        decorationBox = { field ->
                            Box(contentAlignment = Alignment.CenterStart) {
                                if (text.isEmpty()) Text(placeholder, fontSize = 16.sp, color = hint, maxLines = 1)
                                field()
                            }
                        }
                    )
                    // A plain line mic in a white circle (no colour fill).
                    Box(
                        Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surface)
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
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

/** A thin "+" like ChatGPT's attach button. */
@Composable
fun ThinPlus(color: Color) {
    Canvas(Modifier.size(20.dp)) {
        val w = 1.8.dp.toPx()
        drawLine(color, Offset(size.width / 2, 1.dp.toPx()), Offset(size.width / 2, size.height - 1.dp.toPx()), w, StrokeCap.Round)
        drawLine(color, Offset(1.dp.toPx(), size.height / 2), Offset(size.width - 1.dp.toPx(), size.height / 2), w, StrokeCap.Round)
    }
}

/** The side menu in the same plain style as Settings: who you are, then simple rows with line icons. */
@Composable
internal fun DrawerMenu(tab: Tab, name: String, currency: String, onCurrency: (String) -> Unit, onPick: (Tab) -> Unit) {
    var currencyOpen by remember { mutableStateOf(false) }
    ModalDrawerSheet(
        modifier = Modifier.width(240.dp),
        drawerContainerColor = MaterialTheme.colorScheme.background,
        drawerShape = RoundedCornerShape(topEnd = 24.dp, bottomEnd = 24.dp),
        windowInsets = WindowInsets(0, 0, 0, 0)
    ) {
        // The header in the chosen theme's glass colours, with the app logo.
        val header = LocalHeader.current
        Row(
            Modifier.fillMaxWidth().drawBehind { drawGlass(bandColors(header), size.height) }.statusBarsPadding()
                .padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            GlassLogo(40.dp, onBand = true)
            Spacer(Modifier.width(12.dp))
            Column {
                Text("Smart Expense", fontWeight = FontWeight.Bold, fontSize = 17.sp, color = header.on)
                Text(
                    if (name.isBlank()) "Speak it. Track it." else "Hello, ${name.trim()}",
                    fontSize = 12.sp, color = header.on.copy(alpha = 0.75f)
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        DrawerRow("All entries", Icons.Outlined.FormatListBulleted, tab == Tab.ENTRIES, Color(0xFF8B5CF6)) { onPick(Tab.ENTRIES) }
        DrawerRow("Reports", Icons.Outlined.BarChart, tab == Tab.REPORTS, Color(0xFF0EA5E9)) { onPick(Tab.REPORTS) }
        DrawerRow("Trash", Icons.Outlined.Delete, tab == Tab.TRASH, Color(0xFF64748B)) { onPick(Tab.TRASH) }
        DrawerRow("Settings", Icons.Outlined.Settings, tab == Tab.SETTINGS, Color(0xFF64748B)) { onPick(Tab.SETTINGS) }
        HorizontalDivider(Modifier.padding(horizontal = 20.dp, vertical = 10.dp), color = MaterialTheme.colorScheme.outlineVariant)
        Box {
            DrawerRow("Currency", Icons.Outlined.Payments, false, Color(0xFFF59E0B), value = "$currency ▾") { currencyOpen = true }
            PickMenu(currencyOpen, { currencyOpen = false }) {
                CURRENCIES.forEach { c ->
                    DropdownMenuItem(
                        text = { Text(currencyLabel(c), fontSize = 13.sp, fontWeight = if (c == currency) FontWeight.SemiBold else FontWeight.Normal) },
                        onClick = { onCurrency(c); currencyOpen = false }
                    )
                }
            }
        }
    }
}

/** One side-menu row: coloured line icon and label; the open screen sits on a soft orange highlight. */
@Composable
private fun DrawerRow(label: String, icon: ImageVector, selected: Boolean, tint: Color, value: String? = null, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 2.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) EmberTop.copy(alpha = 0.12f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(21.dp), tint = tint)
        Spacer(Modifier.width(14.dp))
        Text(label, fontSize = 15.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) EmberTop else MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
        if (value != null) Text(value, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
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
