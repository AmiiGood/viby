package com.sweetcode.viby.download

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.documentfile.provider.DocumentFile
import com.sweetcode.viby.data.MusicRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import org.jaudiotagger.tag.images.AndroidArtwork
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.concurrent.TimeUnit

/** Resultado de una descarga. */
enum class DownloadOutcome { DOWNLOADED, ALREADY_EXISTS }

/** Busca y descarga audio de YouTube, lo taggea y lo organiza como tu Y1Library. */
class DownloadRepository(private val context: Context) {

    private val musicRepo = MusicRepository(context)
    private val downloadedPrefs = context.getSharedPreferences("viby_downloads", Context.MODE_PRIVATE)
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()
    private val musicBrainz = MusicBrainzClient(httpClient)

    private fun ensureInit() {
        if (!initialized) {
            NewPipe.init(YoutubeDownloader.instance)
            initialized = true
        }
    }

    suspend fun search(query: String): List<SearchResult> = withContext(Dispatchers.IO) {
        ensureInit()
        val extractor = ServiceList.YouTube.getSearchExtractor(query)
        extractor.fetchPage()
        extractor.initialPage.items
            .filterIsInstance<StreamInfoItem>()
            .map {
                SearchResult(
                    title = it.name,
                    uploader = it.uploaderName ?: "",
                    durationSeconds = it.duration,
                    url = it.url,
                    thumbnailUrl = it.thumbnails.maxByOrNull { t -> t.height }?.url
                        ?: it.thumbnails.lastOrNull()?.url,
                )
            }
    }

    /**
     * Recomendaciones basadas en la biblioteca: por cada semilla (artista, título) busca
     * en YouTube y junta sus relacionadas, descartando las que ya tienes.
     */
    suspend fun recommendations(
        seeds: List<Pair<String, String>>,
        ownedKeys: Set<String>,
    ): List<SearchResult> = withContext(Dispatchers.IO) {
        ensureInit()
        val out = LinkedHashMap<String, SearchResult>()
        for ((artist, title) in seeds) {
            runCatching {
                // Semilla desde YouTube Music (canciones) → relacionadas más "canción".
                val extractor = runCatching {
                    ServiceList.YouTube.getSearchExtractor("$artist $title", listOf(MUSIC_SONGS), "")
                }.getOrElse { ServiceList.YouTube.getSearchExtractor("$artist $title") }
                extractor.fetchPage()
                val seed = extractor.initialPage.items
                    .filterIsInstance<StreamInfoItem>().firstOrNull() ?: return@runCatching
                val info = StreamInfo.getInfo(ServiceList.YouTube, seed.url)
                info.relatedItems.filterIsInstance<StreamInfoItem>().forEach { item ->
                    val looksLikeMix = MIX_KEYWORDS.any { item.name.lowercase().contains(it) }
                    // Solo duración de canción (1–10 min): descarta mixes/compilaciones largas y clips cortos.
                    if (item.duration in 60L..600L && !looksLikeMix) {
                        val cleanArtist = (item.uploaderName ?: "").removeSuffix(" - Topic").trim()
                        val key = normalizeTrackKey(cleanArtist, item.name)
                        if (item.url !in out && key !in ownedKeys && !isAlreadyDownloaded(item.url)) {
                            out[item.url] = SearchResult(
                                title = item.name,
                                uploader = item.uploaderName ?: "",
                                durationSeconds = item.duration,
                                url = item.url,
                                thumbnailUrl = item.thumbnails.maxByOrNull { it.height }?.url
                                    ?: item.thumbnails.lastOrNull()?.url,
                            )
                        }
                    }
                }
            }
        }
        out.values.toList().take(40)
    }

    /** Resuelve la URL de audio para reproducir un preview (sin descargar). */
    suspend fun resolveAudioUrl(pageUrl: String): String? = withContext(Dispatchers.IO) {
        ensureInit()
        val info = StreamInfo.getInfo(ServiceList.YouTube, pageUrl)
        val candidates = info.audioStreams.filter { it.content != null }
        (candidates.filter { it.format?.suffix == "m4a" }.maxByOrNull { it.averageBitrate }
            ?: candidates.maxByOrNull { it.averageBitrate })?.content
    }

