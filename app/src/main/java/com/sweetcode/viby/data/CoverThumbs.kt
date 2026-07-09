package com.sweetcode.viby.data

import android.content.Context
import java.io.File
import java.security.MessageDigest

/** Carpeta donde guardamos las miniaturas de carátulas ya extraídas. */
fun coverThumbsDir(context: Context): File = File(context.filesDir, "thumbs")

/** Archivo de miniatura para una canción (clave = su id/URI). */
fun coverThumbFile(context: Context, id: String): File =
    File(coverThumbsDir(context), md5(id) + ".jpg")

private fun md5(text: String): String =
    MessageDigest.getInstance("MD5").digest(text.toByteArray())
        .joinToString("") { "%02x".format(it) }
