package com.bolkhata.app

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

/**
 * Listens with the phone's own recogniser inside the app, with no pop-up: the words show while they are spoken, a busy
 * recogniser is tried again, and a tap on the mic ends it. One sentence at a time; everything runs on the main thread.
 */
class Listener(context: Context) {
    private val app = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null
    private var language = ""
    private var wanted = false
    private var busyTries = 0
    /** Counts the sessions, so a late timer of an old one does nothing. */
    private var run = 0
    private var soFar = ""

    var onPartial: (String) -> Unit = {}
    var onFinal: (String) -> Unit = {}
    /** A short line for the person: nothing heard, no internet, no microphone. */
    var onError: (String) -> Unit = {}
    var onListening: (Boolean) -> Unit = {}
    var onLevel: (Float) -> Unit = {}

    private fun best(b: Bundle?) = b?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()

    private val events = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {}
        override fun onBeginningOfSpeech() {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {}
        override fun onEvent(eventType: Int, params: Bundle?) {}
        override fun onRmsChanged(rmsdB: Float) { if (wanted) onLevel(((rmsdB + 2f) / 12f).coerceIn(0f, 1f)) }
        override fun onPartialResults(partialResults: Bundle?) {
            if (!wanted) return
            best(partialResults).takeIf { it.isNotBlank() }?.let { soFar = it; onPartial(it) }
        }
        override fun onResults(results: Bundle?) {
            if (!wanted) return
            done(best(results).ifBlank { soFar })
        }
        override fun onError(error: Int) {
            if (!wanted) return
            when (error) {
                // Still closing the last session: start afresh a moment later, a few times.
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> if (busyTries < 3) { busyTries++; reset(); again(400) } else fail("Voice is busy. Try again.")
                SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> done(soFar)
                SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> fail("No internet for voice. Try again when online.")
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> fail("Allow the microphone for Smart Expense to talk.")
                // 12 / 13: no recogniser for this language on the phone; its default language is used instead.
                12, 13 -> if (language.isNotBlank()) { language = ""; reset(); again(120) } else fail("Speech recognition isn't available. Check Google voice typing in phone settings.")
                else -> fail("Couldn't catch that. Try again.")
            }
        }
    }

    /** Starts listening in [language] (a tag like "ur-PK"; blank for the phone's own). */
    fun start(language: String) {
        this.language = language
        wanted = true; busyTries = 0; soFar = ""; run++
        onListening(true)
        begin()
    }

    private fun begin() {
        val r = recognizer ?: SpeechRecognizer.createSpeechRecognizer(app).also { it.setRecognitionListener(events); recognizer = it }
        r.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            if (language.isNotBlank()) {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, language)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, language)
            }
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, app.packageName)
        })
    }

    private fun again(afterMs: Long) {
        val mine = run
        main.postDelayed({ if (wanted && run == mine) begin() }, afterMs)
    }

    /** The mic was tapped again: ends the sentence; what was heard comes as the result, or after a short wait as it stands. */
    fun stop() {
        if (!wanted) return
        recognizer?.stopListening()
        val mine = run
        main.postDelayed({ if (wanted && run == mine) { recognizer?.cancel(); done(soFar) } }, 2500)
    }

    /** Stops without a result. */
    fun cancel() {
        if (!wanted) return
        recognizer?.cancel()
        finish()
    }

    private fun finish() {
        wanted = false; run++
        onListening(false)
    }

    private fun done(said: String) {
        finish()
        if (said.isBlank()) onError("Couldn't catch that. Tap the mic again and speak a little slower.") else onFinal(said)
    }

    private fun fail(message: String) {
        reset()
        finish()
        onError(message)
    }

    private fun reset() {
        recognizer?.destroy()
        recognizer = null
    }

    /** Frees the recogniser when the screen goes away. */
    fun release() {
        wanted = false; run++
        reset()
    }
}
