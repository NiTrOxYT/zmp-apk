package com.sitelink.manager.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface SiteDao {
    @Query("SELECT * FROM sites ORDER BY name ASC")
    fun getAllSitesFlow(): Flow<List<SiteEntity>>

    @Query("SELECT * FROM sites WHERE id = :id LIMIT 1")
    suspend fun getSiteById(id: Long): SiteEntity?

    @Query("SELECT * FROM sites WHERE LOWER(name) = LOWER(:name) LIMIT 1")
    suspend fun getSiteByName(name: String): SiteEntity?

    @Query("SELECT * FROM sites WHERE LOWER(name) LIKE '%' || LOWER(:query) || '%' OR ipAddress LIKE '%' || :query || '%' ORDER BY name ASC")
    fun searchSitesFlow(query: String): Flow<List<SiteEntity>>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertSite(site: SiteEntity): Long

    @Update
    suspend fun updateSite(site: SiteEntity)

    @Delete
    suspend fun deleteSite(site: SiteEntity)

    @Query("SELECT COUNT(*) FROM sites")
    suspend fun getCount(): Int
}
