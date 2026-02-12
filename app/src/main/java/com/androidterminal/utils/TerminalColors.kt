package com.androidterminal.utils

import android.graphics.Color

/**
 * Terminal color palette supporting standard 16 colors, 256-color xterm palette,
 * and true color (24-bit).
 */
object TerminalColors {

    /** Standard 16 ANSI colors (dark theme) */
    val DEFAULT_COLORS = intArrayOf(
        // Normal colors (0-7)
        Color.parseColor("#1C1C1C"), // Black
        Color.parseColor("#D32F2F"), // Red
        Color.parseColor("#388E3C"), // Green
        Color.parseColor("#FFA000"), // Yellow
        Color.parseColor("#1976D2"), // Blue
        Color.parseColor("#7B1FA2"), // Magenta
        Color.parseColor("#0097A7"), // Cyan
        Color.parseColor("#CFD8DC"), // White

        // Bright colors (8-15)
        Color.parseColor("#546E7A"), // Bright Black
        Color.parseColor("#FF5252"), // Bright Red
        Color.parseColor("#69F0AE"), // Bright Green
        Color.parseColor("#FFD740"), // Bright Yellow
        Color.parseColor("#448AFF"), // Bright Blue
        Color.parseColor("#E040FB"), // Bright Magenta
        Color.parseColor("#18FFFF"), // Bright Cyan
        Color.parseColor("#FFFFFF"), // Bright White
    )

    /** Current color palette (mutable for theme changes) */
    var colors: IntArray = DEFAULT_COLORS.copyOf()
        private set

    /** Default foreground color */
    var defaultForeground: Int = Color.parseColor("#E0E0E0")
        private set

    /** Default background color */
    var defaultBackground: Int = Color.parseColor("#121212")
        private set

    /** Cursor color */
    var cursorColor: Int = Color.parseColor("#BBBBBB")
        private set

    /**
     * Gets a color from the 256-color palette.
     */
    fun getColor(index: Int): Int {
        return when {
            index < 16 -> colors[index]
            index < 232 -> {
                // 216 color cube (indices 16-231)
                val cubeIndex = index - 16
                val r = cubeIndex / 36
                val g = (cubeIndex % 36) / 6
                val b = cubeIndex % 6
                Color.rgb(
                    if (r == 0) 0 else 55 + r * 40,
                    if (g == 0) 0 else 55 + g * 40,
                    if (b == 0) 0 else 55 + b * 40
                )
            }
            index < 256 -> {
                // Grayscale ramp (indices 232-255)
                val gray = 8 + (index - 232) * 10
                Color.rgb(gray, gray, gray)
            }
            else -> defaultForeground
        }
    }

    /**
     * Applies a color scheme.
     */
    fun applyScheme(scheme: ColorScheme) {
        scheme.colors?.let { colors = it }
        scheme.foreground?.let { defaultForeground = it }
        scheme.background?.let { defaultBackground = it }
        scheme.cursor?.let { cursorColor = it }
    }

    /**
     * Resets to default colors.
     */
    fun resetToDefault() {
        colors = DEFAULT_COLORS.copyOf()
        defaultForeground = Color.parseColor("#E0E0E0")
        defaultBackground = Color.parseColor("#121212")
        cursorColor = Color.parseColor("#BBBBBB")
    }

