package com.bolkhata.app

import java.util.Calendar
import java.util.Locale

class EditPlan(val changed: List<Pair<Entry, Entry>>, val deleted: List<Entry>, val message: String)

/** Fully local finance-domain NLU. It produces intents; the app executes them against its saved ledger. */
object NativeChatEngine {
    class Fixed(
        val text: String, val kind: String, val reply: String, val what: String = "",
        val filter: Query.Filter = Query.Filter(), val set: Map<String, String> = emptyMap(),
        val payer: String = "", val plan: EditPlan? = null,
        /** The engine did not understand the sentence; the app may ask Groq (when turned on) to rephrase it. */
        val unsure: Boolean = false
    )

    private val expense = setOf("expense", "expenses", "spent", "spend", "spending", "kharcha", "kharch", "cashout")
    private val income = setOf("income", "aamdani", "salary", "tankhwah", "earning", "earned", "cashin")
    private val lent = setOf("lent", "gave", "given", "diya", "diye", "udhar", "udhaar")
    private val borrowed = setOf("borrowed", "borrow", "took", "liya", "liye", "lia")
    private val questionWords = setOf("how", "much", "many", "what", "which", "when", "where", "show", "find", "search", "list", "total", "sum", "count", "highest", "largest", "biggest", "last", "latest", "recent", "kitna", "kitne", "kitni", "kitnay", "kitny", "kul", "hisab", "hisaab", "kharcha", "kharch", "dikhao", "batao", "kahan", "kab", "balance", "profit", "stock", "owe", "owed")
    private val stop = setOf(
        "a", "an", "the", "i", "me", "my", "we", "our", "you", "your", "did", "do", "does", "is", "are", "was", "were",
        "have", "has", "had", "what", "which", "when", "where", "who", "how", "much", "many", "show", "find", "search",
        "list", "tell", "please", "plz", "about", "of", "for", "on", "in", "at", "to", "from", "with", "and", "or",
        "all", "every", "entries", "entry", "records", "record", "transactions", "transaction", "spent", "spend",
        "spending", "expense", "expenses", "kharcha", "kharch", "ka", "ki", "ke", "ko", "se", "ne", "mein", "men",
        "par", "pe", "per", "kitna", "kitne", "kitni", "kitnay", "kitny", "hua", "huwa", "hue", "huay", "hai", "hain",
        "tha", "thi", "the", "batao", "btao", "dikhao", "karo", "kar", "do", "dein", "mujhe", "mera", "meri", "mere",
        "today", "yesterday", "aaj", "aj", "kal", "parso", "parson", "parsu", "parsoon", "parsun", "this", "last", "month", "months", "week", "weeks",
        "year", "years", "mahina", "mahine", "mahiny", "saal", "pichle", "pichhle", "previous", "ago", "day", "days",
        "date", "tareekh", "tarikh", "jan", "january", "feb", "february", "mar", "march", "apr", "april", "may",
        "jun", "june", "jul", "july", "aug", "august", "sep", "sept", "september", "oct", "october",
        "nov", "november", "dec", "december", "exactly", "equal", "between", "above", "over", "more", "than", "below", "under",
        "less", "except", "excluding", "siwa", "ilawa", "alawa", "people", "log", "logon", "cash", "out", "into",
        "it", "that", "this", "those", "these", "isko", "usko", "ye", "yeh", "inko",
        "open", "kholo", "khol", "kholen", "kholein", "karein", "karen", "kren", "tum", "aap", "es", "poora", "pura", "poore",
        "check", "hisab", "hisaab", "cashbook", "book", "name", "naam", "tak", "tk", "wal", "dhoondo", "dhundo", "talash",
        "jitni", "jitne", "jitny", "jitna", "parri", "pari", "padi", "pari", "parre", "pare", "pade", "parhi", "bhi", "b", "hui", "hoi", "hoti", "hote", "huin", "hoin", "bheje", "bhejy", "bhejay", "bhejey", "bheja", "bheji", "bhijwaye", "bhijwaya",
        "total", "report", "reports", "summary", "overview", "yearly", "annual", "annually", "alltime", "overall", "lifetime", "ever", "time", "day", "days",
        "delete", "delet", "del", "dlt", "remove", "hatao", "hata", "mitao", "clear", "wipe",
        "change", "edit", "update", "badlo", "correct", "fix", "move", "shift", "transfer", "send", "bhejo",
        "khata", "account", "paid", "pay", "by", "kiya", "kya", "khaya", "pese", "paise", "paisa", "diye", "diya", "us"
    )
    private val categories = mapOf(
        Cat.KHANA to setOf("food", "khana", "khaana", "grocery", "groceries", "meal", "meals", "ration", "sabzi"),
        Cat.TRANSPORT to setOf("transport", "petrol", "diesel", "fuel", "taxi", "uber", "careem", "car", "bus", "travel", "safar"),
        Cat.RENT to setOf("rent", "kiraya", "rental"),
        Cat.BIJLI to setOf("bill", "bills", "bijli", "electricity", "utility", "utilities"),
        Cat.MOBILE to setOf("mobile", "phone", "internet", "recharge", "data", "wifi"),
        Cat.GHAR to setOf("home", "ghar", "family", "senthome", "pakistan"),
        Cat.SHOPPING to setOf("shopping", "clothes", "kapre", "kapray"),
        Cat.SEHAT to setOf("health", "medical", "medicine", "dawai", "doctor", "hospital"),
        Cat.PERSONAL to setOf("personal", "haircut", "salon"),
        Cat.TANKHWAH to setOf("salary", "income", "tankhwah", "aamdani"),
        Cat.UDHAAR to setOf("loan", "loans", "udhar", "udhaar", "qarz", "debt"),
        Cat.FEES to setOf("fees", "fee", "visa", "permit", "fine"),
        Cat.EDU to setOf("education", "school", "books", "tuition"),
        Cat.CHARITY to setOf("charity", "gift", "zakat", "sadqa", "donation"),
        Cat.ASSETS to setOf("asset", "assets", "gold", "laptop"),
        Cat.SALES to setOf("sale", "sales", "bikri"),
        Cat.STOCK to setOf("purchase", "purchases", "stock", "maal"),
        Cat.OTHER to setOf("other", "misc", "miscellaneous")
    )
    private data class Bounds(val from: Long, val to: Long)

