package com.sitelink.manager.data.repository

import com.sitelink.manager.data.config.ServerConfigManager
import com.sitelink.manager.data.db.TrustedHostKeyDao
import com.sitelink.manager.data.db.TrustedHostKeyEntity
import com.sitelink.manager.data.ssh.SshAuthInfo
import com.sitelink.manager.data.ssh.SshManager
import com.sitelink.manager.data.ssh.SshTerminalManager
import com.sitelink.manager.data.ssh.SshTunnelManager
import com.sitelink.manager.data.ssh.TunnelForegroundService
import com.sitelink.manager.domain.model.HostVerificationResult
import com.sitelink.manager.domain.model.Site
import com.sitelink.manager.domain.model.SshSessionState
import com.sitelink.manager.domain.model.TunnelInfo
import com.sitelink.manager.ui.screens.terminal.TerminalState
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

class SshRepository(
    private val sshManager: SshManager,
    private val sshTunnelManager: SshTunnelManager,
    private val sshTerminalManager: SshTerminalManager,
    private val trustedHostKeyDao: TrustedHostKeyDao,
    val serverConfigManager: ServerConfigManager
) {
    val sessionState: StateFlow<SshSessionState> = sshManager.sessionState
    val activeTunnels: StateFlow<Map<String, TunnelInfo>> = sshTunnelManager.activeTunnels
    val hostVerificationEvent: StateFlow<HostVerificationResult?> = sshManager.hostVerificationEvent
    val terminalOutput: SharedFlow<String> = sshTerminalManager.terminalOutput
    val isTerminalActive: StateFlow<Boolean> = sshTerminalManager.isTerminalActive
    val terminalTitle: StateFlow<String> = sshTerminalManager.currentTitle
    val terminalState: StateFlow<TerminalState> = sshTerminalManager.terminalState

    fun isConnected(): Boolean = sshManager.isConnected()

    fun hasSavedPassword(): Boolean = serverConfigManager.hasSavedPassword()

    fun updateSavedPassword(newPassword: String) = serverConfigManager.updatePassword(newPassword)

    fun deleteSavedCredentials() = serverConfigManager.deleteSavedCredentials()

    suspend fun connectToOfficeWithSavedCredentials(): Result<Unit> {
        val result = sshManager.connectWithSavedCredentials()
        return if (result.isSuccess) Result.success(Unit) else Result.failure(result.exceptionOrNull()!!)
    }

    suspend fun connectToOffice(
        host: String = serverConfigManager.getHost(),
        port: Int = serverConfigManager.getPort(),
        username: String = serverConfigManager.getUsername(),
        authInfo: SshAuthInfo
    ): Result<Unit> {
        val result = sshManager.connect(host, port, username, authInfo)
        return if (result.isSuccess) Result.success(Unit) else Result.failure(result.exceptionOrNull()!!)
    }

    suspend fun testConnection(
        host: String,
        port: Int,
        username: String,
        password: String
    ): Result<String> {
        val result = sshManager.connect(
            host = host,
            port = port,
            username = username,
            authInfo = SshAuthInfo.Password(password.toCharArray()),
            autoAccept = false
        )
        return if (result.isSuccess) {
            Result.success("Connection & Authentication Succeeded! Server: $username@$host:$port")
        } else {
            Result.failure(result.exceptionOrNull() ?: Exception("Unknown connection error"))
        }
    }

    fun disconnectOffice() {
        sshTerminalManager.closeTerminalSession()
        sshManager.disconnect()
    }

    suspend fun openZmpTunnel(site: Site, remotePort: Int, context: android.content.Context? = null): Result<TunnelInfo> {
        val res = sshTunnelManager.openZmpTunnel(
            siteId = site.id,
            siteName = site.name,
            siteIp = site.ipAddress,
            remotePort = remotePort
        )
        if (res.isSuccess && context != null) {
            try {
                TunnelForegroundService.startService(context)
            } catch (_: Exception) {}
        }
        return res
    }

    suspend fun closeZmpTunnel(siteId: Long, remotePort: Int): Result<Unit> {
        return sshTunnelManager.closeTunnel(siteId, remotePort)
    }

    suspend fun closeAllTunnels(): Result<Unit> {
        sshTunnelManager.closeAllTunnels()
        return Result.success(Unit)
    }

    fun getActiveTunnel(siteId: Long, remotePort: Int): TunnelInfo? {
        return sshTunnelManager.getActiveTunnel(siteId, remotePort)
    }

    suspend fun startTerminalSession(
        site: Site,
        targetPort: Int,
        sessionType: String,
        context: android.content.Context? = null
    ): Result<Unit> {
        val result = sshTerminalManager.startTerminalSession(
            siteId = site.id,
            siteName = site.name,
            siteIp = site.ipAddress,
            targetPort = targetPort,
            sessionType = sessionType
        )
        if (result.isSuccess && context != null) {
            try {
                TunnelForegroundService.startService(context)
            } catch (_: Exception) {}
        }
        return result
    }

    suspend fun sendTerminalInput(text: String) {
        sshTerminalManager.sendInput(text)
    }

    suspend fun sendTerminalCtrlC() {
        sshTerminalManager.sendCtrlC()
    }

    fun closeTerminalSession() {
        sshTerminalManager.closeTerminalSession()
    }

    fun getTerminalAllOutput(): String = sshTerminalManager.getAllOutput()

    fun getTerminalLastLines(count: Int): String = sshTerminalManager.getLastLines(count)

    fun clearTerminalBuffer() {
        sshTerminalManager.clearTerminalBuffer()
    }

    fun resizeTerminal(cols: Int, rows: Int) {
        sshTerminalManager.resizeTerminal(cols, rows)
    }

    suspend fun trustHostKey(result: HostVerificationResult.UnknownHost) {
        val rawBase64 = java.util.Base64.getEncoder().encodeToString(result.rawKey)
        trustedHostKeyDao.insertOrUpdate(
            TrustedHostKeyEntity(
                hostPortKey = result.host.lowercase(),
                host = result.host,
                port = result.port,
                keyType = result.keyType,
                fingerprint = result.fingerprint,
                rawKeyBase64 = rawBase64
            )
        )
        sshManager.clearHostVerificationEvent()
    }

    fun dismissHostVerification() {
        sshManager.clearHostVerificationEvent()
        sshManager.disconnect()
    }
}
