package com.example.audioplayer

import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import com.example.audioplayer.databinding.ActivitySettingsBinding
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding

    private val presets = listOf(
        "Plano" to intArrayOf(0, 0, 0, 0, 0, 0, 0, 0),
        "Rock" to intArrayOf(5, 4, 2, 0, -1, 1, 3, 5),
        "Pop" to intArrayOf(-1, 2, 4, 2, 0, -1, -2, -2),
        "Jazz" to intArrayOf(3, 2, 0, 2, -1, -1, 0, 2),
        "Clásica" to intArrayOf(4, 3, 1, 2, 3, 3, 3, 4),
        "Voz" to intArrayOf(-2, -1, 1, 3, 5, 3, 1, 0),
        "Graves" to intArrayOf(6, 5, 3, 1, 0, -1, -2, -3),
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SettingsStore.applyAccent(this)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnBack.setOnClickListener { finish() }
        binding.btnEqualizer.setOnClickListener { showEqualizer() }

        populateAccents()
    }

    private fun populateAccents() {
        binding.accentContainer.removeAllViews()
        val currentId = SettingsStore.accentId(this)
        SettingsStore.accents.forEach { acc ->
            val frame = FrameLayout(this).apply {
                layoutParams = LinearLayout.LayoutParams(dp(52), dp(52))
            }
            val circle = View(this).apply {
                background = circleDrawable(acc.primary)
                layoutParams = FrameLayout.LayoutParams(dp(40), dp(40), Gravity.CENTER)
            }
            frame.addView(circle)
            if (acc.id == currentId) {
                val check = ImageView(this).apply {
                    setImageResource(R.drawable.ic_check)
                    imageTintList = ColorStateList.valueOf(Color.WHITE)
                    layoutParams = FrameLayout.LayoutParams(dp(20), dp(20), Gravity.CENTER)
                }
                frame.addView(check)
            }
            frame.setOnClickListener {
                if (acc.id != SettingsStore.accentId(this)) {
                    SettingsStore.setAccentId(this, acc.id)
                    recreate()
                }
            }
            binding.accentContainer.addView(frame)
        }
    }

    private fun showEqualizer() {
        EqualizerManager.init(this)

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(8), dp(4), dp(8), dp(4))
        }

        val switch = SwitchCompat(this).apply {
            text = getString(R.string.eq_enable)
            setTextColor(getColor(R.color.text_primary))
            isChecked = EqualizerManager.isEnabled()
        }
        content.addView(switch)

        val presetLabel = TextView(this).apply {
            text = getString(R.string.eq_preset)
            setTextColor(getColor(R.color.text_secondary))
            textSize = 12f
            setPadding(0, dp(12), 0, dp(4))
        }
        content.addView(presetLabel)

        val entries = mutableListOf(getString(R.string.eq_custom))
        entries.addAll(presets.map { it.first })
        val spinner = Spinner(this).apply {
            adapter = ArrayAdapter(this@SettingsActivity, R.layout.item_spinner, entries)
        }
        spinner.setSelection(matchPreset())
        content.addView(spinner)

        val bandContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(10), 0, dp(4))
        }
        content.addView(bandContainer)

        val sliders = mutableListOf<SeekBar>()
        val valueLabels = mutableListOf<TextView>()

        switch.setOnCheckedChangeListener { _, checked -> EqualizerManager.setEnabled(checked) }

        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (position > 0) {
                    EqualizerManager.setPreset(presets[position - 1].second)
                    for (i in 0 until EqualizerManager.BAND_COUNT) {
                        sliders.getOrNull(i)?.progress = EqualizerManager.bandGain(i) - EqualizerManager.GAIN_MIN_DB
                        valueLabels.getOrNull(i)?.text = formatDb(EqualizerManager.bandGain(i))
                    }
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        for (i in 0 until EqualizerManager.BAND_COUNT) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, 0, 0, dp(8))
            }
            val header = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            val label = TextView(this).apply {
                text = EqualizerManager.bandLabel(i)
                setTextColor(getColor(R.color.text_primary))
                textSize = 13f
            }
            val value = TextView(this).apply {
                text = formatDb(EqualizerManager.bandGain(i))
                setTextColor(getColor(R.color.text_secondary))
                textSize = 12f
            }
            header.addView(label, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            header.addView(value)
            row.addView(header)

            val seek = SeekBar(this).apply {
                max = EqualizerManager.GAIN_MAX_DB - EqualizerManager.GAIN_MIN_DB
                progress = EqualizerManager.bandGain(i) - EqualizerManager.GAIN_MIN_DB
            }
            seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (fromUser) {
                        EqualizerManager.setBandGain(i, progress + EqualizerManager.GAIN_MIN_DB)
                        value.text = formatDb(progress + EqualizerManager.GAIN_MIN_DB)
                        spinner.setSelection(0)
                    }
                }

                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
            row.addView(seek)
            sliders.add(seek)
            valueLabels.add(value)
            bandContainer.addView(row)
        }

        val reset = MaterialButton(this).apply {
            text = getString(R.string.eq_reset)
            isAllCaps = false
            backgroundTintList = ColorStateList.valueOf(getColor(R.color.card_gray_dark))
            setTextColor(getColor(R.color.chrome))
            strokeColor = ColorStateList.valueOf(getColor(R.color.strokes))
            strokeWidth = dp(1)
            insetTop = 0
            insetBottom = 0
        }
        reset.setOnClickListener {
            EqualizerManager.resetBands()
            spinner.setSelection(0)
            for (i in 0 until EqualizerManager.BAND_COUNT) {
                sliders.getOrNull(i)?.progress = 0 - EqualizerManager.GAIN_MIN_DB
                valueLabels.getOrNull(i)?.text = formatDb(0)
            }
        }
        content.addView(reset, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(6)
        })

        val scroll = ScrollView(this).apply { addView(content) }

        MaterialAlertDialogBuilder(this, R.style.App_Dialog)
            .setTitle(R.string.settings_equalizer)
            .setView(scroll)
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun matchPreset(): Int {
        val current = IntArray(EqualizerManager.BAND_COUNT) { EqualizerManager.bandGain(it) }
        presets.forEachIndexed { index, p ->
            if (p.second.contentEquals(current)) return index + 1
        }
        return 0
    }

    private fun formatDb(db: Int): String =
        if (db > 0) "+$db dB" else "$db dB"

    private fun circleDrawable(color: Int): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(color)
        }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()
}
