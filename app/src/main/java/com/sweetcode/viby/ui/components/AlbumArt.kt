package com.sweetcode.viby.ui.components

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.sweetcode.viby.data.coverThumbFile
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.decode.DataSource
import coil.decode.ImageSource
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.fetch.SourceResult
import coil.key.Keyer
import coil.request.Options
import okio.Buffer

/** Modelo para que Coil cargue la carátula embebida de un archivo de audio. */
data class AudioCover(val uri: Uri)

/** Clave de caché (memoria/disco) por URI del archivo. */
class AudioCoverKeyer : Keyer<AudioCover> {
    override fun key(data: AudioCover, options: Options): String = data.uri.toString()
}

/** Extrae la imagen embebida (APIC) del audio y se la entrega a Coil para que la decode/cachee. */
class AudioCoverFetcher(
    private val context: Context,
    private val data: AudioCover,
) : Fetcher {
    override suspend fun fetch(): FetchResult {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, data.uri)
            val bytes = retriever.embeddedPicture ?: error("Sin carátula embebida")
            return SourceResult(
                source = ImageSource(Buffer().apply { write(bytes) }, context),
                mimeType = null,
                dataSource = DataSource.DISK,
            )
        } finally {
            runCatching { retriever.release() }
        }
    }

    class Factory(private val context: Context) : Fetcher.Factory<AudioCover> {
        override fun create(data: AudioCover, options: Options, imageLoader: ImageLoader): Fetcher =
            AudioCoverFetcher(context, data)
    }
}

/**
 * Carátula cuadrada (con Coil: caché en memoria/disco + reducción de tamaño automática).
 * Si el archivo no trae imagen, muestra un ícono de fondo.
 */
@Composable
fun AlbumArt(uri: Uri, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    // Si ya hay miniatura en disco, cárgala (rapidísimo); si no, extrae al vuelo esta vez.
    val model: Any = remember(uri) {
        val thumb = coverThumbFile(context, uri.toString())
        if (thumb.exists()) thumb else AudioCover(uri)
    }
    Box(
        modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Rounded.MusicNote,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(28.dp),
        )
        AsyncImage(
            model = model,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
    }
}
