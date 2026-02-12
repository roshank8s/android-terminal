package com.roshank8s.androidterminal.terminal

import android.util.Log

/**
 * VT100/VT420/xterm-compatible terminal emulator.
 *
 * Implements an escape sequence state machine following the DEC ANSI parser model.
 * Processes incoming bytes from the shell and updates the terminal buffer accordingly.
 *
 * Supported features:
 * - Full SGR (Select Graphic Rendition) with 256-color and true color
 * - Cursor movement and positioning (CUU, CUD, CUF, CUB, CUP, etc.)
 * - Screen clearing (ED, EL)
 * - Scroll regions (DECSTBM)
 * - Insert/Delete lines and characters (IL, DL, ICH, DCH)
 * - Alternate screen buffer (DECSET 1049)
 * - Line drawing character sets (G0/G1)
 * - Tab stops
 * - DEC private modes
 * - OSC sequences (window title, color changes)
 * - Mouse reporting (basic)
 */
class TerminalEmulator(
    var columns: Int,
    var rows: Int,
    private val client: TerminalClient
) {
    companion object {
        private const val TAG = "TerminalEmulator"

        // Escape sequence states
        private const val ESC_NONE = 0
        private const val ESC = 1
        private const val ESC_CSI = 2
        private const val ESC_CSI_PARAM = 3
        private const val ESC_OSC = 4
        private const val ESC_OSC_ESC = 5
        private const val ESC_SELECT_CHARSET = 6
        private const val ESC_DCS = 7
        private const val ESC_CSI_INTERMEDIATE = 8

        // DEC private mode flags (bitmask)
        const val DECSET_CURSOR_KEYS_APPLICATION = 1          // DECCKM
        const val DECSET_REVERSE_VIDEO = 1 shl 1              // DECSCNM
        const val DECSET_ORIGIN_MODE = 1 shl 2                // DECOM
        const val DECSET_AUTO_WRAP = 1 shl 3                  // DECAWM
        const val DECSET_CURSOR_VISIBLE = 1 shl 4             // DECTCEM
        const val DECSET_ALT_SCREEN = 1 shl 5                 // Alt screen buffer
        const val DECSET_ALT_SCREEN_SAVE_CURSOR = 1 shl 6     // 1049
        const val DECSET_MOUSE_TRACKING_PRESS = 1 shl 7       // 1000
        const val DECSET_MOUSE_TRACKING_BUTTON = 1 shl 8      // 1002
        const val DECSET_MOUSE_TRACKING_ANY = 1 shl 9         // 1003
        const val DECSET_MOUSE_PROTOCOL_SGR = 1 shl 10        // 1006
        const val DECSET_BRACKETED_PASTE = 1 shl 11           // 2004
        const val DECSET_KEYPAD_APPLICATION = 1 shl 12        // DECKPAM
        const val DECSET_FOCUS_EVENTS = 1 shl 13              // 1004
    }

    interface TerminalClient {
        fun onTextChanged(startRow: Int, endRow: Int)
        fun onTitleChanged(title: String)
        fun onBell()
        fun onClipboardText(text: String)
        fun onColorsChanged()
        fun write(data: ByteArray)
    }

    /** Main screen buffer. */
    val mainBuffer = TerminalBuffer(columns, rows)

    /** Alternate screen buffer (used by fullscreen apps like vim, htop). */
    val altBuffer = TerminalBuffer(columns, rows, maxScrollback = 0)

    /** Currently active buffer. */
    var screen = mainBuffer
        private set

    /** Terminal color palette. */
    val colors = TerminalColors()

    // Cursor state
    var cursorRow = 0
        private set
    var cursorCol = 0
        private set
    private var savedCursorRow = 0
    private var savedCursorCol = 0
    private var savedStyle = TextStyle.DEFAULT

    // Current text style
    var currentStyle = TextStyle.DEFAULT
        private set
    private var foregroundColor = TerminalColors.COLOR_INDEX_FOREGROUND
    private var backgroundColor = TerminalColors.COLOR_INDEX_BACKGROUND
    private var currentEffects = 0

    // Scroll region
    var topMargin = 0
        private set
    var bottomMargin = rows - 1
        private set

    // DEC private mode flags
    var decSetFlags = DECSET_AUTO_WRAP or DECSET_CURSOR_VISIBLE
        private set

    // Escape sequence parser state
    private var escapeState = ESC_NONE
    private val csiParams = IntArray(16)
    private var csiParamIndex = 0
    private var csiIntermediateChar = 0
    private val oscBuilder = StringBuilder()

    // Character set selection
    private var useLineDrawingG0 = false
    private var useLineDrawingG1 = false
    private var charsetSelectIndex = 0

    // Tab stops
    private val tabStops = BooleanArray(columns) { it > 0 && it % 8 == 0 }

    // Insert mode (vs replace)
    var insertMode = false
        private set

    // Pending wrap: the cursor is at the last column and a wrap is pending
    private var pendingWrap = false

    /** Whether cursor key sequences use application mode. */
    val isCursorKeysApplicationMode: Boolean
        get() = (decSetFlags and DECSET_CURSOR_KEYS_APPLICATION) != 0

    /** Whether keypad is in application mode. */
    val isKeypadApplicationMode: Boolean
        get() = (decSetFlags and DECSET_KEYPAD_APPLICATION) != 0

    /** Whether mouse tracking is enabled. */
    val isMouseTrackingEnabled: Boolean
        get() = (decSetFlags and (DECSET_MOUSE_TRACKING_PRESS or
                DECSET_MOUSE_TRACKING_BUTTON or DECSET_MOUSE_TRACKING_ANY)) != 0

    /** Whether bracketed paste mode is enabled. */
    val isBracketedPasteMode: Boolean
        get() = (decSetFlags and DECSET_BRACKETED_PASTE) != 0

    val isCursorVisible: Boolean
        get() = (decSetFlags and DECSET_CURSOR_VISIBLE) != 0

    /**
     * Process incoming bytes from the shell.
     * This is the main entry point for terminal output processing.
     */
    fun processBytes(data: ByteArray, length: Int) {
        var i = 0
        while (i < length) {
            val b = data[i].toInt() and 0xFF
            when (escapeState) {
                ESC_NONE -> processNormalByte(b)
                ESC -> processEscByte(b)
                ESC_CSI, ESC_CSI_PARAM -> processCsiByte(b)
                ESC_CSI_INTERMEDIATE -> processCsiIntermediateByte(b)
                ESC_OSC -> processOscByte(b)
                ESC_OSC_ESC -> processOscEscByte(b)
                ESC_SELECT_CHARSET -> processCharsetSelect(b)
                ESC_DCS -> processDcsByte(b)
            }
            i++
        }
        client.onTextChanged(0, rows - 1)
    }

    private fun processNormalByte(b: Int) {
        when (b) {
            0 -> { /* NUL - ignore */ }
            7 -> client.onBell()  // BEL
            8 -> { // BS - Backspace
                if (cursorCol > 0) {
                    cursorCol--
                    pendingWrap = false
                }
            }
            9 -> { // HT - Horizontal Tab
                cursorCol = nextTabStop(cursorCol)
                pendingWrap = false
            }
            10, 11, 12 -> { // LF, VT, FF - Line Feed
                lineFeed()
                pendingWrap = false
            }
            13 -> { // CR - Carriage Return
                cursorCol = 0
                pendingWrap = false
            }
            14 -> useLineDrawingG1 = true   // SO - Shift Out (G1)
            15 -> useLineDrawingG1 = false  // SI - Shift In (G0)
            27 -> escapeState = ESC         // ESC
            in 32..126, in 128..255 -> {
                emitChar(b)
            }
        }
    }

    private fun emitChar(codePoint: Int) {
        var c = codePoint.toChar()

        // Handle line drawing characters
        if (useLineDrawingG0 || useLineDrawingG1) {
            c = mapLineDrawingChar(c)
        }

        // Handle pending wrap
        if (pendingWrap) {
            screen.getScreenRow(cursorRow).isLineWrap = true
            lineFeed()
            cursorCol = 0
            pendingWrap = false
        }

        // Insert mode: shift existing characters right
        if (insertMode) {
            screen.getScreenRow(cursorRow).insertBlank(cursorCol, 1, currentStyle)
        }

        // Write the character
        screen.getScreenRow(cursorRow).setChar(cursorCol, c, currentStyle)

        // Advance cursor
        if (cursorCol < columns - 1) {
            cursorCol++
        } else if ((decSetFlags and DECSET_AUTO_WRAP) != 0) {
            pendingWrap = true
        }
    }

    private fun lineFeed() {
        if (cursorRow == bottomMargin) {
            screen.scrollUp(topMargin, bottomMargin, currentStyle)
        } else if (cursorRow < rows - 1) {
            cursorRow++
        }
    }

    private fun processEscByte(b: Int) {
        escapeState = ESC_NONE
        when (b.toChar()) {
            '[' -> {
                escapeState = ESC_CSI
                csiParams.fill(0)
                csiParamIndex = 0
                csiIntermediateChar = 0
            }
            ']' -> {
                escapeState = ESC_OSC
                oscBuilder.clear()
            }
            '(' -> {
                escapeState = ESC_SELECT_CHARSET
                charsetSelectIndex = 0
            }
            ')' -> {
                escapeState = ESC_SELECT_CHARSET
                charsetSelectIndex = 1
            }
            'P' -> escapeState = ESC_DCS
            'D' -> lineFeed()  // IND - Index (move down)
            'E' -> { // NEL - Next Line
                cursorCol = 0
                lineFeed()
            }
            'H' -> { // HTS - Set Tab Stop
                if (cursorCol in tabStops.indices) tabStops[cursorCol] = true
            }
            'M' -> { // RI - Reverse Index (move up, scroll if needed)
                if (cursorRow == topMargin) {
                    screen.scrollDown(topMargin, bottomMargin, currentStyle)
                } else if (cursorRow > 0) {
                    cursorRow--
                }
            }
            '7' -> saveCursor()    // DECSC - Save Cursor
            '8' -> restoreCursor() // DECRC - Restore Cursor
            'c' -> reset()         // RIS - Full Reset
            '=' -> decSetFlags = decSetFlags or DECSET_KEYPAD_APPLICATION     // DECKPAM
            '>' -> decSetFlags = decSetFlags and DECSET_KEYPAD_APPLICATION.inv() // DECKPNM
        }
    }

    private fun processCsiByte(b: Int) {
        when {
            b in 0x30..0x39 -> { // Digit
                escapeState = ESC_CSI_PARAM
                csiParams[csiParamIndex] = csiParams[csiParamIndex] * 10 + (b - 0x30)
            }
            b == 0x3B -> { // Semicolon - parameter separator
                escapeState = ESC_CSI_PARAM
                if (csiParamIndex < csiParams.size - 1) csiParamIndex++
            }
            b == 0x3F -> { // '?' - DEC private mode prefix
                csiIntermediateChar = b
            }
            b in 0x20..0x2F -> { // Intermediate byte
                csiIntermediateChar = b
                escapeState = ESC_CSI_INTERMEDIATE
            }
            b in 0x40..0x7E -> { // Final byte
                handleCsiSequence(b.toChar())
                escapeState = ESC_NONE
            }
            else -> escapeState = ESC_NONE
        }
    }

    private fun processCsiIntermediateByte(b: Int) {
        if (b in 0x40..0x7E) {
            handleCsiSequence(b.toChar())
            escapeState = ESC_NONE
        } else if (b !in 0x20..0x2F) {
            escapeState = ESC_NONE
        }
    }

    private fun handleCsiSequence(finalChar: Char) {
        val param0 = csiParams[0]
        val param1 = csiParams[1]
        val isDecPrivate = csiIntermediateChar == 0x3F

        if (isDecPrivate) {
            when (finalChar) {
                'h' -> handleDecSetMode(true)
                'l' -> handleDecSetMode(false)
            }
            return
        }

        when (finalChar) {
            // Cursor movement
            'A' -> { // CUU - Cursor Up
                cursorRow = maxOf(topMargin, cursorRow - maxOf(1, param0))
                pendingWrap = false
            }
            'B' -> { // CUD - Cursor Down
                cursorRow = minOf(bottomMargin, cursorRow + maxOf(1, param0))
                pendingWrap = false
            }
            'C' -> { // CUF - Cursor Forward
                cursorCol = minOf(columns - 1, cursorCol + maxOf(1, param0))
                pendingWrap = false
            }
            'D' -> { // CUB - Cursor Backward
                cursorCol = maxOf(0, cursorCol - maxOf(1, param0))
                pendingWrap = false
            }
            'E' -> { // CNL - Cursor Next Line
                cursorCol = 0
                cursorRow = minOf(bottomMargin, cursorRow + maxOf(1, param0))
                pendingWrap = false
            }
            'F' -> { // CPL - Cursor Previous Line
                cursorCol = 0
                cursorRow = maxOf(topMargin, cursorRow - maxOf(1, param0))
                pendingWrap = false
            }
            'G' -> { // CHA - Cursor Horizontal Absolute
                cursorCol = clampCol(maxOf(1, param0) - 1)
                pendingWrap = false
            }
            'H', 'f' -> { // CUP/HVP - Cursor Position
                setCursorPosition(maxOf(1, param0) - 1, maxOf(1, param1) - 1)
            }
            'd' -> { // VPA - Vertical Position Absolute
                cursorRow = clampRow(maxOf(1, param0) - 1)
                pendingWrap = false
            }

            // Editing
            'J' -> handleEraseDisplay(param0)
            'K' -> handleEraseLine(param0)
            'L' -> { // IL - Insert Lines
                screen.insertLines(cursorRow, maxOf(1, param0), bottomMargin, currentStyle)
            }
            'M' -> { // DL - Delete Lines
                screen.deleteLines(cursorRow, maxOf(1, param0), bottomMargin, currentStyle)
            }
            '@' -> { // ICH - Insert Characters
                screen.getScreenRow(cursorRow).insertBlank(cursorCol, maxOf(1, param0), currentStyle)
            }
            'P' -> { // DCH - Delete Characters
                screen.getScreenRow(cursorRow).deleteChars(cursorCol, maxOf(1, param0), currentStyle)
            }
            'X' -> { // ECH - Erase Characters
                val count = maxOf(1, param0)
                for (i in cursorCol until minOf(cursorCol + count, columns)) {
                    screen.getScreenRow(cursorRow).setChar(i, ' ', currentStyle)
                }
            }

            // Scrolling
            'S' -> { // SU - Scroll Up
                repeat(maxOf(1, param0)) {
                    screen.scrollUp(topMargin, bottomMargin, currentStyle)
                }
            }
            'T' -> { // SD - Scroll Down
                repeat(maxOf(1, param0)) {
                    screen.scrollDown(topMargin, bottomMargin, currentStyle)
                }
            }

            // Style
            'm' -> handleSGR()

            // Scroll region
            'r' -> { // DECSTBM - Set Top and Bottom Margins
                topMargin = if (param0 > 0) param0 - 1 else 0
                bottomMargin = if (param1 > 0) param1 - 1 else rows - 1
                topMargin = clampRow(topMargin)
                bottomMargin = clampRow(bottomMargin)
                if (topMargin >= bottomMargin) {
                    topMargin = 0
                    bottomMargin = rows - 1
                }
                setCursorPosition(0, 0)
            }

            // Tab control
            'g' -> { // TBC - Tabulation Clear
                when (param0) {
                    0 -> if (cursorCol in tabStops.indices) tabStops[cursorCol] = false
                    3 -> tabStops.fill(false)
                }
            }

            // Mode
            'h' -> { // SM - Set Mode
                if (param0 == 4) insertMode = true
            }
            'l' -> { // RM - Reset Mode
                if (param0 == 4) insertMode = false
            }

            // Device status
            'n' -> { // DSR - Device Status Report
                when (param0) {
                    5 -> client.write("\u001b[0n".toByteArray())  // OK status
                    6 -> client.write("\u001b[${cursorRow + 1};${cursorCol + 1}R".toByteArray())  // Cursor position
                }
            }

            // Device attributes
            'c' -> { // DA - Device Attributes
                // Report as VT420
                client.write("\u001b[?64;1;2;6;9;15;18;21;22c".toByteArray())
            }

            // Soft terminal reset
            'p' -> {
                if (csiIntermediateChar == '!'.code) {
                    softReset()
                }
            }
        }
    }

    private fun handleDecSetMode(set: Boolean) {
        for (i in 0..csiParamIndex) {
            val flag = when (csiParams[i]) {
                1 -> DECSET_CURSOR_KEYS_APPLICATION
                5 -> DECSET_REVERSE_VIDEO
                6 -> DECSET_ORIGIN_MODE
                7 -> DECSET_AUTO_WRAP
                25 -> DECSET_CURSOR_VISIBLE
                47 -> DECSET_ALT_SCREEN
                1000 -> DECSET_MOUSE_TRACKING_PRESS
                1002 -> DECSET_MOUSE_TRACKING_BUTTON
                1003 -> DECSET_MOUSE_TRACKING_ANY
                1004 -> DECSET_FOCUS_EVENTS
                1006 -> DECSET_MOUSE_PROTOCOL_SGR
                1049 -> DECSET_ALT_SCREEN_SAVE_CURSOR
                2004 -> DECSET_BRACKETED_PASTE
                else -> 0
            }

            if (flag == DECSET_ALT_SCREEN || flag == DECSET_ALT_SCREEN_SAVE_CURSOR) {
                if (set) {
                    if (flag == DECSET_ALT_SCREEN_SAVE_CURSOR) saveCursor()
                    screen = altBuffer
                    screen.clearScreen(currentStyle)
                } else {
                    screen = mainBuffer
                    if (flag == DECSET_ALT_SCREEN_SAVE_CURSOR) restoreCursor()
                }
            }

            decSetFlags = if (set) {
                decSetFlags or flag
            } else {
                decSetFlags and flag.inv()
            }
        }
    }

    /** Handle SGR (Select Graphic Rendition) sequences. */
    private fun handleSGR() {
        if (csiParamIndex == 0 && csiParams[0] == 0) {
            // Reset all attributes
            foregroundColor = TerminalColors.COLOR_INDEX_FOREGROUND
            backgroundColor = TerminalColors.COLOR_INDEX_BACKGROUND
            currentEffects = 0
            updateStyle()
            return
        }

        var i = 0
        while (i <= csiParamIndex) {
            when (val code = csiParams[i]) {
                0 -> {
                    foregroundColor = TerminalColors.COLOR_INDEX_FOREGROUND
                    backgroundColor = TerminalColors.COLOR_INDEX_BACKGROUND
                    currentEffects = 0
                }
                1 -> currentEffects = currentEffects or TextStyle.BOLD
                2 -> currentEffects = currentEffects or TextStyle.DIM
                3 -> currentEffects = currentEffects or TextStyle.ITALIC
                4 -> currentEffects = currentEffects or TextStyle.UNDERLINE
                5, 6 -> currentEffects = currentEffects or TextStyle.BLINK
                7 -> currentEffects = currentEffects or TextStyle.INVERSE
                8 -> currentEffects = currentEffects or TextStyle.INVISIBLE
                9 -> currentEffects = currentEffects or TextStyle.STRIKETHROUGH
                21 -> currentEffects = currentEffects or TextStyle.DOUBLE_UNDERLINE
                22 -> currentEffects = currentEffects and (TextStyle.BOLD or TextStyle.DIM).inv()
                23 -> currentEffects = currentEffects and TextStyle.ITALIC.inv()
                24 -> currentEffects = currentEffects and (TextStyle.UNDERLINE or TextStyle.DOUBLE_UNDERLINE or TextStyle.CURLY_UNDERLINE).inv()
                25 -> currentEffects = currentEffects and TextStyle.BLINK.inv()
                27 -> currentEffects = currentEffects and TextStyle.INVERSE.inv()
                28 -> currentEffects = currentEffects and TextStyle.INVISIBLE.inv()
                29 -> currentEffects = currentEffects and TextStyle.STRIKETHROUGH.inv()
                53 -> currentEffects = currentEffects or TextStyle.OVERLINE
                55 -> currentEffects = currentEffects and TextStyle.OVERLINE.inv()

                in 30..37 -> foregroundColor = code - 30
                38 -> {
                    // Extended foreground color
                    i = parseExtendedColor(i, true)
                }
                39 -> foregroundColor = TerminalColors.COLOR_INDEX_FOREGROUND

                in 40..47 -> backgroundColor = code - 40
                48 -> {
                    // Extended background color
                    i = parseExtendedColor(i, false)
                }
                49 -> backgroundColor = TerminalColors.COLOR_INDEX_BACKGROUND

                in 90..97 -> foregroundColor = code - 90 + 8
                in 100..107 -> backgroundColor = code - 100 + 8
            }
            i++
        }
        updateStyle()
    }

    private fun parseExtendedColor(startIndex: Int, isForeground: Boolean): Int {
        if (startIndex + 1 > csiParamIndex) return startIndex

        return when (csiParams[startIndex + 1]) {
            5 -> { // 256-color mode
                if (startIndex + 2 <= csiParamIndex) {
                    val color = csiParams[startIndex + 2]
                    if (color in 0..255) {
                        if (isForeground) foregroundColor = color
                        else backgroundColor = color
                    }
                    startIndex + 2
                } else startIndex + 1
            }
            2 -> { // True color (24-bit RGB)
                if (startIndex + 4 <= csiParamIndex) {
                    val r = csiParams[startIndex + 2] and 0xFF
                    val g = csiParams[startIndex + 3] and 0xFF
                    val b = csiParams[startIndex + 4] and 0xFF
                    // Store as palette index 0 with color encoded in a special way
                    // For now, find nearest 256-color match
                    val nearest = findNearest256Color(r, g, b)
                    if (isForeground) foregroundColor = nearest
                    else backgroundColor = nearest
                    startIndex + 4
                } else startIndex + 1
            }
            else -> startIndex
        }
    }

    private fun findNearest256Color(r: Int, g: Int, b: Int): Int {
        // Check grayscale first
        if (r == g && g == b) {
            if (r < 4) return 16
            if (r > 248) return 231
            return 232 + ((r - 8) / 10).coerceIn(0, 23)
        }
        // Find nearest in 6x6x6 cube
        val ri = ((r - 55).coerceAtLeast(0) / 40.0 + 0.5).toInt().coerceIn(0, 5)
        val gi = ((g - 55).coerceAtLeast(0) / 40.0 + 0.5).toInt().coerceIn(0, 5)
        val bi = ((b - 55).coerceAtLeast(0) / 40.0 + 0.5).toInt().coerceIn(0, 5)
        return 16 + 36 * ri + 6 * gi + bi
    }

    private fun updateStyle() {
        currentStyle = TextStyle.encode(foregroundColor, backgroundColor, currentEffects)
    }

    /** Handle ED (Erase in Display) sequence. */
    private fun handleEraseDisplay(param: Int) {
        when (param) {
            0 -> { // Clear from cursor to end of screen
                screen.getScreenRow(cursorRow).clearFrom(cursorCol, currentStyle)
                for (i in cursorRow + 1 until rows) {
                    screen.getScreenRow(i).clear(currentStyle)
                }
            }
            1 -> { // Clear from start of screen to cursor
                for (i in 0 until cursorRow) {
                    screen.getScreenRow(i).clear(currentStyle)
                }
                screen.getScreenRow(cursorRow).clearTo(cursorCol, currentStyle)
            }
            2 -> { // Clear entire screen
                screen.clearScreen(currentStyle)
            }
            3 -> { // Clear screen + scrollback
                screen.clearScreen(currentStyle)
                screen.clearScrollback()
            }
        }
    }

    /** Handle EL (Erase in Line) sequence. */
    private fun handleEraseLine(param: Int) {
        when (param) {
            0 -> screen.getScreenRow(cursorRow).clearFrom(cursorCol, currentStyle)
            1 -> screen.getScreenRow(cursorRow).clearTo(cursorCol, currentStyle)
            2 -> screen.getScreenRow(cursorRow).clear(currentStyle)
        }
    }

    private fun processOscByte(b: Int) {
        when (b) {
            7 -> { // BEL terminates OSC
                handleOscSequence()
                escapeState = ESC_NONE
            }
            27 -> escapeState = ESC_OSC_ESC // ESC might be start of ST
            else -> {
                if (oscBuilder.length < 4096) {
                    oscBuilder.append(b.toChar())
                }
            }
        }
    }

    private fun processOscEscByte(b: Int) {
        if (b == '\\'.code) { // ST (String Terminator)
            handleOscSequence()
        }
        escapeState = ESC_NONE
    }

    private fun handleOscSequence() {
        val osc = oscBuilder.toString()
        val semicolonIndex = osc.indexOf(';')
        if (semicolonIndex < 0) return

        val command = osc.substring(0, semicolonIndex).toIntOrNull() ?: return
        val value = osc.substring(semicolonIndex + 1)

        when (command) {
            0, 2 -> client.onTitleChanged(value)  // Set window title
            52 -> { // Clipboard
                // OSC 52 clipboard operation
                if (value.startsWith("c;")) {
                    val data = value.substring(2)
                    try {
                        val decoded = String(android.util.Base64.decode(data, android.util.Base64.DEFAULT))
                        client.onClipboardText(decoded)
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to decode OSC 52 data", e)
                    }
                }
            }
        }
    }

    private fun processCharsetSelect(b: Int) {
        when (b.toChar()) {
            '0' -> { // Line drawing
                if (charsetSelectIndex == 0) useLineDrawingG0 = true
                else useLineDrawingG1 = true
            }
            'B' -> { // ASCII
                if (charsetSelectIndex == 0) useLineDrawingG0 = false
                else useLineDrawingG1 = false
            }
        }
        escapeState = ESC_NONE
    }

    private fun processDcsByte(b: Int) {
        // DCS sequences: skip until ST
        if (b == 27) escapeState = ESC_OSC_ESC // Reuse OSC ESC handler for ST detection
    }

    private fun mapLineDrawingChar(c: Char): Char {
        return when (c) {
            'j' -> '\u2518' // ┘
            'k' -> '\u2510' // ┐
            'l' -> '\u250C' // ┌
            'm' -> '\u2514' // └
            'n' -> '\u253C' // ┼
            'q' -> '\u2500' // ─
            't' -> '\u251C' // ├
            'u' -> '\u2524' // ┤
            'v' -> '\u2534' // ┴
            'w' -> '\u252C' // ┬
            'x' -> '\u2502' // │
            else -> c
        }
    }

    private fun nextTabStop(fromCol: Int): Int {
        for (i in fromCol + 1 until columns) {
            if (tabStops[i]) return i
        }
        return columns - 1
    }

    private fun setCursorPosition(row: Int, col: Int) {
        val effectiveRow = if ((decSetFlags and DECSET_ORIGIN_MODE) != 0) {
            row + topMargin
        } else row
        cursorRow = clampRow(effectiveRow)
        cursorCol = clampCol(col)
        pendingWrap = false
    }

    private fun clampRow(row: Int): Int = row.coerceIn(0, rows - 1)
    private fun clampCol(col: Int): Int = col.coerceIn(0, columns - 1)

    fun saveCursor() {
        savedCursorRow = cursorRow
        savedCursorCol = cursorCol
        savedStyle = currentStyle
    }

    fun restoreCursor() {
        cursorRow = clampRow(savedCursorRow)
        cursorCol = clampCol(savedCursorCol)
        currentStyle = savedStyle
        foregroundColor = TextStyle.decodeForeground(currentStyle)
        backgroundColor = TextStyle.decodeBackground(currentStyle)
        currentEffects = TextStyle.decodeEffects(currentStyle)
        pendingWrap = false
    }

    /** Soft terminal reset (DECSTR). */
    fun softReset() {
        insertMode = false
        decSetFlags = DECSET_AUTO_WRAP or DECSET_CURSOR_VISIBLE
        foregroundColor = TerminalColors.COLOR_INDEX_FOREGROUND
        backgroundColor = TerminalColors.COLOR_INDEX_BACKGROUND
        currentEffects = 0
        updateStyle()
        topMargin = 0
        bottomMargin = rows - 1
        useLineDrawingG0 = false
        useLineDrawingG1 = false
        pendingWrap = false
    }

    /** Full terminal reset. */
    fun reset() {
        softReset()
        cursorRow = 0
        cursorCol = 0
        screen = mainBuffer
        mainBuffer.clearScreen()
        mainBuffer.clearScrollback()
        altBuffer.clearScreen()
        tabStops.fill(false)
        for (i in tabStops.indices) {
            if (i > 0 && i % 8 == 0) tabStops[i] = true
        }
        colors.reset()
        client.onColorsChanged()
    }

    /** Resize the terminal. */
    fun resize(newColumns: Int, newRows: Int) {
        if (newColumns == columns && newRows == rows) return

        mainBuffer.resize(newColumns, newRows, currentStyle)
        altBuffer.resize(newColumns, newRows, currentStyle)

        val oldColumns = columns
        columns = newColumns
        rows = newRows
        topMargin = 0
        bottomMargin = rows - 1
        cursorRow = clampRow(cursorRow)
        cursorCol = clampCol(cursorCol)
        pendingWrap = false

        // Reset tab stops for new column count
        val newTabStops = BooleanArray(newColumns) { it > 0 && it % 8 == 0 }
        System.arraycopy(
            tabStops, 0, newTabStops, 0,
            minOf(tabStops.size, newTabStops.size)
        )
    }
}
