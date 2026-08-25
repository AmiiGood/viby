package com.sweetcode.viby.radio

/**
 * Frases de locutor con chispa para Viby FM: presenta canciones, mete cortes
 * de estación y enlaza las noticias. Extrovertido y casual (onda mexicana).
 */
object DjBanter {

    private val STATION = listOf(
        "¡Estás en Viby FM!",
        "Aquí Viby FM, tu música sin anuncios.",
        "Viby FM, puro sabor pa' tus oídos.",
        "Sigues en Viby FM, ¡no le muevas!",
    )

    private val NEWS_INTRO = listOf(
        "Ah, pero antes checa esto:",
        "Y en las noticias de hoy:",
        "Ojo con este dato, que está bueno:",
        "Pausa rapidita para la nota del momento:",
        "Antes de seguir, algo fresquecito:",
    )

    private val AFTER_NEWS = listOf(
        "Y bueno, ya estuvo de noticias.",
        "En fin, volvamos a lo que nos gusta.",
        "Ya sabes más que hace rato, ¿eh?",
        "Órale, pues seguimos.",
        "Y con eso, de vuelta a la música.",
    )

    private val SONG_INTRO = listOf(
        "¡Y ahora sí, agárrate! Va {title}, de {artist}.",
        "Sube el volumen que esto está buenísimo: {title}, de {artist}.",
        "Directo para ti suena {title}, de {artist}. ¡A disfrutarla!",
        "¡Esta está de pelos! {title}, de {artist}, vámonos.",
        "Le subimos a la energía con {title}, de {artist}.",
        "Para seguir prendidos: {title}, de {artist}. ¡Dale!",
        "Ahora se pone bueno: {title}, de {artist}.",
    )

    private val SONG_INTRO_NO_ARTIST = listOf(
        "¡Y ahora sí, agárrate! Va {title}.",
        "Sube el volumen que esto está buenísimo: {title}.",
        "Directo para ti suena {title}. ¡A disfrutarla!",
        "Ahora se pone bueno: {title}.",
    )

    private val FILLER = listOf(
        "¡Seguimos con pura música en Viby FM!",
        "No le muevas, que esto apenas se pone bueno.",
        "Puro hit, uno tras otro. ¡Vámonos!",
    )

    fun stationId() = STATION.random()
    fun newsIntro() = NEWS_INTRO.random()
    fun afterNews() = AFTER_NEWS.random()
    fun filler() = FILLER.random()

    fun songIntro(title: String, artist: String): String {
        val t = title.trim()
        val a = artist.trim()
        if (t.isBlank()) return filler()
        return if (a.isBlank()) {
            SONG_INTRO_NO_ARTIST.random().replace("{title}", t)
        } else {
            SONG_INTRO.random().replace("{title}", t).replace("{artist}", a)
        }
    }

    /**
     * Igual que [songIntro] pero determinista según [seed] (p. ej. la canción):
     * la misma canción siempre usa la misma frase, para poder cachear su audio.
     */
    fun songIntroFor(title: String, artist: String, seed: Int): String {
        val t = title.trim()
        val a = artist.trim()
        if (t.isBlank()) return FILLER[pick(seed, FILLER.size)]
        return if (a.isBlank()) {
            SONG_INTRO_NO_ARTIST[pick(seed, SONG_INTRO_NO_ARTIST.size)].replace("{title}", t)
        } else {
            SONG_INTRO[pick(seed, SONG_INTRO.size)]
                .replace("{title}", t).replace("{artist}", a)
        }
    }

    private fun pick(seed: Int, size: Int): Int = ((seed % size) + size) % size
}
