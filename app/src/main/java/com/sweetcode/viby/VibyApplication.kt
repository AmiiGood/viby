package com.sweetcode.viby

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.sweetcode.viby.ui.components.AudioCoverFetcher
import com.sweetcode.viby.ui.components.AudioCoverKeyer

/** Configura Coil con el extractor de carátulas embebidas + caché en memoria y disco. */
class VibyApplication : Application(), ImageLoaderFactory {

    override fun onCreate() {
        super.onCreate()
        // Limpia lo que dejaron funciones ya eliminadas: el modelo de voz Vosk
        // (~40MB) del asistente y los datos de Viby FM (noticias y clips del DJ).
        runCatching {
            java.io.File(filesDir, "vosk-model-es").deleteRecursively()
            java.io.File(filesDir, "dj_audio").deleteRecursively()
            java.io.File(filesDir, "radio_segments.json").delete()
            deleteSharedPreferences("viby_radio")
            deleteSharedPreferences("viby_radio_state")
        }
    }

    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            .components {
                add(AudioCoverKeyer())
                add(AudioCoverFetcher.Factory(applicationContext))
            }
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.25)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("coil_covers"))
                    .maxSizeBytes(80L * 1024 * 1024) // 80 MB de carátulas en disco
                    .build()
            }
            .crossfade(true)
            .build()
}
