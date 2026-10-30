package com.bolkhata.app

import android.annotation.SuppressLint
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.os.Handler
import android.os.Looper
import android.util.Base64
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlin.math.sqrt

/**
 * A live voice call with the owner's own ElevenLabs agent (speech in, speech out). The agent hands app commands
 * back through its `app_command` client tool; [onCommand] runs them in the chat and returns the app's answer, which
 * the agent then says. All callbacks run on the main thread.
 */
class ElevenAgent(
    private val key: String,
    private val agentId: String,
    private val onCommand: (command: String, answer: (String) -> Unit) -> Unit,
    private val onHeard: (String) -> Unit,
    private val onReply: (String) -> Unit,
    private val onLevel: (Float) -> Unit,
    private val onSpeaking: (Boolean) -> Unit,
    private val onEnd: (error: String?) -> Unit,
) {
    private val main = Handler(Looper.getMainLooper())
    private val client = OkHttpClient.Builder().pingInterval(20, TimeUnit.SECONDS).build()
    private var socket: WebSocket? = null
    private var recorder: AudioRecord? = null
    private var track: AudioTrack? = null
    // Plays the agent's audio pieces one after another, in the order they came.
    private val speaker = java.util.concurrent.Executors.newSingleThreadExecutor()
    @Volatile private var running = false
    // Until this time the agent's audio is still playing (the mic level is then the agent, not the owner).
    @Volatile private var speakingUntil = 0L

    /** Connects (with a signed link when a key is saved, so the agent can stay private) and starts the mic. */
    fun start() {
        running = true
        thread(name = "agent-call") {
            val url = runCatching { signedUrl() }.getOrElse {
                main.post { end("ElevenLabs agent: " + (it.message ?: "can't connect")) }
                return@thread
            }
            socket = client.newWebSocket(Request.Builder().url(url).build(), listener)
        }
    }

    fun stop() {
        if (!running) return
        running = false
        runCatching { socket?.close(1000, null) }
        runCatching { recorder?.stop() }; runCatching { recorder?.release() }
        runCatching { track?.stop() }; runCatching { track?.release() }
        recorder = null; track = null; socket = null
        speaker.shutdownNow()
        client.dispatcher.executorService.shutdown()
    }

    private fun end(error: String?) {
        if (!running) return
        stop()
        onEnd(error)
    }

    private fun signedUrl(): String {
        val id = URLEncoder.encode(agentId.trim(), "UTF-8")
        val open = "wss://api.elevenlabs.io/v1/convai/conversation?agent_id=$id"
        if (key.isBlank()) return open
        val conn = URL("https://api.elevenlabs.io/v1/convai/conversation/get-signed-url?agent_id=$id")
            .openConnection() as HttpURLConnection
        conn.connectTimeout = 10_000
        conn.readTimeout = 15_000
        conn.setRequestProperty("xi-api-key", key)
        try {
            val code = conn.responseCode
            // A key made only for speech may not reach agents; a public agent still works without the signed link.
            if (code == 401 || code == 403) return open
            if (code !in 200..299) error(if (code == 404) "Agent ID not found" else "error $code")
            return JSONObject(conn.inputStream.bufferedReader().use { it.readText() }).getString("signed_url")
        } finally {
            conn.disconnect()
        }
    }

    private val listener = object : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            webSocket.send(JSONObject().put("type", "conversation_initiation_client_data").toString())
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            val m = runCatching { JSONObject(text) }.getOrNull() ?: return
            when (m.optString("type")) {
                "conversation_initiation_metadata" -> {
                    val meta = m.optJSONObject("conversation_initiation_metadata_event")
                    startAudio(rate(meta?.optString("user_input_audio_format")), rate(meta?.optString("agent_output_audio_format")))
                }
                "audio" -> m.optJSONObject("audio_event")?.optString("audio_base_64")?.let { play(Base64.decode(it, Base64.DEFAULT)) }
                "interruption" -> runCatching { track?.pause(); track?.flush(); track?.play(); speakingUntil = 0L }
                "user_transcript" -> m.optJSONObject("user_transcription_event")?.optString("user_transcript")
                    ?.takeIf { it.isNotBlank() }?.let { main.post { onHeard(it) } }
                "agent_response" -> m.optJSONObject("agent_response_event")?.optString("agent_response")
                    ?.takeIf { it.isNotBlank() }?.let { main.post { onReply(it) } }
                "ping" -> m.optJSONObject("ping_event")?.optLong("event_id")?.let {
                    webSocket.send(JSONObject().put("type", "pong").put("event_id", it).toString())
                }
                "client_tool_call" -> m.optJSONObject("client_tool_call")?.let { call ->
                    val id = call.optString("tool_call_id")
                    val command = call.optJSONObject("parameters")?.optString("command").orEmpty()
                    main.post {
                        if (command.isBlank()) answer(webSocket, id, "No command given.", true)
                        else onCommand(command) { answer(webSocket, id, it, false) }
                    }
                }
            }
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            main.post { end(if (code == 1000) null else "ElevenLabs agent: ${reason.ifBlank { "closed ($code)" }}") }
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            main.post { end("ElevenLabs agent: " + (t.message ?: "connection lost")) }
        }
    }

    private fun answer(webSocket: WebSocket, id: String, result: String, isError: Boolean) {
        webSocket.send(
            JSONObject().put("type", "client_tool_result").put("tool_call_id", id)
                .put("result", result).put("is_error", isError).toString()
        )
    }

    /** "pcm_16000" → 16000; ulaw and other formats aren't asked for, so 16000 is the default. */
    private fun rate(format: String?): Int = format?.substringAfter("pcm_", "")?.toIntOrNull() ?: 16000

    @SuppressLint("MissingPermission") // the call only starts after the microphone permission was given
    private fun startAudio(inRate: Int, outRate: Int) {
        if (!running || recorder != null) return
        val outSize = AudioTrack.getMinBufferSize(outRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
        track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build()
            )
            .setAudioFormat(
                AudioFormat.Builder().setSampleRate(outRate).setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build()
            )
            .setBufferSizeInBytes(outSize * 4)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build().also { it.play() }
        val inSize = maxOf(AudioRecord.getMinBufferSize(inRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT), inRate / 5)
        // The voice-call source cancels the agent's own voice coming back through the mic.
        val rec = AudioRecord(MediaRecorder.AudioSource.VOICE_COMMUNICATION, inRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, inSize)
        if (rec.state != AudioRecord.STATE_INITIALIZED) { main.post { end("Microphone is busy.") }; return }
        recorder = rec
        rec.startRecording()
        thread(name = "agent-mic") {
            val chunk = ByteArray(inRate / 10 * 2) // 100 ms
            while (running) {
                val n = rec.read(chunk, 0, chunk.size)
                if (n <= 0) continue
                socket?.send(JSONObject().put("user_audio_chunk", Base64.encodeToString(chunk, 0, n, Base64.NO_WRAP)).toString())
                val level = (rms(chunk, n) / 3000f).coerceIn(0f, 1f)
                val speaking = System.currentTimeMillis() < speakingUntil
                main.post { onSpeaking(speaking); if (!speaking) onLevel(level) }
            }
        }
    }

    private fun play(pcm: ByteArray) {
        val t = track ?: return
        val now = System.currentTimeMillis()
        speakingUntil = maxOf(speakingUntil, now) + pcm.size * 1000L / (2 * t.sampleRate)
        runCatching { speaker.execute { runCatching { t.write(pcm, 0, pcm.size) } } }
    }

    private fun rms(b: ByteArray, n: Int): Float {
        var sum = 0.0
        var i = 0
        while (i + 1 < n) {
            val s = ((b[i + 1].toInt() shl 8) or (b[i].toInt() and 0xff)).toShort().toDouble()
            sum += s * s
            i += 2
        }
        return sqrt(sum / maxOf(1, n / 2)).toFloat()
    }
}
