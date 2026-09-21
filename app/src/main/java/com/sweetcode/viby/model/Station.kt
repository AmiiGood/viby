package com.sweetcode.viby.model

/**
 * Una emisora de radio por internet.
 *
 * A diferencia de [Song], no tiene duración ni permite buscar dentro: es un stream
 * continuo. La UI usa eso para esconder la barra de progreso y los controles que
 * no aplican.
 */
data class Station(
    val id: String,          // stationuuid de Radio Browser
    val name: String,
    val streamUrl: String,
    val faviconUrl: String,  // vacío si la emisora no publica logo
    val country: String,
    val tags: List<String>,
    val codec: String,       // MP3, AAC...
    val bitrate: Int,        // kbps; 0 si la emisora no lo declara
) {
    /** Línea secundaria para las listas: "México · rock, pop · 128 kbps". */
    val subtitle: String
        get() = listOf(
            country,
            tags.take(3).joinToString(", "),
            if (bitrate > 0) "$bitrate kbps" else "",
        ).filter { it.isNotBlank() }.joinToString(" · ")
}
