package com.androidterminal.service

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
import com.androidterminal.R
import com.androidterminal.activity.MainActivity
import com.androidterminal.terminal.TerminalSession
import com.androidterminal.utils.ShellEnvironment

/**
 * Foreground service that manages terminal sessions.
 * Keeps sessions alive even when the activity is destroyed.
 */
class TerminalService : Service() {

    companion object {
        private const val TAG = "TerminalService"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "terminal_service"
    }

    /** Binder for activity connection */
    inner class LocalBinder : Binder() {
        fun getService(): TerminalService = this@TerminalService
    }

    private val binder = LocalBinder()

    /** All active terminal sessions */
    val sessions: MutableList<TerminalSession> = mutableListOf()

    /** Callbacks */
    var onSessionListChanged: (() -> Unit)? = null
    var onSessionContentChanged: ((TerminalSession) -> Unit)? = null

    /** Wake lock for keeping sessions alive */
    private var wakeLock: PowerManager.WakeLock? = null
    private var wakeLockAcquired = false

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        Log.i(TAG, "Terminal service created")
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, createNotification())
        return START_NOT_STICKY
    }

    /**
     * Creates a new terminal session.
     */
    fun createSession(
        rows: Int = 24,
        cols: Int = 80,
        shellCommand: String? = null,
        cwd: String? = null
    ): TerminalSession {
        val env = ShellEnvironment.buildEnvironment(this)
        val shell = shellCommand ?: ShellEnvironment.getDefaultShell(this)
        val workDir = cwd ?: ShellEnvironment.getHomeDirectory(this)

        val session = TerminalSession(
            shellCommand = shell,
            cwd = workDir,
            args = arrayOf(),
            envVars = env,
            initialRows = rows,
            initialCols = cols
        )

        session.onSessionChanged = { s ->
            onSessionContentChanged?.invoke(s)
            updateNotification()
        }

        session.onSessionFinished = { s ->
            updateNotification()
            if (sessions.all { !it.isRunning }) {
                // All sessions finished - could auto-close
                Log.i(TAG, "All sessions finished")
            }
        }

        session.onTitleChanged = { s, title ->
            updateNotification()
            onSessionListChanged?.invoke()
        }

        sessions.add(session)
        session.start()

        updateNotification()
        onSessionListChanged?.invoke()

        Log.i(TAG, "Created session ${session.sessionId}, total: ${sessions.size}")
        return session
    }

    /**
     * Removes a terminal session.
     */
    fun removeSession(session: TerminalSession) {
        session.stop()
        sessions.remove(session)
        updateNotification()
        onSessionListChanged?.invoke()

        if (sessions.isEmpty()) {
            stopSelf()
        }
    }

    /**
     * Removes all sessions.
     */
    fun removeAllSessions() {
        sessions.forEach { it.stop() }
        sessions.clear()
        onSessionListChanged?.invoke()
        stopSelf()
    }

    /**
     * Acquires a wake lock to keep sessions running.
     */
    fun acquireWakeLock() {
        if (!wakeLockAcquired) {
            val pm = getSystemService(POWER_SERVICE) as PowerManager
            wakeLock = pm.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "AndroidTerminal::TerminalService"
            )
            wakeLock?.acquire(60 * 60 * 1000L) // 1 hour max
            wakeLockAcquired = true
            updateNotification()
        }
    }

    /**
     * Releases the wake lock.
     */
    fun releaseWakeLock() {
        if (wakeLockAcquired) {
            wakeLock?.release()
            wakeLock = null
            wakeLockAcquired = false
            updateNotification()
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Terminal Sessions",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps terminal sessions running"
                setShowBadge(false)
            }
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val activeSessions = sessions.count { it.isRunning }
        val title = if (activeSessions > 0) {
            "$activeSessions active session${if (activeSessions > 1) "s" else ""}"
        } else {
            "No active sessions"
        }

        val subtitle = buildString {
            if (wakeLockAcquired) append("Wake lock active")
            val names = sessions.map { it.sessionName }.take(3)
            if (names.isNotEmpty()) {
                if (isNotEmpty()) append(" | ")
                append(names.joinToString(", "))
            }
        }

        val exitIntent = Intent(this, TerminalService::class.java).apply {
            action = "EXIT"
        }
        val exitPendingIntent = PendingIntent.getService(
            this, 0, exitIntent,
            PendingIntent.FLAG_IMMUTABLE
        )

        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(subtitle)
            .setSmallIcon(R.drawable.ic_terminal)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .addAction(
                Notification.Action.Builder(
                    null, "Exit", exitPendingIntent
                ).build()
            )
            .build()
    }

    private fun updateNotification() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(NOTIFICATION_ID, createNotification())
    }

    override fun onDestroy() {
        releaseWakeLock()
        sessions.forEach { it.stop() }
        sessions.clear()
        Log.i(TAG, "Terminal service destroyed")
        super.onDestroy()
    }
}
