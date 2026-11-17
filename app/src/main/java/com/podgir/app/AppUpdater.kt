package com.podgir.app

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

data class UpdateInfo(val version: String, val url: String, val size: Long)
data class UpdateDownload(val status: Int, val percent: Int?)

/** GitHub is the update source; signature is checked again before the Android installer is opened. */
class AppUpdater(private val context: Context) {
    private val prefs = context.getSharedPreferences("podgir_updates", Context.MODE_PRIVATE)
    private val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

    suspend fun latest(): UpdateInfo? = withContext(Dispatchers.IO) {
        val conn = URL("https://api.github.com/repos/Banafshex/Podgir/releases/latest").openConnection() as HttpURLConnection
        conn.connectTimeout = 12000; conn.readTimeout = 15000
        conn.setRequestProperty("Accept", "application/vnd.github+json"); conn.setRequestProperty("User-Agent", "Podgir-Android")
        val json = try { if (conn.responseCode !in 200..299) error("Update check failed"); JSONObject(conn.inputStream.bufferedReader().use { reader ->
            val data = StringBuilder(); val buffer = CharArray(8192)
            while (true) { val n = reader.read(buffer); if (n < 0) break; require(data.length + n <= 1024 * 1024) { "Response too large" }; data.append(buffer, 0, n) }
            data.toString()
        }) } finally { conn.disconnect() }
        val tag = json.getString("tag_name"); val current = context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "0.0.0"
        if (!isNewerVersion(tag, current)) return@withContext null
        val canonical = tag.removePrefix("v")
        val expected = "Podgir-$canonical.apk"
        val assets = json.getJSONArray("assets")
        for (i in 0 until assets.length()) {
            val a = assets.getJSONObject(i)
            if (a.optString("name") == expected) {
                val url = a.getString("browser_download_url"); val size = a.optLong("size")
                require(isTrustedUpdateUrl(url, tag, expected) && size in 1..250_000_000) { "Invalid release asset" }
                return@withContext UpdateInfo(canonical, url, size)
            }
        }
        error("No APK in release")
    }
    fun start(info: UpdateInfo) {
        cancel()
        val name = "podgir-update-${info.version}.apk"
        val file = file(name); if (file.exists()) require(file.delete()) { "Cannot remove old update" }
        val request = DownloadManager.Request(Uri.parse(info.url)).setTitle("Podgir ${info.version}")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, name)
        val id = manager.enqueue(request)
        prefs.edit().putLong("id", id).putString("version", info.version).putString("file", name).putLong("size", info.size).apply()
    }
    fun downloadVersion(): String? = prefs.getString("version", null)
    fun status(): UpdateDownload {
        val id = prefs.getLong("id", -1); if (id == -1L) return UpdateDownload(0, null)
        return manager.query(DownloadManager.Query().setFilterById(id)).use { cursor ->
            if (!cursor.moveToFirst()) UpdateDownload(0, null) else {
                val status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                val total = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                val done = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                UpdateDownload(status, if (total > 0) ((done.toDouble() / total) * 100).toInt().coerceIn(0, 100) else null)
            }
        }
    }
    fun cancel() {
        val id = prefs.getLong("id", -1); if (id != -1L) manager.remove(id)
        prefs.getString("file", null)?.let { runCatching { file(it).delete() } }
        prefs.edit().clear().apply()
    }
    private fun file(name: String): File {
        require(name.matches(Regex("podgir-update-[0-9]+\\.[0-9]+\\.[0-9]+\\.apk")))
        return File(requireNotNull(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)), name)
    }
    fun verifyDownloaded(): File {
        check(status().status == DownloadManager.STATUS_SUCCESSFUL) { "Download not complete" }
        val name = requireNotNull(prefs.getString("file", null)); val apk = file(name)
        check(apk.isFile && apk.length() == prefs.getLong("size", -1)) { "APK size mismatch" }
        val pm = context.packageManager
        @Suppress("DEPRECATION") val flags = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
        @Suppress("DEPRECATION") val archive = pm.getPackageArchiveInfo(apk.absolutePath, flags) ?: error("Invalid APK")
        @Suppress("DEPRECATION") val installed = pm.getPackageInfo(context.packageName, flags)
        check(archive.packageName == context.packageName) { "Unexpected package" }
        check(archive.versionName == prefs.getString("version", null)) { "Unexpected version" }
        val archiveCode = if (Build.VERSION.SDK_INT >= 28) archive.longVersionCode else @Suppress("DEPRECATION") archive.versionCode.toLong()
        val installedCode = if (Build.VERSION.SDK_INT >= 28) installed.longVersionCode else @Suppress("DEPRECATION") installed.versionCode.toLong()
        check(archiveCode > installedCode) { "Not a newer version" }
        @Suppress("DEPRECATION")
        fun fingerprints(info: android.content.pm.PackageInfo): Set<String> {
            val signatures = if (Build.VERSION.SDK_INT >= 28) info.signingInfo?.apkContentsSigners else info.signatures
            return signatures?.map { s -> MessageDigest.getInstance("SHA-256").digest(s.toByteArray()).joinToString("") { "%02x".format(it) } }?.toSet().orEmpty()
        }
        val current = fingerprints(installed); val incoming = fingerprints(archive)
        check(current.isNotEmpty() && incoming == current) { "Signing certificate mismatch" }
        return apk
    }
    fun canInstall() = Build.VERSION.SDK_INT < 26 || context.packageManager.canRequestPackageInstalls()
    fun openPermissionSettings() {
        if (Build.VERSION.SDK_INT >= 26) context.startActivity(Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
    fun install(apk: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", apk)
        context.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

fun parseVersion(value: String): List<Int>? {
    val m = Regex("^v?(\\d+)\\.(\\d+)\\.(\\d+)$").matchEntire(value) ?: return null
    return m.groupValues.drop(1).map { it.toIntOrNull() ?: return null }
}
fun isNewerVersion(candidate: String, current: String): Boolean {
    val a = parseVersion(candidate) ?: return false; val b = parseVersion(current) ?: return false
    for (i in 0..2) if (a[i] != b[i]) return a[i] > b[i]
    return false
}
fun isTrustedUpdateUrl(url: String, tag: String, name: String): Boolean = try {
    val u = java.net.URI(url)
    u.scheme == "https" && u.host == "github.com" && u.port == -1 && u.userInfo == null && u.query == null && u.fragment == null && u.path == "/Banafshex/Podgir/releases/download/$tag/$name"
} catch (_: Exception) { false }
