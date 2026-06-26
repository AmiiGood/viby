package com.sweetcode.viby.download

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import org.json.JSONObject
import java.net.URLEncoder

/**
 * Consulta MusicBrainz para obtener nombres canónicos de artista/título y el álbum.
 * Uso ligero (1 petición por descarga), con User-Agent identificado como exige su API.
 */
class MusicBrainzClient(private val client: OkHttpClient) {

    suspend fun lookup(artist: String, title: String): TrackMetadata? = withContext(Dispatchers.IO) {
        try {
            val query = URLEncoder.encode("artist:\"$artist\" AND recording:\"$title\"", "UTF-8")
            val url = "https://musicbrainz.org/ws/2/recording/?query=$query&fmt=json&limit=1"
            val request = okhttp3.Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val body = response.body?.string() ?: return@withContext null
                val recordings = JSONObject(body).optJSONArray("recordings") ?: return@withContext null
                if (recordings.length() == 0) return@withContext null
                val rec = recordings.getJSONObject(0)
                val canonicalTitle = rec.optString("title").ifBlank { title }
                val canonicalArtist = rec.optJSONArray("artist-credit")
                    ?.optJSONObject(0)?.optString("name")?.ifBlank { null } ?: artist
                val album = rec.optJSONArray("releases")
                    ?.optJSONObject(0)?.optString("title")?.ifBlank { null } ?: ""
                TrackMetadata(canonicalTitle, canonicalArtist, album)
            }
        } catch (e: Exception) {
            null
        }
    }

    companion object {
        private const val USER_AGENT = "Viby/1.0 ( https://github.com/AmiiGood )"
    }
}
