package com.podgir.app

import java.util.Locale

/** Match common Arabic/Persian variants for local episode search without changing stored text. */
internal fun normalizeSearch(text: String): String = text.lowercase(Locale.ROOT)
    .replace('ي', 'ی').replace('ى', 'ی').replace('ك', 'ک')
    .replace("\u0640", "").replace("\u200c", " ")
    .replace(Regex("[\\u064b-\\u065f\\u0670]"), "")
    .replace(Regex("\\s+"), " ").trim()

internal fun searchMatches(text: String, query: String): Boolean {
    val needle = normalizeSearch(query)
    return needle.isNotEmpty() && normalizeSearch(text).contains(needle)
}
