package timur.gilfanov.messenger.data.source.local.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.datetime.Instant

/**
 * Room entity for storing synchronization metadata.
 * Used to track the last sync timestamp for delta synchronization.
 */
@Entity(tableName = "sync_metadata")
data class SyncMetadataEntity(
    @PrimaryKey
    val key: String = "last_sync", // Single row for now, can be extended later
    val lastSyncTimestamp: Instant?,
    val syncStatus: SyncStatus = SyncStatus.IDLE,
    val lastError: String? = null,
    val updatedAt: Instant,
)

enum class SyncStatus {
    IDLE,
    IN_PROGRESS,
    ERROR,
}

