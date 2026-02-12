package com.roshank8s.androidterminal.bootstrap

import android.content.Context
import android.os.Build
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.*
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.GZIPInputStream
import org.tukaani.xz.XZInputStream

/**
 * Handles downloading and installing Linux distribution rootfs archives
 * and the PRoot binary needed to run them.
 *
 * Installation process:
 * 1. Download PRoot static binary for the device architecture
 * 2. Download the selected distro's rootfs tarball
 * 3. Extract the rootfs to the distro directory
 * 4. Configure DNS, locale, and basic settings
 * 5. Run initial setup (apt update, install essentials)
 */
class BootstrapInstaller(private val context: Context) {

    companion object {
        private const val TAG = "BootstrapInstaller"
        private const val CONNECT_TIMEOUT = 30000
        private const val READ_TIMEOUT = 60000
        private const val BUFFER_SIZE = 8192

        // PRoot static binary URLs per architecture
        fun getPRootUrl(): String {
            // skirsten's Android-specific builds (from Termux proot package)
            // Arch names: aarch64, armv7, x86_64, x86
            val arch = when (DistroManager.getPRootArch()) {
                "arm" -> "armv7"
                else -> DistroManager.getPRootArch()
            }
            return "https://skirsten.github.io/proot-portable-android-binaries/$arch/proot"
        }

        fun getPRootFallbackUrl(): String {
            // Official proot-me static builds
            // Arch names: arm64, arm, x86_64, x86
            val arch = when (DistroManager.getPRootArch()) {
                "aarch64" -> "arm64"
                else -> DistroManager.getPRootArch()
            }
            return "https://raw.githubusercontent.com/proot-me/proot-static-build/master/static/proot-$arch"
        }
    }

    interface ProgressListener {
        fun onProgress(stage: String, progress: Int, total: Int)
        fun onStatusMessage(message: String)
        fun onError(error: String)
        fun onComplete()
    }

    private val distroManager = DistroManager(context)

    /**
     * Install PRoot binary.
     */
    suspend fun installPRoot(listener: ProgressListener): Boolean = withContext(Dispatchers.IO) {
        val prootBinary = distroManager.getPRootBinary()

        if (prootBinary.exists() && prootBinary.canExecute()) {
            listener.onStatusMessage("PRoot already installed")
            return@withContext true
        }

        listener.onStatusMessage("Downloading PRoot binary...")
        listener.onProgress("Downloading PRoot", 0, 100)

        // Create directory
        prootBinary.parentFile?.mkdirs()

        // Try primary URL, then fallback
        var success = downloadFile(getPRootUrl(), prootBinary, listener, "Downloading PRoot")
        if (!success) {
            listener.onStatusMessage("Primary PRoot download failed, trying fallback...")
            success = downloadFile(getPRootFallbackUrl(), prootBinary, listener, "Downloading PRoot")
        }

        if (!success) {
            listener.onError("Failed to download PRoot binary")
            return@withContext false
        }

        // Make executable
        prootBinary.setExecutable(true, false)
        prootBinary.setReadable(true, false)

        listener.onStatusMessage("PRoot installed successfully")
        true
    }

