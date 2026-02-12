package com.androidterminal.terminal

/**
 * JNI bridge to native PTY management code.
 * Handles pseudoterminal creation, I/O, and process lifecycle.
 */
object NativePty {

    init {
        System.loadLibrary("androidterminal")
    }

    /**
     * Creates a new subprocess with a PTY.
     * @return IntArray of [masterFd, childPid]
     */
    @JvmStatic
    external fun createSubprocess(
        cmd: String,
        cwd: String,
        args: Array<String>?,
        envVars: Array<String>?,
        rows: Int,
        cols: Int
    ): IntArray

    /** Write bytes to the PTY master fd */
    @JvmStatic
    external fun write(fd: Int, data: ByteArray, len: Int): Int

    /** Read bytes from the PTY master fd */
    @JvmStatic
    external fun read(fd: Int, maxLen: Int): ByteArray?

    /** Resize the terminal window */
    @JvmStatic
    external fun setWindowSize(fd: Int, rows: Int, cols: Int)

    /** Send a signal to the subprocess */
    @JvmStatic
    external fun sendSignal(pid: Int, signal: Int)

    /** Wait for subprocess to exit, returns exit code */
    @JvmStatic
    external fun waitFor(pid: Int): Int

    /** Non-blocking check if process is running. Returns -1 if running, exit code otherwise */
    @JvmStatic
    external fun checkProcessStatus(pid: Int): Int

    /** Close a file descriptor */
    @JvmStatic
    external fun close(fd: Int)

    /** Get working directory of a process */
    @JvmStatic
    external fun getProcessCwd(pid: Int): String?
}
