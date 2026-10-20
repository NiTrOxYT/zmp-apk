package com.sitelink.manager.ui.screens.edit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.sitelink.manager.data.repository.SiteRepository
import com.sitelink.manager.domain.model.Site
import com.sitelink.manager.domain.validation.InputValidator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AddEditSiteViewModel(
    private val siteRepository: SiteRepository,
    private val siteId: Long?
) : ViewModel() {

    private val _siteName = MutableStateFlow("")
    val siteName: StateFlow<String> = _siteName.asStateFlow()

    private val _ipAddress = MutableStateFlow("")
    val ipAddress: StateFlow<String> = _ipAddress.asStateFlow()

    private val _portalPorts = MutableStateFlow(Site.DEFAULT_PORTAL_PORTS)
    val portalPorts: StateFlow<String> = _portalPorts.asStateFlow()

    private val _description = MutableStateFlow("")
    val description: StateFlow<String> = _description.asStateFlow()

    private val _notes = MutableStateFlow("")
    val notes: StateFlow<String> = _notes.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _isSaved = MutableStateFlow(false)
    val isSaved: StateFlow<Boolean> = _isSaved.asStateFlow()

    private var existingSite: Site? = null

    init {
        if (siteId != null && siteId > 0) {
            viewModelScope.launch {
                val site = siteRepository.getSiteById(siteId)
                if (site != null) {
                    existingSite = site
                    _siteName.value = site.name
                    _ipAddress.value = site.ipAddress
                    _portalPorts.value = site.portalPorts.ifEmpty { Site.DEFAULT_PORTAL_PORTS }
                    _description.value = site.description
                    _notes.value = site.notes
                }
            }
        }
    }

    fun onSiteNameChange(name: String) {
        _siteName.value = name
        _errorMessage.value = null
    }

    fun onIpAddressChange(ip: String) {
        _ipAddress.value = ip
        _errorMessage.value = null
    }

    fun onPortalPortsChange(ports: String) {
        _portalPorts.value = ports
        _errorMessage.value = null
    }

    fun onDescriptionChange(desc: String) {
        _description.value = desc
    }

    fun onNotesChange(notesText: String) {
        _notes.value = notesText
    }

    fun saveSite() {
        val name = _siteName.value.trim()
        val ip = _ipAddress.value.trim()
        val portsRaw = _portalPorts.value.trim()

        if (!InputValidator.isValidSiteName(name)) {
            _errorMessage.value = "Site name must be between 1 and 100 characters."
            return
        }

        if (!InputValidator.isValidIpAddress(ip)) {
            _errorMessage.value = "Invalid IP address format. Enter a valid IPv4 or IPv6 address."
            return
        }

        val parsedPorts = if (portsRaw.isNotEmpty()) {
            if (!InputValidator.isValidPortsString(portsRaw)) {
                _errorMessage.value = "Portal ports must be valid port numbers between 1 and 65535, separated by commas (e.g. 8080, 8082, 8083)."
                return
            }
            val list = InputValidator.parsePortsString(portsRaw)
            if (list.isEmpty()) {
                _errorMessage.value = "Please enter at least one valid portal port (1..65535)."
                return
            }
            list.joinToString(", ")
        } else {
            Site.DEFAULT_PORTAL_PORTS
        }

        viewModelScope.launch {
            if (existingSite != null) {
                val updated = existingSite!!.copy(
                    name = name,
                    ipAddress = ip,
                    portalPorts = parsedPorts,
                    description = _description.value.trim(),
                    notes = _notes.value.trim(),
                    updatedAt = System.currentTimeMillis()
                )
                val res = siteRepository.updateSite(updated)
                if (res.isSuccess) {
                    _isSaved.value = true
                } else {
                    _errorMessage.value = res.exceptionOrNull()?.message ?: "Failed to update site"
                }
            } else {
                val newSite = Site(
                    name = name,
                    ipAddress = ip,
                    portalPorts = parsedPorts,
                    description = _description.value.trim(),
                    notes = _notes.value.trim()
                )
                val res = siteRepository.addSite(newSite)
                if (res.isSuccess) {
                    _isSaved.value = true
                } else {
                    _errorMessage.value = res.exceptionOrNull()?.message ?: "Failed to add site"
                }
            }
        }
    }

    class Factory(
        private val siteRepository: SiteRepository,
        private val siteId: Long?
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return AddEditSiteViewModel(siteRepository, siteId) as T
        }
    }
}
