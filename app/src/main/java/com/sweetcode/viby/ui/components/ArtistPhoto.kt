package com.sweetcode.viby.ui.components

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.shape.CircleShape
import coil.compose.AsyncImage
import com.sweetcode.viby.data.ArtistImages
import com.sweetcode.viby.data.Artista

/**
 * Resuelve la foto del artista, descargándola la primera vez.
 *
 * Es un `produceState` y no un flujo del ViewModel porque solo interesa mientras
 * la fila (o la pantalla) está a la vista: si se sale, no hay nada que esperar.
 */
@Composable
fun recordarFotoDeArtista(artista: Artista, raiz: Uri?, imagenes: ArtistImages): Uri? {
    val foto by produceState<Uri?>(initialValue = null, artista, raiz) {
        value = raiz?.let {
            imagenes.deArtista(
                nombre = artista.nombre,
                otrosNombres = artista.unidos,
                muestra = artista.canciones.firstOrNull()?.title,
                raiz = it,
            )
        }
    }
    return foto
}

/**
 * La foto en redondo. Mientras no la haya se deja el icono de siempre, así que la
 * lista sigue funcionando sin conexión y sin esperas.
 */
@Composable
fun FotoDeArtista(foto: Uri?, modifier: Modifier = Modifier) {
    Box(
        modifier.clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        if (foto != null) {
            AsyncImage(
                model = foto,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Icon(
                Icons.Rounded.Person,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