    fun interpret(
        input: String, entries: List<Entry>, contacts: List<String> = emptyList(),
        wordFixes: Map<String, String> = emptyMap(), history: List<ChatMessage> = emptyList(),
        currency: String = "SAR", now: Long = System.currentTimeMillis()
    ): Fixed {
        val raw = input.trim()
        if (raw.isBlank()) return chat("Please type a message and I'll help with your khata.")
        val roman = runCatching { Translit.toRoman(raw) }.getOrDefault(raw)
        val prepared = NumberWords.convert(roman)
        val understood = runCatching { Understand.read(prepared, contacts, wordFixes) }.getOrNull()
        val text = understood?.text ?: prepared
        val lower = text.lowercase(Locale.ROOT)
        val words = tokens(lower)
        val previousAssistant = history.lastOrNull { !it.isUser }?.text.orEmpty()
        val previousUser = history.lastOrNull { it.isUser }?.text

        // Resolve short answers to a contact clarification ("Mazhar", "Shahzad Iqbal").
        if (previousAssistant.startsWith("Which ", ignoreCase = true) &&
            previousAssistant.contains(" do you mean?", ignoreCase = true)) {
            val candidates = contacts.filter { it.isNotBlank() && previousAssistant.contains(it, ignoreCase = true) }
                .distinctBy { it.lowercase(Locale.ROOT) }
            // Use the user's literal answer before contact-name normalization can expand or rewrite it.
            val answerWords = tokens(prepared.lowercase(Locale.ROOT))
                .filter { it !in setOf("yes", "yeah", "yep", "haan", "han", "ji", "please", "plz", "ka", "ki", "ke", "khata", "account", "wala", "wali", "wale") }
            // The answer is exactly one saved name ("Shahbaz" for "Shahbaz or Qaisar Shahbaz Ghulam"): that one.
            val exact = candidates.filter { tokens(it) == answerWords }
            val chosen = exact.ifEmpty { candidates.filter { candidate ->
                val nameWords = tokens(candidate)
                answerWords.isNotEmpty() && answerWords.all { answer ->
                    nameWords.any { name -> name == answer || (answer.length >= 3 && name.startsWith(answer)) }
                }
            } }
            // "Salim Rashid" for "Nargis Salim Mazhar or Nargis Salim Rashid": the name matching most words.
            val best = if (chosen.size == 1) chosen else Understand.mostWordsMatched(answerWords, candidates)
                .takeIf { b -> b.size == 1 && answerWords.any { w -> tokens(b.single()).any { Understand.closeName(w, it) } } }
                .orEmpty()
            if (best.size == 1 && previousUser != null) {
                val name = best.single()
                // The question came from an entry ("170 food, shabaz returned 315"): finish that entry with the full name.
                if (previousUser.any { it.isDigit() }) {
                    val said = tokens(previousUser).firstOrNull { w -> tokens(name).any { Understand.closeName(w, it) } }
                    if (said != null) {
                        val fixed = Regex("""(?i)(?<![\p{L}])${Regex.escape(said)}(?![\p{L}])""").replaceFirst(previousUser, name)
                        return interpret(fixed, entries, contacts, wordFixes, emptyList(), currency, now)
                    }
                }
                return Fixed("", "open", "", filter = Query.Filter(persons = listOf(name)))
            }
            // Insist only when the answer is about these names; anything else is a new request ("<other name> ka khata").
            val aboutCandidates = answerWords.any { w -> candidates.any { c -> tokens(c).any { Understand.closeName(w, it) || (w.length >= 3 && it.startsWith(w)) } } }
            if (candidates.size > 1 && (answerWords.isEmpty() || aboutCandidates)) {
                return chat("Please choose one saved name: " + candidates.joinToString(" or ") + ".")
            }
        }

        // "All" is a valid answer only after the assistant explicitly asked about a multi-entry action.
        val allAnswerWords = setOf("all", "every", "entries", "entry", "sab", "sari", "saari", "tamam", "everything", "please", "haan", "han", "yes")
        val isAllFollowUp = words.isNotEmpty() && words.all { it in allAnswerWords }
        if (isAllFollowUp && previousUser != null &&
            (previousAssistant.contains("matching entries for", ignoreCase = true) &&
                previousAssistant.contains("Which one do you mean?", ignoreCase = true) ||
                previousAssistant.contains("Which entry in", ignoreCase = true))) {
            val previousIntent = interpret(previousUser, entries, contacts, wordFixes, emptyList(), currency, now)
            if (previousIntent.kind in setOf("delete", "change", "to_khata")) return previousIntent
        }

        // Date-only follow-ups and "search again" replay the conversation's search chain.
        // Reinterpreting only the last date message would lose the original type/category filters.
        if (history.isNotEmpty() && isDetailFollowUp(lower)) {
            val base = lastSearchFromHistory(history, entries, contacts, wordFixes, currency, now)
            if (base != null) return Fixed("", "ask", "", "list", base.filter)
        }
        val dateOnlyFollowUp = isDateFollowUp(lower)
        val repeatSearch = isRepeatSearch(lower)
        if (history.isNotEmpty() && (dateOnlyFollowUp || repeatSearch)) {
            val base = lastSearchFromHistory(history, entries, contacts, wordFixes, currency, now)
            if (base != null) {
                val date = if (dateOnlyFollowUp) dateBounds(lower, now) else null
                return if (date != null) Fixed("", "ask", "", base.what, copyFilter(base.filter, date))
                else if (repeatSearch) base
                else chat("Which date should I use for that search?")
            }
        }
        // "Sirf food", "aur petrol?", "Ali ki": the conversation's last search narrowed, however many turns ago it was.
        if (history.isNotEmpty() && lower.none(Char::isDigit) && words.size <= 5) {
            val base = lastSearchFromHistory(history, entries, contacts, wordFixes, currency, now)
            if (base != null) refine(lower, base, contacts, entries, now)?.let { return it }
        }
        if (dateOnlyFollowUp) {
            val date = dateBounds(lower, now)
            if (date != null) return Fixed("", "ask", "", "list", Query.Filter(from = date.from, to = date.to))
        }
        if (repeatSearch) return chat("I don't have a previous search in this conversation. What should I search for?")

        // Pronouns can only refer to stable IDs explicitly attached to the last assistant result.
        val ids = history.lastOrNull { !it.isUser && it.entryIds.isNotEmpty() }?.entryIds.orEmpty().toSet()
        if (ids.isNotEmpty() && Regex("""\b(it|that|this|those|these|isko|usko|ye|yeh|inko)\b""").containsMatchIn(lower)) {
            if (has(words, setOf("delete", "remove", "hatao", "mitao", "del", "dlt")))
                return Fixed("", "delete", "", filter = Query.Filter(ids = ids))
            val n = Regex("""(?<!\d)(\d[\d,]*(?:\.\d{1,2})?)(?!\d)""").find(lower)?.groupValues?.get(1)
            if (n != null && has(words, setOf("change", "update", "edit", "badlo", "make")))
                return Fixed("", "change", "", filter = Query.Filter(ids = ids), set = mapOf("amount" to n.replace(",", "")))
        }

        // "Ye entry Ashiq ke khata me bhej do", "3 no entry Haji Ashiq me daal do": entries the chat just showed.
        // Read from the words as said: number reading would turn "3 no" (number 3) into "3 9".
        // "Other me jo Mahnaz Parveen ki entries hain wo food me move karo": those entries change category.
        moveToCategory(roman.lowercase(Locale.ROOT), entries)?.let { return it }
        moveShown(text, roman.lowercase(Locale.ROOT), words, contacts, history, entries)?.let { return it }
        // "170 food mess after Shahbaz's 315", "315 wali entry ke baad 170 food aur 15 wifi bill daal do".
        afterEntry(text, entries, contacts, now)?.let { return it }

        if (Recat.isCommand(lower) || (words.contains("other") && has(words, setOf("sort", "fix", "categorize", "category", "theek"))))
            return Fixed("", "sort_other", "I'll group uncategorized expenses for you to review.")
        if (has(words, setOf("duplicate", "duplicates", "dupes", "dupe"))) return Fixed("", "duplicates", "")
        if (has(words, setOf("undo", "undoe", "undoo"))) return Fixed("", "undo", "")
        // Just a name ("kamran saeed", "nargis"): that khata, or a choice when several share it. Only whole words or
        // clear starts of a saved name count, so small talk never turns into a name.
        if (smallTalk(lower) == NOT_UNDERSTOOD) {
            // The words as said, before name matching rewrites them.
            val asSaid = tokens(prepared.lowercase(Locale.ROOT))
            val said = asSaid.filter { it !in setOf("ka", "ki", "ke", "khata", "account", "kholo", "open", "yar", "yaar", "plz") }
            if (said.size in 1..3 && asSaid.size <= 5 && said.all { it.length >= 3 }) {
                val hits = contacts.filter { c -> val parts = tokens(c); said.all { w -> parts.any { it == w || (w.length >= 4 && it.startsWith(w)) } } }
                    .distinctBy { it.lowercase(Locale.ROOT) }
                // A saved name said in full ("Kamran" beside "Haji Kamran Saeed") is that one.
                val exact = hits.filter { tokens(it) == said }
                if (exact.size == 1) return Fixed("", "open", "", filter = Query.Filter(persons = exact))
                if (hits.size == 1) return Fixed("", "open", "", filter = Query.Filter(persons = hits))
                if (hits.size in 2..4) return chat("Which " + hits.joinToString(" or ") + " do you mean?")
            }
        }
        if (understood?.choices?.isNotEmpty() == true)
            return chat("Which " + understood.choices.joinToString(" or ") + " do you mean?")
        if (understood?.kind == Understand.Kind.OPEN_KHATA && !understood.contact.isNullOrBlank())
            return Fixed("", "open", "", filter = Query.Filter(persons = listOf(understood.contact)))

        // "Hamid ka naam Hamid Akram kar do", "rename Hamid to Hamid Akram": the khata gets the new name.
        // The words as said: name matching may already have rewritten a new name that looks like a saved one.
        val said = prepared.lowercase(Locale.ROOT)
        // ("X wali entry ka naam Y kar do" renames one entry; that is an edit, below.)
        if (!has(words, setOf("entry", "entries", "wali", "wala", "wale"))) (renameUrdu.find(said) ?: renameEnglish.find(said))?.let { m ->
            val from = findKhata(m.groupValues[1], contacts, entries) ?: return chat("Which khata should I rename? Say its saved name.")
            val to = tokens(m.groupValues[2]).filter { it !in khataFiller }.joinToString(" ") { it.replaceFirstChar(Char::uppercase) }
            if (to.isBlank()) return chat("What should the new name be?")
            return Fixed("", "rename_khata", "", set = mapOf("kind" to from.first, "from" to from.second, "to" to to))
        }
        // "Hamid ka khata Nargis me merge kar do": every entry of the first khata goes into the second.
        mergeKhata.find(said)?.let { m ->
            val from = findKhata(m.groupValues[1], contacts, entries) ?: return chat("Which khata should I move? Say its saved name.")
            val to = findKhata(m.groupValues[2], contacts, entries) ?: return chat("Move it into which khata? Say its saved name.")
            return Fixed("", "move_khata", "", set = mapOf("kind" to from.first, "from" to from.second, "toKind" to to.first, "to" to to.second))
        }

        // "Bank fee ka alag expense khata bana ke us me dal do": the entries just shown get that name, so Expense
        // shows them as their own group inside the category.
        if (has(words, setOf("expense", "alag", "alehda", "alahda", "alehdah", "new", "naya", "nya")))
            newKhataName.find(lower)?.groupValues?.get(1)?.let { said ->
                val name = tokens(said).filter { it !in khataFiller }.joinToString(" ") { it.replaceFirstChar(Char::uppercase) }
                val rows = history.lastOrNull { !it.isUser && (it.listIds.isNotEmpty() || it.entryIds.isNotEmpty()) }
                    ?.let { it.listIds.ifEmpty { it.entryIds } }.orEmpty()
                if (name.isNotBlank() && rows.isNotEmpty())
                    return Fixed("", "change", "", filter = Query.Filter(ids = rows.toSet()), set = mapOf("item" to name))
            }

        val moveWords = setOf("move", "shift", "transfer", "send", "bhejo", "dal", "daal")
        if (has(words, moveWords) && has(words, setOf("khata", "account"))) {
            val destination = Accounts.khataNameMentionedIn(text, "", contacts)
            if (destination == null) return chat("Which saved khata should receive these entries? Please use its full name.")
            val selectionText = text.replace(destination, " ", ignoreCase = true)
            val selectionWords = tokens(selectionText)
            val screenSelection = has(selectionWords, setOf("all", "every", "sab", "sari", "saari", "these", "this", "inko", "ye", "yeh"))
            val filter = if (screenSelection) Query.Filter() else buildFilter(selectionText, contacts, now, false)
            return Fixed("", "to_khata", "", filter = filter, set = mapOf("person" to destination))
        }

        val paidByPhrase = lower.contains("paid by") || lower.contains("pay kiya") ||
            (has(words, setOf("paise", "pese", "paisa")) && has(words, setOf("diye", "diya", "paid", "pay")))
        if (paidByPhrase) {
            val payer = contacts.firstOrNull { it.isNotBlank() && lower.contains(it.lowercase()) }
                ?: Understand.matchContact(words, contacts).first
                ?: Regex("""\b([\p{L}]{3,})\s+(?:ne|has)\s+(?:paid|pay)\b""").find(lower)?.groupValues?.get(1)
                    ?.replaceFirstChar { it.uppercase() }
            if (payer == null) return chat("Who paid for that entry? Please give the person's saved name.")
            val selectionText = text.replace(payer, " ", ignoreCase = true)
            val filter = buildFilter(selectionText, contacts.filterNot { it.equals(payer, true) }, now, false)
            return if (filter.from == null && filter.to == null && filter.cats.isEmpty() && filter.items.isEmpty() && filter.ids.isEmpty())
                chat("Which expense did " + payer + " pay for? Add its date or item.")
            else Fixed("", "paid_by", "", filter = filter, payer = payer)
        }

        val wantsReread = has(words, setOf("wrong", "galat", "ghalat", "reread", "re-read")) &&
            has(words, setOf("entry", "entries", "wali", "wala", "last", "laptop", "petrol", "bill"))
        if (wantsReread) {
            val filter = buildFilter(text, contacts, now, false)
            return if (filter.isEmpty) chat("Which saved entry should I re-read? Give its item, person, or date.")
            else Fixed("", "reread", "", filter = filter)
        }

        // "23 oct ki entry dikhao" only shows; it is never an edit.
        val showOnly = has(words, setOf("dikhao", "dikha", "show", "batao", "btao", "list", "kholo", "open", "search", "find", "dhoondo", "dhundo", "talash")) &&
            !has(words, setOf("change", "badlo", "edit", "update", "correct", "fix", "theek"))
        val planned = if (showOnly) null else runCatching { Edits.plan(text, entries, now) }.getOrNull()
        if (planned != null) return Fixed("", "plan", "", plan = EditPlan(planned.changed, planned.deleted, planned.message))

        val delete = has(words, setOf("delete", "delet", "del", "dlt", "remove", "hatao", "hata", "mitao", "مٹاؤ"))
        val clear = has(words, setOf("clear", "wipe"))
        val all = has(words, setOf("all", "every", "sab", "sari", "saari", "tamam", "everything"))
        // "Is saal ka kharcha kholo", "is mahine ka total kharcha open karein": open means show, not edit.
        if (has(words, setOf("open", "kholo", "khol", "kholen", "kholein")) && !has(words, setOf("khata", "account")) &&
            (has(words, expense + income) || dateBounds(lower, now) != null)) {
            val f = buildFilter(text, contacts, now, false)
            val what = if (has(words, setOf("total", "kitna", "kitne", "kitni", "sum"))) "sum" else "list"
            return Fixed("", "ask", "", what, if (f.types.isEmpty() && f.cats.isEmpty() && f.persons.isEmpty()) withTypes(f, setOf(Type.KHARCHA)) else f)
        }
        val command = runCatching { Commands.read(text) }.getOrNull()
        if (command?.action == Commands.Action.DELETE_PERIOD && command.period != null) {
            val b = periodBounds(command.period, now)
            return Fixed("", "delete", "", filter = Query.Filter(from = b.from, to = b.to))
        }
        if (command?.action == Commands.Action.CLEAR_ALL) return Fixed("", "clear_all", "")
        if (command?.action == Commands.Action.SEARCH)
            return Fixed("", "ask", "", "list", buildFilter(command.query, contacts, now, false))
        if (command?.action == Commands.Action.EDIT)
            return Fixed("", "change", "", filter = buildFilter(command.query, contacts, now, false))
        if ((delete || clear) && !has(words, setOf("nahi", "nahin", "nhi", "not", "mat"))) {
            val f = buildFilter(text, contacts, now, false)
            if ((all || clear) && f.isEmpty) return Fixed("", "clear_all", "")
            return if (f.isEmpty) chat("Which entries should I delete? Give an item, person, date, or say 'delete all' to review everything.")
            else Fixed("", "delete", "", filter = f)
        }

        if (lower.contains("how are you") || lower.contains("what can you do") ||
            has(words, setOf("hello", "hi", "hey", "salam", "assalam", "thanks", "thank", "bye", "goodbye"))) {
            return talk(lower, now)
        }

        // Reports use the requested period instead of silently defaulting yearly reports to this month;
        // a report with its own dates ("from 19 aug to today") is a normal question over those dates.
        if (has(words, setOf("report", "reports", "summary", "overview")) && dateBounds(lower, now) == null) {
            val periodQuery = when {
                has(words, setOf("last", "previous", "pichle", "pichhle")) && has(words, setOf("year", "yearly", "annual", "annually", "saal")) -> "last year spent"
                has(words, setOf("year", "yearly", "annual", "annually", "saal")) -> "this year spent"
                has(words, setOf("all", "time", "overall", "lifetime", "ever", "alltime")) -> "all time spent"
                has(words, setOf("last", "previous", "pichle", "pichhle")) && has(words, setOf("month", "mahina", "mahine")) -> "last month spent"
                has(words, setOf("today", "aaj", "aj")) -> "today spent"
                else -> "this month spent"
            }
            return Fixed("", "chat", Questions.answer(periodQuery, entries, currency)?.text ?: "No saved transactions for that period.")
        }

        // "Do aur do kitna hota hai", "5 x 12": plain sums are answered, not saved.
        sum.find(NumberWords.convert(lower))?.let { m ->
            val a = m.groupValues[1].replace(",", "").toDouble()
            val b = m.groupValues[3].replace(",", "").toDouble()
            val v = when (m.groupValues[2]) {
                "+", "plus", "aur", "or", "and" -> a + b
                "-", "minus" -> a - b
                "x", "*", "into", "guna" -> a * b
                else -> if (b == 0.0) Double.NaN else a / b
            }
            if (!v.isNaN()) return Fixed("", "chat", "${fmt(a)} ${m.groupValues[2]} ${fmt(b)} = ${fmt(v)}")
        }

        // "Kal shaam ka 10 riyal khana likho", "add expense 15 riyal dinner", "kal hum ne 10 ka khana khaya":
        // an amount said with a write word (or done in the past, not asked about) is a new entry, never a search.
        val writes = has(words, setOf("likh", "likho", "likhdo", "likhna", "likhen", "likhein", "add", "save", "darj")) ||
            (has(words, setOf("khaya", "khaye", "khayi", "piya", "kiya", "kia", "liya", "lia", "kharida", "khareeda", "bhara")) &&
                !has(words, setOf("kitna", "kitne", "kitni", "how", "much", "total", "sum", "show", "dikhao", "batao")) && !text.trim().endsWith("?"))
        if (writes && text.any(Char::isDigit)) {
            val p = runCatching { Parser.parse(text, now) }.getOrNull()
            if (p != null && (p.entries.isNotEmpty() || p.unclear.isNotEmpty())) return Fixed(text, "entry", "")
        }

        if (isQuestion(lower)) {
            val financial = has(words, setOf("profit", "nafa", "munafa", "loss", "stock", "balance", "owe", "owed", "qarz", "udhar"))
            val special = if (financial && !text.any(Char::isDigit)) runCatching { Questions.answer(text, entries, currency) }.getOrNull() else null
            if (special != null && special.text.isNotBlank()) return Fixed("", "chat", special.text)
            // "Total check karo", "hisab": this month's spending.
            val defaultMonth = (has(words, expense) && has(words, setOf("how", "much", "kitna", "kitne", "kitni", "total", "sum"))) ||
                (has(words, setOf("total", "hisab", "hisaab")) && dateBounds(lower, now) == null)
            val filter = buildFilter(text, contacts, now, defaultMonth)
            val knownDomain = has(words, expense + income + lent + borrowed + setOf(
                "entries", "entry", "transactions", "transaction", "records", "record", "balance", "profit", "stock",
                "khata", "qarz", "udhar", "udhaar", "report", "summary", "overview", "total", "hisab", "hisaab", "cashbook"
            )) || words.any { token -> categories.values.any { token in it } } || dateBounds(lower, now) != null
            if (!knownDomain)
                return Fixed("", "chat", "I can answer from your saved transactions. Try 'food this month', 'last 5 entries', or 'Ali ka balance'.", unsure = true)
            val periodWords = setOf("today", "yesterday", "aaj", "aj", "kal", "this", "last", "month", "week", "year",
                "mahina", "mahine", "saal", "all", "time", "overall", "lifetime", "ever", "alltime")
            val simpleLookup = (words.any { token -> categories.values.any { token in it } } || has(words, expense + income)) &&
                !has(words, setOf("how", "much", "many", "kitna", "kitne", "kitni", "total", "sum", "count")) &&
                !has(words, periodWords - setOf("all"))
            val what = when {
                has(words, setOf("highest", "largest", "biggest", "maximum", "max", "most")) -> "highest"
                has(words, setOf("count", "howmany", "kitne", "kitni", "number")) -> "count"
                has(words, setOf("show", "find", "search", "list", "which", "kahan", "kab", "dikhao", "dikha", "cashbook")) ||
                    Regex("""\blast\s+\d+\b""").containsMatchIn(lower) || simpleLookup -> "list"
                else -> "sum"
            }
            // "kal kitna huwa tha" names no kind of money: people mean what they spent.
            val bare = what == "sum" && filter.types.isEmpty() && filter.cats.isEmpty() && filter.persons.isEmpty() &&
                filter.items.isEmpty() && filter.from != null
            return Fixed("", "ask", "", what, if (bare) withTypes(filter, setOf(Type.KHARCHA)) else filter)
        }

        if (understood?.kind == Understand.Kind.DELETE_LAST) return Fixed("", "delete", "", filter = Query.Filter(last = 1))
        if (understood?.kind == Understand.Kind.SUMMARY)
            return Fixed("", "chat", Questions.answer("this month spent", entries, currency)?.text ?: "No saved transactions yet.")
        if (runCatching { Parser.payStatus(text) }.getOrNull() != null) return Fixed(text, "entry", "")
        val split = runCatching { Split.detect(text, contacts) }.getOrNull()
        val parsed = if (split == null) runCatching { Parser.parse(text, now) }.getOrNull() else null
        if (split != null || (parsed != null && (parsed.entries.isNotEmpty() || parsed.unclear.isNotEmpty())))
            return Fixed(text, "entry", "")
        // A word that is in saved entries ("easypaisa", "tamara"): those entries.
        val terms = words.filter { it !in stop && it.length >= 3 }
        if (terms.isNotEmpty() && words.size <= 4 && lower.none(Char::isDigit) && smallTalk(lower) == NOT_UNDERSTOOD &&
            entries.any { e -> val hay = (e.item + e.note).lowercase(Locale.ROOT).replace(" ", ""); terms.all { hay.contains(it) } })
            return Fixed("", "ask", "", "list", Query.Filter(items = terms))
        return talk(lower, now)
    }

