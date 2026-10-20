package com.sitelink.manager.domain.validation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InputValidatorTest {

    @Test
    fun testValidIpv4Addresses() {
        assertTrue(InputValidator.isValidIpAddress("14.142.185.130"))
        assertTrue(InputValidator.isValidIpAddress("111.93.205.187"))
        assertTrue(InputValidator.isValidIpAddress("127.0.0.1"))
        assertTrue(InputValidator.isValidIpAddress("192.168.1.1"))
        assertTrue(InputValidator.isValidIpAddress("10.0.0.1"))
        assertTrue(InputValidator.isValidIpAddress("255.255.255.255"))
        assertTrue(InputValidator.isValidIpAddress("0.0.0.0"))
    }

    @Test
    fun testInvalidIpv4Addresses() {
        assertFalse(InputValidator.isValidIpAddress(""))
        assertFalse(InputValidator.isValidIpAddress("   "))
        assertFalse(InputValidator.isValidIpAddress("256.1.1.1"))
        assertFalse(InputValidator.isValidIpAddress("14.142.185"))
        assertFalse(InputValidator.isValidIpAddress("14.142.185.130.1"))
        assertFalse(InputValidator.isValidIpAddress("14.142.185.-1"))
        assertFalse(InputValidator.isValidIpAddress("abc.def.ghi.jkl"))
        assertFalse(InputValidator.isValidIpAddress("14.142.185.130; rm -rf /"))
    }

    @Test
    fun testValidPorts() {
        assertTrue(InputValidator.isValidPort(1))
        assertTrue(InputValidator.isValidPort(80))
        assertTrue(InputValidator.isValidPort(8080))
        assertTrue(InputValidator.isValidPort(8082))
        assertTrue(InputValidator.isValidPort(8083))
        assertTrue(InputValidator.isValidPort(2222))
        assertTrue(InputValidator.isValidPort(2223))
        assertTrue(InputValidator.isValidPort(65535))

        assertFalse(InputValidator.isValidPort(0))
        assertFalse(InputValidator.isValidPort(-1))
        assertFalse(InputValidator.isValidPort(65536))
        assertFalse(InputValidator.isValidPort(100000))
    }

    @Test
    fun testValidSiteNames() {
        assertTrue(InputValidator.isValidSiteName("ITC Grand Chola"))
        assertTrue(InputValidator.isValidSiteName("Mumbai Taj Gateway"))
        assertTrue(InputValidator.isValidSiteName("A"))

        assertFalse(InputValidator.isValidSiteName(""))
        assertFalse(InputValidator.isValidSiteName("   "))
        assertFalse(InputValidator.isValidSiteName("A".repeat(101)))
    }

    @Test
    fun testSanitizeForSshArgument() {
        assertEquals("14.142.185.130", InputValidator.sanitizeForSshArgument("14.142.185.130"))
        assertEquals("hotel-gateway.internal", InputValidator.sanitizeForSshArgument("hotel-gateway.internal"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun testSanitizeForSshArgumentRejectsInjection() {
        InputValidator.sanitizeForSshArgument("14.142.185.130; cat /etc/passwd")
    }

    @Test(expected = IllegalArgumentException::class)
    fun testSanitizeForSshArgumentRejectsPipes() {
        InputValidator.sanitizeForSshArgument("14.142.185.130 | nc -e /bin/sh")
    }
}
