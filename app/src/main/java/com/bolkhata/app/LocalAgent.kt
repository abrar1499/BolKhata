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

        /** This message read as [text] instead (the earlier message with the answer to the app's question in it); its tools count here. */
        fun again(text: String): Said = Said(text, text, AiAgent.counted(text), host, now, previous, justSaved, "", recent, notes) { name, input, out ->
            calls += name to input
            onTool(name, input, out)
        }

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

    /** How every reader asks when a name fits several people. */
    private val WHICH = Regex("""^Which (\S+) do you mean: (.+)\?$""")
    private val order = mapOf("pehla" to 0, "pehle" to 0, "pehli" to 0, "first" to 0, "1" to 0, "dusra" to 1, "doosra" to 1, "dusre" to 1, "dusri" to 1,
        "doosri" to 1, "second" to 1, "2" to 1, "teesra" to 2, "tisra" to 2, "teesre" to 2, "teesri" to 2, "third" to 2, "3" to 2, "last" to -1, "akhri" to -1, "aakhri" to -1)
    /** Words around a picked name that are not part of it ("khan wala", "jo raza hai"). */
    private val around = setOf("wala", "wali", "wale", "walay", "waly", "walo", "jo", "ko", "ne", "se", "ka", "ki", "ke", "hai", "hain", "he", "the", "one",
        "sahab", "sahib", "bhai", "ji", "jee", "mr", "wo", "woh", "vo", "voh", "number", "no", "name", "naam", "i", "mean", "meant", "yes", "haan", "han", "please")

    /**
     * The answer to "Which Salim do you mean: …?" ("khan wala", "salim raza", "dusra"): the message that was asked about,
     * in Roman letters, with the name picked in place of the word. Null when the app did not ask that, or the answer picks
     * none of the names (then it is a new message).
     */
    fun picked(s: Said): String? {
        val shown = s.lastShown.trim()
        val which = WHICH.find(shown) ?: return null
        if (s.words.isEmpty() || s.words.size > 5 || s.words.any { w -> w.any(Char::isDigit) && w !in order }) return null
        val lines = s.recent.lines()
        val asked = lines.indexOfLast { it.startsWith("App: ") && it.removePrefix("App: ").trim().let { a -> a.isNotBlank() && shown.startsWith(a.take(60)) } }
        val request = lines.take(if (asked >= 0) asked else lines.size).lastOrNull { it.startsWith("Person: ") }?.removePrefix("Person: ")?.trim() ?: return null
        val word = which.groupValues[1].lowercase(Locale.ROOT)
        val choices = which.groupValues[2].split(Regex(""",\s*|\s+or\s+""")).map { it.trim() }.filter { it.isNotBlank() }
        val said = s.words.filter { it != word && it !in around }
        val pick = said.firstNotNullOfOrNull { order[it] }?.let { if (it < 0) choices.lastOrNull() else choices.getOrNull(it) }
            ?: choices.firstOrNull { it.equals(s.lower.trim(), true) }
            ?: choices.filter { c -> val parts = c.lowercase(Locale.ROOT).split(' ').filter { it != word }; said.any { w -> parts.any { it == w || Understand.closeName(w, it) } } }.singleOrNull()
            ?: return null
        // The word as it was typed: "saleem" when the question spells it "Salim".
        val roman = runCatching { Translit.toRoman(request) }.getOrDefault(request)
        val typed = Regex("""[\p{L}\p{N}]+""").findAll(roman).firstOrNull { it.value.lowercase(Locale.ROOT) == word }
            ?: Regex("""[\p{L}\p{N}]+""").findAll(roman).firstOrNull { Understand.closeName(it.value.lowercase(Locale.ROOT), word) } ?: return null
        return roman.replaceRange(typed.range, pick)
    }

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
    /** Words of a nod: after the app's own question ("Move them all to Trash?") they answer it, so that reader reads them. */
    private val nods = setOf("ok", "okay", "okk", "ji", "jee", "g", "haan", "han", "ha", "theek", "thik", "thek", "hai", "acha", "accha", "achha", "done", "sahi", "yes")
    /** Words of a how-to question or a task: "delete kaise karte hain" is not "kaise ho". */
    private val doing = setOf("kar", "karte", "karti", "karna", "karun", "karoon", "karen", "karein", "kare", "karu", "karoo", "delete", "add", "save",
        "entry", "entries", "edit", "change", "likhna", "likhun", "hisab", "khata", "pdf", "report")

    override fun read(s: LocalAgent.Said): LocalAgent.Reply? = when {
        s.lastShown.trim().endsWith("?") && s.words.all { it in nods } -> null
        s.words.any { it in doing } -> null
        !NativeChatEngine.isSmallTalk(s.roman, s.now) -> null
        else -> LocalAgent.Reply(NativeChatEngine.interpret(s.roman, emptyList(), now = s.now).reply)
    }
}
