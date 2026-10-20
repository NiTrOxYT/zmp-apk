package com.sitelink.manager.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.sitelink.manager.domain.model.Site

@Entity(
    tableName = "sites",
    indices = [
        Index(value = ["name"], unique = true),
        Index(value = ["ipAddress"], unique = false)
    ]
)
data class SiteEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val ipAddress: String,
    val description: String = "",
    val notes: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    fun toDomain(): Site = Site(
        id = id,
        name = name,
        ipAddress = ipAddress,
        description = description,
        notes = notes,
        createdAt = createdAt,
        updatedAt = updatedAt
    )

    companion object {
        fun fromDomain(site: Site): SiteEntity = SiteEntity(
            id = site.id,
            name = site.name.trim(),
            ipAddress = site.ipAddress.trim(),
            description = site.description.trim(),
            notes = site.notes.trim(),
            createdAt = site.createdAt,
            updatedAt = site.updatedAt
        )
    }
}
