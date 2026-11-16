package com.bolkhata.app

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The side menu's settings said in the chat: what each saying changes, and what is left to the other readers. */
class AppSettingsTest {
    private val now = System.currentTimeMillis()

    /** What [said] asks to change ("setting=value"), or null when the settings reader leaves it. */
    private fun asked(said: String): String? {
        var got: String? = null
        val host = AiAgent.Host(entries = { emptyList() }, add = {}, update = {}, trash = {}, names = { emptyList() }, currency = "SAR",
            settings = { k, v -> got = "$k=$v"; JSONObject().put("done", "ok").toString() })
        val roman = Translit.toRoman(said)
        val counted = AiAgent.counted(roman)
        val s = LocalAgent.Said(said, roman, counted, host, now, "", emptyList(), "", "", AiAgent.Notes()) { _, _, _ -> }
        LocalSettings.read(s)
        return got
    }

    @Test
    fun settingsSaidInTheChat() {
        assertEquals("clear_chat=", asked("chat clear karo"))
        assertEquals("currency=PKR", asked("currency PKR karo"))
        assertEquals("currency=SAR", asked("currency riyal kar do"))
        assertEquals("language=urdu", asked("language urdu karo"))
        assertEquals("header=green", asked("header green kar do"))
        assertEquals("background=dark", asked("dark mode on karo"))
        assertEquals("open=report", asked("report kholo"))
    }

    @Test
    fun entriesAndQuestionsAreLeftAlone() {
        assertNull(asked("light ka bill 2200"))
        assertNull(asked("light bill"))
        assertNull(asked("hamid ka khata kholo"))
        assertNull(asked("kharcha dikhao"))
        assertNull(asked("petrol 60"))
    }

    @Test
    fun wordsForEachSetting() {
        assertEquals("SAR", AppSettings.currency("riyal"))
        assertNull(AppSettings.currency("hello"))
        assertEquals("ur-PK", AppSettings.language("Urdu"))
        assertEquals("Green", AppSettings.header("hara", listOf("Clean", "Red", "Green", "Navy")))
        assertEquals("dark:1", AppSettings.background("dark navy"))
        assertEquals("cashbook", AppSettings.page("cashbook"))
    }
}
