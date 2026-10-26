package com.bolkhata.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
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

    /** Downloads the APK. Returns the file, or null if it failed. */
    suspend fun download(context: Context, info: UpdateInfo, onProgress: (Int) -> Unit): File? =
        withContext(Dispatchers.IO) {
            try {
                val dir = File(context.cacheDir, "updates").apply { mkdirs() }
                val out = File(dir, "BolKhata.apk")
                var url = URL(info.apkUrl)
                var conn = open(url)
                var hops = 0
                while (conn.responseCode in 300..399 && hops < 5) {
                    val location = conn.getHeaderField("Location")
                    conn.disconnect()
                    url = URL(url, location)
                    conn = open(url)
                    hops++
                }
                if (conn.responseCode != 200) return@withContext null
                val total = conn.contentLengthLong
                conn.inputStream.use { input ->
                    out.outputStream().use { output ->
                        val buf = ByteArray(16 * 1024)
                        var read = 0
                        var done = 0L
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
                out
            } catch (e: Exception) {
                null
            }
        }

    private fun open(url: URL): HttpURLConnection {
        val c = url.openConnection() as HttpURLConnection
        c.instanceFollowRedirects = false
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

    fun install(context: Context, apk: File) {
        val uri = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", apk)
        val i = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(i)
    }
}
