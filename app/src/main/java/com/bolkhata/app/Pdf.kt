package com.bolkhata.app

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.TextPaint
import android.text.TextUtils
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** One table of a PDF: a heading over it, the column names, the rows, an optional total row; the first [left] columns are text. */
class PdfTable(val heading: String, val cols: List<String>, val rows: List<List<String>>, val foot: List<String>? = null, val left: Int = 1)

/**
 * What a PDF shows, in the app's plain A4 look: the business on top (logo, name, phone and address), the title and
 * period on the right, a row of summary boxes, the tables, then [note] (a balance, or "Thank you" on a bill) on the right.
 * [billTo] and [totals] make it a bill.
 */
class PdfDoc(
    val title: String, val sub: String, val boxes: List<Pair<String, String>> = emptyList(), val tables: List<PdfTable> = emptyList(),
    val note: String = "", val billTo: String = "", val totals: List<Pair<String, String>> = emptyList()
)

object Pdf {
    private const val PW = 595
    private const val PH = 842
    private const val M = 36f
    private val ink = 0xFF1C1917.toInt()
    private val grey = 0xFF78716C.toInt()
    private val light = 0xFFE7E5E4.toInt()
    private val red = 0xFFDC2626.toInt()

    /** Draws [doc] into [file]; pages follow one another when a table runs long (its column names repeat on top). */
    fun write(file: File, doc: PdfDoc, business: String, info: BizInfo, logo: Bitmap?) {
        val pdf = PdfDocument()
        var pageNo = 0
        lateinit var page: PdfDocument.Page
        lateinit var c: Canvas
        var y = 0f
        val made = "Made with Smart Expense · " + SimpleDateFormat("d MMM yyyy", Locale.US).format(Date())
        fun finish() {
            text(c, made, PW / 2f, PH - 20f, 7.5f, 0xFFA8A29E.toInt(), center = true)
            pdf.finishPage(page)
        }
        fun newPage() {
            if (pageNo > 0) finish()
            pageNo++
            page = pdf.startPage(PdfDocument.PageInfo.Builder(PW, PH, pageNo).create())
            c = page.canvas
            y = M
            // The business on the left, the title on the right, a strong line under them.
            logo?.let { c.drawBitmap(it, null, RectF(M, y, M + 30f, y + 30f), Paint(Paint.FILTER_BITMAP_FLAG)) }
            text(c, business, M + 38f, y + 12f, 13f, ink, bold = true, maxW = 260f)
            text(c, listOf(info.phone, info.address).filter { it.isNotBlank() }.joinToString(" · "), M + 38f, y + 25f, 8f, grey, maxW = 260f)
            text(c, doc.title, PW - M, y + 12f, 15f, ink, bold = true, right = true, maxW = 220f)
            text(c, doc.sub + if (pageNo > 1) " · page $pageNo" else "", PW - M, y + 25f, 8f, grey, right = true, maxW = 240f)
            y += 38f
            c.drawRect(M, y, PW - M, y + 1.5f, fill(ink))
            y += 16f
        }
        newPage()
        if (doc.billTo.isNotBlank()) {
            text(c, "BILL TO", M, y + 6f, 7.5f, grey, bold = true, spacing = 0.08f)
            text(c, doc.billTo, M, y + 22f, 13f, ink, bold = true, maxW = 300f)
            y += 34f
        }
        if (doc.boxes.isNotEmpty()) {
            val gap = 8f
            val w = (PW - 2 * M - gap * (doc.boxes.size - 1)) / doc.boxes.size
            doc.boxes.forEachIndexed { i, (v, l) ->
                val x = M + i * (w + gap)
                c.drawRoundRect(RectF(x, y, x + w, y + 40f), 6f, 6f, stroke(light))
                text(c, v, x + 9f, y + 18f, 12f, ink, bold = true, maxW = w - 16f)
                text(c, l, x + 9f, y + 31f, 8f, grey, maxW = w - 16f)
            }
            y += 54f
        }
        doc.tables.forEach { t ->
            val nums = t.cols.size - t.left
            val numW = if (nums == 0) 0f else minOf(92f, (PW - 2 * M) * 0.55f / nums)
            val textW = (PW - 2 * M - nums * numW) / maxOf(1, t.left)
            fun colX(i: Int) = if (i < t.left) M + i * textW else M + t.left * textW + (i - t.left) * numW
            fun colW(i: Int) = if (i < t.left) textW else numW
            fun row(cells: List<String>, size: Float, color: Int, bold: Boolean, line: Int?, height: Float) {
                cells.forEachIndexed { i, s ->
                    val right = i >= t.left
                    text(c, s, if (right) colX(i) + colW(i) - 3f else colX(i) + 3f, y + height - 7f, size, color, bold = bold, right = right, maxW = colW(i) - 6f)
                }
                line?.let { c.drawRect(M, y + height, PW - M, y + height + 0.6f, fill(it)) }
                y += height
            }
            fun head() = row(t.cols.map { it.uppercase() }, 7.5f, grey, true, 0xFFD6D3D1.toInt(), 18f)
            if (y > PH - 120f) newPage()
            if (t.heading.isNotBlank()) { text(c, t.heading.uppercase(), M, y + 10f, 8.5f, grey, bold = true, spacing = 0.06f); y += 16f }
            head()
            t.rows.forEach { r ->
                if (y > PH - 70f) { newPage(); head() }
                row(r, 9.5f, ink, false, 0xFFEFEDEA.toInt(), 20f)
            }
            t.foot?.let { f ->
                c.drawRect(M, y, PW - M, y + 1.2f, fill(ink))
                row(f, 9.5f, ink, true, null, 20f)
            }
            y += 14f
        }
        if (doc.totals.isNotEmpty()) {
            val x = PW - M - 220f
            doc.totals.forEachIndexed { i, (l, v) ->
                val last = i == doc.totals.lastIndex
                if (last) c.drawRect(x, y, PW - M, y + 1.2f, fill(ink))
                text(c, l, x + 3f, y + 15f, if (last) 11f else 9.5f, if (last) ink else grey, bold = last)
                text(c, v, PW - M - 3f, y + 15f, if (last) 11f else 9.5f, if (last) red else ink, bold = true, right = true)
                y += 22f
            }
            y += 6f
        }
        if (doc.note.isNotBlank()) text(c, doc.note, PW - M, y + 12f, 10.5f, ink, bold = true, right = true, maxW = PW - 2 * M)
        finish()
        file.outputStream().use { pdf.writeTo(it) }
        pdf.close()
    }

    private fun fill(color: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
    private fun stroke(color: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color; style = Paint.Style.STROKE; strokeWidth = 0.8f }

    private fun text(
        c: Canvas, s: String, x: Float, y: Float, size: Float, color: Int, bold: Boolean = false,
        right: Boolean = false, center: Boolean = false, maxW: Float = PW.toFloat(), spacing: Float = 0f
    ) {
        if (s.isBlank()) return
        val p = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color; textSize = size; letterSpacing = spacing
            typeface = Typeface.create(Typeface.SANS_SERIF, if (bold) Typeface.BOLD else Typeface.NORMAL)
            textAlign = when { right -> Paint.Align.RIGHT; center -> Paint.Align.CENTER; else -> Paint.Align.LEFT }
        }
        c.drawText(TextUtils.ellipsize(s, p, maxW, TextUtils.TruncateAt.END).toString(), x, y, p)
    }
}

/** Makes a PDF of what a screen shows and opens the share sheet; given by the app to every screen. */
val LocalPdf = androidx.compose.runtime.staticCompositionLocalOf<(PdfDoc) -> Unit> { {} }
