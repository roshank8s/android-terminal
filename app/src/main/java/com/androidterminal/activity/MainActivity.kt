package com.androidterminal.activity

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.androidterminal.R
import com.androidterminal.service.TerminalService
import com.androidterminal.terminal.TerminalSession
import com.androidterminal.view.ExtraKeysView
import com.androidterminal.view.TerminalView
import com.google.android.material.navigation.NavigationView
import com.google.android.material.tabs.TabLayout

/**
 * Main activity hosting the terminal interface with tabbed sessions,
 * drawer navigation, and extra keys toolbar.
 */
class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "MainActivity"
        private const val REQUEST_NOTIFICATION_PERMISSION = 1001
        private const val REQUEST_STORAGE_PERMISSION = 1002
    }

    // Service connection
    private var terminalService: TerminalService? = null
    private var serviceBound = false

    // Views
    private lateinit var terminalView: TerminalView
    private lateinit var extraKeysView: ExtraKeysView
    private lateinit var tabLayout: TabLayout
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navigationView: NavigationView

    // Current session
    private var currentSession: TerminalSession? = null

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as TerminalService.LocalBinder
            terminalService = binder.getService()
            serviceBound = true
            onServiceReady()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            terminalService = null
            serviceBound = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Keep screen on
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        setupViews()
        requestPermissions()
        startAndBindService()
    }

    private fun setupViews() {
        val toolbar = findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setHomeAsUpIndicator(R.drawable.ic_menu)

        terminalView = findViewById(R.id.terminal_view)
        extraKeysView = findViewById(R.id.extra_keys_view)
        tabLayout = findViewById(R.id.tab_layout)
        drawerLayout = findViewById(R.id.drawer_layout)
        navigationView = findViewById(R.id.navigation_view)

        // Extra keys -> terminal input
        extraKeysView.onKeyPress = { data ->
            currentSession?.write(data)
            terminalView.refresh()
        }

        // Tab selection
        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                val index = tab.position
                val sessions = terminalService?.sessions ?: return
                if (index < sessions.size) {
                    switchToSession(sessions[index])
                }
            }
            override fun onTabUnselected(tab: TabLayout.Tab) {}
            override fun onTabReselected(tab: TabLayout.Tab) {}
        })

        // Navigation drawer
        navigationView.setNavigationItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.nav_new_session -> createNewSession()
                R.id.nav_file_manager -> openFileManager()
                R.id.nav_snippets -> openSnippets()
                R.id.nav_settings -> openSettings()
                R.id.nav_help -> showHelp()
            }
            drawerLayout.closeDrawers()
            true
        }
    }

    private fun requestPermissions() {
        val permissions = mutableListOf<String>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        if (permissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(
                this, permissions.toTypedArray(), REQUEST_NOTIFICATION_PERMISSION
            )
        }
    }

    private fun startAndBindService() {
        val intent = Intent(this, TerminalService::class.java)
        startForegroundService(intent)
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    private fun onServiceReady() {
        val service = terminalService ?: return

        service.onSessionListChanged = { runOnUiThread { updateTabs() } }
        service.onSessionContentChanged = { session ->
            runOnUiThread {
                if (session == currentSession) {
                    terminalView.refresh()
                }
            }
        }

        // Create initial session if none exist
        if (service.sessions.isEmpty()) {
            createNewSession()
        } else {
            switchToSession(service.sessions.first())
            updateTabs()
        }
    }

    private fun createNewSession() {
        val service = terminalService ?: return
        val session = service.createSession()
        switchToSession(session)
        updateTabs()
    }

    private fun switchToSession(session: TerminalSession) {
        currentSession = session
        terminalView.attachSession(session)
        supportActionBar?.subtitle = session.sessionName
    }

    private fun updateTabs() {
        val sessions = terminalService?.sessions ?: return
        tabLayout.removeAllTabs()

        for ((index, session) in sessions.withIndex()) {
            val tab = tabLayout.newTab()
            tab.text = session.sessionName.ifEmpty { "Shell ${index + 1}" }
            tabLayout.addTab(tab, session == currentSession)
        }

        tabLayout.visibility = if (sessions.size > 1) View.VISIBLE else View.GONE
    }

    private fun removeCurrentSession() {
        val service = terminalService ?: return
        val session = currentSession ?: return

        if (service.sessions.size <= 1) {
            // Last session - confirm exit
            AlertDialog.Builder(this)
                .setTitle("Close Terminal")
                .setMessage("This will close the last terminal session and exit.")
                .setPositiveButton("Close") { _, _ ->
                    service.removeSession(session)
                    finish()
                }
                .setNegativeButton("Cancel", null)
                .show()
        } else {
            service.removeSession(session)
            if (service.sessions.isNotEmpty()) {
                switchToSession(service.sessions.last())
            }
            updateTabs()
        }
    }

    private fun openFileManager() {
        startActivity(Intent(this, FileManagerActivity::class.java))
    }

    private fun openSnippets() {
        val intent = Intent(this, SnippetActivity::class.java)
        startActivityForResult(intent, 2001)
    }

    private fun openSettings() {
        startActivity(Intent(this, SettingsActivity::class.java))
    }

    private fun showHelp() {
        AlertDialog.Builder(this)
            .setTitle("Android Terminal")
            .setMessage(buildString {
                appendLine("A powerful terminal emulator for Android.")
                appendLine()
                appendLine("Features:")
                appendLine("- Multiple terminal sessions")
                appendLine("- Extra keys toolbar")
                appendLine("- Pinch to zoom")
                appendLine("- Text selection & copy/paste")
                appendLine("- File manager")
                appendLine("- Command snippets")
                appendLine("- Color themes (Dracula, Nord, etc.)")
                appendLine("- SSH client")
                appendLine()
                appendLine("Swipe from left edge to open the navigation drawer.")
            })
            .setPositiveButton("OK", null)
            .show()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                drawerLayout.openDrawer(navigationView)
                true
            }
            R.id.action_new_session -> {
                createNewSession()
                true
            }
            R.id.action_close_session -> {
                removeCurrentSession()
                true
            }
            R.id.action_copy -> {
                terminalView.copySelection()
                true
            }
            R.id.action_paste -> {
                terminalView.paste()
                true
            }
            R.id.action_toggle_keyboard -> {
                terminalView.toggleKeyboard()
                true
            }
            R.id.action_reset_terminal -> {
                currentSession?.reset()
                terminalView.refresh()
                true
            }
            R.id.action_toggle_extra_keys -> {
                extraKeysView.visibility = if (extraKeysView.visibility == View.VISIBLE)
                    View.GONE else View.VISIBLE
                true
            }
            R.id.action_wake_lock -> {
                val service = terminalService ?: return true
                service.acquireWakeLock()
                Toast.makeText(this, "Wake lock acquired", Toast.LENGTH_SHORT).show()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 2001 && resultCode == RESULT_OK) {
            val snippet = data?.getStringExtra("snippet_command") ?: return
            currentSession?.write(snippet + "\n")
        }
    }

    override fun onDestroy() {
        if (serviceBound) {
            unbindService(serviceConnection)
            serviceBound = false
        }
        super.onDestroy()
    }

    @Deprecated("Use onBackPressedDispatcher")
    override fun onBackPressed() {
        if (drawerLayout.isDrawerOpen(navigationView)) {
            drawerLayout.closeDrawers()
        } else {
            // Double-tap back to exit
            super.onBackPressed()
        }
    }
}
