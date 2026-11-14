package com.bolkhata.app

import org.json.JSONObject
import java.util.Locale

/**
 * The app's own assistant, for when no AI key answers (none saved, AI hidden, out of credit, no internet). It reads the
 * message by rule and works the books with the same tools the AI uses ([AiAgent.use]), so what it saves, changes and
 * shows is exactly what the AI would. It is tested on the AI's own everyday messages (AiEvalTest.localEngine), offline
 * and free, on every build.
 *
 * Each kind of message has its reader, asked in turn: [LocalTalk] small talk, [LocalEdit] changes to what is saved
 * (corrections, delete, restore, rename, accounts), [LocalAsk] questions, [LocalAdd] new entries. A reader that does not
 * know the message gives null and touches nothing; one that acts gives the reply.
 */
object LocalAgent {
    /** One message as every reader sees it, and the way to use a tool on the books. */
    class Said(
        /** As typed or heard. */
        val raw: String,
        /** In Roman letters (Urdu, Hindi or Arabic script read by its sound). */
        val roman: String,
        /** [roman] with its number words in digits ("dhai lakh" is 250000). */
        val counted: String,
        val host: AiAgent.Host,
        val now: Long,
        /** How the last look-up picked its entries (the AI's picking JSON), so "detail" or "aur food?" goes on from it. */
        val previous: String,
        /** The entries the last message saved, so "nahi 400 tha" or "ye delete kar do" changes them. */
        val justSaved: List<String>,
        /** The last thing the chat showed (sentence or card). */
        val lastShown: String,
        /** The last few lines of the chat, "Person: …" and "App: …", newest last. */
        val recent: String,
        /** What the tools leave for the chat: saved ids, the card, its rows, the picking. */
        val notes: AiAgent.Notes,
        private val onTool: (String, JSONObject, String) -> Unit
    ) {
        /** [counted] in lower case. */
        val lower: String = counted.lowercase(Locale.ROOT)
        /** [lower] cut into words (letters and digits; a decimal point stays inside its number). */
        val words: List<String> = lower.split(Regex("""[^\p{L}\p{N}.]+""")).map { it.trim('.') }.filter { it.isNotBlank() }
        /** The tools used so far for this message, in order. */
        val calls = mutableListOf<Pair<String, JSONObject>>()

        /** Uses one tool on the books, as the AI would. Its result as JSON; an "error" key when the tool refused. */
        fun use(name: String, input: JSONObject): JSONObject {
            val out = runCatching { AiAgent.use(name, input, host, now, notes) }.getOrElse { JSONObject().put("error", it.message ?: "failed").toString() }
            calls += name to input
            onTool(name, input, out)
            return runCatching { JSONObject(out) }.getOrElse { JSONObject().put("text", out) }
        }
    }

    /** What a reader made of the message: its sentence for the chat ("" when the card says it all), or "person|number" to ask with the option card. */
    class Reply(val text: String, val ask: String = "")

    /** A reader of one kind of message: null when the message is not its kind (and then it has used no tool). */
    fun interface Reader {
        fun read(s: Said): Reply?
    }

    /** Asked in this order: small talk, changes to what is saved, questions, then new entries. */
    private val readers: List<Reader> get() = listOf(LocalTalk, LocalEdit, LocalAsk, LocalAdd)

    /**
     * Reads one message and does it. The same answer as [AiAgent.run] gives: no sentence over a plain save, the card of a
     * look-up, the option card to ask. Null when the message was not understood (the chat then answers by its older rules).
     */
    fun run(
        said: String, host: AiAgent.Host, now: Long = System.currentTimeMillis(),
        roman: String = runCatching { Translit.toRoman(said) }.getOrDefault(said),
        previous: String = "", justSaved: List<String> = emptyList(), lastShown: String = "", recent: String = "",
        onTool: (name: String, input: JSONObject, result: String) -> Unit = { _, _, _ -> }
    ): AiAgent.Answer? = runCatching {
        val counted = AiAgent.counted(roman)
        val notes = AiAgent.Notes(said = (AiAgent.numbers(roman) + AiAgent.numbers(counted)).distinct(), lastShown = lastShown)
        val s = Said(said, roman, counted, host, now, previous, justSaved, lastShown, recent, notes, onTool)
        for (reader in readers) {
            val reply = reader.read(s) ?: continue
            // Only saved, all of it and nothing to ask: the Saved card says it, no sentence over it.
            val onlySaved = notes.added.isNotEmpty() && notes.skipped == 0 && s.calls.all { it.first == "add_entries" } && '?' !in reply.text
            return@runCatching AiAgent.Answer(
                if (onlySaved) "" else AiAgent.roman(reply.text), notes.added.toList(), notes.card, notes.listIds, notes.filter,
                ask = reply.ask.ifBlank { notes.ask }
            )
        }
        null
    }.getOrNull()
}

/** Greetings, "kaise ho", thanks, "acha": a short friendly answer, nothing saved. */
object LocalTalk : LocalAgent.Reader {
    override fun read(s: LocalAgent.Said): LocalAgent.Reply? =
        if (!NativeChatEngine.isSmallTalk(s.roman, s.now)) null
        else LocalAgent.Reply(NativeChatEngine.interpret(s.roman, emptyList(), now = s.now).reply)
}
