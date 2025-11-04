package timur.gilfanov.messenger.data.source.local

import kotlinx.coroutines.flow.Flow
import timur.gilfanov.messenger.data.source.local.database.entity.SettingEntity
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
     * @param userId The unique identifier of the user
     * @return Flow emitting list of setting entities
     */
    fun observeSettingEntities(userId: UserId): Flow<List<SettingEntity>>

    /**
     * Retrieves a specific setting entity.
     *
     * @param userId The unique identifier of the user
     * @param key The setting key
     * @return Setting entity or null if not found
     */
    suspend fun getSetting(userId: UserId, key: String): SettingEntity?

    /**
     * Updates or inserts a setting entity.
     *
     * @param entity The setting entity to update
     */
    suspend fun updateSetting(entity: SettingEntity)

    /**
     * Retrieves all settings that need synchronization.
     *
     * @return List of setting entities where localVersion > syncedVersion
     */
    suspend fun getUnsyncedSettings(): List<SettingEntity>
}
