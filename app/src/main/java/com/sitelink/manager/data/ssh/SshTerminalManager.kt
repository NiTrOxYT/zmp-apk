package com.sitelink.manager.data.ssh

import com.jcraft.jsch.ChannelExec
import com.sitelink.manager.data.db.ConnectionLogDao
import com.sitelink.manager.data.db.ConnectionLogEntity
import com.sitelink.manager.domain.model.SshDiagnosticError
import com.sitelink.manager.domain.validation.InputValidator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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

    private val _terminalOutput = MutableSharedFlow<String>(replay = 500)
    val terminalOutput: SharedFlow<String> = _terminalOutput.asSharedFlow()

    private val _isTerminalActive = MutableStateFlow(false)
    val isTerminalActive: StateFlow<Boolean> = _isTerminalActive.asStateFlow()

    private val _currentTitle = MutableStateFlow("")
    val currentTitle: StateFlow<String> = _currentTitle.asStateFlow()

    /**
     * Connects to VM (port 2222) or BM (port 2223) on siteIp via the office SSH server.
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

        val session = sshManager.getActiveSession()
            ?: return@withContext Result.failure(
                SshDiagnosticError.NestedTerminal("Office SSH session not connected. Cannot start $sessionType terminal.")
            )

        closeTerminalSession()

        try {
            val isBm = sessionType.equals("BM", ignoreCase = true) || targetPort == 2223
            val actualPort = if (targetPort == 2222 || targetPort == 2223) targetPort else (if (isBm) 2223 else 2222)
            val nestedPassword = if (isBm) "SuPp0rt@Base@202602" else "SuPp0rt@MVM@202602"

            _currentTitle.value = "$sessionType Shell: $siteName ($sanitizedIp:$actualPort)"
            _terminalOutput.emit("\r\n[SiteLink] Initializing $sessionType session via Office Server...\r\n")
            _terminalOutput.emit("[SiteLink] Connecting to support@$sanitizedIp:$actualPort...\r\n\r\n")

            val channel = session.openChannel("exec") as ChannelExec
            channel.setPty(true)
            channel.setPtyType("xterm-256color", 80, 24, 640, 480)

            // Safe nested SSH command to target site appliance:
            // VM: sshpass -p 'SuPp0rt@MVM@202602' ssh -o StrictHostKeyChecking=no support@<ip> -p 2222
            // BM: sshpass -p 'SuPp0rt@Base@202602' ssh -o StrictHostKeyChecking=no support@<ip> -p 2223
            val command = "sshpass -p '$nestedPassword' ssh -o StrictHostKeyChecking=no support@$sanitizedIp -p $actualPort"
            channel.setCommand(command)

            val inputStream: InputStream = channel.inputStream
            val outputStream: OutputStream = channel.outputStream

            currentChannel = channel
            channelOutputStream = outputStream

            channel.connect(10000)
            _isTerminalActive.value = true

            // Launch output reader coroutine
            readJob = scope.launch {
                val buffer = ByteArray(2048)
                try {
                    while (isActive && channel.isConnected) {
                        val read = inputStream.read(buffer)
                        if (read > 0) {
                            val text = String(buffer, 0, read, Charsets.UTF_8)
                            _terminalOutput.emit(text)
                        } else if (read < 0) {
                            break
                        }
                    }
                } catch (e: Exception) {
                    if (isActive) {
                        _terminalOutput.emit("\r\n[SiteLink] Terminal disconnected: ${e.message}\r\n")
                    }
                } finally {
                    _isTerminalActive.value = false
                    _terminalOutput.emit("\r\n[SiteLink] Session ended.\r\n")
                }
            }

            connectionLogDao.insertLog(
                ConnectionLogEntity(
                    siteId = siteId,
                    siteName = siteName,
                    connectionType = "$sessionType-$targetPort",
                    destinationIp = sanitizedIp,
                    destinationPort = targetPort,
                    localPort = null,
                    status = "CONNECTED",
                    details = "Started interactive $sessionType terminal session"
                )
            )

            Result.success(Unit)
        } catch (e: Exception) {
            val diagError = SshDiagnosticError.NestedTerminal(
                "Failed to launch $sessionType shell on $sanitizedIp:$targetPort: ${e.message}",
                e
            )
            _isTerminalActive.value = false
            _terminalOutput.emit("\r\n[SiteLink Error] ${diagError.diagnosticMessage}\r\n")
            connectionLogDao.insertLog(
                ConnectionLogEntity(
                    siteId = siteId,
                    siteName = siteName,
                    connectionType = "$sessionType-$targetPort",
                    destinationIp = sanitizedIp,
                    destinationPort = targetPort,
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
    }
}
