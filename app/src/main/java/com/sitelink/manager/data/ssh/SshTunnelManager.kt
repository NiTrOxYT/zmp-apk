package com.sitelink.manager.data.ssh

import com.jcraft.jsch.Session
import com.sitelink.manager.data.db.ConnectionLogDao
import com.sitelink.manager.data.db.ConnectionLogEntity
import com.sitelink.manager.domain.model.SshDiagnosticError
import com.sitelink.manager.domain.model.TunnelInfo
import com.sitelink.manager.domain.model.TunnelStatus
import com.sitelink.manager.domain.validation.InputValidator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException
import java.util.Collections

class SshTunnelManager(
    private val sshManager: SshManager,
    private val connectionLogDao: ConnectionLogDao
) {
    // Map of tunnel key ("${siteId}_${remotePort}") to TunnelInfo
    private val _activeTunnels = MutableStateFlow<Map<String, TunnelInfo>>(emptyMap())
    val activeTunnels: StateFlow<Map<String, TunnelInfo>> = _activeTunnels.asStateFlow()

    // Thread-safe set of currently allocated local ports to prevent collision across tunnels
    private val allocatedLocalPorts = Collections.synchronizedSet(mutableSetOf<Int>())

    fun getTunnelKey(siteId: Long, remotePort: Int): String = "${siteId}_$remotePort"

    fun getActiveTunnel(siteId: Long, remotePort: Int): TunnelInfo? {
        return _activeTunnels.value[getTunnelKey(siteId, remotePort)]
    }

    /**
     * Finds an available ephemeral port on 127.0.0.1 that is not currently in use by OS
     * or allocated to another tunnel in the application.
     */
    fun findAvailableLocalPort(preferredStart: Int = 18080): Int = synchronized(allocatedLocalPorts) {
        for (port in preferredStart..preferredStart + 500) {
            if (port in allocatedLocalPorts) continue
            try {
                ServerSocket(port, 1, InetAddress.getByName("127.0.0.1")).use {
                    allocatedLocalPorts.add(port)
                    return port
                }
            } catch (_: Exception) {
                // Port occupied by another system process, try next
            }
        }
        // Fallback to random system assigned loopback port
        ServerSocket(0, 1, InetAddress.getByName("127.0.0.1")).use { socket ->
            val port = socket.localPort
            allocatedLocalPorts.add(port)
            return port
        }
    }

    /**
     * Probes the established local port forward end-to-end to verify that:
     * 1. The local listener is up.
     * 2. The office server successfully opened direct-tcpip to the destination site.
     * 3. The remote web server responds.
     */
    private fun verifyForwardingEndToEnd(
        localPort: Int,
        destinationIp: String,
        remotePort: Int
    ): Result<Unit> {
        val maxAttempts = 4
        var lastError: Exception? = null

        for (attempt in 1..maxAttempts) {
            try {
                Socket("127.0.0.1", localPort).use { socket ->
                    socket.soTimeout = 3000
                    val out: OutputStream = socket.getOutputStream()
                    val inputStream: InputStream = socket.getInputStream()

                    // Send HTTP HEAD probe to /zmp/login.do
                    val probe = "HEAD /zmp/login.do HTTP/1.1\r\nHost: 127.0.0.1:$localPort\r\nUser-Agent: SiteLink-Probe/1.0\r\nConnection: close\r\n\r\n"
                    out.write(probe.toByteArray(Charsets.US_ASCII))
                    out.flush()

                    val buffer = ByteArray(256)
                    val bytesRead = inputStream.read(buffer)
                    if (bytesRead > 0) {
                        val header = String(buffer, 0, bytesRead, Charsets.US_ASCII)
                        // Any HTTP response means remote web server is up!
                        if (header.startsWith("HTTP/") || header.contains("HTTP/")) {
                            return Result.success(Unit)
                        }
                    } else if (bytesRead == -1) {
                        // Connection was reset or closed by remote immediately -> direct-tcpip failed on office server!
                        return Result.failure(
                            IllegalStateException("Remote port forward failed: Office server cannot reach $destinationIp:$remotePort (connection refused or closed by remote host).")
                        )
                    }
                }
            } catch (e: SocketTimeoutException) {
                // Server might be slow to respond to HEAD or non-HTTP; if socket connected without reset, consider ready
                return Result.success(Unit)
            } catch (e: Exception) {
                lastError = e
                Thread.sleep(200)
            }
        }

        return if (lastError != null) {
            Result.failure(
                IllegalStateException("Forwarding probe failed: $destinationIp:$remotePort is not responding through office server (${lastError.message}).")
            )
        } else {
            Result.success(Unit)
        }
    }

    /**
     * Establishes a local port forward:
     * 127.0.0.1:<localPort> -> Office Server -> <destinationSiteIp>:<remotePort>
     * Confirms the forwarding is functioning before completing.
     */
    suspend fun openZmpTunnel(
        siteId: Long,
        siteName: String,
        siteIp: String,
        remotePort: Int
    ): Result<TunnelInfo> = withContext(Dispatchers.IO) {
        val sanitizedIp = try {
            InputValidator.sanitizeForSshArgument(siteIp)
        } catch (e: Exception) {
            return@withContext Result.failure(e)
        }

        val tunnelKey = getTunnelKey(siteId, remotePort)

        // Reuse existing tunnel if already connected and session is healthy
        val existing = _activeTunnels.value[tunnelKey]
        if (existing != null && existing.status == TunnelStatus.CONNECTED && sshManager.isConnected()) {
            return@withContext Result.success(existing)
        }

        val session: Session = sshManager.getActiveSession()
            ?: return@withContext Result.failure(
                SshDiagnosticError.PortForward("Office SSH session is not connected. Please connect first.")
            )

        val localPort = findAvailableLocalPort(preferredStart = 10000 + remotePort)

        val tunnelInfo = TunnelInfo(
            siteId = siteId,
            siteName = siteName,
            destinationIp = sanitizedIp,
            remotePort = remotePort,
            localPort = localPort,
            status = TunnelStatus.CONNECTING
        )
        _activeTunnels.value = _activeTunnels.value + (tunnelKey to tunnelInfo)

        try {
            // Bind local forwarded port EXCLUSIVELY to 127.0.0.1
            session.setPortForwardingL("127.0.0.1", localPort, sanitizedIp, remotePort)

            // Confirm forwarding is ready and remote port is actively reachable
            val probeResult = verifyForwardingEndToEnd(localPort, sanitizedIp, remotePort)
            if (probeResult.isFailure) {
                // Tear down failed forward to release OS port
                try {
                    session.delPortForwardingL("127.0.0.1", localPort)
                } catch (_: Exception) {}
                allocatedLocalPorts.remove(localPort)
                _activeTunnels.value = _activeTunnels.value - tunnelKey

                val probeEx = probeResult.exceptionOrNull()
                val failureError = SshDiagnosticError.PortForward(
                    "Remote port forward verification failed: Office server cannot reach $sanitizedIp:$remotePort (${probeEx?.message ?: "no response"}).",
                    probeEx
                )
                connectionLogDao.insertLog(
                    ConnectionLogEntity(
                        siteId = siteId,
                        siteName = siteName,
                        connectionType = "ZMP-$remotePort",
                        destinationIp = sanitizedIp,
                        destinationPort = remotePort,
                        localPort = localPort,
                        status = "FAILED",
                        details = failureError.message
                    )
                )
                return@withContext Result.failure(failureError)
            }

            val connectedTunnel = tunnelInfo.copy(status = TunnelStatus.CONNECTED)
            _activeTunnels.value = _activeTunnels.value + (tunnelKey to connectedTunnel)

            // Log connection success
            connectionLogDao.insertLog(
                ConnectionLogEntity(
                    siteId = siteId,
                    siteName = siteName,
                    connectionType = "ZMP-$remotePort",
                    destinationIp = sanitizedIp,
                    destinationPort = remotePort,
                    localPort = localPort,
                    status = "CONNECTED",
                    details = "Verified forward 127.0.0.1:$localPort -> $sanitizedIp:$remotePort via Office SSH"
                )
            )

            Result.success(connectedTunnel)
        } catch (e: Exception) {
            try {
                session.delPortForwardingL("127.0.0.1", localPort)
            } catch (_: Exception) {}
            allocatedLocalPorts.remove(localPort)
            _activeTunnels.value = _activeTunnels.value - tunnelKey

            val diagError = SshDiagnosticError.PortForward(
                "Remote port forwarding failed on local port $localPort for $sanitizedIp:$remotePort: ${e.message}",
                e
            )
            connectionLogDao.insertLog(
                ConnectionLogEntity(
                    siteId = siteId,
                    siteName = siteName,
                    connectionType = "ZMP-$remotePort",
                    destinationIp = sanitizedIp,
                    destinationPort = remotePort,
                    localPort = localPort,
                    status = "FAILED",
                    details = diagError.message
                )
            )
            Result.failure(diagError)
        }
    }

    /**
     * Closes the local port forward and releases all associated resources.
     */
    suspend fun closeTunnel(siteId: Long, remotePort: Int): Result<Unit> = withContext(Dispatchers.IO) {
        val tunnelKey = getTunnelKey(siteId, remotePort)
        val tunnel = _activeTunnels.value[tunnelKey] ?: return@withContext Result.success(Unit)

        try {
            val session = sshManager.getActiveSession()
            session?.delPortForwardingL("127.0.0.1", tunnel.localPort)
        } catch (_: Exception) {}

        allocatedLocalPorts.remove(tunnel.localPort)
        _activeTunnels.value = _activeTunnels.value - tunnelKey

        connectionLogDao.insertLog(
            ConnectionLogEntity(
                siteId = siteId,
                siteName = tunnel.siteName,
                connectionType = "ZMP-$remotePort",
                destinationIp = tunnel.destinationIp,
                destinationPort = remotePort,
                localPort = tunnel.localPort,
                status = "DISCONNECTED",
                details = "Closed tunnel on local port ${tunnel.localPort}"
            )
        )

        Result.success(Unit)
    }

    /**
     * Closes all active tunnels and releases all local ports.
     */
    suspend fun closeAllTunnels() = withContext(Dispatchers.IO) {
        val currentKeys = _activeTunnels.value.keys.toList()
        for (key in currentKeys) {
            val parts = key.split("_")
            if (parts.size == 2) {
                val siteId = parts[0].toLongOrNull() ?: continue
                val remotePort = parts[1].toIntOrNull() ?: continue
                closeTunnel(siteId, remotePort)
            }
        }
        allocatedLocalPorts.clear()
    }
}
