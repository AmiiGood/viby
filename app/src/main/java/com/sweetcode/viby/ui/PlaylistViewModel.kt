package com.sweetcode.viby.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sweetcode.viby.data.M3uExporter
import com.sweetcode.viby.data.MusicRepository
import com.sweetcode.viby.data.PlaylistRepository
import com.sweetcode.viby.model.Playlist
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class PlaylistViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = PlaylistRepository(app)
    private val musica = MusicRepository(app)
    private val m3u = M3uExporter(app)

    private val _listas = MutableStateFlow<List<Playlist>>(emptyList())
    val listas: StateFlow<List<Playlist>> = _listas.asStateFlow()

    init {
        viewModelScope.launch {
            val guardadas = repo.cargar()
            // Leer el fichero tarda un momento. Si en ese hueco ya se creó algo,
            // se conserva en vez de perderse al llegar lo leído.
            _listas.value = guardadas + _listas.value
            // Las listas hechas antes de que existiera la exportación no tienen
            // fichero; se les crea al arrancar y no cuando se vuelvan a tocar.
            guardadas.filter { it.m3u == null }.forEach { sincronizarM3u(it.id) }
        }
    }

    /** Crea una lista y devuelve su id, para poder abrirla al momento. */
    fun crear(nombre: String, canciones: List<String> = emptyList()): String {
        val id = repo.nuevoId()
        aplicar(id) { it + Playlist(id = id, nombre = nombre.trim(), canciones = canciones) }
        return id
    }

    fun renombrar(id: String, nombre: String, descripcion: String) = aplicar(id) { listas ->
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
        val lista = _listas.value.firstOrNull { it.id == id }
        viewModelScope.launch {
            repo.borrarPortada(id)
            // El .m3u se borra con la lista: dejarlo suelto haría que otros
            // reproductores siguieran enseñando algo que ya no existe aquí.
            val raiz = musica.savedFolderUri()
            if (raiz != null && lista?.m3u != null) m3u.borrar(raiz, lista.m3u)
        }
        aplicar { listas -> listas.filterNot { it.id == id } }
    }

    /** Añade canciones evitando duplicados; devuelve cuántas entraron de verdad. */
    fun anadir(id: String, ids: List<String>): Int {
        val lista = _listas.value.firstOrNull { it.id == id } ?: return 0
        val nuevas = ids.filter { it !in lista.canciones }
        if (nuevas.isEmpty()) return 0
        aplicar(id) { listas ->
            listas.map { if (it.id == id) it.copy(canciones = it.canciones + nuevas) else it }
        }
        return nuevas.size
    }

    fun quitar(id: String, songId: String) = aplicar(id) { listas ->
        listas.map {
            if (it.id == id) it.copy(canciones = it.canciones.filterNot { c -> c == songId }) else it
        }
    }

    private fun aplicar(exportar: String? = null, cambio: (List<Playlist>) -> List<Playlist>) {
        val nuevas = cambio(_listas.value)
        _listas.value = nuevas
        viewModelScope.launch {
            repo.guardar(nuevas)
            exportar?.let { sincronizarM3u(it) }
        }
    }

    /**
     * Rehace el .m3u de una lista dentro de la carpeta de música.
     *
     * Se escribe en cada cambio en vez de con un botón de exportar: una copia que
     * hay que acordarse de actualizar acaba mintiendo, y el fichero es diminuto.
     */
    private suspend fun sincronizarM3u(id: String) {
        val raiz = musica.savedFolderUri() ?: return
        val lista = _listas.value.firstOrNull { it.id == id } ?: return
        val porId = musica.loadCache(raiz)?.associateBy { it.id } ?: return
        val canciones = lista.canciones.mapNotNull { porId[it] }

        val nombre = m3u.nombreDeFichero(lista.nombre)
        // Al renombrar la lista, el fichero anterior se queda huérfano.
        lista.m3u?.takeIf { it != nombre }?.let { m3u.borrar(raiz, it) }

        val escrito = m3u.exportar(raiz, nombre, lista.nombre, canciones) ?: return
        if (escrito == lista.m3u) return
        // Guardar el nombre real va aparte: pasar por aplicar() volvería a exportar.
        val actualizadas = _listas.value.map { if (it.id == id) it.copy(m3u = escrito) else it }
        _listas.value = actualizadas
        repo.guardar(actualizadas)
    }
}
