package com.bolkhata.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatLogTest {
    @Test
    fun flagLineMarksTheAnswerItPointsAt() {
        val records = parseChatLog(
            listOf(
                """{"kind":"msg","id":"a","ts":1,"role":"user","text":"petrol kitna","meta":""}""",
                """{"kind":"msg","id":"b","ts":2,"role":"assistant","text":"0","meta":"q=fuel"}""",
                """{"kind":"flag","id":"b","ts":3,"expected":"60"}"""
            )
        )
        assertEquals(2, records.size)
        assertTrue(records[0].isUser)
        assertFalse(records[0].flagged)
        assertTrue(records[1].flagged)
        assertEquals("60", records[1].expected)
        assertEquals("q=fuel", records[1].meta)
    }

    @Test
    fun brokenAndUnknownLinesAreSkipped() {
        val records = parseChatLog(
            listOf(
                "not json",
                """{"kind":"flag","id":"missing","expected":"x"}""",
                """{"kind":"msg","id":"c","ts":5,"role":"assistant","text":"hi"}"""
            )
        )
        assertEquals(listOf("c"), records.map { it.id })
        assertFalse(records[0].flagged)
    }
}

class ChatLogUploadTest {
    private val utc = java.util.TimeZone.getTimeZone("UTC")
    private val day1 = 1_761_782_400_000L // 2025-10-30 00:00 UTC
    private val dayMs = 86_400_000L

    private fun rec(id: String, ts: Long, user: Boolean = true) = ChatLogRecord(id, ts, user, "t$id", "")

    @Test
    fun onlyDaysSinceLastSendAreUploaded() {
        val records = listOf(rec("a", day1 + 1000), rec("b", day1 + dayMs + 5), rec("c", day1 + 2 * dayMs + 5))
        val days = chatLogDays(records, since = day1 + dayMs + 3_600_000L, zone = utc)
        assertEquals(listOf("2025-10-31", "2025-11-01"), days.keys.toList())
    }

    @Test
    fun firstSendUploadsEveryDay() {
        val records = listOf(rec("a", day1 + 1000), rec("b", day1 + dayMs + 5))
        assertEquals(2, chatLogDays(records, since = 0L, zone = utc).size)
    }

    @Test
    fun jsonlHasOneLinePerMessageAndKeepsWrongMarks() {
        val text = chatLogJsonl(listOf(rec("a", 1), rec("b", 2, user = false).copy(flagged = true, expected = "60")))
        val lines = text.trimEnd().lines()
        assertEquals(2, lines.size)
        assertTrue(lines[1].contains("\"markedWrong\":true"))
        assertTrue(lines[1].contains("\"expected\":\"60\""))
    }
}

class ChatDiagnosticsReportTest {
    @Test
    fun wrongAnswersArePairedWithTheQuestionBeforeThem() {
        val records = listOf(
            ChatLogRecord("1", 1, true, "petrol kitna", ""),
            ChatLogRecord("2", 2, false, "0", "q=fuel", flagged = true, expected = "60"),
            ChatLogRecord("3", 3, true, "thanks", "")
        )
        val report = chatDiagnosticsReport(records, "1.280", "Test phone", 34, now = 0L)
        assertEquals(3, report.getInt("chatMessageCount"))
        assertEquals(1, report.getInt("markedWrongCount"))
        val wrong = report.getJSONArray("markedWrong").getJSONObject(0)
        assertEquals("petrol kitna", wrong.getString("question"))
        assertEquals("60", wrong.getString("expected"))
        assertEquals(3, report.getJSONArray("messages").length())
    }
}

class ChatLogRatingTest {
    @Test
    fun theLatestThumbWins() {
        val base = listOf(
            """{"kind":"msg","id":"a","ts":1,"role":"assistant","text":"x"}""",
            """{"kind":"good","id":"a","ts":2}"""
        )
        val liked = parseChatLog(base).single()
        assertTrue(liked.liked)
        assertFalse(liked.flagged)
        val changed = parseChatLog(base + """{"kind":"flag","id":"a","ts":3}""").single()
        assertTrue(changed.flagged)
        assertFalse(changed.liked)
        assertTrue(chatLogJsonl(listOf(liked)).contains("\"markedGood\":true"))
    }
}
