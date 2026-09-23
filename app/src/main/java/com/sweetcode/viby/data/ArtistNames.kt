package com.sweetcode.viby.data

import com.sweetcode.viby.model.Song
import java.text.Normalizer
import java.util.Locale

/** Un artista de la biblioteca, ya unificado. */
data class Artista(
    /** Forma que se muestra: la variante más común de las que hay en las etiquetas. */
    val nombre: String,
    /** Clave estable para navegar y comparar. */
    val clave: String,
    val canciones: List<Song>,
)

/**
 * Unifica los nombres de artista de las etiquetas.
 *
 * Las etiquetas de los MP3 no son consistentes y eso llenaba la lista de
 * duplicados. Hay dos causas distintas:
 *
 *  - **Se escribe diferente**: "Rosalía" / "ROSALIA" / "rosalia", o "The Strokes"
 *    frente a "Strokes". Se resuelven con una clave normalizada.
 *  - **Colaboraciones**: "Bad Bunny feat. Drake" aparecía como un artista aparte
 *    en vez de contar para los dos. Se separan.
 *
 * Separar es la parte delicada, porque hay grupos cuyo nombre lleva los mismos
 * signos: "AC/DC", "Earth, Wind & Fire", "Florence + The Machine". Por eso se
 * hace en dos niveles, ver [separar].
 */
object ArtistNames {

    /**
     * Marcas inequívocas de colaboración: nadie llama así a su grupo, se separan
     * siempre.
     */
    private val EXPLICITO = Regex(
        """\s+(?:feat\.?|ft\.?|featuring|vs\.?|versus)\s+""",
        RegexOption.IGNORE_CASE,
    )

    /**
     * Separadores ambiguos: separan una colaboración, pero también aparecen
     * dentro del nombre de un grupo. Solo se aplican bajo la condición de
     * [separar].
     */
    private val AMBIGUO = Regex(
        """\s*[,&/+;]\s*|\s+(?:x|y|con|with|w/)\s+""",
        RegexOption.IGNORE_CASE,
    )

    /** "A (feat. B)" es lo mismo que "A feat. B"; se desenvuelve antes de partir. */
    private val ENTRE_PARENTESIS = Regex(
        """[(\[]\s*((?:feat|ft|featuring|con|with)\b[^)\]]*)[)\]]""",
        RegexOption.IGNORE_CASE,
    )

    /**
     * Clave de comparación: minúsculas, sin acentos, sin puntuación y sin el
     * artículo inicial, que unas etiquetas ponen y otras no.
     */
    fun clave(nombre: String): String {
        val sinAcentos = Normalizer.normalize(nombre, Normalizer.Form.NFD)
            .replace(Regex("""\p{Mn}+"""), "")
        return sinAcentos.lowercase(Locale.ROOT).trim()
            .removePrefix("the ")
            .filter { it.isLetterOrDigit() }
    }

    /**
     * Agrupa las canciones por artista real.
     *
     * Una canción de "A feat. B" aparece bajo A y bajo B, como en cualquier
     * servicio de streaming.
     */
    fun agrupar(songs: List<Song>): List<Artista> {
        // Primero, quiénes existen por su cuenta. Un nombre cuenta como conocido
        // cuando aparece sin separadores ambiguos, es decir, cuando la biblioteca
        // lo presenta como artista por sí solo en alguna canción.
        val conocidos = conocidos(songs.map { it.artist })

        val porClave = LinkedHashMap<String, MutableList<Song>>()
        val variantes = HashMap<String, MutableList<String>>()
        for (song in songs) {
            for (nombre in separar(song.artist, conocidos)) {
                val k = clave(nombre)
                if (k.isEmpty()) continue
                val lista = porClave.getOrPut(k) { mutableListOf() }
                if (lista.none { it.id == song.id }) lista += song
                variantes.getOrPut(k) { mutableListOf() } += nombre
            }
        }

        return porClave.map { (k, canciones) ->
            Artista(nombre = mejorVariante(variantes[k].orEmpty()), clave = k, canciones = canciones)
        }.sortedBy { it.nombre.lowercase(Locale.ROOT) }
    }

    /**
     * Quiénes aparecen como artista por sí solos, sin acompañar a nadie. Es la
     * condición que usa [separar] para atreverse con los separadores ambiguos.
     */
    fun conocidos(tags: List<String>): Set<String> {
        val set = HashSet<String>()
        for (tag in tags) {
            for (parte in explicitos(tag)) {
                if (!AMBIGUO.containsMatchIn(parte)) set += clave(parte)
            }
        }
        return set
    }

    /**
     * Parte una etiqueta en los artistas que contiene.
     *
     * Los separadores ambiguos solo se aplican cuando **todas** las partes
     * resultantes son artistas que ya existen solos en la biblioteca. Así
     * "Bad Bunny, Drake" se separa si los dos están, mientras que "AC/DC" o
     * "Earth, Wind & Fire" se quedan enteros porque "AC" y "Wind" no son
     * artistas de nadie.
     */
    fun separar(tag: String, conocidos: Set<String>): List<String> =
        explicitos(tag).flatMap { parte ->
            if (!AMBIGUO.containsMatchIn(parte)) return@flatMap listOf(parte)
            val trozos = AMBIGUO.split(parte).map { it.trim() }.filter { it.isNotEmpty() }
            if (trozos.size > 1 && trozos.all { clave(it) in conocidos }) trozos else listOf(parte)
        }

    private fun explicitos(tag: String): List<String> =
        EXPLICITO.split(tag.replace(ENTRE_PARENTESIS) { " ${it.groupValues[1]} " })
            .map { it.trim() }
            .filter { it.isNotEmpty() }

    /**
     * Elige cómo escribir el nombre: la variante más repetida y, en empate, la
     * que conserva acentos y mayúsculas, que casi siempre es la bien escrita.
     */
    private fun mejorVariante(variantes: List<String>): String =
        variantes.groupingBy { it }.eachCount().entries
            .sortedWith(
                compareByDescending<Map.Entry<String, Int>> { it.value }
                    .thenByDescending { e -> e.key.count { it.code > 127 } }
                    .thenByDescending { e -> e.key.count { it.isUpperCase() } }
                    .thenBy { it.key },
            )
            .first().key
}
