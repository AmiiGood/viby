package com.sweetcode.viby.assistant

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Estado visible del asistente (para la UI). */
data class AssistantState(
    val enabled: Boolean = false,
    val status: String = "Apagado",
)

/** Punto central para encender/apagar el asistente y observar su estado. */
object AssistantController {

    private val _state = MutableStateFlow(AssistantState())
    val state: StateFlow<AssistantState> = _state.asStateFlow()

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isEnabled(context: Context): Boolean = prefs(context).getBoolean(KEY_ENABLED, false)
    fun accessKey(context: Context): String = prefs(context).getString(KEY_ACCESS, "").orEmpty()

    fun setAccessKey(context: Context, key: String) {
        prefs(context).edit().putString(KEY_ACCESS, key.trim()).apply()
    }

    fun setEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_ENABLED, enabled).apply()
        _state.value = _state.value.copy(enabled = enabled)
        val intent = Intent(context, AssistantService::class.java)
        if (enabled) {
            ContextCompat.startForegroundService(context, intent)
        } else {
            context.stopService(intent)
            updateStatus("Apagado")
        }
    }

    /** Lo llama el servicio para reflejar lo que está pasando. */
    fun updateStatus(status: String) {
        _state.value = _state.value.copy(status = status)
    }

    fun syncFrom(context: Context) {
        _state.value = _state.value.copy(enabled = isEnabled(context))
    }

    private const val PREFS = "viby_assistant"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_ACCESS = "access_key"
}
