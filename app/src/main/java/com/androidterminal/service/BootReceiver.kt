package com.androidterminal.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import java.io.File

/**
 * Receives boot completed events to run startup scripts.
 */
class BootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BootReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        Log.i(TAG, "Boot completed, checking for startup scripts")

        val bootScriptDir = File(context.filesDir, "home/.termux/boot")
        if (bootScriptDir.exists() && bootScriptDir.isDirectory) {
            val scripts = bootScriptDir.listFiles()?.filter { it.canExecute() } ?: emptyList()
            if (scripts.isNotEmpty()) {
                Log.i(TAG, "Found ${scripts.size} boot scripts to execute")
                // Start the terminal service to execute boot scripts
                val serviceIntent = Intent(context, TerminalService::class.java)
                context.startForegroundService(serviceIntent)
            }
        }
    }
}
