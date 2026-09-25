package com.sweetcode.viby.data

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import com.sweetcode.viby.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Escribe las listas como ficheros .m3u dentro de la carpeta de música.
 *
 * Las listas viven en un JSON de la app, que se pierde al desinstalar y no ve
 * nadie más. Un .m3u junto a la música sobrevive y lo entiende cualquier
 * reproductor, así que se mantiene como copia legible de lo mismo.
 */
class M3uExporter(private val context: Context) {

    /**
     * Escribe la lista y devuelve el nombre real del fichero.
     *
     * @return el nombre con el que quedó guardado (el proveedor puede cambiarlo),
     *   o null si no se pudo escribir.
     */
    suspend fun exportar(
        raiz: Uri,
        nombreFichero: String,
        nombreLista: String,
        canciones: List<Song>,
    ): String? = withContext(Dispatchers.IO) {
        val root = DocumentFile.fromTreeUri(context, raiz) ?: return@withContext null
        val destino = root.findFile(nombreFichero)
            ?: root.createFile("audio/x-mpegurl", nombreFichero)
            ?: return@withContext null
        val texto = contenido(raiz, nombreLista, canciones)
        runCatching {
            // "wt" trunca: una lista más corta dejaría restos de la anterior.
            context.contentResolver.openOutputStream(destino.uri, "wt")?.use {
                it.write(texto.toByteArray(Charsets.UTF_8))
            } ?: return@withContext null
        }.getOrNull() ?: return@withContext null
        destino.name
    }

    suspend fun borrar(raiz: Uri, nombreFichero: String) = withContext(Dispatchers.IO) {
        DocumentFile.fromTreeUri(context, raiz)?.findFile(nombreFichero)?.delete()
        Unit
    }

    /**
     * Nombre de fichero válido a partir del de la lista.
     *
     * Los caracteres que no caben en un nombre de fichero se sustituyen en vez de
     * quitarse, para que dos listas parecidas no acaben con el mismo nombre.
     */
    fun nombreDeFichero(nombreLista: String): String {
        val limpio = nombreLista.trim()
            .map { if (it in PROHIBIDOS || it.isISOControl()) '_' else it }
            .joinToString("")
            .take(80)
            .ifBlank { "Lista" }
        return "$limpio.m3u"
    }

    private fun contenido(raiz: Uri, nombreLista: String, canciones: List<Song>): String =
        buildString {
            appendLine("#EXTM3U")
            appendLine("#PLAYLIST:$nombreLista")
            for (song in canciones) {
                val ruta = rutaRelativa(raiz, song.uri) ?: continue
                appendLine("#EXTINF:${song.durationMs / 1000},${song.artist} - ${song.title}")
                appendLine(ruta)
            }
        }

    /**
     * Ruta de la canción relativa a la carpeta de música.
     *
     * En un .m3u las rutas se resuelven desde donde está el propio fichero, y es
     * lo que hace que la lista siga valiendo si se copia la carpeta a otro sitio.
     * Se saca del identificador del documento, que para el almacenamiento del
     * dispositivo es la ruta real; si algún proveedor no lo respeta, esa canción
     * se queda fuera antes que escribir una ruta que no lleva a ninguna parte.
     */
    private fun rutaRelativa(raiz: Uri, song: Uri): String? {
        val base = runCatching { DocumentsContract.getTreeDocumentId(raiz) }.getOrNull() ?: return null
        val suyo = runCatching { DocumentsContract.getDocumentId(song) }.getOrNull() ?: return null
        if (!suyo.startsWith(base)) return null
        return suyo.removePrefix(base).trimStart('/').takeIf { it.isNotBlank() }
    }

    private companion object {
        val PROHIBIDOS = charArrayOf('/', '\\', ':', '*', '?', '"', '<', '>', '|')
    }
}
