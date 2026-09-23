package com.sweetcode.viby.data

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/** Nombre del fichero dentro de la carpeta del artista. */
private const val NOMBRE = "artist.jpg"

/**
 * De dónde salen las fotos ahora mismo.
 *
 * Cambiar esta cadena invalida lo descargado antes y hace que se vuelva a pedir,
 * sobrescribiendo el fichero. Es lo que permitió pasar de portadas a retratos.
 */
private const val FUENTE = "deezer-3"

/**
 * Fotos de artista, guardadas dentro de la propia biblioteca.
 *
 * Se descargan una vez y se escriben en la carpeta del artista, no en la caché de
 * la app. Así sobreviven a reinstalar Viby, viajan con la música si se copia a otro
 * sitio, y `artist.jpg` es una convención que otros reproductores también leen.
 *
 * Si no hay carpeta que coincida (una biblioteca plana, sin carpeta por artista),
 * no se guarda nada: se prefiere no ensuciar la biblioteca con ficheros sueltos.
 */
class ArtistImages(
    private val context: Context,
    private val repo: MusicRepository,
) {

    private val http by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build()
    }

    /** En memoria, para no recorrer el árbol en cada recomposición de la lista. */
    private val resueltas = HashMap<String, Uri?>()

    /**
     * Un candado por artista, para que dos filas que piden la misma foto a la vez
     * no la descarguen (ni la escriban) dos veces.
     */
    private val enCurso = HashMap<String, Mutex>()

    /** Índice carpeta-normalizada -> carpeta, construido una sola vez por raíz. */
    private val candado = Mutex()
    private var raizIndexada: Uri? = null
    private var carpetas: Map<String, DocumentFile> = emptyMap()

    /** Quiénes ya tienen foto de la fuente actual. Se vacía al cambiar de fuente. */
    private val hechas: MutableSet<String> by lazy {
        repo.loadArtistPhotosDone(FUENTE).toMutableSet()
    }

    /**
     * Devuelve la foto del artista, descargándola la primera vez.
     *
     * @param otrosNombres nombres que el usuario unió a este. Se prueban también:
     *   un artista que se renombró suele estar catalogado por uno solo de los dos,
     *   y buscar "PXNDX" no encuentra nada mientras que "Panda" sí.
     * @param muestra título de una canción suya, que sirve para identificarlo:
     *   buscar solo por nombre confunde a artistas distintos que se llaman igual.
     * @return el URI del fichero ya guardado, o null si no se pudo resolver.
     */
    suspend fun deArtista(
        nombre: String,
        otrosNombres: List<String>,
        muestra: String?,
        raiz: Uri,
    ): Uri? {
        yaResuelta(nombre)?.let { return it.valor }

        // La lista recicla filas: al desplazarla, el mismo artista se pide varias
        // veces y las peticiones se solapan. Sin esto se descargaba la foto dos
        // veces y la segunda se guardaba como "artist (1).jpg".
        val suyo = synchronized(enCurso) { enCurso.getOrPut(nombre) { Mutex() } }
        return suyo.withLock {
            yaResuelta(nombre)?.let { return@withLock it.valor }
            val uri = withContext(Dispatchers.IO) {
                runCatching { resolver(nombre, otrosNombres, muestra, raiz) }.getOrNull()
            }
            synchronized(resueltas) { resueltas[nombre] = uri }
            uri
        }
    }

    /** Envuelto para distinguir "resuelta y dio null" de "sin resolver". */
    private class Resuelta(val valor: Uri?)

    private fun yaResuelta(nombre: String): Resuelta? = synchronized(resueltas) {
        if (resueltas.containsKey(nombre)) Resuelta(resueltas[nombre]) else null
    }

    private suspend fun resolver(
        nombre: String,
        otrosNombres: List<String>,
        muestra: String?,
        raiz: Uri,
    ): Uri? {
        val carpeta = carpetaDe(nombre, raiz) ?: return null
        val clave = ArtistNames.clave(nombre)
        val existente = carpeta.findFile(NOMBRE)

        // Ya descargada con la fuente actual: no se vuelve a pedir. Si viene de una
        // fuente anterior se ignora y se sobrescribe más abajo.
        if (clave in hechas) existente?.takeIf { it.length() > 0 }?.let { return it.uri }

        val hallazgo = descargar(nombre, otrosNombres, muestra)
        val bytes = hallazgo.bytes ?: return null
        // Si quedó un fichero a medias (la app se cerró durante la escritura) se
        // reescribe ese mismo. Crear otro solo conseguiría un "artist (1).jpg".
        val destino = existente ?: carpeta.createFile("image/jpeg", NOMBRE) ?: return null
        // Los bytes ya están en memoria: que una cancelación no lo deje truncado.
        // El modo "wt" trunca lo que hubiera: al cambiar de fuente la foto nueva
        // puede pesar menos que la vieja y quedaría basura al final.
        return withContext(NonCancellable) {
            val ok = context.contentResolver.openOutputStream(destino.uri, "wt")
                ?.use { it.write(bytes); true } ?: false
            if (!ok) return@withContext null
            // Solo se da por buena si la respuesta era de fiar. Si Deezer falló y
            // se cayó al recambio, se guarda igual para que la fila no quede vacía,
            // pero sin marcarla: así se vuelve a intentar en el siguiente arranque
            // en vez de quedarse con la portada para siempre.
            if (hallazgo.fiable) {
                synchronized(hechas) { hechas += clave }
                repo.markArtistPhotoDone(clave)
            }
            destino.uri
        }
    }

    /**
     * Busca la carpeta del artista comparando nombres normalizados: en disco suele
     * estar saneado ("AC_DC") y en la etiqueta no ("AC/DC").
     *
     * Listar la raíz por SAF es caro, así que se indexa una vez y se reutiliza
     * para todos los artistas de la lista.
     */
    private suspend fun carpetaDe(nombre: String, raiz: Uri): DocumentFile? {
        // Solo el índice va bajo candado; las descargas siguen en paralelo.
        candado.withLock {
            if (raizIndexada != raiz) {
                val root = DocumentFile.fromTreeUri(context, raiz) ?: return null
                carpetas = root.listFiles()
                    .filter { it.isDirectory }
                    .associateBy { ArtistNames.clave(it.name ?: "") }
                raizIndexada = raiz
            }
        }
        return carpetas[ArtistNames.clave(nombre)]
    }

    /**
     * Busca un retrato del artista, en orden de fiabilidad.
     *
     * Deezer sí publica fotos de artista; iTunes solo carátulas de disco. Pero el
     * buscador de Deezer ordena por popularidad y el primer resultado no siempre es
     * quien buscas: "Panda" devuelve antes a "P.A.N.D.A" que a la banda mexicana.
     * Por eso se intenta primero identificarlo por una canción que ya tienes.
     */
    private fun descargar(
        nombre: String,
        otrosNombres: List<String>,
        muestra: String?,
    ): Hallazgo {
        var falloDeezer = false
        // El nombre que se muestra manda, pero si no da nada se prueban los que el
        // usuario unió: la banda puede estar catalogada solo por el otro.
        for (candidato in listOf(nombre) + otrosNombres) {
            porCancion(candidato, muestra).let { (bytes, fallo) ->
                if (bytes != null) return Hallazgo(bytes, fiable = true)
                falloDeezer = falloDeezer || fallo
            }
            porNombre(candidato).let { (bytes, fallo) ->
                if (bytes != null) return Hallazgo(bytes, fiable = true)
                falloDeezer = falloDeezer || fallo
            }
        }
        // Último recurso: la portada de uno de sus discos. No es un retrato, pero
        // es mejor que dejar la fila con el icono genérico. Solo cuenta como
        // definitiva si Deezer contestó bien y de verdad no tiene foto.
        return Hallazgo(portadaDeiTunes(nombre), fiable = !falloDeezer)
    }

    /**
     * Una foto y si se puede dar por definitiva.
     *
     * Distinguir "este artista no tiene foto" de "Deezer no contestó" es lo que
     * evita quedarse con el recambio para siempre por un fallo de un momento.
     */
    private class Hallazgo(val bytes: ByteArray?, val fiable: Boolean)

    /** Identifica al artista por una canción suya y se queda con su foto. */
    private fun porCancion(nombre: String, muestra: String?): Pair<ByteArray?, Boolean> {
        if (muestra.isNullOrBlank()) return null to false
        val r = json("https://api.deezer.com/search?q=${cod("$nombre $muestra")}&limit=5")
        val pistas = r.cuerpo?.optJSONArray("data") ?: return null to r.fallo
        val buscado = ArtistNames.clave(nombre)
        for (i in 0 until pistas.length()) {
            val artista = pistas.getJSONObject(i).optJSONObject("artist") ?: continue
            if (ArtistNames.clave(artista.optString("name")) != buscado) continue
            val (bytes, fallo) = fotoDeArtistaDeezer(artista.optLong("id"))
            if (bytes != null) return bytes to false
            if (fallo) return null to true
        }
        return null to false
    }

    /** Sin canción que valga, el artista con ese nombre exacto que más gente sigue. */
    private fun porNombre(nombre: String): Pair<ByteArray?, Boolean> {
        val r = json("https://api.deezer.com/search/artist?q=${cod(nombre)}&limit=10")
        val datos = r.cuerpo?.optJSONArray("data") ?: return null to r.fallo
        val buscado = ArtistNames.clave(nombre)
        var mejor: JSONObject? = null
        for (i in 0 until datos.length()) {
            val a = datos.getJSONObject(i)
            if (ArtistNames.clave(a.optString("name")) != buscado) continue
            if (mejor == null || a.optLong("nb_fan") > mejor.optLong("nb_fan")) mejor = a
        }
        val elegido = mejor ?: return null to false
        val bytes = imagenDeezer(elegido.optString("picture_xl"))
        return bytes to (bytes == null && "/artist//" !in elegido.optString("picture_xl"))
    }

    private fun fotoDeArtistaDeezer(id: Long): Pair<ByteArray?, Boolean> {
        if (id <= 0) return null to false
        val r = json("https://api.deezer.com/artist/$id")
        val a = r.cuerpo ?: return null to r.fallo
        return imagenDeezer(a.optString("picture_xl")) to false
    }

    /**
     * Deezer no falla cuando un artista no tiene foto: devuelve una silueta
     * genérica, y se reconoce porque la URL viene sin el identificador de imagen.
     */
    private fun imagenDeezer(url: String?): ByteArray? {
        if (url.isNullOrBlank() || "/artist//" in url) return null
        return pedir(url)
    }

    private fun portadaDeiTunes(nombre: String): ByteArray? {
        val url = "https://itunes.apple.com/search?term=${cod(nombre)}" +
            "&media=music&entity=musicArtist&limit=1"
        val res = json(url).cuerpo?.optJSONArray("results") ?: return null
        if (res.length() == 0) return null
        val id = res.getJSONObject(0).optLong("artistId").takeIf { it > 0 } ?: return null
        val albums = json("https://itunes.apple.com/lookup?id=$id&entity=album&limit=1")
            .cuerpo?.optJSONArray("results") ?: return null
        for (i in 0 until albums.length()) {
            val art = albums.getJSONObject(i).optString("artworkUrl100")
            if (art.isNotBlank()) return pedir(art.replace("100x100bb", "600x600bb"))
        }
        return null
    }

    private fun cod(s: String): String = URLEncoder.encode(s, "UTF-8")

    /** Respuesta y si lo que falló fue el servicio (no que no haya nada). */
    private class Respuesta(val cuerpo: JSONObject?, val fallo: Boolean)

    private fun json(url: String): Respuesta {
        val texto = pedir(url)?.toString(Charsets.UTF_8) ?: return Respuesta(null, fallo = true)
        val obj = runCatching { JSONObject(texto) }.getOrNull()
            ?: return Respuesta(null, fallo = true)
        // Deezer contesta 200 con un objeto "error" cuando se pasa de cuota, así
        // que el código HTTP no basta para saber si la respuesta sirve.
        if (obj.has("error")) return Respuesta(null, fallo = true)
        return Respuesta(obj, fallo = false)
    }

    private fun pedir(url: String): ByteArray? {
        val req = Request.Builder().url(url)
            .header("User-Agent", "Viby/1.6 (Android music player)")
            .build()
        return runCatching {
            // La lista pide muchas fotos a la vez al desplazarse. Sin freno, la
            // ráfaga hace que el servicio empiece a rechazar peticiones y varios
            // artistas se quedan sin retrato por nada.
            aLaVez.acquire()
            try {
                http.newCall(req).execute().use { r ->
                    if (!r.isSuccessful) null else r.body?.bytes()
                }
            } finally {
                aLaVez.release()
            }
        }.getOrNull()
    }

    private companion object {
        /** Compartido por todas las instancias: el límite es del servicio, no nuestro. */
        private val aLaVez = java.util.concurrent.Semaphore(3)
    }
}
