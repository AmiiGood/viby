package com.sweetcode.viby.model

import android.net.Uri

/** Una canción de la biblioteca local (MP3 con tags ID3 + carátula embebida). */
data class Song(
    val id: String,          // id estable (la URI como string)
    val uri: Uri,            // content:// URI del archivo (SAF)
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
)
