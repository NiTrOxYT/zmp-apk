package com.sitelink.manager.data.config

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

const val DEFAULT_OFFICE_HOST = "111.93.205.187"
const val DEFAULT_OFFICE_PORT = 22
const val DEFAULT_OFFICE_USER = "sourik"
const val DEFAULT_OFFICE_PASSWORD = "s0urik@@"

data class ServerConfig(
    val host: String = DEFAULT_OFFICE_HOST,
    val port: Int = DEFAULT_OFFICE_PORT,
    val username: String = DEFAULT_OFFICE_USER,
    val password: String = DEFAULT_OFFICE_PASSWORD,
    val autoAcceptHostKey: Boolean = false
)

class ServerConfigManager(
    context: Context? = null,
    customPrefs: SharedPreferences? = null
) {

    private val prefs: SharedPreferences = customPrefs ?: run {
        val nonNullContext = requireNotNull(context) { "Context must be provided if customPrefs is null" }
        try {
            val masterKey = MasterKey.Builder(nonNullContext)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            EncryptedSharedPreferences.create(
                nonNullContext,
                "sitelink_secure_server_config",
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (_: Exception) {
            // Fallback to standard private preferences if Keystore encounters device issues
            nonNullContext.getSharedPreferences("sitelink_server_config", Context.MODE_PRIVATE)
        }
    }

    private val _config = MutableStateFlow(loadConfig())
    val config: StateFlow<ServerConfig> = _config.asStateFlow()

    private fun loadConfig(): ServerConfig {
        val password = if (prefs.contains(KEY_PASS)) {
            prefs.getString(KEY_PASS, "") ?: ""
        } else {
            DEFAULT_OFFICE_PASSWORD
        }
        return ServerConfig(
            host = prefs.getString(KEY_HOST, DEFAULT_OFFICE_HOST) ?: DEFAULT_OFFICE_HOST,
            port = prefs.getInt(KEY_PORT, DEFAULT_OFFICE_PORT),
            username = prefs.getString(KEY_USER, DEFAULT_OFFICE_USER) ?: DEFAULT_OFFICE_USER,
            password = password,
            autoAcceptHostKey = prefs.getBoolean(KEY_AUTO_ACCEPT, false)
        )
    }

    fun saveConfig(
        host: String,
        port: Int,
        username: String,
        password: String,
        autoAcceptHostKey: Boolean = false
    ) {
        val cleanHost = host.trim()
        val cleanUser = username.trim()
        prefs.edit()
            .putString(KEY_HOST, cleanHost)
            .putInt(KEY_PORT, port)
            .putString(KEY_USER, cleanUser)
            .putString(KEY_PASS, password)
            .putBoolean(KEY_AUTO_ACCEPT, autoAcceptHostKey)
            .apply()

        _config.value = ServerConfig(
            host = cleanHost,
            port = port,
            username = cleanUser,
            password = password,
            autoAcceptHostKey = autoAcceptHostKey
        )
    }

    fun updatePassword(newPassword: String) {
        prefs.edit()
            .putString(KEY_PASS, newPassword)
            .apply()
        _config.value = _config.value.copy(password = newPassword)
    }

    fun deleteSavedCredentials() {
        prefs.edit()
            .putString(KEY_PASS, "")
            .apply()
        _config.value = _config.value.copy(password = "")
    }

    fun getHost(): String = _config.value.host
    fun getPort(): Int = _config.value.port
    fun getUsername(): String = _config.value.username
    fun getPassword(): String = _config.value.password
    fun isAutoAcceptHostKey(): Boolean = _config.value.autoAcceptHostKey
    fun hasSavedPassword(): Boolean = _config.value.password.isNotEmpty()

    companion object {
        const val DEFAULT_HOST = "111.93.205.187"
        const val DEFAULT_PORT = 22
        const val DEFAULT_USER = "sourik"
        const val DEFAULT_PASSWORD = "s0urik@@"

        private const val KEY_HOST = "office_server_host"
        private const val KEY_PORT = "office_server_port"
        private const val KEY_USER = "office_server_user"
        private const val KEY_PASS = "office_server_password"
        private const val KEY_AUTO_ACCEPT = "office_server_auto_accept_host"
    }
}
