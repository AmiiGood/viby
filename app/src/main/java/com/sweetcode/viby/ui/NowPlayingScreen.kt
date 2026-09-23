package com.sweetcode.viby.ui

import android.content.Intent
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
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
import androidx.compose.material.icons.rounded.Headphones
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import com.sweetcode.viby.model.Song
import coil.compose.AsyncImage
import com.sweetcode.viby.ui.components.AlbumArt
import com.sweetcode.viby.ui.components.AudioCover
import com.sweetcode.viby.ui.theme.VibyPalette
import com.sweetcode.viby.ui.theme.rememberVibyPalette
import kotlin.math.roundToInt

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
    nextSong: Song? = null,
) {
    // Una emisora no tiene caratula embebida que extraer de su URL: la portada
    // la resuelve el servicio y llega ya como fichero en artworkUri.
    val artModel: Any = state.artworkUri?.takeIf { state.currentStation != null }
        ?: AudioCover(song.uri)

    // El color sale de la carátula: es la idea entera de esta dirección.
    val paleta = rememberVibyPalette(songUri = song.uri, artworkUri = state.artworkUri)

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    0f to paleta.fondoInicio,
                    0.45f to paleta.fondoMedio,
                    1f to paleta.fondoFin,
                )
            )
    ) {
        // Se compara el tamaño real y no la orientación declarada: así también
        // acierta en pantalla dividida y en ventanas redimensionables.
        val esHorizontal = maxWidth > maxHeight

        Column(
            // navigationBarsPadding es imprescindible: con navegacion de tres
            // botones (o la barra de tareas de Samsung) el contenido de abajo
            // quedaba debajo del sistema. Con gestos apenas se notaba.
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(
                    horizontal = 24.dp,
                    vertical = if (esHorizontal) 10.dp else 24.dp,
                ),
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
                    text = (state.currentStation?.name ?: song.album.ifBlank { "Tu biblioteca" })
                        .uppercase(),
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    fontSize = 10.sp,
                    letterSpacing = 1.3.sp,
                    color = Color.White.copy(alpha = 0.5f),
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                // Salida de audio: abre el selector del sistema, que es quien
                // sabe de verdad qué dispositivos hay conectados.
                val contexto = LocalContext.current
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    IconButton(onClick = {
                        // No hay constante pública para este panel; la acción va por
                        // nombre y no existe en todos los dispositivos, de ahí el runCatching.
                        runCatching {
                            contexto.startActivity(
                                Intent("com.android.settings.panel.action.MEDIA_OUTPUT")
                                    .putExtra("package_name", contexto.packageName)
                            )
                        }
                    }) {
                        Icon(
                            Icons.Rounded.Headphones,
                            contentDescription = "Salida de audio",
                            tint = Color.White.copy(alpha = 0.8f),
                        )
                    }
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

            // En horizontal la carátula cuadrada a todo el ancho no cabe de alto:
            // en una tablet apaisada empuja controles y texto fuera de pantalla.
            // Ahí se pasa a dos columnas y la carátula se mide por el alto.
            if (esHorizontal) {
                // Como en una pantalla de asistente: la carátula manda a la
                // izquierda con el transporte justo debajo, y la información
                // ocupa la derecha. Así ninguna columna se queda sin alto y el
                // botón de play conserva su círculo.
                Row(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(
                        modifier = Modifier.weight(0.42f).fillMaxHeight(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Box(
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                            contentAlignment = Alignment.Center,
                        ) {
                            Caratula(
                                song = song,
                                state = state,
                                artModel = artModel,
                                acento = paleta.acento,
                                // matchHeightConstraintsFirst: por defecto aspectRatio
                                // se ajusta al ancho, y aquí el lado corto es el alto.
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .aspectRatio(1f, matchHeightConstraintsFirst = true),
                                proporcion = 0.97f,
                                onNext = onNext,
                                onPrevious = onPrevious,
                            )
                        }
                        Spacer(Modifier.height(18.dp))
                        ControlesTransporte(
                            state, paleta, onPlayPause, onNext, onPrevious,
                        )
                    }
                    Spacer(Modifier.width(32.dp))
                    Column(
                        modifier = Modifier.weight(0.58f).fillMaxHeight(),
                        verticalArrangement = Arrangement.Center,
                    ) {
                        InfoPista(song, state, isFavorite, onToggleFavorite)
                        Spacer(Modifier.height(24.dp))
                        Progreso(state, paleta, onSeek)
                        Spacer(Modifier.height(16.dp))
                        ControlesSecundarios(
                            state, paleta, onToggleShuffle, onCycleRepeat,
                            modifier = Modifier.align(Alignment.CenterHorizontally),
                        )
                    }
                }
            } else {
                Spacer(Modifier.weight(1f))
                Caratula(
                    song = song,
                    state = state,
                    artModel = artModel,
                    acento = paleta.acento,
                    modifier = Modifier.fillMaxWidth().aspectRatio(1f),
                    onNext = onNext,
                    onPrevious = onPrevious,
                )
                Spacer(Modifier.height(32.dp))
                InfoPista(song, state, isFavorite, onToggleFavorite)
                Spacer(Modifier.height(20.dp))
                ProgresoYControles(
                    state, paleta, onSeek, onPlayPause,
                    onNext, onPrevious, onToggleShuffle, onCycleRepeat,
                )
                Spacer(Modifier.weight(1f))
            }

            // Lo que viene después. Una emisora no tiene cola, así que solo sale
            // cuando de verdad hay una canción siguiente.
            if (!esHorizontal && nextSong != null && state.currentStation == null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(Color.White.copy(alpha = 0.08f))
                        .clickable(onClick = onOpenQueue)
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AlbumArt(
                        uri = nextSong.uri,
                        modifier = Modifier.size(38.dp).clip(RoundedCornerShape(9.dp)),
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = "A CONTINUACIÓN",
                            fontSize = 9.sp,
                            letterSpacing = 1.2.sp,
                            color = Color.White.copy(alpha = 0.45f),
                            fontWeight = FontWeight.Medium,
                        )
                        Text(
                            text = "${nextSong.title} · ${nextSong.artist}",
                            fontSize = 13.sp,
                            color = Color.White.copy(alpha = 0.9f),
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}


/** La portada con su halo. El tamaño lo decide quien la coloca, vía [modifier]. */
@Composable
private fun Caratula(
    song: Song,
    state: PlayerUiState,
    artModel: Any,
    acento: Color,
    modifier: Modifier = Modifier,
    // Cuánto de la caja ocupa la portada. El resto es el margen donde se ve el
    // halo; en horizontal se aprieta porque el alto es el recurso escaso.
    proporcion: Float = 0.88f,
    onNext: (() -> Unit)? = null,
    onPrevious: (() -> Unit)? = null,
) {
    val artModifier = Modifier.fillMaxSize(proporcion).clip(RoundedCornerShape(22.dp))
    // Deslizar sobre la portada cambia de pista. Va solo aquí y no en toda la
    // pantalla para no pelearse con la barra de progreso ni con el arrastre
    // vertical que cierra el reproductor.
    val umbral = with(LocalDensity.current) { 72.dp.toPx() }
    val gesto = if (onNext == null && onPrevious == null) Modifier else Modifier.pointerInput(Unit) {
        var recorrido = 0f
        detectHorizontalDragGestures(
            onDragStart = { recorrido = 0f },
            onDragCancel = { recorrido = 0f },
            onDragEnd = {
                when {
                    recorrido <= -umbral -> onNext?.invoke()
                    recorrido >= umbral -> onPrevious?.invoke()
                }
                recorrido = 0f
            },
        ) { _, delta -> recorrido += delta }
    }
    Box(
        modifier = modifier.then(gesto),
        contentAlignment = Alignment.Center,
    ) {
        // Halo del color de la portada: da profundidad sin desenfocarla entera.
        Box(
            Modifier.fillMaxSize().background(
                Brush.radialGradient(
                    colors = listOf(
                        acento.copy(alpha = 0.30f),
                        Color.Transparent,
                    ),
                )
            )
        )
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
    }
}

/** Título con el favorito al lado, artista y álbum. */
@Composable
private fun InfoPista(
    song: Song,
    state: PlayerUiState,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
) {
    // Título / artista / álbum, centrados. El favorito va a la derecha del título.
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Spacer(Modifier.size(48.dp))
        Text(
            text = song.title,
            fontSize = 26.sp,
            lineHeight = 30.sp,
            letterSpacing = (-0.4).sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.Center,
        )
        IconButton(onClick = onToggleFavorite) {
            Icon(
                imageVector = if (isFavorite) Icons.Rounded.Favorite
                else Icons.Rounded.FavoriteBorder,
                contentDescription = if (isFavorite) "Quitar de favoritos"
                else "Agregar a favoritos",
                tint = if (isFavorite) MaterialTheme.colorScheme.tertiary
                else Color.White.copy(alpha = 0.55f),
            )
        }
    }
    Spacer(Modifier.height(2.dp))
    Text(
        text = song.artist,
        style = MaterialTheme.typography.bodyLarge,
        color = Color.White.copy(alpha = 0.62f),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.fillMaxWidth(),
        textAlign = TextAlign.Center,
    )
    // Tercer nivel: el álbum daba contexto y no estaba en ningún sitio.
    val contexto = listOfNotNull(
        song.album.ifBlank { null },
        state.currentStation?.subtitle?.ifBlank { null },
    ).firstOrNull()
    if (contexto != null) {
        Spacer(Modifier.height(3.dp))
        Text(
            text = contexto,
            fontSize = 12.sp,
            color = Color.White.copy(alpha = 0.34f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
        )
    }

    Spacer(Modifier.height(20.dp))

}
/** Progreso (o EN VIVO) con sus tiempos. */
@Composable
private fun Progreso(
    state: PlayerUiState,
    paleta: VibyPalette,
    onSeek: (Long) -> Unit,
) {
    if (state.currentStation != null) {
        LiveIndicator(paleta.acento)
    } else {
        SeekBar(
            positionMs = state.positionMs,
            durationMs = state.durationMs,
            acento = paleta.acento,
            onSeek = onSeek,
        )
    }
}

/**
 * Anterior, play/pausa y siguiente.
 *
 * El botón lleva tamaño fijo y va dentro de un Box con alto reservado: sin eso,
 * cuando la fila se queda sin espacio vertical Compose lo comprime y el círculo
 * sale como un óvalo, que es lo que pasaba en horizontal.
 */
@Composable
private fun ControlesTransporte(
    state: PlayerUiState,
    paleta: VibyPalette,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    diametro: Dp = 76.dp,
) {
    Row(
        modifier = Modifier.height(diametro),
        horizontalArrangement = Arrangement.spacedBy(20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onPrevious, modifier = Modifier.size(56.dp)) {
            Icon(
                Icons.Rounded.SkipPrevious,
                contentDescription = "Anterior",
                tint = Color.White,
                modifier = Modifier.size(42.dp),
            )
        }
        Surface(
            shape = CircleShape,
            color = paleta.acento,
            modifier = Modifier.size(diametro),
        ) {
            IconButton(onClick = onPlayPause) {
                Icon(
                    imageVector = if (state.isPlaying) Icons.Rounded.Pause
                    else Icons.Rounded.PlayArrow,
                    contentDescription = if (state.isPlaying) "Pausar" else "Reproducir",
                    tint = paleta.sobreAcento,
                    modifier = Modifier.size(diametro * 0.45f),
                )
            }
        }
        IconButton(onClick = onNext, modifier = Modifier.size(56.dp)) {
            Icon(
                Icons.Rounded.SkipNext,
                contentDescription = "Siguiente",
                tint = Color.White,
                modifier = Modifier.size(42.dp),
            )
        }
    }
}

/** Aleatorio y repetir: secundarios, se separan del transporte en horizontal. */
@Composable
private fun ControlesSecundarios(
    state: PlayerUiState,
    paleta: VibyPalette,
    onToggleShuffle: () -> Unit,
    onCycleRepeat: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(32.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onToggleShuffle, modifier = Modifier.size(52.dp)) {
            Icon(
                Icons.Rounded.Shuffle,
                contentDescription = "Aleatorio",
                modifier = Modifier.size(28.dp),
                tint = if (state.shuffleEnabled) paleta.acento
                else Color.White.copy(alpha = 0.45f),
            )
        }
        IconButton(onClick = onCycleRepeat, modifier = Modifier.size(52.dp)) {
            Icon(
                imageVector = if (state.repeatMode == Player.REPEAT_MODE_ONE)
                    Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                contentDescription = "Repetir",
                modifier = Modifier.size(28.dp),
                tint = if (state.repeatMode == Player.REPEAT_MODE_OFF)
                    Color.White.copy(alpha = 0.45f) else paleta.acento,
            )
        }
    }
}

/** Disposición vertical: progreso y una sola fila con los cinco controles. */
@Composable
private fun ProgresoYControles(
    state: PlayerUiState,
    paleta: VibyPalette,
    onSeek: (Long) -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeat: () -> Unit,
) {
    Progreso(state, paleta, onSeek)
    Spacer(Modifier.height(12.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onToggleShuffle) {
            Icon(
                Icons.Rounded.Shuffle,
                contentDescription = "Aleatorio",
                tint = if (state.shuffleEnabled) paleta.acento
                else Color.White.copy(alpha = 0.45f),
            )
        }
        ControlesTransporte(state, paleta, onPlayPause, onNext, onPrevious)
        IconButton(onClick = onCycleRepeat) {
            Icon(
                imageVector = if (state.repeatMode == Player.REPEAT_MODE_ONE)
                    Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                contentDescription = "Repetir",
                tint = if (state.repeatMode == Player.REPEAT_MODE_OFF)
                    Color.White.copy(alpha = 0.45f) else paleta.acento,
            )
        }
    }
}

@Composable
private fun SeekBar(positionMs: Long, durationMs: Long, acento: Color, onSeek: (Long) -> Unit) {
    var scrubbing by remember { mutableStateOf(false) }
    var scrubValue by remember { mutableStateOf(0f) }
    var anchoPx by remember { mutableStateOf(1f) }

    val duration = durationMs.coerceAtLeast(1L)
    val fraccion = if (scrubbing) scrubValue
    else (positionMs.toFloat() / duration).coerceIn(0f, 1f)

    Column(Modifier.fillMaxWidth()) {
        // Línea con punto, dibujada a mano: el Slider de Material trae su propio
        // pulgar en forma de barra y marcas de tope que no son lo que pide el diseño.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(20.dp)
                .onSizeChanged { anchoPx = it.width.toFloat().coerceAtLeast(1f) }
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragStart = { offset ->
                            scrubbing = true
                            scrubValue = (offset.x / anchoPx).coerceIn(0f, 1f)
                        },
                        onDragEnd = {
                            onSeek((scrubValue * duration).toLong())
                            scrubbing = false
                        },
                        onDragCancel = { scrubbing = false },
                    ) { change, _ ->
                        scrubValue = (change.position.x / anchoPx).coerceIn(0f, 1f)
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        onSeek(((offset.x / anchoPx).coerceIn(0f, 1f) * duration).toLong())
                    }
                },
            contentAlignment = Alignment.CenterStart,
        ) {
            Box(
                Modifier.fillMaxWidth().height(3.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color.White.copy(alpha = 0.18f))
            )
            Box(
                Modifier.fillMaxWidth(fraccion).height(3.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(acento)
            )
            Box(
                Modifier
                    .offset {
                        IntOffset((fraccion * anchoPx - 6.dp.toPx()).roundToInt(), 0)
                    }
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(Color.White)
            )
        }
        Spacer(Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            val shownPosition = if (scrubbing) (scrubValue * duration).toLong() else positionMs
            Text(
                formatDuration(shownPosition),
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = 0.6f),
            )
            Text(
                formatDuration(durationMs),
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = 0.35f),
            )
        }
    }
}

/** Sustituye a la barra de progreso cuando lo que suena es una emisora. */
@Composable
private fun LiveIndicator(acento: Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(acento)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = "EN VIVO",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = acento,
        )
    }
}
