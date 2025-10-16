package timur.gilfanov.messenger.domain.entity.user

import kotlin.time.Instant

/**
 * Sync-related metadata for a set of [Settings].
 *
 * The repository relies on these timestamps to decide when to trigger recovery, back up
 * local edits, or fall back to defaults.
 *
 * @property isDefault `true` when the settings come from the built-in defaults and have not been
 * altered by the user yet.
 * @property lastModifiedAt Instant of the most recent change applied to the settings, regardless of
 * whether it was stored locally or fetched from remote backup.
 * @property lastSyncedAt Instant of the last successful synchronization with remote storage, or
 * `null` when no backup has been performed.
 */
data class SettingsMetadata(
    val isDefault: Boolean,
    val lastModifiedAt: Instant,
    val lastSyncedAt: Instant?,
) {
    /**
     * Derived view describing the current lifecycle state of the settings.
     */
    val state: SettingsState
        get() = when {
            lastModifiedAt == Instant.fromEpochMilliseconds(0) -> SettingsState.EMPTY
            isDefault && lastSyncedAt == null -> SettingsState.DEFAULT
            lastSyncedAt != null && lastModifiedAt == lastSyncedAt ->
                SettingsState.IN_SYNC_WITH_REMOTE
            lastSyncedAt != null && lastModifiedAt > lastSyncedAt -> SettingsState.MODIFIED
            else -> SettingsState.EMPTY
        }

    companion object {
        val EMPTY = SettingsMetadata(
            isDefault = false,
            lastModifiedAt = Instant.fromEpochMilliseconds(0),
            lastSyncedAt = null,
        )
    }
}