    private val renameUrdu = Regex("""^(.+?)\s+(?:ka|ki|ke)\s+(?:khata\s+(?:ka\s+)?)?(?:naam|name)\s+(?:badal\s+(?:ke|kar|k)\s+)?(.+?)\s+(?:kar|kr|karo|kro|rakh|rakho|rakhdo)\b""")
    private val renameEnglish = Regex("""^rename\s+(?:khata\s+)?(.+?)\s+(?:to|as)\s+(.+)$""")
    private val mergeKhata = Regex("""^(.+?)\s+(?:ka|ki|ke)\s+(?:poora|pura|sara|saara)?\s*khata\s+(.+?)\s+(?:me|mein|main|men|mai|into|in)\s+(?:merge|move|dal|daal|shift|mila|milao|bhej|bhejo)""")
    private val sum = Regex("""^\s*(\d[\d,]*(?:\.\d+)?)\s*(\+|plus|aur|or|and|-|minus|x|\*|into|guna|/|divided)\s*(?:by\s+)?(\d[\d,]*(?:\.\d+)?)\s*(?:=|kitna|kitne|kitni|hota|hote|hai|\?|$)""")

    /**
     * A saved khata named in [said]: a party (by its contact name), an income source or stock item (by its first
     * words), or an expense category, as kind to name.
     */
    private fun findKhata(said: String, contacts: List<String>, entries: List<Entry>): Pair<String, String>? {
        val w = tokens(said).filter { it !in khataFiller }
        if (w.isEmpty()) return null
        contacts.firstOrNull { tokens(it) == w }?.let { return "party" to it }
        Understand.matchContact(w, contacts).first?.let { return "party" to it }
        otherKhata(w, entries)?.let { return it }
        return w.firstNotNullOfOrNull { Query.catWords[it] }?.let { "expense" to it.label }
    }

