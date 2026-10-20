package com.sitelink.manager.ui.screens.terminal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.sitelink.manager.data.repository.SiteRepository
import com.sitelink.manager.data.repository.SshRepository
import com.sitelink.manager.domain.model.Site
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class TerminalViewModel(
    private val siteRepository: SiteRepository,
    private val sshRepository: SshRepository,
    private val siteId: Long,
    val sessionType: String,
    val targetPort: Int
) : ViewModel() {

    private val _site = MutableStateFlow<Site?>(null)
    val site: StateFlow<Site?> = _site.asStateFlow()

    private val _terminalBuffer = MutableStateFlow("")
    val terminalBuffer: StateFlow<String> = _terminalBuffer.asStateFlow()

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
                // Collect output stream
                launch {
                    sshRepository.terminalOutput.collect { chunk ->
                        val current = _terminalBuffer.value
                        // Keep reasonable buffer length for performance
                        val updated = if (current.length > 50000) {
                            current.substring(current.length - 30000) + chunk
                        } else {
                            current + chunk
                        }
                        _terminalBuffer.value = updated
                    }
                }

                // Ensure Office SSH session is active
                if (!sshRepository.isConnected()) {
                    _terminalBuffer.value = "[SiteLink] Connecting to Office SSH Server (111.93.205.187:22)...\r\n"
                    val connRes = sshRepository.connectToOfficeWithSavedCredentials()
                    if (connRes.isFailure) {
                        val diag = connRes.exceptionOrNull()?.message ?: "Failed to connect to office server"
                        _terminalBuffer.value = "[SiteLink Error] $diag\r\n"
                        return@launch
                    }
                }

                // Start interactive SSH session
                sshRepository.startTerminalSession(
                    site = siteObj,
                    targetPort = targetPort,
                    sessionType = sessionType
                )
            }
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

    fun getLastLines(count: Int): String {
        val lines = _terminalBuffer.value.lines()
        val takeLines = if (lines.size > count) lines.takeLast(count) else lines
        return takeLines.joinToString("\n")
    }

    fun clearTerminalBuffer() {
        _terminalBuffer.value = ""
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
            sshRepository.sendTerminalInput("exit\n")
            sshRepository.closeTerminalSession()
            _exitEvent.emit(Unit)
        }
    }

    override fun onCleared() {
        super.onCleared()
        sshRepository.closeTerminalSession()
    }

    class Factory(
        private val siteRepository: SiteRepository,
        private val sshRepository: SshRepository,
        private val siteId: Long,
        private val sessionType: String,
        private val targetPort: Int
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return TerminalViewModel(siteRepository, sshRepository, siteId, sessionType, targetPort) as T
        }
    }
}
