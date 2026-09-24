package com.korkutsoftware.doruhavadisadmin

import android.util.Xml
import androidx.core.text.HtmlCompat
import org.xmlpull.v1.XmlPullParser
import java.io.InputStream
import java.util.Locale

class RssParser {
    fun parse(inputStream: InputStream, city: String, district: String, cityDistricts: List<String> = emptyList()): List<XmlNews> {
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(inputStream, null)
        parser.nextTag()
        return readRss(parser, city, district, cityDistricts)
    }

    private fun readRss(parser: XmlPullParser, city: String, district: String, cityDistricts: List<String>): List<XmlNews> {
        val items = mutableListOf<XmlNews>()
        parser.require(XmlPullParser.START_TAG, null, "rss")
        while (parser.next() != XmlPullParser.END_TAG) {
            if (parser.eventType != XmlPullParser.START_TAG) continue
            if (parser.name == "channel") {
                items.addAll(readChannel(parser, city, district, cityDistricts))
            } else {
                skip(parser)
            }
        }
        return items
    }

    private fun readChannel(parser: XmlPullParser, city: String, district: String, cityDistricts: List<String>): List<XmlNews> {
        val items = mutableListOf<XmlNews>()
        while (parser.next() != XmlPullParser.END_TAG) {
            if (parser.eventType != XmlPullParser.START_TAG) continue
            if (parser.name == "item") {
                items.add(readItem(parser, city, district, cityDistricts))
            } else {
                skip(parser)
            }
        }
        return items
    }

    private fun readItem(parser: XmlPullParser, city: String, district: String, cityDistricts: List<String>): XmlNews {
        var title = ""
        var link = ""
        var description = ""
        var fullContent = ""
        var imageUrl = ""

        while (parser.next() != XmlPullParser.END_TAG) {
            if (parser.eventType != XmlPullParser.START_TAG) continue
            when (parser.name) {
                "title" -> title = cleanHtml(readText(parser))
                "link" -> link = readText(parser)
                "description" -> description = cleanHtml(readText(parser))
                "content:encoded" -> fullContent = cleanHtml(readText(parser))
                "enclosure" -> {
                    imageUrl = parser.getAttributeValue(null, "url") ?: ""
                    parser.nextTag()
                }
                else -> skip(parser)
            }
        }

        val combinedText = "$title $description $fullContent"
        val detectedDistrict = detectDistrict(combinedText, cityDistricts) ?: "Merkez"

        return XmlNews(title, description, fullContent, link, imageUrl, "Haberler.com", city, detectedDistrict)
    }

    private fun detectDistrict(text: String, cityDistricts: List<String>): String? {
        if (text.isEmpty() || cityDistricts.isEmpty()) return null
        val normalizedText = normalizeForSearch(text)
        for (d in cityDistricts) {
            if (d.equals("Merkez", ignoreCase = true) || d.equals("Tüm İlçeler", ignoreCase = true)) continue
            val normalizedD = normalizeForSearch(d)
            if (normalizedText.contains(normalizedD)) {
                return d
            }
        }
        return null
    }

    private fun normalizeForSearch(str: String): String {
        val locale = Locale.forLanguageTag("tr-TR")
        return str.lowercase(locale)
            .replace('ı', 'i')
            .replace('i', 'i')
            .replace('ş', 's')
            .replace('ğ', 'g')
            .replace('ü', 'u')
            .replace('ö', 'o')
            .replace('ç', 'c')
    }

    private fun cleanHtml(text: String): String {
        return HtmlCompat.fromHtml(text, HtmlCompat.FROM_HTML_MODE_LEGACY).toString().trim()
    }

    private fun readText(parser: XmlPullParser): String {
        var result = ""
        if (parser.next() == XmlPullParser.TEXT) {
            result = parser.text
            parser.nextTag()
        }
        return result
    }

    private fun skip(parser: XmlPullParser) {
        if (parser.eventType != XmlPullParser.START_TAG) {
            throw IllegalStateException()
        }
        var depth = 1
        while (depth != 0) {
            when (parser.next()) {
                XmlPullParser.END_TAG -> depth--
                XmlPullParser.START_TAG -> depth++
            }
        }
    }
}
