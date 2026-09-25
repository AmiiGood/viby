package com.sweetcode.viby.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sweetcode.viby.data.PlaylistRepository
import com.sweetcode.viby.model.Playlist
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class PlaylistViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = PlaylistRepository(app)

    private val _listas = MutableStateFlow<List<Playlist>>(emptyList())
    val listas: StateFlow<List<Playlist>> = _listas.asStateFlow()

    init {
        viewModelScope.launch { _listas.value = repo.cargar() }
    }

    /** Crea una lista y devuelve su id, para poder abrirla al momento. */
    fun crear(nombre: String, canciones: List<String> = emptyList()): String {
        val id = repo.nuevoId()
        aplicar { it + Playlist(id = id, nombre = nombre.trim(), canciones = canciones) }
        return id
    }

    fun renombrar(id: String, nombre: String, descripcion: String) = aplicar { listas ->
        listas.map {
            if (it.id == id) it.copy(nombre = nombre.trim(), descripcion = descripcion.trim())
            else it
        }
    }

    fun cambiarPortada(id: String, origen: Uri) {
        viewModelScope.launch {
            val ruta = repo.copiarPortada(id, origen) ?: return@launch
            aplicar { listas -> listas.map { if (it.id == id) it.copy(portada = ruta) else it } }
        }
    }

    fun borrar(id: String) {
        viewModelScope.launch { repo.borrarPortada(id) }
        aplicar { listas -> listas.filterNot { it.id == id } }
    }

    /** Añade canciones evitando duplicados; devuelve cuántas entraron de verdad. */
    fun anadir(id: String, ids: List<String>): Int {
        val lista = _listas.value.firstOrNull { it.id == id } ?: return 0
        val nuevas = ids.filter { it !in lista.canciones }
        if (nuevas.isEmpty()) return 0
        aplicar { listas ->
            listas.map { if (it.id == id) it.copy(canciones = it.canciones + nuevas) else it }
        }
        return nuevas.size
    }

    fun quitar(id: String, songId: String) = aplicar { listas ->
        listas.map {
            if (it.id == id) it.copy(canciones = it.canciones.filterNot { c -> c == songId }) else it
        }
    }

    private fun aplicar(cambio: (List<Playlist>) -> List<Playlist>) {
        val nuevas = cambio(_listas.value)
        _listas.value = nuevas
        viewModelScope.launch { repo.guardar(nuevas) }
    }
}
