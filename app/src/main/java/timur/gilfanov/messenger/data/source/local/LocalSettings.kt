package timur.gilfanov.messenger.data.source.local

import timur.gilfanov.messenger.data.source.local.database.entity.SettingEntity
import timur.gilfanov.messenger.data.source.local.database.entity.SyncStatus
import timur.gilfanov.messenger.domain.entity.user.SettingKey
import timur.gilfanov.messenger.domain.entity.user.Settings
import timur.gilfanov.messenger.domain.entity.user.UiLanguage
import timur.gilfanov.messenger.domain.entity.user.UserId
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Type-safe wrapper for user settings stored locally with synchronization metadata.
 *
 * Provides a typed alternative to working directly with [SettingEntity] flat rows.
 * Each setting property (e.g., [uiLanguage]) is wrapped in [LocalSetting] which includes
 * version tracking and sync status for optimistic replication with Last Write Wins conflict
 * resolution.
 *
 * ## Conversion Methods:
 * - [toDomain]: Converts to domain [Settings] (strips sync metadata)
 * - [toSettingEntities]: Converts to database entities for persistence
 * - [fromEntities]: Factory method to construct from database entities with validation
 *
 * @property uiLanguage UI language setting with sync metadata
 */
data class LocalSettings(val uiLanguage: LocalSetting<UiLanguage>) {
    /**
     * Converts to domain Settings model by extracting values and discarding sync metadata.
     *
     * @return Domain model suitable for use case layer
     */
    fun toDomain(): Settings = Settings(uiLanguage = uiLanguage.value)

    /**
     * Converts to database entities for persistence.
     *
     * Maps each typed setting property to a flat [SettingEntity] row using appropriate
     * string storage format.
     *
     * @param userId The user who owns these settings
     * @return List of setting entities ready for database upsert
     */
    fun toSettingEntities(userId: UserId): List<SettingEntity> = listOf(
        SettingEntity(
            userId = userId.id.toString(),
            key = SettingKey.UI_LANGUAGE.key,
            value = uiLanguage.value.toStorageValue(),
            localVersion = uiLanguage.localVersion,
            syncedVersion = uiLanguage.syncedVersion,
            serverVersion = uiLanguage.serverVersion,
            modifiedAt = uiLanguage.modifiedAt,
            syncStatus = uiLanguage.syncStatus,
        ),
    )

    companion object {
        /**
         * Constructs LocalSettings from database entities with validation and defaults.
         *
         * **Validation Behavior:**
         * - Parses stored string values into typed domain objects
         * - Falls back to default values for invalid or missing settings
         * - Creates settings with version 1 and PENDING sync status when entity is missing
         * - Ignores unrecognized setting keys
         *
         * @param entities List of setting entities from database query
         * @param defaults Default values for settings (injected from repository layer)
         * @return Typed LocalSettings with all required settings populated
         */
        fun fromEntities(entities: List<SettingEntity>, defaults: Settings): LocalSettings {
            val now = Clock.System.now()

            val uiLanguageEntity = entities.find { it.key == SettingKey.UI_LANGUAGE.key }

            val uiLanguage: LocalSetting<UiLanguage> = if (uiLanguageEntity != null) {
                LocalSetting(
                    value = uiLanguageEntity.value.toUiLanguageOrDefault(defaults.uiLanguage),
                    defaultValue = defaults.uiLanguage,
                    localVersion = uiLanguageEntity.localVersion,
                    syncedVersion = uiLanguageEntity.syncedVersion,
                    serverVersion = uiLanguageEntity.serverVersion,
                    modifiedAt = uiLanguageEntity.modifiedAt,
                    syncStatus = uiLanguageEntity.syncStatus,
                )
            } else {
                defaultLocalSetting(value = defaults.uiLanguage, modifiedAt = now)
            }

            return LocalSettings(uiLanguage = uiLanguage)
        }
    }
}

internal fun <T> defaultLocalSetting(value: T, modifiedAt: Instant): LocalSetting<T> = LocalSetting(
    value = value,
    defaultValue = value,
    localVersion = 1,
    syncedVersion = 0,
    serverVersion = 0,
    modifiedAt = modifiedAt.toEpochMilliseconds(),
    syncStatus = SyncStatus.PENDING,
)
