package com.bolkhata.app

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.speech.RecognizerIntent
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import androidx.core.view.WindowCompat

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val store = Store(this)
        setContent {
            var dark by remember { mutableStateOf(store.dark) }
            var themeName by remember { mutableStateOf(store.theme) }
            val header = headerTheme(themeName)
            LaunchedEffect(header) {
                @Suppress("DEPRECATION")
                window.statusBarColor = header.c1.toArgb()
                WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = header.light
            }
            CompositionLocalProvider(LocalHeader provides header) {
                BolTheme(dark) {
                    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                        App(
                            store, dark, { dark = it; store.dark = it },
                            themeName = themeName, onTheme = { themeName = it; store.theme = it }
                        )
                    }
                }
            }
        }
    }
}

enum class Tab { HOME, STOCK, CHAT, ENTRIES, REPORTS, TRASH, SETTINGS }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun App(store: Store, dark: Boolean, onDark: (Boolean) -> Unit, themeName: String, onTheme: (String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    var entries by remember { mutableStateOf(store.load()) }
    var currency by remember { mutableStateOf(store.currency) }
    var name by remember { mutableStateOf(store.name) }
    var lang by remember { mutableStateOf(store.language) }
    var tab by remember { mutableStateOf(Tab.HOME) }
    var chatMessages by remember { mutableStateOf(store.loadChatMessages()) }
    var chatLoading by remember { mutableStateOf(false) }
    var people by remember { mutableStateOf<Boolean?>(null) }   // true = to receive, false = to pay
    var editing by remember { mutableStateOf<Entry?>(null) }
    var adding by remember { mutableStateOf(false) }
    var pending by remember { mutableStateOf(listOf<Entry>()) }
    var moreFor by remember { mutableStateOf<Entry?>(null) }
    var searching by remember { mutableStateOf(false) }
    var answerText by remember { mutableStateOf<String?>(null) }
    var suggestions by remember { mutableStateOf(store.loadSuggestions()) }
    var reviewing by remember { mutableStateOf(false) }
    var bankOn by remember { mutableStateOf(BankListener.isEnabled(context)) }
    var bankHelp by remember { mutableStateOf(false) }
    // A sale or purchase opened from the Stock tab, waiting to be confirmed in the form.
    var trade by remember { mutableStateOf<Entry?>(null) }
    // The entries before a chat message was handled; what changes after that is reported in the chat.
    var chatWatch by remember { mutableStateOf<List<Entry>?>(null) }
    var voiceForChat by remember { mutableStateOf(false) }
    // Transactions read from an attached statement, receipt or bill, waiting to be ticked and added.
    var imports by remember { mutableStateOf(listOf<Entry>()) }
    // Which report opens: Expense opens Cash Out, Income opens Cash In.
    var reportStart by remember { mutableStateOf(ReportKind.CASH_OUT) }
    var importDupes by remember { mutableStateOf(setOf<String>()) }

    DisposableEffect(Unit) {
        val stop = store.watchSuggestions { suggestions = store.loadSuggestions() }
        onDispose { stop() }
    }
    val lifecycle = androidx.compose.ui.platform.LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val obs = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                bankOn = BankListener.isEnabled(context)
                suggestions = store.loadSuggestions()
            }
        }
        lifecycle.addObserver(obs)
        onDispose { lifecycle.removeObserver(obs) }
    }

    fun dropSuggestions(ids: Set<String>) {
        val left = suggestions.filter { it.id !in ids }
        suggestions = left
        store.saveSuggestions(left)
    }
    var results by remember { mutableStateOf<Pair<String, Boolean>?>(null) }      // query, edit mode
    var confirm by remember { mutableStateOf<Pair<String, Set<String>>?>(null) }  // message, ids to delete  // entries the app did not understand
    var newVersion by remember { mutableStateOf<UpdateInfo?>(null) }
    var progress by remember { mutableIntStateOf(-1) }
    var checking by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { newVersion = Updater.check() }

    // While a chat message is being handled, short messages go into the chat instead of a pop-up.
    val chatSink = remember { arrayOfNulls<(String) -> Unit>(1) }

    fun toast(msg: String) {
        val sink = chatSink[0]
        if (tab == Tab.CHAT && chatWatch != null && sink != null) {
            sink(msg)
            return
        }
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            snackbar.showSnackbar(msg)
        }
    }

    var trash by remember { mutableStateOf(store.loadTrash()) }

    fun saveTrash(list: List<Entry>) {
        trash = list
        store.saveTrash(list)
    }

    fun save(list: List<Entry>) {
        entries = list
        store.save(list)
    }

    /** Moves entries to Trash and offers Undo. */
    fun moveToTrash(ids: Set<String>) {
        val gone = entries.filter { it.id in ids }
        if (gone.isEmpty()) return
        save(entries.filter { it.id !in ids })
        store.markTrashed(ids)
        saveTrash(gone + trash.filter { it.id !in ids })
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            val res = snackbar.showSnackbar(
                "Moved ${gone.size} to Trash", actionLabel = "Undo", duration = SnackbarDuration.Long
            )
            if (res == SnackbarResult.ActionPerformed) {
                save(gone + entries)
                saveTrash(trash.filter { it.id !in ids })
            }
        }
    }

    fun askDelete(label: String, list: List<Entry>) {
        if (list.isEmpty()) {
            toast("Nothing found for \"$label\"")
        } else {
            confirm = "Delete ${list.size} " + (if (list.size == 1) "entry" else "entries") + " for \"$label\"?" to list.map { it.id }.toSet()
        }
    }

    /** Handles typed or spoken commands: search, edit, delete, clear all. Returns true if it was a command. */
    fun handleCommand(text: String): Boolean {
        val c = Commands.read(text) ?: return false
        when (c.action) {
            Commands.Action.SEARCH -> { results = c.query to false; people = null }
            Commands.Action.EDIT -> { results = c.query to true; people = null }
            Commands.Action.DELETE -> askDelete(c.query, matchEntries(entries, c.query))
            Commands.Action.CLEAR_ALL -> {
                if (entries.isEmpty()) toast("There are no entries to delete")
                else confirm = "Delete ALL ${entries.size} entries? This removes every entry." to entries.map { it.id }.toSet()
            }
        }
        return true
    }

    var aiKey by remember { mutableStateOf(store.aiKey) }
    // The person's own key wins; otherwise the key built into the app is used.
    val activeKey = aiKey.ifBlank { BuildConfig.GEMINI_KEY }

    /** Sends a question to the read-only AI chat, with recent conversation context. */
    fun sendChat(text: String, addUser: Boolean = true) {
        val question = text.trim()
        if (question.isEmpty() || chatLoading) return
        val history = chatMessages.takeLast(12)
        if (addUser) {
            val userMessage = ChatMessage(
                id = "chat-" + System.currentTimeMillis() + "-" + System.nanoTime(),
                isUser = true,
                text = question
            )
            val next = chatMessages + userMessage
            chatMessages = next
            store.saveChatMessages(next)
        }
        chatLoading = true
        scope.launch {
            try {
                val reply = Ai.chat(question, history, entries, activeKey, store, currency)
                val updated = chatMessages + ChatMessage(
                    id = "chat-" + System.currentTimeMillis() + "-" + System.nanoTime(),
                    isUser = false,
                    text = reply
                )
                chatMessages = updated
                store.saveChatMessages(updated)
            } catch (e: Exception) {
                val message = e.message?.takeIf { it.isNotBlank() } ?: "Sorry, I couldn't answer right now."
                val updated = chatMessages + ChatMessage(
                    id = "chat-" + System.currentTimeMillis() + "-" + System.nanoTime(),
                    isUser = false,
                    text = message
                )
                chatMessages = updated
                store.saveChatMessages(updated)
            } finally {
                chatLoading = false
            }
        }
    }

    // Keep the offline reader up to date with what the person taught it and who is in the khata.
    LaunchedEffect(Unit) { Parser.learned = store.loadLearned() }
    LaunchedEffect(entries) {
        Parser.knownPeople = entries.mapNotNull { e -> e.person.trim().lowercase().takeIf { it.length >= 3 } }.toSet()
        Parser.owed = balances(entries).mapKeys { it.key.lowercase() }
    }

    /** When the person fixes a category, remember the words so it is right next time. */
    fun learnFrom(before: Entry?, after: Entry) {
        if (after.type != Type.KHARCHA || after.category == Cat.OTHER) return
        if (before != null && before.category == after.category && before.item == after.item) return
        val words = Parser.learnWords(after.note)
        if (words.isEmpty()) return
        store.learn(words, after.category, after.item)
        Parser.learned = store.loadLearned()
    }

    fun addOffline(r: Parser.Result) {
        if (r.entries.isEmpty() && r.unclear.isEmpty()) {
            toast("No amount found. Please say or type an amount.")
            return
        }
        if (r.entries.isNotEmpty()) {
            save(r.entries + entries)
            toast("✓ Added " + r.entries.joinToString(", ") { entryTitle(it) + " " + fmt(it.amount) })
        }
        if (r.unclear.isNotEmpty()) pending = pending + r.unclear
    }

    /**
     * Offline first. Only when the offline reader is unsure (something it could not place,
     * an "Other" expense, or no amount at all) and an AI key is set, the sentence goes to Gemini.
     * If the AI fails or there is no internet, the offline result is used as before.
     */
    /** Applies an AI change straight away; Undo puts everything back. */
    fun applyPlan(plan: Ai.EditPlan) {
        val beforeEntries = entries
        val beforeTrash = trash
        val map = plan.changed.associate { it.first.id to it.second }
        val gone = plan.deleted.map { it.id }.toSet()
        save(entries.filter { it.id !in gone }.map { map[it.id] ?: it })
        if (gone.isNotEmpty()) {
            store.markTrashed(gone)
            saveTrash(plan.deleted + trash.filter { it.id !in gone })
        }
        plan.changed.forEach { (b, a) -> learnFrom(b, a) }
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            val res = snackbar.showSnackbar("✓ " + plan.message, actionLabel = "Undo", duration = SnackbarDuration.Long)
            if (res == SnackbarResult.ActionPerformed) {
                save(beforeEntries)
                saveTrash(beforeTrash)
            }
        }
    }

    /**
     * "kachi eint ke pese abhi nahi diye" marks that purchase as on credit (the supplier goes in To pay);
     * "Afzal ko eint ke pese de diye" marks it as paid. Returns false when no sale or purchase fits.
     */
    fun applyPayStatus(text: String): Boolean {
        val st = Parser.payStatus(text) ?: return false
        val lower = text.lowercase()
        val words = lower.split(Regex("""[^\p{L}]+""")).filter { it.isNotEmpty() }
        val latestRef = words.any { it in setOf("last", "latest", "newest", "recent", "pichli", "pichhli", "akhri", "aakhri", "previous") } &&
            words.any { it in setOf("entry", "entries", "record", "transaction", "stock", "sale", "sales", "purchase", "purchases", "item", "payment") }
        val saysReceived = words.any { it in setOf("receive", "received", "recieve", "recieved", "mila", "mili", "mile", "aayi", "aai", "aaya", "aaye", "vasool", "vasooli", "collected") }
        val saysSale = words.any { it in setOf("sale", "sales", "sold", "becha", "bechi", "customer", "buyer") }
        val saysPurchase = words.any { it in setOf("purchase", "purchases", "purchased", "bought", "kharida", "khareeda", "kharidi", "supplier", "vendor") }
        val saysNotPaid = words.any { it in setOf("nahi", "nahin", "nhi", "ni", "not", "didnt", "na") } &&
            words.any { it in setOf("paid", "pay", "diye", "diya", "di", "ada", "chuka") }
        val preferredType = when {
            saysSale -> Type.SALE
            saysPurchase -> Type.PURCHASE
            // "last stock entry ..." means the newest matching trade, regardless of direction.
            latestRef -> null
            saysReceived -> Type.SALE
            saysNotPaid -> Type.PURCHASE
            else -> null
        }
        val trades = entries.filter { isTrade(it.type) }.sortedByDescending { it.createdAt }
        if (trades.isEmpty()) {
            toast("No sale or stock purchase found to update. Add the entry first, then tell me its payment status.")
            return true
        }
        val candidates = if (preferredType != null) trades.filter { it.type == preferredType } else trades
        if (candidates.isEmpty()) {
            toast(if (preferredType == Type.SALE) "I couldn't find a sale to update. Check the entry or mention the customer's name." else "I couldn't find a stock purchase to update. Check the entry or mention the supplier.")
            return true
        }
        fun hit(e: Entry, k: String) = e.person.equals(k, ignoreCase = true) ||
            e.item.lowercase().split(Regex("""\s+""")).any { w -> w.length >= 3 && (w.startsWith(k) || k.startsWith(w)) }
        val matched = if (st.keywords.isNotEmpty()) {
            candidates.firstOrNull { e -> st.keywords.all { k -> hit(e, k) } }
                ?: candidates.firstOrNull { e -> st.keywords.any { k -> hit(e, k) } }
        } else null
        val target = matched ?: if (st.keywords.isEmpty() && (latestRef || st.mentionsMoney)) candidates.firstOrNull() else null
        if (target == null) {
            toast("I couldn't match that payment to an entry. Mention the item or person, e.g. \"payment for kachi eint not received\".")
            return true
        }
        val who = target.person.ifBlank { st.person }
        if (!st.paid && who.isBlank() && target.type == Type.PURCHASE) {
            toast("Please also say the supplier's name, e.g. \"last purchase not paid yet, supplier Aslam\".")
            return true
        }
        val updated = target.copy(paid = st.paid, person = who)
        // A cash payment saved with it in the same sentence did not happen after all.
        val payType = if (target.type == Type.SALE) Type.WAPIS_MILA else Type.WAPIS_DIYA
        val undone = if (st.paid) emptyList() else entries.filter {
            it.type == payType && it.person.equals(who, true) && kotlin.math.abs(it.createdAt - target.createdAt) < 5_000
        }
        val undoneIds = undone.map { it.id }.toSet()
        val after = entries.filter { it.id !in undoneIds }.map { if (it.id == target.id) updated else it }
        val bal = balances(after).entries.firstOrNull { it.key.equals(who, true) }?.value ?: 0.0
        val owe = when {
            who.isBlank() -> ""
            bal < -0.005 -> " · you will pay " + who + " " + money(currency, -bal)
            bal > 0.005 -> " · " + who + " will pay you " + money(currency, bal)
            else -> " · all settled with " + who
        }
        val label = target.item.ifBlank { if (target.type == Type.SALE) "Sale" else "Purchase" }
        val msg = when {
            st.paid -> label + " marked paid" + owe
            target.type == Type.SALE && who.isBlank() -> label + " marked unpaid; add the customer's name to track who owes you"
            else -> label + " marked on credit" + owe
        }
        if (updated == target && undone.isEmpty()) {
            toast(if (target.type == Type.SALE && !st.paid && who.isBlank()) label + " is already unpaid. Add the customer's name to track the payment." else msg)
        } else {
            applyPlan(Ai.EditPlan(listOf(target to updated), undone, msg))
        }
        return true
    }

    /** "hamza wali 500 ko 400 kar do", "last entry food mein daal do", "kal ka petrol 60 tha 50 nahi". */
    fun looksLikeEdit(text: String): Boolean {
        val t = " " + text.lowercase() + " "
        val strong = listOf(
            "change", "badal", "badlo", "update", "correct", "galat", "ghalat", "ki jagah", "ke bajaye", "instead",
            "should be", "make it", "nahi tha", "nahi thi", "nahin tha", "fix", "theek kar", "thik kar", "sahi kar", " edit "
        )
        val soft = listOf("kar do", "kardo", "kr do", "krdo", "daal do", "dal do", "daldo", "kar dein", "kardein")
        val refers = listOf("entry", "wali", "wala", "wale", "last", "pichli", "pichhli", "akhri", "aakhri", "category", "previous")
        return t.contains(" entry ") || strong.any { t.contains(it) } || (soft.any { t.contains(it) } && refers.any { t.contains(it) })
    }

    fun handleTextSafely(text: String) {
        // A vague "change an entry" is not enough information to edit safely; ask what to change.
        val normalizedWords = text.lowercase().split(Regex("""[^\p{L}\p{N}]+""")).filter { it.isNotEmpty() }
        val genericEditWords = setOf(
            "koi", "koee", "any", "entry", "entries", "record", "transaction", "last", "latest", "newest", "recent",
            "pichli", "pichhli", "akhri", "aakhri", "previous", "change", "edit", "update", "badlo", "badal",
            "karo", "kar", "kr", "do", "dein", "krdo", "kardo", "please", "plz", "the", "a", "an", "meri", "my"
        )
        val hasEditVerb = normalizedWords.any { it in setOf("change", "edit", "update", "badlo", "badal", "correct", "fix", "theek", "thik") }
        val hasEntryRef = normalizedWords.any { it in setOf("entry", "entries", "record", "transaction") }
        val vagueEdit = hasEditVerb && hasEntryRef && text.none { it.isDigit() } && normalizedWords.all { it in genericEditWords }
        if (vagueEdit) {
            toast("Which entry, and what should change? For example: \"last entry was 400 not 500\" or \"change petrol entry category to food\".")
            return
        }
        // Process payment-status statements before stock questions, generic edit commands, or the new-entry parser.
        // But never mutate an entry when the person is asking a question.
        val statusQuestion = text.trim().endsWith("?") ||
            normalizedWords.any { it in setOf("kya", "kia", "kitna", "kitne", "kitni", "how", "what", "who", "when", "where", "which", "whether") }
        if (!statusQuestion && applyPayStatus(text)) return
        if (handleCommand(text)) return
        if (Questions.isQuestion(text)) {
            val a = Questions.answer(text, entries, currency)
            if (a != null) {
                if (a.search == Questions.PAY || a.search == Questions.RECEIVE) {
                    results = null
                    people = a.search == Questions.RECEIVE
                } else if (a.search != null) {
                    // Open the person's khata (or the matching entries) with the answer on top.
                    people = null
                    answerText = a.text
                    results = a.search to false
                } else {
                    scope.launch {
                        snackbar.currentSnackbarData?.dismiss()
                        snackbar.showSnackbar(a.text, duration = SnackbarDuration.Long, withDismissAction = true)
                    }
                }
                return
            }
        }
        // Simple changes (date, amount) are done offline straight away, with Undo.
        Edits.plan(text, entries)?.let { applyPlan(it); return }
        if (looksLikeEdit(text) && entries.isNotEmpty()) {
            if (activeKey.isBlank()) {
                toast("Changing entries by voice needs AI. Turn it on in Settings, or tap the entry to edit.")
                return
            }
            toast("✨ Finding the entry…")
            scope.launch {
                try {
                    val plan = Ai.edit(text, entries, activeKey, store, currency)
                    if (plan.changed.isEmpty() && plan.deleted.isEmpty()) {
                        toast(plan.message)
                    } else {
                        applyPlan(plan)
                    }
                } catch (e: Exception) {
                    toast(e.message ?: "AI did not answer. Tap the entry to edit it.")
                }
            }
            return
        }
        val r = Parser.parse(text)
        // Goods said without a price are asked about here (the rate), not sent to the AI.
        val askRate = r.unclear.isNotEmpty() && r.unclear.all { it.qty > 0 }
        val unsure = !askRate && (r.unclear.isNotEmpty() || r.entries.isEmpty()) ||
            r.entries.any { it.type == Type.KHARCHA && it.category == Cat.OTHER }
        if (!unsure || activeKey.isBlank()) {
            addOffline(r)
            return
        }
        toast("✨ Understanding…")
        scope.launch {
            try {
                val list = Ai.parse(text, activeKey, store, currency)
                if (list.isEmpty()) {
                    addOffline(r)
                } else {
                    save(list + entries)
                    toast("✨ Added " + list.joinToString(", ") { entryTitle(it) + " " + fmt(it.amount) })
                }
            } catch (e: Exception) {
                addOffline(r)
            }
        }
    }

    fun handleText(text: String) {
        try {
            handleTextSafely(Translit.toRoman(text))
        } catch (e: Exception) {
            // Never close the app because a sentence was not understood.
            toast("Sorry, I couldn't read that. Tap + to add it by hand.")
        }
    }

    /** Adds a message to the chat and keeps it on the phone. */
    fun postChat(text: String, isUser: Boolean, entryIds: List<String> = emptyList()) {
        val updated = chatMessages + ChatMessage(
            id = "chat-" + System.currentTimeMillis() + "-" + System.nanoTime(),
            isUser = isUser,
            text = text,
            entryIds = entryIds
        )
        chatMessages = updated
        store.saveChatMessages(updated)
    }

    chatSink[0] = { m ->
        chatWatch = null
        postChat(m.removePrefix("✨ ").trim(), isUser = false)
    }

    // When a chat message adds or changes entries, say so in the chat (new ones as a saved card with Undo).
    LaunchedEffect(entries) {
        val before = chatWatch ?: return@LaunchedEffect
        if (entries === before) return@LaunchedEffect
        val ids = before.map { it.id }.toSet()
        val added = entries.filter { it.id !in ids }
        chatWatch = null
        if (added.isNotEmpty()) {
            postChat(
                "✓ " + added.size + " " + (if (added.size == 1) "entry" else "entries") + " saved",
                isUser = false,
                entryIds = added.map { it.id }
            )
        } else {
            postChat("✓ Done. Your entries are updated.", isUser = false)
        }
    }

    /** Hands a message to the home screen's reader and reports the result in the chat. */
    fun chatViaReader(text: String) {
        val snapshot = entries
        chatWatch = snapshot
        handleText(text)
        // Stop waiting if nothing changed (a question box or form that was cancelled).
        scope.launch {
            kotlinx.coroutines.delay(60_000)
            if (chatWatch === snapshot) chatWatch = null
        }
    }

    /** Without AI (no key, or no internet): save clear entries, answer simple questions, never stay silent. */
    fun chatOffline(text: String, roman: String, aiDown: Boolean) {
        val question = roman.endsWith("?") || runCatching { Questions.isQuestion(roman) }.getOrDefault(false)
        val parsed = runCatching { Parser.parse(roman) }.getOrNull()
        if (!question && parsed != null && (parsed.entries.isNotEmpty() || parsed.unclear.isNotEmpty())) {
            chatViaReader(text)
            return
        }
        val answer = if (question) runCatching { Questions.answer(roman, entries, currency) }.getOrNull() else null
        postChat(
            answer?.text ?: if (aiDown) {
                "AI is not reachable right now. I can still save entries like \"petrol 60\", and answer simple questions like \"how much this month?\"."
            } else {
                "Hi! Tell me an expense like \"petrol 60\", or ask \"how much did I spend this month?\". Turn on AI in Settings for full answers."
            },
            isUser = false
        )
    }

    /**
     * Chat input. AI comes first: one call either saves the money the person mentioned or answers them
     * (questions, reports, searches, advice). Changes to existing entries use the app's own reader.
     * Without AI, the offline reader and offline answers take over.
     */
    fun chatInput(raw: String) {
        val text = raw.trim()
        if (text.isEmpty() || chatLoading) return
        val roman = runCatching { Translit.toRoman(text) }.getOrDefault(text)
        val history = chatMessages.takeLast(10)
        postChat(text, isUser = true)
        val changesExisting = runCatching {
            Commands.read(roman) != null || Parser.payStatus(roman) != null ||
                Edits.plan(roman, entries) != null ||
                (looksLikeEdit(roman) && entries.isNotEmpty() && roman.any { it.isDigit() })
        }.getOrDefault(false)
        if (changesExisting) {
            chatViaReader(text)
            return
        }
        if (activeKey.isBlank()) {
            chatOffline(text, roman, aiDown = false)
            return
        }
        chatLoading = true
        scope.launch {
            try {
                val (reply, list) = Ai.assistant(text, history, entries, activeKey, store, currency)
                if (list.isNotEmpty()) {
                    save(list + entries)
                    postChat(reply.ifBlank { "Saved" }, isUser = false, entryIds = list.map { it.id })
                } else {
                    postChat(reply, isUser = false)
                }
            } catch (e: Exception) {
                chatOffline(text, roman, aiDown = true)
            } finally {
                chatLoading = false
            }
        }
    }

    /** An entry already in the khata on the same day with the same amount and kind is probably the same one. */
    fun isDuplicate(e: Entry): Boolean = entries.any {
        it.type == e.type && kotlin.math.abs(it.amount - e.amount) < 0.01 && sameDay(it.date, e.date)
    }

    /** Reads an attached photo or PDF with AI and shows what it found in the chat to tick and add. */
    fun readAttachment(uri: android.net.Uri) {
        if (chatLoading) return
        val resolver = context.contentResolver
        val mime = resolver.getType(uri) ?: "application/octet-stream"
        if (!(mime.startsWith("image/") || mime == "application/pdf")) {
            postChat("Please attach a photo or a PDF.", isUser = false)
            return
        }
        val name = runCatching {
            resolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        }.getOrNull() ?: if (mime == "application/pdf") "Statement.pdf" else "Photo"
        postChat("📎 $name", isUser = true)
        if (activeKey.isBlank()) {
            postChat("Reading statements and bills needs AI. Turn it on in Settings.", isUser = false)
            return
        }
        chatLoading = true
        scope.launch {
            try {
                val bytes = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    resolver.openInputStream(uri)?.use { it.readBytes() }
                } ?: throw Ai.Failure("Could not open this file.")
                if (bytes.size > 15 * 1024 * 1024) throw Ai.Failure("This file is too big (over 15 MB). Send a smaller PDF or a photo of one page.")
                val found = Ai.readDocument(bytes, mime, "", activeKey, store, currency)
                    .sortedByDescending { it.date }
                if (found.isEmpty()) {
                    postChat("I couldn't find any transactions in this file.", isUser = false)
                } else {
                    imports = found
                    importDupes = found.filter { isDuplicate(it) }.map { it.id }.toSet()
                    val dupes = importDupes.size
                    postChat(
                        "Found ${found.size} " + (if (found.size == 1) "transaction" else "transactions") +
                            (if (dupes > 0) ". $dupes look already saved, so I left them unticked." else ".") +
                            " Check them below and tap Add.",
                        isUser = false
                    )
                }
            } catch (e: Exception) {
                postChat(e.message ?: "Could not read this file.", isUser = false)
            } finally {
                chatLoading = false
            }
        }
    }

    val pickFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) readAttachment(uri)
    }

    /** Reads bank messages already in the phone's SMS inbox and offers the payments found to tick and add. */
    fun importSms(days: Int) {
        if (chatLoading) return
        postChat("Import bank SMS · last $days days", isUser = true)
        chatLoading = true
        scope.launch {
            try {
                val since = System.currentTimeMillis() - days * 86_400_000L
                val found = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    val out = mutableListOf<Entry>()
                    context.contentResolver.query(
                        android.net.Uri.parse("content://sms/inbox"),
                        arrayOf("body", "date"),
                        "date >= ?", arrayOf(since.toString()), "date DESC"
                    )?.use { c ->
                        val bodyCol = c.getColumnIndex("body")
                        val dateCol = c.getColumnIndex("date")
                        while (c.moveToNext()) {
                            val body = c.getString(bodyCol) ?: continue
                            val date = c.getLong(dateCol)
                            BankParser.parse(body, date)?.let { e ->
                                out += e.copy(id = "s" + date + "_" + out.size)
                            }
                        }
                    }
                    out
                }
                if (found.isEmpty()) {
                    postChat("No bank payments found in your SMS for the last $days days.", isUser = false)
                } else {
                    imports = found
                    importDupes = found.filter { isDuplicate(it) }.map { it.id }.toSet()
                    val dupes = importDupes.size
                    postChat(
                        "Found ${found.size} bank " + (if (found.size == 1) "payment" else "payments") + " in your SMS" +
                            (if (dupes > 0) ". $dupes look already saved, so I left them unticked." else ".") +
                            " Check them below and tap Add.",
                        isUser = false
                    )
                }
            } catch (e: Exception) {
                postChat("Could not read your SMS: " + (e.message ?: "unknown error"), isUser = false)
            } finally {
                chatLoading = false
            }
        }
    }

    var smsDays by remember { mutableIntStateOf(90) }
    val smsPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) importSms(smsDays)
        else postChat("SMS permission was not given, so I can't read bank messages. You can attach a statement PDF instead.", isUser = false)
    }

    fun askSms(days: Int) {
        smsDays = days
        val granted = androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_SMS) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
        if (granted) importSms(days) else smsPermission.launch(android.Manifest.permission.READ_SMS)
    }

    /** One tap from the chat chips: the same entry again, dated now. */
    fun quickAdd(e: Entry) {
        val now = System.currentTimeMillis()
        val copy = e.copy(id = "e" + System.nanoTime(), date = now, createdAt = now, person = if (withPerson(e)) e.person else "")
        save(listOf(copy) + entries)
        postChat("✓ 1 entry saved", isUser = false, entryIds = listOf(copy.id))
    }

    /** Rank alternatives by how well they fit khata commands, not just their list position. */
    fun voiceGuessScore(text: String, confidence: Float?): Int {
        var score = confidence?.takeIf { it >= 0f }?.let { (it * 20f).toInt() } ?: 0
        val parsed = runCatching { Parser.parse(text) }.getOrNull()
        if (runCatching { Questions.isQuestion(text) }.getOrDefault(false)) score += 80
        if (runCatching { Parser.payStatus(text) != null }.getOrDefault(false)) score += 100
        if (runCatching { Commands.read(text) != null }.getOrDefault(false)) score += 100
        if (runCatching { looksLikeEdit(text) }.getOrDefault(false)) score += 80
        if (parsed != null && parsed.entries.isNotEmpty()) {
            score += if (parsed.unclear.isEmpty()) 65 else 30
        }
        val lower = text.lowercase()
        if (lower.any { it.isDigit() }) score += 8
        val knownItemOrPerson = entries.any { entry ->
            listOf(entry.item, entry.person).filter { it.isNotBlank() }.any { value ->
                val v = value.lowercase()
                lower.contains(v) || v.split(Regex("""\s+""")).any { word ->
                    word.length >= 3 && lower.contains(word)
                }
            }
        }
        if (knownItemOrPerson) score += 15
        if (text.length < 3) score -= 50
        return score
    }

    val voice = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        if (res.resultCode == Activity.RESULT_OK) {
            val rawGuesses = res.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS).orEmpty()
            val confidenceScores = res.data?.getFloatArrayExtra(RecognizerIntent.EXTRA_CONFIDENCE_SCORES) ?: floatArrayOf()
            val guesses = rawGuesses.mapIndexed { index, raw ->
                val converted = Translit.toRoman(raw).trim()
                converted to voiceGuessScore(converted, confidenceScores.getOrNull(index))
            }.filter { it.first.isNotBlank() }
            // Score every alternative: the first transcript can be fluent but still be the wrong words.
            val said = guesses.maxByOrNull { it.second }?.first.orEmpty()
            if (said.isNotBlank()) {
                if (voiceForChat) chatInput(said) else handleText(said)
            } else toast("Couldn't catch that. Tap the mic again and speak a little slower.")
        }
    }

    fun startVoice(forChat: Boolean = false) {
        voiceForChat = forChat
        val hints = buildList {
            addAll(entries.take(30).flatMap { listOf(it.item, it.person) })
            addAll(listOf(
                "kharcha", "aamdani", "pese", "paise", "payment", "receive", "received", "nahi mili",
                "nahi hui", "stock", "sale", "purchase", "udhaar", "cash", "bank", "salary", "petrol",
                "kiraya", "grocery", "entry", "last entry", "pichli entry", "change entry", "badal do"
            ))
        }.map { it.trim() }.filter { it.length in 2..60 }.distinct().take(100)

        val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, lang)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, lang)
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak now…")
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 10)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && hints.isNotEmpty()) {
                putExtra(RecognizerIntent.EXTRA_BIASING_STRINGS, ArrayList(hints))
            }
        }
        try {
            voice.launch(i)
        } catch (e: ActivityNotFoundException) {
            toast("Google voice not found. Update the Google app from Play Store.")
        }
    }

    var updateMsg by remember { mutableStateOf<String?>(null) }

    fun startUpdate() {
        val info = newVersion ?: return
        if (Updater.needsPermission(context)) {
            updateMsg = "Allow \"Install unknown apps\" for Smart Expense, then come back and tap Update now."
            Updater.openPermissionSettings(context)
            return
        }
        scope.launch {
            updateMsg = null
            progress = 0
            val file = Updater.download(context, info) { progress = it }
            progress = -1
            if (file != null) {
                Updater.install(context, file)
            } else {
                updateMsg = "Download failed. Check your internet and tap Update now again."
            }
        }
    }

    val drawer = rememberDrawerState(DrawerValue.Closed)
    fun openMenu() {
        scope.launch { drawer.open() }
    }

    BackHandler(enabled = results != null || people != null || tab != Tab.HOME) {
        if (results != null) {
            results = null
            answerText = null
        } else if (people != null) {
            people = null
        } else {
            tab = Tab.HOME
        }
    }

    ModalNavigationDrawer(
        drawerState = drawer,
        drawerContent = {
            DrawerMenu(tab, name, currency, onCurrency = { c ->
                currency = c
                store.currency = c
                toast("✓ Currency set to $c")
            }) { t ->
                if (t == Tab.REPORTS) reportStart = ReportKind.CASH_OUT
                tab = t
                people = null
                results = null
                scope.launch { drawer.close() }
            }
        }
    ) {
    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            val main = results == null && people == null
            if (main && tab == Tab.CHAT) {
                InputBar(
                    onSend = { chatInput(it) }, onMic = { startVoice(forChat = true) }, placeholder = "Speak, type or ask…",
                    attachOptions = listOf(
                        "Statement, receipt or bill (photo / PDF)" to { pickFile.launch(arrayOf("image/*", "application/pdf")) },
                        "Bank SMS · last month" to { askSms(31) },
                        "Bank SMS · last 3 months" to { askSms(92) },
                        "Bank SMS · last 6 months" to { askSms(183) }
                    )
                )
            } else {
                InputBar(onSend = { handleText(it) }, onMic = { startVoice() })
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            val who = people
            when {
                results != null -> {
                    val (q, editMode) = results!!
                    ResultsScreen(
                        query = q, editMode = editMode, entries = entries, currency = currency,
                        onBack = { results = null; answerText = null },
                        onEdit = { editing = it },
                        onDeleteAll = { list -> askDelete(q, list) },
                        answer = answerText
                    )
                }
                who != null -> PeopleScreen(entries, currency, who, onBack = { people = null }, onEdit = { editing = it })
                tab == Tab.HOME -> HomeScreen(
                    name = name, entries = entries, currency = currency,
                    newVersion = null, progress = progress, onUpdate = { startUpdate() },
                    onAdd = { adding = true }, onMenu = { openMenu() }, onSearch = { searching = true }, onEdit = { editing = it },
                    onPeople = { people = it }, onReports = { reportStart = it; tab = Tab.REPORTS }, onSeeAll = { tab = Tab.ENTRIES },
                    chatBadge = suggestions.size, onTab = { tab = it }
                )
                tab == Tab.STOCK -> StockScreen(
                    entries = entries, currency = currency, chatBadge = suggestions.size,
                    onTab = { tab = it }, onMenu = { openMenu() },
                    onTrade = { trade = it }, onReports = { reportStart = it; tab = Tab.REPORTS }
                )
                tab == Tab.ENTRIES -> TransactionsScreen(entries, currency, onEdit = { editing = it }, onAdd = { adding = true }, onMenu = { openMenu() })
                tab == Tab.REPORTS -> ReportsScreen(entries, currency, onMenu = { openMenu() }, startKind = reportStart)
                tab == Tab.TRASH -> TrashScreen(
                    trash = trash, currency = currency, onMenu = { openMenu() },
                    onRestore = { e ->
                        save(listOf(e) + entries)
                        saveTrash(trash.filter { it.id != e.id })
                        toast("✓ Restored")
                    },
                    onRestoreAll = {
                        save(trash + entries)
                        saveTrash(emptyList())
                        toast("✓ All restored")
                    },
                    onEmpty = {
                        saveTrash(emptyList())
                        toast("Trash emptied")
                    }
                )
                tab == Tab.CHAT -> {
                    val monthStart = java.util.Calendar.getInstance().apply {
                        set(java.util.Calendar.DAY_OF_MONTH, 1)
                        set(java.util.Calendar.HOUR_OF_DAY, 0)
                        set(java.util.Calendar.MINUTE, 0)
                        set(java.util.Calendar.SECOND, 0)
                        set(java.util.Calendar.MILLISECOND, 0)
                    }.timeInMillis
                    val monthSpent = entries.filter { it.date >= monthStart && it.type == Type.KHARCHA }.sumOf { it.amount }
                    val monthName = java.text.SimpleDateFormat("MMM", java.util.Locale.US).format(java.util.Date())
                    val quick = remember(entries) {
                        entries
                            .filter { it.type == Type.KHARCHA && it.amount > 0 && it.date >= System.currentTimeMillis() - 60L * 86_400_000L }
                            .groupBy { quickLabel(it).lowercase() + "|" + it.amount }
                            .entries.sortedByDescending { it.value.size }
                            .take(4).map { g -> g.value.maxBy { it.date } }
                    }
                    ChatScreen(
                        messages = chatMessages,
                        loading = chatLoading,
                        entries = entries,
                        currency = currency,
                        subtitle = "$monthName · ${fmt(monthSpent)} $currency spent",
                        suggestions = suggestions,
                        quick = quick,
                        showStock = entries.any { isTrade(it.type) },
                        onTab = { tab = it },
                        onMenu = { openMenu() },
                        onSend = { chatInput(it) },
                        onClear = {
                            chatMessages = emptyList()
                            store.saveChatMessages(emptyList())
                            toast("Chat history cleared")
                        },
                        onUndo = { ids -> moveToTrash(ids.toSet()) },
                        onEditEntry = { editing = it },
                        onAddSuggestions = { list ->
                            val added = list.mapIndexed { i, e -> e.copy(id = "e" + System.nanoTime() + i) }
                            save(added + entries)
                            dropSuggestions(list.map { it.id }.toSet())
                            postChat(
                                "✓ " + added.size + " bank " + (if (added.size == 1) "payment" else "payments") + " added",
                                isUser = false,
                                entryIds = added.map { it.id }
                            )
                        },
                        onIgnoreSuggestions = { ids -> dropSuggestions(ids) },
                        onQuick = { quickAdd(it) },
                        imports = imports,
                        importDupes = importDupes,
                        onAddImports = { list ->
                            val added = list.mapIndexed { i, e -> e.copy(id = "e" + System.nanoTime() + i) }
                            save(added + entries)
                            imports = emptyList()
                            importDupes = emptySet()
                            postChat("✓ Added from your file", isUser = false, entryIds = added.map { it.id })
                        },
                        onDropImports = {
                            imports = emptyList()
                            importDupes = emptySet()
                        }
                    )
                }
                else -> SettingsScreen(
                    name = name, currency = currency, lang = lang, dark = dark,
                    version = BuildConfig.VERSION_NAME, checking = checking,
                    onName = { name = it; store.name = it; toast("✓ Name saved") },
                    onCurrency = { currency = it; store.currency = it },
                    onLang = { lang = it; store.language = it },
                    onDark = onDark,
                    onCheckUpdate = {
                        scope.launch {
                            checking = true
                            newVersion = Updater.check()
                            checking = false
                            if (newVersion == null) {
                                toast("You have the latest version")
                            } else {
                                tab = Tab.HOME
                            }
                        }
                    },
                    onMenu = { openMenu() },
                    themeName = themeName,
                    onTheme = onTheme,
                    bankOn = bankOn,
                    onBank = { bankHelp = true },
                    aiKey = aiKey,
                    aiBuiltIn = BuildConfig.GEMINI_KEY.isNotBlank(),
                    onAiKey = { k ->
                        aiKey = k
                        store.aiKey = k
                        if (k.isBlank()) {
                            toast(if (BuildConfig.GEMINI_KEY.isNotBlank()) "Using the built-in AI" else "AI turned off")
                        } else {
                            toast("Checking key…")
                            scope.launch {
                                val err = Ai.test(k, store)
                                toast(if (err == null) "✓ AI is ready" else err)
                            }
                        }
                    }
                )
            }
        }
    }
    }

    // Ask about anything the parser did not understand, one at a time.
    val ask = pending.firstOrNull()
    if (ask != null && editing == null && !adding && moreFor == null) {
        AskDialog(
            entry = ask,
            currency = currency,
            onPick = { picked ->
                val e = picked.copy(note = Parser.cleanNote(picked.note, picked.person).ifBlank { picked.item })
                save(listOf(e) + entries)
                pending = pending.drop(1)
                toast("✓ Saved: " + entryTitle(e) + " " + fmt(e.amount))
            },
            onMore = {
                pending = pending.drop(1)
                editing = null
                moreFor = ask
            },
            onSkip = { pending = pending.drop(1); toast("Skipped") }
        )
    }

    // "More options" from the quick question opens the full form.
    moreFor?.let { m ->
        FormPopup(onDismiss = { moreFor = null }) {
            EntryForm(
                initial = m,
                title = "What is this?",
                question = "You said: \"${m.note}\"",
                askType = true,
                currency = currency,
                saveLabel = "Save",
                onSave = { e ->
                    learnFrom(null, e)
                    save(listOf(e) + entries)
                    moreFor = null
                    toast("✓ Saved")
                },
                onDelete = null,
                onCancel = { moreFor = null }
            )
        }
    }

    if (searching) {
        var q by remember { mutableStateOf("") }
        fun go() {
            val t = q.trim()
            if (t.isNotEmpty()) {
                searching = false
                people = null
                results = t to false
            }
        }
        FormPopup(onDismiss = { searching = false }) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Search", fontSize = 19.sp, fontWeight = FontWeight.Bold)
                OutlinedTextField(
                    value = q, onValueChange = { q = it },
                    placeholder = { Text("Search by name, item or category") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    singleLine = true, shape = RoundedCornerShape(14.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { go() }),
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { searching = false }, shape = RoundedCornerShape(14.dp)) { Text("Cancel") }
                    Button(
                        onClick = { go() }, enabled = q.isNotBlank(),
                        shape = RoundedCornerShape(14.dp), modifier = Modifier.weight(1f)
                    ) { Text("Search") }
                }
            }
        }
    }

    // A new version must be installed before the app can be used. No skip.
    newVersion?.let { info ->
        Dialog(
            onDismissRequest = {},
            properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
        ) {
            Surface(shape = RoundedCornerShape(26.dp), color = MaterialTheme.colorScheme.surface, shadowElevation = 12.dp) {
                Column(
                    Modifier.padding(24.dp).fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        Modifier.size(60.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) { Text("⬆", fontSize = 28.sp, color = MaterialTheme.colorScheme.primary) }
                    Text("Update available", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text(
                        "Version ${info.version} is ready. Your entries stay safe.",
                        fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    if (progress >= 0) {
                        LinearProgressIndicator(
                            progress = { progress / 100f },
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp))
                        )
                        Text("Downloading… $progress%", fontSize = 13.sp)
                    }
                    updateMsg?.let {
                        Text(
                            it, fontSize = 13.sp, color = RedSpend,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                    Button(
                        onClick = { startUpdate() },
                        enabled = progress < 0,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth().height(48.dp)
                    ) { Text("Update now", fontWeight = FontWeight.Bold) }
                }
            }
        }
    }

    if (bankHelp) {
        AlertDialog(
            onDismissRequest = { bankHelp = false },
            title = { Text("Turn on bank alerts") },
            text = {
                Text(
                    "On the next screen, find Smart Expense and switch it on.\n\n" +
                        "If Android says \"Restricted setting\": open Settings → Apps → Smart Expense → ⋮ (top right) → " +
                        "Allow restricted settings, then try again.\n\n" +
                        "Only payment messages are read, and nothing leaves your phone."
                )
            },
            confirmButton = {
                Button(onClick = {
                    bankHelp = false
                    try {
                        context.startActivity(
                            Intent(android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    } catch (e: Exception) {
                        toast("Open Settings → Notifications → Device & app notifications")
                    }
                }) { Text("Open settings") }
            },
            dismissButton = { TextButton(onClick = { bankHelp = false }) { Text("Later") } }
        )
    }

    if (reviewing) {
        FormPopup(onDismiss = { reviewing = false }) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Bank payments", fontSize = 19.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    TextButton(onClick = { reviewing = false }) { Text("Close") }
                }
                if (suggestions.isEmpty()) {
                    Text("All done. New payments will show up here.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Column(
                    Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    suggestions.forEach { e ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CatBubble(e.category, 38.dp)
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(e.note, fontWeight = FontWeight.SemiBold, maxLines = 1)
                                Text(
                                    entryTitle(e) + " · " + dayLabel(e.date) + " " + timeLabel(e.date),
                                    fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1
                                )
                                Text(moneyA(currency, e.amount, prefix(e.type)), color = signColor(e.type), fontWeight = FontWeight.Bold)
                            }
                            IconButton(onClick = { dropSuggestions(setOf(e.id)) }) {
                                Text("✕", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 18.sp)
                            }
                            IconButton(onClick = {
                                save(listOf(e.copy(id = "e" + System.nanoTime())) + entries)
                                dropSuggestions(setOf(e.id))
                                toast("✓ Added " + fmt(e.amount))
                            }) { Text("✓", color = GreenIn, fontSize = 22.sp, fontWeight = FontWeight.Bold) }
                        }
                    }
                }
                if (suggestions.size > 1) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { dropSuggestions(suggestions.map { it.id }.toSet()) },
                            shape = RoundedCornerShape(14.dp)
                        ) { Text("Ignore all") }
                        Button(
                            onClick = {
                                val n = suggestions.size
                                save(suggestions.mapIndexed { i, e -> e.copy(id = "e" + System.nanoTime() + i) } + entries)
                                dropSuggestions(suggestions.map { it.id }.toSet())
                                toast("✓ Added $n payments")
                                reviewing = false
                            },
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.weight(1f)
                        ) { Text("Add all ${suggestions.size}") }
                    }
                }
            }
        }
    }

    confirm?.let { (msg, ids) ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text("Are you sure?") },
            text = { Text(msg) },
            confirmButton = {
                Button(
                    onClick = {
                        confirm = null
                        moveToTrash(ids)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = RedSpend)
                ) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text("Cancel") } }
        )
    }

    trade?.let { t ->
        FormPopup(onDismiss = { trade = null }) {
            EntryForm(
                initial = t,
                title = if (t.type == Type.SALE) "Sold " + t.item else "Bought " + t.item,
                question = null,
                askType = false,
                currency = currency,
                saveLabel = "Save",
                onSave = { e ->
                    save(listOf(e) + entries)
                    trade = null
                    toast("✓ Saved")
                },
                onDelete = null,
                onCancel = { trade = null }
            )
        }
    }

    editing?.let { e ->
        FormPopup(onDismiss = { editing = null }) {
            EntryForm(
                initial = e,
                title = "Edit entry",
                question = null,
                askType = false,
                currency = currency,
                saveLabel = "Save",
                onSave = { changed ->
                    learnFrom(e, changed)
                    save(entries.map { if (it.id == changed.id) changed else it })
                    editing = null
                    toast("✓ Saved")
                },
                onDelete = {
                    editing = null
                    moveToTrash(setOf(e.id))
                },
                onCancel = { editing = null }
            )
        }
    }

    if (adding) {
        val fresh = remember { blankEntry() }
        var quick by remember { mutableStateOf("") }
        FormPopup(onDismiss = { adding = false }) {
            EntryForm(
                initial = fresh,
                title = "Add manually",
                question = null,
                askType = false,
                currency = currency,
                saveLabel = "Save entry",
                onSave = { e ->
                    save(listOf(e) + entries)
                    adding = false
                    toast("✓ Added")
                },
                onDelete = null,
                onCancel = { adding = false },
                header = {
                    Text("Quick add", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = quick, onValueChange = { quick = it },
                            placeholder = { Text("Type like you speak: petrol 50") },
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(Modifier.width(8.dp))
                        Button(onClick = {
                            val q = quick
                            quick = ""
                            adding = false
                            handleText(q)
                        }, enabled = quick.isNotBlank()) { Text("Add") }
                    }
                    HorizontalDivider(Modifier.padding(vertical = 6.dp))
                }
            )
        }
    }
}

