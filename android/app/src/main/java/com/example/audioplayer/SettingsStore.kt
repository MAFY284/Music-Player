package com.example.audioplayer

import android.content.Context
import androidx.annotation.ColorInt
import androidx.appcompat.app.AppCompatActivity

object SettingsStore {

    data class Accent(
        val id: String,
        val label: String,
        @ColorInt val primary: Int,
        @ColorInt val light: Int,
        @ColorInt val deep: Int,
        val overlayRes: Int,
    )

    val accents: List<Accent> = listOf(
        Accent("orange", "Naranja", 0xFFFF7A18.toInt(), 0xFFFFA45C.toInt(), 0xFFE05E00.toInt(), 0),
        Accent("blue", "Azul", 0xFF2196F3.toInt(), 0xFF64B5F6.toInt(), 0xFF1565C0.toInt(), R.style.ThemeOverlay_Accent_Blue),
        Accent("green", "Verde", 0xFF4CAF50.toInt(), 0xFF81C784.toInt(), 0xFF388E3C.toInt(), R.style.ThemeOverlay_Accent_Green),
        Accent("red", "Rojo", 0xFFF44336.toInt(), 0xFFE57373.toInt(), 0xFFC62828.toInt(), R.style.ThemeOverlay_Accent_Red),
        Accent("purple", "Morado", 0xFF9C27B0.toInt(), 0xFFBA68C8.toInt(), 0xFF6A1B9A.toInt(), R.style.ThemeOverlay_Accent_Purple),
        Accent("pink", "Rosa", 0xFFE91E63.toInt(), 0xFFF06292.toInt(), 0xFFAD1457.toInt(), R.style.ThemeOverlay_Accent_Pink),
        Accent("cyan", "Cian", 0xFF00BCD4.toInt(), 0xFF4DD0E1.toInt(), 0xFF00838F.toInt(), R.style.ThemeOverlay_Accent_Cyan),
        Accent("yellow", "Amarillo", 0xFFFFC107.toInt(), 0xFFFFD54F.toInt(), 0xFFFF8F00.toInt(), R.style.ThemeOverlay_Accent_Yellow),
    )

    private const val PREFS = "settings"
    private const val KEY_ACCENT = "accent_id"

    @Volatile
    var accentRevision = 0
        private set

    fun accentId(context: Context): String =
        prefs(context).getString(KEY_ACCENT, "orange") ?: "orange"

    fun setAccentId(context: Context, id: String) {
        prefs(context).edit().putString(KEY_ACCENT, id).apply()
        accentRevision++
    }

    fun current(context: Context): Accent =
        accents.firstOrNull { it.id == accentId(context) } ?: accents.first()

    @ColorInt
    fun accent(context: Context): Int = current(context).primary

    @ColorInt
    fun accentLight(context: Context): Int = current(context).light

    @ColorInt
    fun accentDeep(context: Context): Int = current(context).deep

    fun applyAccent(activity: AppCompatActivity) {
        val overlay = current(activity).overlayRes
        if (overlay != 0) activity.theme.applyStyle(overlay, true)
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
