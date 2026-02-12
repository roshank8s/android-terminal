package com.androidterminal.view

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.os.SystemClock
import android.text.InputType
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputMethodManager
import com.androidterminal.terminal.KeyHandler
import com.androidterminal.terminal.TerminalRow
import com.androidterminal.terminal.TerminalSession
import com.androidterminal.utils.TerminalColors
import kotlin.math.max
import kotlin.math.min

/**
 * Custom View that renders the terminal content and handles user input.
 * Supports touch gestures, keyboard input, pinch-to-zoom, and text selection.
 */
class TerminalView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    companion object {
        private const val TAG = "TerminalView"
        private const val DEFAULT_FONT_SIZE = 14f
        private const val MIN_FONT_SIZE = 8f
        private const val MAX_FONT_SIZE = 36f
        private const val CURSOR_BLINK_INTERVAL = 500L
    }

    // Session
    private var session: TerminalSession? = null

    // Paint objects
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.MONOSPACE
        textSize = DEFAULT_FONT_SIZE * resources.displayMetrics.scaledDensity
    }
    private val backgroundPaint = Paint()
    private val cursorPaint = Paint()

    // Dimensions
    private var charWidth: Float = 0f
    private var charHeight: Float = 0f
    private var charDescent: Float = 0f
    private var fontSize: Float = DEFAULT_FONT_SIZE

    // Cursor blinking
    private var cursorBlinkOn: Boolean = true
    private var lastCursorBlinkTime: Long = 0

    // Gesture detection
    private val gestureDetector: GestureDetector
    private val scaleGestureDetector: ScaleGestureDetector

    // Text selection
    private var isSelecting: Boolean = false
    private var selectionStartRow: Int = -1
    private var selectionStartCol: Int = -1
    private var selectionEndRow: Int = -1
    private var selectionEndCol: Int = -1

    // Callbacks
    var onSessionInput: ((ByteArray) -> Unit)? = null

    init {
        isFocusable = true
        isFocusableInTouchMode = true

        updateFontMetrics()

        gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapUp(e: MotionEvent): Boolean {
                requestFocus()
                showKeyboard()
                return true
            }

            override fun onDoubleTap(e: MotionEvent): Boolean {
                // Select word at tap position
                selectWordAt(
                    (e.x / charWidth).toInt(),
                    (e.y / charHeight).toInt()
                )
                return true
            }

            override fun onLongPress(e: MotionEvent) {
                // Start text selection mode or show context menu
                startSelection(
                    (e.x / charWidth).toInt(),
                    (e.y / charHeight).toInt()
                )
            }

            override fun onScroll(
                e1: MotionEvent?,
                e2: MotionEvent,
                distanceX: Float,
                distanceY: Float
            ): Boolean {
                // TODO: Handle scrollback
                return true
            }
        })

        scaleGestureDetector = ScaleGestureDetector(context,
            object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
                override fun onScale(detector: ScaleGestureDetector): Boolean {
                    val newSize = fontSize * detector.scaleFactor
                    setFontSize(newSize)
                    return true
                }
            })
    }

    /**
     * Attaches a terminal session to this view.
     */
    fun attachSession(terminalSession: TerminalSession) {
        session = terminalSession
        updateTerminalSize()
        invalidate()
    }

    /**
     * Detaches the current session.
     */
    fun detachSession() {
        session = null
        invalidate()
    }

    fun setFontSize(size: Float) {
        fontSize = size.coerceIn(MIN_FONT_SIZE, MAX_FONT_SIZE)
        textPaint.textSize = fontSize * resources.displayMetrics.scaledDensity
        updateFontMetrics()
        updateTerminalSize()
        invalidate()
    }

    fun getFontSize(): Float = fontSize

    private fun updateFontMetrics() {
        val metrics = textPaint.fontMetrics
        charHeight = metrics.descent - metrics.ascent
        charDescent = metrics.descent
        charWidth = textPaint.measureText("W")
    }

    private fun updateTerminalSize() {
        if (width <= 0 || height <= 0) return
        val cols = max(1, (width / charWidth).toInt())
        val rows = max(1, (height / charHeight).toInt())
        session?.resize(rows, cols)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        updateTerminalSize()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val session = this.session ?: return
        val emulator = session.emulator

        synchronized(emulator) {
            // Draw background
            canvas.drawColor(TerminalColors.defaultBackground)

            // Draw each row
            for (row in 0 until emulator.rows) {
                val termRow = emulator.buffer.getScreenRow(row)
                val y = (row + 1) * charHeight - charDescent

                for (col in 0 until emulator.columns) {
                    val style = termRow.getStyle(col)
                    val x = col * charWidth

                    // Get colors
                    var fgColorIndex = TerminalRow.unpackFg(style)
                    var bgColorIndex = TerminalRow.unpackBg(style)

                    // Handle inverse
                    if (TerminalRow.hasAttr(style, TerminalRow.ATTR_INVERSE)) {
                        val temp = fgColorIndex
                        fgColorIndex = bgColorIndex
                        bgColorIndex = temp
                    }

                    // Handle selection highlight
                    val isSelected = isInSelection(row, col)
                    if (isSelected) {
                        val temp = fgColorIndex
                        fgColorIndex = bgColorIndex
                        bgColorIndex = temp
                    }

                    val fgColor = if (fgColorIndex == 7 && !TerminalRow.hasAttr(style, TerminalRow.ATTR_INVERSE) && !isSelected) {
                        TerminalColors.defaultForeground
                    } else {
                        TerminalColors.getColor(fgColorIndex)
                    }

                    val bgColor = if (bgColorIndex == 0 && !TerminalRow.hasAttr(style, TerminalRow.ATTR_INVERSE) && !isSelected) {
                        TerminalColors.defaultBackground
                    } else {
                        TerminalColors.getColor(bgColorIndex)
                    }

                    // Draw cell background if not default
                    if (bgColor != TerminalColors.defaultBackground) {
                        backgroundPaint.color = bgColor
                        canvas.drawRect(x, row * charHeight, x + charWidth, (row + 1) * charHeight, backgroundPaint)
                    }

                    // Draw character
                    val cp = termRow.getChar(col)
                    if (cp != 0 && cp != ' '.code) {
                        textPaint.color = fgColor

                        // Handle bold
                        textPaint.isFakeBoldText = TerminalRow.hasAttr(style, TerminalRow.ATTR_BOLD)

                        // Handle italic
                        textPaint.textSkewX = if (TerminalRow.hasAttr(style, TerminalRow.ATTR_ITALIC)) -0.25f else 0f

                        // Handle underline
                        textPaint.isUnderlineText = TerminalRow.hasAttr(style, TerminalRow.ATTR_UNDERLINE)

                        // Handle strikethrough
                        textPaint.isStrikeThruText = TerminalRow.hasAttr(style, TerminalRow.ATTR_STRIKETHROUGH)

                        // Handle dim
                        if (TerminalRow.hasAttr(style, TerminalRow.ATTR_DIM)) {
                            textPaint.alpha = 128
                        } else {
                            textPaint.alpha = 255
                        }

                        canvas.drawText(String(Character.toChars(cp)), x, y, textPaint)

                        // Reset paint
                        textPaint.isFakeBoldText = false
                        textPaint.textSkewX = 0f
                        textPaint.isUnderlineText = false
                        textPaint.isStrikeThruText = false
                        textPaint.alpha = 255
                    }
                }
            }

            // Draw cursor
            if (emulator.cursorVisible) {
                val now = SystemClock.uptimeMillis()
                if (now - lastCursorBlinkTime > CURSOR_BLINK_INTERVAL) {
                    cursorBlinkOn = !cursorBlinkOn
                    lastCursorBlinkTime = now
                }

                if (cursorBlinkOn || !hasFocus()) {
                    val cursorX = emulator.cursorCol * charWidth
                    val cursorY = emulator.cursorRow * charHeight
                    cursorPaint.color = TerminalColors.cursorColor

                    if (hasFocus()) {
                        // Filled block cursor
                        cursorPaint.alpha = 180
                        canvas.drawRect(
                            cursorX, cursorY,
                            cursorX + charWidth, cursorY + charHeight,
                            cursorPaint
                        )
                    } else {
                        // Outline cursor when not focused
                        cursorPaint.style = Paint.Style.STROKE
                        cursorPaint.strokeWidth = 2f
                        canvas.drawRect(
                            cursorX, cursorY,
                            cursorX + charWidth, cursorY + charHeight,
                            cursorPaint
                        )
                        cursorPaint.style = Paint.Style.FILL
                    }
                }
            }
        }

        // Schedule next frame for cursor blink
        postInvalidateDelayed(CURSOR_BLINK_INTERVAL)
    }

    // Input handling
    override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection {
        outAttrs.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        outAttrs.imeOptions = EditorInfo.IME_FLAG_NO_FULLSCREEN

        return object : BaseInputConnection(this, true) {
            override fun commitText(text: CharSequence, newCursorPosition: Int): Boolean {
                val bytes = text.toString().toByteArray(Charsets.UTF_8)
                sendInput(bytes)
                return true
            }

            override fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean {
                if (beforeLength > 0) {
                    sendInput(byteArrayOf(127)) // DEL
                }
                return true
            }

            override fun sendKeyEvent(event: KeyEvent): Boolean {
                if (event.action == KeyEvent.ACTION_DOWN) {
                    handleKeyDown(event)
                }
                return true
            }
        }
    }

    override fun onCheckIsTextEditor(): Boolean = true

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        return handleKeyDown(event) || super.onKeyDown(keyCode, event)
    }

    private fun handleKeyDown(event: KeyEvent): Boolean {
        val session = this.session ?: return false
        val appCursorKeys = session.emulator.isApplicationCursorKeys()
        val sequence = KeyHandler.getKeySequence(event, appCursorKeys) ?: return false
        if (sequence.isNotEmpty()) {
            sendInput(sequence)
        }
        return true
    }

    private fun sendInput(data: ByteArray) {
        session?.write(data)
        onSessionInput?.invoke(data)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleGestureDetector.onTouchEvent(event)
        gestureDetector.onTouchEvent(event)
        return true
    }

    fun showKeyboard() {
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(this, InputMethodManager.SHOW_IMPLICIT)
    }

    fun hideKeyboard() {
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(windowToken, 0)
    }

    fun toggleKeyboard() {
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.toggleSoftInput(InputMethodManager.SHOW_IMPLICIT, 0)
    }

    // Text selection
    private fun isInSelection(row: Int, col: Int): Boolean {
        if (!isSelecting) return false
        if (selectionStartRow == -1 || selectionEndRow == -1) return false

        val startRow = min(selectionStartRow, selectionEndRow)
        val endRow = max(selectionStartRow, selectionEndRow)
        val startCol = if (selectionStartRow <= selectionEndRow) selectionStartCol else selectionEndCol
        val endCol = if (selectionStartRow <= selectionEndRow) selectionEndCol else selectionStartCol

        if (row < startRow || row > endRow) return false
        if (row == startRow && col < startCol) return false
        if (row == endRow && col > endCol) return false
        return true
    }

    private fun startSelection(col: Int, row: Int) {
        isSelecting = true
        selectionStartRow = row
        selectionStartCol = col
        selectionEndRow = row
        selectionEndCol = col
        invalidate()
    }

    private fun selectWordAt(col: Int, row: Int) {
        val session = this.session ?: return
        val emulator = session.emulator
        if (row >= emulator.rows) return

        synchronized(emulator) {
            val termRow = emulator.buffer.getScreenRow(row)

            // Find word boundaries
            var start = col
            var end = col
            while (start > 0 && termRow.getChar(start - 1).toChar().isLetterOrDigit()) start--
            while (end < emulator.columns - 1 && termRow.getChar(end + 1).toChar().isLetterOrDigit()) end++

            isSelecting = true
            selectionStartRow = row
            selectionStartCol = start
            selectionEndRow = row
            selectionEndCol = end
            invalidate()
        }
    }

    /**
     * Copies selected text to clipboard.
     */
    fun copySelection(): String? {
        if (!isSelecting) return null
        val session = this.session ?: return null

        val text = synchronized(session.emulator) {
            session.emulator.buffer.getTextContent(
                selectionStartRow, selectionStartCol,
                selectionEndRow, selectionEndCol
            )
        }

        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Terminal", text))

        clearSelection()
        return text
    }

    /**
     * Pastes text from clipboard.
     */
    fun paste() {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = clipboard.primaryClip ?: return
        if (clip.itemCount == 0) return

        val text = clip.getItemAt(0).coerceToText(context).toString()
        val session = this.session ?: return

        if (session.emulator.isBracketedPasteMode()) {
            sendInput("\u001b[200~".toByteArray())
            sendInput(text.toByteArray(Charsets.UTF_8))
            sendInput("\u001b[201~".toByteArray())
        } else {
            sendInput(text.toByteArray(Charsets.UTF_8))
        }
    }

    fun clearSelection() {
        isSelecting = false
        selectionStartRow = -1
        selectionStartCol = -1
        selectionEndRow = -1
        selectionEndCol = -1
        invalidate()
    }

    /**
     * Refreshes the terminal display.
     */
    fun refresh() {
        invalidate()
    }
}
