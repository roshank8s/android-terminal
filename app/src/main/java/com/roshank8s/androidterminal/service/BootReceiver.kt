package com.roshank8s.androidterminal.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Receives BOOT_COMPLETED broadcast to optionally restart the terminal service.
 */
class BootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BootReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Log.i(TAG, "Boot completed received")
            // Only start service if user has enabled auto-start in settings
            val prefs = context.getSharedPreferences("terminal_prefs", Context.MODE_PRIVATE)
            if (prefs.getBoolean("auto_start_on_boot", false)) {
                val serviceIntent = Intent(context, TerminalService::class.java)
                context.startForegroundService(serviceIntent)
            }
        }
    }
}
