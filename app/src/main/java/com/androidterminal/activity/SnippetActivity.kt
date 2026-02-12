package com.androidterminal.activity

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.androidterminal.R
import com.androidterminal.snippets.SnippetManager

/**
 * Activity for managing command snippets.
 * Users can save frequently used commands and quickly insert them.
 * This is a unique feature not present in Termux.
 */
class SnippetActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyView: TextView
    private lateinit var snippetManager: SnippetManager
    private lateinit var adapter: SnippetAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_snippets)

        val toolbar = findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Command Snippets"

        snippetManager = SnippetManager(this)
        recyclerView = findViewById(R.id.recycler_snippets)
        emptyView = findViewById(R.id.text_empty)

        adapter = SnippetAdapter(
            onSnippetClick = { snippet ->
                // Return the command to the main activity
                val result = Intent()
                result.putExtra("snippet_command", snippet.command)
                setResult(RESULT_OK, result)
                finish()
            },
            onSnippetDelete = { snippet ->
                snippetManager.deleteSnippet(snippet.id)
                refreshList()
            }
        )

        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        findViewById<View>(R.id.fab_add_snippet).setOnClickListener {
            showAddSnippetDialog()
        }

        refreshList()

        // Add default snippets if empty
        if (snippetManager.getAllSnippets().isEmpty()) {
            addDefaultSnippets()
            refreshList()
        }
    }

    private fun refreshList() {
        val snippets = snippetManager.getAllSnippets()
        adapter.submitList(snippets)
        emptyView.visibility = if (snippets.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun showAddSnippetDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_snippet, null)
        val nameEdit = dialogView.findViewById<EditText>(R.id.edit_snippet_name)
        val commandEdit = dialogView.findViewById<EditText>(R.id.edit_snippet_command)
        val descEdit = dialogView.findViewById<EditText>(R.id.edit_snippet_description)

        AlertDialog.Builder(this)
            .setTitle("Add Snippet")
            .setView(dialogView)
            .setPositiveButton("Save") { _, _ ->
                val name = nameEdit.text.toString().trim()
                val command = commandEdit.text.toString().trim()
                val desc = descEdit.text.toString().trim()

                if (name.isNotEmpty() && command.isNotEmpty()) {
                    snippetManager.addSnippet(name, command, desc)
                    refreshList()
                } else {
                    Toast.makeText(this, "Name and command are required", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun addDefaultSnippets() {
        snippetManager.addSnippet("List files (detailed)", "ls -la", "List all files with details")
        snippetManager.addSnippet("Disk usage", "df -h", "Show disk space usage")
        snippetManager.addSnippet("Process list", "ps aux", "List running processes")
        snippetManager.addSnippet("Network info", "ifconfig 2>/dev/null || ip addr", "Show network interfaces")
        snippetManager.addSnippet("Memory info", "cat /proc/meminfo | head -5", "Show memory information")
        snippetManager.addSnippet("System info", "uname -a", "Show system information")
        snippetManager.addSnippet("Find files", "find . -name '*.txt' -type f", "Find text files recursively")
        snippetManager.addSnippet("Clear terminal", "clear", "Clear the terminal screen")
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    /**
     * RecyclerView adapter for snippets.
     */
    private class SnippetAdapter(
        private val onSnippetClick: (SnippetManager.Snippet) -> Unit,
        private val onSnippetDelete: (SnippetManager.Snippet) -> Unit
    ) : RecyclerView.Adapter<SnippetAdapter.ViewHolder>() {

        private var snippets: List<SnippetManager.Snippet> = emptyList()

        fun submitList(list: List<SnippetManager.Snippet>) {
            snippets = list
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_snippet, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.bind(snippets[position])
        }

        override fun getItemCount(): Int = snippets.size

        inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val nameView: TextView = itemView.findViewById(R.id.text_snippet_name)
            private val commandView: TextView = itemView.findViewById(R.id.text_snippet_command)
            private val descView: TextView = itemView.findViewById(R.id.text_snippet_description)
            private val deleteButton: ImageButton = itemView.findViewById(R.id.btn_delete_snippet)

            fun bind(snippet: SnippetManager.Snippet) {
                nameView.text = snippet.name
                commandView.text = "$ ${snippet.command}"
                descView.text = snippet.description
                descView.visibility = if (snippet.description.isNotEmpty()) View.VISIBLE else View.GONE

                itemView.setOnClickListener { onSnippetClick(snippet) }
                deleteButton.setOnClickListener { onSnippetDelete(snippet) }
            }
        }
    }
}
