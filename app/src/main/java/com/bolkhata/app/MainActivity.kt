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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Home
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
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        @Suppress("DEPRECATION")
        window.statusBarColor = GreenDeep.toArgb()
        val store = Store(this)
        setContent {
            var dark by remember { mutableStateOf(store.dark) }
            BolTheme(dark) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    App(store, dark) { dark = it; store.dark = it }
                }
            }
        }
    }
}

enum class Tab { HOME, ENTRIES, REPORTS, SETTINGS }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun App(store: Store, dark: Boolean, onDark: (Boolean) -> Unit) {
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
    var pending by remember { mutableStateOf(listOf<Entry>()) }  // entries the app did not understand
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

    fun save(list: List<Entry>) {
        entries = list
        store.save(list)
    }

    fun handleText(text: String) {
        val r = Parser.parse(text)
        if (r.entries.isEmpty() && r.unclear.isEmpty()) {
            toast("No amount heard. Try: \"50 ka khana\"")
            return
        }
        if (r.entries.isNotEmpty()) {
            save(r.entries + entries)
            toast("✓ Added " + r.entries.joinToString(", ") { entryTitle(it) + " " + fmt(it.amount) })
        }
        if (r.unclear.isNotEmpty()) pending = pending + r.unclear
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
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak… e.g. Hamza ko 1000 diye 14 date")
        }
        try {
            voice.launch(i)
        } catch (e: ActivityNotFoundException) {
            toast("Google voice not found. Update the Google app from Play Store.")
        }
    }

    fun startUpdate() {
        val info = newVersion ?: return
        if (Updater.needsPermission(context)) {
            toast("Allow \"Install unknown apps\" for Bol Khata, then tap Update again.")
            Updater.openPermissionSettings(context)
            return
        }
        scope.launch {
            progress = 0
            val file = Updater.download(context, info) { progress = it }
            progress = -1
            if (file != null) Updater.install(context, file) else toast("Download failed. Check internet and try again.")
        }
    }

    val drawer = rememberDrawerState(DrawerValue.Closed)
    fun openMenu() {
        scope.launch { drawer.open() }
    }

    BackHandler(enabled = people != null || tab != Tab.HOME) {
        if (people != null) {
            people = null
        } else {
            tab = Tab.HOME
        }
    }

    ModalNavigationDrawer(
        drawerState = drawer,
        drawerContent = {
            DrawerMenu(tab, name) { t ->
                tab = t
                people = null
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
                who != null -> PeopleScreen(entries, currency, who, onBack = { people = null }, onEdit = { editing = it })
                tab == Tab.HOME -> HomeScreen(
                    name = name, entries = entries, currency = currency,
                    newVersion = newVersion, progress = progress, onUpdate = { startUpdate() },
                    onAdd = { adding = true }, onMenu = { openMenu() }, onEdit = { editing = it },
                    onPeople = { people = it }, onReports = { tab = Tab.REPORTS }, onSeeAll = { tab = Tab.ENTRIES }
                )
                tab == Tab.ENTRIES -> TransactionsScreen(entries, currency, onEdit = { editing = it }, onAdd = { adding = true }, onMenu = { openMenu() })
                tab == Tab.REPORTS -> ReportsScreen(entries, currency, onMenu = { openMenu() })
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
                    onMenu = { openMenu() }
                )
            }
        }
    }
    }

    // Ask about anything the parser did not understand, one at a time.
    val ask = pending.firstOrNull()
    if (ask != null && editing == null && !adding) {
        ModalBottomSheet(
            onDismissRequest = { pending = pending.drop(1) },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            EntryForm(
                initial = ask,
                title = "What is this?",
                question = "I didn't understand \"${ask.note}\". Please choose:",
                askType = true,
                currency = currency,
                saveLabel = "Save",
                onSave = { e ->
                    save(listOf(e) + entries)
                    pending = pending.drop(1)
                    toast("✓ Saved")
                },
                onDelete = null,
                onCancel = { pending = pending.drop(1); toast("Skipped") }
            )
        }
    }

    editing?.let { e ->
        ModalBottomSheet(
            onDismissRequest = { editing = null },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            EntryForm(
                initial = e,
                title = "Edit entry",
                question = "You said: \"${e.note}\"",
                askType = false,
                currency = currency,
                saveLabel = "Save",
                onSave = { changed ->
                    save(entries.map { if (it.id == changed.id) changed else it })
                    editing = null
                    toast("✓ Saved")
                },
                onDelete = {
                    save(entries.filter { it.id != e.id })
                    editing = null
                    toast("✓ Entry deleted")
                },
                onCancel = { editing = null }
            )
        }
    }

    if (adding) {
        val fresh = remember { blankEntry() }
        var quick by remember { mutableStateOf("") }
        ModalBottomSheet(
            onDismissRequest = { adding = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
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
                    Text("Quick add", fontSize = 20.sp, fontWeight = FontWeight.Bold)
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
    Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 10.dp) {
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
                placeholder = { Text("Type here… e.g. petrol 50") },
                modifier = Modifier.weight(1f),
                maxLines = 3,
                shape = RoundedCornerShape(24.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { send() }),
                trailingIcon = {
                    if (text.isNotBlank()) {
                        IconButton(onClick = { send() }) {
                            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Add", tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            )
            Spacer(Modifier.width(10.dp))
            Box(
                Modifier
                    .size(56.dp)
                    .shadow(6.dp, CircleShape)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
                    .clickable { onMic() },
                contentAlignment = Alignment.Center
            ) { MicGlyph(MaterialTheme.colorScheme.onPrimary, 28.dp) }
        }
    }
}

@Composable
private fun DrawerMenu(tab: Tab, name: String, onPick: (Tab) -> Unit) {
    ModalDrawerSheet {
        Column(
            Modifier
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(GreenDeep, Green)))
                .padding(20.dp)
        ) {
            Text("Bol Khata", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
            Text(
                if (name.isBlank()) "Bolo, hisaab ho jaye" else "Hello, ${name.trim()}",
                color = Color.White.copy(alpha = 0.85f)
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
            label = { Text("Settings") }, selected = tab == Tab.SETTINGS, onClick = { onPick(Tab.SETTINGS) },
            icon = { Icon(Icons.Filled.Settings, contentDescription = null) }, modifier = itemPad
        )
    }
}
