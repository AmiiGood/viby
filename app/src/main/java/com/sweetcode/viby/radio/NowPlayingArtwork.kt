package com.sweetcode.viby.radio

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * Busca la caratula de la cancion que anuncia una emisora por ICY.
 *
 * El titulo ICY es texto libre ("Artista - Titulo", casi siempre), asi que antes
 * de gastar una peticion hay que decidir si eso parece una cancion: las emisoras
 * tambien mandan jingles, publicidad, su propio nombre y cadenas vacias.
 *
 * La caratula se descarga a disco en vez de quedarse como URL porque asi la leen
 * igual el widget (que necesita un fichero) y el BitmapLoader de la sesion.
 */
class NowPlayingArtwork(context: Context) {

    private val dir = File(context.filesDir, "radio_art").apply { mkdirs() }

    private val http = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    // El ICY se repite cada pocos segundos: sin cache serian decenas de
    // peticiones por cancion. Se cachean tambien los fallos para no reintentar
    // eternamente algo que no esta en el catalogo.
    private val cache = object : LinkedHashMap<String, File?>(0, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, File?>) =
            size > MAX_CACHE
    }

    /** Devuelve el fichero con la caratula, o null si no hay nada que mostrar. */
    suspend fun resolve(icyTitle: String): File? {
        val query = toQuery(icyTitle) ?: return null
        synchronized(cache) { if (cache.containsKey(query)) return cache[query] }

        val file = withContext(Dispatchers.IO) { runCatching { fetch(query) }.getOrNull() }
        synchronized(cache) { cache[query] = file }
        return file
    }

    /**
     * Logo de la emisora, a disco. Es el respaldo mientras no se sabe que suena
     * (o cuando la cancion no aparece en el catalogo): la notificacion y el
     * widget necesitan un fichero, no una URL.
     */
    suspend fun logo(url: String): File? {
        if (url.isBlank()) return null
        return withContext(Dispatchers.IO) { runCatching { download(url) }.getOrNull() }
    }

    private fun download(url: String): File? {
        val file = File(dir, hash(url) + ".jpg")
        if (file.exists() && file.length() > 0) return file
        val bytes = request(url) { it.bytes() } ?: return null
        file.writeBytes(bytes)
        return file
    }

    private fun fetch(query: String): File? {
        val cached = File(dir, hash(query) + ".jpg")
        if (cached.exists() && cached.length() > 0) return cached

        val url = "https://itunes.apple.com/search" +
            "?term=${URLEncoder.encode(query, "UTF-8")}&media=music&entity=song&limit=1"
        val json = request(url) { it.string() } ?: return null

        val results = JSONObject(json).optJSONArray("results") ?: return null
        if (results.length() == 0) return null
        // iTunes devuelve la miniatura de 100px; la misma URL sirve en grande.
        val art = results.getJSONObject(0).optString("artworkUrl100")
            .replace("100x100bb", "600x600bb")
        if (art.isBlank()) return null

        val bytes = request(art) { it.bytes() } ?: return null
        cached.writeBytes(bytes)
        return cached.takeIf { it.length() > 0 }
    }

    private fun <T> request(url: String, read: (okhttp3.ResponseBody) -> T): T? {
        val request = Request.Builder().url(url).header("User-Agent", USER_AGENT).build()
        return http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) null else response.body?.let(read)
        }
    }

    private fun hash(s: String): String {
        val md = java.security.MessageDigest.getInstance("MD5")
        return md.digest(s.toByteArray()).joinToString("") { "%02x".format(it) }
    }

    companion object {
        private const val MAX_CACHE = 64
        private const val USER_AGENT = "Viby/1.0 (Android music player)"

        /** Lo que las emisoras mandan cuando NO estan poniendo musica. */
        private val JUNK = listOf(
            "unknown", "no title", "advert", "publicidad", "jingle",
            "live stream", "sin titulo", "n/a", "-",
        )

        /**
         * Convierte el titulo ICY en algo buscable, o null si no parece una cancion.
         *
         * Se quita el separador "Artista - Titulo" porque la busqueda funciona
         * mejor con las palabras sueltas que con el guion de por medio.
         */
        fun toQuery(raw: String): String? {
            val clean = raw.trim()
            if (clean.length < 4) return null
            val lower = clean.lowercase()
            if (JUNK.any { lower == it || lower.startsWith("$it ") }) return null
            // Una URL es la emisora anunciandose, no una cancion.
            if ("http://" in lower || "https://" in lower || "www." in lower) return null

            val parts = clean.split(" - ", limit = 2).map { it.trim() }.filter { it.isNotBlank() }
            // Sin guion se busca tal cual: hay emisoras que mandan solo el titulo.
            val query = if (parts.size == 2) parts.joinToString(" ") else clean
            return query.takeIf { it.length >= 4 }
        }
    }
}
