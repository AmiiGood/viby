package com.sweetcode.viby.playback

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.sweetcode.viby.MainActivity
import com.sweetcode.viby.assistant.VibyVoice
import com.sweetcode.viby.data.coverThumbFile
import com.sweetcode.viby.radio.RadioRepository
import com.sweetcode.viby.radio.RadioSettings
import com.sweetcode.viby.radio.VibyTts
import com.sweetcode.viby.widget.updateVibyWidget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Servicio de reproducción en segundo plano (Media3).
 * Da control desde la notificación y la pantalla bloqueada de forma automática.
 */
@UnstableApi
class PlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    // Viby FM: DJ que habla noticias entre canciones.
    private val radioSettings by lazy { RadioSettings(applicationContext) }
    private val radioRepo by lazy { RadioRepository(applicationContext) }
    private var exo: ExoPlayer? = null
    private var tts: VibyTts? = null
    private var songsSinceDj = 0
    private var djSpeaking = false

    override fun onCreate() {
        super.onCreate()
        val player = ExoPlayer.Builder(this)
            .setHandleAudioBecomingNoisy(true) // pausa al desconectar audífonos
            .build()
        exo = player
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

        // Pre-calienta el TTS si Viby FM está activo (evita que el 1er corte salga mudo).
        if (radioSettings.enabled) tts = VibyTts(applicationContext)
    }

    private val widgetListener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            refreshWidget()
            maybePlayDj(reason)
        }
        override fun onIsPlayingChanged(isPlaying: Boolean) = refreshWidget()
        override fun onPlaybackStateChanged(playbackState: Int) = refreshWidget()
    }

    /** Cada N canciones (fin natural o salto), el DJ baja la música y da una noticia. */
    private fun maybePlayDj(reason: Int) {
        // Cuenta tanto cuando la canción termina sola como cuando el usuario salta.
        val counts = reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO ||
            reason == Player.MEDIA_ITEM_TRANSITION_REASON_SEEK
        if (!counts || djSpeaking || !radioSettings.enabled) return
        // playWhenReady (no isPlaying): al saltar, isPlaying queda false un instante
        // por el buffering, pero la intención de reproducir se mantiene.
        val player = mediaSession?.player ?: return
        if (!player.playWhenReady) return
        songsSinceDj++
        if (songsSinceDj < radioSettings.everyNSongs) return
        songsSinceDj = 0
        scope.launch {
            val segment = withContext(Dispatchers.IO) { radioRepo.nextSegment() } ?: return@launch
            playDj(segment.script)
        }
    }

    private fun playDj(script: String) {
        val e = exo ?: return
        djSpeaking = true
        val prevVolume = e.volume
        e.volume = 0.12f // ducking: baja la música mientras habla
        if (tts == null) tts = VibyTts(applicationContext)
        val text = "$script ${VibyVoice.resume()}"
        tts?.speak(text) {
            scope.launch {
                e.volume = prevVolume
                djSpeaking = false
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
        tts?.release()
        tts = null
        exo = null
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        super.onDestroy()
    }

    companion object {
        /** Sesión de audio del reproductor (0 = aún no disponible). */
        var audioSessionId: Int = 0
            private set

        // Acciones que manda el widget al servicio.
        const val ACTION_WIDGET_PREV = "com.sweetcode.viby.WIDGET_PREV"
        const val ACTION_WIDGET_NEXT = "com.sweetcode.viby.WIDGET_NEXT"
        const val ACTION_WIDGET_TOGGLE = "com.sweetcode.viby.WIDGET_TOGGLE"
    }
}
