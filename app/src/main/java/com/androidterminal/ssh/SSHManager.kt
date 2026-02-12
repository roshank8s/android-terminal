package com.androidterminal.ssh

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File
import java.util.UUID

/**
 * Manages SSH connection profiles and known hosts.
 * Provides functionality for saving and managing SSH connections.
 * Actual SSH connections are executed via the terminal using the ssh command.
 */
class SSHManager(private val context: Context) {

    data class SSHProfile(
        val id: String = UUID.randomUUID().toString(),
        val name: String,
        val host: String,
        val port: Int = 22,
        val username: String,
        val authMethod: AuthMethod = AuthMethod.PASSWORD,
        val keyPath: String = "",
        val createdAt: Long = System.currentTimeMillis()
    )

    enum class AuthMethod {
        PASSWORD,
        KEY
    }

    private val gson = Gson()
    private val profileFile = File(context.filesDir, "ssh_profiles.json")

    /**
     * Gets all SSH profiles.
     */
    fun getAllProfiles(): List<SSHProfile> {
        if (!profileFile.exists()) return emptyList()
        return try {
            val json = profileFile.readText()
            val type = object : TypeToken<List<SSHProfile>>() {}.type
            gson.fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Adds a new SSH profile.
     */
    fun addProfile(profile: SSHProfile): SSHProfile {
        val profiles = getAllProfiles().toMutableList()
        profiles.add(profile)
        saveProfiles(profiles)
        return profile
    }

    /**
     * Deletes a profile.
     */
    fun deleteProfile(id: String) {
        val profiles = getAllProfiles().toMutableList()
        profiles.removeAll { it.id == id }
        saveProfiles(profiles)
    }

    /**
     * Builds the SSH command for a profile.
     */
    fun buildSSHCommand(profile: SSHProfile): String {
        return buildString {
            append("ssh")
            if (profile.port != 22) {
                append(" -p ${profile.port}")
            }
            if (profile.authMethod == AuthMethod.KEY && profile.keyPath.isNotEmpty()) {
                append(" -i ${profile.keyPath}")
            }
            append(" ${profile.username}@${profile.host}")
        }
    }

    /**
     * Generates an SSH key pair.
     * Returns the command to run in the terminal.
     */
    fun generateKeyPairCommand(keyName: String = "id_rsa", keyType: String = "ed25519"): String {
        val sshDir = File(context.filesDir, "home/.ssh")
        sshDir.mkdirs()
        val keyPath = File(sshDir, keyName).absolutePath
        return "ssh-keygen -t $keyType -f $keyPath -N ''"
    }

    private fun saveProfiles(profiles: List<SSHProfile>) {
        val json = gson.toJson(profiles)
        profileFile.writeText(json)
    }
}
