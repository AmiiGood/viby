package com.sweetcode.viby.playback

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Temporizador de apagado: para la música sola al cabo de un rato.
 *
 * Vive a nivel de proceso, como el estado de descargas, y no dentro de un
 * ViewModel: quien lo programa suele dejar el móvil y salir de la app, y el
 * temporizador tiene que seguir corriendo con la reproducción, no con la
 * pantalla. Quien lo cumple es [PlaybackService], que es quien tiene el
 * reproductor.
 */
object SleepTimer {

    /** Instante (epoch ms) en que debe pararse, o null si no hay cuenta atrás. */
    private val _hasta = MutableStateFlow<Long?>(null)
    val hasta: StateFlow<Long?> = _hasta.asStateFlow()

    /** Parar al acabar lo que suena ahora, sin mirar el reloj. */
    private val _alTerminarPista = MutableStateFlow(false)
    val alTerminarPista: StateFlow<Boolean> = _alTerminarPista.asStateFlow()

    /** Programa la parada dentro de [minutos]. Sustituye a lo que hubiera. */
    fun programar(minutos: Int) {
        _alTerminarPista.value = false
        _hasta.value = System.currentTimeMillis() + minutos * 60_000L
    }

    /** Programa la parada para cuando termine la canción en curso. */
    fun alTerminarLaCancion() {
        _hasta.value = null
        _alTerminarPista.value = true
    }

    fun cancelar() {
        _hasta.value = null
        _alTerminarPista.value = false
    }

    /** Minutos y segundos que faltan, o null si no hay cuenta atrás. */
    fun restante(ahora: Long = System.currentTimeMillis()): Long? =
        _hasta.value?.let { (it - ahora).coerceAtLeast(0L) }
}
