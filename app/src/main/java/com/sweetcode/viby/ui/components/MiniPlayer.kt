package com.sweetcode.viby.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.sweetcode.viby.model.Song

/**
 * Barra de reproducción compacta, anclada abajo.
 *
 * La usan tanto la biblioteca como la pantalla de Radio, que necesitan cosas
 * distintas: una emisora no tiene carátula embebida (se le pasa [artworkUrl]),
 * no tiene duración ([progress] nulo) y no tiene "siguiente" ([onNext] nulo).
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
    acento: Color = MaterialTheme.colorScheme.primary,
    sobreAcento: Color = MaterialTheme.colorScheme.onPrimary,
    dragModifier: Modifier = Modifier,
) {
    Surface(
        color = Color.White.copy(alpha = 0.10f),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.09f)),
        modifier = dragModifier.padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Column(modifier = Modifier.clickable(onClick = onExpand)) {
            if (progress != null) LinearProgressIndicator(
                progress = { progress.coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(2.dp),
                color = acento,
                trackColor = Color.White.copy(alpha = 0.12f),
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val artModifier = Modifier.size(58.dp).clip(RoundedCornerShape(10.dp))
                if (artworkUrl != null) {
                    AsyncImage(
                        model = artworkUrl,
                        contentDescription = null,
                        modifier = artModifier.fillMaxSize(),
                    )
                } else {
                    AlbumArt(uri = song.uri, modifier = artModifier)
                }
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(song.title, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                    Text(song.artist, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Surface(shape = CircleShape, color = acento, modifier = Modifier.size(38.dp)) {
                    IconButton(onClick = onPlayPause) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = if (isPlaying) "Pausar" else "Reproducir",
                            tint = sobreAcento,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
                if (onNext != null) {
                    IconButton(onClick = onNext) {
                        Icon(Icons.Rounded.SkipNext, contentDescription = "Siguiente",
                            tint = MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
        }
    }
}
