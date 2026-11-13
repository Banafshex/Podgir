package com.podgir.app

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.util.Xml
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
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
data class Episode(val title: String, val description: String, val audio: String, val date: String, val image: String, val podcast: String, val chaptersUrl: String = "", val transcriptUrl: String = "") {
    fun json() = JSONObject().put("title", title).put("description", description).put("audio", audio).put("date", date).put("image", image).put("podcast", podcast).put("chaptersUrl", chaptersUrl).put("transcriptUrl", transcriptUrl)
    companion object { fun from(j: JSONObject) = Episode(j.optString("title"), j.optString("description"), j.optString("audio"), j.optString("date"), j.optString("image"), j.optString("podcast"), j.optString("chaptersUrl"), j.optString("transcriptUrl")) }
}

data class Bookmark(val id: String, val episode: Episode, val position: Long, val note: String) {
    fun json() = JSONObject().put("id", id).put("episode", episode.json()).put("position", position).put("note", note)
    companion object { fun from(j: JSONObject) = Bookmark(j.getString("id"), Episode.from(j.getJSONObject("episode")), j.optLong("position"), j.optString("note")) }
}

class Repository(private val context: Context) {
    companion object { private val downloadLock = Any() }
    private val prefs = context.getSharedPreferences("podgir", Context.MODE_PRIVATE)
    fun long(key: String, default: Long = 0) = prefs.getLong(key, default)
    fun setLong(key: String, value: Long) { prefs.edit().putLong(key, value).apply() }
    fun boolean(key: String, default: Boolean) = prefs.getBoolean(key, default)
    fun setBoolean(key: String, value: Boolean) { prefs.edit().putBoolean(key, value).apply() }
    fun lastEpisode(): Episode? = runCatching { Episode.from(JSONObject(prefs.getString("lastEpisode", null) ?: return null)) }.getOrNull()
    fun saveLastEpisode(e: Episode) { prefs.edit().putString("lastEpisode", e.json().toString()).apply() }
    fun history(): List<Episode> { val a = array("history"); return (0 until a.length()).mapNotNull { runCatching { Episode.from(a.getJSONObject(it)) }.getOrNull() } }
    fun record(e: Episode) { prefs.edit().putString("history", JSONArray((listOf(e) + history().filterNot { it.audio == e.audio }).take(100).map { it.json() }).toString()).apply() }
    fun clearHistory() { history().forEach { prefs.edit().remove("pos:${it.audio}").remove("duration:${it.audio}").apply() }; prefs.edit().remove("history").apply() }
    fun duration(url: String) = prefs.getLong("duration:$url", 0)
    fun saveDuration(url: String, value: Long) { if (value > 0) prefs.edit().putLong("duration:$url", value).apply() }
    fun position(url: String) = prefs.getLong("pos:$url", 0)
    fun savePosition(url: String, pos: Long) { prefs.edit().putLong("pos:$url", pos).apply() }
    private fun array(key: String): JSONArray = try { JSONArray(prefs.getString(key, "[]")) } catch (_: Exception) { JSONArray() }
    fun subscriptions(): List<Podcast> { val a = array("subscriptions"); return (0 until a.length()).mapNotNull { runCatching { Podcast.from(a.getJSONObject(it)) }.getOrNull() } }
    fun toggle(p: Podcast) {
        val list = subscriptions().toMutableList()
        if (list.any { it.feed == p.feed }) { list.removeAll { it.feed == p.feed }; prefs.edit().remove("auto:${p.feed}").remove("seen:${p.feed}").apply() } else list.add(p)
        prefs.edit().putString("subscriptions", JSONArray(list.map { it.json() }).toString()).apply()
    }
    fun addSubscriptions(newItems: List<Podcast>): Int {
        val existing = subscriptions(); val combined = (existing + newItems).distinctBy { it.feed }
        prefs.edit().putString("subscriptions", JSONArray(combined.map { it.json() }).toString()).apply()
        return combined.size - existing.size
    }
    fun autoDownload(p: Podcast) = prefs.getBoolean("auto:${p.feed}", false)
    fun setAutoDownload(p: Podcast, enabled: Boolean) { prefs.edit().putBoolean("auto:${p.feed}", enabled).apply() }
    fun seenEpisode(p: Podcast): String? = prefs.getString("seen:${p.feed}", null)
    fun setSeenEpisode(p: Podcast, url: String) { prefs.edit().putString("seen:${p.feed}", url).apply() }
    fun favorites(): List<Episode> { val a = array("favorites"); return (0 until a.length()).mapNotNull { runCatching { Episode.from(a.getJSONObject(it)) }.getOrNull() } }
    fun toggleFavorite(e: Episode) { val all = favorites(); val updated = if (all.any { it.audio == e.audio }) all.filterNot { it.audio == e.audio } else listOf(e) + all; prefs.edit().putString("favorites", JSONArray(updated.map { it.json() }).toString()).apply() }
    fun bookmarks(): List<Bookmark> { val a = array("bookmarks"); return (0 until a.length()).mapNotNull { runCatching { Bookmark.from(a.getJSONObject(it)) }.getOrNull() } }
    fun addBookmark(e: Episode, position: Long, note: String) { val b = Bookmark(java.util.UUID.randomUUID().toString(), e, position.coerceAtLeast(0), note.trim()); prefs.edit().putString("bookmarks", JSONArray((listOf(b) + bookmarks()).map { it.json() }).toString()).apply() }
    fun deleteBookmark(id: String) { prefs.edit().putString("bookmarks", JSONArray(bookmarks().filterNot { it.id == id }.map { it.json() }).toString()).apply() }
    fun downloads(): List<Episode> { val a = array("downloads"); return (0 until a.length()).mapNotNull { runCatching { Episode.from(a.getJSONObject(it)) }.getOrNull() } }
    fun queue(): List<Episode> { val a = array("queue"); return (0 until a.length()).mapNotNull { runCatching { Episode.from(a.getJSONObject(it)) }.getOrNull() } }
    fun saveQueue(episodes: List<Episode>) { prefs.edit().putString("queue", JSONArray(episodes.map { it.json() }).toString()).apply() }
    fun downloadProgress(e: Episode): Float? {
        val id = downloadId(e); if (id == -1L) return null
        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        return dm.query(DownloadManager.Query().setFilterById(id)).use { c ->
            if (!c.moveToFirst()) null else {
                val total = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                val downloaded = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                if (total > 0) (downloaded.toFloat() / total).coerceIn(0f, 1f) else null
            }
        }
    }
    fun download(e: Episode) = synchronized(downloadLock) {
        if (downloadId(e) != -1L) {
            if (downloadStatus(e) == DownloadManager.STATUS_FAILED || downloadStatus(e) == 0) removeDownload(e) else return@synchronized
        }
        val request = DownloadManager.Request(Uri.parse(e.audio)).setTitle(e.title)
            .setDescription(e.podcast).setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalFilesDir(context, Environment.DIRECTORY_PODCASTS, "${System.currentTimeMillis()}-${e.audio.hashCode()}.audio")
        if (boolean("wifiOnly", false)) request.setAllowedNetworkTypes(DownloadManager.Request.NETWORK_WIFI)
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
    fun removeDownload(e: Episode) = synchronized(downloadLock) {
        val id = downloadId(e); if (id != -1L) (context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).remove(id)
        prefs.edit().remove("download:${e.audio}").putString("downloads", JSONArray(downloads().filterNot { it.audio == e.audio }.map { it.json() }).toString()).apply()
    }
    private fun read(url: String, limit: Int = 8 * 1024 * 1024): String {
        require(url.startsWith("https://")) { "HTTPS required" }
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 15000; conn.readTimeout = 20000
        conn.setRequestProperty("User-Agent", "Podgir/1.0 (Android podcast player)")
        try {
            if (conn.responseCode !in 200..299) error("HTTP ${conn.responseCode}")
            return conn.inputStream.use { input ->
                val out = java.io.ByteArrayOutputStream(); val buffer = ByteArray(8192)
                while (true) { val n = input.read(buffer); if (n < 0) break; require(out.size() + n <= limit) { "Response too large" }; out.write(buffer, 0, n) }
                out.toString("UTF-8")
            }
        } finally { conn.disconnect() }
    }
    suspend fun chapters(url: String): List<Chapter> = withContext(Dispatchers.IO) { parseChapters(read(url, 2 * 1024 * 1024)) }
    suspend fun transcript(url: String): List<TranscriptCue> = withContext(Dispatchers.IO) { parseTimedTranscript(read(url, 2 * 1024 * 1024)) }
    suspend fun search(query: String): List<Podcast> = withContext(Dispatchers.IO) {
        val url = "https://itunes.apple.com/search?media=podcast&limit=40&term=" + java.net.URLEncoder.encode(query, "UTF-8")
        val a = JSONObject(read(url)).getJSONArray("results")
        (0 until a.length()).mapNotNull { i -> val j = a.getJSONObject(i); val feed = j.optString("feedUrl"); if (!feed.startsWith("https://")) null else Podcast(j.optString("collectionName"), j.optString("artistName"), j.optString("artworkUrl600", j.optString("artworkUrl100")), feed) }.distinctBy { it.feed }
    }
    suspend fun searchFollowedEpisodes(query: String, podcasts: List<Podcast>): Pair<List<Episode>, Int> = coroutineScope {
        val semaphore = Semaphore(4)
        val results = podcasts.take(20).map { podcast -> async {
            semaphore.withPermit {
                try {
                    val feed = episodes(podcast).take(200)
                    val matchPodcast = podcast.title.contains(query, true) || podcast.author.contains(query, true)
                    Pair(feed.filter { matchPodcast || it.title.contains(query, true) || it.description.contains(query, true) }, false)
                } catch (e: CancellationException) { throw e } catch (_: Exception) { Pair(emptyList(), true) }
            }
        } }.awaitAll()
        Pair(results.flatMap { it.first }.distinctBy { it.audio }.take(50), results.count { it.second })
    }
    suspend fun episodes(p: Podcast): List<Episode> = withContext(Dispatchers.IO) {
        val parser = Xml.newPullParser(); parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true); parser.setInput(read(p.feed).reader())
        val episodes = mutableListOf<Episode>(); var inItem = false; var title = ""; var desc = ""; var audio = ""; var date = ""; var image = p.image; var chapters = ""; var transcript = ""
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG) {
                val name = parser.name.lowercase()
                if (name == "item") { inItem = true; title = ""; desc = ""; audio = ""; date = ""; image = p.image; chapters = ""; transcript = "" }
                else if (inItem) when (name) {
                    "title" -> title = parser.nextText()
                    "description", "summary" -> if (desc.isEmpty()) desc = parser.nextText()
                    "pubdate" -> date = parser.nextText()
                    "enclosure" -> audio = parser.getAttributeValue(null, "url") ?: ""
                    "image" -> image = parser.getAttributeValue(null, "href") ?: image
                    "chapters" -> { val url = parser.getAttributeValue(null, "url").orEmpty(); val type = parser.getAttributeValue(null, "type").orEmpty(); if (url.startsWith("https://") && (type.isEmpty() || type.contains("json", true))) chapters = url }
                    "transcript" -> { val url = parser.getAttributeValue(null, "url").orEmpty(); val type = parser.getAttributeValue(null, "type").orEmpty().lowercase(); if (transcript.isEmpty() && url.startsWith("https://") && (type.contains("vtt") || type.contains("srt") || type.contains("subrip") || (type.isEmpty() && (url.contains(".vtt", true) || url.contains(".srt", true))))) transcript = url }
                }
            } else if (event == XmlPullParser.END_TAG && parser.name.equals("item", true)) {
                if (audio.startsWith("https://")) episodes.add(Episode(title, desc.replace(Regex("<[^>]*>"), " ").replace(Regex("\\s+"), " ").trim(), audio, date, image, p.title, chapters, transcript))
                inItem = false
            }
            event = parser.next()
        }
        episodes.distinctBy { it.audio }
    }
}
