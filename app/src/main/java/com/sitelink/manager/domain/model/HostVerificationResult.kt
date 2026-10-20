package com.sitelink.manager.domain.model

sealed interface HostVerificationResult {
    data object Trusted : HostVerificationResult
    data class UnknownHost(
        val host: String,
        val port: Int,
        val keyType: String,
        val fingerprint: String,
        val rawKey: ByteArray
    ) : HostVerificationResult {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            other as UnknownHost
            return host == other.host && port == other.port && fingerprint == other.fingerprint
        }
        override fun hashCode(): Int = host.hashCode() * 31 + fingerprint.hashCode()
    }
    data class KeyChanged(
        val host: String,
        val port: Int,
        val keyType: String,
        val newFingerprint: String,
        val oldFingerprint: String
    ) : HostVerificationResult
}
