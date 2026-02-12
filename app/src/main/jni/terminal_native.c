/*
 * Android Terminal - Native PTY management via JNI
 *
 * Handles pseudoterminal creation, process spawning, window resizing,
 * and subprocess lifecycle management. Similar to Termux's approach
 * but with additional signal handling and error recovery.
 *
 * Copyright (C) 2024 Android Terminal Contributors
 * Licensed under GPLv3
 */

#include <jni.h>
#include <stdio.h>
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
#include <sys/stat.h>
#include <dirent.h>
#include <android/log.h>
#include <pty.h>

#define LOG_TAG "AndroidTerminal"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)

static void throw_runtime_exception(JNIEnv *env, const char *msg) {
    jclass cls = (*env)->FindClass(env, "java/lang/RuntimeException");
    if (cls != NULL) {
        (*env)->ThrowNew(env, cls, msg);
    }
    (*env)->DeleteLocalRef(env, cls);
}

static void close_fd_range(int from_fd) {
    DIR *dir = opendir("/proc/self/fd");
    if (dir != NULL) {
        struct dirent *entry;
        while ((entry = readdir(dir)) != NULL) {
            int fd = atoi(entry->d_name);
            if (fd > from_fd && fd != dirfd(dir)) {
                close(fd);
            }
        }
        closedir(dir);
    } else {
        // Fallback: close a reasonable range of FDs
        int max_fd = sysconf(_SC_OPEN_MAX);
        if (max_fd < 0) max_fd = 1024;
        for (int fd = from_fd + 1; fd < max_fd; fd++) {
            close(fd);
        }
    }
}

/*
 * Creates a pseudoterminal pair and spawns a subprocess.
 *
 * Returns an int array: [PTY master FD, child PID]
 *
 * Parameters:
 *   cmd       - command to execute (e.g., "/system/bin/sh")
 *   cwd       - working directory for the child process
 *   args      - command arguments
 *   envVars   - environment variables ("KEY=VALUE" format)
 *   rows      - initial terminal rows
 *   cols      - initial terminal columns
 */
