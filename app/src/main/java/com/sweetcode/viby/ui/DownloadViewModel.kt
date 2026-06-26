package com.sweetcode.viby.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.sweetcode.viby.download.DownloadProgress
import com.sweetcode.viby.download.DownloadRepository
import com.sweetcode.viby.download.DownloadService
import com.sweetcode.viby.download.DownloadStatus
import com.sweetcode.viby.download.SearchResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SearchUiState(
    val isSearching: Boolean = false,
    val results: List<SearchResult> = emptyList(),
    val error: String? = null,
)

/** Estado del preview en streaming (la canción que se está escuchando antes de bajar). */
data class PreviewState(
    val url: String? = null,
    val isPlaying: Boolean = false,
    val isLoading: Boolean = false,
)

class DownloadViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = DownloadRepository(app)

    private val _state = MutableStateFlow(SearchUiState())
    val state: StateFlow<SearchUiState> = _state.asStateFlow()

    val downloads: StateFlow<Map<String, DownloadStatus>> = DownloadProgress.states
    val completedCount: StateFlow<Int> = DownloadProgress.completed

    private val _preview = MutableStateFlow(PreviewState())
    val preview: StateFlow<PreviewState> = _preview.asStateFlow()

    private var previewPlayer: ExoPlayer? = null
    private var previewUrl: String? = null

    fun search(query: String) {
        if (query.isBlank()) return
        viewModelScope.launch {
            _state.update { it.copy(isSearching = true, error = null) }
            val result = runCatching { repo.search(query) }
            _state.update {
                result.fold(
                    onSuccess = { list -> it.copy(isSearching = false, results = list) },
                    onFailure = { e -> it.copy(isSearching = false, error = e.message ?: "Error al buscar") },
                )
            }
        }
    }

    fun download(result: SearchResult) {
        val current = downloads.value[result.url]
        if (current is DownloadStatus.Downloading) return
        DownloadService.enqueue(getApplication(), result)
    }

    /** Genera recomendaciones a partir de semillas de la biblioteca. */
    fun loadRecommendations(seeds: List<Pair<String, String>>, ownedKeys: Set<String>) {
        if (seeds.isEmpty()) {
            _state.update {
                it.copy(isSearching = false, results = emptyList(),
                    error = "Agrega música a tu biblioteca para recomendarte algo.")
            }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(isSearching = true, error = null, results = emptyList()) }
            val result = runCatching { repo.recommendations(seeds, ownedKeys) }
            _state.update {
                result.fold(
                    onSuccess = { list ->
                        it.copy(
                            isSearching = false,
                            results = list,
                            error = if (list.isEmpty()) "No se encontraron recomendaciones nuevas." else null,
                        )
                    },
                    onFailure = { e -> it.copy(isSearching = false, error = e.message ?: "Error") },
                )
            }
        }
    }

    /** Reproduce/pausa el preview en streaming del resultado. */
    fun togglePreview(result: SearchResult) {
        val player = getOrCreatePlayer()
        if (previewUrl == result.url) {
            if (player.isPlaying) player.pause() else player.play()
            return
        }
        previewUrl = result.url
        _preview.value = PreviewState(url = result.url, isLoading = true)
        viewModelScope.launch {
            val streamUrl = runCatching { repo.resolveAudioUrl(result.url) }.getOrNull()
            if (streamUrl == null || previewUrl != result.url) {
                if (previewUrl == result.url) {
                    _preview.value = PreviewState()
                    previewUrl = null
                }
                return@launch
            }
            player.setMediaItem(MediaItem.fromUri(streamUrl))
            player.prepare()
            player.play()
        }
    }

    private fun getOrCreatePlayer(): ExoPlayer {
        previewPlayer?.let { return it }
        return ExoPlayer.Builder(getApplication())
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                /* handleAudioFocus = */ true, // pausa la música principal mientras escuchas el preview
            )
            .build()
            .also { player ->
                player.addListener(object : Player.Listener {
                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        _preview.update { it.copy(isPlaying = isPlaying) }
                    }

                    override fun onPlaybackStateChanged(state: Int) {
                        _preview.update { it.copy(isLoading = state == Player.STATE_BUFFERING) }
                    }
                })
                previewPlayer = player
            }
    }

    override fun onCleared() {
        previewPlayer?.release()
        previewPlayer = null
        super.onCleared()
    }
}
