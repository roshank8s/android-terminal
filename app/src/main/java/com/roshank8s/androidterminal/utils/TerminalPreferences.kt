package com.roshank8s.androidterminal.utils

import android.content.Context
import android.content.SharedPreferences

/**
 * Terminal preferences manager.
 * Stores user settings for the terminal app.
 */
class TerminalPreferences(context: Context) {

    companion object {
        private const val PREFS_NAME = "terminal_prefs"

        // Keys
        const val KEY_FONT_SIZE = "font_size"
        const val KEY_CURSOR_STYLE = "cursor_style"
        const val KEY_CURSOR_BLINK = "cursor_blink"
        const val KEY_COLOR_SCHEME = "color_scheme"
        const val KEY_EXTRA_KEYS_VISIBLE = "extra_keys_visible"
        const val KEY_VIBRATE_ON_KEY = "vibrate_on_key"
        const val KEY_BELL_VIBRATE = "bell_vibrate"
        const val KEY_KEEP_SCREEN_ON = "keep_screen_on"
        const val KEY_AUTO_START_ON_BOOT = "auto_start_on_boot"
        const val KEY_DEFAULT_DISTRO = "default_distro"
        const val KEY_USE_DISTRO = "use_distro"
        const val KEY_SHOW_TOOLBAR = "show_toolbar"
        const val KEY_BACK_KEY_BEHAVIOR = "back_key_behavior"
        const val KEY_VOLUME_KEYS_BEHAVIOR = "volume_keys_behavior"
        const val KEY_HARDWARE_KEYBOARD_SHORTCUTS = "hw_keyboard_shortcuts"

        // Defaults
        const val DEFAULT_FONT_SIZE = 14f
        const val DEFAULT_CURSOR_STYLE = 0 // 0=block, 1=underline, 2=bar
        const val DEFAULT_COLOR_SCHEME = "dark"
    }

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var fontSize: Float
        get() = prefs.getFloat(KEY_FONT_SIZE, DEFAULT_FONT_SIZE)
        set(value) = prefs.edit().putFloat(KEY_FONT_SIZE, value).apply()

    var cursorStyle: Int
        get() = prefs.getInt(KEY_CURSOR_STYLE, DEFAULT_CURSOR_STYLE)
        set(value) = prefs.edit().putInt(KEY_CURSOR_STYLE, value).apply()

    var cursorBlink: Boolean
        get() = prefs.getBoolean(KEY_CURSOR_BLINK, true)
        set(value) = prefs.edit().putBoolean(KEY_CURSOR_BLINK, value).apply()

    var colorScheme: String
        get() = prefs.getString(KEY_COLOR_SCHEME, DEFAULT_COLOR_SCHEME) ?: DEFAULT_COLOR_SCHEME
        set(value) = prefs.edit().putString(KEY_COLOR_SCHEME, value).apply()

    var extraKeysVisible: Boolean
        get() = prefs.getBoolean(KEY_EXTRA_KEYS_VISIBLE, true)
        set(value) = prefs.edit().putBoolean(KEY_EXTRA_KEYS_VISIBLE, value).apply()

    var vibrateOnKey: Boolean
        get() = prefs.getBoolean(KEY_VIBRATE_ON_KEY, false)
        set(value) = prefs.edit().putBoolean(KEY_VIBRATE_ON_KEY, value).apply()

    var bellVibrate: Boolean
        get() = prefs.getBoolean(KEY_BELL_VIBRATE, true)
        set(value) = prefs.edit().putBoolean(KEY_BELL_VIBRATE, value).apply()

    var keepScreenOn: Boolean
        get() = prefs.getBoolean(KEY_KEEP_SCREEN_ON, true)
        set(value) = prefs.edit().putBoolean(KEY_KEEP_SCREEN_ON, value).apply()

    var autoStartOnBoot: Boolean
        get() = prefs.getBoolean(KEY_AUTO_START_ON_BOOT, false)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_START_ON_BOOT, value).apply()

    var defaultDistro: String
        get() = prefs.getString(KEY_DEFAULT_DISTRO, "debian") ?: "debian"
        set(value) = prefs.edit().putString(KEY_DEFAULT_DISTRO, value).apply()

    var useDistro: Boolean
        get() = prefs.getBoolean(KEY_USE_DISTRO, false)
        set(value) = prefs.edit().putBoolean(KEY_USE_DISTRO, value).apply()

    var showToolbar: Boolean
        get() = prefs.getBoolean(KEY_SHOW_TOOLBAR, true)
        set(value) = prefs.edit().putBoolean(KEY_SHOW_TOOLBAR, value).apply()

    var backKeyBehavior: String
        get() = prefs.getString(KEY_BACK_KEY_BEHAVIOR, "escape") ?: "escape"
        set(value) = prefs.edit().putString(KEY_BACK_KEY_BEHAVIOR, value).apply()

    var volumeKeysBehavior: String
        get() = prefs.getString(KEY_VOLUME_KEYS_BEHAVIOR, "volume") ?: "volume"
        set(value) = prefs.edit().putString(KEY_VOLUME_KEYS_BEHAVIOR, value).apply()

    var hardwareKeyboardShortcuts: Boolean
        get() = prefs.getBoolean(KEY_HARDWARE_KEYBOARD_SHORTCUTS, true)
        set(value) = prefs.edit().putBoolean(KEY_HARDWARE_KEYBOARD_SHORTCUTS, value).apply()

    /** Get available color schemes. */
    fun getColorSchemes(): Map<String, String> = mapOf(
        "dark" to "Dark (Default)",
        "light" to "Light",
        "solarized_dark" to "Solarized Dark",
        "solarized_light" to "Solarized Light",
        "monokai" to "Monokai",
        "dracula" to "Dracula",
        "nord" to "Nord",
        "gruvbox" to "Gruvbox Dark",
        "one_dark" to "One Dark",
        "material" to "Material Dark"
    )

    /** Get available cursor styles. */
    fun getCursorStyles(): Map<Int, String> = mapOf(
        0 to "Block",
        1 to "Underline",
        2 to "Bar"
    )
}