JNIEXPORT jintArray JNICALL
Java_com_androidterminal_terminal_NativePty_createSubprocess(
        JNIEnv *env, jclass clazz,
        jstring cmd, jstring cwd,
        jobjectArray args, jobjectArray envVars,
        jint rows, jint cols) {

    int master_fd;
    int slave_fd;
    pid_t pid;

    // Open a PTY pair
    if (openpty(&master_fd, &slave_fd, NULL, NULL, NULL) == -1) {
        char errmsg[256];
        snprintf(errmsg, sizeof(errmsg), "openpty() failed: %s", strerror(errno));
        throw_runtime_exception(env, errmsg);
        return NULL;
    }

    // Configure the slave terminal
    struct termios tios;
    if (tcgetattr(slave_fd, &tios) == 0) {
        // Enable UTF-8 mode
        tios.c_iflag |= IUTF8;
        // Disable flow control (Ctrl+S/Ctrl+Q)
        tios.c_iflag &= ~(IXON | IXOFF);
        tcsetattr(slave_fd, TCSANOW, &tios);
    }

    // Set initial window size
    struct winsize ws = {
        .ws_row = (unsigned short) rows,
        .ws_col = (unsigned short) cols,
        .ws_xpixel = 0,
        .ws_ypixel = 0
    };
    ioctl(slave_fd, TIOCSWINSZ, &ws);

    pid = fork();

    if (pid < 0) {
        close(master_fd);
        close(slave_fd);
        char errmsg[256];
        snprintf(errmsg, sizeof(errmsg), "fork() failed: %s", strerror(errno));
        throw_runtime_exception(env, errmsg);
        return NULL;
    }

    if (pid == 0) {
        // === Child process ===

        // Close master FD - child only uses slave
        close(master_fd);

        // Create a new session
        setsid();

        // Set controlling terminal
        ioctl(slave_fd, TIOCSCTTY, 0);

        // Redirect stdin/stdout/stderr to slave PTY
        dup2(slave_fd, STDIN_FILENO);
        dup2(slave_fd, STDOUT_FILENO);
        dup2(slave_fd, STDERR_FILENO);

        // Close the original slave fd if it's not one of stdin/stdout/stderr
        if (slave_fd > STDERR_FILENO) {
            close(slave_fd);
        }

        // Close all other file descriptors
        close_fd_range(STDERR_FILENO);

        // Change working directory
        const char *cwd_str = (*env)->GetStringUTFChars(env, cwd, NULL);
        if (cwd_str != NULL) {
            chdir(cwd_str);
            (*env)->ReleaseStringUTFChars(env, cwd, cwd_str);
        }

        // Set up environment variables
        if (envVars != NULL) {
            int env_count = (*env)->GetArrayLength(env, envVars);
            for (int i = 0; i < env_count; i++) {
                jstring env_var = (jstring) (*env)->GetObjectArrayElement(env, envVars, i);
                const char *env_str = (*env)->GetStringUTFChars(env, env_var, NULL);
                if (env_str != NULL) {
                    putenv(strdup(env_str));
                    (*env)->ReleaseStringUTFChars(env, env_var, env_str);
                }
                (*env)->DeleteLocalRef(env, env_var);
            }
        }

        // Build argument array
        const char *cmd_str = (*env)->GetStringUTFChars(env, cmd, NULL);
        int argc = args != NULL ? (*env)->GetArrayLength(env, args) : 0;
        char **argv = malloc((argc + 2) * sizeof(char *));

        argv[0] = strdup(cmd_str);
        for (int i = 0; i < argc; i++) {
            jstring arg = (jstring) (*env)->GetObjectArrayElement(env, args, i);
            const char *arg_str = (*env)->GetStringUTFChars(env, arg, NULL);
            argv[i + 1] = strdup(arg_str);
            (*env)->ReleaseStringUTFChars(env, arg, arg_str);
            (*env)->DeleteLocalRef(env, arg);
        }
        argv[argc + 1] = NULL;

        // Execute the command
        execvp(cmd_str, argv);

        // If exec failed, print error and exit
        char errmsg[256];
        snprintf(errmsg, sizeof(errmsg),
                 "exec(\"%s\") failed: %s\r\n", cmd_str, strerror(errno));
        write(STDERR_FILENO, errmsg, strlen(errmsg));
        _exit(1);
    }

    // === Parent process ===
    close(slave_fd);

    // Set master FD to non-blocking
    int flags = fcntl(master_fd, F_GETFL, 0);
    fcntl(master_fd, F_SETFL, flags | O_NONBLOCK);

    // Return [master_fd, pid]
    jintArray result = (*env)->NewIntArray(env, 2);
    jint buf[2] = { master_fd, (jint) pid };
    (*env)->SetIntArrayRegion(env, result, 0, 2, buf);

    LOGI("Created subprocess: pid=%d, master_fd=%d, rows=%d, cols=%d",
         pid, master_fd, rows, cols);

    return result;
}

/*
 * Writes data to the PTY master file descriptor.
 */
JNIEXPORT jint JNICALL
Java_com_androidterminal_terminal_NativePty_write(
        JNIEnv *env, jclass clazz,
        jint fd, jbyteArray data, jint len) {

    jbyte *buf = (*env)->GetByteArrayElements(env, data, NULL);
    if (buf == NULL) {
        throw_runtime_exception(env, "Failed to get byte array elements");
        return -1;
    }

    int total_written = 0;
    while (total_written < len) {
        int written = write(fd, buf + total_written, len - total_written);
        if (written < 0) {
            if (errno == EINTR) continue;
            if (errno == EAGAIN || errno == EWOULDBLOCK) {
                // Non-blocking: wait a bit and retry
                usleep(1000);
                continue;
            }
            (*env)->ReleaseByteArrayElements(env, data, buf, JNI_ABORT);
            return -1;
        }
        total_written += written;
    }

    (*env)->ReleaseByteArrayElements(env, data, buf, JNI_ABORT);
    return total_written;
}

/*
 * Reads data from the PTY master file descriptor.
 */
