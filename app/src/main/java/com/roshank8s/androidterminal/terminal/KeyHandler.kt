package com.roshank8s.androidterminal.terminal

import android.view.KeyEvent

/**
 * Handles keyboard input and converts key events to terminal escape sequences.
 *
 * Supports:
 * - Cursor keys (with application mode)
 * - Function keys F1-F12
 * - Modifier combinations (Ctrl, Alt, Shift)
 * - Special keys (Home, End, Insert, Delete, PageUp, PageDown)
 * - Keypad (with application mode)
 */
object KeyHandler {

    /**
     * Converts a KeyEvent to the appropriate byte sequence for the terminal.
     *
     * @param event The Android KeyEvent
     * @param cursorApp Whether cursor keys use application mode (DECCKM)
     * @param keypadApp Whether keypad uses application mode (DECKPAM)
     * @return The byte sequence to send to the terminal, or null if unhandled
     */
    fun getSequence(event: KeyEvent, cursorApp: Boolean, keypadApp: Boolean): ByteArray? {
        val keyCode = event.keyCode
        val ctrl = event.isCtrlPressed
        val alt = event.isAltPressed
        val shift = event.isShiftPressed

        // Handle Ctrl+key combinations
        if (ctrl && !alt) {
            val ctrlChar = getCtrlChar(keyCode)
            if (ctrlChar != null) {
                return byteArrayOf(ctrlChar)
            }
        }

        val sequence = when (keyCode) {
            // Cursor keys
            KeyEvent.KEYCODE_DPAD_UP -> if (cursorApp) "\u001bOA" else "\u001b[A"
            KeyEvent.KEYCODE_DPAD_DOWN -> if (cursorApp) "\u001bOB" else "\u001b[B"
            KeyEvent.KEYCODE_DPAD_RIGHT -> if (cursorApp) "\u001bOC" else "\u001b[C"
            KeyEvent.KEYCODE_DPAD_LEFT -> if (cursorApp) "\u001bOD" else "\u001b[D"

            // Navigation keys
            KeyEvent.KEYCODE_MOVE_HOME -> if (cursorApp) "\u001bOH" else "\u001b[H"
            KeyEvent.KEYCODE_MOVE_END -> if (cursorApp) "\u001bOF" else "\u001b[F"
            KeyEvent.KEYCODE_INSERT -> "\u001b[2~"
            KeyEvent.KEYCODE_FORWARD_DEL -> "\u001b[3~"
            KeyEvent.KEYCODE_PAGE_UP -> "\u001b[5~"
            KeyEvent.KEYCODE_PAGE_DOWN -> "\u001b[6~"

            // Function keys
            KeyEvent.KEYCODE_F1 -> "\u001bOP"
            KeyEvent.KEYCODE_F2 -> "\u001bOQ"
            KeyEvent.KEYCODE_F3 -> "\u001bOR"
            KeyEvent.KEYCODE_F4 -> "\u001bOS"
            KeyEvent.KEYCODE_F5 -> "\u001b[15~"
            KeyEvent.KEYCODE_F6 -> "\u001b[17~"
            KeyEvent.KEYCODE_F7 -> "\u001b[18~"
            KeyEvent.KEYCODE_F8 -> "\u001b[19~"
            KeyEvent.KEYCODE_F9 -> "\u001b[20~"
            KeyEvent.KEYCODE_F10 -> "\u001b[21~"
            KeyEvent.KEYCODE_F11 -> "\u001b[23~"
            KeyEvent.KEYCODE_F12 -> "\u001b[24~"

            // Tab
            KeyEvent.KEYCODE_TAB -> if (shift) "\u001b[Z" else "\t"

            // Enter
            KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> "\r"

            // Backspace
            KeyEvent.KEYCODE_DEL -> "\u007f"

            // Escape
            KeyEvent.KEYCODE_ESCAPE -> "\u001b"

            else -> null
        }

        if (sequence != null) {
            // Add modifier encoding for cursor/navigation keys
            if ((ctrl || alt || shift) && sequence.length > 2) {
                val modified = addModifiers(sequence, ctrl, alt, shift)
                if (modified != null) return modified.toByteArray()
            }
            return sequence.toByteArray()
        }

        // Handle printable characters with Alt prefix
        if (alt && !ctrl) {
            val unicodeChar = event.unicodeChar
            if (unicodeChar != 0) {
                return byteArrayOf(0x1b, unicodeChar.toByte())
            }
        }

        // Handle regular printable character input
        val unicodeChar = event.unicodeChar
        if (unicodeChar != 0 && !ctrl && !alt) {
            val str = unicodeChar.toChar().toString()
            return str.toByteArray(Charsets.UTF_8)
        }

        return null
    }

