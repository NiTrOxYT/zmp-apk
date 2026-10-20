package com.sitelink.manager.ui.screens.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.sitelink.manager.data.config.ServerConfigManager
import com.sitelink.manager.data.db.AppDatabase
import com.sitelink.manager.data.db.ConnectionLogEntity
import com.sitelink.manager.data.db.TrustedHostKeyEntity
import com.sitelink.manager.data.keystore.KeyStoreManager
import com.sitelink.manager.data.repository.SshRepository
import com.sitelink.manager.domain.model.SshDiagnosticError
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val database: AppDatabase,
    private val keyStoreManager: KeyStoreManager,
    private val serverConfigManager: ServerConfigManager,
    private val sshRepository: SshRepository
) : ViewModel() {

    private val _hostInput = MutableStateFlow(serverConfigManager.getHost())
    val hostInput: StateFlow<String> = _hostInput.asStateFlow()

    private val _portInput = MutableStateFlow(serverConfigManager.getPort().toString())
    val portInput: StateFlow<String> = _portInput.asStateFlow()

    private val _userInput = MutableStateFlow(serverConfigManager.getUsername())
    val userInput: StateFlow<String> = _userInput.asStateFlow()

    private val _passwordInput = MutableStateFlow(serverConfigManager.getPassword())
    val passwordInput: StateFlow<String> = _passwordInput.asStateFlow()

    private val _autoAcceptHostKey = MutableStateFlow(serverConfigManager.isAutoAcceptHostKey())
    val autoAcceptHostKey: StateFlow<Boolean> = _autoAcceptHostKey.asStateFlow()

    private val _hasSavedPassword = MutableStateFlow(serverConfigManager.hasSavedPassword())
    val hasSavedPassword: StateFlow<Boolean> = _hasSavedPassword.asStateFlow()

    private val _saveStatus = MutableStateFlow<String?>(null)
    val saveStatus: StateFlow<String?> = _saveStatus.asStateFlow()

    private val _testStatus = MutableStateFlow<String?>(null)
    val testStatus: StateFlow<String?> = _testStatus.asStateFlow()

    private val _isTesting = MutableStateFlow(false)
    val isTesting: StateFlow<Boolean> = _isTesting.asStateFlow()

    private val _hasKeystoreKey = MutableStateFlow(keyStoreManager.hasKeyPair())
    val hasKeystoreKey: StateFlow<Boolean> = _hasKeystoreKey.asStateFlow()

    private val _publicKeyString = MutableStateFlow(keyStoreManager.getOpenSshPublicKey())
    val publicKeyString: StateFlow<String?> = _publicKeyString.asStateFlow()

    val trustedHostKeys: StateFlow<List<TrustedHostKeyEntity>> = database.trustedHostKeyDao()
        .getAllTrustedKeysFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val connectionLogs: StateFlow<List<ConnectionLogEntity>> = database.connectionLogDao()
        .getRecentLogsFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun onHostChange(host: String) {
        _hostInput.value = host
        _saveStatus.value = null
    }

    fun onPortChange(port: String) {
        _portInput.value = port
        _saveStatus.value = null
    }

    fun onUserChange(user: String) {
        _userInput.value = user
        _saveStatus.value = null
    }

    fun onPasswordChange(password: String) {
        _passwordInput.value = password
        _saveStatus.value = null
    }

    fun onAutoAcceptChange(autoAccept: Boolean) {
        _autoAcceptHostKey.value = autoAccept
        _saveStatus.value = null
    }

    fun saveServerSettings() {
        val host = _hostInput.value.trim()
        val port = _portInput.value.toIntOrNull() ?: 22
        val user = _userInput.value.trim()
        val pass = _passwordInput.value

        serverConfigManager.saveConfig(
            host = host,
            port = port,
            username = user,
            password = pass,
            autoAcceptHostKey = _autoAcceptHostKey.value
        )
        _hasSavedPassword.value = serverConfigManager.hasSavedPassword()
        _saveStatus.value = "Office SSH credentials encrypted with Android Keystore and saved. SiteLink will auto-authenticate on all connections."
    }

    fun changePassword(newPassword: String) {
        serverConfigManager.updatePassword(newPassword)
        _passwordInput.value = newPassword
        _hasSavedPassword.value = newPassword.isNotEmpty()
        _saveStatus.value = "Password updated securely in Android Keystore."
    }

    fun deleteSavedCredentials() {
        serverConfigManager.deleteSavedCredentials()
        _passwordInput.value = ""
        _hasSavedPassword.value = false
        _saveStatus.value = "Saved credentials deleted. SiteLink will prompt for credentials when connecting."
    }

    fun testConnection() {
        val host = _hostInput.value.trim()
        val port = _portInput.value.toIntOrNull() ?: 22
        val user = _userInput.value.trim()
        val pass = _passwordInput.value.ifEmpty { serverConfigManager.getPassword() }

        viewModelScope.launch {
            _isTesting.value = true
            _testStatus.value = "Testing SSH connection to $user@$host:$port..."

            val res = sshRepository.testConnection(
                host = host,
                port = port,
                username = user,
                password = pass
            )

            _isTesting.value = false
            if (res.isSuccess) {
                _testStatus.value = "SUCCESS: ${res.getOrNull()}"
            } else {
                val diag = SshDiagnosticError.classify(res.exceptionOrNull() ?: Exception("Unknown error"), host, port)
                _testStatus.value = "FAILED [${diag.category.label}]: ${diag.diagnosticMessage}"
            }
        }
    }

    fun generateKeystoreKeyPair() {
        keyStoreManager.generateRsaKeyPair()
        _hasKeystoreKey.value = keyStoreManager.hasKeyPair()
        _publicKeyString.value = keyStoreManager.getOpenSshPublicKey()
    }

    fun deleteKeystoreKey() {
        keyStoreManager.deleteKey()
        _hasKeystoreKey.value = false
        _publicKeyString.value = null
    }

    fun copyPublicKeyToClipboard(context: Context) {
        val key = _publicKeyString.value ?: return
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("SiteLink SSH Public Key", key)
        clipboard.setPrimaryClip(clip)
    }

    fun clearTrustedHostKeys() {
        viewModelScope.launch {
            trustedHostKeys.value.forEach {
                database.trustedHostKeyDao().deleteTrustedKey(it.hostPortKey)
            }
        }
    }

    fun clearConnectionLogs() {
        viewModelScope.launch {
            database.connectionLogDao().clearLogs()
        }
    }

    class Factory(
        private val database: AppDatabase,
        private val keyStoreManager: KeyStoreManager,
        private val serverConfigManager: ServerConfigManager,
        private val sshRepository: SshRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return SettingsViewModel(database, keyStoreManager, serverConfigManager, sshRepository) as T
        }
    }
}
