package com.sitelink.manager.domain.model

data class TunnelInfo(
    val siteId: Long,
    val siteName: String,
    val destinationIp: String,
    val remotePort: Int,
    val localPort: Int,
    val localUrl: String = "http://127.0.0.1:$localPort/zmp/login.do",
    val status: TunnelStatus = TunnelStatus.CONNECTED,
    val establishedAt: Long = System.currentTimeMillis()
)

enum class TunnelStatus {
    CONNECTING,
    CONNECTED,
    FAILED,
    DISCONNECTED
}
