package timur.gilfanov.messenger.data.source.local

import timur.gilfanov.messenger.data.source.local.database.entity.SettingEntity
import timur.gilfanov.messenger.data.source.local.database.entity.SyncStatus
import timur.gilfanov.messenger.domain.entity.user.SettingKey
import timur.gilfanov.messenger.domain.entity.user.Settings
import timur.gilfanov.messenger.domain.entity.user.UiLanguage
import timur.gilfanov.messenger.domain.entity.user.UserId

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
         * - Falls back to English for invalid UI language values
         * - Creates default settings (English, version 1, PENDING) when entity is missing
         * - Ignores unrecognized setting keys
         *
         * @param entities List of setting entities from database query
         * @return Typed LocalSettings with all required settings populated
         */
        fun fromEntities(entities: List<SettingEntity>): LocalSettings {
            val uiLanguageEntity = entities.find { it.key == SettingKey.UI_LANGUAGE.key }

            val uiLanguage: LocalSetting<UiLanguage> = if (uiLanguageEntity != null) {
                LocalSetting(
                    value = uiLanguageEntity.value.toUiLanguageOrDefault(UiLanguage.English),
                    localVersion = uiLanguageEntity.localVersion,
                    syncedVersion = uiLanguageEntity.syncedVersion,
                    serverVersion = uiLanguageEntity.serverVersion,
                    modifiedAt = uiLanguageEntity.modifiedAt,
                    syncStatus = uiLanguageEntity.syncStatus,
                )
            } else {
                LocalSetting(
                    value = UiLanguage.English, // todo should defaults be determined by repository?
                    localVersion = 1,
                    syncedVersion = 0,
                    serverVersion = 0,
                    modifiedAt = System.currentTimeMillis(),
                    syncStatus = SyncStatus.PENDING,
                )
            }

            return LocalSettings(uiLanguage = uiLanguage)
        }
    }
}
