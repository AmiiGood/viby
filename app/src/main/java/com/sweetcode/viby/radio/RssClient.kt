package com.sweetcode.viby.radio

import android.util.Xml
import okhttp3.OkHttpClient
import okhttp3.Request
import org.xmlpull.v1.XmlPullParser
import java.io.StringReader
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * Baja titulares recientes desde el RSS de Google News (gratis, sin API key)
 * para cada tema activo. Devuelve unos pocos por tema.
 */
class RssClient(
    private val http: OkHttpClient = OkHttpClient.Builder()
        .callTimeout(20, TimeUnit.SECONDS)
        .build(),
) {
    fun fetchHeadlines(topic: NewsTopic, max: Int = 5): List<Headline> {
        val q = URLEncoder.encode(topic.query, "UTF-8")
        val url = "https://news.google.com/rss/search?q=$q&hl=es-419&gl=MX&ceid=MX:es-419"
        val xml = runCatching {
            http.newCall(Request.Builder().url(url).header("User-Agent", UA).build())
                .execute().use { resp -> if (resp.isSuccessful) resp.body?.string() else null }
        }.getOrNull() ?: return emptyList()
        return parse(xml, topic).take(max)
    }

    private fun parse(xml: String, topic: NewsTopic): List<Headline> = runCatching {
        val parser = Xml.newPullParser().apply {
            setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            setInput(StringReader(xml))
        }
        val out = mutableListOf<Headline>()
        var inItem = false
        var title = ""
        var source = ""
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            val name = parser.name
            when (event) {
                XmlPullParser.START_TAG -> when {
                    name.equals("item", true) -> { inItem = true; title = ""; source = "" }
                    inItem && name.equals("title", true) -> title = parser.nextText().trim()
                    inItem && name.equals("source", true) -> source = parser.nextText().trim()
                }
                XmlPullParser.END_TAG -> if (name.equals("item", true)) {
                    inItem = false
                    // Google News pone "Titular - Fuente" en el título; separamos.
                    val clean = title.substringBeforeLast(" - ").trim().ifBlank { title }
                    val src = source.ifBlank { title.substringAfterLast(" - ", "").trim() }
                    if (clean.isNotBlank()) out += Headline(topic, clean, src)
                }
            }
            event = parser.next()
        }
        out
    }.getOrDefault(emptyList())

    companion object {
        private const val UA =
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120 Mobile"
    }
}
