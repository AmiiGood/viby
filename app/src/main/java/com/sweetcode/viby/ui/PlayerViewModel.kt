package com.sweetcode.viby.ui

import android.app.Application
import android.content.ComponentName
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.sweetcode.viby.data.MusicRepository
import com.sweetcode.viby.model.Song
import com.sweetcode.viby.model.Station
import com.sweetcode.viby.playback.PlaybackService
import com.sweetcode.viby.radio.StationRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class PlayerViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = MusicRepository(app)

    private val _songs = MutableStateFlow<List<Song>>(emptyList())
    val songs: StateFlow<List<Song>> = _songs.asStateFlow()

    private val _queue = MutableStateFlow<List<Song>>(emptyList())
    val queue: StateFlow<List<Song>> = _queue.asStateFlow()

    private val _uiState = MutableStateFlow(PlayerUiState())
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    // Señal para que la pantalla de inicio despliegue el reproductor: lo pide
    // Radio, que no tiene el reproductor completo y solo puede volver atrás.
    private val _abrirReproductor = MutableStateFlow(false)
    val abrirReproductor: StateFlow<Boolean> = _abrirReproductor.asStateFlow()

    fun pedirAbrirReproductor() { _abrirReproductor.value = true }
    fun reproductorYaAbierto() { _abrirReproductor.value = false }

    private val _favorites = MutableStateFlow(repo.loadFavorites())
    val favorites: StateFlow<Set<String>> = _favorites.asStateFlow()

    private var songById: Map<String, Song> = emptyMap()

    // Emisora sonando ahora, o null si suena la biblioteca. Manda sobre currentSong.
    private var playingStation: Station? = null

    // El catálogo guarda favoritas y recientes: es donde buscar la emisora
    // cuando el proceso muere y hay que reconstruir el estado.
    private val stations = StationRepository(app)

    /**
     * Al cerrar la app el servicio sigue sonando, pero el ViewModel se muere con el
     * proceso y [playingStation] se pierde. Sin esto, al volver a abrir el mini
     * reproductor salía vacío aunque la emisora siguiera sonando.
     *
     * Se recupera cruzando el mediaId del reproductor con lo que hay guardado.
     */
    private fun recuperarEmisoraEnCurso() {
        val id = controller?.currentMediaItem?.mediaId ?: return
        if (playingStation?.id == id) return
        playingStation = (stations.loadRecents() + stations.loadFavorites())
            .firstOrNull { it.id == id }
    }

    // Orden lógico de la cola (sin barajar) y estado de aleatorio propio.
    // Manejamos el shuffle nosotros para garantizar un rebarajado real cada vez.
    private var baseOrder: List<Song> = emptyList()
    private var shuffleOn = repo.loadShuffle()

    private var controller: MediaController? = null
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var controllerReady = false
    private var restored = false

    init {
        val token = SessionToken(app, ComponentName(app, PlaybackService::class.java))
        controllerFuture = MediaController.Builder(app, token).buildAsync().also { future ->
            future.addListener({
                controller = future.get().apply {
                    addListener(playerListener)
                    repeatMode = repo.loadRepeat() // restaura el modo de repetición
                }
                controllerReady = true
                recuperarEmisoraEnCurso()
                syncFromPlayer()
                rebuildQueue()
                tryRestorePlayback()
            }, MoreExecutors.directExecutor())
        }
        _uiState.value = _uiState.value.copy(
            hasFolder = repo.savedFolderUri() != null,
            shuffleEnabled = shuffleOn, // restaura el aleatorio
        )
        repo.savedFolderUri()?.let { loadFolder(it, useCacheFirst = true) }
        startPositionUpdates()
    }

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            syncFromPlayer()
            if (!isPlaying) persistPlayback()
        }
        override fun onPlaybackStateChanged(playbackState: Int) = syncFromPlayer()
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            recuperarEmisoraEnCurso()
            syncFromPlayer()
            rebuildQueue()
            persistPlayback()
        }
        override fun onTimelineChanged(timeline: Timeline, reason: Int) {
            rebuildQueue()
            persistPlayback()
        }
        override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) = syncFromPlayer()
        override fun onShuffleModeEnabledChanged(enabled: Boolean) = syncFromPlayer()
        override fun onRepeatModeChanged(repeatMode: Int) = syncFromPlayer()
    }

    fun onFolderPicked(uri: Uri) {
        repo.persistFolder(uri)
        _uiState.value = _uiState.value.copy(hasFolder = true)
        loadFolder(uri, useCacheFirst = false)
    }

    /** Re-escanea la carpeta (p. ej. tras descargar canciones nuevas). */
    fun refreshLibrary() {
        repo.savedFolderUri()?.let { loadFolder(it, useCacheFirst = false) }
    }

    private fun loadFolder(uri: Uri, useCacheFirst: Boolean) {
        viewModelScope.launch {
            // 1) Carga instantánea desde caché (si hay) para no esperar el escaneo.
            if (useCacheFirst) {
                repo.loadCache(uri)?.let { cached ->
                    songById = cached.associateBy { it.id }
                    _songs.value = cached
                    tryRestorePlayback()
                }
            }
            // 2) Escaneo real (en segundo plano) para reflejar cambios; solo muestra spinner
            //    si no había nada que mostrar.
            val showLoading = _songs.value.isEmpty()
            if (showLoading) _uiState.value = _uiState.value.copy(isLoading = true)
            val list = repo.scanAndCache(uri)
            songById = list.associateBy { it.id }
            _songs.value = list
            _uiState.value = _uiState.value.copy(isLoading = false)
            tryRestorePlayback()
        }
    }

    fun play(list: List<Song>, index: Int) {
        val c = controller ?: return
        if (index !in list.indices) return
        playingStation = null
        // La cola = lo que tocaste + el resto de la biblioteca a continuación,
        // para que al terminar la lista siga sonando con las demás canciones.
        val contextIds = list.mapTo(HashSet()) { it.id }
        val tail = _songs.value.filter { it.id !in contextIds }
        val fullQueue = list + tail
        baseOrder = fullQueue
        val ordered = if (shuffleOn) shuffledWithFirst(fullQueue, index) else fullQueue
        val startIndex = if (shuffleOn) 0 else index
        c.setMediaItems(ordered.map { it.toMediaItem() }, startIndex, 0L)
        c.prepare()
        c.play()
        persistPlayback()
    }

    /**
     * Sintoniza una emisora. Sustituye la cola entera por el stream: mezclarlo con
     * canciones no tiene sentido (no termina nunca) y ademas romperia el orden.
     *
     * A proposito NO llama a persistPlayback(): asi se conserva intacta la cola de
     * musica guardada y al volver a abrir la app se reanuda donde la dejaste.
     */
    fun playStation(station: Station) {
        val c = controller ?: return
        playingStation = station
        baseOrder = emptyList()
        _queue.value = emptyList()
        c.setMediaItems(listOf(station.toMediaItem()), 0, 0L)
        c.prepare()
        c.play()
        syncFromPlayer()
    }

    fun togglePlay() {
        val c = controller ?: return
        if (c.isPlaying) c.pause() else c.play()
    }

    fun next() {
        val c = controller ?: return
        if (c.mediaItemCount == 0) return
        when {
            c.hasNextMediaItem() -> c.seekToNextMediaItem()
            // Al final de la cola con aleatorio: rebarajamos para que no se repita el orden.
            shuffleOn && baseOrder.isNotEmpty() -> {
                val reshuffled = baseOrder.shuffled()
                c.setMediaItems(reshuffled.map { it.toMediaItem() }, 0, 0L)
                c.prepare()
                c.play()
            }
            else -> c.seekToDefaultPosition(0) // vuelve al inicio
        }
    }

    fun previous() {
        val c = controller ?: return
        if (c.mediaItemCount == 0) return
        if (c.hasPreviousMediaItem()) c.seekToPreviousMediaItem()
        else c.seekToDefaultPosition(c.mediaItemCount - 1) // al inicio, salta al final
    }

    fun seekTo(ms: Long) {
        val c = controller ?: return
        c.seekTo(ms)
        // Refleja la posición de inmediato (aunque esté en pausa) para que el slider no salte.
        _uiState.value = _uiState.value.copy(positionMs = ms)
        persistPlayback()
    }

    fun toggleShuffle() {
        val c = controller ?: return
        shuffleOn = !shuffleOn
        repo.saveShuffle(shuffleOn)
        _uiState.value = _uiState.value.copy(shuffleEnabled = shuffleOn)
        val count = c.mediaItemCount
        if (count == 0) return
        val currentIndex = c.currentMediaItemIndex
        val current = _queue.value.getOrNull(currentIndex) ?: return
        if (baseOrder.isEmpty()) baseOrder = _queue.value

        // Quita todo MENOS la canción actual (sin cortar su reproducción), luego
        // reinserta el resto en el nuevo orden. Reordenar así no recarga el audio.
        if (currentIndex + 1 < count) c.removeMediaItems(currentIndex + 1, count)
        if (currentIndex > 0) c.removeMediaItems(0, currentIndex)

        if (shuffleOn) {
            val rest = baseOrder.filter { it.id != current.id }.shuffled()
            if (rest.isNotEmpty()) c.addMediaItems(rest.map { it.toMediaItem() })
        } else {
            val pos = baseOrder.indexOfFirst { it.id == current.id }
            if (pos > 0) {
                c.addMediaItems(0, baseOrder.subList(0, pos).map { it.toMediaItem() })
            }
            if (pos in 0 until baseOrder.lastIndex) {
                c.addMediaItems(baseOrder.subList(pos + 1, baseOrder.size).map { it.toMediaItem() })
            }
        }
    }

    /** Devuelve la lista barajada manteniendo [index] como primer elemento. */
    private fun shuffledWithFirst(list: List<Song>, index: Int): List<Song> {
        if (list.size <= 1) return list
        val rest = list.toMutableList()
        val first = rest.removeAt(index.coerceIn(0, list.lastIndex))
        rest.shuffle()
        return buildList {
            add(first)
            addAll(rest)
        }
    }

    /** Cicla OFF → ALL → ONE → OFF. */
    fun cycleRepeat() {
        val c = controller ?: return
        c.repeatMode = when (c.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
        repo.saveRepeat(c.repeatMode)
    }

    fun playQueueIndex(index: Int) {
        val c = controller ?: return
        playingStation = null
        c.seekToDefaultPosition(index)
        c.play()
    }

    fun removeFromQueue(index: Int) {
        controller?.removeMediaItem(index)
    }

    fun toggleFavorite(id: String) {
        val updated = _favorites.value.toMutableSet()
        if (!updated.add(id)) updated.remove(id)
        _favorites.value = updated
        repo.saveFavorites(updated)
    }

    private fun syncFromPlayer() {
        val c = controller ?: return
        // Solo se considera modo radio cuando el reproductor ya confirmo el stream.
        // No se borra playingStation aqui: entre pedir la emisora y que el
        // controlador lo refleje hay un instante en que aun no coinciden, y
        // borrarlo ahi apagaria el modo radio nada mas encenderlo. Se limpia
        // explicitamente cuando arranca la biblioteca.
        val station = playingStation?.takeIf { it.id == c.currentMediaItem?.mediaId }
        // Si la emisora manda metadatos ICY, el titulo ya no es su nombre sino la
        // cancion que esta sonando; el servicio lo vuelca en el MediaItem.
        val liveTitle = station?.let { s ->
            c.mediaMetadata.title?.toString()?.takeIf { it.isNotBlank() && it != s.name }
        }
        val current = station?.asSong(liveTitle)
            ?: _queue.value.getOrNull(c.currentMediaItemIndex)
            ?: songById[c.currentMediaItem?.mediaId]
        _uiState.value = _uiState.value.copy(
            currentStation = station,
            // La pone el servicio: logo de la emisora primero y, en cuanto se
            // sabe que suena, la portada del disco.
            artworkUri = c.mediaMetadata.artworkUri,
            currentSong = current,
            currentIndex = c.currentMediaItemIndex,
            isPlaying = c.isPlaying,
            positionMs = c.currentPosition.coerceAtLeast(0L),
            durationMs = c.duration.coerceAtLeast(0L),
            repeatMode = c.repeatMode,
            // shuffleEnabled lo manejamos nosotros (no se lee del player).
        )
    }

    private fun rebuildQueue() {
        val c = controller ?: return
        val list = ArrayList<Song>(c.mediaItemCount)
        for (i in 0 until c.mediaItemCount) {
            songById[c.getMediaItemAt(i).mediaId]?.let { list.add(it) }
        }
        _queue.value = list
        syncFromPlayer()
    }

    /** Reanuda la última cola/canción/posición si el reproductor está vacío al abrir. */
    private fun tryRestorePlayback() {
        if (restored || !controllerReady) return
        val c = controller ?: return
        if (c.mediaItemCount > 0) {
            restored = true
            return
        }
        if (songById.isEmpty()) return
        val pb = repo.loadPlayback() ?: return
        val restoredSongs = pb.ids.mapNotNull { songById[it] }
        if (restoredSongs.isEmpty()) return
        baseOrder = restoredSongs
        val items = restoredSongs.map { it.toMediaItem() }
        val index = pb.index.coerceIn(0, items.lastIndex)
        c.setMediaItems(items, index, pb.positionMs)
        c.prepare() // queda en pausa, listo para continuar donde se dejó
        restored = true
        rebuildQueue()
    }

    private fun persistPlayback() {
        val c = controller ?: return
        if (c.mediaItemCount == 0) return
        val ids = (0 until c.mediaItemCount).map { c.getMediaItemAt(it).mediaId }
        repo.savePlayback(ids, c.currentMediaItemIndex, c.currentPosition.coerceAtLeast(0L))
    }

    private fun startPositionUpdates() {
        viewModelScope.launch {
            var tick = 0
            while (true) {
                controller?.let { c ->
                    if (c.isPlaying) {
                        _uiState.value = _uiState.value.copy(
                            positionMs = c.currentPosition.coerceAtLeast(0L),
                            durationMs = c.duration.coerceAtLeast(0L),
                        )
                        // Guarda la posición cada ~2s mientras suena.
                        if (tick % 4 == 0) persistPlayback()
                    }
                }
                tick++
                delay(500)
            }
        }
    }

    override fun onCleared() {
        persistPlayback()
        controller?.removeListener(playerListener)
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controller = null
        super.onCleared()
    }
}

