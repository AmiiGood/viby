package com.sweetcode.viby.assistant

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import ai.picovoice.porcupine.PorcupineManager
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.sweetcode.viby.data.MusicRepository
import com.sweetcode.viby.model.Song
import com.sweetcode.viby.playback.PlaybackService
import java.util.Locale

/** Asistente de voz "Viby": wake word (Porcupine) → comando (SpeechRecognizer) → acción + voz (TTS). */
class AssistantService : Service() {

    private val mainHandler = Handler(Looper.getMainLooper())
    private lateinit var audioManager: AudioManager
    private lateinit var musicRepo: MusicRepository

    private var porcupineManager: PorcupineManager? = null
    private var speechRecognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false

    private var controller: MediaController? = null
    private var controllerFuture: ListenableFuture<MediaController>? = null

    @Volatile private var library: List<Song> = emptyList()
    private var listening = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        musicRepo = MusicRepository(applicationContext)
        createChannel()
        startForegroundCompat()

        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            AssistantController.updateStatus("Falta permiso de micrófono")
            stopSelf()
            return
        }

        loadLibraryAsync()
        initTts()
        initController()
        initSpeech()
        initPorcupine()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    // ---- Inicialización ----

    private fun loadLibraryAsync() {
        Thread {
            val folder = musicRepo.savedFolderUri()
            library = if (folder != null) musicRepo.loadCache(folder) ?: emptyList() else emptyList()
        }.start()
    }

    private fun initTts() {
        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale("es", "MX")
                ttsReady = true
            }
        }
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onDone(utteranceId: String?) { mainHandler.post { resumeWakeWord() } }
            @Deprecated("deprecated") override fun onError(utteranceId: String?) {
                mainHandler.post { resumeWakeWord() }
            }
        })
    }

    private fun initController() {
        val token = SessionToken(this, ComponentName(this, PlaybackService::class.java))
        controllerFuture = MediaController.Builder(this, token).buildAsync().also { future ->
            future.addListener({ controller = future.get() }, MoreExecutors.directExecutor())
        }
    }

    private fun initSpeech() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            AssistantController.updateStatus("Este teléfono no tiene reconocimiento de voz")
            return
        }
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
            setRecognitionListener(recognitionListener)
        }
    }

    private fun initPorcupine() {
        val key = AssistantController.accessKey(this)
        if (key.isBlank()) {
            AssistantController.updateStatus("Falta el access key de Picovoice")
            stopSelf()
            return
        }
        try {
            porcupineManager = PorcupineManager.Builder()
                .setAccessKey(key)
                .setKeywordPath(KEYWORD_ASSET)
                .setSensitivity(0.6f)
                .build(applicationContext) { mainHandler.post { onWakeWord() } }
            porcupineManager?.start()
            AssistantController.updateStatus("Escuchando \"Viby\"…")
        } catch (e: Exception) {
            AssistantController.updateStatus("Error: ${e.message ?: "no se pudo iniciar"}")
            stopSelf()
        }
    }

    // ---- Flujo de escucha ----

    private fun onWakeWord() {
        if (listening) return
        listening = true
        runCatching { porcupineManager?.stop() } // libera el micrófono para el reconocimiento
        AssistantController.updateStatus("Te escucho…")
        beep()
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-MX")
            .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        runCatching { speechRecognizer?.startListening(intent) }
    }

    private val recognitionListener = object : RecognitionListener {
        override fun onResults(results: Bundle?) {
            val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
            if (text.isNullOrBlank()) speak("No te entendí") else handleText(text)
        }

        override fun onError(error: Int) {
            speak("No te escuché bien")
        }

        override fun onReadyForSpeech(params: Bundle?) {}
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {}
        override fun onPartialResults(partialResults: Bundle?) {}
        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    private fun resumeWakeWord() {
        listening = false
        runCatching { porcupineManager?.start() }
        AssistantController.updateStatus("Escuchando \"Viby\"…")
    }

    // ---- Ejecución de comandos ----

    private fun handleText(text: String) {
        when (val cmd = parseVoiceCommand(text)) {
            VoiceCommand.Next -> { controller?.seekToNextMediaItem(); speak("Siguiente") }
            VoiceCommand.Previous -> { controller?.seekToPreviousMediaItem(); speak("Anterior") }
            VoiceCommand.Pause -> { controller?.pause(); speak("Pausado") }
            VoiceCommand.Resume -> { controller?.play(); speak("Reproduciendo") }
            VoiceCommand.WhatSong -> announceCurrentSong()
            VoiceCommand.VolumeUp -> {
                audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI)
                speak("Subiendo volumen")
            }
            VoiceCommand.VolumeDown -> {
                audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI)
                speak("Bajando volumen")
            }
            is VoiceCommand.Play -> playQuery(cmd.query)
            VoiceCommand.Unknown -> speak("No entendí qué quieres")
        }
    }

    private fun announceCurrentSong() {
        val md = controller?.currentMediaItem?.mediaMetadata
        val title = md?.title
        if (title != null) {
            val artist = md.artist ?: "artista desconocido"
            speak("Estás escuchando $title de $artist")
        } else {
            speak("Ahora mismo no hay nada sonando")
        }
    }

    private fun playQuery(query: String) {
        val songs = library
        if (songs.isEmpty()) {
            speak("No tengo tu biblioteca cargada todavía")
            return
        }
        val match = songs.firstOrNull { it.title.contains(query, true) }
            ?: songs.firstOrNull { "${it.artist} ${it.title}".contains(query, true) }
            ?: songs.firstOrNull { it.artist.contains(query, true) }
        if (match == null) {
            speak("No encontré $query en tu biblioteca")
            return
        }
        val index = songs.indexOf(match)
        controller?.apply {
            setMediaItems(songs.map { it.toMediaItem() }, index, 0L)
            prepare()
            play()
        }
        speak("Reproduciendo ${match.title}")
    }

    // ---- TTS / utilidades ----

    private fun speak(text: String) {
        if (ttsReady) {
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "viby")
        } else {
            resumeWakeWord() // sin voz disponible, igual reanuda la escucha
        }
    }

    private fun beep() {
        runCatching {
            ToneGenerator(AudioManager.STREAM_MUSIC, 70).apply {
                startTone(ToneGenerator.TONE_PROP_BEEP, 120)
                mainHandler.postDelayed({ release() }, 250)
            }
        }
    }

    private fun startForegroundCompat() {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Viby está escuchando")
            .setContentText("Di \"Viby\" seguido de tu orden")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } else {
            startForeground(NOTIF_ID, notification)
        }
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "Asistente de voz", NotificationManager.IMPORTANCE_LOW,
            ).apply { description = "Viby escuchando por voz" }
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        runCatching { porcupineManager?.stop() }
        runCatching { porcupineManager?.delete() }
        runCatching { speechRecognizer?.destroy() }
        runCatching { tts?.shutdown() }
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controller = null
        super.onDestroy()
    }

    private fun Song.toMediaItem(): MediaItem =
        MediaItem.Builder()
            .setUri(uri)
            .setMediaId(id)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setArtist(artist)
                    .setAlbumTitle(album)
                    .setArtworkUri(uri)
                    .build()
            )
            .build()

    companion object {
        private const val CHANNEL_ID = "viby_assistant"
        private const val NOTIF_ID = 3001
        private const val KEYWORD_ASSET = "Viby.ppn"
    }
}
