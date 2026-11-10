package com.bolkhata.app

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import kotlin.coroutines.resume

/**
 * Sync with the person's own Google Drive: one zipped file in the app's hidden Drive folder, which only this app can open.
 * Signing in joins this phone's data with what Drive holds (nothing lost, no entry twice); after that every change is
 * saved there a few seconds later, and another phone signed in with the same Gmail picks it up when the app opens.
 */
object Drive {
    private const val FILE = "smart-expense.json.gz"
    private val scopes = listOf(Scope("https://www.googleapis.com/auth/drive.appdata"), Scope("https://www.googleapis.com/auth/userinfo.email"))
    private val http by lazy { OkHttpClient() }
    private val work = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var listener: SharedPreferences.OnSharedPreferenceChangeListener? = null
    private var pending: Job? = null
    /** True while Drive data is being written into the phone, so that write is not sent back. */
    @Volatile private var muted = false

    /** "✓ Synced · 10:42pm", "Saving…" or why it could not save; shown under the Gmail in the side menu. */
    var status by mutableStateOf("")
        private set

    /** While signing in and fetching the data: the Gmail once known ("" before that); null otherwise. */
    var busy by mutableStateOf<String?>(null)
        private set

    /** What the app asks Google for: its own hidden Drive folder and the Gmail address. */
    fun request(): AuthorizationRequest = AuthorizationRequest.builder().setRequestedScopes(scopes).build()

    /** A Drive token without asking, or null when the person has to pick an account first. */
    private suspend fun token(context: Context): String? = suspendCancellableCoroutine { c ->
        Identity.getAuthorizationClient(context).authorize(request())
            .addOnSuccessListener { r -> c.resume(if (r.hasResolution()) null else r.accessToken) }
            .addOnFailureListener { c.resume(null) }
    }

    /** After the person picked a Gmail: joins this phone with Drive and saves the result to both. True when Drive had data. */
    suspend fun link(context: Context, store: Store, token: String): Boolean = withContext(Dispatchers.IO) {
        busy = ""
        val mail = email(token).ifBlank { "Google account" }
        busy = mail
        try {
            val remote = download(token)?.takeIf { store.backupEntries(it) >= 0 }
            if (remote != null) quietly { store.importJson(DriveData.merge(store.exportJson(sync = true), remote), sync = true) }
            save(store, token)
            // Signed in only once Drive really holds the data.
            store.driveEmail = mail
            remote != null
        } catch (e: Exception) {
            // Not signed in after all (e.g. that Gmail's storage is full): the next try offers every Gmail again.
            forget(context, mail)
            throw e
        } finally {
            busy = null
        }
    }

    /** Google forgets the app's access to [mail]'s Drive. */
    private fun forget(context: Context, mail: String) {
        runCatching {
            Identity.getAuthorizationClient(context).revokeAccess(
                com.google.android.gms.auth.api.identity.RevokeAccessRequest.builder()
                    .setAccount(android.accounts.Account(mail, "com.google")).setScopes(scopes).build()
            )
        }
    }

    /** When the app opens: takes what another phone saved (joined with this phone's unsaved changes). True when data changed. */
    suspend fun pull(context: Context, store: Store): Boolean = withContext(Dispatchers.IO) {
        if (store.driveEmail.isBlank()) return@withContext false
        val token = token(context) ?: return@withContext false
        val remote = runCatching { download(token) }.getOrNull()
        val o = remote?.let { runCatching { JSONObject(it) }.getOrNull() }
        val newer = o != null && o.optString("device") != store.deviceId && o.optLong("at") > store.driveAt
        if (newer) {
            quietly { store.importJson(if (store.driveDirty) DriveData.merge(store.exportJson(sync = true), remote!!) else remote!!, sync = true) }
            store.driveAt = o!!.optLong("at")
        }
        if (store.driveDirty) runCatching { save(store, token) }
        newer
    }

    /** Saves this phone's data to Drive now. */
    suspend fun push(context: Context, store: Store): Boolean = withContext(Dispatchers.IO) {
        if (store.driveEmail.isBlank()) return@withContext false
        status = "Saving…"
        val ok = runCatching { save(store, token(context) ?: throw IOException("Sign in again")) }.isSuccess
        if (!ok) status = "Not saved yet · check the internet"
        ok
    }

    /** Starts saving to Drive a few seconds after each change, while signed in. */
    fun watch(context: Context, store: Store) {
        if (listener != null) return
        val app = context.applicationContext
        listener = store.watch {
            if (muted || store.driveEmail.isBlank()) return@watch
            store.driveDirty = true
            pending?.cancel()
            pending = work.launch { delay(4000); push(app, store) }
        }
    }

    /** Stops syncing; the data stays on the phone. Google forgets the app's access, so the next sign-in offers every Gmail again. */
    fun signOut(context: Context, store: Store) {
        pending?.cancel()
        forget(context, store.driveEmail)
        store.driveEmail = ""
        store.driveAt = 0
        status = ""
    }