    private val newKhataName = Regex("""^(.+?)\s+(?:ka|ki|ke)?\s*(?:ek\s+)?(?:alag|alehda|alahda|alehdah|new|naya|nya)?\s*(?:expense\s+)?(?:khata|khaata|group)\s+(?:bana|banao|banado|bnao|bna)""")
    private val khataFiller = setOf("ka", "ki", "ke", "ek", "alag", "alehda", "alahda", "new", "naya", "nya", "expense", "me", "mein", "ye", "yeh", "in", "inka", "inko", "plz")

    private val moveVerbs = setOf(
        "move", "shift", "transfer", "send", "bhej", "bhejo", "bhejdo", "bhijwa", "bhijwado", "dal", "daal", "dalo", "daalo",
        "daldo", "daaldo", "rakh", "rakho", "rakhdo", "put"
    )
    private val ordinalWords = mapOf(
        "pehli" to 1, "pehla" to 1, "pehle" to 1, "first" to 1, "doosri" to 2, "dusri" to 2, "doosra" to 2, "dusra" to 2,
        "second" to 2, "teesri" to 3, "tisri" to 3, "teesra" to 3, "third" to 3, "chothi" to 4, "chauthi" to 4,
        "chautha" to 4, "fourth" to 4, "panchvi" to 5, "panchwi" to 5, "fifth" to 5
    )
    // "3 no", "3rd entry", "2 aur 4 number wali": a row number. "50 wali entry" is an amount, so it needs a marker.
    private val numbered = Regex("""\b((?:\d{1,2}\s*(?:,|aur|and|or|&)\s*)*\d{1,2})\s*(?:(?:no|number|nambar|nmbr|st|nd|rd|th)\b\.?(?:\s*(?:wali|wala|wale))?(?:\s*(?:entry|entri|entries|record))?|(?:entry|entri|record))\b""")
    private val numberedAfter = Regex("""\b(?:entry|record)\s*(?:no|number|#)?\.?\s*(\d{1,2})\b""")
    private val ordinal = Regex("""\b(${ordinalWords.keys.joinToString("|")}|aakhri|akhri|last)\s+(?:wali\s+|wala\s+)?(?:entry|entri|record)\b""")
    private val pointing = Regex("""\b(ye|yeh|isko|isay|this|that|it|wo|woh|usko|inko|these|those)\b|\biss?\s+(?:entry|entri|record)""")
    private val plural = setOf("sab", "sari", "saari", "all", "these", "those", "inko", "entries", "records", "tamam")

