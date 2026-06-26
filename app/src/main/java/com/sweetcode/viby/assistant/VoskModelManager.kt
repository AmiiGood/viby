package com.sweetcode.viby.assistant

import android.content.Context
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.zip.ZipInputStream

/** Descarga e instala (una vez) el modelo de voz en español de Vosk. */
object VoskModelManager {

    private const val MODEL_URL = "https://alphacephei.com/vosk/models/vosk-model-small-es-0.42.zip"
    private val client = OkHttpClient()

    private fun baseDir(context: Context) = File(context.filesDir, "vosk-model-es")
    private fun marker(context: Context) = File(baseDir(context), ".model_path")

    fun installedModelPath(context: Context): String? {
        val m = marker(context)
        if (!m.exists()) return null
        val path = m.readText().trim()
        return if (File(path).exists()) path else null
    }

    /** Devuelve la ruta del modelo, descargándolo/instalándolo si hace falta. */
    fun ensureModel(context: Context, onStatus: (String) -> Unit): String? {
        installedModelPath(context)?.let { return it }

        val base = baseDir(context)
        base.mkdirs()
        val zip = File(context.cacheDir, "vosk-model.zip")
        return try {
            onStatus("Descargando modelo de voz (~40 MB)…")
            client.newCall(Request.Builder().url(MODEL_URL).build()).execute().use { response ->
                if (!response.isSuccessful) error("HTTP ${response.code}")
                val body = response.body ?: error("Respuesta vacía")
                zip.outputStream().use { out -> body.byteStream().use { it.copyTo(out) } }
            }

            onStatus("Instalando modelo…")
            val rootName = unzip(zip, base)
            zip.delete()

            val modelPath = File(base, rootName).absolutePath
            marker(context).writeText(modelPath)
            modelPath
        } catch (e: Exception) {
            zip.delete()
            null
        }
    }

    /** Descomprime el zip en [dest] y devuelve el nombre de la carpeta raíz del modelo. */
    private fun unzip(zip: File, dest: File): String {
        var rootName = ""
        ZipInputStream(zip.inputStream().buffered()).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                val name = entry.name
                if (rootName.isEmpty()) rootName = name.substringBefore('/')
                val outFile = File(dest, name)
                if (entry.isDirectory) {
                    outFile.mkdirs()
                } else {
                    outFile.parentFile?.mkdirs()
                    outFile.outputStream().use { zis.copyTo(it) }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
        return rootName
    }
}
