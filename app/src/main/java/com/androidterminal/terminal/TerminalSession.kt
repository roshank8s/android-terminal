package com.androidterminal.terminal

import android.os.Handler
import android.os.Looper
import android.util.Log
import java.io.File
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Manages a single terminal session including PTY, emulator, and I/O threads.
 */
class TerminalSession(
    private val shellCommand: String,
    private val cwd: String,
    private val args: Array<String>,
    private val envVars: Array<String>,
    initialRows: Int,
    initialCols: Int,
    val sessionId: String = UUID.randomUUID().toString()
) {
    companion object {
        private const val TAG = "TerminalSession"
        private const val READ_BUFFER_SIZE = 8192
    }

    /** The terminal emulator */
    val emulator: TerminalEmulator = TerminalEmulator(initialRows, initialCols)

    /** Session state */
    var isRunning: Boolean = false
        private set
    var exitCode: Int = -1
        private set
    var sessionName: String = "Shell"

    /** PTY file descriptors and PID */
    private var masterFd: Int = -1
    private var childPid: Int = -1

    /** I/O thread */
    private var readerThread: Thread? = null
    private val shouldStop = AtomicBoolean(false)

    /** Callbacks */
    var onSessionChanged: ((TerminalSession) -> Unit)? = null
    var onSessionFinished: ((TerminalSession) -> Unit)? = null
    var onTitleChanged: ((TerminalSession, String) -> Unit)? = null

    private val mainHandler = Handler(Looper.getMainLooper())

    init {
        emulator.onTitleChanged = { title ->
            sessionName = title
            mainHandler.post {
                onTitleChanged?.invoke(this, title)
            }
        }
        emulator.onBell = {
            mainHandler.post {
                onSessionChanged?.invoke(this)
            }
        }
    }

    /**
     * Starts the terminal session by creating a PTY and spawning the shell.
     */
    fun start() {
        if (isRunning) return

        try {
            val result = NativePty.createSubprocess(
                shellCommand, cwd, args, envVars,
                emulator.rows, emulator.columns
            )

            masterFd = result[0]
            childPid = result[1]
            isRunning = true

            Log.i(TAG, "Session started: pid=$childPid, fd=$masterFd")

            // Start reader thread
            startReaderThread()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start session", e)
            isRunning = false
        }
    }

    private fun startReaderThread() {
        readerThread = Thread({
            val buffer = ByteArray(READ_BUFFER_SIZE)
            try {
                while (!shouldStop.get()) {
                    val data = NativePty.read(masterFd, READ_BUFFER_SIZE)
                    if (data == null) {
                        // EOF - process exited
                        break
                    }
                    if (data.isNotEmpty()) {
                        synchronized(emulator) {
                            emulator.processBytes(data, data.size)
                        }
                        mainHandler.post {
                            onSessionChanged?.invoke(this)
                        }
                    } else {
                        // No data available, sleep briefly
                        Thread.sleep(10)
                    }
                }
            } catch (e: Exception) {
                if (!shouldStop.get()) {
                    Log.e(TAG, "Reader thread error", e)
                }
            } finally {
                // Wait for child process to exit
                exitCode = NativePty.waitFor(childPid)
                isRunning = false
                mainHandler.post {
                    onSessionFinished?.invoke(this)
                }
                Log.i(TAG, "Session finished: pid=$childPid, exitCode=$exitCode")
            }
        }, "TerminalReader-$sessionId")
        readerThread?.isDaemon = true
        readerThread?.start()
    }

    /**
     * Writes data to the terminal (user input).
     */
    fun write(data: ByteArray) {
        if (!isRunning || masterFd < 0) return
        try {
            NativePty.write(masterFd, data, data.size)
        } catch (e: Exception) {
            Log.e(TAG, "Write failed", e)
        }
    }

    /**
     * Writes a string to the terminal.
     */
    fun write(text: String) {
        write(text.toByteArray(Charsets.UTF_8))
    }

    /**
     * Resizes the terminal.
     */
    fun resize(rows: Int, cols: Int) {
        if (rows <= 0 || cols <= 0) return
        synchronized(emulator) {
            emulator.resize(rows, cols)
        }
        if (masterFd >= 0) {
            NativePty.setWindowSize(masterFd, rows, cols)
        }
    }

    /**
     * Sends a signal to the running process.
     */
    fun sendSignal(signal: Int) {
        if (childPid > 0) {
            NativePty.sendSignal(childPid, signal)
        }
    }

    /**
     * Gets the current working directory of the process.
     */
    fun getCurrentWorkingDirectory(): String? {
        if (childPid <= 0) return null
        return NativePty.getProcessCwd(childPid)
    }

    /**
     * Stops the session and cleans up resources.
     */
    fun stop() {
        shouldStop.set(true)

        if (childPid > 0 && isRunning) {
            // Send SIGHUP then SIGKILL
            NativePty.sendSignal(childPid, 1) // SIGHUP
            Thread {
                Thread.sleep(200)
                if (isRunning) {
                    NativePty.sendSignal(childPid, 9) // SIGKILL
                }
            }.start()
        }

        if (masterFd >= 0) {
            NativePty.close(masterFd)
            masterFd = -1
        }

        readerThread?.interrupt()
        readerThread = null
    }

    /**
     * Resets the terminal emulator.
     */
    fun reset() {
        synchronized(emulator) {
            emulator.reset()
        }
        onSessionChanged?.invoke(this)
    }

    /**
     * Gets text content from the screen for clipboard operations.
     */
    fun getScreenText(): String {
        synchronized(emulator) {
            val sb = StringBuilder()
            for (row in 0 until emulator.rows) {
                val termRow = emulator.buffer.getScreenRow(row)
                sb.append(termRow.toText())
                if (row < emulator.rows - 1) {
                    sb.append('\n')
                }
            }
            return sb.toString()
        }
    }
}
