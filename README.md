# Android Terminal

A powerful Android terminal emulator with full Linux distribution support. Run Kali Linux, Ubuntu, Debian, or Alpine Linux on your Android device — install apt packages, use standard Linux commands, and access a complete Linux environment without root.

## Features

### Terminal Emulator
- **VT100/VT420/xterm-compatible** escape sequence processing
- **256-color** and true color support
- **SGR text styling** — bold, italic, underline, strikethrough, dim, inverse, blink
- **Alternate screen buffer** for fullscreen apps (vim, htop, less, nano)
- **Scrollback history** with configurable buffer (up to 10,000 lines)
- **Mouse tracking** support (click, button, any-event modes)
- **Bracketed paste mode** for safe pasting in supported shells
- **Line drawing characters** for box-drawing in TUI applications
- **Unicode/UTF-8** support

### Linux Distribution Support
- **Kali Linux** — security-focused distro with penetration testing tools
- **Ubuntu** — most popular general-purpose Linux distribution
- **Debian** — stable and reliable base distribution
- **Alpine Linux** — ultra-lightweight distribution

All distros run via **PRoot** (no root required):
- Full `apt-get` / `dpkg` package management (Debian-based)
- `apk` package management (Alpine)
- Install any packages: `apt install python3 git nmap vim gcc`
- Run standard Linux commands in a real Linux filesystem
- Fake root environment (appear as uid 0)
- `/dev`, `/proc`, `/sys` bind mounts from the host

### Multiple Sessions
- Run multiple terminal sessions simultaneously
- Switch between sessions via navigation drawer
- Sessions persist in background via foreground service
- Each session has independent state and working directory

### Extra Keys Bar
- Scrollable row of commonly needed terminal keys
- **Modifiers**: Ctrl, Alt, Shift (sticky toggle)
- **Navigation**: Arrow keys, Home, End, PgUp, PgDn
- **Special characters**: |, /, -, ~, :, _, \, {, }, [, ]
- **Function keys**: F1-F12 (via Fn toggle)
- **Control**: ESC, Tab, Insert, Delete

### Built-in File Manager
- Browse filesystem (home directory, distro rootfs, sdcard)
- Create files and directories
- View file contents and properties
- Delete files and directories

### Customization
- **10 color schemes**: Dark, Light, Solarized, Monokai, Dracula, Nord, Gruvbox, One Dark, Material
- **3 cursor styles**: Block, Underline, Bar (with blink option)
- **Font size**: Adjustable 6-42sp (pinch-to-zoom or Ctrl+Shift+/-/+)
- **Back key behavior**: ESC, Navigate Back, or Confirm Exit
- **Volume keys**: Volume control, font size, or scroll

### Keyboard Support
- Full hardware keyboard support with proper escape sequences
- Ctrl+key combinations (Ctrl+C, Ctrl+D, Ctrl+Z, etc.)
- Alt+key prefix for Meta key
- Ctrl+Shift+C/V for copy/paste
- Function keys F1-F12
- Soft keyboard with InputConnection for text input

### System Integration
- Foreground service keeps sessions alive when backgrounded
- Wake lock prevents CPU sleep during long-running operations
- Optional auto-start on device boot
- Notification with session count and quick actions
- Clipboard integration (copy/paste, OSC 52)

## Architecture

```
┌──────────────────────────────────────────────┐
│                 Android App                   │
│  ┌──────────┐  ┌───────────┐  ┌───────────┐ │
│  │   Main   │  │  Settings │  │   File    │ │
│  │ Activity │  │  Activity │  │  Manager  │ │
│  └────┬─────┘  └───────────┘  └───────────┘ │
│       │                                       │
│  ┌────┴─────────────────────────────────┐    │
│  │         Terminal View                 │    │
│  │  ┌─────────┐  ┌──────────────────┐  │    │
│  │  │Renderer │  │   Extra Keys     │  │    │
│  │  └─────────┘  └──────────────────┘  │    │
│  └────┬────────────────────────────────┘    │
│       │                                       │
│  ┌────┴─────────────────────────────────┐    │
│  │       Terminal Emulator               │    │
│  │  ┌────────┐ ┌───────┐ ┌──────────┐  │    │
│  │  │ Buffer │ │Colors │ │Key Handle│  │    │
│  │  └────────┘ └───────┘ └──────────┘  │    │
│  └────┬────────────────────────────────┘    │
│       │                                       │
│  ┌────┴────┐  ┌──────────────────────┐       │
│  │   JNI   │  │   Terminal Service   │       │
│  │ Bridge  │  │  (Foreground Svc)    │       │
│  └────┬────┘  └──────────────────────┘       │
└───────┼──────────────────────────────────────┘
        │
   ┌────┴────┐        ┌─────────────────────┐
   │   PTY   │        │   PRoot + Distro    │
   │ Master/ │        │  ┌───────────────┐  │
   │  Slave  │◄──────►│  │ Linux Rootfs  │  │
   │  Pair   │        │  │ (Kali/Ubuntu/ │  │
   └────┬────┘        │  │  Debian/etc)  │  │
        │             │  └───────────────┘  │
   ┌────┴────┐        └─────────────────────┘
   │  Shell  │
   │ Process │
   └─────────┘
```

