package com.roshank8s.androidterminal.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import com.roshank8s.androidterminal.R
import com.roshank8s.androidterminal.terminal.TerminalSession
import com.roshank8s.androidterminal.ui.MainActivity
import com.roshank8s.androidterminal.utils.ShellEnvironment

/**
 * Foreground service that keeps terminal sessions alive when the app is backgrounded.
 *
 * Android aggressively kills background processes, especially on Android 12+ where
 * "phantom process" limits can terminate shell processes. Running as a foreground
 * service with a persistent notification prevents this.
 *
 * Features:
 * - Manages multiple terminal sessions
 * - Keeps a wake lock to prevent CPU sleep
 * - Shows notification with session count
 * - Survives app being backgrounded
 */
class TerminalService : Service(), TerminalSession.SessionClient {

    companion object {
        private const val TAG = "TerminalService"
        private const val NOTIFICATION_ID = 1337
        private const val CHANNEL_ID = "terminal_service"
        const val ACTION_STOP = "com.roshank8s.androidterminal.ACTION_STOP"
        const val ACTION_NEW_SESSION = "com.roshank8s.androidterminal.ACTION_NEW_SESSION"
    }

    /** Binder for activity to connect to this service. */
    inner class TerminalBinder : Binder() {
        fun getService(): TerminalService = this@TerminalService
    }

    private val binder = TerminalBinder()

    /** All active terminal sessions. */
    val sessions = mutableListOf<TerminalSession>()

    /** Callback for UI updates. */
    var sessionChangedCallback: ((TerminalSession) -> Unit)? = null
    var sessionFinishedCallback: ((TerminalSession) -> Unit)? = null

    /** Wake lock to keep CPU running. */
    private var wakeLock: PowerManager.WakeLock? = null

    private lateinit var shellEnvironment: ShellEnvironment

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "Service created")
        shellEnvironment = ShellEnvironment(this)
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
        acquireWakeLock()
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                terminateAllSessions()
                stopSelf()
            }
            ACTION_NEW_SESSION -> {
                createSession()
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        Log.i(TAG, "Service destroyed")
        terminateAllSessions()
        releaseWakeLock()
        super.onDestroy()
    }

    /**
     * Creates a new terminal session.
     *
     * @param shellCommand Override the default shell command
     * @param initialCommand Command to run after shell starts
     * @return The created session
     */
    fun createSession(
        shellCommand: String? = null,
        initialCommand: String? = null,
        rows: Int = 24,
        columns: Int = 80
    ): TerminalSession {
        val env = shellEnvironment.buildEnvironment()
        val shell = shellCommand ?: shellEnvironment.getDefaultShell()
        val cwd = shellEnvironment.getHomeDirectory()
        val args = shellEnvironment.getShellArgs(shell)

        val session = TerminalSession(
            shellPath = shell,
            cwd = cwd,
            args = args,
            env = env,
            initialRows = rows,
            initialColumns = columns,
            client = this
        )

        sessions.add(session)
        session.start()

        // Send initial command if provided
        if (initialCommand != null) {
            session.writeToShell("$initialCommand\n".toByteArray())
        }

        updateNotification()
        Log.i(TAG, "Session created: ${session.id}, total=${sessions.size}")
        return session
    }

    /**
     * Creates a session running a Linux distro via PRoot.
     */
    fun createDistroSession(
        distroCommand: List<String>,
        rows: Int = 24,
        columns: Int = 80
    ): TerminalSession {
        val env = shellEnvironment.buildPRootEnvironment()

        val session = TerminalSession(
            shellPath = distroCommand[0],
            cwd = shellEnvironment.getHomeDirectory(),
            args = distroCommand.drop(1).toTypedArray(),
            env = env,
            initialRows = rows,
            initialColumns = columns,
            client = this
        )

        sessions.add(session)
        session.start()
        updateNotification()
        Log.i(TAG, "Distro session created: ${session.id}")
        return session
    }

    /**
     * Remove a session.
     */
    fun removeSession(session: TerminalSession) {
        session.finish()
        sessions.remove(session)
        updateNotification()

        if (sessions.isEmpty()) {
            stopSelf()
        }
    }

    /**
     * Terminate all sessions.
     */
    fun terminateAllSessions() {
        sessions.forEach { it.finish() }
        sessions.clear()
        updateNotification()
    }

    // TerminalSession.SessionClient implementation

    override fun onSessionChanged(session: TerminalSession) {
        sessionChangedCallback?.invoke(session)
    }

    override fun onSessionTitleChanged(session: TerminalSession) {
        updateNotification()
        sessionChangedCallback?.invoke(session)
    }

    override fun onSessionFinished(session: TerminalSession) {
        Log.i(TAG, "Session finished: ${session.id}, exit=${session.exitStatus}")
        sessionFinishedCallback?.invoke(session)
        updateNotification()
    }

    override fun onBell(session: TerminalSession) {
        // Vibrate or flash notification
    }

    override fun onClipboardText(session: TerminalSession, text: String) {
        // Handle OSC 52 clipboard
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
        clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Terminal", text))
    }

    // Notification management

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Terminal Sessions",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps terminal sessions running in the background"
                setShowBadge(false)
            }
            val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val contentIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val stopIntent = PendingIntent.getService(
            this, 0,
            Intent(this, TerminalService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_IMMUTABLE
        )

        val sessionCount = sessions.size
        val title = "Android Terminal"
        val text = when {
            sessionCount == 0 -> "No active sessions"
            sessionCount == 1 -> "1 active session"
            else -> "$sessionCount active sessions"
        }

        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }

        return builder
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_terminal)
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .addAction(
                Notification.Action.Builder(
                    null, "Exit All",
                    stopIntent
                ).build()
            )
            .build()
    }

    private fun updateNotification() {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID, buildNotification())
    }

    private fun acquireWakeLock() {
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "AndroidTerminal::TerminalService"
        ).apply {
            acquire()
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.let {
            if (it.isHeld) it.release()
        }
        wakeLock = null
    }
}
