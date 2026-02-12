# Android Terminal

A powerful, feature-rich terminal emulator for Android, inspired by [Termux](https://github.com/termux/termux-app) but built from scratch in Kotlin with enhanced features and a modern Material Design UI.

## Features

### Core Terminal
- **Full terminal emulation** - VT100/VT220/xterm compatible ANSI escape sequence parser
- **Native PTY management** - JNI bridge to C code for pseudoterminal creation, process spawning, and lifecycle management
- **Multiple terminal sessions** - Tab-based session management with independent shell processes
- **Persistent sessions** - Foreground service keeps sessions alive when app is backgrounded
- **UTF-8 support** - Full Unicode character support
- **256-color and true color** - Complete xterm 256-color palette support

### Enhanced Features (Beyond Termux)
- **Built-in File Manager** - GUI file browser with create, rename, delete, and preview capabilities
- **Command Snippets** - Save and quickly insert frequently used commands
- **SSH Connection Manager** - Save SSH profiles and connect with one tap
- **Multiple Color Themes** - Dracula, Nord, Gruvbox, Monokai, Solarized Dark, and default dark theme
- **Pinch-to-Zoom** - Resize terminal font with pinch gestures
- **Text Selection** - Double-tap for word selection, long-press for custom selection
- **Extra Keys Toolbar** - Two-row toolbar with ESC, TAB, CTRL, ALT, arrows, function keys, and more

### Terminal Capabilities
- **Keyboard shortcuts** - Full Ctrl+key, Alt+key, and function key support
- **Bracketed paste mode** - Proper paste handling for editors like vim
- **Application cursor keys** - Correct behavior in programs like less, vim, etc.
- **Mouse tracking** - Support for mouse event reporting
- **Alternate screen buffer** - Proper handling for full-screen TUI programs
- **Scrollback history** - 2000 lines of scrollback buffer
- **Copy/Paste** - System clipboard integration with bracketed paste support

### System Integration
- **Boot scripts** - Run commands automatically on device boot
- **External command execution** - Other apps can run commands via intent
- **Wake lock** - Keep device awake for long-running operations
- **Storage access** - Browse and manage files across the device

## Architecture

```
app/
├── src/main/
│   ├── jni/
│   │   ├── CMakeLists.txt          # Native build config
│   │   └── terminal_native.c       # PTY management (C/JNI)
│   │
│   ├── java/com/androidterminal/
│   │   ├── TerminalApplication.kt  # Application entry point
│   │   │
│   │   ├── terminal/               # Core terminal engine
│   │   │   ├── NativePty.kt        # JNI bridge to native code
│   │   │   ├── TerminalEmulator.kt # ANSI escape sequence parser
│   │   │   ├── TerminalSession.kt  # Session lifecycle management
│   │   │   ├── TerminalBuffer.kt   # Screen buffer with scrollback
│   │   │   ├── TerminalRow.kt      # Row data with styled characters
│   │   │   └── KeyHandler.kt       # Key event translation
│   │   │
│   │   ├── view/                   # Custom UI components
│   │   │   ├── TerminalView.kt     # Terminal rendering & input
│   │   │   └── ExtraKeysView.kt    # Special keys toolbar
│   │   │
│   │   ├── service/                # Android services
│   │   │   ├── TerminalService.kt  # Session management service
│   │   │   ├── RunCommandService.kt # External command execution
│   │   │   └── BootReceiver.kt     # Boot completed receiver
│   │   │
│   │   ├── activity/               # UI screens
│   │   │   ├── MainActivity.kt     # Main terminal interface
│   │   │   ├── SettingsActivity.kt # App settings
│   │   │   ├── FileManagerActivity.kt # Built-in file manager
│   │   │   └── SnippetActivity.kt  # Command snippet manager
│   │   │
│   │   ├── snippets/               # Snippet system
│   │   │   └── SnippetManager.kt   # Save/load command snippets
│   │   │
│   │   ├── ssh/                    # SSH management
│   │   │   └── SSHManager.kt       # SSH profile management
│   │   │
│   │   └── utils/                  # Utilities
│   │       ├── ShellEnvironment.kt # Shell env setup
│   │       ├── TerminalColors.kt   # Color palette & themes
│   │       └── TerminalPreferences.kt # Settings persistence
│   │
│   └── res/                        # Android resources
│       ├── layout/                 # XML layouts
│       ├── drawable/               # Vector icons
│       ├── menu/                   # Menus
│       ├── values/                 # Strings, colors, themes
│       └── xml/                    # Config files
```

## How It Works

### Terminal Emulation Pipeline

```
User Input → TerminalView → KeyHandler → TerminalSession.write()
                                              ↓
                                         NativePty.write() → PTY Master FD → Shell Process
                                              ↓
Shell Output → PTY Master FD → NativePty.read() → TerminalEmulator.processBytes()
                                                        ↓
                                                   TerminalBuffer → TerminalView.onDraw()
```

1. **User types** on keyboard or taps extra keys
2. **KeyHandler** translates Android KeyEvents to terminal escape sequences
3. **NativePty** writes bytes to the PTY master file descriptor (via JNI)
4. **Shell process** receives input through the PTY slave
5. Shell output flows back through the PTY master
6. **TerminalEmulator** parses ANSI escape sequences and updates the buffer
7. **TerminalView** renders the buffer content to the screen

### Native Layer (C/JNI)

The native layer handles low-level PTY operations:
- `openpty()` to create pseudoterminal pairs
- `fork()` + `execvp()` to spawn shell processes
- `ioctl(TIOCSWINSZ)` for terminal window resizing
- Non-blocking I/O with `O_NONBLOCK`
- Process lifecycle management with `waitpid()`

## Building

### Requirements
- Android Studio Arctic Fox or later
- Android SDK 34
- Android NDK (for native C code compilation)
- Kotlin 1.9.20+

### Build Steps
1. Clone the repository
2. Open in Android Studio
3. Sync Gradle
4. Build and run on device (API 24+)

```bash
./gradlew assembleDebug
```

## Comparison with Termux

| Feature | Termux | Android Terminal |
|---------|--------|-----------------|
| Language | Java | Kotlin |
| Terminal Emulation | Yes | Yes |
| Package Manager | apt/pkg (1000+ packages) | Basic (expandable) |
| File Manager | CLI only | Built-in GUI |
| Command Snippets | No | Yes |
| SSH Profiles | CLI only | GUI manager |
| Color Themes | Via Termux:Styling plugin | Built-in (6 themes) |
| Extra Keys | Single row | Two rows with F-keys |
| Pinch-to-Zoom | Yes | Yes |
| Material Design | Partial | Full Material 3 |
| Min Android | API 24 | API 24 |
| License | GPLv3 | GPLv3 |

## License

This project is licensed under the GNU General Public License v3.0 - see the [LICENSE](LICENSE) file for details.
