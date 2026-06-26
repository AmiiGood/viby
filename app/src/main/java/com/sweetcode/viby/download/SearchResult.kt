package com.sweetcode.viby.download

/** Un resultado de búsqueda de YouTube (canción/video). */
data class SearchResult(
    val title: String,
    val uploader: String,
    val durationSeconds: Long,
    val url: String,
    val thumbnailUrl: String? = null,
)

/** Clave normalizada (artista|título) para comparar si ya tienes una canción. */
internal fun normalizeTrackKey(artist: String, title: String): String =
    artist.trim().lowercase() + "|" + title.trim().lowercase()