data class PlayerUiState(
    val hasFolder: Boolean = false,
    val isLoading: Boolean = false,
    val currentSong: Song? = null,
    /** No null mientras suena una emisora: la UI esconde progreso, seek y cola. */
    val currentStation: Station? = null,
    /** Caratula resuelta para lo que suena. Solo util en radio: una cancion
     *  local lleva la suya embebida y se extrae del propio archivo. */
    val artworkUri: Uri? = null,
    val currentIndex: Int = 0,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val shuffleEnabled: Boolean = false,
    val repeatMode: Int = Player.REPEAT_MODE_OFF,
)

private fun Song.toMediaItem(): MediaItem =
    MediaItem.Builder()
        .setUri(uri)
        .setMediaId(id)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setArtist(artist)
                .setAlbumTitle(album)
                .setArtworkUri(uri) // el loader extrae la carátula embebida de este archivo
                .build()
        )
        .build()

/**
 * Un stream no tiene duracion ni caratula embebida: se representa como [Song] con
 * durationMs = 0, que es justo la senal que la UI ya usa para no dibujar progreso.
 */
private fun Station.asSong(liveTitle: String? = null): Song = Song(
    id = id,
    uri = Uri.parse(streamUrl),
    // Con ICY manda la cancion y la emisora baja a la segunda linea; sin ICY,
    // la emisora es lo unico que se sabe.
    title = liveTitle ?: name,
    artist = if (liveTitle != null) name else subtitle,
    album = "",
    durationMs = 0L,
)

private fun Station.toMediaItem(): MediaItem =
    MediaItem.Builder()
        .setUri(streamUrl)
        .setMediaId(id)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(name)
                .setArtist(subtitle)
                .setArtworkUri(faviconUrl.takeIf { it.isNotBlank() }?.let(Uri::parse))
                .build()
        )
        .build()
