package com.sweetcode.viby.playback

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.media3.common.util.BitmapLoader
import androidx.media3.common.util.UnstableApi
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import java.util.concurrent.Executors

/**
 * Cargador de carátulas para Media3: extrae la imagen embebida (APIC) del archivo
 * de audio cuyo URI llega como `artworkUri`. Así la notificación y la pantalla
 * bloqueada muestran la portada del álbum. Cachea la última para no re-extraer.
 */
@UnstableApi
class EmbeddedArtBitmapLoader(private val context: Context) : BitmapLoader {

    private val executor =
        MoreExecutors.listeningDecorator(Executors.newSingleThreadExecutor())

    @Volatile private var cacheUri: String? = null
    @Volatile private var cacheBitmap: Bitmap? = null

    override fun supportsMimeType(mimeType: String): Boolean = mimeType.startsWith("image/")

    override fun decodeBitmap(data: ByteArray): ListenableFuture<Bitmap> =
        executor.submit<Bitmap> {
            BitmapFactory.decodeByteArray(data, 0, data.size)
                ?: error("No se pudo decodificar la imagen")
        }

    override fun loadBitmap(uri: Uri): ListenableFuture<Bitmap> {
        cacheBitmap?.let { if (uri.toString() == cacheUri) return Futures.immediateFuture(it) }
        return executor.submit<Bitmap> {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(context, uri)
                val bytes = retriever.embeddedPicture ?: error("Sin carátula embebida")
                val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    ?: error("No se pudo decodificar la carátula")
                cacheUri = uri.toString()
                cacheBitmap = bmp
                bmp
            } finally {
                runCatching { retriever.release() }
            }
        }
    }
}
