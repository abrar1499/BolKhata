package com.bolkhata.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import java.io.File
import java.io.FileOutputStream
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.suspendCancellableCoroutine

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
                    require(renderer.pageCount <= maxPages) {
                        "This PDF has ${renderer.pageCount} pages. Send at most $maxPages pages at a time so no transactions are skipped."
                    }
                    (0 until renderer.pageCount).map { i ->
                        renderer.openPage(i).use { page ->
                            // Sharp enough for small statement print.
                            val width = 2200
                            val height = (width.toFloat() * page.height / page.width).toInt().coerceIn(1, 5000)
                            val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                            bmp.eraseColor(Color.WHITE)
                            page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            val out = java.io.ByteArrayOutputStream()
                            bmp.compress(Bitmap.CompressFormat.JPEG, 92, out)
                            bmp.recycle()
                            out.toByteArray()
                        }
                    }
                }
            }
        } catch (e: IllegalArgumentException) {
            throw e
        } catch (e: Exception) {
            emptyList()
        } finally {
            tmp.delete()
        }
    }


    /**
     * Extracts text entirely on-device. The Latin OCR model is bundled in the APK, so it is available offline.
     * PDF pages are rasterized and processed one at a time to keep memory use bounded.
     */
    suspend fun extractText(context: Context, bytes: ByteArray, mime: String, maxPages: Int = 20): String =
        withContext(Dispatchers.IO) {
            if (mime == "application/pdf") {
                val pages = pdfPages(context, bytes, maxPages)
                val text = StringBuilder()
                pages.forEachIndexed { index, page ->
                    val bitmap = BitmapFactory.decodeByteArray(page, 0, page.size) ?: return@forEachIndexed
                    val pageText = recognize(bitmap)
                    if (pageText.isNotBlank()) {
                        if (text.isNotEmpty()) text.append("\\n")
                        text.append(pageText)
                    }
                }
                text.toString()
            } else {
                val bitmap = photo(bytes) ?: return@withContext ""
                recognize(bitmap)
            }
        }

    private suspend fun recognize(bitmap: Bitmap): String = suspendCancellableCoroutine { continuation ->
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        recognizer.process(InputImage.fromBitmap(bitmap, 0))
            .addOnSuccessListener { result ->
                if (continuation.isActive) continuation.resumeWith(Result.success(result.text))
            }
            .addOnFailureListener { error ->
                if (continuation.isActive) continuation.resumeWith(Result.failure(error))
            }
            .addOnCompleteListener {
                runCatching { recognizer.close() }
                if (!bitmap.isRecycled) bitmap.recycle()
            }
        continuation.invokeOnCancellation {
            runCatching { recognizer.close() }
            if (!bitmap.isRecycled) bitmap.recycle()
        }
    }

    /** Removes saved pictures no chat message uses any more. */
    fun cleanUp(context: Context, keep: Set<String>) {
        dir(context).listFiles()?.forEach { if (it.absolutePath !in keep) it.delete() }
    }
}
