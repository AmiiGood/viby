package com.sweetcode.viby.ui

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.sweetcode.viby.data.ArtistImages
import com.sweetcode.viby.data.Artista
import com.sweetcode.viby.model.Song
import com.sweetcode.viby.ui.components.AlbumArt
import com.sweetcode.viby.ui.components.MiniPlayer
import com.sweetcode.viby.ui.components.SongRow
import com.sweetcode.viby.ui.components.VibyTabs
import com.sweetcode.viby.ui.components.VibyTopBar
import kotlinx.coroutines.launch

private enum class Tab(val label: String, val icon: ImageVector) {
    SONGS("Canciones", Icons.Rounded.MusicNote),
    ALBUMS("Álbumes", Icons.Rounded.Album),
    ARTISTS("Artistas", Icons.Rounded.Person),
    FAVORITES("Favoritos", Icons.Rounded.Favorite),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    vm: PlayerViewModel,
    onOpenQueue: () -> Unit,
    onOpenEqualizer: () -> Unit,
    onOpenDownload: () -> Unit,
    onOpenDiscover: () -> Unit,
    onOpenRadio: () -> Unit,
    onOpenAlbum: (String) -> Unit,
    onOpenArtist: (String) -> Unit,
) {
    val songs by vm.songs.collectAsStateWithLifecycle()
    val artistas by vm.artistas.collectAsStateWithLifecycle()
    val state by vm.uiState.collectAsStateWithLifecycle()
    val favorites by vm.favorites.collectAsStateWithLifecycle()
    val queue by vm.queue.collectAsStateWithLifecycle()
    val abrirReproductor by vm.abrirReproductor.collectAsStateWithLifecycle()

    var tabIndex by rememberSaveable { mutableStateOf(0) }
    val tab = Tab.entries[tabIndex]
    var searching by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    // Referencia estable para que la lista no se recomponga con cada tick de progreso (0.5s).
    val onToggleFavorite = remember { { id: String -> vm.toggleFavorite(id) } }

    val folderPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri -> if (uri != null) vm.onFolderPicked(uri) }

    if (!state.hasFolder) {
        CenteredMessage(
            title = "Bienvenido a Viby",
            subtitle = "Elige la carpeta donde copiaste tu música para empezar.",
            buttonText = "Elegir carpeta",
            onPick = { folderPicker.launch(null) },
        )
        return
    }

    // ---- Panel Now Playing arrastrable (0 = cerrado, 1 = abierto) ----
    val scope = rememberCoroutineScope()
    val progress = remember { Animatable(0f) }
    var expandedTarget by rememberSaveable { mutableStateOf(false) }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val fullHeightPx = constraints.maxHeight.toFloat()

        // Restaura el estado abierto/cerrado (p. ej. al volver de la cola).
        LaunchedEffect(Unit) {
            if (expandedTarget) progress.snapTo(1f)
        }

        fun settle(open: Boolean) {
            expandedTarget = open
            scope.launch {
                progress.animateTo(if (open) 1f else 0f, spring(stiffness = Spring.StiffnessMediumLow))
            }
        }

        // El reproductor es una capa superpuesta, no un destino de navegación, así
        // que el sistema no sabía que había algo que cerrar y el botón atrás sacaba
        // de la app. Lo mismo con la búsqueda abierta.
        BackHandler(enabled = expandedTarget || searching) {
            when {
                expandedTarget -> settle(false)
                searching -> { query = ""; searching = false }
            }
        }

        // Radio no tiene reproductor completo: cuando pide abrirlo, vuelve aquí y
        // se despliega. La señal se consume para que no se repita al recomponer.
        LaunchedEffect(abrirReproductor) {
            if (abrirReproductor && state.currentSong != null) {
                settle(true)
                vm.reproductorYaAbierto()
            }
        }

        val dragState = rememberDraggableState { delta ->
            scope.launch {
                val newP = (progress.value - delta / fullHeightPx).coerceIn(0f, 1f)
                progress.snapTo(newP)
            }
        }
        val dragModifier = Modifier.draggable(
            state = dragState,
            orientation = Orientation.Vertical,
            onDragStopped = { velocity ->
                // velocidad negativa = hacia arriba (abrir)
                val open = velocity < -800f || (velocity <= 800f && progress.value > 0.4f)
                settle(open)
            },
        )

        // ===== Pantalla principal (pestañas) =====
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                Column {
                    VibyTopBar(
                    title = {
                        Text(
                            if (tab == Tab.SONGS) "Viby" else tab.label,
                            fontWeight = FontWeight.Bold,
                        )
                    },
                    actions = {
                        if (tab == Tab.SONGS) {
                            IconButton(onClick = {
                                if (searching) query = ""
                                searching = !searching
                            }) {
                                Icon(
                                    imageVector = if (searching) Icons.Rounded.Close else Icons.Rounded.Search,
                                    contentDescription = if (searching) "Cerrar búsqueda" else "Buscar",
                                )
                            }
                        }
                        if (!searching) {
                            IconButton(onClick = onOpenRadio) {
                                Icon(Icons.Rounded.Radio, contentDescription = "Radio")
                            }
                            IconButton(onClick = onOpenDiscover) {
                                Icon(Icons.Rounded.AutoAwesome, contentDescription = "Descubrir")
                            }
                            IconButton(onClick = onOpenDownload) {
                                Icon(Icons.Rounded.CloudDownload, contentDescription = "Descargar música")
                            }
                            IconButton(onClick = { folderPicker.launch(null) }) {
                                Icon(Icons.Rounded.FolderOpen, contentDescription = "Cambiar carpeta")
                            }
                        }
                    },
                    )
                    // El buscador va BAJO la cabecera, no dentro: un TextField mide
                    // 56dp de alto y la barra superior no da tanto, asi que se
                    // recortaba por arriba y perdia sus esquinas.
                    if (tab == Tab.SONGS && searching) {
                        SearchField(
                            query = query,
                            onQueryChange = { query = it },
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                        )
                    }
                    VibyTabs(
                        labels = Tab.entries.map { it.label },
                        selected = tabIndex,
                        onSelect = { tabIndex = it },
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }
            },
            bottomBar = {
                Column {
                    state.currentSong?.let { song ->
                        MiniPlayer(
                            song = song,
                            isPlaying = state.isPlaying,
                            // null = sin barra: emisora en vivo o duracion aun desconocida.
                            progress = if (state.durationMs > 0)
                                state.positionMs.toFloat() / state.durationMs else null,
                            // Una emisora no tiene caratula embebida que extraer: el
                            // servicio resuelve su portada (o su logo) a un fichero.
                            artworkUrl = state.artworkUri?.takeIf { state.currentStation != null || state.previewUrl != null }
                                ?.toString(),
                            onExpand = { settle(true) },
                            onPlayPause = vm::togglePlay,
                            onNext = vm::next,
                            dragModifier = dragModifier,
                        )
                    }
                }
            },
        ) { padding ->
            Box(modifier = Modifier.fillMaxSize().padding(padding)) {
                when {
                    state.isLoading && songs.isEmpty() -> Box(
                        Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) { CircularProgressIndicator() }

                    songs.isEmpty() -> CenteredMessage(
                        title = "No se encontró música",
                        subtitle = "Esa carpeta no tiene archivos de audio. Prueba con otra.",
                        buttonText = "Elegir otra carpeta",
                        onPick = { folderPicker.launch(null) },
                    )

                    else -> when (tab) {
                        Tab.SONGS -> {
                            val displayed = remember(songs, query) {
                                if (query.isBlank()) songs
                                else songs.filter {
                                    it.title.contains(query, true) ||
                                        it.artist.contains(query, true) ||
                                        it.album.contains(query, true)
                                }
                            }
                            val onPlay = remember(displayed) { fn@{ i: Int -> vm.play(displayed, i) } }
                            SongList(displayed, state.currentSong?.id, favorites, state.isPlaying, onPlay, onToggleFavorite)
                        }

                        Tab.ALBUMS -> AlbumList(songs, onOpenAlbum)
                        Tab.ARTISTS -> ArtistList(
                            artistas, onOpenArtist, vm.carpetaRaiz, vm.artistImages,
                            onUnir = vm::unirArtistas,
                            onSeparar = vm::separarArtistas,
                        )
                        Tab.FAVORITES -> {
                            val favSongs = remember(songs, favorites) {
                                songs.filter { it.id in favorites }
                            }
                            if (favSongs.isEmpty()) {
                                EmptyHint("Aún no tienes favoritos.\nToca el ❤ en una canción para agregarla.")
                            } else {
                                val onPlayFav = remember(favSongs) { fn@{ i: Int -> vm.play(favSongs, i) } }
                                SongList(favSongs, state.currentSong?.id, favorites, state.isPlaying, onPlayFav, onToggleFavorite)
                            }
                        }
                    }
                }
            }
        }

        // ===== Panel Now Playing (se desliza sobre todo) =====
        val current = state.currentSong
        if (current != null) {
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer { translationY = (1f - progress.value) * fullHeightPx }
                    .then(dragModifier),
            ) {
                NowPlayingScreen(
                    song = current,
                    state = state,
                    isFavorite = current.id in favorites,
                    onClose = { settle(false) },
                    onOpenQueue = onOpenQueue,
                    onOpenEqualizer = onOpenEqualizer,
                    onToggleFavorite = { vm.toggleFavorite(current.id) },
                    onPlayPause = vm::togglePlay,
                    onNext = vm::next,
                    onPrevious = vm::previous,
                    onSeek = vm::seekTo,
                    onToggleShuffle = vm::toggleShuffle,
                    onCycleRepeat = vm::cycleRepeat,
                    nextSong = queue.getOrNull(state.currentIndex + 1),
                )
            }
        }
    }
}

