package com.sweetcode.viby.radio

import android.content.Context
import com.sweetcode.viby.model.Station
import okhttp3.OkHttpClient
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Catálogo de emisoras y lo que el usuario guarda de él.
 *
 * Los favoritos se serializan enteros (no solo el id) para que la lista se pueda
 * abrir y reproducir sin depender de que el directorio esté disponible.
 */
class StationRepository(context: Context) {

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("viby_stations", Context.MODE_PRIVATE)

    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    val client = RadioBrowserClient(http)

    // ---- Favoritos ----

    fun loadFavorites(): List<Station> =
        decode(prefs.getString(KEY_FAV, null))

    fun saveFavorites(stations: List<Station>) {
        prefs.edit().putString(KEY_FAV, encode(stations)).apply()
    }

    // ---- Últimas escuchadas ----

    fun loadRecents(): List<Station> = decode(prefs.getString(KEY_RECENT, null))

    /** Deja la emisora al frente, sin repetidos y recortando a [MAX_RECENTS]. */
    fun addRecent(station: Station) {
        val updated = (listOf(station) + loadRecents().filter { it.id != station.id })
            .take(MAX_RECENTS)
        prefs.edit().putString(KEY_RECENT, encode(updated)).apply()
    }

    // ---- Serialización ----

    private fun encode(stations: List<Station>): String {
        val array = JSONArray()
        stations.forEach { s ->
            array.put(
                JSONObject()
                    .put("id", s.id)
                    .put("name", s.name)
                    .put("streamUrl", s.streamUrl)
                    .put("faviconUrl", s.faviconUrl)
                    .put("country", s.country)
                    .put("tags", JSONArray(s.tags))
                    .put("codec", s.codec)
                    .put("bitrate", s.bitrate)
            )
        }
        return array.toString()
    }

    private fun decode(raw: String?): List<Station> {
        if (raw.isNullOrBlank()) return emptyList()
        val array = runCatching { JSONArray(raw) }.getOrNull() ?: return emptyList()
        return (0 until array.length()).mapNotNull { i ->
            val o = array.optJSONObject(i) ?: return@mapNotNull null
            val tagsArray = o.optJSONArray("tags")
            Station(
                id = o.optString("id"),
                name = o.optString("name"),
                streamUrl = o.optString("streamUrl"),
                faviconUrl = o.optString("faviconUrl"),
                country = o.optString("country"),
                tags = (0 until (tagsArray?.length() ?: 0)).map { tagsArray!!.optString(it) },
                codec = o.optString("codec"),
                bitrate = o.optInt("bitrate"),
            ).takeIf { it.id.isNotBlank() && it.streamUrl.isNotBlank() }
        }
    }

    private companion object {
        const val KEY_FAV = "favorite_stations"
        const val KEY_RECENT = "recent_stations"
        const val MAX_RECENTS = 12
    }
}