    private fun save(store: Store, token: String) {
        val at = System.currentTimeMillis()
        upload(token, JSONObject(store.exportJson(sync = true)).put("at", at).put("device", store.deviceId).toString())
        store.driveAt = at
        store.driveDirty = false
        status = "✓ Synced · " + SimpleDateFormat("h:mma", Locale.US).format(Date(at)).lowercase()
    }

    /** Writes Drive data into the phone on the main thread, where the change notices arrive at once and are ignored. */
    private suspend fun quietly(block: () -> Unit) = withContext(Dispatchers.Main) {
        muted = true
        try { block() } finally { muted = false }
    }

    // ---------- Google Drive calls (on a background thread) ----------

    private fun call(token: String, b: Request.Builder): ByteArray =
        http.newCall(b.header("Authorization", "Bearer $token").build()).execute().use { r ->
            // Google's own reason goes along (e.g. "API not enabled"), so a failed sign-in can be understood.
            if (!r.isSuccessful) throw IOException("${r.code} " + runCatching {
                JSONObject(r.body?.string().orEmpty()).getJSONObject("error").optString("message").take(90)
            }.getOrDefault(""))
            r.body?.bytes() ?: ByteArray(0)
        }

    private fun email(token: String): String =
        runCatching { JSONObject(String(call(token, Request.Builder().url("https://www.googleapis.com/oauth2/v3/userinfo")))).optString("email") }.getOrDefault("")

    private fun fileId(token: String): String? {
        val url = "https://www.googleapis.com/drive/v3/files?spaces=appDataFolder&fields=files(id)&q=name%3D%27$FILE%27"
        return JSONObject(String(call(token, Request.Builder().url(url)))).optJSONArray("files")?.optJSONObject(0)?.optString("id")?.ifBlank { null }
    }

    /** The saved backup text, or null when Drive has none yet. */
    private fun download(token: String): String? {
        val id = fileId(token) ?: return null
        val bytes = call(token, Request.Builder().url("https://www.googleapis.com/drive/v3/files/$id?alt=media"))
        return GZIPInputStream(bytes.inputStream()).use { it.readBytes().toString(Charsets.UTF_8) }
    }

    private fun upload(token: String, text: String) {
        val zipped = ByteArrayOutputStream().also { o -> GZIPOutputStream(o).use { it.write(text.toByteArray()) } }.toByteArray()
        val body = zipped.toRequestBody("application/gzip".toMediaType())
        val id = fileId(token)
        if (id != null) call(token, Request.Builder().url("https://www.googleapis.com/upload/drive/v3/files/$id?uploadType=media").patch(body))
        else {
            val meta = JSONObject().put("name", FILE).put("parents", JSONArray().put("appDataFolder")).toString()
            val parts = MultipartBody.Builder().setType("multipart/related".toMediaType())
                .addPart(meta.toRequestBody("application/json; charset=UTF-8".toMediaType()))
                .addPart(body).build()
            call(token, Request.Builder().url("https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart").post(parts))
        }
    }
}

/** Joining backups, kept apart from the Google sign-in code so it runs anywhere. */
object DriveData {
    /**
     * Joins two backups into one: lists of entries (entries, trash, of every business) by id, this phone's copy winning
     * when both have it; maps (businesses, trash times) key by key; any other setting this phone's first. Nothing is dropped.
     */
    fun merge(local: String, remote: String): String {
        val l = JSONObject(local)
        val ld = l.getJSONObject("data")
        val rd = JSONObject(remote).getJSONObject("data")
        for (k in rd.keys()) {
            val ri = rd.getJSONObject(k)
            val li = ld.optJSONObject(k)
            if (li == null) ld.put(k, ri)
            else if (li.optString("t") == "s" && ri.optString("t") == "s") li.put("v", join(li.getString("v"), ri.getString("v")))
        }
        l.put("entries", runCatching { JSONArray(ld.getJSONObject("entries").getString("v")).length() }.getOrDefault(0))
        return l.toString()
    }

    private fun join(a: String, b: String): String {
        val la = runCatching { JSONArray(a) }.getOrNull()
        val lb = runCatching { JSONArray(b) }.getOrNull()
        if (la != null && lb != null) {
            fun ids(x: JSONArray) = (0 until x.length()).map { x.optJSONObject(it)?.optString("id").orEmpty() }
            val ia = ids(la)
            val ib = ids(lb)
            if ((ia + ib).any { it.isBlank() }) return a
            val have = ia.toHashSet()
            ib.forEachIndexed { i, id -> if (have.add(id)) la.put(lb.get(i)) }
            return la.toString()
        }
        val oa = runCatching { JSONObject(a) }.getOrNull()
        val ob = runCatching { JSONObject(b) }.getOrNull()
        if (oa != null && ob != null) {
            for (k in ob.keys()) if (!oa.has(k)) oa.put(k, ob.get(k))
            return oa.toString()
        }
        return a
    }
}
