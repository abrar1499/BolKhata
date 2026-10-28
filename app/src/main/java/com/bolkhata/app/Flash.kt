package com.bolkhata.app

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/** What the strip above the home input shows. */
class Flash(val text: String, val kind: Kind) {
    enum class Kind { WORKING, FIXED, ANSWER }
    val id = System.nanoTime()
}

/**
 * A small premium strip above the input: "✎ petrol 70 subah" (how the sentence was understood) or the answer
 * to a question / hello. Fades away on its own; tap to close.
 */
@Composable
fun FlashStrip(flash: Flash?, onClose: () -> Unit) {
    // Keep the last one drawn while it fades out.
    var shown by remember { mutableStateOf(flash) }
    if (flash != null) shown = flash
    LaunchedEffect(flash?.id) {
        val f = flash ?: return@LaunchedEffect
        if (f.kind == Flash.Kind.WORKING) return@LaunchedEffect
        delay(if (f.kind == Flash.Kind.ANSWER) 14_000 else 6_000)
        onClose()
    }
    AnimatedVisibility(
        visible = flash != null,
        enter = fadeIn() + slideInVertically { it / 2 },
        exit = fadeOut() + slideOutVertically { it / 2 }
    ) {
        val f = shown ?: return@AnimatedVisibility
        val primary = MaterialTheme.colorScheme.primary
        val tertiary = MaterialTheme.colorScheme.tertiary
        val surface = MaterialTheme.colorScheme.surface
        Row(
            Modifier
                .padding(horizontal = 16.dp, vertical = 6.dp)
                .fillMaxWidth()
                .shadow(8.dp, RoundedCornerShape(18.dp), ambientColor = primary.copy(alpha = 0.25f), spotColor = primary.copy(alpha = 0.25f))
                .clip(RoundedCornerShape(18.dp))
                .background(Brush.horizontalGradient(listOf(primary.copy(alpha = 0.10f), tertiary.copy(alpha = 0.10f))))
                .background(surface.copy(alpha = 0.85f))
                .border(1.dp, Brush.horizontalGradient(listOf(primary.copy(alpha = 0.45f), tertiary.copy(alpha = 0.45f))), RoundedCornerShape(18.dp))
                .clickable { onClose() }
                .padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(26.dp).clip(CircleShape)
                    .background(Brush.linearGradient(listOf(primary, tertiary))),
                contentAlignment = Alignment.Center
            ) {
                if (f.kind == Flash.Kind.WORKING) {
                    CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp, color = surface)
                } else {
                    Text(if (f.kind == Flash.Kind.FIXED) "✎" else "✨", fontSize = 13.sp, color = surface)
                }
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    when (f.kind) { Flash.Kind.WORKING -> "Understanding"; Flash.Kind.FIXED -> "Understood as"; Flash.Kind.ANSWER -> "Answer" },
                    fontSize = 10.sp, fontWeight = FontWeight.Bold, color = primary, letterSpacing = 0.6.sp
                )
                if (f.kind != Flash.Kind.WORKING) {
                    Text(
                        f.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface, maxLines = 4, overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

/**
 * A person's name from a bank-statement note: "FRIMTIAZ. Internal Transfer Time:20:57:18, Notes:…" -> "Imtiaz",
 * "TONISAR" -> "Nisar". Bank details (time, account numbers, IPS) are never part of a name.
 */
fun cleanPersonName(raw: String): String? {
    var s = raw.trim().split('.', ';', ',', ':', '/', '(').first().trim()
    s = s.replace(Regex("\\d+"), " ").replace(Regex("\\s+"), " ").trim()
    if (s.isBlank()) return null
    val bad = setOf("transfer", "internal", "inward", "outward", "credit", "debit", "notes", "time", "ips", "acct", "pos", "purchase", "fee")
    if (s.split(' ').any { it.lowercase() in bad }) return null
    // "FRIMTIAZ" / "TONISAR": the bank glues "FR" / "TO" to the name.
    if (!s.contains(' ') && s.length >= 5 && s == s.uppercase()) {
        val m = Regex("^(TO|FR)(\\p{Lu}{3,})$").find(s)
        if (m != null) s = m.groupValues[2]
    }
    s = s.split(' ').take(3).joinToString(" ")
    if (s == s.uppercase() && s.any { it in 'A'..'Z' }) s = s.lowercase().split(' ').joinToString(" ") { w -> w.replaceFirstChar { it.uppercase() } }
    return s.takeIf { it.length >= 2 }
}