    /** Row numbers asked for ("3 no entry", "2 aur 4 number wali", "teesri entry", "entry 5"), last = -1. */
    fun rowNumbers(lower: String): List<Int> {
        numbered.find(lower)?.let { m -> return Regex("""\d{1,2}""").findAll(m.groupValues[1]).map { it.value.toInt() }.toList() }
        numberedAfter.find(lower)?.let { return listOf(it.groupValues[1].toInt()) }
        ordinal.find(lower)?.let { m -> return listOf(ordinalWords[m.groupValues[1]] ?: -1) }
        return emptyList()
    }

    /**
     * "Ye entry Ashiq ke khata me bhej do / daal do / rakh do / move kar do", "3 no entry Haji Ashiq me send karo":
     * the entries the chat last listed or saved go to that person's khata. Null when the message isn't that
     * ("Ali ko 500 bhej diye" is a new entry, so a message with an amount never counts as pointing at entries).
     */
    private fun moveShown(
        text: String, lower: String, words: List<String>, contacts: List<String>, history: List<ChatMessage>, entries: List<Entry>
    ): Fixed? {
        // The words as said: spelling fixes turn "bhej" into "bheja" (money sent), which is a new entry.
        if (!has(tokens(lower), moveVerbs) && !Regex("""\bkhat[ae]y?\s+(?:me|mein|main|men)\s+(?:kar|kr)\s*do\b""").containsMatchIn(lower)) return null
        val shown = history.lastOrNull { !it.isUser && (it.listIds.isNotEmpty() || it.entryIds.isNotEmpty()) } ?: return null
        val rows = shown.listIds.ifEmpty { shown.entryIds }
        val numbers = rowNumbers(lower)
        val rest = lower.replace(numbered, " ").replace(numberedAfter, " ").replace(ordinal, " ")
        val ids = when {
            numbers.isNotEmpty() -> numbers.map { n -> rows.getOrNull(if (n == -1) rows.lastIndex else n - 1) ?: return chat(
                "I can't see entry number $n in the last list (it has ${rows.size}). Which one do you mean?") }
            pointing.containsMatchIn(rest) && rest.none { it.isDigit() } ->
                if (rows.size > 1 && !has(tokens(rest), plural))
                    return chat("Which one of the ${rows.size}? Say its number, e.g. \"2 no entry\", or say \"sab\".")
                else rows
            // "2000 aur 5000 wali entry": the shown rows with those amounts.
            else -> {
                val amounts = Regex("""\d[\d,]*(?:\.\d+)?""").findAll(rest).mapNotNull { it.value.replace(",", "").toDoubleOrNull() }.toList()
                val byId = entries.associateBy { it.id }
                rows.filter { id -> byId[id]?.let { e -> amounts.any { kotlin.math.abs(it - e.amount) < 0.005 } } == true }
                    .ifEmpty { return null }
            }
        }
        Accounts.khataNameMentionedIn(text, "", contacts)?.let {
            return Fixed("", "to_khata", "", filter = Query.Filter(ids = ids.toSet()), set = mapOf("person" to it))
        }
        otherKhata(tokens(rest), entries)?.let { (kind, name) ->
            return Fixed("", "to_khata", "", filter = Query.Filter(ids = ids.toSet()), set = mapOf("kind" to kind, "name" to name))
        }
        val destination = Accounts.khataNameMentionedIn(text, "", contacts)
            ?: Understand.matchContact(tokens(rest).filter { it !in moveVerbs }, contacts).let { (name, choices, _) ->
                if (name == null && choices.size >= 2) return chat("Move to which khata: " + choices.joinToString(" or ") + "?")
                name
            }
            ?: return chat("Which khata should get it? Say the person's saved name.")
        return Fixed("", "to_khata", "", filter = Query.Filter(ids = ids.toSet()), set = mapOf("person" to destination))
    }

    /**
     * An income source or stock item named by its first words ("Noor building contracting" → Noor Building Contracting Company
     * LTD), as ("income" | "business") to its saved name. Null when none or more than one fits equally.
     */
    private fun otherKhata(said: List<String>, entries: List<Entry>): Pair<String, String>? {
        val words = said.toSet()
        val names = entries.filter { it.type == Type.AAMDANI }.map { "income" to source(it).trim() } +
            stock(entries).map { "business" to it.name.trim() }
        val scored = names.filter { it.second.isNotBlank() }.distinctBy { it.second.lowercase(Locale.ROOT) }.map { c ->
            val parts = tokens(c.second.lowercase(Locale.ROOT))
            c to parts.takeWhile { it in words }.size.takeIf { it >= 2 || (it == 1 && parts.size == 1) }
        }.filter { it.second != null }
        val best = scored.maxOfOrNull { it.second!! } ?: return null
        return scored.filter { it.second == best }.singleOrNull()?.first
    }

    private val intoCategory = Regex("""\b(\p{L}+)\s+(?:me|mein|main|men|mai|into|to|in)\s+(?:move|shift|dal|daal|dalo|daalo|rakh|rakho|bhej|bhejo|send|transfer|put|kar|kr|karo|kro)\b""")
    private val categoryFiller = setOf(
        "other", "expense", "expenses", "kharcha", "category", "me", "mein", "main", "men", "jo", "jis", "jin", "wali", "wala",
        "wale", "entry", "entries", "ki", "ka", "ke", "ko", "se", "hain", "hai", "name", "naam", "jitni", "jitne", "bhi",
        "sab", "sari", "saari", "all", "wo", "woh", "ye", "yeh", "the", "from", "with", "per", "par", "account", "tamam"
    )

    /**
     * "Other me jo Mahnaz Parveen ki entry hain wo food me move karo": the expenses in the first category (Other) whose
     * name or note has those words move into the named category (Food). Null when no category is named as the target.
     */
    private fun moveToCategory(lower: String, entries: List<Entry>): Fixed? {
        // "chai 50 food me daal do" is a new entry, not a move.
        if (lower.any(Char::isDigit)) return null
        val m = intoCategory.findAll(lower).lastOrNull() ?: return null
        val word = m.groupValues[1]
        val target = Query.catWords[word]?.takeIf { it != Cat.UDHAAR } ?: return null
        val before = tokens(lower.substring(0, m.range.first))
        val from = before.mapNotNull { Query.catWords[it] }.firstOrNull { it != target }
        val terms = before.filter { it.length >= 3 && it !in categoryFiller && Query.catWords[it] == null && it.none(Char::isDigit) }
        if (from == null && terms.isEmpty()) return null
        val hits = entries.filter { e ->
            e.type == Type.KHARCHA && (from == null || e.category == from) &&
                terms.all { t -> (e.item + " " + e.note + " " + e.person).lowercase().contains(t) }
        }
        if (hits.isEmpty()) return chat("No " + (from?.label ?: "") + " entries with \"" + terms.joinToString(" ") + "\" found.")
        return Fixed("", "change", "", filter = Query.Filter(ids = hits.map { it.id }.toSet()), set = mapOf("category" to word))
    }

    private val afterEnglish = Regex("""^(.+?)\s+after\s+(.+)$""", RegexOption.IGNORE_CASE)
    private val afterUrdu = Regex("""^(.+?)\s+(?:(?:wali|wala|wale)\s+)?(?:entry\s+)?(?:ke|k|kay|ky|ki|se)\s+ba+d\s+(.+)$""", RegexOption.IGNORE_CASE)

