package com.sweetcode.viby.data

import android.content.Context
import android.net.Uri
import com.sweetcode.viby.model.Playlist
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/**
 * Listas de reproducción del usuario.
 *
 * Se guardan en un JSON de la app, no en la biblioteca. Es la diferencia con las
 * fotos de artista: aquellas describen un archivo que ya existe y tiene sentido
 * que viajen con él, mientras que una lista es una decisión del usuario sobre su
 * colección. Exportarlas como .m3u junto a la música sería el siguiente paso si
 * se quieren ver desde otros reproductores.
 */
class PlaylistRepository(private val context: Context) {

    private val fichero: File get() = File(context.filesDir, "playlists.json")
    private val portadas: File get() = File(context.filesDir, "playlists").apply { mkdirs() }

    suspend fun cargar(): List<Playlist> = withContext(Dispatchers.IO) {
        if (!fichero.exists()) return@withContext emptyList()
        runCatching {
            val arr = JSONArray(fichero.readText())
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    val ids = o.optJSONArray("canciones") ?: JSONArray()
                    add(
                        Playlist(
                            id = o.getString("id"),
                            nombre = o.getString("nombre"),
                            descripcion = o.optString("descripcion"),
                            portada = o.optString("portada").takeIf { it.isNotBlank() },
                            canciones = buildList {
                                for (j in 0 until ids.length()) add(ids.getString(j))
                            },
                            m3u = o.optString("m3u").takeIf { it.isNotBlank() },
                            creada = o.optLong("creada"),
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    suspend fun guardar(listas: List<Playlist>) = withContext(Dispatchers.IO) {
        runCatching {
            val arr = JSONArray()
            listas.forEach { p ->
                arr.put(
                    JSONObject().apply {
                        put("id", p.id)
                        put("nombre", p.nombre)
                        put("descripcion", p.descripcion)
                        put("portada", p.portada ?: "")
                        put("canciones", JSONArray(p.canciones))
                        put("m3u", p.m3u ?: "")
                        put("creada", p.creada)
                    }
                )
            }
            fichero.writeText(arr.toString())
        }
        Unit
    }

    fun nuevoId(): String = UUID.randomUUID().toString()

    /**
     * Copia la imagen elegida a la app y devuelve su ruta.
     *
     * No se guarda el URI del selector: es un permiso temporal y a la siguiente
     * apertura la portada saldría en blanco.
     */
    suspend fun copiarPortada(id: String, origen: Uri): String? = withContext(Dispatchers.IO) {
        // El nombre lleva la hora: si se reutilizara la misma ruta, la caché de
        // imágenes seguiría enseñando la portada anterior.
        val destino = File(portadas, "$id-${System.currentTimeMillis()}.jpg")
        val ruta = runCatching {
            context.contentResolver.openInputStream(origen)?.use { entrada ->
                destino.outputStream().use { entrada.copyTo(it) }
            } ?: return@withContext null
            destino.absolutePath
        }.getOrNull() ?: return@withContext null
        borrarPortadas(id, menos = destino.name)
        ruta
    }

    suspend fun borrarPortada(id: String) = withContext(Dispatchers.IO) {
        borrarPortadas(id, menos = null)
    }

    private fun borrarPortadas(id: String, menos: String?) {
        portadas.listFiles()
            ?.filter { it.name.startsWith("$id-") && it.name != menos }
            ?.forEach { it.delete() }
    }
}
