package com.androidterminal.terminal

import android.view.KeyEvent

/**
 * Handles translation of Android key events to terminal escape sequences.
 */
object KeyHandler {

    /**
     * Translates a KeyEvent to the appropriate terminal byte sequence.
     */
    fun getKeySequence(event: KeyEvent, applicationCursorKeys: Boolean): ByteArray? {
        val keyCode = event.keyCode
        val ctrlPressed = event.isCtrlPressed
        val altPressed = event.isAltPressed
        val shiftPressed = event.isShiftPressed

        // Control key combinations
        if (ctrlPressed) {
            val ctrlChar = getCtrlChar(keyCode)
            if (ctrlChar != null) {
                return ctrlChar
            }
        }

        // Special keys
        val specialKey = getSpecialKeySequence(keyCode, applicationCursorKeys, shiftPressed, ctrlPressed, altPressed)
        if (specialKey != null) {
            return specialKey
        }

        // Regular character input
        val unicodeChar = event.unicodeChar
        if (unicodeChar != 0) {
            val char = if (altPressed) {
                // Alt key sends ESC prefix
                byteArrayOf(27, *unicodeChar.toChar().toString().toByteArray(Charsets.UTF_8))
            } else {
                Character.toChars(unicodeChar).let {
                    String(it).toByteArray(Charsets.UTF_8)
                }
            }
            return char
        }

        return null
    }

    private fun getCtrlChar(keyCode: Int): ByteArray? {
        return when (keyCode) {
            KeyEvent.KEYCODE_A -> byteArrayOf(1) // Ctrl+A
            KeyEvent.KEYCODE_B -> byteArrayOf(2)
            KeyEvent.KEYCODE_C -> byteArrayOf(3) // Ctrl+C (SIGINT)
            KeyEvent.KEYCODE_D -> byteArrayOf(4) // Ctrl+D (EOF)
            KeyEvent.KEYCODE_E -> byteArrayOf(5)
            KeyEvent.KEYCODE_F -> byteArrayOf(6)
            KeyEvent.KEYCODE_G -> byteArrayOf(7) // BEL
            KeyEvent.KEYCODE_H -> byteArrayOf(8) // BS
            KeyEvent.KEYCODE_I -> byteArrayOf(9) // TAB
            KeyEvent.KEYCODE_J -> byteArrayOf(10) // LF
            KeyEvent.KEYCODE_K -> byteArrayOf(11) // VT
            KeyEvent.KEYCODE_L -> byteArrayOf(12) // FF
            KeyEvent.KEYCODE_M -> byteArrayOf(13) // CR
            KeyEvent.KEYCODE_N -> byteArrayOf(14)
            KeyEvent.KEYCODE_O -> byteArrayOf(15)
            KeyEvent.KEYCODE_P -> byteArrayOf(16)
            KeyEvent.KEYCODE_Q -> byteArrayOf(17)
            KeyEvent.KEYCODE_R -> byteArrayOf(18)
            KeyEvent.KEYCODE_S -> byteArrayOf(19)
            KeyEvent.KEYCODE_T -> byteArrayOf(20)
            KeyEvent.KEYCODE_U -> byteArrayOf(21)
            KeyEvent.KEYCODE_V -> byteArrayOf(22)
            KeyEvent.KEYCODE_W -> byteArrayOf(23)
            KeyEvent.KEYCODE_X -> byteArrayOf(24)
            KeyEvent.KEYCODE_Y -> byteArrayOf(25)
            KeyEvent.KEYCODE_Z -> byteArrayOf(26) // Ctrl+Z (SIGTSTP)
            KeyEvent.KEYCODE_LEFT_BRACKET -> byteArrayOf(27) // ESC
            KeyEvent.KEYCODE_BACKSLASH -> byteArrayOf(28)
            KeyEvent.KEYCODE_RIGHT_BRACKET -> byteArrayOf(29)
            KeyEvent.KEYCODE_SPACE -> byteArrayOf(0) // NUL
            else -> null
        }
    }

