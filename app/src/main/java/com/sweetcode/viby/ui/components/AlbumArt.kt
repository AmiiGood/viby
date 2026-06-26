package com.sweetcode.viby.ui.components

import android.content.Context
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Una entrada de caché (la carátula puede ser null = el archivo no tiene). */
private class ArtEntry(val bitmap: ImageBitmap?)

/** Caché en memoria de carátulas para que el scroll no re-extraiga de cada archivo. */
private object AlbumArtCache {
    private val maxKb = (Runtime.getRuntime().maxMemory() / 1024 / 8).toInt().coerceAtLeast(4096)
    private val cache = object : LruCache<String, ArtEntry>(maxKb) {
        override fun sizeOf(key: String, value: ArtEntry): Int =
            value.bitmap?.let { (it.asAndroidBitmap().byteCount / 1024).coerceAtLeast(1) } ?: 1
    }

    fun get(key: String): ArtEntry? = cache.get(key)
    fun put(key: String, entry: ArtEntry) = cache.put(key, entry)
}

/** Extrae la carátula embebida del audio, con caché. */
@Composable
fun rememberAlbumArt(uri: Uri): ImageBitmap? {
    val context = LocalContext.current
    val key = uri.toString()
    val bitmap by produceState<ImageBitmap?>(initialValue = AlbumArtCache.get(key)?.bitmap, key) {
        val cached = AlbumArtCache.get(key)
        if (cached != null) {
            value = cached.bitmap // importante: refresca al cambiar de canción aunque esté cacheada
            return@produceState
        }
        value = null // limpia la carátula anterior mientras carga la nueva
        val bmp = withContext(Dispatchers.IO) { loadEmbeddedArt(context, uri) }
        AlbumArtCache.put(key, ArtEntry(bmp))
        value = bmp
    }
    return bitmap
}

private fun loadEmbeddedArt(context: Context, uri: Uri): ImageBitmap? {
    val retriever = MediaMetadataRetriever()
    return try {
        retriever.setDataSource(context, uri)
        retriever.embeddedPicture?.let { bytes ->
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
        }
    } catch (e: Exception) {
        null
    } finally {
        runCatching { retriever.release() }
    }
}

/** Carátula cuadrada con fallback a un ícono si el archivo no trae imagen. */
@Composable
fun AlbumArt(uri: Uri, modifier: Modifier = Modifier) {
    val bitmap = rememberAlbumArt(uri)
    Box(
        modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        val bmp = bitmap
        if (bmp != null) {
            Image(
                bitmap = bmp,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Icon(
                imageVector = Icons.Rounded.MusicNote,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(28.dp),
            )
        }
    }
}
