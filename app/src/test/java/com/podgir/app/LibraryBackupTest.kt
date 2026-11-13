package com.podgir.app

import org.junit.Assert.*
import org.junit.Test

class LibraryBackupTest {
    private val episode = Episode("قسمت اول", "متن", "https://example.org/e.mp3", "", "", "پادکست")
    private val data = LibraryBackup(
        listOf(Podcast("پادکست", "سازنده", "", "https://example.org/feed.xml")),
        listOf(episode), listOf(episode), listOf(Bookmark("123", episode, 15000, "یادداشت")),
        listOf(SavedProgress(episode.audio, 15000, 60000))
    )
    @Test fun roundTripPreservesLibraryAndProgress() {
        val restored = BackupFiles.decode(BackupFiles.encode(data))
        assertEquals(data, restored)
        assertEquals("یادداشت", restored.bookmarks.single().note)
    }
    @Test fun rejectsWrongFormatWithoutProducingBackup() {
        assertThrows(IllegalArgumentException::class.java) { BackupFiles.decode("{\"format\":\"other\",\"version\":1}") }
    }
    @Test fun rejectsInsecureEpisodeUrl() {
        val bad = data.copy(history = listOf(episode.copy(audio = "http://example.org/a.mp3")))
        assertThrows(IllegalArgumentException::class.java) { BackupFiles.encode(bad) }
    }
    @Test fun rejectsUnsupportedVersion() {
        val wrong = BackupFiles.encode(data).replace("\"version\":1", "\"version\":42")
        assertThrows(IllegalArgumentException::class.java) { BackupFiles.decode(wrong) }
    }
}
