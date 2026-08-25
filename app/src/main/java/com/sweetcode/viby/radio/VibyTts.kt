package com.sweetcode.viby.radio

import android.content.Context
import android.media.AudioAttributes
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

/**
 * Envoltura simple de TextToSpeech para que el DJ hable entre canciones.
 * Voz en español; avisa cuando termina de hablar (para reanudar la música).
 */
class VibyTts(context: Context) {
    private var ready = false
    private var onDone: (() -> Unit)? = null
    private val tts: TextToSpeech

    init {
        tts = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                ready = true
                val mx = Locale("es", "MX")
                tts.language =
                    if (tts.isLanguageAvailable(mx) >= TextToSpeech.LANG_AVAILABLE) mx
                    else Locale("es")
                tts.setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
            }
        }
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onDone(utteranceId: String?) { onDone?.invoke() }
            @Deprecated("deprecated")
            override fun onError(utteranceId: String?) { onDone?.invoke() }
            override fun onError(utteranceId: String?, errorCode: Int) { onDone?.invoke() }
        })
    }

    /** Habla el texto; invoca [whenDone] al terminar (o si falla). */
    fun speak(text: String, whenDone: () -> Unit) {
        if (!ready || text.isBlank()) { whenDone(); return }
        onDone = whenDone
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "viby_dj")
    }

    fun stop() {
        runCatching { tts.stop() }
    }

    fun release() {
        runCatching { tts.stop(); tts.shutdown() }
    }
}
