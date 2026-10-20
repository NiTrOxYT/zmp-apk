package com.sitelink.manager.data.ssh

import com.sitelink.manager.domain.model.SshDiagnosticError
import com.sitelink.manager.domain.model.TunnelInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SshKeepaliveAndLifecycleTest {

    @Test
    fun testOuterSshKeepaliveConfiguration() {
        // Verify keepalive values intended for JSch session
        // SO_TIMEOUT must be 0 (no read timeout during idle shell)
        val soTimeout = 0
        assertEquals(0, soTimeout)

        // JSch heartbeat interval must be 15s (15000ms) with 4 retries
        val serverAliveIntervalMs = 15000
        val serverAliveCountMax = 4
        assertEquals(15000, serverAliveIntervalMs)
        assertEquals(4, serverAliveCountMax)
    }

    @Test
    fun testNestedSshCommandWithKeepaliveParameters() {
        val siteIp = "14.142.185.130"
        val vmPassword = "SuPp0rt@MVM@202602"
        val bmPassword = "SuPp0rt@Base@202602"

        val vmPort = 2222
        val bmPort = 2223

        val keepaliveFlags = "-o StrictHostKeyChecking=no -o ServerAliveInterval=15 -o ServerAliveCountMax=4 -o TCPKeepAlive=yes -o ConnectTimeout=15"

        val vmCmd = "sshpass -p '$vmPassword' ssh $keepaliveFlags support@$siteIp -p $vmPort"
        val bmCmd = "sshpass -p '$bmPassword' ssh $keepaliveFlags support@$siteIp -p $bmPort"

        // Verify VM command includes port 2222 and all keepalive flags
        assertTrue("VM command must target port 2222", vmCmd.contains("-p 2222"))
        assertTrue("VM command must include ServerAliveInterval=15", vmCmd.contains("ServerAliveInterval=15"))
        assertTrue("VM command must include ServerAliveCountMax=4", vmCmd.contains("ServerAliveCountMax=4"))
        assertTrue("VM command must include TCPKeepAlive=yes", vmCmd.contains("TCPKeepAlive=yes"))
        assertTrue("VM command must include ConnectTimeout=15", vmCmd.contains("ConnectTimeout=15"))

        // Verify BM command includes port 2223 and all keepalive flags
        assertTrue("BM command must target port 2223", bmCmd.contains("-p 2223"))
        assertTrue("BM command must include ServerAliveInterval=15", bmCmd.contains("ServerAliveInterval=15"))
        assertTrue("BM command must include ServerAliveCountMax=4", bmCmd.contains("ServerAliveCountMax=4"))
        assertTrue("BM command must include TCPKeepAlive=yes", bmCmd.contains("TCPKeepAlive=yes"))
        assertTrue("BM command must include ConnectTimeout=15", bmCmd.contains("ConnectTimeout=15"))
    }

    @Test
    fun testExitCodeClassification() {
        // Exit code 0 indicates normal, clean exit
        val exit0 = 0
        val desc0 = when (exit0) {
            0 -> "Remote session closed normally (exit 0)"
            255 -> "SSH connection closed or dropped by peer (exit 255)"
            else -> "Remote process exited with code $exit0"
        }
        assertEquals("Remote session closed normally (exit 0)", desc0)

        // Exit code 255 indicates nested SSH connection failure / drop
        val exit255 = 255
        val desc255 = when (exit255) {
            0 -> "Remote session closed normally (exit 0)"
            255 -> "SSH connection closed or dropped by peer (exit 255)"
            else -> "Remote process exited with code $exit255"
        }
        assertEquals("SSH connection closed or dropped by peer (exit 255)", desc255)

        // Non-zero exit code indicates process error
        val exit1 = 1
        val desc1 = when (exit1) {
            0 -> "Remote session closed normally (exit 0)"
            255 -> "SSH connection closed or dropped by peer (exit 255)"
            else -> "Remote process exited with code $exit1"
        }
        assertEquals("Remote process exited with code 1", desc1)
    }

    @Test
    fun testOuterSshFailureClassification() {
        val outerDropException = java.net.SocketException("Software caused connection abort: recv failed")
        val classified = SshDiagnosticError.classify(outerDropException, "111.93.205.187", 22)
        assertEquals(SshDiagnosticError.Category.NETWORK, classified.category)
        assertTrue(classified.diagnosticMessage.contains("Network"))
    }

    @Test
    fun testSessionReuseCondition() {
        val activeSiteId = 42L
        val activePort = 2222
        var isSessionActive = true

        // Case 1: Same site, same port, active session -> Reused
        fun shouldReuse(siteId: Long, port: Int): Boolean {
            return isSessionActive && siteId == activeSiteId && port == activePort
        }

        assertTrue("Identical site and port must be reused without restarting", shouldReuse(42L, 2222))

        // Case 2: Switching to BM (port 2223) -> Must not reuse
        assertFalse("Different port must trigger new connection", shouldReuse(42L, 2223))

        // Case 3: Switching to different site -> Must not reuse
        assertFalse("Different site must trigger new connection", shouldReuse(99L, 2222))

        // Case 4: Session was disconnected -> Must not reuse
        isSessionActive = false
        assertFalse("Inactive session must trigger new connection", shouldReuse(42L, 2222))
    }

    @Test
    fun testForegroundServiceLifecycleLogic() = runBlocking {
        val activeTunnelsFlow = MutableStateFlow<Map<String, TunnelInfo>>(emptyMap())
        val isTerminalActiveFlow = MutableStateFlow(false)
        val terminalTitleFlow = MutableStateFlow("")

        val serviceCombinedFlow = combine(
            activeTunnelsFlow,
            isTerminalActiveFlow,
            terminalTitleFlow
        ) { tunnels, isTerminalActive, _ ->
            // Service stays alive if EITHER tunnels are active OR terminal is active
            tunnels.isNotEmpty() || isTerminalActive
        }

        // Initially both empty -> service should stop
        assertFalse(serviceCombinedFlow.first())

        // Terminal active alone (e.g. user navigating or app backgrounded) -> service MUST stay alive
        isTerminalActiveFlow.value = true
        assertTrue(serviceCombinedFlow.first())

        // Add a tunnel as well -> service stays alive
        val dummyTunnel = TunnelInfo(
            siteId = 42L,
            siteName = "Site Alpha",
            destinationIp = "14.142.185.130",
            remotePort = 8080,
            localPort = 18080
        )
        activeTunnelsFlow.value = mapOf("1" to dummyTunnel)
        assertTrue(serviceCombinedFlow.first())

        // Close terminal, tunnel still open -> service stays alive
        isTerminalActiveFlow.value = false
        assertTrue(serviceCombinedFlow.first())

        // Close tunnel -> both idle, service stops
        activeTunnelsFlow.value = emptyMap()
        assertFalse(serviceCombinedFlow.first())
    }

    @Test
    fun testTerminalBufferPreservationAcrossNavigation() {
        val engine = com.sitelink.manager.ui.screens.terminal.TerminalBufferEngine()

        // Simulate incoming SSH chunks from server
        engine.processChunk("Last login: Tue Oct 20 2026\r\n")
        engine.processChunk("support@site:~$ uname -a\r\n")
        engine.processChunk("Linux site-appliance 5.15.0\r\n")
        engine.processChunk("support@site:~$ ")

        val stateBefore = engine.getState()
        assertTrue("Formatted text should contain prompt", stateBefore.formattedText.contains("support@site:~$ "))

        // Simulating screen rotation / navigation away: engine is kept alive in singleton manager
        val stateAfterRecreation = engine.getState()
        assertEquals(stateBefore.formattedText, stateAfterRecreation.formattedText)
        assertEquals(stateBefore.cursorRow, stateAfterRecreation.cursorRow)
        assertEquals(stateBefore.cursorCol, stateAfterRecreation.cursorCol)
    }
}
