package com.androidterminal.utils

import android.content.Context
import android.os.Build
import java.io.File

/**
 * Manages the shell environment including PATH, HOME, and other variables.
 */
object ShellEnvironment {

    /**
     * Gets the prefix directory (equivalent to Termux's PREFIX).
     */
    fun getPrefixDirectory(context: Context): String {
        return File(context.filesDir, "usr").absolutePath
    }

    /**
     * Gets the home directory.
     */
    fun getHomeDirectory(context: Context): String {
        val home = File(context.filesDir, "home")
        if (!home.exists()) home.mkdirs()
        return home.absolutePath
    }

    /**
     * Gets the default shell.
     */
    fun getDefaultShell(context: Context): String {
        // Check for installed shells in order of preference
        val prefix = getPrefixDirectory(context)
        val shells = listOf(
            "$prefix/bin/bash",
            "$prefix/bin/zsh",
            "$prefix/bin/sh",
            "/system/bin/sh"
        )

        for (shell in shells) {
            if (File(shell).exists()) return shell
        }

        return "/system/bin/sh"
    }

    /**
     * Builds environment variables for the shell process.
     */
    fun buildEnvironment(context: Context): Array<String> {
        val prefix = getPrefixDirectory(context)
        val home = getHomeDirectory(context)
        val tmpDir = File(prefix, "tmp")
        if (!tmpDir.exists()) tmpDir.mkdirs()

        val env = mutableListOf<String>()

        // Core paths
        env.add("HOME=$home")
        env.add("PREFIX=$prefix")
        env.add("TMPDIR=${tmpDir.absolutePath}")
        env.add("LANG=en_US.UTF-8")
        env.add("TERM=xterm-256color")
        env.add("COLORTERM=truecolor")

        // PATH - include our prefix binaries and system binaries
        val path = buildString {
            append("$prefix/bin")
            append(":")
            append("$prefix/bin/applets")
            append(":")
            append("/system/bin")
            append(":")
            append("/system/xbin")
        }
        env.add("PATH=$path")

        // Library path
        env.add("LD_LIBRARY_PATH=$prefix/lib")

        // Android specific
        env.add("ANDROID_DATA=/data")
        env.add("ANDROID_ROOT=/system")
        env.add("SHELL=${getDefaultShell(context)}")

        // Display info
        env.add("COLUMNS=80")
        env.add("LINES=24")

        // Useful defaults
        env.add("EDITOR=vi")
        env.add("PAGER=less")

        // App-specific
        env.add("ANDROID_TERMINAL_APP_VERSION=1.0.0")
        env.add("ANDROID_TERMINAL_APP_PACKAGE=${context.packageName}")
        env.add("ANDROID_SDK=${Build.VERSION.SDK_INT}")

        return env.toTypedArray()
    }

    /**
     * Sets up the initial file structure on first run.
     */
    fun setupFileStructure(context: Context) {
        val prefix = getPrefixDirectory(context)
        val home = getHomeDirectory(context)

        // Create directory structure
        val dirs = listOf(
            "$prefix/bin",
            "$prefix/lib",
            "$prefix/etc",
            "$prefix/share",
            "$prefix/var",
            "$prefix/tmp",
            "$prefix/var/log",
            "$home/.config",
            "$home/.local/share",
            "$home/.cache"
        )

        for (dir in dirs) {
            File(dir).mkdirs()
        }

        // Create default shell profile
        val profile = File(home, ".profile")
        if (!profile.exists()) {
            profile.writeText(buildString {
                appendLine("# Android Terminal - Shell Profile")
                appendLine("# This file is sourced by login shells")
                appendLine()
                appendLine("export PS1='\\[\\033[1;32m\\]\\u@terminal\\[\\033[0m\\]:\\[\\033[1;34m\\]\\w\\[\\033[0m\\]\\$ '")
                appendLine()
                appendLine("alias ls='ls --color=auto'")
                appendLine("alias ll='ls -la'")
                appendLine("alias la='ls -A'")
                appendLine("alias l='ls -CF'")
                appendLine("alias grep='grep --color=auto'")
                appendLine("alias ..='cd ..'")
                appendLine("alias ...='cd ../..'")
                appendLine()
                appendLine("# Source .bashrc if it exists")
                appendLine("if [ -f ~/.bashrc ]; then")
                appendLine("    . ~/.bashrc")
                appendLine("fi")
            })
        }

        // Create motd
        val motd = File(prefix, "etc/motd")
        if (!motd.exists()) {
            motd.parentFile?.mkdirs()
            motd.writeText(buildString {
                appendLine("Welcome to Android Terminal!")
                appendLine()
                appendLine("Type 'help' for available commands.")
                appendLine("Use the toolbar above the keyboard for special keys.")
                appendLine()
            })
        }

        // Create a simple help script
        val helpScript = File(prefix, "bin/help")
        if (!helpScript.exists()) {
            helpScript.writeText(buildString {
                appendLine("#!/system/bin/sh")
                appendLine("echo \"Android Terminal - Help\"")
                appendLine("echo \"\"")
                appendLine("echo \"Basic Commands:\"")
                appendLine("echo \"  ls          - List directory contents\"")
                appendLine("echo \"  cd          - Change directory\"")
                appendLine("echo \"  pwd         - Print working directory\"")
                appendLine("echo \"  cat         - Display file contents\"")
                appendLine("echo \"  echo        - Print text\"")
                appendLine("echo \"  mkdir       - Create directories\"")
                appendLine("echo \"  rm          - Remove files\"")
                appendLine("echo \"  cp          - Copy files\"")
                appendLine("echo \"  mv          - Move files\"")
                appendLine("echo \"  chmod       - Change permissions\"")
                appendLine("echo \"  clear       - Clear screen\"")
                appendLine("echo \"\"")
                appendLine("echo \"Keyboard Shortcuts:\"")
                appendLine("echo \"  Ctrl+C      - Interrupt current process\"")
                appendLine("echo \"  Ctrl+D      - Send EOF / Exit\"")
                appendLine("echo \"  Ctrl+Z      - Suspend process\"")
                appendLine("echo \"  Ctrl+L      - Clear screen\"")
                appendLine("echo \"\"")
                appendLine("echo \"Use the extra keys bar for ESC, TAB, CTRL, arrows, etc.\"")
            })
            helpScript.setExecutable(true)
        }
    }
}
