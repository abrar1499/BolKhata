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
data class BizInfo(val owner: String = "", val phone: String = "", val address: String = "", val card: String = "Classic")

/**
 * The business card, drawn as a picture so the same image is shown, saved and shared. Six looks:
 * Classic (the header's glossy colours), Minimal (white, a thin colour line), Luxe (black and gold), Split (a colour
 * panel with the logo beside the details), Bold (a big centred name over a colour bar) and Monogram (navy with a
 * large first letter).
 */
object CardArt {
    val STYLES = listOf("Classic", "Minimal", "Luxe", "Split", "Bold", "Monogram")

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
                logoBox(c, logo, 18 * U, 18 * U, 44 * U, 0xFFF5F5F4.toInt())
                text(c, nm, 74 * U, 38 * U, 20 * U, 0xFF1C1917.toInt(), bold = true, maxW = W - 92 * U)
                if (info.owner.isNotBlank()) text(c, info.owner, 74 * U, 54 * U, 11 * U, 0xFF78716C.toInt(), maxW = W - 92 * U)
                c.drawRoundRect(RectF(18 * U, 80 * U, 64 * U, 83 * U), 2 * U, 2 * U, fill(colors.second))
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
            "Split" -> {
                c.drawColor(0xFFFFFFFF.toInt())
                val sw = W * 0.38f
                c.save(); c.clipRect(0f, 0f, sw, H.toFloat()); band(c, colors); c.restore()
                logoBox(c, logo, sw / 2 - 22 * U, H / 2f - 22 * U, 44 * U, 0xFFFFFFFF.toInt())
                val x = sw + 16 * U
                text(c, nm, x, 34 * U, 17 * U, 0xFF1C1917.toInt(), bold = true, maxW = W - x - 14 * U)
                if (info.owner.isNotBlank()) text(c, info.owner, x, 50 * U, 11 * U, 0xFF78716C.toInt(), maxW = W - x - 14 * U)
                bottomLines(c, lines, x, 0xFF57534E.toInt(), divider = 0xFFE7E5E4.toInt(), right = W - 14 * U)
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
            else -> {   // Classic
                band(c, colors)
                logoBox(c, logo, 18 * U, 18 * U, 44 * U, 0xFFFFFFFF.toInt())
                text(c, nm, 18 * U, 90 * U, 20 * U, 0xFFFFFFFF.toInt(), bold = true, maxW = W - 36 * U)
                if (info.owner.isNotBlank()) text(c, info.owner, 18 * U, 106 * U, 11 * U, 0xD9FFFFFF.toInt(), maxW = W - 36 * U)
                bottomLines(c, lines, 18 * U, 0xF2FFFFFF.toInt(), divider = 0x59FFFFFF)
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

    /** The app logo in a rounded square of [bg]. */
    private fun logoBox(c: Canvas, logo: Bitmap?, x: Float, y: Float, size: Float, bg: Int) {
        val r = RectF(x, y, x + size, y + size)
        c.drawRoundRect(r, size * 0.32f, size * 0.32f, fill(bg))
        logo?.let { val p = size * 0.12f; c.drawBitmap(it, null, RectF(x + p, y + p, x + size - p, y + size - p), Paint(Paint.FILTER_BITMAP_FLAG)) }
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
