package com.sitelink.manager.domain.model

import com.sitelink.manager.domain.validation.InputValidator

data class Site(
    val id: Long = 0,
    val name: String,
    val ipAddress: String,
    val description: String = "",
    val notes: String = "",
    val portalPorts: String = DEFAULT_PORTAL_PORTS,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    /**
     * Returns the parsed list of portal ports configured for this site.
     * Defaults to [8080, 8082, 8083] if the string is empty or invalid.
     */
    fun parsePortalPorts(): List<Int> {
        val parsed = InputValidator.parsePortsString(portalPorts)
        return parsed.ifEmpty { DEFAULT_PORT_LIST }
    }

    companion object {
        const val DEFAULT_PORTAL_PORTS = "8080, 8082, 8083"
        val DEFAULT_PORT_LIST = listOf(8080, 8082, 8083)
    }
}
