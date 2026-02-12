package com.roshank8s.androidterminal.terminal

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface

/**
 * Renders the terminal buffer to an Android Canvas.
 *
 * Handles:
 * - Character rendering with proper monospace font metrics
 * - Color palette rendering (256 colors)
 * - Text effects (bold, italic, underline, strikethrough, inverse)
 * - Cursor rendering (block, underline, bar styles)
 * - Selection highlighting
 */
class TerminalRenderer(private var textSize: Float) {

    /** Paint for drawing text. */
    val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.MONOSPACE
        this.textSize = this@TerminalRenderer.textSize
    }

    /** Character cell width and height. */
    var charWidth: Float = 0f
        private set
    var charHeight: Float = 0f
        private set

    /** Font ascent (distance from baseline to top). */
    var fontAscent: Float = 0f
        private set

    /** Font descent (distance from baseline to bottom). */
    var fontDescent: Float = 0f
        private set

    /** Font leading (extra space between lines). */
    var fontLeading: Float = 0f
        private set

    init {
        updateFontMetrics()
    }

    fun setTextSize(size: Float) {
        textSize = size
        textPaint.textSize = size
        updateFontMetrics()
    }

    private fun updateFontMetrics() {
        val metrics = textPaint.fontMetrics
        fontAscent = -metrics.ascent
        fontDescent = metrics.descent
        fontLeading = metrics.leading
        charHeight = fontAscent + fontDescent + fontLeading
        charWidth = textPaint.measureText("M")
    }

    /**
     * Renders the terminal buffer to the canvas.
     *
     * @param canvas The canvas to draw on
     * @param emulator The terminal emulator to render
     * @param colors The color palette
     * @param topRow The first visible row (for scrollback)
     * @param selectionStart Selection start [row, col] or null
     * @param selectionEnd Selection end [row, col] or null
     * @param showCursor Whether to show the cursor
     * @param cursorStyle Cursor style: 0=block, 1=underline, 2=bar
     */
    fun render(
        canvas: Canvas,
        emulator: TerminalEmulator,
        colors: TerminalColors,
        topRow: Int = 0,
        selectionStart: IntArray? = null,
        selectionEnd: IntArray? = null,
        showCursor: Boolean = true,
        cursorStyle: Int = 0
    ) {
        val screen = emulator.screen

        for (row in 0 until emulator.rows) {
            val absoluteRow = topRow + row
            val y = row * charHeight

            // Get the terminal row data
            val termRow = if (absoluteRow >= 0 && absoluteRow < screen.totalRows) {
                screen.getRow(absoluteRow)
            } else continue

            renderRow(canvas, termRow, row, y, emulator.columns, colors, selectionStart, selectionEnd, absoluteRow)
        }

        // Draw cursor
        if (showCursor && emulator.isCursorVisible && topRow + emulator.rows == screen.totalRows) {
            drawCursor(canvas, emulator, colors, cursorStyle)
        }
    }

    private fun renderRow(
        canvas: Canvas,
        row: TerminalRow,
        screenRow: Int,
        y: Float,
        columns: Int,
        colors: TerminalColors,
        selStart: IntArray?,
        selEnd: IntArray?,
        absoluteRow: Int
    ) {
        var col = 0
        while (col < columns) {
            val style = row.getStyle(col)
            val char = row.getChar(col)

            val fg = resolveColor(TextStyle.decodeForeground(style), colors, true)
            val bg = resolveColor(TextStyle.decodeBackground(style), colors, false)
            val effects = TextStyle.decodeEffects(style)

            val isInverse = (effects and TextStyle.INVERSE) != 0
            val actualFg = if (isInverse) bg else fg
            val actualBg = if (isInverse) fg else bg

            val isSelected = isInSelection(absoluteRow, col, selStart, selEnd)
            val drawFg = if (isSelected) actualBg else actualFg
            val drawBg = if (isSelected) actualFg else actualBg

            val x = col * charWidth

            // Draw background
            if (drawBg != TerminalColors.DEFAULT_BACKGROUND || isSelected) {
                textPaint.color = drawBg
                canvas.drawRect(x, y, x + charWidth, y + charHeight, textPaint)
            }

            // Draw character
            if (char != ' ' && (effects and TextStyle.INVISIBLE) == 0) {
                textPaint.color = drawFg

                // Apply bold
                textPaint.isFakeBoldText = (effects and TextStyle.BOLD) != 0

                // Apply italic
                textPaint.textSkewX = if ((effects and TextStyle.ITALIC) != 0) -0.25f else 0f

                // Apply dim
                if ((effects and TextStyle.DIM) != 0) {
                    textPaint.alpha = 128
                }

                canvas.drawText(char.toString(), x, y + fontAscent, textPaint)

                // Reset paint
                textPaint.alpha = 255
                textPaint.textSkewX = 0f
                textPaint.isFakeBoldText = false
            }

            // Draw underline
            if ((effects and TextStyle.UNDERLINE) != 0) {
                textPaint.color = drawFg
                val underlineY = y + fontAscent + fontDescent * 0.5f
                canvas.drawLine(x, underlineY, x + charWidth, underlineY, textPaint)
            }

            // Draw double underline
            if ((effects and TextStyle.DOUBLE_UNDERLINE) != 0) {
                textPaint.color = drawFg
                val underlineY1 = y + fontAscent + fontDescent * 0.4f
                val underlineY2 = y + fontAscent + fontDescent * 0.6f
                canvas.drawLine(x, underlineY1, x + charWidth, underlineY1, textPaint)
                canvas.drawLine(x, underlineY2, x + charWidth, underlineY2, textPaint)
            }

            // Draw strikethrough
            if ((effects and TextStyle.STRIKETHROUGH) != 0) {
                textPaint.color = drawFg
                val strikeY = y + fontAscent * 0.65f
                canvas.drawLine(x, strikeY, x + charWidth, strikeY, textPaint)
            }

            // Draw overline
            if ((effects and TextStyle.OVERLINE) != 0) {
                textPaint.color = drawFg
                canvas.drawLine(x, y + 1, x + charWidth, y + 1, textPaint)
            }

            col++
        }
    }

    private fun drawCursor(
        canvas: Canvas,
        emulator: TerminalEmulator,
        colors: TerminalColors,
        style: Int
    ) {
        val x = emulator.cursorCol * charWidth
        val y = emulator.cursorRow * charHeight

        textPaint.color = colors.cursorColor

        when (style) {
            0 -> { // Block cursor
                textPaint.alpha = 180
                canvas.drawRect(x, y, x + charWidth, y + charHeight, textPaint)
                textPaint.alpha = 255
            }
            1 -> { // Underline cursor
                val cursorY = y + charHeight - 2
                textPaint.strokeWidth = 2f
                canvas.drawLine(x, cursorY, x + charWidth, cursorY, textPaint)
                textPaint.strokeWidth = 0f
            }
            2 -> { // Bar cursor
                textPaint.strokeWidth = 2f
                canvas.drawLine(x, y, x, y + charHeight, textPaint)
                textPaint.strokeWidth = 0f
            }
        }
    }

    private fun resolveColor(colorIndex: Int, colors: TerminalColors, isForeground: Boolean): Int {
        return when (colorIndex) {
            TerminalColors.COLOR_INDEX_FOREGROUND -> TerminalColors.DEFAULT_FOREGROUND
            TerminalColors.COLOR_INDEX_BACKGROUND -> TerminalColors.DEFAULT_BACKGROUND
            TerminalColors.COLOR_INDEX_CURSOR -> colors.cursorColor
            in 0..255 -> colors.palette[colorIndex]
            else -> if (isForeground) TerminalColors.DEFAULT_FOREGROUND else TerminalColors.DEFAULT_BACKGROUND
        }
    }

    private fun isInSelection(
        row: Int, col: Int,
        selStart: IntArray?, selEnd: IntArray?
    ): Boolean {
        if (selStart == null || selEnd == null) return false

        val startRow = selStart[0]
        val startCol = selStart[1]
        val endRow = selEnd[0]
        val endCol = selEnd[1]

        if (row < startRow || row > endRow) return false
        if (row == startRow && col < startCol) return false
        if (row == endRow && col > endCol) return false
        return true
    }

    /**
     * Calculate the terminal dimensions for a given view size.
     */
    fun calculateTerminalSize(viewWidth: Float, viewHeight: Float): Pair<Int, Int> {
        val columns = maxOf(1, (viewWidth / charWidth).toInt())
        val rows = maxOf(1, (viewHeight / charHeight).toInt())
        return Pair(columns, rows)
    }
}
