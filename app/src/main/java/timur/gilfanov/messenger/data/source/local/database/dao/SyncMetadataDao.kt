package timur.gilfanov.messenger.data.source.local.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import timur.gilfanov.messenger.data.source.local.database.entity.SyncMetadataEntity
import timur.gilfanov.messenger.data.source.local.database.entity.SyncStatus

/**
 * Data Access Object for synchronization metadata operations.
 */
@Dao
interface SyncMetadataDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun updateSyncMetadata(metadata: SyncMetadataEntity)

    @Query("SELECT * FROM sync_metadata WHERE key = :key")
    suspend fun getSyncMetadata(key: String = "last_sync"): SyncMetadataEntity?

    @Query("SELECT lastSyncTimestamp FROM sync_metadata WHERE key = :key")
    suspend fun getLastSyncTimestamp(key: String = "last_sync"): Long?

    @Query(
        "UPDATE sync_metadata SET lastSyncTimestamp = :timestamp, updatedAt = :updatedAt WHERE key = :key",
    )
    suspend fun updateLastSyncTimestamp(
        timestamp: Long,
        updatedAt: Long,
        key: String = "last_sync",
    )

    @Query("UPDATE sync_metadata SET syncStatus = :status, updatedAt = :updatedAt WHERE key = :key")
    suspend fun updateSyncStatus(status: SyncStatus, updatedAt: Long, key: String = "last_sync")

    @Query(
        "UPDATE sync_metadata SET lastError = :error, syncStatus = :status, updatedAt = :updatedAt WHERE key = :key",
    )
    suspend fun updateSyncError(
        error: String?,
        status: SyncStatus = SyncStatus.ERROR,
        updatedAt: Long,
        key: String = "last_sync",
    )

    @Query("DELETE FROM sync_metadata")
    suspend fun clearAllSyncMetadata()
}

