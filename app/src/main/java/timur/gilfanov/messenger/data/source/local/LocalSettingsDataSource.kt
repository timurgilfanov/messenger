package timur.gilfanov.messenger.data.source.local

import kotlinx.coroutines.flow.Flow
import timur.gilfanov.messenger.data.source.local.database.entity.SettingEntity
import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.user.UserId

/**
 * Local data source for user settings data.
 *
 * Provides Room-based access to individual setting entities with sync metadata.
 * The repository layer handles mapping between domain Settings and data SettingEntity.
 */
interface LocalSettingsDataSource {
    /**
     * Observes all settings entities for a specific user.
     *
     * Handles transient errors internally with retry logic. Permanent errors propagate
     * to repository layer for error mapping.
     *
     * @param userId The unique identifier of the user
     * @return Flow emitting list of setting entities
     */
    fun observeSettingEntities(userId: UserId): Flow<List<SettingEntity>>

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
    suspend fun updateSetting(entity: SettingEntity): ResultWithError<Unit, UpdateSettingError>

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
