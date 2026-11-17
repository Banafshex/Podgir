package com.podgir.app

import org.junit.Assert.*
import org.junit.Test

class SearchLogicTest {
    @Test fun persianVariantsMatch() {
        assertTrue(searchMatches("كتاب و زندگي", "کتاب و زندگی"))
        assertTrue(searchMatches("می\u200cخوانم", "می خوانم"))
        assertTrue(searchMatches("پَادکست", "پادکست"))
        assertFalse(searchMatches("Test", "   "))
    }
}
