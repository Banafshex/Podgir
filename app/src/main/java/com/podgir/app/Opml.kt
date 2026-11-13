package com.podgir.app

import android.content.Context
import android.net.Uri
import android.util.Xml
import org.xmlpull.v1.XmlPullParser

/** Subscription interchange only: downloads, notes, progress and favorites are not included. */
object Opml {
    private const val MAX_BYTES = 2 * 1024 * 1024
    fun read(context: Context, uri: Uri): List<Podcast> {
        val bytes = context.contentResolver.openInputStream(uri)?.use { input ->
            val out = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            while (true) { val n = input.read(buffer); if (n < 0) break; if (out.size() + n > MAX_BYTES) error("OPML too large"); out.write(buffer, 0, n) }
            out.toByteArray()
        } ?: error("Cannot open file")
        val xml = bytes.toString(Charsets.UTF_8)
        require(!xml.contains("<!DOCTYPE", ignoreCase = true)) { "DOCTYPE is not supported" }
        val parser = Xml.newPullParser().apply { setInput(xml.reader()) }
        val podcasts = mutableListOf<Podcast>(); var rootSeen = false
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG) {
                if (!rootSeen) { require(parser.name.equals("opml", true)) { "Not OPML" }; rootSeen = true }
                if (parser.name.equals("outline", true)) {
                    fun attr(name: String): String? = (0 until parser.attributeCount).firstOrNull { parser.getAttributeName(it).equals(name, true) }?.let { parser.getAttributeValue(it) }
                    val feed = attr("xmlUrl")?.trim().orEmpty()
                    val parsed = Uri.parse(feed)
                    if (parsed.scheme.equals("https", true) && !parsed.host.isNullOrBlank()) {
                        val title = attr("title")?.takeIf { it.isNotBlank() } ?: attr("text")?.takeIf { it.isNotBlank() } ?: feed
                        podcasts.add(Podcast(title, "", "", feed))
                        require(podcasts.size <= 1000) { "Too many feeds" }
                    }
                }
            }
            event = parser.next()
        }
        require(rootSeen) { "Empty file" }
        return podcasts.distinctBy { it.feed }
    }
    fun write(context: Context, uri: Uri, podcasts: List<Podcast>) {
        context.contentResolver.openOutputStream(uri, "wt")?.use { output ->
            val serializer = Xml.newSerializer(); serializer.setOutput(output, "UTF-8")
            serializer.startDocument("UTF-8", true)
            serializer.startTag(null, "opml"); serializer.attribute(null, "version", "2.0")
            serializer.startTag(null, "head"); serializer.startTag(null, "title"); serializer.text("Podgir subscriptions"); serializer.endTag(null, "title"); serializer.endTag(null, "head")
            serializer.startTag(null, "body")
            podcasts.forEach { p -> serializer.startTag(null, "outline"); serializer.attribute(null, "type", "rss"); serializer.attribute(null, "text", p.title); serializer.attribute(null, "title", p.title); serializer.attribute(null, "xmlUrl", p.feed); serializer.endTag(null, "outline") }
            serializer.endTag(null, "body"); serializer.endTag(null, "opml"); serializer.endDocument()
        } ?: error("Cannot write file")
    }
}
