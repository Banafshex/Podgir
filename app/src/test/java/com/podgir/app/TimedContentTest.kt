package com.podgir.app

import org.junit.Assert.*
import org.junit.Test

class TimedContentTest {
    @Test fun parsesWebVttWithCueSettings() {
        val cues = parseTimedTranscript("WEBVTT\n\n00:00:01.200 --> 00:00:03.400 align:start\nHello <b>world</b>\n\n")
        assertEquals(1, cues.size); assertEquals(1200, cues[0].startMs); assertEquals(3400, cues[0].endMs); assertEquals("Hello world", cues[0].text)
    }
    @Test fun parsesSrtAndMultipleLines() {
        val cues = parseTimedTranscript("1\n00:00:02,500 --> 00:00:04,000\nFirst\nSecond\n\n2\n00:00:04,000 --> 00:00:05,000\nMore\n")
        assertEquals(2, cues.size); assertEquals("First\nSecond", cues[0].text); assertEquals(2500, cues[0].startMs)
    }
    @Test fun ignoresBadTimings() {
        assertNull(parseTimestamp("00:99.000"))
        assertTrue(parseTimedTranscript("00:10.000 --> 00:05.000\ninvalid\n").isEmpty())
        assertEquals(3723000L, parseTimestamp("01:02:03.000"))
    }
}
