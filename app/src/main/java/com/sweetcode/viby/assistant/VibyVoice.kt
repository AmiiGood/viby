package com.sweetcode.viby.assistant

/**
 * Personalidad de Viby: frases variadas y con onda (en vez de respuestas robóticas).
 * Cada acción elige una frase al azar.
 */
object VibyVoice {

    private val NEXT = listOf(
        "Va la que sigue", "Ahí va otra", "Cambiando, va", "Saltamos a la próxima",
        "Esta te va a gustar más", "Siguiente, marchando",
    )
    private val PREVIOUS = listOf(
        "Volvamos atrás", "Regresando esa", "Otra vez la anterior, va", "Le doy para atrás",
    )
    private val PAUSE = listOf(
        "Le pongo pausa", "Aquí te espero", "Pausa, va", "Listo, congelado",
        "Cuando quieras seguimos",
    )
    private val RESUME = listOf(
        "Seguimos", "A darle de nuevo", "Volvemos a la música", "Otra vez al ruedo", "Dale play, va",
    )
    private val VOL_UP = listOf(
        "Más fuerte, va", "Que se oiga", "Subiéndole", "Así se siente mejor",
    )
    private val VOL_DOWN = listOf(
        "Le bajo tantito", "Más tranquilo", "Bajándole", "Ya, más suave",
    )
    private val NOW_PLAYING = listOf(
        "Suena {title}, de {artist}", "Esto es {title} de {artist}",
        "Estás escuchando {title}, de {artist}", "{title}, de {artist}, buen gusto",
    )
    private val PLAYING = listOf(
        "Va {title}", "Ahí va {title}", "Reproduciendo {title}, buena elección",
        "{title}, marchando", "Échate {title}",
    )
    private val NOT_FOUND = listOf(
        "No encontré {query} en tu biblioteca", "Mmm, no tengo {query}",
        "No hallé {query}, ¿la descargamos?", "Esa no la tienes: {query}",
    )
    private val NOTHING = listOf(
        "Ahorita no hay nada sonando", "No estoy tocando nada", "Está en silencio, ponme algo",
    )
    private val UNKNOWN = listOf(
        "No te entendí, ¿me repites?", "¿Cómo dijiste?", "Esa no la capté",
        "Ups, no entendí", "¿Me lo repites?",
    )
    private val NO_LIBRARY = listOf(
        "No tengo tu biblioteca cargada todavía", "Aún no cargo tu música, dame un momento",
    )

    fun next() = NEXT.random()
    fun previous() = PREVIOUS.random()
    fun pause() = PAUSE.random()
    fun resume() = RESUME.random()
    fun volumeUp() = VOL_UP.random()
    fun volumeDown() = VOL_DOWN.random()
    fun unknown() = UNKNOWN.random()
    fun nothing() = NOTHING.random()
    fun noLibrary() = NO_LIBRARY.random()

    fun nowPlaying(title: String, artist: String) =
        NOW_PLAYING.random().replace("{title}", title).replace("{artist}", artist)

    fun playing(title: String) = PLAYING.random().replace("{title}", title)

    fun notFound(query: String) = NOT_FOUND.random().replace("{query}", query)
}