@Composable
private fun InputBar(
    onSend: (String) -> Unit,
    onMic: () -> Unit,
    placeholder: String = "Type here…",
    attachOptions: List<Pair<String, () -> Unit>>? = null
) {
    var attachMenu by remember { mutableStateOf(false) }
    var text by rememberSaveable { mutableStateOf("") }
    fun send() {
        val t = text.trim()
        if (t.isNotEmpty()) {
            text = ""
            onSend(t)
        }
    }
    Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 16.dp) {
        Row(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (attachOptions != null) {
                Box {
                    IconButton(onClick = { attachMenu = true }) {
                        Icon(
                            Icons.Filled.AttachFile, contentDescription = "Import a statement, bill or bank SMS",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    DropdownMenu(expanded = attachMenu, onDismissRequest = { attachMenu = false }) {
                        attachOptions.forEach { (label, action) ->
                            DropdownMenuItem(text = { Text(label) }, onClick = { attachMenu = false; action() })
                        }
                    }
                }
                Spacer(Modifier.width(2.dp))
            }
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                placeholder = { Text(placeholder) },
                modifier = Modifier.weight(1f),
                maxLines = 3,
                shape = RoundedCornerShape(26.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedBorderColor = Color.Transparent,
                    focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { send() })
            )
            Spacer(Modifier.width(10.dp))
            Box(
                Modifier
                    .size(56.dp)
                    .shadow(10.dp, CircleShape, spotColor = Green, ambientColor = Green)
                    .clip(CircleShape)
                    .background(AccentBrush)
                    .clickable { if (text.isNotBlank()) send() else onMic() },
                contentAlignment = Alignment.Center
            ) {
                if (text.isNotBlank()) {
                    Icon(
                        Icons.AutoMirrored.Filled.Send, contentDescription = "Send",
                        tint = Color.White, modifier = Modifier.size(26.dp)
                    )
                } else {
                    MicGlyph(Color.White, 28.dp)
                }
            }
        }
    }
}

