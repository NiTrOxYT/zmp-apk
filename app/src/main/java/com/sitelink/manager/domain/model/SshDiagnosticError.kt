package com.sitelink.manager.domain.model

sealed class SshDiagnosticError(
    val category: Category,
    val diagnosticMessage: String,
    cause: Throwable? = null
) : Exception(diagnosticMessage, cause) {

    enum class Category(val label: String) {
        HOST_KEY("Host-Key Verification"),
        AUTHENTICATION("SSH Authentication"),
        NETWORK("Network Connection"),
        PORT_FORWARDING("Port Forwarding"),
        NESTED_TERMINAL("Nested Terminal"),
        GENERAL("SSH Error")
    }

    class HostKey(message: String, cause: Throwable? = null) :
        SshDiagnosticError(Category.HOST_KEY, message, cause)

    class Auth(message: String, cause: Throwable? = null) :
        SshDiagnosticError(Category.AUTHENTICATION, message, cause)

    class Network(message: String, cause: Throwable? = null) :
        SshDiagnosticError(Category.NETWORK, message, cause)

    class PortForward(message: String, cause: Throwable? = null) :
        SshDiagnosticError(Category.PORT_FORWARDING, message, cause)

    class NestedTerminal(message: String, cause: Throwable? = null) :
        SshDiagnosticError(Category.NESTED_TERMINAL, message, cause)

    class General(message: String, cause: Throwable? = null) :
        SshDiagnosticError(Category.GENERAL, message, cause)

    fun getFormattedError(): String = "${category.label}: $diagnosticMessage"

    companion object {
        fun classify(throwable: Throwable, contextHost: String = "", contextPort: Int = 0): SshDiagnosticError {
            if (throwable is SshDiagnosticError) return throwable

            val msg = throwable.message ?: throwable.javaClass.simpleName
            val lower = msg.lowercase()
            val cause = throwable.cause
            val causeMsg = cause?.message?.lowercase() ?: ""

            // 1. Host-Key repository or verification errors
            if (lower.contains("hostkey") || lower.contains("host-key") ||
                lower.contains("fingerprint") || lower.contains("reject hostkey") ||
                lower.contains("host key") || lower.contains("customhostkeyrepository") ||
                lower.contains("gethostkey") || causeMsg.contains("customhostkeyrepository") ||
                causeMsg.contains("gethostkey")
            ) {
                val detail = if (lower.contains("reject") || lower.contains("rejected")) {
                    "Host key was rejected by policy or user verification."
                } else if (lower.contains("changed")) {
                    "SECURITY WARNING: Host key has changed! Potential Man-In-The-Middle attack detected."
                } else {
                    msg
                }
                val target = if (contextHost.isNotEmpty()) " on $contextHost" else ""
                return HostKey("Host-key repository/verification error$target: $detail", throwable)
            }

            // 2. Authentication failures
            if (lower.contains("auth fail") || lower.contains("authentication fail") ||
                lower.contains("auth cancel") || lower.contains("userauth") ||
                lower.contains("permission denied") || lower.contains("bad password")
            ) {
                val target = if (contextHost.isNotEmpty()) " on $contextHost" else ""
                return Auth(
                    "SSH authentication failed$target. Please verify your SSH username and password in Settings.",
                    throwable
                )
            }

            // 3. Remote port forwarding errors (e.g. direct-tcpip, remote site port unreachable)
            if (lower.contains("port forward") || lower.contains("portforward") ||
                lower.contains("direct-tcpip") || lower.contains("channel open failed") ||
                lower.contains("cannot create a port forwarding") || lower.contains("forwarding probe failed")
            ) {
                return PortForward(
                    "Remote port forwarding failed: $msg. The remote hotel site port may be closed, firewalled, or unreachable from the office server.",
                    throwable
                )
            }

            // 4. Nested VM/BM SSH failures
            if (lower.contains("nested") || lower.contains("terminal") ||
                lower.contains("pty") || lower.contains("shell") ||
                lower.contains("channel exec")
            ) {
                return NestedTerminal("Nested VM/BM terminal session failed: $msg", throwable)
            }

            // 5. Network connection failures (to office gateway server)
            if (throwable is java.net.UnknownHostException || cause is java.net.UnknownHostException ||
                throwable is java.net.ConnectException || cause is java.net.ConnectException ||
                throwable is java.net.NoRouteToHostException || cause is java.net.NoRouteToHostException ||
                throwable is java.net.SocketTimeoutException || cause is java.net.SocketTimeoutException ||
                throwable is java.net.SocketException || cause is java.net.SocketException ||
                lower.contains("connection refused") || causeMsg.contains("connection refused") ||
                lower.contains("connect timed out") || causeMsg.contains("connect timed out") ||
                lower.contains("timeout") || causeMsg.contains("timeout") ||
                lower.contains("network is unreachable") || causeMsg.contains("network is unreachable") ||
                lower.contains("route to host") || causeMsg.contains("route to host") ||
                lower.contains("session.connect:") || lower.contains("connection closed") ||
                lower.contains("connection reset")
            ) {
                val target = if (contextHost.isNotEmpty()) " to $contextHost:$contextPort" else ""
                return Network(
                    "Network connection failed$target: ${throwable.message ?: "Server unreachable or connection timed out"}. Verify device internet and host IP.",
                    throwable
                )
            }

            return General("SSH Error: $msg", throwable)
        }
    }
}
