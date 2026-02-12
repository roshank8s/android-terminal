package com.roshank8s.androidterminal.bootstrap

import android.content.Context
import android.os.Build
import android.util.Log
import java.io.File

/**
 * Manages Linux distribution installations.
 *
 * Supports multiple distributions that can be installed and run via PRoot:
 * - Kali Linux (preferred - includes security tools)
 * - Ubuntu (most popular, widest package support)
 * - Debian (most stable, smallest footprint)
 * - Alpine (ultra-lightweight)
 *
 * Each distro is installed as a rootfs in the app's data directory and
 * launched via PRoot which fakes chroot/root using ptrace syscall interception.
 */
class DistroManager(private val context: Context) {

    companion object {
        private const val TAG = "DistroManager"

        // Architecture mapping from Android to Linux
        fun getLinuxArch(): String {
            return when (Build.SUPPORTED_ABIS.firstOrNull() ?: "") {
                "arm64-v8a" -> "aarch64"
                "armeabi-v7a" -> "armhf"
                "x86_64" -> "amd64"
                "x86" -> "i386"
                else -> "aarch64"
            }
        }

        fun getPRootArch(): String {
            return when (Build.SUPPORTED_ABIS.firstOrNull() ?: "") {
                "arm64-v8a" -> "aarch64"
                "armeabi-v7a" -> "arm"
                "x86_64" -> "x86_64"
                "x86" -> "x86"
                else -> "aarch64"
            }
        }
    }

    /** Available distributions. */
    enum class Distro(
        val displayName: String,
        val id: String,
        val description: String
    ) {
        KALI_LINUX(
            "Kali Linux",
            "kali",
            "Security-focused distro with penetration testing tools"
        ),
        UBUNTU(
            "Ubuntu",
            "ubuntu",
            "Popular general-purpose Linux distribution"
        ),
        DEBIAN(
            "Debian",
            "debian",
            "Stable and reliable base distribution"
        ),
        ALPINE(
            "Alpine Linux",
            "alpine",
            "Ultra-lightweight distribution (musl libc)"
        );

        /** Get the rootfs download URL for this distro. */
        fun getRootfsUrl(): String {
            val arch = getLinuxArch()
            return when (this) {
                KALI_LINUX -> {
                    val kaliArch = when (arch) {
                        "aarch64" -> "arm64"
                        else -> arch
                    }
                    "https://kali.download/nethunter-images/current/rootfs/kali-nethunter-rootfs-minimal-$kaliArch.tar.xz"
                }
                UBUNTU -> {
                    val ubuntuArch = when (arch) {
                        "aarch64" -> "aarch64"
                        "armhf" -> "arm"
                        "amd64" -> "x86_64"
                        "i386" -> "i686"
                        else -> "aarch64"
                    }
                    "https://github.com/termux/proot-distro/releases/download/v4.29.0/ubuntu-plucky-$ubuntuArch-pd-v4.29.0.tar.xz"
                }
                DEBIAN -> {
                    val debianArch = when (arch) {
                        "aarch64" -> "aarch64"
                        "armhf" -> "arm"
                        "amd64" -> "x86_64"
                        "i386" -> "i686"
                        else -> "aarch64"
                    }
                    "https://github.com/termux/proot-distro/releases/download/v4.29.0/debian-trixie-$debianArch-pd-v4.29.0.tar.xz"
                }
                ALPINE -> {
                    val alpineArch = when (arch) {
                        "aarch64" -> "aarch64"
                        "armhf" -> "armhf"
                        "amd64" -> "x86_64"
                        "i386" -> "x86"
                        else -> "aarch64"
                    }
                    "https://dl-cdn.alpinelinux.org/alpine/v3.19/releases/$alpineArch/alpine-minirootfs-3.19.1-$alpineArch.tar.gz"
                }
            }
        }

        fun getFallbackUrl(): String {
            val arch = getLinuxArch()
            // Fallback to Debian bookworm from proot-distro v4.7.0
            val debianArch = when (arch) {
                "aarch64" -> "aarch64"
                "armhf" -> "arm"
                "amd64" -> "x86_64"
                "i386" -> "i686"
                else -> "aarch64"
            }
            return "https://github.com/termux/proot-distro/releases/download/v4.7.0/debian-bookworm-$debianArch-pd-v4.7.0.tar.xz"
        }
    }

