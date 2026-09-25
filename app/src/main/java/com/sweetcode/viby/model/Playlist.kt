package com.sweetcode.viby.model

/** Una lista de reproducción hecha por el usuario. */
data class Playlist(
    /** Identificador propio: el nombre puede cambiar y repetirse. */
    val id: String,
    val nombre: String,
    val descripcion: String = "",
    /** Ruta de la portada elegida, ya copiada a la app, o null si no puso ninguna. */
    val portada: String? = null,
    /** Ids de canciones, en el orden en que se añadieron. */
    val canciones: List<String> = emptyList(),
    /** Nombre del .m3u con el que se exportó, para poder rehacerlo o borrarlo. */
    val m3u: String? = null,
    val creada: Long = System.currentTimeMillis(),
)
