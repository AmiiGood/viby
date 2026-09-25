package com.sweetcode.viby.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.RemoveCircleOutline
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.sweetcode.viby.model.Playlist
import com.sweetcode.viby.model.Song
import com.sweetcode.viby.ui.components.ConfirmarBorradoDeLista
import com.sweetcode.viby.ui.components.DialogoDeLista
import com.sweetcode.viby.ui.components.SongRow
import com.sweetcode.viby.ui.components.VibyTopBar
import com.sweetcode.viby.ui.theme.rememberVibyPalette
import java.io.File

/**
 * Una lista de reproducción, con su portada y su nombre.
 *
 * Tiene pantalla propia y no una vista más de la biblioteca porque una lista es
 * algo que el usuario hizo: se le pone nombre, foto y se edita.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistScreen(
    lista: Playlist,
    canciones: List<Song>,
    currentId: String?,
    favorites: Set<String>,
    onBack: () -> Unit,
    onPlay: (List<Song>, Int) -> Unit,
    onShuffle: (List<Song>) -> Unit,
    onToggleFavorite: (String) -> Unit,
    onEditar: (nombre: String, descripcion: String) -> Unit,
    onPortada: (Uri) -> Unit,
    onQuitar: (String) -> Unit,
    onBorrar: () -> Unit,
) {
    var editando by remember { mutableStateOf(false) }
    var confirmandoBorrado by remember { mutableStateOf(false) }
    val portadaUri = lista.portada?.let { Uri.fromFile(File(it)) }
    val paleta = rememberVibyPalette(songUri = null, artworkUri = portadaUri)

    val selector = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri -> uri?.let(onPortada) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            VibyTopBar(title = "", onBack = onBack) {
                MenuDeLista(
                    onEditar = { editando = true },
                    onCambiarPortada = {
                        selector.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    onBorrar = { confirmandoBorrado = true },
                )
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            item {
                Cabecera(
                    lista = lista,
                    portada = lista.portada,
                    total = canciones.size,
                    acento = paleta.acento,
                    onPlay = { if (canciones.isNotEmpty()) onPlay(canciones, 0) },
                    onShuffle = { if (canciones.isNotEmpty()) onShuffle(canciones) },
                )
            }
            if (canciones.isEmpty()) {
                item {
                    Text(
                        "Esta lista está vacía.\nMantén pulsada una canción para añadirla.",
                        modifier = Modifier.fillMaxWidth().padding(32.dp),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(canciones, key = { it.id }) { song ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SongRow(
                        song = song,
                        isCurrent = song.id == currentId,
                        isFavorite = song.id in favorites,
                        onClick = { onPlay(canciones, canciones.indexOf(song)) },
                        onToggleFavorite = { onToggleFavorite(song.id) },
                        modifier = Modifier.weight(1f).padding(start = 12.dp),
                    )
                    IconButton(onClick = { onQuitar(song.id) }) {
                        Icon(
                            Icons.Rounded.RemoveCircleOutline,
                            contentDescription = "Quitar de la lista",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }

    if (editando) {
        DialogoDeLista(
            titulo = "Editar lista",
            nombreInicial = lista.nombre,
            descripcionInicial = lista.descripcion,
            onGuardar = { nombre, desc -> editando = false; onEditar(nombre, desc) },
            onCerrar = { editando = false },
        )
    }
    if (confirmandoBorrado) {
        ConfirmarBorradoDeLista(
            nombre = lista.nombre,
            onBorrar = { confirmandoBorrado = false; onBorrar() },
            onCerrar = { confirmandoBorrado = false },
        )
    }
}

@Composable
private fun Cabecera(
    lista: Playlist,
    portada: String?,
    total: Int,
    acento: Color,
    onPlay: () -> Unit,
    onShuffle: () -> Unit,
) {
    Box(
        Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(0f to acento.copy(alpha = 0.22f), 1f to Color.Transparent)
            )
            .padding(horizontal = 24.dp, vertical = 8.dp),
    ) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            PortadaDeLista(portada, Modifier.size(180.dp))
            Spacer(Modifier.height(16.dp))
            Text(
                text = lista.nombre,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (lista.descripcion.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = lista.descripcion,
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = if (total == 1) "1 canción" else "$total canciones",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = onPlay,
                    enabled = total > 0,
                    colors = ButtonDefaults.buttonColors(containerColor = acento),
                ) {
                    Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Reproducir")
                }
                OutlinedButton(onClick = onShuffle, enabled = total > 0) {
                    Icon(Icons.Rounded.Shuffle, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Aleatorio")
                }
            }
        }
    }
}

/** La portada, o un marcador con nota musical mientras no se ponga ninguna. */
@Composable
fun PortadaDeLista(portada: String?, modifier: Modifier = Modifier) {
    Box(
        modifier.clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        if (portada != null) {
            AsyncImage(
                model = File(portada),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Icon(
                Icons.Rounded.MusicNote,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun MenuDeLista(
    onEditar: () -> Unit,
    onCambiarPortada: () -> Unit,
    onBorrar: () -> Unit,
) {
    var abierto by remember { mutableStateOf(false) }
    IconButton(onClick = { abierto = true }) {
        Icon(Icons.Rounded.MoreVert, contentDescription = "Opciones de la lista")
    }
    DropdownMenu(expanded = abierto, onDismissRequest = { abierto = false }) {
        DropdownMenuItem(
            text = { Text("Editar nombre") },
            leadingIcon = { Icon(Icons.Rounded.Edit, contentDescription = null) },
            onClick = { abierto = false; onEditar() },
        )
        DropdownMenuItem(
            text = { Text("Cambiar portada") },
            leadingIcon = { Icon(Icons.Rounded.Image, contentDescription = null) },
            onClick = { abierto = false; onCambiarPortada() },
        )
        DropdownMenuItem(
            text = { Text("Borrar lista") },
            leadingIcon = { Icon(Icons.Rounded.Delete, contentDescription = null) },
            onClick = { abierto = false; onBorrar() },
        )
    }
}
