package com.bolkhata.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

data class UpdateInfo(val version: Int, val apkUrl: String)

/** Checks the GitHub releases of this app and installs a newer APK with one tap. */
object Updater {
    private const val LATEST = "https://api.github.com/repos/abrar1499/BolKhata/releases/latest"

    private const val LATEST_PAGE = "https://github.com/abrar1499/BolKhata/releases/latest"
    private const val DOWNLOAD = "https://github.com/abrar1499/BolKhata/releases/download/"

    /** True when the last check could not reach GitHub, so "no update" is not known for sure. */
    @Volatile var lastCheckFailed = false
        private set

    suspend fun check(): UpdateInfo? = withContext(Dispatchers.IO) {
        lastCheckFailed = false
        if (BuildConfig.PLAY) return@withContext null // Play installs its own updates
        val latest = runCatching { fromApi() }.getOrNull() ?: runCatching { fromPage() }.getOrNull()
        if (latest == null) {
            lastCheckFailed = true
            return@withContext null
        }
        if (latest.version <= BuildConfig.VERSION_CODE) null else latest
    }

    /** GitHub's API: has a limit of 60 checks an hour per network, so it can fail on shared Wi-Fi. */
    private fun fromApi(): UpdateInfo? {
        val conn = URL(LATEST).openConnection() as HttpURLConnection
        conn.connectTimeout = 10_000
        conn.readTimeout = 10_000
        conn.useCaches = false
        conn.setRequestProperty("Accept", "application/vnd.github+json")
        conn.setRequestProperty("Cache-Control", "no-cache")
        try {
            if (conn.responseCode != 200) return null
            val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
            val version = json.getString("tag_name").trimStart('v', 'V').toIntOrNull() ?: return null
            val assets = json.getJSONArray("assets")
            for (i in 0 until assets.length()) {
                val a = assets.getJSONObject(i)
                if (a.getString("name").endsWith(".apk")) return UpdateInfo(version, a.getString("browser_download_url"))
            }
            return null
        } finally {
            conn.disconnect()
        }
    }

    /** Backup with no limit: the releases page redirects to the newest version's tag, e.g. .../tag/v67. */
    private fun fromPage(): UpdateInfo? {
        val conn = URL(LATEST_PAGE).openConnection() as HttpURLConnection
        conn.instanceFollowRedirects = false
        conn.connectTimeout = 10_000
        conn.readTimeout = 10_000
        conn.useCaches = false
        try {
            val location = conn.getHeaderField("Location") ?: return null
            val tag = location.substringAfterLast("/tag/", "").takeIf { it.isNotBlank() } ?: return null
            val version = tag.trimStart('v', 'V').toIntOrNull() ?: return null
            return UpdateInfo(version, DOWNLOAD + tag + "/BolKhata.apk")
        } finally {
            conn.disconnect()
        }
    }

