package com.bolkhata.app.screenshots

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.bolkhata.app.*
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Renders the real screens with sample data and saves them to app/screenshots/, so the look can be checked
 * without a phone. Runs only with `gradle testDebugUnitTest -Pscreenshots` (see the screenshots workflow).
 */
@OptIn(ExperimentalRoborazziApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w390dp-h844dp-xxhdpi")
class AppScreenshots {
    @get:Rule val compose = createComposeRule()

    private val day = 86_400_000L
    private val now = System.currentTimeMillis()

    private fun e(id: String, type: Type, amount: Double, cat: Cat, item: String, person: String = "", daysAgo: Int = 0, note: String = "") =
        (now - daysAgo * day).let { Entry(id, type, amount, cat, item, person, note, it, it) }

    private val entries = listOf(
        e("1", Type.KHARCHA, 50.0, Cat.TRANSPORT, "Petrol"),
        e("2", Type.KHARCHA, 6.0, Cat.KHANA, "Nashta", note = "Nashta subah"),
        e("3", Type.KHARCHA, 0.58, Cat.FEES, "Bank transfer fee", daysAgo = 1),
        e("4", Type.TRANSFER_IN, 3.0, Cat.OTHER, "Transfer", "Abdulaziz Sulaiman", daysAgo = 1),
        e("5", Type.KHARCHA, 23.0, Cat.SHOPPING, "Groceries", daysAgo = 2),
        e("6", Type.AAMDANI, 3000.0, Cat.TANKHWAH, "Salary", daysAgo = 3),
        e("7", Type.DIYA, 1500.0, Cat.UDHAAR, "Loans", "Hamza", daysAgo = 4),
        e("8", Type.WAPIS_MILA, 800.0, Cat.UDHAAR, "Loans", "Hamza", daysAgo = 2),
        e("9", Type.LIYA, 2000.0, Cat.UDHAAR, "Loans", "Nadir", daysAgo = 5),
        e("10", Type.KHARCHA, 35.0, Cat.KHANA, "Lunch", daysAgo = 2)
    )

    private fun shot(name: String, content: @Composable () -> Unit) {
        compose.setContent {
            CompositionLocalProvider(LocalHeader provides headerTheme("Clean")) {
                BolTheme(false) {
                    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { content() }
                }
            }
        }
        compose.waitForIdle()
        captureScreenRoboImage("screenshots/$name.png")
    }

    @Composable
    private fun WithBar(voice: VoiceState? = null, content: @Composable () -> Unit) {
        Box(Modifier.fillMaxSize()) {
            // As in the app: the list runs under the floating bar and ends with room for it.
            CompositionLocalProvider(LocalBarSpace provides 90.dp) { Column(Modifier.fillMaxSize()) { content() } }
            Box(Modifier.align(Alignment.BottomCenter)) {
                InputBar(onSend = {}, onMic = {}, placeholder = "Speak, type or ask…", onAttach = {}, voice = voice)
            }
        }
    }

    @Test fun home() = shot("01-home") {
        WithBar {
            HomeScreen(
                "Abrar", entries, "SAR", null, -1, {}, {}, {}, {}, {}, {}, {}, {}
            )
        }
    }

    @Test fun chat() = shot("02-chat") {
        val messages = listOf(
            ChatMessage("m1", true, "28 oct ko kitna kharcha huwa"),
            ChatMessage("m2", false, "Spent 79 SAR (3 entries, 28 Oct)", meta = "engine=NativeChatEngine; intent=ask; query=sum; matched=3"),
            ChatMessage("m3", true, "detail do"),
            ChatMessage("m4", false, "Petrol 50, Groceries 23, Nashta 6")
        )
        WithBar {
            ChatScreen(
                messages = messages, loading = false, entries = entries, currency = "SAR", subtitle = "Oct · 930.66 SAR spent",
                suggestions = emptyList(), showStock = false, onTab = {}, onMenu = {}, onSend = {},
                onClear = {}, onUndo = {}, onEditEntry = {}, onAddSuggestions = {}, onIgnoreSuggestions = {}
            )
        }
    }

    @Test fun chatVoice() = shot("02b-chat-voice") {
        WithBar(VoiceState(true, "Listening…", "aaj kitna kharcha huwa") {}) {
            ChatScreen(
                messages = listOf(ChatMessage("m1", true, "hello"), ChatMessage("m2", false, "Wa alaikum assalam! Add expenses or ask about your money.")),
                loading = false, entries = entries, currency = "SAR", subtitle = "Oct · 930.66 SAR spent",
                suggestions = emptyList(), showStock = false, onTab = {}, onMenu = {}, onSend = {},
                onClear = {}, onUndo = {}, onEditEntry = {}, onAddSuggestions = {}, onIgnoreSuggestions = {},
                voiceOn = true
            )
        }
    }

    @Test fun transactions() = shot("03-transactions") {
        TransactionsScreen(entries, "SAR", onEdit = {}, onAdd = {}, onMenu = {})
    }

    @Test fun reports() = shot("04-reports") {
        ReportsScreen(entries, "SAR", onMenu = {})
    }

    @Test fun toReceive() = shot("05-to-receive") {
        LedgerScreen(MainAcct.TO_RECEIVE, entries, "SAR", onClose = {}, onEdit = {}, onNew = {})
    }

    @Test fun personKhata() = shot("06-khata-hamza") {
        LedgerScreen(MainAcct.TO_RECEIVE, entries, "SAR", onClose = {}, onEdit = {}, onNew = {}, person = "Hamza")
    }

    @Test fun settings() = shot("07-settings") {
        SettingsScreen(
            "Abrar", "SAR", "ur-PK", false, "1.290", false, {}, {}, {}, {}, {}, {}, "Clean", {},
            logRepo = "abrar1499/bolkhata-logs", logTokenSet = true
        )
    }

    @Test fun sideMenu() = shot("10-side-menu") {
        DrawerMenu(Tab.HOME, "Abrar", "SAR", onCurrency = {}, onPick = {})
    }

    @Test fun entryMenu() = shot("08-entry-menu") {
        Box(Modifier.fillMaxSize()) {
            EntryMenu(entries.first(), "SAR", onEdit = {}, onMove = {}, onDelete = {}, onDismiss = {})
        }
    }

    @Test fun khataMenu() = shot("09-khata-menu") {
        KhataMenu("Abdulaziz Sulaiman", 12, 350.0, "SAR", onRename = {}, onMerge = {}, onDelete = {}, onDismiss = {})
    }
}
