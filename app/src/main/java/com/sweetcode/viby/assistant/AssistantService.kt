package com.sweetcode.viby.assistant

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.ToneGenerator
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.AutomaticGainControl
import android.media.audiofx.NoiseSuppressor
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
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.sweetcode.viby.data.MusicRepository
import com.sweetcode.viby.model.Song
import com.sweetcode.viby.playback.PlaybackService
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import java.util.Locale

/** Asistente "Viby": wake word offline (Vosk) → comando (SpeechRecognizer) → acción + voz (TTS). */
class AssistantService : Service() {

    private val mainHandler = Handler(Looper.getMainLooper())
    private lateinit var audioManager: AudioManager
    private lateinit var musicRepo: MusicRepository

    private var voskModel: Model? = null
    private var voskRecognizer: Recognizer? = null

    // Captura propia del micrófono (con cancelación de eco) para oír sobre la música.
    private var audioRecord: AudioRecord? = null
    private var captureThread: Thread? = null
    @Volatile private var capturing = false
    private var aec: AcousticEchoCanceler? = null
    private var ns: NoiseSuppressor? = null
    private var agc: AutomaticGainControl? = null

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
        initWakeWord()
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

    private fun initWakeWord() {
        AssistantController.updateStatus("Preparando…")
        Thread {
            val modelPath = VoskModelManager.ensureModel(applicationContext) { status ->
                AssistantController.updateStatus(status)
            }
            if (modelPath == null) {
                AssistantController.updateStatus("No se pudo descargar el modelo de voz")
                mainHandler.post { stopSelf() }
                return@Thread
            }
            try {
                voskModel = Model(modelPath)
                voskRecognizer = Recognizer(voskModel, SAMPLE_RATE, WAKE_GRAMMAR)
                mainHandler.post {
                    startWakeCapture()
                    AssistantController.updateStatus("Escuchando \"Oye Viby\"…")
                }
            } catch (e: Exception) {
                AssistantController.updateStatus("Error: ${e.message ?: "modelo inválido"}")
                mainHandler.post { stopSelf() }
            }
        }.start()
    }

    // ---- Captura del micrófono con cancelación de eco ----

    private fun startWakeCapture() {
        if (capturing) return
        val recognizer = voskRecognizer ?: return
        runCatching { recognizer.reset() }

        val minBuf = AudioRecord.getMinBufferSize(
            SAMPLE_RATE_INT, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
        ).coerceAtLeast(4096)

        val record = try {
            AudioRecord(
                MediaRecorder.AudioSource.VOICE_COMMUNICATION, // habilita AEC referida a la reproducción
                SAMPLE_RATE_INT,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                minBuf * 2,
            )
        } catch (e: Exception) {
            AssistantController.updateStatus("No se pudo abrir el micrófono")
            return
        }
        if (record.state != AudioRecord.STATE_INITIALIZED) {
            record.release()
            AssistantController.updateStatus("No se pudo abrir el micrófono")
            return
        }

        val sessionId = record.audioSessionId
        if (AcousticEchoCanceler.isAvailable()) {
            aec = AcousticEchoCanceler.create(sessionId)?.apply { setEnabled(true) }
        }
        if (NoiseSuppressor.isAvailable()) {
            ns = NoiseSuppressor.create(sessionId)?.apply { setEnabled(true) }
        }
        if (AutomaticGainControl.isAvailable()) {
            agc = AutomaticGainControl.create(sessionId)?.apply { setEnabled(true) }
        }

        audioRecord = record
        record.startRecording()
        capturing = true
        controller?.volume = LISTEN_VOLUME // baja un poco la música para oír mejor el wake word

        captureThread = Thread {
            val buffer = ShortArray(minBuf)
            while (capturing) {
                val n = record.read(buffer, 0, buffer.size)
                if (n > 0) {
                    // El texto reconocido debe ser EXACTAMENTE la palabra clave (no "contiene").
                    val matched = if (recognizer.acceptWaveForm(buffer, n)) {
                        wakeMatched(recognizer.result, "text")
                    } else {
                        wakeMatched(recognizer.partialResult, "partial")
                    }
                    if (matched) {
                        mainHandler.post { if (!listening) onWakeWord() }
                        break
                    }
                }
            }
        }.also { it.start() }
    }

    private fun stopWakeCapture() {
        capturing = false
        runCatching { captureThread?.join(500) }
        captureThread = null
        runCatching { audioRecord?.stop() }
        runCatching { audioRecord?.release() }
        audioRecord = null
        runCatching { aec?.release() }; aec = null
        runCatching { ns?.release() }; ns = null
        runCatching { agc?.release() }; agc = null
    }

