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
 * Cargador de carátulas para Media3.
 *
 * Dos casos: los archivos de audio locales llevan la imagen embebida (APIC) y hay
 * que extraerla; las emisoras de radio no tienen nada embebido, asi que su
 * caratula llega ya descargada como fichero de imagen y solo hay que decodificarla.
 * Cachea la ultima para no repetir el trabajo.
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
            val bmp = decodeImageFile(uri) ?: extractEmbedded(uri)
            cacheUri = uri.toString()
            cacheBitmap = bmp
            bmp
        }
    }

    /**
     * Caratula de emisora: ya es una imagen en disco, descargada por
     * NowPlayingArtwork. MediaMetadataRetriever no sirve aqui porque busca una
     * imagen DENTRO de un archivo de audio, y esto ya es la imagen.
     */
    private fun decodeImageFile(uri: Uri): Bitmap? {
        if (uri.scheme != "file") return null
        val path = uri.path ?: return null
        if (!path.endsWith(".jpg", ignoreCase = true) &&
            !path.endsWith(".png", ignoreCase = true)
        ) return null
        return BitmapFactory.decodeFile(path)
    }

    /** Archivo de audio local: la portada va embebida en las etiquetas. */
    private fun extractEmbedded(uri: Uri): Bitmap {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)
            val bytes = retriever.embeddedPicture ?: error("Sin carátula embebida")
            return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                ?: error("No se pudo decodificar la carátula")
        } finally {
            runCatching { retriever.release() }
        }
    }
}
