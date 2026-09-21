package com.sweetcode.viby.radio

import com.sweetcode.viby.model.Station
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder

/**
 * Directorio de emisoras de Radio Browser (radio-browser.info): catálogo abierto,
 * sin cuenta ni API key. Su API pide identificarse con un User-Agent propio.
 *
 * Todas las consultas filtran emisoras rotas (`hidebroken`) y piden las más
 * escuchadas primero, que es lo que hace útil un catálogo de decenas de miles.
 */
class RadioBrowserClient(private val client: OkHttpClient) {

    /** Las más escuchadas: es lo que se ve al abrir la pantalla, sin buscar nada. */
    suspend fun topStations(limit: Int = 60): List<Station> =
        get("$BASE/json/stations/topclick/$limit")

    /** Busca por nombre de emisora. */
    suspend fun search(query: String, limit: Int = 60): List<Station> =
        get("$BASE/json/stations/search?name=${enc(query)}&limit=$limit&hidebroken=true&order=clickcount&reverse=true")

    /** Filtra por género/etiqueta (rock, jazz, noticias...). */
    suspend fun byTag(tag: String, limit: Int = 60): List<Station> =
        get("$BASE/json/stations/bytag/${enc(tag)}?limit=$limit&hidebroken=true&order=clickcount&reverse=true")

    /**
     * Avisa al directorio de que se sintonizó una emisora. Es como Radio Browser
     * calcula las más escuchadas; si falla, da igual, no afecta a la reproducción.
     */
    suspend fun reportListen(stationId: String) {
        runCatching { get("$BASE/json/url/$stationId") }
    }

    private suspend fun get(url: String): List<Station> = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@withContext emptyList()
            val body = response.body?.string() ?: return@withContext emptyList()
            parse(body)
        }
    }

    private fun parse(body: String): List<Station> {
        val array = runCatching { JSONArray(body) }.getOrNull() ?: return emptyList()
        return (0 until array.length()).mapNotNull { i ->
            array.optJSONObject(i)?.let(::toStation)
        }
    }

    private fun toStation(o: JSONObject): Station? {
        // url_resolved ya viene con los redirects seguidos; sin stream no hay emisora.
        val stream = o.optString("url_resolved").ifBlank { o.optString("url") }
        if (stream.isBlank()) return null
        val id = o.optString("stationuuid").ifBlank { stream }
        return Station(
            id = id,
            name = o.optString("name").trim().ifBlank { "Sin nombre" },
            streamUrl = stream,
            faviconUrl = o.optString("favicon"),
            country = o.optString("country"),
            tags = o.optString("tags").split(",").map { it.trim() }.filter { it.isNotBlank() },
            codec = o.optString("codec").uppercase(),
            bitrate = o.optInt("bitrate"),
        )
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    private companion object {
        // Entrada con balanceo por DNS; reparte entre los servidores de la red.
        const val BASE = "https://all.api.radio-browser.info"
        const val USER_AGENT = "Viby/1.0 (Android music player; github.com/AmiiGood/viby)"
    }
}
