package com.sweetcode.viby.assistant

/** Comandos de voz que entiende Viby. */
sealed class VoiceCommand {
    object Next : VoiceCommand()
    object Previous : VoiceCommand()
    object Pause : VoiceCommand()
    object Resume : VoiceCommand()
    object WhatSong : VoiceCommand()
    object VolumeUp : VoiceCommand()
    object VolumeDown : VoiceCommand()
    data class Play(val query: String) : VoiceCommand()
    object Unknown : VoiceCommand()
}

/** Interpreta el texto reconocido a un comando (español). */
fun parseVoiceCommand(rawText: String): VoiceCommand {
    val t = " ${rawText.lowercase().trim()} "

    val playQuery = extractPlayQuery(rawText.lowercase())
    return when {
        playQuery != null -> VoiceCommand.Play(playQuery)
        contains(t, "siguiente", "próxima", "proxima", "adelante", "salta") -> VoiceCommand.Next
        contains(t, "anterior", "previa", "regresa", "atrás", "atras", "devuelve") -> VoiceCommand.Previous
        contains(t, "pausa", "pausar", "detén", "deten", "detener", "para") -> VoiceCommand.Pause
        contains(t, "reanuda", "continúa", "continua", "sigue", "play", "reproduce") -> VoiceCommand.Resume
        contains(t, "qué canción", "que cancion", "qué suena", "que suena", "qué estoy", "que estoy") -> VoiceCommand.WhatSong
        contains(t, "sube") && contains(t, "volumen") -> VoiceCommand.VolumeUp
        contains(t, "baja") && contains(t, "volumen") -> VoiceCommand.VolumeDown
        else -> VoiceCommand.Unknown
    }
}

private fun contains(haystack: String, vararg words: String): Boolean =
    words.any { haystack.contains(" $it") || haystack.contains("$it ") }

/** Si dice "reproduce X" / "pon X" / "escucha X" devuelve X (la canción a buscar). */
private fun extractPlayQuery(text: String): String? {
    for (kw in listOf("reproduce ", "pon ", "escucha ", "quiero escuchar ")) {
        val i = text.indexOf(kw)
        if (i >= 0) {
            val q = text.substring(i + kw.length).trim()
            // "reproduce" a secas (sin nada después) = reanudar, no buscar.
            if (q.isNotBlank() && q.length >= 2) return q
        }
    }
    return null
}
