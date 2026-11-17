package com.podgir.app

import org.junit.Assert.*
import org.junit.Test

class PlaybackLogicTest {
    private fun ep(id: String, date: String) = Episode(id, "", id, date, "", "")
    @Test fun incompleteEpisodeResumes() { assertEquals(120000L, resumePosition(120000, 3600000)); assertFalse(isFinished(120000, 3600000)) }
    @Test fun finishedEpisodeRestartsAndIsMarkedFinished() { assertTrue(isFinished(3590000, 3600000)); assertEquals(0L, resumePosition(3590000, 3600000)); assertEquals(100, progressPercent(4000, 3000)) }
    @Test fun unknownDurationIsNotFinished() { assertFalse(isFinished(123, 0)); assertEquals(123L, resumePosition(123, 0)); assertEquals(0, progressPercent(123, 0)) }
    @Test fun sortAndNextFollowChosenOrder() {
        val list = listOf(ep("jan", "Mon, 01 Jan 2024 12:00:00 +0000"), ep("unknown", ""), ep("feb", "Thu, 01 Feb 2024 12:00:00 +0000"))
        assertEquals(listOf("feb", "jan", "unknown"), sortedEpisodes(list, false).map { it.audio })
        val oldest = sortedEpisodes(list, true)
        assertEquals(listOf("jan", "feb", "unknown"), oldest.map { it.audio })
        assertEquals(listOf("feb", "unknown"), episodesAfter(oldest, "jan").map { it.audio })
        assertTrue(episodesAfter(oldest, "missing").isEmpty())
    }
}
