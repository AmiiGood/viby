package com.sweetcode.viby.playback

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
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
import com.sweetcode.viby.radio.NowPlayingArtwork
import com.sweetcode.viby.widget.updateVibyWidget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine

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
    private val artwork by lazy { NowPlayingArtwork(applicationContext) }
    private var artworkJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        val player = ExoPlayer.Builder(this)
            .setHandleAudioBecomingNoisy(true) // pausa al desconectar audífonos
            // Sin esto el reproductor no participaba del foco de audio: ni callaba
            // a otras apps al empezar, ni se pausaba cuando otra (o el preview de
            // descargas) reclamaba el foco. Resultado: dos audios a la vez.
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                /* handleAudioFocus = */ true,
            )
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

        vigilarTemporizador(player)
    }

    /**
     * Cumple el temporizador de apagado.
     *
     * Se hace aquí y no en la pantalla porque quien lo programa deja el móvil y
     * se duerme: el temporizador tiene que correr con la reproducción.
     */
    private fun vigilarTemporizador(player: ExoPlayer) {
        scope.launch {
            // collectLatest: si se reprograma o se cancela, la espera anterior se
            // descarta en vez de pausar más tarde por sorpresa.
            SleepTimer.hasta.collectLatest { hasta ->
                if (hasta == null) return@collectLatest
                val falta = hasta - System.currentTimeMillis()
                if (falta > 0) delay(falta)
                mediaSession?.player?.pause()
                SleepTimer.cancelar()
            }
        }
        scope.launch {
            // Que lo pare el propio reproductor al final de la pista sale mejor
            // que escuchar el cambio de canción: así la siguiente ni empieza.
            SleepTimer.alTerminarPista.collect { activo ->
                player.pauseAtEndOfMediaItems = activo
            }
        }
        scope.launch {
            // Cumplido el "al terminar la canción", se apaga solo; si no, el
            // reproductor se quedaría parándose al final de cada pista.
            SleepTimer.alTerminarPista.collectLatest { activo ->
                if (!activo) return@collectLatest
                esperarAlFinalDeLaPista(player)
                player.pauseAtEndOfMediaItems = false
                SleepTimer.cancelar()
            }
        }
    }

    /** Espera a que el reproductor se pare por haber llegado al final de la pista. */
    private suspend fun esperarAlFinalDeLaPista(player: ExoPlayer) {
        var listener: Player.Listener? = null
        try {
            suspendCancellableCoroutine { cont ->
                val suyo = object : Player.Listener {
                    override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                        // Solo cuenta el final de la pista: si se pausa a mano, el
                        // temporizador sigue puesto para cuando se reanude.
                        val finDePista =
                            reason == Player.PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM
                        if (!playWhenReady && finDePista && cont.isActive) cont.resume(Unit) {}
                    }
                }
                listener = suyo
                player.addListener(suyo)
            }
        } finally {
            listener?.let { player.removeListener(it) }
        }
    }

    private val widgetListener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            // Solo al cambiar de pista de verdad: actualizar los metadatos ICY
            // tambien pasa por aqui, y ahi el titulo ya es el de la cancion.
            val id = mediaItem?.mediaId
            if (id != currentItemId) {
                currentItemId = id
                stationName = mediaItem?.mediaMetadata?.title?.toString()
                cacheStationLogo(mediaItem)
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
        resolveArtwork(title)
    }

    /**
     * El logo de la emisora llega como URL http, y ni la notificacion ni el widget
     * saben cargar eso: se baja a disco y se apunta al fichero. Es lo que se ve
     * mientras no se sabe que cancion suena.
     */
    private fun cacheStationLogo(item: MediaItem?) {
        val url = item?.mediaMetadata?.artworkUri?.takeIf { it.scheme?.startsWith("http") == true }
            ?: return
        val mediaId = item.mediaId
        scope.launch {
            val file = artwork.logo(url.toString()) ?: return@launch
            applyArtwork(file) { it.mediaId == mediaId }
        }
    }

    /**
     * Busca la caratula del disco al que pertenece lo que suena. Es lo unico que
     * sale a la red por cancion, asi que se cancela la busqueda anterior: si la
     * emisora cambio de tema, la caratula que venia en camino ya no vale.
     */
    private fun resolveArtwork(title: String) {
        artworkJob?.cancel()
        artworkJob = scope.launch {
            val file = artwork.resolve(title) ?: return@launch
            applyArtwork(file) { it.mediaMetadata.title?.toString() == title }
        }
    }

    /** Aplica la caratula solo si [stillValid]: entre la busqueda y la respuesta
     *  la emisora ha podido cambiar de cancion. */
    private fun applyArtwork(file: java.io.File, stillValid: (MediaItem) -> Boolean) {
        val player = mediaSession?.player ?: return
        val item = player.currentMediaItem ?: return
        if (!stillValid(item)) return
        val uri = Uri.fromFile(file)
        if (item.mediaMetadata.artworkUri == uri) return
        val updated = item.buildUpon()
            .setMediaMetadata(item.mediaMetadata.buildUpon().setArtworkUri(uri).build())
            .build()
        runCatching { player.replaceMediaItem(player.currentMediaItemIndex, updated) }
        refreshWidget()
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
            // Radio: la caratula ya es un fichero de imagen. Biblioteca: miniatura
            // extraida del propio archivo de audio.
            val downloaded = player.currentMediaItem?.mediaMetadata?.artworkUri
                ?.takeIf { it.scheme == "file" }?.path
                ?.takeIf { java.io.File(it).exists() }
            val thumb = downloaded ?: uri?.let {
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
