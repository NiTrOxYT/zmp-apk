package com.sitelink.manager.domain.model

sealed interface SshSessionState {
    data object Disconnected : SshSessionState
    data class Connecting(val message: String = "Connecting to office server...") : SshSessionState
    data class HostVerificationRequired(
        val host: String,
        val port: Int,
        val keyType: String,
        val fingerprint: String,
        val rawKey: ByteArray
    ) : SshSessionState {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            other as HostVerificationRequired
            return host == other.host && port == other.port && fingerprint == other.fingerprint
        }
        override fun hashCode(): Int = host.hashCode() * 31 + fingerprint.hashCode()
    }
    data class Connected(val sessionHost: String, val username: String) : SshSessionState
    data class Error(val message: String, val throwable: Throwable? = null) : SshSessionState
}
