package com.podgir.app

internal data class PodcastCategory(val id: Int, val fa: String, val en: String)
internal val podcastCategories = listOf(
    PodcastCategory(1303, "کمدی", "Comedy"), PodcastCategory(1487, "تاریخ", "History"),
    PodcastCategory(1318, "فناوری", "Technology"), PodcastCategory(1533, "داستان", "Fiction"),
    PodcastCategory(1304, "آموزش", "Education"), PodcastCategory(1534, "جنایی", "True Crime"),
    PodcastCategory(1314, "فرهنگ و جامعه", "Society & Culture"), PodcastCategory(1532, "علم", "Science"),
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
