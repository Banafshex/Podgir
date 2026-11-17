package com.podgir.app

internal data class PodcastCategory(val id: Int, val fa: String, val en: String, val query: String = fa)
internal val podcastCategories = listOf(
    PodcastCategory(1303, "کمدی", "Comedy"), PodcastCategory(1487, "تاریخ", "History"),
    PodcastCategory(1318, "فناوری", "Technology"), PodcastCategory(1483, "داستان", "Fiction"),
    PodcastCategory(1304, "آموزش", "Education"), PodcastCategory(1488, "جنایی", "True Crime"),
    PodcastCategory(1324, "فرهنگ و جامعه", "Society & Culture"), PodcastCategory(1533, "علم", "Science"),
    PodcastCategory(1321, "کسب‌وکار", "Business"), PodcastCategory(1512, "سلامت", "Health & Fitness"),
    PodcastCategory(1310, "موسیقی", "Music"), PodcastCategory(1489, "اخبار", "News")
)
internal fun continueListening(history: List<Episode>, position: (Episode) -> Long, duration: (Episode) -> Long): List<Episode> =
    history.filter { position(it) > 0 && !isFinished(position(it), duration(it)) }
        .distinctBy { normalizeSearch(it.podcast).ifBlank { it.audio } }.take(3)

internal fun updatedSearchHistory(history: List<String>, query: String): List<String> {
    val q = query.trim().take(200)
    if (q.isEmpty() || q.startsWith("https://", true)) return history
    return (listOf(q) + history.filterNot { normalizeSearch(it) == normalizeSearch(q) }).take(20)
}

/** Search metadata is an approximation: Arabic script alone cannot establish nationality. */
internal fun isPersianCandidate(title: String, author: String, term: String): Boolean {
    val text = normalizeSearch("$title $author")
    val persianWords = listOf("فارسی", "ایران", "تهران", "persian", "farsi", "iranian", "iran")
    val script = text.any { it in '\u0600'..'\u06ff' }
    return script || persianWords.any { it in text } || (term == "persian podcast" && text.contains("persian"))
}
