/*
 * Android Terminal - JNI Native Layer
 *
 * Provides PTY (pseudoterminal) creation and management for the terminal emulator.
 * This bridges the Java/Kotlin Android layer with Linux kernel PTY operations.
 *
 * Architecture:
 *   Android App (Kotlin) <-> JNI Bridge (this file) <-> Linux Kernel PTY
 *
 * The PTY pair provides bidirectional communication:
 *   User input -> master fd -> kernel -> slave fd -> shell process
 *   Shell output -> slave fd -> kernel -> master fd -> terminal view
 */

#include <jni.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <fcntl.h>
#include <errno.h>
#include <signal.h>
#include <termios.h>
#include <sys/ioctl.h>
#include <sys/wait.h>
#include <sys/types.h>
#include <pty.h>
#include <android/log.h>

#define LOG_TAG "TerminalNative"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

static void throw_runtime_exception(JNIEnv *env, const char *msg) {
    jclass cls = (*env)->FindClass(env, "java/lang/RuntimeException");
    if (cls != NULL) {
        (*env)->ThrowNew(env, cls, msg);
    }
}

/*
 * Creates a subprocess connected via a pseudoterminal.
 *
 * This function:
 * 1. Opens a PTY master/slave pair via forkpty()
 * 2. Forks a child process
 * 3. In the child: sets up environment, changes directory, exec's the command
 * 4. In the parent: returns the master fd for I/O with the child
 *
 * @param cmd       The command to execute (e.g., "/bin/sh")
 * @param cwd       Working directory for the subprocess
 * @param args      Command arguments array
 * @param envVars   Environment variables array ("KEY=VALUE" format)
 * @param processId Output array - receives the child PID at index 0
 * @param rows      Initial terminal row count
 * @param columns   Initial terminal column count
 * @return          The master PTY file descriptor
 */
JNIEXPORT jint JNICALL
Java_com_roshank8s_androidterminal_terminal_JNI_createSubprocess(
    JNIEnv *env, jclass clazz,
    jstring cmd, jstring cwd, jobjectArray args, jobjectArray envVars,
    jintArray processId, jint rows, jint columns) {

    // Convert Java strings to C strings
    const char *cmd_str = (*env)->GetStringUTFChars(env, cmd, NULL);
    const char *cwd_str = cwd ? (*env)->GetStringUTFChars(env, cwd, NULL) : NULL;

    // Build argv array
    int argc = args ? (*env)->GetArrayLength(env, args) : 0;
    char **argv = (char **)malloc((argc + 2) * sizeof(char *));
    argv[0] = strdup(cmd_str);
    for (int i = 0; i < argc; i++) {
        jstring arg = (jstring)(*env)->GetObjectArrayElement(env, args, i);
        const char *arg_str = (*env)->GetStringUTFChars(env, arg, NULL);
        argv[i + 1] = strdup(arg_str);
        (*env)->ReleaseStringUTFChars(env, arg, arg_str);
    }
    argv[argc + 1] = NULL;

    // Build envp array
    int envc = envVars ? (*env)->GetArrayLength(env, envVars) : 0;
    char **envp = (char **)malloc((envc + 1) * sizeof(char *));
    for (int i = 0; i < envc; i++) {
        jstring envVar = (jstring)(*env)->GetObjectArrayElement(env, envVars, i);
        const char *env_str = (*env)->GetStringUTFChars(env, envVar, NULL);
        envp[i] = strdup(env_str);
        (*env)->ReleaseStringUTFChars(env, envVar, env_str);
    }
    envp[envc] = NULL;

    // Set up terminal size
    struct winsize win = {
        .ws_row = (unsigned short)rows,
        .ws_col = (unsigned short)columns,
        .ws_xpixel = 0,
        .ws_ypixel = 0
    };

    // Create PTY and fork
    int master_fd;
    pid_t pid = forkpty(&master_fd, NULL, NULL, &win);

    if (pid < 0) {
        char err_msg[256];
        snprintf(err_msg, sizeof(err_msg), "forkpty() failed: %s", strerror(errno));
        throw_runtime_exception(env, err_msg);

        // Cleanup
        for (int i = 0; argv[i]; i++) free(argv[i]);
        free(argv);
        for (int i = 0; envp[i]; i++) free(envp[i]);
        free(envp);
        (*env)->ReleaseStringUTFChars(env, cmd, cmd_str);
        if (cwd_str) (*env)->ReleaseStringUTFChars(env, cwd, cwd_str);
        return -1;
    }

    if (pid == 0) {
        // ---- CHILD PROCESS ----

        // Set up a clean signal environment
        sigset_t signals_to_unblock;
        sigfillset(&signals_to_unblock);
        sigprocmask(SIG_UNBLOCK, &signals_to_unblock, NULL);

        // Change to working directory
        if (cwd_str && *cwd_str) {
            if (chdir(cwd_str) != 0) {
                LOGE("chdir(\"%s\") failed: %s", cwd_str, strerror(errno));
                // Fall back to home or /
                char *home = getenv("HOME");
                if (!home || chdir(home) != 0) {
                    chdir("/");
                }
            }
        }

        // Set environment variables
        if (envc > 0) {
            // Clear environment and set new vars
            clearenv();
            for (int i = 0; envp[i]; i++) {
                putenv(envp[i]);
            }
        }

        // Execute the command
        execvp(cmd_str, argv);

        // If exec fails, print error and exit
        LOGE("execvp(\"%s\") failed: %s", cmd_str, strerror(errno));
        _exit(1);
    }

    // ---- PARENT PROCESS ----

    // Store the child PID
    jint pid_array[1] = { (jint)pid };
    (*env)->SetIntArrayRegion(env, processId, 0, 1, pid_array);

    // Set master fd to non-blocking for better I/O handling
    int flags = fcntl(master_fd, F_GETFL, 0);
    if (flags != -1) {
        // Keep it blocking - the Java layer handles threading
    }

    // Cleanup
    for (int i = 0; argv[i]; i++) free(argv[i]);
    free(argv);
    // Don't free envp strings - child process may still reference them via putenv
    free(envp);
    (*env)->ReleaseStringUTFChars(env, cmd, cmd_str);
    if (cwd_str) (*env)->ReleaseStringUTFChars(env, cwd, cwd_str);

    LOGI("Created subprocess PID=%d, master_fd=%d", pid, master_fd);
    return master_fd;
}