    /**
     * New entries to be put right after an existing one: the existing one is found by its amount and, when said, its
     * person; the new ones go on its day, just after it. Null when the sentence isn't that or no such entry exists.
     */
    private fun afterEntry(text: String, entries: List<Entry>, contacts: List<String>, now: Long): Fixed? {
        val (fresh, anchor) = afterUrdu.find(text)?.let { it.groupValues[2] to it.groupValues[1] }
            ?: afterEnglish.find(text)?.let { it.groupValues[1] to it.groupValues[2] }
            ?: return null
        if (fresh.none { it.isDigit() }) return null
        // "22 oct shahbaz wali entry ke baad": the day is a date, not an amount; the entry is found by date and name.
        val day = dateBounds(anchor.lowercase(Locale.ROOT), now)
        val withoutDate = anchor.replace(Regex("""(?i)\b\d{1,2}\s*(?:st|nd|rd|th)?\s+(?:${monthWords.joinToString("|")})\b"""), " ")
        val amounts = Regex("""\d[\d,]*(?:\.\d+)?""").findAll(withoutDate).mapNotNull { it.value.replace(",", "").toDoubleOrNull() }.toList()
        val (one, choices, _) = Understand.matchContact(tokens(anchor), contacts)
        val names = listOfNotNull(one).ifEmpty { choices }
        if (amounts.isEmpty() && (names.isEmpty() || day == null)) return null
        val hit = entries.filter { e ->
            (amounts.isEmpty() || amounts.any { kotlin.math.abs(it - e.amount) < 0.005 }) &&
                (names.isEmpty() || names.any { n -> e.person.equals(n, true) || e.note.contains(n, true) }) &&
                (day == null || e.date in day.from..day.to)
        }.maxByOrNull { it.date } ?: return null
        val clean = fresh.replace(Regex("""(?i)\b(on the same day|same day|usi din|ussi din|isi din)\b"""), " ").trim()
        return Fixed(clean, "entry_after", "", filter = Query.Filter(ids = setOf(hit.id)))
    }

    /** The saved names offered in "Which A or B do you mean?" / "Please choose one saved name: A or B." */
    fun nameChoices(text: String): List<String> {
        val list = Regex("""^Which (.+) do you mean\?$""").find(text.trim())?.groupValues?.get(1)
            ?: Regex("""^Please choose one saved name: (.+)\.$""").find(text.trim())?.groupValues?.get(1)
            ?: Regex("""^Move to which khata: (.+)\?$""").find(text.trim())?.groupValues?.get(1)
            ?: return emptyList()
        return list.split(" or ").map { it.trim() }.filter { it.isNotEmpty() }.takeIf { it.size >= 2 }.orEmpty()
    }

    private fun chat(reply: String) = Fixed("", "chat", reply)

    private const val NOT_UNDERSTOOD = "Samajh nahi aaya. Jaise: 'chai 5', 'food is mahine', 'Ali ka khata'."

    /** Small talk, or the not-understood reply marked [Fixed.unsure]. */
    private fun talk(text: String, now: Long): Fixed = smallTalk(text, now).let { Fixed("", "chat", it, unsure = it == NOT_UNDERSTOOD) }

    private fun smallTalk(text: String, now: Long): String {
        val w = tokens(text)
        return when {
            has(w, setOf("hi", "hello", "hey", "good", "morning", "evening", "salam", "assalam", "assalamualaikum", "aoa")) ->
                "Wa alaikum assalam! Batayein?"
            has(w, setOf("date", "tareekh", "tarikh", "din", "day")) && has(w, setOf("aaj", "aj", "today", "kya", "kia", "what", "konsa", "kaunsa")) ->
                java.text.SimpleDateFormat("'Aaj' d MMM yyyy, EEEE.", Locale.ENGLISH).format(java.util.Date(now))
            has(w, setOf("time", "waqt", "baje", "bje")) && has(w, setOf("kya", "kia", "what", "kitne", "kitny")) ->
                java.text.SimpleDateFormat("h:mm a", Locale.ENGLISH).format(java.util.Date(now))
            has(w, setOf("hal", "haal", "kaise", "kaisa", "kaisi")) || (has(w, setOf("kya", "kia")) && has(w, setOf("hal", "haal"))) ->
                "Theek hoon, shukriya! Batayein?"
            has(w, setOf("how", "are", "you")) -> "Theek hoon! Batayein?"
            has(w, setOf("what", "can", "you", "do")) -> "I can record money, search transactions, calculate balances, correct entries, move khatas, read receipts, and remember facts you explicitly save."
            has(w, setOf("thanks", "thank", "shukriya", "shukria", "jazakallah")) -> "Koi baat nahi!"
            has(w, setOf("bye", "goodbye", "allahhafiz", "khuda")) -> "Allah Hafiz!"
            has(w, setOf("help", "commands", "examples", "madad")) ->
                "Try: 'chai 5', 'Ali ko 100 diye', 'food this month kitna', 'last 5 transactions', or 'petrol 50 nahi 40 tha'."
            else -> NOT_UNDERSTOOD
        }
    }

    private fun isQuestion(text: String): Boolean {
        val w = tokens(text)
        val periods = setOf("today", "yesterday", "aaj", "kal", "this", "last", "month", "week", "year", "mahine", "saal",
            "all", "time", "overall", "lifetime", "ever", "alltime", "yearly", "annual", "annually",
            "jan", "january", "feb", "february", "mar", "march", "apr", "april", "may", "jun", "june", "jul", "july",
            "aug", "august", "sep", "sept", "september", "oct", "october", "nov", "november", "dec", "december")
        val categoryMentioned = w.any { token -> categories.values.any { token in it } }
        val broadTypeMentioned = has(w, expense + income)
        val simpleLedgerLookup = (categoryMentioned || broadTypeMentioned) &&
            !has(w, setOf("how", "much", "many", "kitna", "kitne", "kitni", "total", "sum", "count")) &&
            !has(w, setOf("today", "yesterday", "aaj", "aj", "kal", "this", "last", "month", "week", "year",
                "mahina", "mahine", "saal", "time", "overall", "lifetime", "ever", "alltime"))
        val howMuch = has(w, setOf("kitna", "kitne", "kitni", "kitnay", "kitny"))
        return text.endsWith("?") || has(w, setOf("report", "summary", "overview")) ||
            (howMuch && has(w, periods + dayBeforeYesterday + "aj")) ||
            // "other me kitni entry hain", "food kitna": a count or sum over a category or kind, all time.
            (howMuch && (categoryMentioned || broadTypeMentioned || has(w, setOf("entry", "entries")))) ||
            has(w, setOf("show", "find", "search", "list", "total", "sum", "count", "highest", "largest", "biggest", "dikhao", "dikha", "cashbook")) ||
            (has(w, expense + income) && has(w, periods)) ||
            (categoryMentioned && has(w, periods)) || simpleLedgerLookup ||
            Regex("""\b(last|latest|recent)\s+\d+\s+(entry|entries|record|records|transaction|transactions)\b""").containsMatchIn(text) ||
            (has(w, questionWords) && (has(w, expense + income + setOf("balance", "khata", "petrol", "food", "salary", "udhar", "udhaar")) ||
                categoryMentioned))
    }

    private fun buildFilter(input: String, contacts: List<String>, now: Long, defaultMonth: Boolean): Query.Filter {
        val lower = input.lowercase(Locale.ROOT)
        val w = tokens(lower)
        val bounds = dateBounds(lower, now) ?: if (defaultMonth) monthBounds(now, 0) else null
        val types = when {
            has(w, expense) -> setOf(Type.KHARCHA)
            has(w, income) -> setOf(Type.AAMDANI)
            has(w, lent) -> setOf(Type.DIYA, Type.WAPIS_MILA)
            has(w, borrowed) -> setOf(Type.LIYA, Type.WAPIS_DIYA)
            has(w, setOf("sale", "sales", "bikri")) -> setOf(Type.SALE)
            has(w, setOf("purchase", "purchases", "bought", "kharida", "khareed")) -> setOf(Type.PURCHASE)
            else -> emptySet()
        }
        val cats = categories.filterValues { aliases -> w.any { it in aliases } }.keys
            .filter { it !in setOf(Cat.TANKHWAH, Cat.UDHAAR, Cat.SALES, Cat.STOCK) || types.isEmpty() }.toSet()
        val people = contacts.filter { it.isNotBlank() && lower.contains(it.lowercase()) }.distinctBy { it.lowercase() }
            .ifEmpty { Understand.matchContact(w, contacts).first?.let { listOf(it) }.orEmpty() }
        val last = Regex("""\b(?:last|latest|recent)\s+(\d+)\b""").find(lower)?.groupValues?.get(1)?.toIntOrNull()
            ?: if (Regex("""\b(last|latest|recent)\s+(entry|record|transaction)\b""").containsMatchIn(lower)) 1 else 0
        val range = Regex("""\bbetween\s+(\d[\d,]*(?:\.\d{1,2})?)\s+and\s+(\d[\d,]*(?:\.\d{1,2})?)\b""").find(lower)
        // "2800 wali entry", "2800 riyal wali": that amount (a day before a month name is a date, not an amount).
        val said = Regex("""\b(\d[\d,]*(?:\.\d+)?)\s*(?:wal|wali|wala|wale|riyal|riyals|sar|rs|rupay|rupees)\b""").find(lower)
            ?.groupValues?.get(1)?.replace(",", "")?.toDoubleOrNull()
        val exact = amountAfter(lower, "exactly|equal\\s+to|amount\\s+is|for") ?: said
        val min = exact ?: amountAfter(lower, "above|over|more\\s+than|greater\\s+than")
            ?: range?.groupValues?.get(1)?.replace(",", "")?.toDoubleOrNull()
        val max = exact ?: amountAfter(lower, "below|under|less\\s+than")
            ?: range?.groupValues?.get(2)?.replace(",", "")?.toDoubleOrNull()
        val exclude = Regex("""\b(?:except|excluding|without|siwa|ilawa|alawa)\s+([\p{L}]+)""").find(lower)?.groupValues?.get(1)
            ?.let { listOf(it) }.orEmpty()
        val personWords = people.flatMap { tokens(it.lowercase()) }.toSet()
        val knownCats = categories.values.flatten().toSet()
        val knownTypes = expense + income + lent + borrowed
        val itemAliases = setOf("petrol", "diesel", "taxi", "uber", "careem", "chai", "tea", "coffee", "milk", "doodh", "lunch", "breakfast", "dinner", "roti", "biryani", "bread", "water", "pani", "recharge")
        val itemWords = w.filter { it.length >= 2 && it !in stop && (it !in knownCats || it in itemAliases) && it !in knownTypes &&
            it !in personWords && it !in exclude && it.none(Char::isDigit) }.distinct().take(6)
        val noPeople = lower.contains("people ke ilawa") || lower.contains("people kay ilawa") ||
            lower.contains("without transfers") || lower.contains("excluding people")
        return Query.Filter(
            from = bounds?.from, to = bounds?.to, types = types, persons = people, cats = cats, items = itemWords,
            min = min, max = max, last = last, noPeople = noPeople, notItems = exclude
        )
    }

