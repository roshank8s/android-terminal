package com.roshank8s.androidterminal.utils

import android.content.Context
import android.os.Build
import android.os.Environment
import java.io.File

/**
 * Manages the shell environment for terminal sessions.
 *
 * Configures:
 * - PATH with system and app-specific binary directories
 * - HOME directory
 * - TERM, LANG, and other environment variables
 * - Shell detection (bash, sh, etc.)
 * - PRoot environment for distro sessions
 */
class ShellEnvironment(private val context: Context) {

    /** App files directory. */
    val filesDir: File get() = context.filesDir

    /** Home directory for the terminal. */
    val homeDir: File
        get() = File(filesDir, "home").apply { mkdirs() }

    /** Usr prefix directory (like Termux's $PREFIX). */
    val prefixDir: File
        get() = File(filesDir, "usr").apply { mkdirs() }

    /** Bin directory. */
    val binDir: File
        get() = File(prefixDir, "bin").apply { mkdirs() }

    /** Tmp directory. */
    val tmpDir: File
        get() = File(filesDir, "tmp").apply { mkdirs() }

    /**
     * Get the default shell to use.
     * Prefers bash if available, falls back to sh.
     */
    fun getDefaultShell(): String {
        // Check for bash in our prefix
        val localBash = File(binDir, "bash")
        if (localBash.exists() && localBash.canExecute()) return localBash.absolutePath

        // Check system paths
        val systemPaths = listOf("/system/bin/sh", "/system/bin/bash", "/bin/sh", "/bin/bash")
        for (path in systemPaths) {
            val file = File(path)
            if (file.exists() && file.canExecute()) return path
        }

        return "/system/bin/sh"
    }

    /**
     * Get shell arguments for login shell.
     */
    fun getShellArgs(shell: String): Array<String> {
        return if (shell.endsWith("bash")) {
            arrayOf("--login")
        } else {
            arrayOf("-l")
        }
    }

    /**
     * Get the home directory path.
     */
    fun getHomeDirectory(): String = homeDir.absolutePath

    /**
     * Build the environment variables array for a terminal session.
     */
    fun buildEnvironment(): Array<String> {
        val env = mutableListOf<String>()

        // Basic environment
        env.add("TERM=xterm-256color")
        env.add("COLORTERM=truecolor")
        env.add("HOME=${homeDir.absolutePath}")
        env.add("LANG=C.UTF-8")
        env.add("LC_ALL=C.UTF-8")
        env.add("TMPDIR=${tmpDir.absolutePath}")
        env.add("SHELL=${getDefaultShell()}")

        // Build PATH
        val pathParts = mutableListOf<String>()
        pathParts.add(binDir.absolutePath)
        pathParts.add("${prefixDir.absolutePath}/local/bin")
        pathParts.add("/system/bin")
        pathParts.add("/system/xbin")
        pathParts.add("/vendor/bin")

        // Add Android SDK paths if available
        val sdkPath = Environment.getExternalStorageDirectory()
        val androidSdkPaths = listOf(
            "$sdkPath/Android/Sdk/platform-tools",
            "$sdkPath/Android/Sdk/tools"
        )
        androidSdkPaths.forEach { path ->
            if (File(path).exists()) pathParts.add(path)
        }

        env.add("PATH=${pathParts.joinToString(":")}")

        // Prefix directory (like Termux's $PREFIX)
        env.add("PREFIX=${prefixDir.absolutePath}")

        // Android-specific
        env.add("ANDROID_DATA=/data")
        env.add("ANDROID_ROOT=/system")
        env.add("ANDROID_RUNTIME_ROOT=/apex/com.android.runtime")
        env.add("ANDROID_TZDATA_ROOT=/apex/com.android.tzdata")

        // Device info
        env.add("ANDROID_API=${Build.VERSION.SDK_INT}")
        env.add("DEVICE_ABI=${Build.SUPPORTED_ABIS.firstOrNull() ?: "unknown"}")

        // App-specific
        env.add("ANDROID_TERMINAL_APP_DIR=${context.applicationInfo.dataDir}")
        env.add("ANDROID_TERMINAL_VERSION=${getAppVersion()}")

        // XDG directories
        env.add("XDG_CONFIG_HOME=${homeDir.absolutePath}/.config")
        env.add("XDG_DATA_HOME=${homeDir.absolutePath}/.local/share")
        env.add("XDG_CACHE_HOME=${homeDir.absolutePath}/.cache")

        return env.toTypedArray()
    }

    /**
     * Build environment for PRoot distro sessions.
     */
    fun buildPRootEnvironment(): Array<String> {
        val env = buildEnvironment().toMutableList()

        // PRoot-specific
        env.add("PROOT_NO_SECCOMP=1")  // Required for some Android versions
        env.add("PROOT_TMP_DIR=${tmpDir.absolutePath}")

        return env.toTypedArray()
    }

    private fun getAppVersion(): String {
        return try {
            val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            pInfo.versionName ?: "1.0.0"
        } catch (e: Exception) {
            "1.0.0"
        }
    }

    /**
     * Set up initial files in the home directory.
     */
    fun setupInitialEnvironment() {
        // Create .bashrc
        val bashrc = File(homeDir, ".bashrc")
        if (!bashrc.exists()) {
            bashrc.writeText(buildString {
                appendLine("# Android Terminal .bashrc")
                appendLine()
                appendLine("# Prompt")
                appendLine("export PS1='\\[\\033[01;32m\\]\\u@android\\[\\033[00m\\]:\\[\\033[01;34m\\]\\w\\[\\033[00m\\]\\$ '")
                appendLine()
                appendLine("# Aliases")
                appendLine("alias ls='ls --color=auto'")
                appendLine("alias ll='ls -la'")
                appendLine("alias la='ls -A'")
                appendLine("alias l='ls -CF'")
                appendLine("alias grep='grep --color=auto'")
                appendLine("alias fgrep='fgrep --color=auto'")
                appendLine("alias egrep='egrep --color=auto'")
                appendLine("alias cls='clear'")
                appendLine()
                appendLine("# History")
                appendLine("export HISTSIZE=10000")
                appendLine("export HISTFILESIZE=20000")
                appendLine("export HISTCONTROL=ignoredups:erasedups")
                appendLine()
                appendLine("# Welcome")
                appendLine("echo -e '\\033[1;36mAndroid Terminal\\033[0m'")
                appendLine("echo -e 'Type \\033[1;33mhelp\\033[0m for available commands'")
                appendLine("echo ''")
            })
        }

        // Create .profile
        val profile = File(homeDir, ".profile")
        if (!profile.exists()) {
            profile.writeText(buildString {
                appendLine("# ~/.profile")
                appendLine("if [ -f ~/.bashrc ]; then")
                appendLine("    . ~/.bashrc")
                appendLine("fi")
            })
        }

        // Create necessary directories
        listOf(".config", ".local/share", ".cache", ".ssh").forEach {
            File(homeDir, it).mkdirs()
        }

        // Create .ssh directory with correct permissions
        val sshDir = File(homeDir, ".ssh")
        sshDir.setReadable(true, true)
        sshDir.setWritable(true, true)
        sshDir.setExecutable(true, true)
    }
}
