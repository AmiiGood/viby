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
    /** Otros nombres que el usuario unió a este, para poder mostrarlos y deshacerlo. */
    val unidos: List<String> = emptyList(),
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
    fun agrupar(songs: List<Song>, alias: Map<String, String> = emptyMap()): List<Artista> {
        val porId = songs.associateBy { it.id }
        return agruparEtiquetas(songs.map { it.id to it.artist }, alias).map { g ->
            Artista(
                nombre = g.nombre,
                clave = g.clave,
                canciones = g.ids.mapNotNull { porId[it] },
                unidos = g.unidos,
            )
        }
    }

    /** Un artista ya resuelto, antes de volver a colgarle sus canciones. */
    data class Grupo(
        val nombre: String,
        val clave: String,
        val unidos: List<String>,
        val ids: List<String>,
    )

    /**
     * El núcleo: agrupa pares (id, etiqueta de artista). Separado de [Song] porque
     * aquí está toda la decisión y así se puede probar sin Android de por medio.
     */
    fun agruparEtiquetas(
        entradas: List<Pair<String, String>>,
        alias: Map<String, String> = emptyMap(),
    ): List<Grupo> {
        // Primero, quiénes existen por su cuenta. Un nombre cuenta como conocido
        // cuando aparece sin separadores ambiguos, es decir, cuando la biblioteca
        // lo presenta como artista por sí solo en alguna canción.
        val conocidos = conocidos(entradas.map { it.second })

        val porClave = LinkedHashMap<String, MutableList<String>>()
        // Las variantes se guardan por su clave original, no por la del destino:
        // el nombre que se muestra sale de las etiquetas que son suyas de verdad,
        // no de las que el usuario le unió.
        val variantes = HashMap<String, MutableList<String>>()
        val unidosA = HashMap<String, LinkedHashSet<String>>()
        for ((id, etiqueta) in entradas) {
            for (nombre in separar(etiqueta, conocidos)) {
                val propia = clave(nombre)
                if (propia.isEmpty()) continue
                val k = destino(propia, alias)
                val lista = porClave.getOrPut(k) { mutableListOf() }
                if (id !in lista) lista += id
                if (k == propia) {
                    variantes.getOrPut(k) { mutableListOf() } += nombre
                } else {
                    unidosA.getOrPut(k) { LinkedHashSet() } += nombre
                }
            }
        }

        return porClave.map { (k, ids) ->
            Grupo(
                // Si solo quedan nombres unidos (el destino no tiene etiquetas
                // propias) se usa el primero de ellos antes que enseñar la clave.
                nombre = mejorVariante(variantes[k] ?: unidosA[k]?.toList().orEmpty()),
                clave = k,
                unidos = unidosA[k]?.toList().orEmpty(),
                ids = ids,
            )
        }.sortedBy { it.nombre.lowercase(Locale.ROOT) }
    }

    /**
     * Sigue la cadena de uniones hasta el artista final.
     *
     * Se puede unir A a B y luego B a C; y se corta por si alguna vez quedara un
     * ciclo guardado, que dejaría esto dando vueltas para siempre.
     */
    fun destino(clave: String, alias: Map<String, String>): String {
        var actual = clave
        val vistas = HashSet<String>()
        while (vistas.add(actual)) actual = alias[actual] ?: return actual
        return actual
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
        if (variantes.isEmpty()) "" else
        variantes.groupingBy { it }.eachCount().entries
            .sortedWith(
                compareByDescending<Map.Entry<String, Int>> { it.value }
                    .thenByDescending { e -> e.key.count { it.code > 127 } }
                    .thenByDescending { e -> e.key.count { it.isUpperCase() } }
                    .thenBy { it.key },
            )
            .first().key
}
