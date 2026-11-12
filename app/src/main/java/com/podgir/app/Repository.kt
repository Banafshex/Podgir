package com.podgir.app

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.util.Xml
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import java.net.HttpURLConnection
import java.net.URL

 data class Podcast(val title: String, val author: String, val image: String, val feed: String) {
    fun json() = JSONObject().put("title", title).put("author", author).put("image", image).put("feed", feed)
    companion object { fun from(j: JSONObject) = Podcast(j.optString("title"), j.optString("author"), j.optString("image"), j.optString("feed")) }
}
data class Episode(val title: String, val description: String, val audio: String, val date: String, val image: String, val podcast: String) {
    fun json() = JSONObject().put("title", title).put("description", description).put("audio", audio).put("date", date).put("image", image).put("podcast", podcast)
    companion object { fun from(j: JSONObject) = Episode(j.optString("title"), j.optString("description"), j.optString("audio"), j.optString("date"), j.optString("image"), j.optString("podcast")) }
}

class Repository(private val context: Context) {
    private val prefs = context.getSharedPreferences("podgir", Context.MODE_PRIVATE)
    fun boolean(key: String, default: Boolean) = prefs.getBoolean(key, default)
    fun setBoolean(key: String, value: Boolean) { prefs.edit().putBoolean(key, value).apply() }
    fun lastEpisode(): Episode? = runCatching { Episode.from(JSONObject(prefs.getString("lastEpisode", null) ?: return null)) }.getOrNull()
    fun saveLastEpisode(e: Episode) { prefs.edit().putString("lastEpisode", e.json().toString()).apply() }
    fun position(url: String) = prefs.getLong("pos:$url", 0)
    fun savePosition(url: String, pos: Long) { prefs.edit().putLong("pos:$url", pos).apply() }
    private fun array(key: String): JSONArray = try { JSONArray(prefs.getString(key, "[]")) } catch (_: Exception) { JSONArray() }
    fun subscriptions(): List<Podcast> { val a = array("subscriptions"); return (0 until a.length()).mapNotNull { runCatching { Podcast.from(a.getJSONObject(it)) }.getOrNull() } }
    fun toggle(p: Podcast) { val list = subscriptions().toMutableList(); if (list.any { it.feed == p.feed }) list.removeAll { it.feed == p.feed } else list.add(p); prefs.edit().putString("subscriptions", JSONArray(list.map { it.json() }).toString()).apply() }
    fun downloads(): List<Episode> { val a = array("downloads"); return (0 until a.length()).mapNotNull { runCatching { Episode.from(a.getJSONObject(it)) }.getOrNull() } }
    fun download(e: Episode) {
        if (downloadId(e) != -1L) {
            if (downloadStatus(e) == DownloadManager.STATUS_FAILED || downloadStatus(e) == 0) removeDownload(e) else return
        }
        val request = DownloadManager.Request(Uri.parse(e.audio)).setTitle(e.title)
            .setDescription(e.podcast).setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalFilesDir(context, Environment.DIRECTORY_PODCASTS, "${System.currentTimeMillis()}-${e.audio.hashCode()}.audio")
        val id = (context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).enqueue(request)
        prefs.edit().putLong("download:${e.audio}", id).putString("downloads", JSONArray((downloads() + e).map { it.json() }).toString()).apply()
    }
    fun downloadId(e: Episode) = prefs.getLong("download:${e.audio}", -1)
    fun downloadStatus(e: Episode): Int {
        val id = downloadId(e); if (id == -1L) return 0
        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        return dm.query(DownloadManager.Query().setFilterById(id)).use { c -> if (c.moveToFirst()) c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)) else 0 }
    }
    fun playbackUri(e: Episode): String {
        if (downloadStatus(e) == DownloadManager.STATUS_SUCCESSFUL) {
            val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            return dm.getUriForDownloadedFile(downloadId(e))?.toString() ?: e.audio
        }
        return e.audio
    }
    fun removeDownload(e: Episode) {
        val id = downloadId(e); if (id != -1L) (context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).remove(id)
        prefs.edit().remove("download:${e.audio}").putString("downloads", JSONArray(downloads().filterNot { it.audio == e.audio }.map { it.json() }).toString()).apply()
    }
    private fun read(url: String): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 15000; conn.readTimeout = 20000
        conn.setRequestProperty("User-Agent", "Podgir/1.0 (Android podcast player)")
        try { if (conn.responseCode !in 200..299) error("HTTP ${conn.responseCode}"); return conn.inputStream.bufferedReader().use { it.readText() } } finally { conn.disconnect() }
    }
    suspend fun search(query: String): List<Podcast> = withContext(Dispatchers.IO) {
        val url = "https://itunes.apple.com/search?media=podcast&limit=40&term=" + java.net.URLEncoder.encode(query, "UTF-8")
        val a = JSONObject(read(url)).getJSONArray("results")
        (0 until a.length()).mapNotNull { i -> val j = a.getJSONObject(i); val feed = j.optString("feedUrl"); if (!feed.startsWith("https://")) null else Podcast(j.optString("collectionName"), j.optString("artistName"), j.optString("artworkUrl600", j.optString("artworkUrl100")), feed) }.distinctBy { it.feed }
    }
    suspend fun episodes(p: Podcast): List<Episode> = withContext(Dispatchers.IO) {
        val parser = Xml.newPullParser(); parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true); parser.setInput(read(p.feed).reader())
        val episodes = mutableListOf<Episode>(); var inItem = false; var title = ""; var desc = ""; var audio = ""; var date = ""; var image = p.image
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG) {
                val name = parser.name.lowercase()
                if (name == "item") { inItem = true; title = ""; desc = ""; audio = ""; date = ""; image = p.image }
                else if (inItem) when (name) {
                    "title" -> title = parser.nextText()
                    "description", "summary" -> if (desc.isEmpty()) desc = parser.nextText()
                    "pubdate" -> date = parser.nextText()
                    "enclosure" -> audio = parser.getAttributeValue(null, "url") ?: ""
                    "image" -> image = parser.getAttributeValue(null, "href") ?: image
                }
            } else if (event == XmlPullParser.END_TAG && parser.name.equals("item", true)) {
                if (audio.startsWith("https://")) episodes.add(Episode(title, desc.replace(Regex("<[^>]*>"), " ").replace(Regex("\\s+"), " ").trim(), audio, date, image, p.title))
                inItem = false
            }
            event = parser.next()
        }
        episodes.distinctBy { it.audio }
    }
}
