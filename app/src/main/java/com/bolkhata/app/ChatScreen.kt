package com.bolkhata.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun ChatScreen(
    messages: List<ChatMessage>,
    loading: Boolean,
    onMenu: () -> Unit,
    onSend: (String) -> Unit,
    onClear: () -> Unit
) {
    var draft by rememberSaveable { mutableStateOf("") }
    var confirmClear by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    fun submit() {
        val text = draft.trim()
        if (text.isNotEmpty() && !loading) {
            draft = ""
            onSend(text)
        }
    }

    LaunchedEffect(messages.size, loading) {
        val itemCount = messages.size + (if (messages.isEmpty()) 1 else 0) + (if (loading) 1 else 0)
        if (itemCount > 0) listState.animateScrollToItem(itemCount - 1)
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .background(LocalHeader.current.brush)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onMenu) {
                Icon(Icons.Filled.Menu, contentDescription = "Open menu", tint = LocalHeader.current.on)
            }
            Column(Modifier.weight(1f)) {
                Text("BolKhata Chat", color = LocalHeader.current.on, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                Text("Ask about your khata", color = LocalHeader.current.on.copy(alpha = 0.8f), fontSize = 12.sp)
            }
            IconButton(
                onClick = { confirmClear = true },
                enabled = messages.isNotEmpty() && !loading
            ) {
                Icon(Icons.Filled.Delete, contentDescription = "Clear chat", tint = LocalHeader.current.on)
            }
        }

        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            state = listState,
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (messages.isEmpty()) {
                item(key = "chat-welcome") {
                    Column(
                        Modifier.fillMaxWidth().padding(vertical = 28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("💬", fontSize = 42.sp)
                        Spacer(Modifier.height(8.dp))
                        Text("Your khata assistant", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Ask questions about saved expenses, income, or who owes you money.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(18.dp))
                        listOf(
                            "How much did I spend this month?",
                            "Summarize my recent expenses",
                            "Who owes me money?"
                        ).forEach { suggestion ->
                            OutlinedButton(
                                onClick = { onSend(suggestion) },
                                modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)
                            ) {
                                Text(suggestion)
                            }
                        }
                    }
                }
            }
            items(messages, key = { it.id }) { message ->
                val bubbleColor = if (message.isUser) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surface
                }
                val textColor = if (message.isUser) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurface
                }
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = if (message.isUser) Arrangement.End else Arrangement.Start
                ) {
                    Column(
                        Modifier
                            .widthIn(max = 320.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(bubbleColor)
                            .padding(14.dp)
                    ) {
                        Text(
                            if (message.isUser) "You" else "BolKhata",
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(message.text, color = textColor, lineHeight = 21.sp)
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
                        Text("Thinking…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 12.dp) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Ask about your khata…") },
                    maxLines = 4,
                    shape = RoundedCornerShape(24.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { submit() })
                )
                Spacer(Modifier.width(8.dp))
                IconButton(
                    onClick = { submit() },
                    enabled = draft.isNotBlank() && !loading
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Send message",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Clear chat history?") },
            text = { Text("This removes the saved conversation from this phone.") },
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
