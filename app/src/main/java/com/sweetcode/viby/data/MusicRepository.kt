package com.sweetcode.viby.data

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.sweetcode.viby.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Lee la música de una carpeta elegida por el usuario (SAF / tree URI).
 * Cachea la biblioteca a disco para abrir rápido y persiste el estado de reproducción.
 */
class MusicRepository(private val context: Context) {

    private val prefs = context.getSharedPreferences("viby_prefs", Context.MODE_PRIVATE)
    private val cacheFile: File get() = File(context.filesDir, "library_cache.json")

    // ---- Carpeta elegida ----

    fun savedFolderUri(): Uri? = prefs.getString(KEY_FOLDER, null)?.let(Uri::parse)

    fun persistFolder(uri: Uri) {
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        }
        prefs.edit().putString(KEY_FOLDER, uri.toString()).apply()
    }

    /** ¿Tenemos permiso de escritura en la carpeta guardada? (para descargas) */
    fun canWriteToFolder(): Boolean {
        val uri = savedFolderUri() ?: return false
        return context.contentResolver.persistedUriPermissions.any {
            it.uri == uri && it.isWritePermission
        }
    }

    // ---- Biblioteca (escaneo + caché) ----

    /** Escanea la carpeta (lento: lee tags de cada archivo) y guarda el resultado en caché. */
    suspend fun scanAndCache(folderUri: Uri): List<Song> = withContext(Dispatchers.IO) {
        val songs = scanFolder(folderUri)
        saveCache(folderUri, songs)
        songs
    }

    /** Devuelve la biblioteca cacheada para esa carpeta (instantáneo), o null si no hay. */
    fun loadCache(folderUri: Uri): List<Song>? {
        val f = cacheFile
        if (!f.exists()) return null
        return try {
            val root = JSONObject(f.readText())
            if (root.optString("folder") != folderUri.toString()) return null
            val arr = root.getJSONArray("songs")
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    val uri = Uri.parse(o.getString("uri"))
                    add(
                        Song(
                            id = uri.toString(),
                            uri = uri,
                            title = o.getString("title"),
                            artist = o.getString("artist"),
                            album = o.getString("album"),
                            durationMs = o.getLong("duration"),
                        )
                    )
                }
            }.takeIf { it.isNotEmpty() }
        } catch (e: Exception) {
            null
        }
    }

    private fun saveCache(folderUri: Uri, songs: List<Song>) {
        runCatching {
            val arr = JSONArray()
            songs.forEach { s ->
                arr.put(JSONObject().apply {
                    put("uri", s.uri.toString())
                    put("title", s.title)
                    put("artist", s.artist)
                    put("album", s.album)
                    put("duration", s.durationMs)
                })
            }
            val root = JSONObject().apply {
                put("folder", folderUri.toString())
                put("songs", arr)
            }
            cacheFile.writeText(root.toString())
        }
    }

    private fun scanFolder(folderUri: Uri): List<Song> {
        val root = DocumentFile.fromTreeUri(context, folderUri) ?: return emptyList()
        val files = mutableListOf<DocumentFile>()
        collectAudio(root, files)
        return files.mapNotNull { readSong(it) }
            .sortedWith(
                compareBy(
                    { it.artist.lowercase() },
                    { it.album.lowercase() },
                    { it.title.lowercase() },
                )
            )
    }

    private fun collectAudio(dir: DocumentFile, out: MutableList<DocumentFile>) {
        for (child in dir.listFiles()) {
            when {
                child.isDirectory -> collectAudio(child, out)
                child.isFile && child.name.extension() in AUDIO_EXT -> out += child
            }
        }
    }

    private fun readSong(file: DocumentFile): Song? {
        val uri = file.uri
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            val title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                ?.takeIf { it.isNotBlank() }
                ?: file.name?.substringBeforeLast('.') ?: "Desconocido"
            val artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                ?.takeIf { it.isNotBlank() } ?: "Artista desconocido"
            val album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
                ?.takeIf { it.isNotBlank() } ?: "Álbum desconocido"
            val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: 0L
            saveThumbnailIfNeeded(uri.toString(), retriever)
            Song(uri.toString(), uri, title, artist, album, duration)
        } catch (e: Exception) {
            null
        } finally {
            runCatching { retriever.release() }
        }
    }

    /** Extrae la carátula embebida una sola vez y la guarda como miniatura ≤256px en disco. */
    private fun saveThumbnailIfNeeded(id: String, retriever: MediaMetadataRetriever) {
        val file = coverThumbFile(context, id)
        if (file.exists()) return
        val bytes = retriever.embeddedPicture ?: return
        runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            var sample = 1
            val target = 256
            while (bounds.outWidth / (sample * 2) >= target && bounds.outHeight / (sample * 2) >= target) {
                sample *= 2
            }
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts) ?: return
            coverThumbsDir(context).mkdirs()
            file.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 85, it) }
            bmp.recycle()
        }
    }

    private fun String?.extension(): String =
        this?.substringAfterLast('.', "")?.lowercase() ?: ""

    // ---- Estado de reproducción (para reanudar) ----

    fun savePlayback(ids: List<String>, index: Int, positionMs: Long) {
        runCatching {
            prefs.edit()
                .putString(KEY_PB_QUEUE, JSONArray(ids).toString())
                .putInt(KEY_PB_INDEX, index)
                .putLong(KEY_PB_POS, positionMs)
                .apply()
        }
    }

    fun loadPlayback(): PlaybackState? {
        val raw = prefs.getString(KEY_PB_QUEUE, null) ?: return null
        return try {
            val arr = JSONArray(raw)
            val ids = buildList { for (i in 0 until arr.length()) add(arr.getString(i)) }
            if (ids.isEmpty()) return null
            PlaybackState(
                ids = ids,
                index = prefs.getInt(KEY_PB_INDEX, 0),
                positionMs = prefs.getLong(KEY_PB_POS, 0L),
            )
        } catch (e: Exception) {
            null
        }
    }

    data class PlaybackState(val ids: List<String>, val index: Int, val positionMs: Long)

    // ---- Favoritos ----

    fun loadFavorites(): Set<String> =
        prefs.getStringSet(KEY_FAV, emptySet())?.toSet() ?: emptySet()

    fun saveFavorites(ids: Set<String>) {
        prefs.edit().putStringSet(KEY_FAV, ids).apply()
    }

    // ---- Modos de reproducción (aleatorio / repetir) ----

    fun saveShuffle(on: Boolean) = prefs.edit().putBoolean(KEY_SHUFFLE, on).apply()
    fun loadShuffle(): Boolean = prefs.getBoolean(KEY_SHUFFLE, false)

    fun saveRepeat(mode: Int) = prefs.edit().putInt(KEY_REPEAT, mode).apply()
    fun loadRepeat(): Int = prefs.getInt(KEY_REPEAT, 0) // 0 = REPEAT_MODE_OFF

    companion object {
        private const val KEY_FOLDER = "folder_uri"
        private const val KEY_PB_QUEUE = "pb_queue"
        private const val KEY_PB_INDEX = "pb_index"
        private const val KEY_PB_POS = "pb_position"
        private const val KEY_FAV = "favorites"
        private const val KEY_SHUFFLE = "pb_shuffle"
        private const val KEY_REPEAT = "pb_repeat"
        private val AUDIO_EXT = setOf("mp3", "flac", "m4a", "aac", "ogg", "wav", "wma", "opus")
    }
}
