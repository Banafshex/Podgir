package com.podgir.app

import org.junit.Assert.assertEquals
import org.junit.Test
import org.kxml2.io.KXmlParser

class PodcastDescriptionParserTest {
    private fun read(xml: String): String {
        val parser = KXmlParser(); parser.setFeature("http://xmlpull.org/v1/doc/features.html#process-namespaces", true); parser.setInput(xml.reader())
        return channelDescription(parser)
    }
    @Test fun readsChannelNotEpisodeOrImage() {
        assertEquals("A channel", read("<rss><channel><image><description>Image</description></image><description>A channel</description><item><description>An episode</description></item></channel></rss>"))
    }
    @Test fun supportsCdataAndNestedMarkup() {
        assertEquals("<b>سلام</b>", read("<rss><channel><description><![CDATA[<b>سلام</b>]]></description></channel></rss>"))
        assertEquals("Hello world", read("<rss><channel><description>Hello <b>world</b></description></channel></rss>"))
    }
    @Test fun fallsBackToSummaryAndDoesNotUseItemText() {
        assertEquals("Summary", read("<rss xmlns:i='http://example.com'><channel><i:summary>Summary</i:summary><item><description>Episode</description></item></channel></rss>"))
        assertEquals("", read("<rss><channel><item><description>Episode</description></item></channel></rss>"))
    }
}
