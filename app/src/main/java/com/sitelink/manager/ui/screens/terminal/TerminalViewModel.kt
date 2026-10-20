package com.sitelink.manager.ui.screens.terminal

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.sitelink.manager.data.repository.SiteRepository
import com.sitelink.manager.data.repository.SshRepository
import com.sitelink.manager.domain.model.Site
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class TerminalViewModel(
    private val siteRepository: SiteRepository,
    private val sshRepository: SshRepository,
    private val siteId: Long,
    val sessionType: String,
    val targetPort: Int,
    private val appContext: Context? = null
) : ViewModel() {

    private val _site = MutableStateFlow<Site?>(null)
    val site: StateFlow<Site?> = _site.asStateFlow()

    val terminalState: StateFlow<TerminalState> = sshRepository.terminalState

    val terminalBuffer: StateFlow<String> = sshRepository.terminalState
        .map { it.formattedText }
        .stateIn(viewModelScope, SharingStarted.Eagerly, sshRepository.terminalState.value.formattedText)

    private val _commandInput = MutableStateFlow("")
    val commandInput: StateFlow<String> = _commandInput.asStateFlow()

    val isTerminalActive: StateFlow<Boolean> = sshRepository.isTerminalActive
    val terminalTitle: StateFlow<String> = sshRepository.terminalTitle

    private val _exitEvent = MutableSharedFlow<Unit>()
    val exitEvent: SharedFlow<Unit> = _exitEvent.asSharedFlow()

    init {
        initSession()
    }

    private fun initSession() {
        viewModelScope.launch {
            val siteObj = siteRepository.getSiteById(siteId)
            _site.value = siteObj

            if (siteObj != null) {
                // Ensure Office SSH session is active
                if (!sshRepository.isConnected()) {
                    val connRes = sshRepository.connectToOfficeWithSavedCredentials()
                    if (connRes.isFailure) {
                        return@launch
                    }
                }

                // Start interactive SSH session (or reuse existing active session if same site/port)
                sshRepository.startTerminalSession(
                    site = siteObj,
                    targetPort = targetPort,
                    sessionType = sessionType,
                    context = appContext
                )
            }
        }
    }

    fun reconnect() {
        viewModelScope.launch {
            sshRepository.closeTerminalSession()
            initSession()
        }
    }

    fun onCommandInputChange(text: String) {
        _commandInput.value = text
    }

    fun submitCommand() {
        val cmd = _commandInput.value
        _commandInput.value = ""
        viewModelScope.launch {
            sshRepository.sendTerminalInput("$cmd\n")
        }
    }

    fun sendInput(text: String) {
        viewModelScope.launch {
            sshRepository.sendTerminalInput(text)
        }
    }

    fun sendEnter() {
        viewModelScope.launch {
            sshRepository.sendTerminalInput("\r")
        }
    }

    fun sendBackspace() {
        viewModelScope.launch {
            sshRepository.sendTerminalInput("\u007F")
        }
    }

    fun getAllOutput(): String {
        return sshRepository.getTerminalAllOutput()
    }

    fun getLastLines(count: Int): String {
        return sshRepository.getTerminalLastLines(count)
    }

    fun clearTerminalBuffer() {
        sshRepository.clearTerminalBuffer()
    }

    fun sendCtrlC() {
        viewModelScope.launch {
            sshRepository.sendTerminalCtrlC()
        }
    }

    fun sendCtrlD() {
        viewModelScope.launch {
            sshRepository.sendTerminalInput("\u0004")
        }
    }

    fun sendTab() {
        viewModelScope.launch {
            sshRepository.sendTerminalInput("\t")
        }
    }

    fun sendEsc() {
        viewModelScope.launch {
            sshRepository.sendTerminalInput("\u001B")
        }
    }

    fun sendArrowUp() {
        viewModelScope.launch {
            sshRepository.sendTerminalInput("\u001B[A")
        }
    }

    fun sendArrowDown() {
        viewModelScope.launch {
            sshRepository.sendTerminalInput("\u001B[B")
        }
    }

    fun sendArrowLeft() {
        viewModelScope.launch {
            sshRepository.sendTerminalInput("\u001B[D")
        }
    }

    fun sendArrowRight() {
        viewModelScope.launch {
            sshRepository.sendTerminalInput("\u001B[C")
        }
    }

    fun sendClear() {
        viewModelScope.launch {
            sshRepository.sendTerminalInput("clear\n")
        }
    }

    fun exitShell() {
        viewModelScope.launch {
            if (isTerminalActive.value) {
                sshRepository.sendTerminalInput("exit\n")
            }
            sshRepository.closeTerminalSession()
            _exitEvent.emit(Unit)
        }
    }

    override fun onCleared() {
        super.onCleared()
        // Do NOT terminate the SSH session here.
        // Terminal sessions are managed by SshTerminalManager and TunnelForegroundService,
        // allowing screen rotation and background operation without dropping the shell.
    }

    class Factory(
        private val siteRepository: SiteRepository,
        private val sshRepository: SshRepository,
        private val siteId: Long,
        private val sessionType: String,
        private val targetPort: Int,
        private val appContext: Context? = null
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return TerminalViewModel(siteRepository, sshRepository, siteId, sessionType, targetPort, appContext) as T
        }
    }
}
