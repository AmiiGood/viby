package com.sweetcode.viby.playback

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Metadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.extractor.metadata.icy.IcyInfo
import com.sweetcode.viby.MainActivity
import com.sweetcode.viby.data.coverThumbFile
import com.sweetcode.viby.widget.updateVibyWidget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Servicio de reproducción en segundo plano (Media3).
 * Da control desde la notificación y la pantalla bloqueada de forma automática.
 */
@UnstableApi
class PlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    // Reintentos de reconexion del stream en curso; se ponen a cero al volver a sonar.
    private var streamRetries = 0

    // Nombre de la emisora en curso: los metadatos ICY pisan el titulo, asi que
    // hay que guardarlo antes de la primera actualizacion para no perderlo.
    private var stationName: String? = null
    private var currentItemId: String? = null

    override fun onCreate() {
        super.onCreate()
        val player = ExoPlayer.Builder(this)
            .setHandleAudioBecomingNoisy(true) // pausa al desconectar audífonos
            .build()
        val sessionId = (getSystemService(Context.AUDIO_SERVICE) as AudioManager)
            .generateAudioSessionId()
        player.setAudioSessionId(sessionId)
        audioSessionId = sessionId
        EqualizerManager.init(applicationContext, sessionId)
        player.addListener(widgetListener)

        val sessionPlayer = WrappingForwardingPlayer(player)
        // Al tocar la notificación / lockscreen se abre la app.
        val openApp = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_SINGLE_TOP },
            PendingIntent.FLAG_IMMUTABLE,
        )
        mediaSession = MediaSession.Builder(this, sessionPlayer)
            .setSessionActivity(openApp)
            .setBitmapLoader(EmbeddedArtBitmapLoader(applicationContext))
            .build()
    }

    private val widgetListener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            // Solo al cambiar de pista de verdad: actualizar los metadatos ICY
            // tambien pasa por aqui, y ahi el titulo ya es el de la cancion.
            val id = mediaItem?.mediaId
            if (id != currentItemId) {
                currentItemId = id
                stationName = mediaItem?.mediaMetadata?.title?.toString()
            }
            refreshWidget()
        }
        override fun onMetadata(metadata: Metadata) = applyIcyMetadata(metadata)
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (isPlaying) streamRetries = 0
            refreshWidget()
        }
        override fun onPlaybackStateChanged(playbackState: Int) = refreshWidget()
        override fun onPlayerError(error: PlaybackException) = reconnectStream()
    }

    /**
     * Las emisoras Icecast mandan dentro del propio stream el titulo de lo que
     * suena (metadatos ICY). Se vuelca en el MediaItem para que lo vean la
     * notificacion, la pantalla bloqueada, el widget y la app.
     *
     * replaceMediaItem con la MISMA uri no reinicia la reproduccion: ExoPlayer
     * reutiliza la fuente y solo intercambia los metadatos. Con setMediaItem
     * volveria a conectar y se oiria el corte.
     */
    private fun applyIcyMetadata(metadata: Metadata) {
        val player = mediaSession?.player ?: return
        val item = player.currentMediaItem ?: return
        if (item.localConfiguration?.uri?.toString()?.startsWith("http") != true) return

        val title = (0 until metadata.length())
            .mapNotNull { metadata.get(it) as? IcyInfo }
            .firstNotNullOfOrNull { it.title?.trim()?.ifBlank { null } }
            ?: return
        // Muchas emisoras reenvian el mismo titulo cada pocos segundos.
        if (title == item.mediaMetadata.title?.toString()) return

        val station = stationName ?: item.mediaMetadata.title?.toString()
        val updated = item.buildUpon()
            .setMediaMetadata(
                item.mediaMetadata.buildUpon()
                    .setTitle(title)
                    .setArtist(station) // la emisora pasa a segunda linea
                    .build()
            )
            .build()
        runCatching { player.replaceMediaItem(player.currentMediaItemIndex, updated) }
    }

    /**
     * Un stream de radio se corta cada dos por tres (red inestable, servidor que
     * recicla la conexion). ExoPlayer se queda parado con el error, asi que se
     * reintenta con espera creciente en vez de dejar la emisora muda.
     *
     * Solo aplica a fuentes http: un archivo local que falla no se arregla
     * reintentando, y ahi conviene que el error se vea.
     */
    private fun reconnectStream() {
        val player = mediaSession?.player ?: return
        val uri = player.currentMediaItem?.localConfiguration?.uri?.toString().orEmpty()
        if (!uri.startsWith("http")) return
        if (streamRetries >= MAX_STREAM_RETRIES) return
        streamRetries++
        scope.launch {
            delay(RETRY_BASE_DELAY_MS * streamRetries)
            mediaSession?.player?.run {
                prepare()
                play()
            }
        }
    }

    private var updateJob: Job? = null

    private fun refreshWidget() {
        // Debounce: junta las señales rápidas y actualiza una vez con el estado final.
        updateJob?.cancel()
        updateJob = scope.launch {
            delay(200)
            val player = mediaSession?.player ?: return@launch
            val md = player.currentMediaItem?.mediaMetadata
            val uri = player.currentMediaItem?.localConfiguration?.uri
            val thumb = uri?.let {
                coverThumbFile(applicationContext, it.toString()).takeIf { f -> f.exists() }?.absolutePath
            }
            runCatching {
                updateVibyWidget(
                    applicationContext,
                    title = md?.title?.toString().orEmpty(),
                    artist = md?.artist?.toString().orEmpty(),
                    thumb = thumb,
                    playing = player.isPlaying,
                )
            }
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? =
        mediaSession

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val player = mediaSession?.player
        when (intent?.action) {
            ACTION_WIDGET_NEXT -> { player?.seekToNextMediaItem(); return START_STICKY }
            ACTION_WIDGET_PREV -> { player?.seekToPreviousMediaItem(); return START_STICKY }
            ACTION_WIDGET_TOGGLE -> {
                player?.let { if (it.isPlaying) it.pause() else it.play() }
                return START_STICKY
            }
        }
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        scope.cancel()
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        super.onDestroy()
    }

    companion object {
        /** Cuantas veces se reintenta reconectar un stream antes de rendirse. */
        private const val MAX_STREAM_RETRIES = 5
        private const val RETRY_BASE_DELAY_MS = 1500L

        /** Sesión de audio del reproductor (0 = aún no disponible). */
        var audioSessionId: Int = 0
            private set

        // Acciones que manda el widget al servicio.
        const val ACTION_WIDGET_PREV = "com.sweetcode.viby.WIDGET_PREV"
        const val ACTION_WIDGET_NEXT = "com.sweetcode.viby.WIDGET_NEXT"
        const val ACTION_WIDGET_TOGGLE = "com.sweetcode.viby.WIDGET_TOGGLE"
    }
}
