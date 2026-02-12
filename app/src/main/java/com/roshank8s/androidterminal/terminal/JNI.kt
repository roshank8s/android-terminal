package com.roshank8s.androidterminal.terminal

/**
 * JNI bridge to native PTY operations.
 * All terminal I/O goes through these native methods.
 */
object JNI {
    init {
        System.loadLibrary("terminal-native")
    }

    /**
     * Creates a subprocess connected via a pseudoterminal.
     *
     * @param cmd       Command to execute (e.g., "/bin/sh")
     * @param cwd       Working directory
     * @param args      Command arguments
     * @param envVars   Environment variables in "KEY=VALUE" format
     * @param processId Output array receiving the child PID at index 0
     * @param rows      Initial terminal row count
     * @param columns   Initial terminal column count
     * @return Master PTY file descriptor
     */
    @JvmStatic
    external fun createSubprocess(
        cmd: String,
        cwd: String?,
        args: Array<String>?,
        envVars: Array<String>?,
        processId: IntArray,
        rows: Int,
        columns: Int
    ): Int

    /** Sets the PTY window size (triggers SIGWINCH in the child). */
    @JvmStatic
    external fun setPtyWindowSize(fd: Int, rows: Int, cols: Int)

    /** Waits for a child process to exit. Returns exit status. */
    @JvmStatic
    external fun waitFor(pid: Int): Int

    /** Closes a file descriptor. */
    @JvmStatic
    external fun close(fd: Int)

    /** Reads from a file descriptor into buffer. Returns bytes read or -1. */
    @JvmStatic
    external fun read(fd: Int, buffer: ByteArray, length: Int): Int

    /** Writes buffer contents to a file descriptor. Returns bytes written or -1. */
    @JvmStatic
    external fun write(fd: Int, buffer: ByteArray, length: Int): Int

    /** Sends a signal to a process. */
    @JvmStatic
    external fun sendSignal(pid: Int, signal: Int)

    /** Gets the current working directory of a process. */
    @JvmStatic
    external fun getProcessCwd(pid: Int): String
}
