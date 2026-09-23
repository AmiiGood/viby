package com.sweetcode.viby.update

/**
 * Comparación de versiones tipo "1.6" o "v1.6.1".
 *
 * Comparar como texto no vale: "1.10" es posterior a "1.9" pero va antes por
 * orden alfabético, y sería justo la versión que nadie recibiría.
 */
internal fun esMasNueva(remota: String, instalada: String): Boolean {
    val a = partes(remota)
    val b = partes(instalada)
    if (a.isEmpty()) return false
    for (i in 0 until maxOf(a.size, b.size)) {
        val x = a.getOrElse(i) { 0 }
        val y = b.getOrElse(i) { 0 }
        if (x != y) return x > y
    }
    return false
}

/** Descompone "v1.6.1" en [1, 6, 1], ignorando lo que no sea número. */
private fun partes(v: String): List<Int> =
    v.trim().removePrefix("v").removePrefix("V")
        .split('.', '-', '+')
        .map { trozo -> trozo.takeWhile { it.isDigit() } }
        .takeWhile { it.isNotEmpty() }
        .map { it.toInt() }

/**
 * Deja las notas de la release en algo legible dentro de un diálogo.
 *
 * Vienen en Markdown, y ahí no hay quien lo renderice: los "###" y los "**" se
 * verían tal cual. Se quitan las marcas y se dejan los títulos y la lista.
 */
internal fun notasLegibles(markdown: String): String =
    markdown.lineSequence()
        .map { linea ->
            linea.trim()
                .replace(Regex("^#{1,6}\\s*"), "")
                .replace(Regex("^[-*]\\s+"), "• ")
                .replace(Regex("\\*\\*(.+?)\\*\\*"), "$1")
                .replace(Regex("`(.+?)`"), "$1")
                // "[texto](url)" se queda solo con el texto.
                .replace(Regex("\\[(.+?)]\\((.+?)\\)"), "$1")
        }
        .joinToString("\n")
        .replace(Regex("\n{3,}"), "\n\n")
        .trim()