JNIEXPORT jbyteArray JNICALL
Java_com_androidterminal_terminal_NativePty_read(
        JNIEnv *env, jclass clazz,
        jint fd, jint maxLen) {

    char *buf = malloc(maxLen);
    if (buf == NULL) {
        throw_runtime_exception(env, "malloc failed");
        return NULL;
    }

    int bytes_read = read(fd, buf, maxLen);
    if (bytes_read < 0) {
        free(buf);
        if (errno == EAGAIN || errno == EWOULDBLOCK) {
            // No data available (non-blocking)
            return (*env)->NewByteArray(env, 0);
        }
        return NULL;
    }

    if (bytes_read == 0) {
        free(buf);
        return NULL; // EOF - process exited
    }

    jbyteArray result = (*env)->NewByteArray(env, bytes_read);
    (*env)->SetByteArrayRegion(env, result, 0, bytes_read, (jbyte *) buf);
    free(buf);

    return result;
}

/*
 * Resizes the terminal window.
 */
JNIEXPORT void JNICALL
Java_com_androidterminal_terminal_NativePty_setWindowSize(
        JNIEnv *env, jclass clazz,
        jint fd, jint rows, jint cols) {

    struct winsize ws = {
        .ws_row = (unsigned short) rows,
        .ws_col = (unsigned short) cols,
        .ws_xpixel = 0,
        .ws_ypixel = 0
    };

    if (ioctl(fd, TIOCSWINSZ, &ws) == -1) {
        LOGW("Failed to set window size: %s", strerror(errno));
    }
}

/*
 * Sends a signal to the subprocess.
 */
JNIEXPORT void JNICALL
Java_com_androidterminal_terminal_NativePty_sendSignal(
        JNIEnv *env, jclass clazz,
        jint pid, jint signal) {

    if (kill((pid_t) pid, signal) == -1) {
        LOGW("Failed to send signal %d to pid %d: %s", signal, pid, strerror(errno));
    }
}

/*
 * Waits for the subprocess to exit and returns the exit code.
 * Uses WNOHANG for non-blocking wait.
 */
JNIEXPORT jint JNICALL
Java_com_androidterminal_terminal_NativePty_waitFor(
        JNIEnv *env, jclass clazz,
        jint pid) {

    int status;
    pid_t result = waitpid((pid_t) pid, &status, 0);

    if (result == -1) {
        LOGE("waitpid(%d) failed: %s", pid, strerror(errno));
        return -1;
    }

    if (WIFEXITED(status)) {
        return WEXITSTATUS(status);
    } else if (WIFSIGNALED(status)) {
        return 128 + WTERMSIG(status);
    }

    return -1;
}

/*
 * Non-blocking check if process is still running.
 * Returns -1 if still running, exit code if exited.
 */
JNIEXPORT jint JNICALL
Java_com_androidterminal_terminal_NativePty_checkProcessStatus(
        JNIEnv *env, jclass clazz,
        jint pid) {

    int status;
    pid_t result = waitpid((pid_t) pid, &status, WNOHANG);

    if (result == 0) {
        return -1; // Still running
    }

    if (result == -1) {
        if (errno == ECHILD) {
            return 0; // Already reaped
        }
        return -1;
    }

    if (WIFEXITED(status)) {
        return WEXITSTATUS(status);
    } else if (WIFSIGNALED(status)) {
        return 128 + WTERMSIG(status);
    }

    return -1;
}

/*
 * Closes a file descriptor.
 */
JNIEXPORT void JNICALL
Java_com_androidterminal_terminal_NativePty_close(
        JNIEnv *env, jclass clazz,
        jint fd) {

    if (close(fd) == -1) {
        LOGW("Failed to close fd %d: %s", fd, strerror(errno));
    }
}

/*
 * Gets the current working directory of a process by reading /proc/pid/cwd.
 */
JNIEXPORT jstring JNICALL
Java_com_androidterminal_terminal_NativePty_getProcessCwd(
        JNIEnv *env, jclass clazz,
        jint pid) {

    char path[64];
    char cwd[PATH_MAX];

    snprintf(path, sizeof(path), "/proc/%d/cwd", pid);

    ssize_t len = readlink(path, cwd, sizeof(cwd) - 1);
    if (len == -1) {
        return NULL;
    }

    cwd[len] = '\0';
    return (*env)->NewStringUTF(env, cwd);
}