    private fun getSpecialKeySequence(
        keyCode: Int,
        appCursorKeys: Boolean,
        shift: Boolean,
        ctrl: Boolean,
        alt: Boolean
    ): ByteArray? {
        val modifier = getModifier(shift, ctrl, alt)
        val modStr = if (modifier > 1) ";$modifier" else ""

        return when (keyCode) {
            KeyEvent.KEYCODE_DPAD_UP -> {
                if (appCursorKeys && modStr.isEmpty()) "\u001bOA".toByteArray()
                else "\u001b[1${modStr}A".toByteArray()
            }
            KeyEvent.KEYCODE_DPAD_DOWN -> {
                if (appCursorKeys && modStr.isEmpty()) "\u001bOB".toByteArray()
                else "\u001b[1${modStr}B".toByteArray()
            }
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                if (appCursorKeys && modStr.isEmpty()) "\u001bOC".toByteArray()
                else "\u001b[1${modStr}C".toByteArray()
            }
            KeyEvent.KEYCODE_DPAD_LEFT -> {
                if (appCursorKeys && modStr.isEmpty()) "\u001bOD".toByteArray()
                else "\u001b[1${modStr}D".toByteArray()
            }
            KeyEvent.KEYCODE_MOVE_HOME -> "\u001b[1${modStr}H".toByteArray()
            KeyEvent.KEYCODE_MOVE_END -> "\u001b[1${modStr}F".toByteArray()
            KeyEvent.KEYCODE_INSERT -> "\u001b[2${modStr}~".toByteArray()
            KeyEvent.KEYCODE_FORWARD_DEL -> "\u001b[3${modStr}~".toByteArray()
            KeyEvent.KEYCODE_PAGE_UP -> "\u001b[5${modStr}~".toByteArray()
            KeyEvent.KEYCODE_PAGE_DOWN -> "\u001b[6${modStr}~".toByteArray()
            KeyEvent.KEYCODE_DEL -> { // Backspace
                if (ctrl) byteArrayOf(0x1f) // Ctrl+Backspace
                else if (alt) byteArrayOf(0x1b, 0x7f)
                else byteArrayOf(127)
            }
            KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> byteArrayOf(13)
            KeyEvent.KEYCODE_TAB -> {
                if (shift) "\u001b[Z".toByteArray() // Shift+Tab (backtab)
                else byteArrayOf(9)
            }
            KeyEvent.KEYCODE_ESCAPE -> byteArrayOf(27)
            KeyEvent.KEYCODE_F1 -> "\u001bOP".toByteArray()
            KeyEvent.KEYCODE_F2 -> "\u001bOQ".toByteArray()
            KeyEvent.KEYCODE_F3 -> "\u001bOR".toByteArray()
            KeyEvent.KEYCODE_F4 -> "\u001bOS".toByteArray()
            KeyEvent.KEYCODE_F5 -> "\u001b[15${modStr}~".toByteArray()
            KeyEvent.KEYCODE_F6 -> "\u001b[17${modStr}~".toByteArray()
            KeyEvent.KEYCODE_F7 -> "\u001b[18${modStr}~".toByteArray()
            KeyEvent.KEYCODE_F8 -> "\u001b[19${modStr}~".toByteArray()
            KeyEvent.KEYCODE_F9 -> "\u001b[20${modStr}~".toByteArray()
            KeyEvent.KEYCODE_F10 -> "\u001b[21${modStr}~".toByteArray()
            KeyEvent.KEYCODE_F11 -> "\u001b[23${modStr}~".toByteArray()
            KeyEvent.KEYCODE_F12 -> "\u001b[24${modStr}~".toByteArray()
            else -> null
        }
    }

    private fun getModifier(shift: Boolean, ctrl: Boolean, alt: Boolean): Int {
        var mod = 1
        if (shift) mod += 1
        if (alt) mod += 2
        if (ctrl) mod += 4
        return mod
    }

    /**
     * Returns the string to send for special toolbar keys.
     */
    fun getExtraKeySequence(key: String): ByteArray {
        return when (key) {
            "ESC" -> byteArrayOf(27)
            "TAB" -> byteArrayOf(9)
            "CTRL" -> byteArrayOf() // Modifier, handled separately
            "ALT" -> byteArrayOf() // Modifier, handled separately
            "HOME" -> "\u001b[H".toByteArray()
            "END" -> "\u001b[F".toByteArray()
            "PGUP" -> "\u001b[5~".toByteArray()
            "PGDN" -> "\u001b[6~".toByteArray()
            "UP" -> "\u001b[A".toByteArray()
            "DOWN" -> "\u001b[B".toByteArray()
            "LEFT" -> "\u001b[D".toByteArray()
            "RIGHT" -> "\u001b[C".toByteArray()
            "INS" -> "\u001b[2~".toByteArray()
            "DEL" -> "\u001b[3~".toByteArray()
            "F1" -> "\u001bOP".toByteArray()
            "F2" -> "\u001bOQ".toByteArray()
            "F3" -> "\u001bOR".toByteArray()
            "F4" -> "\u001bOS".toByteArray()
            "F5" -> "\u001b[15~".toByteArray()
            "F6" -> "\u001b[17~".toByteArray()
            "F7" -> "\u001b[18~".toByteArray()
            "F8" -> "\u001b[19~".toByteArray()
            "F9" -> "\u001b[20~".toByteArray()
            "F10" -> "\u001b[21~".toByteArray()
            "F11" -> "\u001b[23~".toByteArray()
            "F12" -> "\u001b[24~".toByteArray()
            else -> key.toByteArray(Charsets.UTF_8)
        }
    }
}