    /** Verdadero si el texto reconocido (campo [field]) es exactamente una palabra clave. */
    private fun wakeMatched(json: String?, field: String): Boolean {
        if (json == null) return false
        return try {
            JSONObject(json).optString(field).trim().lowercase() in WAKE_WORDS
        } catch (e: Exception) {
            false
        }
    }

    // ---- Flujo de comando ----

    private fun onWakeWord() {
        if (listening) return
        listening = true
        stopWakeCapture()                       // libera el micrófono
        controller?.volume = COMMAND_VOLUME     // baja más la música mientras te escucha
        AssistantController.updateStatus("Te escucho…")
        // Pausa breve para que el micro se libere; el beep suena cuando ya está listo (onReadyForSpeech).
        mainHandler.postDelayed({
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-MX")
                .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
            runCatching { speechRecognizer?.startListening(intent) }
        }, 350)
    }

    private val recognitionListener = object : RecognitionListener {
        override fun onResults(results: Bundle?) {
            val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
            if (text.isNullOrBlank()) speak("No te entendí") else handleText(text)
        }
        override fun onError(error: Int) { speak("No te escuché bien") }
        override fun onReadyForSpeech(params: Bundle?) {
            // Ya está escuchando de verdad: ahora sí suena el beep como señal de "habla".
            beep()
        }
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {}
        override fun onPartialResults(partialResults: Bundle?) {}
        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    private fun resumeWakeWord() {
        listening = false
        startWakeCapture() // vuelve a poner la música al 70% (volumen de escucha)
        AssistantController.updateStatus("Escuchando \"Oye Viby\"…")
    }

    // ---- Ejecución de comandos ----

    private fun handleText(text: String) {
        when (val cmd = parseVoiceCommand(text)) {
            VoiceCommand.Next -> { controller?.seekToNextMediaItem(); speak(VibyVoice.next()) }
            VoiceCommand.Previous -> { controller?.seekToPreviousMediaItem(); speak(VibyVoice.previous()) }
            VoiceCommand.Pause -> { controller?.pause(); speak(VibyVoice.pause()) }
            VoiceCommand.Resume -> { controller?.play(); speak(VibyVoice.resume()) }
            VoiceCommand.WhatSong -> announceCurrentSong()
            VoiceCommand.VolumeUp -> {
                audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI)
                speak(VibyVoice.volumeUp())
            }
            VoiceCommand.VolumeDown -> {
                audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI)
                speak(VibyVoice.volumeDown())
            }
            is VoiceCommand.Play -> playQuery(cmd.query)
            VoiceCommand.Unknown -> speak(VibyVoice.unknown())
        }
    }

    private fun announceCurrentSong() {
        val md = controller?.currentMediaItem?.mediaMetadata
        val title = md?.title
        if (title != null) {
            speak(VibyVoice.nowPlaying(title.toString(), (md.artist ?: "artista desconocido").toString()))
        } else {
            speak(VibyVoice.nothing())
        }
    }

    private fun playQuery(query: String) {
        val songs = library
        if (songs.isEmpty()) {
            speak(VibyVoice.noLibrary())
            return
        }
        val match = songs.firstOrNull { it.title.contains(query, true) }
            ?: songs.firstOrNull { "${it.artist} ${it.title}".contains(query, true) }
            ?: songs.firstOrNull { it.artist.contains(query, true) }
        if (match == null) {
            speak(VibyVoice.notFound(query))
            return
        }
        val index = songs.indexOf(match)
        controller?.apply {
            setMediaItems(songs.map { it.toMediaItem() }, index, 0L)
            prepare()
            play()
        }
        speak(VibyVoice.playing(match.title))
    }

    // ---- Audio focus (duck) / TTS / utilidades ----

    private fun speak(text: String) {
        if (ttsReady) {
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "viby")
        } else {
            resumeWakeWord()
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
            .setContentText("Di \"Oye Viby\" seguido de tu orden")
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
        runCatching { controller?.volume = 1f } // restaura el volumen al apagar el asistente
        stopWakeCapture()
        runCatching { voskRecognizer?.close() }
        runCatching { voskModel?.close() }
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
        private const val SAMPLE_RATE = 16000.0f
        private const val SAMPLE_RATE_INT = 16000
        private const val LISTEN_VOLUME = 0.7f  // música mientras escucha "Viby"
        private const val COMMAND_VOLUME = 0.15f // música mientras dictas la orden
        // Frase de dos palabras: más fácil de detectar y muchos menos falsos positivos.
        private const val WAKE_GRAMMAR = "[\"oye vivi\", \"oye bibi\", \"oye vivy\", \"oye vibi\", \"[unk]\"]"
        private val WAKE_WORDS = setOf("oye vivi", "oye bibi", "oye vivy", "oye vibi")
    }
}
