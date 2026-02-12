package com.roshank8s.androidterminal.ui

import android.content.Context
import android.util.AttributeSet
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.widget.Button
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import com.roshank8s.androidterminal.R

/**
 * Custom keyboard row with extra keys commonly needed in terminal (Ctrl, Alt, Tab, arrows, etc.).
 *
 * This provides quick access to keys that are hard to type on a soft keyboard:
 * - Modifier keys: Ctrl, Alt, Shift
 * - Navigation: Arrows, Home, End, PgUp, PgDn
 * - Special: ESC, Tab, |, /, -, ~, :
 * - Function keys: F1-F12
 *
 * Supports toggling modifier state (sticky modifiers).
 */
class TerminalExtraKeys @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : HorizontalScrollView(context, attrs, defStyleAttr) {

    interface ExtraKeysListener {
        fun onExtraKeyPress(key: ExtraKey)
        fun onModifierToggle(modifier: Modifier, active: Boolean)
    }

    enum class Modifier {
        CTRL, ALT, SHIFT, FN
    }

    data class ExtraKey(
        val label: String,
        val sequence: ByteArray? = null,
        val keyCode: Int = 0,
        val modifier: Modifier? = null,
        val isToggle: Boolean = false
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is ExtraKey) return false
            return label == other.label
        }

        override fun hashCode(): Int = label.hashCode()
    }

    var listener: ExtraKeysListener? = null

    // Modifier states
    private var ctrlActive = false
    private var altActive = false
    private var shiftActive = false
    private var fnActive = false

    // Key buttons
    private val keyButtons = mutableListOf<Button>()

    // Key layouts
    private val mainKeys = listOf(
        ExtraKey("ESC", "\u001b".toByteArray()),
        ExtraKey("CTRL", modifier = Modifier.CTRL, isToggle = true),
        ExtraKey("ALT", modifier = Modifier.ALT, isToggle = true),
        ExtraKey("TAB", "\t".toByteArray()),
        ExtraKey("|", "|".toByteArray()),
        ExtraKey("/", "/".toByteArray()),
        ExtraKey("-", "-".toByteArray()),
        ExtraKey("~", "~".toByteArray()),
        ExtraKey(":", ":".toByteArray()),
        ExtraKey("_", "_".toByteArray()),
        ExtraKey("UP", "\u001b[A".toByteArray()),
        ExtraKey("DN", "\u001b[B".toByteArray()),
        ExtraKey("LT", "\u001b[D".toByteArray()),
        ExtraKey("RT", "\u001b[C".toByteArray()),
        ExtraKey("HOME", "\u001b[H".toByteArray()),
        ExtraKey("END", "\u001b[F".toByteArray()),
        ExtraKey("PGUP", "\u001b[5~".toByteArray()),
        ExtraKey("PGDN", "\u001b[6~".toByteArray()),
        ExtraKey("FN", modifier = Modifier.FN, isToggle = true)
    )

    private val fnKeys = listOf(
        ExtraKey("F1", "\u001bOP".toByteArray()),
        ExtraKey("F2", "\u001bOQ".toByteArray()),
        ExtraKey("F3", "\u001bOR".toByteArray()),
        ExtraKey("F4", "\u001bOS".toByteArray()),
        ExtraKey("F5", "\u001b[15~".toByteArray()),
        ExtraKey("F6", "\u001b[17~".toByteArray()),
        ExtraKey("F7", "\u001b[18~".toByteArray()),
        ExtraKey("F8", "\u001b[19~".toByteArray()),
        ExtraKey("F9", "\u001b[20~".toByteArray()),
        ExtraKey("F10", "\u001b[21~".toByteArray()),
        ExtraKey("F11", "\u001b[23~".toByteArray()),
        ExtraKey("F12", "\u001b[24~".toByteArray()),
        ExtraKey("INS", "\u001b[2~".toByteArray()),
        ExtraKey("DEL", "\u001b[3~".toByteArray()),
        ExtraKey("{", "{".toByteArray()),
        ExtraKey("}", "}".toByteArray()),
        ExtraKey("[", "[".toByteArray()),
        ExtraKey("]", "]".toByteArray()),
        ExtraKey("\\", "\\".toByteArray())
    )

    init {
        setupKeys()
    }

    private fun setupKeys() {
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LayoutParams(
                LayoutParams.WRAP_CONTENT,
                LayoutParams.MATCH_PARENT
            )
        }

        val activeKeys = if (fnActive) fnKeys else mainKeys

        for (key in activeKeys) {
            val button = Button(context).apply {
                text = key.label
                textSize = 12f
                minWidth = 0
                minimumWidth = 0
                setPadding(16, 8, 16, 8)

                val params = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.MATCH_PARENT
                ).apply {
                    marginStart = 2
                    marginEnd = 2
                }
                layoutParams = params

                // Highlight active modifiers
                if (key.isToggle) {
                    val isActive = when (key.modifier) {
                        Modifier.CTRL -> ctrlActive
                        Modifier.ALT -> altActive
                        Modifier.SHIFT -> shiftActive
                        Modifier.FN -> fnActive
                        else -> false
                    }
                    alpha = if (isActive) 1.0f else 0.7f
                    isSelected = isActive
                }

                setOnClickListener {
                    performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)

                    if (key.isToggle && key.modifier != null) {
                        toggleModifier(key.modifier)
                    } else {
                        listener?.onExtraKeyPress(key)
                    }
                }
            }

            keyButtons.add(button)
            container.addView(button)
        }

        removeAllViews()
        addView(container)
    }

    private fun toggleModifier(modifier: Modifier) {
        when (modifier) {
            Modifier.CTRL -> {
                ctrlActive = !ctrlActive
                listener?.onModifierToggle(modifier, ctrlActive)
            }
            Modifier.ALT -> {
                altActive = !altActive
                listener?.onModifierToggle(modifier, altActive)
            }
            Modifier.SHIFT -> {
                shiftActive = !shiftActive
                listener?.onModifierToggle(modifier, shiftActive)
            }
            Modifier.FN -> {
                fnActive = !fnActive
                setupKeys() // Rebuild with function keys
                listener?.onModifierToggle(modifier, fnActive)
            }
        }
        // Update visual state
        refreshModifierStates()
    }

    private fun refreshModifierStates() {
        for (button in keyButtons) {
            val key = mainKeys.find { it.label == button.text.toString() }
                ?: fnKeys.find { it.label == button.text.toString() }

            if (key?.isToggle == true) {
                val isActive = when (key.modifier) {
                    Modifier.CTRL -> ctrlActive
                    Modifier.ALT -> altActive
                    Modifier.SHIFT -> shiftActive
                    Modifier.FN -> fnActive
                    else -> false
                }
                button.alpha = if (isActive) 1.0f else 0.7f
                button.isSelected = isActive
            }
        }
    }

    fun isCtrlActive(): Boolean = ctrlActive
    fun isAltActive(): Boolean = altActive
    fun isShiftActive(): Boolean = shiftActive

    /** Reset all modifier states (called after a key press). */
    fun resetModifiers() {
        ctrlActive = false
        altActive = false
        shiftActive = false
        refreshModifierStates()
    }
}
