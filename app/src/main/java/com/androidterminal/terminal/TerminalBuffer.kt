package com.androidterminal.terminal

/**
 * Manages the terminal screen buffer including scrollback history.
 * Stores terminal content as a circular buffer of TerminalRow objects.
 */
class TerminalBuffer(var columns: Int, totalRows: Int, var screenRows: Int) {

    /** All rows including scrollback */
    private var rows: Array<TerminalRow?> = arrayOfNulls(totalRows)

    /** Total capacity */
    var totalRows: Int = totalRows
        private set

    /** Index of the first row in the circular buffer */
    private var firstRow: Int = 0

    /** Number of active rows */
    var activeRows: Int = screenRows
        private set

    /** Number of rows scrolled back from the screen top */
    var scrollbackRows: Int = 0
        private set

    init {
        for (i in 0 until screenRows) {
            rows[i] = TerminalRow(columns)
        }
        activeRows = screenRows
    }

    /**
     * Gets a row by absolute index.
     */
    fun getRow(index: Int): TerminalRow {
        val mappedIndex = (firstRow + index) % totalRows
        return rows[mappedIndex] ?: TerminalRow(columns).also { rows[mappedIndex] = it }
    }

    /**
     * Gets a screen row (0-based from top of visible screen).
     */
    fun getScreenRow(row: Int): TerminalRow {
        return getRow(scrollbackRows + row)
    }

    /**
     * Scrolls the screen up by one line, adding a new blank line at the bottom.
     * The old top line goes into scrollback.
     */
    fun scrollUp(topMargin: Int, bottomMargin: Int) {
        if (topMargin == 0 && bottomMargin == screenRows - 1) {
            // Simple case: scroll entire screen
            if (activeRows < totalRows) {
                activeRows++
            }
            scrollbackRows = activeRows - screenRows
            val newRowIndex = (firstRow + activeRows - 1) % totalRows
            rows[newRowIndex] = TerminalRow(columns)
        } else {
            // Scroll a region
            val savedRow = getScreenRow(topMargin)
            for (i in topMargin until bottomMargin) {
                setScreenRow(i, getScreenRow(i + 1))
            }
            val blankRow = TerminalRow(columns)
            setScreenRow(bottomMargin, blankRow)
        }
    }

    /**
     * Scrolls the screen down by one line within the given margins.
     */
    fun scrollDown(topMargin: Int, bottomMargin: Int) {
        for (i in bottomMargin downTo topMargin + 1) {
            setScreenRow(i, getScreenRow(i - 1))
        }
        setScreenRow(topMargin, TerminalRow(columns))
    }

    private fun setScreenRow(row: Int, termRow: TerminalRow) {
        val index = (firstRow + scrollbackRows + row) % totalRows
        rows[index] = termRow
    }

    /**
     * Clears the entire buffer including scrollback.
     */
    fun clearAll() {
        for (i in 0 until totalRows) {
            rows[i] = null
        }
        firstRow = 0
        activeRows = screenRows
        scrollbackRows = 0
        for (i in 0 until screenRows) {
            rows[i] = TerminalRow(columns)
        }
    }

    /**
     * Clears the scrollback buffer.
     */
    fun clearScrollback() {
        if (scrollbackRows > 0) {
            // Shift screen content to the beginning
            val newRows: Array<TerminalRow?> = arrayOfNulls(totalRows)
            for (i in 0 until screenRows) {
                newRows[i] = getScreenRow(i)
            }
            rows = newRows
            firstRow = 0
            activeRows = screenRows
            scrollbackRows = 0
        }
    }

    /**
     * Resizes the buffer to new dimensions.
     */
    fun resize(newColumns: Int, newRows: Int, newTotalRows: Int, cursor: IntArray) {
        val oldScreenRows = screenRows
        val oldColumns = columns

        val newBuffer: Array<TerminalRow?> = arrayOfNulls(newTotalRows)
        val rowsToCopy = minOf(newRows, activeRows)
        val startRow = if (activeRows > newRows) activeRows - newRows else 0

        for (i in 0 until rowsToCopy) {
            val oldRow = getRow(startRow + i)
            val newRow = TerminalRow(newColumns)
            val colsToCopy = minOf(oldColumns, newColumns)
            for (col in 0 until colsToCopy) {
                newRow.setChar(col, oldRow.getChar(col), oldRow.getStyle(col))
            }
            newBuffer[i] = newRow
        }
        for (i in rowsToCopy until newRows) {
            newBuffer[i] = TerminalRow(newColumns)
        }

        rows = newBuffer
        totalRows = newTotalRows
        columns = newColumns
        screenRows = newRows
        firstRow = 0
        activeRows = maxOf(rowsToCopy, newRows)
        scrollbackRows = activeRows - screenRows

        // Adjust cursor
        cursor[0] = minOf(cursor[0], newRows - 1)
        cursor[1] = minOf(cursor[1], newColumns - 1)
    }

    /**
     * Gets all text content as a string (for copying).
     */
    fun getTextContent(startRow: Int, startCol: Int, endRow: Int, endCol: Int): String {
        val sb = StringBuilder()
        for (row in startRow..endRow) {
            val termRow = getScreenRow(row)
            val colStart = if (row == startRow) startCol else 0
            val colEnd = if (row == endRow) endCol else columns - 1
            for (col in colStart..colEnd) {
                val char = termRow.getChar(col)
                if (char != 0) {
                    sb.appendCodePoint(char)
                }
            }
            if (row < endRow) {
                sb.append('\n')
            }
        }
        return sb.toString()
    }
}
