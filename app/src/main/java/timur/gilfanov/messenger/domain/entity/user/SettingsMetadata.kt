package timur.gilfanov.messenger.domain.entity.user

import kotlin.time.Instant

data class SettingsMetadata(
    val isDefault: Boolean,
    val lastModifiedAt: Instant,
    val lastSyncedAt: Instant?,
) {
    val source: SettingsSource
        get() = when {
            lastModifiedAt == Instant.fromEpochMilliseconds(0) -> SettingsSource.EMPTY
            isDefault && lastSyncedAt == null -> SettingsSource.DEFAULT
            lastSyncedAt != null && lastModifiedAt == lastSyncedAt -> SettingsSource.REMOTE
            lastSyncedAt != null && lastModifiedAt > lastSyncedAt -> SettingsSource.LOCAL_MODIFIED
            else -> SettingsSource.EMPTY
        }

    companion object {
        val EMPTY = SettingsMetadata(
            isDefault = false,
            lastModifiedAt = Instant.fromEpochMilliseconds(0),
            lastSyncedAt = null,
        )
    }
}