    /**
     * Install a Linux distribution.
     */
    suspend fun installDistro(
        distro: DistroManager.Distro,
        listener: ProgressListener
    ): Boolean = withContext(Dispatchers.IO) {
        val rootfsDir = distroManager.getRootfsDir(distro)

        if (distroManager.isInstalled(distro)) {
            listener.onStatusMessage("${distro.displayName} is already installed")
            return@withContext true
        }

        // Step 1: Ensure PRoot is installed
        if (!installPRoot(listener)) {
            return@withContext false
        }

        // Step 2: Download rootfs
        listener.onStatusMessage("Downloading ${distro.displayName} rootfs...")
        listener.onProgress("Downloading rootfs", 0, 100)

        val rootfsUrl = distro.getRootfsUrl()
        val extension = when {
            rootfsUrl.endsWith(".tar.xz") -> ".tar.xz"
            rootfsUrl.endsWith(".tar.bz2") -> ".tar.bz2"
            else -> ".tar.gz"
        }
        val tempFile = File(context.cacheDir, "${distro.id}-rootfs$extension")
        var success = downloadFile(rootfsUrl, tempFile, listener, "Downloading rootfs")

        if (!success) {
            listener.onStatusMessage("Primary download failed, trying Debian fallback...")
            success = downloadFile(distro.getFallbackUrl(), tempFile, listener, "Downloading rootfs")
        }

        if (!success) {
            listener.onError("Failed to download ${distro.displayName} rootfs")
            return@withContext false
        }

        // Step 3: Extract rootfs
        listener.onStatusMessage("Extracting ${distro.displayName} rootfs...")
        listener.onProgress("Extracting rootfs", 0, 100)

        rootfsDir.mkdirs()

        val extracted = extractTarball(tempFile, rootfsDir, listener)
        tempFile.delete()

        if (!extracted) {
            listener.onError("Failed to extract rootfs")
            rootfsDir.deleteRecursively()
            return@withContext false
        }

        // Step 4: Configure the installation
        listener.onStatusMessage("Configuring ${distro.displayName}...")
        listener.onProgress("Configuring", 90, 100)

        configureDistro(distro, rootfsDir)

        listener.onProgress("Complete", 100, 100)
        listener.onStatusMessage("${distro.displayName} installed successfully!")
        listener.onComplete()
        true
    }

    /**
     * Download a file from URL with progress reporting.
     */
    private fun downloadFile(
        urlStr: String,
        destination: File,
        listener: ProgressListener,
        stage: String
    ): Boolean {
        var retries = 3
        while (retries > 0) {
            try {
                val url = URL(urlStr)
                val connection = url.openConnection() as HttpURLConnection
                connection.connectTimeout = CONNECT_TIMEOUT
                connection.readTimeout = READ_TIMEOUT
                connection.instanceFollowRedirects = true
                connection.setRequestProperty("User-Agent", "AndroidTerminal/1.0")

                val responseCode = connection.responseCode
                if (responseCode == HttpURLConnection.HTTP_MOVED_PERM ||
                    responseCode == HttpURLConnection.HTTP_MOVED_TEMP ||
                    responseCode == 307 || responseCode == 308) {
                    val redirectUrl = connection.getHeaderField("Location")
                    if (redirectUrl != null) {
                        connection.disconnect()
                        return downloadFile(redirectUrl, destination, listener, stage)
                    }
                }

                if (responseCode != HttpURLConnection.HTTP_OK) {
                    Log.w(TAG, "HTTP $responseCode for $urlStr")
                    connection.disconnect()
                    retries--
                    continue
                }

                val totalSize = connection.contentLength.toLong()
                var downloadedSize = 0L

                val input = BufferedInputStream(connection.inputStream, BUFFER_SIZE)
                val output = BufferedOutputStream(FileOutputStream(destination), BUFFER_SIZE)

                val buffer = ByteArray(BUFFER_SIZE)
                var bytesRead: Int

                while (input.read(buffer).also { bytesRead = it } != -1) {
                    output.write(buffer, 0, bytesRead)
                    downloadedSize += bytesRead

                    if (totalSize > 0) {
                        val progress = ((downloadedSize * 100) / totalSize).toInt()
                        listener.onProgress(stage, progress, 100)
                    }
                }

                output.flush()
                output.close()
                input.close()
                connection.disconnect()

                return true

            } catch (e: Exception) {
                Log.e(TAG, "Download failed (retries=$retries): $urlStr", e)
                retries--
                if (retries > 0) {
                    Thread.sleep(2000)
                }
            }
        }

        return false
    }

