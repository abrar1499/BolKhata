package com.bolkhata.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class NativeChatEngineTest {
    private val now = Calendar.getInstance().apply {
        set(2026, Calendar.OCTOBER, 30, 12, 0, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private val entries = listOf(
        entry("tea-today", "Tea", 5.0, Cat.KHANA, 0),
        entry("petrol-yesterday", "Petrol", 60.0, Cat.TRANSPORT, 1),
        entry("food-last-week", "Lunch", 35.0, Cat.KHANA, 5),
        entry("salary", "Salary", 3000.0, Cat.TANKHWAH, 2, Type.AAMDANI)
    )

    private fun entry(id: String, item: String, amount: Double, cat: Cat, daysAgo: Int, type: Type = Type.KHARCHA): Entry {
        val date = Calendar.getInstance().apply {
            timeInMillis = now
            add(Calendar.DAY_OF_YEAR, -daysAgo)
        }.timeInMillis
        return Entry(id, type, amount, cat, item, "", item, date, date)
    }

    private fun read(text: String, history: List<ChatMessage> = emptyList()) =
        NativeChatEngine.interpret(text, entries, now = now, history = history)

    @Test fun aShortFollowUpNarrowsTheLastSearchEvenManyTurnsLater() {
        val talk = listOf(
            ChatMessage("u1", true, "how much did I spend on food this month?"), ChatMessage("a1", false, "Spent 40 SAR"),
            ChatMessage("u2", true, "hello"), ChatMessage("a2", false, "Wa alaikum assalam!"),
            ChatMessage("u3", true, "thanks"), ChatMessage("a3", false, "You're welcome.")
        )
        val r = read("aur petrol?", talk)
        assertEquals("ask", r.kind)
        assertEquals(listOf("petrol"), r.filter.items)
        assertTrue(r.filter.from != null)
        // Small talk is never read as a narrower search.
        assertEquals("chat", read("kya hal hai", talk).kind)
    }

    @Test fun anAmountWithAWriteWordIsANewEntryNotASearch() {
        assertEquals("entry", read("kal shaam ka 10 riyal khana likho").kind)
        assertEquals("entry", read("add expense 15 riyal dinner").kind)
        assertEquals("entry", read("kal subah ke naashte ka 10 riyal kharcha likh do").kind)
    }

    @Test fun openingAPeriodsSpendingShowsItInsteadOfEditing() {
        val year = read("is saal ka kharcha kholo")
        assertEquals("ask", year.kind)
        assertEquals("list", year.what)
        assertEquals(setOf(Type.KHARCHA), year.filter.types)
        assertTrue(year.filter.items.isEmpty())
        val month = read("is mahine ka total kharcha open karein")
        assertEquals("ask", month.kind)
        assertEquals("sum", month.what)
        assertTrue(month.filter.items.isEmpty())
    }

    @Test fun showingADaysEntriesIsNeverAnEdit() {
        val r = read("23 oct ki entry dikhao")
        assertEquals("ask", r.kind)
        assertEquals("list", r.what)
    }

    @Test fun totalCheckIsThisMonthsSpending() {
        val r = read("total check karo")
        assertEquals("ask", r.kind)
        assertEquals("sum", r.what)
        assertTrue(r.filter.from != null)
        assertTrue(r.filter.items.isEmpty())
    }

    @Test fun aNewExpenseKhataNamesTheShownEntries() {
        val shown = listOf(ChatMessage("u1", true, "bank fee"), ChatMessage("a1", false, "Bank fee 0.58 / Bank fee 2.07", listIds = listOf("tea-today", "petrol-yesterday")))
        val r = read("Expense me Bank fee khata bana k os me dal do", shown)
        assertEquals("change", r.kind)
        assertEquals(setOf("tea-today", "petrol-yesterday"), r.filter.ids)
        assertEquals("Bank Fee", r.set["item"])
    }

    @Test fun recognizesLastFiveTransactionsEvenThoughTheRequestContainsANumber() {
        val result = read("show last 5 transactions")
        assertEquals("ask", result.kind)
        assertEquals("list", result.what)
        assertEquals(5, result.filter.last)
    }

    @Test fun composesFoodCategoryExpenseTypeAndThisMonth() {
        val result = read("how much did I spend on food this month?")
        assertEquals("ask", result.kind)
        assertEquals("sum", result.what)
        assertEquals(setOf(Type.KHARCHA), result.filter.types)
        assertEquals(setOf(Cat.KHANA), result.filter.cats)
        assertTrue(result.filter.from != null)
        assertTrue(result.filter.to != null)
    }

    @Test fun understandsRomanUrduAndYesterdayItemSearch() {
        val result = read("kal petrol pe kitna kharcha hua?")
        assertEquals("ask", result.kind)
        assertEquals(listOf("petrol"), result.filter.items)
        assertEquals(setOf(Type.KHARCHA), result.filter.types)
        assertTrue(result.filter.from != null)
        assertEquals(result.filter.from, Query.day("2026-10-29"))
    }

    @Test fun lastFiveExpensesBecomeAConfirmedDeleteFilter() {
        val result = read("delete last 5 expenses")
        assertEquals("delete", result.kind)
        assertEquals(5, result.filter.last)
        assertEquals(setOf(Type.KHARCHA), result.filter.types)
    }

    @Test fun newExpenseIsReturnedAsAnEntryIntent() {
        val result = read("chai 12")
        assertEquals("entry", result.kind)
        assertTrue(result.text.contains("12"))
    }

    @Test fun greetingsDoNotBecomeTransactions() {
        val result = read("hello")
        assertEquals("chat", result.kind)
        assertTrue(result.reply.isNotBlank())
    }

    @Test fun dateFollowUpKeepsThePreviousQueryFilters() {
        val history = listOf(
            ChatMessage("u1", true, "how much did I spend on food this month?"),
            ChatMessage("a1", false, "Spent this month")
        )
        val result = read("what about yesterday?", history)
        assertEquals("ask", result.kind)
        assertEquals(setOf(Cat.KHANA), result.filter.cats)
        assertEquals(setOf(Type.KHARCHA), result.filter.types)
        assertEquals(Query.day("2026-10-29"), result.filter.from)
    }

    @Test fun explicitDayMonthSearchUsesTheExactDayAndDoesNotTreatMonthAsAnItem() {
        val result = read("28 oct expense")
        assertEquals("ask", result.kind)
        assertEquals(setOf(Type.KHARCHA), result.filter.types)
        assertTrue(result.filter.items.isEmpty())
        assertEquals(Query.day("2026-10-28"), result.filter.from)
        assertEquals(86_399_999L, result.filter.to!! - result.filter.from!!)
    }

    @Test fun dateOnlyFollowUpInheritsThePreviousExpenseSearch() {
        val history = listOf(
            ChatMessage("u1", true, "today expense"),
            ChatMessage("a1", false, "Nothing found")
        )
        val result = read("28 oct", history)
        assertEquals("ask", result.kind)
        assertEquals(setOf(Type.KHARCHA), result.filter.types)
        assertEquals(Query.day("2026-10-28"), result.filter.from)
    }

    @Test fun searchAgainRepeatsTheLastDatabaseSearch() {
        val history = listOf(
            ChatMessage("u1", true, "show expenses between 10 and 50"),
            ChatMessage("a1", false, "Found 2 entries")
        )
        val result = read("search again", history)
        assertEquals("ask", result.kind)
        assertEquals(10.0, result.filter.min!!, 0.001)
        assertEquals(50.0, result.filter.max!!, 0.001)
        assertEquals(setOf(Type.KHARCHA), result.filter.types)
    }

    @Test fun searchAgainAfterDateOnlyFollowUpKeepsOriginalExpenseFilter() {
        val history = listOf(
            ChatMessage("u1", true, "today expense"),
            ChatMessage("a1", false, "Nothing found"),
            ChatMessage("u2", true, "28 oct"),
            ChatMessage("a2", false, "Nothing found")
        )
        val result = read("search again", history)
        assertEquals("ask", result.kind)
        assertEquals("sum", result.what)
        assertEquals(setOf(Type.KHARCHA), result.filter.types)
        assertEquals(Query.day("2026-10-28"), result.filter.from)
        assertEquals(86_399_999L, result.filter.to!! - result.filter.from!!)
    }

    @Test fun bareDateAndSearchDateAreReadOnlyListQueries() {
        val bare = read("28 oct")
        assertEquals("ask", bare.kind)
        assertEquals("list", bare.what)
        assertEquals(Query.day("2026-10-28"), bare.filter.from)

        val search = read("search 28 oct")
        assertEquals("ask", search.kind)
        assertEquals("list", search.what)
        assertEquals(Query.day("2026-10-28"), search.filter.from)
    }

    @Test fun showAllEntriesThisMonthReturnsAListQuery() {
        val result = read("show all entry this month")
        assertEquals("ask", result.kind)
        assertEquals("list", result.what)
        assertEquals(Query.day("2026-10-01"), result.filter.from)
        assertTrue(result.filter.items.isEmpty())
    }

    @Test fun allTimeAndBareCategoryQueriesAreSupported() {
        val food = read("food")
        assertEquals("ask", food.kind)
        assertEquals("list", food.what)
        assertEquals(setOf(Cat.KHANA), food.filter.cats)

        val allTime = read("food all time")
        assertEquals("ask", allTime.kind)
        assertEquals("sum", allTime.what)
        assertEquals(setOf(Cat.KHANA), allTime.filter.cats)
        assertEquals(null, allTime.filter.from)

        val expenses = read("all expense")
        assertEquals("ask", expenses.kind)
        assertEquals("list", expenses.what)
        assertEquals(setOf(Type.KHARCHA), expenses.filter.types)
    }

    @Test fun totalExpenseThisYearDoesNotTreatTotalAsAnItemName() {
        val result = read("total expense this year")
        assertEquals("ask", result.kind)
        assertEquals("sum", result.what)
        assertEquals(setOf(Type.KHARCHA), result.filter.types)
        assertTrue(result.filter.items.isEmpty())
        assertEquals(Query.day("2026-01-01"), result.filter.from)

        val month = read("expense this month")
        assertEquals(setOf(Type.KHARCHA), month.filter.types)
        assertTrue(month.filter.items.isEmpty())

        val food = read("food expense this month")
        assertEquals(setOf(Type.KHARCHA), food.filter.types)
        assertEquals(setOf(Cat.KHANA), food.filter.cats)
        assertTrue(food.filter.items.isEmpty())
    }

    @Test fun lastDayExpenseMeansYesterday() {
        val result = read("last day expense")
        assertEquals("ask", result.kind)
        assertEquals(setOf(Type.KHARCHA), result.filter.types)
        assertEquals(Query.day("2026-10-29"), result.filter.from)
    }

    @Test fun yearlyAndAllTimeReportsUseTheRequestedPeriod() {
        val yearly = read("yearly report")
        assertEquals("chat", yearly.kind)
        assertTrue(yearly.reply.contains("this year", ignoreCase = true))

        val allTime = read("all time report")
        assertEquals("chat", allTime.kind)
        assertTrue(allTime.reply.contains("all time", ignoreCase = true))
    }

    @Test fun allFollowUpConfirmsThePreviouslyClarifiedBulkDeleteTarget() {
        val history = listOf(
            ChatMessage("u1", true, "delet 28 oct entries"),
            ChatMessage("a1", false, "I found 3 matching entries for delete:\n28 Oct · Petrol · 50 SAR\nWhich one do you mean? Say 'all entries' for a bulk action.")
        )
        val result = read("all", history)
        assertEquals("delete", result.kind)
        assertEquals(Query.day("2026-10-28"), result.filter.from)
        assertEquals(86_399_999L, result.filter.to!! - result.filter.from!!)
    }

    @Test fun contactClarificationAcceptsTheDistinctiveSurname() {
        val contacts = listOf("Andleeb Shahzad Mazhar", "Andleeb Shahzad Iqbal")
        val history = listOf(
            ChatMessage("u1", true, "andleeb"),
            ChatMessage("a1", false, "Which Andleeb Shahzad Mazhar or Andleeb Shahzad Iqbal do you mean?")
        )
        val selected = NativeChatEngine.interpret("mazhar", entries, contacts = contacts, history = history, now = now)
        assertEquals("open", selected.kind)
        assertEquals(listOf("Andleeb Shahzad Mazhar"), selected.filter.persons)

        val yes = NativeChatEngine.interpret("yes", entries, contacts = contacts, history = history, now = now)
        assertEquals("chat", yes.kind)
        assertTrue(yes.reply.contains("choose one", ignoreCase = true))
    }

    @Test fun pronounDeleteUsesOnlyTheExactEntryIdsFromTheLastAssistantResult() {
        val history = listOf(
            ChatMessage("u1", true, "show petrol yesterday"),
            ChatMessage("a1", false, "Petrol · 60", entryIds = listOf("petrol-yesterday"))
        )
        val result = read("delete that", history)
        assertEquals("delete", result.kind)
        assertEquals(setOf("petrol-yesterday"), result.filter.ids)
    }

    @Test fun vagueDeleteDoesNotCreateAnUnboundedMutation() {
        val result = read("delete it")
        assertEquals("chat", result.kind)
        assertTrue(result.reply.contains("which", ignoreCase = true))
    }

    @Test fun recognizesSavedKhataDestinationForExplicitBulkMove() {
        val result = NativeChatEngine.interpret(
            "move all entries to Haji Ashiq khata", entries, contacts = listOf("Haji Ashiq"), now = now
        )
        assertEquals("to_khata", result.kind)
        assertEquals("Haji Ashiq", result.set["person"])
        assertTrue(result.filter.isEmpty)
    }

    @Test fun attributesAnExistingExpenseToTheNamedPayer() {
        val result = NativeChatEngine.interpret(
            "lunch was paid by Ali yesterday", entries, contacts = listOf("Ali"), now = now
        )
        assertEquals("paid_by", result.kind)
        assertEquals("Ali", result.payer)
        assertTrue(result.filter.from != null)
    }

    @Test fun routesWrongEntryRequestsToTheLocalRereader() {
        val result = read("laptop wali entry galat hai theek karo")
        assertEquals("reread", result.kind)
        assertTrue(!result.filter.isEmpty)
    }

    @Test fun recognizesMonthNamesAndLastNDays() {
        val month = read("show expenses in September 2026")
        assertEquals(Query.day("2026-09-01"), month.filter.from)
        val recent = read("show expenses in last 7 days")
        assertEquals(7L * 86_400_000L, now - recent.filter.from!!)
        assertEquals(now, recent.filter.to)
    }

    @Test fun amountRangeIsAppliedToAListQuery() {
        val result = read("show expenses between 10 and 50")
        assertEquals(10.0, result.filter.min!!, 0.001)
        assertEquals(50.0, result.filter.max!!, 0.001)
    }

    @Test fun asksClarificationInsteadOfGuessingForUnknownMoneyQuestion() {
        val result = read("what is my crypto portfolio worth?")
        assertEquals("chat", result.kind)
        assertTrue(result.reply.isNotBlank())
    }
}

class NativeChatEngineDayQuestionTest {
    private val now = Calendar.getInstance().apply {
        set(2026, Calendar.OCTOBER, 30, 12, 0, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private fun at(daysAgo: Int) = Calendar.getInstance().apply { timeInMillis = now; add(Calendar.DAY_OF_YEAR, -daysAgo) }.timeInMillis

    private val entries = listOf(
        Entry("tea", Type.KHARCHA, 5.0, Cat.KHANA, "Tea", "", "", at(1), at(1)),
        Entry("fuel", Type.KHARCHA, 60.0, Cat.TRANSPORT, "Petrol", "", "", at(2), at(2)),
        Entry("pay", Type.AAMDANI, 3000.0, Cat.TANKHWAH, "Salary", "", "", at(1), at(1))
    )

    private fun read(text: String, history: List<ChatMessage> = emptyList()) =
        NativeChatEngine.interpret(text, entries, now = now, history = history)

    private fun msg(text: String, user: Boolean) = ChatMessage(text.hashCode().toString(), user, text)

    @Test fun kalKitnaHuwaThaAsksForYesterdaysSpending() {
        val result = read("kal kitna huwa tha")
        assertEquals("ask", result.kind)
        assertEquals("sum", result.what)
        assertEquals(setOf(Type.KHARCHA), result.filter.types)
        assertEquals(Query.day("2026-10-29"), result.filter.from)
        assertTrue(result.filter.items.isEmpty())
    }

    @Test fun parsuMeansTheDayBeforeYesterday() {
        val result = read("parsu kitna huwa tha")
        assertEquals("ask", result.kind)
        assertEquals(Query.day("2026-10-28"), result.filter.from)
        assertEquals(setOf(Type.KHARCHA), result.filter.types)
    }

    @Test fun detailDoListsThePreviousSearch() {
        val history = listOf(msg("28 oct ko kitna kharcha huwa", true), msg("Spent 60 SAR (1 entry, 28 Oct)", false))
        val result = read("detail do", history)
        assertEquals("ask", result.kind)
        assertEquals("list", result.what)
        assertEquals(setOf(Type.KHARCHA), result.filter.types)
        assertEquals(Query.day("2026-10-28"), result.filter.from)
    }

    @Test fun detailWithoutAPreviousSearchIsNotAGuess() {
        assertEquals(null, read("detail do").filter.from)
    }
}

/** Mistakes the owner marked 👎 in the chat log (names changed). */
class NativeChatEngineNameTest {
    private val contacts = listOf("Hamid Akram", "Hiba", "Nargis Salim Mazhar", "Nargis Salim Rashid", "Kamil Salim Bahadar")

    private fun read(text: String, history: List<ChatMessage> = emptyList()) =
        NativeChatEngine.interpret(text, emptyList(), contacts = contacts, history = history)

    private fun msg(text: String, user: Boolean) = ChatMessage(text.hashCode().toString() + user, user, text)

    @Test fun kyaHalHaiIsSmallTalkNotANameQuestion() {
        val r = read("kya hal hai")
        assertEquals("chat", r.kind)
        assertTrue(r.reply, r.reply.contains("theek"))
    }

    @Test fun twoMatchingWordsPickThatPerson() {
        val r = read("salim rashid ka khata")
        assertEquals("open", r.kind)
        assertEquals(listOf("Nargis Salim Rashid"), r.filter.persons)
    }

    @Test fun aNewRequestAfterAWhichNameQuestionIsNotForcedIntoTheOldChoice() {
        val history = listOf(msg("kya hal hai", true), msg("Which Hamid Akram or Hiba do you mean?", false))
        val r = read("nargis ka khata", history)
        assertTrue(r.reply, !r.reply.startsWith("Please choose"))
        assertTrue(r.reply, r.reply.contains("Nargis Salim Mazhar") && r.reply.contains("Nargis Salim Rashid"))
    }

    @Test fun answeringWithTheDistinctiveWordsPicksThatName() {
        val history = listOf(
            msg("nargis ka khata", true),
            msg("Which Nargis Salim Mazhar or Nargis Salim Rashid do you mean?", false)
        )
        val r = read("Deepak Salim Rashid", history)
        assertEquals("open", r.kind)
        assertEquals(listOf("Nargis Salim Rashid"), r.filter.persons)
    }

    @Test fun urduScriptNameOpensTheKhata() {
        val r = read("نرگس سلیم راشد کا کھاتہ")
        assertEquals("open", r.kind)
        assertEquals(listOf("Nargis Salim Rashid"), r.filter.persons)
    }

    @Test fun urduShowTransactionsListsEntries() {
        val r = read("مجھے ٹرانزیکشن دکھاؤ")
        assertEquals("ask", r.kind)
        assertEquals("list", r.what)
        assertTrue(r.filter.items.toString(), r.filter.items.isEmpty())
    }
}

class NameChoicesTest {
    @Test fun readsTheNamesOfferedInAWhichQuestion() {
        assertEquals(
            listOf("Nargis Salim Mazhar", "Nargis Salim Rashid"),
            NativeChatEngine.nameChoices("Which Nargis Salim Mazhar or Nargis Salim Rashid do you mean?")
        )
        assertEquals(listOf("Hamid", "Hiba", "Kamil"), NativeChatEngine.nameChoices("Please choose one saved name: Hamid or Hiba or Kamil."))
    }

    @Test fun otherAnswersOfferNoChoices() {
        assertTrue(NativeChatEngine.nameChoices("Spent 79 SAR (3 entries, 28 Oct)").isEmpty())
        assertTrue(NativeChatEngine.nameChoices("Which one do you mean? Give the date or amount").isEmpty())
    }

    @Test fun tappingANameOpensThatKhata() {
        val contacts = listOf("Nargis Salim Mazhar", "Nargis Salim Rashid")
        val history = listOf(
            ChatMessage("u", true, "nargis ka khata"),
            ChatMessage("a", false, "Which Nargis Salim Mazhar or Nargis Salim Rashid do you mean?")
        )
        val r = NativeChatEngine.interpret("Nargis Salim Rashid ka khata", emptyList(), contacts = contacts, history = history)
        assertEquals("open", r.kind)
        assertEquals(listOf("Nargis Salim Rashid"), r.filter.persons)
    }
}

/** Only sentences the local engine cannot read are offered to Groq. */
class NativeChatEngineUnsureTest {
    private fun read(text: String) = NativeChatEngine.interpret(text, emptyList(), contacts = listOf("Hamid Akram"))

    @Test fun gibberishIsMarkedUnsure() = assertTrue(read("blorp zingle").unsure)

    @Test fun understoodMessagesAreNotUnsure() {
        assertTrue(!read("kya hal hai").unsure)
        assertTrue(!read("hello").unsure)
        assertTrue(!read("aaj kitna kharcha huwa").unsure)
    }
}

class NativeChatEngineKhataCommandsTest {
    private val contacts = listOf("Hamid Akram", "Nargis Salim Rashid", "Haji Kamran Saeed", "Kamran Aslam")
    private fun read(text: String) = NativeChatEngine.interpret(text, emptyList(), contacts)

    @Test fun renamesAKhataByItsSavedName() {
        val r = read("Hamid Akram ka naam Hamid Akram Khan kar do")
        assertEquals("rename_khata", r.kind)
        assertEquals("party", r.set["kind"])
        assertEquals("Hamid Akram", r.set["from"])
        assertEquals("Hamid Akram Khan", r.set["to"])
    }

    @Test fun mergesAWholeKhataIntoAnother() {
        val r = read("Hamid Akram ka khata Nargis Salim Rashid me merge kar do")
        assertEquals("move_khata", r.kind)
        assertEquals("Hamid Akram", r.set["from"])
        assertEquals("party", r.set["toKind"])
        assertEquals("Nargis Salim Rashid", r.set["to"])
    }

    @Test fun aBareNameOpensItsKhataOrAsksWhichOne() {
        val one = read("kamran saeed")
        assertEquals("open", one.kind)
        assertEquals(listOf("Haji Kamran Saeed"), one.filter.persons)
        assertTrue(read("kamran").reply.startsWith("Which "))
    }

    @Test fun plainSumsAreAnswered() {
        assertTrue(read("2 aur 2 kitna hota hai").reply.endsWith("= 4"))
        assertEquals("chat", read("5 x 12").kind)
    }

    @Test fun esMahinyIsThisMonth() {
        val r = read("es mahiny")
        assertEquals("ask", r.kind)
        assertTrue(r.filter.from != null)
    }
}

class NativeChatEngineMoveByAmountTest {
    private val entries = listOf(
        Entry("t1", Type.TRANSFER_IN, 2000.0, Cat.OTHER, "Transfer", "Hamid Akram", "From Hamid Akram", 1L, 1L),
        Entry("t2", Type.TRANSFER_IN, 5000.0, Cat.OTHER, "Transfer", "Nargis Salim Rashid", "From Nargis", 2L, 2L),
        Entry("t3", Type.TRANSFER_IN, 600.0, Cat.OTHER, "From Al Rajhi", "", "Own account", 3L, 3L),
        Entry("i1", Type.AAMDANI, 3500.0, Cat.TANKHWAH, "Noor Building Contracting LTD", "", "Payment", 4L, 4L),
        Entry("i2", Type.AAMDANI, 1500.0, Cat.TANKHWAH, "Sitara Paint Works Est", "", "Payment", 5L, 5L)
    )
    private val history = listOf(
        ChatMessage("u1", true, "8 jul ki entries"),
        ChatMessage("a1", false, "Transfer 2,000 · Transfer 5,000 · From Al Rajhi 600", listIds = listOf("t1", "t2", "t3"))
    )

    @Test fun shownRowsPickedByAmountGoToAnIncomeKhataNamedByItsFirstWords() {
        val r = NativeChatEngine.interpret("2000 and 5000 wali entry Noor building contracting me move kr do", entries, history = history)
        assertEquals("to_khata", r.kind)
        assertEquals(setOf("t1", "t2"), r.filter.ids)
        assertEquals("income", r.set["kind"])
        assertEquals("Noor Building Contracting LTD", r.set["name"])
    }
}

class NativeChatEngineMoveShownTest {
    private val contacts = listOf("Haji Ashiq", "Ali Abrar", "Ali Raza", "Hamid Akram")
    private val history = listOf(
        ChatMessage("u1", true, "chai ki entries"),
        ChatMessage("a1", false, "28 Oct · Chai · 5 SAR\n27 Oct · Chai · 6 SAR\n26 Oct · Chai · 7 SAR", listIds = listOf("e1", "e2", "e3"))
    )
    private fun read(text: String) = NativeChatEngine.interpret(text, emptyList(), contacts, history = history)

    @Test fun numberedEntryGoesToTheNamedKhata() {
        val r = read("3 no entry haji ashiq me bhej do")
        assertEquals("to_khata", r.kind)
        assertEquals(setOf("e3"), r.filter.ids)
        assertEquals("Haji Ashiq", r.set["person"])
    }

    @Test fun severalNumbersAndAnyVerb() {
        val r = read("2 aur 3 number wali entry Hamid Akram ko move kar do")
        assertEquals(setOf("e2", "e3"), r.filter.ids)
        assertEquals("Hamid Akram", r.set["person"])
        assertEquals(setOf("e1", "e2", "e3"), read("ye sab Hamid Akram ke khata me rakh do").filter.ids)
    }

    @Test fun oneEntryOfManyAsksWhichNumber() {
        val r = read("ye entry Hamid Akram ko send kar do")
        assertEquals("chat", r.kind)
        assertTrue(r.reply.contains("number"))
        assertTrue(read("5 no entry Hamid Akram ko bhej do").reply.contains("entry number 5"))
    }

    @Test fun partOfANameSharedByTwoOffersBoth() {
        val r = read("3 no entry ali me bhej do")
        assertEquals("chat", r.kind)
        assertEquals(listOf("Ali Abrar", "Ali Raza"), NativeChatEngine.nameChoices(r.reply))
        // The tap repeats the command with the full name.
        assertEquals("Ali Raza", read("3 no entry ali me bhej do Ali Raza").set["person"])
    }

    @Test fun sendingMoneyIsStillAnEntry() {
        assertTrue(read("Hamid Akram ko 500 bhej diye").kind != "to_khata")
    }

    @Test fun rowNumbers() {
        assertEquals(listOf(3), NativeChatEngine.rowNumbers("3rd entry"))
        assertEquals(listOf(2), NativeChatEngine.rowNumbers("entry 2 bhej do"))
        assertEquals(listOf(3), NativeChatEngine.rowNumbers("teesri entry"))
        assertEquals(emptyList<Int>(), NativeChatEngine.rowNumbers("50 wali entry"))
    }
}

class NativeChatEngineNameAnswerTest {
    private val contacts = listOf("Shahbaz", "Qaisar Shahbaz Ghulam")

    @Test fun exactSavedNameOpensThatKhata() {
        val r = NativeChatEngine.interpret("Shahbaz ka khata", emptyList(), contacts)
        assertEquals("open", r.kind)
        assertEquals(listOf("Shahbaz"), r.filter.persons)
    }

    @Test fun pickingTheNameFinishesTheEntryThatAsked() {
        val history = listOf(
            ChatMessage("u1", true, "170 riyal food mess after shabaz return 315 on the same day"),
            ChatMessage("a1", false, "Which Shahbaz or Qaisar Shahbaz Ghulam do you mean?")
        )
        // Shahbaz's 315 is saved, so the finished sentence puts the 170 right after it.
        val back = Entry("b1", Type.WAPIS_MILA, 315.0, Cat.UDHAAR, "Loans", "Shahbaz", "", 1_000_000L, 1_000_000L)
        val r = NativeChatEngine.interpret("Shahbaz ka khata", listOf(back), contacts, history = history)
        assertEquals("entry_after", r.kind)
        assertEquals(setOf("b1"), r.filter.ids)
    }
}

class NativeChatEngineAfterEntryTest {
    private val contacts = listOf("Shahbaz", "Qaisar Shahbaz Ghulam")
    private val back = Entry("b1", Type.WAPIS_MILA, 315.0, Cat.UDHAAR, "Loans", "Shahbaz", "Shahbaz returned", 1_000_000L, 1_000_000L)
    private val other = Entry("o1", Type.KHARCHA, 40.0, Cat.KHANA, "Lunch", "", "lunch", 2_000_000L, 2_000_000L)

    @Test fun newEntriesGoRightAfterTheNamedOne() {
        val r = NativeChatEngine.interpret("315 wali entry k bad 170 riyal ka food mess ar 15 riyal ka wifi bill dal do", listOf(back, other), contacts)
        assertEquals("entry_after", r.kind)
        assertEquals(setOf("b1"), r.filter.ids)
        assertTrue(r.text.startsWith("170"))
    }

    @Test fun englishAfterWithAPerson() {
        val r = NativeChatEngine.interpret("170 riyal food mess after shabaz return 315 on the same day", listOf(back, other), contacts)
        assertEquals("entry_after", r.kind)
        assertEquals(setOf("b1"), r.filter.ids)
        assertTrue(r.text.startsWith("170") && "after" !in r.text && "315" !in r.text)
    }
}

class NativeChatEngineMoveCategoryTest {
    private fun e(id: String, cat: Cat, item: String) = Entry(id, Type.KHARCHA, 10.0, cat, item, "", "POS purchase", 0L, 0L)
    private val entries = listOf(e("m1", Cat.OTHER, "Mahnaz Parveen Est"), e("m2", Cat.OTHER, "Mahnaz Parveen Est"), e("x", Cat.OTHER, "Smart Memory"), e("f", Cat.KHANA, "Mahnaz Parveen Est"))

    @Test fun otherEntriesOfANameGoToFood() {
        val r = NativeChatEngine.interpret("other me jo mahnaz parveen ki entry hain wo food me move karo", entries)
        assertEquals("change", r.kind)
        assertEquals(setOf("m1", "m2"), r.filter.ids)
        assertEquals("food", r.set["category"])
        val r2 = NativeChatEngine.interpret("expense me jo other wali entry me mahnaz parveen name se jitni b entry hain wo food me dal do", entries)
        assertEquals(setOf("m1", "m2"), r2.filter.ids)
    }
}