    /** Base directory for all distro installations. */
    val distrosDir: File
        get() = File(context.filesDir, "distros")

    /** Get the rootfs directory for a specific distro. */
    fun getRootfsDir(distro: Distro): File {
        return File(distrosDir, distro.id)
    }

    /** Check if a distro is installed. */
    fun isInstalled(distro: Distro): Boolean {
        val rootfs = getRootfsDir(distro)
        return rootfs.exists() && File(rootfs, "etc").exists()
    }

    /** Get the list of installed distros. */
    fun getInstalledDistros(): List<Distro> {
        return Distro.entries.filter { isInstalled(it) }
    }

    /** Get the default distro (first installed, or Debian as fallback preference). */
    fun getDefaultDistro(): Distro? {
        // Priority: Kali > Ubuntu > Debian > Alpine
        return Distro.entries.firstOrNull { isInstalled(it) }
    }

    /** Uninstall a distro by removing its rootfs. */
    fun uninstall(distro: Distro): Boolean {
        val rootfs = getRootfsDir(distro)
        return if (rootfs.exists()) {
            rootfs.deleteRecursively()
        } else true
    }

    /** Get the size of an installed distro. */
    fun getInstalledSize(distro: Distro): Long {
        val rootfs = getRootfsDir(distro)
        return if (rootfs.exists()) dirSize(rootfs) else 0
    }

    private fun dirSize(dir: File): Long {
        var size = 0L
        dir.listFiles()?.forEach { file ->
            size += if (file.isDirectory) dirSize(file) else file.length()
        }
        return size
    }

    /**
     * Build the PRoot command to launch a distro.
     *
     * PRoot intercepts syscalls via ptrace to:
     * - Fake chroot into the distro's rootfs
     * - Fake root (uid 0) identity
     * - Bind mount /dev, /proc, /sys from the host
     * - Translate filesystem paths
     */
    fun buildPRootCommand(distro: Distro, command: String? = null): List<String> {
        val rootfs = getRootfsDir(distro)
        val prootBinary = getPRootBinary()
        val homeDir = File(rootfs, "root")

        if (!homeDir.exists()) homeDir.mkdirs()

        // Alpine uses /bin/sh (ash), others use /bin/bash
        val defaultShell = if (distro == Distro.ALPINE) "/bin/sh" else "/bin/bash"
        val actualCommand = command ?: "$defaultShell --login"

        val cmd = mutableListOf<String>()

        cmd.add(prootBinary.absolutePath)

        // Link2Symlink extension for better symlink handling
        cmd.addAll(listOf("--link2symlink"))

        // Kill on exit to prevent orphan processes
        cmd.addAll(listOf("--kill-on-exit"))

        // Fake root identity (appear as uid 0)
        cmd.addAll(listOf("-0"))

        // Set rootfs
        cmd.addAll(listOf("-r", rootfs.absolutePath))

        // Bind mounts
        cmd.addAll(listOf("-b", "/dev"))
        cmd.addAll(listOf("-b", "/proc"))
        cmd.addAll(listOf("-b", "/sys"))

        // Bind the app's home directory
        val appHome = File(context.filesDir, "home")
        if (!appHome.exists()) appHome.mkdirs()
        cmd.addAll(listOf("-b", "${appHome.absolutePath}:/root/storage"))

        // Bind /sdcard if available
        val sdcard = File("/sdcard")
        if (sdcard.exists() && sdcard.canRead()) {
            cmd.addAll(listOf("-b", "/sdcard:/root/sdcard"))
        }

        // Set working directory
        cmd.addAll(listOf("-w", "/root"))

        // Set the command to run
        cmd.addAll(listOf("/usr/bin/env", "-i"))
        cmd.addAll(listOf(
            "HOME=/root",
            "PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin",
            "TERM=xterm-256color",
            "LANG=C.UTF-8",
            "TMPDIR=/tmp",
            "SHELL=$defaultShell",
            "USER=root",
            "LOGNAME=root"
        ))
        cmd.addAll(actualCommand.split(" "))

        return cmd
    }

