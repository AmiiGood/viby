package com.sweetcode.viby.ui.theme

import android.graphics.Bitmap
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.ColorUtils
import androidx.palette.graphics.Palette

/**
 * Los colores que la app toma de la carátula que está sonando.
 *
 * El resto del tema (texto, superficies) es fijo: la legibilidad no se negocia con
 * la portada de turno. Lo que sí cambia es el acento y el tinte del fondo.
 *
 * Equivale a los modos de la colección Color en Figma: [Marca] es el modo del mismo
 * nombre, el respaldo para cuando no hay carátula de la que extraer nada.
 */
@Immutable
data class VibyPalette(
    val acento: Color,
    val acentoSuave: Color,
    val sobreAcento: Color,
    val fondoInicio: Color,
    val fondoMedio: Color,
    val fondoFin: Color,
) {
    companion object {
        /** Sin carátula: la identidad azul que ya tenía la app. */
        val Marca = VibyPalette(
            acento = VibyAccent,
            acentoSuave = VibyAccent.copy(alpha = 0.18f),
            sobreAcento = Color(0xFF0C0709),
            fondoInicio = VibySurface,
            fondoMedio = VibyBackground,
            fondoFin = VibyBackground,
        )
    }
}

val LocalVibyPalette = staticCompositionLocalOf { VibyPalette.Marca }

/**
 * Deriva una paleta de una carátula.
 *
 * Palette devuelve el color tal cual sale de la imagen, y eso no sirve directamente:
 * una portada apagada da un acento que no se ve sobre fondo oscuro, y una chillona
 * da un fondo que compite con el texto. Por eso el acento se fuerza a un rango de
 * luminosidad legible y el fondo se hunde a casi negro.
 */
fun paletteFrom(bmp: Bitmap): VibyPalette {
    val p = runCatching { Palette.from(bmp).clearFilters().generate() }.getOrNull()
        ?: return VibyPalette.Marca

    val crudo = p.getVibrantColor(
        p.getLightVibrantColor(
            p.getDominantColor(VibyAccent.toArgb())
        )
    )

    val acento = Color(ajustar(crudo, luzMin = 0.55f, luzMax = 0.72f, satMin = 0.45f))
    val base = p.getDarkMutedColor(p.getMutedColor(crudo))

    return VibyPalette(
        acento = acento,
        acentoSuave = acento.copy(alpha = 0.18f),
        // Texto sobre el acento: casi negro, porque el acento siempre es claro.
        sobreAcento = Color(ajustar(crudo, luzMin = 0.06f, luzMax = 0.10f, satMin = 0.20f)),
        fondoInicio = Color(ajustar(base, luzMin = 0.14f, luzMax = 0.20f, satMin = 0.25f)),
        fondoMedio = Color(ajustar(base, luzMin = 0.07f, luzMax = 0.10f, satMin = 0.20f)),
        fondoFin = Color(ajustar(base, luzMin = 0.03f, luzMax = 0.05f, satMin = 0.15f)),
    )
}

/** Mete un color en una franja de luminosidad y saturación mínima, conservando el tono. */
private fun ajustar(color: Int, luzMin: Float, luzMax: Float, satMin: Float): Int {
    val hsl = FloatArray(3)
    ColorUtils.colorToHSL(color, hsl)
    hsl[1] = hsl[1].coerceAtLeast(satMin)
    hsl[2] = hsl[2].coerceIn(luzMin, luzMax)
    return ColorUtils.HSLToColor(hsl)
}
