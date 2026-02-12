package com.roshank8s.androidterminal.terminal

/**
 * Encodes text style attributes into a single Long value for efficient storage.
 *
 * Bit layout (64 bits):
 *   Bits 0-8:    Foreground color (0-258)
 *   Bits 9-17:   Background color (0-258)
 *   Bits 18-31:  Effect flags (bold, italic, underline, etc.)
 *   Bits 32-55:  True color foreground (RGB, when using 24-bit color)
 *   Bits 56-63:  Reserved
 */
object TextStyle {

    // Effect flag bits
    const val BOLD = 1 shl 0
    const val ITALIC = 1 shl 1
    const val UNDERLINE = 1 shl 2
    const val BLINK = 1 shl 3
    const val INVERSE = 1 shl 4
    const val INVISIBLE = 1 shl 5
    const val STRIKETHROUGH = 1 shl 6
    const val DIM = 1 shl 7
    const val OVERLINE = 1 shl 8
    const val DOUBLE_UNDERLINE = 1 shl 9
    const val CURLY_UNDERLINE = 1 shl 10

    // Bit positions
    private const val FG_SHIFT = 0
    private const val BG_SHIFT = 9
    private const val EFFECT_SHIFT = 18

    // Masks
    private const val COLOR_MASK = 0x1FFL  // 9 bits
    private const val EFFECT_MASK = 0x3FFFL  // 14 bits

    /** Default style: default foreground, default background, no effects. */
    val DEFAULT = encode(
        TerminalColors.COLOR_INDEX_FOREGROUND,
        TerminalColors.COLOR_INDEX_BACKGROUND,
        0
    )

    fun encode(foreground: Int, background: Int, effects: Int): Long {
        return ((foreground.toLong() and COLOR_MASK) shl FG_SHIFT) or
                ((background.toLong() and COLOR_MASK) shl BG_SHIFT) or
                ((effects.toLong() and EFFECT_MASK) shl EFFECT_SHIFT)
    }

    fun decodeForeground(style: Long): Int =
        ((style shr FG_SHIFT) and COLOR_MASK).toInt()

    fun decodeBackground(style: Long): Int =
        ((style shr BG_SHIFT) and COLOR_MASK).toInt()

    fun decodeEffects(style: Long): Int =
        ((style shr EFFECT_SHIFT) and EFFECT_MASK).toInt()
}
