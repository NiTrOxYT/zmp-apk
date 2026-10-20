package com.sitelink.manager.domain.model

data class Site(
    val id: Long = 0,
    val name: String,
    val ipAddress: String,
    val description: String = "",
    val notes: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
