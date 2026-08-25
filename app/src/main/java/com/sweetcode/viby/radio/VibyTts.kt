package com.sweetcode.viby.radio

import android.content.Context
import android.media.AudioAttributes
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
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
                applyBestVoice() // elige la voz en español de mayor calidad disponible
                tts.setSpeechRate(0.98f)
                tts.setPitch(1.0f)
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

    /**
     * Elige la voz en español de mayor calidad instalada (las "neuronales/mejoradas"
     * de Google suenan mucho menos robóticas que la genérica por defecto).
     * Prioriza: calidad > acento (MX/US/LatAm) > que funcione sin conexión.
     */
    private fun applyBestVoice() {
        val voices = runCatching { tts.voices }.getOrNull() ?: return
        val best = voices.asSequence()
            .filter { it.locale?.language == "es" }
            .filter {
                it.features?.contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED) != true
            }
            .maxWithOrNull(
                // Preferir: voz específica "-x-" (no el alias genérico) > neuronal
                // (-network, la que NO suena robótica) > calidad > acento latino.
                compareBy<Voice> { if (it.name.contains("-x-")) 1 else 0 }
                    .thenBy { if (it.isNetworkConnectionRequired) 1 else 0 }
                    .thenBy { it.quality }
                    .thenBy { countryScore(it.locale?.country) }
            ) ?: return
        runCatching { tts.voice = best }
    }

    private fun countryScore(country: String?): Int = when (country?.uppercase()) {
        "MX" -> 3
        "US", "419" -> 2
        else -> 1
    }

    /** Habla el texto; invoca [whenDone] al terminar (o si falla). */
    fun speak(text: String, whenDone: () -> Unit) {
        if (!ready || text.isBlank()) { whenDone(); return }
        onDone = whenDone
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "viby_dj")
    }

    /** Versión suspend: se completa cuando el TTS termina de hablar. */
    suspend fun speakAwait(text: String) =
        kotlinx.coroutines.suspendCancellableCoroutine<Unit> { cont ->
            speak(text) { if (cont.isActive) cont.resumeWith(Result.success(Unit)) }
        }

    fun stop() {
        runCatching { tts.stop() }
    }

    fun release() {
        runCatching { tts.stop(); tts.shutdown() }
    }
}