    suspend fun download(
        result: SearchResult,
        onProgress: (Float) -> Unit,
    ): Result<DownloadOutcome> = withContext(Dispatchers.IO) {
        ensureInit()
        var temp: File? = null
        try {
            val folderUri = musicRepo.savedFolderUri()
                ?: return@withContext Result.failure(IllegalStateException("Sin carpeta de música"))
            if (!musicRepo.canWriteToFolder()) {
                return@withContext Result.failure(
                    SecurityException("Sin permiso de escritura: vuelve a elegir tu carpeta")
                )
            }

            // Índice de descargadas: si ya bajamos esta URL, no la repetimos.
            if (isAlreadyDownloaded(result.url)) {
                return@withContext Result.success(DownloadOutcome.ALREADY_EXISTS)
            }

            val info = StreamInfo.getInfo(ServiceList.YouTube, result.url)

            // Preferimos m4a (AAC) por compatibilidad y porque acepta tags + carátula.
            val candidates = info.audioStreams.filter { it.content != null }
            val audio = candidates.filter { it.format?.suffix == "m4a" }.maxByOrNull { it.averageBitrate }
                ?: candidates.maxByOrNull { it.averageBitrate }
                ?: return@withContext Result.failure(IllegalStateException("Sin audio disponible"))
            val ext = audio.format?.suffix ?: "m4a"

            // Metadatos: heurística + MusicBrainz (nombres canónicos + álbum).
            val (guessArtist, guessTitle) = parseArtistTitle(result.uploader, info.name)
            val mb = musicBrainz.lookup(guessArtist, guessTitle)
            val meta = TrackMetadata(
                title = (mb?.title ?: guessTitle).ifBlank { info.name },
                artist = (mb?.artist ?: guessArtist).ifBlank { "Artista desconocido" },
                album = (mb?.album ?: "").ifBlank { "Sencillos" },
            )

            // Carpeta destino: <carpeta>/<Artista>/<Álbum>/
            val root = DocumentFile.fromTreeUri(context, folderUri)
                ?: return@withContext Result.failure(IllegalStateException("Carpeta inválida"))
            val artistDir = getOrCreateDir(root, sanitize(meta.artist))
                ?: return@withContext Result.failure(IllegalStateException("No se pudo crear carpeta del artista"))
            val albumDir = getOrCreateDir(artistDir, sanitize(meta.album))
                ?: return@withContext Result.failure(IllegalStateException("No se pudo crear carpeta del álbum"))
            val fileName = "${sanitize(meta.title)}.$ext"

            // ¿Ya existe? → no la descargamos de nuevo.
            if (albumDir.findFile(fileName) != null) {
                return@withContext Result.success(DownloadOutcome.ALREADY_EXISTS)
            }

            // Carátula desde la miniatura → JPEG ≤500px (como tu downloader).
            val thumbUrl = info.thumbnails.maxByOrNull { it.height }?.url
                ?: info.thumbnails.lastOrNull()?.url
            val coverBytes = toJpegCover(downloadBytes(thumbUrl))

            // Descarga el audio a un temporal real (jaudiotagger necesita un File).
            temp = File(context.cacheDir, "viby_dl_${System.currentTimeMillis()}.$ext")
            downloadToFile(audio.content, temp, onProgress)

            // Taggea (solo m4a; webm/opus se deja sin tags).
            if (ext == "m4a") {
                runCatching { tagM4a(temp, meta, coverBytes) }
            }

            val mime = if (ext == "m4a") "audio/mp4" else "audio/webm"
            val dest = albumDir.createFile(mime, fileName)
                ?: return@withContext Result.failure(IllegalStateException("No se pudo crear el archivo"))

            context.contentResolver.openOutputStream(dest.uri)?.use { out ->
                temp.inputStream().use { it.copyTo(out) }
            } ?: run {
                dest.delete()
                return@withContext Result.failure(IllegalStateException("No se pudo escribir"))
            }

            recordDownloaded(result.url)
            Result.success(DownloadOutcome.DOWNLOADED)
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            temp?.delete()
        }
    }

    /**
     * Descarga por trozos con peticiones "Range" y reintentos. Así YouTube no resetea
     * la conexión (que pasa al pedir todo el archivo de una) y un corte puntual se reintenta.
     */
    private fun downloadToFile(url: String, dest: File, onProgress: (Float) -> Unit) {
        val total = fetchTotalSize(url)
        var start = 0L
        dest.outputStream().use { out ->
            while (true) {
                val end = if (total > 0) minOf(start + CHUNK_SIZE - 1, total - 1) else start + CHUNK_SIZE - 1
                val data = fetchRangeWithRetry(url, start, end)
                if (data == null || data.isEmpty()) break
                out.write(data)
                start += data.size
                if (total > 0) {
                    onProgress((start.toFloat() / total).coerceIn(0f, 1f))
                    if (start >= total) break
                } else if (data.size < CHUNK_SIZE) {
                    break // último trozo (no sabíamos el tamaño total)
                }
            }
        }
        if (total > 0 && start < total) error("Descarga incompleta")
    }

    private fun fetchTotalSize(url: String): Long {
        return try {
            val request = okhttp3.Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .header("Range", "bytes=0-1")
                .build()
            httpClient.newCall(request).execute().use { response ->
                response.header("Content-Range")?.substringAfter('/')?.toLongOrNull()
                    ?: response.body?.contentLength() ?: -1L
            }
        } catch (e: Exception) {
            -1L
        }
    }

