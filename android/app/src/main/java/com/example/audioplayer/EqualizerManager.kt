package com.example.audioplayer

import android.content.Context
import android.content.SharedPreferences

object EqualizerManager {

    const val BAND_COUNT = 8
    val BAND_FREQS = intArrayOf(40, 100, 250, 630, 1600, 4000, 8000, 16000)
    const val GAIN_MIN_DB = -12
    const val GAIN_MAX_DB = 12

    val processor = EqualizerProcessor()

    private const val PREFS = "equalizer"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_BAND = "band_"

    private var appContext: Context? = null

    fun init(context: Context) {
        if (appContext == null) appContext = context.applicationContext
        apply()
    }

    fun isEnabled(): Boolean = prefs().getBoolean(KEY_ENABLED, false)

    fun setEnabled(enabled: Boolean) {
        prefs().edit().putBoolean(KEY_ENABLED, enabled).apply()
        apply()
    }

    fun bandGain(band: Int): Int = prefs().getInt(KEY_BAND + band, 0)

    fun setBandGain(band: Int, db: Int) {
        prefs().edit().putInt(KEY_BAND + band, db).apply()
        apply()
    }

    fun setPreset(gains: IntArray) {
        val editor = prefs().edit()
        for (b in 0 until BAND_COUNT) {
            editor.putInt(KEY_BAND + b, gains.getOrElse(b) { 0 })
        }
        editor.apply()
        apply()
    }

    fun resetBands() {
        val editor = prefs().edit()
        for (b in 0 until BAND_COUNT) {
            editor.putInt(KEY_BAND + b, 0)
        }
        editor.apply()
        apply()
    }

    fun bandLabel(band: Int): String {
        val f = BAND_FREQS[band]
        if (f < 1000) return "$f Hz"
        val khz = f / 1000.0
        val s = if (khz == khz.toInt().toDouble()) khz.toInt().toString() else "%.1f".format(khz)
        return "$s kHz"
    }

    private fun apply() {
        val enabled = isEnabled()
        processor.gains = FloatArray(BAND_COUNT) { if (enabled) bandGain(it).toFloat() else 0f }
        processor.version++
    }

    private fun prefs(): SharedPreferences =
        appContext?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            ?: throw IllegalStateException("EqualizerManager not initialized")
}