    private fun amountAfter(text: String, words: String): Double? =
        Regex("""\b(?:$words)\s+(\d[\d,]*(?:\.\d{1,2})?)\b""").find(text)?.groupValues?.get(1)
            ?.replace(",", "")?.toDoubleOrNull()

    private fun withTypes(f: Query.Filter, types: Set<Type>) = Query.Filter(
        from = f.from, to = f.to, types = types, persons = f.persons, cats = f.cats, items = f.items,
        min = f.min, max = f.max, last = f.last, noPeople = f.noPeople, notItems = f.notItems,
        ids = f.ids, createdFrom = f.createdFrom, createdTo = f.createdTo
    )

    private fun copyFilter(f: Query.Filter, b: Bounds) = Query.Filter(
        from = b.from, to = b.to, types = f.types, persons = f.persons, cats = f.cats, items = f.items,
        min = f.min, max = f.max, last = f.last, noPeople = f.noPeople, notItems = f.notItems,
        ids = f.ids, createdFrom = f.createdFrom, createdTo = f.createdTo
    )

    private fun dateBounds(text: String, now: Long): Bounds? {
        // "19 aug se aaj tak", "from 19 aug to today": from the first day to the end of the second.
        (Regex("""^(.*?)\s+(?:se|sy|say)\s+(.*?)\s+(?:tak|tk|takk|until|till)\b""").find(text)
            ?: Regex("""\bfrom\s+(.+?)\s+(?:to|till|until)\s+(.+)$""").find(text))?.let { m ->
            val a = dateBounds(m.groupValues[1], now)
            val b = dateBounds(m.groupValues[2], now)
            if (a != null && b != null) return Bounds(minOf(a.from, b.from), maxOf(a.to, b.to))
        }
        val w = tokens(text)
        fun day(offset: Int): Bounds {
            val c = Calendar.getInstance().apply { timeInMillis = now; add(Calendar.DAY_OF_YEAR, offset) }
            val start = (c.clone() as Calendar).apply {
                set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            }.timeInMillis
            val end = (c.clone() as Calendar).apply {
                set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 59); set(Calendar.SECOND, 59); set(Calendar.MILLISECOND, 999)
            }.timeInMillis
            return Bounds(start, end)
        }
        when {
            w.any { it in dayBeforeYesterday } || text.contains("day before yesterday") -> return day(-2)
            w.any { it in setOf("yesterday", "kal", "kl") } -> return day(-1)
            w.any { it in setOf("today", "aaj", "aj") } -> return day(0)
            "tomorrow" in w -> return day(1)
            "last" in w && "week" in w -> return weekBounds(now, -1)
            "this" in w && "week" in w -> return weekBounds(now, 0)
            "last" in w && w.any { it in monthNames } -> return monthBounds(now, -1)
            w.any { it in thisWords } && w.any { it in monthNames } -> return monthBounds(now, 0)
            "last" in w && w.any { it in setOf("year", "saal") } -> return yearBounds(now, -1)
            w.any { it in thisWords } && w.any { it in setOf("year", "saal") } -> return yearBounds(now, 0)
        }
        val lastDays = Regex("""\b(?:last|past)\s+(\d{1,3})\s+days?\b""").find(text)?.groupValues?.get(1)?.toIntOrNull()
        if (lastDays != null) return Bounds(now - lastDays.coerceAtMost(365) * 86_400_000L, now)
        if (Regex("""\b(last|past|previous)\s+day\b""").containsMatchIn(text)) return day(-1)
        if (Regex("""\blast\s+24\s+hours?\b""").containsMatchIn(text)) return Bounds(now - 86_400_000L, now)

        // Explicit day + month (e.g. "28 Oct", "Oct 28", "28 October 2026").
        val monthNumbers = mapOf(
            "jan" to 1, "january" to 1, "feb" to 2, "february" to 2, "mar" to 3, "march" to 3,
            "apr" to 4, "april" to 4, "may" to 5, "jun" to 6, "june" to 6, "jul" to 7, "july" to 7,
            "aug" to 8, "august" to 8, "sep" to 9, "sept" to 9, "september" to 9,
            "oct" to 10, "october" to 10, "nov" to 11, "november" to 11, "dec" to 12, "december" to 12
        )
        val dayMonth = Regex("""\b(\d{1,2})(?:st|nd|rd|th)?[\s,/-]+([a-z]+)(?:[\s,/-]+(\d{4}))?\b""")
            .find(text)
        val monthDay = Regex("""\b([a-z]+)[\s,/-]+(\d{1,2})(?:st|nd|rd|th)?(?:[\s,/-]+(\d{4}))?\b""")
            .find(text)
        val explicitMonth = dayMonth?.groupValues?.get(2) ?: monthDay?.groupValues?.get(1)
        val explicitDay = (dayMonth?.groupValues?.get(1) ?: monthDay?.groupValues?.get(2))?.toIntOrNull()
        val explicitYear = (dayMonth?.groupValues?.get(3)?.takeIf { it.isNotBlank() }
            ?: monthDay?.groupValues?.get(3)?.takeIf { it.isNotBlank() })?.toIntOrNull()
        val explicitMonthNumber = explicitMonth?.let { monthNumbers[it] }
        if (explicitDay != null && explicitMonthNumber != null) {
            val year = explicitYear ?: Calendar.getInstance().apply { timeInMillis = now }.get(Calendar.YEAR)
            val c = Calendar.getInstance().apply {
                isLenient = false
                set(year, explicitMonthNumber - 1, explicitDay, 0, 0, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val start = runCatching { c.timeInMillis }.getOrNull()
            if (start != null) {
                val end = Calendar.getInstance().apply { timeInMillis = start; set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 59); set(Calendar.SECOND, 59); set(Calendar.MILLISECOND, 999) }.timeInMillis
                return Bounds(start, end)
            }
        }

        val explicit = runCatching { Parser.extractDate(text, now).first }.getOrNull()
        if (explicit != null) {
            val start = Calendar.getInstance().apply { timeInMillis = explicit; set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis
            val end = Calendar.getInstance().apply { timeInMillis = explicit; set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 59); set(Calendar.SECOND, 59); set(Calendar.MILLISECOND, 999) }.timeInMillis
            return Bounds(start, end)
        }
        val monthMap = mapOf(
            "jan" to 0, "january" to 0, "feb" to 1, "february" to 1, "mar" to 2, "march" to 2,
            "apr" to 3, "april" to 3, "may" to 4, "jun" to 5, "june" to 5, "jul" to 6, "july" to 6,
            "aug" to 7, "august" to 7, "sep" to 8, "sept" to 8, "september" to 8, "oct" to 9, "october" to 9,
            "nov" to 10, "november" to 10, "dec" to 11, "december" to 11
        )
        val month = w.firstNotNullOfOrNull { monthMap[it] }
        if (month != null) {
            val currentYear = Calendar.getInstance().apply { timeInMillis = now }.get(Calendar.YEAR)
            val year = w.firstNotNullOfOrNull { it.toIntOrNull()?.takeIf { y -> y in 1900..2200 } } ?: currentYear
            val c = Calendar.getInstance().apply { set(year, month, 1, 0, 0, 0); set(Calendar.MILLISECOND, 0) }
            val from = c.timeInMillis
            c.add(Calendar.MONTH, 1); c.add(Calendar.MILLISECOND, -1)
            return Bounds(from, c.timeInMillis)
        }
        return null
    }

