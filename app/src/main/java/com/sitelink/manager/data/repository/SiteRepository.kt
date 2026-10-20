package com.sitelink.manager.data.repository

import com.sitelink.manager.data.db.SiteDao
import com.sitelink.manager.data.db.SiteEntity
import com.sitelink.manager.domain.model.Site
import com.sitelink.manager.domain.validation.InputValidator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class SiteRepository(private val siteDao: SiteDao) {

    val allSites: Flow<List<Site>> = siteDao.getAllSitesFlow().map { entities ->
        entities.map { it.toDomain() }
    }

    fun searchSites(query: String): Flow<List<Site>> = siteDao.searchSitesFlow(query).map { entities ->
        entities.map { it.toDomain() }
    }

    suspend fun getSiteById(id: Long): Site? {
        return siteDao.getSiteById(id)?.toDomain()
    }

    suspend fun addSite(site: Site): Result<Long> {
        val trimmedName = site.name.trim()
        val trimmedIp = site.ipAddress.trim()

        if (!InputValidator.isValidSiteName(trimmedName)) {
            return Result.failure(IllegalArgumentException("Invalid site name (1-100 characters required)"))
        }

        if (!InputValidator.isValidIpAddress(trimmedIp)) {
            return Result.failure(IllegalArgumentException("Invalid IP address format: $trimmedIp"))
        }

        // Check duplicates
        val existing = siteDao.getSiteByName(trimmedName)
        if (existing != null) {
            return Result.failure(IllegalArgumentException("A site with the name '$trimmedName' already exists"))
        }

        val entity = SiteEntity.fromDomain(site.copy(name = trimmedName, ipAddress = trimmedIp))
        val id = siteDao.insertSite(entity)
        return Result.success(id)
    }

    suspend fun updateSite(site: Site): Result<Unit> {
        val trimmedName = site.name.trim()
        val trimmedIp = site.ipAddress.trim()

        if (!InputValidator.isValidSiteName(trimmedName)) {
            return Result.failure(IllegalArgumentException("Invalid site name"))
        }

        if (!InputValidator.isValidIpAddress(trimmedIp)) {
            return Result.failure(IllegalArgumentException("Invalid IP address format"))
        }

        // Check if name is taken by another site
        val existing = siteDao.getSiteByName(trimmedName)
        if (existing != null && existing.id != site.id) {
            return Result.failure(IllegalArgumentException("Another site named '$trimmedName' already exists"))
        }

        siteDao.updateSite(
            SiteEntity.fromDomain(
                site.copy(
                    name = trimmedName,
                    ipAddress = trimmedIp,
                    updatedAt = System.currentTimeMillis()
                )
            )
        )
        return Result.success(Unit)
    }

    suspend fun deleteSite(site: Site) {
        siteDao.deleteSite(SiteEntity.fromDomain(site))
    }
}
