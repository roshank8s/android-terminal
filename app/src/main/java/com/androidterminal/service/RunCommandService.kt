package com.androidterminal.service

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log

/**
 * Service that allows external apps to run commands in the terminal.
 * Protected by a custom permission.
 */
class RunCommandService : Service() {

    companion object {
        private const val TAG = "RunCommandService"
        const val ACTION_RUN_COMMAND = "com.androidterminal.RUN_COMMAND"
        const val EXTRA_COMMAND = "com.androidterminal.RUN_COMMAND_COMMAND"
        const val EXTRA_ARGUMENTS = "com.androidterminal.RUN_COMMAND_ARGUMENTS"
        const val EXTRA_WORKDIR = "com.androidterminal.RUN_COMMAND_WORKDIR"
        const val EXTRA_BACKGROUND = "com.androidterminal.RUN_COMMAND_BACKGROUND"
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_RUN_COMMAND) {
            val command = intent.getStringExtra(EXTRA_COMMAND) ?: return START_NOT_STICKY
            val arguments = intent.getStringArrayExtra(EXTRA_ARGUMENTS)
            val workdir = intent.getStringExtra(EXTRA_WORKDIR)
            val background = intent.getBooleanExtra(EXTRA_BACKGROUND, false)

            Log.i(TAG, "Run command: $command")

            // Forward to main activity via broadcast
            val forwardIntent = Intent("com.androidterminal.EXECUTE_COMMAND").apply {
                putExtra(EXTRA_COMMAND, command)
                putExtra(EXTRA_ARGUMENTS, arguments)
                putExtra(EXTRA_WORKDIR, workdir)
                putExtra(EXTRA_BACKGROUND, background)
                setPackage(packageName)
            }
            sendBroadcast(forwardIntent)
        }

        stopSelf(startId)
        return START_NOT_STICKY
    }
}
