package com.podgir.app

import org.json.JSONObject

data class Chapter(val startMs: Long, val title: String)
data class TranscriptCue(val startMs: Long, val endMs: Long, val text: String)

/** Podcasting 2.0 JSON chapters; reject invalid or unreasonably large entries. */
fun parseChapters(json: String): List<Chapter> {
    val entries = JSONObject(json).getJSONArray("chapters")
    require(entries.length() <= 10000) { "Too many chapters" }
    return (0 until entries.length()).mapNotNull { i ->
        val item = entries.optJSONObject(i) ?: return@mapNotNull null
        val seconds = item.optDouble("startTime", Double.NaN)
        if (!seconds.isFinite() || seconds < 0 || seconds > 604800) return@mapNotNull null
        Chapter((seconds * 1000).toLong(), item.optString("title").trim().ifBlank { "Chapter ${i + 1}" })
    }.sortedBy { it.startMs }
}

/** Parses timed SRT and WebVTT cues, ignoring metadata and styling. */
fun parseTimedTranscript(text: String): List<TranscriptCue> {
    val lines = text.removePrefix("\uFEFF").replace("\r\n", "\n").replace('\r', '\n').lines()
    val timing = Regex("^\\s*(\\d{1,3}:\\d{2}(?::\\d{2})?[.,]\\d{1,3})\\s*-->\\s*(\\d{1,3}:\\d{2}(?::\\d{2})?[.,]\\d{1,3})(?:\\s+.*)?$")
    val cues = mutableListOf<TranscriptCue>()
    var i = 0
    while (i < lines.size) {
        val match = timing.matchEntire(lines[i])
        if (match == null) { i++; continue }
        val start = parseTimestamp(match.groupValues[1]); val end = parseTimestamp(match.groupValues[2]); i++
        val content = mutableListOf<String>()
        while (i < lines.size && lines[i].isNotBlank()) { content.add(lines[i]); i++ }
        val body = content.joinToString("\n").replace(Regex("<[^>]*>"), "").replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">").replace("&nbsp;", " ").trim()
        if (start != null && end != null && end > start && body.isNotBlank()) cues.add(TranscriptCue(start, end, body))
        require(cues.size <= 20000) { "Too many transcript cues" }
    }
    return cues.sortedBy { it.startMs }
}

internal fun parseTimestamp(value: String): Long? {
    val pieces = value.replace(',', '.').split(':')
    if (pieces.size !in 2..3) return null
    val secondsParts = pieces.last().split('.')
    if (secondsParts.size != 2) return null
    val seconds = secondsParts[0].toLongOrNull() ?: return null
    val millis = secondsParts[1].padEnd(3, '0').take(3).toLongOrNull() ?: return null
    val minutes = pieces[pieces.size - 2].toLongOrNull() ?: return null
    val hours = if (pieces.size == 3) pieces[0].toLongOrNull() ?: return null else 0L
    if (seconds >= 60 || minutes >= 60 || hours > 168) return null
    return ((hours * 60 + minutes) * 60 + seconds) * 1000 + millis
}
