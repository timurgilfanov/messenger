package timur.gilfanov.messenger.data.source.local.database.entity

import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "settings",
    primaryKeys = ["userId", "key"],
    indices = [
        Index(value = ["localVersion", "syncedVersion"]),
        Index(value = ["modifiedAt"]),
    ],
)
data class SettingEntity(
    val userId: String,
    val key: String,
    val value: String,
    val localVersion: Int,
    val syncedVersion: Int,
    val serverVersion: Int,
    val modifiedAt: Long,
    val syncStatus: SyncStatus,
)

enum class SyncStatus {
    SYNCED,
    PENDING,
    SYNCING,
    FAILED,
}
