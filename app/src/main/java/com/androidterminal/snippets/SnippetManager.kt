package com.androidterminal.snippets

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File
import java.util.UUID

/**
 * Manages command snippets - saved commands that can be quickly inserted.
 * Stores snippets as JSON in the app's files directory.
 */
class SnippetManager(private val context: Context) {

    data class Snippet(
        val id: String = UUID.randomUUID().toString(),
        val name: String,
        val command: String,
        val description: String = "",
        val category: String = "General",
        val createdAt: Long = System.currentTimeMillis()
    )

    private val gson = Gson()
    private val snippetFile = File(context.filesDir, "snippets.json")

    /**
     * Gets all saved snippets.
     */
    fun getAllSnippets(): List<Snippet> {
        if (!snippetFile.exists()) return emptyList()
        return try {
            val json = snippetFile.readText()
            val type = object : TypeToken<List<Snippet>>() {}.type
            gson.fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Adds a new snippet.
     */
    fun addSnippet(name: String, command: String, description: String = "", category: String = "General"): Snippet {
        val snippet = Snippet(
            name = name,
            command = command,
            description = description,
            category = category
        )
        val snippets = getAllSnippets().toMutableList()
        snippets.add(snippet)
        saveSnippets(snippets)
        return snippet
    }

    /**
     * Deletes a snippet by ID.
     */
    fun deleteSnippet(id: String) {
        val snippets = getAllSnippets().toMutableList()
        snippets.removeAll { it.id == id }
        saveSnippets(snippets)
    }

    /**
     * Updates an existing snippet.
     */
    fun updateSnippet(snippet: Snippet) {
        val snippets = getAllSnippets().toMutableList()
        val index = snippets.indexOfFirst { it.id == snippet.id }
        if (index >= 0) {
            snippets[index] = snippet
            saveSnippets(snippets)
        }
    }

    /**
     * Searches snippets by name or command.
     */
    fun search(query: String): List<Snippet> {
        val lowerQuery = query.lowercase()
        return getAllSnippets().filter {
            it.name.lowercase().contains(lowerQuery) ||
            it.command.lowercase().contains(lowerQuery) ||
            it.description.lowercase().contains(lowerQuery)
        }
    }

    /**
     * Gets snippets by category.
     */
    fun getByCategory(category: String): List<Snippet> {
        return getAllSnippets().filter { it.category == category }
    }

    /**
     * Gets all categories.
     */
    fun getCategories(): List<String> {
        return getAllSnippets().map { it.category }.distinct().sorted()
    }

    private fun saveSnippets(snippets: List<Snippet>) {
        val json = gson.toJson(snippets)
        snippetFile.writeText(json)
    }
}
