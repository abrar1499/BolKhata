package com.bolkhata.app

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.text.TextPaint
import android.text.TextUtils
import androidx.core.content.FileProvider
import java.io.File

/** A business's details for its bills, PDFs and card. The name lives with the business list. */
data class BizInfo(val owner: String = "", val phone: String = "", val address: String = "", val card: String = "Classic",
    /** The business's own logo (a picture in the app's files); "" for the app icon. */
    val logo: String = "")

/**
 * Logos sit top left on the text's own edge (Corner: in the middle of its quarter circle; Wave: in the middle of the
 * wave; Classic: on the seam). The business card, drawn as a picture so the same image is shown, saved and shared (the mock draws it the same
 * way). Classic (the band with the name, a soft white part below, the logo on a white tile at the seam), Minimal, Luxe
 * (black and gold), Wave (white with a colour wave and the logo on it), Bold, Monogram, Navy (navy with a thin gold
 * frame), Corner (a colour quarter circle with the logo), Sunset (orange to purple), Stripe (a colour edge on warm
 * white) and Mint (soft green).
 */
object CardArt {
    val STYLES = listOf("Classic", "Minimal", "Luxe", "Wave", "Bold", "Monogram", "Navy", "Corner", "Sunset", "Stripe", "Mint")

    private const val W = 1050
    private const val H = 600
    private const val U = W / 350f   // one mock-up pixel

