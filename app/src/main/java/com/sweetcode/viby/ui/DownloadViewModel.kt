package com.sweetcode.viby.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
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

/**
 * Resultado cuyo audio se está resolviendo ahora mismo.
 *
 * Solo la espera: reproducir es cosa del reproductor de la app, no de esta
 * pantalla, así que si algo suena o no se lee de su estado.
 */
data class PreviewState(
    val url: String? = null,
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


    fun search(query: String) {
        if (query.isBlank()) return
        viewModelScope.launch {
            _state.update { it.copy(isSearching = true, error = null) }
            val result = runCatching { repo.search(query) }
            _state.update {
                result.fold(
                    onSuccess = { list -> it.copy(isSearching = false, results = list) },
                    onFailure = { e -> it.copy(isSearching = false, error = mensajeDeError(e, "Error al buscar")) },
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
                    onFailure = { e -> it.copy(isSearching = false, error = mensajeDeError(e, "Error")) },
                )
            }
        }
    }

    /**
     * Resuelve el audio del resultado y se lo entrega al reproductor de la app.
     *
     * Antes esto sonaba en un ExoPlayer propio del ViewModel, y por eso el preview
     * se cortaba al salir de la pantalla y no salía en ningún sitio. Resolver la
     * URL sigue siendo cosa nuestra; reproducirla, no.
     */
    fun abrirPreview(result: SearchResult, reproducir: (String) -> Unit) {
        if (_preview.value.url == result.url) return
        _preview.value = PreviewState(url = result.url, isLoading = true)
        viewModelScope.launch {
            val streamUrl = runCatching { repo.resolveAudioUrl(result.url) }.getOrNull()
            // Si mientras tanto se pidió otro, el que manda es el otro.
            if (_preview.value.url != result.url) return@launch
            _preview.value = PreviewState()
            if (streamUrl == null) {
                _state.update { it.copy(error = "No se pudo reproducir este resultado.") }
                return@launch
            }
            reproducir(streamUrl)
        }
    }

}

/**
 * Traduce el fallo a algo accionable.
 *
 * Antes se mostraba el mensaje de la excepción tal cual, y eso deja al usuario con
 * cosas como "SSL handshake aborted: I/O error during system call", que no le dice
 * qué hacer. Los fallos de red se distinguen porque son los únicos que el usuario
 * puede resolver por su cuenta: cambiando de red.
 */
private fun mensajeDeError(e: Throwable, porDefecto: String): String = when {
    e is java.net.UnknownHostException ->
        "Sin conexión. Revisa que tengas internet."
    e is java.net.SocketTimeoutException ->
        "La conexión tardó demasiado. Inténtalo otra vez."
    e is javax.net.ssl.SSLException || e is java.net.SocketException ->
        "No se pudo conectar con el servidor. Puede que tu red esté bloqueando " +
            "la conexión: prueba con datos móviles o con otra wifi."
    e is java.io.IOException ->
        "Falló la conexión. Inténtalo de nuevo."
    else -> e.message ?: porDefecto
}