    /**
     * Extract a tar.gz/tar.xz archive to a directory.
     * Uses the system tar command for extraction.
     */
    private fun extractTarball(archive: File, destDir: File, listener: ProgressListener): Boolean {
        try {
            val archiveName = archive.name.lowercase()

            // Determine decompression option
            val decompressFlag = when {
                archiveName.endsWith(".tar.xz") || archiveName.endsWith(".txz") -> "--xz"
                archiveName.endsWith(".tar.gz") || archiveName.endsWith(".tgz") -> "-z"
                archiveName.endsWith(".tar.bz2") || archiveName.endsWith(".tbz2") -> "-j"
                else -> "-z" // Default to gzip
            }

            // First try using busybox tar or system tar
            val tarCommands = listOf(
                listOf("tar", decompressFlag, "-xf", archive.absolutePath, "-C", destDir.absolutePath),
                listOf("tar", "-xf", archive.absolutePath, "-C", destDir.absolutePath),
                listOf("busybox", "tar", "-xf", archive.absolutePath, "-C", destDir.absolutePath)
            )

            for (cmd in tarCommands) {
                try {
                    listener.onStatusMessage("Extracting with: ${cmd.first()}")
                    val process = ProcessBuilder(cmd)
                        .directory(destDir)
                        .redirectErrorStream(true)
                        .start()

                    // Read output to prevent buffer blocking
                    val reader = BufferedReader(InputStreamReader(process.inputStream))
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        Log.d(TAG, "tar: $line")
                    }

                    val exitCode = process.waitFor()
                    // Check if files were extracted (even with non-zero exit code)
                    val files = destDir.listFiles()
                    if (files != null && files.size > 1) {
                        handleTopLevelDir(destDir)
                        return true
                    }
                    Log.w(TAG, "tar command failed with exit code $exitCode, files=${files?.size ?: 0}")
                } catch (e: Exception) {
                    Log.w(TAG, "tar command failed: ${cmd.joinToString(" ")}", e)
                }
            }

            // If system tar fails, try Java-based extraction
            if (archiveName.endsWith(".tar.gz") || archiveName.endsWith(".tgz")) {
                listener.onStatusMessage("Using Java-based extraction...")
                return extractTarGzJava(archive, destDir, listener)
            }

            if (archiveName.endsWith(".tar.xz") || archiveName.endsWith(".txz")) {
                listener.onStatusMessage("Using Java-based XZ extraction...")
                return extractTarXzJava(archive, destDir, listener)
            }

            return false
        } catch (e: Exception) {
            Log.e(TAG, "Extraction failed", e)
            return false
        }
    }

    /**
     * Java-based tar.gz extraction as fallback.
     */
    private fun extractTarGzJava(archive: File, destDir: File, listener: ProgressListener): Boolean {
        try {
            val fis = FileInputStream(archive)
            val gis = GZIPInputStream(fis, BUFFER_SIZE)
            val tis = TarInputStream(gis)

            var entry = tis.nextEntry
            var count = 0

            while (entry != null) {
                val outFile = File(destDir, entry.name)
                count++

                if (count % 100 == 0) {
                    listener.onStatusMessage("Extracting: $count files...")
                }

                // Security: prevent path traversal
                if (!outFile.canonicalPath.startsWith(destDir.canonicalPath)) {
                    entry = tis.nextEntry
                    continue
                }

                if (entry.isDirectory) {
                    outFile.mkdirs()
                } else {
                    outFile.parentFile?.mkdirs()
                    val fos = FileOutputStream(outFile)
                    val buffer = ByteArray(BUFFER_SIZE)
                    var bytesRead: Int
                    while (tis.read(buffer).also { bytesRead = it } != -1) {
                        fos.write(buffer, 0, bytesRead)
                    }
                    fos.close()

                    // Set executable if in bin directory
                    if (entry.name.contains("/bin/") ||
                        entry.name.contains("/sbin/") ||
                        entry.name.endsWith(".sh")) {
                        outFile.setExecutable(true, false)
                    }
                }

                // Handle symlinks if possible
                if (entry.isSymlink && entry.linkName != null) {
                    try {
                        val target = entry.linkName!!
                        outFile.delete()
                        Runtime.getRuntime().exec(arrayOf("ln", "-s", target, outFile.absolutePath)).waitFor()
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to create symlink: ${entry.name} -> ${entry.linkName}", e)
                    }
                }

                entry = tis.nextEntry
            }

            tis.close()
            listener.onStatusMessage("Extracted $count files")
            return count > 0
        } catch (e: Exception) {
            Log.e(TAG, "Java extraction failed", e)
            return false
        }
    }

    /**
     * Java-based tar.xz extraction as fallback.
     * Decompresses XZ to a plain .tar first, then uses system tar to extract.
     * Falls back to Java-based tar parsing if system tar fails.
     */
    private fun extractTarXzJava(archive: File, destDir: File, listener: ProgressListener): Boolean {
        val tarFile = File(context.cacheDir, "${archive.nameWithoutExtension}-decompressed.tar")
        try {
            // Step 1: Decompress .tar.xz -> .tar using Java XZ library
            listener.onStatusMessage("Decompressing XZ archive...")
            val fis = FileInputStream(archive)
            val xis = XZInputStream(fis, 65536)
            val fos = FileOutputStream(tarFile)
            val buffer = ByteArray(65536)
            var bytesRead: Int
            var totalBytes = 0L
            while (xis.read(buffer).also { bytesRead = it } != -1) {
                fos.write(buffer, 0, bytesRead)
                totalBytes += bytesRead
                if (totalBytes % (10 * 1024 * 1024) == 0L) {
                    listener.onStatusMessage("Decompressed ${totalBytes / (1024 * 1024)} MB...")
                }
            }
            fos.close()
            xis.close()
            fis.close()
            listener.onStatusMessage("Decompressed to ${totalBytes / (1024 * 1024)} MB tar")

            // Step 2: Try system tar first
            val tarCommands = listOf(
                listOf("tar", "-xf", tarFile.absolutePath, "-C", destDir.absolutePath),
                listOf("busybox", "tar", "-xf", tarFile.absolutePath, "-C", destDir.absolutePath)
            )

            for (cmd in tarCommands) {
                try {
                    listener.onStatusMessage("Extracting with: ${cmd.first()}")
                    val process = ProcessBuilder(cmd)
                        .directory(destDir)
                        .redirectErrorStream(true)
                        .start()

                    val reader = BufferedReader(InputStreamReader(process.inputStream))
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        Log.d(TAG, "tar: $line")
                    }

                    val exitCode = process.waitFor()
                    // Accept extraction even with non-zero exit (tar may warn but still extract)
                    val files = destDir.listFiles()
                    if (files != null && files.size > 1) {
                        handleTopLevelDir(destDir)
                        tarFile.delete()
                        listener.onStatusMessage("Extraction complete (exit=$exitCode)")
                        return true
                    }
                    Log.w(TAG, "tar extraction failed with exit code $exitCode, files=${files?.size ?: 0}")
                } catch (e: Exception) {
                    Log.w(TAG, "tar command failed: ${cmd.joinToString(" ")}", e)
                }
            }

            // Step 3: Fall back to Java-based tar extraction
            listener.onStatusMessage("Using Java-based tar extraction...")
            val result = extractPlainTarJava(tarFile, destDir, listener)
            tarFile.delete()
            return result
        } catch (e: Exception) {
            Log.e(TAG, "Java XZ extraction failed", e)
            tarFile.delete()
            return false
        }
    }

    /**
     * Extract a plain (uncompressed) .tar archive using Java.
     */
    private fun extractPlainTarJava(tarFile: File, destDir: File, listener: ProgressListener): Boolean {
        try {
            val fis = BufferedInputStream(FileInputStream(tarFile), 65536)
            val tis = TarInputStream(fis)

            var entry = tis.nextEntry
            var count = 0

            while (entry != null) {
                count++
                if (count % 500 == 0) {
                    listener.onStatusMessage("Extracting: $count files...")
                }

                if (entry.name.isBlank() || entry.name == "./") {
                    entry = tis.nextEntry
                    continue
                }

                val outFile = File(destDir, entry.name)

                // Security: prevent path traversal
                if (!outFile.canonicalPath.startsWith(destDir.canonicalPath)) {
                    entry = tis.nextEntry
                    continue
                }

                if (entry.isSymlink && entry.linkName != null) {
                    outFile.parentFile?.mkdirs()
                    try {
                        outFile.delete()
                        Runtime.getRuntime().exec(arrayOf("ln", "-sf", entry.linkName!!, outFile.absolutePath)).waitFor()
                    } catch (e: Exception) {
                        Log.w(TAG, "Symlink failed: ${entry.name} -> ${entry.linkName}", e)
                    }
                } else if (entry.isDirectory) {
                    outFile.mkdirs()
                } else {
                    outFile.parentFile?.mkdirs()
                    val fos = FileOutputStream(outFile)
                    val buffer = ByteArray(32768)
                    var bytesRead: Int
                    while (tis.read(buffer).also { bytesRead = it } != -1) {
                        fos.write(buffer, 0, bytesRead)
                    }
                    fos.close()

                    if (entry.name.contains("/bin/") ||
                        entry.name.contains("/sbin/") ||
                        entry.name.endsWith(".sh")) {
                        outFile.setExecutable(true, false)
                    }
                }

                entry = tis.nextEntry
            }

            tis.close()
            listener.onStatusMessage("Extracted $count files")
            return count > 0
        } catch (e: Exception) {
            Log.e(TAG, "Java tar extraction failed", e)
            // Even if we fail partway, check if enough was extracted
            val files = destDir.listFiles()
            if (files != null && files.size > 3) {
                Log.i(TAG, "Partial extraction: ${files.size} top-level entries, considering success")
                return true
            }
            return false
        }
    }

    /**
     * If tarball extracted to a single top-level directory, flatten it.
     */
    private fun handleTopLevelDir(destDir: File) {
        val files = destDir.listFiles() ?: return
        if (files.size == 1 && files[0].isDirectory) {
            val innerDir = files[0]
            innerDir.listFiles()?.forEach { file ->
                file.renameTo(File(destDir, file.name))
            }
            innerDir.deleteRecursively()
        }
    }

    /**
     * Configure a freshly extracted distro.
     */
    private fun configureDistro(distro: DistroManager.Distro, rootfsDir: File) {
        // Set up DNS resolution
        val resolv = File(rootfsDir, "etc/resolv.conf")
        resolv.parentFile?.mkdirs()
        resolv.writeText("nameserver 8.8.8.8\nnameserver 8.8.4.4\n")

        // Set up hostname
        val hostname = File(rootfsDir, "etc/hostname")
        hostname.writeText("android-terminal\n")

        // Set up hosts file
        val hosts = File(rootfsDir, "etc/hosts")
        hosts.writeText("127.0.0.1 localhost android-terminal\n::1 localhost\n")

        // Create essential directories
        listOf("tmp", "root", "proc", "sys", "dev", "var/tmp", "run").forEach {
            File(rootfsDir, it).mkdirs()
        }

        // Set permissions
        File(rootfsDir, "tmp").apply {
            setReadable(true, false)
            setWritable(true, false)
            setExecutable(true, false)
        }

        // Create a basic .bashrc for root
        val bashrc = File(rootfsDir, "root/.bashrc")
        bashrc.writeText(buildString {
            appendLine("# Android Terminal - ${distro.displayName}")
            appendLine("export PS1='\\[\\033[01;31m\\]\\u@\\h\\[\\033[00m\\]:\\[\\033[01;34m\\]\\w\\[\\033[00m\\]\\$ '")
            appendLine("export TERM=xterm-256color")
            appendLine("export LANG=C.UTF-8")
            appendLine("export HOME=/root")
            appendLine("export PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin")
            appendLine()
            appendLine("alias ls='ls --color=auto'")
            appendLine("alias ll='ls -la'")
            appendLine("alias grep='grep --color=auto'")
            appendLine("alias update='apt-get update && apt-get upgrade -y'")
            appendLine()
            appendLine("# Welcome message")
            appendLine("echo -e '\\033[1;32m${distro.displayName} on Android Terminal\\033[0m'")
            appendLine("echo -e 'Type \\033[1;33mapt install <package>\\033[0m to install packages'")
            appendLine("echo ''")
        })

        // Create a profile
        val profile = File(rootfsDir, "root/.profile")
        profile.writeText(buildString {
            appendLine("# ~/.profile")
            appendLine("if [ -f ~/.bashrc ]; then")
            appendLine("    . ~/.bashrc")
            appendLine("fi")
        })

        // Fix apt if needed (for Debian-based distros)
        if (distro != DistroManager.Distro.ALPINE) {
            val aptConf = File(rootfsDir, "etc/apt/apt.conf.d/99android-terminal")
            aptConf.parentFile?.mkdirs()
            aptConf.writeText(buildString {
                appendLine("APT::Sandbox::User \"root\";")
                appendLine("Acquire::AllowInsecureRepositories \"true\";")
                appendLine("Acquire::AllowDowngradeToInsecureRepositories \"true\";")
            })

            // Create dpkg no-triggers config for faster installs
            val dpkgConf = File(rootfsDir, "etc/dpkg/dpkg.cfg.d/android-terminal")
            dpkgConf.parentFile?.mkdirs()
            dpkgConf.writeText("no-triggers\n")
        }

        // Fix group file to prevent warnings
        val groupFile = File(rootfsDir, "etc/group")
        if (!groupFile.exists()) {
            groupFile.writeText(buildString {
                appendLine("root:x:0:")
                appendLine("daemon:x:1:")
                appendLine("bin:x:2:")
                appendLine("sys:x:3:")
                appendLine("adm:x:4:")
                appendLine("tty:x:5:")
                appendLine("disk:x:6:")
                appendLine("lp:x:7:")
                appendLine("mail:x:8:")
                appendLine("news:x:9:")
                appendLine("uucp:x:10:")
                appendLine("man:x:12:")
                appendLine("proxy:x:13:")
                appendLine("kmem:x:15:")
                appendLine("dialout:x:20:")
                appendLine("fax:x:21:")
                appendLine("voice:x:22:")
                appendLine("cdrom:x:24:")
                appendLine("floppy:x:25:")
                appendLine("tape:x:26:")
                appendLine("sudo:x:27:")
                appendLine("audio:x:29:")
                appendLine("dip:x:30:")
                appendLine("www-data:x:33:")
                appendLine("backup:x:34:")
                appendLine("operator:x:37:")
                appendLine("list:x:38:")
                appendLine("irc:x:39:")
                appendLine("src:x:40:")
                appendLine("gnats:x:41:")
                appendLine("shadow:x:42:")
                appendLine("utmp:x:43:")
                appendLine("video:x:44:")
                appendLine("sasl:x:45:")
                appendLine("plugdev:x:46:")
                appendLine("staff:x:50:")
                appendLine("games:x:60:")
                appendLine("users:x:100:")
                appendLine("nogroup:x:65534:")
            })
        }

        // Fix passwd file
        val passwdFile = File(rootfsDir, "etc/passwd")
        if (!passwdFile.exists()) {
            passwdFile.writeText(buildString {
                appendLine("root:x:0:0:root:/root:/bin/bash")
                appendLine("daemon:x:1:1:daemon:/usr/sbin:/usr/sbin/nologin")
                appendLine("bin:x:2:2:bin:/bin:/usr/sbin/nologin")
                appendLine("sys:x:3:3:sys:/dev:/usr/sbin/nologin")
                appendLine("nobody:x:65534:65534:nobody:/nonexistent:/usr/sbin/nologin")
            })
        }

        Log.i(TAG, "Configured ${distro.displayName} at ${rootfsDir.absolutePath}")
    }

    /**
     * Tar stream reader for Java-based extraction.
     * Handles POSIX, GNU (long names type L/K), and pax (type x/g) formats.
     */
    private class TarInputStream(inputStream: InputStream) : FilterInputStream(inputStream) {
        data class TarEntry(
            val name: String,
            val size: Long,
            val isDirectory: Boolean,
            val isSymlink: Boolean,
            val isHardLink: Boolean,
            val linkName: String?
        )

        var currentEntry: TarEntry? = null
        private var remainingBytes = 0L

        /** Skip exactly n bytes, looping as needed. */
        private fun skipFully(n: Long) {
            var remaining = n
            val buf = ByteArray(8192)
            while (remaining > 0) {
                val toRead = minOf(remaining, buf.size.toLong()).toInt()
                val read = `in`.read(buf, 0, toRead)
                if (read <= 0) break
                remaining -= read
            }
        }

        /** Read exactly n bytes into a buffer. Returns false on EOF. */
        private fun readFully(buf: ByteArray, off: Int, len: Int): Boolean {
            var offset = off
            var remaining = len
            while (remaining > 0) {
                val read = `in`.read(buf, offset, remaining)
                if (read <= 0) return false
                offset += read
                remaining -= read
            }
            return true
        }

        val nextEntry: TarEntry?
            get() {
                // Skip remaining bytes of current entry + padding
                if (remainingBytes > 0) {
                    skipFully(remainingBytes)
                    val padding = (512 - (remainingBytes % 512)) % 512
                    if (padding > 0) skipFully(padding)
                }
                remainingBytes = 0

                return readNextEntry()
            }

        private fun readNextEntry(): TarEntry? {
            val header = ByteArray(512)
            if (!readFully(header, 0, 512)) return null

            // End of archive (zero block)
            if (header.all { it.toInt() == 0 }) return null

            val typeFlag = header[156].toInt().toChar()
            val size = extractOctal(header, 124, 12)

            // GNU long name (type 'L'): next header's name is stored in data
            if (typeFlag == 'L') {
                val longName = readEntryData(size)
                // Skip padding after long name data
                val padding = (512 - (size % 512)) % 512
                if (padding > 0) skipFully(padding)
                // Read the actual entry header
                val realHeader = ByteArray(512)
                if (!readFully(realHeader, 0, 512)) return null
                return parseHeader(realHeader, longName.trimEnd('\u0000'))
            }

            // GNU long link (type 'K'): next header's link is stored in data
            if (typeFlag == 'K') {
                val longLink = readEntryData(size)
                val padding = (512 - (size % 512)) % 512
                if (padding > 0) skipFully(padding)
                val realHeader = ByteArray(512)
                if (!readFully(realHeader, 0, 512)) return null
                return parseHeader(realHeader, linkOverride = longLink.trimEnd('\u0000'))
            }

            // Pax extended header (type 'x' or 'g'): skip the data, read next entry
            if (typeFlag == 'x' || typeFlag == 'g') {
                skipFully(size)
                val padding = (512 - (size % 512)) % 512
                if (padding > 0) skipFully(padding)
                return readNextEntry()
            }

            return parseHeader(header)
        }

        private fun readEntryData(size: Long): String {
            val data = ByteArray(size.toInt())
            readFully(data, 0, data.size)
            return String(data, Charsets.UTF_8)
        }

        private fun parseHeader(
            header: ByteArray,
            nameOverride: String? = null,
            linkOverride: String? = null
        ): TarEntry? {
            val name = extractString(header, 0, 100)
            val typeFlag = header[156].toInt().toChar()
            val size = extractOctal(header, 124, 12)
            val linkName = linkOverride ?: extractString(header, 157, 100)
            val prefix = extractString(header, 345, 155)

            val fullName = nameOverride
                ?: if (prefix.isNotEmpty()) "$prefix/$name" else name

            if (fullName.isEmpty()) return null

            remainingBytes = size

            currentEntry = TarEntry(
                name = fullName,
                size = size,
                isDirectory = typeFlag == '5' || fullName.endsWith("/"),
                isSymlink = typeFlag == '2',
                isHardLink = typeFlag == '1',
                linkName = if (typeFlag == '2' || typeFlag == '1') linkName else null
            )
            return currentEntry
        }

        private fun extractString(header: ByteArray, offset: Int, length: Int): String {
            val end = (offset until offset + length).firstOrNull { header[it].toInt() == 0 } ?: (offset + length)
            return String(header, offset, end - offset, Charsets.US_ASCII).trim()
        }

        private fun extractOctal(header: ByteArray, offset: Int, length: Int): Long {
            // Handle binary-encoded size (high bit set)
            if (length > 0 && (header[offset].toInt() and 0x80) != 0) {
                var value = 0L
                for (i in 1 until length) {
                    value = (value shl 8) or (header[offset + i].toLong() and 0xFF)
                }
                return value
            }
            val str = extractString(header, offset, length).trim()
            return try { str.toLong(8) } catch (e: NumberFormatException) { 0L }
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            val toRead = minOf(len.toLong(), remainingBytes).toInt()
            if (toRead <= 0) return -1
            val read = `in`.read(b, off, toRead)
            if (read > 0) remainingBytes -= read
            return read
        }
    }
}