    /** The card for [name] and [info] in [style]; [colors] are the header theme's three colours, [logo] the app icon. */
    fun draw(style: String, name: String, info: BizInfo, colors: Triple<Int, Int, Int>, logo: Bitmap?): Bitmap {
        val bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val clip = Path().apply { addRoundRect(RectF(0f, 0f, W.toFloat(), H.toFloat()), 18 * U, 18 * U, Path.Direction.CW) }
        c.clipPath(clip)
        val lines = listOfNotNull(info.phone.takeIf { it.isNotBlank() }?.let { "📞  $it" }, info.address.takeIf { it.isNotBlank() }?.let { "📍  $it" })
        val nm = name.ifBlank { "Business name" }
        when (style) {
            "Minimal" -> {
                c.drawColor(0xFFFFFFFF.toInt())
                c.drawRoundRect(RectF(U / 2, U / 2, W - U / 2, H - U / 2), 18 * U, 18 * U, stroke(0xFFE7E5E4.toInt(), U))
                logoBox(c, logo, 18 * U, 18 * U, 44 * U)
                text(c, nm, 74 * U, 38 * U, 20 * U, 0xFF1C1917.toInt(), bold = true, maxW = W - 92 * U)
                if (info.owner.isNotBlank()) text(c, info.owner, 74 * U, 54 * U, 11 * U, 0xFF78716C.toInt(), maxW = W - 92 * U)
                c.drawRoundRect(RectF(18 * U, 80 * U, 64 * U, 83 * U), 2 * U, 2 * U, fill(colors.second))
                // A thin grey line on from the colour line to the right edge.
                c.drawRect(68 * U, 81.15f * U, W - 18 * U, 81.85f * U, fill(0xFFE7E5E4.toInt()))
                bottomLines(c, lines, 18 * U, 0xFF57534E.toInt())
            }
            "Luxe" -> {
                c.drawRect(0f, 0f, W.toFloat(), H.toFloat(), gradient(0xFF121212.toInt(), 0xFF2A2723.toInt(), 0xFF2A2723.toInt()))
                var y = 72 * U
                text(c, nm, W / 2f, y, 22 * U, 0xFFE9C46A.toInt(), bold = true, center = true, serif = true, maxW = W - 40 * U, spacing = 0.06f)
                if (info.owner.isNotBlank()) { y += 18 * U; text(c, info.owner.uppercase(), W / 2f, y, 9 * U, 0xFFE9D8A6.toInt(), center = true, spacing = 0.2f, maxW = W - 40 * U) }
                y += 14 * U
                c.drawRect(W / 2f - 30 * U, y, W / 2f + 30 * U, y + U * 0.8f, fill(0xFFE9C46A.toInt()))
                lines.forEach { y += 18 * U; text(c, it, W / 2f, y, 10 * U, 0xFFD6CBB0.toInt(), center = true, maxW = W - 40 * U) }
            }
            "Wave" -> {
                c.drawColor(0xFFFFFFFF.toInt())
                val wave = Path().apply { moveTo(W * 0.66f, 0f); cubicTo(W * 0.56f, H * 0.35f, W * 0.8f, H * 0.62f, W * 0.62f, H.toFloat()); lineTo(W.toFloat(), H.toFloat()); lineTo(W.toFloat(), 0f); close() }
                c.save(); c.clipPath(wave); band(c, colors); c.restore()
                c.drawPath(Path().apply { moveTo(W * 0.78f, 0f); cubicTo(W * 0.7f, H * 0.4f, W * 0.92f, H * 0.6f, W * 0.78f, H.toFloat()); lineTo(W.toFloat(), H.toFloat()); lineTo(W.toFloat(), 0f); close() }, fill(0x2EFFFFFF))
                logoBox(c, logo, W * 0.87f - 23 * U, H / 2f - 23 * U, 46 * U, shadow = true)
                text(c, nm, 18 * U, 48 * U, 19 * U, 0xFF1C1917.toInt(), bold = true, maxW = W * 0.56f)
                if (info.owner.isNotBlank()) text(c, info.owner, 18 * U, 64 * U, 11 * U, 0xFF78716C.toInt(), maxW = W * 0.56f)
                c.drawRoundRect(RectF(18 * U, 76 * U, 46 * U, 78.5f * U), 1.2f * U, 1.2f * U, fill(colors.second))
                bottomLines(c, lines, 18 * U, 0xFF57534E.toInt(), right = W * 0.6f)
            }
            "Bold" -> {
                c.drawColor(0xFFFFFFFF.toInt())
                var y = 70 * U - (lines.size * 7 * U)
                text(c, nm, W / 2f, y, 26 * U, 0xFF1C1917.toInt(), bold = true, center = true, maxW = W - 30 * U)
                if (info.owner.isNotBlank()) { y += 18 * U; text(c, info.owner, W / 2f, y, 11 * U, 0xFF78716C.toInt(), center = true, maxW = W - 30 * U) }
                y += 10 * U
                lines.forEach { y += 16 * U; text(c, it, W / 2f, y, 11 * U, 0xFF57534E.toInt(), center = true, maxW = W - 30 * U) }
                c.save(); c.clipRect(0f, H - 14 * U, W.toFloat(), H.toFloat()); band(c, colors); c.restore()
            }
            "Monogram" -> {
                c.drawRect(0f, 0f, W.toFloat(), H.toFloat(), gradient(0xFF0F172A.toInt(), 0xFF1E3A5F.toInt(), 0xFF1E3A5F.toInt()))
                text(c, nm.take(1).uppercase(), W - 100 * U, 120 * U, 150 * U, 0x14FFFFFF, bold = true, serif = true)
                text(c, nm, 18 * U, 100 * U - lines.size * 14 * U, 21 * U, 0xFFFFFFFF.toInt(), bold = true, maxW = W - 36 * U)
                if (info.owner.isNotBlank()) text(c, info.owner, 18 * U, 116 * U - lines.size * 14 * U, 11 * U, 0xD9FFFFFF.toInt(), maxW = W - 36 * U)
                bottomLines(c, lines, 18 * U, 0xF2FFFFFF.toInt(), divider = 0x59FFFFFF)
            }
            "Navy" -> {
                c.drawRect(0f, 0f, W.toFloat(), H.toFloat(), gradient(0xFF0B1530.toInt(), 0xFF16284A.toInt(), 0xFF1E3A5F.toInt()))
                c.drawRoundRect(RectF(8 * U, 8 * U, W - 8 * U, H - 8 * U), 12 * U, 12 * U, stroke(0xB3C9A54C.toInt(), 0.8f * U))
                logoBox(c, logo, 20 * U, 20 * U, 44 * U)
                text(c, nm, 20 * U, 92 * U, 20 * U, 0xFFFFFFFF.toInt(), bold = true, maxW = W - 40 * U)
                if (info.owner.isNotBlank()) text(c, info.owner.uppercase(), 20 * U, 108 * U, 9 * U, 0xFFE9C46A.toInt(), spacing = 0.15f, maxW = W - 40 * U)
                bottomLines(c, lines, 20 * U, 0xE6FFFFFF.toInt(), divider = 0x80C9A54C.toInt())
            }
            "Corner" -> {
                c.drawColor(0xFFFFFFFF.toInt())
                c.save(); c.clipPath(Path().apply { addCircle(W.toFloat(), 0f, 118 * U, Path.Direction.CW) }); band(c, colors); c.restore()
                logoBox(c, logo, W - 72 * U, 26 * U, 44 * U, shadow = true)
                text(c, nm, 18 * U, 92 * U, 20 * U, 0xFF1C1917.toInt(), bold = true, maxW = W * 0.62f)
                if (info.owner.isNotBlank()) text(c, info.owner, 18 * U, 108 * U, 11 * U, 0xFF78716C.toInt(), maxW = W * 0.62f)
                bottomLines(c, lines, 18 * U, 0xFF57534E.toInt(), divider = 0xFFE7E5E4.toInt())
            }
            "Sunset" -> {
                c.drawRect(0f, 0f, W.toFloat(), H.toFloat(), gradient(0xFFFF8A4C.toInt(), 0xFFF0446E.toInt(), 0xFF7B2FBF.toInt()))
                c.drawCircle(W * 0.1f, H * 1.05f, W * 0.42f, fill(0x24FFFFFF))
                logoBox(c, logo, 18 * U, 18 * U, 44 * U, shadow = true)
                text(c, nm, 18 * U, 96 * U, 21 * U, 0xFFFFFFFF.toInt(), bold = true, maxW = W - 36 * U)
                if (info.owner.isNotBlank()) text(c, info.owner, 18 * U, 112 * U, 11 * U, 0xE0FFFFFF.toInt(), maxW = W - 36 * U)
                bottomLines(c, lines, 18 * U, 0xFFFFFFFF.toInt(), divider = 0x66FFFFFF)
            }
            "Stripe" -> {
                c.drawColor(0xFFF7F5F2.toInt())
                c.save(); c.clipRect(0f, 0f, 10 * U, H.toFloat()); band(c, colors); c.restore()
                logoBox(c, logo, 30 * U, 18 * U, 44 * U)
                text(c, nm, 30 * U, 96 * U, 20 * U, 0xFF1C1917.toInt(), bold = true, maxW = W - 48 * U)
                if (info.owner.isNotBlank()) text(c, info.owner, 30 * U, 112 * U, 11 * U, colors.second, bold = true, maxW = W - 48 * U)
                bottomLines(c, lines, 30 * U, 0xFF57534E.toInt(), divider = 0xFFE7E5E4.toInt(), right = W - 18 * U)
            }
            "Mint" -> {
                c.drawRect(0f, 0f, W.toFloat(), H.toFloat(), gradient(0xFFEAF8F2.toInt(), 0xFFD6F2E7.toInt(), 0xFFC2EBDB.toInt()))
                c.drawCircle(W * 0.95f, H * 0.05f, W * 0.32f, fill(0x80FFFFFF.toInt()))
                logoBox(c, logo, 18 * U, 18 * U, 44 * U, shadow = true)
                text(c, nm, 18 * U, 96 * U, 20 * U, 0xFF0F3D2E.toInt(), bold = true, maxW = W - 36 * U)
                if (info.owner.isNotBlank()) text(c, info.owner, 18 * U, 112 * U, 11 * U, 0xFF3F7A66.toInt(), maxW = W - 36 * U)
                bottomLines(c, lines, 18 * U, 0xFF2F5D4E.toInt(), divider = 0xFF9FD9C3.toInt())
            }
            else -> {   // Classic: the band with the name on top, a soft white part below for phone and address, the logo on a white tile at the seam
                val seam = 126 * U
                c.save(); c.clipRect(0f, 0f, W.toFloat(), seam); band(c, colors); c.restore()
                c.drawRect(0f, seam, W.toFloat(), H.toFloat(), Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    shader = LinearGradient(0f, seam, 0f, H.toFloat(), 0xFFFFFFFF.toInt(), 0xFFFFF1EF.toInt(), Shader.TileMode.CLAMP) })
                logoBox(c, logo, W - 70 * U, seam - 26 * U, 52 * U, shadow = true)
                text(c, nm, 18 * U, 66 * U, 21 * U, 0xFFFFFFFF.toInt(), bold = true, maxW = W - 100 * U)
                if (info.owner.isNotBlank()) text(c, info.owner, 18 * U, 84 * U, 11 * U, 0xE0FFFFFF.toInt(), maxW = W - 100 * U)
                bottomLines(c, lines, 18 * U, 0xFF57534E.toInt(), right = W - 110 * U)
            }
        }
        return bmp
    }

    /** The header's glossy band: its colours left to right with a soft light circle top right. */
    private fun band(c: Canvas, colors: Triple<Int, Int, Int>) {
        c.drawRect(0f, 0f, W.toFloat(), H.toFloat(), gradient(colors.first, colors.second, colors.third))
        c.drawCircle(W * 0.92f, -H * 0.08f, W * 0.47f, fill(0x33FFFFFF))
    }

    private fun gradient(a: Int, b: Int, d: Int) =
        Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = LinearGradient(0f, 0f, W.toFloat(), H.toFloat(), intArrayOf(a, b, d), floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP) }

    private fun fill(color: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
    private fun stroke(color: Int, w: Float) = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color; style = Paint.Style.STROKE; strokeWidth = w }

    /** The logo itself, its corners rounded, with no tile or edge around it ([shadow]: a soft shadow under it). */
    private fun logoBox(c: Canvas, logo: Bitmap?, x: Float, y: Float, size: Float, shadow: Boolean = false) {
        val r = RectF(x, y, x + size, y + size)
        val round = Path().apply { addRoundRect(r, size * 0.26f, size * 0.26f, Path.Direction.CW) }
        if (shadow) c.drawPath(round, fill(0x01000000).apply { setShadowLayer(6 * U, 0f, 2 * U, 0x38000000) })
        logo?.let { c.save(); c.clipPath(round); c.drawBitmap(it, null, r, Paint(Paint.FILTER_BITMAP_FLAG)); c.restore() }
    }

    /** Phone and address at the bottom, with a thin line above them when [divider] is set. */
    private fun bottomLines(c: Canvas, lines: List<String>, x: Float, color: Int, divider: Int? = null, right: Float = W - x) {
        var y = H - 18 * U - (lines.size - 1) * 17 * U
        if (divider != null && lines.isNotEmpty()) c.drawRect(x, y - 20 * U, right, y - 20 * U + U * 0.7f, fill(divider))
        lines.forEach { text(c, it, x, y, 11 * U, color, maxW = right - x); y += 17 * U }
    }

    private fun text(
        c: Canvas, s: String, x: Float, y: Float, size: Float, color: Int, bold: Boolean = false, center: Boolean = false,
        serif: Boolean = false, maxW: Float = W.toFloat(), spacing: Float = 0f
    ) {
        val p = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color; textSize = size; letterSpacing = spacing
            typeface = Typeface.create(if (serif) Typeface.SERIF else Typeface.SANS_SERIF, if (bold) Typeface.BOLD else Typeface.NORMAL)
            textAlign = if (center) Paint.Align.CENTER else Paint.Align.LEFT
        }
        c.drawText(TextUtils.ellipsize(s, p, maxW, TextUtils.TruncateAt.END).toString(), x, y, p)
    }
}

