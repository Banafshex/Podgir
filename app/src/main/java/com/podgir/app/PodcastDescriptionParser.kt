package com.podgir.app

import org.xmlpull.v1.XmlPullParser

/** Read channel-level RSS description, never an episode or image description. */
internal fun channelDescription(parser: XmlPullParser): String {
    var description = ""; var summary = ""; var channelDepth = -1
    var event = parser.eventType
    while (event != XmlPullParser.END_DOCUMENT) {
        if (event == XmlPullParser.START_TAG) {
            val name = parser.name.lowercase()
            if (name == "channel" && channelDepth < 0) channelDepth = parser.depth
            if (channelDepth >= 0 && parser.depth == channelDepth + 1) {
                if (name == "item") break
                if (name == "description" && description.isBlank()) description = readElementText(parser)
                else if (name == "summary" && summary.isBlank()) summary = readElementText(parser)
            }
        } else if (event == XmlPullParser.END_TAG && parser.name.equals("channel", true) && parser.depth == channelDepth) break
        event = parser.next()
    }
    return description.ifBlank { summary }
}

private fun readElementText(parser: XmlPullParser): String {
    val depth = parser.depth
    val text = StringBuilder()
    while (true) {
        when (parser.next()) {
            XmlPullParser.TEXT, XmlPullParser.CDSECT -> text.append(parser.text)
            XmlPullParser.START_TAG -> if (parser.name.equals("p", true) || parser.name.equals("br", true)) text.append('\n')
            XmlPullParser.END_TAG -> if (parser.depth == depth) return text.toString()
            XmlPullParser.END_DOCUMENT -> return text.toString()
        }
    }
}
