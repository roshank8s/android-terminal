package com.roshank8s.androidterminal.terminal

import android.os.Handler
import android.os.Looper
import android.util.Log
import java.io.File
import java.io.FileDescriptor
import java.io.FileInputStream
import java.io.FileOutputStream
import java.lang.reflect.Field
import java.util.UUID

/**
 * Represents a terminal session — a running subprocess connected via PTY.
 *
 * Each session manages:
 * - The PTY master file descriptor for I/O
 * - The child process (shell or command)
 * - A TerminalEmulator instance for escape sequence processing
 * - A background reader thread for shell output
 *
 * Lifecycle: create -> start -> (running) -> finish
 */
class TerminalSession(
    private val shellPath: String,
    private val cwd: String,
    private val args: Array<String>,
    private val env: Array<String>,
    initialRows: Int,
    initialColumns: Int,
    private val client: SessionClient
) : TerminalEmulator.TerminalClient {

    companion object {
        private const val TAG = "TerminalSession"
        private const val READ_BUFFER_SIZE = 8192
    }

    interface SessionClient {
        fun onSessionChanged(session: TerminalSession)
        fun onSessionTitleChanged(session: TerminalSession)
        fun onSessionFinished(session: TerminalSession)
        fun onBell(session: TerminalSession)
        fun onClipboardText(session: TerminalSession, text: String)
    }

    /** Unique session identifier. */
    val id: String = UUID.randomUUID().toString()

    /** Session display name. */
    var title: String = "Terminal"
        private set

    /** The terminal emulator for this session. */
    val emulator = TerminalEmulator(initialColumns, initialRows, this)

    /** Master PTY file descriptor (-1 if not running). */
    private var masterFd: Int = -1

    /** Child process PID (-1 if not running). */
    var pid: Int = -1
        private set

    /** Whether the session has finished. */
    var isFinished: Boolean = false
        private set

    /** Exit status of the child process. */
    var exitStatus: Int = -1
        private set

    /** Output stream for writing to the terminal (shell stdin). */
    private var outputStream: FileOutputStream? = null

    /** Background thread reading from the PTY. */
    private var readerThread: Thread? = null

    /** Handler for posting to the main thread. */
    private val mainHandler = Handler(Looper.getMainLooper())

    /**
     * Starts the session by creating a subprocess and PTY.
     */
    fun start() {
        val processId = IntArray(1)

        try {
            masterFd = JNI.createSubprocess(
                shellPath, cwd, args, env,
                processId,
                emulator.rows, emulator.columns
            )
            pid = processId[0]
            Log.i(TAG, "Session started: pid=$pid, fd=$masterFd, shell=$shellPath")

            // Create output stream for writing to the PTY
            outputStream = createOutputStream(masterFd)

            // Start reading from the PTY in a background thread
            startReaderThread()

            // Start a thread to wait for process exit
            startWaiterThread()

        } catch (e: Exception) {
            Log.e(TAG, "Failed to start session", e)
            isFinished = true
            mainHandler.post { client.onSessionFinished(this) }
        }
    }

    /**
     * Write data to the terminal (shell stdin).
     */
    override fun write(data: ByteArray) {
        writeToShell(data)
    }

    fun writeToShell(data: ByteArray) {
        if (masterFd < 0 || isFinished) return
        try {
            val written = JNI.write(masterFd, data, data.size)
            if (written < 0) {
                Log.w(TAG, "Write failed")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Write error", e)
        }
    }

    /**
     * Update the terminal size.
     */
    fun updateSize(columns: Int, rows: Int) {
        if (columns <= 0 || rows <= 0) return
        emulator.resize(columns, rows)
        if (masterFd >= 0 && !isFinished) {
            JNI.setPtyWindowSize(masterFd, rows, columns)
        }
    }

    /**
     * Send a signal to the child process.
     */
    fun sendSignal(signal: Int) {
        if (pid > 0 && !isFinished) {
            JNI.sendSignal(pid, signal)
        }
    }

    /**
     * Get the current working directory of the child process.
     */
    fun getCurrentWorkingDirectory(): String {
        return if (pid > 0 && !isFinished) {
            try {
                JNI.getProcessCwd(pid)
            } catch (e: Exception) {
                cwd
            }
        } else cwd
    }

    /**
     * Finish the session by closing the PTY and killing the child.
     */
    fun finish() {
        if (isFinished) return
        isFinished = true

        // Kill the child process
        if (pid > 0) {
            try {
                JNI.sendSignal(pid, 9) // SIGKILL
            } catch (e: Exception) {
                Log.w(TAG, "Failed to kill process $pid", e)
            }
        }

        // Close the PTY
        if (masterFd >= 0) {
            try {
                JNI.close(masterFd)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to close fd $masterFd", e)
            }
            masterFd = -1
        }

        outputStream = null
        Log.i(TAG, "Session finished: pid=$pid, exit=$exitStatus")
        mainHandler.post { client.onSessionFinished(this) }
    }

    // TerminalEmulator.TerminalClient callbacks

    override fun onTextChanged(startRow: Int, endRow: Int) {
        mainHandler.post { client.onSessionChanged(this) }
    }

    override fun onTitleChanged(title: String) {
        this.title = title
        mainHandler.post { client.onSessionTitleChanged(this) }
    }

    override fun onBell() {
        mainHandler.post { client.onBell(this) }
    }

    override fun onClipboardText(text: String) {
        mainHandler.post { client.onClipboardText(this, text) }
    }

    override fun onColorsChanged() {
        mainHandler.post { client.onSessionChanged(this) }
    }

    // Internal methods

    private fun createOutputStream(fd: Int): FileOutputStream {
        val fileDescriptor = FileDescriptor()
        val fdField: Field = FileDescriptor::class.java.getDeclaredField("descriptor")
        fdField.isAccessible = true
        fdField.setInt(fileDescriptor, fd)
        return FileOutputStream(fileDescriptor)
    }

    private fun startReaderThread() {
        readerThread = Thread({
            val buffer = ByteArray(READ_BUFFER_SIZE)
            try {
                while (!isFinished && masterFd >= 0) {
                    val bytesRead = JNI.read(masterFd, buffer, buffer.size)
                    if (bytesRead > 0) {
                        emulator.processBytes(buffer, bytesRead)
                    } else if (bytesRead < 0) {
                        break
                    }
                }
            } catch (e: Exception) {
                if (!isFinished) {
                    Log.e(TAG, "Reader thread error", e)
                }
            }
        }, "TerminalReader-$id").apply {
            isDaemon = true
            start()
        }
    }

    private fun startWaiterThread() {
        Thread({
            try {
                exitStatus = JNI.waitFor(pid)
                Log.i(TAG, "Process $pid exited with status $exitStatus")
            } catch (e: Exception) {
                Log.e(TAG, "Waiter thread error", e)
            } finally {
                if (!isFinished) {
                    isFinished = true
                    mainHandler.post { client.onSessionFinished(this) }
                }
            }
        }, "TerminalWaiter-$id").apply {
            isDaemon = true
            start()
        }
    }
}
