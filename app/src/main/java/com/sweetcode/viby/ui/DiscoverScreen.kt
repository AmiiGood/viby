package com.sweetcode.viby.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sweetcode.viby.download.normalizeTrackKey
import com.sweetcode.viby.model.Song
import com.sweetcode.viby.ui.components.DownloadResultRow
import com.sweetcode.viby.ui.components.VibyTopBar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiscoverScreen(
    songs: List<Song>,
    favorites: Set<String>,
    player: PlayerViewModel,
    onBack: () -> Unit,
) {
    val vm: DownloadViewModel = viewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    val downloads by vm.downloads.collectAsStateWithLifecycle()
    val preview by vm.preview.collectAsStateWithLifecycle()
    val reproduciendo by player.uiState.collectAsStateWithLifecycle()

    var refreshKey by remember { mutableIntStateOf(0) }

    val ownedKeys = remember(songs) {
        songs.mapTo(HashSet()) { normalizeTrackKey(it.artist, it.title) }
    }
    val seeds = remember(songs, favorites, refreshKey) {
        val favSongs = songs.filter { it.id in favorites }
        val pool = if (favSongs.size >= 3) favSongs else songs
        pool.shuffled().take(4).map { it.artist to it.title }
    }

    LaunchedEffect(refreshKey) {
        vm.loadRecommendations(seeds, ownedKeys)
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            VibyTopBar(title = "Descubrir", onBack = onBack) {
                IconButton(onClick = { refreshKey++ }) {
                    Icon(Icons.Rounded.Refresh, contentDescription = "Otras recomendaciones")
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                state.isSearching -> Column(
                    Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
                ) {
                    CircularProgressIndicator()
                    Text(
                        "Buscando recomendaciones para ti…",
                        modifier = Modifier.padding(top = 16.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                state.error != null -> Box(Modifier.fillMaxSize().padding(24.dp), Alignment.Center) {
                    Text(
                        state.error!!,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                else -> LazyColumn(contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)) {
                    items(state.results, key = { it.url }) { result ->
                        // "Este" es el que suena ahora en el reproductor de la app,
                        // no el que esta pantalla esté reproduciendo por su cuenta.
                        val isThis = reproduciendo.previewUrl == result.url
                        DownloadResultRow(
                            result = result,
                            status = downloads[result.url],
                            previewPlaying = isThis && reproduciendo.isPlaying,
                            previewLoading = preview.url == result.url && preview.isLoading,
                            onPreview = {
                                if (isThis) player.togglePlay()
                                else vm.abrirPreview(result) { url ->
                                    player.playStream(
                                        url = result.url,
                                        title = result.title,
                                        artist = result.uploader,
                                        streamUrl = url,
                                        artworkUrl = result.thumbnailUrl,
                                    )
                                }
                            },
                            onDownload = { vm.download(result) },
                        )
                    }
                }
            }
        }
    }
}