    /** Get the PRoot binary path. */
    fun getPRootBinary(): File {
        val prootDir = File(context.filesDir, "usr/bin")
        return File(prootDir, "proot")
    }

    /**
     * Build the setup script that configures a fresh distro installation.
     */
    fun getSetupScript(distro: Distro): String {
        val shell = if (distro == Distro.ALPINE) "/bin/sh" else "/bin/bash"
        return buildString {
            appendLine("#!$shell")
            appendLine("set -e")
            appendLine()
            appendLine("# Fix DNS resolution")
            appendLine("echo 'nameserver 8.8.8.8' > /etc/resolv.conf")
            appendLine("echo 'nameserver 8.8.4.4' >> /etc/resolv.conf")
            appendLine()
            appendLine("# Fix locale")
            appendLine("export LANG=C.UTF-8")
            appendLine("export LC_ALL=C.UTF-8")
            appendLine()

            when (distro) {
                Distro.KALI_LINUX -> {
                    appendLine("# Update Kali repositories")
                    appendLine("apt-get update -y")
                    appendLine("apt-get install -y --no-install-recommends \\")
                    appendLine("  bash coreutils apt-utils dialog")
                    appendLine()
                    appendLine("# Install basic tools")
                    appendLine("apt-get install -y --no-install-recommends \\")
                    appendLine("  curl wget git nano vim sudo \\")
                    appendLine("  net-tools iputils-ping dnsutils \\")
                    appendLine("  python3 python3-pip")
                }
                Distro.UBUNTU -> {
                    appendLine("# Update Ubuntu repositories")
                    appendLine("apt-get update -y")
                    appendLine("apt-get install -y --no-install-recommends \\")
                    appendLine("  bash coreutils apt-utils dialog")
                    appendLine()
                    appendLine("# Install basic tools")
                    appendLine("apt-get install -y --no-install-recommends \\")
                    appendLine("  curl wget git nano sudo \\")
                    appendLine("  net-tools iputils-ping dnsutils \\")
                    appendLine("  python3 python3-pip \\")
                    appendLine("  build-essential")
                }
                Distro.DEBIAN -> {
                    appendLine("# Update Debian repositories")
                    appendLine("apt-get update -y")
                    appendLine("apt-get install -y --no-install-recommends \\")
                    appendLine("  bash coreutils apt-utils dialog")
                    appendLine()
                    appendLine("# Install basic tools")
                    appendLine("apt-get install -y --no-install-recommends \\")
                    appendLine("  curl wget git nano sudo \\")
                    appendLine("  net-tools iputils-ping dnsutils \\")
                    appendLine("  python3 python3-pip \\")
                    appendLine("  build-essential")
                }
                Distro.ALPINE -> {
                    appendLine("# Update Alpine repositories")
                    appendLine("apk update")
                    appendLine("apk add bash coreutils")
                    appendLine()
                    appendLine("# Install basic tools")
                    appendLine("apk add curl wget git nano sudo \\")
                    appendLine("  python3 py3-pip build-base")
                }
            }

            appendLine()
            appendLine("# Create tmp directory")
            appendLine("mkdir -p /tmp")
            appendLine("chmod 1777 /tmp")
            appendLine()
            appendLine("echo '=== Setup complete ==='")
        }
    }
}
