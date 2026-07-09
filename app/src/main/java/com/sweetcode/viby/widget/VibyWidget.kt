package com.sweetcode.viby.widget

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.palette.graphics.Palette
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.action.actionStartService
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.sweetcode.viby.MainActivity
import com.sweetcode.viby.R
import com.sweetcode.viby.playback.PlaybackService

private val KEY_TITLE = stringPreferencesKey("title")
private val KEY_ARTIST = stringPreferencesKey("artist")
private val KEY_COVER_PATH = stringPreferencesKey("cover_path")
private val KEY_PLAYING = booleanPreferencesKey("playing")

class VibyWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            val prefs = currentState<Preferences>()
            WidgetContent(
                title = prefs[KEY_TITLE].orEmpty(),
                artist = prefs[KEY_ARTIST].orEmpty(),
                playing = prefs[KEY_PLAYING] ?: false,
                coverPath = prefs[KEY_COVER_PATH]?.takeIf { it.isNotBlank() },
            )
        }
    }
}

@Composable
private fun WidgetContent(title: String, artist: String, playing: Boolean, coverPath: String?) {
    val context = LocalContext.current
    // Decodificamos la carátula como bitmap pequeño y lo incrustamos en el widget.
    // Así el launcher no necesita permisos para leer ninguna URI (a prueba de balas).
    val cover = coverPath?.let { decodeThumb(it, 240) }
    // El panel derecho se tiñe con el color dominante de la portada (estilo WidgetPod).
    val panel = cover?.let { dominantTint(it) } ?: Color(0xFF2A2E37)
    Row(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(panel)
            .cornerRadius(24.dp)
            .clickable(actionStartActivity(Intent(context, MainActivity::class.java))),
    ) {
        // Carátula llenando la mitad izquierda, a todo el alto y pegada al borde.
        Box(
            modifier = GlanceModifier
                .fillMaxHeight()
                .defaultWeight()
                .background(Color(0xFF222B36)),
            contentAlignment = Alignment.Center,
        ) {
            if (cover != null) {
                Image(
                    provider = ImageProvider(cover),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = GlanceModifier.fillMaxSize(),
                )
            } else {
                Image(
                    provider = ImageProvider(R.drawable.ic_widget_music),
                    contentDescription = null,
                    modifier = GlanceModifier.size(44.dp),
                )
            }
        }
        // Panel derecho tintado: info arriba + controles centrados abajo.
        Column(
            modifier = GlanceModifier
                .fillMaxHeight()
                .defaultWeight()
                .padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            Spacer(GlanceModifier.defaultWeight())
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title.ifBlank { "Viby" },
                    style = TextStyle(
                        color = ColorProvider(Color.White),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                    maxLines = 2,
                    modifier = GlanceModifier.defaultWeight(),
                )
                Spacer(GlanceModifier.width(6.dp))
                Image(
                    provider = ImageProvider(R.drawable.ic_widget_music),
                    contentDescription = null,
                    modifier = GlanceModifier.size(16.dp),
                )
            }
            if (artist.isNotBlank()) {
                Text(
                    text = artist,
                    style = TextStyle(color = ColorProvider(Color(0xCCFFFFFF)), fontSize = 12.sp),
                    maxLines = 1,
                )
            }
            Spacer(GlanceModifier.height(8.dp))
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ControlButton(R.drawable.ic_widget_prev, PlaybackService.ACTION_WIDGET_PREV, 34)
                Spacer(GlanceModifier.width(10.dp))
                ControlButton(
                    if (playing) R.drawable.ic_widget_pause else R.drawable.ic_widget_play,
                    PlaybackService.ACTION_WIDGET_TOGGLE,
                    40,
                )
                Spacer(GlanceModifier.width(10.dp))
                ControlButton(R.drawable.ic_widget_next, PlaybackService.ACTION_WIDGET_NEXT, 34)
            }
            Spacer(GlanceModifier.defaultWeight())
        }
    }
}

@Composable
private fun ControlButton(iconRes: Int, serviceAction: String, size: Int) {
    val context = LocalContext.current
    Image(
        provider = ImageProvider(iconRes),
        contentDescription = null,
        modifier = GlanceModifier
            .size(size.dp)
            .clickable(
                actionStartService(
                    Intent(context, PlaybackService::class.java).setAction(serviceAction),
                    isForegroundService = false,
                )
            ),
    )
}

/** Color dominante de la portada, oscurecido para que el texto blanco se lea bien. */
private fun dominantTint(bmp: Bitmap): Color {
    val fallback = 0xFF2A2E37.toInt()
    val base = runCatching {
        val p = Palette.from(bmp).clearFilters().generate()
        p.getDarkMutedColor(
            p.getMutedColor(
                p.getDarkVibrantColor(
                    p.getDominantColor(fallback)
                )
            )
        )
    }.getOrDefault(fallback)
    return Color(darken(base, 0.62f))
}

/** Escala los canales RGB por [factor] (mantiene alfa opaco). */
private fun darken(color: Int, factor: Float): Int {
    val r = (android.graphics.Color.red(color) * factor).toInt().coerceIn(0, 255)
    val g = (android.graphics.Color.green(color) * factor).toInt().coerceIn(0, 255)
    val b = (android.graphics.Color.blue(color) * factor).toInt().coerceIn(0, 255)
    return android.graphics.Color.rgb(r, g, b)
}

/** Decodifica la miniatura del disco a un bitmap reducido (~reqPx) para incrustar en el widget. */
private fun decodeThumb(path: String, reqPx: Int): Bitmap? = runCatching {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, bounds)
    if (bounds.outWidth <= 0) return@runCatching null
    var sample = 1
    var w = bounds.outWidth
    while (w / 2 >= reqPx) {
        sample *= 2
        w /= 2
    }
    val opts = BitmapFactory.Options().apply { inSampleSize = sample }
    BitmapFactory.decodeFile(path, opts)
}.getOrNull()

/** Escribe el estado en el estado de Glance y actualiza cada instancia del widget. */
suspend fun updateVibyWidget(
    context: Context,
    title: String,
    artist: String,
    thumb: String?,
    playing: Boolean,
) {
    val coverPath = thumb.orEmpty()
    val widget = VibyWidget()
    val ids = GlanceAppWidgetManager(context).getGlanceIds(VibyWidget::class.java)
    ids.forEach { id ->
        updateAppWidgetState(context, id) { prefs ->
            prefs[KEY_TITLE] = title
            prefs[KEY_ARTIST] = artist
            prefs[KEY_COVER_PATH] = coverPath
            prefs[KEY_PLAYING] = playing
        }
        widget.update(context, id)
    }
}
