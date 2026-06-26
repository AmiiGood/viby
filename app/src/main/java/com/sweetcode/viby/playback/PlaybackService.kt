package com.sweetcode.viby.playback

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

/**
 * Servicio de reproducción en segundo plano (Media3).
 * Da control desde la notificación y la pantalla bloqueada de forma automática.
 */
@UnstableApi
class PlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        val player = ExoPlayer.Builder(this)
            .setHandleAudioBecomingNoisy(true) // pausa al desconectar audífonos
            .build()
        // Sesión de audio fija para que el ecualizador del sistema pueda engancharse.
        val sessionId = (getSystemService(Context.AUDIO_SERVICE) as AudioManager)
            .generateAudioSessionId()
        player.setAudioSessionId(sessionId)
        audioSessionId = sessionId
        EqualizerManager.init(applicationContext, sessionId)
        val sessionPlayer = WrappingForwardingPlayer(player)
        mediaSession = MediaSession.Builder(this, sessionPlayer)
            .setBitmapLoader(EmbeddedArtBitmapLoader(applicationContext))
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? =
        mediaSession

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
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
    }
}
