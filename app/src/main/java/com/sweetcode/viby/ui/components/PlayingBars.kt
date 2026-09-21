package com.sweetcode.viby.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Las barritas de nivel que marcan qué canción está sonando.
 *
 * Dicen dos cosas a la vez sin ocupar texto: cuál es la pista actual y si está
 * sonando o en pausa — al pausar, las barras se quedan quietas.
 *
 * Las cuatro animaciones se declaran siempre, aunque [animando] sea falso: llamar
 * a un composable dentro de una condición cambiaría el número de llamadas entre
 * recomposiciones, que es justo lo que Compose no admite.
 */
@Composable
fun PlayingBars(
    color: Color,
    modifier: Modifier = Modifier,
    animando: Boolean = true,
) {
    val transicion = rememberInfiniteTransition(label = "olita")
    val vivas = List(4) { i ->
        transicion.animateFloat(
            initialValue = 0.28f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 420 + i * 130, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "barra$i",
        ).value
    }
    // En pausa se congelan a alturas fijas, para que se siga leyendo como nivel.
    val alturas = if (animando) vivas else listOf(0.5f, 0.85f, 0.35f, 0.7f)

    Row(
        modifier = modifier.height(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.5.dp),
    ) {
        alturas.forEach { h ->
            androidx.compose.foundation.layout.Box(
                Modifier
                    .width(2.5.dp)
                    .fillMaxHeight(h)
                    .clip(RoundedCornerShape(2.dp))
                    .background(color)
            )
        }
    }
}
