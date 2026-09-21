package com.sweetcode.viby.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sweetcode.viby.model.Station
import com.sweetcode.viby.radio.StationRepository
import java.util.Locale
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Géneros que se ofrecen como atajo; son etiquetas reales del directorio. */
val RADIO_GENRES = listOf(
    "pop", "rock", "electronic", "jazz", "classical",
    "hip hop", "latin", "reggaeton", "news", "salsa", "cumbia", "80s",
)

/**
 * Una opción de la fila de idioma. [codigo] es el nombre en inglés tal como lo
 * indexa Radio Browser ("spanish"), o null para no filtrar.
 */
data class OpcionIdioma(val etiqueta: String, val codigo: String?)

/**
 * Las opciones que se ofrecen: el idioma del teléfono primero, luego inglés (que es
 * el que más emisoras tiene con diferencia) y por último todos.
 *
 * "Todos" no es un adorno: el idioma lo rellena quien da de alta la emisora en el
 * catálogo, así que hay emisoras mal etiquetadas que solo aparecen sin filtro.
 */
fun opcionesIdioma(): List<OpcionIdioma> {
    val delTelefono = Locale.getDefault()
    // El catálogo indexa por el nombre en inglés: "es" -> "spanish".
    val codigo = delTelefono.getDisplayLanguage(Locale.ENGLISH).lowercase(Locale.ENGLISH)
    val etiqueta = delTelefono.getDisplayLanguage(delTelefono)
        .replaceFirstChar { it.uppercase(delTelefono) }

    val opciones = mutableListOf(OpcionIdioma(etiqueta, codigo))
    if (codigo != "english") opciones += OpcionIdioma("Inglés", "english")
    opciones += OpcionIdioma("Todos", null)
    return opciones
}

data class RadioUiState(
    val query: String = "",
    val genre: String? = null,
    val idioma: String? = null,
    val stations: List<Station> = emptyList(),
    val favorites: List<Station> = emptyList(),
    val recents: List<Station> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
) {
    val favoriteIds: Set<String> get() = favorites.mapTo(HashSet()) { it.id }
}

class RadioViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = StationRepository(app)

    private val _state = MutableStateFlow(
        RadioUiState(
            favorites = repo.loadFavorites(),
            recents = repo.loadRecents(),
            idioma = opcionesIdioma().first().codigo,
        )
    )
    val state: StateFlow<RadioUiState> = _state.asStateFlow()

    private var searchJob: Job? = null

    private companion object {
        const val SEARCH_DEBOUNCE_MS = 350L
    }

    init {
        loadTop()
    }

    /** Lo que se ve al abrir: las más escuchadas del directorio. */
    fun loadTop() {
        _state.update { it.copy(query = "", genre = null) }
        val idioma = _state.value.idioma
        fetch { repo.client.topStations(idioma) }
    }

    /** Cambiar de idioma rehace la consulta actual, sea la que sea. */
    fun onIdiomaClick(codigo: String?) {
        if (_state.value.idioma == codigo) return
        _state.update { it.copy(idioma = codigo) }
        val s = _state.value
        when {
            s.query.isNotBlank() -> fetch { repo.client.search(s.query, codigo) }
            s.genre != null -> fetch { repo.client.byTag(s.genre, codigo) }
            else -> fetch { repo.client.topStations(codigo) }
        }
    }

    fun onQueryChange(query: String) {
        _state.update { it.copy(query = query, genre = null) }
        if (query.isBlank()) {
            loadTop()
            return
        }
        val idioma = _state.value.idioma
        fetch(debounceMs = SEARCH_DEBOUNCE_MS) { repo.client.search(query, idioma) }
    }

    fun onGenreClick(genre: String) {
        val next = if (_state.value.genre == genre) null else genre
        _state.update { it.copy(genre = next, query = "") }
        val idioma = _state.value.idioma
        if (next == null) loadTop() else fetch { repo.client.byTag(next, idioma) }
    }

    fun toggleFavorite(station: Station) {
        val current = _state.value.favorites
        val updated = if (current.any { it.id == station.id }) {
            current.filter { it.id != station.id }
        } else {
            current + station
        }
        repo.saveFavorites(updated)
        _state.update { it.copy(favorites = updated) }
    }

    /** Registra la escucha: alimenta las "más escuchadas" y la lista de recientes. */
    fun onStationPlayed(station: Station) {
        repo.addRecent(station)
        _state.update { it.copy(recents = repo.loadRecents()) }
        viewModelScope.launch { repo.client.reportListen(station.id) }
    }

    /**
     * Lanza una consulta cancelando la anterior. [debounceMs] deja que el usuario
     * siga tecleando sin disparar una peticion por letra.
     */
    private fun fetch(debounceMs: Long = 0L, block: suspend () -> List<Station>) {
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            if (debounceMs > 0) delay(debounceMs)
            _state.update { it.copy(isLoading = true, error = null) }
            val result = runCatching { block() }
            _state.update { s ->
                result.fold(
                    onSuccess = { list ->
                        s.copy(
                            isLoading = false,
                            stations = list,
                            error = if (list.isEmpty()) "No se encontraron emisoras" else null,
                        )
                    },
                    onFailure = {
                        s.copy(isLoading = false, error = "Sin conexión con el directorio de emisoras")
                    },
                )
            }
        }
    }
}
