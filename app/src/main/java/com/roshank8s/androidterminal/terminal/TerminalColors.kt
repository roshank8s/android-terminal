package com.roshank8s.androidterminal.terminal

/**
 * Terminal color palette supporting 256 colors + true color.
 *
 * Color indices:
 *   0-7:     Standard colors (black, red, green, yellow, blue, magenta, cyan, white)
 *   8-15:    Bright/bold colors
 *   16-231:  216-color cube (6x6x6)
 *   232-255: Grayscale ramp (24 shades)
 */
class TerminalColors {

    /** The 256-color palette as ARGB integers. */
    val palette = IntArray(256)

    /** Current default foreground color index. */
    var defaultForeground: Int = COLOR_INDEX_FOREGROUND

    /** Current default background color index. */
    var defaultBackground: Int = COLOR_INDEX_BACKGROUND

    /** Current cursor color. */
    var cursorColor: Int = DEFAULT_CURSOR_COLOR

    init {
        reset()
    }

    /** Resets palette to default xterm-256color values. */
    fun reset() {
        // Standard 16 colors (xterm defaults)
        palette[0] = 0xFF000000.toInt()  // Black
        palette[1] = 0xFFCD0000.toInt()  // Red
        palette[2] = 0xFF00CD00.toInt()  // Green
        palette[3] = 0xFFCDCD00.toInt()  // Yellow
        palette[4] = 0xFF0000EE.toInt()  // Blue
        palette[5] = 0xFFCD00CD.toInt()  // Magenta
        palette[6] = 0xFF00CDCD.toInt()  // Cyan
        palette[7] = 0xFFE5E5E5.toInt()  // White

        // Bright colors
        palette[8]  = 0xFF7F7F7F.toInt()  // Bright Black (Gray)
        palette[9]  = 0xFFFF0000.toInt()  // Bright Red
        palette[10] = 0xFF00FF00.toInt()  // Bright Green
        palette[11] = 0xFFFFFF00.toInt()  // Bright Yellow
        palette[12] = 0xFF5C5CFF.toInt()  // Bright Blue
        palette[13] = 0xFFFF00FF.toInt()  // Bright Magenta
        palette[14] = 0xFF00FFFF.toInt()  // Bright Cyan
        palette[15] = 0xFFFFFFFF.toInt()  // Bright White

        // 216-color cube (indices 16-231)
        for (i in 0 until 216) {
            val r = if (i / 36 == 0) 0 else 55 + (i / 36) * 40
            val g = if ((i % 36) / 6 == 0) 0 else 55 + ((i % 36) / 6) * 40
            val b = if (i % 6 == 0) 0 else 55 + (i % 6) * 40
            palette[16 + i] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
        }

        // Grayscale ramp (indices 232-255)
        for (i in 0 until 24) {
            val v = 8 + i * 10
            palette[232 + i] = (0xFF shl 24) or (v shl 16) or (v shl 8) or v
        }

        cursorColor = DEFAULT_CURSOR_COLOR
    }

    companion object {
        const val COLOR_INDEX_FOREGROUND = 256
        const val COLOR_INDEX_BACKGROUND = 257
        const val COLOR_INDEX_CURSOR = 258

        // Default colors for dark theme
        const val DEFAULT_FOREGROUND = 0xFFDCDCCC.toInt()
        const val DEFAULT_BACKGROUND = 0xFF1C1C1C.toInt()
        const val DEFAULT_CURSOR_COLOR = 0xFFA0A0A0.toInt()

        /** Number of indexed colors (256 palette + special indices). */
        const val NUM_INDEXED_COLORS = 259
    }
}
