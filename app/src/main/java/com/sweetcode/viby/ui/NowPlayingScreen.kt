package com.sweetcode.viby.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import com.sweetcode.viby.model.Song
import coil.compose.AsyncImage
import com.sweetcode.viby.ui.components.AlbumArt
import com.sweetcode.viby.ui.components.AudioCover

@Composable
fun NowPlayingScreen(
    song: Song,
    state: PlayerUiState,
    isFavorite: Boolean,
    onClose: () -> Unit,
    onOpenQueue: () -> Unit,
    onOpenEqualizer: () -> Unit,
    onToggleFavorite: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeek: (Long) -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeat: () -> Unit,
) {
    // Una emisora no tiene caratula embebida que extraer de su URL: la portada
    // la resuelve el servicio y llega ya como fichero en artworkUri.
    val artModel: Any = state.artworkUri?.takeIf { state.currentStation != null }
        ?: AudioCover(song.uri)

    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Fondo desenfocado con la carátula
        AsyncImage(
            model = artModel,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize().blur(60.dp),
        )
        // Velo oscuro para legibilidad
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    listOf(Color.Black.copy(alpha = 0.45f), Color.Black.copy(alpha = 0.85f))
                )
            )
        )

        Column(
            modifier = Modifier.fillMaxSize().statusBarsPadding().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Barra superior
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onClose) {
                    Icon(
                        Icons.Rounded.KeyboardArrowDown,
                        contentDescription = "Cerrar",
                        tint = Color.White,
                    )
                }
                Text(
                    text = "Reproduciendo",
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                )
                IconButton(onClick = onToggleFavorite) {
                    Icon(
                        imageVector = if (isFavorite) Icons.Rounded.Favorite
                        else Icons.Rounded.FavoriteBorder,
                        contentDescription = if (isFavorite) "Quitar de favoritos"
                        else "Agregar a favoritos",
                        tint = if (isFavorite) MaterialTheme.colorScheme.tertiary else Color.White,
                    )
                }
                IconButton(onClick = onOpenEqualizer) {
                    Icon(
                        Icons.Rounded.Equalizer,
                        contentDescription = "Ecualizador",
                        tint = Color.White,
                    )
                }
                IconButton(onClick = onOpenQueue) {
                    Icon(
                        Icons.AutoMirrored.Rounded.QueueMusic,
                        contentDescription = "Cola",
                        tint = Color.White,
                    )
                }
            }

            Spacer(Modifier.weight(1f))

            // Carátula grande
            val artModifier = Modifier
                .fillMaxWidth(0.82f)
                .aspectRatio(1f)
                .clip(RoundedCornerShape(20.dp))
            if (state.currentStation != null) {
                AsyncImage(
                    model = artModel,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = artModifier.background(MaterialTheme.colorScheme.surfaceVariant),
                )
            } else {
                AlbumArt(uri = song.uri, modifier = artModifier)
            }

            Spacer(Modifier.height(32.dp))

            // Título / artista
            Text(
                text = song.title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = song.artist,
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White.copy(alpha = 0.7f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(20.dp))

            // Un stream en vivo no tiene duracion ni permite buscar dentro.
            if (state.currentStation != null) {
                LiveIndicator()
            } else {
                // Barra de progreso arrastrable
                SeekBar(
                    positionMs = state.positionMs,
                    durationMs = state.durationMs,
                    onSeek = onSeek,
                )
            }

            Spacer(Modifier.height(12.dp))

            // Controles
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onToggleShuffle) {
                    Icon(
                        Icons.Rounded.Shuffle,
                        contentDescription = "Aleatorio",
                        tint = if (state.shuffleEnabled) MaterialTheme.colorScheme.primary
                        else Color.White.copy(alpha = 0.7f),
                    )
                }
                IconButton(onClick = onPrevious) {
                    Icon(
                        Icons.Rounded.SkipPrevious,
                        contentDescription = "Anterior",
                        tint = Color.White,
                        modifier = Modifier.size(36.dp),
                    )
                }
                // Botón play/pausa grande
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(72.dp),
                ) {
                    IconButton(onClick = onPlayPause) {
                        Icon(
                            imageVector = if (state.isPlaying) Icons.Rounded.Pause
                            else Icons.Rounded.PlayArrow,
                            contentDescription = if (state.isPlaying) "Pausar" else "Reproducir",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(40.dp),
                        )
                    }
                }
                IconButton(onClick = onNext) {
                    Icon(
                        Icons.Rounded.SkipNext,
                        contentDescription = "Siguiente",
                        tint = Color.White,
                        modifier = Modifier.size(36.dp),
                    )
                }
                IconButton(onClick = onCycleRepeat) {
                    Icon(
                        imageVector = if (state.repeatMode == Player.REPEAT_MODE_ONE)
                            Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                        contentDescription = "Repetir",
                        tint = if (state.repeatMode == Player.REPEAT_MODE_OFF)
                            Color.White.copy(alpha = 0.7f) else MaterialTheme.colorScheme.primary,
                    )
                }
            }

            Spacer(Modifier.weight(1f))
        }
    }
}

@Composable
private fun SeekBar(positionMs: Long, durationMs: Long, onSeek: (Long) -> Unit) {
    var scrubbing by remember { mutableStateOf(false) }
    var scrubValue by remember { mutableStateOf(0f) }

    val duration = durationMs.coerceAtLeast(1L)
    val sliderValue = if (scrubbing) scrubValue
    else (positionMs.toFloat() / duration).coerceIn(0f, 1f)

    Column(Modifier.fillMaxWidth()) {
        Slider(
            value = sliderValue,
            onValueChange = {
                scrubbing = true
                scrubValue = it
            },
            onValueChangeFinished = {
                onSeek((scrubValue * duration).toLong())
                scrubbing = false
            },
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = Color.White.copy(alpha = 0.25f),
            ),
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            val shownPosition = if (scrubbing) (scrubValue * duration).toLong() else positionMs
            Text(
                formatDuration(shownPosition),
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = 0.7f),
            )
            Text(
                formatDuration(durationMs),
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = 0.7f),
            )
        }
    }
}

/** Sustituye a la barra de progreso cuando lo que suena es una emisora. */
@Composable
private fun LiveIndicator() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = "EN VIVO",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}
