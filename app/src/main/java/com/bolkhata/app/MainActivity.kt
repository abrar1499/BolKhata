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
import androidx.core.content.FileProvider
import java.io.File
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
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
    var homeChatFlash by remember { mutableStateOf(false) }
    var loadingLabel by remember { mutableStateOf("Thinking…") }
    var voiceOn by remember { mutableStateOf(false) }
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
    // The account opened by tapping an entry (People › Shahbaz, Transport › Aldrees…).
    var account by remember { mutableStateOf<AccountKey?>(null) }
    // The main account opened from the home screen, and the entry whose long-press menu / move dialog is open.
    var ledger by remember { mutableStateOf<MainAcct?>(null) }
    var menuFor by remember { mutableStateOf<Entry?>(null) }
    var ledgerPerson by remember { mutableStateOf<String?>(null) }
    var activeLedgerPerson by remember { mutableStateOf<String?>(null) }
    // Smart understanding: entries the local engine read (shown before saving), a name to pick, what was just added (Undo),
    // the entry being edited from the card (to learn from), and words the person corrected before.
    var confirmNew by remember { mutableStateOf<Pair<List<Entry>, String>?>(null) }
    var askWho by remember { mutableStateOf<Understand.Result?>(null) }
    // A shared bill said without (all) the names: waiting for "ali aur bilal".
    var splitAsk by remember { mutableStateOf<Split.Plan?>(null) }
    // The small strip above the home input: the corrected sentence, or the answer to a question / hello.
    var flash by remember { mutableStateOf<Flash?>(null) }
    // "Other wali entries theek karo": the list of shops to sort, before anything changes.
    var recat by remember { mutableStateOf<List<Recat.Group>?>(null) }
    // The last thing said and the app's answer, so "wo sab expense me dal do" knows what "wo" is.
    var lastTalk by remember { mutableStateOf("") }
    var lastAdded by remember { mutableStateOf(listOf<String>()) }
    var editFrom by remember { mutableStateOf<Pair<Entry, String>?>(null) }
    var wordFixes by remember { mutableStateOf(store.loadWordFixes()) }
    var moving by remember { mutableStateOf<Entry?>(null) }
    // The entries before a chat message was handled; what changes after that is reported in the chat.
    var chatWatch by remember { mutableStateOf<List<Entry>?>(null) }
    var chatSkippedDuplicates by remember { mutableIntStateOf(0) }
    var voiceForChat by remember { mutableStateOf(false) }
    // The assistant's last reply as it should be spoken (voice conversation).
    var lastSpeech by remember { mutableStateOf("") }
    // Transactions read from an attached statement, receipt or bill, waiting to be ticked and added.
    var imports by remember { mutableStateOf(listOf<Entry>()) }
    // Which report opens: Expense opens Cash Out, Income opens Cash In.
    var reportStart by remember { mutableStateOf(ReportKind.CASH_OUT) }
    var importDupes by remember { mutableStateOf(setOf<String>()) }
    // For each imported row that matches a saved entry: the saved entry's id.
    var importMatch by remember { mutableStateOf(mapOf<String, String>()) }
    // Changes the local engine proposes, shown for the person to Apply or Cancel.
    var pendingPlan by remember { mutableStateOf<EditPlan?>(null) }

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
    var confirmEmptyTrash by remember { mutableStateOf(false) }
    var showMoveHistory by remember { mutableStateOf(false) }
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

    /** A command inside an open khata must never silently act on another account. */
    fun scopeKhataEntries(list: List<Entry>): List<Entry> {
        val person = if (ledger != null) ledgerPerson ?: activeLedgerPerson
            else account?.takeIf { it.main == PEOPLE }?.sub
        return if (person == null) list
        else list.filter { it.person.equals(person, ignoreCase = true) }
    }

    /** Rename and merge use the same reversible move log; merging requires an existing destination. */
    fun renameOrMergeKhata(oldName: String, newName: String, merge: Boolean) {
        val from = oldName.trim()
        val to = newName.trim()
        if (from.isEmpty() || to.isEmpty() || from.equals(to, ignoreCase = true)) return
        val source = entries.filter { it.person.equals(from, ignoreCase = true) }
        if (source.isEmpty()) { toast("Source khata not found"); return }
        val targetExists = entries.any { it.person.equals(to, ignoreCase = true) }
        if (merge && !targetExists) { toast("Choose an existing destination khata"); return }
        if (!merge && targetExists) { toast("Khata exists. Use Move / merge."); return }
        val after = entries.map { if (it.person.equals(from, ignoreCase = true)) it.copy(person = to) else it }
        val record = store.recordMove(entries, after) ?: return
        save(after)
        scope.launch {
            val response = snackbar.showSnackbar(
                if (merge) "Merged ${source.size} entries into $to" else "Renamed khata to $to",
                actionLabel = "Undo", duration = SnackbarDuration.Long
            )
            if (response == SnackbarResult.ActionPerformed) {
                val restored = restoreMoveBatch(entries, record)
                if (restored != null) {
                    save(restored)
                    store.forgetMove(record.id)
                } else toast("Cannot undo: entries were changed after the move.")
            }
        }
    }

    /** Handles typed or spoken commands: search, edit, delete, clear all. Returns true if it was a command. */
    fun handleCommand(text: String): Boolean {
        val c = Commands.read(text) ?: return false
        when (c.action) {
            Commands.Action.SEARCH -> { results = c.query to false; people = null }
            Commands.Action.EDIT -> { results = c.query to true; people = null }
            Commands.Action.DELETE -> askDelete(c.query, scopeKhataEntries(matchEntries(entries, c.query)))
            Commands.Action.DELETE_PERIOD -> {
                val period = c.period ?: return false
                askDelete(period.label(), scopeKhataEntries(matchEntriesByPeriod(entries, period)))
            }
            Commands.Action.CLEAR_ALL -> {
                val targets = scopeKhataEntries(entries)
                if (targets.isEmpty()) toast("There are no entries to delete")
                else confirm = "Delete ALL ${targets.size} entries in this view?" to targets.map { it.id }.toSet()
            }
        }
        return true
    }

    // Keep the offline reader up to date with what the person taught it and who is in the khata.
    LaunchedEffect(Unit) { Parser.learned = store.loadLearned() }
    LaunchedEffect(entries) {
        Parser.knownPeople = entries.mapNotNull { e -> e.person.trim().lowercase().takeIf { it.length >= 3 } }.toSet()
        Parser.owed = balances(entries).mapKeys { it.key.lowercase() }
    }

    /** When the person fixes a category, remember the words so it is right next time. */
    /** Names the person has entries with: people in the khata and names on transfers. */
    fun contactNames(): List<String> = (personAccounts(entries).map { it.name } + entries.mapNotNull { e ->
        e.note.trim().takeIf { it.startsWith("To ") || it.startsWith("From ") }?.substringAfter(' ')?.trim()
    }).mapNotNull { cleanPersonName(it) }.distinctBy { it.lowercase() }

    fun learnFrom(before: Entry?, after: Entry) {
        if (after.type != Type.KHARCHA || after.category == Cat.OTHER) return
        if (before != null && before.category == after.category && before.item == after.item) return
        val words = Parser.learnWords(after.note)
        if (words.isEmpty()) return
        store.learn(words, after.category, after.item)
        Parser.learned = store.loadLearned()
    }

    fun contextualizeCurrentKhata(list: List<Entry>): List<Entry> =
        applyKhataContext(list, if (ledger != null) ledgerPerson ?: activeLedgerPerson else account?.takeIf { it.main == PEOPLE }?.sub)

    fun addOffline(r: Parser.Result) {
        val contextualEntries = contextualizeCurrentKhata(r.entries)
        val contextualUnclear = contextualizeCurrentKhata(r.unclear)
        if (contextualEntries.isEmpty() && contextualUnclear.isEmpty()) {
            flash = Flash("How much? e.g. \"petrol 70\"", Flash.Kind.ANSWER)
            return
        }
        if (contextualEntries.isNotEmpty()) {
            val deduplicated = BankTransactionDeduplicator.filterNew(contextualEntries, entries)
            val freshEntries = deduplicated.fresh
            val skippedDuplicates = deduplicated.skipped
            if (freshEntries.isEmpty() && skippedDuplicates > 0) {
                val message = "This exact bank transaction is already saved. I didn't add it again."
                chatWatch = null
                toast(message)
                return
            }
            if (freshEntries.isNotEmpty()) {
                save(freshEntries + entries)
                lastAdded = freshEntries.map { it.id }
                chatSkippedDuplicates = if (chatWatch != null) skippedDuplicates else 0
                val ids = lastAdded.toSet()
                scope.launch {
                    snackbar.currentSnackbarData?.dismiss()
                    val suffix = if (skippedDuplicates > 0) " · skipped $skippedDuplicates duplicate bank alert(s)" else ""
                    val res = snackbar.showSnackbar(
                        "✓ Added " + freshEntries.joinToString(", ") { understoodLine(it, currency) } + suffix,
                        actionLabel = "Undo", duration = SnackbarDuration.Short
                    )
                    if (res == SnackbarResult.ActionPerformed) moveToTrash(ids)
                }
            }
        }
        if (contextualUnclear.isNotEmpty()) pending = pending + contextualUnclear
    }

    /**
     * Reads entries locally. Unclear amounts or categories are shown for review; no remote model is called.
     */
    /** Applies a local change only while every target still matches the exact snapshot it was based on. */
    fun applyPlan(plan: EditPlan): Boolean {
        val expected = plan.changed.map { it.first } + plan.deleted
        if (expected.isEmpty() || !Accounts.snapshotsMatch(entries, expected)) return false
        val beforeEntries = entries
        val beforeTrash = trash
        val map = plan.changed.associate { it.first.id to it.second }
        val gone = plan.deleted.map { it.id }.toSet()
        val afterEntries = entries.filter { it.id !in gone }.map { map[it.id] ?: it }
        val moveRecord = store.recordMove(beforeEntries, afterEntries)
        save(afterEntries)
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
                moveRecord?.let { store.forgetMove(it.id) }
            }
        }
        return true
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
            if (!applyPlan(EditPlan(listOf(target to updated), undone, msg))) {
                toast("This transaction changed before the update could be applied. Nothing was changed.")
            }
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

    /**
     * The AI rewrites a sentence the app could not read into the app's own simple words; the app then reads it
     * again (no second AI call). Word fixes are remembered, so next time no AI is needed at all.
     */
    /** Words the local engine fixed ("ptrl" -> "petrol") are remembered, so the app reads them itself next time. */
    fun learnWordFixes(said: String, fixed: String) {
        val a = said.lowercase().split(Regex("[^\\p{L}]+")).filter { it.length >= 3 }
        val b = fixed.lowercase().split(Regex("[^\\p{L}]+")).filter { it.length >= 3 }
        var any = false
        a.filter { it !in b }.forEach { w ->
            val best = b.filter { it !in a }.maxByOrNull { Normalizer.similarity(w, it) } ?: return@forEach
            if (Normalizer.similarity(w, best) >= 0.75) { store.addWordFix(w, best); any = true }
        }
        if (any) wordFixes = store.loadWordFixes()
    }

    /**
     * The "Sort Other" list. Shops the app knows get their category straight away; for names it doesn't know
     * ("Atheer Meed Taiba", "Kholoud Rajeh") only the names go to the local engine once, and the person checks the list.
     */
    /** Sorts with the app's local merchant/category rules; unknown shops stay available for manual review. */
    fun openRecat() {
        recat = Recat.plan(entries)
        val unknown = recat.orEmpty().count { it.guess == Cat.OTHER }
        flash = Flash(
            if (unknown == 0) "All recognized shops are categorized. Review and tap Apply."
            else "$unknown shops need your category choice. Nothing was changed yet.",
            Flash.Kind.ANSWER
        )
    }

    // handleText is written further down; this lets the local engine-fix step hand the fixed sentence back to it.
    var readAgain: (String, String) -> Unit = { _, _ -> }
    // chatViaReader is written further down too.
    var chatRead: (String) -> Unit = { }

    fun handleTextSafely(text: String, clock: Understand.Clock? = null, original: String? = null) {
        // "Other wali entries theek karo": the app sorts them itself (the local engine only fixed the wording).
        if (Recat.isCommand(text)) { openRecat(); return }
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
                    flash = Flash(a.text, Flash.Kind.ANSWER)
                }
                return
            }
            flash = Flash("I couldn't match that question to saved records. Try 'food this month', 'last 5 transactions', or 'Ali ka balance'.", Flash.Kind.ANSWER)
            return
        }
        // Simple changes (date, amount) are done offline straight away, with Undo.
        Edits.plan(text, entries)?.let {
            if (!applyPlan(it)) flash = Flash("The matching entry changed before the update could be applied. Nothing was changed.", Flash.Kind.ANSWER)
            return
        }
        if (looksLikeEdit(text) && entries.isNotEmpty()) {
            // The AI never changes entries itself: if the app's own reader couldn't place it, say so.
            flash = Flash("Which entry? e.g. \"last entry 400 karo\" or \"aaj ki sari entries delete karo\"", Flash.Kind.ANSWER)
            return
        }
        val r = Parser.parse(text).let { p ->
            if (clock == null) p else Parser.Result(p.entries.map { Understand.at(it, clock) }, p.unclear.map { Understand.at(it, clock) })
        }
        // Goods said without a price are asked about here (the rate), not sent to the local engine.
        val askRate = r.unclear.isNotEmpty() && r.unclear.all { it.qty > 0 }
        val unsure = !askRate && (r.unclear.isNotEmpty() || r.entries.isEmpty()) ||
            r.entries.any { it.type == Type.KHARCHA && it.category == Cat.OTHER }
        // No network fallback: the local parser either saves a clear entry or asks the person to review it.
        if (!unsure || r.entries.isNotEmpty() || r.unclear.isNotEmpty()) {
            if (r.entries.isNotEmpty() && (r.unclear.isNotEmpty() || r.entries.any { it.type == Type.KHARCHA && it.category == Cat.OTHER })) {
                confirmNew = (r.entries + r.unclear) to (original ?: text)
            } else addOffline(r)
            return
        }
        addOffline(r)
    }

    /** A shared bill: your share is the expense, the others' shares go in their khata. Saved with Undo. */
    fun addSplit(p: Split.Plan) {
        val list = Split.entries(p)
        if (list.isEmpty()) { flash = Flash("How much? e.g. \"petrol 70\"", Flash.Kind.ANSWER); return }
        save(list + entries)
        lastAdded = list.map { it.id }
        val ids = lastAdded.toSet()
        val mine = list.filter { it.type == Type.KHARCHA }.sumOf { it.amount }
        val others = list.filter { it.type == Type.DIYA }
        val owe = list.firstOrNull { it.type == Type.LIYA }
        val msg = "✓ " + p.bill.category.label + " " + fmt(mine) + " (your share)" +
            (if (others.isNotEmpty()) " · " + others.joinToString(" · ") { it.person + " " + fmt(it.amount) } + " to receive" else "") +
            (if (owe != null) " · " + fmt(owe.amount) + " to pay " + owe.person else "")
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            val res = snackbar.showSnackbar(msg, actionLabel = "Undo", duration = SnackbarDuration.Long)
            if (res == SnackbarResult.ActionPerformed) moveToTrash(ids)
        }
    }

    /** The names typed or said after "Who were the other 2?". */
    fun answerSplit(text: String) {
        val p = splitAsk ?: return
        val names = Split.namesIn(Translit.toRoman(text), contactNames())
        if (names.isEmpty()) { toast("Please say the names, e.g. \"Ali and Bilal\"."); return }
        val full = p.with(names)
        if (full.missing > 0) { splitAsk = full; return }
        splitAsk = null
        addSplit(full)
    }

    /** What the person meant, after "smart understanding": a new entry, or a command. */
    fun act(u: Understand.Result, original: String? = null) {
        when (u.kind) {
            Understand.Kind.DELETE_LAST -> {
                val last = entries.maxByOrNull { it.createdAt }
                if (last == null) toast("There are no entries")
                else confirm = "Delete the last entry: ${entryTitle(last)} ${fmt(last.amount)} ${currency}?" to setOf(last.id)
            }
            Understand.Kind.UNDO -> {
                val ids = lastAdded.filter { id -> entries.any { it.id == id } }
                if (ids.isEmpty()) toast("Nothing to undo") else { lastAdded = emptyList(); moveToTrash(ids.toSet()) }
            }
            Understand.Kind.OPEN_KHATA -> {
                val name = u.contact
                if (name == null) handleTextSafely(u.text, u.clock, original)
                else {
                    val p = personAccounts(entries).firstOrNull { it.name.equals(name, true) }
                    ledgerPerson = name
                    ledger = if (p == null || p.toReceive) MainAcct.TO_RECEIVE else MainAcct.TO_PAY
                }
            }
            Understand.Kind.SUMMARY -> { reportStart = ReportKind.CASH_OUT; tab = Tab.REPORTS }
            Understand.Kind.ENTRY -> {
                // "khana 30 ali bilal ke sath": a shared bill.
                val sp = Split.detect(u.text, contactNames())
                if (sp == null) handleTextSafely(u.text, u.clock, original)
                else {
                    val p = if (u.clock == null) sp else Split.Plan(Understand.at(sp.bill, u.clock), sp.payer, sp.names, sp.shares, sp.people)
                    if (p.missing != 0) splitAsk = p else addSplit(p)
                }
            }
        }
    }

    fun handleText(text: String, original: String? = null) {
        if (splitAsk != null) { answerSplit(text); return }
        try {
            // Smart understanding first (offline): spelling, numbers said as words, names, time, commands.
            val u = Understand.read(Translit.toRoman(text), contactNames(), wordFixes)
            if (u.choices.size > 1) { askWho = u; return }
            act(u, original)
        } catch (e: Exception) {
            // Never close the app because a sentence was not understood.
            toast("Sorry, I couldn't read that. Tap + to add it by hand.")
        }
    }

    readAgain = { fixed, said -> handleText(fixed, said) }

    /** The records on the screen the person is looking at (a search or a khata), so "inko" / "ye sari" means them. */
    fun screenList(): Pair<String, List<Entry>>? {
        val activePerson = if (ledger != null) ledgerPerson ?: activeLedgerPerson
            else account?.takeIf { it.main == PEOPLE }?.sub
        activePerson?.let { p ->
            val list = entries.filter { it.person.equals(p, true) }
            return "$p's khata (${list.size} records)" to list
        }
        results?.let { (q, _) ->
            val person = entries.map { it.person.trim() }.firstOrNull { it.isNotEmpty() && it.equals(q.trim(), true) }
            val list = if (person != null) entries.filter { it.person.trim().equals(person, true) && withPerson(it) } else matchEntries(entries, q)
            return "search '$q' (${list.size} records)" to list
        }
        return null
    }

    /** Moves every entry in the currently open khata to another saved khata, preserving entry data. */
    fun handleCurrentKhataMoveCommand(text: String): String? {
        val source = (if (ledger != null) ledgerPerson ?: activeLedgerPerson
            else account?.takeIf { it.main == PEOPLE }?.sub) ?: return null
        val roman = runCatching { Translit.toRoman(text) }.getOrDefault(text).lowercase()
        if (!Accounts.isExplicitBulkKhataMoveRequest(roman)) return null

        val destination = Accounts.khataNameMentionedIn(roman, source, entries.map { it.person })
            ?: return "I couldn't identify the destination khata. Please mention its exact saved name."
        val sourceEntries = entries.filter { it.person.equals(source, ignoreCase = true) }
        if (sourceEntries.isEmpty()) return "No entries found in $source's khata. Nothing was moved."
        val changes = sourceEntries.map { old -> old to old.copy(person = destination) }
        if (!applyPlan(EditPlan(changes, emptyList(), "Moved ${changes.size} entries from $source to $destination"))) {
            return "The khata entries changed before I could move them. Nothing was moved; please try again."
        }
        return "Moved ${changes.size} entries from $source to $destination. Amounts, dates, notes and entry types are unchanged. You can Undo or restore this move from Move history."
    }

    /**
     * Handles the explicit "all entries gave and received" request only inside an open person's khata.
     * Transfers are converted using the running balance; ordinary expenses and income are left untouched.
     * Returns null when this is not that command.
     */
    fun handleCurrentKhataBulkCommand(text: String): String? {
        val person = (if (ledger != null) ledgerPerson ?: activeLedgerPerson else account?.takeIf { it.main == PEOPLE }?.sub) ?: return null
        val m = runCatching { Translit.toRoman(text) }.getOrDefault(text).lowercase()
        val all = Regex("""\b(all|sab|sari|saari|tamam|here|yahan|yaha|these|ye|in sab)\b""").containsMatchIn(m)
        val records = Regex("""\b(entr(?:y|ies)|records?|transactions?)\b""").containsMatchIn(m)
        val directions = Regex("""\b(gave|give|given|received|receive|got|diye|diya|mila|mili)\b""").containsMatchIn(m)
        val action = Regex("""\b(kar do|kr do|kardo|krdo|bana do|bna do|convert|change|make|set|darj|dalo|dal do|karo|kro)\b""").containsMatchIn(m)
        if (!all || !records || !directions || !action) return null

        val changed = convertPersonTransfersToLedgerEntries(entries, person)
        if (changed.isEmpty()) return "No transfer entries found to convert in " + person + "'s khata."
        val message = "Converted " + changed.size + " transfer entries in " + person + "'s khata to gave/received entries"
        if (!applyPlan(EditPlan(changed, emptyList(), message))) {
            return "The khata changed before I could apply the conversion. Nothing was changed; please try again."
        }
        return "Done. Converted " + changed.size + " transfer entries in " + person + "'s khata. The khata stays open."
    }

    /**
     * Carries out what the local engine understood. The AI only translated; every record is picked, counted, added up,
     * deleted or changed here by the app. Returns a line to show, or null when the action shows itself.
     */
    fun runFixed(f: NativeChatEngine.Fixed, said: String, inChat: Boolean): String? {
        // "Inko / ye sari": nothing narrowed down, so it means the records on screen.
        val onScreen = screenList()
        // A vague singular instruction must never silently edit or delete an entire khata.
        val saidRoman = runCatching { Translit.toRoman(said) }.getOrDefault(said).lowercase()
        val explicitlyAll = Regex("""\b(all|every|sab|sari|saari|tamam|these|in sab)\b""").containsMatchIn(saidRoman)
        if (f.filter.isEmpty && onScreen != null && onScreen.second.size > 1 &&
            f.kind in setOf("delete", "change", "to_khata") && !explicitlyAll) {
            return "Which entry in ${onScreen.first}? Tell me the amount or date, or say 'all entries'."
        }
        val q = if (f.filter.isEmpty && onScreen != null && f.kind in setOf("delete", "change", "to_khata", "reread", "ask", "paid_by"))
            Query.Filter(ids = onScreen.second.map { it.id }.toSet()) else f.filter
        // When chatting inside a khata, actions and searches must never leak into another khata.
        fun scoped(list: List<Entry>): List<Entry> = scopeKhataEntries(list)
        fun clarifyMultiple(action: String, hits: List<Entry>, allowAll: Boolean = true): String? {
            if (!Accounts.requiresTargetClarification(hits.size, explicitlyAll, allowAll)) return null
            val preview = hits.take(4).joinToString("\n") { e ->
                shortDate(e.date) + " · " + entryTitle(e) + " · " + fmt(e.amount) + " " + currency
            }
            return "I found ${hits.size} matching entries for $action:\n$preview\nWhich one do you mean? Give the date or amount, or say 'all entries' when you want a bulk action."
        }
        return when (f.kind) {
            "entry" -> {
                if (f.text.none { it.isDigit() }) return f.reply.ifBlank { "How much? e.g. \"${f.text.ifBlank { "petrol" }} 50\"" }
                if (inChat) chatRead(f.text) else handleText(f.text, original = said)
                null
            }
            "plan" -> {
                val plan = f.plan ?: return "I couldn't build a safe change plan. Nothing was changed."
                pendingPlan = plan
                "I prepared a change for review. Check the proposed entry details and tap Apply or Cancel. Nothing has changed yet."
            }
            "clear_all" -> {
                val targets = scoped(entries)
                if (targets.isEmpty()) "There are no entries in this view."
                else {
                    askDelete("all " + targets.size + " entries in this view", targets)
                    "Found " + targets.size + " entries. Confirm the delete dialog to move them to Trash. Nothing is deleted yet."
                }
            }
            "ask" -> {
                val available = scoped(entries)
                if (f.what == "balance" && q.persons.size == 1) {
                    Questions.answer(q.persons.first() + " kitna", available, currency)?.text ?: "No entries with ${q.persons.first()}."
                } else {
                    val main = Query.answer(Query.select(available, q), f.what.ifBlank { "sum" }, q, currency)
                    // "kitna kharcha": money that went out as loans or paybacks that day is shown too, so nothing looks missing.
                    if (q.types == setOf(Type.KHARCHA) && f.what.ifBlank { "sum" } == "sum") {
                        val outs = Query.Filter(q.from, q.to, setOf(Type.DIYA, Type.WAPIS_DIYA), q.persons, emptySet(), q.items, q.min, q.max, q.last, false, q.notItems, q.ids, q.createdFrom, q.createdTo)
                        val other = Query.select(available, outs)
                        if (other.isEmpty()) main
                        else if (Query.select(available, q).isEmpty()) {
                            "No cash-expense entries found (" + Query.label(q) + "). Other money out: " +
                                Query.answer(other, "sum", outs, currency)
                        } else main + "\nOther money out: " + Query.answer(other, "sum", outs, currency)
                    } else main
                }
            }
            "delete" -> {
                if (q.isEmpty) return "Which entries? e.g. \"aaj ki sari entries delete karo\""
                val hits = scoped(Query.select(entries, q))
                if (hits.isEmpty()) return "Nothing found (${Query.label(q)})."
                clarifyMultiple("delete", hits)?.let { return it }
                askDelete(Query.label(q), hits)
                null
            }
            "change" -> {
                // "Other wali expense me dal do": that's sorting Other into real categories.
                if (q.cats == setOf(Cat.OTHER) && (f.set["category"].isNullOrBlank() || f.set["category"].equals("expense", true))) {
                    openRecat(); return null
                }
                if (q.isEmpty) return "Which entry? e.g. \"laptop wali entry\""
                if (f.set.isEmpty()) return runFixed(NativeChatEngine.Fixed("", "reread", "", "", q), said, inChat)
                val hits = scoped(Query.select(entries, q))
                if (hits.isEmpty()) return "Nothing found (${Query.label(q)})."
                clarifyMultiple("change", hits)?.let { return it }
                val changed = hits.map { it to Query.change(it, f.set) }.filter { (a, b) -> a != b }
                if (changed.isEmpty()) return "Already like that."
                if (!applyPlan(EditPlan(changed, emptyList(), "Changed ${changed.size} " + (if (changed.size == 1) "entry" else "entries")))) {
                    return "Those entries changed before I could update them. Nothing was changed; please search again."
                }
                changed.take(3).joinToString("; ") { (a, b) -> entryTitle(a) + " " + fmt(a.amount) + " → " + entryTitle(b) + " " + fmt(b.amount) } +
                    if (changed.size > 3) " +${changed.size - 3} more" else ""
            }
            "to_khata" -> {
                // "Ye sari Abrar D360 ka khata bana do": these records become that khata's lene-dene, in date order,
                // each one a loan or a repayment by the running balance (money out = they owe me, money in = back / I owe).
                val name = f.set["person"]?.trim()?.takeIf { it.isNotEmpty() } ?: f.payer.ifBlank { return "Which khata? e.g. \"Abrar D360 ka khata\"" }
                if (q.isEmpty) return "Which entries? e.g. \"d360 wali entries\""
                val hits = scoped(Query.select(entries, q))
                    .filter { !it.person.equals(name, true) }.sortedWith(compareBy<Entry> { it.date }.thenBy { it.createdAt })
                if (hits.isEmpty()) return "Nothing found (${Query.label(q)})."
                clarifyMultiple("move to $name khata", hits)?.let { return it }
                var work = entries
                val changed = hits.map { e ->
                    val moved = movedTo(e, work, null, false, name).copy(item = e.item.ifBlank { "Loans" })
                    work = work.map { if (it.id == e.id) moved else it }
                    e to moved
                }
                if (!applyPlan(EditPlan(changed, emptyList(), "Moved ${changed.size} to $name"))) {
                    return "Those entries changed before I could move them. Nothing was moved; please search again."
                }
                val bal = personAccounts(work).firstOrNull { it.name.equals(name, true) }?.balance ?: 0.0
                "${changed.size} entries → $name khata · " + when {
                    bal > 0.005 -> "$name will give you ${fmt(bal)}"
                    bal < -0.005 -> "you will give $name ${fmt(-bal)}"
                    else -> "settled"
                }
            }
            "paid_by" -> {
                // "Aaj jo khana khaya us ke paise Nadeem ne diye": find that meal and make it "Nadeem paid".
                val payer = f.payer.ifBlank { return "Who paid? e.g. \"nadeem ne pay kiya\"" }
                val q2 = Query.Filter(q.from, q.to, setOf(Type.KHARCHA), emptyList(), q.cats, q.items, q.min, q.max, q.last, q.noPeople, q.notItems, q.ids, q.createdFrom, q.createdTo)
                if (q2.from == null && q2.to == null && q2.createdFrom == null && q2.createdTo == null &&
                    q2.cats.isEmpty() && q2.items.isEmpty() && q2.ids.isEmpty()) return "Which one? e.g. \"aaj ka khana\""
                val hits = scoped(Query.select(entries, q2))
                // The one that mentions the payer comes first ("Khana with nadeem").
                val candidates = hits.filter { it.note.contains(payer, true) }.ifEmpty { hits }
                clarifyMultiple("mark as paid by $payer", candidates, allowAll = false)?.let { return it }
                val h = candidates.firstOrNull() ?: return "Nothing found (${Query.label(q2)})."
                // A bill I split earlier: the others' shares were "to receive" for me; now the payer is owed instead.
                val siblings = scoped(entries).filter { it.id != h.id && it.createdAt == h.createdAt && it.type == Type.DIYA && it.note.contains("share of", true) }
                val shared = siblings.isNotEmpty() || h.note.contains(payer, true)
                val total = h.amount + siblings.sumOf { it.amount }
                val mine = if (siblings.isNotEmpty()) h.amount else if (shared) Math.round(h.amount * 50.0) / 100.0 else h.amount
                val what = h.item.ifBlank { h.category.label }
                val myEntry = h.copy(amount = mine, note = if (shared) "My share · shared ${fmt(total)} · paid by $payer" else "Paid by $payer")
                val owe = h.copy(id = h.id + "p", type = Type.LIYA, amount = mine, category = Cat.UDHAAR, item = what, person = payer,
                    note = "$payer paid $what (${fmt(total)}), my share")
                val before = entries
                val gone = siblings.map { it.id }.toSet() + h.id
                save(listOf(myEntry, owe) + entries.filter { it.id !in gone })
                scope.launch {
                    snackbar.currentSnackbarData?.dismiss()
                    val res = snackbar.showSnackbar("✓ $payer paid", actionLabel = "Undo", duration = SnackbarDuration.Long)
                    if (res == SnackbarResult.ActionPerformed) save(before)
                }
                "${entryTitle(h)} ${fmt(total)} paid by $payer → your share ${fmt(mine)} · you owe $payer ${fmt(mine)}"
            }
            "reread" -> {
                // "Galat hai, theek karo": the app reads each entry's own note again with what it knows now.
                if (q.isEmpty) return "Which entry? e.g. \"laptop wali entry\""
                val hits = scoped(Query.select(entries, q))
                if (hits.isEmpty()) return "Nothing found (${Query.label(q)})."
                clarifyMultiple("re-read", hits)?.let { return it }
                val names = contactNames()
                val swaps = hits.mapNotNull { old ->
                    // The note keeps the words ("Khana ali"); the amount is put back when it isn't in it.
                    val said = if (old.note.any { it.isDigit() }) old.note else old.note + " " + fmt(old.amount).replace(",", "")
                    val u = runCatching { Understand.read(said, names, wordFixes) }.getOrNull() ?: return@mapNotNull null
                    val fresh = Split.detect(u.text, names)?.let { Split.entries(it) } ?: Parser.parse(u.text).entries
                    val list = fresh.mapIndexed { i, e -> e.copy(id = old.id + "r" + i, date = old.date, createdAt = old.createdAt + i) }
                    val same = list.size == 1 && list[0].type == old.type && list[0].category == old.category &&
                        list[0].item == old.item && list[0].amount == old.amount && list[0].person == old.person
                    if (list.isEmpty() || same) null else old to list
                }
                if (swaps.isEmpty()) return "Looks right to the app. Say what should change, e.g. \"laptop nahi khana tha\"."
                val before = entries
                val gone = swaps.map { it.first.id }.toSet()
                save(swaps.flatMap { it.second } + entries.filter { it.id !in gone })
                val line = swaps.joinToString("; ") { (o, n) ->
                    entryTitle(o) + " " + fmt(o.amount) + " → " + n.joinToString(" · ") { entryTitle(it) + " " + fmt(it.amount) }
                }
                scope.launch {
                    snackbar.currentSnackbarData?.dismiss()
                    val res = snackbar.showSnackbar("✓ Fixed ${swaps.size}", actionLabel = "Undo", duration = SnackbarDuration.Long)
                    if (res == SnackbarResult.ActionPerformed) save(before)
                }
                line
            }
            "open" -> {
                val name = q.persons.firstOrNull()
                if (name != null) {
                    val p = personAccounts(entries).firstOrNull { it.name.equals(name, true) }
                    ledgerPerson = p?.name ?: name
                    ledger = if (p == null || p.toReceive) MainAcct.TO_RECEIVE else MainAcct.TO_PAY
                    if (inChat) tab = Tab.HOME
                    null
                } else {
                    val term = (q.items + q.cats.map { it.label }).firstOrNull()
                    if (term == null) {
                        // Date/type-only list requests have no item term; answer from the exact database filter.
                        if (!q.isEmpty) return runFixed(
                            NativeChatEngine.Fixed("", "ask", "", "list", q), said, inChat
                        )
                        return "What should I show? Add a category, person, date, or amount range."
                    }
                    results = term to false; people = null
                    if (inChat) tab = Tab.HOME
                    null
                }
            }
            "duplicates" -> {
                val source = if (q.isEmpty) scoped(entries) else scoped(Query.select(entries, q))
                val pairs = Query.likelyDuplicateExpenses(source)
                if (pairs.isEmpty()) {
                    "I couldn't find likely duplicate expenses in the saved records."
                } else {
                    val df = java.text.SimpleDateFormat("d MMM yyyy", java.util.Locale.US)
                    "Likely duplicate expenses (nothing deleted):\n" +
                        pairs.take(8).joinToString("\n") { (a, b) ->
                            "${df.format(java.util.Date(a.date))} · ${entryTitle(a)} · ${fmt(a.amount)} " + currency +
                                "  ↔  ${df.format(java.util.Date(b.date))} · ${entryTitle(b)} · ${fmt(b.amount)} " + currency
                        } + if (pairs.size > 8) "\n+${pairs.size - 8} more pairs" else ""
                }
            }
            "sort_other" -> { openRecat(); null }
            "undo" -> {
                val ids = lastAdded.filter { id -> entries.any { it.id == id } }
                if (ids.isEmpty()) "Nothing to undo" else { lastAdded = emptyList(); moveToTrash(ids.toSet()); null }
            }
            "chat" -> f.reply.ifBlank { "🙂" }
            else -> f.reply.ifBlank { "The app can't do that yet." }
        }
    }

    /** Adds a message to the chat and keeps it on the phone. */
    fun postChat(text: String, isUser: Boolean, entryIds: List<String> = emptyList(), attachment: String = "", meta: String = "") {
        val updated = chatMessages + ChatMessage(
            id = "chat-" + System.currentTimeMillis() + "-" + System.nanoTime(),
            isUser = isUser,
            text = text,
            entryIds = entryIds,
            attachment = attachment,
            meta = meta
        )
        chatMessages = updated
        store.saveChatMessages(updated)
        if (!isUser && homeChatFlash) {
            flash = Flash(text, Flash.Kind.ANSWER)
            homeChatFlash = false
        }
    }

    chatSink[0] = { m ->
        chatWatch = null
        postChat(m.removePrefix("✨ ").trim(), isUser = false)
    }

    // When a chat message adds or changes entries, say so in the chat (new ones as a saved card with Undo).
    LaunchedEffect(entries) {
        val before = chatWatch ?: return@LaunchedEffect
        if (entries === before) return@LaunchedEffect
        val beforeById = before.associateBy { it.id }
        val afterById = entries.associateBy { it.id }
        val added = entries.filter { it.id !in beforeById }
        val removed = before.count { it.id !in afterById }
        val updated = beforeById.keys.intersect(afterById.keys).count { id -> beforeById[id] != afterById[id] }
        val skipped = chatSkippedDuplicates
        chatSkippedDuplicates = 0
        chatWatch = null
        val summary = when {
            added.isNotEmpty() && removed == 0 && updated == 0 ->
                "✓ " + added.size + " " + (if (added.size == 1) "entry" else "entries") + " saved" +
                    if (skipped > 0) ". Skipped $skipped duplicate bank alert(s)." else "."
            removed > 0 && added.isEmpty() && updated == 0 ->
                "✓ Moved $removed " + (if (removed == 1) "entry" else "entries") + " to Trash."
            added.isEmpty() && removed == 0 && updated > 0 ->
                "✓ Updated $updated " + (if (updated == 1) "entry" else "entries") + "."
            added.isNotEmpty() || removed > 0 || updated > 0 ->
                "✓ Updated records: added ${added.size}, removed $removed, changed $updated." +
                    if (skipped > 0) ". Skipped $skipped duplicate bank alert(s)." else ""
            else -> "No database change was detected. Please review the requested action."
        }
        val trace = "ledger-sync=confirmed; added=${added.size}; removed=$removed; updated=$updated; duplicateSkipped=$skipped"
        postChat(summary, isUser = false, entryIds = added.map { it.id }, meta = trace)
    }

    /**
     * Creates a user-approved, shareable diagnostics file. It contains the saved chat transcript and
     * local-engine traces only; it never includes the ledger, contact list, credentials, or attachment bytes.
     */
    fun shareChatDiagnostics() {
        try {
            val dir = File(context.cacheDir, "diagnostics").apply { mkdirs() }
            dir.listFiles()?.forEach { runCatching { it.delete() } }
            val report = org.json.JSONObject()
                .put("app", "Smart Expense")
                .put("reportVersion", 1)
                .put("createdAt", java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", java.util.Locale.US).format(java.util.Date()))
                .put("appVersion", runCatching {
                    val info = context.packageManager.getPackageInfo(context.packageName, 0)
                    info.versionName ?: "unknown"
                }.getOrDefault("unknown"))
                .put("androidSdk", Build.VERSION.SDK_INT)
                .put("device", Build.MANUFACTURER + " " + Build.MODEL)
                .put("chatMessageCount", chatMessages.size)
                .put("privacy", "Chat text and engine trace only. No ledger records, contacts, credentials, or attachment files are included.")
                .put("messages", org.json.JSONArray().apply {
                    chatMessages.takeLast(100).forEach { message ->
                        put(
                            org.json.JSONObject()
                                .put("timestamp", message.timestamp)
                                .put("role", if (message.isUser) "user" else "assistant")
                                .put("text", message.text.take(8000))
                                .put("engineTrace", message.meta)
                                .put("hasAttachment", message.attachment.isNotBlank())
                                .put("entryReferenceCount", message.entryIds.size)
                        )
                    }
                })
            val file = File(dir, "smart-expense-chat-diagnostics.txt")
            file.writeText(report.toString(2), Charsets.UTF_8)
            val uri = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, "Smart Expense chat diagnostics")
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                clipData = android.content.ClipData.newUri(context.contentResolver, "Chat diagnostics", uri)
            }
            context.startActivity(Intent.createChooser(send, "Share diagnostics with ChatGPT or save the report"))
        } catch (e: Exception) {
            toast("Couldn't prepare the report: " + (e.message ?: "storage error"))
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

    chatRead = { chatViaReader(it) }

    /** Without AI (no key, or no internet): save clear entries, answer simple questions, never stay silent. */
    fun chatInput(raw: String) {
        val text = raw.trim()
        if (text.isEmpty() || chatLoading) return
        val previousUser = chatMessages.lastOrNull { it.isUser }
        val latestAssistant = chatMessages.lastOrNull { !it.isUser }
        if (previousUser != null && latestAssistant != null &&
            Accounts.isRecentDuplicateMutationRequest(
                previousUser.text, previousUser.timestamp, latestAssistant.text, latestAssistant.timestamp,
                latestAssistant.entryIds.isNotEmpty(), text, System.currentTimeMillis()
            )
        ) {
            postChat(text, isUser = true)
            postChat("That exact request was just completed, so I didn't run it twice. If you meant another identical transaction, say 'add another'.", isUser = false)
            return
        }
        val roman = runCatching { Translit.toRoman(text) }.getOrDefault(text)
        val history = chatMessages.takeLast(20)
        postChat(text, isUser = true)

        NativeMemory.handle(text, store.memory)?.let { memory ->
            store.memory = memory.facts
            lastSpeech = memory.reply
            postChat(memory.reply, isUser = false)
            return
        }

        handleCurrentKhataMoveCommand(text)?.let { lastSpeech = it; postChat(it, isUser = false); return }
        handleCurrentKhataBulkCommand(text)?.let { lastSpeech = it; postChat(it, isUser = false); return }

        chatLoading = true
        scope.launch {
            try {
                val intent = NativeChatEngine.interpret(
                    roman, scopeKhataEntries(entries), contactNames(), wordFixes, history, currency = currency
                )
                if (intent.kind in setOf("delete", "clear_all", "change", "to_khata", "paid_by", "reread", "undo")) {
                    val actionSnapshot = entries
                    chatWatch = actionSnapshot
                    scope.launch {
                        kotlinx.coroutines.delay(60_000)
                        if (chatWatch === actionSnapshot) chatWatch = null
                    }
                }
                var executionStatus = "returned"
                val reply = runCatching { runFixed(intent, text, inChat = true) }
                    .onFailure { executionStatus = "exception:" + it.javaClass.simpleName }
                    .getOrElse {
                        "I couldn't safely complete that action. Nothing was changed. Please add the date, amount, item, or person's name."
                    }
                if (reply == null && executionStatus == "returned") executionStatus = "awaiting_confirmation"
                val matchCount = if (intent.kind in setOf("ask", "open", "delete", "change", "to_khata", "paid_by", "reread")) {
                    runCatching { Query.select(scopeKhataEntries(entries), intent.filter).size }.getOrNull()
                } else null
                val trace = "engine=NativeChatEngine; intent=" + intent.kind +
                    (if (intent.what.isBlank()) "" else "; query=" + intent.what) +
                    (matchCount?.let { "; matched=" + it } ?: "") + "; execution=" + executionStatus
                lastTalk = roman + (if (!reply.isNullOrBlank()) " → app: " + reply.take(100) else "")
                if (!reply.isNullOrBlank()) {
                    lastSpeech = reply
                    postChat(reply, isUser = false, meta = trace)
                }
            } catch (e: Exception) {
                val safe = "I couldn't safely understand that. Nothing was changed. Try 'chai 5', 'food this month', or 'last 5 transactions'."
                lastSpeech = safe
                postChat(safe, isUser = false, meta = "engine=NativeChatEngine; stage=interpret; failure=" + e.javaClass.simpleName)
            } finally {
                chatLoading = false
            }
        }
    }

    /** Home is a second entry point to the same assistant and persisted conversation as the Chat screen. */
    fun homeInput(raw: String) {
        val text = raw.trim()
        if (text.isEmpty() || chatLoading) return
        homeChatFlash = true
        chatInput(text)
        // Clear the home-chat presentation flag when no asynchronous work was started.
        if (!chatLoading && chatMessages.lastOrNull()?.isUser == false) homeChatFlash = false
    }

    /** An entry already in the khata on the same day with the same amount and kind is probably the same one. */
    fun isDuplicate(e: Entry): Boolean = entries.any {
        it.type == e.type && kotlin.math.abs(it.amount - e.amount) < 0.01 && sameDay(it.date, e.date)
    }

    /** Auto-match only when minute, amount, direction and identity agree. */
    fun matchSaved(found: List<Entry>): Map<String, String> {
        val used = mutableSetOf<String>()
        val out = linkedMapOf<String, String>()
        fun normal(s: String) = s.trim().lowercase().replace(Regex("""\\s+"""), " ")
        found.forEach { imported ->
            val hit = entries.firstOrNull { saved ->
                if (saved.id in used || saved.type != imported.type ||
                    kotlin.math.abs(saved.amount - imported.amount) >= 0.01 ||
                    saved.date / 60_000L != imported.date / 60_000L
                ) {
                    false
                } else {
                    val note = normal(saved.note)
                    val sameNote = note.isNotBlank() && note.length >= 6 && note == normal(imported.note)
                    val person = normal(saved.person)
                    val samePerson = person.isNotBlank() && person == normal(imported.person)
                    val item = normal(saved.item)
                    val sameItem = item.isNotBlank() && item == normal(imported.item)
                    sameNote || (samePerson && sameItem)
                }
            }
            if (hit != null) {
                used += hit.id
                out[imported.id] = hit.id
            }
        }
        return out
    }

    /** Reads text-based PDF statements locally and shows candidate entries for review. */
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
        chatLoading = true
        scope.launch {
            try {
                val bytes = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    resolver.openInputStream(uri)?.use { it.readBytes() }
                } ?: throw IllegalStateException("Could not open this file.")
                // Show the photo (or the PDF's first page) in the chat, like a messaging app.
                val preview = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    runCatching { Attachments.savePreview(context, bytes, mime) }.getOrNull().orEmpty()
                }
                postChat(name, isUser = true, attachment = preview)
                if (bytes.size > 15 * 1024 * 1024) throw IllegalStateException("This file is too big (over 15 MB). Send a smaller PDF or a photo of one page.")
                loadingLabel = "Reading on this phone…"
                val textLayer = if (mime == "application/pdf") kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    PdfText.extract(context, bytes)
                } else null
                var recognizedText = textLayer
                if (recognizedText.isNullOrBlank()) {
                    recognizedText = Attachments.extractText(context, bytes, mime)
                }
                var statement = recognizedText?.takeIf { it.isNotBlank() }?.let {
                    runCatching { StatementParser.parse(it) }.getOrNull()
                }
                if (statement?.ok != true && mime == "application/pdf" && textLayer != null) {
                    // A selectable text layer can still be badly ordered; retry the rendered pages with bundled OCR.
                    recognizedText = Attachments.extractText(context, bytes, mime)
                    statement = recognizedText.takeIf { it.isNotBlank() }?.let {
                        runCatching { StatementParser.parse(it) }.getOrNull()
                    }
                }
                val found = when {
                    statement?.ok == true -> {
                        postChat(statement.check, isUser = false)
                        PdfText.toEntries(statement).sortedByDescending { it.date }
                    }
                    !recognizedText.isNullOrBlank() -> ReceiptParser.parse(recognizedText).sortedByDescending { it.date }
                    else -> emptyList()
                }
                if (found.isEmpty()) {
                    postChat(
                        "I couldn't safely extract a dated statement or a printed receipt total. Nothing was imported. " +
                            "The bundled offline OCR reads Latin text and digits; Arabic-only text may need manual review.",
                        isUser = false
                    )
                } else {
                    imports = found
                    importMatch = matchSaved(found)
                    importDupes = importMatch.keys
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
                loadingLabel = "Thinking…"
            }
        }
    }

    // ---------- Backup: export and import all data ----------
    var importText by remember { mutableStateOf<String?>(null) }
    val exportFile = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch {
            val ok = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openOutputStream(uri)?.use { it.write(store.exportJson().toByteArray()) } != null
                }.getOrDefault(false)
            }
            toast(if (ok) "✓ Data exported (${entries.size} entries)" else "Could not save the file")
        }
    }
    val importFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            val text = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                runCatching { context.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) } }.getOrNull()
            }
            if (text == null || store.backupEntries(text) < 0) toast("This is not a Smart Expense backup file")
            else importText = text
        }
    }
    importText?.let { text ->
        AlertDialog(
            onDismissRequest = { importText = null },
            title = { Text("Import data?") },
            text = {
                Text(
                    "This backup has ${store.backupEntries(text)} entries. It will replace everything in the app now " +
                        "(${entries.size} entries). Export first if you want to keep the current data."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    importText = null
                    runCatching { store.importJson(text) }
                        .onSuccess {
                            toast("✓ Data imported")
                            (context as? android.app.Activity)?.recreate()
                        }
                        .onFailure { toast("Could not import this file") }
                }) { Text("Replace") }
            },
            dismissButton = { TextButton(onClick = { importText = null }) { Text("Cancel") } }
        )
    }

    val pickFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) readAttachment(uri)
    }

    // ---------- Voice conversation: talk to the assistant hands-free ----------
    var voiceStatus by remember { mutableStateOf("") }
    var voicePartial by remember { mutableStateOf("") }
    var voiceAwaiting by remember { mutableStateOf(false) }
    var voiceMisses by remember { mutableIntStateOf(0) }
    val ttsReady = remember { mutableStateOf(false) }
    val tts = remember {
        android.speech.tts.TextToSpeech(context) { status -> ttsReady.value = status == android.speech.tts.TextToSpeech.SUCCESS }
    }
    val recognizer = remember {
        if (android.speech.SpeechRecognizer.isRecognitionAvailable(context)) android.speech.SpeechRecognizer.createSpeechRecognizer(context) else null
    }
    DisposableEffect(Unit) {
        onDispose {
            runCatching { recognizer?.destroy() }
            runCatching { tts.shutdown() }
        }
    }

    fun listenNow() {
        val r = recognizer ?: return
        if (!voiceOn) return
        voicePartial = ""
        voiceStatus = "Listening…"
        val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, lang)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
            // Give the person time to finish a long story before it stops listening.
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2500L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 2000L)
        }
        runCatching { r.cancel(); r.startListening(i) }
    }

    fun stopVoice(message: String? = null) {
        voiceOn = false
        voiceAwaiting = false
        voiceStatus = ""
        voicePartial = ""
        runCatching { recognizer?.cancel() }
        runCatching { tts.stop() }
        if (message != null) toast(message)
    }

    /** Reads [text] aloud: Urdu script with an Urdu voice, otherwise English. Listens again when done. */
    fun speak(text: String) {
        val clean = text.replace(Regex("""[✓•*#_]"""), " ").replace(Regex("""(?m)^\s*-\s*"""), "").trim()
        if (clean.isEmpty() || !ttsReady.value) {
            listenNow()
            return
        }
        val urduScript = clean.any { it in '\u0600'..'\u06FF' }
        val wanted = if (urduScript) java.util.Locale("ur", "PK") else java.util.Locale("en", "IN")
        val ok = tts.setLanguage(wanted)
        if (ok == android.speech.tts.TextToSpeech.LANG_MISSING_DATA || ok == android.speech.tts.TextToSpeech.LANG_NOT_SUPPORTED) {
            tts.setLanguage(java.util.Locale.ENGLISH)
        }
        voiceStatus = "Speaking…"
        tts.speak(clean.take(3500), android.speech.tts.TextToSpeech.QUEUE_FLUSH, null, "reply-" + System.nanoTime())
    }

    DisposableEffect(tts) {
        tts.setOnUtteranceProgressListener(object : android.speech.tts.UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onDone(utteranceId: String?) {
                android.os.Handler(android.os.Looper.getMainLooper()).post { if (voiceOn) listenNow() }
            }
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                android.os.Handler(android.os.Looper.getMainLooper()).post { if (voiceOn) listenNow() }
            }
        })
        onDispose { }
    }

    DisposableEffect(recognizer) {
        recognizer?.setRecognitionListener(object : android.speech.RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) { voiceStatus = "Listening…" }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() { voiceStatus = "Thinking…" }
            override fun onEvent(eventType: Int, params: Bundle?) {}
            override fun onPartialResults(partialResults: Bundle?) {
                partialResults?.getStringArrayList(android.speech.SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                    ?.let { voicePartial = it }
            }
            override fun onResults(results: Bundle?) {
                val said = results?.getStringArrayList(android.speech.SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()?.let { runCatching { Translit.toRoman(it) }.getOrDefault(it) }?.trim().orEmpty()
                if (!voiceOn) return
                if (said.isBlank()) { listenNow(); return }
                voiceMisses = 0
                voicePartial = said
                voiceStatus = "Thinking…"
                voiceAwaiting = true
                chatInput(said)
            }
            override fun onError(error: Int) {
                if (!voiceOn) return
                when (error) {
                    android.speech.SpeechRecognizer.ERROR_NO_MATCH, android.speech.SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> {
                        voiceMisses++
                        // Quiet for a while: stop instead of listening forever.
                        if (voiceMisses >= 3) stopVoice("Voice chat paused. Tap the headset to talk again.") else listenNow()
                    }
                    android.speech.SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> stopVoice("Allow the microphone for Smart Expense to talk.")
                    android.speech.SpeechRecognizer.ERROR_NETWORK, android.speech.SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
                        stopVoice("No internet for voice. Try again when online.")
                    else -> listenNow()
                }
            }
        })
        onDispose { }
    }

    // When the assistant has answered a spoken message, read the answer aloud, then listen again.
    LaunchedEffect(chatLoading, chatMessages.size) {
        if (!voiceOn || !voiceAwaiting || chatLoading) return@LaunchedEffect
        val lastUser = chatMessages.indexOfLast { it.isUser }
        val answers = chatMessages.drop(lastUser + 1).filter { !it.isUser }
        if (answers.isEmpty()) return@LaunchedEffect
        kotlinx.coroutines.delay(400) // let any follow-up line (changes, "Remembered") arrive
        voiceAwaiting = false
        val spoken = lastSpeech.takeIf { it.isNotBlank() } ?: answers.joinToString(". ") { it.text }
        lastSpeech = ""
        speak(spoken)
    }

    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) { voiceOn = true; voiceMisses = 0; listenNow() }
        else toast("Microphone permission is needed to talk to the assistant.")
    }

    fun toggleVoice() {
        if (voiceOn) { stopVoice(); return }
        if (recognizer == null) { toast("Speech recognition is not available on this phone."); return }
        val granted = androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECORD_AUDIO) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
        if (granted) { voiceOn = true; voiceMisses = 0; listenNow() } else micPermission.launch(android.Manifest.permission.RECORD_AUDIO)
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
                if (voiceForChat) chatInput(said) else homeInput(said)
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

    BackHandler(enabled = ledger != null || account != null || results != null || people != null || tab != Tab.HOME) {
        if (ledger != null) {
            ledger = null
            ledgerPerson = null
        } else if (account != null) {
            account = null
        } else if (results != null) {
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
            val main = results == null && people == null && account == null && ledger == null
            if (main && tab == Tab.CHAT) {
                InputBar(
                    onSend = { chatInput(it) }, onMic = { startVoice(forChat = true) }, placeholder = "Speak, type or ask…",
                    onAttach = { pickFile.launch(arrayOf("image/*", "application/pdf")) }
                )
            } else {
                Column {
                    FlashStrip(flash) { flash = null }
                    InputBar(
                        onSend = { homeInput(it) },
                        onMic = { startVoice(forChat = true) },
                        placeholder = "Speak, type or ask…",
                        onAttach = { pickFile.launch(arrayOf("image/*", "application/pdf")) }
                    )
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            val who = people
            val acct = account
            CompositionLocalProvider(
                LocalMainOpener provides { a: MainAcct -> ledger = a },
                LocalEntryMenu provides { e: Entry -> menuFor = e }
            ) {
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
                        if (entries.any { it.id == e.id }) {
                            toast("This entry already exists; restore cancelled.")
                        } else {
                            save(listOf(e) + entries)
                            saveTrash(trash.filter { it.id != e.id })
                            toast("✓ Restored")
                        }
                    },
                    onRestoreAll = {
                        val existing = entries.map { it.id }.toSet()
                        val safe = trash.filter { it.id !in existing }.distinctBy { it.id }
                        if (safe.isEmpty()) {
                            toast("No entries to restore without duplicates.")
                        } else {
                            save(safe + entries)
                            val restoredIds = safe.map { it.id }.toSet()
                            saveTrash(trash.filter { it.id !in restoredIds })
                            toast("✓ Restored ${safe.size} entries")
                        }
                    },
                    onEmpty = { if (trash.isNotEmpty()) confirmEmptyTrash = true }
                )
                tab == Tab.CHAT -> {
                    DisposableEffect(Unit) { onDispose { if (voiceOn) stopVoice() } }
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
                        loadingLabel = loadingLabel,
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
                            Attachments.cleanUp(context, emptySet())
                            toast("Chat history cleared")
                        },
                        onShareDiagnostics = { shareChatDiagnostics() },
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
                        voiceOn = voiceOn,
                        voiceStatus = voiceStatus,
                        voicePartial = voicePartial,
                        onVoiceToggle = { toggleVoice() },
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
                            importMatch = emptyMap()
                        },
                        pendingChanges = pendingPlan?.let { plan ->
                            val df = java.text.SimpleDateFormat("d MMM", java.util.Locale.US)
                            fun d(e: Entry) = df.format(java.util.Date(e.date)) + " · " + entryTitle(e) + " · " + fmt(e.amount)
                            plan.deleted.map { "Delete: " + d(it) } +
                                plan.changed.map { (a, b) ->
                                    "Change: " + d(a) + "  →  " + d(b) + (if (a.category != b.category || a.type != b.type) " (" + b.type.label + ", " + b.category.label + ")" else "")
                                }
                        } ?: emptyList(),
                        onApplyChanges = {
                            val applied = pendingPlan?.let { applyPlan(it) } ?: false
                            pendingPlan = null
                            postChat(
                                if (applied) "✓ Changes applied. Use Undo at the bottom if something is wrong."
                                else "Nothing was changed. One or more entries changed while this review was open; please search again.",
                                isUser = false
                            )
                        },
                        onCancelChanges = {
                            pendingPlan = null
                            postChat("Okay, nothing was changed.", isUser = false)
                        },
                        onUpdateSaved = {
                            val byImport = imports.associateBy { it.id }
                            val updates = importMatch.mapNotNull { (importId, savedId) ->
                                byImport[importId]?.let { savedId to it }
                            }.toMap()
                            var changed = 0
                            save(entries.map { old ->
                                val src = updates[old.id] ?: return@map old
                                changed++
                                old.copy(
                                    item = src.item.ifBlank { old.item },
                                    person = src.person.ifBlank { old.person },
                                    note = src.note.ifBlank { old.note },
                                    category = src.category,
                                    date = src.date,
                                    createdAt = src.createdAt
                                )
                            })
                            imports = imports.filter { it.id !in importDupes }
                            importDupes = emptySet()
                            importMatch = emptyMap()
                            postChat("✓ Updated names, notes and times of $changed saved entries. Amounts unchanged.", isUser = false)
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
                            if (newVersion == null && Updater.lastCheckFailed) {
                                toast("Couldn't check for updates. Check the internet and try again.")
                            } else if (newVersion == null) {
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
                    onExport = {
                        val day = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())
                        exportFile.launch("SmartExpense-backup-$day.json")
                    },
                    onImport = { importFile.launch(arrayOf("application/json", "text/plain", "application/octet-stream", "*/*")) },
                )
            }
            }
            // Main accounts open over the list, so going back returns to the same place in the list.
            ledger?.let { start ->
                Box(
                    Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
                        .pointerInput(Unit) { detectTapGestures { } }
                ) {
                    CompositionLocalProvider(LocalEntryMenu provides { e: Entry -> menuFor = e }) {
                        LedgerScreen(
                            start = start, entries = entries, currency = currency,
                            onClose = { ledger = null; ledgerPerson = null; activeLedgerPerson = null }, onEdit = { editing = it }, onNew = { trade = it },
                            person = ledgerPerson,
                            onActivePerson = { activeLedgerPerson = it },
                            onBulkMove = { ids, destination ->
                                val matches = scopeKhataEntries(entries).filter { it.id in ids }
                                val safeIds = matches.map { it.id }.toSet()
                                if (safeIds.isNotEmpty() && destination.isNotBlank()) {
                                    val after = entries.map { entry ->
                                        if (entry.id in safeIds) entry.copy(person = destination.trim()) else entry
                                    }
                                    val record = store.recordMove(entries, after)
                                    if (record != null) {
                                        save(after)
                                        scope.launch {
                                            val result = snackbar.showSnackbar(
                                                "Moved ${safeIds.size} entries", actionLabel = "Undo",
                                                duration = SnackbarDuration.Long
                                            )
                                            if (result == SnackbarResult.ActionPerformed) {
                                                val restored = restoreMoveBatch(entries, record)
                                                if (restored != null) {
                                                    save(restored)
                                                    store.forgetMove(record.id)
                                                } else toast("Cannot undo: one or more entries changed.")
                                            }
                                        }
                                    }
                                }
                            },
                            onMoveHistory = { showMoveHistory = true },
                            onRenamePerson = { oldName, newName ->
                                renameOrMergeKhata(oldName, newName, merge = false)
                            },
                            onMergePerson = { oldName, newName ->
                                renameOrMergeKhata(oldName, newName, merge = true)
                            }
                        )
                    }
                }
            }
            if (confirmEmptyTrash) {
                AlertDialog(
                    onDismissRequest = { confirmEmptyTrash = false },
                    title = { Text("Permanently empty Trash?") },
                    text = { Text("This will permanently delete ${trash.size} entries. This action cannot be undone.") },
                    confirmButton = {
                        Button(onClick = {
                            confirmEmptyTrash = false
                            saveTrash(emptyList())
                            toast("Trash emptied")
                        }) { Text("Delete permanently") }
                    },
                    dismissButton = {
                        TextButton(onClick = { confirmEmptyTrash = false }) { Text("Cancel") }
                    }
                )
            }
            if (showMoveHistory) {
                MoveHistoryDialog(
                    history = store.loadMoveHistory(),
                    onRestore = { record ->
                        val restored = restoreMoveBatch(entries, record)
                        if (restored == null) {
                            toast("Cannot restore: entries were deleted or changed after this move.")
                        } else {
                            save(restored)
                            store.forgetMove(record.id)
                            showMoveHistory = false
                            toast("✓ Restored ${record.changes.size} entries")
                        }
                    },
                    onDismiss = { showMoveHistory = false }
                )
            }
            menuFor?.let { e ->
                EntryMenu(
                    e, currency,
                    onEdit = { menuFor = null; editing = e },
                    onMove = { menuFor = null; moving = e },
                    onDismiss = { menuFor = null },
                    onHistory = { menuFor = null; showMoveHistory = true }
                )
            }
            moving?.let { e ->
                MoveDialog(e, entries, onMove = { moved ->
                    moving = null
                    val current = entries.firstOrNull { it.id == moved.id }
                    if (current != null && current != moved) {
                        val after = entries.map { if (it.id == moved.id) moved else it }
                        val record = store.recordMove(entries, after)
                        save(after)
                        scope.launch {
                            val result = snackbar.showSnackbar("✓ Moved entry", actionLabel = "Undo", duration = SnackbarDuration.Long)
                            if (result == SnackbarResult.ActionPerformed && record != null) {
                                val restored = restoreMoveBatch(entries, record)
                                if (restored != null) {
                                    save(restored)
                                    store.forgetMove(record.id)
                                } else toast("Cannot undo: this entry changed.")
                            }
                        }
                    }
                }, onDismiss = { moving = null })
            }
            confirmNew?.let { (list, said) ->
                ConfirmCard(
                    list, currency,
                    onSave = {
                        confirmNew = null
                        save(list + entries)
                        lastAdded = list.map { it.id }
                        // What the local engine understood is learned, so next time the app knows it without AI (no tokens).
                        list.singleOrNull { it.type == Type.KHARCHA && it.category != Cat.OTHER }?.let { e ->
                            val words = Parser.learnWords(said).filter { w -> Parser.parse("$w 1").entries.firstOrNull()?.category == Cat.OTHER }.take(3)
                            if (words.isNotEmpty()) { store.learn(words, e.category, e.item); Parser.learned = store.loadLearned() }
                        }
                        toast("✓ Saved")
                    },
                    onEdit = {
                        confirmNew = null
                        // The rest is saved; the first one opens to fix, and the fix is learned.
                        if (list.size > 1) save(list.drop(1) + entries)
                        editFrom = list.first() to said
                        trade = list.first()
                    },
                    onCancel = { confirmNew = null }
                )
            }
            recat?.let { groups ->
                RecatDialog(groups, currency, onApply = { change ->
                    recat = null
                    val before = entries
                    save(entries.map { e -> change[e.id]?.let { e.copy(category = it) } ?: e })
                    // Each shop's category is remembered, so the next statement sorts itself.
                    groups.forEach { g ->
                        val c = change[g.ids.first()] ?: return@forEach
                        val generic = setOf("atheer", "est", "company", "trading", "tradin", "store", "stor", "retail", "shop", "market", "abdul", "abdullah", "mohammed", "muhammad", "ahmed", "alsahel")
                        val words = Parser.learnWords(g.name).filter { it !in generic }.take(3)
                        if (words.isNotEmpty()) store.learn(words, c, g.name)
                    }
                    Parser.learned = store.loadLearned()
                    scope.launch {
                        snackbar.currentSnackbarData?.dismiss()
                        val res = snackbar.showSnackbar("✓ Sorted ${change.size} expenses", actionLabel = "Undo", duration = SnackbarDuration.Long)
                        if (res == SnackbarResult.ActionPerformed) save(before)
                    }
                }, onCancel = { recat = null })
            }
            splitAsk?.let { p ->
                SplitAskDialog(
                    understoodLine(p.bill, currency), p.missing,
                    onAnswer = { answerSplit(it) },
                    onMic = { startVoice() },
                    onCancel = { splitAsk = null }
                )
            }
            askWho?.let { u ->
                WhoDialog(u.nameWord ?: "", u.choices, onPick = { chosen ->
                    askWho = null
                    val word = u.nameWord ?: ""
                    if (chosen != null && word.isNotBlank()) {
                        store.addWordFix("@$word", chosen)
                        wordFixes = store.loadWordFixes()
                    }
                    val name = chosen ?: word.replaceFirstChar { it.uppercase() }
                    val text = u.text.split(" ").joinToString(" ") { if (it == word) name else it }
                    act(Understand.Result(u.kind, text, chosen, emptyList(), word, u.clock, u.fixes))
                }, onCancel = { askWho = null })
            }
            // The account opens over the list, so going back returns to the same place in the list.
            if (acct != null) {
                Box(
                    Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
                        .pointerInput(Unit) { detectTapGestures { } }
                ) {
                    CompositionLocalProvider(LocalAccountOpener provides null) {
                        AccountScreen(
                            key = acct, entries = entries, currency = currency,
                            onBack = { account = null }, onEdit = { editing = it }, onNew = { trade = it }
                        )
                    }
                }
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
                    lastAdded = listOf(e.id)
                    toast("✓ Saved")
                    // Fixed on the confirm card: learn it, so the same words are understood right next time.
                    editFrom?.let { (before, said) ->
                        editFrom = null
                        if (before.id == e.id && (before.item != e.item || before.category != e.category)) {
                            learnFrom(before, e.copy(note = said))
                            val old = before.item.lowercase()
                            val new = e.item.trim()
                            if (new.isNotBlank() && ' ' !in new) {
                                said.lowercase().split(Regex("[^\\p{L}]+"))
                                    .filter { w -> w.length >= 3 && (w == old || Normalizer.similarity(w, old) >= 0.8) }
                                    .forEach { w -> store.addWordFix(w, new.lowercase()) }
                                wordFixes = store.loadWordFixes()
                            }
                        }
                    }
                },
                onDelete = null,
                onCancel = { trade = null; editFrom = null }
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
    onAttach: (() -> Unit)? = null
) {
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
            if (onAttach != null) {
                IconButton(onClick = onAttach) {
                    Icon(
                        Icons.Filled.AttachFile, contentDescription = "Attach a photo or PDF",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
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
