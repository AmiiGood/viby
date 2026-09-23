package com.sweetcode.viby.ui.theme

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.compose.animation.animateColorAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.sweetcode.viby.data.coverThumbFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Paleta de lo que está sonando, con transición suave al cambiar de canción.
 *
 * El corte entre dos paletas sería brusco: la pantalla entera saltaría de color en
 * un fotograma. Se animan los colores para que el cambio acompañe a la transición.
 *
 * @param songUri URI del archivo local, del que se extrae la carátula embebida.
 * @param artworkUri carátula ya resuelta (emisora o portada descargada); tiene prioridad.
 */
@Composable
fun rememberVibyPalette(songUri: Uri?, artworkUri: Uri? = null): VibyPalette {
    val context = LocalContext.current
    var destino by remember { mutableStateOf(VibyPalette.Marca) }

    LaunchedEffect(songUri, artworkUri) {
        destino = withContext(Dispatchers.IO) {
            val bmp = cargarMiniatura(context, songUri, artworkUri)
            val paleta = bmp?.let { paletteFrom(it) } ?: VibyPalette.Marca
            bmp?.recycle()
            paleta
        }
    }

    val acento by animateColorAsState(destino.acento, label = "acento")
    val sobre by animateColorAsState(destino.sobreAcento, label = "sobreAcento")
    val inicio by animateColorAsState(destino.fondoInicio, label = "fondoInicio")
    val medio by animateColorAsState(destino.fondoMedio, label = "fondoMedio")
    val fin by animateColorAsState(destino.fondoFin, label = "fondoFin")

    return VibyPalette(
        acento = acento,
        acentoSuave = acento.copy(alpha = 0.18f),
        sobreAcento = sobre,
        fondoInicio = inicio,
        fondoMedio = medio,
        fondoFin = fin,
    )
}

/**
 * Carga una miniatura pequeña: para sacar la paleta basta con 96px, y decodificar la
 * carátula entera por cada cambio de canción sería tirar memoria y tiempo.
 */
private fun cargarMiniatura(context: Context, songUri: Uri?, artworkUri: Uri?): Bitmap? {
    val opciones = BitmapFactory.Options().apply { inSampleSize = 4 }

    // 1. Carátula ya resuelta en disco (emisora, o portada bajada por ICY).
    artworkUri?.takeIf { it.scheme == "file" }?.path?.let { ruta ->
        if (File(ruta).exists()) {
            BitmapFactory.decodeFile(ruta, opciones)?.let { return it }
        }
    }

    // 1b. Imagen dentro de la biblioteca del usuario (la foto del artista), a la
    // que solo se llega por el proveedor de documentos.
    artworkUri?.takeIf { it.scheme == "content" }?.let { uri ->
        runCatching {
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, opciones)
            }
        }.getOrNull()?.let { return it }
    }
    if (songUri == null) return null

    // 2. Miniatura ya cacheada del archivo local.
    val cache = coverThumbFile(context, songUri.toString())
    if (cache.exists()) {
        BitmapFactory.decodeFile(cache.absolutePath, opciones)?.let { return it }
    }

    // 3. Último recurso: extraer la imagen embebida del propio archivo. Solo si
    // es un fichero: en un stream remoto esto se bajaría medio audio por nada.
    if (songUri.scheme !in setOf("content", "file")) return null
    return runCatching {
        val r = MediaMetadataRetriever()
        try {
            r.setDataSource(context, songUri)
            r.embeddedPicture?.let { BitmapFactory.decodeByteArray(it, 0, it.size, opciones) }
        } finally {
            r.release()
        }
    }.getOrNull()
}
