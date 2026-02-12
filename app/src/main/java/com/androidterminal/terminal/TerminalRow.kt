package com.androidterminal.terminal

/**
 * Represents a single row in the terminal buffer.
 * Stores characters as Unicode code points and their associated styles.
 */
class TerminalRow(val columns: Int) {

    /** Character data stored as Unicode code points */
    private val chars: IntArray = IntArray(columns)

    /** Style data (packed foreground/background colors and attributes) */
    private val styles: LongArray = LongArray(columns)

    /** Whether this row has been wrapped from the previous row */
    var isWrapped: Boolean = false

    /**
     * Sets a character at the given column with a style.
     */
    fun setChar(column: Int, codePoint: Int, style: Long) {
        if (column in 0 until columns) {
            chars[column] = codePoint
            styles[column] = style
        }
    }

    /**
     * Gets the character code point at the given column.
     */
    fun getChar(column: Int): Int {
        return if (column in 0 until columns) chars[column] else 0
    }

    /**
     * Gets the style at the given column.
     */
    fun getStyle(column: Int): Long {
        return if (column in 0 until columns) styles[column] else 0L
    }

    /**
     * Clears the row, filling with spaces and default style.
     */
    fun clear(style: Long = 0L) {
        chars.fill(' '.code)
        styles.fill(style)
        isWrapped = false
    }

    /**
     * Clears from the given column to the end of the row.
     */
    fun clearFrom(column: Int, style: Long = 0L) {
        for (i in column until columns) {
            chars[i] = ' '.code
            styles[i] = style
        }
    }

    /**
     * Clears from the beginning to the given column.
     */
    fun clearTo(column: Int, style: Long = 0L) {
        for (i in 0..minOf(column, columns - 1)) {
            chars[i] = ' '.code
            styles[i] = style
        }
    }

    /**
     * Inserts blank characters at the given position, shifting existing content right.
     */
    fun insertBlank(column: Int, count: Int, style: Long = 0L) {
        val moveCount = columns - column - count
        if (moveCount > 0) {
            System.arraycopy(chars, column, chars, column + count, moveCount)
            System.arraycopy(styles, column, styles, column + count, moveCount)
        }
        for (i in column until minOf(column + count, columns)) {
            chars[i] = ' '.code
            styles[i] = style
        }
    }

    /**
     * Deletes characters at the given position, shifting remaining content left.
     */
    fun deleteChars(column: Int, count: Int, style: Long = 0L) {
        val moveCount = columns - column - count
        if (moveCount > 0) {
            System.arraycopy(chars, column + count, chars, column, moveCount)
            System.arraycopy(styles, column + count, styles, column, moveCount)
        }
        for (i in maxOf(columns - count, column) until columns) {
            chars[i] = ' '.code
            styles[i] = style
        }
    }

    /**
     * Returns the text content of this row as a String (trimming trailing spaces).
     */
    fun toText(): String {
        var lastNonSpace = columns - 1
        while (lastNonSpace >= 0 && (chars[lastNonSpace] == ' '.code || chars[lastNonSpace] == 0)) {
            lastNonSpace--
        }
        if (lastNonSpace < 0) return ""

        val sb = StringBuilder(lastNonSpace + 1)
        for (i in 0..lastNonSpace) {
            val cp = chars[i]
            if (cp == 0) {
                sb.append(' ')
            } else {
                sb.appendCodePoint(cp)
            }
        }
        return sb.toString()
    }

    companion object {
        // Style packing utilities
        // Bits 0-7: foreground color index
        // Bits 8-15: background color index
        // Bits 16-23: attributes (bold, italic, underline, etc.)
        const val ATTR_BOLD = 1L shl 16
        const val ATTR_ITALIC = 1L shl 17
        const val ATTR_UNDERLINE = 1L shl 18
        const val ATTR_BLINK = 1L shl 19
        const val ATTR_INVERSE = 1L shl 20
        const val ATTR_INVISIBLE = 1L shl 21
        const val ATTR_STRIKETHROUGH = 1L shl 22
        const val ATTR_DIM = 1L shl 23

        // True color support (bits 24-55)
        const val ATTR_TRUECOLOR_FG = 1L shl 56
        const val ATTR_TRUECOLOR_BG = 1L shl 57

        fun packStyle(fg: Int, bg: Int, attrs: Long = 0L): Long {
            return (fg.toLong() and 0xFF) or
                    ((bg.toLong() and 0xFF) shl 8) or
                    attrs
        }

        fun unpackFg(style: Long): Int = (style and 0xFF).toInt()
        fun unpackBg(style: Long): Int = ((style shr 8) and 0xFF).toInt()
        fun hasAttr(style: Long, attr: Long): Boolean = (style and attr) != 0L
    }
}
