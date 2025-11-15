package timur.gilfanov.messenger.data.source.local

import kotlinx.coroutines.flow.Flow
import timur.gilfanov.messenger.data.source.local.database.entity.SettingEntity
import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.user.UserId

/**
 * Local data source for user settings data.
 *
 * Provides Room-based access to typed settings with sync metadata.
 * Encapsulates all mapping logic between database entities and typed LocalSettings.
 */
interface LocalSettingsDataSource {
    /**
     * Observes all settings for a specific user as a typed LocalSettings object.
     *
     * Handles transient errors internally with retry logic. Permanent errors propagate
     * to repository layer for error mapping.
     *
     * @param userId The unique identifier of the user
     * @return Flow emitting typed LocalSettings, or null if settings are not initialized
     */
    fun observe(userId: UserId): Flow<LocalSettings?>

    /**
     * Retrieves a specific setting entity.
     *
     * @param userId The unique identifier of the user
     * @param key The setting key
     * @return Success with setting entity, or failure with SettingNotFound if not exists,
     *         or failure with infrastructure error
     */
    suspend fun getSetting(
        userId: UserId,
        key: String,
    ): ResultWithError<SettingEntity, GetSettingError>

    /**
     * Updates or inserts a setting entity.
     *
     * @param entity The setting entity to update
     * @return Success or failure with error
     */
    suspend fun upsert(entity: SettingEntity): ResultWithError<Unit, UpsertSettingError>

    /**
     * Updates or inserts multiple setting entities in a single transaction.
     *
     * @param entities The setting entities to update
     * @return Success or failure with error
     */
    suspend fun upsert(entities: List<SettingEntity>): ResultWithError<Unit, UpsertSettingError>

    /**
     * Atomically reads, transforms, and updates settings.
     *
     * Provides atomic read-modify-write semantics using database transactions.
     * The transform function receives the current LocalSettings and returns
     * the updated LocalSettings. If settings don't exist, returns SettingsNotFound error.
     *
     * @param userId The unique identifier of the user
     * @param transform Function to transform the current LocalSettings
     * @return Success or failure with error
     */
    suspend fun upsert(
        userId: UserId,
        transform: (LocalSettings) -> LocalSettings,
    ): ResultWithError<Unit, UpsertSettingError>

    /**
     * Retrieves all settings that need synchronization.
     *
     * @return Success with list of setting entities or failure with error
     */
    suspend fun getUnsyncedSettings(): ResultWithError<
        List<SettingEntity>,
        GetUnsyncedSettingsError,
        >
}