    private fun weekBounds(now: Long, offset: Int): Bounds {
        val c = Calendar.getInstance().apply {
            timeInMillis = now; firstDayOfWeek = Calendar.MONDAY; set(Calendar.DAY_OF_WEEK, Calendar.MONDAY); add(Calendar.WEEK_OF_YEAR, offset)
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        val from = c.timeInMillis
        c.add(Calendar.DAY_OF_YEAR, 7); c.add(Calendar.MILLISECOND, -1)
        return Bounds(from, c.timeInMillis)
    }

    private fun monthBounds(now: Long, offset: Int): Bounds {
        val c = Calendar.getInstance().apply {
            timeInMillis = now; set(Calendar.DAY_OF_MONTH, 1); add(Calendar.MONTH, offset)
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        val from = c.timeInMillis
        c.add(Calendar.MONTH, 1); c.add(Calendar.MILLISECOND, -1)
        return Bounds(from, c.timeInMillis)
    }

    private fun yearBounds(now: Long, offset: Int): Bounds {
        val c = Calendar.getInstance().apply {
            timeInMillis = now; set(Calendar.DAY_OF_YEAR, 1); add(Calendar.YEAR, offset)
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        val from = c.timeInMillis
        c.add(Calendar.YEAR, 1); c.add(Calendar.MILLISECOND, -1)
        return Bounds(from, c.timeInMillis)
    }

    private fun periodBounds(p: Commands.DatePeriod, now: Long): Bounds {
        val c = Calendar.getInstance().apply {
            timeInMillis = now; set(Calendar.YEAR, p.year)
            if (p.month != null) set(Calendar.MONTH, p.month - 1) else set(Calendar.MONTH, Calendar.JANUARY)
            if (p.day != null) set(Calendar.DAY_OF_MONTH, p.day) else set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        val from = c.timeInMillis
        when { p.day != null -> c.add(Calendar.DAY_OF_YEAR, 1); p.month != null -> c.add(Calendar.MONTH, 1); else -> c.add(Calendar.YEAR, 1) }
        c.add(Calendar.MILLISECOND, -1)
        return Bounds(from, c.timeInMillis)
    }

    /** "parson" in the ways people type it. */
    private val dayBeforeYesterday = setOf("parso", "parson", "parsu", "parsoon", "parsun", "prson")

    private val monthWords = setOf(
        "jan", "january", "feb", "february", "mar", "march", "apr", "april", "may", "jun", "june",
        "jul", "july", "aug", "august", "sep", "sept", "september", "oct", "october", "nov", "november", "dec", "december"
    )

    /** "This" and "month" as people write them: "es mahiny", "iss mahinay". */
    private val thisWords = setOf("this", "is", "iss", "es", "ess")
    private val monthNames = setOf("month", "mahina", "mahine", "mahiny", "mahinay", "mahene")

    private fun isDateFollowUp(text: String): Boolean {
        val w = tokens(text)
        val relative = dayBeforeYesterday + monthNames + setOf("yesterday", "today", "aaj", "aj", "kal", "tomorrow", "week", "year", "saal")
        val allowed = relative + monthWords + thisWords + setOf(
            "what", "about", "and", "how", "much", "this", "last", "day", "then", "phir", "on", "the", "of", "st", "nd", "rd", "th"
        )
        val hasCalendarDate = w.any { it in relative || it in monthWords } &&
            w.all { it in allowed || it.toIntOrNull()?.let { n -> n in 1..2200 } == true }
        return w.isNotEmpty() && hasCalendarDate
    }

    private fun lastSearchFromHistory(
        history: List<ChatMessage>, entries: List<Entry>, contacts: List<String>,
        wordFixes: Map<String, String>, currency: String, now: Long
    ): Fixed? {
        var lastSearch: Fixed? = null
        history.filter { it.isUser }.forEach { message ->
            val priorText = message.text.trim()
            val priorLower = priorText.lowercase(Locale.ROOT)
            when {
                isRepeatSearch(priorLower) -> Unit
                isDateFollowUp(priorLower) -> {
                    val date = dateBounds(priorLower, now)
                    if (date != null && lastSearch?.kind == "ask") {
                        val previous = lastSearch!!
                        lastSearch = Fixed("", "ask", "", previous.what, copyFilter(previous.filter, date))
                    } else {
                        val candidate = interpret(priorText, entries, contacts, wordFixes, emptyList(), currency, now)
                        if (candidate.kind == "ask") lastSearch = candidate
                    }
                }
                else -> {
                    val narrowed = lastSearch?.let { refine(priorLower, it, contacts, entries, now) }
                    val candidate = narrowed ?: interpret(priorText, entries, contacts, wordFixes, emptyList(), currency, now)
                    if (candidate.kind == "ask") lastSearch = candidate
                }
            }
        }
        return lastSearch
    }

    private val refineFiller = setOf(
        "sirf", "only", "bas", "aur", "and", "or", "bhi", "b", "what", "about", "wali", "wala", "wale", "ki", "ka", "ke",
        "me", "mein", "main", "se", "isme", "inme", "inmein", "ismein", "un", "in", "us", "usme", "sath", "saath", "wo", "woh"
    )

    /**
     * A short follow-up that only names a category, item, kind of money or person ("sirf food", "aur petrol?",
     * "Ali ki"): [base] narrowed to it, keeping its dates. Null for anything else, including a new date or a khata.
     */
    private fun refine(text: String, base: Fixed, contacts: List<String>, entries: List<Entry>, now: Long): Fixed? {
        val w = tokens(text)
        if (w.isEmpty() || w.size > 5 || text.any(Char::isDigit) || dateBounds(text, now) != null || "khata" in w) return null
        val f = buildFilter(text, contacts, now, false)
        val personWords = f.persons.flatMap { tokens(it) }.toSet()
        val catWords = categories.values.flatten().toSet()
        // Item words must be things actually saved, so small talk is never read as a search.
        val items = f.items.filter { it !in refineFiller }
        if (items.any { t -> entries.none { e -> (e.item + " " + e.note).contains(t, true) } }) return null
        if (f.cats.isEmpty() && f.persons.isEmpty() && items.isEmpty() && f.types.isEmpty()) return null
        if (!w.all { it in refineFiller || it in items || it in catWords || it in personWords || it in expense + income + lent + borrowed }) return null
        val b = base.filter
        return Fixed("", "ask", "", base.what, Query.Filter(
            from = b.from, to = b.to, types = f.types.ifEmpty { b.types }, persons = f.persons.ifEmpty { b.persons },
            cats = f.cats.ifEmpty { if (items.isNotEmpty()) emptySet() else b.cats }, items = items,
            min = b.min, max = b.max, noPeople = b.noPeople
        ))
    }

    /** "detail do", "details", "tafseel batao", "list dikhao": show the entries behind the last answer. */
    private fun isDetailFollowUp(text: String): Boolean {
        val w = tokens(text)
        val ask = setOf("detail", "details", "detailed", "tafseel", "tafsil", "breakdown", "list", "entries")
        val filler = ask + setOf("do", "de", "dein", "dena", "dikhao", "dikha", "batao", "btao", "show", "give", "me", "mujhe",
            "please", "plz", "ki", "ka", "ke", "sab", "sari", "saari", "un", "in", "inki", "unki", "wo", "woh", "the", "its", "of", "with")
        return w.isNotEmpty() && w.size <= 5 && has(w, ask) && w.all { it in filler }
    }

    private fun isRepeatSearch(text: String): Boolean {
        val w = tokens(text)
        return w.isNotEmpty() && (
            w.joinToString(" ") in setOf("search again", "find again", "show again", "repeat search", "same search", "search same", "dobara search", "phir se search", "wapas search") ||
                w == listOf("again") || w == listOf("dobara") || w == listOf("repeat")
            )
    }

    private fun has(words: List<String>, set: Set<String>) = words.any { it in set }
    private fun tokens(text: String) = text.lowercase(Locale.ROOT).split(Regex("""[^\p{L}\p{N}]+""")).filter { it.isNotBlank() }
}
