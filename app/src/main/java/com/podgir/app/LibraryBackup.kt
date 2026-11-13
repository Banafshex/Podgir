package com.podgir.app

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.net.URI

data class SavedProgress(val audio: String, val position: Long, val duration: Long)
data class LibraryBackup(val subscriptions: List<Podcast>, val history: List<Episode>, val favorites: List<Episode>, val bookmarks: List<Bookmark>, val progress: List<SavedProgress>)

/** Portable local backup of library metadata, never downloaded files or OS download IDs. */
object BackupFiles {
    private const val MAX_BYTES = 10 * 1024 * 1024
    private const val MAX_ITEMS = 2000
    private const val MAX_DURATION = 7L * 24 * 60 * 60 * 1000

    fun encode(data: LibraryBackup): String {
        val json = JSONObject().put("format", "podgir-library").put("version", 1)
            .put("subscriptions", JSONArray(data.subscriptions.map { it.json() }))
            .put("history", JSONArray(data.history.map { it.json() }))
            .put("favorites", JSONArray(data.favorites.map { it.json() }))
            .put("bookmarks", JSONArray(data.bookmarks.map { it.json() }))
            .put("progress", JSONArray(data.progress.map { JSONObject().put("audio", it.audio).put("position", it.position).put("duration", it.duration) }))
        val text = json.toString()
        require(text.toByteArray(Charsets.UTF_8).size <= MAX_BYTES) { "Backup too large" }
        decode(text) // Never export a file this version cannot restore.
        return text
    }
    fun decode(text: String): LibraryBackup {
        require(text.toByteArray(Charsets.UTF_8).size <= MAX_BYTES) { "Backup too large" }
        val json = JSONObject(text)
        require(json.optString("format") == "podgir-library" && json.optInt("version", -1) == 1) { "Unsupported backup" }
        fun items(key: String): List<JSONObject> {
            val arr = json.getJSONArray(key); require(arr.length() <= MAX_ITEMS) { "Too many items" }
            return (0 until arr.length()).map { arr.getJSONObject(it) }
        }
        fun https(url: String): Boolean = try { val u = URI(url); u.scheme.equals("https", true) && !u.host.isNullOrBlank() } catch (_: Exception) { false }
        fun validEpisode(e: Episode): Episode {
            require(https(e.audio) && e.title.length <= 4000 && e.description.length <= 100000 && e.podcast.length <= 4000) { "Invalid episode" }
            require((e.chaptersUrl.isEmpty() || https(e.chaptersUrl)) && (e.transcriptUrl.isEmpty() || https(e.transcriptUrl))) { "Invalid resource URL" }
            return e
        }
        val subscriptions = items("subscriptions").map { Podcast.from(it) }.onEach { require(https(it.feed) && it.title.length <= 4000) }.distinctBy { it.feed }
        val history = items("history").map { validEpisode(Episode.from(it)) }.distinctBy { it.audio }
        val favorites = items("favorites").map { validEpisode(Episode.from(it)) }.distinctBy { it.audio }
        val bookmarks = items("bookmarks").map { Bookmark.from(it) }.onEach { validEpisode(it.episode); require(it.id.isNotBlank() && it.id.length <= 128 && it.position in 0..MAX_DURATION && it.note.length <= 500) }.distinctBy { it.id }
        val progress = items("progress").map { SavedProgress(it.getString("audio"), it.getLong("position"), it.getLong("duration")) }.onEach { require(https(it.audio) && it.position in 0..MAX_DURATION && it.duration in 0..MAX_DURATION) }.distinctBy { it.audio }
        return LibraryBackup(subscriptions, history, favorites, bookmarks, progress)
    }
    fun read(context: Context, uri: Uri): LibraryBackup {
        val bytes = context.contentResolver.openInputStream(uri)?.use { input ->
            val out = java.io.ByteArrayOutputStream(); val buffer = ByteArray(8192)
            while (true) { val n = input.read(buffer); if (n < 0) break; require(out.size() + n <= MAX_BYTES) { "Backup too large" }; out.write(buffer, 0, n) }
            out.toByteArray()
        } ?: error("Cannot open file")
        return decode(bytes.toString(Charsets.UTF_8))
    }
    fun write(context: Context, uri: Uri, data: LibraryBackup) {
        val text = encode(data)
        context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(text.toByteArray(Charsets.UTF_8)) } ?: error("Cannot write file")
    }
}
