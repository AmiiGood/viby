package com.sweetcode.viby.ui

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sweetcode.viby.update.Actualizacion
import com.sweetcode.viby.update.UpdateRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** En qué punto está el aviso de actualización. */
data class UpdateUiState(
    /** La versión nueva, si la hay y aún no se ha descartado. */
    val disponible: Actualizacion? = null,
    val descargando: Boolean = false,
    /** De 0 a 1, o null mientras no se sepa el tamaño. */
    val progreso: Float? = null,
    val error: String? = null,
    /** Intent que la pantalla debe lanzar: instalar, o pedir el permiso. */
    val lanzar: Intent? = null,
    /** Cierto cuando lo que se lanza son los ajustes de permiso, no el instalador. */
    val esPermiso: Boolean = false,
)

class UpdateViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = UpdateRepository(app)

    private val _state = MutableStateFlow(UpdateUiState())
    val state: StateFlow<UpdateUiState> = _state.asStateFlow()

    val versionInstalada: String get() = repo.versionInstalada()

    init {
        buscar()
    }

    fun buscar(forzar: Boolean = false) {
        viewModelScope.launch {
            repo.buscar(forzar)?.let { act -> _state.update { it.copy(disponible = act) } }
        }
    }

    /** "Ahora no": se calla con esta versión durante unos días. */
    fun posponer() {
        _state.value.disponible?.let { repo.posponer(it.version) }
        _state.value = UpdateUiState()
    }

    /**
     * Descarga la APK y se la entrega al instalador del sistema.
     *
     * Si aún no se ha autorizado a Viby como origen de instalación, se lleva
     * primero a esos ajustes: el instalador fallaría sin decir por qué.
     */
    fun actualizar() {
        val act = _state.value.disponible ?: return
        if (!repo.puedeInstalar()) {
            _state.update { it.copy(lanzar = repo.intentDePermiso(), esPermiso = true) }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(descargando = true, progreso = 0f, error = null) }
            val apk = repo.descargar(act) { p -> _state.update { it.copy(progreso = p) } }
            if (apk == null) {
                _state.update {
                    it.copy(
                        descargando = false,
                        progreso = null,
                        error = "No se pudo descargar. Revisa tu conexión e inténtalo otra vez.",
                    )
                }
                return@launch
            }
            _state.update {
                it.copy(
                    descargando = false,
                    progreso = null,
                    lanzar = repo.intentDeInstalacion(apk),
                    esPermiso = false,
                )
            }
        }
    }

    /**
     * La pantalla avisa de que ya lanzó el intent, para no repetirlo al recomponer.
     *
     * El reintento tras dar el permiso lo hace ella, que es quien se entera de
     * cuándo se vuelve de los ajustes; aquí el permiso todavía no estaría puesto.
     */
    fun lanzado() {
        _state.update { it.copy(lanzar = null, esPermiso = false) }
    }
}
