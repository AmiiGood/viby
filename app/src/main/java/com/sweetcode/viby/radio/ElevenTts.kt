package com.sweetcode.viby.radio

import android.content.Context
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/**
 * Voz neuronal de ElevenLabs para el DJ. Sintetiza el texto a MP3 y lo guarda
 * en caché en disco (una vez por frase): la primera vez necesita internet, luego
 * se reproduce offline. Devuelve el archivo de audio o null si falla.
 */
class ElevenTts(
    private val http: OkHttpClient = OkHttpClient.Builder()
        .callTimeout(60, TimeUnit.SECONDS)
        .build(),
) {
    fun synthToCache(context: Context, apiKey: String, voiceId: String, text: String): File? {
        if (apiKey.isBlank() || text.isBlank()) return null
        val dir = File(context.filesDir, "dj_audio").apply { mkdirs() }
        val cached = File(dir, md5("$voiceId|$text") + ".mp3")
        if (cached.exists() && cached.length() > 0) return cached

        val body = JSONObject().apply {
            put("text", text)
            put("model_id", "eleven_multilingual_v2")
            put("voice_settings", JSONObject().apply {
                put("stability", 0.4)
                put("similarity_boost", 0.8)
                put("style", 0.45)
                put("use_speaker_boost", true)
            })
        }

        val request = Request.Builder()
            .url("https://api.elevenlabs.io/v1/text-to-speech/$voiceId?output_format=mp3_44100_128")
            .header("xi-api-key", apiKey)
            .header("accept", "audio/mpeg")
            .header("content-type", "application/json")
            .post(body.toString().toRequestBody(JSON))
            .build()

        return runCatching {
            http.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) return null
                val bytes = resp.body?.bytes() ?: return null
                if (bytes.isEmpty()) return null
                cached.writeBytes(bytes)
                cached
            }
        }.getOrNull()
    }

    private fun md5(s: String): String =
        MessageDigest.getInstance("MD5").digest(s.toByteArray())
            .joinToString("") { "%02x".format(it) }

    companion object {
        private val JSON = "application/json; charset=utf-8".toMediaType()
    }
}
