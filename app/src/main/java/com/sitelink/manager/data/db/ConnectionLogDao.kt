package com.sitelink.manager.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ConnectionLogDao {
    @Query("SELECT * FROM connection_logs ORDER BY timestamp DESC LIMIT 100")
    fun getRecentLogsFlow(): Flow<List<ConnectionLogEntity>>

    @Insert
    suspend fun insertLog(log: ConnectionLogEntity): Long

    @Query("DELETE FROM connection_logs")
    suspend fun clearLogs()
}
