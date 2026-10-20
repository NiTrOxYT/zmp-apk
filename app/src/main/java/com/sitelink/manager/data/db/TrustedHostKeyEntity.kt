package com.sitelink.manager.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "trusted_host_keys")
data class TrustedHostKeyEntity(
    @PrimaryKey
    val hostPortKey: String, // e.g. "111.93.205.187:22"
    val host: String,
    val port: Int,
    val keyType: String,
    val fingerprint: String, // SHA256 base64 fingerprint
    val rawKeyBase64: String,
    val trustedAt: Long = System.currentTimeMillis()
)
