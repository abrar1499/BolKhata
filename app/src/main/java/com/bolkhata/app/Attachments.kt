package com.bolkhata.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import java.io.File
import java.io.FileOutputStream

/** Saves a small picture of an attached photo or PDF (its first page) so it can be shown in the chat. */
object Attachments {

    private fun dir(context: Context) = File(context.filesDir, "chat").apply { mkdirs() }

    /** Returns the saved picture's path, or "" if no picture could be made. */
    fun savePreview(context: Context, bytes: ByteArray, mime: String): String {
        val bitmap = if (mime == "application/pdf") pdfPage(context, bytes) else photo(bytes)
        bitmap ?: return ""
        val out = File(dir(context), "att_" + System.currentTimeMillis() + ".jpg")
        FileOutputStream(out).use { bitmap.compress(Bitmap.CompressFormat.JPEG, 85, it) }
        bitmap.recycle()
        return out.absolutePath
    }

    private fun photo(bytes: ByteArray): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= 1080) sample *= 2
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
    }

    private fun pdfPage(context: Context, bytes: ByteArray): Bitmap? {
        val tmp = File(context.cacheDir, "preview.pdf")
        tmp.writeBytes(bytes)
        try {
            ParcelFileDescriptor.open(tmp, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
                PdfRenderer(fd).use { renderer ->
                    if (renderer.pageCount == 0) return null
                    renderer.openPage(0).use { page ->
                        val width = 900
                        val height = (width.toFloat() * page.height / page.width).toInt().coerceIn(1, 2400)
                        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                        bmp.eraseColor(Color.WHITE)
                        page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        return bmp
                    }
                }
            }
        } finally {
            tmp.delete()
        }
    }

    /**
     * Each page of a PDF as a JPEG, sharp enough to read small statement text. Empty if the PDF can't be opened
     * (for example a password-protected one), in which case the whole PDF is sent as it is.
     */
    fun pdfPages(context: Context, bytes: ByteArray, maxPages: Int = 40): List<ByteArray> {
        val tmp = File(context.cacheDir, "pages.pdf")
        tmp.writeBytes(bytes)
        return try {
            ParcelFileDescriptor.open(tmp, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
                PdfRenderer(fd).use { renderer ->
                    (0 until minOf(renderer.pageCount, maxPages)).map { i ->
                        renderer.openPage(i).use { page ->
                            val width = 1500
                            val height = (width.toFloat() * page.height / page.width).toInt().coerceIn(1, 4000)
                            val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                            bmp.eraseColor(Color.WHITE)
                            page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            val out = java.io.ByteArrayOutputStream()
                            bmp.compress(Bitmap.CompressFormat.JPEG, 82, out)
                            bmp.recycle()
                            out.toByteArray()
                        }
                    }
                }
            }
        } catch (e: Exception) {
            emptyList()
        } finally {
            tmp.delete()
        }
    }

    /** Removes saved pictures no chat message uses any more. */
    fun cleanUp(context: Context, keep: Set<String>) {
        dir(context).listFiles()?.forEach { if (it.absolutePath !in keep) it.delete() }
    }
}
