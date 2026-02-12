package com.roshank8s.androidterminal.bootstrap

import android.content.Context
import android.util.Log

/**
 * Manages package operations within the Linux distro environment.
 *
 * Provides a Kotlin interface to apt/dpkg operations that run inside the PRoot environment.
 * This handles both direct commands and the terminal-based package management.
 */
class PackageManager(private val context: Context) {

    companion object {
        private const val TAG = "PackageManager"
    }

    private val distroManager = DistroManager(context)

    /**
     * Get the package manager command for a distro.
     */
    fun getUpdateCommand(distro: DistroManager.Distro): String {
        return when (distro) {
            DistroManager.Distro.ALPINE -> "apk update"
            else -> "apt-get update -y"
        }
    }

    fun getUpgradeCommand(distro: DistroManager.Distro): String {
        return when (distro) {
            DistroManager.Distro.ALPINE -> "apk upgrade"
            else -> "apt-get upgrade -y"
        }
    }

    fun getInstallCommand(distro: DistroManager.Distro, packages: List<String>): String {
        val pkgList = packages.joinToString(" ")
        return when (distro) {
            DistroManager.Distro.ALPINE -> "apk add $pkgList"
            else -> "apt-get install -y $pkgList"
        }
    }

    fun getRemoveCommand(distro: DistroManager.Distro, packages: List<String>): String {
        val pkgList = packages.joinToString(" ")
        return when (distro) {
            DistroManager.Distro.ALPINE -> "apk del $pkgList"
            else -> "apt-get remove -y $pkgList"
        }
    }

    fun getSearchCommand(distro: DistroManager.Distro, query: String): String {
        return when (distro) {
            DistroManager.Distro.ALPINE -> "apk search $query"
            else -> "apt-cache search $query"
        }
    }

    fun getListInstalledCommand(distro: DistroManager.Distro): String {
        return when (distro) {
            DistroManager.Distro.ALPINE -> "apk list --installed"
            else -> "dpkg --list"
        }
    }

    fun getCleanCommand(distro: DistroManager.Distro): String {
        return when (distro) {
            DistroManager.Distro.ALPINE -> "apk cache clean"
            else -> "apt-get clean && apt-get autoclean && apt-get autoremove -y"
        }
    }

    /**
     * Popular package groups that users commonly install.
     */
    data class PackageGroup(
        val name: String,
        val description: String,
        val packages: List<String>
    )

    fun getPopularPackageGroups(distro: DistroManager.Distro): List<PackageGroup> {
        return when (distro) {
            DistroManager.Distro.KALI_LINUX -> listOf(
                PackageGroup("Development", "Build tools and compilers",
                    listOf("build-essential", "gcc", "g++", "make", "cmake")),
                PackageGroup("Python", "Python development environment",
                    listOf("python3", "python3-pip", "python3-dev", "python3-venv")),
                PackageGroup("Networking", "Network analysis and scanning tools",
                    listOf("nmap", "netcat-openbsd", "tcpdump", "traceroute", "whois")),
                PackageGroup("Security Tools", "Penetration testing essentials",
                    listOf("metasploit-framework", "sqlmap", "john", "hydra", "aircrack-ng")),
                PackageGroup("Web Tools", "Web testing and development",
                    listOf("curl", "wget", "nikto", "dirb", "gobuster")),
                PackageGroup("Editors", "Text editors and IDEs",
                    listOf("vim", "nano", "emacs-nox")),
                PackageGroup("Git & VCS", "Version control systems",
                    listOf("git", "subversion")),
                PackageGroup("Node.js", "JavaScript runtime and package manager",
                    listOf("nodejs", "npm"))
            )
            DistroManager.Distro.ALPINE -> listOf(
                PackageGroup("Development", "Build tools",
                    listOf("build-base", "gcc", "g++", "make", "cmake")),
                PackageGroup("Python", "Python environment",
                    listOf("python3", "py3-pip", "python3-dev")),
                PackageGroup("Networking", "Network tools",
                    listOf("nmap", "netcat-openbsd", "tcpdump", "traceroute", "bind-tools")),
                PackageGroup("Editors", "Text editors",
                    listOf("vim", "nano")),
                PackageGroup("Node.js", "JavaScript runtime",
                    listOf("nodejs", "npm"))
            )
            else -> listOf( // Debian/Ubuntu
                PackageGroup("Development", "Build tools and compilers",
                    listOf("build-essential", "gcc", "g++", "make", "cmake", "gdb")),
                PackageGroup("Python", "Python development environment",
                    listOf("python3", "python3-pip", "python3-dev", "python3-venv")),
                PackageGroup("Networking", "Network utilities",
                    listOf("nmap", "netcat-openbsd", "tcpdump", "traceroute", "dnsutils", "whois")),
                PackageGroup("Web Development", "Web tools and frameworks",
                    listOf("curl", "wget", "nginx", "php", "php-cli")),
                PackageGroup("Editors", "Text editors",
                    listOf("vim", "nano", "emacs-nox")),
                PackageGroup("Git & VCS", "Version control systems",
                    listOf("git", "subversion")),
                PackageGroup("Node.js", "JavaScript runtime and package manager",
                    listOf("nodejs", "npm")),
                PackageGroup("Java", "Java development kit",
                    listOf("default-jdk", "maven")),
                PackageGroup("Databases", "Database servers and clients",
                    listOf("sqlite3", "mariadb-client", "postgresql-client")),
                PackageGroup("System Tools", "System administration utilities",
                    listOf("htop", "tmux", "screen", "rsync", "ssh", "sudo"))
            )
        }
    }
}
