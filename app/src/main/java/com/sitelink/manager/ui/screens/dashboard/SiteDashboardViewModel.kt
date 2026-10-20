package com.sitelink.manager.ui.screens.dashboard

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.sitelink.manager.data.keystore.KeyStoreManager
import com.sitelink.manager.data.repository.SiteRepository
import com.sitelink.manager.data.repository.SshRepository
import com.sitelink.manager.data.ssh.SshAuthInfo
import com.sitelink.manager.domain.model.HostVerificationResult
import com.sitelink.manager.domain.model.Site
import com.sitelink.manager.domain.model.SshDiagnosticError
import com.sitelink.manager.domain.model.SshSessionState
import com.sitelink.manager.domain.model.TunnelInfo
import com.sitelink.manager.domain.validation.InputValidator
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface DashboardNavigationEvent {
    data class OpenBrowser(val url: String) : DashboardNavigationEvent
    data class NavigateToTerminal(val siteId: Long, val type: String, val port: Int) : DashboardNavigationEvent
}

class SiteDashboardViewModel(
    private val siteRepository: SiteRepository,
    val sshRepository: SshRepository,
    val keyStoreManager: KeyStoreManager,
    private val siteId: Long
) : ViewModel() {

    private val _site = MutableStateFlow<Site?>(null)
    val site: StateFlow<Site?> = _site.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _isAuthError = MutableStateFlow(false)
    val isAuthError: StateFlow<Boolean> = _isAuthError.asStateFlow()

    private val _showAuthDialog = MutableStateFlow(false)
    val showAuthDialog: StateFlow<Boolean> = _showAuthDialog.asStateFlow()

    private val _navigationEvent = MutableSharedFlow<DashboardNavigationEvent>()
    val navigationEvent: SharedFlow<DashboardNavigationEvent> = _navigationEvent.asSharedFlow()

    val sessionState: StateFlow<SshSessionState> = sshRepository.sessionState
    val activeTunnels: StateFlow<Map<String, TunnelInfo>> = sshRepository.activeTunnels
    val hostVerificationEvent: StateFlow<HostVerificationResult?> = sshRepository.hostVerificationEvent

    private var pendingAction: (() -> Unit)? = null

    init {
        loadSite()
    }

    private fun loadSite() {
        viewModelScope.launch {
            _site.value = siteRepository.getSiteById(siteId)
        }
    }

    /**
     * Seamlessly opens ZMP tunnel:
     * If not connected to office server, automatically connects using saved credentials.
     * Never asks for password when configured!
     */
    fun openZmp(port: Int, context: Context? = null) {
        val currentSite = _site.value ?: return

        if (!sshRepository.isConnected()) {
            pendingAction = { openZmp(port, context) }
            if (sshRepository.hasSavedPassword() || keyStoreManager.hasKeyPair()) {
                viewModelScope.launch {
                    _isLoading.value = true
                    _statusMessage.value = "Auto-connecting to Office SSH Server..."
                    _errorMessage.value = null
                    _isAuthError.value = false

                    val connRes = sshRepository.connectToOfficeWithSavedCredentials()
                    if (connRes.isSuccess) {
                        openTunnelInternal(currentSite, port, context)
                    } else {
                        _isLoading.value = false
                        val host = sshRepository.serverConfigManager.getHost()
                        val p = sshRepository.serverConfigManager.getPort()
                        val diag = SshDiagnosticError.classify(connRes.exceptionOrNull() ?: Exception("Connection failed"), host, p)
                        _errorMessage.value = diag.getFormattedError()
                        _isAuthError.value = (diag is SshDiagnosticError.Auth)
                    }
                }
            } else {
                _showAuthDialog.value = true
            }
            return
        }

        openTunnelInternal(currentSite, port, context)
    }

    private fun openTunnelInternal(site: Site, port: Int, context: Context?) {
        viewModelScope.launch {
            _isLoading.value = true
            _statusMessage.value = "Opening ZMP tunnel to ${site.ipAddress}:$port..."
            _errorMessage.value = null

            val result = sshRepository.openZmpTunnel(site, port, context)
            _isLoading.value = false

            if (result.isSuccess) {
                val tunnel = result.getOrNull()!!
                _statusMessage.value = "Tunnel active on 127.0.0.1:${tunnel.localPort}. Launching portal..."
                _navigationEvent.emit(DashboardNavigationEvent.OpenBrowser(tunnel.localUrl))
            } else {
                val diag = SshDiagnosticError.classify(result.exceptionOrNull() ?: Exception("Tunnel failed"), site.ipAddress, port)
                _errorMessage.value = diag.getFormattedError()
            }
        }
    }

    /**
     * Seamlessly opens VM/BM terminal:
     * If not connected, connects in the background using saved credentials without asking.
     */
    fun openTerminal(type: String, port: Int) {
        val currentSite = _site.value ?: return

        if (!sshRepository.isConnected()) {
            pendingAction = { openTerminal(type, port) }
            if (sshRepository.hasSavedPassword() || keyStoreManager.hasKeyPair()) {
                viewModelScope.launch {
                    _isLoading.value = true
                    _statusMessage.value = "Auto-connecting to Office SSH Server..."
                    _errorMessage.value = null
                    _isAuthError.value = false

                    val connRes = sshRepository.connectToOfficeWithSavedCredentials()
                    _isLoading.value = false
                    if (connRes.isSuccess) {
                        _navigationEvent.emit(
                            DashboardNavigationEvent.NavigateToTerminal(
                                siteId = currentSite.id,
                                type = type,
                                port = port
                            )
                        )
                    } else {
                        val host = sshRepository.serverConfigManager.getHost()
                        val p = sshRepository.serverConfigManager.getPort()
                        val diag = SshDiagnosticError.classify(connRes.exceptionOrNull() ?: Exception("Connection failed"), host, p)
                        _errorMessage.value = diag.getFormattedError()
                        _isAuthError.value = (diag is SshDiagnosticError.Auth)
                    }
                }
            } else {
                _showAuthDialog.value = true
            }
            return
        }

        viewModelScope.launch {
            _navigationEvent.emit(
                DashboardNavigationEvent.NavigateToTerminal(
                    siteId = currentSite.id,
                    type = type,
                    port = port
                )
            )
        }
    }

    fun updatePortalPorts(newPortsString: String) {
        val currentSite = _site.value ?: return
        val portsRaw = newPortsString.trim()
        if (portsRaw.isNotEmpty() && !InputValidator.isValidPortsString(portsRaw)) {
            _errorMessage.value = "Portal ports must be valid numbers between 1 and 65535, separated by commas (e.g. 8080, 8082, 8083)."
            return
        }
        val parsedList = if (portsRaw.isNotEmpty()) {
            InputValidator.parsePortsString(portsRaw)
        } else {
            Site.DEFAULT_PORT_LIST
        }
        if (parsedList.isEmpty()) {
            _errorMessage.value = "Please enter at least one valid port number (1..65535)."
            return
        }
        val formattedPorts = parsedList.joinToString(", ")
        viewModelScope.launch {
            val updatedSite = currentSite.copy(
                portalPorts = formattedPorts,
                updatedAt = System.currentTimeMillis()
            )
            val res = siteRepository.updateSite(updatedSite)
            if (res.isSuccess) {
                _site.value = updatedSite
                _statusMessage.value = "Portal ports updated to: $formattedPorts"
            } else {
                _errorMessage.value = res.exceptionOrNull()?.message ?: "Failed to update portal ports"
            }
        }
    }

    fun disconnectZmp(port: Int) {
        viewModelScope.launch {
            sshRepository.closeZmpTunnel(siteId, port)
            _statusMessage.value = "ZMP tunnel on port $port disconnected."
        }
    }

    fun onAuthDialogSubmit(authInfo: SshAuthInfo) {
        _showAuthDialog.value = false
        // Persist password so it will never be asked again
        if (authInfo is SshAuthInfo.Password) {
            val pass = String(authInfo.passwordChars)
            sshRepository.serverConfigManager.saveConfig(
                host = sshRepository.serverConfigManager.getHost(),
                port = sshRepository.serverConfigManager.getPort(),
                username = sshRepository.serverConfigManager.getUsername(),
                password = pass,
                autoAcceptHostKey = false
            )
        }

        viewModelScope.launch {
            _isLoading.value = true
            _statusMessage.value = "Authenticating with Office SSH Server..."
            val result = sshRepository.connectToOffice(authInfo = authInfo)
            _isLoading.value = false

            if (result.isSuccess) {
                _statusMessage.value = "Connected to Office Server!"
                pendingAction?.invoke()
                pendingAction = null
            } else {
                val host = sshRepository.serverConfigManager.getHost()
                val p = sshRepository.serverConfigManager.getPort()
                val diag = SshDiagnosticError.classify(result.exceptionOrNull() ?: Exception("Authentication failed"), host, p)
                _errorMessage.value = diag.getFormattedError()
                _isAuthError.value = (diag is SshDiagnosticError.Auth)
            }
        }
    }

    fun updateSavedPasswordAndRetry(newPassword: String) {
        sshRepository.updateSavedPassword(newPassword)
        _errorMessage.value = null
        _isAuthError.value = false
        val action = pendingAction
        pendingAction = null
        action?.invoke()
    }

    fun dismissAuthDialog() {
        _showAuthDialog.value = false
        pendingAction = null
    }

    fun trustHostKey(unknownHost: HostVerificationResult.UnknownHost) {
        viewModelScope.launch {
            sshRepository.trustHostKey(unknownHost)
        }
    }

    fun dismissHostVerification() {
        sshRepository.dismissHostVerification()
    }

    fun disconnectOffice() {
        sshRepository.disconnectOffice()
        _statusMessage.value = "Disconnected from office server."
    }

    fun launchUrlInCustomTab(context: Context, url: String) {
        try {
            val customTabsIntent = CustomTabsIntent.Builder()
                .setShowTitle(true)
                .build()
            customTabsIntent.launchUrl(context, Uri.parse(url))
        } catch (_: Exception) {
            // Fallback to regular browser intent
            try {
                val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                browserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(browserIntent)
            } catch (e: Exception) {
                _errorMessage.value = "Could not find a browser to open URL: ${e.message}"
            }
        }
    }

    class Factory(
        private val siteRepository: SiteRepository,
        private val sshRepository: SshRepository,
        private val keyStoreManager: KeyStoreManager,
        private val siteId: Long
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return SiteDashboardViewModel(siteRepository, sshRepository, keyStoreManager, siteId) as T
        }
    }
}