/** Writes a file into the app's shared cache folder and opens the share sheet (WhatsApp, email, save…). */
fun shareFile(context: Context, name: String, mime: String, write: (File) -> Unit) {
    val dir = File(context.cacheDir, "shared").apply { mkdirs() }
    val file = File(dir, name.replace(Regex("""[^\w.\- ]"""), "").ifBlank { "file" })
    write(file)
    val uri = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)
    val send = Intent(Intent.ACTION_SEND).apply {
        type = mime
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        clipData = android.content.ClipData.newUri(context.contentResolver, name, uri)
    }
    context.startActivity(Intent.createChooser(send, "Share"))
}

/** Saves a picture to the phone's Pictures; false when it could not. */
fun saveImage(context: Context, bmp: Bitmap, name: String): Boolean = runCatching {
    if (android.os.Build.VERSION.SDK_INT >= 29) {
        val values = ContentValues().apply {
            put(android.provider.MediaStore.Images.Media.DISPLAY_NAME, "$name.png")
            put(android.provider.MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(android.provider.MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Smart Expense")
        }
        val uri = context.contentResolver.insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return false
        context.contentResolver.openOutputStream(uri)?.use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) } ?: return false
    } else {
        val dir = File(context.getExternalFilesDir(android.os.Environment.DIRECTORY_PICTURES), "").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
    true
}.getOrDefault(false)
