package com.roshank8s.androidterminal.terminal

/**
 * Circular buffer of terminal rows with scrollback history.
 *
 * The buffer is organized as:
 *   [0, totalRows) = scrollback history + visible screen
 *   The most recent `screenRows` rows are the visible screen.
 *
 * Uses a circular/ring buffer to avoid copying when scrolling.
 */
class TerminalBuffer(
    var columns: Int,
    var screenRows: Int,
    val maxScrollback: Int = 10000
) {
    /** Total number of rows in the buffer (scrollback + screen). */
    var totalRows: Int = screenRows
        private set

    /** The ring buffer of rows. */
    private var rows: Array<TerminalRow> = Array(totalRows + maxScrollback) {
        TerminalRow(columns)
    }

    /** Index of the first row in the ring buffer. */
    private var ringStart: Int = 0

    /** Number of rows used for scrollback (above the visible screen). */
    var activeScrollbackRows: Int = 0
        private set

    /** Get a row by absolute index (0 = oldest scrollback row). */
    fun getRow(index: Int): TerminalRow {
        val effectiveIndex = (ringStart + index) % rows.size
        return rows[effectiveIndex]
    }

    /** Get a screen row (0 = top of visible screen). */
    fun getScreenRow(row: Int): TerminalRow {
        return getRow(activeScrollbackRows + row)
    }

    /** Scroll the screen up by one line (new blank line at bottom). */
    fun scrollUp(topMargin: Int, bottomMargin: Int, style: Long = TextStyle.DEFAULT) {
        if (topMargin == 0 && bottomMargin == screenRows - 1) {
            // Full-screen scroll: move to scrollback
            if (activeScrollbackRows < maxScrollback) {
                activeScrollbackRows++
                totalRows++
            } else {
                ringStart = (ringStart + 1) % rows.size
            }
            // Clear the new bottom line
            getScreenRow(bottomMargin).clear(style)
        } else {
            // Margin scroll: shift rows within margin region
            val savedRow = getScreenRow(topMargin)
            val savedText = savedRow.text.copyOf()
            val savedStyles = savedRow.styles.copyOf()

            for (i in topMargin until bottomMargin) {
                val src = getScreenRow(i + 1)
                val dst = getScreenRow(i)
                src.copyTo(dst)
            }
            getScreenRow(bottomMargin).clear(style)
        }
    }

    /** Scroll the screen down by one line (new blank line at top of margin). */
    fun scrollDown(topMargin: Int, bottomMargin: Int, style: Long = TextStyle.DEFAULT) {
        for (i in bottomMargin downTo topMargin + 1) {
            val src = getScreenRow(i - 1)
            val dst = getScreenRow(i)
            src.copyTo(dst)
        }
        getScreenRow(topMargin).clear(style)
    }

    /** Clear the entire visible screen. */
    fun clearScreen(style: Long = TextStyle.DEFAULT) {
        for (i in 0 until screenRows) {
            getScreenRow(i).clear(style)
        }
    }

    /** Clear scrollback history. */
    fun clearScrollback() {
        ringStart = (ringStart + activeScrollbackRows) % rows.size
        totalRows -= activeScrollbackRows
        activeScrollbackRows = 0
    }

    /** Resize the buffer. */
    fun resize(newColumns: Int, newRows: Int, style: Long = TextStyle.DEFAULT) {
        if (newColumns == columns && newRows == screenRows) return

        val newBuffer = Array(newRows + maxScrollback) { TerminalRow(newColumns) }

        // Copy existing rows
        val rowsToCopy = minOf(totalRows, newRows + maxScrollback)
        val startRow = if (totalRows > rowsToCopy) totalRows - rowsToCopy else 0

        for (i in 0 until rowsToCopy) {
            val srcRow = getRow(startRow + i)
            srcRow.resize(newColumns, style)
            srcRow.copyTo(newBuffer[i])
        }

        columns = newColumns
        screenRows = newRows
        rows = newBuffer
        ringStart = 0
        activeScrollbackRows = maxOf(0, rowsToCopy - newRows)
        totalRows = rowsToCopy
    }

    /** Insert blank lines at a position within scroll margins. */
    fun insertLines(row: Int, count: Int, bottomMargin: Int, style: Long = TextStyle.DEFAULT) {
        val effectiveCount = minOf(count, bottomMargin - row + 1)
        for (i in 0 until effectiveCount) {
            scrollDown(row, bottomMargin, style)
        }
    }

    /** Delete lines at a position within scroll margins. */
    fun deleteLines(row: Int, count: Int, bottomMargin: Int, style: Long = TextStyle.DEFAULT) {
        val effectiveCount = minOf(count, bottomMargin - row + 1)
        for (i in 0 until effectiveCount) {
            scrollUp(row, bottomMargin, style)
        }
    }

    /** Get the text content of the visible screen as a string. */
    fun getScreenText(): String {
        val sb = StringBuilder()
        for (i in 0 until screenRows) {
            if (i > 0) sb.append('\n')
            sb.append(getScreenRow(i).getLineText())
        }
        return sb.toString()
    }

    /** Get selected text between two positions. */
    fun getSelectedText(
        startRow: Int, startCol: Int,
        endRow: Int, endCol: Int
    ): String {
        val sb = StringBuilder()
        for (row in startRow..endRow) {
            val termRow = getRow(row)
            val colStart = if (row == startRow) startCol else 0
            val colEnd = if (row == endRow) endCol else columns - 1
            for (col in colStart..colEnd) {
                sb.append(termRow.getChar(col))
            }
            if (row < endRow && !termRow.isLineWrap) {
                sb.append('\n')
            }
        }
        return sb.toString().trimEnd()
    }
}
