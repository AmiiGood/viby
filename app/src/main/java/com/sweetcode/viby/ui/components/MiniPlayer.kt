package com.sweetcode.viby.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
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

private const val SEGUNDOS_POR_VUELTA = 8f

private val ALTO_TARJETA = 62.dp
private val SOLAPE = 10.dp          // cuánto se montan las dos tarjetas
private val DESFASE = 22.dp         // cuánto se desplazan una respecto a otra
private val DISCO = 84.dp
private val DISCO_X = 2.dp

/**
 * Barra de reproducción compacta: dos tarjetas desfasadas con el disco cruzando
 * la unión entre ambas.
 *
 * Arriba van título, artista y avance; abajo los controles. El disco se dibuja el
 * último para quedar por encima de las dos y sobresalir de ambas, que es lo que
 * da la sensación de profundidad.
 *
 * El giro no es adorno: girando = sonando, quieto = en pausa, sin mirar el botón.
 * Va con un [Animatable] y no con una transición infinita porque al pausar tiene
 * que quedarse en el ángulo actual, no volver a cero.
 */
@Composable
fun MiniPlayer(
    song: Song,
    isPlaying: Boolean,
    progress: Float?,
    onExpand: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: (() -> Unit)? = null,
    onPrevious: (() -> Unit)? = null,
    artworkUrl: String? = null,
    dragModifier: Modifier = Modifier,
) {
    val angulo = remember { Animatable(0f) }
    LaunchedEffect(isPlaying) {
        if (isPlaying) {
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

    // Con un círculo el margen seguro es el diámetro entero: a media altura es
    // donde más se mete, y ahí es justo donde cae la línea del artista.
    val margenDisco = DISCO_X + DISCO + 10.dp

    Box(
        modifier = dragModifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 6.dp)
            .height(ALTO_TARJETA * 2 - SOLAPE)
            .clickable(onClick = onExpand),
    ) {
        // --- Tarjeta de arriba: qué suena ---
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(20.dp),
            tonalElevation = 2.dp,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .fillMaxWidth()
                .padding(start = DESFASE)
                .height(ALTO_TARJETA),
        ) {
            Column(
                modifier = Modifier
                    .padding(start = margenDisco - DESFASE, end = 18.dp, bottom = SOLAPE)
                    .fillMaxSize(),
                verticalArrangement = Arrangement.Center,
            ) {
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
                if (progress != null) {
                    Spacer(Modifier.height(6.dp))
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
        }

        // --- Tarjeta de abajo: controles ---
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(20.dp),
            tonalElevation = 4.dp,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(end = DESFASE)
                .height(ALTO_TARJETA),
        ) {
            Row(
                modifier = Modifier.padding(start = margenDisco, end = 8.dp).fillMaxSize(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (onPrevious != null) {
                    IconButton(onClick = onPrevious) {
                        Icon(
                            Icons.Rounded.SkipPrevious,
                            contentDescription = "Anterior",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
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
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        // --- El disco, el último: queda encima de las dos tarjetas ---
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .offset(x = DISCO_X)
                .size(DISCO),
            contentAlignment = Alignment.Center,
        ) {
            val discoModifier = Modifier
                .fillMaxSize()
                .rotate(angulo.value)
                .clip(CircleShape)
            if (artworkUrl != null) {
                AsyncImage(model = artworkUrl, contentDescription = null, modifier = discoModifier)
            } else {
                AlbumArt(uri = song.uri, modifier = discoModifier)
            }
            // El agujero es lo que lo hace leer como disco y no como recorte.
            Box(
                Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface)
            )
        }
    }
}
