package com.sitelink.manager.data.ssh

import com.sitelink.manager.domain.model.TunnelInfo
import com.sitelink.manager.domain.model.TunnelStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TunnelStateTest {

    @Test
    fun testTunnelUrlFormation() {
        val tunnel = TunnelInfo(
            siteId = 1L,
            siteName = "ITC Grand Chola",
            destinationIp = "14.142.185.130",
            remotePort = 8080,
            localPort = 18080,
            status = TunnelStatus.CONNECTED
        )

        assertEquals("http://127.0.0.1:18080/zmp/login.do", tunnel.localUrl)
        assertTrue(tunnel.localUrl.startsWith("http://127.0.0.1:"))
        assertTrue(tunnel.localUrl.endsWith("/zmp/login.do"))
    }

    @Test
    fun testTunnelStateTransitions() {
        var tunnel = TunnelInfo(
            siteId = 1L,
            siteName = "ITC Grand Chola",
            destinationIp = "14.142.185.130",
            remotePort = 8082,
            localPort = 18082,
            status = TunnelStatus.CONNECTING
        )
        assertEquals(TunnelStatus.CONNECTING, tunnel.status)

        // Transition to connected
        tunnel = tunnel.copy(status = TunnelStatus.CONNECTED)
        assertEquals(TunnelStatus.CONNECTED, tunnel.status)

        // Transition to disconnected
        tunnel = tunnel.copy(status = TunnelStatus.DISCONNECTED)
        assertEquals(TunnelStatus.DISCONNECTED, tunnel.status)
    }

    @Test
    fun testTunnelKeyGeneration() {
        fun makeKey(siteId: Long, remotePort: Int): String = "${siteId}_$remotePort"

        val key1 = makeKey(1L, 8080)
        val key2 = makeKey(1L, 8082)
        val key3 = makeKey(2L, 8080)

        assertEquals("1_8080", key1)
        assertEquals("1_8082", key2)
        assertEquals("2_8080", key3)

        val map = mutableMapOf<String, TunnelInfo>()
        val t1 = TunnelInfo(1L, "Site A", "10.0.0.1", 8080, 18080)
        map[key1] = t1

        // Reuse check
        assertTrue("Map contains existing tunnel key", map.containsKey(key1))
        assertEquals(18080, map[key1]?.localPort)
    }
}