    /**
     * Converts Ctrl+key to the appropriate control character.
     */
    private fun getCtrlChar(keyCode: Int): Byte? {
        return when (keyCode) {
            KeyEvent.KEYCODE_A -> 1    // ^A - SOH
            KeyEvent.KEYCODE_B -> 2    // ^B - STX
            KeyEvent.KEYCODE_C -> 3    // ^C - ETX (SIGINT)
            KeyEvent.KEYCODE_D -> 4    // ^D - EOT (EOF)
            KeyEvent.KEYCODE_E -> 5    // ^E - ENQ
            KeyEvent.KEYCODE_F -> 6    // ^F - ACK
            KeyEvent.KEYCODE_G -> 7    // ^G - BEL
            KeyEvent.KEYCODE_H -> 8    // ^H - BS
            KeyEvent.KEYCODE_I -> 9    // ^I - HT (Tab)
            KeyEvent.KEYCODE_J -> 10   // ^J - LF
            KeyEvent.KEYCODE_K -> 11   // ^K - VT
            KeyEvent.KEYCODE_L -> 12   // ^L - FF (Clear)
            KeyEvent.KEYCODE_M -> 13   // ^M - CR
            KeyEvent.KEYCODE_N -> 14   // ^N - SO
            KeyEvent.KEYCODE_O -> 15   // ^O - SI
            KeyEvent.KEYCODE_P -> 16   // ^P - DLE
            KeyEvent.KEYCODE_Q -> 17   // ^Q - DC1 (XON)
            KeyEvent.KEYCODE_R -> 18   // ^R - DC2
            KeyEvent.KEYCODE_S -> 19   // ^S - DC3 (XOFF)
            KeyEvent.KEYCODE_T -> 20   // ^T - DC4
            KeyEvent.KEYCODE_U -> 21   // ^U - NAK (Kill line)
            KeyEvent.KEYCODE_V -> 22   // ^V - SYN
            KeyEvent.KEYCODE_W -> 23   // ^W - ETB (Kill word)
            KeyEvent.KEYCODE_X -> 24   // ^X - CAN
            KeyEvent.KEYCODE_Y -> 25   // ^Y - EM (Yank)
            KeyEvent.KEYCODE_Z -> 26   // ^Z - SUB (SIGTSTP)
            KeyEvent.KEYCODE_LEFT_BRACKET -> 27   // ^[ - ESC
            KeyEvent.KEYCODE_BACKSLASH -> 28      // ^\ - FS (SIGQUIT)
            KeyEvent.KEYCODE_RIGHT_BRACKET -> 29  // ^] - GS
            KeyEvent.KEYCODE_GRAVE -> 0           // ^` - NUL
            KeyEvent.KEYCODE_SPACE -> 0           // ^Space - NUL
            else -> null
        }?.toByte()
    }

    /**
     * Add xterm modifier encoding to escape sequences.
     * Format: CSI 1;{modifier} {final}
     */
    private fun addModifiers(sequence: String, ctrl: Boolean, alt: Boolean, shift: Boolean): String? {
        var modifier = 1
        if (shift) modifier += 1
        if (alt) modifier += 2
        if (ctrl) modifier += 4

        if (modifier == 1) return null

        // CSI sequences: \e[...~ or \e[...X
        if (sequence.startsWith("\u001b[")) {
            val tildeIndex = sequence.indexOf('~')
            return if (tildeIndex >= 0) {
                // \e[N~ -> \e[N;modifier~
                sequence.substring(0, tildeIndex) + ";$modifier~"
            } else {
                // \e[X -> \e[1;modifierX
                "\u001b[1;$modifier${sequence.last()}"
            }
        }

        // SS3 sequences: \eOX -> \e[1;modifierX
        if (sequence.startsWith("\u001bO") && sequence.length == 3) {
            return "\u001b[1;$modifier${sequence[2]}"
        }

        return null
    }
}
