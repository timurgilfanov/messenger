package timur.gilfanov.messenger.data.source.local

import timur.gilfanov.messenger.data.source.local.database.entity.SyncStatus

/**
 * A setting value with synchronization metadata for conflict-free replication.
 *
 * Version Semantics:
 * - [localVersion]: Starts at 1, increments on each local change
 * - [syncedVersion]: The [localVersion] value when last successfully synced to server
 * - [serverVersion]: The version number from the server for this setting
 *   - **0 means "no server version known"** (never synced or server doesn't have this setting)
 *   - Server MUST use versions starting from 1
 *   - Used for conflict detection during synchronization
 *
 * Synchronization States:
 * - Clean: [localVersion] == [syncedVersion] (no pending local changes)
 * - Dirty: [localVersion] > [syncedVersion] (local changes not yet synced)
 *
 * @property value The setting value
 * @property localVersion Version counter incremented on each local modification
 * @property syncedVersion Last successfully synced [localVersion]
 * @property serverVersion Server's version for this setting (0 = unknown/never synced)
 * @property modifiedAt Timestamp of last local modification
 * @property syncStatus Current sync state (SYNCED, PENDING, FAILED, SYNCING)
 */
data class LocalSetting<T>(
    val value: T,
    val localVersion: Int,
    val syncedVersion: Int,
    val serverVersion: Int,
    val modifiedAt: Long,
    val syncStatus: SyncStatus,
) {
    init {
        require(localVersion >= 1) {
            "localVersion must be >= 1 (starts at 1), got: $localVersion"
        }
    }

    val isDirty: Boolean
        get() = localVersion > syncedVersion

    val needsSync: Boolean
        get() = syncStatus == SyncStatus.PENDING || syncStatus == SyncStatus.FAILED
}
