package com.roshank8s.androidterminal.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.roshank8s.androidterminal.R
import com.roshank8s.androidterminal.databinding.ActivityFileManagerBinding
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Built-in file manager for browsing and managing files within the terminal environment.
 *
 * Features:
 * - Browse filesystem (home directory, distro rootfs, sdcard)
 * - Create files and directories
 * - Delete files
 * - View file properties
 * - Open files in terminal (cat, edit)
 */
class FileManagerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityFileManagerBinding
    private lateinit var filesAdapter: FilesAdapter
    private var currentPath: File = File("/")
    private var files = mutableListOf<FileItem>()

    data class FileItem(
        val file: File,
        val name: String,
        val isDirectory: Boolean,
        val size: Long,
        val lastModified: Long,
        val isReadable: Boolean,
        val isWritable: Boolean,
        val isExecutable: Boolean
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityFileManagerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.fileManagerToolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        filesAdapter = FilesAdapter(files) { fileItem ->
            if (fileItem.isDirectory) {
                navigateTo(fileItem.file)
            } else {
                showFileActions(fileItem)
            }
        }

        binding.filesList.apply {
            layoutManager = LinearLayoutManager(this@FileManagerActivity)
            adapter = filesAdapter
        }

        binding.fabCreateFile.setOnClickListener {
            showCreateDialog()
        }

        // Navigate to app home directory
        val homeDir = File(filesDir, "home")
        if (homeDir.exists()) {
            navigateTo(homeDir)
        } else {
            navigateTo(filesDir)
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                onBackPressed()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onBackPressed() {
        val parent = currentPath.parentFile
        if (parent != null && parent.canRead()) {
            navigateTo(parent)
        } else {
            super.onBackPressed()
        }
    }

    private fun navigateTo(directory: File) {
        currentPath = directory
        supportActionBar?.title = directory.name.ifEmpty { "/" }
        binding.pathText.text = directory.absolutePath

        files.clear()

        try {
            val children = directory.listFiles()?.toList() ?: emptyList()
            val sorted = children.sortedWith(compareBy<File> { !it.isDirectory }.thenBy { it.name.lowercase() })

            for (file in sorted) {
                files.add(FileItem(
                    file = file,
                    name = file.name,
                    isDirectory = file.isDirectory,
                    size = if (file.isFile) file.length() else 0,
                    lastModified = file.lastModified(),
                    isReadable = file.canRead(),
                    isWritable = file.canWrite(),
                    isExecutable = file.canExecute()
                ))
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Cannot read directory: ${e.message}", Toast.LENGTH_SHORT).show()
        }

        filesAdapter.notifyDataSetChanged()
    }

    private fun showFileActions(fileItem: FileItem) {
        val actions = mutableListOf("View Info")
        if (fileItem.isReadable) actions.add("View Content")
        if (fileItem.isWritable) actions.add("Delete")

        AlertDialog.Builder(this)
            .setTitle(fileItem.name)
            .setItems(actions.toTypedArray()) { _, which ->
                when (actions[which]) {
                    "View Info" -> showFileInfo(fileItem)
                    "View Content" -> viewFileContent(fileItem)
                    "Delete" -> confirmDelete(fileItem)
                }
            }
            .show()
    }

    private fun showFileInfo(fileItem: FileItem) {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        val info = buildString {
            appendLine("Name: ${fileItem.name}")
            appendLine("Path: ${fileItem.file.absolutePath}")
            appendLine("Size: ${formatSize(fileItem.size)}")
            appendLine("Modified: ${dateFormat.format(Date(fileItem.lastModified))}")
            appendLine("Readable: ${fileItem.isReadable}")
            appendLine("Writable: ${fileItem.isWritable}")
            appendLine("Executable: ${fileItem.isExecutable}")
        }

        AlertDialog.Builder(this)
            .setTitle(fileItem.name)
            .setMessage(info)
            .setPositiveButton("OK", null)
            .show()
    }

    private fun viewFileContent(fileItem: FileItem) {
        try {
            val content = fileItem.file.readText(Charsets.UTF_8)
            val displayContent = if (content.length > 10000) {
                content.substring(0, 10000) + "\n\n[Truncated...]"
            } else content

            AlertDialog.Builder(this)
                .setTitle(fileItem.name)
                .setMessage(displayContent)
                .setPositiveButton("OK", null)
                .show()
        } catch (e: Exception) {
            Toast.makeText(this, "Cannot read file: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun confirmDelete(fileItem: FileItem) {
        AlertDialog.Builder(this)
            .setTitle("Delete ${fileItem.name}?")
            .setMessage("This action cannot be undone.")
            .setPositiveButton("Delete") { _, _ ->
                if (fileItem.file.deleteRecursively()) {
                    navigateTo(currentPath) // Refresh
                } else {
                    Toast.makeText(this, "Failed to delete", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showCreateDialog() {
        val options = arrayOf("New File", "New Directory")
        AlertDialog.Builder(this)
            .setTitle("Create")
            .setItems(options) { _, which ->
                val title = if (which == 0) "New File" else "New Directory"
                val editText = android.widget.EditText(this)
                editText.hint = "Name"

                AlertDialog.Builder(this)
                    .setTitle(title)
                    .setView(editText)
                    .setPositiveButton("Create") { _, _ ->
                        val name = editText.text.toString()
                        if (name.isNotEmpty()) {
                            val newFile = File(currentPath, name)
                            val success = if (which == 0) newFile.createNewFile() else newFile.mkdirs()
                            if (success) {
                                navigateTo(currentPath)
                            } else {
                                Toast.makeText(this, "Failed to create", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
            .show()
    }

    private fun formatSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "${bytes / 1024} KB"
            bytes < 1024 * 1024 * 1024 -> "${bytes / (1024 * 1024)} MB"
            else -> "${bytes / (1024 * 1024 * 1024)} GB"
        }
    }

    // Files list adapter
    private class FilesAdapter(
        private val files: List<FileItem>,
        private val onClick: (FileItem) -> Unit
    ) : RecyclerView.Adapter<FilesAdapter.FileViewHolder>() {

        class FileViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val icon: ImageView = view.findViewById(R.id.file_icon)
            val name: TextView = view.findViewById(R.id.file_name)
            val info: TextView = view.findViewById(R.id.file_info)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FileViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_file, parent, false)
            return FileViewHolder(view)
        }

        override fun onBindViewHolder(holder: FileViewHolder, position: Int) {
            val item = files[position]
            holder.name.text = item.name
            holder.icon.setImageResource(
                if (item.isDirectory) R.drawable.ic_folder else R.drawable.ic_file
            )

            val dateFormat = SimpleDateFormat("MMM dd HH:mm", Locale.US)
            holder.info.text = if (item.isDirectory) {
                "Directory | ${dateFormat.format(Date(item.lastModified))}"
            } else {
                "${formatSize(item.size)} | ${dateFormat.format(Date(item.lastModified))}"
            }

            holder.itemView.setOnClickListener { onClick(item) }
        }

        override fun getItemCount(): Int = files.size

        companion object {
            fun formatSize(bytes: Long): String {
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
