package com.bolkhata.app

import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.animation.core.*
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.window.DialogProperties
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
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
 * the text, a thin mic, and a round green button that sends what is typed (or starts speaking when empty) and
 * shows a spinner while the app is answering. A soft fade above it keeps the list from being cut off sharply.
 */
@Composable
internal fun InputBar(
    onSend: (String) -> Unit,
    onMic: () -> Unit,
    placeholder: String = "Type here…",
    onAttach: (() -> Unit)? = null,
    busy: Boolean = false,
    voice: VoiceState? = null
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
    // Like ChatGPT: the page darkens slightly towards the bottom, so the white bar stands out without an outline.
    val shade = lerp(bg, if (bg.luminance() > 0.5f) Color.Black else Color.White, 0.09f)
    Column(Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(bg.copy(alpha = 0f), shade)))) {
        Spacer(Modifier.height(40.dp))
        if (talking) VoiceCaption(voice!!.status, voice.heard, Modifier.padding(top = 6.dp))
        Box(Modifier.fillMaxWidth().navigationBarsPadding().padding(start = 12.dp, end = 12.dp, top = 6.dp, bottom = 10.dp)) {
            // Like ChatGPT's bar: a white pill with a soft shadow and no outline, on the off-white page.
            Surface(
                shape = RoundedCornerShape(26.dp), color = MaterialTheme.colorScheme.surface, shadowElevation = 3.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(Modifier.heightIn(min = 48.dp).padding(start = 4.dp, end = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (onAttach != null) {
                        Box(Modifier.size(40.dp).clip(CircleShape).clickable(onClick = onAttach), contentAlignment = Alignment.Center) {
                            ThinPlus(ink)
                        }
                    } else Spacer(Modifier.width(12.dp))
                    BasicTextField(
                        value = text,
                        onValueChange = { text = it },
                        modifier = Modifier.weight(1f).padding(horizontal = 4.dp, vertical = 12.dp),
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
                    Box(Modifier.size(40.dp).clip(CircleShape).clickable(onClick = onMic), contentAlignment = Alignment.Center) {
                        ThinMic(ink)
                    }
                    Spacer(Modifier.width(2.dp))
                    Box(
                        Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(GreenDeep)
                            .clickable(enabled = talking || !busy) {
                                when {
                                    talking -> voice!!.toggle()
                                    text.isNotBlank() -> send()
                                    else -> voice?.toggle?.invoke() ?: onMic()
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        when {
                            talking -> Box(Modifier.size(12.dp).clip(RoundedCornerShape(3.dp)).background(Color.White))
                            busy -> CircularProgressIndicator(Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                            text.isNotBlank() -> Icon(Icons.Filled.ArrowUpward, contentDescription = "Send", tint = Color.White, modifier = Modifier.size(20.dp))
                            else -> SoundWave(Color.White)
                        }
                    }
                }
            }
        }
    }
}

/** The voice call as the message bar shows it: on/off, what it is doing, what it heard so far; [toggle] connects or stops. */
class VoiceState(val on: Boolean, val status: String, val heard: String, val toggle: () -> Unit)

/**
 * The voice call, like ChatGPT's: a full screen with a circle that grows while the person talks, breathes while the
 * app thinks and pulses while it answers aloud. Tapping the circle while it speaks cuts it short and listens again.
 */
@Composable
internal fun VoiceCall(status: String, heard: String, reply: String, level: Float, onInterrupt: () -> Unit, onEnd: () -> Unit) {
    val speaking = status == "Speaking…"
    val listening = status.isBlank() || status == "Listening…"
    val pulse by rememberInfiniteTransition(label = "call").animateFloat(
        initialValue = 0.94f, targetValue = 1.06f,
        animationSpec = infiniteRepeatable(tween(if (speaking) 450 else 1100), RepeatMode.Reverse), label = "pulse"
    )
    // The recognizer reports about -2…10 dB; quiet stays at full size, a loud voice makes it about a third bigger.
    val voice by animateFloatAsState(1f + (level.coerceIn(0f, 10f) / 10f) * 0.35f, tween(120), label = "voice")
    Dialog(onDismissRequest = onEnd, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(
                Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = 24.dp, vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Voice call", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text(
                    "Talk naturally: add entries, ask totals, open a khata.",
                    fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center
                )
                Spacer(Modifier.weight(1f))
                Box(
                    Modifier.size(180.dp).scale(if (listening) voice else pulse).clip(CircleShape)
                        .background(Brush.radialGradient(listOf(GreenDeep.copy(alpha = 0.55f), GreenDeep)))
                        .clickable(enabled = speaking, onClick = onInterrupt)
                )
                Spacer(Modifier.height(28.dp))
                Text(status.ifBlank { "Listening…" }, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = GreenDeep)
                Spacer(Modifier.height(10.dp))
                Text(
                    if (speaking) reply else heard.ifBlank { reply },
                    fontSize = 17.sp, lineHeight = 24.sp, textAlign = TextAlign.Center,
                    color = if (speaking || heard.isNotBlank()) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 6, overflow = TextOverflow.Ellipsis, modifier = Modifier.heightIn(min = 72.dp)
                )
                if (speaking) Text("Tap the circle to interrupt", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.weight(1f))
                Box(
                    Modifier.size(64.dp).clip(CircleShape).background(RedSpend).clickable(onClick = onEnd),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.Close, contentDescription = "End call", tint = Color.White, modifier = Modifier.size(28.dp))
                }
                Spacer(Modifier.height(6.dp))
                Text("End", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** One or two small lines above the bar while voice chat is on: a green dot, the status, and the words heard. */
@Composable
private fun VoiceCaption(status: String, heard: String, modifier: Modifier) {
    Row(
        modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(Modifier.padding(top = 5.dp).size(6.dp).clip(CircleShape).background(GreenDeep))
        Spacer(Modifier.width(8.dp))
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(fontWeight = FontWeight.Medium, color = GreenDeep)) { append(status.ifBlank { "Listening…" }) }
                if (heard.isNotBlank()) append("  " + heard)
            },
            fontSize = 12.sp, lineHeight = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2, overflow = TextOverflow.Ellipsis
        )
    }
}

/** A thin "+" like ChatGPT's attach button. */
@Composable
private fun ThinPlus(color: Color) {
    Canvas(Modifier.size(20.dp)) {
        val w = 1.8.dp.toPx()
        drawLine(color, Offset(size.width / 2, 1.dp.toPx()), Offset(size.width / 2, size.height - 1.dp.toPx()), w, StrokeCap.Round)
        drawLine(color, Offset(1.dp.toPx(), size.height / 2), Offset(size.width - 1.dp.toPx(), size.height / 2), w, StrokeCap.Round)
    }
}

/** An outlined microphone drawn with thin strokes. */
@Composable
private fun ThinMic(color: Color) {
    Canvas(Modifier.size(20.dp)) {
        val w = 1.8.dp.toPx()
        val cx = size.width / 2
        val capW = size.width * 0.34f
        val top = 1.dp.toPx()
        val capH = size.height * 0.56f
        drawRoundRect(color, Offset(cx - capW / 2, top), Size(capW, capH), CornerRadius(capW / 2), style = Stroke(w))
        val arcW = size.width * 0.66f
        drawArc(color, 0f, 180f, false, Offset(cx - arcW / 2, top + capH * 0.35f), Size(arcW, capH * 0.95f), style = Stroke(w, cap = StrokeCap.Round))
        drawLine(color, Offset(cx, top + capH * 0.35f + capH * 0.95f), Offset(cx, size.height - 1.dp.toPx()), w, StrokeCap.Round)
    }
}

/** Short vertical bars: tap the empty send button to speak. */
@Composable
private fun SoundWave(color: Color) {
    Canvas(Modifier.size(16.dp)) {
        val w = 1.7.dp.toPx()
        val heights = listOf(0.35f, 0.75f, 1f, 0.55f)
        val gap = size.width / (heights.size + 1)
        heights.forEachIndexed { i, h ->
            val x = gap * (i + 1)
            val half = size.height * h / 2
            drawLine(color, Offset(x, size.height / 2 - half), Offset(x, size.height / 2 + half), w, StrokeCap.Round)
        }
    }
}

/** The side menu in the same plain style as Settings: who you are, then simple rows with line icons. */
@Composable
internal fun DrawerMenu(tab: Tab, name: String, currency: String, onCurrency: (String) -> Unit, onPick: (Tab) -> Unit) {
    var currencyOpen by remember { mutableStateOf(false) }
    ModalDrawerSheet(
        modifier = Modifier.width(280.dp),
        drawerContainerColor = MaterialTheme.colorScheme.background,
        drawerShape = RoundedCornerShape(topEnd = 24.dp, bottomEnd = 24.dp)
    ) {
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) { Text(name.trim().take(1).uppercase().ifEmpty { "B" }, fontWeight = FontWeight.SemiBold, fontSize = 17.sp) }
            Spacer(Modifier.width(12.dp))
            Column {
                Text("BolKhata", fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
                Text(
                    if (name.isBlank()) "Speak it. Track it." else "Hello, ${name.trim()}",
                    fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        DrawerRow("Home", Icons.Outlined.Home, tab == Tab.HOME) { onPick(Tab.HOME) }
        DrawerRow("Chat", Icons.Outlined.ChatBubbleOutline, tab == Tab.CHAT) { onPick(Tab.CHAT) }
        DrawerRow("Entries", Icons.Outlined.ReceiptLong, tab == Tab.ENTRIES) { onPick(Tab.ENTRIES) }
        DrawerRow("Reports", Icons.Outlined.BarChart, tab == Tab.REPORTS) { onPick(Tab.REPORTS) }
        DrawerRow("Trash", Icons.Outlined.Delete, tab == Tab.TRASH) { onPick(Tab.TRASH) }
        DrawerRow("Settings", Icons.Outlined.Settings, tab == Tab.SETTINGS) { onPick(Tab.SETTINGS) }
        HorizontalDivider(Modifier.padding(horizontal = 20.dp, vertical = 10.dp), color = MaterialTheme.colorScheme.outlineVariant)
        Box {
            DrawerRow("Currency", Icons.Outlined.Payments, false, value = "$currency ▾") { currencyOpen = true }
            DropdownMenu(expanded = currencyOpen, onDismissRequest = { currencyOpen = false }) {
                CURRENCIES.forEach { c ->
                    DropdownMenuItem(
                        text = { Text(c, fontWeight = if (c == currency) FontWeight.SemiBold else FontWeight.Normal) },
                        onClick = { onCurrency(c); currencyOpen = false }
                    )
                }
            }
        }
    }
}

/** One side-menu row: line icon and label; the open screen sits on a white rounded highlight. */
@Composable
private fun DrawerRow(label: String, icon: ImageVector, selected: Boolean, value: String? = null, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 2.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) MaterialTheme.colorScheme.surface else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(21.dp),
            tint = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(14.dp))
        Text(label, fontSize = 15.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal, modifier = Modifier.weight(1f))
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
                            ChoiceTile("To pay", "I owe $who", "💸", OrangeOwe, Modifier.weight(1f)) {
                                onPick(entry.copy(type = Type.LIYA, category = Cat.UDHAAR, item = "Loans", person = who))
                            }
                            ChoiceTile("To receive", "$who owes me", "🤝", GreenIn, Modifier.weight(1f)) {
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
