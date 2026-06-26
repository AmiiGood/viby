package com.sweetcode.viby.playback

import android.content.Context
import android.content.SharedPreferences
import android.media.audiofx.Equalizer

/**
 * Ecualizador propio de la app (motor de efectos de Android), enganchado a la
 * sesión de audio del reproductor. Se mantiene vivo a nivel de proceso para que
 * el efecto siga aplicándose, y persiste los ajustes en prefs.
 */
object EqualizerManager {

    private var equalizer: Equalizer? = null
    private var prefs: SharedPreferences? = null

    val isAvailable: Boolean get() = equalizer != null

    fun init(context: Context, sessionId: Int) {
        if (equalizer != null || sessionId == 0) return
        prefs = context.getSharedPreferences("viby_eq", Context.MODE_PRIVATE)
        equalizer = try {
            Equalizer(0, sessionId).also { eq ->
                val saved = prefs!!
                eq.setEnabled(saved.getBoolean(KEY_ENABLED, false))
                val bands = eq.numberOfBands.toInt()
                for (b in 0 until bands) {
                    val key = keyBand(b)
                    if (saved.contains(key)) {
                        eq.setBandLevel(b.toShort(), saved.getInt(key, 0).toShort())
                    }
                }
            }
        } catch (e: Exception) {
            null
        }
    }

    var enabled: Boolean
        get() = equalizer?.enabled ?: false
        set(value) {
            equalizer?.setEnabled(value)
            prefs?.edit()?.putBoolean(KEY_ENABLED, value)?.apply()
        }

    val bandCount: Int get() = equalizer?.numberOfBands?.toInt() ?: 0

    /** Rango de nivel en milibelios [min, max]. */
    val minLevel: Short get() = equalizer?.bandLevelRange?.getOrNull(0) ?: 0
    val maxLevel: Short get() = equalizer?.bandLevelRange?.getOrNull(1) ?: 0

    /** Frecuencia central de la banda en Hz. */
    fun centerFreqHz(band: Int): Int = (equalizer?.getCenterFreq(band.toShort()) ?: 0) / 1000

    fun bandLevel(band: Int): Short = equalizer?.getBandLevel(band.toShort()) ?: 0

    fun setBandLevel(band: Int, levelMb: Short) {
        equalizer?.setBandLevel(band.toShort(), levelMb)
        prefs?.edit()?.putInt(keyBand(band), levelMb.toInt())?.apply()
    }

    val presets: List<String>
        get() {
            val eq = equalizer ?: return emptyList()
            return (0 until eq.numberOfPresets).map { eq.getPresetName(it.toShort()) }
        }

    fun usePreset(index: Int) {
        val eq = equalizer ?: return
        eq.usePreset(index.toShort())
        val editor = prefs?.edit()
        for (b in 0 until bandCount) {
            editor?.putInt(keyBand(b), eq.getBandLevel(b.toShort()).toInt())
        }
        editor?.apply()
    }

    private fun keyBand(b: Int) = "band_$b"
    private const val KEY_ENABLED = "eq_enabled"
}
