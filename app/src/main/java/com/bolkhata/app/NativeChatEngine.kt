package com.bolkhata.app

import java.util.Calendar
import java.util.Locale

class EditPlan(val changed: List<Pair<Entry, Entry>>, val deleted: List<Entry>, val message: String)

/** Fully local finance-domain NLU. It produces intents; the app executes them against its saved ledger. */
object NativeChatEngine {
    class Fixed(
        val text: String, val kind: String, val reply: String, val what: String = "",
        val filter: Query.Filter = Query.Filter(), val set: Map<String, String> = emptyMap(),
        val payer: String = "", val plan: EditPlan? = null
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
        "today", "yesterday", "aaj", "aj", "kal", "parso", "parson", "this", "last", "month", "months", "week", "weeks",
        "year", "years", "mahina", "mahine", "mahiny", "saal", "pichle", "pichhle", "previous", "ago", "day", "days",
        "date", "tareekh", "tarikh", "jan", "january", "feb", "february", "mar", "march", "apr", "april", "may",
        "jun", "june", "jul", "july", "aug", "august", "sep", "sept", "september", "oct", "october",
        "nov", "november", "dec", "december", "exactly", "equal", "between", "above", "over", "more", "than", "below", "under",
        "less", "except", "excluding", "siwa", "ilawa", "alawa", "people", "log", "logon", "cash", "out", "into",
        "it", "that", "this", "those", "these", "isko", "usko", "ye", "yeh", "inko",
        "total", "report", "summary", "overview", "yearly", "annual", "annually", "alltime", "overall", "lifetime", "ever", "time", "day", "days",
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
        Cat.GHAR to setOf("home", "ghar", "family", "senthome"),
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
            val answerWords = words.filter { it !in setOf("yes", "yeah", "yep", "haan", "han", "ji", "please", "plz") }
            val chosen = candidates.filter { candidate ->
                val nameWords = tokens(candidate)
                answerWords.isNotEmpty() && answerWords.all { answer ->
                    nameWords.any { name -> name == answer || (answer.length >= 3 && name.startsWith(answer)) }
                }
            }
            if (chosen.size == 1 && previousUser != null) {
                return Fixed("", "open", "", filter = Query.Filter(persons = listOf(chosen.single())))
            }
            if (candidates.size > 1 && (answerWords.isEmpty() || chosen.size != 1)) {
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

        // Date-only follow-ups and "search again" inherit the last actual database search.
        val dateOnlyFollowUp = isDateFollowUp(lower)
        val repeatSearch = isRepeatSearch(lower)
        if (history.isNotEmpty() && (dateOnlyFollowUp || repeatSearch)) {
            val priorSearch = history.asReversed()
                .filter { it.isUser }
                .asSequence()
                .map { it.text to interpret(it.text, entries, contacts, wordFixes, emptyList(), currency, now) }
                .firstOrNull { it.second.kind == "ask" }
            if (priorSearch != null) {
                val priorDateOnly = history.lastOrNull { it.isUser }?.text?.takeIf { isDateFollowUp(it.lowercase(Locale.ROOT)) }
                val dateText = if (dateOnlyFollowUp) lower else priorDateOnly?.lowercase(Locale.ROOT)
                val date = dateText?.let { dateBounds(it, now) }
                val base = priorSearch.second
                return if (date != null) Fixed("", "ask", "", base.what, copyFilter(base.filter, date))
                else if (repeatSearch) base
                else chat("Which date should I use for that search?")
            }
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

        if (Recat.isCommand(lower) || (words.contains("other") && has(words, setOf("sort", "fix", "categorize", "category", "theek"))))
            return Fixed("", "sort_other", "I'll group uncategorized expenses for you to review.")
        if (has(words, setOf("duplicate", "duplicates", "dupes", "dupe"))) return Fixed("", "duplicates", "")
        if (has(words, setOf("undo", "undoe", "undoo"))) return Fixed("", "undo", "")
        if (understood?.choices?.isNotEmpty() == true)
            return chat("Which " + understood.choices.joinToString(" or ") + " do you mean?")
        if (understood?.kind == Understand.Kind.OPEN_KHATA && !understood.contact.isNullOrBlank())
            return Fixed("", "open", "", filter = Query.Filter(persons = listOf(understood.contact)))

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

        val planned = runCatching { Edits.plan(text, entries, now) }.getOrNull()
        if (planned != null) return Fixed("", "plan", "", plan = EditPlan(planned.changed, planned.deleted, planned.message))

        val delete = has(words, setOf("delete", "delet", "del", "dlt", "remove", "hatao", "hata", "mitao", "مٹاؤ"))
        val clear = has(words, setOf("clear", "wipe"))
        val all = has(words, setOf("all", "every", "sab", "sari", "saari", "tamam", "everything"))
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
            return chat(smallTalk(lower))
        }

        // Reports use the requested period instead of silently defaulting yearly reports to this month.
        if (has(words, setOf("report", "summary", "overview"))) {
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

        if (isQuestion(lower)) {
            val financial = has(words, setOf("profit", "nafa", "munafa", "loss", "stock", "balance", "owe", "owed", "qarz", "udhar"))
            val special = if (financial && !text.any(Char::isDigit)) runCatching { Questions.answer(text, entries, currency) }.getOrNull() else null
            if (special != null && special.text.isNotBlank()) return Fixed("", "chat", special.text)
            val defaultMonth = has(words, expense) && has(words, setOf("how", "much", "kitna", "kitne", "kitni", "total", "sum"))
            val filter = buildFilter(text, contacts, now, defaultMonth)
            val knownDomain = has(words, expense + income + lent + borrowed + setOf(
                "entries", "entry", "transactions", "transaction", "records", "record", "balance", "profit", "stock",
                "khata", "qarz", "udhar", "udhaar", "report", "summary", "overview"
            )) || words.any { token -> categories.values.any { token in it } } || dateBounds(lower, now) != null
            if (!knownDomain)
                return chat("I can answer from your saved transactions. Try 'food this month', 'last 5 entries', or 'Ali ka balance'.")
            val periodWords = setOf("today", "yesterday", "aaj", "aj", "kal", "this", "last", "month", "week", "year",
                "mahina", "mahine", "saal", "all", "time", "overall", "lifetime", "ever", "alltime")
            val simpleLookup = (words.any { token -> categories.values.any { token in it } } || has(words, expense + income)) &&
                !has(words, setOf("how", "much", "many", "kitna", "kitne", "kitni", "total", "sum", "count")) &&
                !has(words, periodWords - setOf("all"))
            val what = when {
                has(words, setOf("highest", "largest", "biggest", "maximum", "max", "most")) -> "highest"
                has(words, setOf("count", "howmany", "kitne", "kitni", "number")) -> "count"
                has(words, setOf("show", "find", "search", "list", "which", "kahan", "kab")) ||
                    Regex("""\blast\s+\d+\b""").containsMatchIn(lower) || simpleLookup -> "list"
                else -> "sum"
            }
            return Fixed("", "ask", "", what, filter)
        }

        if (understood?.kind == Understand.Kind.DELETE_LAST) return Fixed("", "delete", "", filter = Query.Filter(last = 1))
        if (understood?.kind == Understand.Kind.SUMMARY)
            return Fixed("", "chat", Questions.answer("this month spent", entries, currency)?.text ?: "No saved transactions yet.")
        if (runCatching { Parser.payStatus(text) }.getOrNull() != null) return Fixed(text, "entry", "")
        val split = runCatching { Split.detect(text, contacts) }.getOrNull()
        val parsed = if (split == null) runCatching { Parser.parse(text, now) }.getOrNull() else null
        if (split != null || (parsed != null && (parsed.entries.isNotEmpty() || parsed.unclear.isNotEmpty())))
            return Fixed(text, "entry", "")
        return chat(smallTalk(lower))
    }

    private fun chat(reply: String) = Fixed("", "chat", reply)

    private fun smallTalk(text: String): String {
        val w = tokens(text)
        return when {
            has(w, setOf("hi", "hello", "hey", "good", "morning", "evening", "salam", "assalam", "assalamualaikum", "aoa")) ->
                "Wa alaikum assalam! I'm your offline khata assistant. Add expenses, ask about your money, or manage saved entries."
            has(w, setOf("how", "are", "you")) -> "I'm ready to help with your khata. I work locally on this phone."
            has(w, setOf("what", "can", "you", "do")) -> "I can record money, search transactions, calculate balances, correct entries, move khatas, read receipts, and remember facts you explicitly save."
            has(w, setOf("thanks", "thank", "shukriya", "shukria", "jazakallah")) -> "You're welcome. Your khata stays on this device."
            has(w, setOf("bye", "goodbye", "allahhafiz", "khuda")) -> "Take care. Your saved khata will be here when you return."
            has(w, setOf("help", "commands", "examples", "madad")) ->
                "Try: 'chai 5', 'Ali ko 100 diye', 'food this month kitna', 'last 5 transactions', or 'petrol 50 nahi 40 tha'."
            else -> "I can help with your saved khata, transactions, balances, searches and edits. I don't invent answers or use an online AI service."
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
        return text.endsWith("?") || has(w, setOf("report", "summary", "overview")) ||
            has(w, setOf("show", "find", "search", "list", "total", "sum", "count", "highest", "largest", "biggest")) ||
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
        val exact = amountAfter(lower, "exactly|equal\\s+to|amount\\s+is|for")
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

    private fun copyFilter(f: Query.Filter, b: Bounds) = Query.Filter(
        from = b.from, to = b.to, types = f.types, persons = f.persons, cats = f.cats, items = f.items,
        min = f.min, max = f.max, last = f.last, noPeople = f.noPeople, notItems = f.notItems,
        ids = f.ids, createdFrom = f.createdFrom, createdTo = f.createdTo
    )

    private fun dateBounds(text: String, now: Long): Bounds? {
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
            w.any { it in setOf("parso", "parson") } || text.contains("day before yesterday") -> return day(-2)
            w.any { it in setOf("yesterday", "kal", "kl") } -> return day(-1)
            w.any { it in setOf("today", "aaj", "aj") } -> return day(0)
            "tomorrow" in w -> return day(1)
            "last" in w && "week" in w -> return weekBounds(now, -1)
            "this" in w && "week" in w -> return weekBounds(now, 0)
            "last" in w && w.any { it in setOf("month", "mahina", "mahine") } -> return monthBounds(now, -1)
            w.any { it in setOf("this", "is", "iss") } && w.any { it in setOf("month", "mahina", "mahine") } -> return monthBounds(now, 0)
            "last" in w && w.any { it in setOf("year", "saal") } -> return yearBounds(now, -1)
            w.any { it in setOf("this", "is", "iss") } && w.any { it in setOf("year", "saal") } -> return yearBounds(now, 0)
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

    private val monthWords = setOf(
        "jan", "january", "feb", "february", "mar", "march", "apr", "april", "may", "jun", "june",
        "jul", "july", "aug", "august", "sep", "sept", "september", "oct", "october", "nov", "november", "dec", "december"
    )

    private fun isDateFollowUp(text: String): Boolean {
        val w = tokens(text)
        val relative = setOf("yesterday", "today", "aaj", "aj", "kal", "parso", "parson", "tomorrow", "month", "week", "year", "mahina", "mahine", "saal")
        val allowed = relative + monthWords + setOf(
            "what", "about", "and", "how", "much", "this", "last", "day", "then", "phir", "on", "the", "of", "st", "nd", "rd", "th"
        )
        val hasCalendarDate = w.any { it in relative || it in monthWords } &&
            w.all { it in allowed || it.toIntOrNull()?.let { n -> n in 1..2200 } == true }
        return w.isNotEmpty() && hasCalendarDate
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
