package com.sweetcode.viby.radio

import android.content.Context

/**
 * Ajustes de Viby FM guardados en SharedPreferences (privadas de la app).
 * La API key se guarda aquí también; el sandbox de Android la mantiene
 * privada a la app y nunca se sube a GitHub.
 */
class RadioSettings(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences("viby_radio", Context.MODE_PRIVATE)

    var enabled: Boolean
        get() = prefs.getBoolean(KEY_ENABLED, false)
        set(v) = prefs.edit().putBoolean(KEY_ENABLED, v).apply()

    /** Cada cuántas canciones habla el DJ. */
    var everyNSongs: Int
        get() = prefs.getInt(KEY_EVERY_N, 4).coerceIn(2, 10)
        set(v) = prefs.edit().putInt(KEY_EVERY_N, v.coerceIn(2, 10)).apply()

    var wifiOnly: Boolean
        get() = prefs.getBoolean(KEY_WIFI_ONLY, true)
        set(v) = prefs.edit().putBoolean(KEY_WIFI_ONLY, v).apply()

    var apiKey: String
        get() = prefs.getString(KEY_API, "").orEmpty()
        set(v) = prefs.edit().putString(KEY_API, v.trim()).apply()

    /** IDs de temas activos (NewsTopic.id). */
    var topics: Set<String>
        get() = prefs.getStringSet(KEY_TOPICS, DEFAULT_TOPICS) ?: DEFAULT_TOPICS
        set(v) = prefs.edit().putStringSet(KEY_TOPICS, v).apply()

    var lastFetchEpochMs: Long
        get() = prefs.getLong(KEY_LAST_FETCH, 0L)
        set(v) = prefs.edit().putLong(KEY_LAST_FETCH, v).apply()

    fun activeTopics(): List<NewsTopic> = topics.mapNotNull { NewsTopic.fromId(it) }

    fun hasApiKey(): Boolean = apiKey.isNotBlank()

    companion object {
        private const val KEY_ENABLED = "enabled"
        private const val KEY_EVERY_N = "every_n"
        private const val KEY_WIFI_ONLY = "wifi_only"
        private const val KEY_API = "api_key"
        private const val KEY_TOPICS = "topics"
        private const val KEY_LAST_FETCH = "last_fetch"
        private val DEFAULT_TOPICS = setOf(NewsTopic.TECNOLOGIA.id, NewsTopic.MUNDO.id)
    }
}
