package com.roshank8s.androidterminal.ui

import android.os.Bundle
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.*
import com.roshank8s.androidterminal.R
import com.roshank8s.androidterminal.utils.TerminalPreferences

/**
 * Settings activity using PreferenceFragmentCompat for terminal configuration.
 */
class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        setSupportActionBar(findViewById(R.id.settings_toolbar))
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Settings"

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.settings_container, SettingsFragment())
                .commit()
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                onBackPressed()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    class SettingsFragment : PreferenceFragmentCompat() {
        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            val context = preferenceManager.context
            val screen = preferenceManager.createPreferenceScreen(context)

            // Appearance category
            val appearanceCategory = PreferenceCategory(context).apply {
                title = "Appearance"
            }
            screen.addPreference(appearanceCategory)

            appearanceCategory.addPreference(SeekBarPreference(context).apply {
                key = TerminalPreferences.KEY_FONT_SIZE
                title = "Font Size"
                min = 6
                max = 42
                setDefaultValue(14)
                showSeekBarValue = true
            })

            appearanceCategory.addPreference(ListPreference(context).apply {
                key = TerminalPreferences.KEY_CURSOR_STYLE
                title = "Cursor Style"
                entries = arrayOf("Block", "Underline", "Bar")
                entryValues = arrayOf("0", "1", "2")
                setDefaultValue("0")
                summaryProvider = ListPreference.SimpleSummaryProvider.getInstance()
            })

            appearanceCategory.addPreference(SwitchPreferenceCompat(context).apply {
                key = TerminalPreferences.KEY_CURSOR_BLINK
                title = "Cursor Blink"
                setDefaultValue(true)
            })

            appearanceCategory.addPreference(ListPreference(context).apply {
                key = TerminalPreferences.KEY_COLOR_SCHEME
                title = "Color Scheme"
                entries = arrayOf(
                    "Dark (Default)", "Light", "Solarized Dark", "Solarized Light",
                    "Monokai", "Dracula", "Nord", "Gruvbox Dark", "One Dark", "Material Dark"
                )
                entryValues = arrayOf(
                    "dark", "light", "solarized_dark", "solarized_light",
                    "monokai", "dracula", "nord", "gruvbox", "one_dark", "material"
                )
                setDefaultValue("dark")
                summaryProvider = ListPreference.SimpleSummaryProvider.getInstance()
            })

            appearanceCategory.addPreference(SwitchPreferenceCompat(context).apply {
                key = TerminalPreferences.KEY_SHOW_TOOLBAR
                title = "Show Toolbar"
                setDefaultValue(true)
            })

            // Terminal category
            val terminalCategory = PreferenceCategory(context).apply {
                title = "Terminal"
            }
            screen.addPreference(terminalCategory)

            terminalCategory.addPreference(SwitchPreferenceCompat(context).apply {
                key = TerminalPreferences.KEY_EXTRA_KEYS_VISIBLE
                title = "Show Extra Keys Row"
                summary = "Toggle the extra keys bar (Ctrl, Alt, arrows, etc.)"
                setDefaultValue(true)
            })

            terminalCategory.addPreference(SwitchPreferenceCompat(context).apply {
                key = TerminalPreferences.KEY_VIBRATE_ON_KEY
                title = "Haptic Feedback"
                summary = "Vibrate on key press"
                setDefaultValue(false)
            })

            terminalCategory.addPreference(SwitchPreferenceCompat(context).apply {
                key = TerminalPreferences.KEY_BELL_VIBRATE
                title = "Bell Vibrate"
                summary = "Vibrate on terminal bell"
                setDefaultValue(true)
            })

            terminalCategory.addPreference(SwitchPreferenceCompat(context).apply {
                key = TerminalPreferences.KEY_KEEP_SCREEN_ON
                title = "Keep Screen On"
                setDefaultValue(true)
            })

            // Keyboard category
            val keyboardCategory = PreferenceCategory(context).apply {
                title = "Keyboard"
            }
            screen.addPreference(keyboardCategory)

            keyboardCategory.addPreference(ListPreference(context).apply {
                key = TerminalPreferences.KEY_BACK_KEY_BEHAVIOR
                title = "Back Key"
                entries = arrayOf("Send ESC", "Navigate Back", "Confirm Exit")
                entryValues = arrayOf("escape", "back", "confirm")
                setDefaultValue("escape")
                summaryProvider = ListPreference.SimpleSummaryProvider.getInstance()
            })

            keyboardCategory.addPreference(ListPreference(context).apply {
                key = TerminalPreferences.KEY_VOLUME_KEYS_BEHAVIOR
                title = "Volume Keys"
                entries = arrayOf("Volume Control", "Font Size", "Scroll")
                entryValues = arrayOf("volume", "font_size", "scroll")
                setDefaultValue("volume")
                summaryProvider = ListPreference.SimpleSummaryProvider.getInstance()
            })

            keyboardCategory.addPreference(SwitchPreferenceCompat(context).apply {
                key = TerminalPreferences.KEY_HARDWARE_KEYBOARD_SHORTCUTS
                title = "Hardware Keyboard Shortcuts"
                summary = "Enable Ctrl+Shift shortcuts for copy/paste"
                setDefaultValue(true)
            })

            // Linux Distribution category
            val distroCategory = PreferenceCategory(context).apply {
                title = "Linux Distribution"
            }
            screen.addPreference(distroCategory)

            distroCategory.addPreference(SwitchPreferenceCompat(context).apply {
                key = TerminalPreferences.KEY_USE_DISTRO
                title = "Use Linux Distribution"
                summary = "Launch into installed Linux distro by default"
                setDefaultValue(false)
            })

            distroCategory.addPreference(ListPreference(context).apply {
                key = TerminalPreferences.KEY_DEFAULT_DISTRO
                title = "Default Distribution"
                entries = arrayOf("Kali Linux", "Ubuntu", "Debian", "Alpine")
                entryValues = arrayOf("kali", "ubuntu", "debian", "alpine")
                setDefaultValue("debian")
                summaryProvider = ListPreference.SimpleSummaryProvider.getInstance()
            })

            // System category
            val systemCategory = PreferenceCategory(context).apply {
                title = "System"
            }
            screen.addPreference(systemCategory)

            systemCategory.addPreference(SwitchPreferenceCompat(context).apply {
                key = TerminalPreferences.KEY_AUTO_START_ON_BOOT
                title = "Auto-start on Boot"
                summary = "Start terminal service when device boots"
                setDefaultValue(false)
            })

            preferenceScreen = screen
        }
    }
}
