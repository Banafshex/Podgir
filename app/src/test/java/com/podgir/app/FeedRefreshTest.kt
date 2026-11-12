package com.podgir.app

import org.junit.Assert.*
import org.junit.Test

class FeedRefreshTest {
    private fun e(id: String) = Episode(id, "", id, "", "", "")
    @Test fun onlyEpisodesBeforeKnownBaselineAreNew() {
        val feed = listOf(e("newest"), e("new"), e("seen"), e("old"))
        assertEquals(listOf("newest", "new"), newSince(feed, "seen")!!.map { it.audio })
    }
    @Test fun missingBaselineDoesNotTreatArchiveAsNew() {
        assertNull(newSince(listOf(e("one"), e("two")), "missing"))
        assertNull(newSince(emptyList(), "missing"))
    }
    @Test fun unchangedFeedHasNoNewEpisodes() {
        assertTrue(newSince(listOf(e("seen"), e("old")), "seen")!!.isEmpty())
    }
}
