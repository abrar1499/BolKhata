package com.bolkhata.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The test AI key: which provider a key belongs to, and what its tokens cost. */
class AiTest {
    @Test
    fun keyTellsTheProvider() {
        assertEquals("Claude", Ai.provider("sk-ant-api03-abc")?.name)
        assertEquals("Groq", Ai.provider("gsk_abc")?.name)
        assertEquals("OpenRouter", Ai.provider("sk-or-v1-abc")?.name)
        assertEquals("Gemini", Ai.provider(" AIzaSyabc ")?.name)
        assertEquals("ChatGPT", Ai.provider("sk-proj-abc")?.name)
        assertNull(Ai.provider("hello"))
    }

    @Test
    fun costComesFromThePricePerMillion() {
        // 1,000,000 in at $4 and 500,000 out at $20 = $14.
        assertEquals("1,000,000 in · 500,000 out · $14.0000", Ai.usageText(1_000_000, 500_000, "claude-opus-5-5"))
        // A model whose price is not known here shows tokens only.
        assertEquals("340 in · 120 out", Ai.usageText(340, 120, "some-other-model"))
        // Tokens read back from the cache cost far less: 1,000,000 in, 900,000 of them cached at $0.20.
        assertEquals("1,000,000 in (900,000 cached) · 0 out · $0.5800", Ai.usageText(1_000_000, 0, "claude-opus-5-5", cached = 900_000))
    }
}