    /** Keeps a download going when the person leaves the screen (it isn't tied to what's on screen). */
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Downloads the APK. A dropped connection doesn't fail it: it waits and goes on from where it stopped (the part
     * already saved is kept), for about 10 minutes. Returns the file, or null if it still couldn't finish.
     */
    suspend fun download(context: Context, info: UpdateInfo, onProgress: (Int) -> Unit): File? =
        withContext(Dispatchers.IO) {
            val dir = File(context.cacheDir, "updates").apply { mkdirs() }
            val out = File(dir, "BolKhata.apk")
            val part = File(dir, "BolKhata.apk.part")
            // A part left from another version is thrown away.
            val tag = File(dir, "part.version")
            if (runCatching { tag.readText() }.getOrNull() != info.version.toString()) { part.delete(); tag.writeText(info.version.toString()) }
            repeat(40) { attempt ->
                try {
                    var url = URL(info.apkUrl)
                    var conn = open(url, part.length())
                    var hops = 0
                    while (conn.responseCode in 300..399 && hops < 5) {
                        val location = conn.getHeaderField("Location")
                        conn.disconnect()
                        url = URL(url, location)
                        conn = open(url, part.length())
                        hops++
                    }
                    val code = conn.responseCode
                    if (code == 416 && part.length() > 0) return@withContext finish(part, out)
                    if (code != 200 && code != 206) error("HTTP $code")
                    // 200: the server sent the whole file again, so start over; 206: carry on after the saved part.
                    val resume = code == 206
                    val total = (if (resume) part.length() else 0L) + conn.contentLengthLong
                    var done = if (resume) part.length() else 0L
                    conn.inputStream.use { input ->
                        java.io.FileOutputStream(part, resume).use { output ->
                            val buf = ByteArray(16 * 1024)
                            var read: Int
                            while (input.read(buf).also { read = it } > 0) {
                                output.write(buf, 0, read)
                                done += read
                                if (total > 0) {
                                    val pct = (done * 100 / total).toInt()
                                    withContext(Dispatchers.Main) { onProgress(pct) }
                                }
                            }
                        }
                    }
                    if (total <= 0 || done >= total) return@withContext finish(part, out)
                } catch (e: Exception) {
                    // No network or a broken connection: wait a little and try again from the saved part.
                }
                delay(minOf(30_000L, 3_000L * (attempt + 1)))
            }
            null
        }

    private fun finish(part: File, out: File): File {
        out.delete()
        part.renameTo(out)
        return out
    }

    private fun open(url: URL, from: Long = 0): HttpURLConnection {
        val c = url.openConnection() as HttpURLConnection
        c.instanceFollowRedirects = false
        if (from > 0) c.setRequestProperty("Range", "bytes=$from-")
        c.connectTimeout = 15_000
        c.readTimeout = 30_000
        return c
    }

    /** True when Android still needs the user to allow installs from this app. */
    fun needsPermission(context: Context): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !context.packageManager.canRequestPackageInstalls()

    fun openPermissionSettings(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val i = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + context.packageName))
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(i)
        }
    }

    /**
     * Installs the downloaded APK through a package-installer session. On Android 12+ an update of an app this app
     * installed itself goes in without the "Install?" question; otherwise Android shows its confirmation once.
     * Falls back to the plain installer screen if the session can't be used.
     */
    fun install(context: Context, apk: File) {
        runCatching {
            val installer = context.packageManager.packageInstaller
            val params = android.content.pm.PackageInstaller.SessionParams(android.content.pm.PackageInstaller.SessionParams.MODE_FULL_INSTALL)
            if (Build.VERSION.SDK_INT >= 31) params.setRequireUserAction(android.content.pm.PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
            val id = installer.createSession(params)
            installer.openSession(id).use { session ->
                session.openWrite("app", 0, apk.length()).use { out ->
                    apk.inputStream().use { it.copyTo(out) }
                    session.fsync(out)
                }
                val flags = android.app.PendingIntent.FLAG_UPDATE_CURRENT or
                    (if (Build.VERSION.SDK_INT >= 31) android.app.PendingIntent.FLAG_MUTABLE else 0)
                val done = android.app.PendingIntent.getBroadcast(context, id, Intent(context, InstallResult::class.java), flags)
                session.commit(done.intentSender)
            }
        }.onFailure { installWithScreen(context, apk) }
    }

    private fun installWithScreen(context: Context, apk: File) {
        val uri = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", apk)
        val i = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(i)
    }

    /** The install session's answer: when Android wants the person to confirm, its screen is shown. */
    class InstallResult : android.content.BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val status = intent.getIntExtra(android.content.pm.PackageInstaller.EXTRA_STATUS, -1)
            if (status == android.content.pm.PackageInstaller.STATUS_PENDING_USER_ACTION) {
                @Suppress("DEPRECATION")
                val confirm = (if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                    else intent.getParcelableExtra(Intent.EXTRA_INTENT)) ?: return
                context.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        }
    }
}
