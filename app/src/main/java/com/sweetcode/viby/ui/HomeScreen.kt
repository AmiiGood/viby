package com.sweetcode.viby.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sweetcode.viby.model.Song
import com.sweetcode.viby.ui.components.AlbumArt
import com.sweetcode.viby.ui.components.SongRow
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
    val state by vm.uiState.collectAsStateWithLifecycle()
    val favorites by vm.favorites.collectAsStateWithLifecycle()

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
                TopAppBar(
                    title = {
                        if (tab == Tab.SONGS && searching) {
                            SearchField(query = query, onQueryChange = { query = it })
                        } else {
                            Text(if (tab == Tab.SONGS) "Viby" else tab.label, fontWeight = FontWeight.Bold)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                        titleContentColor = MaterialTheme.colorScheme.onBackground,
                    ),
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
                                Icon(Icons.Rounded.Radio, contentDescription = "Viby FM")
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
            },
            bottomBar = {
                Column {
                    state.currentSong?.let { song ->
                        MiniPlayer(
                            song = song,
                            isPlaying = state.isPlaying,
                            progress = if (state.durationMs > 0)
                                state.positionMs.toFloat() / state.durationMs else 0f,
                            onExpand = { settle(true) },
                            onPlayPause = vm::togglePlay,
                            onNext = vm::next,
                            dragModifier = dragModifier,
                        )
                    }
                    NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                        Tab.entries.forEach { t ->
                            NavigationBarItem(
                                selected = tab == t,
                                onClick = { tabIndex = t.ordinal },
                                icon = { Icon(t.icon, contentDescription = t.label) },
                                label = { Text(t.label) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = MaterialTheme.colorScheme.onPrimary,
                                    indicatorColor = MaterialTheme.colorScheme.primary,
                                    selectedTextColor = MaterialTheme.colorScheme.primary,
                                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                ),
                            )
                        }
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
                            SongList(displayed, state.currentSong?.id, favorites, onPlay, onToggleFavorite)
                        }

                        Tab.ALBUMS -> AlbumList(songs, onOpenAlbum)
                        Tab.ARTISTS -> ArtistList(songs, onOpenArtist)
                        Tab.FAVORITES -> {
                            val favSongs = remember(songs, favorites) {
                                songs.filter { it.id in favorites }
                            }
                            if (favSongs.isEmpty()) {
                                EmptyHint("Aún no tienes favoritos.\nToca el ❤ en una canción para agregarla.")
                            } else {
                                val onPlayFav = remember(favSongs) { fn@{ i: Int -> vm.play(favSongs, i) } }
                                SongList(favSongs, state.currentSong?.id, favorites, onPlayFav, onToggleFavorite)
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
    onPlay: (Int) -> Unit,
    onToggleFavorite: (String) -> Unit,
) {
    LazyColumn(contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)) {
        itemsIndexed(songs, key = { _, song -> song.id }) { index, song ->
            SongRow(
                song = song,
                isCurrent = song.id == currentId,
                isFavorite = song.id in favorites,
                onClick = { onPlay(index) },
                onToggleFavorite = { onToggleFavorite(song.id) },
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
private fun ArtistList(songs: List<Song>, onOpenArtist: (String) -> Unit) {
    val artists = remember(songs) {
        songs.groupBy { it.artist }
            .map { (name, list) -> name to list.size }
            .sortedBy { it.first.lowercase() }
    }
    LazyColumn(contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)) {
        items(artists, key = { it.first }) { (name, count) ->
            Row(
                modifier = Modifier.fillMaxWidth()
                    .clickable { onOpenArtist(name) }
                    .padding(vertical = 12.dp, horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(52.dp).clip(RoundedCornerShape(26.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.Person, contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
                    Text("$count canciones", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchField(query: String, onQueryChange: (String) -> Unit) {
    val focusManager = LocalFocusManager.current
    TextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier.fillMaxWidth(),
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
private fun MiniPlayer(
    song: Song,
    isPlaying: Boolean,
    progress: Float,
    onExpand: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    dragModifier: Modifier,
) {
    Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 3.dp, modifier = dragModifier) {
        Column(modifier = Modifier.clickable(onClick = onExpand)) {
            LinearProgressIndicator(
                progress = { progress.coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(2.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AlbumArt(uri = song.uri, modifier = Modifier.size(58.dp).clip(RoundedCornerShape(10.dp)))
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(song.title, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                    Text(song.artist, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = onPlayPause) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = if (isPlaying) "Pausar" else "Reproducir",
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
                IconButton(onClick = onNext) {
                    Icon(Icons.Rounded.SkipNext, contentDescription = "Siguiente",
                        tint = MaterialTheme.colorScheme.onSurface)
                }
            }
        }
    }
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
