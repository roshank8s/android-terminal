package com.androidterminal.activity

import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.androidterminal.R
import com.androidterminal.utils.ShellEnvironment
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Built-in file manager with GUI for browsing, creating, deleting,
 * renaming, and viewing files.
 * This is an enhanced feature not present in standard Termux.
 */
class FileManagerActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var pathTextView: TextView
    private lateinit var emptyTextView: TextView
    private lateinit var adapter: FileListAdapter

    private var currentPath: File = File("/")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_file_manager)

        val toolbar = findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "File Manager"

        recyclerView = findViewById(R.id.recycler_files)
        pathTextView = findViewById(R.id.text_current_path)
        emptyTextView = findViewById(R.id.text_empty)

        recyclerView.layoutManager = LinearLayoutManager(this)
        adapter = FileListAdapter(
            onFileClick = { file -> onFileClicked(file) },
            onFileLongClick = { file, view -> showFileContextMenu(file, view) }
        )
        recyclerView.adapter = adapter

        // Start at home directory
        currentPath = File(ShellEnvironment.getHomeDirectory(this))
        navigateTo(currentPath)

        // FAB for creating new file/folder
        findViewById<View>(R.id.fab_create).setOnClickListener {
            showCreateDialog()
        }
    }

    private fun navigateTo(directory: File) {
        currentPath = directory
        pathTextView.text = directory.absolutePath

        val files = directory.listFiles()?.toList()?.sortedWith(
            compareBy<File> { !it.isDirectory }.thenBy { it.name.lowercase() }
        ) ?: emptyList()

        adapter.submitList(files)
        emptyTextView.visibility = if (files.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun onFileClicked(file: File) {
        if (file.isDirectory) {
            navigateTo(file)
        } else {
            // Open file in a simple text viewer
            if (file.length() < 1024 * 1024) { // < 1MB
                try {
                    val content = file.readText()
                    showFileContent(file.name, content)
                } catch (e: Exception) {
                    Toast.makeText(this, "Cannot read file: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(this, "File too large to preview", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showFileContent(name: String, content: String) {
        AlertDialog.Builder(this)
            .setTitle(name)
            .setMessage(content)
            .setPositiveButton("Close", null)
            .setNeutralButton("Copy Path") { _, _ ->
                val clipboard = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
                clipboard.setPrimaryClip(
                    android.content.ClipData.newPlainText("path", File(currentPath, name).absolutePath)
                )
            }
            .show()
    }

    private fun showFileContextMenu(file: File, anchor: View) {
        PopupMenu(this, anchor).apply {
            menu.add("Copy Path")
            menu.add("Rename")
            menu.add("Delete")
            if (file.isFile) {
                menu.add("Make Executable")
            }

            setOnMenuItemClickListener { item ->
                when (item.title) {
                    "Copy Path" -> {
                        val clipboard = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        clipboard.setPrimaryClip(
                            android.content.ClipData.newPlainText("path", file.absolutePath)
                        )
                        Toast.makeText(this@FileManagerActivity, "Path copied", Toast.LENGTH_SHORT).show()
                    }
                    "Rename" -> showRenameDialog(file)
                    "Delete" -> showDeleteConfirmation(file)
                    "Make Executable" -> {
                        file.setExecutable(true)
                        Toast.makeText(this@FileManagerActivity, "Made executable", Toast.LENGTH_SHORT).show()
                        navigateTo(currentPath)
                    }
                }
                true
            }
            show()
        }
    }

    private fun showCreateDialog() {
        val options = arrayOf("New File", "New Folder")
        AlertDialog.Builder(this)
            .setTitle("Create")
            .setItems(options) { _, which ->
                val editText = EditText(this)
                editText.hint = if (which == 0) "filename.txt" else "folder_name"

                AlertDialog.Builder(this)
                    .setTitle(if (which == 0) "New File" else "New Folder")
                    .setView(editText)
                    .setPositiveButton("Create") { _, _ ->
                        val name = editText.text.toString().trim()
                        if (name.isNotEmpty()) {
                            val newFile = File(currentPath, name)
                            try {
                                if (which == 0) {
                                    newFile.createNewFile()
                                } else {
                                    newFile.mkdir()
                                }
                                navigateTo(currentPath)
                            } catch (e: Exception) {
                                Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
            .show()
    }

    private fun showRenameDialog(file: File) {
        val editText = EditText(this)
        editText.setText(file.name)

        AlertDialog.Builder(this)
            .setTitle("Rename")
            .setView(editText)
            .setPositiveButton("Rename") { _, _ ->
                val newName = editText.text.toString().trim()
                if (newName.isNotEmpty()) {
                    val newFile = File(file.parent, newName)
                    if (file.renameTo(newFile)) {
                        navigateTo(currentPath)
                    } else {
                        Toast.makeText(this, "Rename failed", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showDeleteConfirmation(file: File) {
        AlertDialog.Builder(this)
            .setTitle("Delete")
            .setMessage("Delete ${file.name}?")
            .setPositiveButton("Delete") { _, _ ->
                if (file.isDirectory) {
                    file.deleteRecursively()
                } else {
                    file.delete()
                }
                navigateTo(currentPath)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    @Deprecated("Use onBackPressedDispatcher")
    override fun onBackPressed() {
        val parent = currentPath.parentFile
        if (parent != null && parent.canRead()) {
            navigateTo(parent)
        } else {
            super.onBackPressed()
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        val parent = currentPath.parentFile
        if (parent != null && parent.canRead()) {
            navigateTo(parent)
            return true
        }
        finish()
        return true
    }

    /**
     * RecyclerView adapter for file list.
     */
    private class FileListAdapter(
        private val onFileClick: (File) -> Unit,
        private val onFileLongClick: (File, View) -> Unit
    ) : RecyclerView.Adapter<FileListAdapter.ViewHolder>() {

        private var files: List<File> = emptyList()
        private val dateFormat = SimpleDateFormat("MMM dd yyyy HH:mm", Locale.getDefault())

        fun submitList(newFiles: List<File>) {
            files = newFiles
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_file, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val file = files[position]
            holder.bind(file)
        }

        override fun getItemCount(): Int = files.size

        inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val iconView: ImageView = itemView.findViewById(R.id.icon_file)
            private val nameView: TextView = itemView.findViewById(R.id.text_file_name)
            private val detailView: TextView = itemView.findViewById(R.id.text_file_detail)

            fun bind(file: File) {
                nameView.text = file.name
                iconView.setImageResource(
                    if (file.isDirectory) R.drawable.ic_folder
                    else R.drawable.ic_file
                )

                val size = if (file.isFile) formatSize(file.length()) else ""
                val date = dateFormat.format(Date(file.lastModified()))
                val perms = buildString {
                    if (file.canRead()) append("r") else append("-")
                    if (file.canWrite()) append("w") else append("-")
                    if (file.canExecute()) append("x") else append("-")
                }
                detailView.text = "$perms  $size  $date"

                itemView.setOnClickListener { onFileClick(file) }
                itemView.setOnLongClickListener {
                    onFileLongClick(file, it)
                    true
                }
            }

            private fun formatSize(bytes: Long): String {
                return when {
                    bytes < 1024 -> "$bytes B"
                    bytes < 1024 * 1024 -> "${bytes / 1024} KB"
                    bytes < 1024 * 1024 * 1024 -> "${bytes / (1024 * 1024)} MB"
                    else -> "${bytes / (1024 * 1024 * 1024)} GB"
                }
            }
        }
    }
}