    /**
     * Predefined color schemes.
     */
    enum class Scheme(val displayName: String, val scheme: ColorScheme) {
        DARK("Dark (Default)", ColorScheme()),
        MONOKAI("Monokai", ColorScheme(
            foreground = Color.parseColor("#F8F8F2"),
            background = Color.parseColor("#272822"),
            cursor = Color.parseColor("#F8F8F0"),
            colors = intArrayOf(
                Color.parseColor("#272822"), Color.parseColor("#F92672"),
                Color.parseColor("#A6E22E"), Color.parseColor("#F4BF75"),
                Color.parseColor("#66D9EF"), Color.parseColor("#AE81FF"),
                Color.parseColor("#A1EFE4"), Color.parseColor("#F8F8F2"),
                Color.parseColor("#75715E"), Color.parseColor("#F92672"),
                Color.parseColor("#A6E22E"), Color.parseColor("#F4BF75"),
                Color.parseColor("#66D9EF"), Color.parseColor("#AE81FF"),
                Color.parseColor("#A1EFE4"), Color.parseColor("#F9F8F5"),
            )
        )),
        SOLARIZED_DARK("Solarized Dark", ColorScheme(
            foreground = Color.parseColor("#839496"),
            background = Color.parseColor("#002B36"),
            cursor = Color.parseColor("#93A1A1"),
            colors = intArrayOf(
                Color.parseColor("#073642"), Color.parseColor("#DC322F"),
                Color.parseColor("#859900"), Color.parseColor("#B58900"),
                Color.parseColor("#268BD2"), Color.parseColor("#D33682"),
                Color.parseColor("#2AA198"), Color.parseColor("#EEE8D5"),
                Color.parseColor("#002B36"), Color.parseColor("#CB4B16"),
                Color.parseColor("#586E75"), Color.parseColor("#657B83"),
                Color.parseColor("#839496"), Color.parseColor("#6C71C4"),
                Color.parseColor("#93A1A1"), Color.parseColor("#FDF6E3"),
            )
        )),
        DRACULA("Dracula", ColorScheme(
            foreground = Color.parseColor("#F8F8F2"),
            background = Color.parseColor("#282A36"),
            cursor = Color.parseColor("#F8F8F2"),
            colors = intArrayOf(
                Color.parseColor("#21222C"), Color.parseColor("#FF5555"),
                Color.parseColor("#50FA7B"), Color.parseColor("#F1FA8C"),
                Color.parseColor("#BD93F9"), Color.parseColor("#FF79C6"),
                Color.parseColor("#8BE9FD"), Color.parseColor("#F8F8F2"),
                Color.parseColor("#6272A4"), Color.parseColor("#FF6E6E"),
                Color.parseColor("#69FF94"), Color.parseColor("#FFFFA5"),
                Color.parseColor("#D6ACFF"), Color.parseColor("#FF92DF"),
                Color.parseColor("#A4FFFF"), Color.parseColor("#FFFFFF"),
            )
        )),
        NORD("Nord", ColorScheme(
            foreground = Color.parseColor("#D8DEE9"),
            background = Color.parseColor("#2E3440"),
            cursor = Color.parseColor("#D8DEE9"),
            colors = intArrayOf(
                Color.parseColor("#3B4252"), Color.parseColor("#BF616A"),
                Color.parseColor("#A3BE8C"), Color.parseColor("#EBCB8B"),
                Color.parseColor("#81A1C1"), Color.parseColor("#B48EAD"),
                Color.parseColor("#88C0D0"), Color.parseColor("#E5E9F0"),
                Color.parseColor("#4C566A"), Color.parseColor("#BF616A"),
                Color.parseColor("#A3BE8C"), Color.parseColor("#EBCB8B"),
                Color.parseColor("#81A1C1"), Color.parseColor("#B48EAD"),
                Color.parseColor("#8FBCBB"), Color.parseColor("#ECEFF4"),
            )
        )),
        GRUVBOX("Gruvbox Dark", ColorScheme(
            foreground = Color.parseColor("#EBDBB2"),
            background = Color.parseColor("#282828"),
            cursor = Color.parseColor("#EBDBB2"),
            colors = intArrayOf(
                Color.parseColor("#282828"), Color.parseColor("#CC241D"),
                Color.parseColor("#98971A"), Color.parseColor("#D79921"),
                Color.parseColor("#458588"), Color.parseColor("#B16286"),
                Color.parseColor("#689D6A"), Color.parseColor("#A89984"),
                Color.parseColor("#928374"), Color.parseColor("#FB4934"),
                Color.parseColor("#B8BB26"), Color.parseColor("#FABD2F"),
                Color.parseColor("#83A598"), Color.parseColor("#D3869B"),
                Color.parseColor("#8EC07C"), Color.parseColor("#EBDBB2"),
            )
        ))
    }

    data class ColorScheme(
        val foreground: Int? = null,
        val background: Int? = null,
        val cursor: Int? = null,
        val colors: IntArray? = null
    )
}
