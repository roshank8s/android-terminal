package com.roshank8s.androidterminal

import android.app.Application
import android.util.Log
import com.roshank8s.androidterminal.utils.ShellEnvironment

/**
 * Application class for Android Terminal.
 * Handles global initialization.
 */
class TerminalApplication : Application() {

    companion object {
        private const val TAG = "TerminalApplication"
        lateinit var instance: TerminalApplication
            private set
    }

    lateinit var shellEnvironment: ShellEnvironment
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this

        Log.i(TAG, "Android Terminal starting...")

        // Initialize shell environment
        shellEnvironment = ShellEnvironment(this)
        shellEnvironment.setupInitialEnvironment()

        Log.i(TAG, "Shell environment initialized")
        Log.i(TAG, "Home: ${shellEnvironment.homeDir}")
        Log.i(TAG, "Prefix: ${shellEnvironment.prefixDir}")
        Log.i(TAG, "Shell: ${shellEnvironment.getDefaultShell()}")
    }
}
