package com.sitelink.manager.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "connection_logs")
data class ConnectionLogEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val siteId: Long?,
    val siteName: String,
    val connectionType: String,
    val destinationIp: String,
    val destinationPort: Int,
    val localPort: Int?,
    val status: String,
    val timestamp: Long = System.currentTimeMillis(),
    val details: String? = null
)
