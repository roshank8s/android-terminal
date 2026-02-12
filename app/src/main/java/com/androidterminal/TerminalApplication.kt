package com.androidterminal

import android.app.Application
import android.util.Log
import com.androidterminal.utils.ShellEnvironment

/**
 * Application class for Android Terminal.
 * Handles initialization and global state.
 */
class TerminalApplication : Application() {

    companion object {
        private const val TAG = "TerminalApp"
        lateinit var instance: TerminalApplication
            private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this

        Log.i(TAG, "Android Terminal starting...")

        // Setup file structure on first run
        Thread {
            try {
                ShellEnvironment.setupFileStructure(this)
                Log.i(TAG, "File structure setup complete")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to setup file structure", e)
            }
        }.start()

        // Setup crash handler
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Log.e(TAG, "Uncaught exception in thread ${thread.name}", throwable)
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }
}
