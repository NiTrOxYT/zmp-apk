package com.sitelink.manager.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.sitelink.manager.data.keystore.KeyStoreManager
import com.sitelink.manager.data.repository.SiteRepository
import com.sitelink.manager.data.repository.SshRepository
import com.sitelink.manager.data.ssh.SshAuthInfo
import com.sitelink.manager.domain.model.HostVerificationResult
import com.sitelink.manager.domain.model.Site
import com.sitelink.manager.domain.model.SshSessionState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel(
    private val siteRepository: SiteRepository,
    val sshRepository: SshRepository,
    val keyStoreManager: KeyStoreManager
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _showAuthDialog = MutableStateFlow(false)
    val showAuthDialog: StateFlow<Boolean> = _showAuthDialog.asStateFlow()

    private val _siteToDelete = MutableStateFlow<Site?>(null)
    val siteToDelete: StateFlow<Site?> = _siteToDelete.asStateFlow()

    val sessionState: StateFlow<SshSessionState> = sshRepository.sessionState
    val hostVerificationEvent: StateFlow<HostVerificationResult?> = sshRepository.hostVerificationEvent

    val sites: StateFlow<List<Site>> = _searchQuery.flatMapLatest { query ->
        if (query.isBlank()) {
            siteRepository.allSites
        } else {
            siteRepository.searchSites(query)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    /**
     * Seamlessly connects using saved credentials. Never asks for password if configured!
     */
    fun onConnectClick() {
        if (sshRepository.hasSavedPassword() || keyStoreManager.hasKeyPair()) {
            viewModelScope.launch {
                sshRepository.connectToOfficeWithSavedCredentials()
            }
        } else {
            _showAuthDialog.value = true
        }
    }

    fun dismissAuthDialog() {
        _showAuthDialog.value = false
    }

    fun connectOfficeWithInput(authInfo: SshAuthInfo) {
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
            sshRepository.connectToOffice(authInfo = authInfo)
        }
    }

    fun updateSavedPassword(newPassword: String) {
        sshRepository.updateSavedPassword(newPassword)
        viewModelScope.launch {
            sshRepository.connectToOfficeWithSavedCredentials()
        }
    }

    fun disconnectOffice() {
        sshRepository.disconnectOffice()
    }

    fun requestDeleteSite(site: Site) {
        _siteToDelete.value = site
    }

    fun cancelDeleteSite() {
        _siteToDelete.value = null
    }

    fun confirmDeleteSite() {
        val site = _siteToDelete.value ?: return
        _siteToDelete.value = null
        viewModelScope.launch {
            siteRepository.deleteSite(site)
        }
    }

    fun trustHostKey(unknownHost: HostVerificationResult.UnknownHost) {
        viewModelScope.launch {
            sshRepository.trustHostKey(unknownHost)
        }
    }

    fun dismissHostVerification() {
        sshRepository.dismissHostVerification()
    }

    class Factory(
        private val siteRepository: SiteRepository,
        private val sshRepository: SshRepository,
        private val keyStoreManager: KeyStoreManager
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return HomeViewModel(siteRepository, sshRepository, keyStoreManager) as T
        }
    }
}
