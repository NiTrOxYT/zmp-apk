package com.sitelink.manager.data.ssh

import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetAddress
import java.net.ServerSocket

class SshPortSelectionTest {

    private fun findAvailablePort(preferredStart: Int): Int {
        for (port in preferredStart..preferredStart + 500) {
            try {
                ServerSocket(port, 1, InetAddress.getByName("127.0.0.1")).use {
                    return port
                }
            } catch (_: Exception) {}
        }
        ServerSocket(0, 1, InetAddress.getByName("127.0.0.1")).use { socket ->
            return socket.localPort
        }
    }

    @Test
    fun testPortDiscoveryReturnsValidPort() {
        val port = findAvailablePort(18080)
        assertTrue("Port should be in valid range", port in 1024..65535)
    }

    @Test
    fun testDistinctPortsForSimultaneousTunnels() {
        // Simulate two ports allocated for two different tunnels
        val port1 = findAvailablePort(18080)
        // Bind port1 temporarily
        val socket1 = ServerSocket(port1, 1, InetAddress.getByName("127.0.0.1"))
        try {
            val port2 = findAvailablePort(18080)
            assertNotEquals("Port selection must not collide with occupied port", port1, port2)
            assertTrue("Allocated port 2 should be valid", port2 in 1024..65535)
        } finally {
            socket1.close()
        }
    }

    @Test
    fun testPortBindsToLoopbackOnly() {
        val port = findAvailablePort(19000)
        ServerSocket(port, 1, InetAddress.getByName("127.0.0.1")).use { serverSocket ->
            val boundAddress = serverSocket.inetAddress.hostAddress
            assertTrue("Must bind strictly to loopback", boundAddress == "127.0.0.1")
        }
    }
}
