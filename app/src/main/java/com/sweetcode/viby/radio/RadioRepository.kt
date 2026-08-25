package com.sweetcode.viby.radio

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Orquesta Viby FM: baja titulares (RSS), los convierte en guiones (Claude)
 * y los guarda en disco para reproducirlos offline entre canciones.
 */
class RadioRepository(
    context: Context,
    private val settings: RadioSettings = RadioSettings(context),
    private val rss: RssClient = RssClient(),
    private val writer: ClaudeScriptWriter = ClaudeScriptWriter(),
) {
    private val appContext = context.applicationContext
    private val file = File(appContext.filesDir, "radio_segments.json")

    /**
     * Refresca los segmentos del día. Devuelve cuántos quedaron guardados.
     * Pensado para llamarse en background (Worker).
     */
    fun refresh(): Int {
        val topics = settings.activeTopics()
        if (topics.isEmpty()) return 0

        // Unos pocos titulares por tema para no saturar.
        val headlines = topics.flatMap { rss.fetchHeadlines(it, max = MAX_PER_TOPIC) }
        if (headlines.isEmpty()) return 0

        val scripts = writer.writeScripts(settings.apiKey, headlines)

        val segments = headlines.mapIndexed { i, h ->
            val script = scripts.getOrNull(i)?.takeIf { it.isNotBlank() } ?: fallbackScript(h)
            NewsSegment(
                topicId = h.topic.id,
                topicLabel = h.topic.label,
                script = script,
                headline = h.title,
                source = h.source,
            )
        }

        save(segments)
        settings.lastFetchEpochMs = System.currentTimeMillis()
        resetPointer()
        return segments.size
    }

    fun loadSegments(): List<NewsSegment> = runCatching {
        if (!file.exists()) return emptyList()
        val arr = JSONArray(file.readText())
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            NewsSegment(
                topicId = o.optString("topicId"),
                topicLabel = o.optString("topicLabel"),
                script = o.optString("script"),
                headline = o.optString("headline"),
                source = o.optString("source"),
            )
        }
    }.getOrDefault(emptyList())

    fun hasSegments(): Boolean = file.exists() && loadSegments().isNotEmpty()

    /** Siguiente segmento en rotación (avanza un puntero guardado). */
    fun nextSegment(): NewsSegment? {
        val segments = loadSegments()
        if (segments.isEmpty()) return null
        val prefs = appContext.getSharedPreferences("viby_radio_state", Context.MODE_PRIVATE)
        val idx = prefs.getInt("ptr", 0).mod(segments.size)
        prefs.edit().putInt("ptr", (idx + 1).mod(segments.size)).apply()
        return segments[idx]
    }

    private fun resetPointer() {
        appContext.getSharedPreferences("viby_radio_state", Context.MODE_PRIVATE)
            .edit().putInt("ptr", 0).apply()
    }

    private fun fallbackScript(h: Headline): String =
        "En ${h.topic.label.lowercase()}, lo de hoy: ${h.title}."

    private fun save(segments: List<NewsSegment>) {
        val arr = JSONArray()
        segments.forEach { s ->
            arr.put(JSONObject().apply {
                put("topicId", s.topicId)
                put("topicLabel", s.topicLabel)
                put("script", s.script)
                put("headline", s.headline)
                put("source", s.source)
            })
        }
        runCatching { file.writeText(arr.toString()) }
    }

    companion object {
        private const val MAX_PER_TOPIC = 2
    }
}
