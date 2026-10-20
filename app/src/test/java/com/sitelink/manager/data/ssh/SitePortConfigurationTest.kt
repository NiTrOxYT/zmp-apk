package com.sitelink.manager.data.ssh

import com.sitelink.manager.data.db.AppDatabase
import com.sitelink.manager.data.db.SiteEntity
import com.sitelink.manager.domain.model.Site
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SitePortConfigurationTest {

    @Test
    fun testDefaultSitePortalPorts() {
        val site = Site(
            name = "ITC Grand Chola",
            ipAddress = "14.142.185.130"
        )
        assertEquals(Site.DEFAULT_PORTAL_PORTS, site.portalPorts)
        assertEquals(listOf(8080, 8082, 8083), site.parsePortalPorts())
    }

    @Test
    fun testCustomPortalPortsPerSite() {
        val siteWithCustomPorts = Site(
            name = "Taj Coromandel",
            ipAddress = "10.20.30.40",
            portalPorts = "8080, 8085, 9000, 80"
        )
        val parsed = siteWithCustomPorts.parsePortalPorts()
        assertEquals(listOf(8080, 8085, 9000, 80), parsed)
        assertEquals(4, parsed.size)
    }

    @Test
    fun testCustomSinglePortPerSite() {
        val siteSinglePort = Site(
            name = "Express Inn",
            ipAddress = "192.168.1.100",
            portalPorts = "8080"
        )
        val parsed = siteSinglePort.parsePortalPorts()
        assertEquals(listOf(8080), parsed)
    }

    @Test
    fun testInvalidOrEmptyPortalPortsFallsBackToDefault() {
        val emptyPortsSite = Site(
            name = "Fallback Site",
            ipAddress = "192.168.1.10",
            portalPorts = "   "
        )
        assertEquals(listOf(8080, 8082, 8083), emptyPortsSite.parsePortalPorts())

        val invalidPortsSite = Site(
            name = "Invalid Port Site",
            ipAddress = "192.168.1.10",
            portalPorts = "abc, 0, -5"
        )
        assertEquals(listOf(8080, 8082, 8083), invalidPortsSite.parsePortalPorts())
    }

    @Test
    fun testSiteEntityToAndFromDomainPreservesCustomPorts() {
        val domainSite = Site(
            id = 5L,
            name = "Boutique Hotel",
            ipAddress = "172.16.0.5",
            portalPorts = "8080, 8081, 8082, 8083, 8084"
        )

        val entity = SiteEntity.fromDomain(domainSite)
        assertEquals("8080, 8081, 8082, 8083, 8084", entity.portalPorts)

        val mappedBack = entity.toDomain()
        assertEquals(domainSite.id, mappedBack.id)
        assertEquals(domainSite.name, mappedBack.name)
        assertEquals(domainSite.ipAddress, mappedBack.ipAddress)
        assertEquals(domainSite.portalPorts, mappedBack.portalPorts)
        assertEquals(listOf(8080, 8081, 8082, 8083, 8084), mappedBack.parsePortalPorts())
    }

    @Test
    fun testTunnelKeysForCustomSitePorts() {
        val site = Site(
            id = 77L,
            name = "Site 77",
            ipAddress = "10.0.0.1",
            portalPorts = "8080, 8085, 9090"
        )

        val expectedKeys = listOf("77_8080", "77_8085", "77_9090")
        val generatedKeys = site.parsePortalPorts().map { port -> "${site.id}_$port" }

        assertEquals(expectedKeys, generatedKeys)
    }
}