@Composable
private fun SongList(
    songs: List<Song>,
    currentId: String?,
    favorites: Set<String>,
    isPlaying: Boolean,
    onPlay: (Int) -> Unit,
    onToggleFavorite: (String) -> Unit,
) {
    LazyColumn(contentPadding = PaddingValues(vertical = 8.dp)) {
        itemsIndexed(songs, key = { _, song -> song.id }) { index, song ->
            SongRow(
                song = song,
                isCurrent = song.id == currentId,
                isFavorite = song.id in favorites,
                onClick = { onPlay(index) },
                onToggleFavorite = { onToggleFavorite(song.id) },
                isPlaying = isPlaying,
            )
        }
    }
}

@Composable
private fun AlbumList(songs: List<Song>, onOpenAlbum: (String) -> Unit) {
    val albums = remember(songs) {
        songs.groupBy { it.album }
            .map { (name, list) -> Triple(name, list.first(), list.size) }
            .sortedBy { it.first.lowercase() }
    }
    LazyColumn(contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)) {
        items(albums, key = { it.first }) { (name, sample, count) ->
            Row(
                modifier = Modifier.fillMaxWidth()
                    .clickable { onOpenAlbum(name) }
                    .padding(vertical = 8.dp, horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AlbumArt(uri = sample.uri, modifier = Modifier.size(52.dp).clip(RoundedCornerShape(8.dp)))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
                    Text("${sample.artist} · $count canciones", maxLines = 1,
                        overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun ArtistList(
    artists: List<Artista>,
    onOpenArtist: (String) -> Unit,
    raiz: Uri?,
    imagenes: ArtistImages,
    onUnir: (String, String) -> Unit,
    onSeparar: (String) -> Unit,
) {
    // Artista sobre el que se mantuvo pulsado, si hay menú abierto.
    var menuDe by remember { mutableStateOf<Artista?>(null) }
    var uniendo by remember { mutableStateOf<Artista?>(null) }

    LazyColumn(contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)) {
        items(artists, key = { it.clave }) { artista ->
            val name = artista.nombre
            val count = artista.canciones.size
            Row(
                modifier = Modifier.fillMaxWidth()
                    .combinedClickable(
                        onClick = { onOpenArtist(artista.clave) },
                        onLongClick = { menuDe = artista },
                    )
                    .padding(vertical = 12.dp, horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FotoDeArtista(artista, raiz, imagenes)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
                    Text(
                        // Cuando hay artistas unidos se dice cuáles, para que no
                        // parezca que las canciones salieron de la nada.
                        if (artista.unidos.isEmpty()) "$count canciones"
                        else "$count canciones · con ${artista.unidos.joinToString(", ")}",
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }

    menuDe?.let { artista ->
        MenuDeArtista(
            artista = artista,
            onUnirCon = { menuDe = null; uniendo = artista },
            onSeparar = { menuDe = null; onSeparar(artista.clave) },
            onCerrar = { menuDe = null },
        )
    }

    uniendo?.let { artista ->
        ElegirArtista(
            origen = artista,
            candidatos = artists.filter { it.clave != artista.clave },
            onElegido = { destino -> uniendo = null; onUnir(artista.clave, destino.clave) },
            onCerrar = { uniendo = null },
        )
    }
}

/** Qué se puede hacer con un artista al mantenerlo pulsado. */
@Composable
private fun MenuDeArtista(
    artista: Artista,
    onUnirCon: () -> Unit,
    onSeparar: () -> Unit,
    onCerrar: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onCerrar,
        title = { Text(artista.nombre) },
        text = {
            Column {
                TextButton(onClick = onUnirCon, modifier = Modifier.fillMaxWidth()) {
                    Text("Unir con otro artista", modifier = Modifier.weight(1f))
                }
                if (artista.unidos.isNotEmpty()) {
                    TextButton(onClick = onSeparar, modifier = Modifier.fillMaxWidth()) {
                        Text("Separar ${artista.unidos.joinToString(", ")}",
                            modifier = Modifier.weight(1f))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onCerrar) { Text("Cerrar") } },
    )
}

/**
 * Elige con qué artista unir. El elegido es el que se queda: su nombre es el que
 * se ve después, porque el usuario lo está señalando como el bueno.
 */
@Composable
private fun ElegirArtista(
    origen: Artista,
    candidatos: List<Artista>,
    onElegido: (Artista) -> Unit,
    onCerrar: () -> Unit,
) {
    var filtro by remember { mutableStateOf("") }
    val visibles = remember(filtro, candidatos) {
        if (filtro.isBlank()) candidatos
        else candidatos.filter { it.nombre.contains(filtro, ignoreCase = true) }
    }
    AlertDialog(
        onDismissRequest = onCerrar,
        title = { Text("Unir ${origen.nombre} con…") },
        text = {
            Column {
                SearchField(filtro, { filtro = it })
                Spacer(Modifier.height(8.dp))
                LazyColumn(Modifier.height(320.dp)) {
                    items(visibles, key = { it.clave }) { candidato ->
                        Text(
                            candidato.nombre,
                            modifier = Modifier.fillMaxWidth()
                                .clickable { onElegido(candidato) }
                                .padding(vertical = 14.dp, horizontal = 4.dp),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onCerrar) { Text("Cancelar") } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusManager = LocalFocusManager.current
    TextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier.fillMaxWidth(),
        placeholder = { Text("Buscar canción, artista o álbum…") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surface,
            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
            focusedIndicatorColor = MaterialTheme.colorScheme.primary,
            unfocusedIndicatorColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    )
}

@Composable
private fun EmptyHint(text: String) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun CenteredMessage(title: String, subtitle: String, buttonText: String, onPick: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Rounded.LibraryMusic, contentDescription = null,
            modifier = Modifier.size(72.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground)
        Spacer(Modifier.height(8.dp))
        Text(subtitle, style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(24.dp))
        Button(onClick = onPick) {
            Icon(Icons.Rounded.FolderOpen, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(buttonText)
        }
    }
}

/**
 * Foto del artista. Se resuelve fuera del hilo principal y solo una vez por
 * nombre: la primera vez se descarga y se guarda en su carpeta, después ya está.
 *
 * Mientras no haya foto se deja el icono de siempre, así que la lista sigue
 * funcionando sin conexión y sin esperas.
 */
@Composable
private fun FotoDeArtista(artista: Artista, raiz: Uri?, imagenes: ArtistImages) {
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
    Box(
        Modifier.size(52.dp).clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant),
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
