package com.sitelink.manager.data.ssh

import com.jcraft.jsch.ChannelExec
import com.sitelink.manager.data.db.ConnectionLogDao
import com.sitelink.manager.data.db.ConnectionLogEntity
import com.sitelink.manager.domain.model.SshDiagnosticError
import com.sitelink.manager.domain.validation.InputValidator
import com.sitelink.manager.ui.screens.terminal.TerminalBufferEngine
import com.sitelink.manager.ui.screens.terminal.TerminalState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream

class SshTerminalManager(
    private val sshManager: SshManager,
    private val connectionLogDao: ConnectionLogDao
) {
    private var currentChannel: ChannelExec? = null
    private var channelOutputStream: OutputStream? = null
    private var readJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    val terminalBufferEngine = TerminalBufferEngine()

    private val _terminalState = MutableStateFlow(TerminalState())
    val terminalState: StateFlow<TerminalState> = _terminalState.asStateFlow()

    private val _terminalOutput = MutableSharedFlow<String>(replay = 500)
    val terminalOutput: SharedFlow<String> = _terminalOutput.asSharedFlow()

    private val _isTerminalActive = MutableStateFlow(false)
    val isTerminalActive: StateFlow<Boolean> = _isTerminalActive.asStateFlow()

    private val _currentTitle = MutableStateFlow("")
    val currentTitle: StateFlow<String> = _currentTitle.asStateFlow()

    var currentSiteId: Long? = null
        private set
    var currentTargetPort: Int? = null
        private set
    var currentSessionType: String? = null
        private set

    /**
     * Connects to VM (port 2222) or BM (port 2223) on siteIp via the office SSH server.
     * If the session is already active for this site and port, reuses it without restarting.
     */
    suspend fun startTerminalSession(
        siteId: Long,
        siteName: String,
        siteIp: String,
        targetPort: Int, // 2222 for VM, 2223 for BM
        sessionType: String // "VM" or "BM"
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val sanitizedIp = try {
            InputValidator.sanitizeForSshArgument(siteIp)
        } catch (e: Exception) {
            return@withContext Result.failure(e)
        }

        val isBm = sessionType.equals("BM", ignoreCase = true) || targetPort == 2223
        val actualPort = if (targetPort == 2222 || targetPort == 2223) targetPort else (if (isBm) 2223 else 2222)

        // Session reuse: If shell is already active for this exact site and port, keep it alive!
        if (_isTerminalActive.value &&
            currentSiteId == siteId &&
            currentTargetPort == actualPort &&
            currentChannel?.isConnected == true &&
            sshManager.isConnected()
        ) {
            return@withContext Result.success(Unit)
        }

        val session = sshManager.getActiveSession()
            ?: return@withContext Result.failure(
                SshDiagnosticError.NestedTerminal("Office SSH session not connected. Cannot start $sessionType terminal.")
            )

        closeTerminalSession()
        terminalBufferEngine.clear()
        _terminalState.value = terminalBufferEngine.getState()

        try {
            val nestedPassword = if (isBm) "SuPp0rt@Base@202602" else "SuPp0rt@MVM@202602"

            currentSiteId = siteId
            currentTargetPort = actualPort
            currentSessionType = sessionType
            _currentTitle.value = "$sessionType Shell: $siteName ($sanitizedIp:$actualPort)"

            val initMsg1 = "\r\n[SiteLink] Initializing $sessionType session via Office Server...\r\n"
            val initMsg2 = "[SiteLink] Connecting to support@$sanitizedIp:$actualPort (KeepAlive: 15s)...\r\n\r\n"
            _terminalOutput.emit(initMsg1)
            _terminalOutput.emit(initMsg2)
            terminalBufferEngine.processChunk(initMsg1)
            terminalBufferEngine.processChunk(initMsg2)
            _terminalState.value = terminalBufferEngine.getState()

            val channel = session.openChannel("exec") as ChannelExec
            channel.setPty(true)
            channel.setPtyType("xterm-256color", 80, 24, 640, 480)

            // Safe nested SSH command to target site appliance with application-level keepalives
            // to ensure intermediate firewalls, NAT routers, and appliances never drop idle sessions:
            val command = "sshpass -p '$nestedPassword' ssh " +
                "-o StrictHostKeyChecking=no " +
                "-o ServerAliveInterval=15 " +
                "-o ServerAliveCountMax=4 " +
                "-o TCPKeepAlive=yes " +
                "-o ConnectTimeout=15 " +
                "support@$sanitizedIp -p $actualPort"
            channel.setCommand(command)

            val inputStream: InputStream = channel.inputStream
            val errStream: InputStream = channel.extInputStream
            val outputStream: OutputStream = channel.outputStream

            currentChannel = channel
            channelOutputStream = outputStream

            channel.connect(15000)
            _isTerminalActive.value = true

            // Launch output and error reader coroutine
            readJob = scope.launch {
                val stdoutJob = launch {
                    val buffer = ByteArray(4096)
                    try {
                        while (isActive && channel.isConnected) {
                            val read = inputStream.read(buffer)
                            if (read > 0) {
                                val text = String(buffer, 0, read, Charsets.UTF_8)
                                _terminalOutput.emit(text)
                                terminalBufferEngine.processChunk(text)
                                _terminalState.value = terminalBufferEngine.getState()
                            } else if (read < 0) {
                                break
                            }
                        }
                    } catch (e: Exception) {
                        if (isActive) {
                            val errText = "\r\n[SiteLink] Terminal stream interrupted: ${e.message}\r\n"
                            _terminalOutput.emit(errText)
                            terminalBufferEngine.processChunk(errText)
                            _terminalState.value = terminalBufferEngine.getState()
                        }
                    }
                }

                val stderrJob = launch {
                    val errBuffer = ByteArray(2048)
                    try {
                        while (isActive && channel.isConnected) {
                            val read = errStream.read(errBuffer)
                            if (read > 0) {
                                val text = String(errBuffer, 0, read, Charsets.UTF_8)
                                _terminalOutput.emit(text)
                                terminalBufferEngine.processChunk(text)
                                _terminalState.value = terminalBufferEngine.getState()
                            } else if (read < 0) {
                                break
                            }
                        }
                    } catch (_: Exception) {}
                }

                stdoutJob.join()
                stderrJob.join()

                // Capture exit status
                var attempts = 0
                while (!channel.isClosed && attempts < 15) {
                    delay(50)
                    attempts++
                }
                val exitStatus = channel.exitStatus
                val exitMsg = when {
                    exitStatus == 0 -> "\r\n[SiteLink] Remote shell exited normally (status 0).\r\n"
                    exitStatus == 255 -> "\r\n[SiteLink] Nested SSH connection to $sanitizedIp:$actualPort terminated (exit 255).\r\n"
                    exitStatus > 0 -> "\r\n[SiteLink] Shell process exited with status $exitStatus.\r\n"
                    !sshManager.isConnected() -> "\r\n[SiteLink Error] Office server connection was lost.\r\n"
                    else -> "\r\n[SiteLink] Shell session ended.\r\n"
                }

                _isTerminalActive.value = false
                _terminalOutput.emit(exitMsg)
                terminalBufferEngine.processChunk(exitMsg)
                _terminalState.value = terminalBufferEngine.getState()
            }

            connectionLogDao.insertLog(
                ConnectionLogEntity(
                    siteId = siteId,
                    siteName = siteName,
                    connectionType = "$sessionType-$actualPort",
                    destinationIp = sanitizedIp,
                    destinationPort = actualPort,
                    localPort = null,
                    status = "CONNECTED",
                    details = "Started persistent $sessionType terminal session with 15s keepalive"
                )
            )

            Result.success(Unit)
        } catch (e: Exception) {
            val diagError = SshDiagnosticError.NestedTerminal(
                "Failed to launch $sessionType shell on $sanitizedIp:$actualPort: ${e.message}",
                e
            )
            _isTerminalActive.value = false
            val errText = "\r\n[SiteLink Error] ${diagError.diagnosticMessage}\r\n"
            _terminalOutput.emit(errText)
            terminalBufferEngine.processChunk(errText)
            _terminalState.value = terminalBufferEngine.getState()

            connectionLogDao.insertLog(
                ConnectionLogEntity(
                    siteId = siteId,
                    siteName = siteName,
                    connectionType = "$sessionType-$actualPort",
                    destinationIp = sanitizedIp,
                    destinationPort = actualPort,
                    localPort = null,
                    status = "FAILED",
                    details = diagError.message
                )
            )
            Result.failure(diagError)
        }
    }

    /**
     * Sends user keystrokes / commands to the terminal.
     */
    suspend fun sendInput(text: String) = withContext(Dispatchers.IO) {
        try {
            channelOutputStream?.let { os ->
                os.write(text.toByteArray(Charsets.UTF_8))
                os.flush()
            }
        } catch (e: Exception) {
            _terminalOutput.emit("\r\n[Error writing to terminal: ${e.message}]\r\n")
        }
    }

    /**
     * Sends raw bytes (e.g., Ctrl+C = 0x03, Escape = 0x1B).
     */
    suspend fun sendBytes(bytes: ByteArray) = withContext(Dispatchers.IO) {
        try {
            channelOutputStream?.let { os ->
                os.write(bytes)
                os.flush()
            }
        } catch (e: Exception) {
            _terminalOutput.emit("\r\n[Error writing control code: ${e.message}]\r\n")
        }
    }

    /**
     * Send Ctrl+C (SIGINT)
     */
    suspend fun sendCtrlC() {
        sendBytes(byteArrayOf(3))
    }

    /**
     * Resize terminal dimensions
     */
    fun resizeTerminal(cols: Int, rows: Int, widthPx: Int = cols * 8, heightPx: Int = rows * 16) {
        try {
            currentChannel?.setPtySize(cols, rows, widthPx, heightPx)
        } catch (_: Exception) {}
    }

    /**
     * Cleanly terminates the active terminal session.
     */
    fun closeTerminalSession() {
        readJob?.cancel()
        readJob = null
        try {
            currentChannel?.disconnect()
        } catch (_: Exception) {}
        currentChannel = null
        channelOutputStream = null
        _isTerminalActive.value = false
        currentSiteId = null
        currentTargetPort = null
        currentSessionType = null
    }

    fun getAllOutput(): String = terminalBufferEngine.getAllText()
    fun getLastLines(count: Int): String = terminalBufferEngine.getFormattedText(count)
    fun clearTerminalBuffer() {
        terminalBufferEngine.clear()
        _terminalState.value = terminalBufferEngine.getState()
    }
}
