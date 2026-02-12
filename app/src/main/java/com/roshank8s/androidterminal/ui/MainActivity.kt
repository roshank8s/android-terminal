package com.roshank8s.androidterminal.ui

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.view.KeyEvent
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.roshank8s.androidterminal.R
import com.roshank8s.androidterminal.bootstrap.BootstrapInstaller
import com.roshank8s.androidterminal.bootstrap.DistroManager
import com.roshank8s.androidterminal.databinding.ActivityMainBinding
import com.roshank8s.androidterminal.service.TerminalService
import com.roshank8s.androidterminal.terminal.TerminalSession
import com.roshank8s.androidterminal.terminal.TerminalView
import com.roshank8s.androidterminal.utils.TerminalPreferences
import kotlinx.coroutines.*

/**
 * Main activity hosting the terminal view, extra keys bar, and session drawer.
 *
 * Features:
 * - Multiple terminal session tabs via navigation drawer
 * - Extra keys row (Ctrl, Alt, Tab, arrows, etc.)
 * - Pinch-to-zoom font sizing
 * - Hardware keyboard support
 * - Linux distro management (install/launch via PRoot)
 * - File manager integration
 * - Customizable settings
 */
class MainActivity : AppCompatActivity(),
    TerminalView.TerminalViewClient,
    SessionsAdapter.SessionActionListener,
    TerminalExtraKeys.ExtraKeysListener {

    private lateinit var binding: ActivityMainBinding
    private lateinit var preferences: TerminalPreferences
    private lateinit var distroManager: DistroManager

    // Service connection
    private var terminalService: TerminalService? = null
    private var serviceBound = false

    // Current session
    private var currentSession: TerminalSession? = null

    // Sessions adapter
    private lateinit var sessionsAdapter: SessionsAdapter

    // Coroutine scope for async operations
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            val binder = service as TerminalService.TerminalBinder
            terminalService = binder.getService()
            serviceBound = true

            // Set up callbacks
            terminalService?.sessionChangedCallback = { session ->
                if (session == currentSession) {
                    binding.terminalView.invalidate()
                }
                updateSessionList()
            }

            terminalService?.sessionFinishedCallback = { session ->
                runOnUiThread {
                    Toast.makeText(
                        this@MainActivity,
                        "Session ended (exit ${session.exitStatus})",
                        Toast.LENGTH_SHORT
                    ).show()
                    updateSessionList()
                }
            }

            // Create first session if none exist
            if (terminalService?.sessions?.isEmpty() == true) {
                createNewSession()
            } else {
                // Attach to existing session
                val session = terminalService?.sessions?.firstOrNull()
                if (session != null) {
                    switchToSession(session)
                }
            }

            updateSessionList()
        }

        override fun onServiceDisconnected(name: ComponentName) {
            terminalService = null
            serviceBound = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        preferences = TerminalPreferences(this)
        distroManager = DistroManager(this)

        setupToolbar()
        setupTerminalView()
        setupExtraKeys()
        setupDrawer()
        setupFab()

        // Start and bind to the terminal service
        val serviceIntent = Intent(this, TerminalService::class.java)
        startForegroundService(serviceIntent)
        bindService(serviceIntent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    override fun onDestroy() {
        scope.cancel()
        if (serviceBound) {
            unbindService(serviceConnection)
            serviceBound = false
        }
        super.onDestroy()
    }

    override fun onResume() {
        super.onResume()
        binding.terminalView.invalidate()
    }

    // Setup methods

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            setHomeAsUpIndicator(R.drawable.ic_menu)
            title = "Terminal"
        }

        binding.toolbar.visibility = if (preferences.showToolbar) View.VISIBLE else View.GONE
    }

    private fun setupTerminalView() {
        binding.terminalView.terminalViewClient = this
        binding.terminalView.cursorStyle = preferences.cursorStyle

        // Apply font size from preferences
        binding.terminalView.changeFontSize(preferences.fontSize - TerminalView.DEFAULT_FONT_SIZE)

        binding.terminalView.keepScreenOn = preferences.keepScreenOn
    }

    private fun setupExtraKeys() {
        binding.extraKeys.listener = this
        binding.extraKeys.visibility =
            if (preferences.extraKeysVisible) View.VISIBLE else View.GONE
    }

    private fun setupDrawer() {
        sessionsAdapter = SessionsAdapter(
            terminalService?.sessions ?: emptyList(),
            this
        )

        binding.sessionsList.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = sessionsAdapter
        }

        // New session button in drawer
        binding.btnNewSession.setOnClickListener {
            createNewSession()
            binding.drawerLayout.closeDrawer(GravityCompat.START)
        }

        // Distro button in drawer
        binding.btnDistro.setOnClickListener {
            showDistroDialog()
            binding.drawerLayout.closeDrawer(GravityCompat.START)
        }

        // Settings button
        binding.btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
            binding.drawerLayout.closeDrawer(GravityCompat.START)
        }
    }

    private fun setupFab() {
        binding.fabKeyboard.setOnClickListener {
            binding.terminalView.toggleSoftKeyboard()
        }
    }

    // Session management

    private fun createNewSession() {
        val service = terminalService ?: return

        val session = if (preferences.useDistro) {
            val distro = distroManager.getDefaultDistro()
            if (distro != null) {
                val cmd = distroManager.buildPRootCommand(distro)
                service.createDistroSession(cmd)
            } else {
                service.createSession()
            }
        } else {
            service.createSession()
        }

        switchToSession(session)
        updateSessionList()
    }

    private fun switchToSession(session: TerminalSession) {
        currentSession = session
        binding.terminalView.session = session
        binding.terminalView.scrollToBottom()
        supportActionBar?.title = session.title.ifEmpty { "Terminal" }

        sessionsAdapter.activeSessionId = session.id
        sessionsAdapter.notifyDataSetChanged()
    }

    private fun updateSessionList() {
        runOnUiThread {
            sessionsAdapter.notifyDataSetChanged()
        }
    }

    // Distro management

    private fun showDistroDialog() {
        val installedDistros = distroManager.getInstalledDistros()
        val allDistros = DistroManager.Distro.entries.toTypedArray()

        val items = allDistros.map { distro ->
            val status = if (distro in installedDistros) " [Installed]" else ""
            "${distro.displayName}$status"
        }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle("Linux Distributions")
            .setItems(items) { _, which ->
                val distro = allDistros[which]
                if (distro in installedDistros) {
                    showDistroActions(distro)
                } else {
                    installDistro(distro)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showDistroActions(distro: DistroManager.Distro) {
        val actions = arrayOf(
            "Launch ${distro.displayName}",
            "Set as Default",
            "Uninstall",
            "Run Setup Script"
        )

        AlertDialog.Builder(this)
            .setTitle(distro.displayName)
            .setItems(actions) { _, which ->
                when (which) {
                    0 -> launchDistro(distro)
                    1 -> {
                        preferences.defaultDistro = distro.id
                        preferences.useDistro = true
                        Toast.makeText(this, "${distro.displayName} set as default", Toast.LENGTH_SHORT).show()
                    }
                    2 -> confirmUninstall(distro)
                    3 -> runDistroSetup(distro)
                }
            }
            .show()
    }

    private fun launchDistro(distro: DistroManager.Distro) {
        val service = terminalService ?: return
        val cmd = distroManager.buildPRootCommand(distro)
        val session = service.createDistroSession(cmd)
        switchToSession(session)
    }

    private fun installDistro(distro: DistroManager.Distro) {
        val dialog = AlertDialog.Builder(this)
            .setTitle("Installing ${distro.displayName}")
            .setMessage("Downloading and extracting rootfs...\nThis may take a few minutes.")
            .setCancelable(false)
            .setNegativeButton("Cancel", null)
            .create()

        dialog.show()

        scope.launch {
            val installer = BootstrapInstaller(this@MainActivity)
            val success = installer.installDistro(distro, object : BootstrapInstaller.ProgressListener {
                override fun onProgress(stage: String, progress: Int, total: Int) {
                    runOnUiThread {
                        dialog.setMessage("$stage: $progress%")
                    }
                }

                override fun onStatusMessage(message: String) {
                    runOnUiThread {
                        dialog.setMessage(message)
                    }
                }

                override fun onError(error: String) {
                    runOnUiThread {
                        dialog.dismiss()
                        AlertDialog.Builder(this@MainActivity)
                            .setTitle("Installation Failed")
                            .setMessage(error)
                            .setPositiveButton("OK", null)
                            .show()
                    }
                }

                override fun onComplete() {
                    runOnUiThread {
                        dialog.dismiss()
                        AlertDialog.Builder(this@MainActivity)
                            .setTitle("Installation Complete")
                            .setMessage("${distro.displayName} is ready! Launch it now?")
                            .setPositiveButton("Launch") { _, _ -> launchDistro(distro) }
                            .setNegativeButton("Later", null)
                            .show()
                    }
                }
            })
        }
    }

    private fun confirmUninstall(distro: DistroManager.Distro) {
        AlertDialog.Builder(this)
            .setTitle("Uninstall ${distro.displayName}")
            .setMessage("This will delete the entire rootfs. Are you sure?")
            .setPositiveButton("Uninstall") { _, _ ->
                distroManager.uninstall(distro)
                Toast.makeText(this, "${distro.displayName} uninstalled", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun runDistroSetup(distro: DistroManager.Distro) {
        val service = terminalService ?: return
        val setupScript = distroManager.getSetupScript(distro)

        // Write setup script to a temp file
        val scriptFile = java.io.File(cacheDir, "setup.sh")
        scriptFile.writeText(setupScript)
        scriptFile.setExecutable(true)

        val cmd = distroManager.buildPRootCommand(distro, "/bin/bash /tmp/setup.sh")
        val session = service.createDistroSession(cmd)
        switchToSession(session)
    }

    // Menu

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                binding.drawerLayout.openDrawer(GravityCompat.START)
                true
            }
            R.id.action_new_session -> {
                createNewSession()
                true
            }
            R.id.action_toggle_keyboard -> {
                binding.terminalView.toggleSoftKeyboard()
                true
            }
            R.id.action_toggle_extra_keys -> {
                val visible = binding.extraKeys.visibility == View.VISIBLE
                binding.extraKeys.visibility = if (visible) View.GONE else View.VISIBLE
                preferences.extraKeysVisible = !visible
                true
            }
            R.id.action_paste -> {
                binding.terminalView.pasteFromClipboard()
                true
            }
            R.id.action_copy -> {
                binding.terminalView.copyToClipboard()
                true
            }
            R.id.action_font_increase -> {
                binding.terminalView.changeFontSize(2f)
                true
            }
            R.id.action_font_decrease -> {
                binding.terminalView.changeFontSize(-2f)
                true
            }
            R.id.action_file_manager -> {
                startActivity(Intent(this, FileManagerActivity::class.java))
                true
            }
            R.id.action_settings -> {
                startActivity(Intent(this, SettingsActivity::class.java))
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onBackPressed() {
        if (binding.drawerLayout.isDrawerOpen(GravityCompat.START)) {
            binding.drawerLayout.closeDrawer(GravityCompat.START)
        } else {
            when (preferences.backKeyBehavior) {
                "escape" -> currentSession?.writeToShell("\u001b".toByteArray())
                "back" -> super.onBackPressed()
                "confirm" -> {
                    AlertDialog.Builder(this)
                        .setTitle("Exit?")
                        .setMessage("Terminal sessions will continue running in the background.")
                        .setPositiveButton("Exit") { _, _ -> super.onBackPressed() }
                        .setNegativeButton("Cancel", null)
                        .show()
                }
            }
        }
    }

    // TerminalViewClient implementation

    override fun onScale(scale: Float): Boolean {
        val delta = if (scale > 1) 1f else -1f
        binding.terminalView.changeFontSize(delta)
        return true
    }

    override fun onLongPress(event: MotionEvent) {
        // Show context menu
        val actions = arrayOf("Copy", "Paste", "Select All", "Clear Screen")
        AlertDialog.Builder(this)
            .setItems(actions) { _, which ->
                when (which) {
                    0 -> binding.terminalView.copyToClipboard()
                    1 -> binding.terminalView.pasteFromClipboard()
                    2 -> { /* TODO: select all */ }
                    3 -> currentSession?.writeToShell("clear\n".toByteArray())
                }
            }
            .show()
    }

    override fun shouldUseCtrlKey(): Boolean = binding.extraKeys.isCtrlActive()
    override fun shouldUseAltKey(): Boolean = binding.extraKeys.isAltActive()

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean = false
    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean = false

    override fun onPaste(text: String) {
        binding.terminalView.paste(text)
    }

    // SessionsAdapter.SessionActionListener implementation

    override fun onSessionSelected(session: TerminalSession) {
        switchToSession(session)
        binding.drawerLayout.closeDrawer(GravityCompat.START)
    }

    override fun onSessionClose(session: TerminalSession) {
        val service = terminalService ?: return
        service.removeSession(session)

        if (session == currentSession) {
            val nextSession = service.sessions.lastOrNull()
            if (nextSession != null) {
                switchToSession(nextSession)
            } else {
                createNewSession()
            }
        }
        updateSessionList()
    }

    // ExtraKeysListener implementation

    override fun onExtraKeyPress(key: TerminalExtraKeys.ExtraKey) {
        val session = currentSession ?: return
        val sequence = key.sequence ?: return

        // Apply modifiers
        if (binding.extraKeys.isCtrlActive() && sequence.size == 1) {
            val c = sequence[0].toInt() and 0xFF
            if (c in 0x61..0x7A) { // a-z
                session.writeToShell(byteArrayOf((c - 0x60).toByte()))
                binding.extraKeys.resetModifiers()
                return
            }
        }

        if (binding.extraKeys.isAltActive() && sequence.isNotEmpty()) {
            val altSequence = ByteArray(sequence.size + 1)
            altSequence[0] = 0x1b
            System.arraycopy(sequence, 0, altSequence, 1, sequence.size)
            session.writeToShell(altSequence)
            binding.extraKeys.resetModifiers()
            return
        }

        session.writeToShell(sequence)
        binding.extraKeys.resetModifiers()
        binding.terminalView.scrollToBottom()
    }

    override fun onModifierToggle(modifier: TerminalExtraKeys.Modifier, active: Boolean) {
        // Update visual state handled by ExtraKeys
    }
}
