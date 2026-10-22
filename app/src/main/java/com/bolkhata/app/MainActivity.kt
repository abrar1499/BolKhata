package com.bolkhata.app

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
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

enum class Tab { HOME, ENTRIES, REPORTS, TRASH, SETTINGS }

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
    var people by remember { mutableStateOf<Boolean?>(null) }   // true = to receive, false = to pay
    var editing by remember { mutableStateOf<Entry?>(null) }
    var adding by remember { mutableStateOf(false) }
    var pending by remember { mutableStateOf(listOf<Entry>()) }
    var moreFor by remember { mutableStateOf<Entry?>(null) }
    var searching by remember { mutableStateOf(false) }
    var suggestions by remember { mutableStateOf(store.loadSuggestions()) }
    var reviewing by remember { mutableStateOf(false) }
    var bankOn by remember { mutableStateOf(BankListener.isEnabled(context)) }
    var bankHelp by remember { mutableStateOf(false) }

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

    fun toast(msg: String) {
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
                if (entries.isEmpty()) toast("Khata is already empty")
                else confirm = "Delete ALL ${entries.size} entries? This clears the whole khata." to entries.map { it.id }.toSet()
            }
        }
        return true
    }

    var aiKey by remember { mutableStateOf(store.aiKey) }
    // The person's own key wins; otherwise the key built into the app is used.
    val activeKey = aiKey.ifBlank { BuildConfig.GEMINI_KEY }

    // Keep the offline reader up to date with what the person taught it and who is in the khata.
    LaunchedEffect(Unit) { Parser.learned = store.loadLearned() }
    LaunchedEffect(entries) {
        Parser.knownPeople = entries.mapNotNull { e -> e.person.trim().lowercase().takeIf { it.length >= 3 } }.toSet()
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

    /** "hamza wali 500 ko 400 kar do", "last entry food mein daal do", "kal ka petrol 60 tha 50 nahi". */
    fun looksLikeEdit(text: String): Boolean {
        val t = " " + text.lowercase() + " "
        val strong = listOf(
            "change", "badal", "badlo", "update", "correct", "galat", "ghalat", "ki jagah", "ke bajaye", "instead",
            "should be", "make it", "nahi tha", "nahi thi", "nahin tha", "fix", "theek kar", "thik kar", "sahi kar", " edit "
        )
        val soft = listOf("kar do", "kardo", "kr do", "krdo", "daal do", "dal do", "daldo", "kar dein", "kardein")
        val refers = listOf("entry", "wali", "wala", "wale", "last", "pichli", "pichhli", "akhri", "aakhri", "category", "previous")
        return strong.any { t.contains(it) } || (soft.any { t.contains(it) } && refers.any { t.contains(it) })
    }

    fun handleText(text: String) {
        if (handleCommand(text)) return
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
        val unsure = r.unclear.isNotEmpty() || r.entries.isEmpty() ||
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

    val voice = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        if (res.resultCode == Activity.RESULT_OK) {
            val said = res.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull().orEmpty()
            if (said.isNotBlank()) handleText(said)
        }
    }

    fun startVoice() {
        val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, lang)
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak now…")
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
                tab = t
                people = null
                results = null
                scope.launch { drawer.close() }
            }
        }
    ) {
    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = { InputBar(onSend = { handleText(it) }, onMic = { startVoice() }) },
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
                        onBack = { results = null },
                        onEdit = { editing = it },
                        onDeleteAll = { list -> askDelete(q, list) }
                    )
                }
                who != null -> PeopleScreen(entries, currency, who, onBack = { people = null }, onEdit = { editing = it })
                tab == Tab.HOME -> HomeScreen(
                    name = name, entries = entries, currency = currency,
                    newVersion = null, progress = progress, onUpdate = { startUpdate() },
                    onAdd = { adding = true }, onMenu = { openMenu() }, onSearch = { searching = true }, onEdit = { editing = it },
                    onPeople = { people = it }, onReports = { tab = Tab.REPORTS }, onSeeAll = { tab = Tab.ENTRIES },
                    bankCount = suggestions.size, onReviewBank = { reviewing = true }
                )
                tab == Tab.ENTRIES -> TransactionsScreen(entries, currency, onEdit = { editing = it }, onAdd = { adding = true }, onMenu = { openMenu() })
                tab == Tab.REPORTS -> ReportsScreen(entries, currency, onMenu = { openMenu() })
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
            onPick = { e ->
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

    editing?.let { e ->
        FormPopup(onDismiss = { editing = null }) {
            EntryForm(
                initial = e,
                title = "Edit entry",
                question = "You said: \"${e.note}\"",
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
private fun InputBar(onSend: (String) -> Unit, onMic: () -> Unit) {
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
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                placeholder = { Text("Type here…") },
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
    Dialog(onDismissRequest = onSkip) {
        Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surface, shadowElevation = 12.dp) {
            Column(Modifier.padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier.size(56.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) { Text("🤔", fontSize = 28.sp) }
                Spacer(Modifier.height(12.dp))
                Text(moneyA(currency, entry.amount), fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
                Text(
                    "\"${entry.note}\"",
                    color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                Spacer(Modifier.height(6.dp))
                Text("What is this?", fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ChoiceTile("Expense", "I spent it", "−", RedSpend, Modifier.weight(1f)) {
                        onPick(entry.copy(type = Type.KHARCHA, category = Cat.OTHER, item = "Other", person = ""))
                    }
                    ChoiceTile("Income", "I got paid", "+", GreenIn, Modifier.weight(1f)) {
                        onPick(entry.copy(type = Type.AAMDANI, category = Cat.TANKHWAH, item = "Income", person = ""))
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
