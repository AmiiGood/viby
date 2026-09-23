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
 * Fotos de artista, guardadas dentro de la propia biblioteca.
 *
 * Se descargan una vez y se escriben en la carpeta del artista, no en la caché de
 * la app. Así sobreviven a reinstalar Viby, viajan con la música si se copia a otro
 * sitio, y `artist.jpg` es una convención que otros reproductores también leen.
 *
 * Si no hay carpeta que coincida (una biblioteca plana, sin carpeta por artista),
 * no se guarda nada: se prefiere no ensuciar la biblioteca con ficheros sueltos.
 */
class ArtistImages(private val context: Context) {

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

    /**
     * Devuelve la foto del artista, descargándola la primera vez.
     *
     * @return el URI del fichero ya guardado, o null si no se pudo resolver.
     */
    suspend fun deArtista(nombre: String, raiz: Uri): Uri? {
        yaResuelta(nombre)?.let { return it.valor }

        // La lista recicla filas: al desplazarla, el mismo artista se pide varias
        // veces y las peticiones se solapan. Sin esto se descargaba la foto dos
        // veces y la segunda se guardaba como "artist (1).jpg".
        val suyo = synchronized(enCurso) { enCurso.getOrPut(nombre) { Mutex() } }
        return suyo.withLock {
            yaResuelta(nombre)?.let { return@withLock it.valor }
            val uri = withContext(Dispatchers.IO) {
                runCatching { resolver(nombre, raiz) }.getOrNull()
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

    private suspend fun resolver(nombre: String, raiz: Uri): Uri? {
        val carpeta = carpetaDe(nombre, raiz) ?: return null

        // Ya descargada en una sesión anterior: no se vuelve a pedir.
        val existente = carpeta.findFile(NOMBRE)
        existente?.takeIf { it.length() > 0 }?.let { return it.uri }

        val bytes = descargar(nombre) ?: return null
        // Si quedó un fichero a medias (la app se cerró durante la escritura) se
        // reescribe ese mismo. Crear otro solo conseguiría un "artist (1).jpg".
        val destino = existente ?: carpeta.createFile("image/jpeg", NOMBRE) ?: return null
        // Los bytes ya están en memoria: que una cancelación no lo deje truncado.
        return withContext(NonCancellable) {
            val ok = context.contentResolver.openOutputStream(destino.uri)
                ?.use { it.write(bytes); true } ?: false
            if (ok) destino.uri else null
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

    private fun descargar(nombre: String): ByteArray? {
        val q = URLEncoder.encode(nombre, "UTF-8")
        val url = "https://itunes.apple.com/search?term=$q&media=music&entity=musicArtist&limit=1"
        val json = pedir(url)?.toString(Charsets.UTF_8) ?: return null
        val res = JSONObject(json).optJSONArray("results") ?: return null
        if (res.length() == 0) return null

        // La búsqueda de artista no trae imagen, pero sí su id; con él se pide un
        // álbum suyo y se usa su portada, que es lo que hacen otros clientes.
        val id = res.getJSONObject(0).optLong("artistId").takeIf { it > 0 } ?: return null
        val look = pedir("https://itunes.apple.com/lookup?id=$id&entity=album&limit=1")
            ?.toString(Charsets.UTF_8) ?: return null
        val albums = JSONObject(look).optJSONArray("results") ?: return null
        for (i in 0 until albums.length()) {
            val art = albums.getJSONObject(i).optString("artworkUrl100")
            if (art.isNotBlank()) return pedir(art.replace("100x100bb", "600x600bb"))
        }
        return null
    }

    private fun pedir(url: String): ByteArray? {
        val req = Request.Builder().url(url)
            .header("User-Agent", "Viby/1.6 (Android music player)")
            .build()
        return http.newCall(req).execute().use { r ->
            if (!r.isSuccessful) null else r.body?.bytes()
        }
    }
}
