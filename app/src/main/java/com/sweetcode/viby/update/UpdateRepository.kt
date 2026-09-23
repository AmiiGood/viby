package com.sweetcode.viby.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

/** De dónde salen las versiones. */
private const val REPO = "AmiiGood/viby"

/** No se consulta en cada arranque: una vez al día basta para una app así. */
private val CADA = TimeUnit.DAYS.toMillis(1)

/** Al decir "ahora no", se calla unos días en vez de para siempre. */
private val POSPUESTA = TimeUnit.DAYS.toMillis(3)

/** Una versión publicada más nueva que la instalada. */
data class Actualizacion(
    val version: String,
    val notas: String,
    val urlApk: String,
    val bytes: Long,
    val urlWeb: String,
)

/**
 * Avisa de versiones nuevas publicadas en GitHub.
 *
 * Viby se instala por sideload, así que no hay tienda que actualice sola: sin
 * esto, quien la tiene se entera solo si se le ocurre mirar el repositorio.
 */
class UpdateRepository(private val context: Context) {

    private val prefs = context.getSharedPreferences("viby_prefs", Context.MODE_PRIVATE)

    private val http by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    /** La versión que corre ahora mismo, leída del propio paquete instalado. */
    fun versionInstalada(): String = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName
    }.getOrNull().orEmpty()

    /**
     * Busca una versión más nueva, o null si no hay, si no toca mirar todavía o
     * si no se pudo consultar.
     *
     * @param forzar salta la espera entre consultas (para un botón manual).
     */
    suspend fun buscar(forzar: Boolean = false): Actualizacion? = withContext(Dispatchers.IO) {
        val ahora = System.currentTimeMillis()
        if (!forzar && ahora - prefs.getLong(KEY_ULTIMA, 0L) < CADA) return@withContext null

        val cuerpo = pedir("https://api.github.com/repos/$REPO/releases/latest")
            ?: return@withContext null
        prefs.edit().putLong(KEY_ULTIMA, ahora).apply()

        val json = runCatching { JSONObject(cuerpo) }.getOrNull() ?: return@withContext null
        val tag = json.optString("tag_name").takeIf { it.isNotBlank() } ?: return@withContext null
        if (!esMasNueva(tag, versionInstalada())) return@withContext null
        if (!forzar && pospuesta(tag, ahora)) return@withContext null

        val assets = json.optJSONArray("assets") ?: return@withContext null
        for (i in 0 until assets.length()) {
            val a = assets.getJSONObject(i)
            val url = a.optString("browser_download_url")
            if (!a.optString("name").endsWith(".apk", ignoreCase = true)) continue
            if (!esDeGitHub(url)) continue
            return@withContext Actualizacion(
                version = tag.removePrefix("v"),
                notas = notasLegibles(json.optString("body")),
                urlApk = url,
                bytes = a.optLong("size"),
                urlWeb = json.optString("html_url"),
            )
        }
        null
    }

    /** Silencia esta versión unos días. */
    fun posponer(version: String) {
        prefs.edit()
            .putString(KEY_POSPUESTA, version)
            .putLong(KEY_POSPUESTA_EN, System.currentTimeMillis())
            .apply()
    }

    private fun pospuesta(tag: String, ahora: Long): Boolean {
        val v = prefs.getString(KEY_POSPUESTA, null) ?: return false
        if (v.removePrefix("v") != tag.removePrefix("v")) return false
        return ahora - prefs.getLong(KEY_POSPUESTA_EN, 0L) < POSPUESTA
    }

    /**
     * La URL de descarga viene de la respuesta de GitHub, pero se comprueba de
     * todas formas: es un archivo que se va a instalar, y no se sigue a cualquier
     * sitio al que apunte un JSON.
     */
    private fun esDeGitHub(url: String): Boolean {
        val host = runCatching { Uri.parse(url).host }.getOrNull().orEmpty()
        return url.startsWith("https://") &&
            (host == "github.com" || host.endsWith(".github.com") ||
                host.endsWith(".githubusercontent.com"))
    }

    /**
     * Descarga la APK a la caché.
     *
     * @param progreso de 0 a 1, o null mientras no se sepa el tamaño.
     */
    suspend fun descargar(
        act: Actualizacion,
        progreso: (Float?) -> Unit,
    ): File? = withContext(Dispatchers.IO) {
        if (!esDeGitHub(act.urlApk)) return@withContext null
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        // Se limpia lo de intentos anteriores para no ir acumulando APKs.
        dir.listFiles()?.forEach { it.delete() }
        val destino = File(dir, "viby-${act.version}.apk")

        val ok = runCatching { bajar(act, destino, progreso) }.getOrDefault(false)
        if (ok) destino else null.also { destino.delete() }
    }

    private fun bajar(act: Actualizacion, destino: File, progreso: (Float?) -> Unit): Boolean {
        val req = Request.Builder().url(act.urlApk).build()
        return http.newCall(req).execute().use { r ->
            val body = r.body
            if (!r.isSuccessful || body == null) return@use false
            val total = body.contentLength().takeIf { it > 0 } ?: act.bytes
            var leido = 0L
            body.byteStream().use { entrada ->
                destino.outputStream().use { salida ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val n = entrada.read(buffer)
                        if (n <= 0) break
                        salida.write(buffer, 0, n)
                        leido += n
                        progreso(if (total > 0) leido.toFloat() / total else null)
                    }
                }
            }
            true
        }
    }

    /** ¿Tiene permiso para instalar apps de esta fuente? */
    fun puedeInstalar(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O ||
            context.packageManager.canRequestPackageInstalls()

    /** Lleva a los ajustes donde se autoriza a Viby como origen de instalación. */
    fun intentDePermiso(): Intent =
        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
            .setData(Uri.parse("package:${context.packageName}"))

    /**
     * Abre el instalador del sistema con la APK descargada.
     *
     * No hace falta verificar nada por nuestra cuenta: Android rechaza actualizar
     * una app con un APK firmado con otra clave, así que una descarga cambiada no
     * llegaría a instalarse encima de esta.
     */
    fun intentDeInstalacion(apk: File): Intent {
        val uri = FileProvider.getUriForFile(
            context, "${context.packageName}.fileprovider", apk,
        )
        return Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    private fun pedir(url: String): String? {
        val req = Request.Builder().url(url)
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "Viby (Android music player)")
            .build()
        return runCatching {
            http.newCall(req).execute().use { r ->
                if (!r.isSuccessful) null else r.body?.string()
            }
        }.getOrNull()
    }

    private companion object {
        const val KEY_ULTIMA = "update_last_check"
        const val KEY_POSPUESTA = "update_skipped"
        const val KEY_POSPUESTA_EN = "update_skipped_at"
    }
}
