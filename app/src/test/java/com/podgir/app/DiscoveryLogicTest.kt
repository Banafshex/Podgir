package com.podgir.app

import org.junit.Assert.*
import org.junit.Test

class DiscoveryLogicTest {
    private fun ep(id: String, podcast: String) = Episode(id, "", "https://example.org/$id", "", "", podcast)
    @Test fun unfinishedDistinctAndLimited() {
        val list = listOf(ep("done", "A"), ep("one", "A"), ep("again", "A"), ep("two", "B"), ep("three", "C"), ep("four", "D"), ep("zero", "E"))
        val result = continueListening(list, { if (it.title == "done") 100 else if (it.title == "zero") 0 else 20 }, { 100 })
        assertEquals(listOf("one", "two", "three"), result.map { it.title })
    }
    @Test fun recentSearchesDeduplicateAndBound() {
        assertEquals(listOf("کتاب", "science"), updatedSearchHistory(listOf("كتاب", "science"), "کتاب"))
        assertEquals(listOf("a"), updatedSearchHistory(listOf("a"), "https://example.org/feed"))
        assertEquals(20, updatedSearchHistory((1..30).map { "$it" }, "new").size)
    }
    @Test fun persianCandidateIsApproximateAndExcludesUnrelatedEnglish() {
        assertTrue(isPersianCandidate("رادیو فارسی", "تهران", "پادکست"))
        assertTrue(isPersianCandidate("Persian stories", "Someone", "persian podcast"))
        assertFalse(isPersianCandidate("American history", "Someone", "پادکست فارسی"))
    }
    @Test fun categoriesHaveUniqueIdsAndQueries() {
        assertEquals(podcastCategories.size, podcastCategories.map { it.id }.distinct().size)
        assertTrue(podcastCategories.all { it.query.isNotBlank() })
    }
}
