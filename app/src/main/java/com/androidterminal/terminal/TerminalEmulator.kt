package com.androidterminal.terminal

import android.util.Log

/**
 * Core terminal emulator that parses ANSI/xterm escape sequences and manages terminal state.
 * Supports VT100/VT220/xterm compatible escape sequences.
 */
class TerminalEmulator(
    var rows: Int,
    var columns: Int,
    private val scrollbackCapacity: Int = 2000
) {
    companion object {
        private const val TAG = "TerminalEmulator"

        // Parser states
        private const val STATE_NORMAL = 0
        private const val STATE_ESCAPE = 1
        private const val STATE_CSI = 2
        private const val STATE_CSI_PARAM = 3
        private const val STATE_OSC = 4
        private const val STATE_OSC_STRING = 5
        private const val STATE_CHARSET = 6
        private const val STATE_DCS = 7

        // Max CSI parameters
        private const val MAX_CSI_PARAMS = 16
        private const val MAX_OSC_LENGTH = 4096
    }

    /** The screen buffer */
    var buffer: TerminalBuffer = TerminalBuffer(columns, scrollbackCapacity + rows, rows)
        private set

    /** Cursor position */
    var cursorRow: Int = 0
        private set
    var cursorCol: Int = 0
        private set

    /** Cursor visibility */
    var cursorVisible: Boolean = true
        private set

    /** Current text style */
    private var currentStyle: Long = TerminalRow.packStyle(7, 0)

    /** Scroll margins */
    private var topMargin: Int = 0
    private var bottomMargin: Int = rows - 1

    /** Parser state */
    private var parseState: Int = STATE_NORMAL
    private val csiParams = IntArray(MAX_CSI_PARAMS)
    private var csiParamCount: Int = 0
    private var csiPrivate: Boolean = false
    private val oscBuffer = StringBuilder(MAX_OSC_LENGTH)

    /** Terminal modes */
    private var applicationCursorKeys: Boolean = false
    private var applicationKeypad: Boolean = false
    private var autoWrapMode: Boolean = true
    private var insertMode: Boolean = false
    private var originMode: Boolean = false
    private var bracketedPasteMode: Boolean = false
    private var mouseTracking: Boolean = false
    private var alternateBuffer: Boolean = false

    /** Saved cursor state */
    private var savedCursorRow: Int = 0
    private var savedCursorCol: Int = 0
    private var savedStyle: Long = 0L

    /** Alternate screen buffer */
    private var mainBuffer: TerminalBuffer? = null
    private var mainCursorRow: Int = 0
    private var mainCursorCol: Int = 0

    /** Title change callback */
    var onTitleChanged: ((String) -> Unit)? = null

    /** Bell callback */
    var onBell: (() -> Unit)? = null

    /** Flag to signal content changed */
    var contentChanged: Boolean = false
        private set

    fun clearContentChanged() {
        contentChanged = false
    }

    /**
     * Processes incoming bytes from the PTY.
     */
    fun processBytes(data: ByteArray, length: Int) {
        val text = String(data, 0, length, Charsets.UTF_8)
        for (char in text) {
            processCodePoint(char.code)
        }
        contentChanged = true
    }

    private fun processCodePoint(cp: Int) {
        when (parseState) {
            STATE_NORMAL -> processNormal(cp)
            STATE_ESCAPE -> processEscape(cp)
            STATE_CSI, STATE_CSI_PARAM -> processCSI(cp)
            STATE_OSC, STATE_OSC_STRING -> processOSC(cp)
            STATE_CHARSET -> {
                // Consume charset designation byte and return to normal
                parseState = STATE_NORMAL
            }
            STATE_DCS -> {
                if (cp == 0x1B || cp == 0x9C) {
                    parseState = STATE_NORMAL
                }
            }
        }
    }

    private fun processNormal(cp: Int) {
        when (cp) {
            0 -> {} // NUL - ignore
            7 -> onBell?.invoke() // BEL
            8 -> { // BS - Backspace
                if (cursorCol > 0) cursorCol--
            }
            9 -> { // HT - Tab
                cursorCol = minOf((cursorCol + 8) and 7.inv(), columns - 1)
            }
            10, 11, 12 -> { // LF, VT, FF - Line feed
                lineFeed()
            }
            13 -> { // CR - Carriage return
                cursorCol = 0
            }
            14 -> {} // SO - Shift Out (ignored)
            15 -> {} // SI - Shift In (ignored)
            27 -> { // ESC
                parseState = STATE_ESCAPE
            }
            else -> {
                if (cp >= 32) {
                    emitChar(cp)
                }
            }
        }
    }

    private fun processEscape(cp: Int) {
        parseState = STATE_NORMAL
        when (cp) {
            '['.code -> { // CSI
                parseState = STATE_CSI
                csiParamCount = 0
                csiPrivate = false
                for (i in csiParams.indices) csiParams[i] = -1
            }
            ']'.code -> { // OSC
                parseState = STATE_OSC
                oscBuffer.clear()
            }
            '('.code, ')'.code, '*'.code, '+'.code -> { // Charset designation
                parseState = STATE_CHARSET
            }
            'P'.code -> { // DCS
                parseState = STATE_DCS
            }
            'D'.code -> lineFeed() // IND - Index
            'E'.code -> { // NEL - Next Line
                cursorCol = 0
                lineFeed()
            }
            'H'.code -> {} // HTS - Set tab stop (simplified)
            'M'.code -> { // RI - Reverse Index
                if (cursorRow == topMargin) {
                    buffer.scrollDown(topMargin, bottomMargin)
                } else if (cursorRow > 0) {
                    cursorRow--
                }
            }
            '7'.code -> { // DECSC - Save Cursor
                savedCursorRow = cursorRow
                savedCursorCol = cursorCol
                savedStyle = currentStyle
            }
            '8'.code -> { // DECRC - Restore Cursor
                cursorRow = savedCursorRow
                cursorCol = savedCursorCol
                currentStyle = savedStyle
            }
            'c'.code -> { // RIS - Full Reset
                reset()
            }
            '='.code -> applicationKeypad = true
            '>'.code -> applicationKeypad = false
        }
    }

    private fun processCSI(cp: Int) {
        when {
            cp == '?'.code && parseState == STATE_CSI -> {
                csiPrivate = true
                parseState = STATE_CSI_PARAM
            }
            cp in '0'.code..'9'.code -> {
                parseState = STATE_CSI_PARAM
                if (csiParamCount == 0) csiParamCount = 1
                val current = if (csiParams[csiParamCount - 1] == -1) 0 else csiParams[csiParamCount - 1]
                csiParams[csiParamCount - 1] = current * 10 + (cp - '0'.code)
            }
            cp == ';'.code -> {
                parseState = STATE_CSI_PARAM
                if (csiParamCount < MAX_CSI_PARAMS) {
                    csiParamCount++
                }
            }
            else -> {
                parseState = STATE_NORMAL
                executeCSI(cp)
            }
        }
    }

    private fun processOSC(cp: Int) {
        when {
            cp == ';'.code && parseState == STATE_OSC -> {
                parseState = STATE_OSC_STRING
            }
            parseState == STATE_OSC -> {
                oscBuffer.append(cp.toChar())
            }
            cp == 7 || cp == 0x9C -> { // BEL or ST terminates OSC
                parseState = STATE_NORMAL
                executeOSC()
            }
            cp == 27 -> { // ESC might start ST (ESC \)
                parseState = STATE_NORMAL
                executeOSC()
            }
            else -> {
                if (oscBuffer.length < MAX_OSC_LENGTH) {
                    oscBuffer.append(cp.toChar())
                }
            }
        }
    }

    private fun executeOSC() {
        val content = oscBuffer.toString()
        // Parse OSC command number
        val semicolonIdx = content.indexOf(';')
        if (semicolonIdx < 0 && content.isNotEmpty()) {
            // Just a command number with the buffer content from OSC_STRING state
            try {
                val cmd = content.toInt()
                // No argument
            } catch (_: NumberFormatException) {
                // Try parsing as "cmd;text"
                handleOSCText(content)
            }
            return
        }

        handleOSCText(content)
    }

    private fun handleOSCText(content: String) {
        // The OSC buffer may contain the title directly
        // Common: OSC 0;title ST or OSC 2;title ST
        val title = content.trimStart('0', '1', '2', ';')
        if (title.isNotEmpty()) {
            onTitleChanged?.invoke(title)
        }
    }

    private fun executeCSI(finalChar: Int) {
        val param0 = if (csiParamCount > 0 && csiParams[0] != -1) csiParams[0] else -1
        val param1 = if (csiParamCount > 1 && csiParams[1] != -1) csiParams[1] else -1

        if (csiPrivate) {
            executePrivateCSI(finalChar, param0)
            return
        }

        when (finalChar) {
            '@'.code -> { // ICH - Insert Characters
                val count = maxOf(1, if (param0 == -1) 1 else param0)
                buffer.getScreenRow(cursorRow).insertBlank(cursorCol, count, currentStyle)
            }
            'A'.code -> { // CUU - Cursor Up
                val n = maxOf(1, if (param0 == -1) 1 else param0)
                cursorRow = maxOf(topMargin, cursorRow - n)
            }
            'B'.code -> { // CUD - Cursor Down
                val n = maxOf(1, if (param0 == -1) 1 else param0)
                cursorRow = minOf(bottomMargin, cursorRow + n)
            }
            'C'.code -> { // CUF - Cursor Forward
                val n = maxOf(1, if (param0 == -1) 1 else param0)
                cursorCol = minOf(columns - 1, cursorCol + n)
            }
            'D'.code -> { // CUB - Cursor Back
                val n = maxOf(1, if (param0 == -1) 1 else param0)
                cursorCol = maxOf(0, cursorCol - n)
            }
            'E'.code -> { // CNL - Cursor Next Line
                val n = maxOf(1, if (param0 == -1) 1 else param0)
                cursorRow = minOf(bottomMargin, cursorRow + n)
                cursorCol = 0
            }
            'F'.code -> { // CPL - Cursor Previous Line
                val n = maxOf(1, if (param0 == -1) 1 else param0)
                cursorRow = maxOf(topMargin, cursorRow - n)
                cursorCol = 0
            }
            'G'.code -> { // CHA - Cursor Horizontal Absolute
                val col = if (param0 == -1) 1 else param0
                cursorCol = minOf(columns - 1, maxOf(0, col - 1))
            }
            'H'.code, 'f'.code -> { // CUP/HVP - Cursor Position
                val row = if (param0 == -1) 1 else param0
                val col = if (param1 == -1) 1 else param1
                cursorRow = minOf(rows - 1, maxOf(0, row - 1))
                cursorCol = minOf(columns - 1, maxOf(0, col - 1))
                if (originMode) {
                    cursorRow += topMargin
                    cursorRow = minOf(bottomMargin, cursorRow)
                }
            }
            'J'.code -> { // ED - Erase in Display
                eraseInDisplay(if (param0 == -1) 0 else param0)
            }
            'K'.code -> { // EL - Erase in Line
                eraseInLine(if (param0 == -1) 0 else param0)
            }
            'L'.code -> { // IL - Insert Lines
                val n = maxOf(1, if (param0 == -1) 1 else param0)
                if (cursorRow in topMargin..bottomMargin) {
                    for (i in 0 until n) {
                        buffer.scrollDown(cursorRow, bottomMargin)
                    }
                }
            }
            'M'.code -> { // DL - Delete Lines
                val n = maxOf(1, if (param0 == -1) 1 else param0)
                if (cursorRow in topMargin..bottomMargin) {
                    for (i in 0 until n) {
                        buffer.scrollUp(cursorRow, bottomMargin)
                    }
                }
            }
            'P'.code -> { // DCH - Delete Characters
                val count = maxOf(1, if (param0 == -1) 1 else param0)
                buffer.getScreenRow(cursorRow).deleteChars(cursorCol, count, currentStyle)
            }
            'S'.code -> { // SU - Scroll Up
                val n = maxOf(1, if (param0 == -1) 1 else param0)
                for (i in 0 until n) buffer.scrollUp(topMargin, bottomMargin)
            }
            'T'.code -> { // SD - Scroll Down
                val n = maxOf(1, if (param0 == -1) 1 else param0)
                for (i in 0 until n) buffer.scrollDown(topMargin, bottomMargin)
            }
            'X'.code -> { // ECH - Erase Characters
                val n = maxOf(1, if (param0 == -1) 1 else param0)
                val row = buffer.getScreenRow(cursorRow)
                for (i in cursorCol until minOf(cursorCol + n, columns)) {
                    row.setChar(i, ' '.code, currentStyle)
                }
            }
            'd'.code -> { // VPA - Line Position Absolute
                val row = if (param0 == -1) 1 else param0
                cursorRow = minOf(rows - 1, maxOf(0, row - 1))
            }
            'm'.code -> { // SGR - Select Graphic Rendition
                executeSGR()
            }
            'n'.code -> { // DSR - Device Status Report
                // Handled by session (needs to write response to PTY)
            }
            'r'.code -> { // DECSTBM - Set Scrolling Region
                val top = if (param0 == -1) 1 else param0
                val bottom = if (param1 == -1) rows else param1
                topMargin = maxOf(0, top - 1)
                bottomMargin = minOf(rows - 1, bottom - 1)
                if (topMargin >= bottomMargin) {
                    topMargin = 0
                    bottomMargin = rows - 1
                }
                cursorRow = if (originMode) topMargin else 0
                cursorCol = 0
            }
            's'.code -> { // SCP - Save Cursor Position
                savedCursorRow = cursorRow
                savedCursorCol = cursorCol
            }
            'u'.code -> { // RCP - Restore Cursor Position
                cursorRow = savedCursorRow
                cursorCol = savedCursorCol
            }
            't'.code -> { // Window manipulation (mostly ignored)
            }
            'c'.code -> { // DA - Device Attributes
                // Response handled by session
            }
        }
    }

    private fun executePrivateCSI(finalChar: Int, param: Int) {
        val p = if (param == -1) 0 else param
        val enable = finalChar == 'h'.code

        when (finalChar) {
            'h'.code, 'l'.code -> {
                when (p) {
                    1 -> applicationCursorKeys = enable
                    4 -> insertMode = enable
                    6 -> {
                        originMode = enable
                        cursorRow = if (originMode) topMargin else 0
                        cursorCol = 0
                    }
                    7 -> autoWrapMode = enable
                    12 -> {} // Start/Stop blinking cursor (ignored)
                    25 -> cursorVisible = enable
                    47, 1047 -> switchBuffer(enable)
                    1000 -> mouseTracking = enable
                    1002 -> mouseTracking = enable // Button event tracking
                    1003 -> mouseTracking = enable // Any event tracking
                    1049 -> { // Save cursor and switch to alternate buffer
                        if (enable) {
                            savedCursorRow = cursorRow
                            savedCursorCol = cursorCol
                            switchBuffer(true)
                        } else {
                            switchBuffer(false)
                            cursorRow = savedCursorRow
                            cursorCol = savedCursorCol
                        }
                    }
                    2004 -> bracketedPasteMode = enable
                }
            }
        }
    }

    private fun executeSGR() {
        if (csiParamCount == 0) {
            // Reset all attributes
            currentStyle = TerminalRow.packStyle(7, 0)
            return
        }

        var i = 0
        while (i < csiParamCount) {
            val p = if (csiParams[i] == -1) 0 else csiParams[i]
            when (p) {
                0 -> currentStyle = TerminalRow.packStyle(7, 0)
                1 -> currentStyle = currentStyle or TerminalRow.ATTR_BOLD
                2 -> currentStyle = currentStyle or TerminalRow.ATTR_DIM
                3 -> currentStyle = currentStyle or TerminalRow.ATTR_ITALIC
                4 -> currentStyle = currentStyle or TerminalRow.ATTR_UNDERLINE
                5 -> currentStyle = currentStyle or TerminalRow.ATTR_BLINK
                7 -> currentStyle = currentStyle or TerminalRow.ATTR_INVERSE
                8 -> currentStyle = currentStyle or TerminalRow.ATTR_INVISIBLE
                9 -> currentStyle = currentStyle or TerminalRow.ATTR_STRIKETHROUGH
                22 -> currentStyle = currentStyle and (TerminalRow.ATTR_BOLD or TerminalRow.ATTR_DIM).inv()
                23 -> currentStyle = currentStyle and TerminalRow.ATTR_ITALIC.inv()
                24 -> currentStyle = currentStyle and TerminalRow.ATTR_UNDERLINE.inv()
                25 -> currentStyle = currentStyle and TerminalRow.ATTR_BLINK.inv()
                27 -> currentStyle = currentStyle and TerminalRow.ATTR_INVERSE.inv()
                28 -> currentStyle = currentStyle and TerminalRow.ATTR_INVISIBLE.inv()
                29 -> currentStyle = currentStyle and TerminalRow.ATTR_STRIKETHROUGH.inv()
                in 30..37 -> { // Set foreground color
                    val fg = p - 30
                    currentStyle = (currentStyle and 0xFF.toLong().inv()) or fg.toLong()
                }
                38 -> { // Extended foreground color
                    if (i + 1 < csiParamCount && csiParams[i + 1] == 5 && i + 2 < csiParamCount) {
                        val color = csiParams[i + 2]
                        currentStyle = (currentStyle and 0xFF.toLong().inv()) or (color.toLong() and 0xFF)
                        i += 2
                    }
                }
                39 -> { // Default foreground
                    currentStyle = (currentStyle and 0xFF.toLong().inv()) or 7L
                }
                in 40..47 -> { // Set background color
                    val bg = p - 40
                    currentStyle = (currentStyle and (0xFF.toLong() shl 8).inv()) or (bg.toLong() shl 8)
                }
                48 -> { // Extended background color
                    if (i + 1 < csiParamCount && csiParams[i + 1] == 5 && i + 2 < csiParamCount) {
                        val color = csiParams[i + 2]
                        currentStyle = (currentStyle and (0xFF.toLong() shl 8).inv()) or ((color.toLong() and 0xFF) shl 8)
                        i += 2
                    }
                }
                49 -> { // Default background
                    currentStyle = (currentStyle and (0xFF.toLong() shl 8).inv())
                }
                in 90..97 -> { // Bright foreground colors
                    val fg = p - 90 + 8
                    currentStyle = (currentStyle and 0xFF.toLong().inv()) or fg.toLong()
                }
                in 100..107 -> { // Bright background colors
                    val bg = p - 100 + 8
                    currentStyle = (currentStyle and (0xFF.toLong() shl 8).inv()) or (bg.toLong() shl 8)
                }
            }
            i++
        }
    }

    private fun emitChar(cp: Int) {
        // Handle auto-wrap
        if (cursorCol >= columns) {
            if (autoWrapMode) {
                buffer.getScreenRow(cursorRow).isWrapped = true
                cursorCol = 0
                lineFeed()
            } else {
                cursorCol = columns - 1
            }
        }

        if (insertMode) {
            buffer.getScreenRow(cursorRow).insertBlank(cursorCol, 1, currentStyle)
        }

        buffer.getScreenRow(cursorRow).setChar(cursorCol, cp, currentStyle)
        cursorCol++
    }

    private fun lineFeed() {
        if (cursorRow == bottomMargin) {
            buffer.scrollUp(topMargin, bottomMargin)
        } else if (cursorRow < rows - 1) {
            cursorRow++
        }
    }

    private fun eraseInDisplay(mode: Int) {
        when (mode) {
            0 -> { // Erase from cursor to end of display
                eraseInLine(0)
                for (row in cursorRow + 1 until rows) {
                    buffer.getScreenRow(row).clear(currentStyle)
                }
            }
            1 -> { // Erase from start to cursor
                eraseInLine(1)
                for (row in 0 until cursorRow) {
                    buffer.getScreenRow(row).clear(currentStyle)
                }
            }
            2 -> { // Erase entire display
                for (row in 0 until rows) {
                    buffer.getScreenRow(row).clear(currentStyle)
                }
            }
            3 -> { // Erase scrollback
                buffer.clearScrollback()
            }
        }
    }

    private fun eraseInLine(mode: Int) {
        val row = buffer.getScreenRow(cursorRow)
        when (mode) {
            0 -> row.clearFrom(cursorCol, currentStyle) // Erase from cursor to end
            1 -> row.clearTo(cursorCol, currentStyle) // Erase from start to cursor
            2 -> row.clear(currentStyle) // Erase entire line
        }
    }

    private fun switchBuffer(toAlternate: Boolean) {
        if (toAlternate && !alternateBuffer) {
            mainBuffer = buffer
            mainCursorRow = cursorRow
            mainCursorCol = cursorCol
            buffer = TerminalBuffer(columns, rows, rows)
            cursorRow = 0
            cursorCol = 0
            alternateBuffer = true
        } else if (!toAlternate && alternateBuffer) {
            mainBuffer?.let { buffer = it }
            cursorRow = mainCursorRow
            cursorCol = mainCursorCol
            mainBuffer = null
            alternateBuffer = false
        }
    }

    /**
     * Resizes the terminal.
     */
    fun resize(newRows: Int, newCols: Int) {
        val cursor = intArrayOf(cursorRow, cursorCol)
        buffer.resize(newCols, newRows, scrollbackCapacity + newRows, cursor)
        rows = newRows
        columns = newCols
        cursorRow = cursor[0]
        cursorCol = cursor[1]
        topMargin = 0
        bottomMargin = rows - 1
        contentChanged = true
    }

    /**
     * Resets the terminal to initial state.
     */
    fun reset() {
        cursorRow = 0
        cursorCol = 0
        cursorVisible = true
        currentStyle = TerminalRow.packStyle(7, 0)
        topMargin = 0
        bottomMargin = rows - 1
        parseState = STATE_NORMAL
        applicationCursorKeys = false
        applicationKeypad = false
        autoWrapMode = true
        insertMode = false
        originMode = false
        bracketedPasteMode = false
        mouseTracking = false

        if (alternateBuffer) {
            switchBuffer(false)
        }
        buffer.clearAll()
        contentChanged = true
    }

    /** Whether application cursor keys mode is active */
    fun isApplicationCursorKeys(): Boolean = applicationCursorKeys

    /** Whether bracketed paste mode is active */
    fun isBracketedPasteMode(): Boolean = bracketedPasteMode

    /** Get the current style */
    fun getCurrentStyle(): Long = currentStyle
}
