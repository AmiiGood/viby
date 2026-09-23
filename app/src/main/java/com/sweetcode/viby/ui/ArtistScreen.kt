package com.sweetcode.viby.ui

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sweetcode.viby.data.Artista
import com.sweetcode.viby.model.Song
import com.sweetcode.viby.ui.components.FotoDeArtista
import com.sweetcode.viby.ui.components.SongRow
import com.sweetcode.viby.ui.components.VibyTopBar
import com.sweetcode.viby.ui.theme.rememberVibyPalette

/**
 * Página del artista.
 *
 * Antes era la pantalla genérica: el nombre arriba y una lista plana de canciones,
 * igual que un álbum. Con la foto ya descargada y los nombres unificados, aquí sí
 * se puede enseñar quién es y qué tienes suyo, agrupado por disco.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArtistScreen(
    artista: Artista,
    foto: Uri?,
    currentId: String?,
    favorites: Set<String>,
    onBack: () -> Unit,
    onPlay: (List<Song>, Int) -> Unit,
    onShuffle: (List<Song>) -> Unit,
    onToggleFavorite: (String) -> Unit,
) {
    val canciones = artista.canciones
    // Se respeta el orden en que vienen (artista, álbum, título), así que los
    // discos salen agrupados sin tener que reordenar nada.
    val porAlbum = remember(canciones) { canciones.groupBy { it.album } }
    val paleta = rememberVibyPalette(songUri = null, artworkUri = foto)

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { VibyTopBar(title = "", onBack = onBack) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            item {
                Cabecera(
                    artista = artista,
                    foto = foto,
                    albumes = porAlbum.size,
                    acento = paleta.acento,
                    onPlay = { onPlay(canciones, 0) },
                    onShuffle = { onShuffle(canciones) },
                )
            }
            for ((album, suyas) in porAlbum) {
                item(key = "album:$album") {
                    Text(
                        text = album,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = paleta.acento,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 4.dp),
                    )
                }
                items(suyas, key = { it.id }) { song ->
                    SongRow(
                        song = song,
                        isCurrent = song.id == currentId,
                        isFavorite = song.id in favorites,
                        // Al tocar una canción suena su artista entero desde ahí,
                        // no solo el disco: es lo que se espera de esta pantalla.
                        onClick = { onPlay(canciones, canciones.indexOf(song)) },
                        onToggleFavorite = { onToggleFavorite(song.id) },
                        modifier = Modifier.padding(horizontal = 12.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun Cabecera(
    artista: Artista,
    foto: Uri?,
    albumes: Int,
    acento: Color,
    onPlay: () -> Unit,
    onShuffle: () -> Unit,
) {
    Box(
        Modifier
            .fillMaxWidth()
            // El color sale de la foto, como en el reproductor: la pantalla se
            // siente de ese artista y no de la app.
            .background(
                Brush.verticalGradient(
                    0f to acento.copy(alpha = 0.22f),
                    1f to Color.Transparent,
                )
            )
            .padding(horizontal = 24.dp, vertical = 8.dp),
    ) {
        Column(
            Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            FotoDeArtista(foto, Modifier.size(150.dp))
            Spacer(Modifier.height(16.dp))
            Text(
                text = artista.nombre,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = resumen(artista, albumes),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = onPlay,
                    colors = ButtonDefaults.buttonColors(containerColor = acento),
                ) {
                    Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Reproducir")
                }
                OutlinedButton(onClick = onShuffle) {
                    Icon(Icons.Rounded.Shuffle, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Aleatorio")
                }
            }
        }
    }
}

/** "5 canciones · 2 álbumes", cuidando los singulares y los nombres unidos. */
private fun resumen(artista: Artista, albumes: Int): String {
    val canciones = artista.canciones.size
    val base = buildString {
        append(canciones)
        append(if (canciones == 1) " canción" else " canciones")
        if (albumes > 1) {
            append(" · ")
            append(albumes)
            append(" álbumes")
        }
    }
    if (artista.unidos.isEmpty()) return base
    return "$base · con ${artista.unidos.joinToString(", ")}"
}
