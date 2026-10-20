package com.sitelink.manager.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TrustedHostKeyDao {
    @Query("SELECT * FROM trusted_host_keys WHERE hostPortKey = :hostPortKey LIMIT 1")
    suspend fun getTrustedKey(hostPortKey: String): TrustedHostKeyEntity?

    @Query("SELECT * FROM trusted_host_keys WHERE hostPortKey = :key1 OR hostPortKey = :key2 LIMIT 1")
    suspend fun findTrustedKey(key1: String, key2: String): TrustedHostKeyEntity?

    @Query("SELECT * FROM trusted_host_keys ORDER BY trustedAt DESC")
    fun getAllTrustedKeysFlow(): Flow<List<TrustedHostKeyEntity>>

    @Query("SELECT * FROM trusted_host_keys")
    suspend fun getAllTrustedKeysList(): List<TrustedHostKeyEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(key: TrustedHostKeyEntity)

    @Query("DELETE FROM trusted_host_keys WHERE hostPortKey = :hostPortKey")
    suspend fun deleteTrustedKey(hostPortKey: String)
}
