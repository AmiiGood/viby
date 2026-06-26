package com.sweetcode.viby.download

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Estado global de descargas (vive a nivel de proceso) para que la UI lo observe
 * aunque el servicio corra en segundo plano y el usuario salga de la pantalla.
 */
object DownloadProgress {

    private val _states = MutableStateFlow<Map<String, DownloadStatus>>(emptyMap())
    val states: StateFlow<Map<String, DownloadStatus>> = _states.asStateFlow()

    private val _completed = MutableStateFlow(0)
    val completed: StateFlow<Int> = _completed.asStateFlow()

    fun update(url: String, status: DownloadStatus) {
        _states.value = _states.value + (url to status)
    }

    fun markCompleted() {
        _completed.value = _completed.value + 1
    }
}
