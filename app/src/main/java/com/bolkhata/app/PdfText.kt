package com.bolkhata.app

import android.content.Context
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.text.SimpleDateFormat
import java.util.Locale

/** Reads the text inside a PDF on the phone (no internet), and turns a statement into entries without AI. */
object PdfText {

    /** The PDF's text, line by line in reading order; null when it has none (a scan) or can't be opened. */
    fun extract(context: Context, bytes: ByteArray): String? = runCatching {
        PDFBoxResourceLoader.init(context.applicationContext)
        PDDocument.load(bytes).use { doc ->
            val stripper = PDFTextStripper()
            stripper.setSortByPosition(true)
            stripper.getText(doc)
        }
    }.getOrNull()?.takeIf { it.isNotBlank() }

    /** A statement read by [StatementParser] as entries ready to review and add. */
    fun toEntries(result: StatementParser.Result): List<Entry> {
        val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
        val now = System.currentTimeMillis()
        return result.rows.mapIndexed { i, r ->
            val at = runCatching { fmt.parse(r.date + " " + r.time.ifBlank { "12:00" })!!.time }.getOrDefault(now)
            Entry(
                id = "s" + now + "-" + i,
                type = if (r.kind == "income") Type.AAMDANI else Type.KHARCHA,
                amount = r.amount,
                category = runCatching { Cat.valueOf(r.category) }.getOrDefault(Cat.OTHER),
                item = r.item.take(40),
                person = "",
                note = r.note.take(60),
                date = at,
                createdAt = at
            )
        }
    }
}
