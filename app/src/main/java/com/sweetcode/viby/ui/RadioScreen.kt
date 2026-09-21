package com.sweetcode.viby.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.sweetcode.viby.model.Song
import com.sweetcode.viby.model.Station
import com.sweetcode.viby.ui.components.MiniPlayer

/**
 * Catálogo de emisoras de radio por internet (Radio Browser).
 *
 * Al abrir muestra las más escuchadas; desde ahí se busca por nombre, se filtra
 * por género y se guardan favoritas.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RadioScreen(
    onBack: () -> Unit,
    onPlay: (Station) -> Unit,
    playingStation: Station?,
    nowPlaying: Song?,
    isPlaying: Boolean,
    onPlayPause: () -> Unit,
) {
    val playingStationId = playingStation?.id
    val vm: RadioViewModel = viewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    val focusManager = LocalFocusManager.current

    val play: (Station) -> Unit = { station ->
        focusManager.clearFocus()
        vm.onStationPlayed(station)
        onPlay(station)
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Radio", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Volver")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                    navigationIconContentColor = MaterialTheme.colorScheme.onBackground,
                ),
            )
        },
        bottomBar = {
            // Sin esto, al quedarte navegando el catalogo pierdes de vista que
            // hay algo sonando y no tienes forma de pararlo sin volver atras.
            if (playingStation != null && nowPlaying != null) {
                MiniPlayer(
                    song = nowPlaying,
                    isPlaying = isPlaying,
                    progress = null, // en vivo: no hay barra que llenar
                    artworkUrl = playingStation.faviconUrl.takeIf { it.isNotBlank() },
                    onExpand = onBack, // el reproductor completo vive en la pantalla de inicio
                    onPlayPause = onPlayPause,
                    // Sin "siguiente": una emisora no tiene cola detras.
                )
            }
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {

            TextField(
                value = state.query,
                onValueChange = vm::onQueryChange,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                placeholder = { Text("Buscar emisora...") },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                trailingIcon = {
                    if (state.query.isNotBlank()) {
                        IconButton(onClick = { vm.onQueryChange("") }) {
                            Icon(Icons.Rounded.Close, contentDescription = "Limpiar")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
                colors = TextFieldDefaults.colors(
                    focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                    unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                ),
            )

            Spacer(Modifier.height(12.dp))

            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(RADIO_GENRES) { genre ->
                    FilterChip(
                        selected = state.genre == genre,
                        onClick = { vm.onGenreClick(genre) },
                        label = { Text(genre) },
                        colors = FilterChipDefaults.filterChipColors(),
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            when {
                state.isLoading && state.stations.isEmpty() -> CenteredBox {
                    CircularProgressIndicator()
                }

                state.error != null && state.stations.isEmpty() -> CenteredBox {
                    Text(
                        state.error!!,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }

                else -> {
                    val favoriteIds = state.favoriteIds
                    // Favoritos y recientes solo estorban cuando estás buscando algo concreto.
                    val showShortcuts = state.query.isBlank() && state.genre == null

                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 24.dp),
                    ) {
                        if (showShortcuts && state.favorites.isNotEmpty()) {
                            item { SectionHeader("Favoritas") }
                            items(state.favorites, key = { "fav_" + it.id }) { station ->
                                StationRow(
                                    station = station,
                                    isFavorite = true,
                                    isPlaying = station.id == playingStationId,
                                    onPlay = { play(station) },
                                    onToggleFavorite = { vm.toggleFavorite(station) },
                                )
                            }
                        }

                        if (showShortcuts && state.recents.isNotEmpty()) {
                            item { SectionHeader("Escuchadas hace poco") }
                            items(state.recents, key = { "rec_" + it.id }) { station ->
                                StationRow(
                                    station = station,
                                    isFavorite = station.id in favoriteIds,
                                    isPlaying = station.id == playingStationId,
                                    onPlay = { play(station) },
                                    onToggleFavorite = { vm.toggleFavorite(station) },
                                )
                            }
                        }

                        item {
                            SectionHeader(
                                when {
                                    state.genre != null -> state.genre!!.replaceFirstChar { it.uppercase() }
                                    state.query.isNotBlank() -> "Resultados"
                                    else -> "Las más escuchadas"
                                }
                            )
                        }
                        items(state.stations, key = { it.id }) { station ->
                            StationRow(
                                station = station,
                                isFavorite = station.id in favoriteIds,
                                isPlaying = station.id == playingStationId,
                                onPlay = { play(station) },
                                onToggleFavorite = { vm.toggleFavorite(station) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CenteredBox(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp),
    )
}

@Composable
private fun StationRow(
    station: Station,
    isFavorite: Boolean,
    isPlaying: Boolean,
    onPlay: () -> Unit,
    onToggleFavorite: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onPlay)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            if (station.faviconUrl.isNotBlank()) {
                AsyncImage(
                    model = station.faviconUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                // Muchas emisoras no publican logo; el icono evita el hueco vacío.
                Icon(
                    Icons.Rounded.Radio,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Spacer(Modifier.width(12.dp))

        Column(Modifier.weight(1f)) {
            Text(
                text = station.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (isPlaying) FontWeight.Bold else FontWeight.Normal,
                color = if (isPlaying) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (station.subtitle.isNotBlank()) {
                Text(
                    text = station.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        if (isPlaying) {
            Icon(
                Icons.Rounded.GraphicEq,
                contentDescription = "Sonando",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(4.dp))
        }

        IconButton(onClick = onToggleFavorite) {
            Icon(
                imageVector = if (isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                contentDescription = if (isFavorite) "Quitar de favoritas" else "Guardar en favoritas",
                tint = if (isFavorite) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
