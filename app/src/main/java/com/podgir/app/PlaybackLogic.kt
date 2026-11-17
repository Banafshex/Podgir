package com.podgir.app

import java.text.ParsePosition
import java.text.SimpleDateFormat
import java.util.Locale

/** An episode is finished at 98%, or within the last 15 seconds after 90%. */
fun isFinished(position: Long, duration: Long): Boolean = duration > 0 && position > 0 && (position.toDouble() / duration >= .98 || (position.toDouble() / duration >= .90 && duration - position <= 15000))
fun resumePosition(position: Long, duration: Long): Long = if (isFinished(position, duration)) 0 else position.coerceAtLeast(0)
fun progressPercent(position: Long, duration: Long): Int = if (duration <= 0 || position <= 0) 0 else ((position.toDouble() / duration) * 100).toInt().coerceIn(0, 100)

private fun publishedTime(value: String): Long? {
    val patterns = listOf("EEE, dd MMM yyyy HH:mm:ss Z", "dd MMM yyyy HH:mm:ss Z", "EEE, dd MMM yyyy HH:mm:ss z", "yyyy-MM-dd'T'HH:mm:ssXXX", "yyyy-MM-dd'T'HH:mm:ss'Z'")
    for (pattern in patterns) {
        val format = SimpleDateFormat(pattern, Locale.US).apply { isLenient = false; timeZone = java.util.TimeZone.getTimeZone("UTC") }
        val pos = ParsePosition(0); val date = format.parse(value.trim(), pos)
        if (date != null && pos.index == value.trim().length) return date.time
    }
    return null
}

/** Unknown dates stay in feed order at the end in both sort modes. */
fun sortedEpisodes(episodes: List<Episode>, oldestFirst: Boolean): List<Episode> {
    val dated = episodes.mapIndexed { index, e -> Triple(e, publishedTime(e.date), index) }
    return dated.sortedWith { a, b ->
        val x = a.second; val y = b.second
        when { x == null && y == null -> a.third.compareTo(b.third); x == null -> 1; y == null -> -1; x == y -> a.third.compareTo(b.third); oldestFirst -> x.compareTo(y); else -> y.compareTo(x) }
    }.map { it.first }
}

/** Explicit playback order follows the currently displayed sort order. */
fun episodesAfter(episodes: List<Episode>, currentAudio: String): List<Episode> {
    val index = episodes.indexOfFirst { it.audio == currentAudio }
    return if (index < 0) emptyList() else episodes.drop(index + 1).distinctBy { it.audio }
}
