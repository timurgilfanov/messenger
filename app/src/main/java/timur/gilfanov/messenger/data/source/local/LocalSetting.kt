package timur.gilfanov.messenger.data.source.local

import timur.gilfanov.messenger.data.source.local.database.entity.SyncStatus

data class LocalSetting<T>(
    val value: T,
    val localVersion: Int,
    val syncedVersion: Int,
    val serverVersion: Int,
    val modifiedAt: Long,
    val syncStatus: SyncStatus,
) {
    val isDirty: Boolean
        get() = localVersion > syncedVersion

    val needsSync: Boolean
        get() = syncStatus == SyncStatus.PENDING || syncStatus == SyncStatus.FAILED
}
