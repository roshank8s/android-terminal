package com.roshank8s.androidterminal.terminal

/**
 * Represents a single row in the terminal screen buffer.
 * Stores both the text content and per-character style information.
 */
class TerminalRow(private var columns: Int) {

    /** Characters in this row. May contain multi-byte UTF-16. */
    var text: CharArray = CharArray(columns) { ' ' }
        private set

    /** Style for each column position. */
    var styles: LongArray = LongArray(columns) { TextStyle.DEFAULT }
        private set

    /** Whether this row wraps to the next line. */
    var isLineWrap: Boolean = false

    /** Whether this row has any content (optimization for rendering). */
    var hasContent: Boolean = false

    /** Set a character and its style at a given column. */
    fun setChar(column: Int, char: Char, style: Long) {
        if (column in 0 until columns) {
            text[column] = char
            styles[column] = style
            hasContent = true
        }
    }

    /** Get the character at a given column. */
    fun getChar(column: Int): Char {
        return if (column in 0 until columns) text[column] else ' '
    }

    /** Get the style at a given column. */
    fun getStyle(column: Int): Long {
        return if (column in 0 until columns) styles[column] else TextStyle.DEFAULT
    }

    /** Clear the entire row with a given style. */
    fun clear(style: Long = TextStyle.DEFAULT) {
        text.fill(' ')
        styles.fill(style)
        isLineWrap = false
        hasContent = false
    }

    /** Clear from a column to end of line. */
    fun clearFrom(column: Int, style: Long = TextStyle.DEFAULT) {
        for (i in column until columns) {
            text[i] = ' '
            styles[i] = style
        }
    }

    /** Clear from start of line to a column (inclusive). */
    fun clearTo(column: Int, style: Long = TextStyle.DEFAULT) {
        for (i in 0..minOf(column, columns - 1)) {
            text[i] = ' '
            styles[i] = style
        }
    }

    /** Insert blank characters at a position, shifting existing chars right. */
    fun insertBlank(column: Int, count: Int, style: Long = TextStyle.DEFAULT) {
        val shift = minOf(count, columns - column)
        // Shift right
        for (i in columns - 1 downTo column + shift) {
            text[i] = text[i - shift]
            styles[i] = styles[i - shift]
        }
        // Fill inserted positions
        for (i in column until column + shift) {
            text[i] = ' '
            styles[i] = style
        }
    }

    /** Delete characters at a position, shifting remaining chars left. */
    fun deleteChars(column: Int, count: Int, style: Long = TextStyle.DEFAULT) {
        val shift = minOf(count, columns - column)
        // Shift left
        for (i in column until columns - shift) {
            text[i] = text[i + shift]
            styles[i] = styles[i + shift]
        }
        // Fill end with blanks
        for (i in columns - shift until columns) {
            text[i] = ' '
            styles[i] = style
        }
    }

    /** Resize the row to new column count. */
    fun resize(newColumns: Int, style: Long = TextStyle.DEFAULT) {
        if (newColumns == columns) return
        val newText = CharArray(newColumns) { if (it < columns) text[it] else ' ' }
        val newStyles = LongArray(newColumns) { if (it < columns) styles[it] else style }
        text = newText
        styles = newStyles
        columns = newColumns
    }

    /** Get the text content as a string, trimming trailing spaces. */
    fun getLineText(): String {
        var end = columns - 1
        while (end >= 0 && text[end] == ' ') end--
        return if (end < 0) "" else String(text, 0, end + 1)
    }

    /** Copy this row's data to another row. */
    fun copyTo(other: TerminalRow) {
        System.arraycopy(text, 0, other.text, 0, minOf(columns, other.text.size))
        System.arraycopy(styles, 0, other.styles, 0, minOf(columns, other.styles.size))
        other.isLineWrap = isLineWrap
        other.hasContent = hasContent
    }
}
