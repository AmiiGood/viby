package com.sweetcode.viby.radio

/** Temas de noticias que el usuario puede activar para el DJ de Viby FM. */
enum class NewsTopic(val id: String, val label: String, val query: String) {
    TECNOLOGIA("tech", "Tecnología", "tecnología"),
    CIENCIA("science", "Ciencia", "ciencia"),
    CULTURA("culture", "Cultura", "cultura"),
    DEPORTES("sports", "Deportes", "deportes"),
    NEGOCIOS("business", "Negocios", "negocios economía"),
    MUNDO("world", "Mundo", "internacional mundo"),
    ENTRETENIMIENTO("show", "Entretenimiento", "entretenimiento espectáculos"),
    POLITICA("politics", "Política", "política");

    companion object {
        fun fromId(id: String): NewsTopic? = entries.firstOrNull { it.id == id }
    }
}

/** Un titular crudo tomado del RSS antes de convertirlo en guion. */
data class Headline(
    val topic: NewsTopic,
    val title: String,
    val source: String,
)

/**
 * Un segmento listo para que el DJ lo lea entre canciones: el guion con
 * personalidad + los datos de la noticia por si se quieren mostrar.
 */
data class NewsSegment(
    val topicId: String,
    val topicLabel: String,
    val script: String,
    val headline: String,
    val source: String,
)
