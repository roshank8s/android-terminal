package com.androidterminal.utils

import android.content.Context
import android.content.SharedPreferences

/**
 * Manages terminal preferences/settings using SharedPreferences.
 */
class TerminalPreferences(context: Context) {

    companion object {
        private const val PREFS_NAME = "terminal_preferences"
        private const val KEY_FONT_SIZE = "font_size"
        private const val KEY_COLOR_SCHEME = "color_scheme"
        private const val KEY_KEEP_SCREEN_ON = "keep_screen_on"
        private const val KEY_SHOW_EXTRA_KEYS = "show_extra_keys"
        private const val KEY_VIBRATE_ON_KEY = "vibrate_on_key"
        private const val KEY_BELL_VIBRATE = "bell_vibrate"
        private const val KEY_DEFAULT_SHELL = "default_shell"
        private const val KEY_INITIAL_COMMAND = "initial_command"
        private const val KEY_CURSOR_BLINK = "cursor_blink"
        private const val KEY_CURSOR_STYLE = "cursor_style"
    }

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var fontSize: Float
        get() = prefs.getFloat(KEY_FONT_SIZE, 14f)
        set(value) = prefs.edit().putFloat(KEY_FONT_SIZE, value).apply()

    var colorScheme: String
        get() = prefs.getString(KEY_COLOR_SCHEME, "DARK") ?: "DARK"
        set(value) = prefs.edit().putString(KEY_COLOR_SCHEME, value).apply()

    var keepScreenOn: Boolean
        get() = prefs.getBoolean(KEY_KEEP_SCREEN_ON, true)
        set(value) = prefs.edit().putBoolean(KEY_KEEP_SCREEN_ON, value).apply()

    var showExtraKeys: Boolean
        get() = prefs.getBoolean(KEY_SHOW_EXTRA_KEYS, true)
        set(value) = prefs.edit().putBoolean(KEY_SHOW_EXTRA_KEYS, value).apply()

    var vibrateOnKey: Boolean
        get() = prefs.getBoolean(KEY_VIBRATE_ON_KEY, true)
        set(value) = prefs.edit().putBoolean(KEY_VIBRATE_ON_KEY, value).apply()

    var bellVibrate: Boolean
        get() = prefs.getBoolean(KEY_BELL_VIBRATE, true)
        set(value) = prefs.edit().putBoolean(KEY_BELL_VIBRATE, value).apply()

    var defaultShell: String
        get() = prefs.getString(KEY_DEFAULT_SHELL, "") ?: ""
        set(value) = prefs.edit().putString(KEY_DEFAULT_SHELL, value).apply()

    var initialCommand: String
        get() = prefs.getString(KEY_INITIAL_COMMAND, "") ?: ""
        set(value) = prefs.edit().putString(KEY_INITIAL_COMMAND, value).apply()

    var cursorBlink: Boolean
        get() = prefs.getBoolean(KEY_CURSOR_BLINK, true)
        set(value) = prefs.edit().putBoolean(KEY_CURSOR_BLINK, value).apply()

    var cursorStyle: String
        get() = prefs.getString(KEY_CURSOR_STYLE, "block") ?: "block"
        set(value) = prefs.edit().putString(KEY_CURSOR_STYLE, value).apply()
}