    private fun fetchRangeWithRetry(url: String, start: Long, end: Long): ByteArray? {
        var attempt = 0
        while (true) {
            try {
                val request = okhttp3.Request.Builder()
                    .url(url)
                    .header("User-Agent", USER_AGENT)
                    .header("Range", "bytes=$start-$end")
                    .build()
                httpClient.newCall(request).execute().use { response ->
                    if (response.code != 206 && response.code != 200) error("HTTP ${response.code}")
                    return response.body?.bytes()
                }
            } catch (e: Exception) {
                attempt++
                if (attempt >= MAX_RETRIES) throw e
                Thread.sleep(600L * attempt)
            }
        }
    }

    private fun tagM4a(file: File, meta: TrackMetadata, coverBytes: ByteArray?) {
        val audioFile = AudioFileIO.read(file)
        val tag = audioFile.tagOrCreateAndSetDefault
        tag.setField(FieldKey.TITLE, meta.title)
        tag.setField(FieldKey.ARTIST, meta.artist)
        tag.setField(FieldKey.ALBUM, meta.album)
        if (coverBytes != null) {
            runCatching {
                val artwork = AndroidArtwork().apply {
                    binaryData = coverBytes
                    mimeType = "image/jpeg"
                    pictureType = 3 // front cover
                }
                tag.deleteArtworkField()
                tag.setField(artwork)
            }
        }
        audioFile.commit()
    }

    private fun downloadBytes(url: String?): ByteArray? {
        if (url.isNullOrBlank()) return null
        return runCatching {
            val request = okhttp3.Request.Builder().url(url).build()
            httpClient.newCall(request).execute().use {
                if (it.isSuccessful) it.body?.bytes() else null
            }
        }.getOrNull()
    }

    private fun toJpegCover(bytes: ByteArray?): ByteArray? {
        if (bytes == null) return null
        return runCatching {
            var bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
            val max = 500
            val largest = maxOf(bmp.width, bmp.height)
            if (largest > max) {
                val scale = max.toFloat() / largest
                bmp = Bitmap.createScaledBitmap(
                    bmp, (bmp.width * scale).toInt(), (bmp.height * scale).toInt(), true
                )
            }
            ByteArrayOutputStream().use { out ->
                bmp.compress(Bitmap.CompressFormat.JPEG, 90, out)
                out.toByteArray()
            }
        }.getOrNull()
    }

    private fun getOrCreateDir(parent: DocumentFile, name: String): DocumentFile? {
        parent.findFile(name)?.let { if (it.isDirectory) return it }
        return parent.createDirectory(name)
    }

    /** Deduce artista/título de un canal "Artista - Topic" o un título "Artista - Tema". */
    private fun parseArtistTitle(uploader: String, videoTitle: String): Pair<String, String> {
        val cleanUploader = uploader.removeSuffix(" - Topic").trim()
        val parts = videoTitle.split(" - ", limit = 2)
        val (artist, rawTitle) = if (parts.size == 2) {
            parts[0].trim() to parts[1].trim()
        } else {
            cleanUploader to videoTitle.trim()
        }
        val title = rawTitle
            .replace(Regex("\\((?:[^()]*?(?:official|video|audio|lyric|hd|4k)[^()]*?)\\)", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\[(?:[^\\[\\]]*?(?:official|video|audio|lyric|hd|4k)[^\\[\\]]*?)\\]", RegexOption.IGNORE_CASE), "")
            .trim()
        return artist to title.ifBlank { rawTitle }
    }

    private fun isAlreadyDownloaded(url: String): Boolean =
        downloadedPrefs.getStringSet(KEY_URLS, emptySet())?.contains(url) == true

    private fun recordDownloaded(url: String) {
        val current = downloadedPrefs.getStringSet(KEY_URLS, emptySet())?.toMutableSet() ?: mutableSetOf()
        current.add(url)
        downloadedPrefs.edit().putStringSet(KEY_URLS, current).apply()
    }

    private fun sanitize(name: String): String =
        name.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim().ifBlank { "Desconocido" }.take(120)

    companion object {
        private const val KEY_URLS = "downloaded_urls"
        private const val CHUNK_SIZE = 1L * 1024 * 1024 // 1 MB por trozo
        private const val MAX_RETRIES = 4
        private const val MUSIC_SONGS = "music_songs" // filtro de búsqueda de YouTube Music
        private val MIX_KEYWORDS = listOf(
            "playlist", "greatest hits", "full album", "álbum completo", "album completo",
            "compilation", "compilado", "grandes éxitos", "mega mix", "megamix",
        )
        private const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

        @Volatile
        private var initialized = false
    }
}
