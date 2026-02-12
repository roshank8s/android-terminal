package com.androidterminal.view

import android.content.Context
import android.util.AttributeSet
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.widget.Button
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import com.androidterminal.R
import com.androidterminal.terminal.KeyHandler

/**
 * Extra keys toolbar that provides quick access to special keys
 * commonly needed in terminal operations (ESC, TAB, CTRL, arrows, etc.)
 */
class ExtraKeysView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : HorizontalScrollView(context, attrs, defStyleAttr) {

    /** Callback when a key is pressed */
    var onKeyPress: ((ByteArray) -> Unit)? = null

    /** Whether CTRL modifier is active */
    private var ctrlActive: Boolean = false

    /** Whether ALT modifier is active */
    private var altActive: Boolean = false

    private var ctrlButton: Button? = null
    private var altButton: Button? = null

    /** Key layout configuration */
    private val keyRows = listOf(
        listOf("ESC", "TAB", "CTRL", "ALT", "/", "-", "|", "HOME", "UP", "END", "PGUP"),
        listOf("F1", "F2", "F3", "F4", "F5", "F6", "LEFT", "DOWN", "RIGHT", "PGDN")
    )

    init {
        isHorizontalScrollBarEnabled = false
        setupKeys()
    }

    private fun setupKeys() {
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
        }

        for (row in keyRows) {
            val rowLayout = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }

            for (key in row) {
                val button = createKeyButton(key)
                rowLayout.addView(button)

                if (key == "CTRL") ctrlButton = button
                if (key == "ALT") altButton = button
            }

            container.addView(rowLayout)
        }

        addView(container)
    }

    private fun createKeyButton(key: String): Button {
        return Button(context).apply {
            text = key
            textSize = 12f
            minWidth = 0
            minimumWidth = 0
            setPadding(
                (12 * resources.displayMetrics.density).toInt(),
                (4 * resources.displayMetrics.density).toInt(),
                (12 * resources.displayMetrics.density).toInt(),
                (4 * resources.displayMetrics.density).toInt()
            )
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(
                    (2 * resources.displayMetrics.density).toInt(),
                    (2 * resources.displayMetrics.density).toInt(),
                    (2 * resources.displayMetrics.density).toInt(),
                    (2 * resources.displayMetrics.density).toInt()
                )
            }

            setOnClickListener {
                performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                handleKeyPress(key)
            }
        }
    }

    private fun handleKeyPress(key: String) {
        when (key) {
            "CTRL" -> {
                ctrlActive = !ctrlActive
                ctrlButton?.alpha = if (ctrlActive) 1.0f else 0.6f
                return
            }
            "ALT" -> {
                altActive = !altActive
                altButton?.alpha = if (altActive) 1.0f else 0.6f
                return
            }
        }

        var sequence = KeyHandler.getExtraKeySequence(key)

        // Apply modifiers for regular character keys
        if (key.length == 1) {
            if (ctrlActive) {
                val ch = key[0]
                if (ch in 'a'..'z' || ch in 'A'..'Z') {
                    val ctrl = (ch.uppercaseChar() - 'A' + 1).toByte()
                    sequence = byteArrayOf(ctrl)
                }
            }
            if (altActive) {
                sequence = byteArrayOf(27, *sequence)
            }
        }

        // Reset modifiers after use
        if (ctrlActive) {
            ctrlActive = false
            ctrlButton?.alpha = 0.6f
        }
        if (altActive) {
            altActive = false
            altButton?.alpha = 0.6f
        }

        if (sequence.isNotEmpty()) {
            onKeyPress?.invoke(sequence)
        }
    }

    /**
     * Updates the key labels and layout for a specific configuration.
     */
    fun setKeyLayout(layout: KeyLayout) {
        // Future: support custom key layouts
    }

    enum class KeyLayout {
        DEFAULT,
        VIM,
        EMACS,
        ARROW_ONLY
    }
}
