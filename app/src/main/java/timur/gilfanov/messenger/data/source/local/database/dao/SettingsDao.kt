package timur.gilfanov.messenger.data.source.local.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import timur.gilfanov.messenger.data.source.local.database.entity.SettingEntity

/**
 * Room DAO for user settings table operations.
 *
 * Provides reactive queries and atomic upsert operations for settings data.
 * All settings are stored with sync metadata (versions, timestamps, status).
 */
@Dao
interface SettingsDao {

    /**
     * Observes all settings for a specific user as a reactive Flow.
     *
     * Emits new list whenever any setting changes for this user.
     *
     * @param userId User identifier
     * @return Flow of setting entities (empty list if no settings exist)
     */
    @Query("SELECT * FROM settings WHERE userId = :userId")
    fun observeAllByUser(userId: String): Flow<List<SettingEntity>>

    /**
     * Retrieves a single setting by user and key.
     *
     * @param userId User identifier
     * @param key Setting key (e.g., "ui_language")
     * @return Setting entity or null if not found
     */
    @Query("SELECT * FROM settings WHERE userId = :userId AND key = :key")
    suspend fun get(userId: String, key: String): SettingEntity?

    /**
     * Retrieves all settings for a specific user.
     *
     * @param userId User identifier
     * @return List of setting entities (empty if no settings exist)
     */
    @Query("SELECT * FROM settings WHERE userId = :userId")
    suspend fun getAll(userId: String): List<SettingEntity>

    /**
     * Retrieves all settings that need synchronization for a specific user.
     *
     * Returns settings where localVersion > syncedVersion, indicating
     * local modifications that haven't been synced to the server yet.
     *
     * @param userId User identifier
     * @return List of unsynced setting entities for the specified user
     */
    @Query("SELECT * FROM settings WHERE userId = :userId AND localVersion > syncedVersion")
    suspend fun getUnsynced(userId: String): List<SettingEntity>

    /**
     * Updates or inserts a single setting atomically.
     *
     * Uses Room's @Upsert which performs INSERT or UPDATE based on primary key.
     *
     * @param setting Setting entity to upsert
     */
    @Upsert
    suspend fun upsert(setting: SettingEntity)

    /**
     * Updates or inserts multiple settings atomically in a single transaction.
     *
     * @param settings List of setting entities to upsert
     */
    @Upsert
    suspend fun upsert(settings: List<SettingEntity>)
}
