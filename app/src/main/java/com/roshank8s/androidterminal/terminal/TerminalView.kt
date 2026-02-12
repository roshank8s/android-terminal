package com.roshank8s.androidterminal.terminal

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Canvas
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputMethodManager

/**
 * Android View that renders a terminal emulator and handles user input.
 *
 * Features:
 * - Full terminal rendering with scrollback
 * - Touch gestures (scroll, tap to focus, long press for context menu)
 * - Hardware keyboard support with full key mapping
 * - Soft keyboard support via InputConnection
 * - Text selection and clipboard operations
 * - Pinch to zoom (font size)
 * - Cursor blinking
 */
class TerminalView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    interface TerminalViewClient {
        fun onScale(scale: Float): Boolean
        fun onLongPress(event: MotionEvent)
        fun shouldUseCtrlKey(): Boolean
        fun shouldUseAltKey(): Boolean
        fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean
        fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean
        fun onPaste(text: String)
    }

    /** The terminal session displayed by this view. */
    var session: TerminalSession? = null
        set(value) {
            field = value
            scrollPosition = 0
            invalidate()
        }

    /** Client for handling view events. */
    var terminalViewClient: TerminalViewClient? = null

    /** Terminal renderer. */
    private val renderer = TerminalRenderer(DEFAULT_FONT_SIZE)

    /** Current scroll position (0 = at bottom/current screen). */
    private var scrollPosition = 0

    /** Selection state. */
    private var selectionStart: IntArray? = null
    private var selectionEnd: IntArray? = null
    private var isSelecting = false

    /** Gesture detector for scrolling and tapping. */
    private val gestureDetector = GestureDetector(context, TerminalGestureListener())

    /** Scale detector for pinch-to-zoom. */
    private var scaleFactor = 1.0f
    private var lastSpan = 0f
    private var isScaling = false

    /** Cursor blink state. */
    private var cursorVisible = true
    private val blinkHandler = Handler(Looper.getMainLooper())
    private val blinkRunnable = object : Runnable {
        override fun run() {
            cursorVisible = !cursorVisible
            invalidate()
            blinkHandler.postDelayed(this, CURSOR_BLINK_INTERVAL)
        }
    }

    /** Cursor style: 0=block, 1=underline, 2=bar. */
    var cursorStyle: Int = 0

    init {
        isFocusable = true
        isFocusableInTouchMode = true
        keepScreenOn = true

        // Start cursor blinking
        blinkHandler.postDelayed(blinkRunnable, CURSOR_BLINK_INTERVAL)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        updateTerminalSize()
    }

    private fun updateTerminalSize() {
        val session = this.session ?: return
        val (columns, rows) = renderer.calculateTerminalSize(width.toFloat(), height.toFloat())
        if (columns > 0 && rows > 0) {
            session.updateSize(columns, rows)
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val session = this.session ?: return
        val emulator = session.emulator

        // Clear background
        canvas.drawColor(TerminalColors.DEFAULT_BACKGROUND)

        // Calculate top row based on scroll position
        val screen = emulator.screen
        val topRow = screen.activeScrollbackRows - scrollPosition

        renderer.render(
            canvas, emulator, emulator.colors,
            topRow,
            selectionStart, selectionEnd,
            showCursor = cursorVisible && scrollPosition == 0,
            cursorStyle = cursorStyle
        )
    }

    // Input handling

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        // Handle pinch-to-zoom
        if (event.pointerCount == 2) {
            handlePinchZoom(event)
            return true
        }

        if (isScaling) {
            if (event.action == MotionEvent.ACTION_UP) {
                isScaling = false
            }
            return true
        }

        gestureDetector.onTouchEvent(event)

        if (event.action == MotionEvent.ACTION_UP && isSelecting) {
            isSelecting = false
        }

        return true
    }

    private fun handlePinchZoom(event: MotionEvent) {
        when (event.actionMasked) {
            MotionEvent.ACTION_POINTER_DOWN -> {
                isScaling = true
                lastSpan = getSpan(event)
            }
            MotionEvent.ACTION_MOVE -> {
                if (isScaling) {
                    val span = getSpan(event)
                    if (lastSpan > 0) {
                        val scale = span / lastSpan
                        terminalViewClient?.onScale(scale)
                    }
                    lastSpan = span
                }
            }
        }
    }

    private fun getSpan(event: MotionEvent): Float {
        if (event.pointerCount < 2) return 0f
        val dx = event.getX(0) - event.getX(1)
        val dy = event.getY(0) - event.getY(1)
        return Math.sqrt((dx * dx + dy * dy).toDouble()).toFloat()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (terminalViewClient?.onKeyDown(keyCode, event) == true) return true

        val session = this.session ?: return super.onKeyDown(keyCode, event)
        val emulator = session.emulator

        // Handle special key combinations
        if (handleTerminalShortcuts(keyCode, event, session)) return true

        val sequence = KeyHandler.getSequence(
            event,
            emulator.isCursorKeysApplicationMode,
            emulator.isKeypadApplicationMode
        )

        if (sequence != null) {
            // Reset scroll position on key press
            if (scrollPosition != 0) {
                scrollPosition = 0
                invalidate()
            }
            session.writeToShell(sequence)
            return true
        }

        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        if (terminalViewClient?.onKeyUp(keyCode, event) == true) return true
        return super.onKeyUp(keyCode, event)
    }

    private fun handleTerminalShortcuts(keyCode: Int, event: KeyEvent, session: TerminalSession): Boolean {
        val ctrl = event.isCtrlPressed
        val shift = event.isShiftPressed

        if (ctrl && shift) {
            when (keyCode) {
                KeyEvent.KEYCODE_V -> {
                    // Ctrl+Shift+V: Paste
                    pasteFromClipboard()
                    return true
                }
                KeyEvent.KEYCODE_C -> {
                    // Ctrl+Shift+C: Copy
                    copyToClipboard()
                    return true
                }
                KeyEvent.KEYCODE_N -> {
                    // Ctrl+Shift+N: New session (handled by client)
                    return false
                }
                KeyEvent.KEYCODE_EQUALS, KeyEvent.KEYCODE_PLUS -> {
                    // Ctrl+Shift+=: Increase font size
                    changeFontSize(2f)
                    return true
                }
                KeyEvent.KEYCODE_MINUS -> {
                    // Ctrl+Shift+-: Decrease font size
                    changeFontSize(-2f)
                    return true
                }
            }
        }

        return false
    }

    fun pasteFromClipboard() {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = clipboard.primaryClip ?: return
        if (clip.itemCount > 0) {
            val text = clip.getItemAt(0).coerceToText(context).toString()
            paste(text)
        }
    }

    fun paste(text: String) {
        val session = this.session ?: return
        val bytes = if (session.emulator.isBracketedPasteMode) {
            "\u001b[200~$text\u001b[201~".toByteArray()
        } else {
            text.toByteArray()
        }
        session.writeToShell(bytes)
    }

    fun copyToClipboard() {
        val text = getSelectedText()
        if (text.isNotEmpty()) {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("Terminal", text))
        }
        clearSelection()
    }

    private fun getSelectedText(): String {
        val start = selectionStart ?: return ""
        val end = selectionEnd ?: return ""
        val session = this.session ?: return ""
        return session.emulator.screen.getSelectedText(start[0], start[1], end[0], end[1])
    }

    fun clearSelection() {
        selectionStart = null
        selectionEnd = null
        invalidate()
    }

    fun changeFontSize(delta: Float) {
        val newSize = (renderer.textPaint.textSize + delta).coerceIn(MIN_FONT_SIZE, MAX_FONT_SIZE)
        renderer.setTextSize(newSize)
        updateTerminalSize()
        invalidate()
    }

    /** Scroll the terminal view. */
    fun scrollBy(rows: Int) {
        val session = this.session ?: return
        val maxScroll = session.emulator.screen.activeScrollbackRows
        scrollPosition = (scrollPosition + rows).coerceIn(0, maxScroll)
        invalidate()
    }

    fun scrollToBottom() {
        scrollPosition = 0
        invalidate()
    }

    // Soft keyboard support via InputConnection

    override fun onCheckIsTextEditor(): Boolean = true

    override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection {
        outAttrs.inputType = InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS or InputType.TYPE_CLASS_TEXT
        outAttrs.imeOptions = EditorInfo.IME_FLAG_NO_FULLSCREEN

        return object : BaseInputConnection(this, false) {
            override fun commitText(text: CharSequence, newCursorPosition: Int): Boolean {
                val session = this@TerminalView.session ?: return true
                val bytes = text.toString().toByteArray()
                session.writeToShell(bytes)
                return true
            }

            override fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean {
                val session = this@TerminalView.session ?: return true
                if (beforeLength > 0) {
                    val delBytes = ByteArray(beforeLength) { 0x7f.toByte() }
                    session.writeToShell(delBytes)
                }
                return true
            }

            override fun sendKeyEvent(event: KeyEvent): Boolean {
                if (event.action == KeyEvent.ACTION_DOWN) {
                    return this@TerminalView.onKeyDown(event.keyCode, event)
                }
                return super.sendKeyEvent(event)
            }
        }
    }

    fun showSoftKeyboard() {
        requestFocus()
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(this, InputMethodManager.SHOW_IMPLICIT)
    }

    fun hideSoftKeyboard() {
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(windowToken, 0)
    }

    fun toggleSoftKeyboard() {
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.toggleSoftInput(InputMethodManager.SHOW_IMPLICIT, 0)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        blinkHandler.removeCallbacks(blinkRunnable)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        blinkHandler.postDelayed(blinkRunnable, CURSOR_BLINK_INTERVAL)
    }

    /** Gesture listener for scroll and tap. */
    private inner class TerminalGestureListener : GestureDetector.SimpleOnGestureListener() {
        override fun onScroll(
            e1: MotionEvent?,
            e2: MotionEvent,
            distanceX: Float,
            distanceY: Float
        ): Boolean {
            val rowDelta = (distanceY / renderer.charHeight).toInt()
            if (rowDelta != 0) {
                scrollBy(rowDelta)
            }
            return true
        }

        override fun onSingleTapUp(e: MotionEvent): Boolean {
            requestFocus()
            showSoftKeyboard()
            return true
        }

        override fun onLongPress(e: MotionEvent) {
            terminalViewClient?.onLongPress(e)
        }

        override fun onDoubleTap(e: MotionEvent): Boolean {
            // Select word at tap position
            val col = (e.x / renderer.charWidth).toInt()
            val row = (e.y / renderer.charHeight).toInt()
            selectWordAt(row, col)
            return true
        }

        override fun onDown(e: MotionEvent): Boolean = true
    }

    private fun selectWordAt(row: Int, col: Int) {
        val session = this.session ?: return
        val emulator = session.emulator
        val termRow = emulator.screen.getScreenRow(row.coerceIn(0, emulator.rows - 1))

        // Find word boundaries
        var start = col
        var end = col

        while (start > 0 && !termRow.getChar(start - 1).isWhitespace()) start--
        while (end < emulator.columns - 1 && !termRow.getChar(end + 1).isWhitespace()) end++

        val absoluteRow = emulator.screen.activeScrollbackRows - scrollPosition + row
        selectionStart = intArrayOf(absoluteRow, start)
        selectionEnd = intArrayOf(absoluteRow, end)
        invalidate()
    }

    companion object {
        const val DEFAULT_FONT_SIZE = 14f
        const val MIN_FONT_SIZE = 6f
        const val MAX_FONT_SIZE = 42f
        const val CURSOR_BLINK_INTERVAL = 600L
    }
}