### Key Components

| Component | Location | Description |
|-----------|----------|-------------|
| **JNI Bridge** | `app/src/main/jni/terminal_jni.c` | C code for PTY creation via `forkpty()`, process management |
| **Terminal Emulator** | `terminal/TerminalEmulator.kt` | VT420-compatible escape sequence state machine |
| **Terminal Buffer** | `terminal/TerminalBuffer.kt` | Circular buffer with scrollback history |
| **Terminal Renderer** | `terminal/TerminalRenderer.kt` | Canvas-based rendering with color and style support |
| **Terminal View** | `terminal/TerminalView.kt` | Android View with touch, keyboard, and selection handling |
| **Terminal Session** | `terminal/TerminalSession.kt` | Manages PTY + child process + reader thread |
| **Key Handler** | `terminal/KeyHandler.kt` | Converts Android KeyEvents to terminal escape sequences |
| **Bootstrap Installer** | `bootstrap/BootstrapInstaller.kt` | Downloads and extracts Linux rootfs tarballs |
| **Distro Manager** | `bootstrap/DistroManager.kt` | Manages installed distros and PRoot commands |
| **Terminal Service** | `service/TerminalService.kt` | Foreground service for persistent sessions |

## Building

### Prerequisites
- Android Studio Hedgehog (2023.1.1) or newer
- Android SDK 34
- Android NDK (for native C compilation)
- JDK 17

### Build Steps

```bash
# Clone the repository
git clone https://github.com/roshank8s/android-terminal.git
cd android-terminal

# Build debug APK
./gradlew assembleDebug

# Build release APK
./gradlew assembleRelease

# Install on connected device
./gradlew installDebug
```

## Usage

### Basic Usage
1. Launch the app — a shell session starts automatically
2. Type commands as you would in any Linux terminal
3. Use the extra keys bar for Ctrl, Alt, Tab, arrows, etc.
4. Swipe from the left edge to open the session drawer

### Install a Linux Distribution
1. Open the session drawer (swipe from left or tap hamburger menu)
2. Tap "Linux Distributions"
3. Select a distro to install (Kali Linux, Ubuntu, Debian, or Alpine)
4. Wait for download and extraction to complete
5. Launch the distro — you'll get a full Linux shell

### Package Management (inside distro)
```bash
# Update package list
apt-get update

# Install packages
apt-get install python3 git nmap vim gcc nodejs

# Search for packages
apt-cache search <keyword>

# Remove packages
apt-get remove <package>
```

### Keyboard Shortcuts
| Shortcut | Action |
|----------|--------|
| Ctrl+Shift+C | Copy selected text |
| Ctrl+Shift+V | Paste from clipboard |
| Ctrl+Shift+N | New session |
| Ctrl+Shift++ | Increase font size |
| Ctrl+Shift+- | Decrease font size |
| Ctrl+C | Send SIGINT |
| Ctrl+D | Send EOF |
| Ctrl+Z | Send SIGTSTP (suspend) |
| Ctrl+L | Clear screen |

## How It Works

### Terminal Emulation
The app creates a **pseudoterminal (PTY)** pair via the `forkpty()` system call through JNI. The master side connects to the terminal view; the slave side connects to the shell process. All terminal escape sequences (cursor movement, colors, screen clearing, etc.) are processed by a VT420-compatible state machine.

### Linux Distribution Support
Linux distros run via **PRoot**, which uses `ptrace()` to intercept every system call made by child processes. PRoot rewrites filesystem paths (so `/usr/bin/python` becomes `<rootfs>/usr/bin/python`) and fakes root identity (so `apt-get` thinks it's running as root). No actual root access is needed.

### Why target SDK 28?
Android 10+ (SDK 29+) blocks executing files from the app's data directory (`W^X` policy). By targeting SDK 28, the app can execute binaries (PRoot, shell, etc.) from its private storage. This is the same approach used by Termux.

## License

GNU General Public License v3.0 — see [LICENSE](LICENSE) for details.
