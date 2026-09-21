package com.sweetcode.viby.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.sweetcode.viby.model.Song

/** Segundos que tarda el disco en dar una vuelta. */
private const val SEGUNDOS_POR_VUELTA = 8f

/**
 * Barra de reproducción compacta, con la carátula como disco que gira.
 *
 * El giro no es un adorno: es el indicador de estado más barato que hay. Girando
 * = sonando, quieto = en pausa, sin necesidad de mirar el botón.
 *
 * La rotación se lleva con un [Animatable] y no con una transición infinita
 * porque al pausar tiene que **quedarse donde está**, no volver a cero ni saltar.
 * Al reanudar sigue desde ese mismo ángulo.
 */
@Composable
fun MiniPlayer(
    song: Song,
    isPlaying: Boolean,
    progress: Float?,
    onExpand: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: (() -> Unit)? = null,
    artworkUrl: String? = null,
    dragModifier: Modifier = Modifier,
) {
    val angulo = remember { Animatable(0f) }
    LaunchedEffect(isPlaying) {
        if (isPlaying) {
            // Un objetivo muy lejano con avance lineal: velocidad constante y,
            // al cancelarse el efecto en la pausa, se detiene en el ángulo actual.
            val vueltas = 100_000f
            angulo.animateTo(
                targetValue = angulo.value + 360f * vueltas,
                animationSpec = tween(
                    durationMillis = (vueltas * SEGUNDOS_POR_VUELTA * 1000).toInt(),
                    easing = LinearEasing,
                ),
            )
        }
    }

    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(20.dp),
        tonalElevation = 4.dp,
        modifier = dragModifier.padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        Row(
            modifier = Modifier
                .clickable(onClick = onExpand)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(contentAlignment = Alignment.Center) {
                val discoModifier = Modifier
                    .size(52.dp)
                    .rotate(angulo.value)
                    .clip(CircleShape)
                if (artworkUrl != null) {
                    AsyncImage(
                        model = artworkUrl,
                        contentDescription = null,
                        modifier = discoModifier.fillMaxSize(),
                    )
                } else {
                    AlbumArt(uri = song.uri, modifier = discoModifier)
                }
                // El agujero central, que es lo que lo hace leer como disco.
                Box(
                    Modifier
                        .size(15.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surface)
                )
            }

            Spacer(Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = song.title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = song.artist,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                // Línea de avance fina, bajo el texto. Sin duración no se dibuja.
                if (progress != null) {
                    Spacer(Modifier.height(7.dp))
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(3.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(Color.White.copy(alpha = 0.12f))
                    ) {
                        Box(
                            Modifier
                                .fillMaxWidth(progress.coerceIn(0f, 1f))
                                .fillMaxSize()
                                .clip(RoundedCornerShape(2.dp))
                                .background(MaterialTheme.colorScheme.primary)
                        )
                    }
                }
            }

            IconButton(onClick = onPlayPause) {
                Icon(
                    imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = if (isPlaying) "Pausar" else "Reproducir",
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
            if (onNext != null) {
                IconButton(onClick = onNext) {
                    Icon(
                        Icons.Rounded.SkipNext,
                        contentDescription = "Siguiente",
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}
