package com.sitelink.manager.domain.validation

object InputValidator {

    private val IPV4_REGEX = Regex(
        "^((25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)$"
    )

    private val IPV6_REGEX = Regex(
        "^(([0-9a-fA-F]{1,4}:){7,7}[0-9a-fA-F]{1,4}|([0-9a-fA-F]{1,4}:){1,7}:|([0-9a-fA-F]{1,4}:){1,6}:[0-9a-fA-F]{1,4}|([0-9a-fA-F]{1,4}:){1,5}(:[0-9a-fA-F]{1,4}){1,2}|([0-9a-fA-F]{1,4}:){1,4}(:[0-9a-fA-F]{1,4}){1,3}|([0-9a-fA-F]{1,4}:){1,3}(:[0-9a-fA-F]{1,4}){1,4}|([0-9a-fA-F]{1,4}:){1,2}(:[0-9a-fA-F]{1,4}){1,5}|[0-9a-fA-F]{1,4}:((:[0-9a-fA-F]{1,4}){1,6})|:((:[0-9a-fA-F]{1,4}){1,7}|:))$"
    )

    fun isValidIpAddress(ip: String): Boolean {
        val trimmed = ip.trim()
        if (trimmed.isEmpty()) return false
        return IPV4_REGEX.matches(trimmed) || IPV6_REGEX.matches(trimmed)
    }

    fun isValidPort(port: Int): Boolean {
        return port in 1..65535
    }

    fun isValidSiteName(name: String): Boolean {
        val trimmed = name.trim()
        return trimmed.isNotEmpty() && trimmed.length <= 100
    }

    /**
     * Ensures an IP or hostname contains only characters safe for SSH parameters,
     * preventing any shell command injection.
     */
    fun sanitizeForSshArgument(input: String): String {
        val clean = input.trim()
        require(isValidIpAddress(clean) || clean.matches(Regex("^[a-zA-Z0-9.-]+$"))) {
            "Invalid character sequence in SSH target host: $input"
        }
        return clean
    }
}