@Composable
private fun DrawerMenu(tab: Tab, name: String, currency: String, onCurrency: (String) -> Unit, onPick: (Tab) -> Unit) {
    var currencyOpen by remember { mutableStateOf(false) }
    ModalDrawerSheet(
        modifier = Modifier.width(250.dp),
        drawerContainerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .background(LocalHeader.current.brush)
                .padding(20.dp)
        ) {
            Text("Smart Expense", color = LocalHeader.current.on, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
            Text(
                if (name.isBlank()) "Speak it. Track it." else "Hello, ${name.trim()}",
                color = LocalHeader.current.on.copy(alpha = 0.8f)
            )
        }
        Spacer(Modifier.height(8.dp))
        val itemPad = Modifier.padding(horizontal = 12.dp)
        NavigationDrawerItem(
            label = { Text("Home") }, selected = tab == Tab.HOME, onClick = { onPick(Tab.HOME) },
            icon = { Icon(Icons.Filled.Home, contentDescription = null) }, modifier = itemPad
        )
        NavigationDrawerItem(
            label = { Text("Chat") }, selected = tab == Tab.CHAT, onClick = { onPick(Tab.CHAT) },
            icon = { Text("💬", fontSize = 20.sp) }, modifier = itemPad
        )
        NavigationDrawerItem(
            label = { Text("Entries") }, selected = tab == Tab.ENTRIES, onClick = { onPick(Tab.ENTRIES) },
            icon = { Icon(Icons.AutoMirrored.Filled.List, contentDescription = null) }, modifier = itemPad
        )
        NavigationDrawerItem(
            label = { Text("Reports") }, selected = tab == Tab.REPORTS, onClick = { onPick(Tab.REPORTS) },
            icon = { ChartGlyph(LocalContentColor.current) }, modifier = itemPad
        )
        NavigationDrawerItem(
            label = { Text("Trash") }, selected = tab == Tab.TRASH, onClick = { onPick(Tab.TRASH) },
            icon = { Icon(Icons.Filled.Delete, contentDescription = null) }, modifier = itemPad
        )
        NavigationDrawerItem(
            label = { Text("Settings") }, selected = tab == Tab.SETTINGS, onClick = { onPick(Tab.SETTINGS) },
            icon = { Icon(Icons.Filled.Settings, contentDescription = null) }, modifier = itemPad
        )
        HorizontalDivider(Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
        NavigationDrawerItem(
            label = { Text("Currency") },
            badge = {
                Text(
                    "$currency ▾", fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            },
            selected = false,
            onClick = { currencyOpen = !currencyOpen },
            icon = { Text("💱", fontSize = 20.sp) },
            modifier = itemPad
        )
        if (currencyOpen) {
            Column(
                Modifier
                    .padding(horizontal = 24.dp)
                    .heightIn(max = 260.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                CURRENCIES.forEach { c ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable {
                                onCurrency(c)
                                currencyOpen = false
                            }
                            .padding(horizontal = 12.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(c, Modifier.weight(1f), fontWeight = if (c == currency) FontWeight.Bold else FontWeight.Normal)
                        if (c == currency) Text("✓", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

/** A friendly one-tap question for things the app did not understand. */
@Composable
private fun AskDialog(entry: Entry, currency: String, onPick: (Entry) -> Unit, onMore: () -> Unit, onSkip: () -> Unit) {
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
