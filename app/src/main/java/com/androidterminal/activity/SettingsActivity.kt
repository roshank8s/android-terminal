package com.androidterminal.activity

import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.Switch
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.androidterminal.R
import com.androidterminal.utils.TerminalColors
import com.androidterminal.utils.TerminalPreferences

/**
 * Settings activity for configuring terminal appearance and behavior.
 */
class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        val toolbar = findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Settings"

        setupFontSettings()
        setupColorScheme()
        setupBehaviorSettings()
    }

    private fun setupFontSettings() {
        val fontSizeSeekBar = findViewById<SeekBar>(R.id.seekbar_font_size)
        val fontSizeLabel = findViewById<TextView>(R.id.text_font_size_value)
        val prefs = TerminalPreferences(this)

        val currentSize = prefs.fontSize
        fontSizeSeekBar.progress = (currentSize - 8).toInt()
        fontSizeLabel.text = "${currentSize.toInt()} sp"

        fontSizeSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val size = progress + 8f
                fontSizeLabel.text = "${size.toInt()} sp"
                if (fromUser) prefs.fontSize = size
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    private fun setupColorScheme() {
        val schemeSpinner = findViewById<Spinner>(R.id.spinner_color_scheme)
        val schemes = TerminalColors.Scheme.entries.toTypedArray()
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item,
            schemes.map { it.displayName })
        schemeSpinner.adapter = adapter

        val prefs = TerminalPreferences(this)
        val currentScheme = prefs.colorScheme
        val index = schemes.indexOfFirst { it.name == currentScheme }
        if (index >= 0) schemeSpinner.setSelection(index)
    }

    private fun setupBehaviorSettings() {
        val prefs = TerminalPreferences(this)

        val switchKeepScreenOn = findViewById<Switch>(R.id.switch_keep_screen_on)
        switchKeepScreenOn.isChecked = prefs.keepScreenOn
        switchKeepScreenOn.setOnCheckedChangeListener { _, isChecked ->
            prefs.keepScreenOn = isChecked
        }

        val switchExtraKeys = findViewById<Switch>(R.id.switch_extra_keys)
        switchExtraKeys.isChecked = prefs.showExtraKeys
        switchExtraKeys.setOnCheckedChangeListener { _, isChecked ->
            prefs.showExtraKeys = isChecked
        }

        val switchVibrate = findViewById<Switch>(R.id.switch_vibrate_on_key)
        switchVibrate.isChecked = prefs.vibrateOnKey
        switchVibrate.setOnCheckedChangeListener { _, isChecked ->
            prefs.vibrateOnKey = isChecked
        }

        val switchBellVibrate = findViewById<Switch>(R.id.switch_bell_vibrate)
        switchBellVibrate.isChecked = prefs.bellVibrate
        switchBellVibrate.setOnCheckedChangeListener { _, isChecked ->
            prefs.bellVibrate = isChecked
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
