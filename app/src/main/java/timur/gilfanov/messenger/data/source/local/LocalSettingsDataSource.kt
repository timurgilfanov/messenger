package timur.gilfanov.messenger.data.source.local

import kotlinx.coroutines.flow.Flow
import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.profile.UserId
import timur.gilfanov.messenger.domain.entity.settings.SettingKey

/**
 * Local data source for user settings data.
 *
 * Provides persistent access to typed settings with sync metadata.
 * Encapsulates all mapping logic between storage entities and typed domain values.
 *
 * Uses [TypedLocalSetting] at the boundary to hide storage implementation details
 * and enforce validation when converting from storage to domain types.
 */
interface LocalSettingsDataSource {
    /**
     * Observes all settings for a specific user as a typed LocalSettings object.
     *
     * Handles transient errors internally with retry logic. Permanent errors propagate
     * to repository layer for error mapping.
     *
     * @param userId The unique identifier of the user
     * @return Flow emitting Success with LocalSettings when initialized, or Failure with
     *         NoSettings error when not initialized, or Failure with other errors for
     *         infrastructure issues
     */
    fun observe(
        userId: UserId,
    ): Flow<ResultWithError<LocalSettings, GetSettingsLocalDataSourceError>>

    /**
     * Retrieves a specific typed setting with sync metadata.
     *
     * @param userId The unique identifier of the user
     * @param key The setting key (domain type)
     * @return Success with typed setting, or failure with SettingNotFound if not exists,
     *         or failure with infrastructure error
     */
    suspend fun getSetting(
        userId: UserId,
        key: SettingKey,
    ): ResultWithError<TypedLocalSetting, GetSettingError>

    /**
     * Updates or inserts a typed setting.
     *
     * @param userId The user ID that owns this setting
     * @param setting The typed setting to update
     * @return Success or failure with error
     */
    suspend fun upsert(
        userId: UserId,
        setting: TypedLocalSetting,
    ): ResultWithError<Unit, UpsertSettingError>

    /**
     * Updates or inserts multiple typed settings in a single transaction.
     *
     * @param userId The user ID that owns these settings
     * @param settings The typed settings to update
     * @return Success or failure with error
     */
    suspend fun upsert(
        userId: UserId,
        settings: List<TypedLocalSetting>,
    ): ResultWithError<Unit, UpsertSettingError>

    /**
     * Atomically reads, transforms, and updates settings.
     *
     * Provides atomic read-modify-write semantics. The transform function receives the current
     * LocalSettings and returns the updated LocalSettings. If settings don't exist, returns
     * SettingsNotFound error.
     *
     * @param userId The unique identifier of the user
     * @param transform Function to transform the current LocalSettings
     * @return Success or failure with error
     */
    suspend fun transform(
        userId: UserId,
        transform: (LocalSettings) -> LocalSettings,
    ): ResultWithError<Unit, TransformSettingError>

    /**
     * Retrieves all settings that need synchronization as typed settings.
     *
     * @param userId The unique identifier of the user
     * @return Success with list of typed settings or failure with error
     */
    suspend fun getUnsyncedSettings(
        userId: UserId,
    ): ResultWithError<List<TypedLocalSetting>, GetUnsyncedSettingsError>
}