/*
 * Sets the PTY window size and sends SIGWINCH to the child process group.
 * Called when the terminal view resizes (e.g., keyboard shown/hidden).
 */
JNIEXPORT void JNICALL
Java_com_roshank8s_androidterminal_terminal_JNI_setPtyWindowSize(
    JNIEnv *env, jclass clazz,
    jint fd, jint rows, jint cols) {

    struct winsize win = {
        .ws_row = (unsigned short)rows,
        .ws_col = (unsigned short)cols,
        .ws_xpixel = 0,
        .ws_ypixel = 0
    };

    if (ioctl(fd, TIOCSWINSZ, &win) < 0) {
        LOGW("ioctl(TIOCSWINSZ) failed: %s", strerror(errno));
    }
}

/*
 * Waits for a child process to exit.
 * Returns the exit status or -1 on error.
 */
JNIEXPORT jint JNICALL
Java_com_roshank8s_androidterminal_terminal_JNI_waitFor(
    JNIEnv *env, jclass clazz,
    jint pid) {

    int status;
    while (1) {
        int result = waitpid((pid_t)pid, &status, 0);
        if (result == -1) {
            if (errno == EINTR) {
                continue;  // Retry on interrupt
            }
            LOGE("waitpid(%d) failed: %s", pid, strerror(errno));
            return -1;
        }
        if (WIFEXITED(status)) {
            return WEXITSTATUS(status);
        } else if (WIFSIGNALED(status)) {
            return 128 + WTERMSIG(status);
        }
    }
}

/*
 * Closes a file descriptor (the master PTY fd).
 */
JNIEXPORT void JNICALL
Java_com_roshank8s_androidterminal_terminal_JNI_close(
    JNIEnv *env, jclass clazz,
    jint fd) {

    if (close(fd) < 0) {
        LOGW("close(%d) failed: %s", fd, strerror(errno));
    }
}

/*
 * Reads data from a file descriptor into a byte array.
 * Returns the number of bytes read, or -1 on error/EOF.
 */
JNIEXPORT jint JNICALL
Java_com_roshank8s_androidterminal_terminal_JNI_read(
    JNIEnv *env, jclass clazz,
    jint fd, jbyteArray buffer, jint length) {

    jbyte *buf = (*env)->GetByteArrayElements(env, buffer, NULL);
    if (buf == NULL) return -1;

    ssize_t bytes_read = read(fd, buf, (size_t)length);
    (*env)->ReleaseByteArrayElements(env, buffer, buf, 0);

    if (bytes_read < 0) {
        if (errno == EINTR || errno == EAGAIN) {
            return 0;
        }
        return -1;
    }

    return (jint)bytes_read;
}

/*
 * Writes data from a byte array to a file descriptor.
 * Returns the number of bytes written, or -1 on error.
 */
JNIEXPORT jint JNICALL
Java_com_roshank8s_androidterminal_terminal_JNI_write(
    JNIEnv *env, jclass clazz,
    jint fd, jbyteArray buffer, jint length) {

    jbyte *buf = (*env)->GetByteArrayElements(env, buffer, NULL);
    if (buf == NULL) return -1;

    ssize_t bytes_written = write(fd, buf, (size_t)length);
    (*env)->ReleaseByteArrayElements(env, buffer, buf, JNI_ABORT);

    if (bytes_written < 0) {
        if (errno == EINTR || errno == EAGAIN) {
            return 0;
        }
        return -1;
    }

    return (jint)bytes_written;
}

/*
 * Sends a signal to a process.
 */
JNIEXPORT void JNICALL
Java_com_roshank8s_androidterminal_terminal_JNI_sendSignal(
    JNIEnv *env, jclass clazz,
    jint pid, jint signal) {

    if (kill((pid_t)pid, signal) < 0) {
        LOGW("kill(%d, %d) failed: %s", pid, signal, strerror(errno));
    }
}

/*
 * Gets the current working directory of a process via /proc/pid/cwd.
 */
JNIEXPORT jstring JNICALL
Java_com_roshank8s_androidterminal_terminal_JNI_getProcessCwd(
    JNIEnv *env, jclass clazz,
    jint pid) {

    char proc_path[64];
    char cwd_buf[4096];

    snprintf(proc_path, sizeof(proc_path), "/proc/%d/cwd", pid);
    ssize_t len = readlink(proc_path, cwd_buf, sizeof(cwd_buf) - 1);

    if (len < 0) {
        return (*env)->NewStringUTF(env, "/");
    }

    cwd_buf[len] = '\0';
    return (*env)->NewStringUTF(env, cwd_buf);
}
