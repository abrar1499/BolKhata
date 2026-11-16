package com.bolkhata.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.google.android.gms.tasks.Task
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.Locale
import kotlin.coroutines.resume

/**
 * Languages other than the app's own (English, Urdu, Hindi and Roman Urdu): Google's ML Kit turns a message into
 * English on the phone itself (nothing is sent anywhere), once the language's pack (about 30 MB) has come down.
 */
object Lingo {
    /** What the app reads by itself; these never go through a translation. */
    private val own = setOf("en", "ur", "hi")

    /** A language: its code, its English name and its name in its own writing. */
    data class Lang(val code: String, val name: String, val local: String)

    /** The language to translate from for a Language setting ("nl", "ar-SA"); "" for Auto and the app's own languages. */
    fun code(setting: String): String = setting.substringBefore('-').lowercase(Locale.US).let { if (it in own) "" else it }

    fun name(code: String): String = Locale(code).getDisplayLanguage(Locale.ENGLISH).ifBlank { code }

    /** Every language that can be turned into English, by its English name. */
    fun all(): List<Lang> = TranslateLanguage.getAllLanguages().filter { it !in own }
        .map { Lang(it, name(it), Locale(it).getDisplayLanguage(Locale(it))) }.sortedBy { it.name }

    /** The language whose pack is coming down now. */
    var loading by mutableStateOf("")
        private set

    /** The language whose pack has just arrived: the menu says Ready for a moment, then calls [seen]. */
    var arrived by mutableStateOf("")
        private set

    fun seen() { arrived = "" }

    private val made = HashMap<String, Translator>()

    private fun translator(code: String): Translator? {
        val from = TranslateLanguage.fromLanguageTag(code) ?: return null
        return made.getOrPut(from) {
            Translation.getClient(TranslatorOptions.Builder().setSourceLanguage(from).setTargetLanguage(TranslateLanguage.ENGLISH).build())
        }
    }

    /** Brings [code]'s pack down when it is not on the phone yet; true when it is ready. */
    suspend fun fetch(code: String): Boolean {
        val t = translator(code) ?: return false
        loading = code
        val ok = t.downloadModelIfNeeded(DownloadConditions.Builder().build()).done()
        loading = ""
        if (ok) arrived = code
        return ok
    }

    /** [text] in English; null when the pack could not be had or the translation failed. */
    suspend fun toEnglish(code: String, text: String): String? {
        val t = translator(code) ?: return null
        if (!t.downloadModelIfNeeded(DownloadConditions.Builder().build()).done()) return null
        return t.translate(text).result()
    }

    private suspend fun <T> Task<T>.done(): Boolean = suspendCancellableCoroutine { c -> addOnCompleteListener { c.resume(it.isSuccessful) } }

    private suspend fun <T> Task<T>.result(): T? =
        suspendCancellableCoroutine { c -> addOnCompleteListener { c.resume(if (it.isSuccessful) it.result else null) } }
}
