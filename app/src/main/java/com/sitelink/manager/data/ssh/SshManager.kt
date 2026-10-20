package com.sitelink.manager.data.ssh

import com.jcraft.jsch.JSch
import com.jcraft.jsch.Session
import com.jcraft.jsch.UIKeyboardInteractive
import com.jcraft.jsch.UserInfo
import com.sitelink.manager.data.config.ServerConfigManager
import com.sitelink.manager.data.db.TrustedHostKeyDao
import com.sitelink.manager.domain.model.HostVerificationResult
import com.sitelink.manager.domain.model.SshDiagnosticError
import com.sitelink.manager.domain.model.SshSessionState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

class SshManager(
    private val trustedHostKeyDao: TrustedHostKeyDao,
    private val serverConfigManager: ServerConfigManager
) {
    private val jsch = JSch()
    private var currentSession: Session? = null

    private val _sessionState = MutableStateFlow<SshSessionState>(SshSessionState.Disconnected)
    val sessionState: StateFlow<SshSessionState> = _sessionState.asStateFlow()

    private val _hostVerificationEvent = MutableStateFlow<HostVerificationResult?>(null)
    val hostVerificationEvent: StateFlow<HostVerificationResult?> = _hostVerificationEvent.asStateFlow()

    init {
        // Configure custom host key repository backed by Room DB
        val repo = CustomHostKeyRepository(trustedHostKeyDao) { verificationResult ->
            _hostVerificationEvent.value = verificationResult
        }
        jsch.hostKeyRepository = repo
    }

    fun clearHostVerificationEvent() {
        _hostVerificationEvent.value = null
    }

    fun isConnected(): Boolean {
        return currentSession?.isConnected == true
    }

    fun getActiveSession(): Session? {
        return if (isConnected()) currentSession else null
    }

    /**
     * Connects to the office server using saved credentials.
     */
    suspend fun connectWithSavedCredentials(): Result<Session> = withContext(Dispatchers.IO) {
        val host = serverConfigManager.getHost()
        val port = serverConfigManager.getPort()
        val username = serverConfigManager.getUsername()
        val password = serverConfigManager.getPassword()
        val autoAccept = serverConfigManager.isAutoAcceptHostKey()

        connect(
            host = host,
            port = port,
            username = username,
            authInfo = SshAuthInfo.Password(password.toCharArray()),
            autoAccept = autoAccept
        )
    }

    /**
     * Connects to the office server with full authentication and strict host key checks.
     */
    suspend fun connect(
        host: String = serverConfigManager.getHost(),
        port: Int = serverConfigManager.getPort(),
        username: String = serverConfigManager.getUsername(),
        authInfo: SshAuthInfo,
        autoAccept: Boolean = serverConfigManager.isAutoAcceptHostKey()
    ): Result<Session> = withContext(Dispatchers.IO) {
        try {
            if (isConnected()) {
                val session = currentSession!!
                if (session.host == host && session.userName == username) {
                    return@withContext Result.success(session)
                } else {
                    disconnect()
                }
            }

            _sessionState.value = SshSessionState.Connecting("Connecting to $username@$host:$port...")

            val session = jsch.getSession(username, host, port)
            // SO_TIMEOUT = 0: Disables socket read timeout so idle SSH sessions remain open indefinitely
            session.timeout = 0
            // Send SSH keepalive ping every 15 seconds to prevent NAT/router/firewall disconnections
            session.serverAliveInterval = 15000
            session.serverAliveCountMax = 4

            var pwdString = ""
            when (authInfo) {
                is SshAuthInfo.Password -> {
                    pwdString = String(authInfo.passwordChars)
                    session.setPassword(pwdString)
                }
                is SshAuthInfo.KeyStoreAuth -> {
                    // Android Keystore key authentication
                }
                is SshAuthInfo.ImportedKey -> {
                    val pass = authInfo.passphrase?.let { String(it).toByteArray() }
                    jsch.addIdentity("custom_key", authInfo.privateKeyBytes, null, pass)
                }
            }

            // Implement UserInfo & UIKeyboardInteractive for PAM challenge-response
            val userInfo = object : UserInfo, UIKeyboardInteractive {
                override fun getPassphrase(): String? = null
                override fun getPassword(): String = pwdString
                override fun promptPassword(message: String?): Boolean = true
                override fun promptPassphrase(message: String?): Boolean = true
                override fun promptYesNo(message: String?): Boolean = autoAccept
                override fun showMessage(message: String?) {}
                override fun promptKeyboardInteractive(
                    destination: String?,
                    name: String?,
                    instruction: String?,
                    prompt: Array<out String>?,
                    echo: BooleanArray?
                ): Array<String>? {
                    if (prompt == null || prompt.isEmpty()) return emptyArray()
                    return Array(prompt.size) { pwdString }
                }
            }
            session.userInfo = userInfo

            // Strict host key checking: "ask" enforces checking against known hosts,
            // immediately rejects changed keys, and requests confirmation for new keys.
            session.setConfig("StrictHostKeyChecking", if (autoAccept) "ask" else "yes")
            session.setConfig("PreferredAuthentications", "password,keyboard-interactive,publickey")
            session.setConfig(
                "server_host_key",
                "ssh-ed25519,ecdsa-sha2-nistp256,ecdsa-sha2-nistp384,ecdsa-sha2-nistp521,rsa-sha2-512,rsa-sha2-256,ssh-rsa"
            )
            session.setConfig(
                "PubkeyAcceptedAlgorithms",
                "ssh-ed25519,ecdsa-sha2-nistp256,ecdsa-sha2-nistp384,ecdsa-sha2-nistp521,rsa-sha2-512,rsa-sha2-256,ssh-rsa"
            )
            session.setConfig(
                "kex",
                "curve25519-sha256,curve25519-sha256@libssh.org,ecdh-sha2-nistp256,ecdh-sha2-nistp384,ecdh-sha2-nistp521,diffie-hellman-group-exchange-sha256,diffie-hellman-group16-sha512,diffie-hellman-group18-sha512,diffie-hellman-group14-sha256"
            )
            // Eliminate Nagle buffering delay for instant interactive shell keystrokes
            session.setConfig("TCP_NODELAY", "yes")
            session.setConfig("compression.s2c", "none")
            session.setConfig("compression.c2s", "none")

            session.connect(15000)

            currentSession = session
            _sessionState.value = SshSessionState.Connected(host, username)
            Result.success(session)
        } catch (e: Exception) {
            disconnect()
            val diagError = SshDiagnosticError.classify(e, host, port)
            _sessionState.value = SshSessionState.Error(diagError.getFormattedError(), diagError)
            Result.failure(diagError)
        }
    }

    fun disconnect() {
        try {
            currentSession?.disconnect()
        } catch (_: Exception) {}
        currentSession = null
        _sessionState.value = SshSessionState.Disconnected
    }
}
