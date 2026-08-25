package com.sweetcode.viby.radio

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Convierte titulares crudos en guiones de locutor con personalidad, usando
 * la API de Claude (Haiku, barato) en UNA sola llamada en lote. Se llama 1x
 * al día durante la descarga; la reproducción luego es offline.
 *
 * Usa HTTP directo con OkHttp (lo idóneo en Android; el SDK de Java infla el APK).
 */
class ClaudeScriptWriter(
    private val http: OkHttpClient = OkHttpClient.Builder()
        .callTimeout(60, TimeUnit.SECONDS)
        .build(),
) {
    /**
     * Devuelve un guion por titular, en el mismo orden. Si la API falla o la
     * respuesta no cuadra, devuelve lista vacía (el repositorio hace fallback).
     */
    fun writeScripts(apiKey: String, headlines: List<Headline>): List<String> {
        if (apiKey.isBlank() || headlines.isEmpty()) return emptyList()

        val numbered = headlines.mapIndexed { i, h ->
            "${i + 1}. [${h.topic.label}] ${h.title}"
        }.joinToString("\n")

        val userMsg = """
            Aquí están los titulares de hoy. Escribe un guion breve de locutor para CADA uno,
            en el mismo orden. Devuelve SOLO un arreglo JSON de strings, sin texto extra.

            $numbered
        """.trimIndent()

        val body = JSONObject().apply {
            put("model", MODEL)
            put("max_tokens", 2000)
            put("system", SYSTEM)
            put("messages", JSONArray().put(JSONObject().apply {
                put("role", "user")
                put("content", userMsg)
            }))
        }

        val request = Request.Builder()
            .url("https://api.anthropic.com/v1/messages")
            .header("x-api-key", apiKey)
            .header("anthropic-version", "2023-06-01")
            .header("content-type", "application/json")
            .post(body.toString().toRequestBody(JSON))
            .build()

        val text = runCatching {
            http.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) return emptyList()
                val json = JSONObject(resp.body?.string().orEmpty())
                json.getJSONArray("content").getJSONObject(0).getString("text")
            }
        }.getOrNull() ?: return emptyList()

        return parseScripts(text)
    }

    private fun parseScripts(text: String): List<String> = runCatching {
        // El modelo devuelve un arreglo JSON; recortamos por si viene envuelto.
        val start = text.indexOf('[')
        val end = text.lastIndexOf(']')
        if (start < 0 || end <= start) return emptyList()
        val arr = JSONArray(text.substring(start, end + 1))
        (0 until arr.length()).map { arr.getString(it).trim() }.filter { it.isNotBlank() }
    }.getOrDefault(emptyList())

    companion object {
        private const val MODEL = "claude-haiku-4-5"
        private val JSON = "application/json; charset=utf-8".toMediaType()
        private val SYSTEM = """
            Eres "Viby", locutor de radio de la app de música Viby FM. Hablas en español,
            con onda casual y mexicana, cercano y con chispa, como un DJ real entre canciones.
            Por cada titular escribe UN guion corto (1 o 2 frases, máx ~35 palabras) para leer
            en voz alta: resume la noticia con tus palabras y méte una reacción con personalidad.
            Reglas: NO inventes datos que no estén en el titular; no uses emojis, hashtags ni
            comillas; no leas la fuente ni digas "titular"; suena natural al hablarlo.
            Devuelve SOLO un arreglo JSON de strings (un guion por titular, en orden).
        """.trimIndent()
    }
}
